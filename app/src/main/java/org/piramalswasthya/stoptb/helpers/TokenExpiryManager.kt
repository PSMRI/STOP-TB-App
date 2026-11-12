package org.piramalswasthya.stoptb.helpers

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.piramalswasthya.stoptb.work.WorkerUtils
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenExpiryManager @Inject constructor(
    @ApplicationContext private val appContext: Context
) {

    companion object {
        private const val MAX_CONSECUTIVE_FAILURES = 1
    }

    private val consecutiveFailures = AtomicInteger(0)

    // State is retained while the screen is stopped; a one-shot event can be missed by
    // background sync and leave the user looking at pending rows with no explanation.
    private val _loginRequired = MutableStateFlow(false)
    val loginRequired: StateFlow<Boolean> = _loginRequired

    fun onRefreshFailed() {
        val count = consecutiveFailures.incrementAndGet()
        Timber.w("Token refresh failed. Consecutive failures: $count")
        if (count >= MAX_CONSECUTIVE_FAILURES) {
            Timber.w("Authentication failed. Sync paused until login.")
            consecutiveFailures.set(0)
            _loginRequired.value = true
            WorkerUtils.cancelAllWork(appContext)
        }
    }

    fun onRefreshSuccess() {
        consecutiveFailures.set(0)
        _loginRequired.value = false
    }
}
