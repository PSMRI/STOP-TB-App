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

    // ── Always visible ────────────────────────────────────────────────────────

    private val nikshayId = FormElement(
        id = 8,
        inputType = InputType.TEXT_VIEW,
        title = resources.getString(R.string.nikshay_id),
        required = false
    )

    // ── Digital Chest X-Ray block ─────────────────────────────────────────────

    private val referredForDigitalChestXray = FormElement(
        id = 9,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.tb_referred_for_digital_chest_xray),
        entries = yesNoEntries,
        required = true,
        hasDependants = true
    )

    private val reasonForDenialChestXray = FormElement(
        id = 10,
        inputType = InputType.CHECKBOXES,
        title = resources.getString(R.string.tb_reason_for_denial_chest_xray),
        arrayId = R.array.tb_reason_for_denial_xray,
        entries = resources.getStringArray(R.array.tb_reason_for_denial_xray),
        required = false,
        hasDependants = true,
        showAsMultiSelectDialog = true
    )

    private val reasonForDenialChestXrayOther = FormElement(
        id = 11,
        inputType = InputType.EDIT_TEXT,
        title = resources.getString(R.string.tb_reason_for_denial_chest_xray_other),
        required = false,
        etMaxLength = 250
    )

    private val digitalChestXrayConducted = FormElement(
        id = 1,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.tb_digital_chest_xray_conducted),
        entries = yesNoEntries,
        required = true,
        hasDependants = true
    )

    private val reasonNotConductedChestXray = FormElement(
        id = 12,
        inputType = InputType.DROPDOWN,
        title = resources.getString(R.string.tb_reason_not_conducted_xray),
        arrayId = R.array.tb_reason_not_conducted_xray,
        entries = resources.getStringArray(R.array.tb_reason_not_conducted_xray),
        required = false,
        hasDependants = true
    )

    private val reasonNotConductedChestXrayOther = FormElement(
        id = 13,
        inputType = InputType.EDIT_TEXT,
        title = resources.getString(R.string.tb_reason_not_conducted_xray_other),
        required = false,
        etMaxLength = 250
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

    // ── Sputum Collection block ────────────────────────────────────────────────

    private val referredForSputumCollection = FormElement(
        id = 2,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.tb_referred_for_sputum_collection),
        entries = yesNoEntries,
        required = true,
        hasDependants = true
    )

    private val reasonForDenialSputum = FormElement(
        id = 14,
        inputType = InputType.CHECKBOXES,
        title = resources.getString(R.string.tb_reason_for_denial_sputum),
        arrayId = R.array.tb_reason_for_denial_sputum,
        entries = resources.getStringArray(R.array.tb_reason_for_denial_sputum),
        required = false,
        hasDependants = true,
        showAsMultiSelectDialog = true
    )

    private val reasonForDenialSputumOther = FormElement(
        id = 15,
        inputType = InputType.EDIT_TEXT,
        title = resources.getString(R.string.tb_reason_for_denial_sputum_other),
        required = false,
        etMaxLength = 250
    )

    private val sputumSampleSubmittedAt = FormElement(
        id = 16,
        inputType = InputType.DROPDOWN,
        title = resources.getString(R.string.tb_sputum_submitted_at),
        arrayId = R.array.tb_diagnostics_sputum_submitted_at,
        entries = resources.getStringArray(R.array.tb_diagnostics_sputum_submitted_at),
        required = false,
        hasDependants = true
    )

    // ── NAAT / TrueNAT block ──────────────────────────────────────────────────

    private val trueNatConducted = FormElement(
        id = 3,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.tb_naat_conducted),
        entries = yesNoEntries,
        required = true,
        hasDependants = true
    )

    private val reasonNotConductedNaat = FormElement(
        id = 17,
        inputType = InputType.DROPDOWN,
        title = resources.getString(R.string.tb_reason_not_conducted_naat),
        arrayId = R.array.tb_reason_not_conducted_naat,
        entries = resources.getStringArray(R.array.tb_reason_not_conducted_naat),
        required = false,
        hasDependants = true
    )

    private val reasonNotConductedNaatOther = FormElement(
        id = 18,
        inputType = InputType.EDIT_TEXT,
        title = resources.getString(R.string.tb_reason_not_conducted_naat_other),
        required = false,
        etMaxLength = 250
    )

    private val trueNatResult = FormElement(
        id = 6,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.tb_naat_result),
        arrayId = R.array.tb_test_result,
        entries = resources.getStringArray(R.array.tb_test_result),
        required = false
    )

    // ── Liquid Culture block ───────────────────────────────────────────────────

    private val liquidCultureConducted = FormElement(
        id = 4,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.recommended_for_liquid_culture_test),
        entries = yesNoEntries,
        required = true,
        hasDependants = true
    )

    private val liquidCultureResult = FormElement(
        id = 7,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.tb_liquid_culture_result),
        arrayId = R.array.tb_test_result,
        entries = resources.getStringArray(R.array.tb_test_result),
        required = false
    )

    // ── Setup ─────────────────────────────────────────────────────────────────

    suspend fun setUpPage(
        ben: BenRegCache?,
        screening: TBScreeningCache?,
        saved: TBDiagnosticsCache?,
        referralMode: Boolean = false
    ) {
        benCache = ben
        screeningCache = screening
        this.referralMode = referralMode

        // NikshayId
        nikshayId.value = ben?.nikshayId?.takeIf { it.isNotBlank() }
            ?: saved?.nikshayId?.takeIf { it.isNotBlank() }
            ?: nikshayIdUnavailable

        // ── Digital Chest X-Ray ────────────────────────────────────────────
        referredForDigitalChestXray.value = boolToYesNo(saved?.isReferredForDigitalChestXray)
        reasonForDenialChestXray.value = englishPipeToIndexPipe(
            saved?.reasonForDenialChestXray, R.array.tb_reason_for_denial_xray
        )
        reasonForDenialChestXrayOther.value = saved?.reasonForDenialChestXrayOther
        digitalChestXrayConducted.value = boolToYesNo(saved?.isChestXRayDone)
        reasonNotConductedChestXray.value = getLocalValueInArray(
            R.array.tb_reason_not_conducted_xray, saved?.reasonNotConductedChestXray
        )
        reasonNotConductedChestXrayOther.value = saved?.reasonNotConductedChestXrayOther
        digitalChestXrayResult.value = getLocalValueInArray(
            R.array.tb_test_result, saved?.chestXRayResult
        )

        // ── Sputum Collection ──────────────────────────────────────────────
        referredForSputumCollection.value = boolToYesNo(saved?.isSputumCollected)
        reasonForDenialSputum.value = englishPipeToIndexPipe(
            saved?.reasonForDenialSputum, R.array.tb_reason_for_denial_sputum
        )
        reasonForDenialSputumOther.value = saved?.reasonForDenialSputumOther
        sputumSampleSubmittedAt.value = getLocalValueInArray(
            R.array.tb_diagnostics_sputum_submitted_at, saved?.sputumSubmittedAt
        )

        // ── TrueNAT ───────────────────────────────────────────────────────
        trueNatConducted.value = boolToYesNo(saved?.isNaatConducted)
        reasonNotConductedNaat.value = getLocalValueInArray(
            R.array.tb_reason_not_conducted_naat, saved?.reasonNotConductedNaat
        )
        reasonNotConductedNaatOther.value = saved?.reasonNotConductedNaatOther
        trueNatResult.value = getLocalValueInArray(R.array.tb_test_result, saved?.naatResult)

        // ── Liquid Culture ────────────────────────────────────────────────
        liquidCultureConducted.value = conductedFromSaved(
            savedValue = saved?.recommendedForLiquidCultureTest ?: saved?.isLiquidCultureConducted,
            shouldShow = shouldShowLiquidCultureConducted()
        )
        liquidCultureResult.value = getLocalValueInArray(
            R.array.tb_test_result, saved?.liquidCultureResult
        )

        // ── Apply defaults for new/blank forms ────────────────────────────
        // Referral for X-Ray defaults to Yes
        if (referredForDigitalChestXray.value.isNullOrBlank()) {
            referredForDigitalChestXray.value = yesValue
        }
        // Denial reason defaults to index 0 (Patient refused) when shown
        if (isNo(referredForDigitalChestXray) && reasonForDenialChestXray.value.isNullOrBlank()) {
            reasonForDenialChestXray.value = "0"
        }
        // Sputum referral defaults to Yes when the section should be shown
        if (shouldShowSputumCollected() && referredForSputumCollection.value.isNullOrBlank()) {
            referredForSputumCollection.value = yesValue
        }
        // Sputum submitted-at defaults to TB Screening Camp when referred = Yes and blank
        if (shouldShowSputumCollected() && isYes(referredForSputumCollection) &&
            sputumSampleSubmittedAt.value.isNullOrBlank()
        ) {
            sputumSampleSubmittedAt.value = sputumSampleSubmittedAt.entries?.firstOrNull()
        }
        // Sputum denial reason defaults to index 0 (Patient refused) when shown
        if (shouldShowSputumCollected() && isNo(referredForSputumCollection) &&
            reasonForDenialSputum.value.isNullOrBlank()
        ) {
            reasonForDenialSputum.value = "0"
        }

        configureReferralLocks(saved)
        syncFieldStates()
        setUpPage(buildFormList())
    }

    // ── Value change handler ──────────────────────────────────────────────────

    override suspend fun handleListOnValueChanged(formId: Int, index: Int): Int {
        return when (formId) {

            referredForDigitalChestXray.id -> {
                referredForDigitalChestXray.value = if (index == 0) yesValue else noValue
                syncFieldStates()
                if (index == 0) {
                    // Yes: remove denial, add conducted
                    triggerDependants(
                        source = referredForDigitalChestXray,
                        removeItems = listOf(
                            reasonForDenialChestXray,
                            reasonForDenialChestXrayOther
                        ),
                        addItems = if (shouldShowDigitalChestXray()) listOf(digitalChestXrayConducted) else emptyList()
                    )
                } else {
                    // No: remove conducted + children, add denial (default to Patient refused)
                    if (reasonForDenialChestXray.value.isNullOrBlank()) {
                        reasonForDenialChestXray.value = "0"
                    }
                    triggerDependants(
                        source = referredForDigitalChestXray,
                        removeItems = listOf(
                            digitalChestXrayConducted,
                            reasonNotConductedChestXray,
                            reasonNotConductedChestXrayOther,
                            digitalChestXrayResult,
                            trueNatConducted,
                            reasonNotConductedNaat,
                            reasonNotConductedNaatOther,
                            trueNatResult
                        ),
                        addItems = listOf(reasonForDenialChestXray)
                    )
                }
            }

            reasonForDenialChestXray.id -> {
                // index is ignored for CHECKBOXES (value is already updated by adapter)
                syncFieldStates()
                val addOther = isLastItemSelected(reasonForDenialChestXray, R.array.tb_reason_for_denial_xray)
                triggerDependants(
                    source = reasonForDenialChestXray,
                    removeItems = if (!addOther) listOf(reasonForDenialChestXrayOther) else emptyList(),
                    addItems = if (addOther) listOf(reasonForDenialChestXrayOther) else emptyList()
                )
            }

            digitalChestXrayConducted.id -> {
                digitalChestXrayConducted.value = if (index == 0) yesValue else noValue
                syncFieldStates()
                if (index == 0) {
                    // Conducted = Yes → show result, remove not-conducted fields
                    triggerDependants(
                        source = digitalChestXrayConducted,
                        removeItems = listOf(reasonNotConductedChestXray, reasonNotConductedChestXrayOther),
                        addItems = listOf(digitalChestXrayResult)
                    )
                } else {
                    // Conducted = No → remove result, show not-conducted reason
                    triggerDependants(
                        source = digitalChestXrayConducted,
                        removeItems = listOf(
                            digitalChestXrayResult,
                            trueNatConducted,
                            reasonNotConductedNaat,
                            reasonNotConductedNaatOther,
                            trueNatResult
                        ),
                        addItems = listOf(reasonNotConductedChestXray)
                    )
                }
            }

            reasonNotConductedChestXray.id -> {
                reasonNotConductedChestXray.value =
                    reasonNotConductedChestXray.entries?.getOrNull(index)
                syncFieldStates()
                val addOther = isLastItemSelectedDropdown(
                    reasonNotConductedChestXray, R.array.tb_reason_not_conducted_xray
                )
                triggerDependants(
                    source = reasonNotConductedChestXray,
                    removeItems = if (!addOther) listOf(reasonNotConductedChestXrayOther) else emptyList(),
                    addItems = if (addOther) listOf(reasonNotConductedChestXrayOther) else emptyList()
                )
            }

            digitalChestXrayResult.id -> {
                digitalChestXrayResult.value = digitalChestXrayResult.entries?.getOrNull(index)
                syncFieldStates()
                val addItems = mutableListOf<FormElement>()
                val removeItems = mutableListOf<FormElement>()

                // Manage sputum section: add when xray becomes positive (and not already visible),
                // remove when xray no longer positive and no other static sputum conditions met.
                val sputumShouldShow = shouldShowSputumCollected()
                val sputumInList = getIndexOfElement(referredForSputumCollection) >= 0

                if (sputumShouldShow && !sputumInList) {
                    // Xray just turned positive – set defaults and reveal sputum section
                    if (referredForSputumCollection.value.isNullOrBlank()) {
                        referredForSputumCollection.value = yesValue
                    }
                    addItems.add(referredForSputumCollection)
                    if (isYes(referredForSputumCollection)) {
                        if (sputumSampleSubmittedAt.value.isNullOrBlank()) {
                            sputumSampleSubmittedAt.value = sputumSampleSubmittedAt.entries?.firstOrNull()
                        }
                        addItems.add(sputumSampleSubmittedAt)
                    } else if (isNo(referredForSputumCollection)) {
                        if (reasonForDenialSputum.value.isNullOrBlank()) {
                            reasonForDenialSputum.value = "0"
                        }
                        addItems.add(reasonForDenialSputum)
                        if (isLastItemSelected(reasonForDenialSputum, R.array.tb_reason_for_denial_sputum)) {
                            addItems.add(reasonForDenialSputumOther)
                        }
                    }
                } else if (!sputumShouldShow && sputumInList) {
                    // Xray result no longer positive and no other static conditions – hide sputum section
                    removeItems.addAll(listOf(
                        referredForSputumCollection,
                        reasonForDenialSputum,
                        reasonForDenialSputumOther,
                        sputumSampleSubmittedAt
                    ))
                }

                // Manage NAAT
                if (shouldShowTrueNatConducted()) {
                    val naatInList = getIndexOfElement(trueNatConducted) >= 0
                    if (!naatInList) {
                        addItems.add(trueNatConducted)
                        if (isYes(trueNatConducted)) {
                            addItems.add(trueNatResult)
                        } else if (!trueNatConducted.value.isNullOrBlank()) {
                            addItems.add(reasonNotConductedNaat)
                            if (isLastItemSelectedDropdown(reasonNotConductedNaat, R.array.tb_reason_not_conducted_naat)) {
                                addItems.add(reasonNotConductedNaatOther)
                            }
                        }
                    }
                } else {
                    removeItems.addAll(
                        listOf(trueNatConducted, reasonNotConductedNaat, reasonNotConductedNaatOther, trueNatResult)
                    )
                }
                triggerDependants(
                    source = digitalChestXrayResult,
                    removeItems = removeItems,
                    addItems = addItems
                )
            }

            referredForSputumCollection.id -> {
                referredForSputumCollection.value = if (index == 0) yesValue else noValue
                syncFieldStates()
                if (index == 0) {
                    // Yes: show submitted-at (default to TB Screening Camp if blank), remove denial
                    if (sputumSampleSubmittedAt.value.isNullOrBlank()) {
                        sputumSampleSubmittedAt.value = sputumSampleSubmittedAt.entries?.firstOrNull()
                    }
                    triggerDependants(
                        source = referredForSputumCollection,
                        removeItems = listOf(reasonForDenialSputum, reasonForDenialSputumOther),
                        addItems = listOf(sputumSampleSubmittedAt)
                    )
                } else {
                    // No: show denial (default to Patient refused if blank), conditionally remove NAAT
                    if (reasonForDenialSputum.value.isNullOrBlank()) {
                        reasonForDenialSputum.value = "0"
                    }
                    // Keep NAAT visible if xray positive, historyTB, antiTBDrugs, or pregnant still apply
                    val keepNaat = shouldShowTrueNatConducted()
                    triggerDependants(
                        source = referredForSputumCollection,
                        removeItems = buildList {
                            add(sputumSampleSubmittedAt)
                            if (!keepNaat) addAll(listOf(
                                trueNatConducted,
                                reasonNotConductedNaat,
                                reasonNotConductedNaatOther,
                                trueNatResult
                            ))
                        },
                        addItems = listOf(reasonForDenialSputum)
                    )
                }
            }

            reasonForDenialSputum.id -> {
                syncFieldStates()
                val addOther = isLastItemSelected(reasonForDenialSputum, R.array.tb_reason_for_denial_sputum)
                triggerDependants(
                    source = reasonForDenialSputum,
                    removeItems = if (!addOther) listOf(reasonForDenialSputumOther) else emptyList(),
                    addItems = if (addOther) listOf(reasonForDenialSputumOther) else emptyList()
                )
            }

            sputumSampleSubmittedAt.id -> {
                sputumSampleSubmittedAt.value =
                    sputumSampleSubmittedAt.entries?.getOrNull(index)
                syncFieldStates()
                // Show TrueNAT if applicable after sputum submission
                val addItems = mutableListOf<FormElement>()
                val removeItems = mutableListOf<FormElement>()
                if (shouldShowTrueNatConducted()) {
                    addItems.add(trueNatConducted)
                } else {
                    removeItems.addAll(
                        listOf(trueNatConducted, reasonNotConductedNaat, reasonNotConductedNaatOther, trueNatResult)
                    )
                }
                triggerDependants(
                    source = sputumSampleSubmittedAt,
                    removeItems = removeItems,
                    addItems = addItems
                )
            }

            trueNatConducted.id -> {
                trueNatConducted.value = if (index == 0) yesValue else noValue
                syncFieldStates()
                if (index == 0) {
                    triggerDependants(
                        source = trueNatConducted,
                        removeItems = listOf(reasonNotConductedNaat, reasonNotConductedNaatOther),
                        addItems = listOf(trueNatResult)
                    )
                } else {
                    triggerDependants(
                        source = trueNatConducted,
                        removeItems = listOf(trueNatResult),
                        addItems = listOf(reasonNotConductedNaat)
                    )
                }
            }

            reasonNotConductedNaat.id -> {
                reasonNotConductedNaat.value =
                    reasonNotConductedNaat.entries?.getOrNull(index)
                syncFieldStates()
                val addOther = isLastItemSelectedDropdown(
                    reasonNotConductedNaat, R.array.tb_reason_not_conducted_naat
                )
                triggerDependants(
                    source = reasonNotConductedNaat,
                    removeItems = if (!addOther) listOf(reasonNotConductedNaatOther) else emptyList(),
                    addItems = if (addOther) listOf(reasonNotConductedNaatOther) else emptyList()
                )
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

    // ── Save ──────────────────────────────────────────────────────────────────

    override fun mapValues(cacheModel: FormDataModel, pageNumber: Int) {
        (cacheModel as TBDiagnosticsCache).let { form ->
            // Digital Chest X-Ray
            form.isReferredForDigitalChestXray = isYes(referredForDigitalChestXray)
            form.reasonForDenialChestXray =
                if (!isYes(referredForDigitalChestXray))
                    indexPipeToEnglishPipe(reasonForDenialChestXray, R.array.tb_reason_for_denial_xray)
                else null
            form.reasonForDenialChestXrayOther =
                if (!isYes(referredForDigitalChestXray))
                    reasonForDenialChestXrayOther.value?.takeIf { it.isNotBlank() }
                else null
            form.isChestXRayDone =
                if (shouldShowDigitalChestXray()) isYes(digitalChestXrayConducted) else null
            form.reasonNotConductedChestXray =
                if (shouldShowDigitalChestXray() && !isYes(digitalChestXrayConducted))
                    getEnglishValueInArray(R.array.tb_reason_not_conducted_xray, reasonNotConductedChestXray.value)
                else null
            form.reasonNotConductedChestXrayOther =
                if (shouldShowDigitalChestXray() && !isYes(digitalChestXrayConducted))
                    reasonNotConductedChestXrayOther.value?.takeIf { it.isNotBlank() }
                else null
            form.chestXRayResult =
                if (isYes(digitalChestXrayConducted))
                    getEnglishValueInArray(R.array.tb_test_result, digitalChestXrayResult.value)
                else null

            // Sputum Collection
            form.isSputumCollected =
                if (shouldShowSputumCollected()) isYes(referredForSputumCollection) else null
            form.reasonForDenialSputum =
                if (shouldShowSputumCollected() && !isYes(referredForSputumCollection))
                    indexPipeToEnglishPipe(reasonForDenialSputum, R.array.tb_reason_for_denial_sputum)
                else null
            form.reasonForDenialSputumOther =
                if (shouldShowSputumCollected() && !isYes(referredForSputumCollection))
                    reasonForDenialSputumOther.value?.takeIf { it.isNotBlank() }
                else null
            form.sputumSubmittedAt =
                if (shouldShowSputumCollected() && isYes(referredForSputumCollection))
                    getEnglishValueInArray(R.array.tb_diagnostics_sputum_submitted_at, sputumSampleSubmittedAt.value)
                else null

            // TrueNAT
            form.isNaatConducted =
                if (shouldShowTrueNatConducted()) isYes(trueNatConducted) else null
            form.reasonNotConductedNaat =
                if (shouldShowTrueNatConducted() && !isYes(trueNatConducted))
                    getEnglishValueInArray(R.array.tb_reason_not_conducted_naat, reasonNotConductedNaat.value)
                else null
            form.reasonNotConductedNaatOther =
                if (shouldShowTrueNatConducted() && !isYes(trueNatConducted))
                    reasonNotConductedNaatOther.value?.takeIf { it.isNotBlank() }
                else null
            form.naatResult =
                if (isYes(trueNatConducted))
                    getEnglishValueInArray(R.array.tb_test_result, trueNatResult.value)
                else null

            // Liquid Culture
            form.isLiquidCultureConducted =
                if (shouldShowLiquidCultureConducted()) isYes(liquidCultureConducted) else null
            form.recommendedForLiquidCultureTest =
                if (shouldShowLiquidCultureConducted()) isYes(liquidCultureConducted) else null
            form.liquidCultureResult =
                if (isYes(liquidCultureConducted))
                    getEnglishValueInArray(R.array.tb_test_result, liquidCultureResult.value)
                else null

            val isConfirmed = isPositive(form.chestXRayResult) ||
                isPositive(form.naatResult) ||
                isPositive(form.liquidCultureResult)
            form.isTBConfirmed = isConfirmed
            form.isConfirmed = isConfirmed
        }
    }

    // ── Submit visibility ─────────────────────────────────────────────────────

    fun shouldShowSubmit(): Boolean {
        if (!referralMode) return true
        return listOf(
            shouldShowDigitalChestXray() && !lockDigitalChestXray,
            shouldShowTrueNatConducted() && !lockTrueNat,
            shouldShowLiquidCultureConducted() && !lockLiquidCulture
        ).any { it }
    }

    // ── Form list builder ─────────────────────────────────────────────────────

    private fun buildFormList(): List<FormElement> = buildList {
        add(nikshayId)

        // Digital Chest X-Ray section
        add(referredForDigitalChestXray)
        if (isYes(referredForDigitalChestXray)) {
            if (shouldShowDigitalChestXray()) {
                add(digitalChestXrayConducted)
                if (isYes(digitalChestXrayConducted)) {
                    add(digitalChestXrayResult)
                } else if (!digitalChestXrayConducted.value.isNullOrBlank()) {
                    add(reasonNotConductedChestXray)
                    if (isLastItemSelectedDropdown(reasonNotConductedChestXray, R.array.tb_reason_not_conducted_xray)) {
                        add(reasonNotConductedChestXrayOther)
                    }
                }
            }
        } else if (isNo(referredForDigitalChestXray)) {
            add(reasonForDenialChestXray)
            if (isLastItemSelected(reasonForDenialChestXray, R.array.tb_reason_for_denial_xray)) {
                add(reasonForDenialChestXrayOther)
            }
        }

        // Sputum Collection section
        if (shouldShowSputumCollected()) {
            add(referredForSputumCollection)
            if (isYes(referredForSputumCollection)) {
                add(sputumSampleSubmittedAt)
            } else if (isNo(referredForSputumCollection)) {
                add(reasonForDenialSputum)
                if (isLastItemSelected(reasonForDenialSputum, R.array.tb_reason_for_denial_sputum)) {
                    add(reasonForDenialSputumOther)
                }
            }
        }

        // TrueNAT section
        if (shouldShowTrueNatConducted()) {
            add(trueNatConducted)
            if (isYes(trueNatConducted)) {
                add(trueNatResult)
            } else if (!trueNatConducted.value.isNullOrBlank()) {
                add(reasonNotConductedNaat)
                if (isLastItemSelectedDropdown(reasonNotConductedNaat, R.array.tb_reason_not_conducted_naat)) {
                    add(reasonNotConductedNaatOther)
                }
            }
        }

        // Liquid Culture section
        if (shouldShowLiquidCultureConducted()) {
            add(liquidCultureConducted)
            if (isYes(liquidCultureConducted)) {
                add(liquidCultureResult)
            }
        }
    }

    // ── Referral locks (view-only mode) ───────────────────────────────────────

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

    // ── Field state sync ──────────────────────────────────────────────────────

    private fun syncFieldStates() {
        // Referral for X-Ray — always editable (unless referralMode lock)
        referredForDigitalChestXray.isEnabled = !lockDigitalChestXray
        referredForDigitalChestXray.required = true

        // Denial reason for X-Ray
        val xrayReferred = isYes(referredForDigitalChestXray)
        val xrayDenied = isNo(referredForDigitalChestXray)
        reasonForDenialChestXray.isEnabled = xrayDenied && !lockDigitalChestXray
        reasonForDenialChestXrayOther.isEnabled = xrayDenied && !lockDigitalChestXray

        // Conducted
        digitalChestXrayConducted.isEnabled = shouldShowDigitalChestXray() && !lockDigitalChestXray
        digitalChestXrayConducted.required = shouldShowDigitalChestXray() && !lockDigitalChestXray
        if (!shouldShowDigitalChestXray()) resetField(digitalChestXrayConducted)

        // Not-conducted reason for X-Ray
        val xrayConductedNo = xrayReferred && !isYes(digitalChestXrayConducted) &&
            !digitalChestXrayConducted.value.isNullOrBlank()
        reasonNotConductedChestXray.isEnabled = xrayConductedNo && !lockDigitalChestXray
        reasonNotConductedChestXrayOther.isEnabled = xrayConductedNo && !lockDigitalChestXray

        // X-Ray result
        digitalChestXrayResult.isEnabled =
            shouldShowDigitalChestXray() && isYes(digitalChestXrayConducted) && !lockDigitalChestXray
        if (!shouldShowDigitalChestXray() || !isYes(digitalChestXrayConducted)) {
            resetField(digitalChestXrayResult)
        }

        // Sputum section
        referredForSputumCollection.isEnabled = shouldShowSputumCollected() && !referralMode
        referredForSputumCollection.required = shouldShowSputumCollected() && !referralMode
        if (!shouldShowSputumCollected()) resetField(referredForSputumCollection)

        val sputumReferred = isYes(referredForSputumCollection)
        val sputumDenied = isNo(referredForSputumCollection)
        sputumSampleSubmittedAt.isEnabled = sputumReferred && !referralMode
        reasonForDenialSputum.isEnabled = sputumDenied && !referralMode
        reasonForDenialSputumOther.isEnabled = sputumDenied && !referralMode

        // TrueNAT
        trueNatConducted.isEnabled = shouldShowTrueNatConducted() && !lockTrueNat
        trueNatConducted.required = shouldShowTrueNatConducted() && !lockTrueNat
        if (!shouldShowTrueNatConducted()) resetField(trueNatConducted)

        val naatConductedNo = !trueNatConducted.value.isNullOrBlank() && !isYes(trueNatConducted)
        reasonNotConductedNaat.isEnabled = naatConductedNo && !lockTrueNat
        reasonNotConductedNaatOther.isEnabled = naatConductedNo && !lockTrueNat

        trueNatResult.isEnabled =
            shouldShowTrueNatConducted() && isYes(trueNatConducted) && !lockTrueNat
        if (!shouldShowTrueNatConducted() || !isYes(trueNatConducted)) {
            resetField(trueNatResult)
        }

        // NikshayId
        nikshayId.isEnabled = false

        // Liquid Culture
        liquidCultureConducted.isEnabled =
            shouldShowLiquidCultureConducted() && !lockLiquidCulture
        liquidCultureConducted.required =
            shouldShowLiquidCultureConducted() && !lockLiquidCulture
        if (!shouldShowLiquidCultureConducted()) resetField(liquidCultureConducted)

        liquidCultureResult.isEnabled =
            shouldShowLiquidCultureConducted() && isYes(liquidCultureConducted) && !lockLiquidCulture
        if (!shouldShowLiquidCultureConducted() || !isYes(liquidCultureConducted)) {
            resetField(liquidCultureResult)
        }
    }

    // ── Show conditions ───────────────────────────────────────────────────────

    /** X-Ray conducted question is shown when referred=Yes and not pregnant */
    private fun shouldShowDigitalChestXray(): Boolean =
        isYes(referredForDigitalChestXray) && !isPregnant()

    /** Sputum section shown when patient has history/antiTB drugs/pregnant or X-Ray is positive */
    private fun shouldShowSputumCollected(): Boolean =
        screeningCache?.historyOfTb == true ||
            isPregnant() ||
            screeningCache?.takingAntiTBDrugs == true ||
            isPositive(getEnglishValueInArray(R.array.tb_test_result, digitalChestXrayResult.value))

    /** TrueNAT shown when xray positive, sputum referred, history of TB, anti-TB drugs, or pregnant */
    private fun shouldShowTrueNatConducted(): Boolean =
        isPositive(getEnglishValueInArray(R.array.tb_test_result, digitalChestXrayResult.value)) ||
            isYes(referredForSputumCollection) ||
            screeningCache?.takingAntiTBDrugs == true ||
            screeningCache?.historyOfTb == true ||
            isPregnant()

    /** Liquid Culture shown when both history of TB AND taking anti-TB drugs */
    private fun shouldShowLiquidCultureConducted(): Boolean =
        screeningCache?.historyOfTb == true && screeningCache?.takingAntiTBDrugs == true

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun isYes(formElement: FormElement): Boolean = formElement.value == yesValue
    private fun isNo(formElement: FormElement): Boolean = formElement.value == noValue

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

    private fun conductedFromSaved(savedValue: Boolean?, shouldShow: Boolean): String {
        if (!shouldShow) return ""
        return boolToYesNo(savedValue)
    }

    private fun resetField(formElement: FormElement) {
        formElement.value = null
        formElement.errorText = null
    }

    /**
     * Check if the last item (= "Others") is selected in a CHECKBOXES field.
     * CHECKBOXES value is stored as pipe-separated 0-based indexes, e.g. "0|3|14".
     */
    private fun isLastItemSelected(field: FormElement, arrayId: Int): Boolean {
        val lastIndex = resources.getStringArray(arrayId).size - 1
        return field.value?.split("|")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.contains(lastIndex) == true
    }

    /**
     * Check if the last item (= "Others") is selected in a DROPDOWN field.
     * DROPDOWN value is the localized display string.
     */
    private fun isLastItemSelectedDropdown(field: FormElement, arrayId: Int): Boolean {
        val entries = resources.getStringArray(arrayId)
        return field.value != null && field.value == entries.lastOrNull()
    }

    /**
     * Convert pipe-separated English values (stored in DB) → pipe-separated 0-based indexes
     * (needed for CHECKBOXES display).
     */
    private fun englishPipeToIndexPipe(value: String?, arrayId: Int): String? {
        if (value.isNullOrBlank()) return null
        val englishEntries = englishResources.getStringArray(arrayId)
        val indexes = value.split("|")
            .mapNotNull { v -> englishEntries.indexOf(v.trim()).takeIf { it >= 0 } }
        return if (indexes.isEmpty()) null else indexes.joinToString("|")
    }

    /**
     * Convert pipe-separated 0-based indexes (CHECKBOXES field value) → pipe-separated English
     * values (for DB storage).
     */
    private fun indexPipeToEnglishPipe(field: FormElement, arrayId: Int): String? {
        val value = field.value ?: return null
        val englishEntries = englishResources.getStringArray(arrayId)
        val values = value.split("|")
            .mapNotNull { i -> i.trim().toIntOrNull()?.let { englishEntries.getOrNull(it) } }
        return if (values.isEmpty()) null else values.joinToString("|")
    }

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
}
