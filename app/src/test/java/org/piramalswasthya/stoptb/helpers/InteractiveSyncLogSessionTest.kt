package org.piramalswasthya.stoptb.helpers

import org.junit.Assert.*
import org.junit.Test
import org.piramalswasthya.stoptb.model.LogLevel
import org.piramalswasthya.stoptb.model.SyncLogEntry

class InteractiveSyncLogSessionTest {
    private fun http(id: Long) = SyncLogEntry(id, 0L, LogLevel.INFO, "SyncApi",
        "HTTP EXCHANGE | trigger=MANUAL_REFRESH | phase=RESPONSE")

    @Test fun switchingLoginsClearsVisibleLogsAndRejectsLateEntries() {
        val store = InteractiveSyncLogSession()
        store.reset(signedIn = true)
        val first = requireNotNull(store.session.value)
        store.append(first, http(1))
        store.publish()
        assertEquals(listOf(1L), store.logs.value.map { it.id })
        store.reset(signedIn = false)
        assertTrue(store.logs.value.isEmpty())
        store.reset(signedIn = true)
        val second = requireNotNull(store.session.value)
        assertNotEquals(first, second)
        store.append(first, http(2))
        store.append(null, http(3))
        store.append(second, http(4))
        store.publish()
        assertEquals(listOf(4L), store.logs.value.map { it.id })
        assertEquals(listOf(4L), store.snapshot(second).map { it.id })
        assertTrue(store.snapshot(first).isEmpty())
    }

    @Test fun oldRunAttributionDoesNotCarryIntoNewSession() {
        val store = InteractiveSyncLogSession()
        store.reset(signedIn = true)
        val first = requireNotNull(store.session.value)
        val start = SyncLogEntry(1L, 0L, LogLevel.INFO, "SyncTrace",
            "SYNC START | runId=one | direction=PUSH | trigger=MANUAL_REFRESH")
        store.append(first, start)
        assertEquals(1, store.snapshot(first).size)
        store.reset(signedIn = true)
        val second = requireNotNull(store.session.value)
        store.append(second, start.copy(message = "SYNC END | runId=one | direction=PUSH"))
        assertTrue(store.snapshot(second).isEmpty())
    }
}
