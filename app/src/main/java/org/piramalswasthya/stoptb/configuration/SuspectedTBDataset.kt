package org.piramalswasthya.stoptb.configuration

import android.content.Context
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.helpers.Languages
import org.piramalswasthya.stoptb.model.AgeUnit
import org.piramalswasthya.stoptb.model.BenRegCache
import org.piramalswasthya.stoptb.model.FormElement
import org.piramalswasthya.stoptb.model.InputType
import org.piramalswasthya.stoptb.model.TBScreeningCache
import org.piramalswasthya.stoptb.model.TBSuspectedCache

class SuspectedTBDataset(
    context: Context,
    currentLanguage: Languages
) : Dataset(context, currentLanguage) {

    private val yesValue get() = resources.getStringArray(R.array.yes_no)[0]
    private val noValue get() = resources.getStringArray(R.array.yes_no)[1]
    private val positiveNegativeEntries get() = resources.getStringArray(R.array.tb_test_result)
    private var benCache: BenRegCache? = null
    private var screeningCache: TBScreeningCache? = null
    private var savedCache: TBSuspectedCache? = null
    private var isQuickPrefillLockActive: Boolean = false

    private val dateOfVisit = FormElement(
        id = 1,
        inputType = InputType.DATE_PICKER,
        title = resources.getString(R.string.tracking_date),
        required = true,
        max = System.currentTimeMillis()
    )

    private val sputumCollected = FormElement(
        id = 2,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.is_sputum_sample_collected),
        entries = resources.getStringArray(R.array.yes_no),
        required = false,
        hasDependants = true
    )

    private val sputumSubmittedAt = FormElement(
        id = 3,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.sputum_sample_submitted_at),
        arrayId = R.array.tb_suspected_sample_submitted_at,
        entries = resources.getStringArray(R.array.tb_suspected_sample_submitted_at),
        required = false,
        hasDependants = true
    )

