package org.piramalswasthya.stoptb.work

import androidx.work.CoroutineWorker
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException

// Promotes the worker to a foreground service so aggressive-battery OEMs don't
// kill it mid-sync. On Android 12+ the OS refuses foreground service starts from
// the background (ForegroundServiceStartNotAllowedException), and a missing
// notification channel or permission can also throw. In those cases the sync
// still runs as normal background work instead of failing the worker.
suspend fun CoroutineWorker.trySetForeground() {
    try {
        trySetForeground()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "[${this::class.simpleName}] Could not start foreground service, continuing in background")
    }
}
