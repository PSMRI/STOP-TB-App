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
    @Volatile private var lastRefreshBothIntegrated = false

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
        // A "not integrated" answer is kept only briefly, so a device that reconnects is seen
        // within a couple of minutes.
        val keepFor = if (lastRefreshBothIntegrated) maxAgeMs else minOf(maxAgeMs, NOT_INTEGRATED_MAX_AGE_MS)
        if (maxAgeMs > 0L && lastRefreshAt != 0L && hubUrl == lastRefreshHubUrl && now - lastRefreshAt < keepFor) {
            return
        }
        val xrayHealth = getVendorHealth("XRAY_CHEST")
        val truenatHealth = getVendorHealth("MTB")
        // Save a flag only from a real answer: a failed check stored as "not integrated" made the
        // poll treat every order as not pending and stop rescheduling until something re-triggered it.
        if (xrayHealth is NetworkResponse.Success) preferenceDao.setXrayIntegrated(isIntegrated(xrayHealth))
        if (truenatHealth is NetworkResponse.Success) preferenceDao.setTruenatIntegrated(isIntegrated(truenatHealth))
        // Cache a real answer from both checks: "both integrated" for [maxAgeMs], anything else
        // for NOT_INTEGRATED_MAX_AGE_MS (see the check above). Caching only "both integrated" meant
        // a hub with one device never cached, so every poll cycle and every order re-asked. A
        // timeout or HTTP error is not cached and is checked again next time.
        if (xrayHealth is NetworkResponse.Success && truenatHealth is NetworkResponse.Success) {
            lastRefreshAt = now
            lastRefreshHubUrl = hubUrl
            lastRefreshBothIntegrated = isIntegrated(xrayHealth) && isIntegrated(truenatHealth)
        } else {
            lastRefreshAt = 0L
            lastRefreshHubUrl = null
            lastRefreshBothIntegrated = false
        }
    }

    private companion object {
        const val NOT_INTEGRATED_MAX_AGE_MS = 2 * 60_000L
    }

    fun isXrayIntegrated(): Boolean {
        return preferenceDao.getXrayIntegrated()
    }
    /** A camp hub that creates the referred orders itself (from the screening and from an X-ray
     *  result that refers for TrueNat), so this tablet must not push them too. */
    fun hubCreatesOrders(): Boolean =
        preferenceDao.isCampModeEnabled() && preferenceDao.hubCreatesReferralOrders()

    fun isTruenatIntegrated(): Boolean {
        return preferenceDao.getTruenatIntegrated()
    }
}
