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

    private val symptomaticQuestionIds = symptomaticQuestions.map { it.id }.toSet()

    /** Latest radio index (0 = Yes, 1 = No) per symptomatic question — avoids string/locale mismatches. */
    private val symptomaticAnswerIndexById = mutableMapOf<Int, Int>()

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
        title = resources.getString(R.string.tb_is_beneficiary_asymptomatic),
        entries = resources.getStringArray(R.array.yes_no),
        required = false,
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

        symptomaticAnswerIndexById.clear()
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
        syncSymptomaticIndicesFromList()
        refreshAsymptomaticFormElementInList()
        emitListUpdate()
    }

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
        if (formId in symptomaticQuestionIds) {
            symptomaticAnswerIndexById[formId] = index
        }
        // If a symptom question was answered, recompute asymptomatic and signal a
        // list refresh so the fragment can force-rebind the auto-computed field.
        return if (formId in symptomQuestionIds) {
            isAsymptomatic.value = computeAsymptomatic()
            listFlow.value.indexOf(isAsymptomatic).takeIf { it >= 0 } ?: -1
        } else -1
        setYesNoAnswer(formId, index)
        val asymptomaticChanged = applyAsymptomaticDefault()
        applyReferralDefaults()
        val referralUpdateIndex = syncReferralFields()
        if (formId in symptomaticQuestionIds || asymptomaticChanged) {
            return indexOfFormElementById(isBeneficiaryAsymptomatic.id)
                .takeIf { it >= 0 } ?: referralUpdateIndex
        }
        return referralUpdateIndex
    }

    fun getIndexOfBeneficiaryAsymptomatic(): Int =
        indexOfFormElementById(isBeneficiaryAsymptomatic.id)

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
            form.asymptomatic = asymptomaticElementInList().value?.trim()?.takeIf { it.isNotBlank() }
            form.sympotomatic = when (form.asymptomatic) {
                yesValue -> noValue
                noValue -> yesValue
                else -> null
            }
            form.recommandateTest = null
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
        syncSymptomaticIndicesFromList()
        val allSymptomsExplicitlyNo = symptomaticQuestions.all { question ->
            symptomaticAnswerIndexById[question.id] == 1
        }
        val newValue = if (allSymptomsExplicitlyNo) yesValue else noValue
        val previousValue = isBeneficiaryAsymptomatic.value
        isBeneficiaryAsymptomatic.value = newValue
        // Replace with a new instance so DiffUtil detects the change (in-place mutation
        // on the same FormElement leaves old/new list snapshots identical for the adapter).
        replaceFormElementById(
            isBeneficiaryAsymptomatic.id,
            isBeneficiaryAsymptomatic.copy(value = newValue, isEnabled = false)
        )
        requestListRefresh = true
        return previousValue != newValue
    }

    private fun syncSymptomaticIndicesFromList() {
        symptomaticQuestions.forEach { question ->
            yesNoSelectionIndex(symptomElementInList(question))?.let { selectedIndex ->
                symptomaticAnswerIndexById[question.id] = selectedIndex
            }
        }
    }

    /** Uses the list-backed element so adapter selections are included in asymptomatic logic. */
    private fun symptomElementInList(question: FormElement): FormElement =
        findFormElementById(question.id) ?: question

    private fun asymptomaticElementInList(): FormElement =
        findFormElementById(isBeneficiaryAsymptomatic.id) ?: isBeneficiaryAsymptomatic

    private fun setYesNoAnswer(formId: Int, index: Int) {
        val answer = yesNoFromIndex(index) ?: return
        findFormElementById(formId)?.value = answer
        when (formId) {
            isCoughing.id -> isCoughing.value = answer
            bloodInSputum.id -> bloodInSputum.value = answer
            isFever.id -> isFever.value = answer
            riseOfFever.id -> riseOfFever.value = answer
            lossOfAppetite.id -> lossOfAppetite.value = answer
            lossOfWeight.id -> lossOfWeight.value = answer
            nightSweats.id -> nightSweats.value = answer
            historyOfTB.id -> historyOfTB.value = answer
            currentlyTakingDrugs.id -> currentlyTakingDrugs.value = answer
            familyHistoryTB.id -> familyHistoryTB.value = answer
            referredForDigitalChestXray.id -> referredForDigitalChestXray.value = answer
            referredForSputumCollection.id -> referredForSputumCollection.value = answer
            recommendedForTruenatTest.id -> recommendedForTruenatTest.value = answer
            recommendedForLiquidCultureTest.id -> recommendedForLiquidCultureTest.value = answer
        }
    }

    /** Returns true only when the question has an explicit No answer. */
    private fun isSymptomAnsweredNo(formElement: FormElement): Boolean =
        yesNoSelectionIndex(formElement) == 1

    /** Returns true only when the question has an explicit Yes answer. */
    private fun isSymptomAnsweredYes(formElement: FormElement): Boolean =
        yesNoSelectionIndex(formElement) == 0

    private fun yesNoSelectionIndex(formElement: FormElement): Int? {
        val value = formElement.value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        formElement.entries?.let { entries ->
            entries.indexOfFirst { it == value }.takeIf { it >= 0 }?.let { return it }
            entries.indexOfFirst { it.equals(value, ignoreCase = true) }.takeIf { it >= 0 }?.let { return it }
        }
        val englishYesNo = englishResources.getStringArray(R.array.yes_no)
        return when {
            value.equals(yesValue, ignoreCase = true) -> 0
            value.equals(noValue, ignoreCase = true) -> 1
            value.equals(englishYesNo[0], ignoreCase = true) -> 0
            value.equals(englishYesNo[1], ignoreCase = true) -> 1
            else -> null
        }
    }

    private fun isSymptomYes(formElement: FormElement): Boolean =
        yesNoSelectionIndex(formElement) == 0

    private fun refreshAsymptomaticFormElementInList() {
        replaceFormElementById(
            isBeneficiaryAsymptomatic.id,
            isBeneficiaryAsymptomatic.copy(
                value = isBeneficiaryAsymptomatic.value,
                isEnabled = false
            )
        )
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

    private fun yesNoFromIndex(index: Int): String? = if (index == 0) yesValue else noValue
    private fun isPregnant(): Boolean {
        if (ben?.gender != Gender.FEMALE) return false
        val reproductiveStatus = ben?.genDetails?.reproductiveStatus
        return ben?.genDetails?.reproductiveStatusId == 1 ||
            reproductiveStatus.equals("Yes", ignoreCase = true)
    }

    private fun yesNoFromIndex(index: Int): String? = when (index) {
        0 -> yesValue
        1 -> noValue
        else -> null
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

    private fun boolToYesNo(value: Boolean?): String = when (value) {
        true -> yesValue
        false -> noValue
        null  -> ""
    }

    private fun isYes(formElement: FormElement): Boolean = isSymptomAnsweredYes(formElement)
}
