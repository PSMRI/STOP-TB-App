package org.piramalswasthya.stoptb.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.utils.sync.TabletVersionReporter

// Sends this tablet's app version to the camp laptop (see TabletVersionReporter). Runs on app
// start and camp hub connect (forced) and from SyncHeartbeatWorker (at most hourly). A failed
// report is not retried; the next trigger sends it.
@HiltWorker
class TabletVersionReportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val preferenceDao: PreferenceDao,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val name = "TabletVersionReportWorker"
        const val FORCE = "force"
    }

    override suspend fun doWork(): Result {
        withContext(Dispatchers.IO) {
            TabletVersionReporter.report(applicationContext, preferenceDao, inputData.getBoolean(FORCE, false))
        }
        return Result.success()
    }
}
