package org.piramalswasthya.stoptb.repositories.tb

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.network.AmritApiService
import org.piramalswasthya.stoptb.helpers.NetworkResponse
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Camp-hub diagnostic device integration (X-ray / TrueNat vendor health), moved out of TBRepo
 * unchanged. The integration flags it stores decide which order slots the result poll treats
 * as pending.
 */
@Singleton
class DeviceIntegrationRepo @Inject constructor(
    private val preferenceDao: PreferenceDao,
    private val tmcNetworkApiService: AmritApiService,
    @ApplicationContext private val context: Context
) {

    suspend fun getVendorHealth(orderType: String): NetworkResponse<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (!preferenceDao.isCampModeEnabled()) {
                    return@withContext NetworkResponse.Error(context.getString(R.string.error_camp_mode_disabled))
                }
                if (!preferenceDao.isCampHubConnected()) {
                    return@withContext NetworkResponse.Error(context.getString(R.string.error_camp_hub_disconnected))
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
                NetworkResponse.Error(context.getString(R.string.error_http_code, statusCode))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // cancellation must stop the poll, not read as a failed health check
            } catch (e: Exception) {
                Timber.e(e, "getVendorHealth failed for $orderType")
                NetworkResponse.Error(e.message ?: "Unknown error")
            }
        }
    }

    suspend fun checkDeviceIntegration(orderType: String): Boolean =
        isIntegrated(getVendorHealth(orderType))

    private fun isIntegrated(health: NetworkResponse<String>): Boolean {
        if (health is NetworkResponse.Success) {
            val dataStr = health.data
            return dataStr?.contains("isDeviceIntegrated: true") == true &&
                    dataStr?.contains("isConnected: true") == true
        }
        return false
    }

    @Volatile private var lastRefreshAt = 0L
    @Volatile private var lastRefreshHubUrl: String? = null

    /**
     * Refreshes the X-ray / TrueNat integration flags (2 vendor/health calls; the server pings the
     * vendor synchronously for each). [maxAgeMs] > 0 skips the calls if the flags were refreshed
     * that recently for the same hub - the result poll passes 10 min instead of calling it every
     * 60 s cycle. The first call in a process and any hub change always refresh.
     */
    suspend fun refreshDeviceIntegrationConfig(maxAgeMs: Long = 0L) {
        if (!preferenceDao.isCampModeEnabled() || !preferenceDao.isCampHubConnected()) {
            Timber.d("Skipping refreshDeviceIntegrationConfig: Camp Mode is disabled or Camp Hub is disconnected")
            return
        }
        val hubUrl = preferenceDao.getCampHubUrl()
        val now = android.os.SystemClock.elapsedRealtime()
        if (maxAgeMs > 0L && lastRefreshAt != 0L && hubUrl == lastRefreshHubUrl && now - lastRefreshAt < maxAgeMs) {
            return
        }
        val xrayHealth = getVendorHealth("XRAY_CHEST")
        val truenatHealth = getVendorHealth("MTB")
        // Save a flag only from a real answer: a failed check stored as "not integrated" made the
        // poll treat every order as not pending and stop rescheduling until something re-triggered it.
        if (xrayHealth is NetworkResponse.Success) preferenceDao.setXrayIntegrated(isIntegrated(xrayHealth))
        if (truenatHealth is NetworkResponse.Success) preferenceDao.setTruenatIntegrated(isIntegrated(truenatHealth))
        // Cache only a real answer from the hub that both devices are integrated. A timeout or
        // HTTP error, or a device reported disconnected, is checked again on the next poll cycle:
        // caching "not integrated" for the whole window let a triggered poll stop at once on a
        // stale answer, after the device had reconnected.
        if (xrayHealth is NetworkResponse.Success && truenatHealth is NetworkResponse.Success &&
            isIntegrated(xrayHealth) && isIntegrated(truenatHealth)
        ) {
            lastRefreshAt = now
            lastRefreshHubUrl = hubUrl
        }
    }

    fun isXrayIntegrated(): Boolean {
        return preferenceDao.getXrayIntegrated()
    }
    fun isTruenatIntegrated(): Boolean {
        return preferenceDao.getTruenatIntegrated()
    }
}
