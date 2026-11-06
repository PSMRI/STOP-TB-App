package org.piramalswasthya.stoptb.helpers

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext
import timber.log.Timber

internal class SyncTrace(val runId: String, val direction: String, val accountScope: String? = null) :
    AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<SyncTrace>
}

internal class SyncDataResult {
    val reported = AtomicBoolean(false)
    val failed = AtomicBoolean(false)
    private val deferred = AtomicBoolean(false)

    fun record(outcome: String) {
        reported.set(true)
        if (outcome == "DEFERRED") deferred.set(true)
        if (outcome in setOf("FAILURE", "PARTIAL_FAILURE", "NETWORK_FAILURE", "RETRY", "PENDING")) {
            failed.set(true)
        }
    }

    fun outcome() = when {
        failed.get() -> "PARTIAL_FAILURE"
        deferred.get() -> "DEFERRED_RECORDS_REMAIN"
        reported.get() -> "REPORTED_MODULES_OK"
        else -> "NOT_REPORTED"
    }
}

internal fun syncPushOutcome(pending: Int, synced: Int): String = when {
    pending == 0 -> "NO_PENDING_DATA"
    synced == pending -> "SUCCESS"
    synced == 0 -> "FAILURE"
    else -> "PARTIAL_FAILURE"
}

internal fun syncRunOutcome(states: List<String>, dataOutcome: String): String = when {
    states.any { it !in setOf("SUCCEEDED", "FAILED", "CANCELLED") } -> "IN_PROGRESS"
    "FAILED" in states -> if ("SUCCEEDED" in states) "PARTIAL_FAILURE" else "FAILURE"
    "CANCELLED" in states -> "CANCELLED"
    dataOutcome == "PARTIAL_FAILURE" -> "PARTIAL_FAILURE"
    dataOutcome == "DEFERRED_RECORDS_REMAIN" -> "DEFERRED"
    else -> "WORKERS_SUCCEEDED"
}

/** Counts refer to the actual upload selection, not every row in the database. */
internal suspend fun logSyncModule(module: String, direction: String, outcome: String,
                                  vararg fields: Pair<String, Any?>) {
    val trace = currentCoroutineContext()[SyncTrace]
    val prefix = "SYNC MODULE | runId=${trace?.runId ?: "DIRECT_CALL"} | direction=$direction | module=$module"
    val details = fields.joinToString(" | ") { (key, value) -> "$key=${value ?: "UNKNOWN"}" }
    // Logging must not change the result of a sync operation.
    runCatching {
        trace?.let { SyncDiagnostics.recordModule(it, outcome) }
        val reason = fields.firstOrNull { it.first == "reason" }?.second?.toString()
        if (outcome in setOf("FAILURE", "PARTIAL_FAILURE", "RETRY") && !reason.isNullOrBlank()) {
            SyncDailyStore.record(module, listOf("UNKNOWN"), direction, "FAILURE", reason)
        }
        SyncSummaryStore.record("$prefix | outcome=$outcome | $details")
        Timber.tag("SyncTrace").i("%s | outcome=%s | %s", prefix, outcome, details)
    }
}

/** Record identifiers and fixed reason codes only; never include a patient payload. */
internal suspend fun logSyncSkip(module: String, direction: String, reason: String,
                                 vararg fields: Pair<String, Any?>) {
    val trace = currentCoroutineContext()[SyncTrace]
    runCatching {
        val message = "SYNC SKIP | runId=${trace?.runId ?: "DIRECT_CALL"} | direction=$direction" +
            " | module=$module | reason=$reason | " +
            fields.joinToString(" | ") { (key, value) -> "$key=${value ?: "UNKNOWN"}" }
        SyncSummaryStore.record(message)
        Timber.tag("SyncTrace").i("%s", message)
    }
}
