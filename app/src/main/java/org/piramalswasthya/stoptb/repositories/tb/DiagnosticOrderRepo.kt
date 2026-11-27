package org.piramalswasthya.stoptb.repositories.tb

import android.content.Context
import androidx.room.withTransaction
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.configuration.GeneralOpdMasterConfig
import org.piramalswasthya.stoptb.database.room.InAppDb
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.database.room.dao.BenDao
import org.piramalswasthya.stoptb.database.room.dao.TBDao
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.database.shared_preferences.SyncRetryStore
import org.piramalswasthya.stoptb.helpers.NetworkResponse
import org.piramalswasthya.stoptb.model.BenRegCache
import org.piramalswasthya.stoptb.model.ChestXrayResult
import org.piramalswasthya.stoptb.model.MtbResult
import org.piramalswasthya.stoptb.model.OrderResultData
import org.piramalswasthya.stoptb.model.PerTestResults
import org.piramalswasthya.stoptb.model.OrderStatus
import org.piramalswasthya.stoptb.model.RifResult
import org.piramalswasthya.stoptb.model.TBDiagnosticsCache
import org.piramalswasthya.stoptb.model.TBSuspectedCache
import org.piramalswasthya.stoptb.network.AmritApiService
import org.piramalswasthya.stoptb.network.DiagnosticOrderPushRequest
import org.piramalswasthya.stoptb.network.PatientRequest
import org.piramalswasthya.stoptb.repositories.*
import org.piramalswasthya.stoptb.work.WorkerUtils
import timber.log.Timber

/**
 * Diagnostic orders (X-ray, TrueNat, RIF): creating and re-pushing orders, fetching and applying
 * results, the MTB -> RIF cascade and the village status sweeps. Moved out of TBRepo unchanged.
 */
