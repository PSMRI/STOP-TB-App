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
                if (tbRepo.isXrayIntegrated()) {
                    tbRepo.fetchBeneficiariesByStatus("XRAY_CHEST")
                }
                if (tbRepo.isTruenatIntegrated()) {
                    tbRepo.fetchBeneficiariesByStatus("SPUTUM_TRUENAT")
                    tbRepo.fetchBeneficiariesByStatus("MDR_RIF")
                }
                val activeList = tbRepo.getDiagnosticsList()
                var hasInProgress = false
                val now = System.currentTimeMillis()

                for (diag in activeList) {
                    val xrayInProgress = diag.xrayOrderStatus.equals("IN_PROGRESS", ignoreCase = true) || diag.xrayOrderStatus.equals("AWAITING_PROVIDER_RESULT", ignoreCase = true)
                    val trueNatInProgress = diag.trueNatOrderStatus.equals("IN_PROGRESS", ignoreCase = true) || diag.trueNatOrderStatus.equals("AWAITING_PROVIDER_RESULT", ignoreCase = true)
                    val rifInProgress = diag.rifOrderStatus.equals("IN_PROGRESS", ignoreCase = true) || diag.rifOrderStatus.equals("AWAITING_PROVIDER_RESULT", ignoreCase = true)

                    if (xrayInProgress && tbRepo.isXrayIntegrated()) {
                        Timber.d("Polling xray result for benId=${diag.benId}")
                        tbRepo.fetchOrderResult(diag.benId, "XRAY_CHEST")
                        preferenceDao.setLastCheckedTime(diag.benId, "XRAY_CHEST", now)
                        hasInProgress = true
                    }
                    if (trueNatInProgress && tbRepo.isTruenatIntegrated()) {
                        Timber.d("Polling truenat result for benId=${diag.benId}")
                        tbRepo.fetchOrderResult(diag.benId, "SPUTUM_TRUENAT")
                        preferenceDao.setLastCheckedTime(diag.benId, "SPUTUM_TRUENAT", now)
                        hasInProgress = true
                    }
                    if (rifInProgress && tbRepo.isTruenatIntegrated()) {
                        Timber.d("Polling rif result for benId=${diag.benId}")
                        tbRepo.fetchOrderResult(diag.benId, "MDR_RIF")
                        preferenceDao.setLastCheckedTime(diag.benId, "MDR_RIF", now)
                        hasInProgress = true
                    }
                }

                // If any test is still in progress, schedule another poll in 60 seconds
                if (hasInProgress) {
                    val pollDelaySec = 60L
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
