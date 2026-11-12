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
import org.piramalswasthya.stoptb.helpers.Konstants
import org.piramalswasthya.stoptb.helpers.NetworkResponse
import org.piramalswasthya.stoptb.helpers.manualResultTextToStore
import org.piramalswasthya.stoptb.helpers.serverResultOrNull
import org.piramalswasthya.stoptb.model.BenRegCache
import org.piramalswasthya.stoptb.model.ChestXrayResult
import org.piramalswasthya.stoptb.model.ChiefComplaintMasterCache
import org.piramalswasthya.stoptb.model.GeneralOpdCache
import org.piramalswasthya.stoptb.model.GeneralOpdPrescription
import org.piramalswasthya.stoptb.model.MtbResult
import org.piramalswasthya.stoptb.model.OpdDrugMasters
import org.piramalswasthya.stoptb.model.OpdMedicineDraft
import org.piramalswasthya.stoptb.model.OrderResultData
import org.piramalswasthya.stoptb.model.OrderStatus
import org.piramalswasthya.stoptb.model.RifResult
import org.piramalswasthya.stoptb.model.TBConfirmedTreatmentCache
import org.piramalswasthya.stoptb.model.TBDiagnosticsCache
import org.piramalswasthya.stoptb.model.TBScreeningCache
import org.piramalswasthya.stoptb.model.TBSuspectedCache
import org.piramalswasthya.stoptb.model.TbScreeningSymptoms
import org.piramalswasthya.stoptb.model.VisitCategoryMasterCache
import org.piramalswasthya.stoptb.network.AmritApiService
import org.piramalswasthya.stoptb.network.DiagnosticBeneficiaryStatusData
import org.piramalswasthya.stoptb.network.DiagnosticManualResultRequest
import org.piramalswasthya.stoptb.network.DiagnosticOrderPushRequest
import org.piramalswasthya.stoptb.network.GeneralOpdDrugResponse
import org.piramalswasthya.stoptb.network.GeneralOpdRequestDTO
import org.piramalswasthya.stoptb.network.GeneralOpdSaveRequest
import org.piramalswasthya.stoptb.network.GetDataPaginatedRequest
import org.piramalswasthya.stoptb.network.PatientRequest
import org.piramalswasthya.stoptb.network.StopTbVillageRequest
import org.piramalswasthya.stoptb.network.TBConfirmedRequestDTO
import org.piramalswasthya.stoptb.network.TBDiagnosticsRequestDTO
import org.piramalswasthya.stoptb.network.TBDiagnosticsSaveRequest
import org.piramalswasthya.stoptb.network.TBScreeningRequestDTO
import org.piramalswasthya.stoptb.network.TBScreeningSaveRequest
import org.piramalswasthya.stoptb.network.TBSuspectedRequestDTO
import org.piramalswasthya.stoptb.repositories.*
import org.piramalswasthya.stoptb.utils.ServerDateParser
import org.piramalswasthya.stoptb.utils.throttleLatest
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
    companion object {
        // How TBSuspectedQuickViewModel joins an "Other" choice and its text into reasonToClose.
        private const val OTHER_REASON = "Other"
        private const val OTHER_REASON_PREFIX = "Other: "

        // English values of R.array.tb_reason_not_conducted_naat (the stored form). A TrueNat
        // closure reason outside this list came from the sputum-denial question.
        private val NAAT_NOT_CONDUCTED_REASONS = setOf(
            "Machine unavailable", "Cartridge unavailable", "Sample rejected", "Patient refused",
            "Connectivity issue", "Technical issue", "Patient absent", "Excessive waiting time", OTHER_REASON
        )

        /**
         * How many "completed" beneficiaries one sweep of [fetchBeneficiariesByStatus] fans out
         * at a time. Sized to keep OkHttp's 5 per-host slots saturated with a little slack for
         * the Room reads each one does first, without holding thousands of coroutines alive.
         */
        const val completedFetchChunkSize = 16

        /**
         * Chunk size for `IN (:ids)` reads. SQLite's default host-parameter ceiling is 999, so
         * this stays clear of it while still collapsing a whole bucket into a handful of reads.
         */
        const val sqliteInClauseChunkSize = 900

        /**
         * How many missing order ids one sweep of [fetchBeneficiariesByStatus] backfills.
         *
         * Each backfill is a network round trip plus a TB_DIAGNOSTICS write, and every one of
         * those writes invalidates the Paging source and the diagnostics flow of whichever
         * screen triggered the sweep. Unbounded, a single open of the X-ray list fired 552
         * result fetches and the list never settled - it was re-created five times in 28 s.
         * The backlog drains across sweeps instead.
         */
        const val orderIdBackfillPerSweep = 50

        /**
         * Per orderType, the chunk index the previous backfill pass stopped at, so the next
         * sweep resumes there rather than re-scanning the same already-filled prefix.
         */
        val backfillChunkCursor = java.util.concurrent.ConcurrentHashMap<String, Int>()

        /**
         * Per orderType, a content signature of the awaiting bucket for which a complete pass
         * found nothing left to backfill. While the bucket is unchanged the scan is skipped.
         *
         * Keyed on content, not size: one beneficiary entering the bucket as another leaves
         * keeps the size identical, and a size-keyed marker would hide the newcomer's missing
         * order id for as long as the count happened to hold steady.
         */
        val backfillConvergedForBucket =
        java.util.concurrent.ConcurrentHashMap<String, Int>()

        /**
         * Clears the backfill cursor and converged markers. Both live here, so without this
         * they would survive a logout and be applied to the next user's village.
         */
        fun resetOrderIdBackfillState() {
        backfillChunkCursor.clear()
        backfillConvergedForBucket.clear()
        }
    }

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
            else -> OrderStatus.PENDING.name // PENDING, IN_PROGRESS, or anything unrecognized
        }
    }

    suspend fun createOrder(
        benId: Long,
        testType: String,
        reasonForRefusal: String? = null
    ): NetworkResponse<String> {
        return withContext(Dispatchers.IO) {
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
                    reasonToClose = reasonForRefusal,
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
                            
                            val cache = benIdLocks.withLock(benId) {
                            val existing = tbDao.getTbDiagnosticsByBenId(benId)
                            val cache = (existing ?: TBDiagnosticsCache(benId = benId)).let {
                                if (testType.equals("XRAY_CHEST", ignoreCase = true)) {
                                    it.copy(
                                        xrayOrderId = orderId,
                                        xrayOrderStatus = if (status.equals(OrderStatus.FAILED.name, ignoreCase = true))
                                            OrderStatus.FAILED.name else OrderStatus.PENDING.name,
                                        isChestXRayDone = true,
                                        isReferredForDigitalChestXray = true,
                                        chestXRayResult = null,
                                        reasonNotConductedChestXray = null,
                                        reasonNotConductedChestXrayOther = null,
                                        syncState = SyncState.UNSYNCED,
                                        errorMsgXray = responseBody.data.errorMessage
                                    )
                                } else if (testType.equals("MDR_RIF", ignoreCase = true)) {
                                    it.copy(
                                        rifOrderId = orderId,
                                        rifOrderStatus = if (status.equals(OrderStatus.FAILED.name, ignoreCase = true))
                                            OrderStatus.FAILED.name else OrderStatus.PENDING.name,
                                        trueNatRifResult = null,
                                        isDrTbConfirmed = null,
                                        reasonNotConductedRif = null,
                                        reasonNotConductedRifOther = null,
                                        syncState = SyncState.UNSYNCED,
                                        errorMsgRif = responseBody.data.errorMessage
                                    )
                                } else {
                                    // A fresh MTB order invalidates any RIF order/result tied to
                                    // the previous MTB result (RIF only ever exists off the back
                                    // of an MTB-positive result) — reset it here too.
                                    it.copy(
                                        trueNatOrderId = orderId,
                                        trueNatOrderStatus = if (status.equals(OrderStatus.FAILED.name, ignoreCase = true))
                                            OrderStatus.FAILED.name else OrderStatus.PENDING.name,
                                        naatResult = null,
                                        trueNatRifResult = null,
                                        isReferredForSputum = true,
                                        isNaatConducted = true,
                                        reasonNotConductedNaat = null,
                                        reasonNotConductedNaatOther = null,
                                        rifOrderId = null,
                                        rifOrderStatus = null,
                                        isDrTbConfirmed = null,
                                        sputumSubmittedAt = it.sputumSubmittedAt ?: "TB Screening Camp",
                                        syncState = SyncState.UNSYNCED,
                                        errorMsgTrueNat = responseBody.data.errorMessage
                                    )
                                }
                            }
                            tbDao.saveTbDiagnostics(cache)
                            orderCreatedTimestamps["${benId}_${testType}"] = System.currentTimeMillis()
                            cache
                            }
                            syncTBSuspectedFromDiagnostics(
                                benId, cache,
                                reasonForRefusalMDRRIF = if (testType.equals("MDR_RIF", ignoreCase = true)) reasonForRefusal else null
                            )
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
        val mappedSputumTestResult = when {
            !naatResLocal.isNullOrBlank() -> {
                val clean = naatResLocal.trim().lowercase()
                when {
                    clean.contains("not detected") || clean.contains("negative") -> "TB Negative"
                    clean.contains("detected") || clean.contains("positive") -> "TB Positive"
                    else -> null
                }
            }
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

        val mappedMdrRifResult = when {
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
            else -> diag.trueNatRifResult ?: existing?.mdrRifResult
        }

        val mappedIsDRTBConfirmed = when (mappedMdrRifResult) {
            "DR TB" -> true
            "Non DR TB", "Indeterminate" -> false
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
            naatResult = diag.naatResult ?: existing?.naatResult,
            reasonForRefusalXray = diag.reasonNotConductedChestXray ?: existing?.reasonForRefusalXray,
            reasonForRefusalSputum = diag.reasonForDenialSputum ?: existing?.reasonForRefusalSputum,
            reasonForRefusalMTB = diag.reasonNotConductedNaat ?: existing?.reasonForRefusalMTB,
            reasonForRefusalMDRRIF = reasonForRefusalMDRRIF ?: existing?.reasonForRefusalMDRRIF,
            mdrRifResult = mappedMdrRifResult,
            isDRTBConfirmed = mappedIsDRTBConfirmed,
            isTBConfirmed = mappedSputumTestResult == "TB Positive",
            isConfirmed = mappedSputumTestResult == "TB Positive",
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

    // Returns true when a failed order has no error message and requires reconciliation.
    suspend fun isAwaitingReconciliation(benId: Long, orderType: String, errorMsg: String?): Boolean {
        if (!errorMsg.isNullOrBlank()) return false
        reconcileOrderResult(benId, orderType)
        return true
    }

    // Rechecks the order result and updates the diagnostic record.
    private suspend fun reconcileOrderResult(benId: Long, orderType: String) {
        try {
            if (tbDao.getTbDiagnosticsByBenId(benId) == null) return
            val response = tmcNetworkApiService.fetchOrderResult(
                benId = benId,
                orderType = orderType
            )
            if (response.code() != 200) return

            val responseData = response.body()?.takeIf { it.statusCode == 200 }?.data
                ?: return

            // Network call outside the lock; read-modify-write inside it on a fresh row, like every
            // other TB_DIAGNOSTICS update here - a row read before the call could revert a write
            // that landed during it (a result, a new order, a feed apply) and re-push the old state.
            benIdLocks.withLock(benId) {
            val existing = tbDao.getTbDiagnosticsByBenId(benId) ?: return@withLock
            val cache = when {
                orderType.equals("XRAY_CHEST", ignoreCase = true) ->
                    existing.copy(
                        errorMsgXray = responseData.errorMessage,
                        syncState = SyncState.UNSYNCED
                    )

                orderType.equals("MDR_RIF", ignoreCase = true) ->
                    existing.copy(
                        errorMsgRif = responseData.errorMessage,
                        syncState = SyncState.UNSYNCED
                    )

                // Callers pass the server's order type, which is MTB for TrueNat.
                orderType.equals("SPUTUM_TRUENAT", ignoreCase = true) ||
                        orderType.equals("MTB", ignoreCase = true) ->
                    existing.copy(
                        errorMsgTrueNat = responseData.errorMessage,
                        syncState = SyncState.UNSYNCED
                    )

                else -> return@withLock
            }

            // Only write (and re-mark for push) when the error message actually changed. While
            // the server's errorMessage stays null this ran on every status sweep, rewriting the
            // row and re-queueing the same push each time.
            if (cache.copy(syncState = existing.syncState) != existing) {
                tbDao.saveTbDiagnostics(cache)
            }
            }
        } catch (e: Exception) {
            Timber.e(e, "giveUpReconciliation failed")
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
                            
                            if (orderType.equals("XRAY_CHEST", ignoreCase = true)) {
                                // Immediately fetch order result from server for X-Ray
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
     * Applies one order's result to TB_DIAGNOSTICS: the result mapping per test type, the MTB
     * positive -> RIF cascade, and the TB Suspected sync. Used by [fetchOrderResult] after its
     * network call and by the changes feed with the order rows it delivers. Returns the server
     * status. [rifOrderKnown] skips the RIF cascade when the caller already holds an MDR_RIF order
     * for this beneficiary (the feed applies it from the same batch).
     */
    internal suspend fun applyOrderResult(
        ben: BenRegCache,
        orderType: String,
        responseData: OrderResultData,
        orderIdAtStart: String?,
        pushSuspectedAfter: Boolean,
        rifOrderKnown: Boolean = false
    ): String {
        val benId = ben.beneficiaryId
        val isXrayType = orderType.equals("XRAY_CHEST", ignoreCase = true)
        val isRifType = orderType.equals("MDR_RIF", ignoreCase = true)
        val rawStatus = responseData.status
        val status = if (rawStatus.isNullOrBlank()) "IN_PROGRESS" else rawStatus
       Timber.d("STOP-TB polling debug: fetchOrderResult benId=$benId status=$status rawStatus=$rawStatus")

        val isCompleted = status.equals(OrderStatus.COMPLETED.name, ignoreCase = true)

        // MTB-positive -> RIF cascade must run to completion BEFORE this
        // function takes its own per-benId lock below — createOrder()/
        // fetchBeneficiariesByStatus() for the RIF cascade lock the SAME
        // benId internally (kotlinx.coroutines Mutex is not reentrant), so
        // holding our own lock across this call would deadlock. rifFieldOverride
        // stays null unless this cascade actually determined a fresh RIF
        // status/id to persist; null means "leave whatever is currently in the
        // row untouched" in the locked commit below (never re-apply a stale
        // pre-cascade snapshot over it).
        val rifFieldOverride = mtbRifCascade(ben, orderType, responseData, isCompleted, rifOrderKnown)

        // Final commit: re-reads the row fresh, INSIDE the per-benId lock,
        // right before writing — this is what actually fixes the lost-update
        // race (Gaps 1/2), since any concurrent write for a different test
        // type on the same beneficiary (or the RIF cascade above, which
        // already committed its own fields) is guaranteed to be visible here
        // rather than clobbered by a stale pre-cascade snapshot.
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
        val merged = mergeOrderResult(existing, benId, orderType, responseData, status, rifFieldOverride, deviceIntegrationRepo.isTruenatIntegrated())
        // Enqueued before the row is saved: WorkManager keeps the work across a restart, so the
        // referral survives the process dying right after the completed X-ray is stored.
        if (merged.triggerTrueNat) {
            WorkerUtils.triggerDiagnosticOrderPushWorkers(context, benId, listOf("SPUTUM_TRUENAT"))
        }
        val synced = merged.cache.copy(syncState = SyncState.SYNCED)
        // Skip the write when nothing changed: every TB_DIAGNOSTICS write re-runs every live
        // query on the table (list badges, dashboard), and the fallback poll re-applies the same
        // unchanged result for every pending order each cycle. The feed path already does this.
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

    // MTB-positive -> RIF cascade: must run to completion BEFORE the caller
    // takes its own per-benId lock — createOrder()/fetchBeneficiariesByStatus()
    // for the RIF cascade lock the SAME benId internally (kotlinx.coroutines
    // Mutex is not reentrant), so holding the lock across this call would
    // deadlock. Returns null unless this cascade actually determined a fresh RIF
    // status/id to persist; null means "leave whatever is currently in the row
    // untouched" in the locked commit (never re-apply a stale pre-cascade
    // snapshot over it).
    private suspend fun mtbRifCascade(
        ben: BenRegCache,
        orderType: String,
        responseData: OrderResultData,
        isCompleted: Boolean,
        rifOrderKnown: Boolean
    ): Pair<String, String?>? {
        val benId = ben.beneficiaryId
        var rifFieldOverride: Pair<String, String?>? = null
        if (!orderType.equals("XRAY_CHEST", ignoreCase = true) &&
            !orderType.equals("MDR_RIF", ignoreCase = true)) {
            val mtbResultForCascade = if (isCompleted) MtbResult.fromResultText(responseData.resultSummary ?: "") else null
            val isMtbDetectedForCascade = isCompleted && mtbResultForCascade == MtbResult.TB_POSITIVE
            if (isMtbDetectedForCascade) {
                val precheck = tbDao.getTbDiagnosticsByBenId(benId)
                val hasExistingRifOrder = !precheck?.rifOrderId.isNullOrBlank() ||
                        precheck?.rifOrderStatus.equals(OrderStatus.COMPLETED.name, ignoreCase = true) ||
                        precheck?.rifOrderStatus.equals(OrderStatus.PENDING.name, ignoreCase = true) ||
                        precheck?.rifOrderStatus.equals(OrderStatus.CLOSED.name, ignoreCase = true)
                if (!hasExistingRifOrder && !rifOrderKnown) {
                    // MTB Positive always creates a RIF order — unchanged
                    // cascade shape, just recast onto the new status
                    // vocabulary below.
                    val statusResponse = fetchBeneficiariesByStatus("MDR_RIF")
                    var serverHasOrder = false
                    if (statusResponse is NetworkResponse.Success) {
                        val statusData = statusResponse.data
                        val awaitingProviderResult = statusData?.awaitingProviderResult ?: emptyList()
                        val completedList = statusData?.completed ?: emptyList()
                        val regId = if (ben.benRegId > 0) ben.benRegId else ben.beneficiaryId
                        if (awaitingProviderResult.contains(regId)) {
                            serverHasOrder = true
                            rifFieldOverride = OrderStatus.PENDING.name to "EXISTING-RIF-${regId}"
                            WorkerUtils.triggerRifDiagnosticResultPollWorker(context)
                        } else if (completedList.contains(regId)) {
                            serverHasOrder = true
                            rifFieldOverride = OrderStatus.COMPLETED.name to "EXISTING-RIF-${regId}"
                        }
                    }
                    if (!serverHasOrder) {
                        val maxRifRetries = 1
                        var rifAttempt = 0
                        var rifSuccess = false
                        while (rifAttempt <= maxRifRetries && !rifSuccess) {
                            try {
                                val newOrderId = createOrder(benId, "MDR_RIF")
                                if (newOrderId is NetworkResponse.Success) {
                                    rifFieldOverride = OrderStatus.PENDING.name to newOrderId.data
                                    rifSuccess = true
                                    WorkerUtils.triggerRifDiagnosticResultPollWorker(context)
                                } else {
                                    rifAttempt++
                                    if (rifAttempt <= maxRifRetries) {
                                        kotlinx.coroutines.delay(5000L)
                                    }
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Auto createOrder for MDR_RIF failed, attempt=${rifAttempt}")
                                rifAttempt++
                                if (rifAttempt <= maxRifRetries) {
                                    kotlinx.coroutines.delay(5000L)
                                }
                            }
                        }
                        if (!rifSuccess) {
                            rifFieldOverride = OrderStatus.FAILED.name to null
                        }
                    }
                }
            }
        }

        return rifFieldOverride
    }

    /** Whether a feed result for [orderType] would start the MTB -> RIF cascade. */
    @androidx.annotation.VisibleForTesting
    internal fun needsRifCascade(orderType: String, responseData: OrderResultData, rifOrderKnown: Boolean): Boolean {
        if (rifOrderKnown) return false
        if (orderType.equals("XRAY_CHEST", ignoreCase = true) || orderType.equals("MDR_RIF", ignoreCase = true)) return false
        val status = responseData.status
        if (!status.equals(OrderStatus.COMPLETED.name, ignoreCase = true)) return false
        return MtbResult.fromResultText(responseData.resultSummary ?: "") == MtbResult.TB_POSITIVE
    }

    /**
     * The diagnostics row after applying one order result to [existing] (null = no row yet).
     * Pure: no database or network. [triggerTrueNat] asks the caller to start the X-ray ->
     * TrueNat referral push once the row is saved.
     */
    @androidx.annotation.VisibleForTesting
    internal fun mergeOrderResult(
        existing: TBDiagnosticsCache?,
        benId: Long,
        orderType: String,
        responseData: OrderResultData,
        status: String,
        rifFieldOverride: Pair<String, String?>?,
        truenatIntegrated: Boolean
    ): MergedOrderResult {
        val fetchedOrderId = responseData.externalOrderId.asValidOrderId()
        val isCompleted = status.equals(OrderStatus.COMPLETED.name, ignoreCase = true)
        var triggerTrueNat = false
        val cache = (existing ?: TBDiagnosticsCache(benId = benId)).let {
            if (orderType.equals("XRAY_CHEST", ignoreCase = true)) {
                // Chest X-Ray order lifecycle redesign: standardized 4-way
                // result mapping (replaces the old exact-string
                // isChestXrayPositive/isChestXrayAbnormalNonTB checks) and
                // the reduced 5-value status vocabulary.
                val chestResult = responseData.resultSummary ?: ""
                val standardizedResult = if (isCompleted) ChestXrayResult.fromResultText(chestResult) else null

                when {
                    isCompleted && standardizedResult?.triggersTrueNatReferral == true -> {
                        // TB Presumptive/Abnormal — trigger SPUTUM_TRUENAT via the
                        // per-benId push-worker chain, not inline (an inline call
                        // here used to race the Screening Form's own TrueNat push).
                        // Leave trueNatOrderStatus/isReferredForSputum/isNaatConducted
                        // untouched — writing PENDING here would make the worker's
                        // own "does an order exist" check skip the push.
                        val hasTruenat = !it.trueNatOrderId.isNullOrBlank() ||
                                it.trueNatOrderStatus.equals(OrderStatus.COMPLETED.name, ignoreCase = true) ||
                                it.trueNatOrderStatus.equals(OrderStatus.PENDING.name, ignoreCase = true) ||
                                it.trueNatOrderStatus.equals(OrderStatus.CLOSED.name, ignoreCase = true)
                        if (!hasTruenat && truenatIntegrated) triggerTrueNat = true
                        it.copy(
                            xrayOrderId = fetchedOrderId ?: it.xrayOrderId,
                            xrayOrderStatus = OrderStatus.COMPLETED.name,
                            isReferredForDigitalChestXray = true,
                            isChestXRayDone = true,
                            chestXRayResult = standardizedResult?.displayValue ?: chestResult,
                            syncState = SyncState.UNSYNCED
                        )
                    }
                    isCompleted && standardizedResult == ChestXrayResult.AI_INVALID -> {
                        // Automated AI-Invalid: the backend creates the repeat
                        // order itself and doesn't return its id in this
                        // response. Don't call createOrder and don't mark this
                        // Closed — leave the row as-is so the poll worker keeps
                        // sweeping this beneficiary until a later poll surfaces
                        // the backend-created replacement order's new
                        // xrayOrderId/PENDING status.
                        it
                    }
                    isCompleted -> {
                        // Normal (or an unrecognized-but-completed summary) —
                        // terminal, no cascade. Canonicalize for the same reason
                        // as the referral branch above.
                        it.copy(
                            xrayOrderId = fetchedOrderId ?: it.xrayOrderId,
                            xrayOrderStatus = OrderStatus.COMPLETED.name,
                            isReferredForDigitalChestXray = true,
                            isChestXRayDone = true,
                            chestXRayResult = standardizedResult?.displayValue ?: chestResult,
                            syncState = SyncState.UNSYNCED
                        )
                    }
                    else -> {
                        // Not completed yet — reuse the same real-contract
                        // mapping as reducedOrderStatus() (order/result's
                        // confirmed status values: PENDING, IN_PROGRESS,
                        // COMPLETED, FAILED, CLOSED, MANUAL_ENTRY).
                        val mappedStatus = reducedOrderStatus(status)
                        it.copy(
                            xrayOrderId = fetchedOrderId ?: it.xrayOrderId,
                            xrayOrderStatus = mappedStatus,
                            isReferredForDigitalChestXray = true,
                            errorMsgXray = if (mappedStatus == OrderStatus.FAILED.name)
                                responseData.errorMessage ?: it.errorMsgXray else it.errorMsgXray,
                            syncState = SyncState.UNSYNCED
                        )
                    }
                }
            } else if (orderType.equals("MDR_RIF", ignoreCase = true)) {
                // RIF order lifecycle redesign — standardized 4-way result
                // mapping (DR TB / Non DR TB / Indeterminate / Invalid-Error)
                // replacing the old free-text "Indeterminate" check, PLUS an
                // intentionally INVERTED behavior from before: Indeterminate
                // is now terminal (Completed) instead of auto-repeating the
                // order — the auto-reorder-on-Indeterminate block that used to
                // live here has been removed entirely. Invalid/Error is the
                // new repeat-trigger instead (previously had no handling).
                val serverRifResultSummary = responseData.resultSummary
                val rifResult = serverRifResultSummary ?: ""
                val standardizedRifResult = if (isCompleted) RifResult.fromResultText(rifResult) else null

                when {
                    isCompleted && standardizedRifResult == RifResult.INVALID_ERROR -> {
                        // Automated Invalid/Error: the backend creates the
                        // repeat order itself and doesn't return its id in
                        // this response. Don't call createOrder and don't mark
                        // this Closed — leave the row as-is so the poll worker
                        // keeps sweeping this beneficiary until a later poll
                        // surfaces the backend-created replacement order's new
                        // rifOrderId/PENDING status. Mirrors Chest X-Ray's
                        // automated AI-Invalid handling exactly.
                        it
                    }
                    isCompleted -> {
                        // DR TB / Non DR TB / Indeterminate — all terminal now.
                        it.copy(
                            rifOrderId = fetchedOrderId ?: it.rifOrderId,
                            rifOrderStatus = OrderStatus.COMPLETED.name,
                            trueNatRifResult = rifResult,
                            isDrTbConfirmed = if (standardizedRifResult == RifResult.DR_TB) true else it.isDrTbConfirmed,
                            isConfirmed = if (standardizedRifResult == RifResult.DR_TB || standardizedRifResult == RifResult.NON_DR_TB)
                                true else it.isConfirmed,
                            isTBConfirmed = if (standardizedRifResult == RifResult.DR_TB || standardizedRifResult == RifResult.NON_DR_TB)
                                true else it.isTBConfirmed,
                            syncState = SyncState.UNSYNCED
                        )
                    }
                    else -> {
                        val mappedStatus = reducedOrderStatus(status)
                        it.copy(
                            rifOrderId = fetchedOrderId ?: it.rifOrderId,
                            rifOrderStatus = mappedStatus,
                            errorMsgRif = if (mappedStatus == OrderStatus.FAILED.name)
                                responseData.errorMessage ?: it.errorMsgRif else it.errorMsgRif,
                            syncState = SyncState.UNSYNCED
                        )
                    }
                }
            } else {
                val serverMtbResultSummary = responseData.resultSummary
                val mtbResult = serverMtbResultSummary ?: ""
                val standardizedMtbResult = if (isCompleted) MtbResult.fromResultText(mtbResult) else null

                if (isCompleted && standardizedMtbResult == MtbResult.INVALID_ERROR) {
                    // Automated Invalid/Error: same treatment as RIF's above
                    // (and Chest X-Ray's automated AI-Invalid) — the backend
                    // creates the repeat order itself, so don't call
                    // createOrder and don't mark this Closed/Completed; leave
                    // the row as-is for the poll worker to keep sweeping.
                    it
                } else {
                val isMtbDetected = isCompleted && standardizedMtbResult == MtbResult.TB_POSITIVE
                val computedTrueNatStatus = reducedOrderStatus(status)
                val computedTrueNatOrderId = fetchedOrderId ?: it.trueNatOrderId

                it.copy(
                    trueNatOrderStatus = computedTrueNatStatus,
                    trueNatOrderId = computedTrueNatOrderId ?: it.trueNatOrderId,
                    isReferredForSputum = true,
                    isNaatConducted = if (isCompleted) true else it.isNaatConducted,
                    naatResult = if (isCompleted) (serverMtbResultSummary ?: mtbResult) else it.naatResult,
                    isTBConfirmed = if (isCompleted) isMtbDetected else it.isTBConfirmed,
                    isConfirmed = if (isCompleted) isMtbDetected else it.isConfirmed,
                    // rifFieldOverride (computed above, before this lock was
                    // taken) is non-null only when the MTB->RIF cascade
                    // actually determined a fresh RIF status/id; otherwise
                    // preserve whatever this freshly-read row currently holds
                    // (e.g. a RIF result the poll worker already committed
                    // concurrently) rather than a stale pre-cascade value.
                    rifOrderStatus = rifFieldOverride?.first ?: it.rifOrderStatus,
                    rifOrderId = rifFieldOverride?.second ?: it.rifOrderId,
                    errorMsgTrueNat = if (computedTrueNatStatus == OrderStatus.FAILED.name)
                        responseData.errorMessage ?: it.errorMsgTrueNat else it.errorMsgTrueNat,
                    syncState = SyncState.UNSYNCED
                )
                }
            }
        }
        val closed = reducedOrderStatus(status) == OrderStatus.CLOSED.name
        return MergedOrderResult(if (closed) withCloseReason(cache, orderType, responseData.reasonToClose) else cache, triggerTrueNat)
    }

    /**
     * Copies the reason an order was closed without a result onto the row, so every tablet shows
     * why the test was not done. Before, only the tablet that entered "Not Conducted" had it; the
     * order closure reached the others with the status alone (UAT field test 2026-11-11, finding 5).
     *
     * [reasonToClose] is what that tablet sent: the selected English value, or "Other: <text>".
     * TrueNat is closed for two reasons that share one server field - test not conducted
     * (reasonNotConductedNaat) or sputum not collected (reasonForDenialSputum, pipe-separated) -
     * so the value decides: all of it from the not-conducted list means not conducted, anything
     * else means the sputum was not collected. A row that already holds the reason (the tablet
     * that entered it) is left as it is.
     */
    internal fun withCloseReason(cache: TBDiagnosticsCache, orderType: String, reasonToClose: String?): TBDiagnosticsCache {
        val reason = reasonToClose?.trim()?.takeIf { it.isNotEmpty() } ?: return cache
        val (value, other) = if (reason.startsWith(OTHER_REASON_PREFIX)) {
            OTHER_REASON to reason.removePrefix(OTHER_REASON_PREFIX).trim().ifEmpty { null }
        } else reason to null
        return when {
            orderType.equals("XRAY_CHEST", ignoreCase = true) ->
                if (cache.reasonNotConductedChestXray == value && cache.reasonNotConductedChestXrayOther == other) cache
                else cache.copy(isChestXRayDone = false, reasonNotConductedChestXray = value, reasonNotConductedChestXrayOther = other)
            orderType.equals("MDR_RIF", ignoreCase = true) ->
                if (cache.reasonNotConductedRif == value && cache.reasonNotConductedRifOther == other) cache
                else cache.copy(reasonNotConductedRif = value, reasonNotConductedRifOther = other)
            cache.reasonForDenialSputum == reason -> cache
            reason.split('|').all { part -> part.trim() in NAAT_NOT_CONDUCTED_REASONS } || value == OTHER_REASON ->
                if (cache.reasonNotConductedNaat == value && cache.reasonNotConductedNaatOther == other) cache
                else cache.copy(isNaatConducted = false, reasonNotConductedNaat = value, reasonNotConductedNaatOther = other)
            else -> cache.copy(isSputumCollected = false, reasonForDenialSputum = reason)
        }
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

    /**
     * The beneficiaries in the awaiting buckets that still have no local order id, capped at
     * [orderIdBackfillPerSweep].
     *
     * Resolving the *whole* bucket to find the next 50 is what made every referral-list open
     * slow. On a 19,110-entry bucket that is ~21 chunks x 2 large IN-clause reads = ~42 cursor
     * fills, queued on the same ~3 SQLite read connections as the Paging refresh the user is
     * waiting on: a no-op "SELECT 1" was measured waiting 2,872 ms behind it while the refresh
     * took 3,588 ms of a 4,068 ms screen open.
     *
     * Three things bound it now:
     *  - it stops as soon as the cap is reached, so the common case is one or two reads;
     *  - it resumes from where the previous sweep stopped ([backfillChunkCursor]) instead of
     *    re-scanning an already-backfilled prefix, so successive sweeps make progress;
     *  - once a full pass finds nothing, it short-circuits entirely until the bucket changes
     *    size ([backfillConvergedForBucketSize]), so a drained backlog costs no reads at all.
     *
     * The cursor and the converged marker are in-memory on purpose: losing them on process
     * death only costs one extra full pass.
     */
    private suspend fun collectBenIdsNeedingOrderId(
        awaitingRegIds: List<Long>,
        orderType: String,
        isXray: Boolean,
        isRif: Boolean
    ): List<Long> {
        if (awaitingRegIds.isEmpty()) {
            backfillConvergedForBucket.remove(orderType)
            return emptyList()
        }
        // Content signature, computed in memory - no DB reads - so a bucket whose membership
        // changed is always rescanned even when its size did not.
        val bucketSignature = awaitingRegIds.sorted().hashCode()
        if (backfillConvergedForBucket[orderType] == bucketSignature) {
            return emptyList()
        }

        val chunks = awaitingRegIds.chunked(sqliteInClauseChunkSize)
        val startChunk = (backfillChunkCursor[orderType] ?: 0).let { if (it in chunks.indices) it else 0 }
        val needsBackfill = mutableListOf<Long>()
        var chunksExamined = 0
        var index = startChunk

        while (chunksExamined < chunks.size && needsBackfill.size < orderIdBackfillPerSweep) {
            val regIdChunk = chunks[index]

            val mappings = benDao.getBenIdsByRegIds(regIdChunk)
            val benIds = mappings.mapTo(mutableListOf()) { mapping -> mapping.beneficiaryId }
            // Same fallback the per-record form had: an id that matches no benRegId may already
            // BE a local beneficiaryId.
            val matchedRegIds = mappings.mapTo(mutableSetOf()) { mapping -> mapping.benRegId }
            val unmatched = regIdChunk.filterNot { regId -> regId in matchedRegIds }
            if (unmatched.isNotEmpty()) {
                benIds += benDao.getExistingBenIds(unmatched)
            }

            val distinctBenIds = benIds.distinct()
            if (distinctBenIds.isNotEmpty()) {
                val withOrderId = when {
                    isXray -> tbDao.getBenIdsWithXrayOrderId(distinctBenIds)
                    isRif -> tbDao.getBenIdsWithRifOrderId(distinctBenIds)
                    else -> tbDao.getBenIdsWithTrueNatOrderId(distinctBenIds)
                }.toSet()
                needsBackfill += distinctBenIds.filterNot { benId -> benId in withOrderId }
            }

            index = (index + 1) % chunks.size
            chunksExamined++
        }

        backfillChunkCursor[orderType] = index
        val completedFullPass = chunksExamined == chunks.size
        if (completedFullPass && needsBackfill.isEmpty()) {
            backfillConvergedForBucket[orderType] = bucketSignature
            Timber.d("Order id backfill [$orderType]: full pass over ${awaitingRegIds.size} entries found nothing; skipping until the bucket changes")
        } else {
            backfillConvergedForBucket.remove(orderType)
        }

        val batch = needsBackfill.distinct().take(orderIdBackfillPerSweep)
        if (batch.isNotEmpty()) {
            Timber.d(
                "Order id backfill [$orderType]: filling ${batch.size} ids, " +
                        "examined $chunksExamined of ${chunks.size} chunks from chunk $startChunk"
            )
        }
        return batch
    }

    /**
     * Local beneficiary ids the server still lists as awaiting a provider result for this sweep's
     * order type. The status lists carry the server's beneficiary ids (benRegId).
     */
    suspend fun awaitingResultBenIds(status: NetworkResponse<DiagnosticBeneficiaryStatusData>): Set<Long> {
        val regIds = (status as? NetworkResponse.Success)?.data?.awaitingProviderResult.orEmpty()
        if (regIds.isEmpty()) return emptySet()
        return withContext(Dispatchers.IO) {
            regIds.distinct().chunked(sqliteInClauseChunkSize)
                .flatMap { chunk -> benDao.getBenIdsByRegIds(chunk) }
                .mapTo(HashSet()) { mapping -> mapping.beneficiaryId }
        }
    }

    suspend fun fetchBeneficiariesByStatus(orderType: String, fetchResult: Boolean = true): NetworkResponse<DiagnosticBeneficiaryStatusData> {
        return withContext(Dispatchers.IO) {
            val user = preferenceDao.getLoggedInUser()
                ?: return@withContext NetworkResponse.Error(context.getString(R.string.error_no_user_logged_in))
            val locationRecord = preferenceDao.getLocationRecord()
                ?: return@withContext NetworkResponse.Error(context.getString(R.string.error_no_location_record_found))

            val villageId = locationRecord.village.id
            val providerServiceMapId = user.serviceMapId

            val apiOrderType = if (orderType.equals("SPUTUM_TRUENAT", ignoreCase = true)) "MTB" else orderType

            try {
                val response = tmcNetworkApiService.getBeneficiariesByStatus(
                    orderType = apiOrderType,
                    villageId = villageId,
                    providerServiceMapId = providerServiceMapId
                )
                val statusCode = response.code()
                if (statusCode == 200) {
                    val responseString = response.body()?.string()
                    if (responseString != null) {
                        val jsonObj = JSONObject(responseString)
                        val success = jsonObj.optBoolean("success", true)
                        val resStatusCode = jsonObj.optInt("statusCode", 200)
                        if (success && (resStatusCode == 200 || resStatusCode == 0)) {
                            // Real, confirmed `getBeneficiariesByStatus` contract: exactly 5
                            // buckets — awaitingProviderResult, completed, failed, closed,
                            // awaitingManualEntry. No awaitingTestCompletion/pollingTimedOut/
                            // refused buckets exist server-side; those were guessed pre-contract
                            // and are removed.
                            val dataObj = jsonObj.optJSONObject("data")
                            val awaitingProvResList = mutableListOf<Long>()
                            val completedList = mutableListOf<Long>()
                            val closedList = mutableListOf<Long>()
                            val failedList = mutableListOf<Long>()
                            val awaitingManualEntryList = mutableListOf<Long>()

                            dataObj?.optJSONArray("awaitingProviderResult")?.let { arr ->
                                for (i in 0 until arr.length()) {
                                    awaitingProvResList.add(arr.getLong(i))
                                }
                            }
                            dataObj?.optJSONArray("completed")?.let { arr ->
                                for (i in 0 until arr.length()) {
                                    completedList.add(arr.getLong(i))
                                }
                            }
                            dataObj?.optJSONArray("closed")?.let { arr ->
                                for (i in 0 until arr.length()) {
                                    closedList.add(arr.getLong(i))
                                }
                            }
                            dataObj?.optJSONArray("failed")?.let { arr ->
                                // Read TB_DIAGNOSTICS once and index it, instead of running
                                // "SELECT * FROM TB_DIAGNOSTICS" inside the loop and scanning the
                                // whole materialised list for every failed order.
                                // The bucket carries benRegIds. Resolve them to local
                                // beneficiaryIds and index the diagnostics by benId.
                                //
                                // This used to match `it.id.toLong() == arr.getLong(i)`, i.e. it
                                // compared a benRegId against the TB_DIAGNOSTICS autogenerated
                                // row id. A match was coincidental and returned another
                                // beneficiary's error message; the same unresolved value was then
                                // passed as `benId` into isAwaitingReconciliation, so
                                // reconcileOrderResult could read and overwrite a different
                                // beneficiary's diagnostics row.
                                val failedRegIds = (0 until arr.length()).map { arr.getLong(it) }
                                val failedBenIdByRegId = mutableMapOf<Long, Long>()
                                failedRegIds.chunked(sqliteInClauseChunkSize).forEach { chunk ->
                                    benDao.getBenIdsByRegIds(chunk).forEach { mapping ->
                                        failedBenIdByRegId[mapping.benRegId] = mapping.beneficiaryId
                                    }
                                    val unmatched = chunk.filterNot { it in failedBenIdByRegId }
                                    // A value that matches no benRegId may already BE a local id.
                                    benDao.getExistingBenIds(unmatched).forEach { benId ->
                                        failedBenIdByRegId[benId] = benId
                                    }
                                }
                                val diagnosticsByBenId = if (failedRegIds.isNotEmpty()) {
                                    // Only the failed beneficiaries' rows, not the whole table.
                                    failedBenIdByRegId.values.distinct()
                                        .chunked(sqliteInClauseChunkSize)
                                        .flatMap { benIds -> tbDao.getDiagnosticsForBenIds(benIds) }
                                        .associateBy { it.benId }
                                } else {
                                    emptyMap()
                                }
                                for (regId in failedRegIds) {
                                    val benId = failedBenIdByRegId[regId]
                                    if (benId == null) {
                                        Timber.w("Failed-order reconciliation: no local beneficiary for benRegId=$regId, skipping")
                                        failedList.add(regId)
                                        continue
                                    }
                                    isAwaitingReconciliation(
                                        benId,
                                        apiOrderType,
                                        diagnosticsByBenId[benId]
                                            ?.let { bens ->
                                                when {
                                                    orderType.equals("XRAY_CHEST", ignoreCase = true) ->
                                                        bens.errorMsgXray

                                                    orderType.equals("MDR_RIF", ignoreCase = true) ->
                                                        bens.errorMsgRif

                                                    orderType.equals("MTB", ignoreCase = true) ->
                                                        bens.errorMsgTrueNat

                                                    else -> null
                                                }
                                            }
                                    )
                                    failedList.add(regId)
                                }
                            }
                            dataObj?.optJSONArray("awaitingManualEntry")?.let { arr ->
                                for (i in 0 until arr.length()) {
                                    awaitingManualEntryList.add(arr.getLong(i))
                                }
                            }

                            val isXray = orderType.equals("XRAY_CHEST", ignoreCase = true)
                            val isRif = orderType.equals("MDR_RIF", ignoreCase = true)

                            // 0. Backfill order ids for the two "order exists, no result yet"
                            // buckets. getBeneficiariesByStatus returns benRegIds only - it never
                            // carries an order id - so a beneficiary referred somewhere else (the
                            // registrar tab, say) arrives here with a real backend order, gets a
                            // status of PENDING or MANUAL_ENTRY written below, and still has no
                            // local order id. The card reads a missing id as "the referral never
                            // went through" and offers Retry Referral, which would raise a second
                            // order for one that already succeeded.
                            //
                            // fetchOrderResult is the only call that returns externalOrderId, so
                            // pull it once per beneficiary that lacks one. Three things keep the
                            // cost off the screen that triggered this: the "who is missing an id"
                            // question is answered in bulk rather than per beneficiary; the
                            // fetches themselves are capped per sweep; and they are chunked like
                            // the completed bucket below, because one coroutine per entry is what
                            // caused the 2,664-job cancellation stall documented there.
                            //
                            // It must run OUTSIDE withBenIdLock - fetchOrderResult takes the same
                            // per-benId lock itself and kotlinx Mutex is not reentrant.
                            if (fetchResult) {
                                val awaitingRegIds =
                                    (awaitingProvResList + awaitingManualEntryList).distinct()
                                val backfillBatch = collectBenIdsNeedingOrderId(
                                    awaitingRegIds = awaitingRegIds,
                                    orderType = orderType,
                                    isXray = isXray,
                                    isRif = isRif
                                )
                                backfillBatch.chunked(completedFetchChunkSize).forEach { benIdChunk ->
                                    coroutineScope {
                                        benIdChunk.forEach { benId ->
                                            // pushSuspectedAfter = false: one push after the whole
                                            // batch below, not one per beneficiary.
                                            launch { fetchOrderResult(benId, orderType, pushSuspectedAfter = false) }
                                        }
                                    }
                                }
                                if (backfillBatch.isNotEmpty()) {
                                    try {
                                        tbPushRepo.pushUnSyncedRecordsTBSuspected()
                                    } catch (e: Exception) {
                                        Timber.e(e, "Failed to push TB Suspected after order id backfill")
                                    }
                                }
                            }

                            // 1. Awaiting Provider Result — the only real "still in progress"
                            // bucket (no separate "awaiting test completion" bucket exists).
                            for (regId in awaitingProvResList) {
                                val ben = benDao.getBenByRegId(regId) ?: benDao.getBen(regId)
                                ben?.let { b ->
                                    benIdLocks.withLock(b.beneficiaryId) {
                                        // Guard, status check, and write all read the SAME fresh
                                        // snapshot taken inside this lock — previously the guard
                                        // read happened before the lock, so a concurrent write
                                        // (e.g. a fresh createOrder()) landing in between could be
                                        // silently re-marked SYNCED by this sweep even though it
                                        // still needed to be pushed to Amrit.
                                        val fresh = tbDao.getTbDiagnosticsByBenId(b.beneficiaryId)
                                        val currentStatus = if (isXray) fresh?.xrayOrderStatus else if (isRif) fresh?.rifOrderStatus else fresh?.trueNatOrderStatus
                                        // A row stuck at FAILED for this order type has nothing worth
                                        // protecting — saveFailedOrderStatus() set syncState UNSYNCED
                                        // itself, which would otherwise block this exact correction
                                        // forever even though the backend just confirmed the order is
                                        // genuinely still in progress.
                                        val isStaleFailed = currentStatus.equals(OrderStatus.FAILED.name, ignoreCase = true)
                                        if (fresh?.syncState != null && fresh.syncState != SyncState.SYNCED && !isStaleFailed) {
                                            return@withLock
                                        }
                                        val isDone = currentStatus.equals(OrderStatus.COMPLETED.name, ignoreCase = true)
                                        Timber.d("STOP-TB polling debug: awaitingProviderResult regId=$regId benId=${b.beneficiaryId} currentStatus=$currentStatus isDone=$isDone")
                                        if (!isDone) {
                                            val cache = (fresh ?: TBDiagnosticsCache(benId = b.beneficiaryId)).let {
                                                if (isXray) {
                                                    // Chest X-Ray's reduced 5-value vocabulary collapses
                                                    // this "in progress" bucket into PENDING.
                                                    it.copy(
                                                        xrayOrderStatus = OrderStatus.PENDING.name,
                                                        isReferredForDigitalChestXray = true,
                                                        syncState = SyncState.SYNCED
                                                    )
                                                } else if (isRif) {
                                                    // Reduced 5-value vocabulary — see bucket 1 above.
                                                    it.copy(
                                                        rifOrderStatus = OrderStatus.PENDING.name,
                                                        syncState = SyncState.SYNCED
                                                    )
                                                } else {
                                                    it.copy(
                                                        trueNatOrderStatus = OrderStatus.PENDING.name,
                                                        isReferredForSputum = true,
                                                        syncState = SyncState.SYNCED
                                                    )
                                                }
                                            }
                                            // Only write when the row actually changed. These status buckets
                                            // re-report the same orders on every poll, so saving unconditionally
                                            // rewrote the row and invalidated every list observing TB_DIAGNOSTICS
                                            // once per beneficiary, for no change at all.
                                            if (cache != fresh) {
                                                tbDao.saveTbDiagnostics(cache)
                                            }
                                        }
                                    }
                                }
                            }

                            // 3. Completed — each beneficiary here is independent (its own
                            // benId lock, no shared mutable state beyond the ConcurrentHashMaps
                            // already used elsewhere in this file), so fan these out concurrently
                            // instead of one-by-one — but a chunk at a time, not all at once.
                            // completedList is the whole backlog the backend reports as done for
                            // this order type - a
                            // real camp device measured 4,000 of them for XRAY_CHEST and 1,500 for
                            // MTB - and `completedList.map { async { ... } }` created one live
                            // coroutine for every single one. The comment that used to sit here
                            // reasoned that OkHttp's per-host cap (5) made that safe; it does cap
                            // requests in flight, but that is exactly what made it unsafe. The
                            // other ~5,495 coroutines did not disappear, they queued behind that
                            // 5-wide pipe holding their Room reads alive, and at ~42 results/sec
                            // the queue took over two minutes to drain. Measured on the device:
                            // 433 MB allocated in 18 s, 12 GCs, heap pinned at 160/160 MB with 0%
                            // free. Leaving the screen then cancelled 2,664 still-queued jobs at
                            // once and froze the UI for ~1.8 s (180 dropped frames).
                            //
                            // Chunking keeps at most [completedFetchChunkSize] coroutines alive,
                            // which still keeps OkHttp's 5 slots busy - the pipe, not the number
                            // of waiting coroutines, is what sets throughput - while bounding both
                            // the live heap and how much there is to cancel on the way out.
                            // Set by any entry that actually fetched a result, so the single
                            // post-bucket TB Suspected push below is skipped when nothing changed.
                            val resultsFetchedForCompleted = java.util.concurrent.atomic.AtomicBoolean(false)
                            completedList.chunked(completedFetchChunkSize).forEach { completedChunk ->
                            coroutineScope {
                                completedChunk.map { regId ->
                                async {
                                val ben = benDao.getBenByRegId(regId) ?: benDao.getBen(regId)
                                ben?.let { b ->
                                    // This pre-check is intentionally unlocked — it only decides
                                    // WHICH branch to take (fetchOrderResult, which is itself
                                    // self-locking and can't be called from inside our own lock
                                    // without risking a same-benId deadlock, vs. a direct write).
                                    // A stale decision here only means a missed opportunity to
                                    // fetch a fresher result this cycle, self-healing on the next
                                    // sweep — the actual write below re-verifies against a fresh
                                    // read before committing anything.
                                    val existing = tbDao.getTbDiagnosticsByBenId(b.beneficiaryId)
                                    val currentStatus = if (isXray) existing?.xrayOrderStatus else if (isRif) existing?.rifOrderStatus else existing?.trueNatOrderStatus
                                    // See bucket 1's isStaleFailed comment — same reasoning here.
                                    val isStaleFailed = currentStatus.equals(OrderStatus.FAILED.name, ignoreCase = true)
                                    if (existing?.syncState != null && existing.syncState != SyncState.SYNCED && !isStaleFailed) {
                                        return@let
                                    }
                                    val needsResultFetch = when {
                                        isXray -> !currentStatus.equals(OrderStatus.COMPLETED.name, ignoreCase = true) || existing?.chestXRayResult.isNullOrBlank()
                                        isRif -> !currentStatus.equals(OrderStatus.COMPLETED.name, ignoreCase = true) || existing?.trueNatRifResult.isNullOrBlank()
                                        else -> !currentStatus.equals(OrderStatus.COMPLETED.name, ignoreCase = true) || existing?.naatResult.isNullOrBlank()
                                    }

                                    if (needsResultFetch && fetchResult) {
                                        // pushSuspectedAfter = false: this runs once per entry in
                                        // a bucket measured at 4,000 entries, and that push reads
                                        // and POSTs every UNSYNCED TB Suspected row each time.
                                        // One push after the whole bucket, below.
                                        fetchOrderResult(b.beneficiaryId, orderType, pushSuspectedAfter = false)
                                        resultsFetchedForCompleted.set(true)
                                    }
                                    if (!fetchResult || !needsResultFetch) {
                                        benIdLocks.withLock(b.beneficiaryId) {
                                        // Re-verify against a fresh read before writing — the
                                        // pre-check above may be stale by the time this lock is
                                        // acquired (e.g. a concurrent submitManualResult() already
                                        // landed), so both the sync-guard and the "already done"
                                        // check are redone here against current state.
                                        val fresh = tbDao.getTbDiagnosticsByBenId(b.beneficiaryId)
                                        val freshStatus = if (isXray) fresh?.xrayOrderStatus else if (isRif) fresh?.rifOrderStatus else fresh?.trueNatOrderStatus
                                        val isStaleFailed = freshStatus.equals(OrderStatus.FAILED.name, ignoreCase = true)
                                        if (fresh?.syncState != null && fresh.syncState != SyncState.SYNCED && !isStaleFailed) {
                                            return@withLock
                                        }
                                        if (freshStatus.equals(OrderStatus.COMPLETED.name, ignoreCase = true)) {
                                            return@withLock
                                        }
                                        val cache = (fresh ?: TBDiagnosticsCache(benId = b.beneficiaryId)).let {
                                            if (isXray) {
                                                it.copy(
                                                    xrayOrderStatus = OrderStatus.COMPLETED.name,
                                                    isReferredForDigitalChestXray = true,
                                                    isChestXRayDone = true,
                                                    syncState = SyncState.SYNCED
                                                )
                                            } else if (isRif) {
                                                it.copy(
                                                    rifOrderStatus = OrderStatus.COMPLETED.name,
                                                    syncState = SyncState.SYNCED
                                                )
                                            } else {
                                                it.copy(
                                                    trueNatOrderStatus = OrderStatus.COMPLETED.name,
                                                    isReferredForSputum = true,
                                                    isNaatConducted = true,
                                                    syncState = SyncState.SYNCED
                                                )
                                            }
                                        }
                                        // Only write when the row actually changed. These status buckets
                                        // re-report the same orders on every poll, so saving unconditionally
                                        // rewrote the row and invalidated every list observing TB_DIAGNOSTICS
                                        // once per beneficiary, for no change at all.
                                        if (cache != fresh) {
                                            tbDao.saveTbDiagnostics(cache)
                                        }
                                        }
                                    }
                                }
                                }
                                }.awaitAll()
                            }
                            }
                            if (resultsFetchedForCompleted.get()) {
                                try {
                                    tbPushRepo.pushUnSyncedRecordsTBSuspected()
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to push TB Suspected after completed-bucket result fetches")
                                }
                            }

                            // 4. Closed — backend's own confirmed closure (EoD expiry, not
                            // conducted, etc.); the app just reflects it, per the plan's
                            // backend-authoritative EoD decision.
                            for (regId in closedList) {
                                val ben = benDao.getBenByRegId(regId) ?: benDao.getBen(regId)
                                ben?.let { b ->
                                    benIdLocks.withLock(b.beneficiaryId) {
                                        val fresh = tbDao.getTbDiagnosticsByBenId(b.beneficiaryId)
                                        val currentStatus = if (isXray) fresh?.xrayOrderStatus else if (isRif) fresh?.rifOrderStatus else fresh?.trueNatOrderStatus
                                        val isStaleFailed = currentStatus.equals(OrderStatus.FAILED.name, ignoreCase = true)
                                        if (fresh?.syncState != null && fresh.syncState != SyncState.SYNCED && !isStaleFailed) {
                                            return@withLock
                                        }
                                        val isAlreadyClosed = currentStatus.equals(OrderStatus.CLOSED.name, ignoreCase = true)
                                        if (!isAlreadyClosed) {
                                            val cache = (fresh ?: TBDiagnosticsCache(benId = b.beneficiaryId)).let {
                                                if (isXray) {
                                                    it.copy(
                                                        xrayOrderStatus = OrderStatus.CLOSED.name,
                                                        isReferredForDigitalChestXray = true,
                                                        syncState = SyncState.SYNCED
                                                    )
                                                } else if (isRif) {
                                                    it.copy(
                                                        rifOrderStatus = OrderStatus.CLOSED.name,
                                                        syncState = SyncState.SYNCED
                                                    )
                                                } else {
                                                    it.copy(
                                                        trueNatOrderStatus = OrderStatus.CLOSED.name,
                                                        isReferredForSputum = true,
                                                        syncState = SyncState.SYNCED
                                                    )
                                                }
                                            }
                                            // Only write when the row actually changed. These status buckets
                                            // re-report the same orders on every poll, so saving unconditionally
                                            // rewrote the row and invalidated every list observing TB_DIAGNOSTICS
                                            // once per beneficiary, for no change at all.
                                            if (cache != fresh) {
                                                tbDao.saveTbDiagnostics(cache)
                                            }
                                        }
                                    }
                                }
                            }

                            // 5. Failed
                            for (regId in failedList) {
                                val ben = benDao.getBenByRegId(regId) ?: benDao.getBen(regId)
                                ben?.let { b ->
                                    benIdLocks.withLock(b.beneficiaryId) {
                                        val fresh = tbDao.getTbDiagnosticsByBenId(b.beneficiaryId)
                                        // Relies solely on the isRecentlyPushed grace-period check below (not a
                                        // syncState==SYNCED check) - a diagnostics row can stay UNSYNCED indefinitely
                                        // if its own push to Amrit never succeeds, which would otherwise block the
                                        // server's confirmed "failed" status from ever landing locally.
                                        val currentStatus = if (isXray) fresh?.xrayOrderStatus else if (isRif) fresh?.rifOrderStatus else fresh?.trueNatOrderStatus
                                        val isDone = currentStatus.equals(OrderStatus.COMPLETED.name, ignoreCase = true)
                                        // Same staleness guard as the "Polling Timed Out" bucket above.
                                        val justPushedAt = orderCreatedTimestamps["${b.beneficiaryId}_$orderType"]
                                        val isRecentlyPushed = justPushedAt != null && System.currentTimeMillis() - justPushedAt < ORDER_STATUS_GRACE_PERIOD_MS
                                        if (!isDone && !isRecentlyPushed) {
                                            val cache = (fresh ?: TBDiagnosticsCache(benId = b.beneficiaryId)).let {
                                                if (isXray) {
                                                    it.copy(
                                                        xrayOrderStatus = OrderStatus.FAILED.name,
                                                        isReferredForDigitalChestXray = true,
                                                        syncState = SyncState.SYNCED
                                                    )
                                                } else if (isRif) {
                                                    it.copy(
                                                        rifOrderStatus = OrderStatus.FAILED.name,
                                                        syncState = SyncState.SYNCED
                                                    )
                                                } else {
                                                    it.copy(
                                                        trueNatOrderStatus = OrderStatus.FAILED.name,
                                                        isReferredForSputum = true,
                                                        syncState = SyncState.SYNCED
                                                    )
                                                }
                                            }
                                            // Only write when the row actually changed. These status buckets
                                            // re-report the same orders on every poll, so saving unconditionally
                                            // rewrote the row and invalidated every list observing TB_DIAGNOSTICS
                                            // once per beneficiary, for no change at all.
                                            if (cache != fresh) {
                                                tbDao.saveTbDiagnostics(cache)
                                            }
                                        }
                                    }
                                }
                            }

                            // No "Refused" bucket — not part of the real getBeneficiariesByStatus
                            // contract. A declined-before-order referral is stored as CLOSED
                            // directly by this app (TBSuspectedQuickViewModel), never derived from
                            // this sweep — same as the "closed" bucket handling above.

                            // 6. Awaiting Manual Entry
                            for (regId in awaitingManualEntryList) {
                                val ben = benDao.getBenByRegId(regId) ?: benDao.getBen(regId)
                                ben?.let { b ->
                                    benIdLocks.withLock(b.beneficiaryId) {
                                        val fresh = tbDao.getTbDiagnosticsByBenId(b.beneficiaryId)
                                        val currentStatus = if (isXray) fresh?.xrayOrderStatus else if (isRif) fresh?.rifOrderStatus else fresh?.trueNatOrderStatus
                                        val isStaleFailed = currentStatus.equals(OrderStatus.FAILED.name, ignoreCase = true)
                                        if (fresh?.syncState != null && fresh.syncState != SyncState.SYNCED && !isStaleFailed) {
                                            return@withLock
                                        }
                                        val isDone = currentStatus.equals(OrderStatus.COMPLETED.name, ignoreCase = true)
                                        if (!isDone) {
                                            val cache = (fresh ?: TBDiagnosticsCache(benId = b.beneficiaryId)).let {
                                                if (isXray) {
                                                    it.copy(
                                                        xrayOrderStatus = OrderStatus.MANUAL_ENTRY.name,
                                                        isReferredForDigitalChestXray = true,
                                                        syncState = SyncState.SYNCED
                                                    )
                                                } else if (isRif) {
                                                    // Reduced 5-value vocabulary.
                                                    it.copy(
                                                        rifOrderStatus = OrderStatus.MANUAL_ENTRY.name,
                                                        syncState = SyncState.SYNCED
                                                    )
                                                } else {
                                                    it.copy(
                                                        trueNatOrderStatus = OrderStatus.MANUAL_ENTRY.name,
                                                        isReferredForSputum = true,
                                                        syncState = SyncState.SYNCED
                                                    )
                                                }
                                            }
                                            // Only write when the row actually changed. These status buckets
                                            // re-report the same orders on every poll, so saving unconditionally
                                            // rewrote the row and invalidated every list observing TB_DIAGNOSTICS
                                            // once per beneficiary, for no change at all.
                                            if (cache != fresh) {
                                                tbDao.saveTbDiagnostics(cache)
                                            }
                                        }
                                    }
                                }
                            }
 
                            val resultData = DiagnosticBeneficiaryStatusData(
                                awaitingProviderResult = awaitingProvResList,
                                completed = completedList,
                                failed = failedList,
                                closed = closedList,
                                awaitingManualEntry = awaitingManualEntryList
                            )
                            return@withContext NetworkResponse.Success(resultData)
                        } else {
                            val errorMsg = jsonObj.optString("message") ?: "Failed to fetch beneficiary order statuses"
                            return@withContext NetworkResponse.Error(errorMsg)
                        }
                    }
                }
                NetworkResponse.Error(context.getString(R.string.error_http_code, statusCode))
            } catch (e: CancellationException) {
                // Leaving the screen mid-sweep is ordinary, not a failure - let it propagate
                // rather than reporting it as one. See the matching catch in fetchOrderResult.
                throw e
            } catch (e: Exception) {
                Timber.e(e, "fetchBeneficiariesByStatus failed for $orderType")
                NetworkResponse.Error(e.message ?: "Unknown error")
            }
        }
    }

    internal data class MergedOrderResult(val cache: TBDiagnosticsCache, val triggerTrueNat: Boolean)
}
