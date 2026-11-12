package org.piramalswasthya.stoptb.repositories.tb

import android.content.Context
import androidx.room.withTransaction
import com.google.gson.Gson
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
import org.piramalswasthya.stoptb.helpers.SyncDailyStore
import org.piramalswasthya.stoptb.helpers.logSyncModule
import org.piramalswasthya.stoptb.helpers.syncPushOutcome
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
import org.piramalswasthya.stoptb.repositories.UserRepo
import org.piramalswasthya.stoptb.utils.ServerDateParser
import org.piramalswasthya.stoptb.utils.throttleLatest
import org.piramalswasthya.stoptb.work.WorkerUtils
import timber.log.Timber

/**
 * Pushes unsynced TB screening, General OPD, diagnostics, suspected and confirmed records,
 * moved out of TBRepo unchanged. TBRepo delegates its push entry points here.
 */
@Singleton
class TbPushRepo @Inject constructor(
    private val tbDao: TBDao,
    private val benDao: BenDao,
    private val preferenceDao: PreferenceDao,
    private val userRepo: UserRepo,
    private val tmcNetworkApiService: AmritApiService,
    private val database: InAppDb,
    private val syncRetryStore: SyncRetryStore,
    private val generalOpdRepo: GeneralOpdRepo,
    private val benIdLocks: BenIdLocks
) {
    private companion object {
        // Returned by the TB push sub-functions when a network error stopped the run.
        const val PUSH_NETWORK_ERROR = -1
        // Same size as the diagnostics / suspected / confirmed pushes.
        const val TB_SCREENING_CHUNK_SIZE = 20
    }

    // Serializes pushUnSyncedRecordsTBSuspected() against itself — it reads all UNSYNCED rows,
    // posts them, then marks that snapshot synced with no claim/lease step, so overlapping calls
    // (createOrder/retryPushOrder/fetchOrderResult plus the generic push sweep) can double-POST
    // the same row. Never called from inside a withBenIdLock block, so safe to hold separately.
    private val tbSuspectedPushMutex = Mutex()

    // Same issue, same fix, for pushUnSyncedRecordsTBScreening(): TBScreeningFormViewModel also
    // calls it directly after a save, in addition to the generic push sweep.
    private val tbScreeningPushMutex = Mutex()
    private val generalOpdPushMutex = Mutex()
    // PR 7.4: the diagnostics and confirmed pushes had no lock, so two overlapping sync runs could
    // POST the same rows twice. Same pattern as the screening / suspected / OPD pushes.
    private val tbDiagnosticsPushMutex = Mutex()
    private val tbConfirmedPushMutex = Mutex()

    suspend fun pushUnSyncedRecords(): Boolean {
        // Stops at the first module that hits a network error: with the hub unreachable, every
        // later module with pending rows would only wait out its own timeout before failing too.
        val modules: List<suspend () -> Int> = listOf(
            { pushUnSyncedRecordsTBScreening() },
            { pushUnSyncedRecordsGeneralOpd() },
            { pushUnSyncedRecordsTBDiagnostics() },
            { pushUnSyncedRecordsTBSuspected() },
            { pushUnSyncedRecordsTBConfirmed() },
        )
        val results = mutableListOf<Int>()
        for (pushModule in modules) {
            val result = pushModule()
            results += result
            if (result == PUSH_NETWORK_ERROR) break
        }
        Timber.d("TB push results (screening, generalOpd, diagnostics, suspected, confirmed): $results")
        if (PUSH_NETWORK_ERROR in results) {
            // Lets PushTBToAmritWorker retry with backoff instead of reporting success.
            throw IOException("TB push interrupted by a network error")
        }
        // Records the server rejected stay UNSYNCED for the next cycle.
        return true
    }

    /**
     * Marks rows the hub has already accepted. A local failure here must not reach the chunk's
     * generic catch, which treats the chunk as rejected and re-sends each record on its own in
     * the same run. The rows stay UNSYNCED and go out again on the next cycle instead.
     */
    private suspend fun markSyncedAfterAck(markSynced: suspend () -> Int): Int = try {
        markSynced()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Could not mark acknowledged TB records synced; they stay UNSYNCED for the next cycle")
        0
    }

    suspend fun pushUnSyncedTBScreeningRecords(): Int {
        return pushUnSyncedRecordsTBScreening()
    }

    // PR 7: TB Screening is pushed in chunks of TB_SCREENING_CHUNK_SIZE, like diagnostics,
    // suspected and confirmed. It had gone to one record per request (0875cbe9, nurse-flow
    // endpoint), which at 2,000 pending records meant ~1,200 requests and as many sync-state
    // writes, each re-running every observed list query (1M device test: 11 records/min).
    // stoptb/nurse/tbScreening/save takes a list and saves it in one transaction with the same
    // per-beneficiary, per-day visit handling as single sends. A rejected chunk is re-queued one
    // record at a time (rejectTbChunk), so one bad record no longer holds back the rest, and a
    // record the server keeps rejecting backs off and shows on the sync dashboard
    // (SyncRetryStore). Only records actually sent are marked SYNCED, in one transaction per
    // chunk, and only if unchanged since they were read (updateSyncStatusScreening).
    internal suspend fun pushUnSyncedRecordsTBScreening(): Int = tbScreeningPushMutex.withLock {

        withContext(Dispatchers.IO) {
            val user =
                preferenceDao.getLoggedInUser()
                    ?: throw IllegalStateException("No user logged in!!")

            val tbsnList: List<TBScreeningCache> = tbDao.getTBScreening(SyncState.UNSYNCED)

            if (tbsnList.isEmpty()) {
                logSyncModule("TB_SCREENING", "PUSH", "NO_PENDING_DATA", "pending" to 0)
                return@withContext 1
            }

            val queue = tbUploadQueue(tbsnList, TB_SCREENING_CHUNK_SIZE, SyncRetryStore.ENTITY_TB_SCREENING) { it.id.toLong() }
            val rejectedSingles = mutableListOf<TBScreeningCache>()
            var lastError = ""
            var successCount = 0
            var failCount = 0
            var sentCount = 0
            var acknowledgedCount = 0
            var networkFailed = false

            while (queue.isNotEmpty()) {
                val chunk = queue.removeFirst()
                val diagnosticSentIds = mutableSetOf<Int>()
                try {
                    // Only what is sent may be marked SYNCED: a row whose beneficiary has no
                    // server benRegId yet stays UNSYNCED for the next cycle.
                    val sentRecords = mutableListOf<TBScreeningCache>()
                    val request = chunk.mapNotNull { screening ->
                        val beneficiaryRegID = benDao.getBen(screening.benId)?.benRegId?.takeIf { it > 0L }
                        if (beneficiaryRegID == null) {
                            SyncDailyStore.record("TB_SCREENING", listOf(screening.id), "PUSH", "PENDING", "SERVER_REGISTRATION_ID_NOT_AVAILABLE")
                            null
                        } else {
                            sentRecords += screening
                            TBScreeningSaveRequest.from(
                                cache = screening,
                                beneficiaryRegID = beneficiaryRegID,
                                providerServiceMapID = user.serviceMapId,
                                createdBy = user.userName
                            )
                        }
                    }
                    val notSent = chunk.size - sentRecords.size
                    if (request.isEmpty()) {
                        failCount += chunk.size
                        continue
                    }
                    diagnosticSentIds.addAll(sentRecords.map { it.id })
                    sentCount += request.size
                    val response = tmcNetworkApiService.saveTBScreeningData(request)
                    val statusCode = response.code()
                    if (statusCode == 200) {
                        val responseString = SyncDailyStore.observedBody("TB_SCREENING", diagnosticSentIds, response)
                        if (responseString != null) {
                            val jsonObj = JSONObject(responseString)
                            val responseStatusCode = jsonObj.getInt("statusCode")
                            Timber.d("Push to Amrit TB Screening chunk of ${request.size}: $responseStatusCode")
                            when (responseStatusCode) {
                                200 -> {
                                    if (notSent > 0) {
                                        failCount += notSent
                                        Timber.w("TB Screening push: $notSent record(s) not sent (no server benRegId yet), stay UNSYNCED")
                                    }
                                    acknowledgedCount += sentRecords.size
                                    successCount += markSyncedAfterAck { updateSyncStatusScreening(sentRecords) }
                                    syncRetryStore.clear(SyncRetryStore.ENTITY_TB_SCREENING, sentRecords.map { it.id.toLong() })
                                }

                                401, 5002 -> {
                                    if (userRepo.refreshTokenTmc(user.userName, user.password)) {
                                        Timber.d("Token refreshed, TB Screening chunk will retry next cycle")
                                    }
                                    failCount += chunk.size
                                }

                                else -> {
                                    Timber.e("TB Screening chunk failed with statusCode: $responseStatusCode")
                                    lastError = "statusCode=$responseStatusCode"
                                    failCount += rejectTbChunk(chunk, queue, rejectedSingles)
                                }
                            }
                        }
                    } else {
                        Timber.e("TB Screening chunk HTTP error: $statusCode")
                        SyncDailyStore.response("TB_SCREENING", diagnosticSentIds, statusCode, null)
                        lastError = "HTTP $statusCode"
                        failCount += rejectTbChunk(chunk, queue, rejectedSingles)
                    }
                } catch (e: CancellationException) {
                    // A cancelled chain is not a server rejection: let WorkManager stop the run.
                    throw e
                } catch (e: IOException) {
                    // Hub unreachable: stop this run and let the push worker retry with backoff.
                    Timber.e("TB Screening push stopped by network error: $e")
                    SyncDailyStore.record("TB_SCREENING", diagnosticSentIds, "PUSH", "FAILURE", "NETWORK_${e.javaClass.simpleName}")
                    networkFailed = true
                    break
                } catch (e: Exception) {
                    Timber.e(e, "TB Screening chunk push failed: ${chunk.size} records")
                    SyncDailyStore.record("TB_SCREENING", diagnosticSentIds, "PUSH", "FAILURE", "EXCEPTION_${e.javaClass.simpleName}")
                    lastError = e.message ?: e.javaClass.simpleName
                    failCount += rejectTbChunk(chunk, queue, rejectedSingles)
                }
            }

            recordTbRejections(SyncRetryStore.ENTITY_TB_SCREENING, rejectedSingles, successCount > 0, lastError) { it.id.toLong() }
            Timber.d("TB Screening push complete: $successCount succeeded, $failCount failed out of ${tbsnList.size}")
            logSyncModule("TB_SCREENING", "PUSH", syncPushOutcome(tbsnList.size, minOf(successCount, acknowledgedCount)),
                "pending" to tbsnList.size, "sent" to sentCount,
                "acknowledgedBatchItems" to acknowledgedCount, "locallyMarkedSynced" to successCount,
                "remaining" to tbsnList.size - successCount)
            // Worker succeeds — failed records stay UNSYNCED for next cycle
            return@withContext if (networkFailed) PUSH_NETWORK_ERROR else 1
        }
    }

    private suspend fun pushUnSyncedRecordsGeneralOpd(): Int {
        return generalOpdPushMutex.withLock { withContext(Dispatchers.IO) {
            val user =
                preferenceDao.getLoggedInUser()
                    ?: throw IllegalStateException("No user logged in!!")

            val opdList: List<GeneralOpdCache> = tbDao.getGeneralOpd(SyncState.UNSYNCED)
            if (opdList.isEmpty()) {
                logSyncModule("GENERAL_OPD", "PUSH", "NO_PENDING_DATA", "pending" to 0)
                return@withContext 1
            }

            // Keep the list-shaped endpoint, but acknowledge each OPD independently.
            val chunks = opdList.chunked(1)
            var successCount = 0
            var failCount = 0
            var sentCount = 0
            var acknowledgedCount = 0
            var markedSyncedCount = 0
            var networkFailed = false

            for (chunk in chunks) {
                val diagnosticSentIds = mutableSetOf<Int>()
                try {
                    val prepared = chunk.mapNotNull { opd ->
                        val benRegId = benDao.getBen(opd.benId)?.benRegId?.takeIf { it > 0L }
                        if (benRegId == null) {
                            SyncDailyStore.record("GENERAL_OPD", listOf(opd.id), "PUSH", "PENDING", "SERVER_REGISTRATION_ID_NOT_AVAILABLE")
                            return@mapNotNull null
                        }
                        try {
                            generalOpdRepo.prepareGeneralOpdRequest(opd.id, benRegId, user.serviceMapId, user.userName)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: IllegalArgumentException) {
                            Timber.w("General OPD id=%s remains pending: unresolved or incomplete prescription data", opd.id)
                            SyncDailyStore.record("GENERAL_OPD", listOf(opd.id), "PUSH", "PENDING", "PRESCRIPTION_INCOMPLETE_OR_UNRESOLVED")
                            null
                        }
                    }
                    failCount += chunk.size - prepared.size
                    if (prepared.isEmpty()) {
                        continue
                    }
                    diagnosticSentIds.addAll(prepared.map { it.first.id })
                    sentCount += prepared.size
                    val response = tmcNetworkApiService.saveGeneralOpdData(
                        prepared.map { it.second }
                    )
                    val statusCode = response.code()
                    if (statusCode == 200) {
                        val responseString = SyncDailyStore.observedBody("GENERAL_OPD", prepared.map { it.first.id }, response)
                        if (responseString != null) {
                            val jsonObj = JSONObject(responseString)
                            when (val responseStatusCode = jsonObj.getInt("statusCode")) {
                                200 -> {
                                    acknowledgedCount += prepared.size
                                    markedSyncedCount += markSyncedAfterAck { updateSyncStatusGeneralOpd(prepared) }
                                    successCount += prepared.size
                                }

                                401, 5002 -> {
                                    if (userRepo.refreshTokenTmc(user.userName, user.password)) {
                                        Timber.d("Token refreshed, General OPD chunk will retry next cycle")
                                    }
                                    SyncDailyStore.response("GENERAL_OPD", prepared.map { it.first.id }, statusCode, responseString)
                                    failCount += prepared.size
                                }

                                else -> {
                                    Timber.e("General OPD chunk failed with statusCode: $responseStatusCode")
                                    SyncDailyStore.response("GENERAL_OPD", prepared.map { it.first.id }, statusCode, responseString)
                                    failCount += prepared.size
                                }
                            }
                        }
                    } else {
                        Timber.e("General OPD chunk HTTP error: $statusCode")
                        SyncDailyStore.response("GENERAL_OPD", prepared.map { it.first.id }, statusCode, null)
                        failCount += prepared.size
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: IOException) {
                    // Hub unreachable: stop this run and let the push worker retry with backoff.
                    Timber.e("General OPD push stopped by network error: $e")
                    SyncDailyStore.record("GENERAL_OPD", diagnosticSentIds, "PUSH", "FAILURE", "NETWORK_${e.javaClass.simpleName}")
                    networkFailed = true
                    break
                } catch (e: Exception) {
                    Timber.e(e, "General OPD chunk push failed: ${chunk.size} records")
                    SyncDailyStore.record("GENERAL_OPD", diagnosticSentIds, "PUSH", "FAILURE", "EXCEPTION_${e.javaClass.simpleName}")
                    failCount += chunk.size
                }
            }

            Timber.d("General OPD push complete: $successCount succeeded, $failCount failed out of ${opdList.size}")
            logSyncModule("GENERAL_OPD", "PUSH", syncPushOutcome(opdList.size, minOf(markedSyncedCount, acknowledgedCount)),
                "pending" to opdList.size, "sent" to sentCount,
                "acknowledgedBatchItems" to acknowledgedCount, "locallyMarkedSynced" to markedSyncedCount,
                "remaining" to opdList.size - markedSyncedCount)
            return@withContext if (networkFailed) PUSH_NETWORK_ERROR else 1
        } }
    }

    private suspend fun pushUnSyncedRecordsTBDiagnostics(): Int = tbDiagnosticsPushMutex.withLock {
        withContext(Dispatchers.IO) {
            val user =
                preferenceDao.getLoggedInUser()
                    ?: throw IllegalStateException("No user logged in!!")

            val diagnosticsList: List<TBDiagnosticsCache> =
                tbDao.getTbDiagnostics(SyncState.UNSYNCED)
            if (diagnosticsList.isEmpty()) {
                logSyncModule("TB_DIAGNOSTICS", "PUSH", "NO_PENDING_DATA", "pending" to 0)
                return@withContext 1
            }

            val queue = tbUploadQueue(diagnosticsList, 20, SyncRetryStore.ENTITY_TB_DIAGNOSTICS) { it.id.toLong() }
            val rejectedSingles = mutableListOf<TBDiagnosticsCache>()
            var lastError = ""
            var successCount = 0
            var failCount = 0
            var sentCount = 0
            var acknowledgedCount = 0
            var markedSyncedCount = 0
            var networkFailed = false

            while (queue.isNotEmpty()) {
                val chunk = queue.removeFirst()
                val diagnosticSentIds = mutableSetOf<Int>()
                try {
                    // See the TB Suspected push below: only what is actually sent may be
                    // marked SYNCED. A row whose beneficiary has no server benRegId yet is
                    // dropped here, and updateSyncStatusDiagnostics would otherwise find it
                    // unchanged against its snapshot and flip it to SYNCED regardless.
                    val excludedBenIds = mutableListOf<Long>()
                    val sentRecords = mutableListOf<TBDiagnosticsCache>()
                    val sentIds = mutableSetOf<Int>()
                    val request = chunk.mapNotNull { diagnostics ->
                        val benRegId = benDao.getBen(diagnostics.benId)?.benRegId?.takeIf { it > 0L }
                        if (benRegId == null) {
                            excludedBenIds += diagnostics.benId
                            null
                        } else {
                            sentRecords += diagnostics
                            TBDiagnosticsSaveRequest.from(
                                cache = diagnostics,
                                benRegID = benRegId,
                                providerServiceMapID = user.serviceMapId,
                                createdBy = user.userName
                            ).also { sentIds.add(diagnostics.id) }
                        }
                    }
                    if (request.isEmpty()) {
                        failCount += chunk.size
                        continue
                    }
                    diagnosticSentIds.addAll(sentRecords.map { it.id })
                    sentCount += request.size
                    val response = tmcNetworkApiService.saveTBDiagnosticsData(
                        request
                    )
                    val statusCode = response.code()
                    if (statusCode == 200) {
                        val responseString = SyncDailyStore.observedBody("TB_DIAGNOSTICS", sentIds, response)
                        if (responseString != null) {
                            val jsonObj = JSONObject(responseString)
                            when (val responseStatusCode = jsonObj.getInt("statusCode")) {
                                200 -> {
                                    if (excludedBenIds.isNotEmpty()) {
                                        failCount += excludedBenIds.size
                                        Timber.w("TB Diagnostics push: benIds=$excludedBenIds were NOT sent (no server benRegId yet) and stay UNSYNCED for the next cycle")
                                    }
                                    acknowledgedCount += sentRecords.size
                                    markedSyncedCount += markSyncedAfterAck { updateSyncStatusDiagnostics(sentRecords) }
                                    syncRetryStore.clear(SyncRetryStore.ENTITY_TB_DIAGNOSTICS, sentRecords.map { it.id.toLong() })
                                    successCount += sentRecords.size
                                }

                                401, 5002 -> {
                                    if (userRepo.refreshTokenTmc(user.userName, user.password)) {
                                        Timber.d("Token refreshed, TB Diagnostics chunk will retry next cycle")
                                    }
                                    SyncDailyStore.response("TB_DIAGNOSTICS", sentIds, statusCode, responseString)
                                    failCount += chunk.size
                                }

                                else -> {
                                    Timber.e("TB Diagnostics chunk failed with statusCode: $responseStatusCode")
                                    SyncDailyStore.response("TB_DIAGNOSTICS", sentIds, statusCode, responseString)
                                    lastError = "statusCode=$responseStatusCode"
                                    failCount += rejectTbChunk(chunk, queue, rejectedSingles)
                                }
                            }
                        }
                    } else {
                        Timber.e("TB Diagnostics chunk HTTP error: $statusCode")
                        SyncDailyStore.response("TB_DIAGNOSTICS", sentIds, statusCode, null)
                        lastError = "HTTP $statusCode"
                        failCount += rejectTbChunk(chunk, queue, rejectedSingles)
                    }
                } catch (e: CancellationException) {
                    // A cancelled chain is not a server rejection: let WorkManager stop the run.
                    throw e
                } catch (e: IOException) {
                    // Hub unreachable: stop this run and let the push worker retry with backoff.
                    Timber.e("TB Diagnostics push stopped by network error: $e")
                    SyncDailyStore.record("TB_DIAGNOSTICS", diagnosticSentIds, "PUSH", "FAILURE", "NETWORK_${e.javaClass.simpleName}")
                    networkFailed = true
                    break
                } catch (e: Exception) {
                    Timber.e(e, "TB Diagnostics chunk push failed: ${chunk.size} records")
                    SyncDailyStore.record("TB_DIAGNOSTICS", diagnosticSentIds, "PUSH", "FAILURE", "EXCEPTION_${e.javaClass.simpleName}")
                    lastError = e.message ?: e.javaClass.simpleName
                    failCount += rejectTbChunk(chunk, queue, rejectedSingles)
                }
            }

            recordTbRejections(SyncRetryStore.ENTITY_TB_DIAGNOSTICS, rejectedSingles, successCount > 0, lastError) { it.id.toLong() }
            Timber.d("TB Diagnostics push complete: $successCount succeeded, $failCount failed out of ${diagnosticsList.size}")
            logSyncModule("TB_DIAGNOSTICS", "PUSH", syncPushOutcome(diagnosticsList.size, minOf(markedSyncedCount, acknowledgedCount)),
                "pending" to diagnosticsList.size, "sent" to sentCount,
                "acknowledgedBatchItems" to acknowledgedCount, "locallyMarkedSynced" to markedSyncedCount,
                "remaining" to diagnosticsList.size - markedSyncedCount)
            return@withContext if (networkFailed) PUSH_NETWORK_ERROR else 1
        }
    }

    // RECORD-LEVEL ISOLATION: Same chunking pattern as TB Screening.
    // Records sent in chunks of 20 with per-chunk error isolation.
    suspend fun pushUnSyncedRecordsTBSuspected(): Int = tbSuspectedPushMutex.withLock {
        withContext(Dispatchers.IO) {
            val user =
                preferenceDao.getLoggedInUser()
                    ?: throw IllegalStateException("No user logged in!!")

            val tbspList: List<TBSuspectedCache> = tbDao.getTbSuspected(SyncState.UNSYNCED)

            if (tbspList.isEmpty()) {
                logSyncModule("TB_SUSPECTED", "PUSH", "NO_PENDING_DATA", "pending" to 0)
                return@withContext 1
            }

            val callTag = java.util.UUID.randomUUID().toString().take(6)
            Timber.d("SYNC_DIAG[$callTag] TB Suspected push: ${tbspList.size} unsynced records, benIds=${tbspList.map { it.benId }}")

            val CHUNK_SIZE = 20
            val queue = tbUploadQueue(tbspList, CHUNK_SIZE, SyncRetryStore.ENTITY_TB_SUSPECTED) { it.id.toLong() }
            val rejectedSingles = mutableListOf<TBSuspectedCache>()
            var lastError = ""
            var successCount = 0
            var failCount = 0
            var sentCount = 0
            var acknowledgedCount = 0
            var markedSyncedCount = 0
            var networkFailed = false

            while (queue.isNotEmpty()) {
                val chunk = queue.removeFirst()
                val diagnosticSentIds = mutableSetOf<Int>()
                try {
                    val excludedBenIds = mutableListOf<Long>()
                    // Only the records that actually make it into the payload may be marked
                    // SYNCED afterwards. Passing the whole `chunk` to updateSyncStatusSuspected
                    // marked the excluded ones SYNCED too, so a TB Suspected row whose
                    // BENEFICIARY had not been pulled yet - routine in camp mode - was silently
                    // dropped from the payload and then became invisible to every future push.
                    val sentRecords = mutableListOf<TBSuspectedCache>()
                    val chunkDtos = chunk.mapNotNull { suspected ->
                        val ben = benDao.getBen(suspected.benId)
                        if (ben == null) {
                            excludedBenIds += suspected.benId
                            Timber.w("SYNC_DIAG[$callTag] TB Suspected push: benId=${suspected.benId} (local id=${suspected.id}, visitDate=${suspected.visitDate}) has NO matching BENEFICIARY row locally — excluded from push payload, left UNSYNCED to retry")
                            null
                        } else {
                            sentRecords += suspected
                            suspected.toDTO()
                        }
                    }
                    Timber.d("SYNC_DIAG[$callTag] TB Suspected push: chunk benIds=${chunk.map { it.benId }} -> ${chunkDtos.size}/${chunk.size} resolved for sending")
                    if (chunkDtos.isEmpty()) {
                        failCount += chunk.size
                        Timber.w("SYNC_DIAG[$callTag] TB Suspected push: entire chunk skipped (no resolvable records), benIds=${chunk.map { it.benId }}")
                        continue
                    }

                    diagnosticSentIds.addAll(sentRecords.map { it.id })
                    sentCount += chunkDtos.size
                    val response = tmcNetworkApiService.saveTBSuspectedData(
                        TBSuspectedRequestDTO(
                            userId = user.userId,
                            tbSuspectedList = chunkDtos
                        )
                    )
                    val statusCode = response.code()
                    if (statusCode == 200) {
                        val responseString = SyncDailyStore.observedBody("TB_SUSPECTED", chunk.filter { it.benId !in excludedBenIds }.map { it.id }, response)
                        if (responseString != null) {
                            val jsonObj = JSONObject(responseString)
                            val responseStatusCode = jsonObj.getInt("statusCode")
                            Timber.d("Push to Amrit TB Suspected chunk: $responseStatusCode, callTag=$callTag, benIds=${chunk.map { it.benId }}")
                            when (responseStatusCode) {
                                200 -> {
                                    if (excludedBenIds.isNotEmpty()) {
                                        failCount += excludedBenIds.size
                                        Timber.w("SYNC_DIAG[$callTag] TB Suspected push: benIds=$excludedBenIds were NOT sent (benId resolution failed above) and stay UNSYNCED for the next cycle")
                                    }
                                    acknowledgedCount += sentRecords.size
                                    markedSyncedCount += markSyncedAfterAck { updateSyncStatusSuspected(sentRecords) }
                                    syncRetryStore.clear(SyncRetryStore.ENTITY_TB_SUSPECTED, sentRecords.map { it.id.toLong() })
                                    successCount += sentRecords.size
                                    Timber.d("SYNC_DIAG[$callTag] TB Suspected push: chunk marked SYNCED, benIds=${sentRecords.map { it.benId }}")
                                }

                                401, 5002 -> {
                                    if (userRepo.refreshTokenTmc(user.userName, user.password)) {
                                        Timber.d("Token refreshed, TB Suspected chunk will retry next cycle")
                                    }
                                    failCount += chunk.size
                                    Timber.w("SYNC_DIAG[$callTag] TB Suspected push: token issue, benIds=${chunk.map { it.benId }}")
                                    SyncDailyStore.response("TB_SUSPECTED", chunk.filter { it.benId !in excludedBenIds }.map { it.id }, statusCode, responseString)
                                }

                                else -> {
                                    Timber.e("SYNC_DIAG[$callTag] TB Suspected chunk failed with statusCode: $responseStatusCode, benIds=${chunk.map { it.benId }}, body=$responseString")
                                    SyncDailyStore.response("TB_SUSPECTED", chunk.filter { it.benId !in excludedBenIds }.map { it.id }, statusCode, responseString)
                                    lastError = "statusCode=$responseStatusCode"
                                    failCount += rejectTbChunk(chunk, queue, rejectedSingles)
                                }
                            }
                        }
                    } else {
                        Timber.e("SYNC_DIAG[$callTag] TB Suspected chunk HTTP error: $statusCode, benIds=${chunk.map { it.benId }}")
                        SyncDailyStore.response("TB_SUSPECTED", chunk.filter { it.benId !in excludedBenIds }.map { it.id }, statusCode, null)
                        lastError = "HTTP $statusCode"
                        failCount += rejectTbChunk(chunk, queue, rejectedSingles)
                    }
                } catch (e: CancellationException) {
                    // A cancelled chain is not a server rejection: let WorkManager stop the run.
                    throw e
                } catch (e: IOException) {
                    // Hub unreachable: stop this run and let the push worker retry with backoff.
                    Timber.e("TB Suspected push stopped by network error: $e")
                    SyncDailyStore.record("TB_SUSPECTED", diagnosticSentIds, "PUSH", "FAILURE", "NETWORK_${e.javaClass.simpleName}")
                    networkFailed = true
                    break
                } catch (e: Exception) {
                    Timber.e(e, "SYNC_DIAG[$callTag] TB Suspected chunk push failed: ${chunk.size} records, benIds=${chunk.map { it.benId }}")
                    SyncDailyStore.record("TB_SUSPECTED", diagnosticSentIds, "PUSH", "FAILURE", "EXCEPTION_${e.javaClass.simpleName}")
                    lastError = e.message ?: e.javaClass.simpleName
                    failCount += rejectTbChunk(chunk, queue, rejectedSingles)
                }
            }

            recordTbRejections(SyncRetryStore.ENTITY_TB_SUSPECTED, rejectedSingles, successCount > 0, lastError) { it.id.toLong() }
            Timber.d("SYNC_DIAG[$callTag] TB Suspected push complete: $successCount succeeded, $failCount failed out of ${tbspList.size}")
            logSyncModule("TB_SUSPECTED", "PUSH", syncPushOutcome(tbspList.size, minOf(markedSyncedCount, acknowledgedCount)),
                "pending" to tbspList.size, "sent" to sentCount,
                "acknowledgedBatchItems" to acknowledgedCount, "locallyMarkedSynced" to markedSyncedCount,
                "remaining" to tbspList.size - markedSyncedCount)
            return@withContext if (networkFailed) PUSH_NETWORK_ERROR else 1
        }
    }

    // RECORD-LEVEL ISOLATION: Same chunking pattern as TB Screening.
    // Records sent in chunks of 20 with per-chunk error isolation.
    private suspend fun pushUnSyncedRecordsTBConfirmed(): Int = tbConfirmedPushMutex.withLock {
        withContext(Dispatchers.IO) {
            val user =
                preferenceDao.getLoggedInUser()
                    ?: throw IllegalStateException("No user logged in!!")

            val tbspList: List<TBConfirmedTreatmentCache> = tbDao.getTbConfirmed(SyncState.UNSYNCED)

            if (tbspList.isEmpty()) {
                logSyncModule("TB_CONFIRMED", "PUSH", "NO_PENDING_DATA", "pending" to 0)
                return@withContext 1
            }

            val CHUNK_SIZE = 20
            val queue = tbUploadQueue(tbspList, CHUNK_SIZE, SyncRetryStore.ENTITY_TB_CONFIRMED) { it.id.toLong() }
            val rejectedSingles = mutableListOf<TBConfirmedTreatmentCache>()
            var lastError = ""
            var successCount = 0
            var markedSyncedCount = 0
            var failCount = 0
            var sentCount = 0
            var acknowledgedCount = 0
            var networkFailed = false

            while (queue.isNotEmpty()) {
                val chunk = queue.removeFirst()
                val diagnosticSentIds = mutableSetOf<Int>()
                try {
                    // See the TB Suspected push above: only what is actually sent may be
                    // marked SYNCED, or a record whose BENEFICIARY has not been pulled yet is
                    // dropped from the payload and then never retried.
                    val excludedBenIds = mutableListOf<Long>()
                    val sentRecords = mutableListOf<TBConfirmedTreatmentCache>()
                    val chunkDtos = chunk.mapNotNull { confirmed ->
                        val ben = benDao.getBen(confirmed.benId)
                        if (ben == null) {
                            excludedBenIds += confirmed.benId
                            null
                        } else {
                            sentRecords += confirmed
                            confirmed.toDTO()
                        }
                    }
                    if (chunkDtos.isEmpty()) {
                        failCount += chunk.size
                        continue
                    }

                    diagnosticSentIds.addAll(sentRecords.map { it.id })
                    sentCount += chunkDtos.size
                    val response = tmcNetworkApiService.saveTBConfirmedData(
                        TBConfirmedRequestDTO(
                            userId = user.userId,
                            tbConfirmedList = chunkDtos
                        )
                    )
                    val statusCode = response.code()
                    if (statusCode == 200) {
                        val responseString = SyncDailyStore.observedBody("TB_CONFIRMED", chunk.filter { item -> chunkDtos.any { it.benId == item.benId } }.map { it.id }, response)
                        if (responseString != null) {
                            val jsonObj = JSONObject(responseString)
                            val responseStatusCode = jsonObj.getInt("statusCode")
                            Timber.d("Push to Amrit TB Confirmed chunk: $responseStatusCode")
                            when (responseStatusCode) {
                                200 -> {
                                    if (excludedBenIds.isNotEmpty()) {
                                        failCount += excludedBenIds.size
                                        Timber.w("TB Confirmed push: benIds=$excludedBenIds were NOT sent (no local BENEFICIARY row) and stay UNSYNCED for the next cycle")
                                    }
                                    acknowledgedCount += sentRecords.size
                                    markedSyncedCount += markSyncedAfterAck { updateSyncStatusConfirmed(sentRecords) }
                                    syncRetryStore.clear(SyncRetryStore.ENTITY_TB_CONFIRMED, sentRecords.map { it.id.toLong() })
                                    successCount += sentRecords.size
                                }

                                401, 5002 -> {
                                    if (userRepo.refreshTokenTmc(user.userName, user.password)) {
                                        Timber.d("Token refreshed, TB Confirmed chunk will retry next cycle")
                                    }
                                    SyncDailyStore.response("TB_CONFIRMED", diagnosticSentIds, statusCode, responseString)
                                    failCount += chunk.size
                                }

                                else -> {
                                    Timber.e("TB Confirmed chunk failed with statusCode: $responseStatusCode")
                                    SyncDailyStore.response("TB_CONFIRMED", diagnosticSentIds, statusCode, responseString)
                                    lastError = "statusCode=$responseStatusCode"
                                    failCount += rejectTbChunk(chunk, queue, rejectedSingles)
                                }
                            }
                        }
                    } else {
                        Timber.e("TB Confirmed chunk HTTP error: $statusCode")
                        SyncDailyStore.response("TB_CONFIRMED", diagnosticSentIds, statusCode, null)
                        lastError = "HTTP $statusCode"
                        failCount += rejectTbChunk(chunk, queue, rejectedSingles)
                    }
                } catch (e: CancellationException) {
                    // A cancelled chain is not a server rejection: let WorkManager stop the run.
                    throw e
                } catch (e: IOException) {
                    // Hub unreachable: stop this run and let the push worker retry with backoff.
                    Timber.e("TB Confirmed push stopped by network error: $e")
                    SyncDailyStore.record("TB_CONFIRMED", diagnosticSentIds, "PUSH", "FAILURE", "NETWORK_${e.javaClass.simpleName}")
                    networkFailed = true
                    break
                } catch (e: Exception) {
                    Timber.e(e, "TB Confirmed chunk push failed: ${chunk.size} records")
                    SyncDailyStore.record("TB_CONFIRMED", diagnosticSentIds, "PUSH", "FAILURE", "EXCEPTION_${e.javaClass.simpleName}")
                    lastError = e.message ?: e.javaClass.simpleName
                    failCount += rejectTbChunk(chunk, queue, rejectedSingles)
                }
            }

            recordTbRejections(SyncRetryStore.ENTITY_TB_CONFIRMED, rejectedSingles, successCount > 0, lastError) { it.id.toLong() }
            Timber.d("TB Confirmed push complete: $successCount succeeded, $failCount failed out of ${tbspList.size}")
            logSyncModule("TB_CONFIRMED", "PUSH", syncPushOutcome(tbspList.size, minOf(markedSyncedCount, acknowledgedCount)),
                "pending" to tbspList.size, "sent" to sentCount,
                "acknowledgedBatchItems" to acknowledgedCount, "locallyMarkedSynced" to markedSyncedCount,
                "remaining" to tbspList.size - markedSyncedCount)
            return@withContext if (networkFailed) PUSH_NETWORK_ERROR else 1
        }
    }


    // Each of these runs after a push chunk comes back 200 and flips that chunk to SYNCED. They
    // wrote one row per commit, so a 20-row chunk produced 20 separate transactions and therefore
    // 20 Room invalidations. TB_SUSPECTED and TB_SCREENING are both referenced by the
    // BEN_BASIC_CACHE view, so each of those invalidations threw away the list screens'
    // PagingSource mid-load and re-ran every observer flow over the table. Measured on the device
    // while a push was running: 83 PagingSource generations and 93 diagnosisBenIds emissions
    // (929k boxed Longs) in 171s, with each list REFRESH taking 1.8-3.6s for a query that costs
    // 54ms - the list never settled. One transaction per chunk makes that one invalidation.
    // Each row is re-read inside that transaction and marked SYNCED only if it is unchanged since it
    // was pushed, so an edit saved while the push was in flight stays UNSYNCED and goes next push.
    private suspend fun updateSyncStatusScreening(tbsnList: List<TBScreeningCache>): Int {
        if (tbsnList.isEmpty()) return 0
        val markedIds = mutableListOf<Int>()
        database.withTransaction {
            tbsnList.forEach { pushed ->
                val fresh = tbDao.getTbScreeningById(pushed.id) ?: return@forEach
                if (fresh.copy(syncState = SyncState.SYNCED) == pushed.copy(syncState = SyncState.SYNCED)) {
                    tbDao.saveTbScreening(fresh.copy(syncState = SyncState.SYNCED))
                    markedIds.add(fresh.id)
                }
            }
        }
        SyncDailyStore.record("TB_SCREENING", markedIds, "PUSH", "SUCCESS")
        return markedIds.size
    }

    private suspend fun updateSyncStatusGeneralOpd(opdList: List<Pair<GeneralOpdCache, GeneralOpdSaveRequest>>): Int {
        var marked = 0
        val markedIds = mutableListOf<Int>()
        database.withTransaction {
            opdList.forEach { (pushed, request) ->
                val current = tbDao.getGeneralOpdById(pushed.id)
                if (current != null && current == pushed) {
                    val currentPayload = runCatching {
                        GeneralOpdSaveRequest.from(current, tbDao.getGeneralOpdPrescriptions(current.id),
                            request.beneficiaryRegID, request.providerServiceMapID, request.createdBy)
                    }.getOrNull()
                    if (currentPayload == request) {
                        tbDao.updateGeneralOpd(current.copy(syncState = SyncState.SYNCED))
                        marked++
                        markedIds.add(current.id)
                    }
                }
            }
        }
        SyncDailyStore.record("GENERAL_OPD", markedIds, "PUSH", "SUCCESS")
        return marked
    }

    // pushUnSyncedRecordsTBDiagnostics() snapshots UNSYNCED rows once, then pushes them over the
    // network — during that round trip something else (most notably createOrder()'s reorder-
    // clearing write) can land on the same row. Blindly writing the stale snapshot back here (as
    // this used to do) would silently discard that newer write while marking the row SYNCED,
    // hiding it from future push cycles. Re-read fresh under the row's lock and only flip
    // syncState if nothing else changed since the snapshot was pushed; otherwise leave it
    // UNSYNCED so the next sync cycle picks up and pushes the newer state instead.
    private suspend fun updateSyncStatusDiagnostics(diagnosticsList: List<TBDiagnosticsCache>): Int {
        if (diagnosticsList.isEmpty()) return 0
        val markedIds = mutableListOf<Int>()
        // Locks first, then the transaction - the one global ordering withBenIdLocks documents.
        benIdLocks.withLocks(diagnosticsList.map { it.benId }) {
            database.withTransaction {
                diagnosticsList.forEach perRecord@ { pushed ->
                    val fresh = tbDao.getTbDiagnosticsByBenId(pushed.benId) ?: return@perRecord
                    val unchanged = fresh.copy(syncState = SyncState.SYNCED) == pushed.copy(syncState = SyncState.SYNCED)
                    if (unchanged) {
                        tbDao.saveTbDiagnostics(fresh.copy(syncState = SyncState.SYNCED))
                        markedIds.add(fresh.id)
                    }
                }
            }
        }
        SyncDailyStore.record("TB_DIAGNOSTICS", markedIds, "PUSH", "SUCCESS")
        return markedIds.size
    }

    // Same stale-snapshot race as updateSyncStatusDiagnostics() above, for TB_SUSPECTED.
    private suspend fun updateSyncStatusSuspected(tbspList: List<TBSuspectedCache>): Int {
        if (tbspList.isEmpty()) return 0
        val markedIds = mutableListOf<Int>()
        benIdLocks.withLocks(tbspList.map { it.benId }) {
            database.withTransaction {
                tbspList.forEach perRecord@ { pushed ->
                    // By primary key: a beneficiary can have several suspected rows, and the
                    // newest one may not be the row that was pushed.
                    val fresh = tbDao.getTbSuspectedById(pushed.id) ?: return@perRecord
                    val unchanged = fresh.copy(syncState = SyncState.SYNCED) == pushed.copy(syncState = SyncState.SYNCED)
                    if (unchanged) {
                        tbDao.saveTbSuspected(fresh.copy(syncState = SyncState.SYNCED))
                        markedIds.add(fresh.id)
                    }
                }
            }
        }
        SyncDailyStore.record("TB_SUSPECTED", markedIds, "PUSH", "SUCCESS")
        return markedIds.size
    }

    // Same as updateSyncStatusScreening: one transaction per chunk, and only rows unchanged since
    // they were pushed become SYNCED.
    private suspend fun updateSyncStatusConfirmed(tbcList: List<TBConfirmedTreatmentCache>): Int {
        if (tbcList.isEmpty()) return 0
        val markedIds = mutableListOf<Int>()
        database.withTransaction {
            tbcList.forEach { pushed ->
                val fresh = tbDao.getTbConfirmedById(pushed.id) ?: return@forEach
                if (fresh.copy(syncState = SyncState.SYNCED) == pushed.copy(syncState = SyncState.SYNCED)) {
                    tbDao.saveTbConfirmed(fresh.copy(syncState = SyncState.SYNCED))
                    markedIds.add(fresh.id)
                }
            }
        }
        SyncDailyStore.record("TB_CONFIRMED", markedIds, "PUSH", "SUCCESS")
        return markedIds.size
    }

    // Upload order for a chunked TB push: records the server rejected before are
    // sent alone once their retry delay has passed (records still waiting sit
    // this cycle out); everything else goes in chunks of [size].
    private fun <T> tbUploadQueue(records: List<T>, size: Int, entity: String, id: (T) -> Long): ArrayDeque<List<T>> {
        val (retrying, fresh) = records.partition { syncRetryStore.contains(entity, id(it)) }
        val due = retrying.filterNot { syncRetryStore.isWaiting(entity, id(it)) }
        return ArrayDeque(fresh.chunked(size) + due.map { listOf(it) })
    }

    // A rejected chunk of several records is re-queued one record at a time so
    // the others aren't held back. Returns how many records failed for good in
    // this run (only a rejected single record counts).
    private fun <T> rejectTbChunk(chunk: List<T>, queue: ArrayDeque<List<T>>, rejectedSingles: MutableList<T>): Int {
        if (chunk.size > 1) {
            chunk.forEach { queue.addLast(listOf(it)) }
            return 0
        }
        rejectedSingles += chunk
        return chunk.size
    }

    // Rejected records stay UNSYNCED and are retried individually. If nothing at
    // all was accepted, it's treated as a server-side problem and they are not delayed.
    private fun <T> recordTbRejections(entity: String, rejected: List<T>, anyAccepted: Boolean, error: String, id: (T) -> Long) {
        if (anyAccepted) syncRetryStore.resetBackoff()
        rejected.forEach {
            syncRetryStore.recordFailure(entity, id(it), error.ifBlank { "Rejected by server" }, backoff = anyAccepted)
        }
    }
}
