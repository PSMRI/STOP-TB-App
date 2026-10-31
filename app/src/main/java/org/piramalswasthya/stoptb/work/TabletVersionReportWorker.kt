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
import timber.log.Timber

// Sends this tablet's app version to the camp laptop (see TabletVersionReporter). Runs on app
// start and camp hub connect (forced) and from SyncHeartbeatWorker (at most hourly). A failed
// report is not retried; the next trigger sends it. Always succeeds, whatever happens.
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
        try {
            val outcome = withContext(Dispatchers.IO) {
                TabletVersionReporter.report(applicationContext, preferenceDao, inputData.getBoolean(FORCE, false))
            }
            // Logged from the worker so SyncLogTree keeps it (its tag contains "Worker"): every
            // attempt is in the Sync Dashboard log and the exported log file, also in release builds.
            when (outcome.kind) {
                TabletVersionReporter.Outcome.Kind.SENT -> Timber.i("Tablet version report: ${outcome.message}")
                TabletVersionReporter.Outcome.Kind.SKIPPED -> Timber.i("Tablet version report skipped: ${outcome.message}")
                TabletVersionReporter.Outcome.Kind.FAILED -> Timber.w("Tablet version report failed: ${outcome.message}")
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.w("Tablet version report failed: ${e.javaClass.simpleName}: ${e.message}")
        }
        return Result.success()
    }
}
