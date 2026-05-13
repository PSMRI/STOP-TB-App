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
import timber.log.Timber
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

class TBRepo @Inject constructor(
    private val tbDao: TBDao,
    private val benDao: BenDao,
    private val preferenceDao: PreferenceDao,
    private val userRepo: UserRepo,
    private val tmcNetworkApiService: AmritApiService
) {

    suspend fun getTBScreening(benId: Long): TBScreeningCache? {
        return withContext(Dispatchers.IO) {
            tbDao.getTbScreening(benId)
        }
    }

    suspend fun saveTBScreening(tbScreeningCache: TBScreeningCache) {
        withContext(Dispatchers.IO) {
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

    suspend fun saveTBDiagnostics(tbDiagnosticsCache: TBDiagnosticsCache) {
        withContext(Dispatchers.IO) {
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
                if (tbScreeningCache == null) {
                    benDao.getBen(tbScreeningDTO.benId)?.let {
                        tbDao.saveTbScreening(tbScreeningDTO.toCache())
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
            if (tbDao.getGeneralOpd(generalOpdDTO.benId) == null) {
                benDao.getBen(generalOpdDTO.benId)?.let {
                    val cache = generalOpdDTO.toCache()
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
            val cache = (existing ?: GeneralOpdCache(benId = ben.beneficiaryId)).copy(
                chiefComplaints = item.optStringListOrNull("chiefComplaint"),
                medications = item.optStringOrNull("medication")?.let { listOf(it) },
                dosage = item.optStringOrNull("dosage"),
                frequency = item.optStringOrNull("frequency"),
                duration = item.optStringOrNull("duration"),
                notes = item.optStringOrNull("notes"),
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
                if (tbDiagnosticsCache == null) {
                    benDao.getBen(tbDiagnosticsDTO.benId)?.let {
                        val cache = tbDiagnosticsDTO.toCache()
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
            val cache = (existing ?: TBDiagnosticsCache(benId = ben.beneficiaryId)).copy(
                visitDate = visitDate,
                nikshayId = item.optStringOrNull("nikshayId"),
                isChestXRayDone = item.optNullableBoolean("isDigitalChestXrayConducted"),
                chestXRayResult = item.optStringOrNull("digitalChestXrayResult"),
                isNaatConducted = item.optNullableBoolean("isTruenatConducted"),
                naatResult = item.optStringOrNull("truenatResult"),
                recommendedForLiquidCultureTest = item.optNullableBoolean("recommendedForLiquidCulture"),
                liquidCultureResult = item.optStringOrNull("liquidCultureResult"),
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
                val response = tmcNetworkApiService.getTBSuspectedData(
                    GetDataPaginatedRequest(
                        ashaId = user.userId,
                        pageNo = 0,
                        fromDate = BenRepo.getCurrentDate(Konstants.defaultTimeStamp),
                        toDate = getCurrentDate()
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
                val tbSuspectedCache: TBSuspectedCache? =
                    tbDao.getTbSuspected(
                        tbSuspectedDTO.benId,
                        getLongFromDate(tbSuspectedDTO.visitDate),
                        getLongFromDate(tbSuspectedDTO.visitDate) - 19_800_000
                    )
                if (tbSuspectedCache == null) {
                    benDao.getBen(tbSuspectedDTO.benId)?.let {
                        tbDao.saveTbSuspected(tbSuspectedDTO.toCache())
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

                    tbDao.saveTbConfirmed(cache)


                    tbConfirmedList.add(cache)

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
                return System.currentTimeMillis()
            }
            val patterns = listOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "MMM d, yyyy h:mm:ss a"
            )
            patterns.forEach { pattern ->
                runCatching {
                    SimpleDateFormat(pattern, Locale.ENGLISH).parse(dateString)?.time
                }.getOrNull()?.let { return it }
            }
            return System.currentTimeMillis()
        }

        private fun JSONObject.optNullableBoolean(name: String): Boolean? {
            if (!has(name) || isNull(name)) return null
            return optBoolean(name)
        }

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
    }


}
