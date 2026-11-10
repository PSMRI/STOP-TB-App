package org.piramalswasthya.stoptb.repositories

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.flowOn
import org.piramalswasthya.stoptb.utils.throttleLatest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import org.piramalswasthya.stoptb.database.room.InAppDb
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.database.room.dao.BenDao
import org.piramalswasthya.stoptb.database.room.dao.VitalDao
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.model.VitalCache
import org.piramalswasthya.stoptb.network.AmritApiService
import org.piramalswasthya.stoptb.network.GeneralExaminationGetRequest
import org.piramalswasthya.stoptb.network.GeneralExaminationRecord
import org.piramalswasthya.stoptb.network.GeneralExaminationSaveRequest
import timber.log.Timber
import java.io.IOException
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

class VitalRepo @Inject constructor(
    private val vitalDao: VitalDao,
    private val benDao: BenDao,
    private val preferenceDao: PreferenceDao,
    private val userRepo: UserRepo,
    private val tmcNetworkApiService: AmritApiService,
    private val database: InAppDb,
    @org.piramalswasthya.stoptb.di.ApplicationScope private val appScope: kotlinx.coroutines.CoroutineScope
) {

    private companion object {
        /** Records per write transaction in the general-examination pull. */
        const val pullWriteChunkSize = 500
        /** Records per push request (PR 7.3), same as the TB pushes. */
        const val VITALS_CHUNK_SIZE = 20

        /**
         * PR 7.4: serializes the background push and the push right after a save, which could
         * POST the same vital twice. Process-wide because VitalRepo is not a singleton.
         */
        private val pushMutex = kotlinx.coroutines.sync.Mutex()
    }

    suspend fun getVitals(benId: Long): VitalCache? {
        return withContext(Dispatchers.IO) {
            vitalDao.getVitals(benId)
        }
    }

    /**
     * One query for all three sets. Previously these were three Room Flows over the same table,
     * so every write to BEN_VITALS re-ran three queries and woke three collectors.
     */
    private val vitalBenIdStates: Flow<List<org.piramalswasthya.stoptb.model.BenIdSyncState>> =
        vitalDao.getVitalBenIdSyncStates()
            // One query shared by all three sets and every collector of this repo instance,
            // delivering at most 2 updates/s to the list screens during sync.
            .throttleLatest(500L)
            .shareIn(appScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), replay = 1)

    val vitalBenIds: Flow<List<Long>> = vitalBenIdStates.map { rows -> rows.map { it.benId } }.flowOn(Dispatchers.Default)
    val unsyncedVitalBenIds: Flow<List<Long>> =
        vitalBenIdStates.map { rows -> rows.filter { it.syncState == SyncState.UNSYNCED }.map { it.benId } }.flowOn(Dispatchers.Default)
    val syncingVitalBenIds: Flow<List<Long>> =
        vitalBenIdStates.map { rows -> rows.filter { it.syncState == SyncState.SYNCING }.map { it.benId } }.flowOn(Dispatchers.Default)

    suspend fun saveVitals(vitalCache: VitalCache) {
        withContext(Dispatchers.IO) {
            vitalDao.saveVitals(vitalCache)
        }
    }

    suspend fun saveVitalsAndSync(vitalCache: VitalCache): Boolean {
        return withContext(Dispatchers.IO) {
            val localData = vitalCache.copy(syncState = SyncState.UNSYNCED)

            vitalDao.saveVitals(localData)

            // Network failures leave the record UNSYNCED for the background push.
            pushMutex.withLock {
                val synced = try {
                    pushSingleVital(localData)
                } catch (e: IOException) {
                    Timber.w(e, "Vital push deferred to background sync for benId=%s", localData.benId)
                    false
                }

                if (synced) {
                    vitalDao.markSyncedIfUnchanged(localData)
                }

                synced
            }
        }
    }

    /**
     * Pushes UNSYNCED vitals in chunks of [VITALS_CHUNK_SIZE] (PR 7.3; it was one request per
     * record). stoptb/nurse/generalExamination/save takes a list in one transaction with the
     * same per-beneficiary, per-day visit handling as single sends. A rejected chunk is retried
     * one record at a time, so one bad record does not hold back the rest. Records whose
     * beneficiary has no server benRegId yet are not sent and stay UNSYNCED, as before. Each
     * sent chunk is marked SYNCED in one transaction, only for rows unchanged since read.
     * A network error still stops the run (thrown, as before) so the worker retries.
     */
    suspend fun pushUnSyncedVitals(): Boolean = pushMutex.withLock {
        withContext(Dispatchers.IO) {
            val vitals = vitalDao.getVitals(SyncState.UNSYNCED)
            if (vitals.isEmpty()) return@withContext true
            val user = preferenceDao.getLoggedInUser() ?: return@withContext true

            var successCount = 0
            var failCount = 0
            val queue = ArrayDeque(vitals.chunked(VITALS_CHUNK_SIZE))

            while (queue.isNotEmpty()) {
                val chunk = queue.removeFirst()
                val sendable = chunk.mapNotNull { vital ->
                    val benRegId = benDao.getBen(vital.benId)?.benRegId?.takeIf { it > 0L }
                    if (benRegId == null) {
                        Timber.d("Vital push waiting for beneficiary registration: benId=%s", vital.benId)
                        null
                    } else {
                        vital to benRegId
                    }
                }
                failCount += chunk.size - sendable.size
                if (sendable.isEmpty()) continue

                if (pushVitalChunk(sendable, user)) {
                    database.withTransaction {
                        sendable.forEach { (vital, _) -> vitalDao.markSyncedIfUnchanged(vital) }
                    }
                    successCount += sendable.size
                } else if (sendable.size > 1) {
                    sendable.forEach { (vital, _) -> queue.addLast(listOf(vital)) }
                } else {
                    failCount += 1
                }
            }

            Timber.d(
                "General Examination push complete: %s succeeded, %s failed out of %s",
                successCount,
                failCount,
                vitals.size
            )

            // Keep the worker successful so failed records remain UNSYNCED for the next refresh.
            true
        }
    }

    suspend fun fetchGeneralExaminationsFromApi(
        request: GeneralExaminationGetRequest
    ): List<GeneralExaminationRecord> {
        return withContext(Dispatchers.IO) {
            try {
                val response = tmcNetworkApiService.getGeneralExaminations(request)
                val responseString = response.body()?.string()

                if (response.isSuccessful && !responseString.isNullOrBlank()) {
                    val jsonObj = JSONObject(responseString)
                    if (jsonObj.optInt("statusCode") == 200) {
                        parseGeneralExaminationRecords(jsonObj)
                    } else {
                        Timber.e("General examination get failed. statusCode=%s", jsonObj.optInt("statusCode"))
                        emptyList()
                    }
                } else {
                    Timber.e(
                        "General examination get failed. code=%s message=%s",
                        response.code(),
                        response.message()
                    )
                    emptyList()
                }
            } catch (e: Exception) {
                Timber.e(e, "General examination get API failed")
                emptyList()
            }
        }
    }

    suspend fun getGeneralExaminationDetailsFromServer(): Int {
        return withContext(Dispatchers.IO) {
            val user = preferenceDao.getLoggedInUser()
                ?: throw IllegalStateException("No user logged in!!")
            val villageId = preferenceDao.getLocationRecord()?.village?.id ?: return@withContext 0

            try {
                val records = fetchGeneralExaminationsFromApi(
                    GeneralExaminationGetRequest(
                        providerServiceMapID = user.serviceMapId,
                        villageID = villageId
                    )
                )

                saveGeneralExaminationRecords(records)

                if (records.isEmpty()) 0 else 1
            } catch (e: SocketTimeoutException) {
                Timber.e("get_general_examination error : $e")
                -2
            } catch (e: IllegalStateException) {
                Timber.e("get_general_examination error : $e")
                -1
            } catch (e: Exception) {
                Timber.e(e, "General examination pull failed")
                -1
            }
        }
    }

    // Network failures (IOException) propagate so the push worker retries with
    // backoff. A token refresh is retried at most once.
    private suspend fun pushSingleVital(vitalCache: VitalCache): Boolean {
        val user = preferenceDao.getLoggedInUser() ?: return false

        // The benRegId stored with the vital is the one known when the form was
        // opened, which is 0 for a beneficiary not yet registered on the server.
        // Use the current one, and wait until registration has assigned it.
        val benRegId = benDao.getBen(vitalCache.benId)?.benRegId?.takeIf { it > 0L }
        if (benRegId == null) {
            Timber.d("Vital push waiting for beneficiary registration: benId=%s", vitalCache.benId)
            return false
        }
        return pushVitalChunk(listOf(vitalCache to benRegId), user)
    }

    /** One save request for [records] (vital to current benRegId); true when the server accepted it. */
    private suspend fun pushVitalChunk(
        records: List<Pair<VitalCache, Long>>,
        user: org.piramalswasthya.stoptb.model.User,
        retriedAfterRefresh: Boolean = false
    ): Boolean {
        return try {
            val response = tmcNetworkApiService.saveGeneralExamination(
                records.map { (vital, benRegId) -> GeneralExaminationSaveRequest.from(vital.copy(benRegId = benRegId), user) }
            )

            val responseBody = response.body()?.string()

            if (!response.isSuccessful || responseBody.isNullOrBlank()) {
                return false
            }

            val statusCode = JSONObject(responseBody).optInt("statusCode", 0)

            when (statusCode) {
                200 -> true

                401, 5002 -> {
                    if (!retriedAfterRefresh && userRepo.refreshTokenTmc(user.userName, user.password)) {
                        pushVitalChunk(records, user, retriedAfterRefresh = true)
                    } else {
                        false
                    }
                }

                else -> false
            }

        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            if (!retriedAfterRefresh && shouldRetryWithRefresh(e, user.userName, user.password)) {
                pushVitalChunk(records, user, retriedAfterRefresh = true)
            } else {
                Timber.e(e, "Vital sync failed for benIds=%s", records.map { (vital, _) -> vital.benId })
                false
            }
        }
    }

    private suspend fun shouldRetryWithRefresh(
        error: Exception,
        userName: String,
        password: String
    ): Boolean {
        val message = error.message.orEmpty()

        return if (
            message.contains("401") ||
            message.contains("5002") ||
            message.contains("Invalid login key", ignoreCase = true)
        ) {
            userRepo.refreshTokenTmc(userName, password)
        } else {
            false
        }
    }

    private fun parseIntList(value: String?): List<Int>? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            val jsonArray = JSONArray(value)
            List(jsonArray.length()) { index -> jsonArray.optInt(index) }
        }.getOrNull()
    }

    private fun parseStringList(value: String?): List<String>? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            val jsonArray = JSONArray(value)
            List(jsonArray.length()) { index -> jsonArray.optString(index) }
                .filter { it.isNotBlank() }
        }.getOrNull()
    }

    // Records from the changes feed, in the same JSON getAll returns.
    suspend fun applyFeedGeneralExaminations(records: JSONArray) {
        withContext(Dispatchers.IO) {
            saveGeneralExaminationRecords(parseGeneralExaminationRecords(JSONObject().put("data", records)))
        }
    }

    private suspend fun saveGeneralExaminationRecords(records: List<GeneralExaminationRecord>) {
        // One transaction per [pullWriteChunkSize] records instead of one per record.
        // Room's InvalidationTracker fires per transaction, so a 20,000-record payload
        // used to invalidate VITALS - and every query observing it - twenty thousand
        // times, each invalidation re-running the open screen's list queries while the
        // next record waited behind them for the connection.
        records.chunked(pullWriteChunkSize).forEach { chunk ->
            database.withTransaction {
                chunk.forEach perRecord@ { record ->
                    val beneficiary = record.beneficiaryID?.let { benDao.getBen(it) }
                        ?: record.beneficiaryRegID?.let { benDao.getBenByRegId(it) }
                    val benId = beneficiary?.beneficiaryId ?: record.beneficiaryID ?: return@perRecord
                    val benRegId = record.beneficiaryRegID ?: beneficiary?.benRegId ?: return@perRecord

                    val existing = vitalDao.getVitals(benId)
                    val serverUpdatedDate = parseServerUpdateDate(record.updateDate ?: record.createdDate)
                    if (!shouldApplyServerRecord(
                            existing?.syncState,
                            existing?.serverUpdatedDate,
                            existing?.capturedAt,
                            serverUpdatedDate
                        )
                    ) {
                        return@perRecord
                    }
                    vitalDao.saveVitals(
                        (existing ?: VitalCache(benId = benId, benRegId = benRegId)).copy(
                            benRegId = benRegId,
                            pulseRate = record.pulseRate,
                            bpSystolic = record.systolicBP,
                            bpDiastolic = record.diastolicBP,
                            rbs = record.randomBloodSugar,
                            pallorId = record.pallorId,
                            pallor = record.pallor,
                            icterusId = record.icterusId,
                            icterus = record.icterus,
                            cyanosisId = record.cyanosisId,
                            cyanosis = record.cyanosis,
                            clubbingId = record.clubbingId,
                            clubbing = record.clubbing,
                            lymphadenopathyId = record.lymphadenopathyId,
                            lymphadenopathy = record.lymphadenopathy,
                            oedemaId = record.oedemaId,
                            oedema = record.oedema,
                            keyPopulationRiskFactorIds = parseIntList(record.keyPopulationRiskFactorIds),
                            keyPopulationRiskFactors = parseStringList(record.keyPopulationRiskFactors),
                            hivStatusId = record.hivStatusId,
                            hivStatus = record.hivStatus,
                            referralToHwcNeeded = record.referralToHWCNeeded,
                            serverUpdatedDate = serverUpdatedDate.takeIf { it > 0L },
                            syncState = SyncState.SYNCED
                        )
                    )
                }
            }
        }
    }

    private fun parseGeneralExaminationRecords(jsonObj: JSONObject): List<GeneralExaminationRecord> {
        val records = when (val data = jsonObj.opt("data")) {
            is JSONArray -> data
            is JSONObject -> data.optJSONArray("data") ?: JSONArray()
            else -> JSONArray()
        }

        return List(records.length()) { index -> records.optJSONObject(index) }
            .mapNotNull { item ->
                item ?: return@mapNotNull null
                GeneralExaminationRecord(
                    id = item.optLongOrNull("id"),
                    beneficiaryID = item.optLongOrNull("beneficiaryID"),
                    beneficiaryRegID = item.optLongOrNull("beneficiaryRegID"),
                    createdBy = item.optStringOrNull("createdBy"),
                    providerServiceMapID = item.optIntOrNull("providerServiceMapID"),
                    pulseRate = item.optIntOrNull("pulseRate"),
                    systolicBP = item.optIntOrNull("systolicBP"),
                    diastolicBP = item.optIntOrNull("diastolicBP"),
                    randomBloodSugar = item.optDoubleOrNull("randomBloodSugar"),
                    pallorId = item.optIntOrNull("pallorId"),
                    pallor = item.optStringOrNull("pallor"),
                    icterusId = item.optIntOrNull("icterusId"),
                    icterus = item.optStringOrNull("icterus"),
                    cyanosisId = item.optIntOrNull("cyanosisId"),
                    cyanosis = item.optStringOrNull("cyanosis"),
                    clubbingId = item.optIntOrNull("clubbingId"),
                    clubbing = item.optStringOrNull("clubbing"),
                    lymphadenopathyId = item.optIntOrNull("lymphadenopathyId"),
                    lymphadenopathy = item.optStringOrNull("lymphadenopathy"),
                    oedemaId = item.optIntOrNull("oedemaId"),
                    oedema = item.optStringOrNull("oedema"),
                    keyPopulationRiskFactorIds = item.optJsonArrayString("keyPopulationRiskFactorIds"),
                    keyPopulationRiskFactors = item.optJsonArrayString("keyPopulationRiskFactors"),
                    hivStatusId = item.optIntOrNull("hivStatusId"),
                    hivStatus = item.optStringOrNull("hivStatus"),
                    referralToHWCNeeded = item.optBooleanOrNull("referralToHWCNeeded"),
                    createdDate = item.optStringOrNull("createdDate"),
                    updateDate = item.optStringOrNull("updateDate")
                        ?: item.optStringOrNull("updatedDate")
                )
            }
    }

    private fun parseServerUpdateDate(dateString: String?): Long {
        if (dateString.isNullOrBlank() || dateString.equals("null", ignoreCase = true)) return 0L
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "MMM dd, yyyy h:mm:ss a",
            "MMM d, yyyy h:mm:ss a"
        )
        patterns.forEach { pattern ->
            runCatching {
                SimpleDateFormat(pattern, Locale.ENGLISH).parse(dateString)?.time
            }.getOrNull()?.let { return it }
        }
        return 0L
    }

    private fun shouldApplyServerRecord(
        existingSyncState: SyncState?,
        savedServerUpdatedDate: Long?,
        localCapturedAt: Long?,
        serverUpdatedDate: Long
    ): Boolean {
        if (existingSyncState != null && existingSyncState != SyncState.SYNCED) {
            val localTime = localCapturedAt ?: 0L
            val clockSkewToleranceMs = 5 * 60 * 1000L
            return serverUpdatedDate > 0L && serverUpdatedDate + clockSkewToleranceMs >= localTime
        }
        if (serverUpdatedDate <= 0L) return true
        return serverUpdatedDate > (savedServerUpdatedDate ?: 0L)
    }

    private fun JSONObject.optLongOrNull(name: String): Long? =
        if (!has(name) || isNull(name)) null else optLong(name)


    private fun JSONObject.optIntOrNull(name: String): Int? =
        if (!has(name) || isNull(name)) null else optInt(name)

    private fun JSONObject.optDoubleOrNull(name: String): Double? =
        if (!has(name) || isNull(name)) null else optDouble(name).takeIf { !it.isNaN() }

    private fun JSONObject.optBooleanOrNull(name: String): Boolean? =
        if (!has(name) || isNull(name)) null else optBoolean(name)

    private fun JSONObject.optJsonArrayString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return when (val value = opt(name)) {
            is JSONArray -> value.toString()
            is String -> value.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            else -> null
        }
    }

    private fun JSONObject.optStringOrNull(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name).takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
    }
}
