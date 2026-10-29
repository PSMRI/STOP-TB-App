package org.piramalswasthya.stoptb.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.repositories.contactTracing.IContactTracingRepository
import timber.log.Timber

// Pushes contact tracing, contact follow-up and TPT follow-up responses as part of the
// push chain, so they are retried with everything else (gate, heartbeat, Retry now)
// instead of only by the one-off ContactTracingSyncWorker enqueued when a form is saved.
@HiltWorker
class PushContactTracingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val contactTracingRepository: IContactTracingRepository,
    override val preferenceDao: PreferenceDao,
) : BasePushWorker(appContext, params) {
    companion object {
        const val name = "PushContactTracingWorker"
    }

    override val workerName = name

    override suspend fun doSyncWork(): Result {
        return if (contactTracingRepository.syncUnsyncedResponses()) {
            Timber.d("Worker completed")
            Result.success()
        } else {
            // Failed responses stay unsynced; retry with backoff. Nothing in the
            // chain depends on this worker.
            Timber.w("[$workerName] Some contact tracing responses were not synced, will retry")
            Result.retry()
        }
    }
}
