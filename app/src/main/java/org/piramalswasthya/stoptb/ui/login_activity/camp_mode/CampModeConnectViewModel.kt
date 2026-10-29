package org.piramalswasthya.stoptb.ui.login_activity.camp_mode

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.utils.CampHubUrlValidator
import org.piramalswasthya.stoptb.work.WorkerUtils
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

@HiltViewModel
class CampModeConnectViewModel @Inject constructor(
    private val pref: PreferenceDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "CampHub"
    }

    enum class CampHubStatus {
        IDLE,
        CHECKING,
        CONNECTED,
        NOT_CONNECTED
    }

    private val _campHubStatus = MutableLiveData(CampHubStatus.IDLE)
    val campHubStatus: LiveData<CampHubStatus>
        get() = _campHubStatus

    fun getCampHubUrl(): String {
        val stored = pref.getStoredCampHubUrl()?.trim().orEmpty()
        if (stored.isNotEmpty()) return stored
        return pref.configuredCampHubUrl()
    }

    fun connectToCampHub(url: String) {
        // Reject anything that is not a local-network address BEFORE storing it. The stored URL
        // re-points every authenticated request (see CampHubUrlValidator), and it used to be
        // persisted first and validated never - so a scanned QR code pointing at a public host
        // took effect immediately and survived a failed health check.
        val normalizedUrl = CampHubUrlValidator.normalizeOrNull(url, extraAllowedHost = configuredHubHost())
        if (normalizedUrl == null) {
            Log.w(TAG, "Rejected camp hub URL: not a local-network http(s) address")
            _campHubStatus.value = CampHubStatus.NOT_CONNECTED
            return
        }
        Log.d(TAG, "Connect clicked. normalizedUrl=$normalizedUrl")
        pref.setCampHubUrl(normalizedUrl)
        pref.setCampModeEnabled(false)
        pref.setCampHubConnected(false)
        _campHubStatus.value = CampHubStatus.CHECKING

        viewModelScope.launch {
            val connected = pingCampHub(normalizedUrl)
            Log.d(TAG, "Connect result. connected=$connected, normalizedUrl=$normalizedUrl")
            pref.setCampModeEnabled(connected)
            pref.setCampHubConnected(connected)
            if (connected) {
                WorkerUtils.triggerDiagnosticResultPollWorker(context)
            }
            _campHubStatus.value =
                if (connected) CampHubStatus.CONNECTED else CampHubStatus.NOT_CONNECTED
        }
    }

    /** Host of the URL baked into this build - always acceptable, even when it is public. */
    private fun configuredHubHost(): String? =
        pref.configuredCampHubUrl().takeIf { it.isNotBlank() }
            ?.let { runCatching { java.net.URI(it).host }.getOrNull() }

    private suspend fun pingCampHub(rawUrl: String): Boolean = withContext(Dispatchers.IO) {
        val baseUrl = rawUrl.trim().trimEnd('/')
        val healthUrl = "$baseUrl/common-api/health"
        var connection: HttpURLConnection? = null
        runCatching {
            Log.d(TAG, "Health check started. url=$healthUrl")
            connection = URL(healthUrl).openConnection() as HttpURLConnection
            connection?.connectTimeout = 3000
            connection?.readTimeout = 3000
            connection?.requestMethod = "GET"
            connection?.useCaches = false
            val responseCode = connection?.responseCode ?: return@runCatching false
            Log.d(TAG, "Health check response. url=$healthUrl, code=$responseCode")
            responseCode in 200..299
        }.onFailure { error ->
            Log.e(TAG, "Health check failed. url=$healthUrl, error=${error.javaClass.simpleName}: ${error.message}", error)
        }.also {
            connection?.disconnect()
        }.getOrDefault(false)
    }
}
