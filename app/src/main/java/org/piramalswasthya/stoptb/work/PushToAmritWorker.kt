package org.piramalswasthya.stoptb.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.repositories.BenRepo
import org.piramalswasthya.stoptb.repositories.VitalRepo
import timber.log.Timber

@HiltWorker
class PushToAmritWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val benRepo: BenRepo,
    private val vitalRepo: VitalRepo,
    override val preferenceDao: PreferenceDao,
) : BasePushWorker(appContext, params) {
    companion object {
        const val name = "PushToAmritWorker"
    }

    override val workerName = name

    override suspend fun doSyncWork(): Result {
        // Network failures throw and are retried by BasePushWorker. A false result
        // means the server rejected the beneficiary batch: those records stay
        // UNSYNCED for the next cycle, and the worker still succeeds so the TB,
        // ABHA and counselling workers that follow it in the chain are not failed.
        if (!benRepo.processNewBen()) {
            Timber.e("[$workerName] Beneficiary batch rejected by server; records stay UNSYNCED")
        }
        vitalRepo.pushUnSyncedVitals()
        Timber.d("Worker completed")
        return Result.success()
    }
}
