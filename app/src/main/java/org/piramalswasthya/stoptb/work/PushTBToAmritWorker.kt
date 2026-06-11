package org.piramalswasthya.stoptb.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.repositories.TBRepo
import org.piramalswasthya.stoptb.repositories.VitalRepo
import timber.log.Timber

@HiltWorker
class PushTBToAmritWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val tbRepo: TBRepo,
    private val vitalRepo: VitalRepo,
    override val preferenceDao: PreferenceDao,
) : BasePushWorker(appContext, params) {
    companion object {
        const val name = "PushTBToAmritWorker"
    }

    override val workerName = name

    override suspend fun doSyncWork(): Result {
        val tbWorkerResult = tbRepo.pushUnSyncedRecords()
        val vitalWorkerResult = vitalRepo.pushUnSyncedVitals()
        val workerResult = tbWorkerResult && vitalWorkerResult
        return if (workerResult) {
            Timber.d("Worker completed")
            Result.success()
        } else {
            Timber.e("Worker Failed as usual!")
            Result.failure(workDataOf(KEY_WORKER_NAME to workerName, KEY_ERROR to "Sync operation returned false"))
        }
    }
}
