package org.piramalswasthya.stoptb.work

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingWorkPolicy
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.piramalswasthya.stoptb.repositories.TBRepo
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.model.OrderStatus
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

        // Caps how many order/result requests run at once against the camp hub — a lightweight
        // local LAN server, not built to take dozens of simultaneous connections. Parallelizing
        // at all (instead of the previous one-by-one sequential loop) is what actually shortens
        // a busy cycle's wall-clock time; this cap just keeps that from overwhelming the hub.
        private const val MAX_CONCURRENT_POLLS = 4
    }

    override suspend fun doWork(): Result {
        return try {
            withContext(Dispatchers.IO) {
                Timber.d("DiagnosticResultPollWorker starting work")
                if (!preferenceDao.isCampModeEnabled() || !preferenceDao.isCampHubConnected()) {
                    Timber.d("Skipping DiagnosticResultPollWorker: Camp Mode is disabled or Camp Hub is disconnected")
                    return@withContext Result.success()
                }

                // Retry any manually-entered results saved locally while the hub was
                // disconnected (or a network failure happened) — Enter Result/Not Conducted are
                // standing actions independent of device integration, so this must run
                // regardless of the integration check below, not be skipped along with it.
                val hasPendingManualResultSync = tbRepo.retryPendingManualResultSyncs()

                // Refresh vendor integration config dynamically before polling
                tbRepo.refreshDeviceIntegrationConfig()

                var hasInProgress = false
                if (!tbRepo.isXrayIntegrated() && !tbRepo.isTruenatIntegrated()) {
                    Timber.d("Skipping automated result polling: no diagnostic devices are integrated")
                } else {
                if (tbRepo.isXrayIntegrated()) {
                    tbRepo.fetchBeneficiariesByStatus("XRAY_CHEST")
                }
                if (tbRepo.isTruenatIntegrated()) {
                    tbRepo.fetchBeneficiariesByStatus("SPUTUM_TRUENAT")
                    tbRepo.fetchBeneficiariesByStatus("MDR_RIF")
                }
                val activeList = tbRepo.getDiagnosticsList()
                val now = System.currentTimeMillis()

                // Real order/result contract confirmed: PENDING, IN_PROGRESS, COMPLETED, FAILED,
                // CLOSED, MANUAL_ENTRY. IN_PROGRESS is collapsed into our stored PENDING at the
                // repository boundary (TBRepo.reducedOrderStatus/createOrder/
                // fetchBeneficiariesByStatus), so Room never actually holds "IN_PROGRESS" or
                // "AWAITING_PROVIDER_RESULT" literally — checking PENDING alone is correct.
                // MANUAL_ENTRY is intentionally excluded here — there is no automated result to
                // fetch for it; the unconditional fetchBeneficiariesByStatus sweep above already
                // reconciles it (and FAILED) every time this worker runs.
                data class PendingOrder(val benId: Long, val xray: Boolean, val trueNat: Boolean, val rif: Boolean)

                val pendingOrders = activeList.mapNotNull { diag ->
                    val xrayInProgress = tbRepo.isXrayIntegrated() &&
                            diag.xrayOrderStatus.equals(OrderStatus.PENDING.name, ignoreCase = true)
                    val trueNatInProgress = tbRepo.isTruenatIntegrated() &&
                            diag.trueNatOrderStatus.equals(OrderStatus.PENDING.name, ignoreCase = true)
                    val rifInProgress = tbRepo.isTruenatIntegrated() &&
                            diag.rifOrderStatus.equals(OrderStatus.PENDING.name, ignoreCase = true)
                    if (xrayInProgress || trueNatInProgress || rifInProgress) {
                        PendingOrder(diag.benId, xrayInProgress, trueNatInProgress, rifInProgress)
                    } else null
                }
                hasInProgress = pendingOrders.isNotEmpty()

                // Fetch results with bounded parallelism instead of one-by-one sequential awaits
                // — a busy camp with many orders in flight no longer makes this cycle's
                // wall-clock time grow linearly with beneficiary count. Each fetchOrderResult
                // call already catches its own exceptions and returns NetworkResponse.Error
                // (never throws), so one beneficiary's failure can't cancel the others under
                // coroutineScope's structured concurrency.
                if (hasInProgress) {
                    val pollSemaphore = Semaphore(MAX_CONCURRENT_POLLS)
                    coroutineScope {
                        pendingOrders.forEach { order ->
                            if (order.xray) {
                                launch {
                                    pollSemaphore.withPermit {
                                        Timber.d("Polling xray result for benId=${order.benId}")
                                        tbRepo.fetchOrderResult(order.benId, "XRAY_CHEST")
                                        preferenceDao.setLastCheckedTime(order.benId, "XRAY_CHEST", now)
                                    }
                                }
                            }
                            if (order.trueNat) {
                                launch {
                                    pollSemaphore.withPermit {
                                        Timber.d("Polling truenat result for benId=${order.benId}")
                                        tbRepo.fetchOrderResult(order.benId, "SPUTUM_TRUENAT")
                                        preferenceDao.setLastCheckedTime(order.benId, "SPUTUM_TRUENAT", now)
                                    }
                                }
                            }
                            if (order.rif) {
                                launch {
                                    pollSemaphore.withPermit {
                                        Timber.d("Polling rif result for benId=${order.benId}")
                                        tbRepo.fetchOrderResult(order.benId, "MDR_RIF")
                                        preferenceDao.setLastCheckedTime(order.benId, "MDR_RIF", now)
                                    }
                                }
                            }
                        }
                    }
                }
                }

                // Reschedule while there's still an active automated order OR a manual-result
                // sync still pending (e.g. the hub only just reconnected and the retry above
                // hasn't caught up yet).
                if (hasInProgress || hasPendingManualResultSync) {
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
                    Timber.d("No active in-progress diagnostic orders or pending manual-result syncs. DiagnosticResultPollWorker stopping.")
                }

                Result.success()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error inside DiagnosticResultPollWorker")
            Result.failure()
        }
    }

}
