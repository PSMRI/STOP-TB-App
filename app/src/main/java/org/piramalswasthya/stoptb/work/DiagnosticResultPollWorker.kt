package org.piramalswasthya.stoptb.work

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
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
import org.piramalswasthya.stoptb.helpers.NetworkResponse
import org.piramalswasthya.stoptb.model.OrderStatus
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@HiltWorker
class DiagnosticResultPollWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val tbRepo: TBRepo,
    private val preferenceDao: PreferenceDao,
    private val pollSchedule: ResultPollSchedule,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val name = "DiagnosticResultPollWorker"

        // Caps how many order/result requests run at once against the camp hub — a lightweight
        // local LAN server, not built to take dozens of simultaneous connections. Parallelizing
        // at all (instead of the previous one-by-one sequential loop) is what actually shortens
        // a busy cycle's wall-clock time; this cap just keeps that from overwhelming the hub.
        private const val MAX_CONCURRENT_POLLS = 4
        private const val DEVICE_CONFIG_MAX_AGE_MS = 10 * 60_000L
        private const val HUB_DOWN_RECHECK_SEC = 300L
    }

    private fun scheduleNextRun(delaySec: Long) {
        val workManager = WorkManager.getInstance(appContext)
        // A trigger that arrived during this run already queued the next cycle right behind it,
        // and that cycle schedules its own successor. Adding ours too would keep an extra cycle in
        // the chain for good. Called from doWork on Dispatchers.IO, so the blocking query is fine.
        val successorQueued = try {
            workManager.getWorkInfosForUniqueWork(name).get().any { info ->
                info.id != id && (info.state == WorkInfo.State.ENQUEUED || info.state == WorkInfo.State.BLOCKED)
            }
        } catch (e: Exception) {
            Timber.w(e, "Could not read DiagnosticResultPollWorker state; scheduling the next run anyway")
            false
        }
        if (successorQueued) {
            Timber.d("Next DiagnosticResultPollWorker run already queued; not adding another")
            return
        }
        Timber.d("Scheduling next DiagnosticResultPollWorker run in ${delaySec}s")
        val pollRequest = OneTimeWorkRequestBuilder<DiagnosticResultPollWorker>()
            .setInitialDelay(delaySec, TimeUnit.SECONDS)
            .build()
        // APPEND_OR_REPLACE: queue the next cycle behind this one. REPLACE on our own unique
        // name cancelled this still-running work.
        workManager.enqueueUniqueWork(
            name,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            pollRequest
        )
    }

    override suspend fun doWork(): Result {
        return try {
            withContext(Dispatchers.IO) {
                Timber.d("DiagnosticResultPollWorker starting work")
                if (!preferenceDao.isCampModeEnabled()) {
                    Timber.d("Skipping DiagnosticResultPollWorker: Camp Mode is disabled")
                    return@withContext Result.success()
                }
                if (!preferenceDao.isCampHubConnected()) {
                    // The hub can read as disconnected for a moment (e.g. while Connect re-checks
                    // it). Stopping here left result polling dead until something triggered it
                    // again, so look again later; this check reads prefs only, no network.
                    // Only while there is something to poll or replay: with nothing pending, a
                    // device that left the camp would otherwise wake every 5 min indefinitely;
                    // the hub-reconnect path triggers this worker again anyway.
                    val workLeft = preferenceDao.getFeedSkippedOrders().isNotEmpty() ||
                        tbRepo.getDiagnosticsWithPendingOrder().isNotEmpty() ||
                        tbRepo.getDiagnosticsWithPendingManualResult().isNotEmpty()
                    if (workLeft) {
                        Timber.d("DiagnosticResultPollWorker: Camp Hub is disconnected, checking again in ${HUB_DOWN_RECHECK_SEC}s")
                        scheduleNextRun(HUB_DOWN_RECHECK_SEC)
                    } else {
                        Timber.d("DiagnosticResultPollWorker: Camp Hub is disconnected and nothing is pending; not rescheduling")
                    }
                    return@withContext Result.success()
                }

                // Retry any manually-entered results saved locally while the hub was
                // disconnected (or a network failure happened) — Enter Result/Not Conducted are
                // standing actions independent of device integration, so this must run
                // regardless of the integration check below, not be skipped along with it.
                preferenceDao.pruneLegacyLastCheckedTimes()
                val hasPendingManualResultSync = tbRepo.retryPendingManualResultSyncs()

                // Refresh vendor integration config dynamically before polling
                // At most every 10 min (and on the first cycle / a hub change), not every cycle.
                tbRepo.refreshDeviceIntegrationConfig(maxAgeMs = DEVICE_CONFIG_MAX_AGE_MS)

                var hasInProgress = false
                // The changes feed already brings every order's status on each pull, so the
                // village-wide status sweeps and per-order result calls are only needed when the
                // hub has no feed.
                val feedActive = preferenceDao.isChangesFeedActive()

                // The feed skips a row for an order this tablet created moments ago and moves past
                // it, so a result that arrived then has to be asked for directly. Runs whether or
                // not a device is integrated or any order still looks pending (an order saved
                // without an order id is not in pendingOrders). A pair is cleared only once its
                // fetch succeeds; the worker keeps rescheduling while any remain.
                var hasSkippedFeedOrders = false
                if (feedActive) {
                    val skipped = tbRepo.feedSkippedOrdersPastGrace()
                    skipped.forEach { (benId, type) ->
                        Timber.d("Polling $type result for benId=$benId skipped by the feed")
                        if (tbRepo.fetchOrderResult(benId, type) is NetworkResponse.Success) {
                            tbRepo.ackFeedSkippedOrder(benId, type)
                        }
                    }
                    hasSkippedFeedOrders = preferenceDao.getFeedSkippedOrders().isNotEmpty()
                }

                if (!tbRepo.isXrayIntegrated() && !tbRepo.isTruenatIntegrated()) {
                    Timber.d("Skipping automated result polling: no diagnostic devices are integrated")
                } else {
                // Without a feed: one status sweep per order type. It applies completed results
                // itself, and its "awaiting provider result" list tells us which pending orders
                // the server has nothing new for, so we don't ask order/result about those.
                val awaitingByType = HashMap<String, Set<Long>>()
                if (!feedActive && tbRepo.isXrayIntegrated()) {
                    awaitingByType["XRAY_CHEST"] =
                        tbRepo.awaitingResultBenIds(tbRepo.fetchBeneficiariesByStatus("XRAY_CHEST"))
                }
                if (!feedActive && tbRepo.isTruenatIntegrated()) {
                    awaitingByType["SPUTUM_TRUENAT"] =
                        tbRepo.awaitingResultBenIds(tbRepo.fetchBeneficiariesByStatus("SPUTUM_TRUENAT"))
                    awaitingByType["MDR_RIF"] =
                        tbRepo.awaitingResultBenIds(tbRepo.fetchBeneficiariesByStatus("MDR_RIF"))
                }
                // Only rows with a PENDING order: the filter below keeps nothing else, and loading
                // the whole table every 60 s cost a full scan + every row's object at scale.
                val activeList = tbRepo.getDiagnosticsWithPendingOrder()
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

                fun hasRealOrderId(orderId: String?) =
                    !orderId.isNullOrBlank() && !orderId.equals("N/A", ignoreCase = true)

                val pendingOrders = activeList.mapNotNull { diag ->
                    val xrayInProgress = tbRepo.isXrayIntegrated() &&
                            diag.xrayOrderStatus.equals(OrderStatus.PENDING.name, ignoreCase = true) &&
                            hasRealOrderId(diag.xrayOrderId)
                    val trueNatInProgress = tbRepo.isTruenatIntegrated() &&
                            diag.trueNatOrderStatus.equals(OrderStatus.PENDING.name, ignoreCase = true) &&
                            hasRealOrderId(diag.trueNatOrderId)
                    val rifInProgress = tbRepo.isTruenatIntegrated() &&
                            diag.rifOrderStatus.equals(OrderStatus.PENDING.name, ignoreCase = true) &&
                            hasRealOrderId(diag.rifOrderId)
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
                if (hasInProgress && feedActive) {
                    // The server polls the X-ray and TrueNat vendors itself and every result change
                    // reaches the tablet through the changes feed (applyFeedDiagnosticOrders runs the
                    // same applyOrderResult as fetchOrderResult). Calling order/result per pending
                    // order on top of that only duplicated the feed, and the TB suspected re-uploads
                    // it set off kept the push chain busy and the feed pull waiting. Ask for a quick
                    // delta pull instead; it is debounced and costs one small request.
                    Timber.d("Pending diagnostic orders: ${pendingOrders.size}, results come from the changes feed")
                    WorkerUtils.triggerCampQuickPullIfConnected(appContext, preferenceDao)
                    // Keeps the "Last checked" caption current: one write per cycle, not 3 per order.
                    preferenceDao.setLastResultCheckTime(now)
                } else if (hasInProgress) {
                    // Fallback (no feed): ask order/result only for orders that are due on their
                    // own backoff schedule and that the status sweep didn't already report as
                    // still awaiting a result. Was: every pending order, every cycle.
                    val pollSemaphore = Semaphore(MAX_CONCURRENT_POLLS)
                    val fetched = AtomicInteger(); val skippedAwaiting = AtomicInteger(); val notDue = AtomicInteger()
                    pollSchedule.retainOnly(pendingOrders.flatMap { order ->
                        listOfNotNull(
                            (order.benId to "XRAY_CHEST").takeIf { order.xray },
                            (order.benId to "SPUTUM_TRUENAT").takeIf { order.trueNat },
                            (order.benId to "MDR_RIF").takeIf { order.rif }
                        )
                    })
                    coroutineScope {
                        pendingOrders.forEach { order ->
                            for ((isPending, orderType) in listOf(
                                order.xray to "XRAY_CHEST",
                                order.trueNat to "SPUTUM_TRUENAT",
                                order.rif to "MDR_RIF"
                            )) {
                                if (!isPending) continue
                                when {
                                    order.benId in awaitingByType[orderType].orEmpty() -> {
                                        pollSchedule.recordUnchanged(order.benId, orderType, now)
                                        skippedAwaiting.incrementAndGet()
                                    }
                                    !pollSchedule.isDue(order.benId, orderType, now) -> notDue.incrementAndGet()
                                    else -> launch {
                                        pollSemaphore.withPermit {
                                            fetched.incrementAndGet()
                                            val result = tbRepo.fetchOrderResult(order.benId, orderType)
                                            val stillPending = result !is NetworkResponse.Success ||
                                                result.data.equals(OrderStatus.PENDING.name, ignoreCase = true) ||
                                                result.data.equals("IN_PROGRESS", ignoreCase = true)
                                            if (stillPending) pollSchedule.recordUnchanged(order.benId, orderType, now)
                                            else pollSchedule.recordChanged(order.benId, orderType)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Timber.d("Result poll (no feed): ${pendingOrders.size} pending, fetched ${fetched.get()}, " +
                        "server still awaiting ${skippedAwaiting.get()}, not yet due ${notDue.get()}")
                    preferenceDao.setLastResultCheckTime(now)
                }
                }

                // Reschedule while there's still an active automated order OR a manual-result
                // sync still pending (e.g. the hub only just reconnected and the retry above
                // hasn't caught up yet).
                if (hasInProgress || hasPendingManualResultSync || hasSkippedFeedOrders) {
                    // Without a feed, sleep until the earliest order is due (1-10 min) instead of
                    // waking every minute for orders that are all backing off.
                    val pollDelaySec = if (!feedActive && hasInProgress) {
                        val wallNow = System.currentTimeMillis()
                        val dueIn = (pollSchedule.earliestDue() ?: wallNow) - wallNow
                        (dueIn / 1000).coerceIn(60L, 600L)
                    } else 60L
                    scheduleNextRun(pollDelaySec)
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
