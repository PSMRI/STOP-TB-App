package org.piramalswasthya.stoptb.ui.home_activity.sync_dashboard

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
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
import kotlinx.coroutines.CancellationException
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.database.room.dao.SyncDao
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
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

    // Tab 2: Logs
    val syncLogs: StateFlow<List<SyncLogEntry>> = syncLogManager.interactiveLogs

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

    // Shared export for Logs and Summary.
    private val _exportIntent = MutableStateFlow<Intent?>(null)
    val exportIntent: StateFlow<Intent?> = _exportIntent.asStateFlow()

    private val _exportEmpty = MutableStateFlow(false)
    val exportEmpty: StateFlow<Boolean> = _exportEmpty.asStateFlow()

    private val _shareSummary = MutableStateFlow<String?>(null)
    val shareSummary: StateFlow<String?> = _shareSummary.asStateFlow()
    private var shareSummaryScope: String? = null
    internal fun summaryScope(): String {
        val user = preferenceDao.getLoggedInUser()
        return "${user?.userId}/${user?.serviceMapId}/$selectedVillage"
    }
    fun updateShareSummary(summary: String?, scope: String? = null) {
        shareSummaryScope = scope
        _shareSummary.value = summary
    }
    private val _exportBusy = MutableStateFlow(false)
    val exportBusy: StateFlow<Boolean> = _exportBusy.asStateFlow()

    fun exportLogs(includeLogs: Boolean = true, includeSummary: Boolean = false) {
        if (_exportBusy.value || (!includeLogs && !includeSummary)) return
        val summary = if (includeSummary) _shareSummary.value else null
        if (includeSummary && summary == null) return
        if (includeSummary && shareSummaryScope != summaryScope()) {
            updateShareSummary(null)
            _exportEmpty.value = true
            return
        }
        _exportBusy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val intent = syncLogExporter.createShareIntent(getApplication(), includeLogs, summary)
                if (intent != null) {
                    _exportIntent.value = intent
                } else {
                    _exportEmpty.value = true
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _exportEmpty.value = true
            } finally {
                _exportBusy.value = false
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
