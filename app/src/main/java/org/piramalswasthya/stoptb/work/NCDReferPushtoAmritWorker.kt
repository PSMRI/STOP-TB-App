package org.piramalswasthya.stoptb.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.repositories.NcdReferalRepo

@HiltWorker
class NCDReferPushtoAmritWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val referalRepo: NcdReferalRepo,
    override val preferenceDao: PreferenceDao,
) : BasePushWorker(appContext, params) {
    companion object {
        const val name = "PushToAmritWorker"
    }

    override val workerName = "NCDReferPushtoAmritWorker"

    override suspend fun doSyncWork(): Result {
        referalRepo.pushAndUpdateNCDReferRecord()

        return Result.success()
    }
}
