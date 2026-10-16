package org.piramalswasthya.stoptb.helpers

/** Unknown-source raw logs stay in the file, never guessed to be manual activity. */
internal class SyncLiveLogFilter(private val capacity: Int = 256) {
    private val manualRuns = linkedSetOf<Pair<String, String>>()

    fun accepts(tag: String, message: String): Boolean {
        if (tag == "SyncApi" && message.startsWith("HTTP EXCHANGE | ")) {
            return summaryFields(message.substringBefore('\n'))["trigger"] in setOf("MANUAL_REFRESH", "DIRECT_SAVE")
        }
        if (tag == "WorkerUtils" && (message.contains("[MANUAL_REFRESH]") || message.contains("[DIRECT_SAVE]"))) return true
        if (tag != "SyncTrace") return false
        if (message.startsWith("LOCAL SAVE | ")) return true
        if (!message.startsWith("SYNC ")) return false
        val fields = summaryFields(message)
        val run = fields["runId"]?.takeIf { it.isNotBlank() } ?: return false
        if (run == "DIRECT_CALL") return false
        val direction = fields["direction"] ?: return false
        val key = run to direction
        fields["trigger"]?.let { trigger ->
            if (trigger == "MANUAL_REFRESH" || trigger == "DIRECT_SAVE") {
                manualRuns.add(key)
                while (manualRuns.size > capacity.coerceAtLeast(1)) manualRuns.remove(manualRuns.first())
            } else manualRuns.remove(key)
        }
        return key in manualRuns
    }
}
