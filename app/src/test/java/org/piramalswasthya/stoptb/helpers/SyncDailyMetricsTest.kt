package org.piramalswasthya.stoptb.helpers

import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test

class SyncDailyMetricsTest {
    @Test fun pendingRetryDoesNotClearAnUnresolvedFailureAndUnknownIdsAreNotInventedRecords() {
        val module = syncDailyModules(listOf(event("1", "SAVE", "SUCCESS", 0), event("1", "PUSH", "FAILURE", 1), event("1", "PUSH", "PENDING", 2), event("UNKNOWN", "PUSH", "FAILURE", 3)), null)
            .single { it.name == "GENERAL_OPD" }
        assertEquals(1, module.failed)
        assertTrue(module.unidentifiedFailure)
    }
    private fun event(id: String, action: String, outcome: String, at: Long, day: String = "2026-10-14") =
        SyncDailyEvent("GENERAL_OPD", id, action, outcome, "SAFE_REASON", "run-$at", at, day)
    @Test fun retriesDoNotInflateUniqueCountsAndSuccessClearsCurrentFailure() {
        val rows = listOf(event("1", "SAVE", "SUCCESS", 0), event("1", "PUSH", "FAILURE", 1), event("1", "PUSH", "FAILURE", 2), event("1", "PUSH", "SUCCESS", 3))
        val module = syncDailyModules(rows, "2026-10-14").single { it.name == "GENERAL_OPD" }
        assertEquals(1, module.synced)
        assertEquals(0, module.failed)
    }
    @Test fun savingDoesNotPretendThatAPushFailureWasRecovered() {
        val rows = listOf(event("1", "SAVE", "SUCCESS", 1), event("1", "PUSH", "FAILURE", 2))
        val module = syncDailyModules(rows, null).single { it.name == "GENERAL_OPD" }
        assertEquals(1, module.saved)
        assertEquals(1, module.failed)
        assertEquals(0, module.synced)
    }
    @Test fun yesterdayCohortCanRecoverTodayWithoutCountingAsTodaySave() {
        val rows = listOf(event("1", "SAVE", "SUCCESS", 0, "2026-10-13"), event("1", "PUSH", "FAILURE", 1, "2026-10-13"), event("1", "PUSH", "SUCCESS", 2), event("1", "PUSH", "SUCCESS", 3))
        assertEquals(0, syncDailyModules(rows, null).single { it.name == "GENERAL_OPD" }.failed)
        assertEquals(0, syncDailyModules(rows, "2026-10-13").single { it.name == "GENERAL_OPD" }.failed)
        assertEquals(1, syncDailyModules(rows, "2026-10-13").single { it.name == "GENERAL_OPD" }.synced)
        assertEquals(0, syncDailyModules(rows, "2026-10-14").single { it.name == "GENERAL_OPD" }.saved)
        assertEquals(1, syncDailyModules(rows, null).single { it.name == "GENERAL_OPD" }.synced)
    }
    @Test fun dayUsesDeviceTimezoneAndYesterdayMeansCalendarDay() {
        val zone = TimeZone.getTimeZone("Asia/Kolkata")
        val utc = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val now = utc.parse("2026-10-13 19:00")!!.time
        assertEquals("2026-10-14", selectedSyncDay(SyncDayFilter.TODAY, now, zone))
        assertEquals("2026-10-13", selectedSyncDay(SyncDayFilter.YESTERDAY, now, zone))
        assertNull(selectedSyncDay(SyncDayFilter.ALL, now, zone))
    }
    @Test fun pullDoesNotCountAsLocalSaveAndPendingIsNotFailed() {
        val rows = listOf(event("1", "SAVE", "SUCCESS", 1), event("1", "PULL", "SUCCESS", 2), event("1", "PUSH", "PENDING", 3))
        val module = syncDailyModules(rows, null).single { it.name == "GENERAL_OPD" }
        assertEquals(1, module.saved)
        assertEquals(0, module.failed)
        assertEquals(0, module.synced)
        assertEquals(1, module.pending)
    }

    @Test fun serverDownloadsAndUnrelatedUploadsDoNotInflateLocalWork() {
        val rows = (1..612).map { event("server-$it", "PULL", "SUCCESS", 1) } + listOf(
            event("old", "PUSH", "SUCCESS", 2), event("other", "PUSH", "FAILURE", 2),
            event("1", "SAVE", "SUCCESS", 3), event("2", "SAVE", "SUCCESS", 3))
        val module = syncDailyModules(rows, null).single { it.name == "GENERAL_OPD" }
        assertEquals(2, module.saved)
        assertEquals(0, module.synced)
        assertEquals(0, module.failed)
        assertEquals(2, module.pending)
    }

    @Test fun newerLocalEditNeedsItsOwnAcknowledgement() {
        val rows = listOf(event("1", "SAVE", "SUCCESS", 0), event("1", "PUSH", "SUCCESS", 1), event("1", "SAVE", "SUCCESS", 2))
        val module = syncDailyModules(rows, null).single { it.name == "GENERAL_OPD" }
        assertEquals(1, module.saved)
        assertEquals(0, module.synced)
        assertEquals(1, module.pending)
    }

    @Test fun statusesPartitionSelectedLocalSaveCohort() {
        val rows = listOf(event("1", "SAVE", "SUCCESS", 0), event("1", "PUSH", "SUCCESS", 1),
            event("2", "SAVE", "SUCCESS", 0), event("2", "REGISTER", "FAILURE", 1),
            event("3", "SAVE", "SUCCESS", 0), event("3", "REGISTER", "SUCCESS", 1))
        val module = syncDailyModules(rows, null).single { it.name == "GENERAL_OPD" }
        assertEquals(3, module.saved)
        assertEquals(1, module.synced)
        assertEquals(1, module.failed)
        assertEquals(1, module.pending)
        assertEquals(module.saved, module.synced + module.failed + module.pending)
        assertEquals("2", module.failures.single().recordId)
    }
}
