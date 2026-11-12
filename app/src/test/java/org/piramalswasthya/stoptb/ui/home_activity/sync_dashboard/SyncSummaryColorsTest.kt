package org.piramalswasthya.stoptb.ui.home_activity.sync_dashboard

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

class SyncSummaryColorsTest {
    private fun luminance(hex: String): Double {
        val rgb = hex.removePrefix("#").toLong(16)
        val channels = listOf(16, 8, 0).map { shift ->
            val value = ((rgb shr shift) and 255) / 255.0
            if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722
    }
    private fun contrast(a: String, b: String): Double {
        val x = luminance(a); val y = luminance(b)
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }

    @Test fun coloredLabelsAndActionButtonsHaveReadableContrast() {
        SummaryTone.values().forEach { tone ->
            assertTrue("${tone.name} label contrast", contrast(tone.ink, tone.tint) >= 4.5)
            assertTrue("${tone.name} action contrast", contrast(tone.ink, "#FFFFFF") >= 4.5)
        }
    }

    @Test fun metricGridAdaptsToPhoneTabletAndSingleCounters() {
        assertEquals(2, summaryMetricColumns(360, 4))
        assertEquals(2, summaryMetricColumns(599, 3))
        assertEquals(3, summaryMetricColumns(800, 3))
        assertEquals(4, summaryMetricColumns(800, 4))
        assertEquals(1, summaryMetricColumns(360, 1))
        assertEquals(1, summaryMetricColumns(0, 0))
    }
}
