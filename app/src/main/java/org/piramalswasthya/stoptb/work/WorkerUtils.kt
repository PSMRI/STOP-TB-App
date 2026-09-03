package org.piramalswasthya.stoptb.work

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
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
    const val pullTriggerSourceKey = "pull_trigger_source"
    const val pullStartedAtKey = "pull_started_at"
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

    fun triggerAmritPushWorker(context: Context): List<java.util.UUID> {
        if (!hasLoggedInUser(context)) {
            Timber.w("Push worker skipped: no logged-in user is stored yet")
            return emptyList()
        }

        // Block all data push until camp mode is active AND hub is connected
        val prefs = PreferenceManager.getInstance(context)
        val campKey = context.getString(R.string.PREF_camp_mode_enabled)
        if (!prefs.getBoolean(campKey, false)) {
            Timber.d("Push worker skipped: camp mode is disabled")
            return emptyList()
        }
        val hubConnectedKey = context.getString(R.string.PREF_camp_hub_connected)
        if (!prefs.getBoolean(hubConnectedKey, false)) {
            Timber.d("Push worker skipped: camp hub is disconnected")
            return emptyList()
        }

        val isCampHubSyncActive = isCampHubSyncActive(context)
        val workManager = WorkManager.getInstance(context)
        val activePushInfos = getActiveUniqueWorkInfos(workManager, pushWorkerUniqueName)
        if (activePushInfos.any { it.state == WorkInfo.State.RUNNING }) {
            Timber.d("Push worker skipped: existing unique push chain is already running")
            return activePushInfos.map { it.id }
        }
        if (activePushInfos.isNotEmpty() && isCampHubSyncActive) {
            Timber.d("Replacing stale unique push chain for camp sync")
            workManager.cancelUniqueWork(pushWorkerUniqueName)
        }

        // StopTB push chain:
        // Registration → NCD Referrals + TB data + ABHA
        val registration = if (isCampHubSyncActive) {
            campSyncRequestBuilder<PushToAmritWorker>()
        } else {
            syncRequestBuilder<PushToAmritWorker>()
        }.addTag("push_group1_registration").build()

        val afterRegistration = workManager.beginUniqueWork(
            pushWorkerUniqueName, ExistingWorkPolicy.KEEP, registration)

        val groupTB = listOf(
            (if (isCampHubSyncActive) {
                campSyncRequestBuilder<PushTBToAmritWorker>()
            } else {
                syncRequestBuilder<PushTBToAmritWorker>()
            }).addTag("push_group5_tb").build(),
        ) 

        val groupAbha = if (isCampHubSyncActive) {
            campSyncRequestBuilder<PushMapAbhatoBenficiaryWorker>()
        } else {
            syncRequestBuilder<PushMapAbhatoBenficiaryWorker>()
        }.addTag("push_group9_digital_health").build()

        val chainTB = afterRegistration.then(groupTB)
        val chainAbha = afterRegistration.then(listOf(groupAbha))

        WorkContinuation.combine(listOf(chainTB, chainAbha)).enqueue()
        return listOf(registration.id, groupAbha.id) + groupTB.map { it.id }
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

        val isCampHubSyncActive = isCampHubSyncActive(context)
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
        val pullWorkRequest = if (isCampHubSyncActive) {
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
            (if (isCampHubSyncActive) {
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

        lastCampQuickPullAt = now
        triggerAmritPullWorker(context, triggerSource = "AUTO_PULL")
    }

    fun startManualCampRefresh(
        context: Context,
        preferenceDao: PreferenceDao
    ): List<java.util.UUID> {
        manualCampRefreshInProgress = true
        Timber.i("Camp sync [MANUAL_REFRESH] requested by user")
        val pushIds = triggerAmritPushWorker(context)
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

    fun triggerDiagnosticResultPollWorker(context: Context) {
        val workRequest = OneTimeWorkRequestBuilder<DiagnosticResultPollWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            DiagnosticResultPollWorker.name, ExistingWorkPolicy.REPLACE, workRequest
        )
    }

    fun triggerTrueNatDiagnosticResultPollWorker(context: Context) {
        triggerDiagnosticResultPollWorker(context)
    }

    fun triggerRifDiagnosticResultPollWorker(context: Context) {
        triggerDiagnosticResultPollWorker(context)
    }
}
