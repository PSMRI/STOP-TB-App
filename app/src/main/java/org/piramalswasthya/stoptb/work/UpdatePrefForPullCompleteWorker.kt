package org.piramalswasthya.stoptb.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import timber.log.Timber
import java.util.concurrent.TimeUnit

@HiltWorker
class UpdatePrefForPullCompleteWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val preferenceDao: PreferenceDao,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val name = "setPullCompleteWorker"

    }

    override suspend fun getForegroundInfo(): ForegroundInfo = createForegroundInfo()

    override suspend fun doWork(): Result {
        setForeground(getForegroundInfo())

        preferenceDao.isFullPullComplete = true
        val triggerSource = inputData.getString(WorkerUtils.pullTriggerSourceKey) ?: "DIRECT"
        val startedAt = inputData.getLong(WorkerUtils.pullStartedAtKey, SystemClock.elapsedRealtime())
        val durationSeconds = TimeUnit.MILLISECONDS.toSeconds(SystemClock.elapsedRealtime() - startedAt)
        Timber.i("Pull [$triggerSource] completed in $durationSeconds seconds")
        return Result.success()
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val notification = NotificationCompat.Builder(
            applicationContext,
            applicationContext.getString(R.string.notification_sync_channel_id)
        ).setContentTitle("Data Sync").setContentText("Completing sync")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setProgress(100, 0, true).setOngoing(true).build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(1003, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else ForegroundInfo(1003, notification)
    }
}
