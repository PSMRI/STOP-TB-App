package org.piramalswasthya.stoptb.helpers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class SyncTraceTest {
    @Test
    fun pushOutcomesDistinguishEmptyPartialAndFailedUploads() {
        assertEquals("NO_PENDING_DATA", syncPushOutcome(0, 0))
        assertEquals("SUCCESS", syncPushOutcome(20, 20))
        assertEquals("FAILURE", syncPushOutcome(20, 0))
        assertEquals("PARTIAL_FAILURE", syncPushOutcome(20, 18))
    }

    @Test
    fun dataFailureCannotBeHiddenByALaterSuccessfulModule() {
        val result = SyncDataResult()
        assertEquals("NOT_REPORTED", result.outcome())
        result.record("NO_PENDING_DATA")
        assertEquals("REPORTED_MODULES_OK", result.outcome())
        result.record("PARTIAL_FAILURE")
        result.record("SUCCESS")
        assertEquals("PARTIAL_FAILURE", result.outcome())
    }

    @Test
    fun traceSurvivesIoAndParallelRepositoryCallsWithoutLeakingToAnotherRun() = runBlocking {
        withContext(SyncTrace("run-a", "PULL")) {
            val trace = async(Dispatchers.IO) { currentCoroutineContext()[SyncTrace] }.await()
            assertEquals("run-a", trace?.runId)
            assertEquals("PULL", trace?.direction)
        }
        withContext(SyncTrace("run-b", "PUSH")) {
            assertEquals("run-b", currentCoroutineContext()[SyncTrace]?.runId)
        }
        assertNull(currentCoroutineContext()[SyncTrace])
    }

    @Test
    fun deferredRecordsAreNotReportedAsFullyCompleted() {
        val result = SyncDataResult()
        result.record("DEFERRED")
        result.record("SUCCESS")
        assertEquals("DEFERRED_RECORDS_REMAIN", result.outcome())
        result.record("FAILURE")
        assertEquals("PARTIAL_FAILURE", result.outcome())
    }

    @Test
    fun runOutcomeKeepsWorkerSuccessSeparateFromDataSuccess() {
        assertEquals("PARTIAL_FAILURE", syncRunOutcome(listOf("SUCCEEDED"), "PARTIAL_FAILURE"))
        assertEquals("PARTIAL_FAILURE", syncRunOutcome(listOf("SUCCEEDED", "FAILED"), "NOT_REPORTED"))
        assertEquals("FAILURE", syncRunOutcome(listOf("FAILED"), "NOT_REPORTED"))
        assertEquals("CANCELLED", syncRunOutcome(listOf("CANCELLED"), "NOT_REPORTED"))
        assertEquals("WORKERS_SUCCEEDED", syncRunOutcome(listOf("SUCCEEDED"), "NOT_REPORTED"))
        assertEquals("DEFERRED", syncRunOutcome(listOf("SUCCEEDED"), "DEFERRED_RECORDS_REMAIN"))
    }
}
