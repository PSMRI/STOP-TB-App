package org.piramalswasthya.stoptb.helpers

import org.junit.Assert.*
import org.junit.Test
import org.piramalswasthya.stoptb.model.LogLevel
import org.piramalswasthya.stoptb.model.SyncLogEntry

class SyncLogDisplayTest {
    @Test fun rollingWindowRetainsLatestEntriesAndOriginalLogsRemainUntouched() {
        val logs = (0..499).map { SyncLogEntry(it.toLong(), it.toLong(), LogLevel.INFO, "Sync", "x".repeat(2000)) }
        val shown = syncLogDisplaySnapshot(logs)
        assertEquals(200, shown.size)
        assertEquals(300L, shown.first().id)
        assertEquals(499L, shown.last().id)
        assertEquals(503, shown.first().message.length)
        assertEquals(2000, logs.first().message.length)
    }
    @Test fun emptyAndSmallSnapshotsPreserveOrderAndMetadata() {
        assertTrue(syncLogDisplaySnapshot(emptyList()).isEmpty())
        val entry = SyncLogEntry(7, 50, LogLevel.ERROR, "Sync", "failure")
        assertEquals(listOf(entry), syncLogDisplaySnapshot(listOf(entry)))
    }
}
