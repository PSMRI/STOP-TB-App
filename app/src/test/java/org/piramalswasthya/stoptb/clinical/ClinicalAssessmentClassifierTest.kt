package org.piramalswasthya.stoptb.clinical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClinicalAssessmentClassifierTest {

    private val now = 1_700_000_000_000L
    private val adultDob = dobYearsAgo(40)
    private val childDob = dobYearsAgo(10)

    @Test
    fun symptomaticAbnormalCxrAndNegativeTruenatIsCategory1Symptomatic() {
        val group = classify(
            screenings = listOf(ScreeningFacts(coughMoreThan2Weeks = true)),
            diagnostics = listOf(
                DiagnosticFacts(
                    chestXRayResult = "TB Presumptive",
                    naatResult = "MTB not detected"
                )
            )
        )
        assertEquals(ClinicalAssessmentGroup.CATEGORY_1_SYMPTOMATIC, group)
    }

    @Test
    fun noReportedSymptomsAbnormalCxrAndNegativeTruenatIsCategory1Asymptomatic() {
        val group = classify(
            screenings = listOf(
                ScreeningFacts(coughMoreThan2Weeks = false, asymptomatic = "YES")
            ),
            diagnostics = listOf(
                DiagnosticFacts(
                    chestXRayResult = "Abnormal but not TB Presumptive",
                    naatResult = "TB Negative"
                )
            )
        )
        assertEquals(ClinicalAssessmentGroup.CATEGORY_1_ASYMPTOMATIC, group)
    }

    @Test
    fun symptomaticAbnormalCxrWhenTruenatCouldNotBeConductedIsCategory1Symptomatic() {
        val group = classify(
            screenings = listOf(ScreeningFacts(feverMoreThan2Weeks = true)),
            diagnostics = listOf(
                DiagnosticFacts(
                    chestXRayResult = "Positive",
                    isNaatConducted = false,
                    reasonNotConductedNaat = "Cartridge unavailable"
                )
            )
        )
        assertEquals(ClinicalAssessmentGroup.CATEGORY_1_SYMPTOMATIC, group)
    }

    @Test
    fun asymptomaticAbnormalCxrWhenTruenatCouldNotBeConductedIsCategory1Asymptomatic() {
        val group = classify(
            diagnostics = listOf(
                DiagnosticFacts(
                    chestXRayResult = "Abnormal",
                    isNaatConducted = false,
                    trueNatOrderStatus = "CLOSED"
                )
            )
        )
        assertEquals(ClinicalAssessmentGroup.CATEGORY_1_ASYMPTOMATIC, group)
    }

    @Test
    fun pendingTruenatOrderIsNotTreatedAsCouldNotBeConducted() {
        val group = classify(
            diagnostics = listOf(
                DiagnosticFacts(
                    chestXRayResult = "Abnormal",
                    isNaatConducted = false,
                    trueNatOrderStatus = "PENDING"
                )
            )
        )
        assertEquals(null, group)
    }

    @Test
    fun explicitTruenatReasonStillQualifiesWhenOrderIsPending() {
        val group = classify(
            diagnostics = listOf(
                DiagnosticFacts(
                    chestXRayResult = "Abnormal",
                    isNaatConducted = false,
                    reasonNotConductedNaat = "Cartridge unavailable",
                    trueNatOrderStatus = "PENDING"
                )
            )
        )
        assertEquals(ClinicalAssessmentGroup.CATEGORY_1_ASYMPTOMATIC, group)
    }

    @Test
    fun suspectedNaatFlagWithoutReasonIsNotCouldNotBeConducted() {
        val group = classify(
            suspected = listOf(
                SuspectedFacts(
                    chestXRayResult = "Abnormal",
                    isNaatConducted = false
                )
            )
        )
        assertEquals(null, group)
    }

    @Test
    fun suspectedRefusalReasonWithAbnormalCxrIsCategory1() {
        val group = classify(
            suspected = listOf(
                SuspectedFacts(
                    chestXRayResult = "Abnormal",
                    reasonForRefusalMTB = "Could not produce sputum"
                )
            )
        )
        assertEquals(ClinicalAssessmentGroup.CATEGORY_1_ASYMPTOMATIC, group)
    }

    @Test
    fun positiveTruenatIsNotCategory1() {
        val group = classify(
            screenings = listOf(ScreeningFacts(coughMoreThan2Weeks = true)),
            diagnostics = listOf(
                DiagnosticFacts(
                    chestXRayResult = "TB Presumptive",
                    naatResult = "MTB detected"
                )
            )
        )
        assertNull(group)
    }

    @Test
    fun symptomaticPersonWhoCouldNotHaveCxrIsCategory2() {
        val group = classify(
            screenings = listOf(ScreeningFacts(nightSweats = true)),
            diagnostics = listOf(
                DiagnosticFacts(
                    isChestXRayDone = false,
                    reasonNotConductedChestXray = "Power issue"
                )
            )
        )
        assertEquals(ClinicalAssessmentGroup.CATEGORY_2, group)
    }

    @Test
    fun pregnantHighRiskPersonWithoutCxrIsCategory2() {
        val group = classify(
            reproductiveStatusId = 1,
            screenings = listOf(ScreeningFacts(asymptomatic = "YES"))
        )
        assertEquals(ClinicalAssessmentGroup.CATEGORY_2, group)
    }

    @Test
    fun childUnder14WithHighRiskAndNoCxrIsCategory2() {
        val group = classify(
            dobMillis = childDob,
            screenings = listOf(
                ScreeningFacts(keyPopulationRiskFactors = listOf("DIABETES"))
            )
        )
        assertEquals(ClinicalAssessmentGroup.CATEGORY_2, group)
    }

    @Test
    fun age14WithOnlyHighRiskAndNoCxrBlockIsNotCategory2() {
        val group = classify(
            dobMillis = dobYearsAgo(14),
            screenings = listOf(
                ScreeningFacts(keyPopulationRiskFactors = listOf("DIABETES"))
            )
        )
        assertNull(group)
    }

    @Test
    fun symptomaticAdultWaitingForCxrIsNotCategory2() {
        val group = classify(
            screenings = listOf(ScreeningFacts(coughMoreThan2Weeks = true))
        )
        assertNull(group)
    }

    @Test
    fun notApplicableRiskFactorAloneIsNotHighRisk() {
        val group = classify(
            screenings = listOf(
                ScreeningFacts(keyPopulationRiskFactors = listOf("NOT_APPLICABLE"))
            ),
            diagnostics = listOf(DiagnosticFacts(isChestXRayDone = false))
        )
        assertNull(group)
    }

    @Test
    fun positiveXrayAndNegativeNaatStillShowsUnderCategory1() {
        val group = classify(
            suspected = listOf(
                SuspectedFacts(
                    chestXRayResult = "TB Presumptive",
                    naatResult = "MTB not detected",
                    hasSymptoms = true
                )
            )
        )
        assertEquals(ClinicalAssessmentGroup.CATEGORY_1_SYMPTOMATIC, group)
    }

    @Test
    fun rifIndeterminateWithoutTheNewGroupsStillShows() {
        val group = classify(
            diagnostics = listOf(DiagnosticFacts(trueNatRifResult = "Indeterminate"))
        )
        assertEquals(ClinicalAssessmentGroup.RIF_INDETERMINATE, group)
    }

    @Test
    fun abnormalCxrTakesPriorityOverPregnancyWithoutCxr() {
        val group = classify(
            reproductiveStatusId = 1,
            screenings = listOf(ScreeningFacts(coughMoreThan2Weeks = true)),
            diagnostics = listOf(
                DiagnosticFacts(
                    chestXRayResult = "TB Presumptive",
                    naatResult = "Negative"
                )
            )
        )
        assertEquals(ClinicalAssessmentGroup.CATEGORY_1_SYMPTOMATIC, group)
    }

    @Test
    fun pregnancyRecordedOnlyOnVitalsStillQualifiesForCategory2() {
        val group = classify(
            screenings = listOf(ScreeningFacts()),
            vitalRiskFactors = listOf("PREGNANCY")
        )
        assertEquals(ClinicalAssessmentGroup.CATEGORY_2, group)
    }

    private fun classify(
        dobMillis: Long = adultDob,
        reproductiveStatusId: Int = 0,
        screenings: List<ScreeningFacts> = emptyList(),
        diagnostics: List<DiagnosticFacts> = emptyList(),
        suspected: List<SuspectedFacts> = emptyList(),
        vitalRiskFactors: List<String> = emptyList()
    ) = ClinicalAssessmentClassifier.classifyBeneficiary(
        dobMillis = dobMillis,
        reproductiveStatusId = reproductiveStatusId,
        screenings = screenings,
        diagnostics = diagnostics,
        suspected = suspected,
        vitalRiskFactors = vitalRiskFactors,
        nowMillis = now
    )

    private fun dobYearsAgo(years: Int): Long = now - years * 365L * 24L * 60L * 60L * 1000L
}
