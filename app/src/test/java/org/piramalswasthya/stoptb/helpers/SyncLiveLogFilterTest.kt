package org.piramalswasthya.stoptb.helpers

import org.junit.Assert.*
import org.junit.Test

class SyncLiveLogFilterTest {
    @Test fun onlyExplicitInteractiveApiPreviewsAreVisible() {
        val filter = SyncLiveLogFilter()
        assertTrue(filter.accepts("SyncApi", "HTTP EXCHANGE | trigger=MANUAL_REFRESH | callId=1\n{}"))
        assertTrue(filter.accepts("SyncApi", "HTTP EXCHANGE | trigger=DIRECT_SAVE | callId=2\n{}"))
        assertFalse(filter.accepts("SyncApi", "HTTP EXCHANGE | trigger=AUTO_PULL | callId=3\n{}"))
        assertFalse(filter.accepts("SyncApi", "HTTP EXCHANGE | callId=4\n | trigger=MANUAL_REFRESH"))
    }
    @Test fun manualRequestIsVisibleEvenWhenAnExistingAutoChainIsKept() {
        val filter = SyncLiveLogFilter()
        assertTrue(filter.accepts("WorkerUtils", "Camp sync [MANUAL_REFRESH] requested by user"))
        assertFalse(filter.accepts("WorkerUtils", "Pull [AUTO_PULL] enqueued"))
    }
    @Test fun autoEventsAndUnattributedHttpAreHidden() {
        val filter = SyncLiveLogFilter()
        assertFalse(filter.accepts("SyncTrace", "SYNC START | runId=a | direction=PULL | trigger=AUTO_PULL"))
        assertFalse(filter.accepts("SyncTrace", "SYNC MODULE | runId=a | direction=PULL | saved=10"))
        assertFalse(filter.accepts("OkHttp", "<-- 200 /api"))
        assertFalse(filter.accepts("Repo", "manual save completed"))
    }
    @Test fun overlappingManualAndAutoChainsStaySeparate() {
        val filter = SyncLiveLogFilter()
        assertTrue(filter.accepts("SyncTrace", "SYNC START | runId=m | direction=PUSH | trigger=MANUAL_REFRESH"))
        assertFalse(filter.accepts("SyncTrace", "SYNC START | runId=a | direction=PULL | trigger=AUTO_PULL"))
        assertTrue(filter.accepts("SyncTrace", "SYNC MODULE | runId=m | direction=PUSH | outcome=FAILURE"))
        assertFalse(filter.accepts("SyncTrace", "SYNC MODULE | runId=m | direction=PULL | outcome=SUCCESS"))
        assertTrue(filter.accepts("SyncTrace", "SYNC END | runId=m | direction=PUSH | outcome=SUCCESS"))
    }
    @Test fun resumedManualWorkerCanEstablishItsSource() {
        val filter = SyncLiveLogFilter()
        assertTrue(filter.accepts("SyncTrace", "SYNC WORKER START | runId=m | direction=PULL | trigger=MANUAL_REFRESH"))
        assertTrue(filter.accepts("SyncTrace", "SYNC SKIP | runId=m | direction=PULL | reason=MISSING_ID"))
    }
    @Test fun localSaveIsShownButUnattributedDirectCallsAreNotGuessedManual() {
        val filter = SyncLiveLogFilter()
        assertTrue(filter.accepts("SyncTrace", "LOCAL SAVE | module=GENERAL_OPD | records=1 | outcome=SUCCESS"))
        assertFalse(filter.accepts("SyncTrace", "SYNC MODULE | runId=DIRECT_CALL | direction=PUSH | outcome=SUCCESS"))
        assertFalse(filter.accepts("SyncTrace", "SYNC START | runId=d | direction=PUSH | trigger=DIRECT"))
        assertFalse(filter.accepts("SyncTrace", "SYNC MODULE | runId=d | direction=PUSH | outcome=SUCCESS"))
    }
    @Test fun malformedLogsAndEvictedRunsAreNotGuessedManual() {
        val filter = SyncLiveLogFilter(1)
        assertFalse(filter.accepts("SyncTrace", "SYNC MODULE | direction=PUSH"))
        filter.accepts("SyncTrace", "SYNC START | runId=one | direction=PUSH | trigger=MANUAL_REFRESH")
        filter.accepts("SyncTrace", "SYNC START | runId=two | direction=PUSH | trigger=MANUAL_REFRESH")
        assertFalse(filter.accepts("SyncTrace", "SYNC END | runId=one | direction=PUSH"))
        assertTrue(filter.accepts("SyncTrace", "SYNC END | runId=two | direction=PUSH"))
    }
    @Test fun automaticRunFloodDoesNotEvictManualSourceTracking() {
        val filter = SyncLiveLogFilter(1)
        filter.accepts("SyncTrace", "SYNC START | runId=m | direction=PUSH | trigger=MANUAL_REFRESH")
        repeat(1000) { index ->
            assertFalse(filter.accepts("SyncTrace", "SYNC START | runId=auto-$index | direction=PULL | trigger=AUTO_PULL"))
        }
        assertTrue(filter.accepts("SyncTrace", "SYNC MODULE | runId=m | direction=PUSH | outcome=SUCCESS"))
    }
}
