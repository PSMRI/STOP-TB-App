package org.piramalswasthya.stoptb.helpers

import org.junit.Assert.*
import org.junit.Test

class SyncSummaryHistoryTest {
    @Test fun timestampsEveryReportAndTracksRunCompletion() {
        val history = SyncSummaryHistory()
        history.record("SYNC START | runId=a | direction=PULL", 1000L)
        history.record("SYNC MODULE | runId=a | direction=PULL | module=TB | outcome=SUCCESS", 2000L)
        history.record("SYNC END | runId=a | direction=PULL | outcome=WORKERS_SUCCEEDED | durationMs=3000", 4000L)
        val run = history.snapshot().single()
        assertEquals(1000L, run.startedAt)
        assertTrue(run.reports.single().startsWith("eventTime=2000"))
        assertEquals("4000", run.details["endedAt"])
    }
    @Test fun skipDetailsDoNotOverwriteRunOutcomeOrEvictModuleCounts() {
        val history = SyncSummaryHistory()
        history.record("SYNC END | runId=a | direction=PULL | outcome=WORKERS_SUCCEEDED")
        history.record("SYNC MODULE | runId=a | direction=PULL | module=TB | skipped=105")
        repeat(105) { history.record("SYNC SKIP | runId=a | direction=PULL | module=TB | benRegId=$it | reason=LOCAL_PENDING_EDITS_PROTECTED", it.toLong()) }
        val run = history.snapshot().single()
        assertEquals(100, run.skippedReports.size)
        assertEquals(1, run.reports.size)
        assertEquals("WORKERS_SUCCEEDED", run.details["outcome"])
        assertTrue(run.skippedReports.last().contains("benRegId=104"))
        assertTrue(run.skippedReports.last().startsWith("eventTime=104"))
    }
    @Test fun boundsReportsAndPreservesUnknownCountsInsteadOfGuessingFailures() {
        val history = SyncSummaryHistory()
        repeat(105) {
            history.record("SYNC MODULE | runId=a | direction=PUSH | module=OPD | outcome=DEFERRED | remaining=3 | failed=UNKNOWN")
        }
        val run = history.snapshot().single()
        assertEquals(100, run.reports.size)
        assertTrue(run.reports.all { it.contains("failed=UNKNOWN") && it.contains("outcome=DEFERRED") })
    }
    @Test fun separatesDirectionsAndDoesNotTreatWorkerSuccessAsRecordSuccess() {
        val history = SyncSummaryHistory()
        history.record("SYNC START | runId=a | direction=PUSH | userId=1")
        history.record("SYNC MODULE | runId=a | direction=PUSH | module=OPD | outcome=PARTIAL_FAILURE | remaining=2")
        history.record("SYNC WORKER END | runId=a | direction=PUSH | outcome=SUCCESS")
        history.record("SYNC START | runId=a | direction=PULL")
        val push = history.snapshot().single { it.direction == "PUSH" }
        assertNull(push.details["outcome"])
        assertTrue(push.reports.single().contains("remaining=2"))
        assertEquals(2, history.snapshot().size)
    }
    @Test fun retainsReasonsAndBoundsHistory() {
        val history = SyncSummaryHistory(2)
        for (id in 1..3) history.record("SYNC START | runId=$id | direction=PUSH")
        history.record("SYNC WORKER END | runId=3 | direction=PUSH | worker=TB | outcome=FAILURE | reason=ConnectException")
        assertEquals(listOf("3", "2"), history.snapshot().map { it.runId })
        assertTrue(history.snapshot().first().reports.single().contains("ConnectException"))
    }
    @Test fun ignoresMalformedAndUnrelatedInput() {
        val history = SyncSummaryHistory()
        listOf("", "SYNC START", "SYNC START | runId= | direction=PUSH", "SYNC START | runId=a | direction=UNKNOWN", "private response").forEach { history.record(it) }
        assertTrue(history.snapshot().isEmpty())
    }
    @Test fun endStatusAndIdentitySurviveLaterModuleEvents() {
        val history = SyncSummaryHistory()
        history.record("SYNC START | runId=a | direction=PUSH | userId=7")
        history.record("SYNC END | runId=a | direction=PUSH | outcome=PARTIAL_FAILURE")
        history.record("SYNC MODULE | runId=a | direction=PUSH | outcome=SUCCESS | module=OPD")
        assertEquals("PARTIAL_FAILURE", history.snapshot().single().details["outcome"])
        assertEquals("7", history.snapshot().single().details["userId"])
    }
}
