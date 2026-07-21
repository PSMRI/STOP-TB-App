package org.piramalswasthya.stoptb.repositories

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.network.AbdmMappedFacilityData
import org.piramalswasthya.stoptb.network.AbdmMappedFacilityResponse
import org.piramalswasthya.stoptb.network.AmritApiService
import org.piramalswasthya.stoptb.network.BenHealthDetails
import org.piramalswasthya.stoptb.network.CareContextGenerateOtpData
import org.piramalswasthya.stoptb.network.CareContextGenerateOtpRequest
import org.piramalswasthya.stoptb.network.CareContextGenerateOtpResponse
import org.piramalswasthya.stoptb.network.CareContextValidateOtpData
import org.piramalswasthya.stoptb.network.CareContextValidateOtpRequest
import org.piramalswasthya.stoptb.network.CareContextValidateOtpResponse
import org.piramalswasthya.stoptb.network.GetBenHealthIdRequest
import org.piramalswasthya.stoptb.network.NetworkResult
import org.piramalswasthya.stoptb.network.SaveAbdmFacilityIdData
import org.piramalswasthya.stoptb.network.SaveAbdmFacilityIdRequest
import org.piramalswasthya.stoptb.network.SaveAbdmFacilityIdResponse
import timber.log.Timber
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject

