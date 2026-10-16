package org.piramalswasthya.stoptb.helpers

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.*
import org.junit.Test
import org.piramalswasthya.stoptb.model.LogLevel
import org.piramalswasthya.stoptb.model.SyncLogEntry

class SyncInteractiveLogArchiveTest {
    private fun contents(bytes: ByteArray): Map<String, String> {
        val result = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                result[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        return result
    }

    @Test fun sharedArchiveContainsManualAndSaveEventsButNoAutoOrRawApiEntries() {
        val filter = SyncLiveLogFilter()
        val events = listOf(
            "SyncTrace" to "SYNC START | runId=m | direction=PUSH | trigger=MANUAL_REFRESH",
            "SyncTrace" to "SYNC START | runId=a | direction=PULL | trigger=AUTO_PULL | secret=AUTO_MARKER",
            "SyncTrace" to "SYNC MODULE | runId=m | direction=PUSH | outcome=SUCCESS",
            "SyncTrace" to "SYNC MODULE | runId=a | direction=PULL | secret=AUTO_MARKER",
            "OkHttp" to "RAW_API_MARKER",
            "SyncTrace" to "LOCAL SAVE | module=GENERAL_OPD | records=1 | outcome=SUCCESS"
        ).mapIndexedNotNull { id, (tag, message) ->
            if (filter.accepts(tag, message)) SyncLogEntry(id.toLong(), 123, LogLevel.INFO, tag, message) else null
        }
        val output = ByteArrayOutputStream()
        writeInteractiveLogArchive(output, events, "device=test")
        val files = contents(output.toByteArray())
        assertEquals(setOf("sync-context.txt", "manual-direct-sync.log"), files.keys)
        assertTrue(files.getValue("sync-context.txt").contains("entries=3"))
        val log = files.getValue("manual-direct-sync.log")
        assertTrue(log.contains("MANUAL_REFRESH"))
        assertTrue(log.contains("LOCAL SAVE"))
        assertFalse(log.contains("AUTO_MARKER"))
        assertFalse(log.contains("RAW_API_MARKER"))
        assertFalse(log.contains("AUTO_PULL"))
    }

    @Test fun exportPreservesFullMessagesAndSnapshotOrderRatherThanDisplayPreviews() {
        val output = ByteArrayOutputStream()
        val message = "LOCAL SAVE | " + "x".repeat(2000)
        writeInteractiveLogArchive(output, listOf(
            SyncLogEntry(1, 1, LogLevel.INFO, "SyncTrace", message),
            SyncLogEntry(2, 2, LogLevel.ERROR, "SyncTrace", "SECOND_EVENT")), "device=test")
        val log = contents(output.toByteArray()).getValue("manual-direct-sync.log")
        assertTrue(log.contains(message))
        assertTrue(log.indexOf(message) < log.indexOf("SECOND_EVENT"))
    }
}
