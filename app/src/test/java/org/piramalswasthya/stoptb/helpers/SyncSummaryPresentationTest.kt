package org.piramalswasthya.stoptb.helpers

import org.junit.Assert.*
import org.junit.Test

class SyncSummaryPresentationTest {
    @Test fun blockedSkipsExcludeUnchangedAndDuplicatesButKeepSaveBlockers() {
        val snapshot = run(skips = listOf(
            "module=BEN | reason=SERVER_VERSION_NOT_NEWER_AND_FIELDS_UNCHANGED",
            "module=BEN | reason=SERVER_VERSION_NOT_NEWER",
            "module=HH | reason=DUPLICATE_HOUSEHOLD_IN_RESPONSE",
            "module=BEN | reason=BENEFICIARY_ID_MISSING",
            "module=OPD | reason=LOCAL_PENDING_EDITS_PROTECTED"))
        val groups = summaryBlockedSkipGroups(snapshot)
        assertEquals(2, groups.size)
        assertTrue(groups.none { it.normal })
        assertTrue(groups.any { it.module == "BEN" })
        assertTrue(groups.any { it.module == "OPD" })
    }
    private fun run(reports: List<String> = emptyList(), skips: List<String> = emptyList()) = SyncSummaryRun("a", "PULL", 1L, emptyMap(), reports, skips)
    @Test fun repeatedPageIsNotDoubleCountedAndUnknownFailureIsNotZero() {
        val module = summaryModules(run(listOf("module=BEN | outcome=SUCCESS | page=0 | saved=8",
            "module=BEN | outcome=SUCCESS | page=0 | saved=8", "module=BEN | outcome=SUCCESS | page=1 | saved=2"))).single()
        assertEquals(10L, module.saved)
        assertNull(module.failed)
    }
    @Test fun earlierFailureCannotBeHiddenByLaterSuccessOrRemainingGuessedAsFailure() {
        val module = summaryModules(run(listOf("module=OPD | outcome=FAILURE | failed=1",
            "module=OPD | outcome=SUCCESS | saved=2 | remaining=3"))).single()
        assertEquals("Needs attention", module.status)
        assertNull(module.failed)
        assertEquals(3L, module.remaining)
    }
    @Test fun skipReasonsAreGroupedAndExpectedSkipsAreNormal() {
        val groups = summarySkipGroups(run(skips = listOf("module=BEN | reason=SERVER_VERSION_NOT_NEWER_AND_FIELDS_UNCHANGED | benId=1",
            "module=BEN | reason=SERVER_VERSION_NOT_NEWER_AND_FIELDS_UNCHANGED | benId=2", "module=HH | reason=BENEFICIARY_OR_HOUSEHOLD_ID_MISSING")))
        assertFalse(groups.first().normal)
        assertTrue(groups.first().description.contains("non-household"))
        assertTrue(groups.last().normal)
        assertEquals(2, groups.last().reports.size)
    }
    @Test fun malformedUnknownAndWorkerCompletionStayConservative() {
        assertTrue(summaryFields("bad input").isEmpty())
        assertEquals("Workers completed", summaryStatus("WORKERS_SUCCEEDED"))
        assertEquals("Status not confirmed", summaryStatus("UNKNOWN"))
        assertNull(summaryModules(run(listOf("module=OPD | saved=-1 | failed=UNKNOWN"))).single().saved)
    }
    @Test fun pullSavedAndSkippedCountsUseLatestPageSnapshots() {
        val module = summaryModules(run(listOf(
            "module=GENERAL_OPD | outcome=SUCCESS | page=0 | saved=3 | skipped=2 | failed=0",
            "module=GENERAL_OPD | outcome=SUCCESS | page=0 | saved=3 | skipped=2 | failed=0",
            "module=GENERAL_OPD | outcome=SUCCESS | page=1 | saved=4 | skipped=1 | failed=0"))).single()
        assertEquals(7L, module.saved)
        assertEquals(3L, module.skipped)
        assertEquals(0L, module.failed)
    }
    @Test fun skipDetailsStayWithTheirModuleWithoutInventingAggregateCounts() {
        val snapshot = run(skips = listOf(
            "module=HOUSEHOLD | reason=DUPLICATE_HOUSEHOLD_IN_RESPONSE | householdId=42 | eventTime=123",
            "module=GENERAL_OPD | reason=DURATION_NOT_POSITIVE_OR_FREQUENCY_OR_UNIT_BLANK | id=7"))
        assertTrue(summaryModules(snapshot).isEmpty())
        val groups = summarySkipGroups(snapshot)
        val household = groups.single { it.module == "HOUSEHOLD" }
        assertTrue(household.normal)
        assertEquals("42", summaryFields(household.reports.single())["householdId"])
        assertFalse(groups.single { it.module == "GENERAL_OPD" }.normal)
    }
}
