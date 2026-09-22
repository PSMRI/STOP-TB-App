package org.piramalswasthya.stoptb.custom_views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import org.piramalswasthya.stoptb.R

class DonutChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    data class Segment(
        val value: Float,
        @ColorInt val color: Int,
    )

    private val density = resources.displayMetrics.density
    private var strokePx = 14f * density
    private var segments: List<Segment> = emptyList()
    @ColorInt
    private var trackColor: Int = ContextCompat.getColor(context, R.color.dashboard_donut_track)

    private val arcRect = RectF()
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }

    fun setChart(
        segments: List<Segment>,
        @ColorInt trackColor: Int = this.trackColor,
        strokeDp: Float = 14f,
    ) {
        this.segments = segments
        this.trackColor = trackColor
        this.strokePx = strokeDp * density
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = strokePx / 2f + 1f
        if (width <= inset * 2 || height <= inset * 2) return

        arcRect.set(inset, inset, width - inset, height - inset)
        trackPaint.strokeWidth = strokePx
        trackPaint.color = trackColor
        canvas.drawArc(arcRect, 0f, 360f, false, trackPaint)

        val total = segments.sumOf { it.value.toDouble() }.toFloat()
        if (total <= 0f) return

        segmentPaint.strokeWidth = strokePx
        var startAngle = -90f
        segments.forEach { segment ->
            if (segment.value <= 0f) return@forEach
            val sweep = 360f * (segment.value / total)
            segmentPaint.color = segment.color
            canvas.drawArc(arcRect, startAngle, sweep, false, segmentPaint)
            startAngle += sweep
        }
    }
}
