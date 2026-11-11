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
import org.piramalswasthya.stoptb.repositories.TBRepo.Companion.canRecoverChiefComplaints
import org.piramalswasthya.stoptb.repositories.TBRepo.Companion.canRecoverGeneralOpdDrugs
import org.piramalswasthya.stoptb.repositories.TBRepo.Companion.optChiefComplaintsOrNull
import org.piramalswasthya.stoptb.utils.ServerDateParser
import org.piramalswasthya.stoptb.utils.throttleLatest
import org.piramalswasthya.stoptb.work.WorkerUtils
import timber.log.Timber

/**
 * TB data pull: the legacy getAll endpoints and the changes-feed apply functions (screening,
 * General OPD, diagnostics, suspected, confirmed, diagnostic orders). Moved out of TBRepo
 * unchanged.
 */
@Singleton
class TbPullRepo @Inject constructor(
    private val tbDao: TBDao,
    private val benDao: BenDao,
    private val preferenceDao: PreferenceDao,
    private val userRepo: UserRepo,
    private val tmcNetworkApiService: AmritApiService,
    private val database: InAppDb,
    @ApplicationContext private val context: Context,
    private val tbLocalRepo: TbLocalRepo,
    private val generalOpdRepo: GeneralOpdRepo,
    private val diagnosticOrderRepo: DiagnosticOrderRepo,
    private val deviceIntegrationRepo: DeviceIntegrationRepo,
    private val tbPushRepo: TbPushRepo,
    private val benIdLocks: BenIdLocks
) {
    private companion object {
        /**
         * Records per write transaction in the pull loops below.
         *
         * Each of those loops used to commit once per record. Room's InvalidationTracker fires
         * per transaction, so a 20,000-record payload invalidated BENEFICIARY/TB_* twenty
         * thousand times, and every invalidation re-ran every query the open screen observes -
         * including a full PagingSource refresh of BEN_BASIC_CACHE, which the view's scalar
         * subqueries tie to TB_SCREENING and TB_SUSPECTED. Measured on device, that feedback
         * loop (write -> invalidate -> multi-second refresh -> next write queues behind it)
         * cost 3.34s per record: 64 of 5,505 TB Suspected rows in 214 seconds.
         *
         * Chunking rather than one transaction for the whole payload keeps the write lock hold
         * bounded and lets the list fill in progressively, while still cutting invalidations by
         * three orders of magnitude.
         */
        const val pullWriteChunkSize = 500
    }

    suspend fun getTBScreeningDetailsFromServer(): Int {
        return withContext(Dispatchers.IO) {
            val user =
                preferenceDao.getLoggedInUser()
                    ?: throw IllegalStateException("No user logged in!!")
            val villageId = preferenceDao.getLocationRecord()?.village?.id ?: return@withContext 0
            try {
                val response = tmcNetworkApiService.getTBScreeningData(
                    StopTbVillageRequest(
                        providerServiceMapID = user.serviceMapId,
                        villageID = villageId
                    )
                )
                val statusCode = response.code()
                if (statusCode == 200) {
                    val responseString = response.body()?.string()
                    if (responseString != null) {
                        val jsonObj = JSONObject(responseString)
                        val errorMessage = jsonObj.optString("errorMessage")
                        val responseStatusCode = jsonObj.optInt("statusCode")
                        Timber.d("Pull from amrit tb screening data : $responseStatusCode")
                        when (responseStatusCode) {
                            200 -> {
                                try {
                                    saveTBScreeningCacheFromNewResponse(jsonObj)
                                } catch (e: CancellationException) {
                                    throw e // a cancelled pull must stop, not report 0 / -1 records
                                } catch (e: Exception) {
                                    Timber.d("TB Screening entries not synced $e")
                                    return@withContext 0
                                }

                                return@withContext 1
                            }

                            401,5002 -> {
                                if (userRepo.refreshTokenTmc(
                                        user.userName, user.password
                                    )
                                ) throw SocketTimeoutException("Refreshed Token!")
                                else throw IllegalStateException("User Logged out!!")
                            }

                            5000 -> {
                                if (errorMessage == "No record found") return@withContext 0
                            }

                            else -> {
                                throw IllegalStateException("$responseStatusCode received, dont know what todo!?")
                            }
                        }
                    }
                }

            } catch (e: CancellationException) {
                throw e // a cancelled pull must stop, not report 0 / -1 records
            } catch (e: SocketTimeoutException) {
                Timber.e("get_tb error : $e")
                return@withContext -2

            } catch (e: java.lang.IllegalStateException) {
                Timber.e("get_tb error : $e")
                return@withContext -1
            }
            -1
        }
    }

    private suspend fun saveTBScreeningCacheFromResponse(dataObj: String): MutableList<TBScreeningCache> {
        val tbScreeningList = mutableListOf<TBScreeningCache>()
        var requestDTO = Gson().fromJson(dataObj, TBScreeningRequestDTO::class.java)
        database.withTransaction {
            requestDTO?.tbScreeningList?.forEach { tbScreeningDTO ->
                tbScreeningDTO.visitDate?.let {
                    var tbScreeningCache: TBScreeningCache? =
                        tbDao.getTbScreening(
                            tbScreeningDTO.benId,
                            getLongFromDate(tbScreeningDTO.visitDate),
                            getLongFromDate(tbScreeningDTO.visitDate) - 19_800_000
                        )
                    val cache = tbScreeningDTO.toCache()
                    if (shouldApplyServerRecord(
                            tbScreeningCache?.syncState,
                            tbScreeningCache?.serverUpdatedDate,
                            cache.serverUpdatedDate ?: 0L
                        )
                    ) {
                        benDao.getBen(tbScreeningDTO.benId)?.let {
                            tbDao.saveTbScreening(cache)
                        }
                    }
                }
            }
        }
        return tbScreeningList
    }

    suspend fun getGeneralOpdDetailsFromServer(): Int {
        return withContext(Dispatchers.IO) {
            val user =
                preferenceDao.getLoggedInUser()
                    ?: throw IllegalStateException("No user logged in!!")
            try {
                val response = tmcNetworkApiService.getGeneralOpdData(
                    StopTbVillageRequest(
                        providerServiceMapID = user.serviceMapId,
                        villageID = preferenceDao.getLocationRecord()?.village?.id ?: return@withContext 0
                    )
                )
                val statusCode = response.code()
                if (statusCode == 200) {
                    val responseString = response.body()?.string()
                    if (responseString != null) {
                        val jsonObj = JSONObject(responseString)
                        val errorMessage = jsonObj.optString("errorMessage")
                        when (val responseStatusCode = jsonObj.getInt("statusCode")) {
                            200 -> {
                                try {
                                    return@withContext if (saveGeneralOpdCacheFromNewResponse(jsonObj)) 1 else -1
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    Timber.e(e, "General OPD entries not synced")
                                    return@withContext -1
                                }
                            }

                            401, 5002 -> {
                                if (userRepo.refreshTokenTmc(
                                        user.userName,
                                        user.password
                                    )
                                ) throw SocketTimeoutException("Refreshed Token!")
                                else throw IllegalStateException("User Logged out!!")
                            }

                            5000 -> {
                                if (errorMessage == "No record found") return@withContext 0
                            }

                            else -> {
                                throw IllegalStateException("$responseStatusCode received, dont know what todo!?")
                            }
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: SocketTimeoutException) {
                Timber.e("get_general_opd error : $e")
                return@withContext -2
            } catch (e: IllegalStateException) {
                Timber.e("get_general_opd error : $e")
                return@withContext -1
            }
            -1
        }
    }

    suspend fun applyFeedTbScreenings(records: JSONArray) = withContext(Dispatchers.IO) {
        saveTBScreeningCacheFromNewResponse(feedResponse(records))
    }

    suspend fun applyFeedGeneralOpd(records: JSONArray) = withContext(Dispatchers.IO) {
        saveGeneralOpdCacheFromNewResponse(feedResponse(records))
    }

    suspend fun applyFeedDiagnostics(records: JSONArray) = withContext(Dispatchers.IO) {
        saveTBDiagnosticsCacheFromNewResponse(feedResponse(records))
    }

    suspend fun applyFeedTbSuspected(records: JSONArray) = withContext(Dispatchers.IO) {
        saveTBSuspectedCacheFromResponse(JSONObject().put("userId", 0).put("tbSuspectedList", records).toString())
    }

    suspend fun applyFeedTbConfirmed(records: JSONArray) = withContext(Dispatchers.IO) {
        saveTBConfirmedCacheFromResponse(
            JSONObject().put("userId", 0).put("tbConfirmedCases", records).toString(), rethrow = true
        )
    }

    // Diagnostic orders from the changes feed: one item per order row, with the same result fields
    // order/result returns. Only the latest order per beneficiary and test type is applied, through
    // the same mapping fetchOrderResult uses. RIF goes first, then TrueNat, then X-ray, so the
    // MTB -> RIF cascade and the X-ray -> TrueNat referral see the orders this batch already holds.
    suspend fun applyFeedDiagnosticOrders(records: JSONArray) = withContext(Dispatchers.IO) {
        val latest = HashMap<Pair<Long, String>, Pair<Long, OrderResultData>>()
        for (i in 0 until records.length()) {
            val item = records.optJSONObject(i) ?: continue
            val benKey = item.optLong("beneficiaryId", 0L).takeIf { it > 0 } ?: continue
            val localType = when (item.optString("orderType").uppercase()) {
                "XRAY_CHEST" -> "XRAY_CHEST"
                "MTB", "MTB_PLUS", "SPUTUM_TRUENAT" -> "SPUTUM_TRUENAT"
                "MDR_RIF" -> "MDR_RIF"
                else -> continue
            }
            val orderId = item.optLong("id", 0L)
            val key = benKey to localType
            if ((latest[key]?.first ?: Long.MIN_VALUE) >= orderId) continue
            latest[key] = orderId to OrderResultData(
                externalOrderId = item.optStringOrNull("externalOrderId"),
                orderType = item.optStringOrNull("orderType"),
                status = item.optStringOrNull("status"),
                providerStatus = item.optStringOrNull("providerStatus"),
                resultSummary = item.optStringOrNull("resultSummary"),
                tbPresence = item.optNullableBoolean("tbPresence"),
                drugResistancePresence = item.optNullableBoolean("drugResistancePresence"),
                errorMessage = item.optStringOrNull("errorMessage"),
                reasonToClose = item.optStringOrNull("reasonToClose")
            )
        }
        val typeOrder = listOf("MDR_RIF", "SPUTUM_TRUENAT", "XRAY_CHEST")
        val now = System.currentTimeMillis()
        val skippedBefore = preferenceDao.getFeedSkippedOrders()
        // Skipped-order entries for orders this page applies; removed only once they are applied,
        // so a page that fails part-way leaves them for DiagnosticResultPollWorker.
        val appliedSkipKeys = mutableListOf<String>()

        // Sorted RIF -> TrueNat -> X-ray, resolved to the local beneficiary, and split three ways:
        // inside the grace period (left for DiagnosticResultPollWorker), needing the MTB -> RIF
        // cascade (network calls; applied one at a time through applyOrderResult, which already
        // handles its locking), and everything else, applied below in one transaction.
        data class FeedOrder(val ben: BenRegCache, val localType: String, val data: OrderResultData)
        val bulk = ArrayList<FeedOrder>()
        val cascade = ArrayList<Pair<FeedOrder, Boolean>>()
        for ((key, value) in latest.entries.sortedBy { typeOrder.indexOf(it.key.second) }) {
            val (benKey, localType) = key
            val ben = benDao.getBenByRegId(benKey) ?: benDao.getBen(benKey) ?: continue
            val skipKey = diagnosticOrderRepo.feedSkippedKey(ben.beneficiaryId, localType)
            // An order this tablet created in the last moments may not be on the hub's feed yet;
            // the older server row must not overwrite it.
            val createdAt = diagnosticOrderRepo.orderCreatedTimestamps["${ben.beneficiaryId}_$localType"]
            if (createdAt != null && now - createdAt < diagnosticOrderRepo.ORDER_STATUS_GRACE_PERIOD_MS) {
                preferenceDao.addFeedSkippedOrder(skipKey)
                continue
            }
            val rifOrderKnown = latest.containsKey(benKey to "MDR_RIF")
            val order = FeedOrder(ben, localType, value.second)
            if (diagnosticOrderRepo.needsRifCascade(localType, value.second, rifOrderKnown)) cascade += order to rifOrderKnown
            else bulk += order
        }

        // One set of locks and one write transaction for the page. Applying each order on its own
        // (lock, read, write TB_DIAGNOSTICS, read and write TB_SUSPECTED) committed four or so
        // transactions per order, and every commit re-ran the queries of whatever list screen was
        // open; with a thousand pending orders on first login that took about two seconds an
        // order. Rows that come out unchanged are not written at all, so a page that only
        // repeats what the tablet already holds costs no writes.
        var applied = 0
        // A cascade only arises when this page has no RIF order for the beneficiary, so applying
        // these TrueNat results first keeps the RIF -> TrueNat -> X-ray order: the X-ray rows
        // below then see the TrueNat state, as they did when every order was applied in turn.
        for ((order, rifOrderKnown) in cascade) {
            val current = tbDao.getTbDiagnosticsByBenId(order.ben.beneficiaryId)?.trueNatOrderId
            diagnosticOrderRepo.applyOrderResult(order.ben, order.localType, order.data, current, pushSuspectedAfter = false, rifOrderKnown = rifOrderKnown)
            applied++
            diagnosticOrderRepo.feedSkippedKey(order.ben.beneficiaryId, order.localType).let { if (it in skippedBefore) appliedSkipKeys += it }
        }

        if (bulk.isNotEmpty()) {
            val truenatIntegrated = deviceIntegrationRepo.isTruenatIntegrated()
            var bulkApplied = 0
            benIdLocks.withLocks(bulk.map { it.ben.beneficiaryId }) {
                database.withTransaction {
                    for (order in bulk) {
                        val benId = order.ben.beneficiaryId
                        val skipKey = diagnosticOrderRepo.feedSkippedKey(benId, order.localType)
                        // Checked again under the lock: diagnosticOrderRepo.createOrder() saves a new order and records
                        // its time under this same lock, so one placed after the page was sorted
                        // above shows here, and the older feed row must not overwrite it.
                        val createdAt = diagnosticOrderRepo.orderCreatedTimestamps["${benId}_${order.localType}"]
                        if (createdAt != null && System.currentTimeMillis() - createdAt < diagnosticOrderRepo.ORDER_STATUS_GRACE_PERIOD_MS) {
                            preferenceDao.addFeedSkippedOrder(skipKey)
                            continue
                        }
                        val rawStatus = order.data.status
                        val status = if (rawStatus.isNullOrBlank()) "IN_PROGRESS" else rawStatus
                        val existing = tbDao.getTbDiagnosticsByBenId(benId)
                        val merged = diagnosticOrderRepo.mergeOrderResult(existing, benId, order.localType, order.data, status, null, truenatIntegrated)
                        // Enqueued before the row is saved, as before: WorkManager keeps the work
                        // across a restart, so the referral is not lost if the process dies after
                        // the completed X-ray is stored.
                        if (merged.triggerTrueNat) {
                            WorkerUtils.triggerDiagnosticOrderPushWorkers(context, benId, listOf("SPUTUM_TRUENAT"))
                        }
                        val synced = merged.cache.copy(syncState = SyncState.SYNCED)
                        if (synced != existing) tbDao.saveTbDiagnostics(synced)
                        val existingSuspected = tbDao.getTbSuspected(benId)
                        val suspected = diagnosticOrderRepo.suspectedFromDiagnostics(existingSuspected, benId, synced)
                        if (suspected != null) {
                            order.ben.gpsLatitude?.let { suspected.latitude = it }
                            order.ben.gpsLongitude?.let { suspected.longitude = it }
                            if (suspected != existingSuspected) tbDao.saveTbSuspected(suspected)
                        }
                        if (skipKey in skippedBefore) appliedSkipKeys += skipKey
                        bulkApplied++
                    }
                }
            }
            applied += bulkApplied
            Timber.d("Feed diagnostic orders: applied $bulkApplied of ${bulk.size} in one transaction")
        }
        preferenceDao.removeFeedSkippedOrders(appliedSkipKeys)

        if (applied > 0) {
            // Pushes whatever TB_SUSPECTED rows are waiting, including ones an earlier failed
            // upload left behind; it returns at once when there are none.
            try {
                tbPushRepo.pushUnSyncedRecordsTBSuspected()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to push TB Suspected after applying feed diagnostic orders")
            }
        }
        applied
    }

    /**
     * The orders the feed skipped inside the grace period whose grace period has now ended, as
     * (benId, order type) pairs ready for [fetchOrderResult]. They stay recorded until
     * [ackFeedSkippedOrder] (after a successful fetch) or a later feed row for the same order, so
     * a failed or interrupted fetch is tried again on the next run. After a restart the creation
     * time is gone, so the pair is due straight away.
     */
    fun feedSkippedOrdersPastGrace(): List<Pair<Long, String>> {
        val now = System.currentTimeMillis()
        return preferenceDao.getFeedSkippedOrders().mapNotNull { entry ->
            val benId = entry.substringBefore('|').toLongOrNull()
            val type = entry.substringAfter('|', "")
            if (benId == null || type.isEmpty()) {
                preferenceDao.removeFeedSkippedOrder(entry)
                return@mapNotNull null
            }
            val createdAt = diagnosticOrderRepo.orderCreatedTimestamps["${benId}_$type"]
            if (createdAt == null || now - createdAt >= diagnosticOrderRepo.ORDER_STATUS_GRACE_PERIOD_MS) benId to type else null
        }
    }

    fun ackFeedSkippedOrder(benId: Long, type: String) {
        preferenceDao.removeFeedSkippedOrder(diagnosticOrderRepo.feedSkippedKey(benId, type))
    }

    private fun feedResponse(records: JSONArray): JSONObject =
        JSONObject().put("statusCode", 200).put("data", JSONObject().put("data", records))

    private suspend fun saveTBScreeningCacheFromNewResponse(jsonObj: JSONObject): MutableList<TBScreeningCache> {
        val tbScreeningList = mutableListOf<TBScreeningCache>()
        val records = when (val data = jsonObj.opt("data")) {
            is org.json.JSONArray -> data
            is JSONObject -> data.optJSONArray("data") ?: org.json.JSONArray()
            else -> org.json.JSONArray()
        }
        // One transaction per [pullWriteChunkSize] records instead of one per record - see that
        // constant for why. The inner `for` is local to the lambda, so `continue` still skips a
        // single record rather than ending the chunk.
        //
        // Both lookups are resolved for the whole chunk up front. Done per record, they cost
        // three statements each: benRegId carries no index (the primary key is beneficiaryId),
        // so `getBenByRegId` scanned the entire beneficiary table - 9,987 wide rows on a camp
        // device - to read one id, once per record. At ~10,000 records that is ~10,000 full
        // scans for this domain alone, which is what holds the database for the length of a
        // pull and leaves the beneficiary list waiting seconds for a query that costs 54 ms.
        for (chunkStart in 0 until records.length() step pullWriteChunkSize) {
            val chunkEnd = minOf(chunkStart + pullWriteChunkSize, records.length())
            database.withTransaction {
                val chunkItems = (chunkStart until chunkEnd).mapNotNull { index ->
                    val item = records.optJSONObject(index) ?: return@mapNotNull null
                    val benRegId = item.optLong("beneficiaryRegID", 0L).takeIf { regId -> regId > 0 }
                        ?: return@mapNotNull null
                    item to benRegId
                }
                // associateBy keeps the last duplicate; the per-record query it replaces used
                // LIMIT 1 with no ORDER BY, which SQLite answers with the first row in scan
                // order, so resolve ties by the lowest id to match the old behaviour.
                val benIdByRegId = benDao.getBenIdsByRegIds(chunkItems.map { (_, regId) -> regId })
                    .groupBy { mapping -> mapping.benRegId }
                    .mapValues { (_, mappings) -> mappings.minOf { mapping -> mapping.beneficiaryId } }
                // Mutable, because the per-record version wrote each row immediately and so a
                // second record for the same beneficiary in the same chunk compared against the
                // row the first one had just written. Keeping that matters: a brand-new row
                // carries id = 0, and inserting it twice would create two rows for one
                // beneficiary rather than updating one.
                val existingByBenId = tbDao.getTbScreeningByBenIds(benIdByRegId.values.toList())
                    .groupBy { screening -> screening.benId }
                    .mapValues { (_, screenings) -> screenings.minByOrNull { screening -> screening.id }!! }
                    .toMutableMap()
                val pendingWrites = linkedMapOf<Long, TBScreeningCache>()

                for ((item, benRegId) in chunkItems) {
                    val benId = benIdByRegId[benRegId] ?: continue
                    val visitDate = getLongFromDateMultipleSupport(item.optString("visitDate"))
                    val existing = existingByBenId[benId]
                    val serverUpdatedDate = getServerUpdatedDate(item)
                    if (!shouldApplyServerRecord(existing?.syncState, existing?.serverUpdatedDate, serverUpdatedDate)) {
                        continue
                    }
                    val cache = (existing ?: TBScreeningCache(benId = benId)).copy(
                        visitDate = visitDate,
                        coughMoreThan2Weeks = item.optNullableBoolean("coughMoreThan2Weeks"),
                        bloodInSputum = item.optNullableBoolean("bloodInSputum"),
                        feverMoreThan2Weeks = item.optNullableBoolean("feverMoreThan2Weeks"),
                        lossOfWeight = item.optNullableBoolean("lossOfWeight"),
                        nightSweats = item.optNullableBoolean("nightSweats"),
                        historyOfTb = item.optNullableBoolean("historyOfTb"),
                        takingAntiTBDrugs = item.optNullableBoolean("takingAntiTBDrugs"),
                        familySufferingFromTB = item.optNullableBoolean("familySufferingFromTB"),
                        riseOfFever = item.optNullableBoolean("riseOfFever"),
                        lossOfAppetite = item.optNullableBoolean("lossOfAppetite"),

                        chestPain = item.optNullableBoolean("chestPain") ?: existing?.chestPain,
                        shortnessOfBreath = item.optNullableBoolean("shortnessOfBreath") ?: existing?.shortnessOfBreath,
                        fatigue = item.optNullableBoolean("fatigue") ?: existing?.fatigue,
                        failureToGainWeightInChildren = item.optNullableBoolean("failureToGainWeightInChildren") ?: existing?.failureToGainWeightInChildren,
                        decreasedActivityOrPlayfulnessInChildren = item.optNullableBoolean("decreasedActivityOrPlayfulnessInChildren") ?: existing?.decreasedActivityOrPlayfulnessInChildren,
                        otherSymptoms = item.optNullableBoolean("otherSymptoms") ?: existing?.otherSymptoms,
                        referredForDigitalChestXray = item.optNullableBoolean("referredForDigitalChestXray"),
                        referredForSputumCollection = item.optNullableBoolean("referredForSputumCollection"),
                        sputumSampleSubmittedAt = item.optStringOrNull("sputumSampleSubmittedAt"),
                        recommendedForTruenatTest = item.optNullableBoolean("recommendedForTruenat"),
                        recommendedForLiquidCultureTest = item.optNullableBoolean("recommendedForLiquidCulture"),
                        reasonForDenialForGettingTested = item.optStringListOrNull("testDenialReasons"),
                        keyPopulationRiskFactorIds = item.optIntListOrNull("keyPopulationRiskFactorIds"),
                        keyPopulationRiskFactors = item.optStringListOrNull("keyPopulationRiskFactors"),
                        hivStatusId = item.optIntOrNull("hivStatusId"),
                        hivStatus = item.optStringOrNull("hivStatus"),
                        serverUpdatedDate = serverUpdatedDate.takeIf { it > 0L },
                        syncState = SyncState.SYNCED
                    )
                    existingByBenId[benId] = cache
                    pendingWrites[benId] = cache
                    tbScreeningList.add(cache)
                }
                // One batched insert per chunk rather than one statement per record.
                if (pendingWrites.isNotEmpty()) tbDao.saveTbScreening(pendingWrites.values.toList())
            }
        }
        return tbScreeningList
    }

    private suspend fun saveGeneralOpdCacheFromResponse(dataObj: String): MutableList<GeneralOpdCache> {
        val generalOpdList = mutableListOf<GeneralOpdCache>()
        val requestDTO = Gson().fromJson(dataObj, GeneralOpdRequestDTO::class.java)
        database.withTransaction {
            requestDTO?.generalOpdList?.forEach { generalOpdDTO ->
                val existing = tbDao.getGeneralOpd(generalOpdDTO.benId)
                val cache = generalOpdDTO.toCache()
                if (shouldApplyServerRecord(
                        existing?.syncState,
                        existing?.serverUpdatedDate,
                        cache.serverUpdatedDate ?: 0L
                    )
                ) {
                    benDao.getBen(generalOpdDTO.benId)?.let {
                        tbDao.saveGeneralOpd(cache)
                        generalOpdList.add(cache)
                    }
                }
            }
        }
        return generalOpdList
    }

    private suspend fun saveGeneralOpdCacheFromNewResponse(jsonObj: JSONObject): Boolean {
        val records = getStopTbDataArray(jsonObj)
        // One transaction per [pullWriteChunkSize] records instead of one per record - see that
        // constant for why. Room's withTransaction is reentrant, so the per-record one inside
        // `save` below joins this chunk's transaction rather than committing on its own; the
        // prescription rows a record rewrites therefore still land atomically with it.
        var complete = true
        var recordsBeforeChunk = 0
        (0 until records.length()).map { index -> records.optJSONObject(index) }
            .chunked(pullWriteChunkSize)
            .forEach { chunk ->
                val chunkOffset = recordsBeforeChunk
                database.withTransaction {
                    val chunkComplete = processGeneralOpdPullBatch(
                        records = chunk,
                        parse = { record ->
                            val item = requireNotNull(record) { "Invalid General OPD record" }
                            val hasDrugs = item.has("drugs") && !item.isNull("drugs")
                            if (hasDrugs) {
                                val drugs = requireNotNull(item.optJSONArray("drugs")) { "Invalid General OPD drugs array" }
                                GeneralOpdDrugResponse.parsePrescriptions(drugs.toString(), 0)
                            } else emptyList()
                        },
                        save = saveRecord@ { record, incomingRows ->
                            val item = requireNotNull(record)
                            val benRegId = item.optLong("beneficiaryRegID", 0L).takeIf { it > 0 } ?: return@saveRecord
                            val ben = benDao.getBenByRegId(benRegId) ?: return@saveRecord
                            val serverUpdatedDate = getServerUpdatedDate(item)
                            val incomingComplaints = item.optChiefComplaintsOrNull()
                            val hasDrugs = item.has("drugs") && !item.isNull("drugs")
                            database.withTransaction perRecord@ {
                                val existing = tbDao.getGeneralOpd(ben.beneficiaryId)
                                val applyRecord = shouldApplyServerRecord(existing?.syncState,
                                    existing?.serverUpdatedDate, serverUpdatedDate)
                                val recoverDrugs = canRecoverGeneralOpdDrugs(existing, incomingRows, serverUpdatedDate)
                                if (!applyRecord && !recoverDrugs) {
                                    if (canRecoverChiefComplaints(existing, incomingComplaints, serverUpdatedDate)) {
                                        val recovered = existing!!.copy(chiefComplaints = incomingComplaints,
                                            chiefComplaintIds = null)
                                        tbDao.saveGeneralOpd(recovered)
                                        return@perRecord
                                    }
                                    return@perRecord
                                }
                                val complaints = incomingComplaints ?: existing?.chiefComplaints
                                val cache = (existing ?: GeneralOpdCache(benId = ben.beneficiaryId)).copy(
                                    chiefComplaints = complaints,
                                    chiefComplaintIds = existing?.chiefComplaintIds?.takeIf { complaints == existing?.chiefComplaints },
                                    medications = if (incomingRows.isNotEmpty()) incomingRows.map { it.medicine }
                                        else item.optStringOrNull("medication")?.split(",")?.map { it.trim() }
                                            ?.filter { it.isNotBlank() } ?: existing?.medications,
                                    dosage = item.optStringOrNull("dosage") ?: existing?.dosage,
                                    frequency = if (incomingRows.isNotEmpty()) incomingRows.joinToString(", ") { it.frequency }
                                        else item.optStringOrNull("frequency") ?: existing?.frequency,
                                    duration = if (incomingRows.isNotEmpty()) incomingRows.joinToString(", ") {
                                        "${it.durationCount} ${it.durationUnit}"
                                    } else item.optStringOrNull("duration") ?: existing?.duration,
                                    notes = item.optStringOrNull("notes") ?: existing?.notes,
                                    serverUpdatedDate = serverUpdatedDate.takeIf { it > 0L },
                                    syncState = SyncState.SYNCED
                                )
                                val opdId = tbDao.saveGeneralOpd(cache).toInt()
                                // Parent and per-drug rows must become visible together, never from different revisions.
                                if (hasDrugs || cache.medications != existing?.medications ||
                                    cache.frequency != existing?.frequency || cache.duration != existing?.duration) {
                                    tbDao.deleteGeneralOpdPrescriptions(opdId)
                                    if (incomingRows.isNotEmpty()) tbDao.insertGeneralOpdPrescriptions(
                                        incomingRows.map { it.copy(opdId = opdId) })
                                }
                            }
                        },
                        onInvalid = { index, error ->
                            Timber.e("General OPD pull skipped invalid record at index ${chunkOffset + index} (${error.javaClass.simpleName})")
                        }
                    )
                    if (!chunkComplete) complete = false
                }
                recordsBeforeChunk += chunk.size
            }
        return complete
    }

    suspend fun getTbDiagnosticsDetailsFromServer(): Int {
        return withContext(Dispatchers.IO) {
            val user =
                preferenceDao.getLoggedInUser()
                    ?: throw IllegalStateException("No user logged in!!")
            try {
                val response = tmcNetworkApiService.getTBDiagnosticsData(
                    StopTbVillageRequest(
                        providerServiceMapID = user.serviceMapId,
                        villageID = preferenceDao.getLocationRecord()?.village?.id ?: return@withContext 0
                    )
                )
                if (response.code() == 200) {
                    val responseString = response.body()?.string()
                    if (responseString != null) {
                        val jsonObj = JSONObject(responseString)
                        val errorMessage = jsonObj.optString("errorMessage")
                        when (val responseStatusCode = jsonObj.getInt("statusCode")) {
                            200 -> {
                                try {
                                    saveTBDiagnosticsCacheFromNewResponse(jsonObj)
                                } catch (e: CancellationException) {
                                    throw e // a cancelled pull must stop, not report 0 / -1 records
                                } catch (e: Exception) {
                                    Timber.d("TB Diagnostics entries not synced $e")
                                    return@withContext 0
                                }
                                return@withContext 1
                            }

                            401, 5002 -> {
                                if (userRepo.refreshTokenTmc(user.userName, user.password)) {
                                    throw SocketTimeoutException("Refreshed Token!")
                                } else {
                                    throw IllegalStateException("User Logged out!!")
                                }
                            }

                            5000 -> {
                                if (errorMessage == "No record found") return@withContext 0
                            }

                            else -> {
                                throw IllegalStateException("$responseStatusCode received, don't know what todo!?")
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e // a cancelled pull must stop, not report 0 / -1 records
            } catch (e: SocketTimeoutException) {
                Timber.e("get_tb_diagnostics error : $e")
                return@withContext -2
            } catch (e: IllegalStateException) {
                Timber.e("get_tb_diagnostics error : $e")
                return@withContext -1
            }
            -1
        }
    }

    private suspend fun saveTBDiagnosticsCacheFromResponse(dataObj: String): MutableList<TBDiagnosticsCache> {
        val tbDiagnosticsList = mutableListOf<TBDiagnosticsCache>()
        val requestDTO = Gson().fromJson(dataObj, TBDiagnosticsRequestDTO::class.java)
        database.withTransaction {
            requestDTO?.tbDiagnosticsList?.forEach { tbDiagnosticsDTO ->
                tbDiagnosticsDTO.visitDate?.let {
                    val tbDiagnosticsCache: TBDiagnosticsCache? =
                        tbDao.getTbDiagnostics(
                            tbDiagnosticsDTO.benId,
                            getLongFromDate(tbDiagnosticsDTO.visitDate),
                            getLongFromDate(tbDiagnosticsDTO.visitDate) - 19_800_000
                        )
                    val cache = tbDiagnosticsDTO.toCache()
                    if (shouldApplyServerRecord(
                            tbDiagnosticsCache?.syncState,
                            tbDiagnosticsCache?.serverUpdatedDate,
                            cache.serverUpdatedDate ?: 0L
                        )
                    ) {
                        benDao.getBen(tbDiagnosticsDTO.benId)?.let {
                            tbDao.saveTbDiagnostics(cache)
                            tbDiagnosticsList.add(cache)
                        }
                    }
                }
            }
        }
        return tbDiagnosticsList
    }

    private suspend fun saveTBDiagnosticsCacheFromNewResponse(jsonObj: JSONObject): MutableList<TBDiagnosticsCache> {
        val tbDiagnosticsList = mutableListOf<TBDiagnosticsCache>()
        val records = getStopTbDataArray(jsonObj)
        // One transaction per [pullWriteChunkSize] records instead of one per record - see that
        // constant for why.
        //
        // Unlike every other TB_DIAGNOSTICS write path in this file, this pull used to read
        // `existing` once and write much later (after the full network round-trip for every
        // beneficiary in the response) with no lock at all — a wide TOCTOU window where a
        // concurrent diagnosticOrderRepo.createOrder()/submitManualResult() write (e.g. re-ordering a Closed order,
        // which correctly clears naatResult/trueNatRifResult/reasonNotConducted*) could land in
        // between and then get silently overwritten by this pull's stale snapshot. The read still
        // happens immediately before the decision and the write, and now additionally inside the
        // transaction that commits it, so that window stays closed.
        //
        // The chunk's locks are all taken before the transaction opens. Taking them the other way
        // round would let this loop hold the write lock while waiting on a beneficiary lock held
        // by DiagnosticResultPollWorker, which would itself be waiting for that write lock.
        for (chunkStart in 0 until records.length() step pullWriteChunkSize) {
            val chunkEnd = minOf(chunkStart + pullWriteChunkSize, records.length())
            // benRegId has no index, so resolve the chunk's ids in one query (was two full-table
            // scans per record). Only ids resolved here get a lock; a beneficiary registered after
            // this lookup is left for the next pull, as before - re-applying is idempotent.
            val chunkRegIds = (chunkStart until chunkEnd).mapNotNull { index ->
                records.optJSONObject(index)?.optLong("benRegID", 0L)?.takeIf { it > 0 }
            }.distinct()
            val benIdByRegId = if (chunkRegIds.isEmpty()) emptyMap() else
                benDao.getBenIdsByRegIds(chunkRegIds).associate { mapping -> mapping.benRegId to mapping.beneficiaryId }
            val chunkBenIds = benIdByRegId.values.toSet()
            if (chunkBenIds.isEmpty()) continue
            benIdLocks.withLocks(chunkBenIds) {
                database.withTransaction {
                    for (index in chunkStart until chunkEnd) {
                        val item = records.optJSONObject(index) ?: continue
                        val benRegId = item.optLong("benRegID", 0L).takeIf { it > 0 } ?: continue
                        val benId = benIdByRegId[benRegId] ?: continue
                        val visitDate = getLongFromDateMultipleSupport(item.optString("visitDate"))
                        val serverUpdatedDate = getServerUpdatedDate(item)
                        val existing = tbDao.getTbDiagnostics(benId)
                        if (!shouldApplyServerRecord(existing?.syncState, existing?.serverUpdatedDate, serverUpdatedDate)) {
                            continue
                        }
                        val cache = (existing ?: TBDiagnosticsCache(benId = benId)).copy(
                            visitDate = visitDate,
                            nikshayId = item.optStringOrNull("nikshayId") ?: existing?.nikshayId,
                            isChestXRayDone = item.optNullableBoolean("isDigitalChestXrayConducted") ?: existing?.isChestXRayDone,
                            chestXRayResult = item.optStringOrNull("digitalChestXrayResult") ?: existing?.chestXRayResult,
                            isNaatConducted = item.optNullableBoolean("isTruenatConducted") ?: existing?.isNaatConducted,
                            naatResult = item.optStringOrNull("truenatResult") ?: existing?.naatResult,
                            recommendedForLiquidCultureTest = item.optNullableBoolean("recommendedForLiquidCulture") ?: existing?.recommendedForLiquidCultureTest,
                            liquidCultureResult = item.optStringOrNull("liquidCultureResult") ?: existing?.liquidCultureResult,
                            xrayOrderId = item.optStringOrNull("xrayOrderId") ?: existing?.xrayOrderId,
                            xrayOrderStatus = item.optStringOrNull("xrayOrderStatus") ?: existing?.xrayOrderStatus,
                            trueNatOrderId = item.optStringOrNull("trueNatOrderId") ?: existing?.trueNatOrderId,
                            trueNatOrderStatus = item.optStringOrNull("trueNatOrderStatus") ?: existing?.trueNatOrderStatus,
                            trueNatRifResult = item.optStringOrNull("trueNatRifResult") ?: existing?.trueNatRifResult,
                            rifOrderId = item.optStringOrNull("rifOrderId") ?: existing?.rifOrderId,
                            rifOrderStatus = item.optStringOrNull("rifOrderStatus") ?: existing?.rifOrderStatus,
                            serverUpdatedDate = serverUpdatedDate.takeIf { it > 0L },
                            syncState = SyncState.SYNCED
                        )
                        tbDao.saveTbDiagnostics(cache)
                        tbDiagnosticsList.add(cache)
                    }
                }
            }
        }
        return tbDiagnosticsList
    }

    suspend fun getTbSuspectedDetailsFromServer(): Int {
        return withContext(Dispatchers.IO) {
            val user =
                preferenceDao.getLoggedInUser()
                    ?: throw IllegalStateException("No user logged in!!")
            val lastTimeStamp = preferenceDao.getLastSyncedTimeStamp()
            try {
                val villageId = preferenceDao.getLocationRecord()?.village?.id
                val response = tmcNetworkApiService.getTBSuspectedData(
                    GetDataPaginatedRequest(
                        ashaId = user.userId,
                        pageNo = 0,
                        fromDate = BenRepo.getCurrentDate(Konstants.defaultTimeStamp),
                        toDate = getCurrentDate(),
                        providerServiceMapID = user.serviceMapId,
                        villageID = villageId
                    )
                )
                val statusCode = response.code()
                if (statusCode == 200) {
                    val responseString = response.body()?.string()
                    if (responseString != null) {
                        val jsonObj = JSONObject(responseString)

                        val errorMessage = jsonObj.getString("errorMessage")
                        val responseStatusCode = jsonObj.getInt("statusCode")
                        Timber.d("Pull from amrit tb suspected data : $responseStatusCode")
                        when (responseStatusCode) {
                            200 -> {
                                try {
                                    val dataObj = jsonObj.getString("data")
                                    saveTBSuspectedCacheFromResponse(dataObj)
                                } catch (e: CancellationException) {
                                    throw e // a cancelled pull must stop, not report 0 / -1 records
                                } catch (e: Exception) {
                                    Timber.d("TB Suspected entries not synced $e")
                                    return@withContext 0
                                }

                                return@withContext 1
                            }

                            401,5002 -> {
                                if (userRepo.refreshTokenTmc(
                                        user.userName, user.password
                                    )
                                ) throw SocketTimeoutException("Refreshed Token!")
                                else throw IllegalStateException("User Logged out!!")
                            }

                            5000 -> {
                                if (errorMessage == "No record found") return@withContext 0
                            }

                            else -> {
                                throw IllegalStateException("$responseStatusCode received, don't know what todo!?")
                            }
                        }
                    }
                }

            } catch (e: CancellationException) {
                throw e // a cancelled pull must stop, not report 0 / -1 records
            } catch (e: SocketTimeoutException) {
                Timber.e("get_tb error : $e")
                return@withContext -2

            } catch (e: java.lang.IllegalStateException) {
                Timber.e("get_tb error : $e")
                return@withContext -1
            }
            -1
        }
    }

    private suspend fun saveTBSuspectedCacheFromResponse(dataObj: String): MutableList<TBSuspectedCache> {
        val tbSuspectedList = mutableListOf<TBSuspectedCache>()
        val requestDTO = Gson().fromJson(dataObj, TBSuspectedRequestDTO::class.java)
        // One transaction per [pullWriteChunkSize] records instead of one per record. Nothing
        // inside the loop touches the network, so the write lock is only held for local work.
        requestDTO?.tbSuspectedList.orEmpty().chunked(pullWriteChunkSize).forEach { chunk ->
            database.withTransaction {
                chunk.forEach { tbSuspectedDTO ->
                    tbSuspectedDTO.visitDate?.let {
                        val matchedByVisitDate: TBSuspectedCache? =
                            tbDao.getTbSuspected(
                                tbSuspectedDTO.benId,
                                getLongFromDate(tbSuspectedDTO.visitDate),
                                getLongFromDate(tbSuspectedDTO.visitDate) - 19_800_000
                            )
                        val tbSuspectedCache = matchedByVisitDate ?: tbDao.getTbSuspected(tbSuspectedDTO.benId)
                        val cache = tbSuspectedDTO.toCache().let { incoming ->
                            tbSuspectedCache?.copy(
                                visitDate = incoming.visitDate,
                                visitLabel = incoming.visitLabel,
                                typeOfTBCase = incoming.typeOfTBCase,
                                reasonForSuspicion = incoming.reasonForSuspicion,
                                // The server keeps no column for these, so a pulled record
                                // carries null; keep what the tablet derived.
                                hasSymptoms = tbSuspectedDTO.hasSymptoms ?: tbSuspectedCache.hasSymptoms,
                                isSputumCollected = incoming.isSputumCollected,
                                sputumSubmittedAt = incoming.sputumSubmittedAt,
                                nikshayId = incoming.nikshayId,
                                sputumTestResult = incoming.sputumTestResult,
                                isChestXRayDone = incoming.isChestXRayDone,
                                chestXRayResult = incoming.chestXRayResult,
                                isAICoughAssessmentDone = incoming.isAICoughAssessmentDone,
                                aiCoughAssessmentResult = incoming.aiCoughAssessmentResult,
                                isNaatConducted = incoming.isNaatConducted ?: tbSuspectedCache.isNaatConducted,
                                naatResult = incoming.naatResult ?: tbSuspectedCache.naatResult,
                                recommendedForLiquidCultureTest = incoming.recommendedForLiquidCultureTest,
                                isLiquidCultureConducted = incoming.isLiquidCultureConducted,
                                liquidCultureResult = incoming.liquidCultureResult,
                                referralFacility = incoming.referralFacility,
                                isTBConfirmed = incoming.isTBConfirmed,
                                isDRTBConfirmed = incoming.isDRTBConfirmed,
                                otherReasonForSuspicion = incoming.otherReasonForSuspicion,
                                isConfirmed = incoming.isConfirmed,
                                latitude = incoming.latitude ?: tbSuspectedCache.latitude,
                                longitude = incoming.longitude ?: tbSuspectedCache.longitude,
                                address = incoming.address ?: tbSuspectedCache.address,
                                referred = incoming.referred,
                                followUps = incoming.followUps,
                                serverUpdatedDate = incoming.serverUpdatedDate,
                                syncState = SyncState.SYNCED
                            ) ?: incoming
                        }
                        val shouldApply = shouldApplyServerRecord(
                            tbSuspectedCache?.syncState,
                            tbSuspectedCache?.serverUpdatedDate,
                            cache.serverUpdatedDate ?: 0L
                        )
                        Timber.d("SYNC_DIAG TB Suspected pull: benId=${tbSuspectedDTO.benId} serverId=${tbSuspectedDTO.id} matchedLocalId=${tbSuspectedCache?.id} localSyncState=${tbSuspectedCache?.syncState} localServerUpdatedDate=${tbSuspectedCache?.serverUpdatedDate} incomingServerUpdatedDate=${cache.serverUpdatedDate} shouldApply=$shouldApply")
                        if (shouldApply) {
                            benDao.getBen(tbSuspectedDTO.benId)?.let {
                                tbDao.saveTbSuspected(cache)
                                tbSuspectedList.add(cache)
                            }
                        } else if (tbSuspectedCache != null && tbSuspectedCache.syncState != SyncState.SYNCED) {
                            Timber.w("SYNC_DIAG TB Suspected pull: benId=${tbSuspectedDTO.benId} server already has this record (serverId=${tbSuspectedDTO.id}) but local id=${tbSuspectedCache.id} is stuck at syncState=${tbSuspectedCache.syncState} — reconciliation SKIPPED, will remain unsynced until a push of this local row succeeds")
                        }
                    }
                }
            }
        }
        return tbSuspectedList
    }

    suspend fun getTbConfirmedDetailsFromServer(): Int {
        return withContext(Dispatchers.IO) {

            try {

                val user =
                    preferenceDao.getLoggedInUser()
                        ?: throw IllegalStateException("No user logged in!!")

                val response = tmcNetworkApiService.getTBConfirmedData()
                val statusCode = response.code()

                if (statusCode == 200) {
                    val responseString = response.body()?.string()

                    if (responseString != null) {
                        val jsonObj = JSONObject(responseString)

                        val errorMessage = jsonObj.getString("errorMessage")
                        val responseStatusCode = jsonObj.getInt("statusCode")

                        when (responseStatusCode) {
                            200 -> {
                                try {
                                    val dataObj = jsonObj.getString("data")

                                    saveTBConfirmedCacheFromResponse(dataObj)

                                } catch (e: CancellationException) {
                                    throw e // a cancelled pull must stop, not report 0 / -1 records
                                } catch (e: Exception) {
                                    Timber.e(e, "TBConfirmed: Error while saving data")
                                    return@withContext 0
                                }

                                return@withContext 1
                            }

                            5002 -> {
                                if (userRepo.refreshTokenTmc(user.userName, user.password))
                                    throw SocketTimeoutException("Refreshed Token!")
                                else
                                    throw IllegalStateException("User Logged out!!")
                            }

                            5000 -> {
                                if (errorMessage == "No record found") {
                                    return@withContext 0
                                }
                            }

                            else -> {
                                throw IllegalStateException("$responseStatusCode received, don't know what todo!?")
                            }
                        }
                    }
                }

            } catch (e: CancellationException) {
                throw e // a cancelled pull must stop, not report 0 / -1 records
            } catch (e: SocketTimeoutException) {
                return@withContext -2

            } catch (e: IllegalStateException) {
                return@withContext -1
            } catch (e: Exception) {
                return@withContext -1
            }

            -1
        }
    }

    // rethrow: the changes feed must not save its token past records that were not saved, so it
    // gets every failure; the full pull keeps logging and carrying on as before.
    private suspend fun saveTBConfirmedCacheFromResponse(
        dataObj: String,
        rethrow: Boolean = false
    ): MutableList<TBConfirmedTreatmentCache> {

        val tbConfirmedList = mutableListOf<TBConfirmedTreatmentCache>()

        try {
            val requestDTO = Gson().fromJson(dataObj, TBConfirmedRequestDTO::class.java)

            // One transaction per [pullWriteChunkSize] records instead of one per record - see
            // that constant for why.
            requestDTO?.tbConfirmedList.orEmpty().chunked(pullWriteChunkSize).forEach { chunk ->
                database.withTransaction {
                    chunk.forEach { tbConfirmedDTO ->

                        try {
                            val cache = tbConfirmedDTO.toCache()
                            // Get all existing follow-ups for this ben
                            val allExisting = tbDao.getAllFollowUpsForBeneficiary(cache.benId)
                            // Find exact match by followUpDate
                            val existing = allExisting.find {
                                it.followUpDate != null &&
                                        cache.followUpDate != null &&
                                        it.followUpDate == cache.followUpDate
                            }
                            if (shouldApplyServerRecord(
                                    existing?.syncState,
                                    existing?.serverUpdatedDate,
                                    cache.serverUpdatedDate ?: 0L
                                )
                            ) {
                                val cacheToSave = if (existing != null) cache.copy(id = existing.id) else cache
                                tbDao.saveTbConfirmed(cacheToSave)
                                tbConfirmedList.add(cacheToSave)
                            }

                        } catch (e: CancellationException) {
                            // Must propagate: without this, a cancelled worker keeps looping
                            // because every later suspend call in the chunk throws and is
                            // swallowed here, so the transaction only notices at the end.
                            throw e
                        } catch (e: Exception) {
                            // The changes feed must not move its token past a record that wasn't saved.
                            if (rethrow) throw e
                            Timber.e(e, "TBConfirmed: skipping one record in this chunk")
                        }
                    }
                }
            }

        } catch (e: CancellationException) {
            throw e // a cancelled pull must stop, not report 0 / -1 records
        } catch (e: Exception) {
            if (rethrow) throw e
            Timber.e(e, "TBConfirmed: Error parsing or saving JSON")
        }

        return tbConfirmedList
    }
}
