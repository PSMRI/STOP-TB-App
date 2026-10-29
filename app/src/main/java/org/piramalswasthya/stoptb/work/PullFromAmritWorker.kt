package org.piramalswasthya.stoptb.work

import android.content.pm.ServiceInfo
import android.os.Build
import android.content.Context
import android.os.SystemClock
import android.database.sqlite.SQLiteConstraintException
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.helpers.Konstants
import org.piramalswasthya.stoptb.repositories.BenRepo
import timber.log.Timber
import java.lang.Integer.min
import java.util.concurrent.TimeUnit

@HiltWorker
class PullFromAmritWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val benRepo: BenRepo,
    private val preferenceDao: PreferenceDao,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val name = "PullFromAmritWorker"
        const val Progress = "Progress"
        const val NumPages = "Total Pages"
        // Parallel page fetchers. This is BOTH the number of shards and the page stride, so
        // the two must change together or shards overlap and re-fetch each other's pages.
        // Reduced from 4: each shard wraps its page in a write transaction, and four of those
        // held every thread Room had. SQLite also allows only one writer at a time, so the
        // extra shards were contending for the write lock rather than adding throughput.
        const val n = 2
    }

    /** Next page each shard will fetch; index is the shard's remainder. One writer per index. */
    private val pagesByShard = IntArray(n)


    override suspend fun getForegroundInfo(): ForegroundInfo = createForegroundInfo("Syncing data...")

    override suspend fun doWork(): Result {
        setForeground(getForegroundInfo())
        val triggerSource = inputData.getString(WorkerUtils.pullTriggerSourceKey) ?: "DIRECT"
        val startedAt = inputData.getLong(WorkerUtils.pullStartedAtKey, SystemClock.elapsedRealtime())
        Timber.i("Pull [$triggerSource] beneficiary and household data started")

        if (preferenceDao.getLoggedInUser() == null) {
            Timber.w("[$name] Worker deferred: logged-in user is not available yet")
            return Result.retry()
        }

        return try {
            withContext(Dispatchers.IO) {
                val startTime = System.currentTimeMillis()
                var numPages: Int
                // Refreshes must start from the first page so changes that shift page offsets
                // (such as a newly registered beneficiary) are not missed after a partial pull.
                val startPage = when (triggerSource) {
                    "AUTO_PULL", "MANUAL_REFRESH" -> 0
                    else -> if (preferenceDao.getLastSyncedTimeStamp() == Konstants.defaultTimeStamp)
                        preferenceDao.getFirstSyncLastSyncedPage()
                    else 0
                }

                try {
                    // Pages are buffered and written in batches, so that a ~2,000-page pull
                    // invalidates every observed query ~100 times rather than ~2,000 times.
                    // Whatever is still buffered when this worker stops - normally, by failure,
                    // or by cancellation - has to be committed here or it is silently dropped
                    // and left to contaminate the next run's first batch.
                    do {
                        numPages = benRepo.getBeneficiariesFromServerForWorker(startPage)
                    } while (numPages == -2)
                    if (numPages == 0)
                        return@withContext Result.success()
                    val result1 = supervisorScope {
                        awaitAll(
                            *(0 until n)
                                .map { shard -> async { getBenForPage(numPages, shard, startPage) } }
                                .toTypedArray()
                        )
                    }
                    val endTime = System.currentTimeMillis()
                    val timeTaken = TimeUnit.MILLISECONDS.toSeconds(endTime - startTime)
                    Timber.d(
                        "Pull [$triggerSource] beneficiary and household data completed in ${TimeUnit.MILLISECONDS.toSeconds(SystemClock.elapsedRealtime() - startedAt)} seconds " +
                                "($numPages pages, worker stage $timeTaken seconds)"
                    )

                    if (result1.all { it }) {
                        return@withContext Result.success()
                    }
                    return@withContext Result.failure(workDataOf("worker_name" to "PullFromAmritWorker", "error" to "Pull operation returned incomplete results"))
                } catch (e: SQLiteConstraintException) {
                    Timber.e("exception $e raised ${e.message} with stacktrace : ${e.stackTrace}")
                    return@withContext Result.failure(workDataOf("worker_name" to "PullFromAmritWorker", "error" to "SQLite constraint: ${e.message}"))
                } finally {
                    withContext(NonCancellable) {
                        benRepo.flushPendingServerBeneficiaries()
                    }
                }

            }

        } catch (e: CancellationException) {
            Timber.w("PullFromAmritFullLoadWorker cancelled: $e")
            throw e
        } catch (e: java.lang.Exception) {
            Timber.e("Error occurred in PullFromAmritFullLoadWorker $e ${e.stackTrace}")

            Result.failure(workDataOf("worker_name" to "PullFromAmritWorker", "error" to (e.message ?: "Unknown error")))
        }
    }

    private fun createForegroundInfo(progress: String): ForegroundInfo {

        val notification = NotificationCompat.Builder(
            appContext,
            appContext.getString(org.piramalswasthya.stoptb.R.string.notification_sync_channel_id)
        )
            .setContentTitle("Syncing Data")
            .setContentText(progress)
            .setSmallIcon(org.piramalswasthya.stoptb.R.drawable.ic_launcher_foreground)
            .setProgress(100, 0, true)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(1003, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(1003, notification)
        }
    }


    private suspend fun getBenForPage(numPages: Int, rem: Int, startPage: Int): Boolean {
        return withContext(Dispatchers.IO) {
            var page: Int = startPage + rem

            try {
                while (page <= numPages) {
                    val ret = benRepo.getBeneficiariesFromServerForWorker(page)

                    if (ret == -1)
                        throw IllegalStateException("benRepo.getBeneficiariesFromServerForWorker(page) returned -1 ")
                    if (ret != -2) {
                        val finalPage = pagesByShard.sum() / n
                        // Pages are written in batches, so "fetched" is ahead of "on disk".
                        // The resume marker has to stay behind the oldest page still buffered,
                        // otherwise a worker that dies before its next flush resumes past rows
                        // that were never committed.
                        val unflushedFloor = benRepo.lowestUnflushedPullPage()
                        val minPageSynced =
                            listOfNotNull(pagesByShard.min(), unflushedFloor).min()
                        preferenceDao.setFirstSyncLastSyncedPage(minPageSynced)
                        setProgressAsync(workDataOf(Progress to finalPage, NumPages to numPages))
                        page += n
                    }
                    pagesByShard[rem] = page

                }
            } catch (e: CancellationException) {
                Timber.w("Page pull cancelled for rem=$rem page=$page: $e")
                throw e
            } catch (e: SQLiteConstraintException) {
                Timber.e("exception $e raised ${e.message} with stacktrace : ${e.stackTrace}")
            }
            true
        }
    }

}
