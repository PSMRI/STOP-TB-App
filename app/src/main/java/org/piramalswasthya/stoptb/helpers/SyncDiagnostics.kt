package org.piramalswasthya.stoptb.helpers

import android.content.Context
import android.net.Uri
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.WorkQuery
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.piramalswasthya.stoptb.BuildConfig
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.model.AppRole
import timber.log.Timber

/** Local diagnostics only: no network headers, payloads, sync state, or scheduling changes. */
object SyncDiagnostics {
    const val RUN_ID = "sync_run_id"
    const val TRIGGER = "sync_trigger"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val results = ConcurrentHashMap<String, SyncDataResult>()
    @Volatile private var activeRole: String = "UNKNOWN"

    fun setActiveRole(role: AppRole) { activeRole = role.name }

    @Synchronized
    private fun instanceId(context: Context): String {
        val prefs = context.getSharedPreferences("sync_diagnostics", Context.MODE_PRIVATE)
        return prefs.getString("app_instance_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("app_instance_id", it).apply()
        }
    }

    fun identity(context: Context): String = runCatching {
        val pref = PreferenceDao(context.applicationContext)
        val user = pref.getLoggedInUser()
        val roles = AppRole.resolveAssignedRoles(user?.assignedRoleScreenNames.orEmpty())
            .ifEmpty { listOfNotNull(user?.role.toLegacyAppRole()) }
        val hub = Uri.parse(pref.getCampHubUrl())
        val address = "${hub.scheme}://${hub.host}${if (hub.port >= 0) ":${hub.port}" else ""}"
        "userId=${user?.userId ?: "UNKNOWN"} | assignedRoles=${roles.joinToString(",") { it.name }}" +
            " | activeRole=$activeRole | appInstanceId=${instanceId(context)} | appVersion=${BuildConfig.VERSION_NAME}" +
            " | hub=$address | villageId=${pref.getLocationRecord()?.village?.id ?: "UNKNOWN"}" +
            " | serviceMapId=${user?.serviceMapId ?: "UNKNOWN"}"
    }.getOrDefault("identity=UNAVAILABLE")

    private fun emit(message: String) {
        SyncSummaryStore.record(message)
        runCatching { Timber.tag("SyncTrace").i("%s", message) }
    }

    internal fun recordModule(trace: SyncTrace, outcome: String) {
        results["${trace.runId}/${trace.direction}"]?.record(outcome)
    }

    fun prepareRun(runId: String, direction: String) {
        results.putIfAbsent("$runId/$direction", SyncDataResult())
    }

    fun observeRun(context: Context, runId: String, direction: String, trigger: String, ids: List<UUID>) {
        if (ids.isEmpty()) return
        val key = "$runId/$direction"
        results.putIfAbsent(key, SyncDataResult())
        val app = context.applicationContext
        val start = android.os.SystemClock.elapsedRealtime()
        emit("SYNC START | runId=$runId | direction=$direction | trigger=$trigger | ${identity(app)}")
        scope.launch {
            try {
                val manager = WorkManager.getInstance(app)
                // React to local state changes, without polling or changing scheduled work.
                val infos = withTimeoutOrNull(TimeUnit.HOURS.toMillis(12)) {
                    manager.getWorkInfosFlow(WorkQuery.fromIds(ids)).first { work ->
                        work.size == ids.size && work.all { it.state.isFinished }
                    }
                }
                if (infos == null) {
                    emit("SYNC OBSERVATION | runId=$runId | direction=$direction | outcome=OBSERVATION_TIMEOUT")
                } else {
                    val states = infos.groupingBy { it.state.name }.eachCount()
                    val dataOutcome = results[key]?.outcome() ?: "NOT_REPORTED"
                    val outcome = syncRunOutcome(infos.map { it.state.name }, dataOutcome)
                    emit("SYNC END | runId=$runId | direction=$direction | workerStates=$states" +
                        " | outcome=$outcome | dataOutcome=$dataOutcome" +
                        " | durationMs=${android.os.SystemClock.elapsedRealtime() - start}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emit("SYNC OBSERVATION | runId=$runId | direction=$direction | outcome=UNAVAILABLE | reason=${e.javaClass.simpleName}")
            } finally {
                results.remove(key)
            }
        }
    }

    internal suspend fun worker(context: Context, input: Data, name: String, workId: UUID,
                                attempt: Int, direction: String,
                                block: suspend () -> ListenableWorker.Result): ListenableWorker.Result {
        val runId = input.getString(RUN_ID) ?: workId.toString()
        return withContext(SyncTrace(runId, direction, SyncDailyStore.accountScope(context))) {
            emit("SYNC WORKER START | runId=$runId | direction=$direction | worker=$name | workId=$workId | attempt=$attempt" +
                " | trigger=${input.getString(TRIGGER) ?: "DIRECT"} | ${identity(context)}")
            try {
                val result = block()
                val outcome = when (result) {
                    is ListenableWorker.Result.Success -> "SUCCESS"
                    is ListenableWorker.Result.Retry -> "RETRY"
                    else -> "FAILURE"
                }
                emit("SYNC WORKER END | runId=$runId | direction=$direction | worker=$name | outcome=$outcome")
                result
            } catch (e: CancellationException) {
                emit("SYNC WORKER END | runId=$runId | direction=$direction | worker=$name | outcome=CANCELLED")
                throw e
            } catch (e: Exception) {
                emit("SYNC WORKER END | runId=$runId | direction=$direction | worker=$name | outcome=FAILURE | reason=${e.javaClass.simpleName}")
                throw e
            }
        }
    }
}
