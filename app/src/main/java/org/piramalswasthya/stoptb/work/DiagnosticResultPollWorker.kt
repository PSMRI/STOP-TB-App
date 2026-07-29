package org.piramalswasthya.stoptb.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingWorkPolicy
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.piramalswasthya.stoptb.repositories.TBRepo
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.database.room.SyncState
import timber.log.Timber
import java.util.concurrent.TimeUnit

@HiltWorker
class DiagnosticResultPollWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val tbRepo: TBRepo,
    private val preferenceDao: PreferenceDao,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val name = "DiagnosticResultPollWorker"
    }

    override suspend fun doWork(): Result {
        return try {
            withContext(Dispatchers.IO) {
                Timber.d("DiagnosticResultPollWorker starting work")
                val activeList = tbRepo.getDiagnosticsList()
                var hasInProgress = false
                val now = System.currentTimeMillis()

                for (diag in activeList) {
                    val xrayInProgress = diag.xrayOrderStatus.equals("IN_PROGRESS", ignoreCase = true) || diag.xrayOrderStatus.equals("AWAITING_PROVIDER_RESULT", ignoreCase = true)
                    val trueNatInProgress = diag.trueNatOrderStatus.equals("IN_PROGRESS", ignoreCase = true) || diag.trueNatOrderStatus.equals("AWAITING_PROVIDER_RESULT", ignoreCase = true)
                    val rifInProgress = diag.rifOrderStatus.equals("IN_PROGRESS", ignoreCase = true) || diag.rifOrderStatus.equals("AWAITING_PROVIDER_RESULT", ignoreCase = true)

                    if (xrayInProgress) {
                        var actualStart = preferenceDao.getDiagPollActualStartTime(diag.benId, "XRAY_CHEST")
                        if (actualStart <= 0L) {
                            actualStart = now
                            preferenceDao.setDiagPollActualStartTime(diag.benId, "XRAY_CHEST", now)
                        }
                        if (now - actualStart > 30 * 60 * 1000) { // 30 minutes
                            Timber.d("Polling xray result timed out (30 mins) for benId=${diag.benId}")
                            val updated = diag.copy(xrayOrderStatus = "FAILED", syncState = SyncState.SYNCED)
                            tbRepo.saveTBDiagnostics(updated)
                        } else {
                            Timber.d("Polling xray result for benId=${diag.benId}")
                            tbRepo.fetchOrderResult(diag.benId, "XRAY_CHEST")
                            hasInProgress = true
                        }
                    }
                    if (trueNatInProgress) {
                        var actualStart = preferenceDao.getDiagPollActualStartTime(diag.benId, "SPUTUM_TRUENAT")
                        if (actualStart <= 0L) {
                            actualStart = now
                            preferenceDao.setDiagPollActualStartTime(diag.benId, "SPUTUM_TRUENAT", now)
                        }
                        if (now - actualStart > 30 * 60 * 1000) { // 30 minutes
                            Timber.d("Polling truenat result timed out (30 mins) for benId=${diag.benId}")
                            val updated = diag.copy(trueNatOrderStatus = "FAILED", syncState = SyncState.SYNCED)
                            tbRepo.saveTBDiagnostics(updated)
                        } else {
                            Timber.d("Polling truenat result for benId=${diag.benId}")
                            tbRepo.fetchOrderResult(diag.benId, "SPUTUM_TRUENAT")
                            hasInProgress = true
                        }
                    }
                    if (rifInProgress) {
                        var actualStart = preferenceDao.getDiagPollActualStartTime(diag.benId, "MDR_RIF")
                        if (actualStart <= 0L) {
                            actualStart = now
                            preferenceDao.setDiagPollActualStartTime(diag.benId, "MDR_RIF", now)
                        }
                        if (now - actualStart > 30 * 60 * 1000) { // 30 minutes
                            Timber.d("Polling rif result timed out (30 mins) for benId=${diag.benId}")
                            val updated = diag.copy(rifOrderStatus = "FAILED", syncState = SyncState.SYNCED)
                            tbRepo.saveTBDiagnostics(updated)
                        } else {
                            Timber.d("Polling rif result for benId=${diag.benId}")
                            tbRepo.fetchOrderResult(diag.benId, "MDR_RIF")
                            hasInProgress = true
                        }
                    }
                }

                // If any test is still in progress, schedule another poll in 40 seconds
                if (hasInProgress) {
                    val pollDelaySec = 40L
                    Timber.d("Scheduling next DiagnosticResultPollWorker run in ${pollDelaySec}s")
                    val pollRequest = OneTimeWorkRequestBuilder<DiagnosticResultPollWorker>()
                        .setInitialDelay(pollDelaySec, TimeUnit.SECONDS)
                        .build()
                    WorkManager.getInstance(appContext).enqueueUniqueWork(
                        name,
                        ExistingWorkPolicy.REPLACE,
                        pollRequest
                    )
                } else {
                    Timber.d("No active in-progress diagnostic orders. DiagnosticResultPollWorker stopping.")
                }

                Result.success()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error inside DiagnosticResultPollWorker")
            Result.failure()
        }
    }
}
