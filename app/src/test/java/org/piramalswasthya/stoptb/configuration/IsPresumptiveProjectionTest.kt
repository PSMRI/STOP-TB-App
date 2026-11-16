package org.piramalswasthya.stoptb.configuration

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.piramalswasthya.stoptb.model.TBScreeningCache
import org.piramalswasthya.stoptb.model.TbScreeningSymptoms

/**
 * The beneficiary lists stopped observing the whole TB_SCREENING row and now observe the
 * [TbScreeningSymptoms] projection instead. The only thing they derive from it is the
 * Presumptive / Asymptomatic label, so the two [TBScreeningDataset.TbScreeningUtils.isPresumptive]
 * overloads have to agree on every input - otherwise the narrower query silently relabels cards.
 */
class IsPresumptiveProjectionTest {

    private fun cacheOf(answers: List<Boolean?>, asymptomatic: String?) = TBScreeningCache(
        benId = 1L,
        coughMoreThan2Weeks = answers[0],
        bloodInSputum = answers[1],
        feverMoreThan2Weeks = answers[2],
        riseOfFever = answers[3],
        lossOfAppetite = answers[4],
        lossOfWeight = answers[5],
        nightSweats = answers[6],
        historyOfTb = answers[7],
        takingAntiTBDrugs = answers[8],
        familySufferingFromTB = answers[9],
        asymptomatic = asymptomatic
    )

    private fun projectionOf(answers: List<Boolean?>, asymptomatic: String?) = TbScreeningSymptoms(
        benId = 1L,
        coughMoreThan2Weeks = answers[0],
        bloodInSputum = answers[1],
        feverMoreThan2Weeks = answers[2],
        riseOfFever = answers[3],
        lossOfAppetite = answers[4],
        lossOfWeight = answers[5],
        nightSweats = answers[6],
        historyOfTb = answers[7],
        takingAntiTBDrugs = answers[8],
        familySufferingFromTB = answers[9],
        asymptomatic = asymptomatic
    )

    /**
     * Every answer slot crossed with every tri-state value is 3^10 combinations, which is more
     * than this needs to prove. Each slot is exercised in isolation against a background of the
     * other two states, which covers every branch of the rule: any-true, all-false, and the
     * mixed case that falls through to the stored asymptomatic value.
     */
    private fun answerPermutations(): List<List<Boolean?>> {
        val states = listOf(true, false, null)
        val cases = mutableListOf<List<Boolean?>>()
        states.forEach { background ->
            cases += List(10) { background }
            for (slot in 0 until 10) {
                states.forEach { value ->
                    cases += List(10) { index -> if (index == slot) value else background }
                }
            }
        }
        return cases
    }

    @Test
    fun `projection and full row agree on every answer and asymptomatic combination`() {
        val asymptomaticValues = listOf("YES", "NO", "", "yes", null)
        var compared = 0
        answerPermutations().forEach { answers ->
            asymptomaticValues.forEach { asymptomatic ->
                val fromCache = TBScreeningDataset.TbScreeningUtils
                    .isPresumptive(cacheOf(answers, asymptomatic))
                val fromProjection = TBScreeningDataset.TbScreeningUtils
                    .isPresumptive(projectionOf(answers, asymptomatic))
                assertThat(fromProjection).isEqualTo(fromCache)
                compared++
            }
        }
        assertThat(compared).isEqualTo(answerPermutations().size * asymptomaticValues.size)
    }

    /** The six later questions, in [TbScreeningSymptoms] order, each set alone to [value]. */
    private fun laterAnswer(slot: Int, value: Boolean?) = List<Boolean?>(6) { index -> if (index == slot) value else null }

    private fun TBScreeningCache.withLater(later: List<Boolean?>) = copy(
        chestPain = later[0], shortnessOfBreath = later[1], fatigue = later[2], otherSymptoms = later[3],
        failureToGainWeightInChildren = later[4], decreasedActivityOrPlayfulnessInChildren = later[5]
    )

    private fun TbScreeningSymptoms.withLater(later: List<Boolean?>) = copy(
        chestPain = later[0], shortnessOfBreath = later[1], fatigue = later[2], otherSymptoms = later[3],
        failureToGainWeightInChildren = later[4], decreasedActivityOrPlayfulnessInChildren = later[5]
    )

    // Fatigue as the only symptom: the form and the server treat it as symptomatic, so the card must too.
    @Test
    fun `a yes on any later question is presumptive on both shapes`() {
        val coreNo = List<Boolean?>(10) { false }
        for (slot in 0 until 6) {
            val later = laterAnswer(slot, true)
            assertThat(TBScreeningDataset.TbScreeningUtils.isPresumptive(cacheOf(coreNo, "NO").withLater(later)))
                .isTrue()
            assertThat(TBScreeningDataset.TbScreeningUtils.isPresumptive(projectionOf(coreNo, "NO").withLater(later)))
                .isTrue()
        }
    }

    @Test
    fun `later questions answered no or not asked keep an all-no core asymptomatic`() {
        val coreNo = List<Boolean?>(10) { false }
        listOf(false, null).forEach { value ->
            for (slot in 0 until 6) {
                val later = laterAnswer(slot, value)
                assertThat(TBScreeningDataset.TbScreeningUtils.isPresumptive(cacheOf(coreNo, "YES").withLater(later)))
                    .isFalse()
                assertThat(TBScreeningDataset.TbScreeningUtils.isPresumptive(projectionOf(coreNo, "YES").withLater(later)))
                    .isFalse()
            }
        }
    }

    @Test
    fun `null row is undecidable for both shapes`() {
        assertThat(TBScreeningDataset.TbScreeningUtils.isPresumptive(null as TBScreeningCache?))
            .isNull()
        assertThat(TBScreeningDataset.TbScreeningUtils.isPresumptive(null as TbScreeningSymptoms?))
            .isNull()
    }

    @Test
    fun `any yes is presumptive, all no is not, unanswered falls back to asymptomatic`() {
        val allNo = List<Boolean?>(10) { false }
        val allNull = List<Boolean?>(10) { null }
        val oneYes = List<Boolean?>(10) { index -> index == 4 }

        assertThat(TBScreeningDataset.TbScreeningUtils.isPresumptive(projectionOf(oneYes, "YES")))
            .isTrue()
        assertThat(TBScreeningDataset.TbScreeningUtils.isPresumptive(projectionOf(allNo, "NO")))
            .isFalse()
        assertThat(TBScreeningDataset.TbScreeningUtils.isPresumptive(projectionOf(allNull, "NO")))
            .isTrue()
        assertThat(TBScreeningDataset.TbScreeningUtils.isPresumptive(projectionOf(allNull, "YES")))
            .isFalse()
        assertThat(TBScreeningDataset.TbScreeningUtils.isPresumptive(projectionOf(allNull, null)))
            .isNull()
    }
}
