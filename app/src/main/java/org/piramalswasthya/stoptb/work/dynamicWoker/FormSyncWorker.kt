package org.piramalswasthya.stoptb.work.dynamicWoker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.repositories.dynamicRepo.FormRepository
import timber.log.Timber

@HiltWorker
class FormSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    override val preferenceDao: PreferenceDao,
    private val repository: FormRepository
) : BaseDynamicWorker(context, workerParams) {

    override val workerName = "FormSyncWorker"

    override suspend fun doSyncWork(): Result {
        val unsyncedForms = repository.getUnsyncedForms()
        for (form in unsyncedForms) {
            if ((form.benId ?: -1) < 0) continue
            try {
                val success = repository.syncFormToServer(form)
                if (success) repository.markFormAsSynced(form.id)
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync form ${form.id}")
            }
        }
        return Result.success()
    }

    companion object {
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<FormSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }
}