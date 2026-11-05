package org.piramalswasthya.stoptb.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.repositories.dynamicRepo.ICounsellingRepository
import timber.log.Timber

// Pushes counselling responses as part of the push chain, alongside TB and ABHA.
// This used to run inside PushToAmritWorker, where one rejected counselling
// record failed beneficiary registration and, through it, TB and ABHA push.
@HiltWorker
class PushCounsellingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val counsellingRepository: ICounsellingRepository,
    override val preferenceDao: PreferenceDao,
) : BasePushWorker(appContext, params) {
    companion object {
        const val name = "PushCounsellingWorker"
    }

    override val workerName = name

    override suspend fun doSyncWork(): Result {
        return if (counsellingRepository.syncUnsyncedRecords()) {
            Timber.d("Worker completed")
            Result.success()
        } else {
            // Failed responses stay UNSYNCED; retry with backoff. Nothing in the
            // chain depends on this worker, so a failure here blocks nothing else.
            Timber.w("[$workerName] Some counselling responses were not synced, will retry")
            Result.retry()
        }
    }
}
