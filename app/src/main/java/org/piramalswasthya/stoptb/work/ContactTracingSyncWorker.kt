package org.piramalswasthya.stoptb.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.piramalswasthya.stoptb.repositories.contactTracing.IContactTracingRepository
import java.util.concurrent.TimeUnit

@HiltWorker
class ContactTracingSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val contactTracingRepository: IContactTracingRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // syncUnsyncedResponses() is currently a stub: no submit/complete endpoints exist yet
        // for Contact Tracing on the backend, so this always succeeds without pushing anything.
        val success = contactTracingRepository.syncUnsyncedResponses()
        return if (success) {
            Result.success()
        } else {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        private const val SYNC_WORK_NAME = "contact_tracing_sync_work"

        fun scheduleSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = OneTimeWorkRequestBuilder<ContactTracingSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                SYNC_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                syncRequest
            )
        }
    }
}
