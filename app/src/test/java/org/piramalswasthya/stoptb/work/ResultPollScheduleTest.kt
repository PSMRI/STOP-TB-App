package org.piramalswasthya.stoptb.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ResultPollScheduleTest {

    private val noJitter = object : Random() {
        override fun nextBits(bitCount: Int) = 0
        override fun nextDouble() = 0.5 // jitter factor 0.8 + 0.5 * 0.4 = 1.0
    }

    @Test
    fun delayStartsAtTheTestsBaseAndDoublesUpToTheCap() {
        assertEquals(2 * 60_000L, ResultPollSchedule.delayFor("XRAY_CHEST", 1, noJitter))
        assertEquals(4 * 60_000L, ResultPollSchedule.delayFor("XRAY_CHEST", 2, noJitter))
        assertEquals(10 * 60_000L, ResultPollSchedule.delayFor("SPUTUM_TRUENAT", 1, noJitter))
        assertEquals(20 * 60_000L, ResultPollSchedule.delayFor("MDR_RIF", 1, noJitter))
        assertEquals(ResultPollSchedule.MAX_DELAY_MS, ResultPollSchedule.delayFor("XRAY_CHEST", 50, noJitter))
    }

    @Test
    fun jitterStaysWithinTwentyPercent() {
        repeat(200) {
            val delay = ResultPollSchedule.delayFor("XRAY_CHEST", 1)
            assertTrue(delay in 96_000L..144_000L)
        }
    }

    @Test
    fun unchangedOrdersBackOffAndChangedOrdersReset() {
        val schedule = ResultPollSchedule()
        val now = 1_000_000L
        assertTrue("an order never polled is due", schedule.isDue(1L, "XRAY_CHEST", now))
        schedule.recordUnchanged(1L, "XRAY_CHEST", now)
        assertFalse(schedule.isDue(1L, "XRAY_CHEST", now + 60_000L))
        assertTrue(schedule.isDue(1L, "XRAY_CHEST", now + 3 * 60_000L))
        schedule.recordChanged(1L, "XRAY_CHEST")
        assertTrue(schedule.isDue(1L, "XRAY_CHEST", now))
    }

    @Test
    fun retainOnlyDropsOrdersThatAreNoLongerPending() {
        val schedule = ResultPollSchedule()
        schedule.recordUnchanged(1L, "XRAY_CHEST", 0L)
        schedule.recordUnchanged(2L, "MDR_RIF", 0L)
        schedule.retainOnly(listOf(2L to "MDR_RIF"))
        assertTrue(schedule.isDue(1L, "XRAY_CHEST", 0L))
        assertFalse(schedule.isDue(2L, "MDR_RIF", 0L))
        schedule.retainOnly(emptyList())
        assertNull(schedule.earliestDue())
    }
}
