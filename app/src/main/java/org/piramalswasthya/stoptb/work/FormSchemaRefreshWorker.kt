package org.piramalswasthya.stoptb.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.piramalswasthya.stoptb.repositories.dynamicRepo.ICounsellingRepository
import java.util.concurrent.TimeUnit

/**
 * Pulls every dynamic form schema (getAllForms) after login and overwrites the stored copy, so backend
 * form changes reach the device on the next login instead of only after a reinstall / clear data.
 */
@HiltWorker
class FormSchemaRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val counsellingRepository: ICounsellingRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val success = counsellingRepository.downloadAndStoreAllForms(forceUpdate = true)
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
        private const val REFRESH_WORK_NAME = "form_schema_refresh_work"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val refreshRequest = OneTimeWorkRequestBuilder<FormSchemaRefreshWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                REFRESH_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                refreshRequest
            )
        }
    }
}
