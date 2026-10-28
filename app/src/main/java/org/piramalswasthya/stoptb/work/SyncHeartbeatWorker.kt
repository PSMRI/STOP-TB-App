package org.piramalswasthya.stoptb.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.piramalswasthya.stoptb.database.shared_preferences.SyncRetryStore
import org.piramalswasthya.stoptb.helpers.AnalyticsHelper
import timber.log.Timber

// Periodic safety net so unsynced records are retried even when nobody saves a
// form or opens the app. It only goes through the normal push gate; the push
// chain does nothing when there is nothing pending, and the gate skips when the
// user is logged out or camp sync is not active.
//
// It also reports records the server has not accepted for more than a day to
// Crashlytics (entity, record ID and server error only; no personal data), at
// most once a day per record, so support sees them without collecting logs.
@HiltWorker
class SyncHeartbeatWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncRetryStore: SyncRetryStore,
    private val analyticsHelper: AnalyticsHelper,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val name = "SyncHeartbeatWorker"
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
        private const val MAX_ERROR_LENGTH = 120
    }

    class LongUnsyncedRecordException(message: String) : Exception(message)

    override suspend fun doWork(): Result {
        reportLongUnsyncedRecords()
        Timber.d("SyncHeartbeat: triggering push gate")
        WorkerUtils.triggerAmritPushWorker(applicationContext)
        return Result.success()
    }

    private fun reportLongUnsyncedRecords(now: Long = System.currentTimeMillis()) {
        syncRetryStore.entries()
            .filter { now - it.firstFailedAt >= DAY_MILLIS && now - it.lastReportedAt >= DAY_MILLIS }
            .forEach {
                val hours = (now - it.firstFailedAt) / (60 * 60 * 1000L)
                analyticsHelper.recordException(
                    LongUnsyncedRecordException(
                        "${it.entity} ${it.id} not accepted by server for ${hours}h, " +
                                "${it.attempts} attempts, last error: ${it.lastError.take(MAX_ERROR_LENGTH)}"
                    )
                )
                syncRetryStore.markReported(it.entity, it.id, now)
            }
    }
}
