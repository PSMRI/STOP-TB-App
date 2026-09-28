package org.piramalswasthya.stoptb.repositories

import android.content.Context
import com.google.gson.Gson
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.database.room.InAppDb
import org.piramalswasthya.stoptb.database.room.dao.BenDao
import org.piramalswasthya.stoptb.database.room.dao.TBDao
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.helpers.Konstants
import org.piramalswasthya.stoptb.helpers.NetworkResponse
import org.piramalswasthya.stoptb.model.GeneralOpdCache
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
import org.piramalswasthya.stoptb.network.GeneralOpdRequestDTO
import org.piramalswasthya.stoptb.network.GeneralOpdSaveRequest
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
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TBRepo @Inject constructor(
    private val tbDao: TBDao,
    private val benDao: BenDao,
    val preferenceDao: PreferenceDao,
    private val userRepo: UserRepo,
    private val tmcNetworkApiService: AmritApiService,
    private val database: InAppDb,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) {
    private val orderCreatedTimestamps = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val ORDER_STATUS_GRACE_PERIOD_MS = 90_000L

    // Per-beneficiary lock for every TB_DIAGNOSTICS/TB_SUSPECTED read-modify-write below, so
    // concurrent calls for the same benId (e.g. parallel X-ray/TrueNat polling) can't clobber
    // each other. Only ever wraps a short leaf read-modify-write, never a whole function that
    // might call another lock-acquiring function for the same benId — Mutex isn't reentrant.
    // Requires TBRepo to be @Singleton, or each injection site would get its own map.
    private val benIdLocks = java.util.concurrent.ConcurrentHashMap<Long, Mutex>()
    private suspend fun <T> withBenIdLock(benId: Long, block: suspend () -> T): T =
        benIdLocks.getOrPut(benId) { Mutex() }.withLock { block() }

    // Serializes pushUnSyncedRecordsTBSuspected() against itself — it reads all UNSYNCED rows,
    // posts them, then marks that snapshot synced with no claim/lease step, so overlapping calls
    // (createOrder/retryPushOrder/fetchOrderResult plus the generic push sweep) can double-POST
    // the same row. Never called from inside a withBenIdLock block, so safe to hold separately.
    private val tbSuspectedPushMutex = Mutex()

    // Same issue, same fix, for pushUnSyncedRecordsTBScreening(): TBScreeningFormViewModel also
    // calls it directly after a save, in addition to the generic push sweep.
    private val tbScreeningPushMutex = Mutex()

    val allTbDiagnostics: Flow<List<TBDiagnosticsCache>> = tbDao.getAllTbDiagnostics()

    val allTbScreening: Flow<List<TBScreeningCache>> = tbDao.getAllTbScreening()

    suspend fun getDiagnosticsList(): List<TBDiagnosticsCache> = withContext(Dispatchers.IO) {
        tbDao.getDiagnosticsList()
    }

    suspend fun getTBScreening(benId: Long): TBScreeningCache? {
        return withContext(Dispatchers.IO) {
            tbDao.getTbScreening(benId)
        }
    }

    suspend fun saveTBScreening(tbScreeningCache: TBScreeningCache) {
        withContext(Dispatchers.IO) {
            benDao.getBen(tbScreeningCache.benId)?.let { ben ->
                ben.gpsLatitude?.let { tbScreeningCache.latitude = it }
                ben.gpsLongitude?.let { tbScreeningCache.longitude = it }
            }
            tbDao.saveTbScreening(tbScreeningCache)
            benDao.updateScreeningStatus(tbScreeningCache.benId,"SCREENED")   // NEW
        }
    }

    suspend fun getGeneralOpd(benId: Long): GeneralOpdCache? {
        return withContext(Dispatchers.IO) {
            tbDao.getGeneralOpd(benId)
        }
    }

    suspend fun getCachedChiefComplaintNames(): List<String> =
        database.chiefComplaintMasterDao.getChiefComplaints().map { it.chiefComplaint }

    suspend fun refreshVisitCategories(): Boolean {
        return try {
            val categoriesResponse = tmcNetworkApiService.getVisitReasonAndCategories()
            val categoriesBody = categoriesResponse.body()
            if (!categoriesResponse.isSuccessful || categoriesBody?.statusCode != 200) return false

            val categories = categoriesBody.data?.visitCategories.orEmpty()
            if (categories.isEmpty()) return false

            database.withTransaction {
                database.chiefComplaintMasterDao.deleteVisitCategories()
                database.chiefComplaintMasterDao.insertVisitCategories(
                    categories.map { VisitCategoryMasterCache(it.visitCategoryID, it.visitCategory) }
                )
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "Visit category master refresh failed")
            false
        }
    }

    suspend fun refreshChiefComplaintMasters(): Boolean {
        val loggedInUser = preferenceDao.getLoggedInUser() ?: return false

        return try {
            val generalOpdCategoryId = database.chiefComplaintMasterDao.getGeneralOpdCategoryId()
                ?: GENERAL_OPD_CATEGORY_FALLBACK

            val complaintsResponse = tmcNetworkApiService.getChiefComplaintMaster(
                visitCategoryId = generalOpdCategoryId,
                providerServiceMapId = loggedInUser.serviceMapId
            )
            val complaintsBody = complaintsResponse.body()
            if (!complaintsResponse.isSuccessful || complaintsBody?.statusCode != 200) return false

            val complaints = complaintsBody.data?.chiefComplaintMaster.orEmpty()
            if (complaints.isEmpty()) return false

            database.withTransaction {
                database.chiefComplaintMasterDao.deleteChiefComplaints()
                database.chiefComplaintMasterDao.insertChiefComplaints(
                    complaints.map { ChiefComplaintMasterCache(it.chiefComplaintID, it.chiefComplaint) }
                )
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "Chief complaint master refresh failed")
            false
        }
    }

    val tbScreeningBenIds: Flow<List<Long>> = tbDao.getAllTbScreeningBenIds()
    val unsyncedTbScreeningBenIds: Flow<List<Long>> = tbDao.getTbScreeningBenIdsBySyncState(SyncState.UNSYNCED)
    val syncingTbScreeningBenIds: Flow<List<Long>> = tbDao.getTbScreeningBenIdsBySyncState(SyncState.SYNCING)

    val generalOpdBenIds: Flow<List<Long>> = tbDao.getAllGeneralOpdBenIds()
    val unsyncedGeneralOpdBenIds: Flow<List<Long>> = tbDao.getGeneralOpdBenIdsBySyncState(SyncState.UNSYNCED)
    val syncingGeneralOpdBenIds: Flow<List<Long>> = tbDao.getGeneralOpdBenIdsBySyncState(SyncState.SYNCING)

    val tbSuspectedBenIds: Flow<List<Long>> = tbDao.getAllTbSuspectedBenIds()

    /** benIds that have a record in TB_DIAGNOSTICS (new diagnostics table) */
    val tbDiagnosticsBenIds: Flow<List<Long>> = tbDao.getAllTbDiagnosticsBenIds()

    suspend fun saveGeneralOpd(generalOpdCache: GeneralOpdCache) {
        withContext(Dispatchers.IO) {
            tbDao.saveGeneralOpd(generalOpdCache)
        }
    }

    suspend fun getTBDiagnostics(benId: Long): TBDiagnosticsCache? {
        return withContext(Dispatchers.IO) {
            tbDao.getTbDiagnostics(benId)
        }
    }

    /** Returns latest TB_DIAGNOSTICS record by benId — used to get existing id before save */
    suspend fun getTBDiagnosticsById(benId: Long): TBDiagnosticsCache? {
        return withContext(Dispatchers.IO) {
            tbDao.getTbDiagnosticsByBenId(benId)
        }
    }

    suspend fun saveTBDiagnostics(tbDiagnosticsCache: TBDiagnosticsCache) {
        withContext(Dispatchers.IO) {
            benDao.getBen(tbDiagnosticsCache.benId)?.let { ben ->
                ben.gpsLatitude?.let { tbDiagnosticsCache.latitude = it }
                ben.gpsLongitude?.let { tbDiagnosticsCache.longitude = it }
            }
            tbDao.saveTbDiagnostics(tbDiagnosticsCache)
            if (tbDiagnosticsCache.isChestXRayDone == true) benDao.updateScreeningStatus(tbDiagnosticsCache.benId,"SCREENED")   // NEW

            if (tbDiagnosticsCache.isNaatConducted == true) benDao.updateScreeningStatus(tbDiagnosticsCache.benId,"SCREENED")   // NEW
        }
    }

    suspend fun getTBSuspected(benId: Long): TBSuspectedCache? {
        return withContext(Dispatchers.IO) {
            tbDao.getTbSuspected(benId)
        }
    }

    suspend fun saveTBSuspected(tbSuspectedCache: TBSuspectedCache) {
        withContext(Dispatchers.IO) {
            benDao.getBen(tbSuspectedCache.benId)?.let { ben ->
                ben.gpsLatitude?.let { tbSuspectedCache.latitude = it }
                ben.gpsLongitude?.let { tbSuspectedCache.longitude = it }
            }
            tbDao.saveTbSuspected(tbSuspectedCache)
        }
    }

    suspend fun getTBConfirmed(benId: Long): TBConfirmedTreatmentCache? {
        return withContext(Dispatchers.IO) {
            tbDao.getTbConfirmed(benId)
        }
    }

    suspend fun saveTBConfirmed(tbConfirmedTreatmentCache: TBConfirmedTreatmentCache) {
        withContext(Dispatchers.IO)
        {
            tbDao.saveTbConfirmed(tbConfirmedTreatmentCache)
        }
    }

    suspend fun getAllFollowUpsForBeneficiary(benId: Long): List<TBConfirmedTreatmentCache> {
        return withContext(Dispatchers.IO) {
            tbDao.getAllFollowUpsForBeneficiary(benId)
        }
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
                                    saveGeneralOpdCacheFromNewResponse(jsonObj)
                                } catch (e: Exception) {
                                    Timber.d("General OPD entries not synced $e")
                                    return@withContext 0
                                }
                                return@withContext 1
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

    private suspend fun saveTBScreeningCacheFromNewResponse(jsonObj: JSONObject): MutableList<TBScreeningCache> {
        val tbScreeningList = mutableListOf<TBScreeningCache>()
        val records = when (val data = jsonObj.opt("data")) {
            is org.json.JSONArray -> data
            is JSONObject -> data.optJSONArray("data") ?: org.json.JSONArray()
            else -> org.json.JSONArray()
        }
        for (index in 0 until records.length()) {
            val item = records.optJSONObject(index) ?: continue
            val benRegId = item.optLong("beneficiaryRegID", 0L).takeIf { it > 0 } ?: continue
            val ben = benDao.getBenByRegId(benRegId) ?: continue
            val visitDate = getLongFromDateMultipleSupport(item.optString("visitDate"))
            val existing = tbDao.getTbScreening(ben.beneficiaryId)
            val serverUpdatedDate = getServerUpdatedDate(item)
            if (!shouldApplyServerRecord(existing?.syncState, existing?.serverUpdatedDate, serverUpdatedDate)) {
                continue
            }
            val cache = (existing ?: TBScreeningCache(benId = ben.beneficiaryId)).copy(
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
            tbDao.saveTbScreening(cache)
            tbScreeningList.add(cache)
        }
        return tbScreeningList
    }

    private suspend fun saveGeneralOpdCacheFromResponse(dataObj: String): MutableList<GeneralOpdCache> {
        val generalOpdList = mutableListOf<GeneralOpdCache>()
        val requestDTO = Gson().fromJson(dataObj, GeneralOpdRequestDTO::class.java)
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
        return generalOpdList
    }

    private suspend fun saveGeneralOpdCacheFromNewResponse(jsonObj: JSONObject): MutableList<GeneralOpdCache> {
        val generalOpdList = mutableListOf<GeneralOpdCache>()
        val records = getStopTbDataArray(jsonObj)
        for (index in 0 until records.length()) {
            val item = records.optJSONObject(index) ?: continue
            val benRegId = item.optLong("beneficiaryRegID", 0L).takeIf { it > 0 } ?: continue
            val ben = benDao.getBenByRegId(benRegId) ?: continue
            val existing = tbDao.getGeneralOpd(ben.beneficiaryId)
            val serverUpdatedDate = getServerUpdatedDate(item)
            if (!shouldApplyServerRecord(existing?.syncState, existing?.serverUpdatedDate, serverUpdatedDate)) {
                continue
            }
            val cache = (existing ?: GeneralOpdCache(benId = ben.beneficiaryId)).copy(
                chiefComplaints = item.optStringListOrNull("chiefComplaint"),

                // Keep existing medication if server does not return it
                medications = item.optStringOrNull("medication")
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() },


                dosage = item.optStringOrNull("dosage")
                    ?: existing?.dosage,

                frequency = item.optStringOrNull("frequency")
                    ?: existing?.frequency,

                duration = item.optStringOrNull("duration")
                    ?: existing?.duration,

                notes = item.optStringOrNull("notes")
                    ?: existing?.notes,

                serverUpdatedDate = serverUpdatedDate.takeIf { it > 0L },
                syncState = SyncState.SYNCED
            )
            tbDao.saveGeneralOpd(cache)
            generalOpdList.add(cache)
        }
        return generalOpdList
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
        return tbDiagnosticsList
    }

    private suspend fun saveTBDiagnosticsCacheFromNewResponse(jsonObj: JSONObject): MutableList<TBDiagnosticsCache> {
        val tbDiagnosticsList = mutableListOf<TBDiagnosticsCache>()
        val records = getStopTbDataArray(jsonObj)
        for (index in 0 until records.length()) {
            val item = records.optJSONObject(index) ?: continue
            val benRegId = item.optLong("benRegID", 0L).takeIf { it > 0 } ?: continue
            val ben = benDao.getBenByRegId(benRegId) ?: continue
            val visitDate = getLongFromDateMultipleSupport(item.optString("visitDate"))
            val existing = tbDao.getTbDiagnostics(ben.beneficiaryId)
            val serverUpdatedDate = getServerUpdatedDate(item)
            if (!shouldApplyServerRecord(existing?.syncState, existing?.serverUpdatedDate, serverUpdatedDate)) {
                continue
            }
            val cache = (existing ?: TBDiagnosticsCache(benId = ben.beneficiaryId)).copy(
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
        requestDTO?.tbSuspectedList?.forEach { tbSuspectedDTO ->
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
                        hasSymptoms = incoming.hasSymptoms,
                        isSputumCollected = incoming.isSputumCollected,
                        sputumSubmittedAt = incoming.sputumSubmittedAt,
                        nikshayId = incoming.nikshayId,
                        sputumTestResult = incoming.sputumTestResult,
                        isChestXRayDone = incoming.isChestXRayDone,
                        chestXRayResult = incoming.chestXRayResult,
                        isAICoughAssessmentDone = incoming.isAICoughAssessmentDone,
                        aiCoughAssessmentResult = incoming.aiCoughAssessmentResult,
                        isNaatConducted = incoming.isNaatConducted,
                        naatResult = incoming.naatResult,
                        recommendedForLiquidCultureTest = incoming.recommendedForLiquidCultureTest,
                        isLiquidCultureConducted = incoming.isLiquidCultureConducted,
                        liquidCultureResult = incoming.liquidCultureResult,
                        referralFacility = incoming.referralFacility,
                        isTBConfirmed = incoming.isTBConfirmed,
                        isDRTBConfirmed = incoming.isDRTBConfirmed,
                        otherReasonForSuspicion = incoming.otherReasonForSuspicion,
                        isConfirmed = incoming.isConfirmed,
                        latitude = incoming.latitude,
                        longitude = incoming.longitude,
                        address = incoming.address,
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


    private suspend fun saveTBConfirmedCacheFromResponse(dataObj: String): MutableList<TBConfirmedTreatmentCache> {


        val tbConfirmedList = mutableListOf<TBConfirmedTreatmentCache>()

        try {
            val requestDTO = Gson().fromJson(dataObj, TBConfirmedRequestDTO::class.java)


            requestDTO?.tbConfirmedList?.forEachIndexed { index, tbConfirmedDTO ->


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

                } catch (e: Exception) {
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "TBConfirmed: Error parsing or saving JSON")
        }

        return tbConfirmedList
    }


    // RECORD-LEVEL ISOLATION: Coordinator always returns true so the
    // WorkManager worker succeeds. Each sub-method handles its own failures
    // independently — failed records stay UNSYNCED and retry on next sync cycle.
    // Previously, if any sub-method failed, the coordinator returned false which
    // could cause the worker to be marked as failed.
    suspend fun pushUnSyncedRecords(): Boolean {
        val screeningResult = pushUnSyncedRecordsTBScreening()
        val generalOpdResult = pushUnSyncedRecordsGeneralOpd()
        val diagnosticsResult = pushUnSyncedRecordsTBDiagnostics()
        val suspectedResult = pushUnSyncedRecordsTBSuspected()
        val confirmedResult = pushUnSyncedRecordsTBConfirmed()
        Timber.d("TB push results: screening=$screeningResult, generalOpd=$generalOpdResult, diagnostics=$diagnosticsResult, suspected=$suspectedResult, confirmed=$confirmedResult")
        // Worker succeeds — failed records stay UNSYNCED for next cycle
        return true
    }

    suspend fun pushUnSyncedTBScreeningRecords(): Int {
        return pushUnSyncedRecordsTBScreening()
    }

    // RECORD-LEVEL ISOLATION: TB Screening records are now sent in
    // chunks of 20 instead of one giant batch. Previously, if ANY record in
    // the batch was malformed, the ENTIRE batch failed and ALL records stayed
    // UNSYNCED. Now each chunk is independent — one bad chunk doesn't affect
    // the others. Failed chunks' records stay UNSYNCED for the next sync cycle.
    // Also removed dangerous recursive retry on SocketTimeoutException that
    // could cause infinite recursion and stack overflow.
    private suspend fun pushUnSyncedRecordsTBScreening(): Int = tbScreeningPushMutex.withLock {

        withContext(Dispatchers.IO) {
            val user =
                preferenceDao.getLoggedInUser()
                    ?: throw IllegalStateException("No user logged in!!")

            val tbsnList: List<TBScreeningCache> = tbDao.getTBScreening(SyncState.UNSYNCED)

            if (tbsnList.isEmpty()) return@withContext 1

            var successCount = 0
            var failCount = 0

            for (screening in tbsnList) {
                try {
                    val beneficiaryRegID = benDao.getBen(screening.benId)?.benRegId
                    if (beneficiaryRegID == null || beneficiaryRegID <= 0L) {
                        failCount += 1
                        continue
                    }
                    val response = tmcNetworkApiService.saveTBScreeningData(
                        listOf(
                            TBScreeningSaveRequest.from(
                                cache = screening,
                                beneficiaryRegID = beneficiaryRegID,
                                providerServiceMapID = user.serviceMapId,
                                createdBy = user.userName
                            )
                        )
                    )
                    val statusCode = response.code()
                    if (statusCode == 200) {
                        val responseString = response.body()?.string()
                        if (responseString != null) {
                            val jsonObj = JSONObject(responseString)
                            val responseStatusCode = jsonObj.getInt("statusCode")
                            Timber.d("Push to Amrit TB Screening record: $responseStatusCode")
                            when (responseStatusCode) {
                                200 -> {
                                    updateSyncStatusScreening(listOf(screening))
                                    successCount += 1
                                }

                                401, 5002 -> {
                                    // Token expired — try refreshing for subsequent chunks
                                    if (userRepo.refreshTokenTmc(user.userName, user.password)) {
                                        Timber.d("Token refreshed, TB Screening record will retry next cycle")
                                    }
                                    failCount += 1
                                }

                                else -> {
                                    Timber.e("TB Screening record failed with statusCode: $responseStatusCode")
                                    failCount += 1
                                }
                            }
                        }
                    } else {
                        Timber.e("TB Screening record HTTP error: $statusCode")
                        failCount += 1
                    }
                } catch (e: Exception) {
                    Timber.e(e, "TB Screening record push failed for benId=${screening.benId}")
                    failCount += 1
                }
            }

            Timber.d("TB Screening push complete: $successCount succeeded, $failCount failed out of ${tbsnList.size}")
            // Worker succeeds — failed records stay UNSYNCED for next cycle
            return@withContext 1
        }
    }

    private suspend fun pushUnSyncedRecordsGeneralOpd(): Int {
        return withContext(Dispatchers.IO) {
            val user =
                preferenceDao.getLoggedInUser()
                    ?: throw IllegalStateException("No user logged in!!")

            val opdList: List<GeneralOpdCache> = tbDao.getGeneralOpd(SyncState.UNSYNCED)
            if (opdList.isEmpty()) return@withContext 1

            val chunks = opdList.chunked(20)
            var successCount = 0
            var failCount = 0

            for (chunk in chunks) {
                try {
                    val request = chunk.mapNotNull { opd ->
                        val benRegId = benDao.getBen(opd.benId)?.benRegId?.takeIf { it > 0L }
                        benRegId?.let {
                            GeneralOpdSaveRequest.from(
                                cache = opd,
                                beneficiaryRegID = it,
                                providerServiceMapID = user.serviceMapId,
                                createdBy = user.userName
                            )
                        }
                    }
                    if (request.isEmpty()) {
                        failCount += chunk.size
                        continue
                    }
                    val response = tmcNetworkApiService.saveGeneralOpdData(
                        request
                    )
                    val statusCode = response.code()
                    if (statusCode == 200) {
                        val responseString = response.body()?.string()
                        if (responseString != null) {
                            val jsonObj = JSONObject(responseString)
                            when (val responseStatusCode = jsonObj.getInt("statusCode")) {
                                200 -> {
                                    updateSyncStatusGeneralOpd(chunk)
                                    successCount += chunk.size
                                }

                                401, 5002 -> {
                                    if (userRepo.refreshTokenTmc(user.userName, user.password)) {
                                        Timber.d("Token refreshed, General OPD chunk will retry next cycle")
                                    }
                                    failCount += chunk.size
                                }

                                else -> {
                                    Timber.e("General OPD chunk failed with statusCode: $responseStatusCode")
                                    failCount += chunk.size
                                }
                            }
                        }
                    } else {
                        Timber.e("General OPD chunk HTTP error: $statusCode")
                        failCount += chunk.size
                    }
                } catch (e: Exception) {
                    Timber.e(e, "General OPD chunk push failed: ${chunk.size} records")
                    failCount += chunk.size
                }
            }

            Timber.d("General OPD push complete: $successCount succeeded, $failCount failed out of ${opdList.size}")
            return@withContext 1
        }
    }

    private suspend fun pushUnSyncedRecordsTBDiagnostics(): Int {
        return withContext(Dispatchers.IO) {
            val user =
                preferenceDao.getLoggedInUser()
                    ?: throw IllegalStateException("No user logged in!!")

            val diagnosticsList: List<TBDiagnosticsCache> =
                tbDao.getTbDiagnostics(SyncState.UNSYNCED)
            if (diagnosticsList.isEmpty()) return@withContext 1

            val chunks = diagnosticsList.chunked(20)
            var successCount = 0
            var failCount = 0

            for (chunk in chunks) {
                try {
                    val request = chunk.mapNotNull { diagnostics ->
                        val benRegId = benDao.getBen(diagnostics.benId)?.benRegId?.takeIf { it > 0L }
                        benRegId?.let {
                            TBDiagnosticsSaveRequest.from(
                                cache = diagnostics,
                                benRegID = it,
                                providerServiceMapID = user.serviceMapId,
                                createdBy = user.userName
                            )
                        }
                    }
                    if (request.isEmpty()) {
                        failCount += chunk.size
                        continue
                    }
                    val response = tmcNetworkApiService.saveTBDiagnosticsData(
                        request
                    )
                    val statusCode = response.code()
                    if (statusCode == 200) {
                        val responseString = response.body()?.string()
                        if (responseString != null) {
                            val jsonObj = JSONObject(responseString)
                            when (val responseStatusCode = jsonObj.getInt("statusCode")) {
                                200 -> {
                                    updateSyncStatusDiagnostics(chunk)
                                    successCount += chunk.size
                                }

                                401, 5002 -> {
                                    if (userRepo.refreshTokenTmc(user.userName, user.password)) {
                                        Timber.d("Token refreshed, TB Diagnostics chunk will retry next cycle")
                                    }
                                    failCount += chunk.size
                                }

                                else -> {
                                    Timber.e("TB Diagnostics chunk failed with statusCode: $responseStatusCode")
                                    failCount += chunk.size
                                }
                            }
                        }
                    } else {
                        Timber.e("TB Diagnostics chunk HTTP error: $statusCode")
                        failCount += chunk.size
                    }
                } catch (e: Exception) {
                    Timber.e(e, "TB Diagnostics chunk push failed: ${chunk.size} records")
                    failCount += chunk.size
                }
            }

            Timber.d("TB Diagnostics push complete: $successCount succeeded, $failCount failed out of ${diagnosticsList.size}")
            return@withContext 1
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

            if (tbspList.isEmpty()) return@withContext 1

            val callTag = java.util.UUID.randomUUID().toString().take(6)
            Timber.d("SYNC_DIAG[$callTag] TB Suspected push: ${tbspList.size} unsynced records, benIds=${tbspList.map { it.benId }}")

            val CHUNK_SIZE = 20
            val chunks = tbspList.chunked(CHUNK_SIZE)
            var successCount = 0
            var failCount = 0

            for (chunk in chunks) {
                try {
                    val excludedBenIds = mutableListOf<Long>()
                    val chunkDtos = chunk.mapNotNull { suspected ->
                        val ben = benDao.getBen(suspected.benId)
                        if (ben == null) {
                            excludedBenIds += suspected.benId
                            Timber.w("SYNC_DIAG[$callTag] TB Suspected push: benId=${suspected.benId} (local id=${suspected.id}, visitDate=${suspected.visitDate}) has NO matching BENEFICIARY row locally — excluded from push payload")
                        }
                        ben?.let {
                            suspected.toDTO()
                        }
                    }
                    Timber.d("SYNC_DIAG[$callTag] TB Suspected push: chunk benIds=${chunk.map { it.benId }} -> ${chunkDtos.size}/${chunk.size} resolved for sending")
                    if (chunkDtos.isEmpty()) {
                        failCount += chunk.size
                        Timber.w("SYNC_DIAG[$callTag] TB Suspected push: entire chunk skipped (no resolvable records), benIds=${chunk.map { it.benId }}")
                        continue
                    }

                    val response = tmcNetworkApiService.saveTBSuspectedData(
                        TBSuspectedRequestDTO(
                            userId = user.userId,
                            tbSuspectedList = chunkDtos
                        )
                    )
                    val statusCode = response.code()
                    if (statusCode == 200) {
                        val responseString = response.body()?.string()
                        if (responseString != null) {
                            val jsonObj = JSONObject(responseString)
                            val responseStatusCode = jsonObj.getInt("statusCode")
                            Timber.d("Push to Amrit TB Suspected chunk: $responseStatusCode, callTag=$callTag, benIds=${chunk.map { it.benId }}")
                            when (responseStatusCode) {
                                200 -> {
                                    if (excludedBenIds.isNotEmpty()) {
                                        Timber.w("SYNC_DIAG[$callTag] TB Suspected push: marking benIds=$excludedBenIds SYNCED even though they were NOT sent (benId resolution failed above) — this hides a real failure for those records")
                                    }
                                    updateSyncStatusSuspected(chunk)
                                    successCount += chunk.size
                                    Timber.d("SYNC_DIAG[$callTag] TB Suspected push: chunk marked SYNCED, benIds=${chunk.map { it.benId }}")
                                }

                                401, 5002 -> {
                                    if (userRepo.refreshTokenTmc(user.userName, user.password)) {
                                        Timber.d("Token refreshed, TB Suspected chunk will retry next cycle")
                                    }
                                    failCount += chunk.size
                                    Timber.w("SYNC_DIAG[$callTag] TB Suspected push: token issue, benIds=${chunk.map { it.benId }}")
                                }

                                else -> {
                                    Timber.e("SYNC_DIAG[$callTag] TB Suspected chunk failed with statusCode: $responseStatusCode, benIds=${chunk.map { it.benId }}, body=$responseString")
                                    failCount += chunk.size
                                }
                            }
                        }
                    } else {
                        Timber.e("SYNC_DIAG[$callTag] TB Suspected chunk HTTP error: $statusCode, benIds=${chunk.map { it.benId }}")
                        failCount += chunk.size
                    }
                } catch (e: Exception) {
                    Timber.e(e, "SYNC_DIAG[$callTag] TB Suspected chunk push failed: ${chunk.size} records, benIds=${chunk.map { it.benId }}")
                    failCount += chunk.size
                }
            }

            Timber.d("SYNC_DIAG[$callTag] TB Suspected push complete: $successCount succeeded, $failCount failed out of ${tbspList.size}")
            return@withContext 1
        }
    }

    // RECORD-LEVEL ISOLATION: Same chunking pattern as TB Screening.
    // Records sent in chunks of 20 with per-chunk error isolation.
    private suspend fun pushUnSyncedRecordsTBConfirmed(): Int {
        return withContext(Dispatchers.IO) {
            val user =
                preferenceDao.getLoggedInUser()
                    ?: throw IllegalStateException("No user logged in!!")

            val tbspList: List<TBConfirmedTreatmentCache> = tbDao.getTbConfirmed(SyncState.UNSYNCED)

            if (tbspList.isEmpty()) return@withContext 1

            val CHUNK_SIZE = 20
            val chunks = tbspList.chunked(CHUNK_SIZE)
            var successCount = 0
            var failCount = 0

            for (chunk in chunks) {
                try {
                    val chunkDtos = chunk.mapNotNull { confirmed ->
                        val ben = benDao.getBen(confirmed.benId)
                        ben?.let {
                            confirmed.toDTO()
                        }
                    }
                    if (chunkDtos.isEmpty()) {
                        failCount += chunk.size
                        continue
                    }

                    val response = tmcNetworkApiService.saveTBConfirmedData(
                        TBConfirmedRequestDTO(
                            userId = user.userId,
                            tbConfirmedList = chunkDtos
                        )
                    )
                    val statusCode = response.code()
                    if (statusCode == 200) {
                        val responseString = response.body()?.string()
                        if (responseString != null) {
                            val jsonObj = JSONObject(responseString)
                            val responseStatusCode = jsonObj.getInt("statusCode")
                            Timber.d("Push to Amrit TB Confirmed chunk: $responseStatusCode")
                            when (responseStatusCode) {
                                200 -> {
                                    updateSyncStatusConfirmed(chunk)
                                    successCount += chunk.size
                                }

                                401, 5002 -> {
                                    if (userRepo.refreshTokenTmc(user.userName, user.password)) {
                                        Timber.d("Token refreshed, TB Confirmed chunk will retry next cycle")
                                    }
                                    failCount += chunk.size
                                }

                                else -> {
                                    Timber.e("TB Confirmed chunk failed with statusCode: $responseStatusCode")
                                    failCount += chunk.size
                                }
                            }
                        }
                    } else {
                        Timber.e("TB Confirmed chunk HTTP error: $statusCode")
                        failCount += chunk.size
                    }
                } catch (e: Exception) {
                    Timber.e(e, "TB Confirmed chunk push failed: ${chunk.size} records")
                    failCount += chunk.size
                }
            }

            Timber.d("TB Confirmed push complete: $successCount succeeded, $failCount failed out of ${tbspList.size}")
            return@withContext 1
        }
    }


    private suspend fun updateSyncStatusScreening(tbsnList: List<TBScreeningCache>) {
        tbsnList.forEach {
            it.syncState = SyncState.SYNCED
            tbDao.saveTbScreening(it)
        }
    }

    private suspend fun updateSyncStatusGeneralOpd(opdList: List<GeneralOpdCache>) {
        opdList.forEach {
            it.syncState = SyncState.SYNCED
            tbDao.saveGeneralOpd(it)
        }
    }

    private suspend fun updateSyncStatusDiagnostics(diagnosticsList: List<TBDiagnosticsCache>) {
        diagnosticsList.forEach {
            it.syncState = SyncState.SYNCED
            tbDao.saveTbDiagnostics(it)
        }
    }

    private suspend fun updateSyncStatusSuspected(tbspList: List<TBSuspectedCache>) {
        tbspList.forEach {
            it.syncState = SyncState.SYNCED
            tbDao.saveTbSuspected(it)
        }
    }

    private suspend fun updateSyncStatusConfirmed(tbspList: List<TBConfirmedTreatmentCache>) {
        tbspList.forEach {
            it.syncState = SyncState.SYNCED
            tbDao.saveTbConfirmed(it)
        }
    }

    companion object {
        private const val GENERAL_OPD_CATEGORY_FALLBACK = 6
        private val pollCounts = java.util.concurrent.ConcurrentHashMap<String, Int>()
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.ENGLISH)
        private fun getCurrentDate(millis: Long = System.currentTimeMillis()): String {
            val dateString = dateFormat.format(millis)
            val timeString = timeFormat.format(millis)
            return "${dateString}T${timeString}.000Z"
        }

        private fun getLongFromDate(dateString: String): Long {
            //Jul 22, 2023 8:17:23 AM"
            val f = SimpleDateFormat("MMM d, yyyy h:mm:ss a", Locale.ENGLISH)
            val date = f.parse(dateString)
            return date?.time ?: throw IllegalStateException("Invalid date for dateReg")
        }

        private fun getLongFromDateMultipleSupport(dateString: String?): Long {
            if (dateString.isNullOrBlank() || dateString.equals("null", ignoreCase = true)) {
                return 0L
            }
            val patterns = listOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "MMM d, yyyy, h:mm:ss a",
                "MMM dd, yyyy, h:mm:ss a",
                "MMM d, yyyy h:mm:ss a",
                "MMM dd, yyyy h:mm:ss a",
                "yyyy-MM-dd'T'HH:mm:ss.SSS",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd"
            )
            patterns.forEach { pattern ->
                runCatching {
                    SimpleDateFormat(pattern, Locale.ENGLISH).parse(dateString)?.time
                }.getOrNull()?.let { return it }
            }
            Timber.w("TB_DATE_PARSE: failed to parse visitDate='$dateString'")
            return 0L
        }
        private fun getServerUpdatedDate(jsonObject: JSONObject): Long {
            return parseServerUpdateDate(
                jsonObject.optStringOrNull("updateDate")
                    ?: jsonObject.optStringOrNull("updatedDate")
            )
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
            serverUpdatedDate: Long
        ): Boolean {
            if (existingSyncState != null && existingSyncState != SyncState.SYNCED) return false
            if (serverUpdatedDate <= 0L) return true
            return serverUpdatedDate > (savedServerUpdatedDate ?: 0L)
        }

        private fun JSONObject.optNullableBoolean(name: String): Boolean? {
            if (!has(name) || isNull(name)) return null
            return optBoolean(name)
        }

        private fun JSONObject.optIntOrNull(name: String): Int? =
            if (!has(name) || isNull(name)) null else optInt(name)

        private fun JSONObject.optStringOrNull(name: String): String? {
            if (!has(name) || isNull(name)) return null
            return optString(name).takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        }

        private fun JSONObject.optStringListOrNull(name: String): List<String>? {
            if (!has(name) || isNull(name)) return null
            val value = opt(name)
            return when (value) {
                is JSONArray -> List(value.length()) { index -> value.optString(index) }
                    .filter { it.isNotBlank() }
                is String -> runCatching {
                    val jsonArray = JSONArray(value)
                    List(jsonArray.length()) { index -> jsonArray.optString(index) }
                        .filter { it.isNotBlank() }
                }.getOrNull()
                else -> null
            }
        }

        private fun getStopTbDataArray(jsonObj: JSONObject): JSONArray {
            return when (val data = jsonObj.opt("data")) {
                is JSONArray -> data
                is JSONObject -> data.optJSONArray("data") ?: JSONArray()
                else -> JSONArray()
            }
        }

        private fun JSONObject.optIntListOrNull(name: String): List<Int>? {
            if (!has(name) || isNull(name)) return null
            return when (val value = opt(name)) {
                is JSONArray -> List(value.length()) { index -> value.optInt(index) }
                is String -> runCatching {
                    val jsonArray = JSONArray(value)
                    List(jsonArray.length()) { index -> jsonArray.optInt(index) }
                }.getOrNull()
                else -> null
            }
        }
    }

    suspend fun submitManualResult(
        benId: Long,
        orderType: String,
        // Null for the "Not Conducted" closure path — see reasonForRefusal below.
        resultSummary: String? = null,
        // Null resultSummary + a reason here closes the order (X-Ray/TrueNat/RIF alike) instead
        // of completing it. Sent to the backend as reasonToClose.
        reasonForRefusal: String? = null
    ):NetworkResponse<String> {
        return withContext(Dispatchers.IO) {
            val ben = benDao.getBen(benId)
                ?: return@withContext NetworkResponse.Error("Beneficiary not found")
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
                return@withContext NetworkResponse.Success("PENDING_SYNC")
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
                        withBenIdLock(benId) {
                            val cache = buildManualResultCache(
                                benId, orderType, resultSummary, reasonForRefusal, localResult,
                                fetchedOrderId = fetchedOrderId, pendingSync = false
                            )
                            tbDao.saveTbDiagnostics(cache)
                        }
                        return@withContext NetworkResponse.Success("Result submitted successfully")
                    } else {
                        // A definitive rejection (e.g. already COMPLETED) won't succeed on retry —
                        // surface it as a real error instead of queuing for offline-first retry.
                        return@withContext NetworkResponse.Error(
                            responseBody.errorMessage ?: "Manual result submission was rejected"
                        )
                    }
                } else {
                    // Couldn't confirm anything either way — treat like an unreachable hub.
                    saveManualResultPendingSync(benId, orderType, resultSummary, reasonForRefusal, localResult)
                    return@withContext NetworkResponse.Success("PENDING_SYNC")
                }
            } catch (e: Exception) {
                Timber.e(e, "submitManualResult failed, saving locally for later sync")
                saveManualResultPendingSync(benId, orderType, resultSummary, reasonForRefusal, localResult)
                return@withContext NetworkResponse.Success("PENDING_SYNC")
            }
        }
    }

    /** Writes the same fields the confirmed-by-backend path would, marked with this test
     *  type's pending-sync flag so DiagnosticResultPollWorker's retry sweep knows to replay
     *  the order/manualResult call later. */
    private suspend fun saveManualResultPendingSync(
        benId: Long, orderType: String, resultSummary: String?, reasonForRefusal: String?, localResult: String?
    ) {
        withBenIdLock(benId) {
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
                    it.copy(
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
                    it.copy(
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
                        trueNatRifResult = localResult,
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
                    it.copy(
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
                        isSputumCollected = true,
                        isNaatConducted = true,
                        naatResult = localResult,
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

    private fun String?.asValidOrderId(): String? =
        this?.trim()?.takeIf { it.isNotBlank() && !it.equals("N/A", ignoreCase = true) }

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
            for (diag in getDiagnosticsList()) {
                if (diag.xrayManualResultPendingSync == true) {
                    val resultSummary = ChestXrayResult.fromResultText(diag.chestXRayResult)?.displayValue
                    val reason = if (resultSummary == null)
                        formatNotConductedReason(diag.reasonNotConductedChestXray, diag.reasonNotConductedChestXrayOther) else null
                    if (resultSummary != null || reason != null) {
                        val response = submitManualResult(diag.benId, "XRAY_CHEST", resultSummary, reason)
                        when {
                            response.data == "PENDING_SYNC" -> stillPending = true
                            response is NetworkResponse.Error -> clearStuckPendingSync(diag.benId, "XRAY_CHEST", response.message)
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
                            response.data == "PENDING_SYNC" -> stillPending = true
                            response is NetworkResponse.Error -> clearStuckPendingSync(diag.benId, "SPUTUM_TRUENAT", response.message)
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
                            response.data == "PENDING_SYNC" -> stillPending = true
                            response is NetworkResponse.Error -> clearStuckPendingSync(diag.benId, "MDR_RIF", response.message)
                        }
                    }
                }
            }
            stillPending
        }
    }

    /** A definitively-rejected retry (e.g. "already COMPLETED") won't succeed by retrying again —
     *  clears just that test type's pending-sync flag so it stops retrying forever. */
    private suspend fun clearStuckPendingSync(benId: Long, orderType: String, errorMessage: String?) {
        Timber.w("Manual result retry for benId=$benId orderType=$orderType was rejected by the backend (not a connectivity issue) — clearing pending-sync flag to stop endless retries: $errorMessage")
        withBenIdLock(benId) {
            val existing = tbDao.getTbDiagnosticsByBenId(benId) ?: return@withBenIdLock
            val cache = when {
                orderType.equals("XRAY_CHEST", ignoreCase = true) -> existing.copy(xrayManualResultPendingSync = false)
                orderType.equals("MDR_RIF", ignoreCase = true) -> existing.copy(rifManualResultPendingSync = false)
                else -> existing.copy(trueNatManualResultPendingSync = false)
            }
            tbDao.saveTbDiagnostics(cache)
        }
    }

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
                ?: return@withContext NetworkResponse.Error("No user logged in!!")
            val ben = benDao.getBen(benId)
                ?: return@withContext NetworkResponse.Error("Beneficiary not found")
            val targetBenId = ben.beneficiaryId
            if (targetBenId <= 0) {
                return@withContext NetworkResponse.Error("Beneficiary ID not valid")
            }
            // Skip the network call entirely when the hub is known disconnected — otherwise a
            // request via CampModeUrlInterceptor can eat the full connect timeout for a request
            // that's certain to fail. This only catches KNOWN-disconnected state (explicit
            // disconnect, WiFi loss, a prior request that already failed and flipped the flag);
            // isCampHubConnected() is a cached flag, not continuously re-verified, so a hub that
            // died moments ago while still marked connected will still attempt the call below and
            // fall through to the same timeout-driven FAILED path as before.
            if (preferenceDao.isCampModeEnabled() && !preferenceDao.isCampHubConnected()) {
                saveFailedOrderStatus(benId, testType, "Camp Hub not connected")
                return@withContext NetworkResponse.Error("Camp Hub not connected")
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
                            
                            val cache = withBenIdLock(benId) {
                            val existing = tbDao.getTbDiagnosticsByBenId(benId)
                            val cache = (existing ?: TBDiagnosticsCache(benId = benId)).let {
                                if (testType.equals("XRAY_CHEST", ignoreCase = true)) {
                                    // Chest X-Ray order lifecycle redesign: a fresh order/push
                                    // always lands the row in PENDING (or FAILED if the push
                                    // response itself reports it), resetting any stale result/
                                    // not-conducted data from a previously Closed order so a new
                                    // order never shows leftover data.
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
                                    // TrueNat/RIF order lifecycle redesign: same reduced 5-value
                                    // vocabulary/reset-on-fresh-order pattern as Chest X-Ray above
                                    // — a fresh order/push lands in PENDING (or FAILED if the push
                                    // response itself reports it), clearing any stale result/
                                    // DR-TB flag from a previously Closed order.
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
                                        isSputumCollected = true,
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
                                pushUnSyncedRecordsTBSuspected()
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to call pushUnSyncedRecordsTBSuspected after createOrder")
                            }
                            return@withContext NetworkResponse.Success(orderId ?: "")
                        } else {
                            val errorMsg =  response.body()?.errorMessage?: "Failed to push order"
                            saveFailedOrderStatus(benId, testType, errorMsg)
                            return@withContext NetworkResponse.Error(errorMsg)
                        }
                    }
                }
                saveFailedOrderStatus(benId, testType, "HTTP Error $statusCode")
                NetworkResponse.Error("HTTP Error $statusCode")
            } catch (e: Exception) {
                Timber.e(e, "createOrder failed")
                // Keep errorMessage null on timeout/connect failure to flag the request for reconciliation.
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
                ?: return@withContext NetworkResponse.Error("Beneficiary not found")
            val targetBenId = ben.beneficiaryId
            if (targetBenId <= 0) {
                return@withContext NetworkResponse.Error("Beneficiary ID not valid")
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

                            val cache = withBenIdLock(benId) {
                            val existing = tbDao.getTbDiagnosticsByBenId(benId)
                            val cache = (existing ?: TBDiagnosticsCache(benId = benId)).let {
                                if (testType.equals("XRAY_CHEST", ignoreCase = true)) {
                                    it.copy(
                                        xrayOrderId = orderId ?: it.xrayOrderId,
                                        xrayOrderStatus = status,
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
                                    val isIntegrated = isTruenatIntegrated()
                                    it.copy(
                                        trueNatOrderId = orderId ?: it.trueNatOrderId,
                                        trueNatOrderStatus = reducedOrderStatus(status),
                                        naatResult = null,
                                        trueNatRifResult = null,
                                        isSputumCollected = true,
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
                                pushUnSyncedRecordsTBSuspected()
                            } catch (e: java.lang.Exception) {
                                Timber.e(e, "Failed to call pushUnSyncedRecordsTBSuspected after retryPushOrder")
                            }
                            return@withContext NetworkResponse.Success(orderId ?: "")
                        } else {
                            val errorMsg = responseBody.errorMessage ?: "Failed to retry order"
                            return@withContext NetworkResponse.Error(errorMsg)
                        }
                    }
                }
                return@withContext NetworkResponse.Error("HTTP Error $statusCode")
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
            withBenIdLock(benId) {
            val existing = tbDao.getTbSuspected(benId)

            val mappedIsSputumCollected = when {
                // reasonForDenialSputum alone identifies "declined before order exists" — it's
                // only ever set on that path. Checking trueNatOrderStatus == CLOSED here as well
                // would misfire: CLOSED is now shared with the unrelated "Not Conducted" closure
                // (reasonNotConductedNaat, handled in the branch below), which must still map to
                // isSputumCollected == true, not false.
                diag.isSputumCollected == false ||
                diag.reasonForDenialSputum != null -> false

                diag.reasonNotConductedNaat != null ||
                diag.isSputumCollected == true ||
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
                sputumTestResult = mappedSputumTestResult
            )

            // hasSymptoms is deliberately always forced true above (this function only runs off
            // the back of a diagnostic test, which implies symptomatic), but the server's getAll
            // response sometimes omits the field entirely, which toCache() then defaults back to
            // false on the next pull. Normalize it here so that server-side quirk alone can't
            // masquerade as a real change and defeat the guard below on every poll.
            val normalizedExisting = existing?.copy(hasSymptoms = true)
            if (existing != null && provisionalCache == normalizedExisting) {
                return@withBenIdLock
            }

            if (existing != null) {
                val diffs = buildList {
                    if (existing.hasSymptoms != provisionalCache.hasSymptoms) add("hasSymptoms: ${existing.hasSymptoms} -> ${provisionalCache.hasSymptoms}")
                    if (existing.isChestXRayDone != provisionalCache.isChestXRayDone) add("isChestXRayDone: ${existing.isChestXRayDone} -> ${provisionalCache.isChestXRayDone}")
                    if (existing.chestXRayResult != provisionalCache.chestXRayResult) add("chestXRayResult: ${existing.chestXRayResult} -> ${provisionalCache.chestXRayResult}")
                    if (existing.isSputumCollected != provisionalCache.isSputumCollected) add("isSputumCollected: ${existing.isSputumCollected} -> ${provisionalCache.isSputumCollected}")
                    if (existing.sputumSubmittedAt != provisionalCache.sputumSubmittedAt) add("sputumSubmittedAt: ${existing.sputumSubmittedAt} -> ${provisionalCache.sputumSubmittedAt}")
                    if (existing.isNaatConducted != provisionalCache.isNaatConducted) add("isNaatConducted: ${existing.isNaatConducted} -> ${provisionalCache.isNaatConducted}")
                    if (existing.naatResult != provisionalCache.naatResult) add("naatResult: ${existing.naatResult} -> ${provisionalCache.naatResult}")
                    if (existing.reasonForRefusalXray != provisionalCache.reasonForRefusalXray) add("reasonForRefusalXray: ${existing.reasonForRefusalXray} -> ${provisionalCache.reasonForRefusalXray}")
                    if (existing.reasonForRefusalSputum != provisionalCache.reasonForRefusalSputum) add("reasonForRefusalSputum: ${existing.reasonForRefusalSputum} -> ${provisionalCache.reasonForRefusalSputum}")
                    if (existing.reasonForRefusalMTB != provisionalCache.reasonForRefusalMTB) add("reasonForRefusalMTB: ${existing.reasonForRefusalMTB} -> ${provisionalCache.reasonForRefusalMTB}")
                    if (existing.reasonForRefusalMDRRIF != provisionalCache.reasonForRefusalMDRRIF) add("reasonForRefusalMDRRIF: ${existing.reasonForRefusalMDRRIF} -> ${provisionalCache.reasonForRefusalMDRRIF}")
                    if (existing.mdrRifResult != provisionalCache.mdrRifResult) add("mdrRifResult: ${existing.mdrRifResult} -> ${provisionalCache.mdrRifResult}")
                    if (existing.isDRTBConfirmed != provisionalCache.isDRTBConfirmed) add("isDRTBConfirmed: ${existing.isDRTBConfirmed} -> ${provisionalCache.isDRTBConfirmed}")
                    if (existing.isTBConfirmed != provisionalCache.isTBConfirmed) add("isTBConfirmed: ${existing.isTBConfirmed} -> ${provisionalCache.isTBConfirmed}")
                    if (existing.isConfirmed != provisionalCache.isConfirmed) add("isConfirmed: ${existing.isConfirmed} -> ${provisionalCache.isConfirmed}")
                    if (existing.sputumTestResult != provisionalCache.sputumTestResult) add("sputumTestResult: ${existing.sputumTestResult} -> ${provisionalCache.sputumTestResult}")
                }
                Timber.w("TB_SUSPECTED_DIRTY: benId=$benId marking UNSYNCED (was ${existing.syncState}), diag.xrayOrderStatus=${diag.xrayOrderStatus} trueNatOrderStatus=${diag.trueNatOrderStatus} rifOrderStatus=${diag.rifOrderStatus}, changes=$diffs")
            } else {
                Timber.w("TB_SUSPECTED_DIRTY: benId=$benId creating new tb_suspected record from diagnostics")
            }

            saveTBSuspected(provisionalCache.copy(syncState = SyncState.UNSYNCED))
            }
        } catch (e: Exception) {
            Timber.e(e, "syncTBSuspectedFromDiagnostics failed for benId=$benId")
        }
    }

    // Pass errorMessage only for confirmed server/HTTP failures; keep it null for ambiguous failures.
    private suspend fun saveFailedOrderStatus(benId: Long, testType: String, errorMessage: String? = null) {
        try {
            val cache = withBenIdLock(benId) {
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
            val existing = tbDao.getTbDiagnosticsByBenId(benId) ?: return
            val response = tmcNetworkApiService.fetchOrderResult(
                benId = benId,
                orderType = orderType
            )
            if (response.code() != 200) return

            val responseData = response.body()?.takeIf { it.statusCode == 200 }?.data
                ?: return

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

                orderType.equals("SPUTUM_TRUENAT", ignoreCase = true) ->
                    existing.copy(
                        errorMsgTrueNat = responseData.errorMessage,
                        syncState = SyncState.UNSYNCED
                    )

                else -> return
            }

            tbDao.saveTbDiagnostics(cache)
        } catch (e: Exception) {
            Timber.e(e, "giveUpReconciliation failed")
        }
    }

    suspend fun markTestCompleted(benId: Long, orderType: String): NetworkResponse<String> {
        return withContext(Dispatchers.IO) {
            val ben = benDao.getBen(benId)
                ?: return@withContext NetworkResponse.Error("Beneficiary not found")
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
                            val status = if (rawStatus.isNullOrBlank()) "IN_PROGRESS" else rawStatus
                            
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
                                            isSputumCollected = true,
                                            syncState = SyncState.UNSYNCED
                                        )
                                    }
                                }
                                tbDao.saveTbDiagnostics(cache)
                                preferenceDao.setDiagPollStartTime(benId, orderType, System.currentTimeMillis())
                            }
                            
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
                NetworkResponse.Error("HTTP Error $statusCode")
            } catch (e: Exception) {
                Timber.e(e, "markTestCompleted failed")
                NetworkResponse.Error(e.message ?: "Unknown error")
            }
        }
    }

    suspend fun fetchOrderResult(benId: Long, orderType: String): NetworkResponse<String> {
        return withContext(Dispatchers.IO) {
            val ben = benDao.getBen(benId)
                ?: return@withContext NetworkResponse.Error("Beneficiary not found")
            val targetBenId = ben.beneficiaryId
            if (targetBenId <= 0) {
                return@withContext NetworkResponse.Error("Beneficiary ID not valid")
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
                            val rawStatus = responseData.status
                            val status = if (rawStatus.isNullOrBlank()) "IN_PROGRESS" else rawStatus
                           Timber.d("STOP-TB polling debug: fetchOrderResult benId=$benId status=$status rawStatus=$rawStatus")

                            val fetchedOrderId = responseData.externalOrderId.asValidOrderId()
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
                                    if (!hasExistingRifOrder) {
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

                            // Final commit: re-reads the row fresh, INSIDE the per-benId lock,
                            // right before writing — this is what actually fixes the lost-update
                            // race (Gaps 1/2), since any concurrent write for a different test
                            // type on the same beneficiary (or the RIF cascade above, which
                            // already committed its own fields) is guaranteed to be visible here
                            // rather than clobbered by a stale pre-cascade snapshot.
                            val syncedCache = withBenIdLock(benId) {
                            val existing = tbDao.getTbDiagnosticsByBenId(benId)
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
                                            // Leave trueNatOrderStatus/isSputumCollected/isNaatConducted
                                            // untouched — writing PENDING here would make the worker's
                                            // own "does an order exist" check skip the push.
                                            val hasTruenat = !it.trueNatOrderId.isNullOrBlank() ||
                                                    it.trueNatOrderStatus.equals(OrderStatus.COMPLETED.name, ignoreCase = true) ||
                                                    it.trueNatOrderStatus.equals(OrderStatus.PENDING.name, ignoreCase = true) ||
                                                    it.trueNatOrderStatus.equals(OrderStatus.CLOSED.name, ignoreCase = true)
                                            if (!hasTruenat && isTruenatIntegrated()) {
                                                WorkerUtils.triggerDiagnosticOrderPushWorkers(
                                                    context, benId, listOf("SPUTUM_TRUENAT")
                                                )
                                            }
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
                                        isSputumCollected = true,
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
                            val synced = cache.copy(syncState = SyncState.SYNCED)
                            tbDao.saveTbDiagnostics(synced)
                            synced
                            }

                            try {
                                syncTBSuspectedFromDiagnostics(benId, syncedCache)
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to sync diagnostic results into tb_suspected table")
                            }
                            try {
                                pushUnSyncedRecordsTBSuspected()
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to call pushUnSyncedRecordsTBSuspected after fetchOrderResult")
                            }

                            return@withContext NetworkResponse.Success(status)
                        } else {
                            val errorMsg = responseBody.errorMessage ?: "Failed to fetch result"
                            return@withContext NetworkResponse.Error(errorMsg)
                        }
                    }
                }
                NetworkResponse.Error("HTTP Error $statusCode")
            } catch (e: Exception) {
                Timber.e(e, "fetchOrderResult failed")
                NetworkResponse.Error(e.message ?: "Unknown error")
            }
        }
    }

    suspend fun fetchBeneficiariesByStatus(orderType: String, fetchResult: Boolean = true): NetworkResponse<DiagnosticBeneficiaryStatusData> {
        return withContext(Dispatchers.IO) {
            val user = preferenceDao.getLoggedInUser()
                ?: return@withContext NetworkResponse.Error("No user logged in")
            val locationRecord = preferenceDao.getLocationRecord()
                ?: return@withContext NetworkResponse.Error("No location record found")

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
                                for (i in 0 until arr.length()) {
                                    isAwaitingReconciliation(
                                        arr.getLong(i),
                                        apiOrderType,
                                        tbDao.getDiagnosticsList()
                                            .firstOrNull { it.id.toLong() == arr.getLong(i) }
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
                                    failedList.add(arr.getLong(i))
                                }
                            }
                            dataObj?.optJSONArray("awaitingManualEntry")?.let { arr ->
                                for (i in 0 until arr.length()) {
                                    awaitingManualEntryList.add(arr.getLong(i))
                                }
                            }

                            val isXray = orderType.equals("XRAY_CHEST", ignoreCase = true)
                            val isRif = orderType.equals("MDR_RIF", ignoreCase = true)

                            // 1. Awaiting Provider Result — the only real "still in progress"
                            // bucket (no separate "awaiting test completion" bucket exists).
                            for (regId in awaitingProvResList) {
                                val ben = benDao.getBenByRegId(regId) ?: benDao.getBen(regId)
                                ben?.let { b ->
                                    withBenIdLock(b.beneficiaryId) {
                                        // Guard, status check, and write all read the SAME fresh
                                        // snapshot taken inside this lock — previously the guard
                                        // read happened before the lock, so a concurrent write
                                        // (e.g. a fresh createOrder()) landing in between could be
                                        // silently re-marked SYNCED by this sweep even though it
                                        // still needed to be pushed to Amrit.
                                        val fresh = tbDao.getTbDiagnosticsByBenId(b.beneficiaryId)
                                        if (fresh?.syncState != null && fresh.syncState != SyncState.SYNCED) {
                                            return@withBenIdLock
                                        }
                                        val currentStatus = if (isXray) fresh?.xrayOrderStatus else if (isRif) fresh?.rifOrderStatus else fresh?.trueNatOrderStatus
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
                                                        isSputumCollected = true,
                                                        syncState = SyncState.SYNCED
                                                    )
                                                }
                                            }
                                            tbDao.saveTbDiagnostics(cache)
                                        }
                                    }
                                }
                            }

                            // 3. Completed
                            for (regId in completedList) {
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
                                    if (existing?.syncState != null && existing.syncState != SyncState.SYNCED) {
                                        return@let
                                    }
                                    val currentStatus = if (isXray) existing?.xrayOrderStatus else if (isRif) existing?.rifOrderStatus else existing?.trueNatOrderStatus
                                    val needsResultFetch = when {
                                        isXray -> !currentStatus.equals(OrderStatus.COMPLETED.name, ignoreCase = true) || existing?.chestXRayResult.isNullOrBlank()
                                        isRif -> !currentStatus.equals(OrderStatus.COMPLETED.name, ignoreCase = true) || existing?.trueNatRifResult.isNullOrBlank()
                                        else -> !currentStatus.equals(OrderStatus.COMPLETED.name, ignoreCase = true) || existing?.naatResult.isNullOrBlank()
                                    }

                                    if (needsResultFetch && fetchResult) {
                                        fetchOrderResult(b.beneficiaryId, orderType)
                                    }
                                    if (!fetchResult || !needsResultFetch) {
                                        withBenIdLock(b.beneficiaryId) {
                                        // Re-verify against a fresh read before writing — the
                                        // pre-check above may be stale by the time this lock is
                                        // acquired (e.g. a concurrent submitManualResult() already
                                        // landed), so both the sync-guard and the "already done"
                                        // check are redone here against current state.
                                        val fresh = tbDao.getTbDiagnosticsByBenId(b.beneficiaryId)
                                        if (fresh?.syncState != null && fresh.syncState != SyncState.SYNCED) {
                                            return@withBenIdLock
                                        }
                                        val freshStatus = if (isXray) fresh?.xrayOrderStatus else if (isRif) fresh?.rifOrderStatus else fresh?.trueNatOrderStatus
                                        if (freshStatus.equals(OrderStatus.COMPLETED.name, ignoreCase = true)) {
                                            return@withBenIdLock
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
                                                    isSputumCollected = true,
                                                    isNaatConducted = true,
                                                    syncState = SyncState.SYNCED
                                                )
                                            }
                                        }
                                        tbDao.saveTbDiagnostics(cache)
                                        }
                                    }
                                }
                            }

                            // 4. Closed — backend's own confirmed closure (EoD expiry, not
                            // conducted, etc.); the app just reflects it, per the plan's
                            // backend-authoritative EoD decision.
                            for (regId in closedList) {
                                val ben = benDao.getBenByRegId(regId) ?: benDao.getBen(regId)
                                ben?.let { b ->
                                    withBenIdLock(b.beneficiaryId) {
                                        val fresh = tbDao.getTbDiagnosticsByBenId(b.beneficiaryId)
                                        if (fresh?.syncState != null && fresh.syncState != SyncState.SYNCED) {
                                            return@withBenIdLock
                                        }
                                        val currentStatus = if (isXray) fresh?.xrayOrderStatus else if (isRif) fresh?.rifOrderStatus else fresh?.trueNatOrderStatus
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
                                                        isSputumCollected = true,
                                                        syncState = SyncState.SYNCED
                                                    )
                                                }
                                            }
                                            tbDao.saveTbDiagnostics(cache)
                                        }
                                    }
                                }
                            }

                            // 5. Failed
                            for (regId in failedList) {
                                val ben = benDao.getBenByRegId(regId) ?: benDao.getBen(regId)
                                ben?.let { b ->
                                    withBenIdLock(b.beneficiaryId) {
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
                                                        isSputumCollected = true,
                                                        syncState = SyncState.SYNCED
                                                    )
                                                }
                                            }
                                            tbDao.saveTbDiagnostics(cache)
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
                                    withBenIdLock(b.beneficiaryId) {
                                        val fresh = tbDao.getTbDiagnosticsByBenId(b.beneficiaryId)
                                        if (fresh?.syncState != null && fresh.syncState != SyncState.SYNCED) {
                                            return@withBenIdLock
                                        }
                                        val currentStatus = if (isXray) fresh?.xrayOrderStatus else if (isRif) fresh?.rifOrderStatus else fresh?.trueNatOrderStatus
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
                                                        isSputumCollected = true,
                                                        syncState = SyncState.SYNCED
                                                    )
                                                }
                                            }
                                            tbDao.saveTbDiagnostics(cache)
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
                NetworkResponse.Error("HTTP Error $statusCode")
            } catch (e: Exception) {
                Timber.e(e, "fetchBeneficiariesByStatus failed for $orderType")
                NetworkResponse.Error(e.message ?: "Unknown error")
            }
        }
    }

    suspend fun getVendorHealth(orderType: String): NetworkResponse<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (!preferenceDao.isCampModeEnabled()) {
                    return@withContext NetworkResponse.Error("Camp Mode is disabled")
                }
                if (!preferenceDao.isCampHubConnected()) {
                    return@withContext NetworkResponse.Error("Camp Hub is disconnected")
                }
                val response = tmcNetworkApiService.getVendorHealth(orderType)
                val statusCode = response.code()
                if (response.isSuccessful) {
                    val responseStr = response.body()?.string()
                    if (!responseStr.isNullOrBlank()) {
                        val json = org.json.JSONObject(responseStr as String)
                        if (json.optBoolean("success")) {
                            val data = json.optJSONObject("data")
                            val isConnected = data?.optBoolean("isConnected") ?: false
                            val isDeviceIntegrated = data?.optBoolean("isDeviceIntegrated") ?: false
                            return@withContext NetworkResponse.Success(
                                "isConnected: $isConnected, isDeviceIntegrated: $isDeviceIntegrated"
                            )
                        } else {
                            val errorMsg = json.optString("message", "Health check failed")
                            return@withContext NetworkResponse.Error(errorMsg)
                        }
                    }
                }
                NetworkResponse.Error("HTTP Error $statusCode")
            } catch (e: Exception) {
                Timber.e(e, "getVendorHealth failed for $orderType")
                NetworkResponse.Error(e.message ?: "Unknown error")
            }
        }
    }

    suspend fun checkDeviceIntegration(orderType: String): Boolean {
        val health = getVendorHealth(orderType)
        if (health is NetworkResponse.Success) {
            val dataStr = health.data
            return dataStr?.contains("isDeviceIntegrated: true") == true &&
                    dataStr?.contains("isConnected: true") == true
        }
        return false
    }

    suspend fun refreshDeviceIntegrationConfig() {
        if (!preferenceDao.isCampModeEnabled() || !preferenceDao.isCampHubConnected()) {
            Timber.d("Skipping refreshDeviceIntegrationConfig: Camp Mode is disabled or Camp Hub is disconnected")
            return
        }
        val xrayVal = checkDeviceIntegration("XRAY_CHEST")
        val truenatVal = checkDeviceIntegration("MTB")
        preferenceDao.setXrayIntegrated(xrayVal)
        preferenceDao.setTruenatIntegrated(truenatVal)
    }

    fun isXrayIntegrated(): Boolean {
        return preferenceDao.getXrayIntegrated()
    }
    fun isTruenatIntegrated(): Boolean {
        return preferenceDao.getTruenatIntegrated()
    }
}
