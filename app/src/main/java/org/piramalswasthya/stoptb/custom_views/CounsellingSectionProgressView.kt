package org.piramalswasthya.stoptb.custom_views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import org.piramalswasthya.stoptb.R

/**
 * Horizontal counselling-section progress tracker: draws [totalSections] evenly spaced
 * nodes connected by lines, with the first [sectionsFilled] nodes/segments highlighted,
 * and a letter label (A, B, C, ...) under each node. Scales to any section count received
 * from the backend - nothing here is hardcoded to a fixed number of sections.
 */
class CounsellingSectionProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var totalSections: Int = 0
    private var sectionsFilled: Int = 0

    private val density = resources.displayMetrics.density
    private val nodeRadius = 6f * density
    private val lineStrokeWidth = 2.5f * density
    private val horizontalInset = 16f * density
    private val labelGap = 4f * density
    private val labelTextSize = 11f * resources.displayMetrics.scaledDensity

    private val filledColor = ContextCompat.getColor(context, android.R.color.holo_green_dark)
    private val unfilledColor = run {
        val onPrimary = ContextCompat.getColor(context, R.color.md_theme_light_onPrimary)
        Color.argb(110, Color.red(onPrimary), Color.green(onPrimary), Color.blue(onPrimary))
    }
    private val labelColor = ContextCompat.getColor(context, R.color.md_theme_light_onPrimary)

    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = lineStrokeWidth
        strokeCap = Paint.Cap.ROUND
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = labelTextSize
        textAlign = Paint.Align.CENTER
        color = labelColor
    }

    fun setProgress(sectionsFilled: Int, totalSections: Int) {
        this.sectionsFilled = sectionsFilled.coerceIn(0, totalSections.coerceAtLeast(0))
        this.totalSections = totalSections.coerceAtLeast(0)
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (nodeRadius * 2 + labelGap + labelPaint.textSize + 6 * density).toInt()
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (totalSections <= 0) return

        val usableWidth = (width - 2 * horizontalInset).coerceAtLeast(0f)
        val spacing = if (totalSections > 1) usableWidth / (totalSections - 1) else 0f
        val centerY = nodeRadius + 2 * density
        val labelBaselineY = centerY + nodeRadius + labelGap + labelPaint.textSize

        fun centerX(index: Int) = horizontalInset + spacing * index

        for (segment in 0 until totalSections - 1) {
            linePaint.color = if (segment < sectionsFilled - 1) filledColor else unfilledColor
            canvas.drawLine(
                centerX(segment), centerY,
                centerX(segment + 1), centerY,
                linePaint
            )
        }

        for (index in 0 until totalSections) {
            nodePaint.color = if (index < sectionsFilled) filledColor else unfilledColor
            canvas.drawCircle(centerX(index), centerY, nodeRadius, nodePaint)

            val label = ('A' + index).toString()
            canvas.drawText(label, centerX(index), labelBaselineY, labelPaint)
        }
    }
}
