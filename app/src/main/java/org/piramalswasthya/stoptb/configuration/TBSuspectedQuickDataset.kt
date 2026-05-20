package org.piramalswasthya.stoptb.configuration

import android.content.Context
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.helpers.Languages
import org.piramalswasthya.stoptb.model.AgeUnit
import org.piramalswasthya.stoptb.model.BenRegCache
import org.piramalswasthya.stoptb.model.FormElement
import org.piramalswasthya.stoptb.model.InputType
import org.piramalswasthya.stoptb.model.TBDiagnosticsCache
import org.piramalswasthya.stoptb.model.TBScreeningCache

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
    private val nikshayIdUnavailable = "N/A"

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
        required = true,
        hasDependants = true
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
        title = resources.getString(R.string.recommended_for_liquid_culture_test),
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
        required = false,
        hasDependants = true
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

    private val nikshayId = FormElement(
        id = 8,
        inputType = InputType.TEXT_VIEW,
        title = resources.getString(R.string.nikshay_id),
        required = false
    )

    suspend fun setUpPage(
        ben: BenRegCache?,
        screening: TBScreeningCache?,
        saved: TBDiagnosticsCache?,
        referralMode: Boolean = false
    ) {
        benCache = ben
        screeningCache = screening
        this.referralMode = referralMode

        digitalChestXrayConducted.value =
            conductedValueFromScreening(
                savedValue = saved?.isChestXRayDone,
                shouldShow = shouldShowDigitalChestXray(),
                screeningRecommended = screeningCache?.referredForDigitalChestXray
            )
        digitalChestXrayResult.value =
            getLocalValueInArray(R.array.tb_test_result, saved?.chestXRayResult)
        sputumCollected.value =
            conductedValueFromScreening(
                savedValue = saved?.isSputumCollected,
                shouldShow = shouldShowSputumCollected(),
                screeningRecommended = screeningCache?.referredForSputumCollection
            )
        trueNatConducted.value =
            conductedValueFromScreening(
                savedValue = saved?.isNaatConducted,
                shouldShow = shouldShowTrueNatConducted(),
                screeningRecommended = screeningCache?.recommendedForTruenatTest
            )
        trueNatResult.value =
            getLocalValueInArray(R.array.tb_test_result, saved?.naatResult)
        nikshayId.value = ben?.nikshayId?.takeIf { it.isNotBlank() }
            ?: saved?.nikshayId?.takeIf { it.isNotBlank() }
            ?: nikshayIdUnavailable
        liquidCultureConducted.value =
            conductedValueFromScreening(
                savedValue = saved?.recommendedForLiquidCultureTest
                    ?: saved?.isLiquidCultureConducted,
                shouldShow = shouldShowLiquidCultureConducted(),
                screeningRecommended = screeningCache?.recommendedForLiquidCultureTest
            )
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
                    val updateIndex = triggerDependants(
                        source = digitalChestXrayConducted,
                        removeItems = emptyList(),
                        addItems = listOf(digitalChestXrayResult)
                    )
                    val changedIndex = getChangedElementIndex(digitalChestXrayConducted)
                    if (changedIndex != -1) changedIndex else updateIndex
                } else {
                    val removeItems = mutableListOf(
                        digitalChestXrayResult,
                        trueNatResult,
                        trueNatConducted
                    )
                    val updateIndex = triggerDependants(
                        source = digitalChestXrayConducted,
                        removeItems = removeItems,
                        addItems = emptyList()
                    )
                    val changedIndex = getChangedElementIndex(digitalChestXrayConducted)
                    if (changedIndex != -1) changedIndex else updateIndex
                }
            }

            sputumCollected.id -> {
                sputumCollected.value = if (index == 0) yesValue else noValue
                syncFieldStates()
                getChangedElementIndex(sputumCollected)
            }

            digitalChestXrayResult.id -> {
                digitalChestXrayResult.value = digitalChestXrayResult.entries?.getOrNull(index)
                syncFieldStates()
                val addItems = mutableListOf<FormElement>()
                val removeItems = mutableListOf<FormElement>()
                if (shouldShowTrueNatConducted()) {
                    addItems.add(trueNatConducted)
                    if (isYes(trueNatConducted)) addItems.add(trueNatResult)
                } else {
                    removeItems.addAll(listOf(trueNatResult, trueNatConducted))
                }
                triggerDependants(
                    source = digitalChestXrayResult,
                    removeItems = removeItems,
                    addItems = addItems
                )
            }

            trueNatConducted.id -> {
                trueNatConducted.value = if (index == 0) yesValue else noValue
                syncFieldStates()
                if (index == 0) {
                    val updateIndex = triggerDependants(
                        source = trueNatConducted,
                        removeItems = emptyList(),
                        addItems = listOf(trueNatResult)
                    )
                    val changedIndex = getChangedElementIndex(trueNatConducted)
                    if (changedIndex != -1) changedIndex else updateIndex
                } else {
                    val removeItems = mutableListOf(trueNatResult)
                    val updateIndex = triggerDependants(
                        source = trueNatConducted,
                        removeItems = removeItems,
                        addItems = emptyList()
                    )
                    val changedIndex = getChangedElementIndex(trueNatConducted)
                    if (changedIndex != -1) changedIndex else updateIndex
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
        (cacheModel as TBDiagnosticsCache).let { form ->
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
            form.nikshayId =
                nikshayId.value?.trim()?.takeIf { it.isNotEmpty() && it != nikshayIdUnavailable }
            form.isLiquidCultureConducted =
                if (shouldShowLiquidCultureConducted()) isYes(liquidCultureConducted) else null
            form.recommendedForLiquidCultureTest =
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
        add(nikshayId)

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

    private fun configureReferralLocks(saved: TBDiagnosticsCache?) {
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

        nikshayId.isEnabled = false

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

    private fun shouldShowDigitalChestXray(): Boolean =
        screeningCache?.referredForDigitalChestXray ?: !isPregnant()

    private fun shouldShowSputumCollected(): Boolean =
        screeningCache?.referredForSputumCollection ?: (
            screeningCache?.historyOfTb == true ||
                isPregnant() ||
                screeningCache?.takingAntiTBDrugs == true
            )

    private fun shouldShowTrueNatConducted(): Boolean =
        screeningCache?.recommendedForTruenatTest == true ||
            isPregnant() ||
            isPositive(getEnglishValueInArray(R.array.tb_test_result, digitalChestXrayResult.value))

    private fun shouldShowLiquidCultureConducted(): Boolean =
        screeningCache?.recommendedForLiquidCultureTest ?: (
            screeningCache?.historyOfTb == true && screeningCache?.takingAntiTBDrugs == true
            )

    private fun getChangedElementIndex(source: FormElement): Int = getIndexOfElement(source)

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
            reproductiveStatus.equals("Yes", ignoreCase = true)
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

    private fun conductedValueFromScreening(
        savedValue: Boolean?,
        shouldShow: Boolean,
        screeningRecommended: Boolean?
    ): String {
        if (!shouldShow) return ""
        return when (savedValue) {
            true -> yesValue
            false -> noValue
            null -> if (screeningRecommended == true) yesValue else ""
        }
    }

    private fun isYes(formElement: FormElement): Boolean = formElement.value == yesValue
}
