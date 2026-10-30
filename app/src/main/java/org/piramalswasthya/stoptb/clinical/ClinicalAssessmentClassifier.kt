package org.piramalswasthya.stoptb.clinical

import org.piramalswasthya.stoptb.model.TBDiagnosticsCache
import org.piramalswasthya.stoptb.model.TBScreeningCache
import org.piramalswasthya.stoptb.model.TBSuspectedCache

/**
 * Who belongs on the Clinical Assessment & Diagnosis referral list, and which group
 * they are shown under. [ClinicalAssessmentSql] must accept the same people.
 *
 * Category 1 is an abnormal chest X-ray with a negative Truenat result, or a Truenat
 * that could not be conducted, split by whether TB symptoms were reported.
 * Category 2 is someone with TB symptoms or a high-risk factor who could not have a
 * chest X-ray. A positive X-ray with a negative NAAT, and an indeterminate RIF result,
 * stay on the list when they do not already fall into those two groups.
 */
enum class ClinicalAssessmentGroup {
    CATEGORY_1_SYMPTOMATIC,
    CATEGORY_1_ASYMPTOMATIC,
    CATEGORY_2,
    POSITIVE_XRAY_NEGATIVE_NAAT,
    RIF_INDETERMINATE
}

data class ScreeningFacts(
    val coughMoreThan2Weeks: Boolean? = null,
    val bloodInSputum: Boolean? = null,
    val feverMoreThan2Weeks: Boolean? = null,
    val riseOfFever: Boolean? = null,
    val lossOfAppetite: Boolean? = null,
    val lossOfWeight: Boolean? = null,
    val nightSweats: Boolean? = null,
    val historyOfTb: Boolean? = null,
    val takingAntiTBDrugs: Boolean? = null,
    val familySufferingFromTB: Boolean? = null,
    val chestPain: Boolean? = null,
    val shortnessOfBreath: Boolean? = null,
    val fatigue: Boolean? = null,
    val failureToGainWeightInChildren: Boolean? = null,
    val decreasedActivityOrPlayfulnessInChildren: Boolean? = null,
    val otherSymptoms: Boolean? = null,
    val asymptomatic: String? = null,
    val keyPopulationRiskFactors: List<String>? = null
)

data class DiagnosticFacts(
    val chestXRayResult: String? = null,
    val isChestXRayDone: Boolean? = null,
    val reasonNotConductedChestXray: String? = null,
    val reasonNotConductedChestXrayOther: String? = null,
    val reasonForDenialChestXray: String? = null,
    val reasonForDenialChestXrayOther: String? = null,
    val naatResult: String? = null,
    val isNaatConducted: Boolean? = null,
    val reasonNotConductedNaat: String? = null,
    val trueNatOrderStatus: String? = null,
    val trueNatRifResult: String? = null
)

data class SuspectedFacts(
    val chestXRayResult: String? = null,
    val isChestXRayDone: Boolean? = null,
    val reasonForRefusalXray: String? = null,
    val naatResult: String? = null,
    val isNaatConducted: Boolean? = null,
    val reasonForRefusalMTB: String? = null,
    val hasSymptoms: Boolean = false
)

object ClinicalAssessmentClassifier {

    private val abnormalCxr = setOf(
        "TB PRESUMPTIVE",
        "ABNORMAL BUT NOT TB PRESUMPTIVE",
        "ABNORMAL (NON-TB PRESUMPTIVE)",
        "ABNORMAL",
        "POSITIVE"
    )

    private val legacyAbnormalCxr = setOf(
        "TB PRESUMPTIVE",
        "ABNORMAL BUT NOT TB PRESUMPTIVE"
    )

    private val negativeTruenat = setOf(
        "TB NEGATIVE",
        "MTB NOT DETECTED",
        "MTB NEGATIVE",
        "NEGATIVE",
        "NOT DETECTED"
    )

    private val legacyNegativeNaat = setOf(
        "TB NEGATIVE",
        "MTB NOT DETECTED"
    )

    private val positiveTruenat = setOf(
        "TB POSITIVE",
        "MTB DETECTED",
        "MTB POSITIVE",
        "POSITIVE",
        "DETECTED"
    )

