package org.piramalswasthya.stoptb.repositories

import android.content.Context
import com.google.gson.Gson
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import org.piramalswasthya.stoptb.utils.throttleLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.piramalswasthya.stoptb.model.BenRegCache
import org.piramalswasthya.stoptb.model.OrderResultData
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.configuration.GeneralOpdMasterConfig
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.database.room.InAppDb
import org.piramalswasthya.stoptb.database.room.dao.BenDao
import org.piramalswasthya.stoptb.database.room.dao.TBDao
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.database.shared_preferences.SyncRetryStore
import org.piramalswasthya.stoptb.helpers.Konstants
import org.piramalswasthya.stoptb.helpers.NetworkResponse
import org.piramalswasthya.stoptb.helpers.manualResultTextToStore
import org.piramalswasthya.stoptb.helpers.serverResultOrNull
import org.piramalswasthya.stoptb.model.GeneralOpdCache
import org.piramalswasthya.stoptb.model.GeneralOpdPrescription
import org.piramalswasthya.stoptb.model.OpdMedicineDraft
import org.piramalswasthya.stoptb.model.OpdDrugMasters
import org.piramalswasthya.stoptb.model.ChiefComplaintMasterCache
import org.piramalswasthya.stoptb.model.TBConfirmedTreatmentCache
import org.piramalswasthya.stoptb.model.TBDiagnosticsCache
import org.piramalswasthya.stoptb.model.TBScreeningCache
import org.piramalswasthya.stoptb.model.TBSuspectedCache
import org.piramalswasthya.stoptb.model.OrderStatus
import org.piramalswasthya.stoptb.model.ChestXrayResult
import org.piramalswasthya.stoptb.model.MtbResult
import org.piramalswasthya.stoptb.model.RifResult
import org.piramalswasthya.stoptb.model.VisitCategoryMasterCache
import org.piramalswasthya.stoptb.network.AmritApiService
import org.piramalswasthya.stoptb.utils.ServerDateParser
import org.piramalswasthya.stoptb.network.GeneralOpdRequestDTO
import org.piramalswasthya.stoptb.network.GeneralOpdSaveRequest
import org.piramalswasthya.stoptb.network.GeneralOpdDrugResponse
import org.piramalswasthya.stoptb.network.GetDataPaginatedRequest
import org.piramalswasthya.stoptb.network.StopTbVillageRequest
import org.piramalswasthya.stoptb.network.TBConfirmedRequestDTO
import org.piramalswasthya.stoptb.network.TBDiagnosticsRequestDTO
import org.piramalswasthya.stoptb.network.TBDiagnosticsSaveRequest
import org.piramalswasthya.stoptb.network.TBScreeningRequestDTO
import org.piramalswasthya.stoptb.network.TBScreeningSaveRequest
import org.piramalswasthya.stoptb.network.TBSuspectedRequestDTO
import org.piramalswasthya.stoptb.network.PatientRequest
import org.piramalswasthya.stoptb.network.DiagnosticOrderPushRequest
import org.piramalswasthya.stoptb.network.DiagnosticBeneficiaryStatusData
import org.piramalswasthya.stoptb.network.DiagnosticManualResultRequest
import org.piramalswasthya.stoptb.work.WorkerUtils
import timber.log.Timber
import java.io.IOException
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import org.piramalswasthya.stoptb.model.TbScreeningSymptoms

/** Badge/decoration Flows update the list screens at most this often. */
private const val BADGE_UPDATE_PERIOD_MS = 500L