@Singleton
class DiagnosticOrderRepo @Inject constructor(
    private val tbDao: TBDao,
    private val benDao: BenDao,
    private val preferenceDao: PreferenceDao,
    private val userRepo: UserRepo,
    private val tmcNetworkApiService: AmritApiService,
    private val database: InAppDb,
    private val syncRetryStore: SyncRetryStore,
    @ApplicationContext private val context: Context,
    private val tbLocalRepo: TbLocalRepo,
    private val tbPushRepo: TbPushRepo,
    private val deviceIntegrationRepo: DeviceIntegrationRepo,
    private val benIdLocks: BenIdLocks
) {
    internal val orderCreatedTimestamps = java.util.concurrent.ConcurrentHashMap<String, Long>()
    internal val ORDER_STATUS_GRACE_PERIOD_MS = 90_000L
    // Orders whose feed row was skipped inside the grace period are kept in PreferenceDao
    // (getFeedSkippedOrders). The feed token moves past that row, so DiagnosticResultPollWorker
    // asks order/result for them once the grace period ends.
    internal fun feedSkippedKey(benId: Long, type: String) = "$benId|$type"

    // TrueNat (MTB) & RIF order lifecycle redesign: collapses the backend's real `order/result`
    // status vocabulary (confirmed: PENDING, IN_PROGRESS, COMPLETED, FAILED, CLOSED,
    // MANUAL_ENTRY — no other values are ever sent) onto our own storage vocabulary. IN_PROGRESS
    // has no distinct UI treatment from PENDING, so it collapses into PENDING here (the `else`
    // branch) rather than needing its own OrderStatus constant.
    private fun reducedOrderStatus(rawStatus: String?): String {
        val status = rawStatus?.takeIf { it.isNotBlank() } ?: return OrderStatus.PENDING.name
        return when {
            status.equals(OrderStatus.COMPLETED.name, ignoreCase = true) -> OrderStatus.COMPLETED.name
            status.equals(OrderStatus.FAILED.name, ignoreCase = true) -> OrderStatus.FAILED.name
            status.equals(OrderStatus.CLOSED.name, ignoreCase = true) -> OrderStatus.CLOSED.name
            // The order table also holds these terminal states (the status-bucket endpoint already
            // reports them as closed); they must not show as still pending.
            status.equals("CANCELLED", ignoreCase = true) ||
                    status.equals("EXPIRED", ignoreCase = true) ||
                    status.equals("REFUSED", ignoreCase = true) -> OrderStatus.CLOSED.name
            status.equals(OrderStatus.MANUAL_ENTRY.name, ignoreCase = true) -> OrderStatus.MANUAL_ENTRY.name
            // An invalid MTB / RIF machine run; the machine retests the same order.
            status.equals(OrderStatus.INVALID.name, ignoreCase = true) -> OrderStatus.INVALID.name
            else -> OrderStatus.PENDING.name // PENDING, IN_PROGRESS, or anything unrecognized
        }
    }

    suspend fun createOrder(
        benId: Long,
        testType: String,
        reasonForRefusal: String? = null
    ): NetworkResponse<String> {
        return withContext(Dispatchers.IO) {
            // RIF is a test on the MTB order; the server has no MDR_RIF order to create.
            if (testType.equals("MDR_RIF", ignoreCase = true)) {
                Timber.e("createOrder called for MDR_RIF benId=$benId; RIF is part of the MTB order, nothing pushed")
                return@withContext NetworkResponse.Error(context.getString(R.string.error_rif_part_of_mtb_order))
            }
            val user = preferenceDao.getLoggedInUser()
                ?: return@withContext NetworkResponse.Error(context.getString(R.string.error_no_user_logged_in))
            val ben = benDao.getBen(benId)
                ?: return@withContext NetworkResponse.Error(context.getString(R.string.error_beneficiary_not_found))
            val targetBenId = ben.beneficiaryId
            if (targetBenId <= 0) {
                return@withContext NetworkResponse.Error(context.getString(R.string.error_beneficiary_id_invalid))
            }
            if (preferenceDao.isCampModeEnabled() && !preferenceDao.isCampHubConnected()) {
                saveFailedOrderStatus(benId, testType, context.getString(R.string.error_camp_hub_not_connected))
                return@withContext NetworkResponse.Error(context.getString(R.string.error_camp_hub_not_connected))
            }
            try {
                val dobString = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(java.util.Date(ben.dob))
                val sexMapped = when {
                    ben.gender?.name.equals("MALE", ignoreCase = true) -> "Male"
                    ben.gender?.name.equals("FEMALE", ignoreCase = true) -> "Female"
                    else -> "Other"
                }
                val patientReq = PatientRequest(
                    firstName = ben.firstName ?: "",
                    lastName = ben.lastName ?: "",
                    dateOfBirth = dobString,
                    sex = sexMapped
                )
                val apiOrderType = if (testType.equals("SPUTUM_TRUENAT", ignoreCase = true)) "MTB" else testType
                val request = DiagnosticOrderPushRequest(
                    benRegID = ben.beneficiaryId,
                    providerServiceMapID = user.serviceMapId,
                    orderType = apiOrderType,
                    orderEvent = "STOP_TB_REFERRAL",
                    reasonToCloseXray = reasonForRefusal?.takeIf { apiOrderType == "XRAY_CHEST" },
                    reasonToCloseMTB = reasonForRefusal?.takeIf { apiOrderType == "MTB" },
                    patient = patientReq
                )
                val response = tmcNetworkApiService.pushDiagnosticOrder(request)
                val responseBody = response.body()
                val statusCode = response.code()
                if (statusCode == 200) {
                    if (responseBody!= null && response.isSuccessful) {
                        val responseData = responseBody.data
                        if (responseBody.statusCode == 200) {
                            val orderId = responseData.providerOrderId.asValidOrderId()
                                ?: responseData.externalOrderId.asValidOrderId()
                            val status = responseData.status
                            // A declined referral is pushed with its reason and the server closes
                            // the order: it is stored CLOSED and not done, not as a pending test
                            // (the form save takes these fields from this row).
                            val refused = !reasonForRefusal.isNullOrBlank()
                            val storedStatus = when {
                                status.equals(OrderStatus.FAILED.name, ignoreCase = true) -> OrderStatus.FAILED.name
                                refused || status.equals(OrderStatus.CLOSED.name, ignoreCase = true) -> OrderStatus.CLOSED.name
                                else -> OrderStatus.PENDING.name
                            }

                            val cache = benIdLocks.withLock(benId) {
                            val existing = tbDao.getTbDiagnosticsByBenId(benId)
                            val cache = (existing ?: TBDiagnosticsCache(benId = benId)).let {
                                if (testType.equals("XRAY_CHEST", ignoreCase = true)) {
                                    it.copy(
                                        xrayOrderId = orderId,
                                        xrayOrderStatus = storedStatus,
                                        isChestXRayDone = !refused,
                                        isReferredForDigitalChestXray = !refused,
                                        chestXRayResult = null,
                                        reasonNotConductedChestXray = null,
                                        reasonNotConductedChestXrayOther = null,
                                        // A pending result of the old order must not be replayed onto this one.
                                        xrayManualResultPendingSync = false,
                                        syncState = SyncState.UNSYNCED,
                                        errorMsgXray = responseBody.data.errorMessage
                                    )
                                } else {
                                    // A fresh MTB order invalidates any RIF order/result tied to
                                    // the previous MTB result (RIF only ever exists off the back
                                    // of an MTB-positive result) — reset it here too.
                                    it.copy(
                                        trueNatOrderId = orderId,
                                        trueNatOrderStatus = storedStatus,
                                        naatResult = null,
                                        trueNatRifResult = null,
                                        isReferredForSputum = !refused,
                                        isNaatConducted = !refused,
                                        reasonNotConductedNaat = null,
                                        reasonNotConductedNaatOther = null,
                                        rifOrderId = null,
                                        rifOrderStatus = null,
                                        // The previous order's "RIF not conducted" would otherwise
                                        // show on the new order and keep its RIF entry shut.
                                        reasonNotConductedRif = null,
                                        reasonNotConductedRifOther = null,
                                        // A pending result of the old order must not be replayed onto this one.
                                        trueNatManualResultPendingSync = false,
                                        rifManualResultPendingSync = false,
                                        isDrTbConfirmed = null,
                                        // A new order is a new test: not TB confirmed until its
                                        // MTB result arrives, unless liquid culture is positive.
                                        isTBConfirmed = isLiquidCulturePositive(it.liquidCultureResult),
                                        isConfirmed = isLiquidCulturePositive(it.liquidCultureResult),
                                        sputumSubmittedAt = it.sputumSubmittedAt ?: "TB Screening Camp",
                                        syncState = SyncState.UNSYNCED,
                                        errorMsgTrueNat = responseBody.data.errorMessage
                                    )
                                }
                            }
                            tbDao.saveTbDiagnostics(cache)
                            orderCreatedTimestamps["${benId}_${testType}"] = System.currentTimeMillis()
                            // A new order starts clean: a hand-entered result of the old one is
                            // no reference for "Updated by device".
                            preferenceDao.setManualEnteredResult(benId, testType, null)
                            if (!testType.equals("XRAY_CHEST", ignoreCase = true)) {
                                preferenceDao.setManualEnteredResult(benId, "MDR_RIF", null)
                            }
                            cache
                            }
                            syncTBSuspectedFromDiagnostics(benId, cache)
                            try {
                                tbPushRepo.pushUnSyncedRecordsTBSuspected()
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to call pushUnSyncedRecordsTBSuspected after createOrder")
                            }
                            return@withContext NetworkResponse.Success(orderId ?: "")
                        } else {
                            val errorMsg =  response.body()?.errorMessage?: context.getString(R.string.error_push_order_failed)
                            saveFailedOrderStatus(benId, testType, errorMsg)
                            return@withContext NetworkResponse.Error(errorMsg)
                        }
                    }
                }
                saveFailedOrderStatus(benId, testType, context.getString(R.string.error_http_code, statusCode))
                NetworkResponse.Error(context.getString(R.string.error_http_code, statusCode))
            } catch (e: Exception) {
                Timber.e(e, "createOrder failed")
                saveFailedOrderStatus(benId, testType)
                NetworkResponse.Error(e.message ?: "Unknown error")
            }
        }
    }

    suspend fun retryPushOrder(
        benId: Long,
        testType: String
    ): NetworkResponse<String> {
        return withContext(Dispatchers.IO) {
            val ben = benDao.getBen(benId)
                ?: return@withContext NetworkResponse.Error(context.getString(R.string.error_beneficiary_not_found))
            val targetBenId = ben.beneficiaryId
            if (targetBenId <= 0) {
                return@withContext NetworkResponse.Error(context.getString(R.string.error_beneficiary_id_invalid))
            }
            try {
                val apiOrderType = if (testType.equals("SPUTUM_TRUENAT", ignoreCase = true)) "MTB" else testType
                val response = tmcNetworkApiService.retryOrder(benId = targetBenId, orderType = apiOrderType)
                val statusCode = response.code()
                if (statusCode == 200) {
                    val responseBody = response.body()
                    if (responseBody != null && response.isSuccessful) {
                        val resStatusCode = responseBody.statusCode
                        if (resStatusCode == 200) {
                            val responseData = responseBody.data
                            val orderId = responseData.externalOrderId.asValidOrderId()
                            val status = responseData.status?.takeIf { it.isNotBlank() } ?: "PENDING"

                            val cache = benIdLocks.withLock(benId) {
                            val existing = tbDao.getTbDiagnosticsByBenId(benId)
                            val cache = (existing ?: TBDiagnosticsCache(benId = benId)).let {
                                if (testType.equals("XRAY_CHEST", ignoreCase = true)) {
                                    it.copy(
                                        xrayOrderId = orderId ?: it.xrayOrderId,
                                        xrayOrderStatus = reducedOrderStatus(status),
                                        isChestXRayDone = true,
                                        isReferredForDigitalChestXray = true,
                                        syncState = SyncState.UNSYNCED
                                    )
                                } else if (testType.equals("MDR_RIF", ignoreCase = true)) {
                                    // TrueNat/RIF order lifecycle redesign: collapse whatever the
                                    // retry response reports into the reduced 5-value vocabulary.
                                    it.copy(
                                        rifOrderId = orderId ?: it.rifOrderId,
                                        rifOrderStatus = reducedOrderStatus(status),
                                        trueNatRifResult = null,
                                        syncState = SyncState.UNSYNCED
                                    )
                                } else {
                                    val isIntegrated = deviceIntegrationRepo.isTruenatIntegrated()
                                    it.copy(
                                        trueNatOrderId = orderId ?: it.trueNatOrderId,
                                        trueNatOrderStatus = reducedOrderStatus(status),
                                        naatResult = null,
                                        trueNatRifResult = null,
                                        isReferredForSputum = true,
                                        isNaatConducted = isIntegrated,
                                        sputumSubmittedAt = "TB Screening Camp",
                                        syncState = SyncState.UNSYNCED
                                    )
                                }
                            }
                            tbDao.saveTbDiagnostics(cache)
                            orderCreatedTimestamps["${benId}_${testType}"] = System.currentTimeMillis()
                            cache
                            }
                            syncTBSuspectedFromDiagnostics(benId, cache)
                            try {
                                tbPushRepo.pushUnSyncedRecordsTBSuspected()
                            } catch (e: java.lang.Exception) {
                                Timber.e(e, "Failed to call pushUnSyncedRecordsTBSuspected after retryPushOrder")
                            }
                            return@withContext NetworkResponse.Success(orderId ?: "")
                        } else {
                            val errorMsg = responseBody.errorMessage ?: context.getString(R.string.error_retry_order_failed)
                            return@withContext NetworkResponse.Error(errorMsg)
                        }
                    }
                }
                return@withContext NetworkResponse.Error(context.getString(R.string.error_http_code, statusCode))
            } catch (e: Exception) {
                Timber.e(e, "retryOrder failed")
                return@withContext NetworkResponse.Error(e.message ?: "Unknown error")
            }
        }
    }

    // Ensures a TB_SUSPECTED row exists/stays current for this beneficiary whenever a diagnostic
    // order is created, manually completed, or retried — mirroring only the fields that already
    // exist on TBSuspectedCache (no schema change). Fields with no equivalent here (order
    // ids/statuses, RIF result, denial reasons) simply aren't written; the row still gets
    // created/touched so the beneficiary shows up in the backend's tb_suspected table.
    suspend fun syncTBSuspectedFromDiagnostics(
        benId: Long,
        diag: TBDiagnosticsCache,
        reasonForRefusalMDRRIF: String? = null // no local field carries this; caller passes it explicitly
    ) {
        try {
            val confirmed = benIdLocks.withLock(benId) {
                val updated = suspectedFromDiagnostics(tbDao.getTbSuspected(benId), benId, diag, reasonForRefusalMDRRIF)
                updated != null && tbLocalRepo.saveTBSuspectedLocked(updated)
            }
            // As saveTBSuspected does: a confirmed case marks its household's members as
            // contacts of a known TB patient, once the beneficiary's lock is released.
            if (confirmed) tbLocalRepo.applyKnownTbContactRiskFactorsForBen(benId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "syncTBSuspectedFromDiagnostics failed for benId=$benId")
        }
    }

    /**
     * The TB_SUSPECTED row [diag] implies for this beneficiary, given the current row [existing],
     * with its syncState already decided; null when nothing changes. Pure: no database access.
     */
    @androidx.annotation.VisibleForTesting
    internal fun suspectedFromDiagnostics(
        existing: TBSuspectedCache?,
        benId: Long,
        diag: TBDiagnosticsCache,
        reasonForRefusalMDRRIF: String? = null
    ): TBSuspectedCache? {
        // TB_SUSPECTED.isSputumCollected still means "referred" (a separate, unrelated table
        // from TB_DIAGNOSTICS' own isSputumCollected, which was repurposed to mean "sample
        // physically collected") — so this reads diag.isReferredForSputum, not diag.isSputumCollected.
        val mappedIsSputumCollected = when {
            diag.isReferredForSputum == false ||
            diag.reasonForDenialSputum != null -> false

            diag.reasonNotConductedNaat != null ||
            diag.isReferredForSputum == true ||
            !diag.trueNatOrderId.isNullOrBlank() -> true

            else -> existing?.isSputumCollected
        }

        val mappedSputumSubmittedAt = when {
            mappedIsSputumCollected == false -> null
            mappedIsSputumCollected == true -> diag.sputumSubmittedAt ?: existing?.sputumSubmittedAt ?: "TB Screening Camp"
            else -> existing?.sputumSubmittedAt
        }

        val naatResLocal = diag.naatResult
        // The current MTB order has no MTB result (waiting, INVALID, CLOSED or FAILED). A new order is a
        // new test: earlier MTB / RIF results no longer apply and the beneficiary is not TB confirmed
        // until its result arrives (a positive liquid culture still confirms). No MTB order: keep stored.
        val mtbOrderHasNoResult = naatResLocal.isNullOrBlank() && diag.trueNatOrderId.asValidOrderId() != null
        val mappedSputumTestResult = when {
            !naatResLocal.isNullOrBlank() -> {
                val clean = naatResLocal.trim().lowercase()
                when {
                    clean.contains("not detected") || clean.contains("negative") -> "TB Negative"
                    clean.contains("detected") || clean.contains("positive") -> "TB Positive"
                    else -> null
                }
            }
            mtbOrderHasNoResult -> null
            else -> existing?.sputumTestResult
        }

        val mappedIsChestXRayDone = when {
            // Unlike sputum's mappedIsSputumCollected above, both CLOSED sub-cases
            // (declined, or not-conducted via reasonNotConductedChestXray already checked
            // below in this same branch) legitimately map to the same outcome here — a
            // closed X-ray order always means the chest X-ray itself was not done.
            diag.xrayOrderStatus.equals("CLOSED", ignoreCase = true) ||
            diag.isReferredForDigitalChestXray == false ||
            diag.reasonNotConductedChestXray != null -> false

            diag.isChestXRayDone == true ||
            !diag.xrayOrderId.isNullOrBlank() -> true

            else -> existing?.isChestXRayDone
        }

        val mappedChestXRayResult = when {
            diag.xrayOrderStatus.equals("COMPLETED", ignoreCase = true) && diag.chestXRayResult != null -> {
                diag.chestXRayResult
            }
            else -> diag.chestXRayResult ?: existing?.chestXRayResult
        }

        // The server's result wins: once MTB is known and not positive, or absent
        // (mtbOrderHasNoResult), no RIF result applies, even one TB_SUSPECTED held before.
        val mtbKnownNotPositive = mtbOrderHasNoResult ||
            MtbResult.fromResultText(diag.naatResult).let { it != null && it != MtbResult.TB_POSITIVE }
        val currentOrderCarriesRif = diag.trueNatOrderId.asValidOrderId() != null &&
            diag.rifOrderId == diag.trueNatOrderId
        val mappedMdrRifResult = when {
            mtbKnownNotPositive -> null
            diag.rifOrderStatus.equals("COMPLETED", ignoreCase = true) && diag.trueNatRifResult != null -> {
                when {
                    diag.trueNatRifResult.equals("Rif Resistance Detected", ignoreCase = true) ||
                    diag.trueNatRifResult.equals("DR TB", ignoreCase = true) -> "DR TB"
                    diag.trueNatRifResult.equals("Rif Resistance Not Detected", ignoreCase = true) ||
                    diag.trueNatRifResult.equals("Non DR TB", ignoreCase = true) -> "Non DR TB"
                    diag.trueNatRifResult.equals("Indeterminate", ignoreCase = true) -> "Indeterminate"
                    else -> diag.trueNatRifResult
                }
            }
            // Combined order: the current MTB order carries its own RIF test, so an earlier order's RIF
            // result must not stand in for it. Legacy separate MDR_RIF orders keep the fallback.
            currentOrderCarriesRif -> diag.trueNatRifResult
            else -> diag.trueNatRifResult ?: existing?.mdrRifResult
        }

        val mappedIsDRTBConfirmed = when {
            mappedMdrRifResult == "DR TB" -> true
            mappedMdrRifResult == "Non DR TB" || mappedMdrRifResult == "Indeterminate" -> false
            mtbKnownNotPositive -> false
            currentOrderCarriesRif -> false
            else -> existing?.isDRTBConfirmed
        }

        // Built from `existing` without touching syncState, so a structural comparison
        // against `existing` tells us whether anything actually changed. Without this guard,
        // every diagnostic poll (which runs every 60s while a device order is in progress)
        // would unconditionally flip an already-synced record back to UNSYNCED.
        val provisionalCache = (existing ?: TBSuspectedCache(benId = benId, visitLabel = "Visit 1")).copy(
            hasSymptoms = true,
            isChestXRayDone = mappedIsChestXRayDone,
            chestXRayResult = mappedChestXRayResult,
            isSputumCollected = mappedIsSputumCollected,
            sputumSubmittedAt = mappedSputumSubmittedAt,
            isNaatConducted = diag.isNaatConducted ?: existing?.isNaatConducted,
            naatResult = if (mtbOrderHasNoResult) null else diag.naatResult ?: existing?.naatResult,
            // A previous X-ray order's "not conducted" reason stays only while the current X-ray
            // order is CLOSED (as for MTB and RIF).
            reasonForRefusalXray = diag.reasonNotConductedChestXray
                ?: if (diag.xrayOrderId.asValidOrderId() != null &&
                    !diag.xrayOrderStatus.equals(OrderStatus.CLOSED.name, ignoreCase = true)
                ) null else existing?.reasonForRefusalXray,
            reasonForRefusalSputum = diag.reasonForDenialSputum ?: existing?.reasonForRefusalSputum,
            // A previous order's "MTB not conducted" reason stays only while the current MTB order is CLOSED.
            reasonForRefusalMTB = diag.reasonNotConductedNaat
                ?: if (diag.trueNatOrderId.asValidOrderId() != null &&
                    !diag.trueNatOrderStatus.equals(OrderStatus.CLOSED.name, ignoreCase = true)
                ) null else existing?.reasonForRefusalMTB,
            // The stored reason stays only while the current MTB order's RIF test is closed; a new MTB
            // order or a RIF still to come must not inherit the previous order's reason.
            reasonForRefusalMDRRIF = reasonForRefusalMDRRIF
                ?: formatNotConductedReason(diag.reasonNotConductedRif, diag.reasonNotConductedRifOther)
                ?: if (!diag.trueNatOrderId.isNullOrBlank() &&
                    !diag.rifOrderStatus.equals(OrderStatus.CLOSED.name, ignoreCase = true)
                ) null else existing?.reasonForRefusalMDRRIF,
            mdrRifResult = mappedMdrRifResult,
            isDRTBConfirmed = mappedIsDRTBConfirmed,
            // Confirmed by an MTB Positive or a positive liquid culture, as on TB_DIAGNOSTICS
            // (formRowOverStored / mergeTrueNatOrder).
            isTBConfirmed = mappedSputumTestResult == "TB Positive" ||
                isLiquidCulturePositive(diag.liquidCultureResult ?: existing?.liquidCultureResult),
            isConfirmed = mappedSputumTestResult == "TB Positive" ||
                isLiquidCulturePositive(diag.liquidCultureResult ?: existing?.liquidCultureResult),
            sputumTestResult = mappedSputumTestResult,
            // TB_SUSPECTED's own copy of these fields stayed null for any beneficiary referred via
            // the quick-entry screen (which only writes them onto TB_DIAGNOSTICS), even though
            // TB_SUSPECTED has its own push to the backend (saveTBSuspectedData).
            recommendedForLiquidCultureTest = diag.recommendedForLiquidCultureTest ?: existing?.recommendedForLiquidCultureTest,
            isLiquidCultureConducted = diag.isLiquidCultureConducted ?: existing?.isLiquidCultureConducted,
            liquidCultureResult = diag.liquidCultureResult ?: existing?.liquidCultureResult
        )

        // hasSymptoms is deliberately always forced true above (this function only runs off
        // the back of a diagnostic test, which implies symptomatic), but the server's getAll
        // response sometimes omits the field entirely, which toCache() then defaults back to
        // false on the next pull. Normalize it here so that server-side quirk alone can't
        // masquerade as a real change and defeat the guard below on every poll.
        val normalizedExisting = existing?.copy(hasSymptoms = true)
        if (existing != null && provisionalCache == normalizedExisting) {
            return null
        }

        // hasSymptoms, isNaatConducted and naatResult live only on the tablet: the server's
        // tb_suspected has no columns for them, so they are dropped on upload and come back
        // false/null in the next changes-feed page. Re-deriving them here then looked like a
        // change every time, so the record was marked UNSYNCED, uploaded, pulled back without
        // them and marked again. Keep them locally, but only queue an upload when a field the
        // server stores has changed.
        if (existing != null &&
            provisionalCache.withoutTabletOnlyFields() == existing.withoutTabletOnlyFields()
        ) {
            return provisionalCache.copy(syncState = existing.syncState)
        }

        // Logged only here, where the row is really queued for upload: a change to tablet-only
        // fields alone (returned above with its sync state kept) is not.
        if (existing != null) {
            val diffs = buildList {
                if (existing.hasSymptoms != provisionalCache.hasSymptoms) add("hasSymptoms: ${existing.hasSymptoms} -> ${provisionalCache.hasSymptoms}")
                if (existing.isChestXRayDone != provisionalCache.isChestXRayDone) add("isChestXRayDone: ${existing.isChestXRayDone} -> ${provisionalCache.isChestXRayDone}")
                if (existing.chestXRayResult != provisionalCache.chestXRayResult) add("chestXRayResult: changed")
                if (existing.isSputumCollected != provisionalCache.isSputumCollected) add("isSputumCollected: ${existing.isSputumCollected} -> ${provisionalCache.isSputumCollected}")
                if (existing.sputumSubmittedAt != provisionalCache.sputumSubmittedAt) add("sputumSubmittedAt: ${existing.sputumSubmittedAt} -> ${provisionalCache.sputumSubmittedAt}")
                if (existing.isNaatConducted != provisionalCache.isNaatConducted) add("isNaatConducted: ${existing.isNaatConducted} -> ${provisionalCache.isNaatConducted}")
                if (existing.naatResult != provisionalCache.naatResult) add("naatResult: changed")
                if (existing.reasonForRefusalXray != provisionalCache.reasonForRefusalXray) add("reasonForRefusalXray: ${existing.reasonForRefusalXray} -> ${provisionalCache.reasonForRefusalXray}")
                if (existing.reasonForRefusalSputum != provisionalCache.reasonForRefusalSputum) add("reasonForRefusalSputum: ${existing.reasonForRefusalSputum} -> ${provisionalCache.reasonForRefusalSputum}")
                if (existing.reasonForRefusalMTB != provisionalCache.reasonForRefusalMTB) add("reasonForRefusalMTB: ${existing.reasonForRefusalMTB} -> ${provisionalCache.reasonForRefusalMTB}")
                if (existing.reasonForRefusalMDRRIF != provisionalCache.reasonForRefusalMDRRIF) add("reasonForRefusalMDRRIF: ${existing.reasonForRefusalMDRRIF} -> ${provisionalCache.reasonForRefusalMDRRIF}")
                if (existing.mdrRifResult != provisionalCache.mdrRifResult) add("mdrRifResult: changed")
                if (existing.isDRTBConfirmed != provisionalCache.isDRTBConfirmed) add("isDRTBConfirmed: ${existing.isDRTBConfirmed} -> ${provisionalCache.isDRTBConfirmed}")
                if (existing.isTBConfirmed != provisionalCache.isTBConfirmed) add("isTBConfirmed: ${existing.isTBConfirmed} -> ${provisionalCache.isTBConfirmed}")
                if (existing.isConfirmed != provisionalCache.isConfirmed) add("isConfirmed: ${existing.isConfirmed} -> ${provisionalCache.isConfirmed}")
                if (existing.sputumTestResult != provisionalCache.sputumTestResult) add("sputumTestResult: changed")
                if (existing.recommendedForLiquidCultureTest != provisionalCache.recommendedForLiquidCultureTest) add("recommendedForLiquidCultureTest: ${existing.recommendedForLiquidCultureTest} -> ${provisionalCache.recommendedForLiquidCultureTest}")
                if (existing.isLiquidCultureConducted != provisionalCache.isLiquidCultureConducted) add("isLiquidCultureConducted: ${existing.isLiquidCultureConducted} -> ${provisionalCache.isLiquidCultureConducted}")
                if (existing.liquidCultureResult != provisionalCache.liquidCultureResult) add("liquidCultureResult: changed")
            }
            Timber.w("TB_SUSPECTED_DIRTY: benId=$benId marking UNSYNCED (was ${existing.syncState}), diag.xrayOrderStatus=${diag.xrayOrderStatus} trueNatOrderStatus=${diag.trueNatOrderStatus} rifOrderStatus=${diag.rifOrderStatus}, changes=$diffs")
        } else {
            Timber.w("TB_SUSPECTED_DIRTY: benId=$benId creating new tb_suspected record from diagnostics")
        }

        return provisionalCache.copy(syncState = SyncState.UNSYNCED)
    }

    // Pass errorMessage only for confirmed server/HTTP failures; keep it null for ambiguous failures.
    private suspend fun saveFailedOrderStatus(benId: Long, testType: String, errorMessage: String? = null) {
        try {
            val cache = benIdLocks.withLock(benId) {
            val existing = tbDao.getTbDiagnosticsByBenId(benId)
            val cache = (existing ?: TBDiagnosticsCache(benId = benId)).let {
                when {
                    testType.equals("XRAY_CHEST", ignoreCase = true) -> it.copy(
                        xrayOrderStatus = "FAILED",
                        errorMsgXray = errorMessage ?: it.errorMsgXray,
                        syncState = SyncState.UNSYNCED
                    )
                    testType.equals("MDR_RIF", ignoreCase = true) -> it.copy(
                        rifOrderStatus = "FAILED",
                        errorMsgRif = errorMessage ?: it.errorMsgRif,
                        syncState = SyncState.UNSYNCED
                    )
                    else -> it.copy(
                        trueNatOrderStatus = "FAILED",
                        errorMsgTrueNat = errorMessage ?: it.errorMsgTrueNat,
                        syncState = SyncState.UNSYNCED
                    )
                }
            }
            tbDao.saveTbDiagnostics(cache)
            cache
            }
            syncTBSuspectedFromDiagnostics(benId, cache)
        } catch (e: Exception) {
            Timber.e(e, "saveFailedOrderStatus failed")
        }
    }

    suspend fun markTestCompleted(benId: Long, orderType: String): NetworkResponse<String> {
        return withContext(Dispatchers.IO) {
            val ben = benDao.getBen(benId)
                ?: return@withContext NetworkResponse.Error(context.getString(R.string.error_beneficiary_not_found))
            try {
                val apiOrderType = if (orderType.equals("SPUTUM_TRUENAT", ignoreCase = true)) "MTB" else orderType
                val response = tmcNetworkApiService.markTestCompleted(benRegID = ben.beneficiaryId, orderType = apiOrderType)
                val statusCode = response.code()
                if (statusCode == 200) {
                    val responseString = response.body()?.string()
                    if (responseString != null) {
                        val jsonObj = JSONObject(responseString)
                        val resStatusCode = jsonObj.optInt("statusCode")
                        if (resStatusCode == 200) {
                            val dataObj = jsonObj.optJSONObject("data")
                            val rawStatus = dataObj?.optString("status")
                            // reducedOrderStatus() already defaults a blank/unrecognized status to
                            // PENDING — never write the raw server string (or a hardcoded
                            // "IN_PROGRESS") directly, or the poll worker's exact-PENDING filter
                            // silently stops tracking this order.
                            val status = reducedOrderStatus(rawStatus)

                            benIdLocks.withLock(benId) {
                                val existing = tbDao.getTbDiagnosticsByBenId(benId)
                                existing?.let {
                                    val cache = when {
                                        orderType.equals("XRAY_CHEST", ignoreCase = true) -> {
                                            it.copy(
                                                xrayOrderStatus = status,
                                                isReferredForDigitalChestXray = true,
                                                syncState = SyncState.UNSYNCED
                                            )
                                        }
                                        orderType.equals("MDR_RIF", ignoreCase = true) -> {
                                            it.copy(
                                                rifOrderStatus = status,
                                                syncState = SyncState.UNSYNCED
                                            )
                                        }
                                        else -> {
                                            it.copy(
                                                trueNatOrderStatus = status,
                                                isReferredForSputum = true,
                                                syncState = SyncState.UNSYNCED
                                            )
                                        }
                                    }
                                    tbDao.saveTbDiagnostics(cache)
                                }
                            }
                            preferenceDao.setDiagPollStartTime(benId, orderType, System.currentTimeMillis())
                            
                            // Without the changes feed, fetch the X-ray result now; with it, the result
                            // arrives through the feed (no per-order calls).
                            if (orderType.equals("XRAY_CHEST", ignoreCase = true) && !preferenceDao.isChangesFeedActive()) {
                                fetchOrderResult(benId, orderType)
                            }

                            return@withContext NetworkResponse.Success(status)
                        } else {
                            val errorMsg = jsonObj.optString("errorMessage") ?: "Failed to mark test completed"
                            return@withContext NetworkResponse.Error(errorMsg)
                        }
                    }
                }
                NetworkResponse.Error(context.getString(R.string.error_http_code, statusCode))
            } catch (e: Exception) {
                Timber.e(e, "markTestCompleted failed")
                NetworkResponse.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Applies one order's result to TB_DIAGNOSTICS (through [mergeOrderResult]), syncs TB Suspected
     * from it and returns the server status. Used by [fetchOrderResult].
     */
    internal suspend fun applyOrderResult(
        ben: BenRegCache,
        orderType: String,
        responseData: OrderResultData,
        orderIdAtStart: String?,
        pushSuspectedAfter: Boolean
    ): String {
        val benId = ben.beneficiaryId
        val isXrayType = orderType.equals("XRAY_CHEST", ignoreCase = true)
        val isRifType = orderType.equals("MDR_RIF", ignoreCase = true)
        val rawStatus = responseData.status
        val status = if (rawStatus.isNullOrBlank()) "IN_PROGRESS" else rawStatus

        // Re-reads the row inside the per-benId lock right before writing, so a concurrent write
        // for another test type on the same beneficiary is not overwritten by a stale snapshot.
        val syncedCache = benIdLocks.withLock(benId) {
        val existing = tbDao.getTbDiagnosticsByBenId(benId)
        val currentOrderId = if (isXrayType) existing?.xrayOrderId else if (isRifType) existing?.rifOrderId else existing?.trueNatOrderId
        if (orderIdAtStart != null && currentOrderId != orderIdAtStart) {
            // A newer order for this test type (e.g. an auto-reorder on
            // Invalid/Error) was created while this call's network round trip
            // was in flight. This response — including its fresh-looking
            // status/order id — describes the now-superseded order; committing
            // it here (and force-marking SYNCED below) would clobber whatever
            // that newer order's own write already committed. Skip; the next
            // poll cycle will fetch against the current order instead.
            return@withLock existing ?: TBDiagnosticsCache(benId = benId)
        }
        val merged = mergeOrderResult(existing, benId, orderType, responseData, status, deviceIntegrationRepo.isTruenatIntegrated(), deviceIntegrationRepo.hubCreatesOrders())
        // Enqueued before the row is saved: WorkManager keeps the work across a restart, so the
        // referral survives the process dying right after the completed X-ray is stored.
        if (merged.triggerTrueNat) {
            WorkerUtils.triggerDiagnosticOrderPushWorkers(context, benId, listOf("SPUTUM_TRUENAT"))
        }
        val synced = merged.cache.copy(syncState = SyncState.SYNCED)
        // Skip the write when nothing changed: every TB_DIAGNOSTICS write re-runs every live
        // query on the table (list badges, dashboard).
        if (synced != existing) tbDao.saveTbDiagnostics(synced)
        synced
        }

        try {
            syncTBSuspectedFromDiagnostics(benId, syncedCache)
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync diagnostic results into tb_suspected table")
        }
        if (pushSuspectedAfter) {
            try {
                tbPushRepo.pushUnSyncedRecordsTBSuspected()
            } catch (e: Exception) {
                Timber.e(e, "Failed to call pushUnSyncedRecordsTBSuspected after fetchOrderResult")
            }
        }
        return status
    }

    /**
     * The diagnostics row after applying one order (from the changes feed, order/result or the
     * order/manualResult response) to [existing] (null = no row yet). Pure: no database or network.
     *
     * The server's result always wins: when it sent the `results` list, every result field of the
     * test is rewritten and a missing entry clears the stored value (so a vendor TB Negative hides
     * RIF again). Exception: a manual result still pending sync keeps its fields until the replay.
     *
     * [triggerTrueNat] asks the caller to push the X-ray -> TrueNat referral once the row is saved.
     * Never when [hubCreatesOrders]: a camp hub creates that order itself from the X-ray result.
     */
    @androidx.annotation.VisibleForTesting
    internal fun mergeOrderResult(
        existing: TBDiagnosticsCache?,
        benId: Long,
        orderType: String,
        responseData: OrderResultData,
        status: String,
        truenatIntegrated: Boolean,
        hubCreatesOrders: Boolean = false
    ): MergedOrderResult {
        val base = existing ?: TBDiagnosticsCache(benId = benId)
        val fetchedOrderId = responseData.externalOrderId.asValidOrderId()
        val perTest = responseData.perTest(fallbackType = orderType)
        return when {
            orderType.equals("XRAY_CHEST", ignoreCase = true) ->
                mergeXrayOrder(base, fetchedOrderId, responseData, perTest, status, truenatIntegrated, hubCreatesOrders)
            orderType.equals("MDR_RIF", ignoreCase = true) ->
                MergedOrderResult(mergeLegacyRifOrder(base, fetchedOrderId, responseData, perTest, status), false)
            else -> MergedOrderResult(mergeTrueNatOrder(base, fetchedOrderId, responseData, perTest, status), false)
        }
    }

    private fun mergeXrayOrder(
        base: TBDiagnosticsCache,
        fetchedOrderId: String?,
        data: OrderResultData,
        perTest: PerTestResults,
        status: String,
        truenatIntegrated: Boolean,
        hubCreatesOrders: Boolean
    ): MergedOrderResult {
        val mappedStatus = reducedOrderStatus(status)
        val isCompleted = mappedStatus == OrderStatus.COMPLETED.name
        val summary = perTest.xray?.resultSummary?.takeIf { it.isNotBlank() }
        val standardized = ChestXrayResult.fromResultText(summary)
        // Automated AI-Invalid: the server raises the repeat order itself; keep the row as it is
        // until that order arrives.
        if (isCompleted && standardized == ChestXrayResult.AI_INVALID) return MergedOrderResult(base, false)

        val closeReason = splitOtherReason(data.reasonToCloseXray)
        // A different order (e.g. a retest the server raised) carries none of the old one's reason.
        val newOrder = fetchedOrderId != null && fetchedOrderId != base.xrayOrderId
        var merged = base.copy(
            xrayOrderId = fetchedOrderId ?: base.xrayOrderId,
            reasonNotConductedChestXray = if (newOrder) null else base.reasonNotConductedChestXray,
            reasonNotConductedChestXrayOther = if (newOrder) null else base.reasonNotConductedChestXrayOther,
            xrayOrderStatus = mappedStatus,
            isReferredForDigitalChestXray = true,
            errorMsgXray = if (mappedStatus == OrderStatus.FAILED.name) data.errorMessage ?: base.errorMsgXray else base.errorMsgXray,
            syncState = SyncState.UNSYNCED
        )
        if (base.xrayManualResultPendingSync != true) {
            merged = when {
                summary != null -> merged.copy(
                    isChestXRayDone = true,
                    chestXRayResult = standardized?.displayValue ?: summary,
                    reasonNotConductedChestXray = null,
                    reasonNotConductedChestXrayOther = null
                )
                closeReason != null -> merged.copy(
                    isChestXRayDone = false,
                    chestXRayResult = null,
                    reasonNotConductedChestXray = closeReason.first,
                    reasonNotConductedChestXrayOther = closeReason.second
                )
                // The server holds no X-ray result: one that was stored before is gone.
                perTest.hasResultsList -> merged.copy(chestXRayResult = null, isChestXRayDone = if (isCompleted) base.isChestXRayDone else null)
                else -> merged
            }
        }
        val hasTrueNat = !base.trueNatOrderId.isNullOrBlank() ||
                base.trueNatOrderStatus.equals(OrderStatus.COMPLETED.name, ignoreCase = true) ||
                base.trueNatOrderStatus.equals(OrderStatus.PENDING.name, ignoreCase = true) ||
                base.trueNatOrderStatus.equals(OrderStatus.MANUAL_ENTRY.name, ignoreCase = true) ||
                base.trueNatOrderStatus.equals(OrderStatus.CLOSED.name, ignoreCase = true)
        val triggerTrueNat = isCompleted && standardized?.triggersTrueNatReferral == true &&
                !hasTrueNat && truenatIntegrated && !hubCreatesOrders
        return MergedOrderResult(merged, triggerTrueNat)
    }

    /**
     * The TrueNat order: MTB result plus, on the same order, the RIF result. The RIF fields
     * (rifOrderId / rifOrderStatus / trueNatRifResult) are derived from this one order:
     *  - a MDR_RIF result entry -> its result, status COMPLETED
     *  - closed with reasonToCloseMDRRIF -> CLOSED with the reason
     *  - MTB Positive and no RIF result yet -> the order's own status (waiting for RIF)
     *  - otherwise no RIF at all.
     */
    private fun mergeTrueNatOrder(
        base: TBDiagnosticsCache,
        fetchedOrderId: String?,
        data: OrderResultData,
        perTest: PerTestResults,
        status: String
    ): TBDiagnosticsCache {
        val mappedStatus = reducedOrderStatus(status)
        val orderId = fetchedOrderId ?: base.trueNatOrderId
        // A different order (e.g. a retest the server raised) carries none of the old one's reasons.
        val newOrder = fetchedOrderId != null && fetchedOrderId != base.trueNatOrderId
        var merged = base.copy(
            trueNatOrderId = orderId,
            trueNatOrderStatus = mappedStatus,
            isReferredForSputum = true,
            reasonNotConductedNaat = if (newOrder) null else base.reasonNotConductedNaat,
            reasonNotConductedNaatOther = if (newOrder) null else base.reasonNotConductedNaatOther,
            reasonNotConductedRif = if (newOrder) null else base.reasonNotConductedRif,
            reasonNotConductedRifOther = if (newOrder) null else base.reasonNotConductedRifOther,
            errorMsgTrueNat = if (mappedStatus == OrderStatus.FAILED.name) data.errorMessage ?: base.errorMsgTrueNat else base.errorMsgTrueNat,
            syncState = SyncState.UNSYNCED
        )
        // A manual MTB or RIF result still on its way to the server keeps every result field as
        // the user entered it; the replay's response brings the server's answer.
        if (base.trueNatManualResultPendingSync == true || base.rifManualResultPendingSync == true) return merged

        val legacyFlat = !perTest.hasResultsList
        // An invalid run ("Error-2") is not a result: the machine retests the same order (INVALID).
        // Kept out of naatResult / trueNatRifResult so the card offers Enter Result instead.
        val mtbSummary = perTest.mtb?.resultSummary?.takeIf { it.isNotBlank() && !isMachineRunError(it) }
        val mtbResult = MtbResult.fromResultText(mtbSummary)
        // Older server, order not finished yet: the flat summary is not a result; keep what is stored.
        if (legacyFlat && mappedStatus != OrderStatus.COMPLETED.name && mappedStatus != OrderStatus.CLOSED.name) return merged

        val mtbClose = splitOtherReason(data.reasonToCloseMTB)
        val rifClose = splitOtherReason(data.reasonToCloseMDRRIF)
        val naatResult = mtbResult?.displayValue ?: mtbSummary
        merged = merged.copy(
            naatResult = naatResult,
            isNaatConducted = when {
                naatResult != null -> true
                mtbClose != null -> false
                else -> base.isNaatConducted
            },
            reasonNotConductedNaat = mtbClose?.first ?: if (naatResult != null) null else merged.reasonNotConductedNaat,
            reasonNotConductedNaatOther = mtbClose?.second ?: if (naatResult != null) null else merged.reasonNotConductedNaatOther,
            // The results list is the server's whole answer: with no MTB result in it, an earlier MTB
            // Positive no longer confirms TB (only a positive liquid culture does). A flat response keeps it.
            isTBConfirmed = when {
                mtbResult != null -> mtbResult == MtbResult.TB_POSITIVE || isLiquidCulturePositive(base.liquidCultureResult)
                perTest.hasResultsList -> isLiquidCulturePositive(base.liquidCultureResult)
                else -> base.isTBConfirmed
            },
            isConfirmed = when {
                mtbResult != null -> mtbResult == MtbResult.TB_POSITIVE || isLiquidCulturePositive(base.liquidCultureResult)
                perTest.hasResultsList -> isLiquidCulturePositive(base.liquidCultureResult)
                else -> base.isConfirmed
            }
        )

        // On an older server the RIF result arrives on its own MDR_RIF order (mergeLegacyRifOrder).
        if (legacyFlat) return merged

        val rifSummary = perTest.rif?.resultSummary?.takeIf { it.isNotBlank() && !isMachineRunError(it) }
        val rifResult = RifResult.fromResultText(rifSummary)
        val mtbPositive = mtbResult == MtbResult.TB_POSITIVE
        // The RIF row's own status: a hand-entered RIF "Invalid/Error" is CLOSED (and closes the
        // order) on the server, not COMPLETED. Absent or unknown -> COMPLETED, as a result is in.
        val rifRowStatus = perTest.rif?.providerStatus?.let { reducedOrderStatus(it) }
            ?.takeIf { it == OrderStatus.CLOSED.name || it == OrderStatus.FAILED.name }
            ?: OrderStatus.COMPLETED.name
        return when {
            rifSummary != null -> merged.copy(
                rifOrderId = orderId,
                rifOrderStatus = rifRowStatus,
                trueNatRifResult = rifResult?.displayValue ?: rifSummary,
                isDrTbConfirmed = rifResult == RifResult.DR_TB,
                reasonNotConductedRif = null,
                reasonNotConductedRifOther = null
            )
            rifClose != null -> merged.copy(
                rifOrderId = orderId,
                rifOrderStatus = OrderStatus.CLOSED.name,
                trueNatRifResult = null,
                isDrTbConfirmed = false,
                reasonNotConductedRif = rifClose.first,
                reasonNotConductedRifOther = rifClose.second
            )
            mtbPositive -> merged.copy(
                rifOrderId = orderId,
                rifOrderStatus = mappedStatus,
                trueNatRifResult = null,
                isDrTbConfirmed = false,
                reasonNotConductedRif = null,
                reasonNotConductedRifOther = null
            )
            else -> merged.copy(
                rifOrderId = null,
                rifOrderStatus = null,
                trueNatRifResult = null,
                isDrTbConfirmed = false,
                reasonNotConductedRif = null,
                reasonNotConductedRifOther = null,
                errorMsgRif = null
            )
        }
    }

    /**
     * An MDR_RIF order from an older server (RIF as a separate order). Applied onto the RIF fields
     * only while the current TrueNat order is MTB Positive with no RIF result of its own, so an old
     * order never overrides a newer MTB order's RIF.
     */
    private fun mergeLegacyRifOrder(
        base: TBDiagnosticsCache,
        fetchedOrderId: String?,
        data: OrderResultData,
        perTest: PerTestResults,
        status: String
    ): TBDiagnosticsCache {
        if (base.rifManualResultPendingSync == true) return base
        val mtbPositive = MtbResult.fromResultText(base.naatResult) == MtbResult.TB_POSITIVE
        // rifOrderId == trueNatOrderId: the current MTB order carries its own RIF test (set by
        // mergeTrueNatOrder), whether or not that RIF result is in yet. An old MDR_RIF order in
        // the same feed batch must not fill the new order's waiting RIF with its old result.
        val currentOrderCarriesRif = base.rifOrderId != null && base.rifOrderId == base.trueNatOrderId
        if (!mtbPositive || currentOrderCarriesRif) return base
        val mappedStatus = reducedOrderStatus(status)
        val summary = perTest.rif?.resultSummary?.takeIf { it.isNotBlank() }
        val rifResult = RifResult.fromResultText(summary)
        return base.copy(
            rifOrderId = fetchedOrderId ?: base.rifOrderId,
            rifOrderStatus = mappedStatus,
            trueNatRifResult = if (mappedStatus == OrderStatus.COMPLETED.name) rifResult?.displayValue ?: summary else base.trueNatRifResult,
            isDrTbConfirmed = if (rifResult != null) rifResult == RifResult.DR_TB else base.isDrTbConfirmed,
            errorMsgRif = if (mappedStatus == OrderStatus.FAILED.name) data.errorMessage ?: base.errorMsgRif else base.errorMsgRif,
            syncState = SyncState.UNSYNCED
        )
    }

    /** The vendor's code for an invalid TrueNat run (MTB or RIF); the machine retests the order. */
    private fun isMachineRunError(summary: String): Boolean = summary.trim().equals("Error-2", ignoreCase = true)

    /** "Other: text" (how the form sends a free-text reason) -> ("Other", "text"); else (reason, null). */
    internal fun splitOtherReason(reason: String?): Pair<String, String?>? {
        val value = reason?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return if (value.startsWith("Other:", ignoreCase = true)) {
            "Other" to value.substringAfter(':').trim().ifEmpty { null }
        } else value to null
    }

    /**
     * @param pushSuspectedAfter when true (the default, and what every single-beneficiary caller
     *   wants) this pushes the unsynced TB Suspected rows once the result has been stored.
     *   Batch callers pass false and push once after the whole batch instead: that push reads
     *   every UNSYNCED row and POSTs them, so doing it per beneficiary turned one sweep into
     *   hundreds of identical `tb/suspected/saveAll` calls - 125 of them against 552 result
     *   fetches in one measured capture - each writing back to TB_SUSPECTED and so invalidating
     *   every list the open screen is collecting.
     */
    suspend fun fetchOrderResult(
        benId: Long,
        orderType: String,
        pushSuspectedAfter: Boolean = true
    ): NetworkResponse<String> {
        return withContext(Dispatchers.IO) {
            val ben = benDao.getBen(benId)
                ?: return@withContext NetworkResponse.Error(context.getString(R.string.error_beneficiary_not_found))
            val targetBenId = ben.beneficiaryId
            if (targetBenId <= 0) {
                return@withContext NetworkResponse.Error(context.getString(R.string.error_beneficiary_id_invalid))
            }
            val isXrayType = orderType.equals("XRAY_CHEST", ignoreCase = true)
            val isRifType = orderType.equals("MDR_RIF", ignoreCase = true)
            // Captured before the network round trip below — this call is triggered by an
            // independent poll worker, decoupled from any concurrent reorder (e.g. createOrder()
            // on Invalid/Error). If a newer order gets created for this test type while this
            // call's own GET is in flight, the fresh order id/status this response carries would
            // still look plausible on their own, but this response is for a now-superseded order —
            // see the check before the final commit below.
            val orderIdAtStart = tbDao.getTbDiagnosticsByBenId(benId)?.let {
                if (isXrayType) it.xrayOrderId else if (isRifType) it.rifOrderId else it.trueNatOrderId
            }
            try {
                val apiOrderType = if (orderType.equals("SPUTUM_TRUENAT", ignoreCase = true)) "MTB" else orderType
                val response = tmcNetworkApiService.fetchOrderResult(benId = ben.beneficiaryId, orderType = apiOrderType)
                val statusCode = response.code()
                if (statusCode == 200) {
                    val responseBody = response.body()
                    if (responseBody != null  && response.isSuccessful) {
                        val resStatusCode = responseBody.statusCode
                        if (resStatusCode == 200) {
                            val responseData = responseBody.data
                            val status = applyOrderResult(ben, orderType, responseData, orderIdAtStart, pushSuspectedAfter)
                            return@withContext NetworkResponse.Success(status)
                        } else {
                            val errorMsg = responseBody.errorMessage ?: context.getString(R.string.error_fetch_result_failed)
                            return@withContext NetworkResponse.Error(errorMsg)
                        }
                    }
                }
                NetworkResponse.Error(context.getString(R.string.error_http_code, statusCode))
            } catch (e: CancellationException) {
                // Leaving the X-ray/TrueNat list cancels every poll this sweep still has queued.
                // Catching that as a plain failure logged one full stack trace per cancelled call
                // - 2,664 of them in one measured back-press - and swallowed the cancellation
                // instead of letting it propagate, which is what structured concurrency needs.
                throw e
            } catch (e: Exception) {
                Timber.e(e, "fetchOrderResult failed")
                NetworkResponse.Error(e.message ?: "Unknown error")
            }
        }
    }

    internal data class MergedOrderResult(val cache: TBDiagnosticsCache, val triggerTrueNat: Boolean)
}
