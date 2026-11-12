package org.piramalswasthya.stoptb.ui.home_activity.sync_dashboard

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.DateFormat
import java.util.Date
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import org.piramalswasthya.stoptb.database.room.InAppDb
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.helpers.SyncSummaryRun
import org.piramalswasthya.stoptb.helpers.SyncSummaryStore
import org.piramalswasthya.stoptb.helpers.summaryFields
import org.piramalswasthya.stoptb.helpers.summaryStatus
import org.piramalswasthya.stoptb.helpers.summaryModules
import org.piramalswasthya.stoptb.helpers.summaryBlockedSkipGroups
import org.piramalswasthya.stoptb.helpers.SyncDailyStore
import org.piramalswasthya.stoptb.helpers.SyncDayFilter
import org.piramalswasthya.stoptb.helpers.SyncDailyModule
import org.piramalswasthya.stoptb.helpers.selectedSyncDay
import org.piramalswasthya.stoptb.helpers.villageSummaryRuns

@AndroidEntryPoint
class SyncSummaryTabFragment : Fragment(R.layout.fragment_sync_summary_tab) {
    private val viewModel: SyncDashboardViewModel by viewModels({ requireParentFragment() })
    @Inject lateinit var database: InAppDb
    @Inject lateinit var preferences: PreferenceDao
    private data class Row(val title: String, val body: String, val attention: Boolean = false,
        val action: String? = null, val details: String? = null, val chart: List<SyncDailyModule>? = null,
        val visual: SummaryCardVisual? = null)
    private var dayFilter = SyncDayFilter.TODAY
    private var selectedDirection: String? = "PUSH"
    private var renderJob: Job? = null
    private var requestedScope: String? = null
    private fun currentScope(): String = viewModel.summaryScope()
    private data class Snapshot(val runs: List<SyncSummaryRun>, val run: SyncSummaryRun?, val rows: List<Row>)
    private val dateTime get() = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
    private fun time(value: Long?) = value?.let { dateTime.format(Date(it)) } ?: "Not reported"
    private fun details(reports: List<String>): String = reports.joinToString("\n\n") { report ->
        summaryFields(report).entries.joinToString("\n") { (name, value) ->
            when (name) {
                "eventTime", "localUpdatedAt", "serverUpdatedAt" -> "$name: ${time(value.toLongOrNull()?.takeIf { it > 0 })}"
                "reason" -> "Reason: ${value.replace('_', ' ')}"
                else -> "$name: $value"
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val list = view.findViewById<RecyclerView>(R.id.summary_list)
        list.layoutManager = LinearLayoutManager(requireContext())
        list.itemAnimator = null
        val owner = viewLifecycleOwner
        val appContext = requireContext().applicationContext
        val emptyText = getString(R.string.sync_summary_empty)
        fun showDetails(title: String, text: String) {
            val label = TextView(requireContext()).apply {
                setPadding(24, 16, 24, 16)
                this.text = text
                setTextIsSelectable(true)
            }
            AlertDialog.Builder(requireContext()).setTitle(title)
                .setView(ScrollView(requireContext()).apply { addView(label) })
                .setPositiveButton("Close", null).show()
        }
        fun render() {
            renderJob?.cancel()
            viewModel.updateShareSummary(null)
            val scope = currentScope()
            requestedScope = scope
            val requestedDirection = selectedDirection
            val requestedFilter = dayFilter
            renderJob = owner.lifecycleScope.launch {
            val snapshot = withContext(Dispatchers.Default) {
            val day = selectedSyncDay(requestedFilter, System.currentTimeMillis())
            val village = withContext(Dispatchers.IO) { preferences.getLocationRecord()?.village }
            val user = withContext(Dispatchers.IO) { preferences.getLoggedInUser() }
            val runs = villageSummaryRuns(SyncSummaryStore.snapshot(), user?.userId?.toString(),
                user?.serviceMapId?.toString(), village?.id, day)
            val direction = requestedDirection ?: "PUSH"
            val run = runs.firstOrNull { it.direction == direction }
            val rows = mutableListOf<Row>()
            val connection = org.piramalswasthya.stoptb.helpers.CampConnectionDiagnostics.snapshot(
                appContext, requestedFilter)
            if (!connection.available) rows.add(Row("Camp Connection", "Connection diagnostics unavailable; counts are not assumed to be zero."))
            else {
                val status = when {
                    !connection.currentSession || connection.current == null -> "Not checked in this app session"
                    connection.current.connected -> "Connected"
                    else -> "Disconnected"
                }
                val connectionDetails = "Observed status transitions on this tablet across accounts and villages, for the selected dates. Latest 200 retained events; daily totals persist even when older details are removed. First observations, app restarts and hub changes are not counted as reconnects. Times are when the app observed the status, not proof of when an outage began.\n\n" +
                    connection.events.joinToString("\n\n") { event ->
                        "${time(event.at)} | ${if (event.connected) "CONNECTED" else "DISCONNECTED"}\n${org.piramalswasthya.stoptb.helpers.campHubDisplay(event.hub).text()}\nReason: ${org.piramalswasthya.stoptb.helpers.campConnectionReason(event.reason)}\nSource: ${event.source}"
                    }.ifBlank { "No retained transitions for this date range." }
                rows.add(Row("Camp Connection | This tablet | ${requestedFilter.name}",
                    "${org.piramalswasthya.stoptb.helpers.campHubDisplay(connection.hub).text("Camp Hub / IP")}\nCurrent observed status: $status\nLast observed check: ${time(connection.current?.at)}\n" +
                        "Last check result: ${connection.current?.reason?.let { org.piramalswasthya.stoptb.helpers.campConnectionReason(it) } ?: "Not reported"}\n" +
                        "Connected: ${connection.connects} times    Disconnected: ${connection.disconnects} times\n" +
                        "Unreachable/network failures: ${connection.unreachable} (subset of disconnects, not extra events)\n" +
                        "Last connected transition: ${time(connection.lastConnect)}\nLast disconnected transition: ${time(connection.lastDisconnect)}\n" +
                        "Last disconnect reason: ${connection.lastReason?.let { org.piramalswasthya.stoptb.helpers.campConnectionReason(it) } ?: "No transition reported"}\n" +
                        "${org.piramalswasthya.stoptb.helpers.campHubDisplay(connection.lastHub).text("Last disconnect hub")}\nTracking since: ${time(connection.since)}\n" +
                        "Totals cover this tablet and selected dates across villages and hub addresses. Patient and PUSH/PULL data remain village-specific. First observations and repeated checks do not increase counts. Refresh Summary to update. Backend server status is not monitored here; hub reachability does not confirm backend/API success.",
                    attention = connection.currentSession && connection.current?.connected == false,
                    action = "View Details", details = connectionDetails,
                    visual = SummaryCardVisual(
                        tone = if (!connection.currentSession || connection.current == null) SummaryTone.NEUTRAL
                            else if (connection.current.connected) SummaryTone.GREEN else SummaryTone.RED,
                        status = status, hub = connection.hub,
                        metrics = listOf(
                            SummaryMetric("Connected", connection.connects.toString(), SummaryTone.GREEN),
                            SummaryMetric("Disconnected", connection.disconnects.toString(), SummaryTone.RED),
                            SummaryMetric("Unreachable", connection.unreachable.toString(), SummaryTone.AMBER)),
                        facts = listOf(
                            SummaryFact("LAST CHECK", "${time(connection.current?.at)}\n${connection.current?.reason?.let { org.piramalswasthya.stoptb.helpers.campConnectionReason(it) } ?: "Not reported"}"),
                            SummaryFact("LAST CONNECTED", time(connection.lastConnect), SummaryTone.GREEN),
                            SummaryFact("LAST DISCONNECTED", time(connection.lastDisconnect), SummaryTone.RED),
                            SummaryFact("LAST DISCONNECT REASON", connection.lastReason?.let {
                                org.piramalswasthya.stoptb.helpers.campConnectionReason(it)
                            } ?: "No transition reported", SummaryTone.AMBER)),
                        note = "This tablet, across villages; patient and PUSH/PULL counts stay village-wise. Unreachable is part of Disconnected, not an extra event. First/repeated checks do not count. Refresh to update. Backend status is not monitored here; more information in View Details.")))
                val hubBreakdown = connection.hubs.joinToString("\n\n") { hub ->
                    fun number(value: Long) = org.piramalswasthya.stoptb.helpers.campHubCount(value, hub.partial)
                    "${org.piramalswasthya.stoptb.helpers.campHubDisplay(hub.hub).text()}\nConnected: ${number(hub.connects)}\nDisconnected: ${number(hub.disconnects)}\nUnreachable: ${number(hub.unreachable)}\nLast seen/configured: ${time(hub.lastSeen)}"
                }.ifBlank { "No hub addresses recorded for these dates." }
                val addressDetails = "HUB ADDRESS CHANGES\n" + connection.changes.joinToString("\n\n") {
                    "${time(it.at)}\n${org.piramalswasthya.stoptb.helpers.campHubDisplay(it.fromHub).text("From hub")}\n${org.piramalswasthya.stoptb.helpers.campHubDisplay(it.toHub).text("To hub")}"
                }.ifBlank { "No retained address changes in this date range." }
                val hubBody = "Hub address changes: ${connection.hubChanges}\nKnown hub addresses: ${connection.hubs.size}\n" +
                    "Address-change tracking since: ${time(connection.hubTrackingSince)}\n" +
                    "This tablet across accounts and villages, selected dates only. Known addresses include configured/observed endpoints, not proof that all were connected. Changing an address does not itself increment Disconnected. Changes before tracking began cannot be reconstructed. Legacy per-hub counts are partial; original overall totals are preserved.\n\n$hubBreakdown"
                rows.add(Row("Hub Address History | This tablet | ${requestedFilter.name}", hubBody,
                    action = "View Hub Details", details = "$addressDetails\n\nConnection transitions (latest retained):\n$connectionDetails",
                    visual = SummaryCardVisual(SummaryTone.BLUE,
                        listOf(SummaryMetric("Hub address changes", connection.hubChanges.toString(), SummaryTone.BLUE),
                            SummaryMetric("Known hub addresses", connection.hubs.size.toString(), SummaryTone.TEAL)),
                        connection.hubs.take(5).map { hub ->
                            SummaryFact("Hub: ${org.piramalswasthya.stoptb.helpers.campHubDisplay(hub.hub).address}",
                                "Port: ${org.piramalswasthya.stoptb.helpers.campHubDisplay(hub.hub).port}\n" +
                                    "Connected: ${org.piramalswasthya.stoptb.helpers.campHubCount(hub.connects, hub.partial)}\n" +
                                    "Disconnected: ${org.piramalswasthya.stoptb.helpers.campHubCount(hub.disconnects, hub.partial)}\n" +
                                    "Unreachable: ${org.piramalswasthya.stoptb.helpers.campHubCount(hub.unreachable, hub.partial)}",
                                if (hub.disconnects > 0) SummaryTone.AMBER else SummaryTone.BLUE)
                        },
                        "Tracking since ${time(connection.hubTrackingSince)}. This tablet across villages, selected dates only. Configured addresses may not have connected. Legacy counts marked partial are incomplete. Showing up to 5 hubs; View Hub Details has the full list and address-change timeline.")))
            }
            if (direction == "PUSH") {
            val available = if (village == null || village.id <= 0) null else try {
                database.syncVillageCountsDao().counts(village.id).associate { it.module to it.count }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
            rows.add(Row("Available in village DB (all dates)",
                if (available == null) "Current village counts unavailable; not assumed to be zero." else
                    "Village: ${village?.name} (${village?.id})\nModule records available: ${available.values.sum()}\n" +
                        "Includes existing/downloaded and newly saved data. Independent of Today/Yesterday/All; this is the current DB total, not today's saves or unique patients. Drafts/deactivated records are excluded.",
                visual = if (available == null) null else SummaryCardVisual(SummaryTone.BLUE,
                    listOf(SummaryMetric("Available module records", available.values.sum().toString(), SummaryTone.BLUE)),
                    listOf(SummaryFact("VILLAGE", "${village?.name}")),
                    "All dates, including existing/downloaded and new records. Drafts/deactivated records excluded; not unique patients.")))
            val records = if (village == null || village.id <= 0) null else try {
                database.syncVillageCountsDao().records(village.id).map { it.module to it.recordId }.toSet()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
            val daily = if (records == null) SyncDailyStore.Snapshot(emptyList(), null, null, false)
                else SyncDailyStore.snapshot(requestedFilter, records)
            if (!daily.available) rows.add(Row("Daily tracking unavailable", "No account or diagnostic history could be read. Counts are not assumed to be zero.", true))
            else {
                val totals = daily.modules
                rows.add(Row("PUSH | ${requestedFilter.name.lowercase().replaceFirstChar { it.uppercase() }} - unique records",
                    "Village: ${village?.name} | ID: ${village?.id}\nSaved locally: ${totals.sumOf { it.saved }}    Synced: ${totals.sumOf { it.synced }}\nFailed: ${totals.sumOf { it.failed }}    Pending: ${totals.sumOf { it.pending }}\n" +
                        "Tracking since ${time(daily.trackingSince)}. Only local saves in the selected date range matching this village's DB records; server downloads are excluded. Unmatched/unknown record IDs are excluded, not assigned to a village. Upload status is current for these same records, including later retries/edits. Totals are module records, not unique patients.",
                    visual = SummaryCardVisual(SummaryTone.TEAL,
                        listOf(SummaryMetric("Saved locally", totals.sumOf { it.saved }.toString(), SummaryTone.TEAL),
                            SummaryMetric("Synced", totals.sumOf { it.synced }.toString(), SummaryTone.GREEN),
                            SummaryMetric("Failed", totals.sumOf { it.failed }.toString(), SummaryTone.RED),
                            SummaryMetric("Pending", totals.sumOf { it.pending }.toString(), SummaryTone.BLUE)),
                        listOf(SummaryFact("VILLAGE", "${village?.name}"), SummaryFact("TRACKING SINCE", time(daily.trackingSince))),
                        "Selected dates, matched village records only; downloads/unmatched IDs excluded. Saved = Synced + Failed + Pending. Module records, not unique patients.")))
                rows.add(Row("Local work by module", "Teal: saved locally | Green: synced | Red: failed | Blue: pending\nSaved = Synced + Failed + Pending. Zero means no tracked local save in this range." +
                    "\nOpen View Failures on the affected module for record IDs, time and reason."))
                for (module in daily.modules) rows.add(Row(
                    module.name.replace('_', ' '),
                    "Available in village DB (all dates): ${available?.get(module.name)?.toString() ?: "Not available"}\n" +
                        if (module.unidentifiedFailure) "Additional failure reported without record IDs; see Logs. Known-ID counts are shown below." else "",
                    attention = module.failed > 0 || module.unidentifiedFailure,
                    action = if (module.failures.isNotEmpty()) "View Failures (${module.failed})" else null,
                    details = module.failures.joinToString("\n\n") {
                        "Record: ${it.recordId}\nAction: ${it.action}\nTime: ${time(it.at)}\nRun: ${it.runId}\nReason: ${it.reason.replace('_', ' ')}"
                    }, chart = listOf(module)))
                val last = daily.lastFailure
                rows.add(if (last == null) Row("Last Failed Sync", "No recorded failure since tracking started.") else Row(
                    "Last Failed Sync (this village, any day)", "${time(last.at)} | ${last.module.replace('_', ' ')}\n${last.reason.replace('_', ' ')}\nHistorical failure; see current Failed counts above for unresolved records.",
                    true, "Failure Details", "Run: ${last.runId}\nAction: ${last.action}\nRecord IDs (up to 20): ${last.recordId}\nTime: ${time(last.at)}\nReason: ${last.reason}"))
            }
            } else if (run != null) {
                val modules = summaryModules(run)
                val skips = summaryBlockedSkipGroups(run)
                fun count(value: Long?) = value?.toString() ?: "Not reported"
                fun total(values: List<Long?>): String = if (values.isEmpty() || values.any { it == null }) "Not reported" else values.sumOf { it ?: 0 }.toString()
                rows.add(Row("PULL | Selected sync",
                    "Village: ${village?.name} | ID: ${village?.id}\nStarted: ${time(run.startedAt)}\nSaved to DB: ${total(modules.map { it.saved })}\nBlocked skips with reasons: ${skips.sumOf { it.reports.size }} reported events\n" +
                        "Already-up-to-date and duplicate records are excluded. Blocked skip details are retained events, not a complete record count; missing reports do not mean zero blockers. Shows the latest PULL in the selected date range.",
                    visual = SummaryCardVisual(SummaryTone.BLUE,
                        listOf(SummaryMetric("Saved to DB", total(modules.map { it.saved }), SummaryTone.GREEN),
                            SummaryMetric("Blocked skip reports", skips.sumOf { it.reports.size }.toString(), SummaryTone.AMBER)),
                        listOf(SummaryFact("VILLAGE", "${village?.name}"), SummaryFact("STARTED", time(run.startedAt))),
                        "Latest PULL in selected dates. Blocker reports are retained events, not a complete count. Missing reports do not mean zero blockers; unchanged/duplicate records excluded.")))
                for (name in (modules.map { it.name } + skips.map { it.module }).distinct()) {
                    val module = modules.find { it.name == name }
                    val groups = skips.filter { it.module == name }
                    val retained = groups.sumOf { it.reports.size }
                    rows.add(Row(name.replace('_', ' '),
                        "Saved to DB: ${count(module?.saved)}    Failed: ${count(module?.failed)}\n" +
                            if (retained > 0) "Blocked skips with reasons: $retained reported events (not a full total)." else "No blocked skip details reported. Normal unchanged/duplicate records are excluded.",
                        attention = module?.status == "Needs attention" || groups.any { !it.normal },
                        action = if (retained > 0) "View Skips" else null,
                        details = if (groups.isEmpty()) "The sync reported skipped records but did not report their individual reasons. Reasons cannot be inferred." else
                            "Server records not applied to DB: reported blockers only. Unchanged/duplicate records are excluded. Local pending edits may be intentionally protected, not a DB error. Retained events are not a complete count.\n\n" +
                                groups.joinToString("\n\n") { group -> "${group.description}\n${details(group.reports)}" }))
                }
                if (modules.isEmpty() && skips.isEmpty()) rows.add(Row("PULL modules", "Saved and skipped counts have not been reported yet."))
            }
            if (run == null) rows.add(Row("No sync reports yet", emptyText))
            else {
                val status = summaryStatus(run.details["outcome"])
                val duration = run.details["durationMs"]?.toLongOrNull()?.let { "${it / 1000.0}s" } ?: "Not reported"
                rows.add(Row("${run.direction} - $status",
                    "Started: ${time(run.startedAt)}\nEnded: ${time(run.details["endedAt"]?.toLongOrNull())}\nDuration: $duration | ${run.details["trigger"] ?: "Unknown trigger"}",
                    status == "Needs attention", "Run Details",
                    "Run: ${run.runId}\n" + run.details.entries.joinToString("\n") { "${it.key}: ${it.value}" } +
                        "\n\nTechnical sync reports (not daily local-work totals):\n" + details(run.reports)))
            }
            Snapshot(runs, run, rows)
            }
            if (scope != currentScope()) {
                render()
                return@launch
            }
            selectedDirection = snapshot.run?.direction ?: requestedDirection ?: "PUSH"
            for ((direction, buttonId) in listOf("PUSH" to R.id.summary_push, "PULL" to R.id.summary_pull)) {
                val latest = snapshot.runs.firstOrNull { it.direction == direction }
                view.findViewById<Button>(buttonId).apply {
                    text = direction
                    contentDescription = "$direction: ${if (latest == null) "No reports" else summaryStatus(latest.details["outcome"])}"
                    isEnabled = true
                    isSelected = selectedDirection == direction
                    backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(if (isSelected) "#176B68" else "#DFECEA"))
                    setTextColor(Color.parseColor(if (isSelected) "#FFFFFF" else "#176B68"))
                }
            }
            list.adapter = SummaryAdapter(snapshot.rows) { row ->
                showDetails(row.title, if (row.visual == null) row.details.orEmpty() else row.body + "\n\n" + row.details.orEmpty())
            }
            val summary = withContext(Dispatchers.Default) {
                buildString {
                    appendLine("SYNC SUMMARY | ${requestedFilter.name} | ${requestedDirection ?: "PUSH"}")
                    appendLine("Snapshot time: ${time(System.currentTimeMillis())}")
                    appendLine("Counts and details match the rendered summary; not a full historical audit.")
                    for (row in snapshot.rows) {
                        appendLine("\n=== ${row.title} ===")
                        appendLine(row.body)
                        row.chart?.forEach { module ->
                            appendLine("Saved locally: ${module.saved} | Synced: ${module.synced} | Failed: ${module.failed} | Pending: ${module.pending}")
                        }
                        if (!row.details.isNullOrBlank()) appendLine(row.details)
                    }
                }
            }
            if (scope != currentScope()) {
                render()
                return@launch
            }
            viewModel.updateShareSummary(summary, scope)
            list.scrollToPosition(0)
            }
        }
        view.findViewById<View>(R.id.refresh_summary).setOnClickListener { render() }
        view.findViewById<View>(R.id.summary_share).setOnClickListener {
            showSyncShareDialog(viewModel, fromSummary = true)
        }
        val filters = listOf(SyncDayFilter.TODAY to R.id.summary_today, SyncDayFilter.YESTERDAY to R.id.summary_yesterday, SyncDayFilter.ALL to R.id.summary_all)
        for ((filter, id) in filters) view.findViewById<Button>(id).apply {
            isSelected = filter == dayFilter
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(if (isSelected) "#176B68" else "#DFECEA"))
            setTextColor(Color.parseColor(if (isSelected) "#FFFFFF" else "#176B68"))
            setOnClickListener {
                dayFilter = filter
                for ((other, buttonId) in filters) view.findViewById<Button>(buttonId).apply {
                    isSelected = other == filter
                    backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(if (isSelected) "#176B68" else "#DFECEA"))
                    setTextColor(Color.parseColor(if (isSelected) "#FFFFFF" else "#176B68"))
                }
                render()
            }
        }
        for ((direction, id) in listOf("PUSH" to R.id.summary_push, "PULL" to R.id.summary_pull)) {
            view.findViewById<View>(id).setOnClickListener {
                selectedDirection = direction
                render()
            }
        }
        render()
        owner.lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
                if (requestedScope != currentScope()) render()
            }
            override fun onResume(owner: androidx.lifecycle.LifecycleOwner) {
                if (requestedScope != currentScope()) render()
            }
        })
    }

    private class SummaryAdapter(private val rows: List<Row>, private val open: (Row) -> Unit) : RecyclerView.Adapter<Holder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val context = parent.context
            val padding = (14 * parent.resources.displayMetrics.density).toInt()
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding, padding, padding)
                layoutParams = RecyclerView.LayoutParams(-1, -2).apply { bottomMargin = padding }
            }
            val title = TextView(context).apply { textSize = 17f; setTypeface(typeface, Typeface.BOLD) }
            val body = TextView(context).apply {
                textSize = 14f; setPadding(0, padding / 2, 0, 0)
                setTextColor(Color.parseColor("#475569"))
                setLineSpacing(2 * resources.displayMetrics.density, 1f)
            }
            val button = com.google.android.material.button.MaterialButton(context).apply {
                isAllCaps = false
                cornerRadius = (10 * resources.displayMetrics.density).toInt()
                minimumHeight = (48 * resources.displayMetrics.density).toInt()
            }
            val chartHost = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            card.addView(title); card.addView(body); card.addView(chartHost); card.addView(button, LinearLayout.LayoutParams(-2, -2))
            return Holder(card, title, body, button, chartHost)
        }
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val row = rows[position]
            val tone = row.visual?.tone ?: if (row.attention) SummaryTone.AMBER else SummaryTone.TEAL
            holder.title.text = row.title
            holder.body.text = row.body
            holder.body.visibility = if (row.body.isBlank() || row.visual != null) View.GONE else View.VISIBLE
            holder.chartHost.removeAllViews()
            row.visual?.let { holder.chartHost.addView(SyncSummaryCardView(holder.card.context, it)) }
            row.chart?.let { holder.chartHost.addView(SyncModuleChart(holder.card.context, it)) }
            holder.title.setTextColor(Color.parseColor(tone.ink))
            holder.card.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.WHITE, Color.parseColor(tone.tint))).apply {
                cornerRadius = 16 * holder.card.resources.displayMetrics.density
                setStroke(1, Color.parseColor(tone.border))
            }
            holder.button.visibility = if (row.action == null) View.GONE else View.VISIBLE
            holder.button.text = row.action
            holder.button.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(tone.ink))
            holder.button.setTextColor(Color.WHITE)
            holder.button.setOnClickListener { open(row) }
        }
        override fun getItemCount() = rows.size
    }
    private class Holder(val card: LinearLayout, val title: TextView, val body: TextView, val button: Button,
                         val chartHost: LinearLayout) : RecyclerView.ViewHolder(card)
}
