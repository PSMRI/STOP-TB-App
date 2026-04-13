package org.piramalswasthya.stoptb.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import timber.log.Timber

@HiltWorker
class PushToD2DWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    override val preferenceDao: PreferenceDao,
) : BasePushWorker(appContext, params) {

    companion object {
        const val name = "PushToD2DWorker"
        val constraint = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }

    override val workerName = "PushToD2DWorker"

    override suspend fun doSyncWork(): Result {
        Timber.d("PushToD2DWorker: no-op")
        return Result.success()
    }
}