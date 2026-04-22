package org.piramalswasthya.stoptb.repositories

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.database.room.dao.VitalDao
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.model.VitalCache
import org.piramalswasthya.stoptb.network.AmritApiService
import org.piramalswasthya.stoptb.network.VitalNurseDataRequest
import timber.log.Timber
import java.net.SocketTimeoutException
import javax.inject.Inject

class VitalRepo @Inject constructor(
    private val vitalDao: VitalDao,
    private val preferenceDao: PreferenceDao,
    private val userRepo: UserRepo,
    private val tmcNetworkApiService: AmritApiService
) {

    suspend fun getVitals(benId: Long): VitalCache? {
        return withContext(Dispatchers.IO) {
            vitalDao.getVitals(benId)
        }
    }

    val vitalBenIds: Flow<List<Long>> = vitalDao.getAllVitalBenIds()

    suspend fun saveVitals(vitalCache: VitalCache) {
        withContext(Dispatchers.IO) {
            vitalDao.saveVitals(vitalCache)
        }
    }

    suspend fun saveVitalsAndSync(vitalCache: VitalCache): Boolean {
        return withContext(Dispatchers.IO) {
            vitalDao.saveVitals(vitalCache.copy(syncState = SyncState.UNSYNCED))
            val synced = pushSingleVital(vitalCache)
            if (synced) {
                vitalDao.saveVitals(vitalCache.copy(syncState = SyncState.SYNCED))
            }
            true
        }
    }

    private suspend fun pushSingleVital(vitalCache: VitalCache): Boolean {
        val user = preferenceDao.getLoggedInUser() ?: return false
        return try {
            val response = tmcNetworkApiService.saveVitalNurseData(
                VitalNurseDataRequest.from(vitalCache, user)
            )
            val responseBody = response.body()?.string()
            if (!response.isSuccessful || responseBody.isNullOrBlank()) return false
            val statusCode = JSONObject(responseBody).optInt("statusCode", 0)
            when (statusCode) {
                200 -> true
                401, 5002 -> {
                    if (userRepo.refreshTokenTmc(user.userName, user.password)) {
                        pushSingleVital(vitalCache)
                    } else {
                        false
                    }
                }
                else -> false
            }
        } catch (e: SocketTimeoutException) {
            if (e.message == "Refreshed Token") {
                pushSingleVital(vitalCache)
            } else {
                false
            }
        } catch (e: Exception) {
            if (shouldRetryWithRefresh(e, user.userName, user.password)) {
                pushSingleVital(vitalCache)
            } else {
                Timber.e(e, "Vital sync failed for benId=%s", vitalCache.benId)
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
}
