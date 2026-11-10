package org.piramalswasthya.stoptb.utils

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowThrottleTest {

    @Test
    fun burstIsCollapsed_firstAndLastValuesAreDelivered() = runBlocking {
        val emitted = flow {
            for (value in 1..50) {
                emit(value)
                delay(2)
            }
        }.throttleLatest(periodMs = 100).toList()

        assertEquals(1, emitted.first())
        assertEquals(50, emitted.last())
        assertTrue("expected the burst to be collapsed, got $emitted", emitted.size < 10)
        assertEquals("values must stay in order", emitted.sorted(), emitted)
    }

    @Test
    fun slowSourcePassesEveryValue() = runBlocking {
        val emitted = flow {
            for (value in 1..3) {
                emit(value)
                delay(150)
            }
        }.throttleLatest(periodMs = 50).toList()

        assertEquals(listOf(1, 2, 3), emitted)
    }
}
