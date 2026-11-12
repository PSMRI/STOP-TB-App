package org.piramalswasthya.stoptb.helpers

internal fun summaryFields(report: String): Map<String, String> = report.split(" | ").mapNotNull {
    val index = it.indexOf('=')
    if (index <= 0) null else it.substring(0, index) to it.substring(index + 1)
}.toMap()

internal fun summaryStatus(outcome: String?): String = when (outcome) {
    "FAILURE", "PARTIAL_FAILURE", "NETWORK_FAILURE", "RETRY", "PENDING" -> "Needs attention"
    "WORKERS_SUCCEEDED" -> "Workers completed"
    "SUCCESS" -> "Completed"
    "NO_PENDING_DATA" -> "Nothing to upload"
    "NO_RECORDS_RETURNED" -> "No server records"
    "DEFERRED" -> "Pending / deferred"
    "CANCELLED" -> "Cancelled"
    null -> "Running / completion not reported"
    else -> "Status not confirmed"
}

internal data class SummaryModule(val name: String, val status: String, val saved: Long?,
    val failed: Long?, val skipped: Long?, val remaining: Long?, val reports: List<String>)

internal fun summaryModules(run: SyncSummaryRun): List<SummaryModule> = run.reports
    .filter { summaryFields(it)["module"] != null }.groupBy { summaryFields(it).getValue("module") }
    .map { (module, reports) ->
        // Repeated page summaries are snapshots, not additional records.
        val latest = reports.associateBy { summaryFields(it)["page"] ?: "module" }.values.map(::summaryFields)
        fun count(vararg names: String): Long? {
            val values = latest.map { fields -> names.firstNotNullOfOrNull { name -> fields[name]?.toLongOrNull()?.takeIf { it >= 0 } } }
            return if (values.any { it == null }) null else values.sumOf { it ?: 0 }
        }
        val attention = reports.any { summaryStatus(summaryFields(it)["outcome"]) == "Needs attention" }
        SummaryModule(module, if (attention) "Needs attention" else summaryStatus(latest.lastOrNull()?.get("outcome")),
            count("locallyMarkedSynced", "saved", "synced", "registered"), count("failed"), count("skipped"), count("remaining"), reports)
    }.sortedBy { it.status != "Needs attention" }

internal data class SummarySkipGroup(val module: String, val normal: Boolean,
    val description: String, val reports: List<String>)

internal fun summaryBlockedSkipGroups(run: SyncSummaryRun): List<SummarySkipGroup> =
    summarySkipGroups(run).filterNot { it.normal }

internal fun summarySkipGroups(run: SyncSummaryRun): List<SummarySkipGroup> = run.skippedReports
    .groupBy { summaryFields(it).let { fields -> (fields["module"] ?: "UNKNOWN") to (fields["reason"] ?: "UNKNOWN") } }
    .map { (key, reports) ->
        val reason = key.second
        val normal = reason in setOf("SERVER_VERSION_NOT_NEWER", "SERVER_VERSION_NOT_NEWER_AND_FIELDS_UNCHANGED", "DUPLICATE_HOUSEHOLD_IN_RESPONSE")
        val description = when (reason) {
            "SERVER_VERSION_NOT_NEWER", "SERVER_VERSION_NOT_NEWER_AND_FIELDS_UNCHANGED" -> "Already up to date / server version not newer"
            "DUPLICATE_HOUSEHOLD_IN_RESPONSE" -> "Duplicate household in server response"
            "LOCAL_UNSYNCED_EDITS_PROTECTED", "LOCAL_PENDING_EDITS_PROTECTED" -> "Local changes protected; upload may still be pending"
            "SERVER_REGISTRATION_ID_NOT_AVAILABLE" -> "Waiting for server registration ID"
            "BENEFICIARY_NOT_FOUND_LOCALLY" -> "Beneficiary is not available on this device"
            "BENEFICIARY_OR_HOUSEHOLD_ID_MISSING" -> "Household or beneficiary ID missing; check whether this is a non-household record"
            "BENEFICIARY_ID_MISSING", "BENEFICIARY_REGISTRATION_ID_MISSING" -> "Required beneficiary ID missing"
            "DURATION_NOT_POSITIVE_OR_FREQUENCY_OR_UNIT_BLANK" -> "Invalid medicine duration, frequency or unit"
            else -> reason.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
        }
        SummarySkipGroup(key.first, normal, description, reports)
    }.sortedBy { it.normal }
