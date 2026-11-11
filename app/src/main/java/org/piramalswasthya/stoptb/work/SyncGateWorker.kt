package org.piramalswasthya.stoptb.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.database.shared_preferences.SyncRetryStore
import org.piramalswasthya.stoptb.utils.sync.CampHubReachability
import timber.log.Timber
import java.util.concurrent.TimeUnit

// Debounce + serialization gate in front of the push chain (ported from
// FLW-Mobile-App).
//
// Every form save calls WorkerUtils.triggerAmritPushWorker(), which enqueues
// only this worker as unique work with REPLACE and a short initial delay, so a
// burst of saves collapses into one gate run. REPLACE is safe here because the
// gate never touches sync state; it only inspects WorkManager and enqueues.
//
// When it runs:
//   - If a push chain is RUNNING, it does not start another. It re-arms itself
//     so records saved during the current cycle go out in a follow-up cycle.
//     Previously such saves were dropped until the next trigger.
//   - Otherwise it starts one push chain via WorkerUtils.enqueuePushChain().
//
//   - If the camp hub is unreachable, it re-checks every OFFLINE_RECHECK_SECONDS
//     so a record saved offline goes out shortly after the hub is back, even with
//     the app in the background or restarted. Before, it gave up until the next
//     save or the 15-minute heartbeat (UAT field test 2026-11-11: 7.5 min).
//
// It never loops forever: re-arming only happens while a chain is running or the
// hub is unreachable, bounded by MAX_REARM / MAX_OFFLINE_REARM. A gate run that
// starts a chain does not re-arm, so a permanently failing record cannot cause a
// retrigger loop.
@HiltWorker
class SyncGateWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val preferenceDao: PreferenceDao,
    private val syncRetryStore: SyncRetryStore,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val name = "SyncGateWorker"

        // Rapid saves within this window coalesce into one cycle.
        const val DEBOUNCE_SECONDS = 2L

        // How often the gate re-checks while a push chain is still running.
        const val BUSY_RECHECK_SECONDS = 20L

        // Ceiling on consecutive busy re-checks (~20 minutes).
        const val MAX_REARM = 60

        // How often the gate re-checks an unreachable hub, and for how long
        // (~15 minutes; after that the heartbeat and the next save take over).
        const val OFFLINE_RECHECK_SECONDS = 30L
        const val MAX_OFFLINE_REARM = 30

        private const val KEY_REARM = "sync_gate_rearm_count"
        private const val KEY_OFFLINE_REARM = "sync_gate_offline_rearm_count"
    }

    private enum class Hub { READY, UNREACHABLE, NOT_CONFIGURED }

    override suspend fun doWork(): Result {
        return try {
            val workManager = WorkManager.getInstance(applicationContext)

            when (ensureCampHubConnected()) {
                Hub.READY -> Unit
                Hub.NOT_CONFIGURED -> return Result.success()
                Hub.UNREACHABLE -> {
                    reArmWhileOffline(workManager)
                    return Result.success()
                }
            }

            val chainRunning = withContext(Dispatchers.IO) {
                workManager.getWorkInfosForUniqueWork(WorkerUtils.pushWorkerUniqueName).get()
            }.any { it.state == WorkInfo.State.RUNNING }

            if (chainRunning) {
                val reArm = inputData.getInt(KEY_REARM, 0)
                if (reArm >= MAX_REARM) {
                    // The next save re-arms the gate anyway.
                    Timber.w("SyncGate: push chain still running after $reArm re-checks, stop polling")
                    return Result.success()
                }
                Timber.d("SyncGate: push chain running, re-arming (attempt ${reArm + 1})")
                val next = OneTimeWorkRequestBuilder<SyncGateWorker>()
                    .setInitialDelay(BUSY_RECHECK_SECONDS, TimeUnit.SECONDS)
                    .setInputData(workDataOf(KEY_REARM to reArm + 1))
                    .build()
                workManager.enqueueUniqueWork(
                    WorkerUtils.syncGateUniqueName,
                    ExistingWorkPolicy.REPLACE,
                    next
                )
            } else {
                Timber.d("SyncGate: no running push chain, starting one")
                WorkerUtils.enqueuePushChain(applicationContext)
            }
            Result.success()
        } catch (e: Exception) {
            // Never leave the gate in retry back-off; the next save re-arms it.
            Timber.e(e, "SyncGate: gate check failed")
            Result.success()
        }
    }

    // The hub-connected flag is cleared after a failed request and used to be
    // set again only by an open screen. If it is cleared, check the hub here so
    // pushes from saves and the heartbeat resume in the background.
    private suspend fun ensureCampHubConnected(): Hub {
        if (!preferenceDao.isCampModeEnabled() || preferenceDao.isCampHubConnected()) return Hub.READY
        val url = preferenceDao.getStoredCampHubUrl()?.takeIf { it.isNotBlank() } ?: return Hub.NOT_CONFIGURED
        val reachable = withContext(Dispatchers.IO) { CampHubReachability.isReachable(url) }
        if (!reachable) return Hub.UNREACHABLE
        Timber.i("SyncGate: camp hub reachable again")
        preferenceDao.setCampHubConnected(true)
        syncRetryStore.resetBackoff()
        return Hub.READY
    }

    // A save or the heartbeat replaces this request (REPLACE), which restarts the count.
    private fun reArmWhileOffline(workManager: WorkManager) {
        val reArm = inputData.getInt(KEY_OFFLINE_REARM, 0)
        if (reArm >= MAX_OFFLINE_REARM) {
            Timber.d("SyncGate: camp hub still unreachable after $reArm re-checks, push waits for the next save or heartbeat")
            return
        }
        Timber.d("SyncGate: camp hub unreachable, re-checking in ${OFFLINE_RECHECK_SECONDS}s (attempt ${reArm + 1})")
        // Requires a network: with the app in the background Android blocks its network
        // (blocked=APP_BACKGROUND) unless the running job declared one, so an unconstrained
        // re-check failed DNS for ~90 s after the network returned (device test 2026-11-11).
        // The constraint also holds the re-check while the device is fully offline and runs it
        // as soon as a network is back.
        val next = OneTimeWorkRequestBuilder<SyncGateWorker>()
            .setInitialDelay(OFFLINE_RECHECK_SECONDS, TimeUnit.SECONDS)
            .setConstraints(WorkerUtils.networkOnlyConstraint)
            .setInputData(workDataOf(KEY_OFFLINE_REARM to reArm + 1))
            .build()
        workManager.enqueueUniqueWork(WorkerUtils.syncGateUniqueName, ExistingWorkPolicy.REPLACE, next)
    }
}
