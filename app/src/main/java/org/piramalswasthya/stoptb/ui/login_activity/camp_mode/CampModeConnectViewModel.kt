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
import org.piramalswasthya.stoptb.work.WorkerUtils
import org.piramalswasthya.stoptb.utils.sync.CampHubReachability
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
            if (connected) {
                WorkerUtils.triggerDiagnosticResultPollWorker(context)
            }
            _campHubStatus.value =
                if (connected) CampHubStatus.CONNECTED else CampHubStatus.NOT_CONNECTED
        }
    }

    private fun normalizeCampHubUrl(url: String): String {
        val trimmedUrl = url.trim()
        val resolvedUrl = when {
            trimmedUrl.isBlank() -> ""
            trimmedUrl.contains("://") -> trimmedUrl
            else -> "http://$trimmedUrl"
        }
        return resolvedUrl.trimEnd('/').plus("/")
    }

    private suspend fun pingCampHub(rawUrl: String): Boolean = withContext(Dispatchers.IO) {
        CampHubReachability.isReachable(rawUrl)
    }
}
