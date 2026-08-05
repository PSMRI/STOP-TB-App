package org.piramalswasthya.stoptb.repositories

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.database.room.dao.BenDao
import org.piramalswasthya.stoptb.database.room.dao.TBDao
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.helpers.Konstants
import org.piramalswasthya.stoptb.model.GeneralOpdCache
import org.piramalswasthya.stoptb.model.TBConfirmedTreatmentCache
import org.piramalswasthya.stoptb.model.TBDiagnosticsCache
import org.piramalswasthya.stoptb.model.TBScreeningCache
import org.piramalswasthya.stoptb.model.TBSuspectedCache
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
import okhttp3.ResponseBody
import timber.log.Timber
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

class TBRepo @Inject constructor(
    private val tbDao: TBDao,
    private val benDao: BenDao,
    val preferenceDao: PreferenceDao,
    private val userRepo: UserRepo,
    private val tmcNetworkApiService: AmritApiService,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) {
    enum class MockMtbScenario { DETECTED, NOT_DETECTED, INVALID }
    enum class MockRifScenario { DETECTED, NOT_DETECTED, INDETERMINATE }

    var useMockApi: Boolean = false
    var mockMtbScenario: MockMtbScenario = MockMtbScenario.DETECTED
    var mockRifScenario: MockRifScenario = MockRifScenario.DETECTED

    private val orderCreatedTimestamps = java.util.concurrent.ConcurrentHashMap<String, Long>()

    val allTbDiagnostics: Flow<List<TBDiagnosticsCache>> = tbDao.getAllTbDiagnostics()

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
        }
    }

    suspend fun getGeneralOpd(benId: Long): GeneralOpdCache? {
        return withContext(Dispatchers.IO) {
            tbDao.getGeneralOpd(benId)
        }
    }

    val tbScreeningBenIds: Flow<List<Long>> = tbDao.getAllTbScreeningBenIds()

    val generalOpdBenIds: Flow<List<Long>> = tbDao.getAllGeneralOpdBenIds()

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
                medications = item.optStringOrNull("medication")?.let { listOf(it) },
                dosage = item.optStringOrNull("dosage"),
                frequency = item.optStringOrNull("frequency"),
                duration = item.optStringOrNull("duration"),
                notes = item.optStringOrNull("notes"),
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
                if (shouldApplyServerRecord(
                        tbSuspectedCache?.syncState,
                        tbSuspectedCache?.serverUpdatedDate,
                        cache.serverUpdatedDate ?: 0L
                    )
                ) {
                    benDao.getBen(tbSuspectedDTO.benId)?.let {
                        tbDao.saveTbSuspected(cache)
                        tbSuspectedList.add(cache)
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
    private suspend fun pushUnSyncedRecordsTBScreening(): Int {

        return withContext(Dispatchers.IO) {
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
    private suspend fun pushUnSyncedRecordsTBSuspected(): Int {
        return withContext(Dispatchers.IO) {
            val user =
                preferenceDao.getLoggedInUser()
                    ?: throw IllegalStateException("No user logged in!!")

            val tbspList: List<TBSuspectedCache> = tbDao.getTbSuspected(SyncState.UNSYNCED)

            if (tbspList.isEmpty()) return@withContext 1

            val CHUNK_SIZE = 20
            val chunks = tbspList.chunked(CHUNK_SIZE)
            var successCount = 0
            var failCount = 0

            for (chunk in chunks) {
                try {
                    val chunkDtos = chunk.map { it.toDTO() }

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
                            Timber.d("Push to Amrit TB Suspected chunk: $responseStatusCode")
                            when (responseStatusCode) {
                                200 -> {
                                    updateSyncStatusSuspected(chunk)
                                    successCount += chunk.size
                                }

                                401, 5002 -> {
                                    if (userRepo.refreshTokenTmc(user.userName, user.password)) {
                                        Timber.d("Token refreshed, TB Suspected chunk will retry next cycle")
                                    }
                                    failCount += chunk.size
                                }

                                else -> {
                                    Timber.e("TB Suspected chunk failed with statusCode: $responseStatusCode")
                                    failCount += chunk.size
                                }
                            }
                        }
                    } else {
                        Timber.e("TB Suspected chunk HTTP error: $statusCode")
                        failCount += chunk.size
                    }
                } catch (e: Exception) {
                    Timber.e(e, "TB Suspected chunk push failed: ${chunk.size} records")
                    failCount += chunk.size
                }
            }

            Timber.d("TB Suspected push complete: $successCount succeeded, $failCount failed out of ${tbspList.size}")
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
                    val chunkDtos = chunk.map { it.toDTO() }

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
        const val useMockApi = false
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
        resultSummary: String
    ): org.piramalswasthya.stoptb.helpers.NetworkResponse<String> {
        return withContext(Dispatchers.IO) {
            val user = preferenceDao.getLoggedInUser()
                ?: return@withContext org.piramalswasthya.stoptb.helpers.NetworkResponse.Error("No user logged in!!")
            val ben = benDao.getBen(benId)
                ?: return@withContext org.piramalswasthya.stoptb.helpers.NetworkResponse.Error("Beneficiary not found")
            val targetBenId = ben.beneficiaryId

            val apiOrderType = if (orderType.equals("SPUTUM_TRUENAT", ignoreCase = true)) "MTB" else orderType

            val localResult = when (resultSummary) {
                "TB Positive" -> "MTB detected"
                "TB Negative" -> "MTB not detected"
                "DR TB" -> "Rif Resistance Detected"
                "Non DR TB" -> "Rif Resistance Not Detected"
                else -> resultSummary
            }

            if (useMockApi) {
                val existing = tbDao.getTbDiagnosticsByBenId(benId)
                val cache = (existing ?: TBDiagnosticsCache(benId = benId)).let {
                    if (orderType.equals("XRAY_CHEST", ignoreCase = true)) {
                        it.copy(
                            xrayOrderStatus = "COMPLETED",
                            isChestXRayDone = true,
                            chestXRayResult = localResult,
                            syncState = SyncState.SYNCED
                        )
                    } else if (orderType.equals("MDR_RIF", ignoreCase = true)) {
                        it.copy(
                            rifOrderStatus = "COMPLETED",
                            trueNatRifResult = localResult,
                            syncState = SyncState.SYNCED
                        )
                    } else {
                        it.copy(
                            trueNatOrderStatus = "COMPLETED",
                            isSputumCollected = true,
                            isNaatConducted = true,
                            naatResult = localResult,
                            syncState = SyncState.SYNCED
                        )
                    }
                }
                tbDao.saveTbDiagnostics(cache)
                return@withContext org.piramalswasthya.stoptb.helpers.NetworkResponse.Success("Mock success")
            }

            try {
                val request = org.piramalswasthya.stoptb.network.DiagnosticManualResultRequest(
                    beneficiaryId = targetBenId,
                    orderType = apiOrderType,
                    resultSummary = resultSummary
                )
                val response = tmcNetworkApiService.submitManualResult(request)
                val statusCode = response.code()
                if (statusCode == 200) {
                    val existing = tbDao.getTbDiagnosticsByBenId(benId)
                    val cache = (existing ?: TBDiagnosticsCache(benId = benId)).let {
                        if (orderType.equals("XRAY_CHEST", ignoreCase = true)) {
                            it.copy(
                                xrayOrderStatus = "COMPLETED",
                                isChestXRayDone = true,
                                chestXRayResult = localResult,
                                syncState = SyncState.SYNCED
                            )
                        } else if (orderType.equals("MDR_RIF", ignoreCase = true)) {
                            it.copy(
                                rifOrderStatus = "COMPLETED",
                                trueNatRifResult = localResult,
                                syncState = SyncState.SYNCED
                            )
                        } else {
                            it.copy(
                                trueNatOrderStatus = "COMPLETED",
                                isSputumCollected = true,
                                isNaatConducted = true,
                                naatResult = localResult,
                                syncState = SyncState.SYNCED
                            )
                        }
                    }
                    tbDao.saveTbDiagnostics(cache)
                    return@withContext org.piramalswasthya.stoptb.helpers.NetworkResponse.Success("Result submitted successfully")
                } else {
                    return@withContext org.piramalswasthya.stoptb.helpers.NetworkResponse.Error("HTTP Error $statusCode")
                }
            } catch (e: Exception) {
                Timber.e(e, "submitManualResult failed")
                return@withContext org.piramalswasthya.stoptb.helpers.NetworkResponse.Error(e.message ?: "Unknown error")
            }
        }
    }

    suspend fun createProdigiOrder(
        benId: Long, 
        testType: String, 
        customVisitCode: Int? = null,
        reasonForRefusal: String? = null
    ): org.piramalswasthya.stoptb.helpers.NetworkResponse<String> {
        return withContext(Dispatchers.IO) {
            val user = preferenceDao.getLoggedInUser()
                ?: return@withContext org.piramalswasthya.stoptb.helpers.NetworkResponse.Error("No user logged in!!")
            val ben = benDao.getBen(benId)
                ?: return@withContext org.piramalswasthya.stoptb.helpers.NetworkResponse.Error("Beneficiary not found")
            val targetBenId = ben.beneficiaryId
            if (targetBenId <= 0) {
                return@withContext org.piramalswasthya.stoptb.helpers.NetworkResponse.Error("Beneficiary ID not valid")
            }
            val computedVisitCode = customVisitCode ?: (kotlin.math.abs(java.util.UUID.randomUUID().mostSignificantBits) % 900000 + 100000).toInt()
            if (useMockApi) {
                try {
                    val status = if (reasonForRefusal != null) "REFUSED" else "PENDING"
                    val mockOrderId = if (testType.equals("MDR_RIF", ignoreCase = true)) "MOCK-RIF-001" else "MOCK-MTB-001"
                    orderCreatedTimestamps["${benId}_${testType}"] = System.currentTimeMillis()
                    val existing = tbDao.getTbDiagnosticsByBenId(benId)
                    val cache = (existing ?: TBDiagnosticsCache(benId = benId)).let {
                        if (testType.equals("XRAY_CHEST", ignoreCase = true)) {
                            it.copy(
                                xrayOrderId = mockOrderId,
                                xrayOrderStatus = status,
                                syncState = SyncState.SYNCED
                            )
                        } else if (testType.equals("MDR_RIF", ignoreCase = true)) {
                            it.copy(
                                rifOrderId = mockOrderId,
                                rifOrderStatus = status,
                                trueNatRifResult = null,
                                syncState = SyncState.SYNCED
                            )
                        } else {
                            it.copy(
                                trueNatOrderId = mockOrderId,
                                trueNatOrderStatus = status,
                                naatResult = null,
                                trueNatRifResult = null,
                                syncState = SyncState.SYNCED
                            )
                        }
                    }
                    tbDao.saveTbDiagnostics(cache)
                    return@withContext org.piramalswasthya.stoptb.helpers.NetworkResponse.Success(mockOrderId)
                } catch (e: Exception) {
                    return@withContext org.piramalswasthya.stoptb.helpers.NetworkResponse.Error(e.message ?: "Mock push failed")
                }
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
                    visitCode = computedVisitCode,
                    providerServiceMapID = user.serviceMapId,
                    orderType = apiOrderType,
                    orderEvent = "STOP_TB_REFERRAL",
                    reasonForRefusal = reasonForRefusal,
                    patient = patientReq
                )
                val response = tmcNetworkApiService.pushDiagnosticOrder(request)
                val statusCode = response.code()
                if (statusCode == 200) {
                    val responseString = response.body()?.string()
                    if (responseString != null) {
                        val jsonObj = JSONObject(responseString)
                        val resStatusCode = jsonObj.optInt("statusCode")
                        if (resStatusCode == 200) {
                            val dataObj = jsonObj.optJSONObject("data")
                            val orderId = dataObj?.optString("providerOrderId") ?: dataObj?.optString("externalOrderId")
                            val status = dataObj?.optString("status") ?: "PENDING"
                            
                            val existing = tbDao.getTbDiagnosticsByBenId(benId)
                            val cache = (existing ?: TBDiagnosticsCache(benId = benId)).let {
                                if (testType.equals("XRAY_CHEST", ignoreCase = true)) {
                                    it.copy(
                                        xrayOrderId = orderId,
                                        xrayOrderStatus = if (status.equals("COMPLETED", ignoreCase = true)) "COMPLETED" else if (status.equals("FAILED", ignoreCase = true)) "FAILED" else if (it.xrayOrderStatus == "AWAITING_PROVIDER_RESULT") "AWAITING_PROVIDER_RESULT" else status,
                                        syncState = SyncState.SYNCED
                                    )
                                } else if (testType.equals("MDR_RIF", ignoreCase = true)) {
                                    it.copy(
                                        rifOrderId = orderId,
                                        rifOrderStatus = if (status.equals("COMPLETED", ignoreCase = true)) "COMPLETED" else if (status.equals("FAILED", ignoreCase = true)) "FAILED" else if (it.rifOrderStatus == "AWAITING_PROVIDER_RESULT") "AWAITING_PROVIDER_RESULT" else status,
                                        trueNatRifResult = null,
                                        syncState = SyncState.SYNCED
                                    )
                                } else {
                                    it.copy(
                                        trueNatOrderId = orderId,
                                        trueNatOrderStatus = if (status.equals("COMPLETED", ignoreCase = true)) "COMPLETED" else if (status.equals("FAILED", ignoreCase = true)) "FAILED" else if (it.trueNatOrderStatus == "AWAITING_PROVIDER_RESULT") "AWAITING_PROVIDER_RESULT" else status,
                                        naatResult = null,
                                        trueNatRifResult = null,
                                        syncState = SyncState.SYNCED
                                    )
                                }
                            }
                            tbDao.saveTbDiagnostics(cache)
                            return@withContext org.piramalswasthya.stoptb.helpers.NetworkResponse.Success(orderId ?: "")
                        } else {
                            val errorMsg = jsonObj.optString("errorMessage") ?: "Failed to push order"
                            saveFailedOrderStatus(benId, testType)
                            return@withContext org.piramalswasthya.stoptb.helpers.NetworkResponse.Error(errorMsg)
                        }
                    }
                }
                saveFailedOrderStatus(benId, testType)
                org.piramalswasthya.stoptb.helpers.NetworkResponse.Error("HTTP Error $statusCode")
            } catch (e: Exception) {
                Timber.e(e, "createProdigiOrder failed")
                saveFailedOrderStatus(benId, testType)
                org.piramalswasthya.stoptb.helpers.NetworkResponse.Error(e.message ?: "Unknown error")
            }
        }
    }

    suspend fun retryProdigiOrder(
        benId: Long,
        testType: String
    ): org.piramalswasthya.stoptb.helpers.NetworkResponse<String> {
        return withContext(Dispatchers.IO) {
            val ben = benDao.getBen(benId)
                ?: return@withContext org.piramalswasthya.stoptb.helpers.NetworkResponse.Error("Beneficiary not found")
            val targetBenId = ben.beneficiaryId
            if (targetBenId <= 0) {
                return@withContext org.piramalswasthya.stoptb.helpers.NetworkResponse.Error("Beneficiary ID not valid")
            }
            if (useMockApi) {
                try {
                    val orderId = "MOCK-RETRY-" + (kotlin.math.abs(java.util.UUID.randomUUID().mostSignificantBits) % 900000 + 100000)
                    val existing = tbDao.getTbDiagnosticsByBenId(benId)
                    val cache = (existing ?: TBDiagnosticsCache(benId = benId)).let {
                        if (testType.equals("XRAY_CHEST", ignoreCase = true)) {
                            it.copy(
                                xrayOrderId = orderId,
                                xrayOrderStatus = "PENDING",
                                syncState = SyncState.SYNCED
                            )
                        } else if (testType.equals("MDR_RIF", ignoreCase = true)) {
                            it.copy(
                                rifOrderId = orderId,
                                rifOrderStatus = "PENDING",
                                trueNatRifResult = null,
                                syncState = SyncState.SYNCED
                            )
                        } else {
                            it.copy(
                                trueNatOrderId = orderId,
                                trueNatOrderStatus = "PENDING",
                                naatResult = null,
                                trueNatRifResult = null,
                                syncState = SyncState.SYNCED
                            )
                        }
                    }
                    tbDao.saveTbDiagnostics(cache)
                    return@withContext org.piramalswasthya.stoptb.helpers.NetworkResponse.Success(orderId)
                } catch (e: Exception) {
                    return@withContext org.piramalswasthya.stoptb.helpers.NetworkResponse.Error(e.message ?: "Mock retry failed")
                }
            }
            try {
                val apiOrderType = if (testType.equals("SPUTUM_TRUENAT", ignoreCase = true)) "MTB" else testType
                val response = tmcNetworkApiService.retryOrder(benId = targetBenId, orderType = apiOrderType)
                val statusCode = response.code()
                if (statusCode == 200) {
                    val responseString = response.body()?.string()
                    if (responseString != null) {
                        val jsonObj = JSONObject(responseString)
                        val resStatusCode = jsonObj.optInt("statusCode")
                        if (resStatusCode == 200) {
                            val dataObj = jsonObj.optJSONObject("data")
                            val orderId = dataObj?.optString("providerOrderId") ?: dataObj?.optString("externalOrderId")
                            val status = dataObj?.optString("status") ?: "PENDING"
                            
                            val existing = tbDao.getTbDiagnosticsByBenId(benId)
                            val cache = (existing ?: TBDiagnosticsCache(benId = benId)).let {
                                if (testType.equals("XRAY_CHEST", ignoreCase = true)) {
                                    it.copy(
                                        xrayOrderId = orderId,
                                        xrayOrderStatus = status,
                                        syncState = SyncState.SYNCED
                                    )
                                } else if (testType.equals("MDR_RIF", ignoreCase = true)) {
                                    it.copy(
                                        rifOrderId = orderId,
                                        rifOrderStatus = status,
                                        trueNatRifResult = null,
                                        syncState = SyncState.SYNCED
                                    )
                                } else {
                                    it.copy(
                                        trueNatOrderId = orderId,
                                        trueNatOrderStatus = status,
                                        naatResult = null,
                                        trueNatRifResult = null,
                                        syncState = SyncState.SYNCED
                                    )
                                }
                            }
                            tbDao.saveTbDiagnostics(cache)
                            return@withContext org.piramalswasthya.stoptb.helpers.NetworkResponse.Success(orderId ?: "")
                        } else {
                            val errorMsg = jsonObj.optString("errorMessage") ?: "Failed to retry order"
                            return@withContext org.piramalswasthya.stoptb.helpers.NetworkResponse.Error(errorMsg)
                        }
                    }
                }
                return@withContext org.piramalswasthya.stoptb.helpers.NetworkResponse.Error("HTTP Error $statusCode")
            } catch (e: Exception) {
                Timber.e(e, "retryProdigiOrder failed")
                return@withContext org.piramalswasthya.stoptb.helpers.NetworkResponse.Error(e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun saveFailedOrderStatus(benId: Long, testType: String) {
        try {
            val existing = tbDao.getTbDiagnosticsByBenId(benId)
            val cache = (existing ?: TBDiagnosticsCache(benId = benId)).let {
                if (testType.equals("XRAY_CHEST", ignoreCase = true)) {
                    it.copy(xrayOrderStatus = "FAILED", syncState = SyncState.SYNCED)
                } else {
                    it.copy(trueNatOrderStatus = "FAILED", syncState = SyncState.SYNCED)
                }
            }
            tbDao.saveTbDiagnostics(cache)
        } catch (e: Exception) {
            Timber.e(e, "saveFailedOrderStatus failed")
        }
    }

    suspend fun markTestCompleted(benId: Long, orderType: String): org.piramalswasthya.stoptb.helpers.NetworkResponse<String> {
        return withContext(Dispatchers.IO) {
            val ben = benDao.getBen(benId)
                ?: return@withContext org.piramalswasthya.stoptb.helpers.NetworkResponse.Error("Beneficiary not found")
            if (useMockApi) {
                try {
                    val status = "IN_PROGRESS"
                    val existing = tbDao.getTbDiagnosticsByBenId(benId)
                    existing?.let {
                        val cache = when {
                            orderType.equals("XRAY_CHEST", ignoreCase = true) -> {
                                it.copy(
                                    xrayOrderStatus = status,
                                    syncState = SyncState.SYNCED
                                )
                            }
                            orderType.equals("MDR_RIF", ignoreCase = true) -> {
                                it.copy(
                                    rifOrderStatus = status,
                                    syncState = SyncState.SYNCED
                                )
                            }
                            else -> {
                                it.copy(
                                    trueNatOrderStatus = status,
                                    syncState = SyncState.SYNCED
                                )
                            }
                        }
                        tbDao.saveTbDiagnostics(cache)
                        preferenceDao.setDiagPollStartTime(benId, orderType, System.currentTimeMillis())
                    }
                    val pollKey = "${benId}_${orderType}"
                    pollCounts[pollKey] = 0
                    return@withContext org.piramalswasthya.stoptb.helpers.NetworkResponse.Success(status)
                } catch (e: Exception) {
                    return@withContext org.piramalswasthya.stoptb.helpers.NetworkResponse.Error(e.message ?: "Mock test completed failed")
                }
            }
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
                                            syncState = SyncState.SYNCED
                                        )
                                    }
                                    orderType.equals("MDR_RIF", ignoreCase = true) -> {
                                        it.copy(
                                            rifOrderStatus = status,
                                            syncState = SyncState.SYNCED
                                        )
                                    }
                                    else -> {
                                        it.copy(
                                            trueNatOrderStatus = status,
                                            isSputumCollected = true,
                                            syncState = SyncState.SYNCED
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

                            return@withContext org.piramalswasthya.stoptb.helpers.NetworkResponse.Success(status)
                        } else {
                            val errorMsg = jsonObj.optString("errorMessage") ?: "Failed to mark test completed"
                            return@withContext org.piramalswasthya.stoptb.helpers.NetworkResponse.Error(errorMsg)
                        }
                    }
                }
                org.piramalswasthya.stoptb.helpers.NetworkResponse.Error("HTTP Error $statusCode")
            } catch (e: Exception) {
                Timber.e(e, "markTestCompleted failed")
                org.piramalswasthya.stoptb.helpers.NetworkResponse.Error(e.message ?: "Unknown error")
            }
        }
    }

    suspend fun fetchOrderResult(benId: Long, orderType: String): org.piramalswasthya.stoptb.helpers.NetworkResponse<String> {
        return withContext(Dispatchers.IO) {
            val ben = benDao.getBen(benId)
                ?: return@withContext org.piramalswasthya.stoptb.helpers.NetworkResponse.Error("Beneficiary not found")
            val targetBenId = ben.beneficiaryId
            if (targetBenId <= 0) {
                return@withContext org.piramalswasthya.stoptb.helpers.NetworkResponse.Error("Beneficiary ID not valid")
            }
            if (useMockApi) {
                try {
                    val status = "COMPLETED"
                    if (orderType.equals("XRAY_CHEST", ignoreCase = true)) {
                        val isPositiveResult = (benId % 2 == 0L)
                        val existing = tbDao.getTbDiagnosticsByBenId(benId)
                        val cache = (existing ?: TBDiagnosticsCache(benId = benId)).copy(
                            xrayOrderStatus = status,
                            isChestXRayDone = true,
                            chestXRayResult = if (isPositiveResult) "TB Presumptive" else "Normal",
                            syncState = SyncState.SYNCED
                        )
                        tbDao.saveTbDiagnostics(cache)
                        return@withContext org.piramalswasthya.stoptb.helpers.NetworkResponse.Success(status)
                    } else if (orderType.equals("MDR_RIF", ignoreCase = true)) {
                        val rifResult = when (mockRifScenario) {
                            MockRifScenario.DETECTED -> "Rif Resistance Detected"
                            MockRifScenario.NOT_DETECTED -> "Rif Resistance Not Detected"
                            MockRifScenario.INDETERMINATE -> "Indeterminate"
                        }
                        val existing = tbDao.getTbDiagnosticsByBenId(benId)
                        val cache = (existing ?: TBDiagnosticsCache(benId = benId)).copy(
                            trueNatRifResult = rifResult,
                            syncState = SyncState.SYNCED
                        )
                        tbDao.saveTbDiagnostics(cache)
                        return@withContext org.piramalswasthya.stoptb.helpers.NetworkResponse.Success(status)
                    } else {
                        val mtbResult = when (mockMtbScenario) {
                            MockMtbScenario.DETECTED -> "MTB detected"
                            MockMtbScenario.NOT_DETECTED -> "MTB not detected"
                            MockMtbScenario.INVALID -> "Invalid"
                        }
                        val rifResult = when (mockRifScenario) {
                            MockRifScenario.DETECTED -> "Rif Resistance Detected"
                            MockRifScenario.NOT_DETECTED -> "Rif Resistance Not Detected"
                            MockRifScenario.INDETERMINATE -> "Indeterminate"
                        }
                        val isMtbDetected = mtbResult == "MTB detected"
                        if (isMtbDetected) {
                            try {
                                createProdigiOrder(benId, "MDR_RIF")
                            } catch (e: Exception) {
                                Timber.e(e, "Auto createProdigiOrder for MDR_RIF failed in mock mode")
                            }
                        }
                        val existing = tbDao.getTbDiagnosticsByBenId(benId)
                        val cache = (existing ?: TBDiagnosticsCache(benId = benId)).copy(
                            trueNatOrderStatus = status,
                            isNaatConducted = true,
                            naatResult = mtbResult,
                            trueNatRifResult = if (isMtbDetected) rifResult else existing?.trueNatRifResult,
                            isTBConfirmed = if (isMtbDetected) true else existing?.isTBConfirmed ?: false,
                            isConfirmed = if (isMtbDetected) true else existing?.isConfirmed ?: false,
                            rifOrderStatus = if (isMtbDetected) "PENDING" else existing?.rifOrderStatus,
                            rifOrderId = if (isMtbDetected) "MOCK-RIF-001" else existing?.rifOrderId,
                            syncState = SyncState.SYNCED
                        )
                        tbDao.saveTbDiagnostics(cache)
                        return@withContext org.piramalswasthya.stoptb.helpers.NetworkResponse.Success(status)
                    }
                } catch (e: Exception) {
                    return@withContext org.piramalswasthya.stoptb.helpers.NetworkResponse.Error(e.message ?: "Mock fetch result failed")
                }
            }
            try {
                val apiOrderType = if (orderType.equals("SPUTUM_TRUENAT", ignoreCase = true)) "MTB" else orderType
                val response = tmcNetworkApiService.fetchOrderResult(benId = ben.beneficiaryId, orderType = apiOrderType)
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
                            timber.log.Timber.d("STOP-TB polling debug: fetchOrderResult benId=$benId status=$status rawStatus=$rawStatus")
                            
                            val existing = tbDao.getTbDiagnosticsByBenId(benId)
                            val cache = (existing ?: TBDiagnosticsCache(benId = benId)).let {
                                if (orderType.equals("XRAY_CHEST", ignoreCase = true)) {
                                    val hasTbPresence = dataObj?.has("tbPresence") == true && !dataObj.isNull("tbPresence")
                                    val tbPresence = if (hasTbPresence) dataObj?.optBoolean("tbPresence") else null
                                    val serverResultSummary = dataObj?.optString("resultSummary")
                                    val chestResult = if (!serverResultSummary.isNullOrBlank()) {
                                        serverResultSummary
                                    } else {
                                        if (tbPresence == true) "TB Presumptive" else "Normal"
                                    }

                                    val isCompleted = status.equals("COMPLETED", ignoreCase = true)
                                    val xrayPos = isCompleted && isChestXrayPositive(chestResult)

                                     if (xrayPos && isTruenatIntegrated()) {
                                        val hasTruenat = !it.trueNatOrderStatus.isNullOrBlank() && !it.trueNatOrderStatus.equals("FAILED", ignoreCase = true)
                                        if (!hasTruenat) {
                                            try {
                                                val response = createProdigiOrder(benId, "SPUTUM_TRUENAT")
                                                if (response is org.piramalswasthya.stoptb.helpers.NetworkResponse.Success) {
                                                    org.piramalswasthya.stoptb.work.WorkerUtils.triggerTrueNatDiagnosticResultPollWorker(context, useMockApi)
                                                    it.copy(
                                                        xrayOrderStatus = "COMPLETED",
                                                        isReferredForDigitalChestXray = true,
                                                        isChestXRayDone = true,
                                                        chestXRayResult = chestResult,
                                                        trueNatOrderStatus = "AWAITING_PROVIDER_RESULT",
                                                        isSputumCollected = true,
                                                        isNaatConducted = true,
                                                        syncState = SyncState.SYNCED
                                                    )
                                                } else {
                                                    it.copy(
                                                        xrayOrderStatus = "COMPLETED",
                                                        isReferredForDigitalChestXray = true,
                                                        isChestXRayDone = true,
                                                        chestXRayResult = chestResult,
                                                        syncState = SyncState.SYNCED
                                                    )
                                                }
                                            } catch (e: Exception) {
                                                Timber.e(e, "Auto createProdigiOrder for SPUTUM_TRUENAT failed on +ve X-Ray")
                                                it.copy(
                                                    xrayOrderStatus = "COMPLETED",
                                                    isReferredForDigitalChestXray = true,
                                                    isChestXRayDone = true,
                                                    chestXRayResult = chestResult,
                                                    syncState = SyncState.SYNCED
                                                )
                                            }
                                        } else {
                                            it.copy(
                                                xrayOrderStatus = "COMPLETED",
                                                isReferredForDigitalChestXray = true,
                                                isChestXRayDone = true,
                                                chestXRayResult = chestResult,
                                                syncState = SyncState.SYNCED
                                            )
                                        }
                                    } else {
                                        it.copy(
                                            xrayOrderStatus = if (status.equals("COMPLETED", ignoreCase = true)) "COMPLETED" else if (status.equals("FAILED", ignoreCase = true) || status.equals("POLLING_TIMEOUT", ignoreCase = true) || status.equals("EXPIRED", ignoreCase = true)) "FAILED" else "AWAITING_PROVIDER_RESULT",
                                            isReferredForDigitalChestXray = true,
                                            isChestXRayDone = if (isCompleted) true else it.isChestXRayDone,
                                            chestXRayResult = if (isCompleted) chestResult else it.chestXRayResult,
                                            syncState = SyncState.SYNCED
                                        )
                                    }
                                } else if (orderType.equals("MDR_RIF", ignoreCase = true)) {
                                    val isCompleted = status.equals("COMPLETED", ignoreCase = true)
                                    val serverRifResultSummary = dataObj?.optString("resultSummary")
                                    val rifResult = if (!serverRifResultSummary.isNullOrBlank()) {
                                        serverRifResultSummary
                                    } else {
                                        when {
                                            dataObj == null || !dataObj.has("drugResistancePresence") || dataObj.isNull("drugResistancePresence") -> "Indeterminate"
                                            dataObj.optBoolean("drugResistancePresence") -> "Rif Resistance Detected"
                                            else -> "Rif Resistance Not Detected"
                                        }
                                    }
                                    val isRifIndeterminate = isCompleted && rifResult.equals("Indeterminate", ignoreCase = true)
                                    var computedRifStatus = if (isCompleted) "COMPLETED" else if (status.equals("FAILED", ignoreCase = true) || status.equals("POLLING_TIMEOUT", ignoreCase = true) || status.equals("EXPIRED", ignoreCase = true)) "FAILED" else "AWAITING_PROVIDER_RESULT"
                                    var computedRifOrderId = it.rifOrderId

                                     if (isRifIndeterminate) {
                                        try {
                                            val rifResponse = createProdigiOrder(benId, "MDR_RIF")
                                            if (rifResponse is org.piramalswasthya.stoptb.helpers.NetworkResponse.Success) {
                                                computedRifStatus = "AWAITING_PROVIDER_RESULT"
                                                computedRifOrderId = rifResponse.data
                                                org.piramalswasthya.stoptb.work.WorkerUtils.triggerRifDiagnosticResultPollWorker(context, useMockApi)
                                            }
                                        } catch (e: Exception) {
                                            Timber.e(e, "Failed to auto re-push RIF order on indeterminate result")
                                        }
                                    }

                                    it.copy(
                                        rifOrderStatus = computedRifStatus,
                                        rifOrderId = computedRifOrderId ?: it.rifOrderId,
                                        trueNatRifResult = if (isCompleted) rifResult else it.trueNatRifResult,
                                        syncState = SyncState.SYNCED
                                    )
                                } else {
                                    val serverMtbResultSummary = dataObj?.optString("resultSummary")
                                    val mtbResult = if (!serverMtbResultSummary.isNullOrBlank()) {
                                        val clean = serverMtbResultSummary.trim().lowercase()
                                        when {
                                            clean.contains("positive") || clean.contains("detected") -> "MTB detected"
                                            clean.contains("negative") || clean.contains("not detected") -> "MTB not detected"
                                            else -> "Invalid"
                                        }
                                    } else {
                                        when {
                                            dataObj == null || !dataObj.has("tbPresence") || dataObj.isNull("tbPresence") -> "Invalid"
                                            dataObj.optBoolean("tbPresence") -> "MTB detected"
                                            else -> "MTB not detected"
                                        }
                                    }
                                    val isCompleted = status.equals("COMPLETED", ignoreCase = true)
                                    val isMtbDetected = isCompleted && mtbResult == "MTB detected"

                                    var computedRifStatus: String? = it.rifOrderStatus
                                    var computedRifOrderId: String? = it.rifOrderId
                                    val hasExistingRifOrder = !it.rifOrderStatus.isNullOrBlank() && !it.rifOrderStatus.equals("FAILED", ignoreCase = true)
                                    
                                    if (isMtbDetected && !hasExistingRifOrder) {
                                        if (useMockApi) {
                                            try {
                                                createProdigiOrder(benId, "MDR_RIF")
                                                computedRifStatus = "AWAITING_PROVIDER_RESULT"
                                                computedRifOrderId = "MOCK-RIF-001"
                                                org.piramalswasthya.stoptb.work.WorkerUtils.triggerRifDiagnosticResultPollWorker(context, useMockApi)
                                            } catch (e: Exception) {
                                                Timber.e(e, "Auto createProdigiOrder for MDR_RIF failed in mock mode")
                                            }
                                        } else {
                                            val statusResponse = fetchBeneficiariesByStatus("MDR_RIF")
                                            var serverHasOrder = false
                                            if (statusResponse is org.piramalswasthya.stoptb.helpers.NetworkResponse.Success) {
                                                val statusData = statusResponse.data
                                                val awaitingTestCompletion = statusData?.awaitingTestCompletion ?: emptyList()
                                                val awaitingProviderResult = statusData?.awaitingProviderResult ?: emptyList()
                                                val completedList = statusData?.completed ?: emptyList()
                                                val regId = ben.beneficiaryId
                                                if (awaitingTestCompletion.contains(regId)) {
                                                    serverHasOrder = true
                                                    computedRifStatus = "AWAITING_PROVIDER_RESULT"
                                                    computedRifOrderId = "EXISTING-RIF-${regId}"
                                                    org.piramalswasthya.stoptb.work.WorkerUtils.triggerRifDiagnosticResultPollWorker(context, useMockApi)
                                                } else if (awaitingProviderResult.contains(regId)) {
                                                    serverHasOrder = true
                                                    computedRifStatus = "AWAITING_PROVIDER_RESULT"
                                                    computedRifOrderId = "EXISTING-RIF-${regId}"
                                                    org.piramalswasthya.stoptb.work.WorkerUtils.triggerRifDiagnosticResultPollWorker(context, useMockApi)
                                                } else if (completedList.contains(regId)) {
                                                    serverHasOrder = true
                                                    computedRifStatus = "COMPLETED"
                                                    computedRifOrderId = "EXISTING-RIF-${regId}"
                                                }
                                            }
                                            if (!serverHasOrder) {
                                                val maxRifRetries = 1
                                                var rifAttempt = 0
                                                var rifSuccess = false
                                                while (rifAttempt <= maxRifRetries && !rifSuccess) {
                                                    try {
                                                        val newOrderId = createProdigiOrder(benId, "MDR_RIF")
                                                        if (newOrderId is org.piramalswasthya.stoptb.helpers.NetworkResponse.Success) {
                                                            computedRifStatus = "AWAITING_PROVIDER_RESULT"
                                                            computedRifOrderId = newOrderId.data
                                                            rifSuccess = true
                                                            org.piramalswasthya.stoptb.work.WorkerUtils.triggerRifDiagnosticResultPollWorker(context, useMockApi)
                                                        } else {
                                                            rifAttempt++
                                                            if (rifAttempt <= maxRifRetries) {
                                                                kotlinx.coroutines.delay(5000L)
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        Timber.e(e, "Auto createProdigiOrder for MDR_RIF failed, attempt=${rifAttempt}")
                                                        rifAttempt++
                                                        if (rifAttempt <= maxRifRetries) {
                                                            kotlinx.coroutines.delay(5000L)
                                                        }
                                                    }
                                                }
                                                if (!rifSuccess) {
                                                    computedRifStatus = "FAILED"
                                                }
                                            }
                                        }
                                    }

                                    var computedTrueNatStatus = if (status.equals("COMPLETED", ignoreCase = true)) "COMPLETED" else if (status.equals("FAILED", ignoreCase = true) || status.equals("POLLING_TIMEOUT", ignoreCase = true) || status.equals("EXPIRED", ignoreCase = true)) "FAILED" else "AWAITING_PROVIDER_RESULT"
                                    var computedTrueNatOrderId = it.trueNatOrderId

                                    it.copy(
                                        trueNatOrderStatus = computedTrueNatStatus,
                                        trueNatOrderId = computedTrueNatOrderId ?: it.trueNatOrderId,
                                        isSputumCollected = true,
                                        isNaatConducted = if (isCompleted) true else it.isNaatConducted,
                                        naatResult = if (isCompleted) (serverMtbResultSummary ?: mtbResult) else it.naatResult,
                                        isTBConfirmed = if (isMtbDetected) true else it.isTBConfirmed,
                                        isConfirmed = if (isMtbDetected) true else it.isConfirmed,
                                        rifOrderStatus = computedRifStatus,
                                        rifOrderId = computedRifOrderId ?: it.rifOrderId,
                                        syncState = SyncState.SYNCED
                                    )
                                }
                            }
                            tbDao.saveTbDiagnostics(cache)
                            
                            return@withContext org.piramalswasthya.stoptb.helpers.NetworkResponse.Success(status)
                        } else {
                            val errorMsg = jsonObj.optString("errorMessage") ?: "Failed to fetch result"
                            return@withContext org.piramalswasthya.stoptb.helpers.NetworkResponse.Error(errorMsg)
                        }
                    }
                }
                org.piramalswasthya.stoptb.helpers.NetworkResponse.Error("HTTP Error $statusCode")
            } catch (e: Exception) {
                Timber.e(e, "fetchOrderResult failed")
                org.piramalswasthya.stoptb.helpers.NetworkResponse.Error(e.message ?: "Unknown error")
            }
        }
    }

    suspend fun fetchBeneficiariesByStatus(orderType: String, fetchResult: Boolean = true): org.piramalswasthya.stoptb.helpers.NetworkResponse<DiagnosticBeneficiaryStatusData> {
        return withContext(Dispatchers.IO) {
            val user = preferenceDao.getLoggedInUser()
                ?: return@withContext org.piramalswasthya.stoptb.helpers.NetworkResponse.Error("No user logged in")
            val locationRecord = preferenceDao.getLocationRecord()
                ?: return@withContext org.piramalswasthya.stoptb.helpers.NetworkResponse.Error("No location record found")

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
                            val dataObj = jsonObj.optJSONObject("data")
                            val awaitingTestCompList = mutableListOf<Long>()
                            val awaitingProvResList = mutableListOf<Long>()
                            val completedList = mutableListOf<Long>()
                            val pollingTimedOutList = mutableListOf<Long>()
                            val failedList = mutableListOf<Long>()
                            val refusedList = mutableListOf<Long>()

                            dataObj?.optJSONArray("awaitingTestCompletion")?.let { arr ->
                                for (i in 0 until arr.length()) {
                                    awaitingTestCompList.add(arr.getLong(i))
                                }
                            }
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
                            dataObj?.optJSONArray("pollingTimedOut")?.let { arr ->
                                for (i in 0 until arr.length()) {
                                    pollingTimedOutList.add(arr.getLong(i))
                                }
                            }
                            dataObj?.optJSONArray("failed")?.let { arr ->
                                for (i in 0 until arr.length()) {
                                    failedList.add(arr.getLong(i))
                                }
                            }
                            dataObj?.optJSONArray("refused")?.let { arr ->
                                for (i in 0 until arr.length()) {
                            refusedList.add(arr.getLong(i))
                                }
                            }

                            val isXray = orderType.equals("XRAY_CHEST", ignoreCase = true)
                            val isRif = orderType.equals("MDR_RIF", ignoreCase = true)

                            // 1. Awaiting Test Completion
                            for (regId in awaitingTestCompList) {
                                val ben = benDao.getBenByRegId(regId) ?: benDao.getBen(regId)
                                ben?.let { b ->
                                    val existing = tbDao.getTbDiagnosticsByBenId(b.beneficiaryId)
                                    val currentStatus = if (isXray) existing?.xrayOrderStatus else if (isRif) existing?.rifOrderStatus else existing?.trueNatOrderStatus
                                    val isInProgressOrDone = currentStatus.equals("IN_PROGRESS", ignoreCase = true) ||
                                            currentStatus.equals("AWAITING_PROVIDER_RESULT", ignoreCase = true) ||
                                            currentStatus.equals("PROCESSING", ignoreCase = true) ||
                                            currentStatus.equals("COMPLETED", ignoreCase = true)
                                    Timber.d("STOP-TB polling debug: awaitingTestCompletion regId=$regId benId=${b.beneficiaryId} currentStatus=$currentStatus isInProgressOrDone=$isInProgressOrDone")
                                    if (!isInProgressOrDone) {
                                        val cache = (existing ?: TBDiagnosticsCache(benId = b.beneficiaryId)).let {
                                            if (isXray) {
                                                it.copy(
                                                    xrayOrderStatus = "AWAITING_TEST_COMPLETION",
                                                    isReferredForDigitalChestXray = true,
                                                    syncState = SyncState.SYNCED
                                                )
                                            } else if (isRif) {
                                                it.copy(
                                                    rifOrderStatus = "AWAITING_TEST_COMPLETION",
                                                    syncState = SyncState.SYNCED
                                                )
                                            } else {
                                                it.copy(
                                                    trueNatOrderStatus = "AWAITING_TEST_COMPLETION",
                                                    isSputumCollected = true,
                                                    syncState = SyncState.SYNCED
                                                )
                                            }
                                        }
                                        tbDao.saveTbDiagnostics(cache)
                                    }
                                }
                            }

                            // 2. Awaiting Provider Result
                            for (regId in awaitingProvResList) {
                                val ben = benDao.getBenByRegId(regId) ?: benDao.getBen(regId)
                                ben?.let { b ->
                                    val existing = tbDao.getTbDiagnosticsByBenId(b.beneficiaryId)
                                    val currentStatus = if (isXray) existing?.xrayOrderStatus else if (isRif) existing?.rifOrderStatus else existing?.trueNatOrderStatus
                                    val isDone = currentStatus.equals("COMPLETED", ignoreCase = true)
                                    Timber.d("STOP-TB polling debug: awaitingProviderResult regId=$regId benId=${b.beneficiaryId} currentStatus=$currentStatus isDone=$isDone")
                                    if (!isDone) {
                                        val cache = (existing ?: TBDiagnosticsCache(benId = b.beneficiaryId)).let {
                                            if (isXray) {
                                                it.copy(
                                                    xrayOrderStatus = "AWAITING_PROVIDER_RESULT",
                                                    isReferredForDigitalChestXray = true,
                                                    syncState = SyncState.SYNCED
                                                )
                                            } else if (isRif) {
                                                it.copy(
                                                    rifOrderStatus = "AWAITING_PROVIDER_RESULT",
                                                    syncState = SyncState.SYNCED
                                                )
                                            } else {
                                                it.copy(
                                                    trueNatOrderStatus = "AWAITING_PROVIDER_RESULT",
                                                    isSputumCollected = true,
                                                    syncState = SyncState.SYNCED
                                                )
                                            }
                                        }
                                        tbDao.saveTbDiagnostics(cache)
                                    }
                                }
                            }

                            // 3. Completed
                            for (regId in completedList) {
                                val ben = benDao.getBenByRegId(regId) ?: benDao.getBen(regId)
                                ben?.let { b ->
                                    val existing = tbDao.getTbDiagnosticsByBenId(b.beneficiaryId)
                                    val currentStatus = if (isXray) existing?.xrayOrderStatus else if (isRif) existing?.rifOrderStatus else existing?.trueNatOrderStatus
                                    val needsResultFetch = when {
                                        isXray -> !currentStatus.equals("COMPLETED", ignoreCase = true) || existing?.chestXRayResult.isNullOrBlank()
                                        isRif -> !currentStatus.equals("COMPLETED", ignoreCase = true) || existing?.trueNatRifResult.isNullOrBlank()
                                        else -> !currentStatus.equals("COMPLETED", ignoreCase = true) || existing?.naatResult.isNullOrBlank()
                                    }

                                    if (needsResultFetch && fetchResult) {
                                        fetchOrderResult(b.beneficiaryId, orderType)
                                    }
                                    if (!fetchResult || !needsResultFetch) {
                                        val cache = (existing ?: TBDiagnosticsCache(benId = b.beneficiaryId)).let {
                                            if (isXray) {
                                                it.copy(
                                                    xrayOrderStatus = "COMPLETED",
                                                    isReferredForDigitalChestXray = true,
                                                    isChestXRayDone = true,
                                                    syncState = SyncState.SYNCED
                                                )
                                            } else if (isRif) {
                                                it.copy(
                                                    rifOrderStatus = "COMPLETED",
                                                    syncState = SyncState.SYNCED
                                                )
                                            } else {
                                                it.copy(
                                                    trueNatOrderStatus = "COMPLETED",
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

                            // 4. Polling Timed Out
                            for (regId in pollingTimedOutList) {
                                val ben = benDao.getBenByRegId(regId) ?: benDao.getBen(regId)
                                ben?.let { b ->
                                    val existing = tbDao.getTbDiagnosticsByBenId(b.beneficiaryId)
                                    val currentStatus = if (isXray) existing?.xrayOrderStatus else if (isRif) existing?.rifOrderStatus else existing?.trueNatOrderStatus
                                    val isDone = currentStatus.equals("COMPLETED", ignoreCase = true)
                                    if (!isDone) {
                                        val cache = (existing ?: TBDiagnosticsCache(benId = b.beneficiaryId)).let {
                                            if (isXray) {
                                                it.copy(
                                                    xrayOrderStatus = "POLLING_TIMEOUT",
                                                    isReferredForDigitalChestXray = true,
                                                    syncState = SyncState.SYNCED
                                                )
                                            } else if (isRif) {
                                                it.copy(
                                                    rifOrderStatus = "POLLING_TIMEOUT",
                                                    syncState = SyncState.SYNCED
                                                )
                                            } else {
                                                it.copy(
                                                    trueNatOrderStatus = "POLLING_TIMEOUT",
                                                    isSputumCollected = true,
                                                    syncState = SyncState.SYNCED
                                                )
                                            }
                                        }
                                        tbDao.saveTbDiagnostics(cache)
                                    }
                                }
                            }

                            // 5. Failed
                            for (regId in failedList) {
                                val ben = benDao.getBenByRegId(regId) ?: benDao.getBen(regId)
                                ben?.let { b ->
                                    val existing = tbDao.getTbDiagnosticsByBenId(b.beneficiaryId)
                                    val currentStatus = if (isXray) existing?.xrayOrderStatus else if (isRif) existing?.rifOrderStatus else existing?.trueNatOrderStatus
                                    val isDone = currentStatus.equals("COMPLETED", ignoreCase = true)
                                    if (!isDone) {
                                        val cache = (existing ?: TBDiagnosticsCache(benId = b.beneficiaryId)).let {
                                            if (isXray) {
                                                it.copy(
                                                    xrayOrderStatus = "FAILED",
                                                    isReferredForDigitalChestXray = true,
                                                    syncState = SyncState.SYNCED
                                                )
                                            } else if (isRif) {
                                                it.copy(
                                                    rifOrderStatus = "FAILED",
                                                    syncState = SyncState.SYNCED
                                                )
                                            } else {
                                                it.copy(
                                                    trueNatOrderStatus = "FAILED",
                                                    isSputumCollected = true,
                                                    syncState = SyncState.SYNCED
                                                )
                                            }
                                        }
                                        tbDao.saveTbDiagnostics(cache)
                                    }
                                }
                            }

                            // 5. Refused
                            for (regId in refusedList) {
                                val ben = benDao.getBenByRegId(regId) ?: benDao.getBen(regId)
                                ben?.let { b ->
                                    val existing = tbDao.getTbDiagnosticsByBenId(b.beneficiaryId)
                                    val currentStatus = if (isXray) existing?.xrayOrderStatus else if (isRif) existing?.rifOrderStatus else existing?.trueNatOrderStatus
                                    val isRefused = currentStatus.equals("REFUSED", ignoreCase = true)
                                    if (!isRefused) {
                                        val cache = (existing ?: TBDiagnosticsCache(benId = b.beneficiaryId)).let {
                                            if (isXray) {
                                                it.copy(
                                                    xrayOrderStatus = "REFUSED",
                                                    isReferredForDigitalChestXray = false,
                                                    syncState = SyncState.SYNCED
                                                )
                                            } else if (isRif) {
                                                it.copy(
                                                    rifOrderStatus = "REFUSED",
                                                    syncState = SyncState.SYNCED
                                                )
                                            } else {
                                                it.copy(
                                                    trueNatOrderStatus = "REFUSED",
                                                    isSputumCollected = false,
                                                    syncState = SyncState.SYNCED
                                                )
                                            }
                                        }
                                        tbDao.saveTbDiagnostics(cache)
                                    }
                                }
                            }
 
                            val resultData = DiagnosticBeneficiaryStatusData(
                                awaitingTestCompletion = awaitingTestCompList,
                                awaitingProviderResult = awaitingProvResList,
                                completed = completedList,
                                pollingTimedOut = pollingTimedOutList,
                                failed = failedList,
                                refused = refusedList
                            )
                            return@withContext org.piramalswasthya.stoptb.helpers.NetworkResponse.Success(resultData)
                        } else {
                            val errorMsg = jsonObj.optString("message") ?: "Failed to fetch beneficiary order statuses"
                            return@withContext org.piramalswasthya.stoptb.helpers.NetworkResponse.Error(errorMsg)
                        }
                    }
                }
                org.piramalswasthya.stoptb.helpers.NetworkResponse.Error("HTTP Error $statusCode")
            } catch (e: Exception) {
                Timber.e(e, "fetchBeneficiariesByStatus failed for $orderType")
                org.piramalswasthya.stoptb.helpers.NetworkResponse.Error(e.message ?: "Unknown error")
            }
        }
    }

    suspend fun getVendorHealth(orderType: String): org.piramalswasthya.stoptb.helpers.NetworkResponse<String> {
        return withContext(Dispatchers.IO) {
            try {
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
                            return@withContext org.piramalswasthya.stoptb.helpers.NetworkResponse.Success(
                                "isConnected: $isConnected, isDeviceIntegrated: $isDeviceIntegrated"
                            )
                        } else {
                            val errorMsg = json.optString("message", "Health check failed")
                            return@withContext org.piramalswasthya.stoptb.helpers.NetworkResponse.Error(errorMsg)
                        }
                    }
                }
                org.piramalswasthya.stoptb.helpers.NetworkResponse.Error("HTTP Error $statusCode")
            } catch (e: Exception) {
                Timber.e(e, "getVendorHealth failed for $orderType")
                org.piramalswasthya.stoptb.helpers.NetworkResponse.Error(e.message ?: "Unknown error")
            }
        }
    }

    suspend fun checkDeviceIntegration(orderType: String): Boolean {
        val health = getVendorHealth(orderType)
        if (health is org.piramalswasthya.stoptb.helpers.NetworkResponse.Success) {
            val dataStr = health.data
            return dataStr?.contains("isDeviceIntegrated: true") == true
        }
        return false
    }

    suspend fun refreshDeviceIntegrationConfig() {
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

    private fun isChestXrayPositive(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val clean = value.trim().lowercase()
        if (clean.contains("negative") || clean.contains("invalid") || clean.contains("not detected") || clean.contains("waiting")) {
            return false
        }
        return clean.contains("positive") || clean.contains("presumptive") || clean.contains("detected") || clean.contains("tb") || clean.contains("abnormal")
    }
}