    fun classifyBeneficiary(
        dobMillis: Long,
        reproductiveStatusId: Int,
        screenings: List<ScreeningFacts> = emptyList(),
        diagnostics: List<DiagnosticFacts> = emptyList(),
        suspected: List<SuspectedFacts> = emptyList(),
        vitalRiskFactors: List<String> = emptyList(),
        nowMillis: Long = System.currentTimeMillis()
    ): ClinicalAssessmentGroup? {
        val symptomatic = isSymptomatic(screenings, suspected)
        val highRisk = reproductiveStatusId == 1 ||
            screenings.any { isHighRisk(it.keyPopulationRiskFactors) } ||
            isHighRisk(vitalRiskFactors)
        val pregnant = reproductiveStatusId == 1 ||
            screenings.any { hasPregnancy(it.keyPopulationRiskFactors) } ||
            hasPregnancy(vitalRiskFactors)
        val under14 = dobMillis > 0L && ageYears(dobMillis, nowMillis) < 14
        val cxrAssessed = diagnostics.any { cxrWasAssessed(it.chestXRayResult, it.isChestXRayDone) } ||
            suspected.any { cxrWasAssessed(it.chestXRayResult, it.isChestXRayDone) }
        val cxrBlocked = diagnostics.any { cxrExplicitlyBlocked(it) } ||
            suspected.any { cxrExplicitlyBlocked(it) }
        val abnormal = diagnostics.any { isAbnormalCxr(it.chestXRayResult) } ||
            suspected.any { isAbnormalCxr(it.chestXRayResult) }
        val negative = diagnostics.any { isNegativeTruenat(it.naatResult) } ||
            suspected.any { isNegativeTruenat(it.naatResult) }
        val positive = diagnostics.any { isPositiveTruenat(it.naatResult) } ||
            suspected.any { isPositiveTruenat(it.naatResult) }
        val truenatNotConducted = diagnostics.any { truenatCouldNotBeConducted(it) } ||
            suspected.any { truenatCouldNotBeConducted(it) }
        val hasTbRecord = screenings.isNotEmpty() || diagnostics.isNotEmpty() || suspected.isNotEmpty()

        val category1 = abnormal && !positive && (negative || truenatNotConducted)
        val category2 = hasTbRecord &&
            !cxrAssessed &&
            (pregnant || under14 || cxrBlocked) &&
            (symptomatic || highRisk)
        val legacyPair = diagnostics.any { isLegacyPair(it.chestXRayResult, it.naatResult) } ||
            suspected.any { isLegacyPair(it.chestXRayResult, it.naatResult) }
        val rifIndeterminate = diagnostics.any { isRifIndeterminate(it.trueNatRifResult) }

        return when {
            category1 && symptomatic -> ClinicalAssessmentGroup.CATEGORY_1_SYMPTOMATIC
            category1 -> ClinicalAssessmentGroup.CATEGORY_1_ASYMPTOMATIC
            category2 -> ClinicalAssessmentGroup.CATEGORY_2
            legacyPair -> ClinicalAssessmentGroup.POSITIVE_XRAY_NEGATIVE_NAAT
            rifIndeterminate -> ClinicalAssessmentGroup.RIF_INDETERMINATE
            else -> null
        }
    }

    fun classifyRecords(
        dobMillis: Long,
        reproductiveStatusId: Int,
        screenings: List<TBScreeningCache>,
        diagnostics: List<TBDiagnosticsCache>,
        suspected: List<TBSuspectedCache>,
        vitalRiskFactors: List<String>,
        nowMillis: Long = System.currentTimeMillis()
    ): ClinicalAssessmentGroup? = classifyBeneficiary(
        dobMillis = dobMillis,
        reproductiveStatusId = reproductiveStatusId,
        screenings = screenings.map { it.toFacts() },
        diagnostics = diagnostics.map { it.toFacts() },
        suspected = suspected.map { it.toFacts() },
        vitalRiskFactors = vitalRiskFactors,
        nowMillis = nowMillis
    )

    /** Same year count as the list query: whole 365-day years since [dobMillis]. */
    fun ageYears(dobMillis: Long, nowMillis: Long): Int {
        if (dobMillis <= 0L) return Int.MAX_VALUE
        val seconds = (nowMillis / 1000L) - (dobMillis / 1000L)
        if (seconds <= 0L) return 0
        return (seconds / 60L / 60L / 24L / 365L).toInt()
    }

    private fun isSymptomatic(
        screenings: List<ScreeningFacts>,
        suspected: List<SuspectedFacts>
    ): Boolean {
        if (suspected.any { it.hasSymptoms }) return true
        return screenings.any { screening ->
            listOf(
                screening.coughMoreThan2Weeks,
                screening.bloodInSputum,
                screening.feverMoreThan2Weeks,
                screening.riseOfFever,
                screening.lossOfAppetite,
                screening.lossOfWeight,
                screening.nightSweats,
                screening.historyOfTb,
                screening.takingAntiTBDrugs,
                screening.familySufferingFromTB,
                screening.chestPain,
                screening.shortnessOfBreath,
                screening.fatigue,
                screening.failureToGainWeightInChildren,
                screening.decreasedActivityOrPlayfulnessInChildren,
                screening.otherSymptoms
            ).any { it == true } || screening.asymptomatic.equals("NO", ignoreCase = true)
        }
    }

