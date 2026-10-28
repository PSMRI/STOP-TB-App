package org.piramalswasthya.stoptb.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

// Periodic safety net so unsynced records are retried even when nobody saves a
// form or opens the app. It only goes through the normal push gate; the push
// chain does nothing when there is nothing pending, and the gate skips when the
// user is logged out or camp sync is not active.
@HiltWorker
class SyncHeartbeatWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val name = "SyncHeartbeatWorker"
    }

    override suspend fun doWork(): Result {
        Timber.d("SyncHeartbeat: triggering push gate")
        WorkerUtils.triggerAmritPushWorker(applicationContext)
        return Result.success()
    }
}