//    private val sputumTestResult = FormElement(
//        id = 4,
//        inputType = InputType.RADIO,
//        title = resources.getString(R.string.sputum_test_result),
//        arrayId = R.array.tb_test_result,
//        entries = resources.getStringArray(R.array.tb_test_result),
//        required = false,
//        hasDependants = true
//    )

    private val digitalChestXRayConducted = FormElement(
        id = 5,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.tb_is_digital_chest_xray_conducted),
        entries = resources.getStringArray(R.array.yes_no),
        required = false,
        hasDependants = true
    )

    private val digitalChestXRayResult = FormElement(
        id = 6,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.tb_digital_chest_xray_result),
        arrayId = R.array.tb_test_result,
        entries = resources.getStringArray(R.array.tb_test_result),
        required = false,
        hasDependants = true
    )

    private val naatConducted = FormElement(
        id = 9,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.tb_naat_conducted),
        entries = resources.getStringArray(R.array.yes_no),
        required = false,
        hasDependants = true
    )

    private val naatResult = FormElement(
        id = 10,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.tb_naat_result),
        arrayId = R.array.tb_test_result,
        entries = resources.getStringArray(R.array.tb_test_result),
        required = false,
        hasDependants = true
    )

    private val liquidCultureConducted = FormElement(
        id = 16,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.tb_liquid_culture_conducted),
        entries = resources.getStringArray(R.array.yes_no),
        required = false,
        hasDependants = true
    )

    private val liquidCultureResult = FormElement(
        id = 17,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.tb_liquid_culture_result),
        arrayId = R.array.tb_test_result,
        entries = resources.getStringArray(R.array.tb_test_result),
        required = false,
        hasDependants = true
    )

    private val nikshayId = FormElement(
        id = 11,
        inputType = InputType.EDIT_TEXT,
        title = resources.getString(R.string.nikshay_id),
        required = false
    )

    suspend fun setUpPage(
        ben: BenRegCache?,
        screening: TBScreeningCache?,
        saved: TBSuspectedCache?
    ) {
        benCache = ben
        screeningCache = screening
        savedCache = saved
        isQuickPrefillLockActive = saved?.visitLabel.isNullOrBlank() && saved != null
        ben?.let {
            dateOfVisit.min = it.regDate
        }

        if (saved == null) {
            dateOfVisit.value = getDateFromLong(System.currentTimeMillis())
        } else {
            dateOfVisit.value = getDateFromLong(saved.visitDate)
            sputumCollected.value = boolToYesNo(saved.isSputumCollected)
            sputumSubmittedAt.value = getLocalValueInArray(
                R.array.tb_suspected_sample_submitted_at,
                saved.sputumSubmittedAt
            )
//            sputumTestResult.value = getLocalValueInArray(R.array.tb_test_result, saved.sputumTestResult)
            digitalChestXRayConducted.value = boolToYesNo(saved.isChestXRayDone)
            digitalChestXRayResult.value = getLocalValueInArray(R.array.tb_test_result, saved.chestXRayResult)
            naatConducted.value = boolToYesNo(saved.isNaatConducted)
            naatResult.value = getLocalValueInArray(R.array.tb_test_result, saved.naatResult)
            liquidCultureConducted.value = boolToYesNo(saved.isLiquidCultureConducted)
            liquidCultureResult.value = getLocalValueInArray(R.array.tb_test_result, saved.liquidCultureResult)
            nikshayId.value = saved.nikshayId
        }

        syncFieldStates()
        setUpPage(buildFormList())
    }

    override suspend fun handleListOnValueChanged(formId: Int, index: Int): Int {
        syncFieldStates()
        triggerDependants(
            source = sputumCollected,
            passedIndex = if (isYes(sputumCollected)) 1 else 0,
            triggerIndex = 1,
//            target = listOf(sputumSubmittedAt, sputumTestResult)
            target = listOf(sputumSubmittedAt)
        )
        triggerDependants(
            source = digitalChestXRayConducted,
            passedIndex = if (isYes(digitalChestXRayConducted)) 1 else 0,
            triggerIndex = 1,
            target = digitalChestXRayResult
        )
        triggerDependants(
            source = naatConducted,
            passedIndex = if (isYes(naatConducted)) 1 else 0,
            triggerIndex = 1,
            target = naatResult
        )
        triggerDependants(
            source = liquidCultureConducted,
            passedIndex = if (isYes(liquidCultureConducted)) 1 else 0,
            triggerIndex = 1,
            target = liquidCultureResult
        )
        updateNikshayVisibility()
        return 0
    }

    override fun mapValues(cacheModel: FormDataModel, pageNumber: Int) {
        (cacheModel as TBSuspectedCache).let { form ->
            val confirmed = isMarkedConfirmed()

            form.visitDate = getLongFromDate(dateOfVisit.value)
            form.visitLabel = resources.getString(R.string.visit_format, 1)
            form.typeOfTBCase = null
            form.reasonForSuspicion = null
            form.otherReasonForSuspicion = null
            form.hasSymptoms = true
            form.isSputumCollected = isYes(sputumCollected).takeIf { isSputumReferralEnabled() }
            form.sputumSubmittedAt =
                getEnglishValueInArray(R.array.tb_suspected_sample_submitted_at, sputumSubmittedAt.value)
//            form.sputumTestResult = getEnglishValueInArray(R.array.tb_test_result, sputumTestResult.value)
            form.isChestXRayDone = isYes(digitalChestXRayConducted).takeIf { isDigitalChestXRayReferralEnabled() }
            form.chestXRayResult = getEnglishValueInArray(R.array.tb_test_result, digitalChestXRayResult.value)
            form.isAICoughAssessmentDone = null
            form.aiCoughAssessmentResult = null
            form.isNaatConducted = isYes(naatConducted).takeIf { isNaatReferralEnabled() }
            form.naatResult = getEnglishValueInArray(R.array.tb_test_result, naatResult.value)
            form.isLiquidCultureConducted = isYes(liquidCultureConducted)
            form.liquidCultureResult = getEnglishValueInArray(R.array.tb_test_result, liquidCultureResult.value)
            form.nikshayId = nikshayId.value?.trim()?.takeIf { it.isNotEmpty() }
            form.referralFacility = null
            form.isTBConfirmed = confirmed
            form.isDRTBConfirmed = null
            form.isConfirmed = confirmed
        }
    }

    fun getSubmissionIndex(): Int = getIndexOfElement(dateOfVisit)

    fun getAlerts(): String? =
        if (isMarkedConfirmed()) resources.getString(R.string.tb_suspected_alert_confirmed) else null

