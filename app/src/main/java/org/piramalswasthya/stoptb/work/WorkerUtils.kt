package org.piramalswasthya.stoptb.work

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkContinuation
import androidx.work.WorkManager
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceManager
import org.piramalswasthya.stoptb.work.dynamicWoker.FormSyncWorker
import org.piramalswasthya.stoptb.work.dynamicWoker.NCDFollowUpSyncWorker
import org.piramalswasthya.stoptb.work.dynamicWoker.NDCFollowUpPushWorker
import timber.log.Timber
import java.util.concurrent.TimeUnit

object WorkerUtils {

    const val pushWorkerUniqueName = "PUSH-TO-AMRIT"
    const val pullWorkerUniqueName = "PULL-FROM-AMRIT"
    const val syncGateUniqueName = "SYNC-GATE"
    const val syncHeartbeatUniqueName = "SYNC-HEARTBEAT"
    const val tabletVersionReportUniqueName = "TABLET-VERSION-REPORT"
    private const val syncHeartbeatIntervalMinutes = 15L
    const val pullTriggerSourceKey = "pull_trigger_source"
    const val pullStartedAtKey = "pull_started_at"
    const val changesFeedAppliedKey = "changes_feed_applied"
    const val campAutoPullIntervalMs = 10_000L
    private const val campQuickPullDebounceMs = 10_000L
    private var lastCampQuickPullAt = 0L
    @Volatile
    private var manualCampRefreshInProgress = false

    private val networkOnlyConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    private inline fun <reified W : androidx.work.ListenableWorker> syncRequestBuilder() =
        OneTimeWorkRequestBuilder<W>()
            .setConstraints(networkOnlyConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)

