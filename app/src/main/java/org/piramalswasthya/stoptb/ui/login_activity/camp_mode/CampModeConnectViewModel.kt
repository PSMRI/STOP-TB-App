package org.piramalswasthya.stoptb.ui.login_activity.camp_mode

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

@HiltViewModel
class CampModeConnectViewModel @Inject constructor(
    private val pref: PreferenceDao
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

    fun getCampHubUrl(): String = pref.getCampHubUrl()

    fun connectToCampHub(url: String) {
        val normalizedUrl = normalizeCampHubUrl(url)
        Log.d(TAG, "Connect clicked. inputUrl=$url, normalizedUrl=$normalizedUrl")
        pref.setCampHubUrl(normalizedUrl)
        pref.setCampModeEnabled(false)
        pref.setCampHubConnected(false)
        _campHubStatus.value = CampHubStatus.CHECKING

        viewModelScope.launch {
            val connected = pingCampHub(normalizedUrl)
            Log.d(TAG, "Connect result. connected=$connected, normalizedUrl=$normalizedUrl")
            pref.setCampModeEnabled(connected)
            pref.setCampHubConnected(connected)
            _campHubStatus.value =
                if (connected) CampHubStatus.CONNECTED else CampHubStatus.NOT_CONNECTED
        }
    }

    private fun normalizeCampHubUrl(url: String): String {
        val trimmedUrl = url.trim()
        val resolvedUrl = when {
            trimmedUrl.isBlank() -> "http://192.168.137.1:8080"
            trimmedUrl.contains("://") -> trimmedUrl
            else -> "http://$trimmedUrl"
        }
        return resolvedUrl.trimEnd('/').plus("/")
    }

    private suspend fun pingCampHub(rawUrl: String): Boolean = withContext(Dispatchers.IO) {
        val baseUrl = rawUrl.trim().trimEnd('/')
        val healthUrl = "$baseUrl/health"
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
