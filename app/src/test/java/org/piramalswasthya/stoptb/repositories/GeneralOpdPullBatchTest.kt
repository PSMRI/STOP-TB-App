package org.piramalswasthya.stoptb.repositories

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.piramalswasthya.stoptb.network.GeneralOpdDrugResponse

class GeneralOpdPullBatchTest {
    @Test
    fun skipReasonExplainsValidationWithoutExposingExceptionValues() {
        assertEquals("INVALID_DRUGS_ARRAY", generalOpdSkipReason(IllegalArgumentException("Invalid General OPD drugs array")))
        assertEquals("DURATION_NOT_POSITIVE_OR_FREQUENCY_OR_UNIT_BLANK",
            generalOpdSkipReason(IllegalArgumentException("Invalid General OPD prescription details")))
        assertFalse(generalOpdSkipReason(IllegalArgumentException("patient-secret-value")).contains("patient-secret-value"))
    }

    private val valid = """[{"drugID":1,"drugName":"Medicine","frequency":"Once daily","duration":2,"durationUnit":"Day(s)","itemFormID":1,"drugForm":"Tablet"}]"""

    @Test
    fun validInvalidValidContinuesAndPreservesInvalidRecordsSavedData() = runBlocking {
        val invalidRecords = listOf(
            valid.replace("\"duration\":2", "\"duration\":0"),
            valid.replace("Once daily", ""),
            valid.replace("\"drugName\":\"Medicine\",", ""),
            "["
        )
        for (invalid in invalidRecords) {
            val stored = mutableMapOf(1 to "old prescription")
            val errors = mutableListOf<Int>()
            val complete = processGeneralOpdPullBatch(
                listOf(0 to valid, 1 to invalid, 2 to valid),
                parse = { GeneralOpdDrugResponse.parsePrescriptions(it.second, it.first) },
                save = { record, rows -> stored[record.first] = rows.single().drugForm!! },
                onInvalid = { index, _ -> errors.add(index) }
            )
            assertFalse(complete)
            assertEquals(mapOf(0 to "Tablet", 1 to "old prescription", 2 to "Tablet"), stored)
            assertEquals(listOf(1), errors)
        }
    }

    @Test
    fun validAndLegacyEmptyDrugsCompleteSuccessfully() = runBlocking {
        val sizes = mutableListOf<Int>()
        assertTrue(processGeneralOpdPullBatch(listOf(valid, "[]"),
            parse = { GeneralOpdDrugResponse.parsePrescriptions(it, 7) },
            save = { _, rows -> sizes.add(rows.size) },
            onInvalid = { _, _ -> fail("Valid records must not be skipped") }))
        assertEquals(listOf(1, 0), sizes)
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNotTreatedAsAnInvalidRecord() = runBlocking {
        processGeneralOpdPullBatch(listOf(valid),
            parse = { throw CancellationException("cancelled") },
            save = { _, _ -> fail("Cancelled batch must not save") },
            onInvalid = { _, _ -> fail("Cancellation must propagate") })
        Unit
    }

    @Test(expected = IllegalStateException::class)
    fun databaseFailureIsNotSilentlySkipped() = runBlocking {
        processGeneralOpdPullBatch(listOf(valid),
            parse = { GeneralOpdDrugResponse.parsePrescriptions(it, 7) },
            save = { _, _ -> throw IllegalStateException("database unavailable") },
            onInvalid = { _, _ -> fail("Save errors must propagate") })
        Unit
    }
}
