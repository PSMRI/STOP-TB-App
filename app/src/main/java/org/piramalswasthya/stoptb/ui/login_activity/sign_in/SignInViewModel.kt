package org.piramalswasthya.stoptb.ui.login_activity.sign_in

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.piramalswasthya.stoptb.database.room.InAppDb
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.helpers.Konstants
import org.piramalswasthya.stoptb.helpers.NetworkResponse
import org.piramalswasthya.stoptb.model.User
import org.piramalswasthya.stoptb.repositories.UserRepo
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val userRepo: UserRepo,
    private val database: InAppDb,
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

    private val _state: MutableLiveData<NetworkResponse<User?>> =
        MutableLiveData(NetworkResponse.Idle())
    val state: LiveData<NetworkResponse<User?>>
        get() = _state

    private val _loggedInUser: MutableLiveData<User?> =
        MutableLiveData(null)
    val loggedInUser: LiveData<User?>
        get() = _loggedInUser

    private val _logoutComplete: MutableLiveData<Boolean?> =
        MutableLiveData(null)
    val logoutComplete: LiveData<Boolean?>
        get() = _logoutComplete

    private var _unprocessedRecordsCount: MutableLiveData<Int> = MutableLiveData(0)
    val unprocessedRecordsCount: LiveData<Int>
        get() = _unprocessedRecordsCount

    private val _campHubStatus: MutableLiveData<CampHubStatus> =
        MutableLiveData(CampHubStatus.IDLE)
    val campHubStatus: LiveData<CampHubStatus>
        get() = _campHubStatus

    init {
        _loggedInUser.value = pref.getLoggedInUser()
        viewModelScope.launch {
            launch {
                userRepo.unProcessedRecordCount.collect { value ->
                    _unprocessedRecordsCount.value =
                        value.filter { it.syncState != SyncState.SYNCED }.sumOf { it.count }
                }
            }
        }
    }

    fun loginInClicked() {
        _state.value = NetworkResponse.Loading()
    }

    fun isCampModeEnabled(): Boolean = pref.isCampModeEnabled()

    fun isCampHubConnected(): Boolean = pref.isCampHubConnected()

    fun getCampHubUrl(): String = pref.getCampHubUrl()

    fun updateCampHubUrl(url: String) {
        val normalizedUrl = normalizeCampHubUrl(url)
        if (normalizedUrl == pref.getCampHubUrl()) return

        Log.d(TAG, "Login camp URL changed. inputUrl=$url, normalizedUrl=$normalizedUrl")
        pref.setCampHubUrl(normalizedUrl)
        pref.setCampHubConnected(false)
        if (pref.isCampModeEnabled()) {
            _campHubStatus.value = CampHubStatus.NOT_CONNECTED
        }
    }

    fun setCampModeEnabled(enabled: Boolean) {
        Log.d(TAG, "Camp mode toggle changed. enabled=$enabled")
        pref.setCampModeEnabled(enabled)
        if (enabled) {
            checkCampHubConnection()
        } else {
            pref.setCampHubConnected(false)
            _campHubStatus.value = CampHubStatus.IDLE
        }
    }

    fun checkCampHubConnection() {
        if (!pref.isCampModeEnabled()) {
            Log.d(TAG, "Camp hub check skipped. Camp mode disabled.")
            pref.setCampHubConnected(false)
            _campHubStatus.value = CampHubStatus.IDLE
            return
        }

        Log.d(TAG, "Camp hub check started from login. url=${pref.getCampHubUrl()}")
        _campHubStatus.value = CampHubStatus.CHECKING
        viewModelScope.launch {
            val connected = pingCampHub(pref.getCampHubUrl())
            Log.d(TAG, "Camp hub check finished from login. connected=$connected, url=${pref.getCampHubUrl()}")
            if (!pref.isCampModeEnabled()) {
                pref.setCampHubConnected(false)
                _campHubStatus.value = CampHubStatus.IDLE
                return@launch
            }
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
            Log.d(TAG, "Login health check started. url=$healthUrl")
            connection = URL(healthUrl).openConnection() as HttpURLConnection
            connection?.connectTimeout = 3000
            connection?.readTimeout = 3000
            connection?.requestMethod = "GET"
            connection?.useCaches = false
            val responseCode = connection?.responseCode ?: return@runCatching false
            Log.d(TAG, "Login health check response. url=$healthUrl, code=$responseCode")
            responseCode in 200..299
        }.onFailure { error ->
            Log.e(TAG, "Login health check failed. url=$healthUrl, error=${error.javaClass.simpleName}: ${error.message}", error)
        }.also {
            connection?.disconnect()
        }.getOrDefault(false)
    }

    /**
     * function to remove data of currently logged in uset
     * clear all in app db data
     * remove shared preferences data and reset last synced time
     * set logoutComplete live data to true to be observed in fragment
     */
    fun logout() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                database.clearAllTables()
            }
            pref.deleteForLogout()
            pref.setLastSyncedTimeStamp(Konstants.defaultTimeStamp)
            _loggedInUser.value = null
            Thread.sleep(2000)
            _logoutComplete.value = true
        }
    }

    fun getLoggedInUser(): User? {
        return pref.getLoggedInUser()
    }

    /**
     * authenticate a user with username and password
     */
    fun authUser(username: String, password: String) {
        viewModelScope.launch {
            try {
                _state.value = userRepo.authenticateUser(username, password)
            } catch (e: Exception) {
                _state.value =
                    NetworkResponse.Error("Network Call failed.\nUnknown error : ${e.message} stack-trace : ${e.stackTrace}")
                pref.deleteLoginCred()

            }
        }
    }

    fun fetchRememberedUserName(): String? =
        pref.getRememberedUserName()

    fun fetchRememberedPassword(): String? =
        pref.getRememberedPassword()

    fun fetchRememberedState(): String? =
        pref.getRememberedState()


    fun rememberUser(username: String, password: String) {
        pref.registerLoginCred(username, password)
    }

    fun forgetUser() {
        pref.deleteLoginCred()
    }

    fun updateState(state: NetworkResponse<User?>) {
        _state.value = state
    }

    /**
     * Used ONLY for legacy JWT migration.
     * No LiveData, no UI side effects.
     */
    suspend fun authenticateForMigration(
        username: String,
        password: String
    ): NetworkResponse<User?> {
        return userRepo.authenticateUser(username, password)
    }

}