//    private fun buildFormList(): List<FormElement> = listOf(
//        dateOfVisit,
//    ).toMutableList().apply {
//        add(digitalChestXRayConducted)
//        if (isYes(digitalChestXRayConducted)) add(digitalChestXRayResult)
//        if (isSputumReferralEnabled()) {
//            add(sputumCollected)
////            if (isYes(sputumCollected)) addAll(listOf(sputumSubmittedAt, sputumTestResult))
//            if (isYes(sputumCollected)) addAll(listOf(sputumSubmittedAt))
//        }
//        if (isNaatReferralEnabled()) {
//            add(naatConducted)
//            if (isYes(naatConducted)) add(naatResult)
//        }
//        if (isLiquidCultureReferralEnabled()) {
//            add(liquidCultureConducted)
//            if (isYes(liquidCultureConducted)) add(liquidCultureResult)
//        }
//        if (shouldEnableNikshayId()) add(nikshayId)
//    }

    private fun buildFormList(): List<FormElement> = listOf(
        dateOfVisit,
    ).toMutableList().apply {

        if (isDigitalChestXRayReferralEnabled()) {
            add(digitalChestXRayConducted)
            if (isYes(digitalChestXRayConducted)) {
                add(digitalChestXRayResult)
            }
        }

        if (isSputumReferralEnabled()) {
            add(sputumCollected)
            if (isYes(sputumCollected)) {
                add(sputumSubmittedAt)
            }
        }

        if (isNaatReferralEnabled()) {
            add(naatConducted)
            if (isYes(naatConducted)) {
                add(naatResult)
            }
        }

        if (isLiquidCultureReferralEnabled()) {
            add(liquidCultureConducted)
            if (isYes(liquidCultureConducted)) {
                add(liquidCultureResult)
            }
        }

        if (shouldEnableNikshayId()) {
            add(nikshayId)
        }
    }
    private fun syncFieldStates() {
        syncReferralDrivenField(
            radioField = digitalChestXRayConducted,
            referralEnabled =  isDigitalChestXRayReferralEnabled(),
            locked = shouldLockDigitalChestXRayConducted()
        )
        syncReferralDrivenField(
            radioField = naatConducted,
            referralEnabled = isNaatReferralEnabled(),
            locked = shouldLockTrueNatConducted()
        )
        syncReferralDrivenField(
            radioField = liquidCultureConducted,
            referralEnabled = isLiquidCultureReferralEnabled(),
            locked = shouldLockLiquidCultureConducted()
        )
        syncReferralDrivenField(
            radioField = sputumCollected,
            referralEnabled = isSputumReferralEnabled(),
            locked = shouldLockSputumCollected()
        )

        sputumSubmittedAt.isEnabled = isSputumReferralEnabled() && isYes(sputumCollected)
//        sputumSubmittedAt.required = sputumSubmittedAt.isEnabled
        sputumSubmittedAt.required = false
        if (!sputumSubmittedAt.isEnabled) resetField(sputumSubmittedAt)

//        sputumTestResult.isEnabled = isSputumReferralEnabled() && isYes(sputumCollected)
//        sputumTestResult.required = sputumTestResult.isEnabled
//        if (!sputumTestResult.isEnabled) resetField(sputumTestResult)

        digitalChestXRayResult.isEnabled =
            isDigitalChestXRayReferralEnabled() &&
            isYes(digitalChestXRayConducted) &&
            !shouldLockDigitalChestXRayResult()
        digitalChestXRayResult.required = digitalChestXRayResult.isEnabled
        if (!digitalChestXRayResult.isEnabled && !shouldLockDigitalChestXRayResult()) resetField(digitalChestXRayResult)

        naatResult.isEnabled = isNaatReferralEnabled() && isYes(naatConducted) && !shouldLockTrueNatResult()
        naatResult.required = naatResult.isEnabled
        if (!naatResult.isEnabled && !shouldLockTrueNatResult()) resetField(naatResult)

        liquidCultureResult.isEnabled =
            isLiquidCultureReferralEnabled() &&
            isYes(liquidCultureConducted) &&
            !shouldLockLiquidCultureResult()
        liquidCultureResult.required = liquidCultureResult.isEnabled
        if (!liquidCultureResult.isEnabled && !shouldLockLiquidCultureResult()) resetField(liquidCultureResult)

        nikshayId.isEnabled = shouldEnableNikshayId()
        if (!nikshayId.isEnabled) resetField(nikshayId)

    }

    private fun syncReferralDrivenField(radioField: FormElement, referralEnabled: Boolean, locked: Boolean = false) {
        radioField.isEnabled = referralEnabled && !locked
        radioField.required = referralEnabled
        if (!referralEnabled && !locked) {
            resetField(radioField)
        }
    }

    private fun shouldLockDigitalChestXRayConducted(): Boolean =
        isQuickPrefillLockActive && savedCache?.isChestXRayDone != null

    private fun shouldLockDigitalChestXRayResult(): Boolean =
        isQuickPrefillLockActive && !savedCache?.chestXRayResult.isNullOrBlank()

    private fun shouldLockSputumCollected(): Boolean =
        isQuickPrefillLockActive && savedCache?.isSputumCollected != null

    private fun shouldLockTrueNatConducted(): Boolean =
        isQuickPrefillLockActive && savedCache?.isNaatConducted != null

    private fun shouldLockTrueNatResult(): Boolean =
        isQuickPrefillLockActive && !savedCache?.naatResult.isNullOrBlank()

    private fun shouldLockLiquidCultureConducted(): Boolean =
        isQuickPrefillLockActive && savedCache?.isLiquidCultureConducted != null

    private fun shouldLockLiquidCultureResult(): Boolean =
        isQuickPrefillLockActive && !savedCache?.liquidCultureResult.isNullOrBlank()

    private fun shouldEnableNikshayId(): Boolean =
        isYes(sputumCollected) ||
            isYes(digitalChestXRayConducted) ||
            isYes(naatConducted)

    private fun isMarkedConfirmed(): Boolean =
