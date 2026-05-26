package org.piramalswasthya.stoptb.configuration

import android.content.Context
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.helpers.Languages
import org.piramalswasthya.stoptb.model.BenBasicCache
import org.piramalswasthya.stoptb.model.BenRegCache
import org.piramalswasthya.stoptb.model.FormElement
import org.piramalswasthya.stoptb.model.Gender
import org.piramalswasthya.stoptb.model.InputType
import org.piramalswasthya.stoptb.model.TBScreeningCache

class TBScreeningDataset(
    context: Context,
    currentLanguage: Languages
) : Dataset(context, currentLanguage) {

    private var benAgeYears: Int = 0
    private var ben: BenRegCache? = null
    private var hasPregnancyRiskFactor: Boolean = false

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

    private val isBeneficiaryAsymptomatic = FormElement(
        id = 16,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.tb_is_beneficiary_asymptomatic),
        entries = resources.getStringArray(R.array.yes_no),
        required = true,
        isEnabled = false,
        hasDependants = true
    )

    private val symptomaticQuestions = listOf(
        isCoughing,
        bloodInSputum,
        isFever,
        riseOfFever,
        lossOfAppetite,
        lossOfWeight,
        nightSweats,
        historyOfTB,
        currentlyTakingDrugs,
        familyHistoryTB
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

    private val referredForDigitalChestXray = FormElement(
        id = 18,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.referred_for_digital_chest_xray),
        entries = resources.getStringArray(R.array.yes_no),
        required = true,
        hasDependants = true
    )

    private val referredForSputumCollection = FormElement(
        id = 19,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.referred_for_sputum_collection),
        entries = resources.getStringArray(R.array.yes_no),
        required = true,
        hasDependants = true
    )

    private val sputumSampleSubmittedAt = FormElement(
        id = 20,
        inputType = InputType.DROPDOWN,
        title = resources.getString(R.string.sputum_sample_submitted_at),
        arrayId = R.array.tb_sputum_sample_submitted_at,
        entries = resources.getStringArray(R.array.tb_sputum_sample_submitted_at),
        required = false
    )

    private val recommendedForTruenatTest = FormElement(
        id = 21,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.recommended_for_truenat_test),
        entries = resources.getStringArray(R.array.yes_no),
        required = true,
        hasDependants = true
    )

    private val recommendedForLiquidCultureTest = FormElement(
        id = 22,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.recommended_for_liquid_culture_test),
        entries = resources.getStringArray(R.array.yes_no),
        required = true,
        hasDependants = true
    )

    private val reasonForDenialForGettingTested = FormElement(
        id = 23,
        inputType = InputType.CHECKBOXES,
        title = resources.getString(R.string.reason_for_denial_for_getting_tested),
        arrayId = R.array.tb_reason_for_denial_testing,
        entries = resources.getStringArray(R.array.tb_reason_for_denial_testing),
        required = false
    )

    suspend fun setUpPage(
        ben: BenRegCache?,
        saved: TBScreeningCache?,
        hasPregnancyRiskFactor: Boolean = false
    ) {
        this.ben = ben
        this.hasPregnancyRiskFactor = hasPregnancyRiskFactor
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
            isBeneficiaryAsymptomatic.value = saved.asymptomatic?.let { savedValue ->
                when (savedValue.trim().lowercase()) {
                    "yes" -> yesValue
                    "no" -> noValue
                    else -> null
                }
            }
            referredForDigitalChestXray.value = boolToYesNo(saved.referredForDigitalChestXray)
            referredForSputumCollection.value = boolToYesNo(saved.referredForSputumCollection)
            sputumSampleSubmittedAt.value = getLocalValueInArray(
                R.array.tb_sputum_sample_submitted_at,
                saved.sputumSampleSubmittedAt
            )
            recommendedForTruenatTest.value = boolToYesNo(saved.recommendedForTruenatTest)
            recommendedForLiquidCultureTest.value = boolToYesNo(saved.recommendedForLiquidCultureTest)
            reasonForDenialForGettingTested.value =
                englishValuesToSelectionIndexes(saved.reasonForDenialForGettingTested, R.array.tb_reason_for_denial_testing)
        }

        applyAsymptomaticDefault()
        applyReferralDefaults()
        setUpPage(buildFormList())
        refreshAsymptomaticFormElementInList()
    }

    override suspend fun handleListOnValueChanged(formId: Int, index: Int): Int {
        when (formId) {
            isCoughing.id -> isCoughing.value = yesNoFromIndex(index)
            bloodInSputum.id -> bloodInSputum.value = yesNoFromIndex(index)
            isFever.id -> isFever.value = yesNoFromIndex(index)
            riseOfFever.id -> riseOfFever.value = yesNoFromIndex(index)
            lossOfAppetite.id -> lossOfAppetite.value = yesNoFromIndex(index)
            lossOfWeight.id -> lossOfWeight.value = yesNoFromIndex(index)
            nightSweats.id -> nightSweats.value = yesNoFromIndex(index)
            historyOfTB.id -> historyOfTB.value = yesNoFromIndex(index)
            currentlyTakingDrugs.id -> currentlyTakingDrugs.value = yesNoFromIndex(index)
            familyHistoryTB.id -> familyHistoryTB.value = yesNoFromIndex(index)
            referredForDigitalChestXray.id -> referredForDigitalChestXray.value = yesNoFromIndex(index)
            referredForSputumCollection.id -> referredForSputumCollection.value = yesNoFromIndex(index)
            recommendedForTruenatTest.id -> recommendedForTruenatTest.value = yesNoFromIndex(index)
            recommendedForLiquidCultureTest.id -> recommendedForLiquidCultureTest.value = yesNoFromIndex(index)
        }
        val asymptomaticChanged = applyAsymptomaticDefault()
        if (asymptomaticChanged) {
            refreshAsymptomaticFormElementInList()
        }
        applyReferralDefaults()
        val referralUpdateIndex = syncReferralFields()
        if (formId in symptomaticQuestions.map { it.id } || asymptomaticChanged) {
            return indexOfFormElementById(isBeneficiaryAsymptomatic.id)
                .takeIf { it >= 0 } ?: referralUpdateIndex
        }
        return referralUpdateIndex
    }

    fun getIndexOfBeneficiaryAsymptomatic(): Int =
        indexOfFormElementById(isBeneficiaryAsymptomatic.id)

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
            form.referredForDigitalChestXray =
                if (shouldShowDigitalChestXrayReferral()) isYes(referredForDigitalChestXray) else null
            form.referredForSputumCollection =
                if (shouldShowSputumCollectionReferral()) isYes(referredForSputumCollection) else null
            form.sputumSampleSubmittedAt =
                if (shouldShowSputumCollectionReferral()) {
                    getEnglishValueInArray(R.array.tb_sputum_sample_submitted_at, sputumSampleSubmittedAt.value)
                } else null
            form.recommendedForTruenatTest =
                if (shouldShowTruenatRecommendation()) isYes(recommendedForTruenatTest) else null
            form.recommendedForLiquidCultureTest =
                if (shouldShowLiquidCultureRecommendation()) isYes(recommendedForLiquidCultureTest) else null
            form.reasonForDenialForGettingTested =
                if (shouldShowReasonForDenial()) {
                    getSelectedEnglishValues(reasonForDenialForGettingTested, R.array.tb_reason_for_denial_testing)
                } else null
            form.familyContactScreeningRequired = requiresFamilyContactScreening()
            form.asymptomatic = isBeneficiaryAsymptomatic.value?.trim()?.takeIf { it.isNotBlank() }
            form.sympotomatic = when (form.asymptomatic) {
                yesValue -> noValue
                noValue -> yesValue
                else -> null
            }
            form.recommandateTest = null
        }
    }

    fun requiresFamilyContactScreening(): Boolean =
        isYes(familyHistoryTB)

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
            isBeneficiaryAsymptomatic,
        ).apply { addAll(buildReferralFields()) }
    }

    private fun buildReferralFields(): List<FormElement> = buildList {
        if (shouldShowDigitalChestXrayReferral()) {
            add(referredForDigitalChestXray)
        }
        if (shouldShowSputumCollectionReferral()) {
            add(referredForSputumCollection)
            add(sputumSampleSubmittedAt)
        }
        if (shouldShowTruenatRecommendation()) {
            add(recommendedForTruenatTest)
        }
        if (shouldShowLiquidCultureRecommendation()) {
            add(recommendedForLiquidCultureTest)
        }
        if (shouldShowReasonForDenial()) {
            add(reasonForDenialForGettingTested)
        }
    }

    private fun syncReferralFields(): Int {
        val allReferralFields = listOf(
            referredForDigitalChestXray,
            referredForSputumCollection,
            sputumSampleSubmittedAt,
            recommendedForTruenatTest,
            recommendedForLiquidCultureTest,
            reasonForDenialForGettingTested
        )
        val visibleFields = buildReferralFields()
        val visibleValues = visibleFields.associateWith { it.value }
        val asymptomaticInList = findFormElementById(isBeneficiaryAsymptomatic.id)
            ?: isBeneficiaryAsymptomatic
        val insertPosition = indexOfFormElementById(isBeneficiaryAsymptomatic.id)
            .takeIf { it >= 0 }
            ?.plus(1)
            ?: -2

        val updateIndex = triggerDependants(
            source = asymptomaticInList,
            removeItems = allReferralFields,
            addItems = visibleFields,
            position = insertPosition
        )
        visibleValues.forEach { (field, value) -> field.value = value }
        return updateIndex
    }

    private fun applyAsymptomaticDefault(): Boolean {
        val previousValue = isBeneficiaryAsymptomatic.value
        val newValue = when {
            symptomaticQuestions.any { isYes(it) } -> noValue
            symptomaticQuestions.all { it.value == noValue } -> yesValue
            else -> null
        }
        isBeneficiaryAsymptomatic.value = newValue
        return previousValue != newValue
    }

    private fun refreshAsymptomaticFormElementInList() {
        replaceFormElementById(isBeneficiaryAsymptomatic.id, isBeneficiaryAsymptomatic.copy())
    }

    private fun applyReferralDefaults() {
        if (shouldShowDigitalChestXrayReferral() && referredForDigitalChestXray.value.isNullOrBlank()) {
            referredForDigitalChestXray.value = yesValue
        }
        if (shouldShowSputumCollectionReferral() && referredForSputumCollection.value.isNullOrBlank()) {
            referredForSputumCollection.value = yesValue
        }
        if (shouldShowTruenatRecommendation() && recommendedForTruenatTest.value.isNullOrBlank()) {
            recommendedForTruenatTest.value = yesValue
        }
        if (shouldShowLiquidCultureRecommendation() && recommendedForLiquidCultureTest.value.isNullOrBlank()) {
            recommendedForLiquidCultureTest.value = yesValue
        }
        if (!shouldShowDigitalChestXrayReferral()) referredForDigitalChestXray.value = null
        if (!shouldShowSputumCollectionReferral()) {
            referredForSputumCollection.value = null
            sputumSampleSubmittedAt.value = null
        }
        if (!shouldShowTruenatRecommendation()) recommendedForTruenatTest.value = null
        if (!shouldShowLiquidCultureRecommendation()) recommendedForLiquidCultureTest.value = null
        if (!shouldShowReasonForDenial()) reasonForDenialForGettingTested.value = null
    }

    private fun shouldShowDigitalChestXrayReferral(): Boolean =
        !isPregnant()

    private fun shouldShowSputumCollectionReferral(): Boolean =
        isPregnant() || isYes(historyOfTB) || isYes(currentlyTakingDrugs) || hasPregnancyRiskFactor

    private fun shouldShowTruenatRecommendation(): Boolean =
        isYes(referredForSputumCollection)

    private fun shouldShowLiquidCultureRecommendation(): Boolean =
        isYes(referredForSputumCollection) && isYes(historyOfTB) && isYes(currentlyTakingDrugs)

    private fun shouldShowReasonForDenial(): Boolean =
        listOf(
            referredForDigitalChestXray.takeIf { shouldShowDigitalChestXrayReferral() },
            referredForSputumCollection.takeIf { shouldShowSputumCollectionReferral() },
            recommendedForTruenatTest.takeIf { shouldShowTruenatRecommendation() },
            recommendedForLiquidCultureTest.takeIf { shouldShowLiquidCultureRecommendation() }
        ).any { it?.value == noValue }

    private fun isPregnant(): Boolean {
        if (ben?.gender != Gender.FEMALE) return false
        val reproductiveStatus = ben?.genDetails?.reproductiveStatus
        return ben?.genDetails?.reproductiveStatusId == 1 ||
            reproductiveStatus.equals("Yes", ignoreCase = true)
    }

    private fun yesNoFromIndex(index: Int): String? =
        if (index == 0) yesValue else noValue

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

    private fun boolToYesNo(value: Boolean?): String = when (value) {
        true -> yesValue
        false -> noValue
        null -> ""
    }

    private fun isYes(formElement: FormElement): Boolean = formElement.value == yesValue
}
