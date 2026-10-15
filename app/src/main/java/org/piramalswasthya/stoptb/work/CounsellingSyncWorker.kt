package org.piramalswasthya.stoptb.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.piramalswasthya.stoptb.repositories.dynamicRepo.ICounsellingRepository
import java.util.concurrent.TimeUnit

@HiltWorker
class CounsellingSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val counsellingRepository: ICounsellingRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val success = counsellingRepository.syncUnsyncedRecords()
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
        private const val SYNC_WORK_NAME = "counselling_sync_work"

        fun scheduleSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = OneTimeWorkRequestBuilder<CounsellingSyncWorker>()
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
