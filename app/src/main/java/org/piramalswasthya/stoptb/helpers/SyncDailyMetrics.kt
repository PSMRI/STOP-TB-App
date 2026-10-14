package org.piramalswasthya.stoptb.helpers

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

internal enum class SyncDayFilter { TODAY, YESTERDAY, ALL }
internal fun syncDay(timestamp: Long, zone: TimeZone = TimeZone.getDefault()): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = zone }.format(java.util.Date(timestamp))
internal fun selectedSyncDay(filter: SyncDayFilter, now: Long, zone: TimeZone = TimeZone.getDefault()): String? {
    if (filter == SyncDayFilter.ALL) return null
    val calendar = Calendar.getInstance(zone).apply { timeInMillis = now }
    if (filter == SyncDayFilter.YESTERDAY) calendar.add(Calendar.DATE, -1)
    return syncDay(calendar.timeInMillis, zone)
}
internal data class SyncDailyEvent(val module: String, val recordId: String, val action: String,
    val outcome: String, val reason: String, val runId: String, val at: Long, val day: String)
internal data class SyncDailyModule(val name: String, val registered: Int, val saved: Int,
    val synced: Int, val failed: Int, val failures: List<SyncDailyEvent>, val unidentifiedFailure: Boolean = false,
    val pending: Int = 0)
internal val SYNC_DAILY_MODULES = listOf("BENEFICIARY", "HOUSEHOLD", "TB_SCREENING", "GENERAL_OPD",
    "TB_DIAGNOSTICS", "TB_SUSPECTED", "TB_CONFIRMED", "GENERAL_EXAMINATION", "COUNSELLING")

/** Select local-save cohorts by day, then resolve their latest upload state across days. */
internal fun syncDailyModules(events: List<SyncDailyEvent>, day: String?): List<SyncDailyModule> {
    val selected = events.filter { day == null || it.day == day }
    return (SYNC_DAILY_MODULES + selected.map { it.module }).distinct().map { module ->
        val rows = events.filter { it.module == module && it.recordId != "UNKNOWN" }.groupBy { it.recordId }
        val cohort = selected.filter { it.module == module && it.recordId != "UNKNOWN" &&
            it.action == "SAVE" && it.outcome == "SUCCESS" }.map { it.recordId }.toSet()
        var synced = 0
        val failures = mutableListOf<SyncDailyEvent>()
        for (id in cohort) {
            val record = rows.getValue(id)
            // A prior acknowledgement cannot confirm a later local edit.
            val savedAt = record.filter { it.action == "SAVE" && it.outcome == "SUCCESS" }.maxOf { it.at }
            val status = record.filter { it.at >= savedAt &&
                ((it.action == "PUSH" && it.outcome in setOf("SUCCESS", "FAILURE")) ||
                    (it.action == "REGISTER" && it.outcome == "FAILURE")) }
                .maxWithOrNull(compareBy<SyncDailyEvent> { it.at }.thenBy { it.outcome == "FAILURE" })
            if (status?.outcome == "SUCCESS") synced++
            else if (status?.outcome == "FAILURE") failures.add(status)
        }
        SyncDailyModule(module, 0, cohort.size, synced, failures.size, failures,
            selected.any { it.module == module && it.recordId == "UNKNOWN" && it.outcome == "FAILURE" },
            cohort.size - synced - failures.size)
    }
}