@Singleton
class TBRepo @Inject constructor(
    private val tbDao: TBDao,
    private val benDao: BenDao,
    val preferenceDao: PreferenceDao,
    private val userRepo: UserRepo,
    private val tmcNetworkApiService: AmritApiService,
    private val database: InAppDb,
    private val drugMasterRepo: DrugMasterRepo,
    private val syncRetryStore: SyncRetryStore,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    @org.piramalswasthya.stoptb.di.ApplicationScope private val appScope: kotlinx.coroutines.CoroutineScope,
    private val benIdLocks: org.piramalswasthya.stoptb.repositories.tb.BenIdLocks,
    private val deviceIntegrationRepo: org.piramalswasthya.stoptb.repositories.tb.DeviceIntegrationRepo,
    private val generalOpdRepo: org.piramalswasthya.stoptb.repositories.tb.GeneralOpdRepo,
    private val tbPushRepo: org.piramalswasthya.stoptb.repositories.tb.TbPushRepo,
    private val tbLocalRepo: org.piramalswasthya.stoptb.repositories.tb.TbLocalRepo,
    private val diagnosticOrderRepo: org.piramalswasthya.stoptb.repositories.tb.DiagnosticOrderRepo,
    private val manualResultRepo: org.piramalswasthya.stoptb.repositories.tb.ManualResultRepo,
    private val tbPullRepo: org.piramalswasthya.stoptb.repositories.tb.TbPullRepo
) {
    /**
     * Shares one Room query between every collector (badge sets on AllBen, NonHH, household,
     * unscreened, examine...). Without this each `.map` below re-ran the query per collector.
     */
    private fun <T> Flow<T>.shareBadges(): Flow<T> =
        // At most 2 updates/s: during sync every TB write re-ran these whole-table queries and
        // the list screens re-processed every beneficiary on the main thread (measured at 100K:
        // ~10 s of main-thread work per 20 s on All Beneficiaries while the push drained).
        throttleLatest(BADGE_UPDATE_PERIOD_MS)
            .shareIn(appScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), replay = 1)

    // Per-beneficiary lock for every TB_DIAGNOSTICS/TB_SUSPECTED read-modify-write below, so
    // concurrent calls for the same benId (e.g. parallel X-ray/TrueNat polling) can't clobber
    // each other. Only ever wraps a short leaf read-modify-write, never a whole function that
    // might call another lock-acquiring function for the same benId — Mutex isn't reentrant.
    // Requires TBRepo to be @Singleton, or each injection site would get its own map.
    // The lock map lives in the @Singleton BenIdLocks so every class that does these
    // read-modify-writes shares one set of locks (see BenIdLocks for the ordering rules).
    private suspend fun <T> withBenIdLock(benId: Long, block: suspend () -> T): T =
        benIdLocks.withLock(benId, block)

    private suspend fun <T> withBenIdLocks(benIds: Collection<Long>, block: suspend () -> T): T =
        benIdLocks.withLocks(benIds, block)

    /**
     * Card-rendering columns only. The list screens decorate rows from this; nothing on a card
     * reads the other seventeen columns. See [org.piramalswasthya.stoptb.model.TbDiagnosticsCardFields].
     */
    val allTbDiagnostics: Flow<List<org.piramalswasthya.stoptb.model.TbDiagnosticsCardFields>> =
        tbDao.getAllTbDiagnosticsCardFields().shareBadges()

    /**
     * benIds of every TB_DIAGNOSTICS row, derived from [allTbDiagnostics] rather than queried
     * again. Screens that want both were running two scans of the same table per invalidation
     * - the same duplication already removed for TB_SCREENING above.
     */
    val tbDiagnosticsBenIdsFromCards: Flow<List<Long>> =
        allTbDiagnostics.map { rows -> rows.map { row -> row.benId } }.flowOn(Dispatchers.Default)

    val allTbScreening: Flow<List<TbScreeningSymptoms>> = tbDao.getAllTbScreeningSymptoms().shareBadges()

    suspend fun getDiagnosticsWithPendingOrder(): List<TBDiagnosticsCache> =
        tbLocalRepo.getDiagnosticsWithPendingOrder()

    suspend fun getDiagnosticsList(): List<TBDiagnosticsCache> =
        tbLocalRepo.getDiagnosticsList()

    suspend fun getTBScreening(benId: Long): TBScreeningCache? =
        tbLocalRepo.getTBScreening(benId)

    suspend fun saveTBScreening(tbScreeningCache: TBScreeningCache) =
        tbLocalRepo.saveTBScreening(tbScreeningCache)

    // General OPD + chief complaint / visit category masters moved to GeneralOpdRepo; these
    // delegates keep the existing callers unchanged.
    suspend fun getGeneralOpd(benId: Long): GeneralOpdCache? = generalOpdRepo.getGeneralOpd(benId)

    suspend fun getCachedChiefComplaintNames(): List<String> = generalOpdRepo.getCachedChiefComplaintNames()

    suspend fun getGeneralOpdPrescriptions(opdId: Int) = generalOpdRepo.getGeneralOpdPrescriptions(opdId)

    suspend fun saveGeneralOpdWithPrescriptions(cache: GeneralOpdCache, medicines: List<OpdMedicineDraft>) =
        generalOpdRepo.saveGeneralOpdWithPrescriptions(cache, medicines)

    internal suspend fun resolveChiefComplaintIds(cache: GeneralOpdCache): List<Int>? =
        generalOpdRepo.resolveChiefComplaintIds(cache)

    suspend fun refreshVisitCategories(): Boolean = generalOpdRepo.refreshVisitCategories()

    suspend fun refreshChiefComplaintMasters(): Boolean = generalOpdRepo.refreshChiefComplaintMasters()

    // One query per table for all three sets. These were three Room Flows over the same table,
    // so every write re-ran three queries and woke three collectors on the list screens.
    private val tbScreeningBenIdStates: Flow<List<org.piramalswasthya.stoptb.model.BenIdSyncState>> =
        tbDao.getTbScreeningBenIdSyncStates().shareBadges()

    val tbScreeningBenIds: Flow<List<Long>> = tbScreeningBenIdStates.map { rows -> rows.map { it.benId } }.flowOn(Dispatchers.Default)
    val unsyncedTbScreeningBenIds: Flow<List<Long>> =
        tbScreeningBenIdStates.map { rows -> rows.filter { it.syncState == SyncState.UNSYNCED }.map { it.benId } }.flowOn(Dispatchers.Default)
    val syncingTbScreeningBenIds: Flow<List<Long>> =
        tbScreeningBenIdStates.map { rows -> rows.filter { it.syncState == SyncState.SYNCING }.map { it.benId } }.flowOn(Dispatchers.Default)

    private val generalOpdBenIdStates: Flow<List<org.piramalswasthya.stoptb.model.BenIdSyncState>> =
        tbDao.getGeneralOpdBenIdSyncStates().shareBadges()

    val generalOpdBenIds: Flow<List<Long>> = generalOpdBenIdStates.map { rows -> rows.map { it.benId } }.flowOn(Dispatchers.Default)
    val unsyncedGeneralOpdBenIds: Flow<List<Long>> =
        generalOpdBenIdStates.map { rows -> rows.filter { it.syncState == SyncState.UNSYNCED }.map { it.benId } }.flowOn(Dispatchers.Default)
    val syncingGeneralOpdBenIds: Flow<List<Long>> =
        generalOpdBenIdStates.map { rows -> rows.filter { it.syncState == SyncState.SYNCING }.map { it.benId } }.flowOn(Dispatchers.Default)

    val tbSuspectedBenIds: Flow<List<Long>> = tbDao.getAllTbSuspectedBenIds().shareBadges()

    /** benIds that have a record in TB_DIAGNOSTICS (new diagnostics table) */
    val tbDiagnosticsBenIds: Flow<List<Long>> = tbDao.getAllTbDiagnosticsBenIds().shareBadges()

    suspend fun saveGeneralOpd(generalOpdCache: GeneralOpdCache) =
        tbLocalRepo.saveGeneralOpd(generalOpdCache)

    suspend fun getTBDiagnostics(benId: Long): TBDiagnosticsCache? =
        tbLocalRepo.getTBDiagnostics(benId)

    suspend fun getTBDiagnosticsById(benId: Long): TBDiagnosticsCache? =
        tbLocalRepo.getTBDiagnosticsById(benId)

    suspend fun saveTBDiagnostics(tbDiagnosticsCache: TBDiagnosticsCache) =
        tbLocalRepo.saveTBDiagnostics(tbDiagnosticsCache)

    suspend fun getTBSuspected(benId: Long): TBSuspectedCache? =
        tbLocalRepo.getTBSuspected(benId)

    suspend fun saveTBSuspected(tbSuspectedCache: TBSuspectedCache) =
        tbLocalRepo.saveTBSuspected(tbSuspectedCache)

    suspend fun getTBConfirmed(benId: Long): TBConfirmedTreatmentCache? =
        tbLocalRepo.getTBConfirmed(benId)

    suspend fun saveTBConfirmed(tbConfirmedTreatmentCache: TBConfirmedTreatmentCache) =
        tbLocalRepo.saveTBConfirmed(tbConfirmedTreatmentCache)

    suspend fun getAllFollowUpsForBeneficiary(benId: Long): List<TBConfirmedTreatmentCache> =
        tbLocalRepo.getAllFollowUpsForBeneficiary(benId)

    suspend fun getTBScreeningDetailsFromServer(): Int =
        tbPullRepo.getTBScreeningDetailsFromServer()

    suspend fun getGeneralOpdDetailsFromServer(): Int =
        tbPullRepo.getGeneralOpdDetailsFromServer()

    // ── Records from the changes feed, in the same JSON each getAll returns ──
    // Each goes through the same save path as the full pull, so the rules for local edits are identical.

    suspend fun applyFeedTbScreenings(records: JSONArray) =
        tbPullRepo.applyFeedTbScreenings(records)

    suspend fun applyFeedGeneralOpd(records: JSONArray) =
        tbPullRepo.applyFeedGeneralOpd(records)

    suspend fun applyFeedDiagnostics(records: JSONArray) =
        tbPullRepo.applyFeedDiagnostics(records)

    suspend fun applyFeedTbSuspected(records: JSONArray) =
        tbPullRepo.applyFeedTbSuspected(records)

    suspend fun applyFeedTbConfirmed(records: JSONArray) =
        tbPullRepo.applyFeedTbConfirmed(records)

    suspend fun applyFeedDiagnosticOrders(records: JSONArray) =
        tbPullRepo.applyFeedDiagnosticOrders(records)

    fun feedSkippedOrdersPastGrace(): List<Pair<Long, String>> =
        tbPullRepo.feedSkippedOrdersPastGrace()

    fun ackFeedSkippedOrder(benId: Long, type: String) =
        tbPullRepo.ackFeedSkippedOrder(benId, type)

    suspend fun getTbDiagnosticsDetailsFromServer(): Int =
        tbPullRepo.getTbDiagnosticsDetailsFromServer()

    suspend fun getTbSuspectedDetailsFromServer(): Int =
        tbPullRepo.getTbSuspectedDetailsFromServer()

    suspend fun getTbConfirmedDetailsFromServer(): Int =
        tbPullRepo.getTbConfirmedDetailsFromServer()

    // RECORD-LEVEL ISOLATION: Coordinator always returns true so the
    // WorkManager worker succeeds. Each sub-method handles its own failures
    // independently — failed records stay UNSYNCED and retry on next sync cycle.
    // Previously, if any sub-method failed, the coordinator returned false which
    // could cause the worker to be marked as failed.
    // Push moved to TbPushRepo; these delegates keep the existing callers unchanged.
    suspend fun pushUnSyncedRecords() = tbPushRepo.pushUnSyncedRecords()

    suspend fun pushUnSyncedTBScreeningRecords() = tbPushRepo.pushUnSyncedTBScreeningRecords()

    suspend fun pushUnSyncedRecordsTBSuspected() = tbPushRepo.pushUnSyncedRecordsTBSuspected()

    private suspend fun pushUnSyncedRecordsTBScreening() = tbPushRepo.pushUnSyncedRecordsTBScreening()

    companion object {
        /** [submitManualResult]'s "stored locally, not yet accepted by the backend" result. */
        const val PENDING_SYNC = "PENDING_SYNC"

        /** Order-id backfill state lives in DiagnosticOrderRepo. */
        fun resetOrderIdBackfillState() =
            org.piramalswasthya.stoptb.repositories.tb.DiagnosticOrderRepo.resetOrderIdBackfillState()

        internal fun canRecoverChiefComplaints(
            existing: GeneralOpdCache?,
            incoming: List<String>?,
            serverUpdatedDate: Long
        ): Boolean = existing != null && existing.syncState == SyncState.SYNCED &&
            existing.chiefComplaints.orEmpty().all { it.isBlank() } &&
            incoming.orEmpty().any { it.isNotBlank() } &&
            serverUpdatedDate > 0L && serverUpdatedDate == existing.serverUpdatedDate

        internal fun canRecoverGeneralOpdDrugs(
            existing: GeneralOpdCache?,
            incoming: List<GeneralOpdPrescription>,
            serverUpdatedDate: Long
        ): Boolean = existing != null && existing.syncState == SyncState.SYNCED &&
            incoming.isNotEmpty() && serverUpdatedDate > 0L &&
            serverUpdatedDate == existing.serverUpdatedDate

        internal fun JSONObject.optChiefComplaintsOrNull(): List<String>? {
            for (key in listOf("chiefComplaint", "chiefComplaints")) {
                val arrayValues = optStringListOrNull(key)
                    ?.map { it.trim() }?.filter { it.isNotEmpty() && !it.equals("null", true) }
                    ?.takeIf { it.isNotEmpty() }
                if (arrayValues != null) return arrayValues
                val text = optStringOrNull(key)?.trim() ?: continue
                // Older responses can return plain text instead of a JSON array.
                if (text.startsWith("[") || text.startsWith("{")) continue
                val values = text.split("|||", ",")
                    .map { it.trim() }.filter { it.isNotEmpty() }.takeIf { it.isNotEmpty() }
                if (values != null) return values
            }
            return null
        }

    }

    suspend fun submitManualResult(
        benId: Long,
        orderType: String,
        // Null for the "Not Conducted" closure path — see reasonForRefusal below.
        resultSummary: String? = null,
        // Null resultSummary + a reason here closes the order (X-Ray/TrueNat/RIF alike) instead
        // of completing it. Sent to the backend as reasonToClose.
        reasonForRefusal: String? = null,
        isRetryAfterOrderCreation: Boolean = false
    ):NetworkResponse<String> =
        manualResultRepo.submitManualResult(benId, orderType, // Null for the "Not Conducted" closure path — see reasonForRefusal below.
        resultSummary, // Null resultSummary + a reason here closes the order (X-Ray/TrueNat/RIF alike) instead
        // of completing it. Sent to the backend as reasonToClose.
        reasonForRefusal, isRetryAfterOrderCreation)

    suspend fun cascadeOrdersForConfirmedResult(
        benId: Long,
        orderType: String,
        resultSummary: String?
    ): List<String> =
        manualResultRepo.cascadeOrdersForConfirmedResult(benId, orderType, resultSummary)

    suspend fun retryPendingManualResultSyncs(): Boolean =
        manualResultRepo.retryPendingManualResultSyncs()

    suspend fun createOrder(
        benId: Long,
        testType: String,
        reasonForRefusal: String? = null
    ): NetworkResponse<String> =
        diagnosticOrderRepo.createOrder(benId, testType, reasonForRefusal)

    suspend fun retryPushOrder(
        benId: Long,
        testType: String
    ): NetworkResponse<String> =
        diagnosticOrderRepo.retryPushOrder(benId, testType)

    suspend fun syncTBSuspectedFromDiagnostics(
        benId: Long,
        diag: TBDiagnosticsCache,
        reasonForRefusalMDRRIF: String? = null // no local field carries this; caller passes it explicitly
    ) =
        diagnosticOrderRepo.syncTBSuspectedFromDiagnostics(benId, diag, reasonForRefusalMDRRIF)

    internal fun suspectedFromDiagnostics(
        existing: TBSuspectedCache?,
        benId: Long,
        diag: TBDiagnosticsCache,
        reasonForRefusalMDRRIF: String? = null
    ): TBSuspectedCache? =
        diagnosticOrderRepo.suspectedFromDiagnostics(existing, benId, diag, reasonForRefusalMDRRIF)

    suspend fun isAwaitingReconciliation(benId: Long, orderType: String, errorMsg: String?): Boolean =
        diagnosticOrderRepo.isAwaitingReconciliation(benId, orderType, errorMsg)

    suspend fun markTestCompleted(benId: Long, orderType: String): NetworkResponse<String> =
        diagnosticOrderRepo.markTestCompleted(benId, orderType)

    private suspend fun applyOrderResult(
        ben: BenRegCache,
        orderType: String,
        responseData: OrderResultData,
        orderIdAtStart: String?,
        pushSuspectedAfter: Boolean,
        rifOrderKnown: Boolean = false
    ): String =
        diagnosticOrderRepo.applyOrderResult(ben, orderType, responseData, orderIdAtStart, pushSuspectedAfter, rifOrderKnown)

    internal fun needsRifCascade(orderType: String, responseData: OrderResultData, rifOrderKnown: Boolean): Boolean =
        diagnosticOrderRepo.needsRifCascade(orderType, responseData, rifOrderKnown)

    internal fun mergeOrderResult(
        existing: TBDiagnosticsCache?,
        benId: Long,
        orderType: String,
        responseData: OrderResultData,
        status: String,
        rifFieldOverride: Pair<String, String?>?,
        truenatIntegrated: Boolean
    ): org.piramalswasthya.stoptb.repositories.tb.DiagnosticOrderRepo.MergedOrderResult =
        diagnosticOrderRepo.mergeOrderResult(existing, benId, orderType, responseData, status, rifFieldOverride, truenatIntegrated)

    suspend fun fetchOrderResult(
        benId: Long,
        orderType: String,
        pushSuspectedAfter: Boolean = true
    ): NetworkResponse<String> =
        diagnosticOrderRepo.fetchOrderResult(benId, orderType, pushSuspectedAfter)

    suspend fun awaitingResultBenIds(status: NetworkResponse<org.piramalswasthya.stoptb.network.DiagnosticBeneficiaryStatusData>): Set<Long> =
        diagnosticOrderRepo.awaitingResultBenIds(status)

    suspend fun fetchBeneficiariesByStatus(orderType: String, fetchResult: Boolean = true): NetworkResponse<DiagnosticBeneficiaryStatusData> =
        diagnosticOrderRepo.fetchBeneficiariesByStatus(orderType, fetchResult)

    // Device integration moved to DeviceIntegrationRepo; kept here so callers don't change.
    suspend fun getVendorHealth(orderType: String): NetworkResponse<String> =
        deviceIntegrationRepo.getVendorHealth(orderType)

    suspend fun checkDeviceIntegration(orderType: String): Boolean =
        deviceIntegrationRepo.checkDeviceIntegration(orderType)

    suspend fun refreshDeviceIntegrationConfig(maxAgeMs: Long = 0L) =
        deviceIntegrationRepo.refreshDeviceIntegrationConfig(maxAgeMs)

    fun isXrayIntegrated(): Boolean = deviceIntegrationRepo.isXrayIntegrated()

    fun isTruenatIntegrated(): Boolean = deviceIntegrationRepo.isTruenatIntegrated()
}
