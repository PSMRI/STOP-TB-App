package org.piramalswasthya.stoptb.ui.home_activity.sync_dashboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.LinearLayout
import android.widget.TextView

internal enum class SummaryTone(val ink: String, val tint: String, val border: String) {
    TEAL("#145F5B", "#EAF7F5", "#C2E4DE"),
    GREEN("#166534", "#ECFDF3", "#BBE4CA"),
    RED("#B42318", "#FFF1F0", "#FFD1CE"),
    AMBER("#8A4B08", "#FFF7E6", "#F2D49D"),
    BLUE("#1D4E89", "#EDF4FF", "#CDDFF5"),
    NEUTRAL("#475569", "#F1F5F9", "#D8E2ED")
}

internal data class SummaryMetric(val label: String, val value: String, val tone: SummaryTone)
internal data class SummaryFact(val label: String, val value: String, val tone: SummaryTone = SummaryTone.NEUTRAL)
internal data class SummaryCardVisual(
    val tone: SummaryTone,
    val metrics: List<SummaryMetric>,
    val facts: List<SummaryFact>,
    val note: String,
    val status: String? = null,
    val hub: String? = null
)

internal fun summaryMetricColumns(widthDp: Int, metricCount: Int): Int =
    minOf(if (widthDp >= 600) 4 else 2, metricCount).coerceAtLeast(1)

internal class SyncSummaryCardView(context: Context, visual: SummaryCardVisual) : LinearLayout(context) {
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun rounded(tone: SummaryTone) = GradientDrawable().apply {
        setColor(Color.parseColor(tone.tint))
        cornerRadius = dp(12).toFloat()
        setStroke(dp(1), Color.parseColor(tone.border))
    }
    private fun label(value: String, size: Float, color: String, bold: Boolean = false) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(Color.parseColor(color))
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    init {
        orientation = VERTICAL
        visual.status?.let { status ->
            addView(label(status, 13f, visual.tone.ink, true).apply {
                background = rounded(visual.tone)
                setPadding(dp(12), dp(7), dp(12), dp(7))
            }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        }
        visual.hub?.let { hub ->
            val display = org.piramalswasthya.stoptb.helpers.campHubDisplay(hub)
            addView(label("CAMP HUB / IP", 11f, SummaryTone.NEUTRAL.ink, true).apply { setPadding(0, dp(14), 0, dp(4)) })
            addView(label(display.address, 15f, "#173E3B", true).apply { setTextIsSelectable(true) })
            addView(label("Port: ${display.port}", 14f, SummaryTone.NEUTRAL.ink).apply { setPadding(0, dp(4), 0, 0) })
        }
        // Two columns on phones; wider layouts can display a compact single metric row.
        val columns = summaryMetricColumns(resources.configuration.screenWidthDp, visual.metrics.size)
        visual.metrics.chunked(columns).forEach { metrics ->
            val line = LinearLayout(context).apply { orientation = HORIZONTAL; isBaselineAligned = false }
            metrics.forEachIndexed { index, metric ->
                val tile = LinearLayout(context).apply {
                    orientation = VERTICAL
                    background = rounded(metric.tone)
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    minimumHeight = dp(82)
                    addView(label(metric.value, if (metric.value.toLongOrNull() == null) 14f else 26f, metric.tone.ink, true))
                    addView(label(metric.label, 12f, metric.tone.ink, true))
                    contentDescription = "${metric.label}: ${metric.value}"
                }
                line.addView(tile, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (index > 0) marginStart = dp(8)
                })
            }
            if (metrics.size < columns) repeat(columns - metrics.size) {
                line.addView(android.view.View(context), LayoutParams(0, 1, 1f).apply { marginStart = dp(8) })
            }
            addView(line, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        }
        visual.facts.forEach { fact ->
            val group = LinearLayout(context).apply {
                orientation = VERTICAL
                setPadding(dp(12), dp(9), dp(12), dp(9))
                background = rounded(fact.tone)
                addView(label(fact.label, 11f, fact.tone.ink, true))
                addView(label(fact.value, 14f, fact.tone.ink).apply {
                    setPadding(0, dp(3), 0, 0)
                    setTextIsSelectable(true)
                })
            }
            addView(group, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        }
        addView(label(visual.note, 12f, SummaryTone.NEUTRAL.ink).apply {
            setPadding(0, dp(12), 0, dp(4))
            setLineSpacing(dp(2).toFloat(), 1f)
        })
    }
}
