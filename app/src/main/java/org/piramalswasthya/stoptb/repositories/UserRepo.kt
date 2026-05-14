package org.piramalswasthya.stoptb.repositories

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.crypt.CryptoUtil
import org.piramalswasthya.stoptb.database.room.InAppDb
import org.piramalswasthya.stoptb.database.room.dao.BenDao
import org.piramalswasthya.stoptb.database.room.dao.SyncDao
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.helpers.NetworkResponse
import org.piramalswasthya.stoptb.model.SyncStatusCache
import org.piramalswasthya.stoptb.model.LocationEntity
import org.piramalswasthya.stoptb.model.LocationRecord
import org.piramalswasthya.stoptb.model.User
import org.piramalswasthya.stoptb.network.AmritApiService
import org.piramalswasthya.stoptb.network.TmcAuthUserRequest
import org.piramalswasthya.stoptb.network.TmcRefreshTokenRequest
import org.piramalswasthya.stoptb.network.interceptors.TokenInsertTmcInterceptor
import retrofit2.HttpException
import timber.log.Timber
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject


class UserRepo @Inject constructor(
    benDao: BenDao,
    private val db: InAppDb,
    private val preferenceDao: PreferenceDao,
    private val syncDao: SyncDao,
    private val amritApiService: AmritApiService,
    @ApplicationContext private val appContext: Context,
) {

    private val selectedVillage get() = preferenceDao.getLocationRecord()?.village?.id ?: 0
    val unProcessedRecordCount: Flow<List<SyncStatusCache>> get() = syncDao.getSyncStatus(selectedVillage)



    suspend fun authenticateUser(userName: String, password: String): NetworkResponse<User?> {
        return withContext(Dispatchers.IO) {
            try {
                val authData = getTokenAmrit(userName, password)
                val user = setUserRole(authData.userId, password, authData.subCentre)
                return@withContext NetworkResponse.Success(user)
            } catch (se: SocketTimeoutException) {
                return@withContext NetworkResponse.Error(
                    message = appContext.getString(R.string.error_sign_in_timeout)
                )
            } catch (se: HttpException) {
                return@withContext when (se.code()) {
                    401 -> NetworkResponse.Error(
                        message = appContext.getString(R.string.error_sign_in_invalid_u_p)
                    )
                    else -> NetworkResponse.Error(
                        message = appContext.getString(R.string.error_login_unable_to_reach_server)
                    )
                }
            } catch (ce: ConnectException) {
                return@withContext NetworkResponse.Error(
                    message = appContext.getString(R.string.error_login_server_refused)
                )
            } catch (ue: UnknownHostException) {
                return@withContext NetworkResponse.Error(
                    message = appContext.getString(R.string.error_login_unable_to_reach_server)
                )
            } catch (ie: Exception) {
                return@withContext NetworkResponse.Error(message = localizedAuthExceptionMessage(ie))
            }
        }
    }

    suspend fun saveToken(userName: String, password: String): NetworkResponse<User?> {
        return withContext(Dispatchers.IO) {
            try {
                val authData = getTokenAmrit(userName, password)
                val user = setUserRole(authData.userId, password, authData.subCentre)
                return@withContext NetworkResponse.Success(user)
            } catch (se: SocketTimeoutException) {
                return@withContext NetworkResponse.Error(
                    message = appContext.getString(R.string.error_sign_in_timeout)
                )
            } catch (se: HttpException) {
                return@withContext NetworkResponse.Error(
                    message = appContext.getString(R.string.error_login_unable_to_reach_server)
                )
            } catch (ce: ConnectException) {
                return@withContext NetworkResponse.Error(
                    message = appContext.getString(R.string.error_login_server_refused)
                )
            } catch (ue: UnknownHostException) {
                return@withContext NetworkResponse.Error(
                    message = appContext.getString(R.string.error_login_unable_to_reach_server)
                )
            } catch (ie: Exception) {
                return@withContext NetworkResponse.Error(message = localizedAuthExceptionMessage(ie))
            }
        }
    }

    private suspend fun setUserRole(userId: Int, password: String, subCentre: String?): User {
        val response = amritApiService.getUserDetailsById(userId = userId)
        Timber.d(
            "Login user details response: role=${response.data.roleName}, " +
                    "tuId=${response.data.tuId}, tuName=${response.data.tuName}, " +
                    "healthFacilityId=${response.data.healthFacilityId}, " +
                    "healthFacilityName=${response.data.healthFacilityName}"
        )
        val user = response.data.toUser(password, subCentre)
        Timber.d(
            "Login mapped locations: tuCount=${user.tus.orEmpty().size}, " +
                    "healthFacilityCount=${user.healthFacilities.orEmpty().size}, " +
                    "villageCount=${user.villages.size}"
        )
        preferenceDao.registerUser(user)
        // Auto-set location if user has exactly one village (common for ASHA workers)
        if (user.villages.size == 1) {
            val locationRecord = LocationRecord(
                country = LocationEntity(1, "India"),
                state = user.state,
                district = user.district,
                block = user.block,
                tu = user.tus.orEmpty().firstOrNull(),
                healthFacility = user.healthFacilities.orEmpty().firstOrNull(),
                village = user.villages.first()
            )
            preferenceDao.saveLocationRecord(locationRecord)
            Timber.d("UserRepo: Auto-set location to village ${user.villages.first().id} (${user.villages.first().name})")
        }
        return user
    }


    private fun offlineLogin(userName: String, password: String): Boolean {
        val loggedInUser = preferenceDao.getLoggedInUser()
        loggedInUser?.let {
            if (it.userName == userName && it.password == password) {
                val amritToken = preferenceDao.getAmritToken()
                TokenInsertTmcInterceptor.setToken(
                    amritToken
                        ?: throw IllegalStateException("User logging offline without pref saved token B!")
                )
                Timber.w("User Logged in!")

                return true
            } else if (it.userName == userName) {
                throw IllegalStateException("Invalid Username/password")
                Timber.w("Invalid Username/password")
                return false
            }
        }
        return false
    }

    private fun encrypt(password: String): String {
        val util = CryptoUtil()
        return util.encrypt(password)
    }

    suspend fun refreshTokenTmc(userName: String, password: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val refreshToken = preferenceDao.getRefreshToken()
                    ?: return@withContext false
                val response =     amritApiService.getRefreshToken(
                    json = TmcRefreshTokenRequest(refreshToken)
                )

                val responseBody = JSONObject(
                    response.body()?.string()
                        ?: throw IllegalStateException("Response success but data missing @ $response")
                )
                val responseStatusCode = responseBody.getInt("statusCode")
                if (responseStatusCode == 200) {
                    val data = responseBody.getJSONObject("data")
                    TokenInsertTmcInterceptor.setJwt(data.getString("jwtToken"))
                    preferenceDao.registerJWTAmritToken(data.getString("jwtToken"))
                    preferenceDao.registerRefreshToken(data.getString("refreshToken"))

                    val token = data.getString("key")
                    TokenInsertTmcInterceptor.setToken(token)
                    preferenceDao.registerAmritToken(token)
                    return@withContext true
                } else {
                    val errorMessage = responseBody.getString("errorMessage")
                    Timber.e("Error Message $errorMessage")
                    return@withContext false
                }

            } catch (se: SocketTimeoutException) {
                return@withContext refreshTokenTmc(userName, password)
            } catch (e: HttpException) {
                Timber.e("Auth Failed!")
                return@withContext false
            } catch (e: Exception) {
                return@withContext true
            }
        }
    }

    private data class AuthUserData(
        val userId: Int,
        val subCentre: String?
    )

    private suspend fun getTokenAmrit(userName: String, password: String): AuthUserData {
        return withContext(Dispatchers.IO) {
            val encryptedPassword = encrypt(password)
            val response =
                amritApiService.getJwtToken(
                    json = TmcAuthUserRequest(
                        userName,
                        encryptedPassword
                    )
                )
            Timber.d("JWT : $response")
            val responseBody = JSONObject(
                response.body()?.string()
                    ?: throw IllegalStateException("Response success but data missing @ $response")
            )
            val statusCode = responseBody.getInt("statusCode")
            if (statusCode == 5002)
                throw IllegalStateException("Login failed")
            if (statusCode == 401)
                throw IllegalStateException("Invalid username / password")
            val data = responseBody.getJSONObject("data")
            val token = data.getString("key")
            val userId = data.getInt("userID")
            val subCentre = data.optJSONObject("facilityData")
                ?.optJSONArray("facilities")
                ?.takeIf { it.length() > 0 }
                ?.optJSONObject(0)
                ?.optString("facilityName")
                ?.takeIf { !it.isNullOrBlank() }
            val refreshToken = data.getString("refreshToken")
            //  db.clearAllTables()
            TokenInsertTmcInterceptor.setJwt(data.getString("jwtToken"))
            preferenceDao.registerJWTAmritToken(data.getString("jwtToken"))
            preferenceDao.registerRefreshToken(refreshToken)
            TokenInsertTmcInterceptor.setToken(token)
            preferenceDao.registerAmritToken(token)
            preferenceDao.lastAmritTokenFetchTimestamp = System.currentTimeMillis()
            return@withContext AuthUserData(userId, subCentre)
        }
    }

    private fun localizedAuthExceptionMessage(ie: Exception): String {
        return when (ie.message) {
            "Invalid username / password",
            "Invalid Username/password" -> appContext.getString(R.string.error_sign_in_invalid_u_p)

            "Login failed" -> appContext.getString(R.string.error_login_failed)
            else -> ie.message?.takeIf { it.isNotBlank() }
                ?: appContext.getString(R.string.error_login_generic)
        }
    }

    suspend fun saveFirebaseToken(userId: Int, token: String, updatedAt: String) {
        withContext(Dispatchers.IO) {
            try {
                val requestBody = mapOf(
                    "userId" to userId,
                    "token" to token,
                    "updatedAt" to updatedAt
                )

                val response = amritApiService.saveFirebaseToken(requestBody)

                if (response.isSuccessful) {
                    Timber.d("Firebase token saved successfully: ${response.body()?.string()}")
                } else {
                    Timber.e("Failed to save Firebase token: ${response.code()} ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Exception while saving Firebase token")
            }
        }
    }

}
