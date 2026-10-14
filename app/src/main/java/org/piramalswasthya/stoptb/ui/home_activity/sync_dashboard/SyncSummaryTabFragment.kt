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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.DateFormat
import java.util.Date
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
import org.piramalswasthya.stoptb.helpers.syncDay

class SyncSummaryTabFragment : Fragment(R.layout.fragment_sync_summary_tab) {
    private data class Row(val title: String, val body: String, val attention: Boolean = false,
        val action: String? = null, val details: String? = null, val chart: List<SyncDailyModule>? = null)
    private var dayFilter = SyncDayFilter.TODAY
    private var selectedDirection: String? = "PUSH"
    private var renderJob: Job? = null
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
            val requestedDirection = selectedDirection
            val requestedFilter = dayFilter
            renderJob = owner.lifecycleScope.launch {
            val snapshot = withContext(Dispatchers.Default) {
            val day = selectedSyncDay(requestedFilter, System.currentTimeMillis())
            val runs = SyncSummaryStore.snapshot().filter { day == null || syncDay(it.startedAt) == day }.sortedByDescending { it.startedAt }
            val direction = requestedDirection ?: "PUSH"
            val run = runs.firstOrNull { it.direction == direction }
            val rows = mutableListOf<Row>()
            if (direction == "PUSH") {
            val daily = SyncDailyStore.snapshot(requestedFilter)
            if (!daily.available) rows.add(Row("Daily tracking unavailable", "No account or diagnostic history could be read. Counts are not assumed to be zero.", true))
            else {
                val totals = daily.modules
                rows.add(Row("PUSH | ${requestedFilter.name.lowercase().replaceFirstChar { it.uppercase() }} - unique records",
                    "Saved locally: ${totals.sumOf { it.saved }}    Synced: ${totals.sumOf { it.synced }}\nFailed: ${totals.sumOf { it.failed }}    Pending: ${totals.sumOf { it.pending }}\n" +
                        "Tracking since ${time(daily.trackingSince)}. Only local saves on the selected day; server downloads are excluded. Upload status is current for these same records, including later retries/edits. Totals are module records, not unique patients."))
                rows.add(Row("Local work by module", "Teal: saved locally | Green: synced | Red: failed | Blue: pending\nSaved = Synced + Failed + Pending. Zero means no tracked local save in this range." +
                    "\nOpen View Failures on the affected module for record IDs, time and reason."))
                for (module in daily.modules) rows.add(Row(
                    module.name.replace('_', ' '),
                    if (module.unidentifiedFailure) "Additional failure reported without record IDs; see Logs. Known-ID counts are shown below." else "",
                    attention = module.failed > 0 || module.unidentifiedFailure,
                    action = if (module.failures.isNotEmpty()) "View Failures (${module.failed})" else null,
                    details = module.failures.joinToString("\n\n") {
                        "Record: ${it.recordId}\nAction: ${it.action}\nTime: ${time(it.at)}\nRun: ${it.runId}\nReason: ${it.reason.replace('_', ' ')}"
                    }, chart = listOf(module)))
                val last = daily.lastFailure
                rows.add(if (last == null) Row("Last Failed Sync", "No recorded failure since tracking started.") else Row(
                    "Last Failed Sync (any day)", "${time(last.at)} | ${last.module.replace('_', ' ')}\n${last.reason.replace('_', ' ')}\nHistorical failure; see current Failed counts above for unresolved records.",
                    true, "Failure Details", "Run: ${last.runId}\nAction: ${last.action}\nRecord IDs (up to 20): ${last.recordId}\nTime: ${time(last.at)}\nReason: ${last.reason}"))
            }
            } else if (run != null) {
                val modules = summaryModules(run)
                val skips = summaryBlockedSkipGroups(run)
                fun count(value: Long?) = value?.toString() ?: "Not reported"
                fun total(values: List<Long?>): String = if (values.isEmpty() || values.any { it == null }) "Not reported" else values.sumOf { it ?: 0 }.toString()
                rows.add(Row("PULL | Selected sync",
                    "Started: ${time(run.startedAt)}\nSaved to DB: ${total(modules.map { it.saved })}\nBlocked skips with reasons: ${skips.sumOf { it.reports.size }} reported events\n" +
                        "Already-up-to-date and duplicate records are excluded. Blocked skip details are retained events, not a complete record count; missing reports do not mean zero blockers. Shows the latest PULL in the selected date range."))
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
            list.adapter = SummaryAdapter(snapshot.rows) { row -> showDetails(row.title, row.details.orEmpty()) }
            list.scrollToPosition(0)
            }
        }
        view.findViewById<View>(R.id.refresh_summary).setOnClickListener { render() }
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
            val title = TextView(context).apply { textSize = 16f; setTypeface(typeface, Typeface.BOLD) }
            val body = TextView(context).apply { textSize = 14f; setPadding(0, padding / 2, 0, 0) }
            val button = Button(context)
            val chartHost = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            card.addView(title); card.addView(body); card.addView(chartHost); card.addView(button, LinearLayout.LayoutParams(-2, -2))
            return Holder(card, title, body, button, chartHost)
        }
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val row = rows[position]
            holder.title.text = row.title
            holder.body.text = row.body
            holder.body.visibility = if (row.body.isBlank()) View.GONE else View.VISIBLE
            holder.chartHost.removeAllViews()
            row.chart?.let { holder.chartHost.addView(SyncModuleChart(holder.card.context, it)) }
            holder.title.setTextColor(Color.parseColor(if (row.attention) "#9B430B" else "#166B68"))
            holder.card.background = GradientDrawable().apply {
                setColor(Color.WHITE); cornerRadius = 12 * holder.card.resources.displayMetrics.density
                setStroke(1, Color.parseColor(if (row.attention) "#EBCDAF" else "#D3E4E2"))
            }
            holder.button.visibility = if (row.action == null) View.GONE else View.VISIBLE
            holder.button.text = row.action
            holder.button.setOnClickListener { open(row) }
        }
        override fun getItemCount() = rows.size
    }
    private class Holder(val card: LinearLayout, val title: TextView, val body: TextView, val button: Button,
                         val chartHost: LinearLayout) : RecyclerView.ViewHolder(card)
}