    private fun isHighRisk(codes: List<String>?): Boolean =
        codes.orEmpty().any { code ->
            val cleaned = code.trim()
            cleaned.isNotEmpty() && !cleaned.equals("NOT_APPLICABLE", ignoreCase = true)
        }

    private fun hasPregnancy(codes: List<String>?): Boolean =
        codes.orEmpty().any { it.contains("PREGNANCY", ignoreCase = true) }

    private fun cxrWasAssessed(result: String?, conducted: Boolean?): Boolean =
        !result.isNullOrBlank() || conducted == true

    private fun cxrExplicitlyBlocked(diagnostics: DiagnosticFacts): Boolean =
        diagnostics.isChestXRayDone == false ||
            filled(diagnostics.reasonNotConductedChestXray) ||
            filled(diagnostics.reasonNotConductedChestXrayOther) ||
            filled(diagnostics.reasonForDenialChestXray) ||
            filled(diagnostics.reasonForDenialChestXrayOther)

    private fun cxrExplicitlyBlocked(suspected: SuspectedFacts): Boolean =
        suspected.isChestXRayDone == false || filled(suspected.reasonForRefusalXray)

    private fun truenatCouldNotBeConducted(diagnostics: DiagnosticFacts): Boolean =
        filled(diagnostics.reasonNotConductedNaat) ||
            (diagnostics.isNaatConducted == false && isClosedOrder(diagnostics.trueNatOrderStatus))

    private fun truenatCouldNotBeConducted(suspected: SuspectedFacts): Boolean =
        filled(suspected.reasonForRefusalMTB)

    /** CLOSED is the only lifecycle-terminal status. A missing or pending status is not final. */
    private fun isClosedOrder(status: String?): Boolean =
        status?.trim()?.equals("CLOSED", ignoreCase = true) == true

    private fun isAbnormalCxr(result: String?): Boolean = normalize(result) in abnormalCxr

    private fun isNegativeTruenat(result: String?): Boolean = normalize(result) in negativeTruenat

    private fun isPositiveTruenat(result: String?): Boolean = normalize(result) in positiveTruenat

    private fun isLegacyPair(cxr: String?, naat: String?): Boolean =
        normalize(cxr) in legacyAbnormalCxr && normalize(naat) in legacyNegativeNaat

    private fun isRifIndeterminate(result: String?): Boolean = normalize(result) == "INDETERMINATE"

    private fun filled(value: String?): Boolean = !value.isNullOrBlank()

    private fun normalize(value: String?): String = value?.trim()?.uppercase().orEmpty()
}

private fun TBScreeningCache.toFacts() = ScreeningFacts(
    coughMoreThan2Weeks = coughMoreThan2Weeks,
    bloodInSputum = bloodInSputum,
    feverMoreThan2Weeks = feverMoreThan2Weeks,
    riseOfFever = riseOfFever,
    lossOfAppetite = lossOfAppetite,
    lossOfWeight = lossOfWeight,
    nightSweats = nightSweats,
    historyOfTb = historyOfTb,
    takingAntiTBDrugs = takingAntiTBDrugs,
    familySufferingFromTB = familySufferingFromTB,
    chestPain = chestPain,
    shortnessOfBreath = shortnessOfBreath,
    fatigue = fatigue,
    failureToGainWeightInChildren = failureToGainWeightInChildren,
    decreasedActivityOrPlayfulnessInChildren = decreasedActivityOrPlayfulnessInChildren,
    otherSymptoms = otherSymptoms,
    asymptomatic = asymptomatic,
    keyPopulationRiskFactors = keyPopulationRiskFactors
)

private fun TBDiagnosticsCache.toFacts() = DiagnosticFacts(
    chestXRayResult = chestXRayResult,
    isChestXRayDone = isChestXRayDone,
    reasonNotConductedChestXray = reasonNotConductedChestXray,
    reasonNotConductedChestXrayOther = reasonNotConductedChestXrayOther,
    reasonForDenialChestXray = reasonForDenialChestXray,
    reasonForDenialChestXrayOther = reasonForDenialChestXrayOther,
    naatResult = naatResult,
    isNaatConducted = isNaatConducted,
    reasonNotConductedNaat = reasonNotConductedNaat,
    trueNatOrderStatus = trueNatOrderStatus,
    trueNatRifResult = trueNatRifResult
)

private fun TBSuspectedCache.toFacts() = SuspectedFacts(
    chestXRayResult = chestXRayResult,
    isChestXRayDone = isChestXRayDone,
    reasonForRefusalXray = reasonForRefusalXray,
    naatResult = naatResult,
    isNaatConducted = isNaatConducted,
    reasonForRefusalMTB = reasonForRefusalMTB,
    hasSymptoms = hasSymptoms
)
