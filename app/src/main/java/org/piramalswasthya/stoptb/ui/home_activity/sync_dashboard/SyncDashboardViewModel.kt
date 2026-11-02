package org.piramalswasthya.stoptb.ui.home_activity.sync_dashboard

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.database.room.InAppDb
import org.piramalswasthya.stoptb.database.room.dao.SyncDao
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.database.shared_preferences.SyncRetryStore
import org.piramalswasthya.stoptb.helpers.Languages
import org.piramalswasthya.stoptb.helpers.SyncLogExporter
import org.piramalswasthya.stoptb.helpers.SyncLogManager
//import org.piramalswasthya.stoptb.helpers.isCounsellingOfficerRole
import org.piramalswasthya.stoptb.helpers.RoleManager
import org.piramalswasthya.stoptb.model.FailedWorkerInfo
import org.piramalswasthya.stoptb.model.SyncLogEntry
import org.piramalswasthya.stoptb.model.SyncStatusCache
import org.piramalswasthya.stoptb.utils.HelperUtil.getLocalizedResources
import org.piramalswasthya.stoptb.work.BasePushWorker
import org.piramalswasthya.stoptb.work.WorkerUtils
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SyncDashboardViewModel @Inject constructor(
    private val syncDao: SyncDao,
    private val preferenceDao: PreferenceDao,
    private val syncLogManager: SyncLogManager,
    private val syncLogExporter: SyncLogExporter,
    private val roleManager: RoleManager,
    private val syncRetryStore: SyncRetryStore,
    private val database: InAppDb,
    application: Application
) : AndroidViewModel(application) {

    // Tab 1: Status
    private val selectedVillage get() = preferenceDao.getLocationRecord()?.village?.id ?: 0
    val syncStatus: Flow<List<SyncStatusCache>> get() = syncDao.getSyncStatus(selectedVillage)

    val overallProgress: Flow<Pair<Int, Int>> = syncStatus.map { list ->
        val synced = list.filter { it.syncState == org.piramalswasthya.stoptb.database.room.SyncState.SYNCED }
            .sumOf { it.count }
        val total = list.sumOf { it.count }
        Pair(synced, total)
    }

    val workerStates: LiveData<List<WorkInfo>> = WorkManager.getInstance(application)
        .getWorkInfosLiveData(WorkQuery.fromUniqueWorkNames(WorkerUtils.pushWorkerUniqueName, WorkerUtils.pullWorkerUniqueName))

    val failedWorkerDetails: LiveData<List<FailedWorkerInfo>> = workerStates.map { workInfoList ->
        workInfoList
            .filter { it.state == WorkInfo.State.FAILED }
            .map { workInfo ->
                val outputName = workInfo.outputData.getString(BasePushWorker.KEY_WORKER_NAME)
                val outputError = workInfo.outputData.getString(BasePushWorker.KEY_ERROR)
                // Fallback: extract class name from tags if outputData wasn't set
                val name = outputName ?: workInfo.tags
                    .firstOrNull { it.startsWith("org.piramalswasthya.stoptb.work.") }
                    ?.substringAfterLast(".")
                    ?: "Unknown Worker"
                // Cascade detection: when WorkManager auto-fails a downstream worker
                // due to an upstream failure, the worker never runs — so outputData
                // is completely empty (no keys at all).
                val isCascadeFailure = workInfo.outputData.keyValueMap.isEmpty()
                val error = when {
                    outputError != null -> outputError
                    isCascadeFailure -> "Blocked by earlier failure in sync chain"
                    else -> "No error details available"
                }
                FailedWorkerInfo(
                    workerName = name,
                    error = error
                )
            }
    }

    // Records the server has not accepted yet. They stay UNSYNCED and are
    // retried automatically; listed here so they are not invisible.
    private val _needsAttention = MutableLiveData<List<FailedWorkerInfo>>(emptyList())
    val needsAttention: LiveData<List<FailedWorkerInfo>> = _needsAttention

    fun refreshNeedsAttention(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val items = syncRetryStore.entries().map { e ->
                FailedWorkerInfo(
                    workerName = "${entityLabel(context, e.entity)} #${e.id}",
                    error = context.getString(
                        R.string.sync_dashboard_retry_detail,
                        formatAge(context, now - e.firstFailedAt), e.attempts, e.lastError
                    )
                )
            }.toMutableList()
            val orphaned = database.counsellingFormResponseDao().countOrphanedTempIdResponses()
            if (orphaned > 0) {
                items += FailedWorkerInfo(
                    workerName = context.getString(R.string.sync_dashboard_orphan_responses_title),
                    error = context.getString(R.string.sync_dashboard_orphan_responses_detail, orphaned)
                )
            }
            _needsAttention.postValue(items)
        }
    }

    /** Makes every waiting record due now and starts a push. */
    fun retryNow(context: Context) {
        syncRetryStore.resetBackoff()
        WorkerUtils.triggerAmritPushWorker(context.applicationContext)
    }

    private fun entityLabel(context: Context, entity: String): String = when (entity) {
        SyncRetryStore.ENTITY_BENEFICIARY -> context.getString(R.string.sync_entity_beneficiary)
        SyncRetryStore.ENTITY_REGISTRATION -> context.getString(R.string.sync_entity_registration)
        SyncRetryStore.ENTITY_TB_DIAGNOSTICS -> context.getString(R.string.sync_entity_tb_diagnostics)
        SyncRetryStore.ENTITY_TB_SUSPECTED -> context.getString(R.string.sync_entity_tb_suspected)
        SyncRetryStore.ENTITY_TB_CONFIRMED -> context.getString(R.string.sync_entity_tb_confirmed)
        else -> entity
    }

    private fun formatAge(context: Context, millis: Long): String {
        val minutes = (millis / 60_000L).toInt().coerceAtLeast(0)
        return when {
            minutes < 60 -> context.getString(R.string.sync_dashboard_age_minutes, minutes)
            minutes < 48 * 60 -> context.getString(R.string.sync_dashboard_age_hours, minutes / 60)
            else -> context.getString(R.string.sync_dashboard_age_days, minutes / (24 * 60))
        }
    }

    // Tab 2: Logs
    val syncLogs: StateFlow<List<SyncLogEntry>> = syncLogManager.logs

    fun clearLogs() = syncLogManager.clearLogs()

    // Localization helpers (same pattern as SyncViewModel)
    val lang = preferenceDao.getCurrentLanguage()

    fun getLocalNames(context: Context): Array<String> {
        return getLocalizedResources(context, lang).getStringArray(R.array.sync_records)
    }

    fun getEnglishNames(context: Context): Array<String> {
        return getLocalizedResources(context, Languages.ENGLISH).getStringArray(R.array.sync_records)
    }

    // Legacy, kept for reference:
//    fun isCounsellingOfficerRole():Boolean{
//        return preferenceDao.getLoggedInUser()?.role.isCounsellingOfficerRole()
//    }
    fun isCounsellingOfficerRole(): Boolean {
        val result = roleManager.privilegesUnion().syncShowCounsellingStatusRow
        Timber.d("RoleManager: syncShowCounsellingStatusRow=$result")
        return result
    }

    // Tab 2: Log export
    private val _exportIntent = MutableStateFlow<Intent?>(null)
    val exportIntent: StateFlow<Intent?> = _exportIntent.asStateFlow()

    private val _exportEmpty = MutableStateFlow(false)
    val exportEmpty: StateFlow<Boolean> = _exportEmpty.asStateFlow()

    fun exportLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            val intent = syncLogExporter.createShareIntent(getApplication())
            if (intent != null) {
                _exportIntent.value = intent
            } else {
                _exportEmpty.value = true
            }
        }
    }

    fun onExportHandled() {
        _exportIntent.value = null
    }

    fun onExportEmptyHandled() {
        _exportEmpty.value = false
    }
}
