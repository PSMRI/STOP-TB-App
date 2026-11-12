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
import org.piramalswasthya.stoptb.repositories.TBRepo.Companion.PENDING_SYNC
import org.piramalswasthya.stoptb.utils.ServerDateParser
import org.piramalswasthya.stoptb.utils.throttleLatest
import org.piramalswasthya.stoptb.work.WorkerUtils
import timber.log.Timber

/**
 * Manually entered diagnostic results (Enter Result / Not Conducted): submitting them, queueing
 * them while the hub is unreachable, replaying them and the follow-up order cascade. Moved out of
 * TBRepo unchanged.
 */
@Singleton
class ManualResultRepo @Inject constructor(
    private val tbDao: TBDao,
    private val benDao: BenDao,
    private val preferenceDao: PreferenceDao,
    private val tmcNetworkApiService: AmritApiService,
    @ApplicationContext private val context: Context,
    private val tbLocalRepo: TbLocalRepo,
    private val diagnosticOrderRepo: DiagnosticOrderRepo,
    private val benIdLocks: BenIdLocks
) {

    suspend fun submitManualResult(
        benId: Long,
        orderType: String,
        // Null for the "Not Conducted" closure path — see reasonForRefusal below.
        resultSummary: String? = null,
        // Null resultSummary + a reason here closes the order (X-Ray/TrueNat/RIF alike) instead
        // of completing it. Sent to the backend as reasonToClose.
        reasonForRefusal: String? = null,
        isRetryAfterOrderCreation: Boolean = false
    ):NetworkResponse<String> {
        return withContext(Dispatchers.IO) {
            val ben = benDao.getBen(benId)
                ?: return@withContext NetworkResponse.Error(context.getString(R.string.error_beneficiary_not_found))
            val targetBenId = ben.beneficiaryId

            val apiOrderType = if (orderType.equals("SPUTUM_TRUENAT", ignoreCase = true)) "MTB" else orderType

            val localResult = when (resultSummary) {
                "TB Positive" -> "MTB detected"
                "TB Negative" -> "MTB not detected"
                "DR TB" -> "Rif Resistance Detected"
                "Non DR TB" -> "Rif Resistance Not Detected"
                else -> resultSummary
            }

            // Offline-first: never let a connectivity issue discard user-entered data — skip the
            // network attempt entirely when the hub is known-disconnected.
            if (preferenceDao.isCampModeEnabled() && !preferenceDao.isCampHubConnected()) {
                saveManualResultPendingSync(benId, orderType, resultSummary, reasonForRefusal, localResult)
                return@withContext NetworkResponse.Success(PENDING_SYNC)
            }

            try {
                val request = DiagnosticManualResultRequest(
                    beneficiaryId = targetBenId,
                    orderType = apiOrderType,
                    resultSummary = resultSummary,
                    reasonToClose = reasonForRefusal
                )
                val response = tmcNetworkApiService.submitManualResult(request)
                val statusCode = response.code()
                val responseBody = response.body()
                Timber.d("STOP-TB manualResult debug: benId=$benId orderType=$orderType apiOrderType=$apiOrderType httpCode=$statusCode isSuccessful=${response.isSuccessful} bodyStatusCode=${responseBody?.statusCode}")
                if (statusCode == 200 && responseBody != null && response.isSuccessful) {
                    if (responseBody.statusCode == 200) {
                        // The legacy envelope can signal an error via body statusCode even on
                        // HTTP 200 (e.g. "order already COMPLETED"), so check both.
                        val fetchedOrderId = responseBody.data.externalOrderId.asValidOrderId()
                        benIdLocks.withLock(benId) {
                            val cache = buildManualResultCache(
                                benId, orderType, resultSummary, reasonForRefusal, localResult,
                                fetchedOrderId = fetchedOrderId, pendingSync = false
                            )
                            tbDao.saveTbDiagnostics(cache)
                        }
                        return@withContext NetworkResponse.Success(context.getString(R.string.diagnostic_manual_result_success))
                    } else {
                        val errMsg = responseBody.errorMessage ?: context.getString(R.string.error_manual_result_rejected)
                        if (!isRetryAfterOrderCreation &&
                            errMsg.contains("DiagnosticOrder not found for beneficiaryId=")
                        ) {
                            if (WorkerUtils.isDiagnosticOrderPushActive(context, benId)) {
                                // Not a connectivity problem: this device's own order push is
                                // still in flight, so the same submit succeeds once it lands.
                                // Queued rather than reported, and replayed by
                                // DiagnosticResultPollWorker. That worker only runs in camp mode,
                                // which is the whole deployment - every tablet talks to the camp
                                // hub and sign-in is not possible without one - so there is no
                                // mode in which this queue goes unserviced.
                                return@withContext queueForLaterReplay(
                                    benId, orderType, resultSummary, reasonForRefusal, localResult
                                )
                            }
                            val orderResult = diagnosticOrderRepo.createOrder(benId, orderType)
                            if (orderResult is NetworkResponse.Success) {
                                // A real order exists now — retry this exact submission against it, once.
                                return@withContext submitManualResult(
                                    benId, orderType, resultSummary, reasonForRefusal,
                                    isRetryAfterOrderCreation = true
                                )
                            }
                            val orderErrMsg = (orderResult as? NetworkResponse.Error)?.message
                            // Compared against the same localized resources diagnosticOrderRepo.createOrder() itself
                            // returns below — a raw English literal here would stop matching as
                            // soon as the device locale changes diagnosticOrderRepo.createOrder()'s actual message.
                            val isDefinitiveOrderFailure = orderErrMsg == context.getString(R.string.error_no_user_logged_in) ||
                                orderErrMsg == context.getString(R.string.error_beneficiary_not_found) ||
                                orderErrMsg == context.getString(R.string.error_beneficiary_id_invalid)
                            if (isDefinitiveOrderFailure) {
                                return@withContext NetworkResponse.Error(
                                    context.getString(R.string.diagnostic_order_creation_failed, orderErrMsg)
                                )
                            }
                            return@withContext queueForLaterReplay(
                                benId, orderType, resultSummary, reasonForRefusal, localResult
                            )
                        }
                        // A definitive rejection (e.g. already COMPLETED) won't succeed on retry —
                        // surface it as a real error instead of queuing for offline-first retry.
                        return@withContext NetworkResponse.Error(errMsg)
                    }
                } else {
                    // Couldn't confirm anything either way — treat like an unreachable hub.
                    return@withContext queueForLaterReplay(
                        benId, orderType, resultSummary, reasonForRefusal, localResult
                    )
                }
            } catch (e: CancellationException) {
                throw e // a stopped worker must not queue the entry and go on to the next one
            } catch (e: Exception) {
                Timber.e(e, "submitManualResult failed")
                return@withContext queueForLaterReplay(
                    benId, orderType, resultSummary, reasonForRefusal, localResult
                )
            }
        }
    }

    /**
     * The follow-on orders a manual result raises, once the backend has actually accepted it.
     *
     * Single source of truth for both callers: the form (which cascades as soon as its submit is
     * confirmed) and [retryPendingManualResultSyncs] (which cascades when a queued entry is
     * finally accepted). Cascading off an *unconfirmed* entry is what left a beneficiary showing
     * "RIF Order ID: Pending" after their offline TrueNat result was refused and replaced by a
     * TB Negative one - a drug-resistance test apparently in progress that the backend had never
     * heard of, on a patient who did not need one.
     */
    suspend fun cascadeOrdersForConfirmedResult(
        benId: Long,
        orderType: String,
        resultSummary: String?
    ): List<String> {
        val existing = tbDao.getTbDiagnosticsByBenId(benId)
        return when {
            orderType.equals("XRAY_CHEST", ignoreCase = true) -> {
                when (ChestXrayResult.fromResultText(resultSummary)) {
                    ChestXrayResult.TB_PRESUMPTIVE, ChestXrayResult.ABNORMAL_NOT_PRESUMPTIVE -> {
                        val hasTrueNat = !existing?.trueNatOrderId.isNullOrBlank() ||
                                existing?.trueNatOrderStatus.equals(OrderStatus.COMPLETED.name, ignoreCase = true) ||
                                existing?.trueNatOrderStatus.equals(OrderStatus.PENDING.name, ignoreCase = true) ||
                                existing?.trueNatOrderStatus.equals(OrderStatus.CLOSED.name, ignoreCase = true)
                        if (hasTrueNat) emptyList() else listOf("SPUTUM_TRUENAT")
                    }
                    ChestXrayResult.AI_INVALID -> listOf("XRAY_CHEST")
                    else -> emptyList()
                }
            }
            orderType.equals("MDR_RIF", ignoreCase = true) ->
                if (RifResult.fromResultText(resultSummary) == RifResult.INVALID_ERROR) listOf("MDR_RIF") else emptyList()
            else -> when (MtbResult.fromResultText(resultSummary)) {
                MtbResult.TB_POSITIVE -> listOf("MDR_RIF")
                MtbResult.INVALID_ERROR -> listOf("SPUTUM_TRUENAT")
                else -> emptyList()
            }
        }
    }

    /**
     * Stores the entry for later replay. Offline-first in both modes: a result a health worker
     * has typed is never discarded because the network was unavailable at that instant.
     *
     * DiagnosticResultPollWorker replays these once the hub is reachable again. That worker is
     * camp-mode only, which is the whole deployment: every tablet talks to the camp hub, and
     * sign-in is not possible without it. An earlier version of this refused to store anything
     * outside camp mode on the grounds that nothing would replay it there - a case that does not
     * arise, and the wrong trade regardless, since discarding a typed result is worse than
     * storing one that syncs late.
     */
    private suspend fun queueForLaterReplay(
        benId: Long,
        orderType: String,
        resultSummary: String?,
        reasonForRefusal: String?,
        localResult: String?
    ): NetworkResponse<String> {
        saveManualResultPendingSync(benId, orderType, resultSummary, reasonForRefusal, localResult)
        return NetworkResponse.Success(PENDING_SYNC)
    }

    /** Writes the same fields the confirmed-by-backend path would, marked with this test
     *  type's pending-sync flag so DiagnosticResultPollWorker's retry sweep knows to replay
     *  the order/manualResult call later. */
    private suspend fun saveManualResultPendingSync(
        benId: Long, orderType: String, resultSummary: String?, reasonForRefusal: String?, localResult: String?
    ) {
        benIdLocks.withLock(benId) {
            val cache = buildManualResultCache(
                benId, orderType, resultSummary, reasonForRefusal, localResult,
                fetchedOrderId = null, pendingSync = true
            )
            tbDao.saveTbDiagnostics(cache)
        }
    }

    private suspend fun buildManualResultCache(
        benId: Long,
        orderType: String,
        resultSummary: String?,
        reasonForRefusal: String?,
        localResult: String?,
        fetchedOrderId: String?,
        pendingSync: Boolean
    ): TBDiagnosticsCache {
        val isXrayNotConducted = orderType.equals("XRAY_CHEST", ignoreCase = true) &&
                resultSummary == null && reasonForRefusal != null
        val isRifNotConducted = orderType.equals("MDR_RIF", ignoreCase = true) &&
                resultSummary == null && reasonForRefusal != null
        val isMtbNotConducted = !orderType.equals("XRAY_CHEST", ignoreCase = true) &&
                !orderType.equals("MDR_RIF", ignoreCase = true) &&
                resultSummary == null && reasonForRefusal != null

        val existing = tbDao.getTbDiagnosticsByBenId(benId)
        return (existing ?: TBDiagnosticsCache(benId = benId)).let {
            if (orderType.equals("XRAY_CHEST", ignoreCase = true)) {
                // A CLOSED response from order/manualResult (Not Conducted) is treated as
                // success — see isXrayNotConducted above.
                if (isXrayNotConducted) {
                    val (reason, other) = keptOrSplitReason(it.reasonNotConductedChestXray, it.reasonNotConductedChestXrayOther, reasonForRefusal)
                    it.copy(
                        reasonNotConductedChestXray = reason,
                        reasonNotConductedChestXrayOther = other,
                        xrayOrderId = fetchedOrderId ?: it.xrayOrderId,
                        xrayOrderStatus = OrderStatus.CLOSED.name,
                        isChestXRayDone = false,
                        chestXRayResult = null,
                        xrayManualResultPendingSync = pendingSync,
                        syncState = SyncState.UNSYNCED
                    )
                } else {
                    it.copy(
                        xrayOrderId = fetchedOrderId ?: it.xrayOrderId,
                        xrayOrderStatus = OrderStatus.COMPLETED.name,
                        isChestXRayDone = true,
                        chestXRayResult = localResult,
                        xrayManualResultPendingSync = pendingSync,
                        syncState = SyncState.UNSYNCED
                    )
                }
            } else if (orderType.equals("MDR_RIF", ignoreCase = true)) {
                // RIF's own "Not Conducted" closure — a CLOSED response from order/manualResult
                // is treated as success, mirroring Chest X-Ray's isXrayNotConducted handling
                // above.
                if (isRifNotConducted) {
                    val (reason, other) = keptOrSplitReason(it.reasonNotConductedRif, it.reasonNotConductedRifOther, reasonForRefusal)
                    it.copy(
                        reasonNotConductedRif = reason,
                        reasonNotConductedRifOther = other,
                        rifOrderId = fetchedOrderId ?: it.rifOrderId,
                        rifOrderStatus = OrderStatus.CLOSED.name,
                        trueNatRifResult = null,
                        rifManualResultPendingSync = pendingSync,
                        syncState = SyncState.UNSYNCED
                    )
                } else {
                    val standardizedRif = RifResult.fromResultText(resultSummary)
                    it.copy(
                        rifOrderId = fetchedOrderId ?: it.rifOrderId,
                        rifOrderStatus = OrderStatus.COMPLETED.name,
                        // Invalid/Error triggers a reorder, after which this text must not
                        // outlive it locally - but only once the backend has accepted the entry.
                        // While it is queued the text is the only thing the replay can send.
                        // Indeterminate is terminal, keep it.
                        trueNatRifResult = manualResultTextToStore(
                            localResult, standardizedRif == RifResult.INVALID_ERROR, pendingSync
                        ),
                        // RIF DR TB / Non DR TB manual entry — extend the same
                        // isConfirmed/isTBConfirmed flags the automated path sets;
                        // isDrTbConfirmed distinguishes the DR-TB-specific outcome.
                        isDrTbConfirmed = if (standardizedRif == RifResult.DR_TB) true else it.isDrTbConfirmed,
                        isConfirmed = if (standardizedRif == RifResult.DR_TB || standardizedRif == RifResult.NON_DR_TB)
                            true else it.isConfirmed,
                        isTBConfirmed = if (standardizedRif == RifResult.DR_TB || standardizedRif == RifResult.NON_DR_TB)
                            true else it.isTBConfirmed,
                        rifManualResultPendingSync = pendingSync,
                        syncState = SyncState.UNSYNCED
                    )
                }
            } else {
                // TrueNat/MTB's own "Not Conducted" closure — same convention as above.
                if (isMtbNotConducted) {
                    val (reason, other) = keptOrSplitReason(it.reasonNotConductedNaat, it.reasonNotConductedNaatOther, reasonForRefusal)
                    it.copy(
                        reasonNotConductedNaat = reason,
                        reasonNotConductedNaatOther = other,
                        trueNatOrderId = fetchedOrderId ?: it.trueNatOrderId,
                        trueNatOrderStatus = OrderStatus.CLOSED.name,
                        isNaatConducted = false,
                        naatResult = null,
                        trueNatManualResultPendingSync = pendingSync,
                        syncState = SyncState.UNSYNCED
                    )
                } else {
                    val standardizedMtb = MtbResult.fromResultText(resultSummary)
                    it.copy(
                        trueNatOrderId = fetchedOrderId ?: it.trueNatOrderId,
                        trueNatOrderStatus = OrderStatus.COMPLETED.name,
                        isReferredForSputum = true,
                        isNaatConducted = true,
                        // See the RIF branch above: kept while the entry is still queued for
                        // replay, dropped once the backend accepts and the reorder is raised.
                        naatResult = manualResultTextToStore(
                            localResult, standardizedMtb == MtbResult.INVALID_ERROR, pendingSync
                        ),
                        isTBConfirmed = when (standardizedMtb) {
                            MtbResult.TB_POSITIVE -> true
                            MtbResult.TB_NEGATIVE -> false
                            else -> it.isTBConfirmed
                        },
                        isConfirmed = when (standardizedMtb) {
                            MtbResult.TB_POSITIVE -> true
                            MtbResult.TB_NEGATIVE -> false
                            else -> it.isConfirmed
                        },
                        trueNatManualResultPendingSync = pendingSync,
                        syncState = SyncState.UNSYNCED
                    )
                }
            }
        }
    }

    /**
     * The not-conducted reason to store with a queued closure: the row's own (the form saves it
     * right after this) when it has one, else [sent] split back into the form's two fields, so
     * the replay still has it if the app stops before the form's save.
     */
    private fun keptOrSplitReason(current: String?, currentOther: String?, sent: String?): Pair<String?, String?> {
        if (!current.isNullOrBlank() || sent.isNullOrBlank()) return current to currentOther
        val otherPrefix = "Other: "
        return if (sent.startsWith(otherPrefix)) "Other" to sent.removePrefix(otherPrefix) else sent to null
    }

    private fun formatNotConductedReason(reason: String?, other: String?): String? {
        if (reason.isNullOrBlank()) return null
        return if (reason.equals("Other", ignoreCase = true) && !other.isNullOrBlank()) "Other: $other" else reason
    }

    /**
     * Replays any manually-entered result/not-conducted-reason that was saved locally
     * (offline-first, see submitManualResult()/saveManualResultPendingSync()) but hasn't been
     * confirmed by the backend yet. Called from DiagnosticResultPollWorker's own sweep, which
     * only runs when camp mode is on and the hub is connected — exactly the precondition this
     * needs. Returns true if any beneficiary still has a pending sync after this sweep (so the
     * caller knows whether to keep rescheduling itself).
     *
     * Reconstructs resultSummary via each result enum's fromResultText()+displayValue rather
     * than reading the stored field directly — TrueNat/RIF store a locally-remapped display
     * string (e.g. "MTB detected" for TB_POSITIVE), not the original wire value ("TB Positive")
     * that was actually sent the first time, and the enum round-trip recovers it correctly
     * regardless of which of the two synonym forms is currently stored.
     */
    suspend fun retryPendingManualResultSyncs(): Boolean {
        return withContext(Dispatchers.IO) {
            var stillPending = false
            // Only rows with a manual result pending sync; every branch below requires one.
            for (diag in tbLocalRepo.getDiagnosticsWithPendingManualResult()) {
                if (diag.xrayManualResultPendingSync == true) {
                    val resultSummary = ChestXrayResult.fromResultText(diag.chestXRayResult)?.displayValue
                    val reason = if (resultSummary == null)
                        formatNotConductedReason(diag.reasonNotConductedChestXray, diag.reasonNotConductedChestXrayOther) else null
                    if (resultSummary != null || reason != null) {
                        val response = submitManualResult(diag.benId, "XRAY_CHEST", resultSummary, reason)
                        when {
                            response.data == PENDING_SYNC -> stillPending = true
                            response is NetworkResponse.Error -> clearStuckPendingSync(diag.benId, "XRAY_CHEST", response.message)
                            // Accepted at last - raise the follow-on orders the form could not
                            // raise at entry time, because back then nothing had accepted it yet.
                            else -> cascadeAcceptedReplay(diag.benId, "XRAY_CHEST", resultSummary)
                        }
                    }
                }
                if (diag.trueNatManualResultPendingSync == true) {
                    val resultSummary = MtbResult.fromResultText(diag.naatResult)?.displayValue
                    val reason = if (resultSummary == null)
                        formatNotConductedReason(diag.reasonNotConductedNaat, diag.reasonNotConductedNaatOther) else null
                    if (resultSummary != null || reason != null) {
                        val response = submitManualResult(diag.benId, "SPUTUM_TRUENAT", resultSummary, reason)
                        when {
                            response.data == PENDING_SYNC -> stillPending = true
                            response is NetworkResponse.Error -> clearStuckPendingSync(diag.benId, "SPUTUM_TRUENAT", response.message)
                            // Accepted at last - raise the follow-on orders the form could not
                            // raise at entry time, because back then nothing had accepted it yet.
                            else -> cascadeAcceptedReplay(diag.benId, "SPUTUM_TRUENAT", resultSummary)
                        }
                    }
                }
                if (diag.rifManualResultPendingSync == true) {
                    val resultSummary = RifResult.fromResultText(diag.trueNatRifResult)?.displayValue
                    val reason = if (resultSummary == null)
                        formatNotConductedReason(diag.reasonNotConductedRif, diag.reasonNotConductedRifOther) else null
                    if (resultSummary != null || reason != null) {
                        val response = submitManualResult(diag.benId, "MDR_RIF", resultSummary, reason)
                        when {
                            response.data == PENDING_SYNC -> stillPending = true
                            response is NetworkResponse.Error -> clearStuckPendingSync(diag.benId, "MDR_RIF", response.message)
                            // Accepted at last - raise the follow-on orders the form could not
                            // raise at entry time, because back then nothing had accepted it yet.
                            else -> cascadeAcceptedReplay(diag.benId, "MDR_RIF", resultSummary)
                        }
                    }
                }
            }
            stillPending
        }
    }

    private suspend fun cascadeAcceptedReplay(benId: Long, orderType: String, resultSummary: String?) {
        try {
            val orders = cascadeOrdersForConfirmedResult(benId, orderType, resultSummary)
            if (orders.isNotEmpty()) {
                WorkerUtils.triggerDiagnosticOrderPushWorkers(context, benId, orders)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Could not raise follow-on orders after an accepted manual-result replay")
        }
    }

    /** A definitively-rejected retry (e.g. "already COMPLETED") won't succeed by retrying again —
     *  clears just that test type's pending-sync flag so it stops retrying forever, pulls in
     *  whatever the backend actually holds, and tells the user their entry didn't land.
     *
     *  Clearing the flag alone used to be the whole of this: the tablet then went on showing the
     *  result the user had entered offline while the backend held a different one, and the only
     *  record of the divergence was a log line nobody reads. The user had been told "saved
     *  locally, will sync when Camp Hub reconnects", and that promise was withdrawn in silence. */
    private suspend fun clearStuckPendingSync(benId: Long, orderType: String, errorMessage: String?) {
        Timber.w("Manual result retry for benId=$benId orderType=$orderType was rejected by the backend (not a connectivity issue) — clearing pending-sync flag to stop endless retries: $errorMessage")
        benIdLocks.withLock(benId) {
            val existing = tbDao.getTbDiagnosticsByBenId(benId) ?: return@withLock
            val cache = when {
                orderType.equals("XRAY_CHEST", ignoreCase = true) -> existing.copy(xrayManualResultPendingSync = false)
                orderType.equals("MDR_RIF", ignoreCase = true) -> existing.copy(rifManualResultPendingSync = false)
                else -> existing.copy(trueNatManualResultPendingSync = false)
            }
            tbDao.saveTbDiagnostics(cache)
        }

        // Outside the lock on purpose: fetchOrderResult takes the same per-benId lock and
        // kotlinx Mutex is not reentrant.
        val recordedResult = try {
            // Same contract as resultAlreadyOnServer(): fetchOrderResult signals failure by
            // returning Error, not by throwing, and leaves the stored result untouched for a
            // non-completed order. Reading the row back regardless would hand the user their own
            // unsynced entry as "the result already on the server".
            val fetched = diagnosticOrderRepo.fetchOrderResult(benId, orderType, pushSuspectedAfter = false)
            serverResultOrNull(fetched, tbDao.getTbDiagnosticsByBenId(benId), orderType)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Could not read back the backend's result after a rejected manual-result replay")
            null
        }

        notifyManualResultRejected(benId, recordedResult)
    }

    /**
     * The replay runs in a worker with no screen attached, so the only way to reach the person
     * who typed the result is a notification. Private visibility: the body names a beneficiary,
     * which shouldn't appear on a locked screen.
     */
    private suspend fun notifyManualResultRejected(benId: Long, recordedResult: String?) {
        try {
            val name = benDao.getBen(benId)?.let { "${it.firstName.orEmpty()} ${it.lastName.orEmpty()}".trim() }
                ?.takeIf { it.isNotBlank() } ?: context.getString(R.string.manual_result_rejected_fallback_name)
            val text = if (recordedResult != null) {
                context.getString(R.string.manual_result_rejected_with_result, name, recordedResult)
            } else {
                context.getString(R.string.manual_result_rejected_generic, name)
            }
            val notification = androidx.core.app.NotificationCompat.Builder(
                context, context.getString(R.string.notification_sync_channel_id)
            )
                .setSmallIcon(R.drawable.ic__ncd)
                .setContentTitle(context.getString(R.string.manual_result_rejected_title))
                .setContentText(text)
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(text))
                .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .build()
            // Distinct id per beneficiary so two rejections don't overwrite each other.
            androidx.core.app.NotificationManagerCompat.from(context)
                .notify("manual-result-rejected", benId.hashCode(), notification)
        } catch (e: SecurityException) {
            // Notification permission denied - the data has still been reconciled above.
            Timber.w(e, "Could not post the rejected-manual-result notification")
        } catch (e: Exception) {
            Timber.e(e, "Could not post the rejected-manual-result notification")
        }
    }
}
