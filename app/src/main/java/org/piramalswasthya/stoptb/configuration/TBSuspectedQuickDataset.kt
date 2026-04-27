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

class TBSuspectedQuickDataset(
    context: Context,
    currentLanguage: Languages
) : Dataset(context, currentLanguage) {

    private val yesNoEntries get() = resources.getStringArray(R.array.yes_no)
    private val yesValue get() = yesNoEntries[0]
    private val noValue get() = yesNoEntries[1]

    private var benCache: BenRegCache? = null
    private var screeningCache: TBScreeningCache? = null
    private var referralMode = false

    private var lockDigitalChestXray = false
    private var lockTrueNat = false
    private var lockLiquidCulture = false

    private val digitalChestXrayConducted = FormElement(
        id = 1,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.tb_digital_chest_xray_conducted),
        entries = yesNoEntries,
        required = true,
        hasDependants = true
    )

    private val sputumCollected = FormElement(
        id = 2,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.tb_sputum_sample_collected),
        entries = yesNoEntries,
        required = true
    )

    private val trueNatConducted = FormElement(
        id = 3,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.tb_naat_conducted),
        entries = yesNoEntries,
        required = true,
        hasDependants = true
    )

    private val liquidCultureConducted = FormElement(
        id = 4,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.tb_liquid_culture_conducted),
        entries = yesNoEntries,
        required = true,
        hasDependants = true
    )

    private val digitalChestXrayResult = FormElement(
        id = 5,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.tb_digital_chest_xray_result),
        arrayId = R.array.tb_test_result,
        entries = resources.getStringArray(R.array.tb_test_result),
        required = false
    )

    private val trueNatResult = FormElement(
        id = 6,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.tb_naat_result),
        arrayId = R.array.tb_test_result,
        entries = resources.getStringArray(R.array.tb_test_result),
        required = false
    )

    private val liquidCultureResult = FormElement(
        id = 7,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.tb_liquid_culture_result),
        arrayId = R.array.tb_test_result,
        entries = resources.getStringArray(R.array.tb_test_result),
        required = false
    )

    suspend fun setUpPage(
        ben: BenRegCache?,
        screening: TBScreeningCache?,
        saved: TBSuspectedCache?,
        referralMode: Boolean = false
    ) {
        benCache = ben
        screeningCache = screening
        this.referralMode = referralMode

        digitalChestXrayConducted.value =
            boolToYesNo(saved?.isChestXRayDone.takeIf { shouldShowDigitalChestXray() })
        digitalChestXrayResult.value =
            getLocalValueInArray(R.array.tb_test_result, saved?.chestXRayResult)
        sputumCollected.value =
            boolToYesNo(saved?.isSputumCollected.takeIf { shouldShowSputumCollected() })
        trueNatConducted.value =
            boolToYesNo(saved?.isNaatConducted.takeIf { shouldShowTrueNatConducted() })
        trueNatResult.value =
            getLocalValueInArray(R.array.tb_test_result, saved?.naatResult)
        liquidCultureConducted.value =
            boolToYesNo(saved?.isLiquidCultureConducted.takeIf { shouldShowLiquidCultureConducted() })
        liquidCultureResult.value =
            getLocalValueInArray(R.array.tb_test_result, saved?.liquidCultureResult)

        configureReferralLocks(saved)
        syncFieldStates()
        setUpPage(buildFormList())
    }

    override suspend fun handleListOnValueChanged(formId: Int, index: Int): Int {
        return when (formId) {
            digitalChestXrayConducted.id -> {
                digitalChestXrayConducted.value = if (index == 0) yesValue else noValue
                syncFieldStates()
                if (index == 0) {
                    triggerDependants(
                        source = digitalChestXrayConducted,
                        removeItems = emptyList(),
                        addItems = listOf(digitalChestXrayResult)
                    )
                } else {
                    triggerDependants(
                        source = digitalChestXrayConducted,
                        removeItems = listOf(digitalChestXrayResult),
                        addItems = emptyList()
                    )
                }
            }

            trueNatConducted.id -> {
                trueNatConducted.value = if (index == 0) yesValue else noValue
                syncFieldStates()
                if (index == 0) {
                    triggerDependants(
                        source = trueNatConducted,
                        removeItems = emptyList(),
                        addItems = listOf(trueNatResult)
                    )
                } else {
                    triggerDependants(
                        source = trueNatConducted,
                        removeItems = listOf(trueNatResult),
                        addItems = emptyList()
                    )
                }
            }

            liquidCultureConducted.id -> {
                liquidCultureConducted.value = if (index == 0) yesValue else noValue
                syncFieldStates()
                if (index == 0) {
                    triggerDependants(
                        source = liquidCultureConducted,
                        removeItems = emptyList(),
                        addItems = listOf(liquidCultureResult)
                    )
                } else {
                    triggerDependants(
                        source = liquidCultureConducted,
                        removeItems = listOf(liquidCultureResult),
                        addItems = emptyList()
                    )
                }
            }

            else -> 0
        }
    }

    override fun mapValues(cacheModel: FormDataModel, pageNumber: Int) {
        (cacheModel as TBSuspectedCache).let { form ->
            form.isChestXRayDone =
                if (shouldShowDigitalChestXray()) isYes(digitalChestXrayConducted) else null
            form.chestXRayResult =
                if (isYes(digitalChestXrayConducted)) {
                    getEnglishValueInArray(R.array.tb_test_result, digitalChestXrayResult.value)
                } else {
                    null
                }
            form.isSputumCollected =
                if (shouldShowSputumCollected()) isYes(sputumCollected) else null
            form.isNaatConducted =
                if (shouldShowTrueNatConducted()) isYes(trueNatConducted) else null
            form.naatResult =
                if (isYes(trueNatConducted)) {
                    getEnglishValueInArray(R.array.tb_test_result, trueNatResult.value)
                } else {
                    null
                }
            form.isLiquidCultureConducted =
                if (shouldShowLiquidCultureConducted()) isYes(liquidCultureConducted) else null
            form.liquidCultureResult =
                if (isYes(liquidCultureConducted)) {
                    getEnglishValueInArray(R.array.tb_test_result, liquidCultureResult.value)
                } else {
                    null
                }

            val isConfirmed = isPositive(form.chestXRayResult) ||
                isPositive(form.naatResult) ||
                isPositive(form.liquidCultureResult)
            form.isTBConfirmed = isConfirmed
            form.isConfirmed = isConfirmed
        }
    }

    fun shouldShowSubmit(): Boolean {
        if (!referralMode) return true
        return listOf(
            shouldShowDigitalChestXray() && !lockDigitalChestXray,
            shouldShowTrueNatConducted() && !lockTrueNat,
            shouldShowLiquidCultureConducted() && !lockLiquidCulture
        ).any { it }
    }

    private fun buildFormList(): List<FormElement> = buildList {
        if (shouldShowDigitalChestXray()) {
            add(digitalChestXrayConducted)
            if (isYes(digitalChestXrayConducted)) {
                add(digitalChestXrayResult)
            }
        }

        if (shouldShowSputumCollected()) {
            add(sputumCollected)
        }

        if (shouldShowTrueNatConducted()) {
            add(trueNatConducted)
            if (isYes(trueNatConducted)) {
                add(trueNatResult)
            }
        }

        if (shouldShowLiquidCultureConducted()) {
            add(liquidCultureConducted)
            if (isYes(liquidCultureConducted)) {
                add(liquidCultureResult)
            }
        }
    }

    private fun configureReferralLocks(saved: TBSuspectedCache?) {
        if (!referralMode || saved == null) {
            lockDigitalChestXray = false
            lockTrueNat = false
            lockLiquidCulture = false
            return
        }

        lockDigitalChestXray = !saved.chestXRayResult.isNullOrBlank()
        lockTrueNat = !saved.naatResult.isNullOrBlank()
        lockLiquidCulture = !saved.liquidCultureResult.isNullOrBlank()
    }

    private fun syncFieldStates() {
        digitalChestXrayConducted.isEnabled = shouldShowDigitalChestXray() && !lockDigitalChestXray
        digitalChestXrayConducted.required = shouldShowDigitalChestXray() && !lockDigitalChestXray
        if (!shouldShowDigitalChestXray()) resetField(digitalChestXrayConducted)

        digitalChestXrayResult.isEnabled =
            shouldShowDigitalChestXray() && isYes(digitalChestXrayConducted) && !lockDigitalChestXray
//        digitalChestXrayResult.required = digitalChestXrayResult.isEnabled
        if (!shouldShowDigitalChestXray() || !isYes(digitalChestXrayConducted)) {
            resetField(digitalChestXrayResult)
        }

        sputumCollected.isEnabled = !referralMode
        sputumCollected.required = shouldShowSputumCollected() && !referralMode
        if (!shouldShowSputumCollected()) resetField(sputumCollected)

        trueNatConducted.isEnabled = shouldShowTrueNatConducted() && !lockTrueNat
        trueNatConducted.required = shouldShowTrueNatConducted() && !lockTrueNat
        if (!shouldShowTrueNatConducted()) resetField(trueNatConducted)

        trueNatResult.isEnabled =
            shouldShowTrueNatConducted() && isYes(trueNatConducted) && !lockTrueNat
//        trueNatResult.required = trueNatResult.isEnabled
        if (!shouldShowTrueNatConducted() || !isYes(trueNatConducted)) {
            resetField(trueNatResult)
        }

        liquidCultureConducted.isEnabled =
            shouldShowLiquidCultureConducted() && !lockLiquidCulture
        liquidCultureConducted.required =
            shouldShowLiquidCultureConducted() && !lockLiquidCulture
        if (!shouldShowLiquidCultureConducted()) resetField(liquidCultureConducted)

        liquidCultureResult.isEnabled =
            shouldShowLiquidCultureConducted() && isYes(liquidCultureConducted) && !lockLiquidCulture
//        liquidCultureResult.required = liquidCultureResult.isEnabled
        if (!shouldShowLiquidCultureConducted() || !isYes(liquidCultureConducted)) {
            resetField(liquidCultureResult)
        }
    }

    private fun shouldShowDigitalChestXray(): Boolean = !isUnderFive() && !isPregnant()

    private fun shouldShowSputumCollected(): Boolean =
        screeningCache?.historyOfTb == true ||
            isUnderFive() ||
            isPregnant() ||
            screeningCache?.takingAntiTBDrugs == true

    private fun shouldShowTrueNatConducted(): Boolean =
        isUnderFive() || isPregnant()

    private fun shouldShowLiquidCultureConducted(): Boolean =
        screeningCache?.historyOfTb == true || screeningCache?.takingAntiTBDrugs == true

    private fun isUnderFive(): Boolean {
        val ben = benCache ?: return false
        return when (ben.ageUnit) {
            AgeUnit.YEARS -> ben.age <= 5
            AgeUnit.MONTHS, AgeUnit.DAYS -> true
            else -> false
        }
    }

    private fun isPregnant(): Boolean {
        val reproductiveStatus = benCache?.genDetails?.reproductiveStatus
        return benCache?.genDetails?.reproductiveStatusId == 1 ||
            reproductiveStatus.equals("Yes", ignoreCase = true) ||
            (reproductiveStatus?.contains("preg", ignoreCase = true) == true)
    }

    private fun resetField(formElement: FormElement) {
        formElement.value = null
        formElement.errorText = null
    }

    private fun isPositive(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val englishPositive = englishResources.getStringArray(R.array.tb_test_result).firstOrNull()
        return value.equals(englishPositive, ignoreCase = true)
    }

    private fun boolToYesNo(value: Boolean?): String = when (value) {
        true -> yesValue
        false -> noValue
        null -> ""
    }

    private fun isYes(formElement: FormElement): Boolean = formElement.value == yesValue
}
