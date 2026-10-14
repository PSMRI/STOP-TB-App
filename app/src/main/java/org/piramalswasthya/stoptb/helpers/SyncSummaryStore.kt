package org.piramalswasthya.stoptb.helpers

data class SyncSummaryRun(
    val runId: String,
    val direction: String,
    val startedAt: Long,
    val details: Map<String, String>,
    val reports: List<String>,
    val skippedReports: List<String> = emptyList()
)

/** Bounded process-session history, independent of the noisy raw-log buffer. */
class SyncSummaryHistory(private val capacity: Int = 100) {
    private val runs = linkedMapOf<String, SyncSummaryRun>()

    @Synchronized
    fun record(message: String, timestamp: Long = System.currentTimeMillis()) {
        if (!message.startsWith("SYNC ")) return
        val fields = message.split(" | ").drop(1).mapNotNull {
            val index = it.indexOf('=')
            if (index <= 0) null else it.substring(0, index) to it.substring(index + 1)
        }.toMap()
        val id = fields["runId"]?.takeIf { it.isNotBlank() } ?: return
        val direction = fields["direction"]?.takeIf { it == "PUSH" || it == "PULL" } ?: return
        val key = "$id/$direction"
        val old = runs[key] ?: SyncSummaryRun(id, direction, timestamp, emptyMap(), emptyList())
        val isSkip = message.startsWith("SYNC SKIP |")
        val report = when {
            isSkip -> fields.filterKeys { it !in setOf("runId", "direction") }
                .entries.joinToString(" | ") { "${it.key}=${it.value}" }
            message.startsWith("SYNC MODULE |") -> fields.filterKeys {
                it !in setOf("runId", "direction")
            }.entries.joinToString(" | ") { "${it.key}=${it.value}" }
            message.startsWith("SYNC WORKER END |") && fields["outcome"] != "SUCCESS" ->
                "worker=${fields["worker"] ?: "UNKNOWN"} | outcome=${fields["outcome"] ?: "UNKNOWN"}" +
                    " | reason=${fields["reason"] ?: "Not reported by worker"}"
            else -> null
        }
        // Worker success is not proof of record-level success. Only the run END sets its outcome.
        val metadata = if (isSkip || message.startsWith("SYNC WORKER END |") || message.startsWith("SYNC MODULE |")) {
            fields.filterKeys { it in setOf("userId", "villageId", "serviceMapId") }
        } else fields
        val timedReport = report?.let { "eventTime=$timestamp | $it" }
        val endTime = if (message.startsWith("SYNC END |")) mapOf("endedAt" to timestamp.toString()) else emptyMap()
        runs[key] = old.copy(details = old.details + metadata + endTime,
            reports = if (timedReport == null || isSkip) old.reports else (old.reports + timedReport).takeLast(100),
            skippedReports = if (timedReport == null || !isSkip) old.skippedReports else
                (old.skippedReports + timedReport).takeLast(100))
        while (runs.size > capacity.coerceAtLeast(1)) runs.remove(runs.keys.first())
    }

    @Synchronized
    fun snapshot(): List<SyncSummaryRun> = runs.values.toList().asReversed()
}

object SyncSummaryStore {
    private val history = SyncSummaryHistory()
    fun record(message: String) { runCatching { history.record(message) } }
    fun snapshot(): List<SyncSummaryRun> = history.snapshot()
}
