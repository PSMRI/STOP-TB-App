package org.piramalswasthya.stoptb.ui.home_activity.sync_dashboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import org.piramalswasthya.stoptb.helpers.SyncDailyModule

internal class SyncModuleChart(context: Context, private val modules: List<SyncDailyModule>) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val colors = intArrayOf(Color.rgb(23, 107, 104), Color.rgb(102, 153, 0), Color.rgb(195, 43, 36), Color.rgb(32, 102, 170))
    init { contentDescription = modules.joinToString(". ") { "${it.name}: saved locally ${it.saved}, synced ${it.synced}, failed ${it.failed}, pending ${it.pending}" } }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (modules.size * 92 * density).toInt())
    }
    override fun onDraw(canvas: Canvas) {
        val maximum = modules.maxOfOrNull { maxOf(it.saved, it.synced, it.failed, it.pending) }?.coerceAtLeast(1) ?: 1
        val barStart = 82 * density
        val usable = (width - barStart - 38 * density).coerceAtLeast(1f)
        modules.forEachIndexed { index, module ->
            val top = index * 92 * density
            val values = intArrayOf(module.saved, module.synced, module.failed, module.pending)
            val labels = arrayOf("Saved locally", "Synced", "Failed", "Pending")
            for (metric in 0..3) {
                val y = top + (8 + metric * 19) * density
                paint.color = Color.DKGRAY; paint.textSize = 11 * resources.displayMetrics.scaledDensity
                canvas.drawText(labels[metric], 0f, y + 10 * density, paint)
                paint.color = Color.rgb(237, 243, 242)
                canvas.drawRect(barStart, y, barStart + usable, y + 11 * density, paint)
                paint.color = colors[metric]
                canvas.drawRect(barStart, y, barStart + usable * values[metric] / maximum, y + 11 * density, paint)
                canvas.drawText(values[metric].toString(), barStart + usable + 5 * density, y + 10 * density, paint)
            }
        }
    }
}
