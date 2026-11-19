package org.piramalswasthya.stoptb.ui.contact_tracing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.model.dynamicEntity.CounsellingOptionDto
import org.piramalswasthya.stoptb.model.dynamicEntity.CounsellingQuestionDto

/** STOP-620: TPT Regimen Advised stays disabled until TPT Start Date is entered. */
class TptRegimenGateTest {

    private fun regimen(value: String? = null, locked: Boolean = false) = CounsellingQuestionDto(
        questionId = 1, questionUuid = QUESTION_UUID_REGIMEN_ADVISED, questionText = "Regimen",
        questionType = "RADIO", isMandatory = true, displayOrder = 2,
        options = RegimenAdvised.entries.take(6).mapIndexed { i, r ->
            CounsellingOptionDto(optionId = i, optionLabel = r.optionValue, optionValue = r.optionValue, displayOrder = i)
        },
        value = value, lockedAnswer = locked
    )

    private fun startDate(value: String?) = CounsellingQuestionDto(
        questionId = 2, questionUuid = QUESTION_UUID_TPT_START_DATE, questionText = "Start date",
        questionType = "DATE", isMandatory = true, displayOrder = 1, value = value
    )

    private fun apply(vararg questions: CounsellingQuestionDto) =
        TptRegimenGate.apply(questions.associateBy { it.questionUuid })

    @Test
    fun `without a start date every regimen option is disabled and the hint is set`() {
        val r = regimen()
        apply(r, startDate(null))
        assertTrue(r.options!!.none { it.enabled })
        assertEquals(R.string.tpt_regimen_requires_start_date, r.disabledOptionHintRes)
    }

    @Test
    fun `a blank start date counts as missing`() {
        val r = regimen()
        apply(r, startDate("  "))
        assertTrue(r.options!!.none { it.enabled })
    }

    @Test
    fun `entering a start date enables every option and drops the hint`() {
        val r = regimen()
        apply(r, startDate(null))
        apply(r, startDate("17-11-2026"))
        assertTrue(r.options!!.all { it.enabled })
        assertNull(r.disabledOptionHintRes)
    }

    @Test
    fun `a regimen left without a start date is cleared`() {
        val r = regimen(value = "3HP").apply { errorMessage = "old" }
        apply(r, startDate(null))
        assertNull(r.value)
        assertNull(r.errorMessage)
    }

    @Test
    fun `a locked saved regimen is never cleared`() {
        val r = regimen(value = "3HP", locked = true)
        apply(r, startDate(null))
        assertEquals("3HP", r.value)
    }

    @Test
    fun `a regimen with a start date is kept`() {
        val r = regimen(value = "6H")
        apply(r, startDate("17-11-2026"))
        assertEquals("6H", r.value)
    }

    @Test
    fun `no-op when the start date question is not in the map`() {
        val r = regimen(value = "6H")
        TptRegimenGate.apply(mapOf(r.questionUuid to r))
        assertEquals("6H", r.value)
        assertTrue(r.options!!.all { it.enabled })
        assertFalse(r.disabledOptionHintRes != null)
    }
}