//        sputumTestResult.value == positiveNegativeEntries.getOrNull(0) ||
            digitalChestXRayResult.value == positiveNegativeEntries.getOrNull(0) ||
            naatResult.value == positiveNegativeEntries.getOrNull(0) ||
            liquidCultureResult.value == positiveNegativeEntries.getOrNull(0)

    private fun isSputumReferralEnabled(): Boolean =
        isChestXRayPositive() ||
            screeningCache?.historyOfTb == true ||
            isUnderFive() ||
            isPregnant() ||
            screeningCache?.takingAntiTBDrugs == true ||
            savedCache?.isSputumCollected != null

    private fun isDigitalChestXRayReferralEnabled(): Boolean =
        !isUnderFive() && !isPregnant()

    private fun isNaatReferralEnabled(): Boolean =
        isChestXRayPositive() ||
            isYes(sputumCollected) ||
            savedCache?.isNaatConducted != null

    private fun isLiquidCultureReferralEnabled(): Boolean =
        screeningCache?.historyOfTb == true ||
            screeningCache?.takingAntiTBDrugs == true ||
            savedCache?.isLiquidCultureConducted != null

    private fun isChestXRayPositive(): Boolean =
        digitalChestXRayResult.value == positiveNegativeEntries.getOrNull(0)

    private fun isUnderFive(): Boolean {
        val ben = benCache ?: return false
        return when (ben.ageUnit) {
            AgeUnit.YEARS -> ben.age <= 5
            AgeUnit.MONTHS, AgeUnit.DAYS -> true
            else -> false
        }
    }

    private fun isPregnant(): Boolean {
        val ben = benCache ?: return false
        return ben.genDetails?.reproductiveStatusId == 1 ||
            ben.genDetails?.reproductiveStatus?.equals("Yes", ignoreCase = true) == true
    }

    private fun resetField(formElement: FormElement) {
        formElement.value = null
        formElement.errorText = null
    }

    private fun boolToYesNo(value: Boolean?): String =
        when (value) {
            true -> yesValue
            false -> noValue
            null -> ""
        }

    private fun isYes(formElement: FormElement): Boolean = formElement.value == yesValue

    private fun updateNikshayVisibility() {
        val anchor = when {
            isNaatReferralEnabled() && isYes(naatConducted) -> naatResult
            isNaatReferralEnabled() -> naatConducted
            isDigitalChestXRayReferralEnabled() && isYes(digitalChestXRayConducted) -> digitalChestXRayResult
            isDigitalChestXRayReferralEnabled() -> digitalChestXRayConducted
//            isSputumReferralEnabled() && isYes(sputumCollected) -> sputumTestResult
            else -> sputumCollected
        }
        triggerDependants(
            source = anchor,
            passedIndex = if (shouldEnableNikshayId()) 1 else 0,
            triggerIndex = 1,
            target = nikshayId
        )
    }

    companion object {
        private const val TB_REFERRAL_CHEST_XRAY = "Digital Chest X-ray"
    }
}
