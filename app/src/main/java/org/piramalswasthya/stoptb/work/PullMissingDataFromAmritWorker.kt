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
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.helpers.Konstants
import org.piramalswasthya.stoptb.repositories.BenRepo
import timber.log.Timber
import java.util.concurrent.TimeUnit

@HiltWorker
class PullMissingDataFromAmritWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val benRepo: BenRepo,
    private val preferenceDao: PreferenceDao
) : CoroutineWorker(appContext, params) {

    companion object {
        const val NAME = "PullMissingDataFromAmritWorker"
        const val PROGRESS = "Progress"
        const val NUM_PAGES = "Total Pages"
        const val MAX_RETRIES = 3
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = createForegroundInfo("Syncing missing data...")

    override suspend fun doWork(): Result {
        setForeground(getForegroundInfo())
        val triggerSource = inputData.getString(WorkerUtils.pullTriggerSourceKey) ?: "DIRECT"
        val startedAt = inputData.getLong(WorkerUtils.pullStartedAtKey, SystemClock.elapsedRealtime())
        Timber.i("Pull [$triggerSource] missing beneficiary data started")

        if (preferenceDao.getLoggedInUser() == null) {
            Timber.w("[$NAME] Worker deferred: logged-in user is not available yet")
            return Result.retry()
        }

        return try {
            withContext(Dispatchers.IO) {
                val startTime = System.currentTimeMillis()
                var totalPages: Int
                val startPage = when (triggerSource) {
                    "AUTO_PULL", "MANUAL_REFRESH" -> 0
                    else -> if (preferenceDao.getLastSyncedTimeStamp() == Konstants.defaultTimeStamp)
                        preferenceDao.getFirstSyncMissingDataLastSyncedPage()
                    else 0
                }

                var page = startPage
                do {
                    totalPages = benRepo.getMissingDataBeneficiariesFromServerForWorker(page)
                    if (totalPages == -1) {
                        if (runAttemptCount < MAX_RETRIES) {
                            Timber.w("Pull [$triggerSource] missing data failed at page $page, retrying (attempt ${runAttemptCount + 1})")
                            return@withContext Result.retry()
                        }
                        Timber.e("Pull [$triggerSource] missing data failed at page $page after $MAX_RETRIES retries")
                        return@withContext Result.failure(
                            workDataOf("worker_name" to NAME, "error" to "Missing data pull failed at page $page")
                        )
                    }
                    preferenceDao.setFirstSyncMissingDataLastSyncedPage(page)
                    page++
                    setProgressAsync(workDataOf(PROGRESS to page, NUM_PAGES to totalPages))
                } while (page < totalPages)

                val endTime = System.currentTimeMillis()
                val timeTaken = TimeUnit.MILLISECONDS.toSeconds(endTime - startTime)
                Timber.d("Pull [$triggerSource] missing data completed in ${TimeUnit.MILLISECONDS.toSeconds(SystemClock.elapsedRealtime() - startedAt)} seconds (stage $timeTaken s)")

                Result.success()
            }
        } catch (e: CancellationException) {
            Timber.w("PullMissingDataFromAmritWorker cancelled: $e")
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Error in PullMissingDataFromAmritWorker")
            Result.failure(workDataOf("worker_name" to NAME, "error" to (e.message ?: "Unknown error")))
        }
    }

    private fun createForegroundInfo(progress: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(
            appContext,
            appContext.getString(R.string.notification_sync_channel_id)
        )
            .setContentTitle("Syncing Missing Data")
            .setContentText(progress)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setProgress(100, 0, true)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(1004, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(1004, notification)
        }
    }
}