    private inline fun <reified W : androidx.work.ListenableWorker> campSyncRequestBuilder() =
        OneTimeWorkRequestBuilder<W>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)

    private fun hasLoggedInUser(context: Context): Boolean {
        val prefs = PreferenceManager.getInstance(context)
        val userKey = context.getString(R.string.PREF_user_entry)
        return !prefs.getString(userKey, null).isNullOrBlank()
    }

    // Camp Wi-Fi reaches the hub but has no internet. WorkManager's CONNECTED constraint waits for a
    // network Android has validated as having internet, so in camp mode sync work must not use it;
    // the sync gate checks the hub itself.
    private fun isCampModeEnabled(context: Context): Boolean {
        val prefs = PreferenceManager.getInstance(context)
        return prefs.getBoolean(context.getString(R.string.PREF_camp_mode_enabled), false)
    }

    private fun isCampHubSyncActive(context: Context): Boolean {
        val prefs = PreferenceManager.getInstance(context)
        val campEnabledKey = context.getString(R.string.PREF_camp_mode_enabled)
        val hubConnectedKey = context.getString(R.string.PREF_camp_hub_connected)
        return prefs.getBoolean(campEnabledKey, false) &&
                prefs.getBoolean(hubConnectedKey, false)
    }

    private fun getActiveUniqueWorkInfos(
        workManager: WorkManager,
        uniqueWorkName: String
    ): List<WorkInfo> {
        return try {
            workManager.getWorkInfosForUniqueWork(uniqueWorkName).get()
                .filter {
                    it.state == WorkInfo.State.ENQUEUED ||
                            it.state == WorkInfo.State.RUNNING ||
                            it.state == WorkInfo.State.BLOCKED
                }
        } catch (e: Exception) {
            Timber.w(e, "Unable to query active work for $uniqueWorkName")
            emptyList()
        }
    }

    // Whether a push may be requested at all: a user is logged in and camp mode
    // is on. Whether the hub is reachable is checked later by SyncGateWorker.
    private fun canTriggerPush(context: Context): Boolean {
        if (!hasLoggedInUser(context)) {
            Timber.w("Push worker skipped: no logged-in user is stored yet")
            return false
        }
        val prefs = PreferenceManager.getInstance(context)
        val campKey = context.getString(R.string.PREF_camp_mode_enabled)
        if (!prefs.getBoolean(campKey, false)) {
            Timber.d("Push worker skipped: camp mode is disabled")
            return false
        }
        return true
    }

    private fun canPush(context: Context): Boolean {
        // Block all data push until camp mode is active AND hub is connected
        if (!canTriggerPush(context)) return false
        val prefs = PreferenceManager.getInstance(context)
        val hubConnectedKey = context.getString(R.string.PREF_camp_hub_connected)
        if (!prefs.getBoolean(hubConnectedKey, false)) {
            Timber.d("Push worker skipped: camp hub is disconnected")
            return false
        }
        return true
    }

    // Called on every form save. Goes through SyncGateWorker, which debounces
    // bursts of saves into one push cycle and, if a cycle is already running,
    // starts a follow-up cycle after it ends instead of dropping the save.
    fun triggerAmritPushWorker(context: Context) {
        if (!canTriggerPush(context)) return
        val gate = OneTimeWorkRequestBuilder<SyncGateWorker>()
            .setInitialDelay(SyncGateWorker.DEBOUNCE_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            syncGateUniqueName,
            ExistingWorkPolicy.REPLACE,
            gate
        )
    }

    // Tells the camp laptop which app version this tablet runs. Cheap no-op outside a camp.
    // No network constraint, same reason as the heartbeat below. Not important: it has its own
    // unique name (no sync screen or gate watches it), and it never throws into the caller.
    fun reportTabletVersion(context: Context, force: Boolean) {
        try {
            val request = OneTimeWorkRequestBuilder<TabletVersionReportWorker>()
                .setInputData(Data.Builder().putBoolean(TabletVersionReportWorker.FORCE, force).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                tabletVersionReportUniqueName,
                if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request
            )
        } catch (e: Exception) {
            Timber.d("Tablet version report not scheduled: ${e.message}")
        }
    }

    // Schedules SyncHeartbeatWorker every 15 minutes (the WorkManager minimum) so
    // unsynced records keep being retried without user action. KEEP makes repeat
    // calls cheap; logout cancels all work and the next app start or login
    // screen schedules it again.
    fun scheduleSyncHeartbeat(context: Context) {
        // No network constraint: in camp there is Wi-Fi but no internet, and the gate it
        // triggers checks the hub itself.
        val request = PeriodicWorkRequestBuilder<SyncHeartbeatWorker>(
            syncHeartbeatIntervalMinutes, TimeUnit.MINUTES
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            syncHeartbeatUniqueName,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    // Enqueues the push chain directly and returns its work IDs. Used by
    // SyncGateWorker and by manual camp refresh, which tracks these IDs.
    fun enqueuePushChain(context: Context): List<java.util.UUID> {
        if (!canPush(context)) return emptyList()

        val isCampHubSyncActive = isCampHubSyncActive(context)
        val workManager = WorkManager.getInstance(context)
        val activePushInfos = getActiveUniqueWorkInfos(workManager, pushWorkerUniqueName)
        if (activePushInfos.any { it.state == WorkInfo.State.RUNNING }) {
            Timber.d("Push chain already running, arming sync gate for a follow-up cycle")
            triggerAmritPushWorker(context)
            return activePushInfos.map { it.id }
        }
        if (activePushInfos.isNotEmpty() && isCampHubSyncActive) {
            Timber.d("Replacing stale unique push chain for camp sync")
            workManager.cancelUniqueWork(pushWorkerUniqueName)
        }

        val campMode = isCampModeEnabled(context)

        // StopTB push chain:
        // Registration (+ vitals) → TB data + ABHA + counselling + contact tracing
        //
        // Registration is expedited so it runs promptly instead of waiting for
        // a Doze / battery-saver maintenance window. If the expedited quota is
        // used up it falls back to normal work. On Android 11 and below,
        // WorkManager runs expedited work as a foreground service using
        // BasePushWorker.getForegroundInfo(); on 12+ there is no notification.
        val registration = if (campMode) {
            campSyncRequestBuilder<PushToAmritWorker>()
        } else {
            syncRequestBuilder<PushToAmritWorker>()
        }.addTag("push_group1_registration")
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        val afterRegistration = workManager.beginUniqueWork(
            pushWorkerUniqueName, ExistingWorkPolicy.KEEP, registration)

        val groupTB = listOf(
            (if (campMode) {
                campSyncRequestBuilder<PushTBToAmritWorker>()
            } else {
                syncRequestBuilder<PushTBToAmritWorker>()
            }).addTag("push_group5_tb").build(),
        ) 

        val groupAbha = if (campMode) {
            campSyncRequestBuilder<PushMapAbhatoBenficiaryWorker>()
        } else {
            syncRequestBuilder<PushMapAbhatoBenficiaryWorker>()
        }.addTag("push_group9_digital_health").build()

        val groupCounselling = if (campMode) {
            campSyncRequestBuilder<PushCounsellingWorker>()
        } else {
            syncRequestBuilder<PushCounsellingWorker>()
        }.addTag("push_group10_counselling").build()

        val groupContactTracing = if (campMode) {
            campSyncRequestBuilder<PushContactTracingWorker>()
        } else {
            syncRequestBuilder<PushContactTracingWorker>()
        }.addTag("push_group11_contact_tracing").build()

        val chainTB = afterRegistration.then(groupTB)
        val chainAbha = afterRegistration.then(listOf(groupAbha))
        val chainCounselling = afterRegistration.then(listOf(groupCounselling))
        val chainContactTracing = afterRegistration.then(listOf(groupContactTracing))

        WorkContinuation.combine(listOf(chainTB, chainAbha, chainCounselling, chainContactTracing)).enqueue()
        return listOf(registration.id, groupAbha.id, groupCounselling.id, groupContactTracing.id) +
                groupTB.map { it.id }
    }

    fun triggerAmritPullWorker(
        context: Context,
        force: Boolean = false,
        triggerSource: String = "DIRECT"
    ): List<java.util.UUID> {
        if (!hasLoggedInUser(context)) {
            Timber.w("Pull worker skipped: no logged-in user is stored yet")
            return emptyList()
        }

        val workManager = WorkManager.getInstance(context)
        val activePullInfos = getActiveUniqueWorkInfos(workManager, pullWorkerUniqueName)
        if (!force && activePullInfos.isNotEmpty()) {
            Timber.d("Pull [$triggerSource] skipped: existing unique pull chain is already active")
            return activePullInfos.map { it.id }
        }

        Timber.i("Pull [$triggerSource] enqueued")
        val pullRunData = Data.Builder()
            .putString(pullTriggerSourceKey, triggerSource)
            .putLong(pullStartedAtKey, SystemClock.elapsedRealtime())
            .build()

        // StopTB pull chain:
        // Beneficiaries → Referrals + TB data → Mark complete
        val campMode = isCampModeEnabled(context)
        val pullWorkRequest = if (campMode) {
            campSyncRequestBuilder<PullFromAmritWorker>()
        } else {
            syncRequestBuilder<PullFromAmritWorker>()
        }.setInputData(pullRunData).addTag("pull_phase1_foundation").build()

        val afterFoundation = workManager.beginUniqueWork(
            pullWorkerUniqueName,
            if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            pullWorkRequest
        )

        val groupTB = listOf(
            (if (campMode) {
                campSyncRequestBuilder<PullTBFromAmritWorker>()
            } else {
                syncRequestBuilder<PullTBFromAmritWorker>()
            }).setInputData(pullRunData).addTag("pull_group5_tb").build(),
        )

        val setSyncCompleteWorker = OneTimeWorkRequestBuilder<UpdatePrefForPullCompleteWorker>()
            .setInputData(pullRunData)
            .build()

        val chainTB = afterFoundation.then(groupTB)

        chainTB.then(setSyncCompleteWorker).enqueue()
        return listOf(pullWorkRequest.id, setSyncCompleteWorker.id) + groupTB.map { it.id }
    }

    /** Convenience alias — camp check is already inside [triggerAmritPushWorker]. */
    fun triggerCampAwarePushWorker(context: Context, preferenceDao: PreferenceDao) {
        triggerAmritPushWorker(context)
    }

    /**
     * ABHA push — does NOT require camp mode, only needs internet.
     * Use this from ABHA creation flow.
     */
    fun triggerAbhaPushWorker(context: Context) {
        val workRequest = syncRequestBuilder<PushMapAbhatoBenficiaryWorker>()
            .addTag("push_group9_digital_health").build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "PUSH-ABHA-TO-AMRIT", ExistingWorkPolicy.APPEND_OR_REPLACE, workRequest
        )
    }

    fun triggerCampQuickPullIfConnected(
        context: Context,
        preferenceDao: PreferenceDao,
        force: Boolean = false
    ) {
        if (!preferenceDao.isCampModeEnabled() || !preferenceDao.isCampHubConnected()) return

        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastCampQuickPullAt < campQuickPullDebounceMs) return

        // Pull and push are safe to overlap, but a full pull competing with an
        // upload slows both on the camp hub. The auto-pull loop retries shortly.
        if (!force && getActiveUniqueWorkInfos(WorkManager.getInstance(context), pushWorkerUniqueName)
                .any { it.state == WorkInfo.State.RUNNING }
        ) {
            Timber.d("Pull [AUTO_PULL] skipped: push chain is running")
            return
        }

        lastCampQuickPullAt = now
        triggerAmritPullWorker(context, triggerSource = "AUTO_PULL")
    }

    fun startManualCampRefresh(
        context: Context,
        preferenceDao: PreferenceDao
    ): List<java.util.UUID> {
        manualCampRefreshInProgress = true
        Timber.i("Camp sync [MANUAL_REFRESH] requested by user")
        val pushIds = enqueuePushChain(context)
        val pullIds = triggerAmritPullWorker(
            context,
            force = true,
            triggerSource = "MANUAL_REFRESH"
        )
        return pushIds + pullIds
    }

    fun finishManualCampRefresh() {
        manualCampRefreshInProgress = false
    }

    fun isManualCampRefreshInProgress(): Boolean = manualCampRefreshInProgress

    fun cancelCampPullWorker(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(pullWorkerUniqueName)
    }

    fun triggerD2dSyncWorker(context: Context) {}

    fun triggerCbacPullWorker(context: Context) {
        val workRequest = syncRequestBuilder<CbacPullFromAmritWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            CbacPullFromAmritWorker.name, ExistingWorkPolicy.APPEND_OR_REPLACE, workRequest)
    }

    fun triggerCbacPushWorker(context: Context) {
        val workRequest = syncRequestBuilder<CbacPushToAmritWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            CbacPushToAmritWorker.name, ExistingWorkPolicy.APPEND_OR_REPLACE, workRequest)
    }

    fun triggerGenBenIdWorker(context: Context) {
        val workRequest = OneTimeWorkRequestBuilder<GenerateBenIdsWorker>()
            .setConstraints(GenerateBenIdsWorker.constraint).build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(GenerateBenIdsWorker.name, ExistingWorkPolicy.KEEP, workRequest)
    }

    fun triggerDownloadCardWorker(
        context: Context, fileName: String, otpTxnID: MutableLiveData<String?>
    ): LiveData<Operation.State> {
        val workRequest = syncRequestBuilder<DownloadCardWorker>()
            .setInputData(Data.Builder().apply {
                putString(DownloadCardWorker.file_name, fileName)
            }.build()).build()
        return WorkManager.getInstance(context).enqueueUniqueWork(
            DownloadCardWorker.name, ExistingWorkPolicy.REPLACE, workRequest).state
    }

    fun cancelAllWork(context: Context) {
        WorkManager.getInstance(context).cancelAllWork()
    }

    /**
     * KEEP, not REPLACE: REPLACE cancels a poll that is already running and starts it again from
     * the top, so a run that is part-way through its per-beneficiary network calls loses that work
     * and repeats the writes it has already done. With ~9 call sites plus the worker's own 60s
     * reschedule, runs were cancelling each other continuously (97 runs in 104 minutes, minimum
     * gap 0.1s). KEEP lets an in-flight run finish; the worker reschedules itself every 60s while
     * any order is still in progress, so nothing is lost — a trigger that arrives while a run is
     * pending is simply folded into that run instead of restarting it.
     */
    fun triggerDiagnosticResultPollWorker(context: Context) {
        val workRequest = OneTimeWorkRequestBuilder<DiagnosticResultPollWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            DiagnosticResultPollWorker.name, ExistingWorkPolicy.KEEP, workRequest
        )
    }

    fun triggerTrueNatDiagnosticResultPollWorker(context: Context) {
        triggerDiagnosticResultPollWorker(context)
    }

    fun triggerRifDiagnosticResultPollWorker(context: Context) {
        triggerDiagnosticResultPollWorker(context)
    }

    private fun diagnosticOrderPushWorkName(benId: Long) = "DIAGNOSTIC-ORDER-PUSH-$benId"

    private fun buildOrderPushRequest(benId: Long, testType: String) =
        OneTimeWorkRequestBuilder<DiagnosticOrderPushWorker>()
            .setInputData(
                Data.Builder()
                    .putLong(DiagnosticOrderPushWorker.KEY_BEN_ID, benId)
                    .putString(DiagnosticOrderPushWorker.KEY_TEST_TYPE, testType)
                    .build()
            ).build()

    /**
     * Pushes order creation (order/push) for one or more test types for the same beneficiary as
     * background work instead of awaited inline network calls — see [DiagnosticOrderPushWorker]
     * for why. Multiple call sites can push orders for the same beneficiary (the TB Screening
     * Form's initial referral, the X-ray-positive auto-TrueNat cascade in TBRepo.fetchOrderResult,
     * a manual retry) — createOrder()'s read-then-write plus syncTBSuspectedFromDiagnostics'
     * get-or-create have no transaction/lock, so two concurrent pushes for the same benId can
     * race and create duplicate orders/tb_suspected rows. Requests are always chained under one
     * unique work name per benId (APPEND_OR_REPLACE — appends to whatever's still in flight,
     * starts fresh otherwise), so every push for a beneficiary is fully serialized and, when more
     * than one testType is passed, runs in the given order (e.g. X-ray before TrueNat).
     */
    fun triggerDiagnosticOrderPushWorkers(context: Context, benId: Long, testTypes: List<String>) {
        if (testTypes.isEmpty()) return
        val requests = testTypes.map { testType -> buildOrderPushRequest(benId, testType) }
        val workManager = WorkManager.getInstance(context)
        var continuation = workManager.beginUniqueWork(
            diagnosticOrderPushWorkName(benId), ExistingWorkPolicy.APPEND_OR_REPLACE, requests.first()
        )
        for (request in requests.drop(1)) {
            continuation = continuation.then(request)
        }
        continuation.enqueue()
    }

    /**
     * Whether a [DiagnosticOrderPushWorker] chain is currently enqueued/running/blocked for this
     * beneficiary. Used to guard entry points that call tbRepo.createOrder() directly (e.g.
     * AllBenViewModel.retryTest) instead of going through the chain, so they don't race a
     * still-active automatic push for the same benId.
     */
    fun isDiagnosticOrderPushActive(context: Context, benId: Long): Boolean {
        val workManager = WorkManager.getInstance(context)
        return getActiveUniqueWorkInfos(workManager, diagnosticOrderPushWorkName(benId)).isNotEmpty()
    }
}