class AbdmCareContextRepo @Inject constructor(
    private val amritApiService: AmritApiService,
    private val preferenceDao: PreferenceDao,
    private val userRepo: UserRepo
) {

    suspend fun getMappedFacility(providerServiceMapId: Int): NetworkResult<AbdmMappedFacilityData> {
        return withContext(Dispatchers.IO) {
            try {
                val response = amritApiService.getWorklocationMappedAbdmFacility(providerServiceMapId)
                val responseBody = response.body()?.string()
                when (responseBody?.let { JSONObject(it).optInt("statusCode") }) {
                    200 -> {
                        val result = gson.fromJson(responseBody, AbdmMappedFacilityResponse::class.java)
                        val facility = result?.data
                        if (facility?.abdmFacilityID.isNullOrBlank()) {
                            NetworkResult.Error(0, "ABDM facility mapping not available")
                        } else {
                            NetworkResult.Success(facility!!)
                        }
                    }

                    401, 5000, 5002 -> handleAuthRetry(responseBody) {
                        getMappedFacility(providerServiceMapId)
                    }

                    else -> NetworkResult.Error(0, responseBody ?: "Unknown Error")
                }
            } catch (e: IOException) {
                NetworkResult.NetworkError
            } catch (e: SocketTimeoutException) {
                NetworkResult.NetworkError
            } catch (e: Exception) {
                NetworkResult.Error(-4, e.message ?: "Unknown Error")
            }
        }
    }

    suspend fun saveFacilityAgainstVisit(
        visitCode: Long,
        providerServiceMapId: Int
    ): NetworkResult<String> {
        return withContext(Dispatchers.IO) {
            try {
                val facility = getResolvedFacility(providerServiceMapId)
                val response = amritApiService.saveAbdmFacilityId(
                    SaveAbdmFacilityIdRequest(
                        visitCode = visitCode,
                        abdmFacilityId = facility.first
                    )
                )
                val responseBody = response.body()?.string()
                when (responseBody?.let { JSONObject(it).optInt("statusCode") }) {
                    200 -> {
                        val result = gson.fromJson(responseBody, SaveAbdmFacilityIdResponse::class.java)
                        NetworkResult.Success(result?.data?.response ?: "ABDM Facility ID updated successfully")
                    }

                    401, 5000, 5002 -> handleAuthRetry(responseBody) {
                        saveFacilityAgainstVisit(visitCode, providerServiceMapId)
                    }

                    else -> NetworkResult.Error(0, responseBody ?: "Unknown Error")
                }
            } catch (e: IOException) {
                NetworkResult.NetworkError
            } catch (e: SocketTimeoutException) {
                NetworkResult.NetworkError
            } catch (e: Exception) {
                NetworkResult.Error(-4, e.message ?: "Unknown Error")
            }
        }
    }

    suspend fun getBeneficiaryHealthId(
        beneficiaryRegID: Long,
        beneficiaryID: Long
    ): NetworkResult<BenHealthDetails> {
        return withContext(Dispatchers.IO) {
            try {
                val response = amritApiService.getBenHealthID(
                    GetBenHealthIdRequest(
                        beneficiaryRegID = beneficiaryRegID,
                        beneficiaryID = beneficiaryID
                    )
                )
                val responseBody = response.body()?.string()
                when (responseBody?.let { JSONObject(it).optInt("statusCode") }) {
                    200 -> {
                        val array = JSONObject(responseBody)
                            .optJSONObject("data")
                            ?.optJSONArray("BenHealthDetails")
                        if (array == null || array.length() == 0) {
                            NetworkResult.Error(0, "ABHA details not found for beneficiary")
                        } else {
                            val list = gson.fromJson(array.toString(), Array<BenHealthDetails>::class.java)
                            val details = list.lastOrNull()
                            if (details == null) {
                                NetworkResult.Error(0, "ABHA details not found for beneficiary")
                            } else {
                                NetworkResult.Success(details)
                            }
                        }
                    }

                    401, 5000, 5002 -> handleAuthRetry(responseBody) {
                        getBeneficiaryHealthId(beneficiaryRegID, beneficiaryID)
                    }

                    else -> NetworkResult.Error(0, responseBody ?: "Unknown Error")
                }
            } catch (e: IOException) {
                NetworkResult.NetworkError
            } catch (e: SocketTimeoutException) {
                NetworkResult.NetworkError
            } catch (e: Exception) {
                NetworkResult.Error(-4, e.message ?: "Unknown Error")
            }
        }
    }

    suspend fun generateOtpForCareContext(
        healthID: String,
        healthIdNumber: String,
        providerServiceMapId: Int
    ): NetworkResult<CareContextGenerateOtpData> {
        return withContext(Dispatchers.IO) {
            try {
                val facility = getResolvedFacility(providerServiceMapId)
                val response = amritApiService.generateOTPForCareContext(
                    CareContextGenerateOtpRequest(
                        healthID = healthID,
                        healthIdNumber = healthIdNumber,
                        abdmFacilityId = facility.first,
                        abdmFacilityName = facility.second
                    )
                )
                val responseBody = response.body()?.string()
                when (responseBody?.let { JSONObject(it).optInt("statusCode") }) {
                    200 -> {
                        val result = gson.fromJson(responseBody, CareContextGenerateOtpResponse::class.java)
                        val data = result?.data
                        if (data?.txnId.isNullOrBlank()) {
                            NetworkResult.Error(0, "Unable to generate Care Context OTP")
                        } else {
                            NetworkResult.Success(data!!)
                        }
                    }

                    401, 5000, 5002 -> handleAuthRetry(responseBody) {
                        generateOtpForCareContext(healthID, healthIdNumber, providerServiceMapId)
                    }

                    else -> NetworkResult.Error(0, responseBody ?: "Unknown Error")
                }
            } catch (e: IOException) {
                NetworkResult.NetworkError
            } catch (e: SocketTimeoutException) {
                NetworkResult.NetworkError
            } catch (e: Exception) {
                NetworkResult.Error(-4, e.message ?: "Unknown Error")
            }
        }
    }

    suspend fun validateOtpAndCreateCareContext(
        otp: String,
        txnId: String,
        beneficiaryID: Long,
        healthID: String,
        healthIdNumber: String,
        visitCode: Long,
        visitCategory: String,
        providerServiceMapId: Int
    ): NetworkResult<CareContextValidateOtpData> {
        return withContext(Dispatchers.IO) {
            try {
                val facility = getResolvedFacility(providerServiceMapId)
                val response = amritApiService.validateOTPAndCreateCareContext(
                    CareContextValidateOtpRequest(
                        otp = otp,
                        txnId = txnId,
                        beneficiaryID = beneficiaryID,
                        healthID = healthID,
                        healthIdNumber = healthIdNumber,
                        visitCode = visitCode,
                        visitCategory = visitCategory,
                        abdmFacilityId = facility.first,
                        abdmFacilityName = facility.second
                    )
                )
                val responseBody = response.body()?.string()
                when (responseBody?.let { JSONObject(it).optInt("statusCode") }) {
                    200 -> {
                        val result = gson.fromJson(responseBody, CareContextValidateOtpResponse::class.java)
                        val data = result?.data
                        if (data?.response.isNullOrBlank()) {
                            NetworkResult.Error(0, "Care Context creation failed")
                        } else {
                            NetworkResult.Success(data!!)
                        }
                    }

                    401, 5000, 5002 -> handleAuthRetry(responseBody) {
                        validateOtpAndCreateCareContext(
                            otp = otp,
                            txnId = txnId,
                            beneficiaryID = beneficiaryID,
                            healthID = healthID,
                            healthIdNumber = healthIdNumber,
                            visitCode = visitCode,
                            visitCategory = visitCategory,
                            providerServiceMapId = providerServiceMapId
                        )
                    }

                    else -> NetworkResult.Error(0, responseBody ?: "Unknown Error")
                }
            } catch (e: IOException) {
                NetworkResult.NetworkError
            } catch (e: SocketTimeoutException) {
                NetworkResult.NetworkError
            } catch (e: Exception) {
                NetworkResult.Error(-4, e.message ?: "Unknown Error")
            }
        }
    }

    private suspend fun getResolvedFacility(providerServiceMapId: Int): Pair<String, String> {
        return when (val result = getMappedFacility(providerServiceMapId)) {
            is NetworkResult.Success -> {
                val id = result.data.abdmFacilityID?.takeIf { it.isNotBlank() } ?: DEFAULT_ABDM_FACILITY_ID
                val name = result.data.abdmFacilityName?.takeIf { it.isNotBlank() } ?: DEFAULT_ABDM_FACILITY_NAME
                id to name
            }

            else -> {
                Timber.w("Using fallback ABDM facility for providerServiceMapId=$providerServiceMapId")
                DEFAULT_ABDM_FACILITY_ID to DEFAULT_ABDM_FACILITY_NAME
            }
        }
    }

    private suspend fun <T : Any> handleAuthRetry(
        responseBody: String?,
        retry: suspend () -> NetworkResult<T>
    ): NetworkResult<T> {
        val errorMessage = responseBody?.let { JSONObject(it).optString("errorMessage") }.orEmpty()
        return if (errorMessage.contentEquals("Invalid login key or session is expired")) {
            val user = preferenceDao.getLoggedInUser()
                ?: return NetworkResult.Error(0, "No user logged in")
            userRepo.refreshTokenTmc(user.userName, user.password)
            retry()
        } else {
            NetworkResult.Error(0, errorMessage.ifBlank { responseBody ?: "Unknown Error" })
        }
    }

    companion object {
        private const val DEFAULT_ABDM_FACILITY_ID = "Test_HIP_Amrit"
        private const val DEFAULT_ABDM_FACILITY_NAME = "Test HIP Amrit"
        private val gson = Gson()
    }
}
