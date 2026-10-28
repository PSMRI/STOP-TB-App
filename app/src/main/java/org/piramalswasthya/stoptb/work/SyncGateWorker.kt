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
// It never loops forever: re-arming only happens while a chain is running, and
// MAX_REARM bounds polling of a stuck chain. A gate run that starts a chain
// does not re-arm, so a permanently failing record cannot cause a retrigger loop.
@HiltWorker
class SyncGateWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val name = "SyncGateWorker"

        // Rapid saves within this window coalesce into one cycle.
        const val DEBOUNCE_SECONDS = 2L

        // How often the gate re-checks while a push chain is still running.
        const val BUSY_RECHECK_SECONDS = 20L

        // Ceiling on consecutive busy re-checks (~20 minutes).
        const val MAX_REARM = 60

        private const val KEY_REARM = "sync_gate_rearm_count"
    }

    override suspend fun doWork(): Result {
        return try {
            val workManager = WorkManager.getInstance(applicationContext)

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
}
