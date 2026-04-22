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
    private val noValue get() = resources.getStringArray(R.array.yes_no)[1]

    private val symptomaticLabel = FormElement(
        id = 100,
        inputType = InputType.HEADLINE,
        title = resources.getString(R.string.symptomatic_tb_screening),
        required = false
    )

    private val checkSymptomsLabel = FormElement(
        id = 101,
        inputType = InputType.HEADLINE,
        title = resources.getString(R.string.check_if_the_person_has_any_of_these_symptoms),
        required = false
    )

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

    private val asymptomaticLabel = FormElement(
        id = 102,
        inputType = InputType.HEADLINE,
        title = resources.getString(R.string.tb_asymptomatic_screening),
        required = false
    )

    private val checkSymptomsLabel1 = FormElement(
        id = 103,
        inputType = InputType.HEADLINE,
        title = resources.getString(R.string.check_if_the_person_has_any_of_these_symptoms),
        required = false
    )

    private val age = FormElement(
        id = 12,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.tb_age_more_than_60),
        entries = resources.getStringArray(R.array.yes_no),
        required = true,
        isEnabled = false
    )

    private val diabetic = FormElement(
        id = 13,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.tb_diabetic),
        entries = resources.getStringArray(R.array.yes_no),
        required = true
    )

    private val tobaccoUser = FormElement(
        id = 14,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.tb_tobacco_user),
        entries = resources.getStringArray(R.array.yes_no),
        required = true
    )

    private val contactWithTBPatient = FormElement(
        id = 15,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.tb_contact_with_patient_on_treatment),
        entries = resources.getStringArray(R.array.yes_no),
        required = true
    )

    private val referralRequired = FormElement(
        id = 16,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.tb_referral_required),
        entries = resources.getStringArray(R.array.yes_no),
        required = true,
        hasDependants = true
    )

    private val referralFor = FormElement(
        id = 17,
        inputType = InputType.CHECKBOXES,
        title = resources.getString(R.string.tb_referral_for),
        entries = resources.getStringArray(R.array.tb_referral_tests),
        required = false
    )

    suspend fun setUpPage(ben: BenRegCache?, saved: TBScreeningCache?) {
        ben?.let {
            dateOfVisit.min = it.regDate
            benAgeYears = when {
                it.dob > 0L -> BenBasicCache.getAgeFromDob(it.dob)
                else -> it.age
            }
        }

        if (saved == null) {
            dateOfVisit.value = getDateFromLong(System.currentTimeMillis())
        } else {
            dateOfVisit.value = getDateFromLong(saved.visitDate)
            isCoughing.value = boolToYesNo(saved.coughMoreThan2Weeks)
            bloodInSputum.value = boolToYesNo(saved.bloodInSputum)
            isFever.value = boolToYesNo(saved.feverMoreThan2Weeks)
            riseOfFever.value = boolToYesNo(saved.riseOfFever)
            lossOfAppetite.value = boolToYesNo(saved.lossOfAppetite)
            lossOfWeight.value = boolToYesNo(saved.lossOfWeight)
            nightSweats.value = boolToYesNo(saved.nightSweats)
            historyOfTB.value = boolToYesNo(saved.historyOfTb)
            currentlyTakingDrugs.value = boolToYesNo(saved.takingAntiTBDrugs)
            familyHistoryTB.value = boolToYesNo(saved.familySufferingFromTB)
        }

        setUpPage(buildFormList())
    }

    override suspend fun handleListOnValueChanged(formId: Int, index: Int): Int {
        return getIndexOfElement(dateOfVisit)
    }

    override fun mapValues(cacheModel: FormDataModel, pageNumber: Int) {
        (cacheModel as TBScreeningCache).let { form ->
            form.visitDate = getLongFromDate(dateOfVisit.value)
            form.coughMoreThan2Weeks = isYes(isCoughing)
            form.bloodInSputum = isYes(bloodInSputum)
            form.feverMoreThan2Weeks = isYes(isFever)
            form.riseOfFever = isYes(riseOfFever)
            form.lossOfAppetite = isYes(lossOfAppetite)
            form.lossOfWeight = isYes(lossOfWeight)
            form.nightSweats = isYes(nightSweats)
            form.historyOfTb = isYes(historyOfTB)
            form.takingAntiTBDrugs = isYes(currentlyTakingDrugs)
            form.familySufferingFromTB = isYes(familyHistoryTB)
            form.age = null
            form.diabetic = null
            form.tobaccoUser = null
            form.contactWithTBPatient = null
            form.bmi = null
            form.historyOfTBInLastFiveYrs = null
            form.referralRequired = null
            form.referralFor = null
            form.familyContactScreeningRequired = requiresFamilyContactScreening()
            form.sympotomatic = null
            form.asymptomatic = null
            form.recommandateTest = null
        }
    }

    fun requiresFamilyContactScreening(): Boolean =
        isYes(currentlyTakingDrugs) || isYes(familyHistoryTB)

    fun getFamilyContactAlert(): String? =
        if (requiresFamilyContactScreening()) resources.getString(R.string.tb_family_contact_screening_alert) else null

    fun getIndexOfDate(): Int = listFlow.value.indexOf(dateOfVisit)

    private fun buildFormList(): MutableList<FormElement> {
        return mutableListOf(
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
        )
    }

    private fun getSelectedEnglishValues(formElement: FormElement, arrayId: Int): List<String> {
        val selectedIndexes = formElement.value
            ?.split("|")
            ?.mapNotNull { it.toIntOrNull() }
            .orEmpty()

        val englishEntries = englishResources.getStringArray(arrayId)
        return selectedIndexes.mapNotNull { idx -> englishEntries.getOrNull(idx) }
    }

    private fun englishValuesToSelectionIndexes(values: List<String>?, arrayId: Int): String? {
        if (values.isNullOrEmpty()) return null
        val englishEntries = englishResources.getStringArray(arrayId)
        val selectedIndexes = values.mapNotNull { value -> englishEntries.indexOf(value).takeIf { it >= 0 } }
        return selectedIndexes.takeIf { it.isNotEmpty() }?.joinToString("|")
    }

    private fun boolToYesNo(value: Boolean?): String = if (value == true) yesValue else noValue

    private fun isYes(formElement: FormElement): Boolean = formElement.value == yesValue
}
