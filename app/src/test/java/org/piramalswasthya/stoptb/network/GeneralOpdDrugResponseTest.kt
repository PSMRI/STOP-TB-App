package org.piramalswasthya.stoptb.network

import com.squareup.moshi.JsonDataException
import org.junit.Assert.*
import org.junit.Test

class GeneralOpdDrugResponseTest {
    @Test
    fun restoresEachDrugsOwnFormDurationFrequencyAndInstruction() {
        val rows = GeneralOpdDrugResponse.parsePrescriptions("""
            [
              {"drugID":1345,"drugName":"Acetylsalicylic acid","itemFormID":1,"drugForm":"Tablet",
               "frequency":"Twice Daily(BD)","duration":5,"durationUnit":"Day(s)",
               "qtyPrescribed":10,"instructions":"After food"},
              {"drugID":1360,"drugName":"Amikacin Sulphate","itemFormID":13,"drugForm":"Injection",
               "frequency":"Once in a Week","duration":1,"durationUnit":"Day(s)",
               "qtyPrescribed":1,"instructions":"With food"}
            ]
        """.trimIndent(), 7)
        assertEquals(listOf(7, 7), rows.map { it.opdId })
        assertEquals(listOf(0, 1), rows.map { it.position })
        assertEquals(listOf(1345, 1360), rows.map { it.drugId })
        assertEquals(listOf(1, 13), rows.map { it.itemFormId })
        assertEquals(listOf("Tablet", "Injection"), rows.map { it.drugForm })
        assertEquals(listOf("Tablet Acetylsalicylic acid", "Injection Amikacin Sulphate"), rows.map { it.medicine })
        assertEquals(listOf("Twice Daily(BD)", "Once in a Week"), rows.map { it.frequency })
        // The UI counter is duration, not qtyPrescribed from the server.
        assertEquals(listOf(5, 1), rows.map { it.durationCount })
        assertEquals(listOf("Day(s)", "Day(s)"), rows.map { it.durationUnit })
        assertEquals(listOf("After food", "With food"), rows.map { it.instruction })
    }

    @Test
    fun emptyDrugsAllowsLegacyFallbackAndNamesAreNotSplitOnCommas() {
        assertTrue(GeneralOpdDrugResponse.parsePrescriptions("[]", 7).isEmpty())
        val rows = GeneralOpdDrugResponse.parsePrescriptions("""
            [{"drugID":1,"drugName":"Medicine, combination","frequency":"Once daily",
              "duration":2,"durationUnit":"Days"}]
        """.trimIndent(), 7)
        assertEquals("Medicine, combination", rows.single().medicine)
        assertEquals("Day(s)", rows.single().durationUnit)
        assertEquals("", rows.single().instruction)
        assertNull(rows.single().itemFormId)
    }

    @Test(expected = JsonDataException::class)
    fun incompleteDrugCannotSilentlyReplaceSavedPrescriptions() {
        GeneralOpdDrugResponse.parsePrescriptions("""[{"drugID":1345,"drugName":"Medicine"}]""", 7)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidDurationCannotBecomeADefaultOneDayPrescription() {
        GeneralOpdDrugResponse.parsePrescriptions("""
            [{"drugID":1,"drugName":"Medicine","frequency":"Once daily","duration":0,"durationUnit":"Day(s)"}]
        """.trimIndent(), 7)
    }
}
