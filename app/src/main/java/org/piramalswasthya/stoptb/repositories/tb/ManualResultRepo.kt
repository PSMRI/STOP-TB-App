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
import org.piramalswasthya.stoptb.network.DiagnosticManualResultRequest
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
    private val benIdLocks: BenIdLocks,
    private val deviceIntegrationRepo: DeviceIntegrationRepo
) {

    /**
     * order/manualResult for one test (see [DiagnosticManualResultRequest]). [orderType] is the local
     * test; MDR_RIF is sent as orderType MTB with mdrRifResult / reasonToCloseRif. [resultSummary] is
     * in the server's wording; null with a [reasonForRefusal] closes the test ("Not Conducted").
     *
     * The server decides the order status, so the accepted response is written through
     * [mergeOrderResult] rather than assuming COMPLETED.
     */
    suspend fun submitManualResult(
        benId: Long,
        orderType: String,
        resultSummary: String? = null,
        reasonForRefusal: String? = null,
        isRetryAfterOrderCreation: Boolean = false
    ):NetworkResponse<String> {
        return withContext(Dispatchers.IO) {
            val ben = benDao.getBen(benId)
                ?: return@withContext NetworkResponse.Error(context.getString(R.string.error_beneficiary_not_found))
            val targetBenId = ben.beneficiaryId
            val isXray = orderType.equals("XRAY_CHEST", ignoreCase = true)
            val isRif = orderType.equals("MDR_RIF", ignoreCase = true)
            val apiOrderType = if (isXray) "XRAY_CHEST" else "MTB"
            val wireResult = toServerResultWording(orderType, resultSummary)

            // Offline-first: never let a connectivity issue discard user-entered data — skip the
            // network attempt entirely when the hub is known-disconnected.
            if (preferenceDao.isCampModeEnabled() && !preferenceDao.isCampHubConnected()) {
                saveManualResultPendingSync(benId, orderType, wireResult, reasonForRefusal)
                return@withContext NetworkResponse.Success(PENDING_SYNC)
            }

            try {
                val request = DiagnosticManualResultRequest(
                    beneficiaryId = targetBenId,
                    orderType = apiOrderType,
                    resultSummary = if (isRif) null else wireResult,
                    mdrRifResult = if (isRif) wireResult else null,
                    reasonToCloseXray = if (isXray && wireResult == null) reasonForRefusal else null,
                    reasonToCloseMTB = if (!isXray && !isRif && wireResult == null) reasonForRefusal else null,
                    reasonToCloseRif = if (isRif && wireResult == null) reasonForRefusal else null
                )
                val response = tmcNetworkApiService.submitManualResult(request)
                val statusCode = response.code()
                val responseBody = response.body()
                Timber.d("STOP-TB manualResult debug: benId=$benId orderType=$orderType apiOrderType=$apiOrderType httpCode=$statusCode isSuccessful=${response.isSuccessful} bodyStatusCode=${responseBody?.statusCode}")
                if (statusCode == 200 && responseBody != null && response.isSuccessful) {
                    if (responseBody.statusCode == 200) {
                        // The legacy envelope can signal an error via body statusCode even on
                        // HTTP 200 (e.g. "order already COMPLETED"), so check both.
                        saveAcceptedManualResult(benId, orderType, wireResult, reasonForRefusal, responseBody.data)
                        return@withContext NetworkResponse.Success(context.getString(R.string.diagnostic_manual_result_success))
                    } else {
                        val errMsg = responseBody.errorMessage ?: context.getString(R.string.error_manual_result_rejected)
                        // RIF has no order of its own to create: it is a test on the MTB order.
                        if (!isRif && !isRetryAfterOrderCreation &&
                            errMsg.contains("DiagnosticOrder not found for beneficiaryId=")
                        ) {
                            if (WorkerUtils.isDiagnosticOrderPushActive(context, benId)) {
                                // This device's order push is still in flight; queued and replayed by
                                // DiagnosticResultPollWorker once it lands.
                                return@withContext queueForLaterReplay(benId, orderType, wireResult, reasonForRefusal)
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
                            val isDefinitiveOrderFailure = orderErrMsg == context.getString(R.string.error_no_user_logged_in) ||
                                orderErrMsg == context.getString(R.string.error_beneficiary_not_found) ||
                                orderErrMsg == context.getString(R.string.error_beneficiary_id_invalid)
                            if (isDefinitiveOrderFailure) {
                                return@withContext NetworkResponse.Error(
                                    context.getString(R.string.diagnostic_order_creation_failed, orderErrMsg)
                                )
                            }
                            return@withContext queueForLaterReplay(benId, orderType, wireResult, reasonForRefusal)
                        }
                        // A definitive rejection (e.g. already COMPLETED, or RIF on a negative MTB)
                        // won't succeed on retry — surface it as a real error.
                        return@withContext NetworkResponse.Error(errMsg)
                    }
                } else {
                    // Couldn't confirm anything either way — treat like an unreachable hub.
                    return@withContext queueForLaterReplay(benId, orderType, wireResult, reasonForRefusal)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "submitManualResult failed")
                return@withContext queueForLaterReplay(benId, orderType, wireResult, reasonForRefusal)
            }
        }
    }

    /** The server's wording for a result the form picked (it may hold an older local synonym). */
    private fun toServerResultWording(orderType: String, resultSummary: String?): String? {
        if (resultSummary.isNullOrBlank()) return null
        return when {
            orderType.equals("XRAY_CHEST", ignoreCase = true) -> ChestXrayResult.fromResultText(resultSummary)?.displayValue
            orderType.equals("MDR_RIF", ignoreCase = true) -> RifResult.fromResultText(resultSummary)?.displayValue
            else -> MtbResult.fromResultText(resultSummary)?.displayValue
        } ?: resultSummary
    }

    /**
     * Writes an accepted manual result: a `results` list response goes through [mergeOrderResult];
     * an older server returns no result, so the entered values are written.
     */
    private suspend fun saveAcceptedManualResult(
        benId: Long,
        orderType: String,
        wireResult: String?,
        reasonForRefusal: String?,
        data: OrderResultData
    ) {
        preferenceDao.setManualEnteredResult(benId, orderType, wireResult)
        val fetchedOrderId = data.externalOrderId.asValidOrderId()
        benIdLocks.withLock(benId) {
            val cleared = tbDao.getTbDiagnosticsByBenId(benId)?.let { diag ->
                when {
                    orderType.equals("XRAY_CHEST", ignoreCase = true) -> diag.copy(xrayManualResultPendingSync = false)
                    orderType.equals("MDR_RIF", ignoreCase = true) -> diag.copy(rifManualResultPendingSync = false)
                    else -> diag.copy(trueNatManualResultPendingSync = false)
                }
            }
            val cache = if (data.results != null && !data.status.isNullOrBlank()) {
                val mergeType = if (orderType.equals("XRAY_CHEST", ignoreCase = true)) "XRAY_CHEST" else "SPUTUM_TRUENAT"
                // The manual MTB -> TrueNat referral of an X-ray is raised by the form itself
                // (cascadeOrdersForConfirmedResult), so the merge's trigger is not used here.
                diagnosticOrderRepo.mergeOrderResult(cleared, benId, mergeType, data, data.status, deviceIntegrationRepo.isTruenatIntegrated(), hubCreatesOrders = true).cache
                    .let { merged -> keepEnteredReason(merged, orderType, reasonForRefusal) }
            } else {
                buildManualResultCache(cleared, benId, orderType, wireResult, reasonForRefusal, fetchedOrderId, pendingSync = false)
            }
            tbDao.saveTbDiagnostics(cache.copy(syncState = SyncState.UNSYNCED))
        }
    }

    /** The form's own reason fields stay as entered ("Other" + free text) when the server echoes it. */
    private fun keepEnteredReason(merged: TBDiagnosticsCache, orderType: String, reasonForRefusal: String?): TBDiagnosticsCache {
        if (reasonForRefusal == null) return merged
        val (reason, other) = diagnosticOrderRepo.splitOtherReason(reasonForRefusal) ?: return merged
        return when {
            orderType.equals("XRAY_CHEST", ignoreCase = true) ->
                merged.copy(reasonNotConductedChestXray = reason, reasonNotConductedChestXrayOther = other)
            orderType.equals("MDR_RIF", ignoreCase = true) ->
                merged.copy(reasonNotConductedRif = reason, reasonNotConductedRifOther = other)
            else -> merged.copy(reasonNotConductedNaat = reason, reasonNotConductedNaatOther = other)
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
            // RIF is a test on the MTB order, never an order of its own. An invalid MTB closes
            // the order on the server; the nurse raises a new one (Create New Order).
            else -> emptyList()
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
        wireResult: String?,
        reasonForRefusal: String?
    ): NetworkResponse<String> {
        saveManualResultPendingSync(benId, orderType, wireResult, reasonForRefusal)
        return NetworkResponse.Success(PENDING_SYNC)
    }

    /** Writes the entered values, marked with this test's pending-sync flag so
     *  DiagnosticResultPollWorker's retry sweep replays the order/manualResult call later. */
    private suspend fun saveManualResultPendingSync(
        benId: Long, orderType: String, wireResult: String?, reasonForRefusal: String?
    ) {
        preferenceDao.setManualEnteredResult(benId, orderType, wireResult)
        benIdLocks.withLock(benId) {
            val cache = buildManualResultCache(
                tbDao.getTbDiagnosticsByBenId(benId), benId, orderType, wireResult, reasonForRefusal,
                fetchedOrderId = null, pendingSync = true
            )
            tbDao.saveTbDiagnostics(cache)
        }
    }

    /**
     * The row with a manual entry written into it, for a result the server has not answered with
     * its own view of the order (queued offline, or an older server). The status mirrors the server:
     *  - X-ray result -> COMPLETED; MTB Negative -> COMPLETED; Invalid/Error -> CLOSED
     *  - MTB Positive -> order stays as it was (PENDING / MANUAL_ENTRY, waiting for RIF)
     *  - RIF result -> the whole TrueNat order COMPLETED (RIF Invalid/Error -> CLOSED)
     *  - any "Not Conducted" reason -> CLOSED (for RIF, the MTB result is kept)
     */
    private fun buildManualResultCache(
        existing: TBDiagnosticsCache?,
        benId: Long,
        orderType: String,
        wireResult: String?,
        reasonForRefusal: String?,
        fetchedOrderId: String?,
        pendingSync: Boolean
    ): TBDiagnosticsCache {
        val base = existing ?: TBDiagnosticsCache(benId = benId)
        val notConducted = wireResult == null && reasonForRefusal != null
        val reason = diagnosticOrderRepo.splitOtherReason(reasonForRefusal)
        return when {
            orderType.equals("XRAY_CHEST", ignoreCase = true) -> if (notConducted) base.copy(
                xrayOrderId = fetchedOrderId ?: base.xrayOrderId,
                xrayOrderStatus = OrderStatus.CLOSED.name,
                isChestXRayDone = false,
                chestXRayResult = null,
                reasonNotConductedChestXray = reason?.first ?: base.reasonNotConductedChestXray,
                reasonNotConductedChestXrayOther = reason?.second,
                xrayManualResultPendingSync = pendingSync,
                syncState = SyncState.UNSYNCED
            ) else base.copy(
                xrayOrderId = fetchedOrderId ?: base.xrayOrderId,
                xrayOrderStatus = OrderStatus.COMPLETED.name,
                isChestXRayDone = true,
                chestXRayResult = wireResult,
                xrayManualResultPendingSync = pendingSync,
                syncState = SyncState.UNSYNCED
            )
            orderType.equals("MDR_RIF", ignoreCase = true) -> if (notConducted) base.copy(
                trueNatOrderStatus = OrderStatus.CLOSED.name,
                rifOrderId = base.trueNatOrderId ?: base.rifOrderId,
                rifOrderStatus = OrderStatus.CLOSED.name,
                trueNatRifResult = null,
                isDrTbConfirmed = false,
                reasonNotConductedRif = reason?.first ?: base.reasonNotConductedRif,
                reasonNotConductedRifOther = reason?.second,
                rifManualResultPendingSync = pendingSync,
                syncState = SyncState.UNSYNCED
            ) else {
                val rif = RifResult.fromResultText(wireResult)
                // A hand-entered RIF Invalid/Error closes the order on the server (MTB result kept).
                val rifStatus = if (rif == RifResult.INVALID_ERROR) OrderStatus.CLOSED.name else OrderStatus.COMPLETED.name
                base.copy(
                    trueNatOrderStatus = rifStatus,
                    rifOrderId = base.trueNatOrderId ?: base.rifOrderId,
                    rifOrderStatus = rifStatus,
                    trueNatRifResult = wireResult,
                    isDrTbConfirmed = rif == RifResult.DR_TB,
                    reasonNotConductedRif = null,
                    reasonNotConductedRifOther = null,
                    rifManualResultPendingSync = pendingSync,
                    syncState = SyncState.UNSYNCED
                )
            }
            notConducted -> base.copy(
                trueNatOrderId = fetchedOrderId ?: base.trueNatOrderId,
                trueNatOrderStatus = OrderStatus.CLOSED.name,
                isNaatConducted = false,
                naatResult = null,
                reasonNotConductedNaat = reason?.first ?: base.reasonNotConductedNaat,
                reasonNotConductedNaatOther = reason?.second,
                trueNatManualResultPendingSync = pendingSync,
                syncState = SyncState.UNSYNCED
            )
            else -> {
                val mtb = MtbResult.fromResultText(wireResult)
                val orderStatus = when (mtb) {
                    MtbResult.TB_POSITIVE -> base.trueNatOrderStatus?.takeIf {
                        it == OrderStatus.MANUAL_ENTRY.name || it == OrderStatus.PENDING.name
                    } ?: OrderStatus.PENDING.name
                    MtbResult.INVALID_ERROR -> OrderStatus.CLOSED.name
                    else -> OrderStatus.COMPLETED.name
                }
                val orderId = fetchedOrderId ?: base.trueNatOrderId
                base.copy(
                    trueNatOrderId = orderId,
                    trueNatOrderStatus = orderStatus,
                    isReferredForSputum = true,
                    isNaatConducted = true,
                    naatResult = wireResult,
                    isTBConfirmed = if (mtb != null) mtb == MtbResult.TB_POSITIVE else base.isTBConfirmed,
                    isConfirmed = if (mtb != null) mtb == MtbResult.TB_POSITIVE else base.isConfirmed,
                    // MTB Positive opens the RIF test on this same order.
                    rifOrderId = if (mtb == MtbResult.TB_POSITIVE) orderId else null,
                    rifOrderStatus = if (mtb == MtbResult.TB_POSITIVE) orderStatus else null,
                    trueNatRifResult = null,
                    isDrTbConfirmed = false,
                    trueNatManualResultPendingSync = pendingSync,
                    syncState = SyncState.UNSYNCED
                )
            }
        }
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
