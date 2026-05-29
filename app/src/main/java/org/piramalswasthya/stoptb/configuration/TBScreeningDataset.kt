package org.piramalswasthya.stoptb.configuration

import android.content.Context
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.helpers.Languages
import org.piramalswasthya.stoptb.model.BenBasicCache
import org.piramalswasthya.stoptb.model.BenRegCache
import org.piramalswasthya.stoptb.model.FormElement
import org.piramalswasthya.stoptb.model.InputType
import org.piramalswasthya.stoptb.model.TBScreeningCache

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
        hasDependants = true
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
        isEnabled = false
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
            fields.all  { !it.value.isNullOrBlank() }  -> yesValue  // all No   → Asymptomatic
            else                                        -> null      // still answering → blank
        }
    }

    // ── Page setup ───────────────────────────────────────────────────────────

    suspend fun setUpPage(ben: BenRegCache?, saved: TBScreeningCache?) {
        ben?.let {
            dateOfVisit.min = it.regDate
            benAgeYears = if (it.dob > 0L) BenBasicCache.getAgeFromDob(it.dob) else it.age
        }

        if (saved == null) {
            dateOfVisit.value = getDateFromLong(System.currentTimeMillis())
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
            // Restore saved asymptomatic; fall back to computed if null
            isAsymptomatic.value     = saved.asymptomatic ?: computeAsymptomatic()
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
        }
        // If a symptom question was answered, recompute asymptomatic and signal a
        // list refresh so the fragment can force-rebind the auto-computed field.
        return if (formId in symptomQuestionIds) {
            isAsymptomatic.value = computeAsymptomatic()
            listFlow.value.indexOf(isAsymptomatic).takeIf { it >= 0 } ?: -1
        } else -1
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
        isAsymptomatic
    )

    private fun yesNoFromIndex(index: Int): String? = if (index == 0) yesValue else noValue

    private fun boolToYesNo(value: Boolean?): String = when (value) {
        true -> yesValue
        false -> noValue
        null  -> ""
    }

    private fun isYes(formElement: FormElement): Boolean = formElement.value == yesValue
}
