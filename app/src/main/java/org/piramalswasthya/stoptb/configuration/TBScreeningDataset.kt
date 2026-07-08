package org.piramalswasthya.stoptb.configuration

import android.content.Context
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.helpers.Languages
import org.piramalswasthya.stoptb.model.BenBasicCache
import org.piramalswasthya.stoptb.model.BenRegCache
import org.piramalswasthya.stoptb.model.FormElement
import org.piramalswasthya.stoptb.model.InputType
import org.piramalswasthya.stoptb.model.TBScreeningCache
import org.piramalswasthya.stoptb.utils.Log
import org.piramalswasthya.stoptb.model.Gender
import org.piramalswasthya.stoptb.utils.CommonConstants


class TBScreeningDataset(
    context: Context,
    currentLanguage: Languages
) : Dataset(context, currentLanguage) {

    private var benAgeYears: Int = 0

    private val yesValue get() = resources.getStringArray(R.array.yes_no)[0]
    private val noValue  get() = resources.getStringArray(R.array.yes_no)[1]

    // ── Form fields ──────────────────────────────────────────────────────────

    private val dateOfVisit = FormElement(
        id = 1,
        inputType = InputType.DATE_PICKER,
        title = resources.getString(R.string.tracking_date),
        required = true,
        max = System.currentTimeMillis(),
        hasDependants = true,
        isEnabled = false
    )

    private val isCoughing = FormElement(
        id = 2,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.cbac_coughing),
        entries = resources.getStringArray(R.array.yes_no),
        required = true,
        hasDependants = true
    )

    private val bloodInSputum = FormElement(
        id = 3,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.cbac_blsputum),
        entries = resources.getStringArray(R.array.yes_no),
        required = true,
        hasDependants = true
    )

    private val isFever = FormElement(
        id = 4,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.cbac_feverwks),
        entries = resources.getStringArray(R.array.yes_no),
        required = true,
        hasDependants = true
    )

    private val riseOfFever = FormElement(
        id = 5,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.tb_rise_of_fever),
        entries = resources.getStringArray(R.array.yes_no),
        required = true,
        hasDependants = true
    )

    private val lossOfAppetite = FormElement(
        id = 6,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.tb_loss_of_appetite),
        entries = resources.getStringArray(R.array.yes_no),
        required = true,
        hasDependants = true
    )

    private val lossOfWeight = FormElement(
        id = 7,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.cbac_lsweight),
        entries = resources.getStringArray(R.array.yes_no),
        required = true,
        hasDependants = true
    )

    private val nightSweats = FormElement(
        id = 8,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.cbac_ntswets),
        entries = resources.getStringArray(R.array.yes_no),
        required = true,
        hasDependants = true
    )

    private val historyOfTB = FormElement(
        id = 9,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.cbac_histb),
        entries = resources.getStringArray(R.array.yes_no),
        required = true,
        hasDependants = true
    )

    private val currentlyTakingDrugs = FormElement(
        id = 10,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.cbac_taking_tb_drug),
        entries = resources.getStringArray(R.array.yes_no),
        required = true,
        doubleStar = true,
        hasDependants = true
    )

    private val familyHistoryTB = FormElement(
        id = 11,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.cbac_fh_tb),
        entries = resources.getStringArray(R.array.yes_no),
        required = true,
        doubleStar = true,
        hasDependants = true
    )

    /** Auto-computed from the 10 symptom answers — not editable by user */
    private val isAsymptomatic = FormElement(
        id = 16,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.tb_is_beneficiary_asymptomatic),
        entries = resources.getStringArray(R.array.yes_no),
        required = false,
        isEnabled = false,
        hasDependants = true
    )

    private data class CodedOption(val id: Int, val code: String, val label: String)

    private val riskFactorCodes = CommonConstants.RISK_FACTOR_CODES

    private fun masterRiskFactorOptions(): List<CodedOption> {
        val labels = resources.getStringArray(R.array.key_population_risk_factor_options)
        return labels.mapIndexed { index, label ->
            CodedOption(index + 1, riskFactorCodes.getOrElse(index) { label.uppercase().replace(" ", "_") }, label)
        }
    }

    private var isMaleBen: Boolean = false
    private var isPregnantBen: Boolean = false
    private var riskFactorOptions: List<CodedOption> = emptyList()

    private val hivStatusOptions: List<CodedOption>
        get() = listOf(
            CodedOption(1, "POSITIVE", resources.getString(R.string.positive)),
            CodedOption(2, "REACTIVE", resources.getString(R.string.reactive)),
            CodedOption(3, "NEGATIVE", resources.getString(R.string.negative)),
            CodedOption(4, "UNKNOWN", resources.getString(R.string.unknown))
        )

    private val riskFactorsHeading = FormElement(
        id = 17,
        inputType = InputType.HEADLINE,
        title = resources.getString(R.string.risk_factors),
        required = false
    )

    private val keyPopulationRiskFactors = FormElement(
        id = 18,
        inputType = InputType.CHECKBOXES,
        title = resources.getString(R.string.select_key_population_risk_factors),
        entries = emptyArray(),
        required = true,
        showAsMultiSelectDialog = true
    )

    private val hivStatus = FormElement(
        id = 19,
        inputType = InputType.DROPDOWN,
        title = resources.getString(R.string.hiv_status),
        entries = emptyArray(),
        required = true
    )

    // ── Asymptomatic logic ───────────────────────────────────────────────────

    /** IDs of the 10 symptom questions that drive asymptomatic auto-select */
    private val symptomQuestionIds = setOf(
        isCoughing.id, bloodInSputum.id, isFever.id, riseOfFever.id,
        lossOfAppetite.id, lossOfWeight.id, nightSweats.id,
        historyOfTB.id, currentlyTakingDrugs.id, familyHistoryTB.id
    )

    /**
     * PRD rule:
     *  - Any 1  = "Yes"  →  asymptomatic = "No"
     *  - All 10 = "No"   →  asymptomatic = "Yes"
     *  - Not all answered yet → null (blank)
     */
    private fun computeAsymptomatic(): String? {
        val fields = listOf(
            isCoughing, bloodInSputum, isFever, riseOfFever,
            lossOfAppetite, lossOfWeight, nightSweats,
            historyOfTB, currentlyTakingDrugs, familyHistoryTB
        )
        return when {
            fields.any  { isYes(it) }                  -> noValue   // any Yes  → Not asymptomatic
            fields.all { it.value == noValue } -> yesValue  // all No   → Asymptomatic
            else                                        -> null      // still answering → blank
        }
    }

    // ── Page setup ───────────────────────────────────────────────────────────

    suspend fun setUpPage(ben: BenRegCache?, saved: TBScreeningCache?) {
        ben?.let {
            dateOfVisit.min = it.regDate
            benAgeYears = if (it.dob > 0L) BenBasicCache.getAgeFromDob(it.dob) else it.age
            isMaleBen = it.gender == Gender.MALE
            val reproductiveStatus = it.genDetails?.reproductiveStatus
            isPregnantBen = it.genDetails?.reproductiveStatusId == 1 ||
                    reproductiveStatus.equals("Yes", ignoreCase = true)
        }

        riskFactorOptions = masterRiskFactorOptions().let { all ->
            if (isMaleBen) all.filter { it.code != "PREGNANCY" && it.code != "LACTATING_MOTHER" } else all
        }
        keyPopulationRiskFactors.entries = riskFactorOptions.map { it.label }.toTypedArray()
        val notApplicableIndex = riskFactorOptions.indexOfFirst { it.code == "NOT_APPLICABLE" }
        keyPopulationRiskFactors.exclusiveOptionIndices =
            if (notApplicableIndex >= 0) setOf(notApplicableIndex) else null
        hivStatus.entries = hivStatusOptions.map { it.label }.toTypedArray()

        if (saved == null) {
            dateOfVisit.value = getDateFromLong(System.currentTimeMillis())
            val pregnancyIndex = riskFactorOptions.indexOfFirst { it.code == "PREGNANCY" }
            val notApplicableIndex = riskFactorOptions.indexOfFirst { it.code == "NOT_APPLICABLE" }

            keyPopulationRiskFactors.value = when {
                isPregnantBen && pregnancyIndex >= 0 -> pregnancyIndex.toString()
                notApplicableIndex >= 0 -> notApplicableIndex.toString()
                else -> null
            }
            hivStatus.value = hivStatusOptions.first { it.code == "UNKNOWN" }.label
        } else {
            dateOfVisit.value        = getDateFromLong(saved.visitDate)
            isCoughing.value         = boolToYesNo(saved.coughMoreThan2Weeks)
            bloodInSputum.value      = boolToYesNo(saved.bloodInSputum)
            isFever.value            = boolToYesNo(saved.feverMoreThan2Weeks)
            riseOfFever.value        = boolToYesNo(saved.riseOfFever)
            lossOfAppetite.value     = boolToYesNo(saved.lossOfAppetite)
            lossOfWeight.value       = boolToYesNo(saved.lossOfWeight)
            nightSweats.value        = boolToYesNo(saved.nightSweats)
            historyOfTB.value        = boolToYesNo(saved.historyOfTb)
            currentlyTakingDrugs.value = boolToYesNo(saved.takingAntiTBDrugs)
            familyHistoryTB.value    = boolToYesNo(saved.familySufferingFromTB)
            isAsymptomatic.value     = saved.asymptomatic ?: computeAsymptomatic()

            val savedIds = saved.keyPopulationRiskFactorIds.orEmpty()
            val savedCodes = saved.keyPopulationRiskFactors.orEmpty()
            val selectedIndexes = riskFactorOptions.mapIndexedNotNull { index, option ->
                val matches = savedIds.contains(option.id) ||
                        savedCodes.any { it.equals(option.code, true) || it.equals(option.label, true) }
                if (matches) index else null
            }
            keyPopulationRiskFactors.value =
                if (selectedIndexes.isEmpty()) null else selectedIndexes.sorted().joinToString("|")

            hivStatus.value = hivStatusOptions.firstOrNull {
                it.id == saved.hivStatusId ||
                        saved.hivStatus.equals(it.code, true) ||
                        saved.hivStatus.equals(it.label, true)
            }?.label ?: hivStatusOptions.first { it.code == "UNKNOWN" }.label
        }

        setUpPage(buildFormList())
    }
    // ── Value change handling ────────────────────────────────────────────────

    override suspend fun handleListOnValueChanged(formId: Int, index: Int): Int {
        when (formId) {
            isCoughing.id           -> isCoughing.value           = yesNoFromIndex(index)
            bloodInSputum.id        -> bloodInSputum.value        = yesNoFromIndex(index)
            isFever.id              -> isFever.value              = yesNoFromIndex(index)
            riseOfFever.id          -> riseOfFever.value          = yesNoFromIndex(index)
            lossOfAppetite.id       -> lossOfAppetite.value       = yesNoFromIndex(index)
            lossOfWeight.id         -> lossOfWeight.value         = yesNoFromIndex(index)
            nightSweats.id          -> nightSweats.value          = yesNoFromIndex(index)
            historyOfTB.id          -> historyOfTB.value          = yesNoFromIndex(index)
            currentlyTakingDrugs.id -> currentlyTakingDrugs.value = yesNoFromIndex(index)
            familyHistoryTB.id      -> familyHistoryTB.value      = yesNoFromIndex(index)
            keyPopulationRiskFactors.id -> enforceNotApplicableExclusivity()
        }
        // If a symptom question was answered, recompute asymptomatic and signal a
        // list refresh so the fragment can force-rebind the auto-computed field.
        return if (formId in symptomQuestionIds) {
            isAsymptomatic.value = computeAsymptomatic()
            Log.d("ASYM_TEST", "Computed = ${isAsymptomatic.value}")
            listFlow.value.indexOf(isAsymptomatic).takeIf { it >= 0 } ?: -1
        } else -1
    }

    private fun enforceNotApplicableExclusivity() {
        val naIndex = riskFactorOptions.indexOfFirst { it.code == "NOT_APPLICABLE" }
        if (naIndex < 0) return
        val selected = keyPopulationRiskFactors.value
            ?.split("|")?.mapNotNull { it.toIntOrNull() }?.toMutableSet() ?: return
        if (selected.size > 1 && selected.contains(naIndex)) {
            selected.remove(naIndex)
            keyPopulationRiskFactors.value =
                if (selected.isEmpty()) null else selected.sorted().joinToString("|")
        }
    }

    // ── Saving ───────────────────────────────────────────────────────────────

    override fun mapValues(cacheModel: FormDataModel, pageNumber: Int) {
        (cacheModel as TBScreeningCache).let { form ->
            form.visitDate             = getLongFromDate(dateOfVisit.value)
            form.coughMoreThan2Weeks   = isYes(isCoughing)
            form.bloodInSputum         = isYes(bloodInSputum)
            form.feverMoreThan2Weeks   = isYes(isFever)
            form.riseOfFever           = isYes(riseOfFever)
            form.lossOfAppetite        = isYes(lossOfAppetite)
            form.lossOfWeight          = isYes(lossOfWeight)
            form.nightSweats           = isYes(nightSweats)
            form.historyOfTb           = isYes(historyOfTB)
            form.takingAntiTBDrugs     = isYes(currentlyTakingDrugs)
            form.familySufferingFromTB = isYes(familyHistoryTB)
            form.asymptomatic          = isAsymptomatic.value?.takeIf { it.isNotBlank() }
            form.familyContactScreeningRequired = requiresFamilyContactScreening()
            val selectedRiskFactors = keyPopulationRiskFactors.value
                ?.split("|")?.mapNotNull { it.toIntOrNull() }
                ?.mapNotNull { riskFactorOptions.getOrNull(it) }
                .orEmpty()
            form.keyPopulationRiskFactorIds = selectedRiskFactors.map { it.id }.takeIf { it.isNotEmpty() }
            form.keyPopulationRiskFactors = selectedRiskFactors.map { it.code }.takeIf { it.isNotEmpty() }

            val selectedHiv = hivStatusOptions.firstOrNull { it.label == hivStatus.value }
            form.hivStatusId = selectedHiv?.id
            form.hivStatus = selectedHiv?.code
            // Fields no longer collected in TB Screening — clear them
            form.age                             = null
            form.diabetic                        = null
            form.tobaccoUser                     = null
            form.contactWithTBPatient            = null
            form.bmi                             = null
            form.historyOfTBInLastFiveYrs        = null
            form.sympotomatic                    = null
            form.recommandateTest                = null
            form.referredForDigitalChestXray     = null
            form.referredForSputumCollection     = null
            form.sputumSampleSubmittedAt         = null
            form.recommendedForTruenatTest       = null
            form.recommendedForLiquidCultureTest = null
            form.reasonForDenialForGettingTested = null
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    fun requiresFamilyContactScreening(): Boolean = isYes(familyHistoryTB)

    private fun hasSingleStarYes(): Boolean = listOf(
        isCoughing,
        bloodInSputum,
        isFever,
        riseOfFever,
        lossOfAppetite,
        lossOfWeight,
        nightSweats,
        historyOfTB
    ).any(::isYes)

    private fun hasDoubleStarYes(): Boolean = listOf(
        currentlyTakingDrugs,
        familyHistoryTB
    ).any(::isYes)

    fun getPresumptiveTbAlert(): String? =
        when {
            hasDoubleStarYes() ->
                resources.getString(R.string.tb_presumptive_alert_title) +
                    "\n" + resources.getString(R.string.tb_presumptive_alert_refer) +
                    "\n" + resources.getString(R.string.tb_presumptive_alert_family)
            hasSingleStarYes() ->
                resources.getString(R.string.tb_presumptive_alert_title) +
                    "\n" + resources.getString(R.string.tb_presumptive_alert_refer)
            else -> null
        }

    fun getFamilyContactAlert(): String? =
        if (requiresFamilyContactScreening())
            resources.getString(R.string.tb_family_contact_screening_alert)
        else null

    fun getIndexOfDate(): Int        = listFlow.value.indexOf(dateOfVisit)
    fun getIndexOfAsymptomatic(): Int = listFlow.value.indexOf(isAsymptomatic)

    private fun buildFormList(): List<FormElement> = listOf(
        dateOfVisit,
        isCoughing,
        bloodInSputum,
        isFever,
        riseOfFever,
        lossOfAppetite,
        lossOfWeight,
        nightSweats,
        historyOfTB,
        currentlyTakingDrugs,
        familyHistoryTB,
        isAsymptomatic,
        riskFactorsHeading,
        keyPopulationRiskFactors,
        hivStatus
    )

    private fun yesNoFromIndex(index: Int): String? = if (index == 0) yesValue else noValue

    private fun boolToYesNo(value: Boolean?): String = when (value) {
        true -> yesValue
        false -> noValue
        null  -> ""
    }

    private fun isYes(formElement: FormElement): Boolean = formElement.value == yesValue
}
