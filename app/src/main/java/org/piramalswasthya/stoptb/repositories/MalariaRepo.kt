package org.piramalswasthya.stoptb.repositories

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.database.room.dao.BenDao
import org.piramalswasthya.stoptb.database.room.dao.MalariaDao
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.helpers.Konstants
import org.piramalswasthya.stoptb.model.MalariaConfirmedCasesCache
import org.piramalswasthya.stoptb.model.MalariaScreeningCache
import org.piramalswasthya.stoptb.network.AmritApiService
import org.piramalswasthya.stoptb.network.GetDataPaginatedRequestForDisease
import org.piramalswasthya.stoptb.network.MalariaConfirmedDTO
import org.piramalswasthya.stoptb.network.MalariaConfirmedRequestDTO
import org.piramalswasthya.stoptb.network.MalariaScreeningDTO
import org.piramalswasthya.stoptb.network.MalariaScreeningRequestDTO
import timber.log.Timber
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

class MalariaRepo @Inject constructor(
    private val malariaDao: MalariaDao,
    private val benDao: BenDao,
    private val preferenceDao: PreferenceDao,
    private val userRepo: UserRepo,
    private val tmcNetworkApiService: AmritApiService
) {

    suspend fun getLatestVisitForBen(benId: Long): MalariaScreeningCache? {
        return withContext(Dispatchers.IO) { malariaDao.getLatestVisitForBen(benId) }
    }

    suspend fun getlastvisitIdforBen(benId: Long): Long? {
        return withContext(Dispatchers.IO) { malariaDao.getLastVisitIdForBen(benId) }
    }

    suspend fun saveMalariaScreening(malariaScreeningCache: MalariaScreeningCache) {
        withContext(Dispatchers.IO) { malariaDao.saveMalariaScreening(malariaScreeningCache) }
    }

    suspend fun getMalariaConfirmed(benId: Long): MalariaConfirmedCasesCache? {
        return withContext(Dispatchers.IO) { malariaDao.getMalariaConfirmed(benId) }
    }

    suspend fun saveMalariaConfirmed(tbSuspectedCache: MalariaConfirmedCasesCache) {
        withContext(Dispatchers.IO) { malariaDao.saveMalariaConfirmed(tbSuspectedCache) }
    }

    suspend fun getMalariaScreeningDetailsFromServer(): Int {
        return withContext(Dispatchers.IO) {
            val user = preferenceDao.getLoggedInUser()
                ?: throw IllegalStateException("No user logged in!!")
            try {
                val response = tmcNetworkApiService.getMalariaScreeningData(
                    GetDataPaginatedRequestForDisease(
                        ashaId = user.userId,
                        pageNo = 0,
                        fromDate = BenRepo.getCurrentDate(Konstants.defaultTimeStamp),
                        toDate = getCurrentDate(),
                        diseaseTypeID = 1
                    )
                )
                val statusCode = response.code()
                if (statusCode == 200) {
                    val responseString = response.body()?.string()
                    if (responseString != null) {
                        val jsonObj = JSONObject(responseString)
                        val responseStatusCode = jsonObj.getInt("statusCode")
                        val errorMessage = jsonObj.optString("errorMessage", "")
                        when (responseStatusCode) {
                            200 -> {
                                try {
                                    saveMalariaScreeningCacheFromResponse(jsonObj.getString("data"))
                                } catch (e: Exception) {
                                    Timber.d("Malaria Screening entries not synced $e")
                                    return@withContext 0
                                }
                                return@withContext 1
                            }
                            5002 -> {
                                if (userRepo.refreshTokenTmc(user.userName, user.password))
                                    throw SocketTimeoutException("Refreshed Token!")
                                else throw IllegalStateException("User Logged out!!")
                            }
                            5000 -> { if (errorMessage == "No record found") return@withContext 0 }
                            else -> throw IllegalStateException("$responseStatusCode received")
                        }
                    }
                }
            } catch (e: SocketTimeoutException) {
                Timber.e("get_malaria error : $e"); return@withContext -2
            } catch (e: IllegalStateException) {
                Timber.e("get_malaria error : $e"); return@withContext -1
            }
            -1
        }
    }

    private suspend fun saveMalariaScreeningCacheFromResponse(dataObj: String) {
        val malariaLists: List<MalariaScreeningDTO> = if (dataObj.trimStart().startsWith("[")) {
            Gson().fromJson(dataObj, Array<MalariaScreeningDTO>::class.java)?.toList() ?: emptyList()
        } else {
            Gson().fromJson(dataObj, MalariaScreeningRequestDTO::class.java)?.malariaLists ?: emptyList()
        }
        malariaLists.forEach { dto ->
            dto.caseDate?.let {
                val existing = malariaDao.getMalariaScreening(
                    dto.benId,
                    getLongFromDate(dto.caseDate),
                    getLongFromDate(dto.caseDate) - 19_800_000
                )
                if (existing == null) {
                    benDao.getBen(dto.benId)?.let { malariaDao.saveMalariaScreening(dto.toCache()) }
                }
            }
        }
    }

    suspend fun getMalariaConfiremedDetailsFromServer(): Int {
        return withContext(Dispatchers.IO) {
            val user = preferenceDao.getLoggedInUser()
                ?: throw IllegalStateException("No user logged in!!")
            try {
                val response = tmcNetworkApiService.getMalariaConfirmedData(
                    GetDataPaginatedRequestForDisease(
                        ashaId = user.userId,
                        pageNo = 0,
                        fromDate = BenRepo.getCurrentDate(Konstants.defaultTimeStamp),
                        toDate = getCurrentDate(),
                        diseaseTypeID = 1
                    )
                )
                val statusCode = response.code()
                if (statusCode == 200) {
                    val responseString = response.body()?.string()
                    if (responseString != null) {
                        val jsonObj = JSONObject(responseString)
                        val responseStatusCode = jsonObj.getInt("statusCode")
                        val errorMessage = jsonObj.optString("errorMessage", "")
                        when (responseStatusCode) {
                            200 -> {
                                try {
                                    saveMalariaConfirmedCacheFromResponse(jsonObj.getString("data"))
                                } catch (e: Exception) {
                                    Timber.d("Malaria Confirmed entries not synced $e"); return@withContext 0
                                }
                                return@withContext 1
                            }
                            5002 -> {
                                if (userRepo.refreshTokenTmc(user.userName, user.password))
                                    throw SocketTimeoutException("Refreshed Token!")
                                else throw IllegalStateException("User Logged out!!")
                            }
                            5000 -> { if (errorMessage == "No record found") return@withContext 0 }
                            else -> throw IllegalStateException("$responseStatusCode received")
                        }
                    }
                }
            } catch (e: SocketTimeoutException) {
                Timber.e("get_malaria_confirmed error : $e"); return@withContext -2
            } catch (e: IllegalStateException) {
                Timber.e("get_malaria_confirmed error : $e"); return@withContext -1
            }
            -1
        }
    }

    private suspend fun saveMalariaConfirmedCacheFromResponse(dataObj: String) {
        val list: List<MalariaConfirmedDTO> = if (dataObj.trimStart().startsWith("[")) {
            Gson().fromJson(dataObj, Array<MalariaConfirmedDTO>::class.java)?.toList() ?: emptyList()
        } else {
            Gson().fromJson(dataObj, MalariaConfirmedRequestDTO::class.java)?.malariaFollowListUp ?: emptyList()
        }
        list.forEach { dto ->
            dto.dateOfDiagnosis?.let {
                val existing = malariaDao.getMalariaConfirmed(
                    dto.benId,
                    getLongFromDate(dto.dateOfDiagnosis),
                    getLongFromDate(dto.dateOfDiagnosis) - 19_800_000
                )
                if (existing == null) {
                    benDao.getBen(dto.benId)?.let { malariaDao.saveMalariaConfirmed(dto.toCache()) }
                }
            }
        }
    }

    suspend fun pushUnSyncedRecords(): Boolean {
        val screeningResult = pushUnSyncedRecordsMalariaScreening()
        val confirmedResult = pushUnSyncedRecordsTBSuspected()
        Timber.d("Malaria push results: screening=$screeningResult, confirmed=$confirmedResult")
        return true
    }

    private suspend fun pushUnSyncedRecordsMalariaScreening(): Int {
        return withContext(Dispatchers.IO) {
            val user = preferenceDao.getLoggedInUser()
                ?: throw IllegalStateException("No user logged in!!")
            val list = malariaDao.getMalariaScreening(SyncState.UNSYNCED)
            if (list.isEmpty()) return@withContext 1
            for (chunk in list.chunked(20)) {
                try {
                    val response = tmcNetworkApiService.saveMalariaScreeningData(
                        MalariaScreeningRequestDTO(userId = user.userId, malariaLists = chunk.map { it.toDTO() })
                    )
                    if (response.code() == 200) {
                        val jsonObj = JSONObject(response.body()?.string() ?: "{}")
                        if (jsonObj.getInt("statusCode") == 200) {
                            chunk.forEach { it.syncState = SyncState.SYNCED; malariaDao.saveMalariaScreening(it) }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Malaria Screening chunk push failed")
                }
            }
            1
        }
    }

    private suspend fun pushUnSyncedRecordsTBSuspected(): Int {
        return withContext(Dispatchers.IO) {
            val user = preferenceDao.getLoggedInUser()
                ?: throw IllegalStateException("No user logged in!!")
            val list = malariaDao.getMalariaConfirmed(SyncState.UNSYNCED)
            if (list.isEmpty()) return@withContext 1
            for (chunk in list.chunked(20)) {
                try {
                    val response = tmcNetworkApiService.saveMalariaConfirmedData(
                        MalariaConfirmedRequestDTO(userId = user.userId, malariaFollowListUp = chunk.map { it.toDTO() })
                    )
                    if (response.code() == 200) {
                        val jsonObj = JSONObject(response.body()?.string() ?: "{}")
                        if (jsonObj.getInt("statusCode") == 200) {
                            chunk.forEach { it.syncState = SyncState.SYNCED; malariaDao.saveMalariaConfirmed(it) }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Malaria Confirmed chunk push failed")
                }
            }
            1
        }
    }

    companion object {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.ENGLISH)
        private fun getCurrentDate(millis: Long = System.currentTimeMillis()): String {
            return "${dateFormat.format(millis)}T${timeFormat.format(millis)}.000Z"
        }
        private fun getLongFromDate(dateString: String): Long {
            val date = try {
                SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(dateString)
            } catch (_: Exception) {
                SimpleDateFormat("MMM d, yyyy h:mm:ss a", Locale.ENGLISH).parse(dateString)
            }
            return date?.time ?: throw IllegalStateException("Invalid date: $dateString")
        }
    }
}