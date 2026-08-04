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
import org.piramalswasthya.stoptb.model.VitalCache

class TBSuspectedQuickDataset(
    context: Context,
    currentLanguage: Languages
) : Dataset(context, currentLanguage) {

    private val preferenceDao = org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao(context)
    private val yesNoEntries get() = resources.getStringArray(R.array.yes_no)
    val yesValue get() = yesNoEntries[0]
    val noValue get() = yesNoEntries[1]

    private var benCache: BenRegCache? = null
    private var screeningCache: TBScreeningCache? = null
    private var vitalCache: VitalCache? = null
    private var referralMode = false
    private var referralType = 0
    private var diagnosticsCache: TBDiagnosticsCache? = null

    private var lockDigitalChestXray = false
    private var lockTrueNat = false
    private var lockRif = false
    private var lockLiquidCulture = false
    private val nikshayIdUnavailable = "N/A"

    // ── Always visible ────────────────────────────────────────────────────────

    private val dateOfVisit = FormElement(
        id = 19,
        inputType = InputType.DATE_PICKER,
        title = resources.getString(R.string.tracking_date),
        required = true,
        max = System.currentTimeMillis(),
        hasDependants = true
    )

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

    val digitalChestXrayResult = FormElement(
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

    val trueNatConducted = FormElement(
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
        arrayId = R.array.tb_truenat_mtb_result,
        entries = resources.getStringArray(R.array.tb_truenat_mtb_result),
        required = false,
        hasDependants = true
    )

    val trueNatRifResult = FormElement(
        id = 20,
        inputType = InputType.RADIO,
        title = "TrueNat Rif Test Result",
        arrayId = R.array.tb_truenat_rif_result,
        entries = resources.getStringArray(R.array.tb_truenat_rif_result),
        required = false
    )

    val rifConducted = FormElement(
        id = 21,
        inputType = InputType.RADIO,
        title = "Is Truenat Rif Test Conducted?",
        entries = yesNoEntries,
        required = true,
        hasDependants = true
    )

    val reasonNotConductedRif = FormElement(
        id = 22,
        inputType = InputType.DROPDOWN,
        title = "Reason for Rif Test not conducted",
        arrayId = R.array.tb_reason_not_conducted_naat,
        entries = resources.getStringArray(R.array.tb_reason_not_conducted_naat),
        required = false,
        hasDependants = true
    )

    val reasonNotConductedRifOther = FormElement(
        id = 23,
        inputType = InputType.EDIT_TEXT,
        title = "Reason for Rif Test not conducted other",
        required = false,
        etMaxLength = 250
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
        vital: VitalCache? = null,
        referralMode: Boolean = false,
        referralType: Int = 0
    ) {
        benCache = ben
        screeningCache = screening
        vitalCache = vital
        diagnosticsCache = saved
        this.referralMode = referralMode
        this.referralType = referralType

        // Date of visit — same min/default logic as TBScreeningDataset
        dateOfVisit.value = saved?.visitDate?.takeIf { it > 0 }
            ?.let { getDateFromLong(it) }
            ?: getDateFromLong(System.currentTimeMillis())
        dateOfVisit.isEnabled = false

        // NikshayId
        nikshayId.value = ben?.nikshayId?.takeIf { it.isNotBlank() }
            ?: saved?.nikshayId?.takeIf { it.isNotBlank() }
            ?: nikshayIdUnavailable

        // ── Digital Chest X-Ray ────────────────────────────────────────────
        val isXrayDeviceIntegrated = preferenceDao.getXrayIntegrated()
        val isTruenatDeviceIntegrated = preferenceDao.getTruenatIntegrated()

        val isXrayReferred = referralType == 6 || saved?.isReferredForDigitalChestXray == true || saved?.isChestXRayDone == true || !saved?.chestXRayResult.isNullOrBlank()
        val isXrayDone = saved?.isChestXRayDone == true || !saved?.chestXRayResult.isNullOrBlank()

        referredForDigitalChestXray.value = boolToYesNo(if (isXrayReferred) true else saved?.isReferredForDigitalChestXray)
        reasonForDenialChestXray.value = englishPipeToIndexPipe(
            saved?.reasonForDenialChestXray, R.array.tb_reason_for_denial_xray
        )
        reasonForDenialChestXrayOther.value = saved?.reasonForDenialChestXrayOther
        digitalChestXrayConducted.value = boolToYesNo(if (isXrayDone) true else saved?.isChestXRayDone)
        reasonNotConductedChestXray.value = getLocalValueInArray(
            R.array.tb_reason_not_conducted_xray, saved?.reasonNotConductedChestXray
        )
        reasonNotConductedChestXrayOther.value = saved?.reasonNotConductedChestXrayOther
        
        if (isXrayDeviceIntegrated && referralType == 6 && isYes(digitalChestXrayConducted)) {
            digitalChestXrayResult.inputType = InputType.TEXT_VIEW
            digitalChestXrayResult.value = if (saved?.chestXRayResult.isNullOrBlank()) "Waiting for Result" else (getLocalValueInArray(R.array.tb_test_result, saved?.chestXRayResult) ?: saved?.chestXRayResult)
        } else {
            digitalChestXrayResult.inputType = InputType.RADIO
            digitalChestXrayResult.value = when {
                saved?.chestXRayResult.isNullOrBlank() -> null
                isPositive(saved?.chestXRayResult) -> digitalChestXrayResult.entries?.getOrNull(0)
                else -> digitalChestXrayResult.entries?.getOrNull(1)
            }
        }

        // ── Sputum Collection ──────────────────────────────────────────────
        referredForSputumCollection.value = boolToYesNo(if (isTruenatDeviceIntegrated && referralType == 7) true else saved?.isSputumCollected)
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

        if (isTruenatDeviceIntegrated && referralType == 7 && isYes(trueNatConducted)) {
            trueNatResult.inputType = InputType.TEXT_VIEW
            trueNatResult.value = if (saved?.naatResult.isNullOrBlank()) "Waiting for Result" else getLocalValueInArray(R.array.tb_truenat_mtb_result, saved?.naatResult)
        } else {
            trueNatResult.inputType = InputType.RADIO
            trueNatResult.value = getLocalValueInArray(R.array.tb_truenat_mtb_result, saved?.naatResult)
        }

        // ── RIF Conducted & Results ───────────────────────────────────────
        val isRifConductedVal = saved?.rifOrderStatus.equals("COMPLETED", ignoreCase = true) ||
                saved?.rifOrderStatus.equals("IN_PROGRESS", ignoreCase = true) ||
                saved?.rifOrderStatus.equals("AWAITING_PROVIDER_RESULT", ignoreCase = true)
        rifConducted.value = boolToYesNo(if (isRifConductedVal) true else if (saved?.rifOrderStatus.equals("NOT_CONDUCTED", ignoreCase = true)) false else null)
        reasonNotConductedRif.value = getLocalValueInArray(
            R.array.tb_reason_not_conducted_naat, preferenceDao.getRifNotConductedReason(ben?.beneficiaryId ?: saved?.benId ?: 0L)
        )
        reasonNotConductedRifOther.value = saved?.reasonNotConductedChestXrayOther // Wait, this doesn't matter since we don't have separate table column, but let's load it if we need to.

        if (isTruenatDeviceIntegrated && referralType == 7 && isYes(rifConducted)) {
            trueNatRifResult.inputType = InputType.TEXT_VIEW
            trueNatRifResult.value = if (saved?.trueNatRifResult.isNullOrBlank()) "Waiting for Result" else getLocalValueInArray(R.array.tb_truenat_rif_result, saved?.trueNatRifResult)
        } else {
            trueNatRifResult.inputType = InputType.RADIO
            trueNatRifResult.value = getLocalValueInArray(R.array.tb_truenat_rif_result, saved?.trueNatRifResult)
        }

        // ── Liquid Culture ────────────────────────────────────────────────
        liquidCultureConducted.value = conductedFromSaved(
            savedValue = saved?.recommendedForLiquidCultureTest ?: saved?.isLiquidCultureConducted,
            shouldShow = shouldShowLiquidCultureConducted()
        )
        liquidCultureResult.value = getLocalValueInArray(
            R.array.tb_test_result, saved?.liquidCultureResult
        )

        // ── Apply defaults for new/blank forms ────────────────────────────
        if (!isPregnant() && referredForDigitalChestXray.value.isNullOrBlank()) {
            referredForDigitalChestXray.value = yesValue
        }
        if (!isPregnant() && isNo(referredForDigitalChestXray) && reasonForDenialChestXray.value.isNullOrBlank()) {
            reasonForDenialChestXray.value = "0"
        }
        val sputumVisible = referralType == 7 || shouldShowSputumCollected()
        if (sputumVisible && referredForSputumCollection.value.isNullOrBlank()) {
            referredForSputumCollection.value = yesValue
        }
        if (sputumVisible && isYes(referredForSputumCollection) &&
            sputumSampleSubmittedAt.value.isNullOrBlank()
        ) {
            sputumSampleSubmittedAt.value = sputumSampleSubmittedAt.entries?.firstOrNull()
        }
        if (sputumVisible && isNo(referredForSputumCollection) &&
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
                val sputumShouldShow = referralType != 6 && shouldShowSputumCollected()
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
                if (referralType != 6 && shouldShowTrueNatConducted()) {
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
                    sputumSampleSubmittedAt.isEnabled = !referralMode  // explicitly enable when first shown
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
                    val addItems = mutableListOf<FormElement>(trueNatResult)
                    val isTruenatDevIntegrated = preferenceDao.getTruenatIntegrated()
                    if (isTruenatDevIntegrated) {
                        val isRifCompleted = diagnosticsCache?.rifOrderStatus.equals("COMPLETED", ignoreCase = true)
                        if (isMtbDetected() && isRifCompleted) {
                            addItems.add(trueNatRifResult)
                        }
                    } else {
                        if (isMtbDetected()) {
                            addItems.add(trueNatRifResult)
                        }
                    }
                    triggerDependants(
                        source = trueNatConducted,
                        removeItems = listOf(reasonNotConductedNaat, reasonNotConductedNaatOther),
                        addItems = addItems
                    )
                } else {
                    resetField(trueNatResult)
                    resetField(trueNatRifResult)
                    triggerDependants(
                        source = trueNatConducted,
                        removeItems = listOf(trueNatResult, trueNatRifResult),
                        addItems = listOf(reasonNotConductedNaat)
                    )
                }
            }

            trueNatResult.id -> {
                trueNatResult.value = trueNatResult.entries?.getOrNull(index)
                syncFieldStates()
                val isMtb = isMtbDetected()
                val isTruenatDevIntegrated = preferenceDao.getTruenatIntegrated()
                val shouldShowRif = if (isTruenatDevIntegrated) {
                    val isRifCompleted = diagnosticsCache?.rifOrderStatus.equals("COMPLETED", ignoreCase = true)
                    isMtb && isRifCompleted
                } else {
                    isMtb
                }
                val addItems = if (shouldShowRif) listOf(trueNatRifResult) else emptyList()
                val removeItems = if (!shouldShowRif) {
                    resetField(trueNatRifResult)
                    listOf(trueNatRifResult)
                } else emptyList()
                triggerDependants(
                    source = trueNatResult,
                    removeItems = removeItems,
                    addItems = addItems
                )
            }

            trueNatRifResult.id -> {
                trueNatRifResult.value = trueNatRifResult.entries?.getOrNull(index)
                syncFieldStates()
                0
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

            rifConducted.id -> {
                rifConducted.value = if (index == 0) yesValue else noValue
                syncFieldStates()
                if (index == 0) {
                    triggerDependants(
                        source = rifConducted,
                        removeItems = listOf(reasonNotConductedRif, reasonNotConductedRifOther),
                        addItems = listOf(trueNatRifResult)
                    )
                } else {
                    resetField(trueNatRifResult)
                    triggerDependants(
                        source = rifConducted,
                        removeItems = listOf(trueNatRifResult),
                        addItems = listOf(reasonNotConductedRif)
                    )
                }
            }

            reasonNotConductedRif.id -> {
                reasonNotConductedRif.value = reasonNotConductedRif.entries?.getOrNull(index)
                syncFieldStates()
                val addOther = isLastItemSelectedDropdown(reasonNotConductedRif, R.array.tb_reason_not_conducted_naat)
                triggerDependants(
                    source = reasonNotConductedRif,
                    removeItems = if (!addOther) listOf(reasonNotConductedRifOther) else emptyList(),
                    addItems = if (addOther) listOf(reasonNotConductedRifOther) else emptyList()
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
            // Always record the actual moment of submission — never editable,
            // never derived from a possibly-stale displayed value.
            form.visitDate = System.currentTimeMillis()
            // Digital Chest X-Ray (referralType == 0 or 6)
            if (referralType == 0 || referralType == 6) {
                form.isReferredForDigitalChestXray =
                    if (!isPregnant()) isYes(referredForDigitalChestXray) else null
                form.reasonForDenialChestXray =
                    if (!isPregnant() && !isYes(referredForDigitalChestXray))
                        indexPipeToEnglishPipe(reasonForDenialChestXray, R.array.tb_reason_for_denial_xray)
                    else null
                form.reasonForDenialChestXrayOther =
                    if (!isPregnant() && !isYes(referredForDigitalChestXray))
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
            }

            // Sputum Collection & TrueNAT (referralType == 0 or 7)
            if (referralType == 0 || referralType == 7) {
                val sputumVisible = referralType == 7 || shouldShowSputumCollected()
                form.isSputumCollected =
                    if (sputumVisible) isYes(referredForSputumCollection) else null
                form.reasonForDenialSputum =
                    if (sputumVisible && !isYes(referredForSputumCollection))
                        indexPipeToEnglishPipe(reasonForDenialSputum, R.array.tb_reason_for_denial_sputum)
                    else null
                form.reasonForDenialSputumOther =
                    if (sputumVisible && !isYes(referredForSputumCollection))
                        reasonForDenialSputumOther.value?.takeIf { it.isNotBlank() }
                    else null
                form.sputumSubmittedAt =
                    if (sputumVisible && isYes(referredForSputumCollection))
                        getEnglishValueInArray(R.array.tb_diagnostics_sputum_submitted_at, sputumSampleSubmittedAt.value)
                    else null

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
                        getEnglishValueInArray(R.array.tb_truenat_mtb_result, trueNatResult.value)
                    else null
                form.trueNatRifResult =
                    if (shouldShowTrueNatConducted() && isYes(trueNatConducted) && isMtbDetected())
                        getEnglishValueInArray(R.array.tb_truenat_rif_result, trueNatRifResult.value)
                    else null

                val benIdVal = benCache?.beneficiaryId ?: form.benId ?: 0L
                if (benIdVal > 0L) {
                    val rNotConductedVal = getEnglishValueInArray(R.array.tb_reason_not_conducted_naat, reasonNotConductedRif.value)
                    preferenceDao.setRifNotConductedReason(benIdVal, rNotConductedVal ?: "")
                }
            }

            // Liquid Culture (referralType == 0 or 8)
            if (referralType == 0 || referralType == 8) {
                form.isLiquidCultureConducted =
                    if (shouldShowLiquidCultureConducted()) isYes(liquidCultureConducted) else null
                form.recommendedForLiquidCultureTest =
                    if (shouldShowLiquidCultureConducted()) isYes(liquidCultureConducted) else null
                form.liquidCultureResult =
                    if (isYes(liquidCultureConducted))
                        getEnglishValueInArray(R.array.tb_test_result, liquidCultureResult.value)
                    else null
            }

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

    fun getIndexOfDate(): Int = listFlow.value.indexOf(dateOfVisit)

    // ── Form list builder ─────────────────────────────────────────────────────

    private fun buildFormList(): List<FormElement> = buildList {
        add(dateOfVisit)
        add(nikshayId)

        if (referralType == 0 || referralType == 6) {
            // Digital Chest X-Ray section — hidden entirely for pregnant women
            if (!isPregnant()) {
                add(referredForDigitalChestXray)
                if (isYes(referredForDigitalChestXray)) {
                    if (referralMode || referralType == 0 || referralType == 6) {
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
            }
        }

        if (referralType == 0 || referralType == 7) {
            // Sputum & TrueNat Collection section
            if (referralType == 7 || referralType == 0 || shouldShowSputumCollected()) {
                add(referredForSputumCollection)
                if (isYes(referredForSputumCollection)) {
                    add(sputumSampleSubmittedAt)
                    if (referralMode || referralType == 0 || referralType == 7) {
                        add(trueNatConducted)
                        if (isYes(trueNatConducted)) {
                            add(trueNatResult)
                            
                            val isRifCompleted = diagnosticsCache?.rifOrderStatus.equals("COMPLETED", ignoreCase = true)
                            val showRif = if (referralMode) {
                                isRifCompleted && isMtbDetected()
                            } else {
                                !diagnosticsCache?.rifOrderId.isNullOrBlank() && isMtbDetected()
                            }
                            if (showRif) {
                                add(rifConducted)
                                if (isYes(rifConducted)) {
                                    add(trueNatRifResult)
                                } else if (isNo(rifConducted)) {
                                    add(reasonNotConductedRif)
                                    if (isLastItemSelectedDropdown(reasonNotConductedRif, R.array.tb_reason_not_conducted_naat)) {
                                        add(reasonNotConductedRifOther)
                                    }
                                }
                            }
                        } else if (!trueNatConducted.value.isNullOrBlank()) {
                            add(reasonNotConductedNaat)
                            if (isLastItemSelectedDropdown(reasonNotConductedNaat, R.array.tb_reason_not_conducted_naat)) {
                                add(reasonNotConductedNaatOther)
                            }
                        }
                    }
                } else if (isNo(referredForSputumCollection)) {
                    add(reasonForDenialSputum)
                    if (isLastItemSelected(reasonForDenialSputum, R.array.tb_reason_for_denial_sputum)) {
                        add(reasonForDenialSputumOther)
                    }
                }
            }
        }

        if (referralType == 0 || referralType == 8) {
            // Liquid Culture section
            if (referralType == 0 || shouldShowLiquidCultureConducted()) {
                add(liquidCultureConducted)
                if (isYes(liquidCultureConducted)) {
                    add(liquidCultureResult)
                }
            }
        }
    }

    // ── Referral locks (view-only mode) ───────────────────────────────────────

    private fun configureReferralLocks(saved: TBDiagnosticsCache?) {
        if (saved == null) {
            lockDigitalChestXray = false
            lockTrueNat = false
            lockRif = false
            lockLiquidCulture = false
            return
        }
        lockDigitalChestXray = referralMode || !saved.chestXRayResult.isNullOrBlank()
        lockTrueNat = referralMode || !saved.naatResult.isNullOrBlank()
        lockRif = referralMode || !saved.trueNatRifResult.isNullOrBlank() || saved.rifOrderStatus.equals("COMPLETED", ignoreCase = true)
        lockLiquidCulture = referralMode || !saved.liquidCultureResult.isNullOrBlank()
    }

    // ── Field state sync ──────────────────────────────────────────────────────

    private fun syncFieldStates() {
        val isXrayDevIntegrated = preferenceDao.getXrayIntegrated()
        val isTruenatDevIntegrated = preferenceDao.getTruenatIntegrated()

        // Referral for X-Ray — not shown for pregnant women
        if (referralType == 6) {
            referredForDigitalChestXray.value = yesValue
            referredForDigitalChestXray.isEnabled = false
            referredForDigitalChestXray.required = false
        } else {
            referredForDigitalChestXray.isEnabled = !lockDigitalChestXray && !isPregnant() && !referralMode
            referredForDigitalChestXray.required = !isPregnant() && !referralMode
        }

        // Denial reason for X-Ray
        val xrayReferred = isYes(referredForDigitalChestXray)
        val xrayDenied = isNo(referredForDigitalChestXray)
        reasonForDenialChestXray.isEnabled = xrayDenied && !lockDigitalChestXray && !referralMode
        reasonForDenialChestXray.required = xrayDenied && !lockDigitalChestXray && !referralMode
        if (!xrayDenied) {
            reasonForDenialChestXray.errorText = null
        }

        reasonForDenialChestXrayOther.isEnabled =
            xrayDenied &&
                    !referralMode &&
                    isLastItemSelected(reasonForDenialChestXray, R.array.tb_reason_for_denial_xray)

        reasonForDenialChestXrayOther.required =
            reasonForDenialChestXrayOther.isEnabled

        if (!reasonForDenialChestXrayOther.isEnabled) {
            reasonForDenialChestXrayOther.errorText = null
        }

        // Conducted
        digitalChestXrayConducted.isEnabled = shouldShowDigitalChestXray() && !lockDigitalChestXray
        digitalChestXrayConducted.required = shouldShowDigitalChestXray() && !lockDigitalChestXray
        if (!shouldShowDigitalChestXray()) resetField(digitalChestXrayConducted)

        // Not-conducted reason for X-Ray
        val xrayConductedNo = xrayReferred &&
                !isYes(digitalChestXrayConducted) &&
                !digitalChestXrayConducted.value.isNullOrBlank()

        reasonNotConductedChestXray.isEnabled =
            xrayConductedNo && !lockDigitalChestXray

        reasonNotConductedChestXray.required =
            xrayConductedNo && !lockDigitalChestXray

        if (!xrayConductedNo) {
            reasonNotConductedChestXray.errorText = null
        }

        reasonNotConductedChestXrayOther.isEnabled =
            xrayConductedNo &&
                    isLastItemSelectedDropdown(
                        reasonNotConductedChestXray,
                        R.array.tb_reason_not_conducted_xray
                    )

        reasonNotConductedChestXrayOther.required =
            reasonNotConductedChestXrayOther.isEnabled

        if (!reasonNotConductedChestXrayOther.isEnabled) {
            reasonNotConductedChestXrayOther.errorText = null
        }

        // X-Ray result
        val xrayStatus = diagnosticsCache?.xrayOrderStatus
        val isXrayCompleted = xrayStatus.equals("COMPLETED", ignoreCase = true) || !diagnosticsCache?.chestXRayResult.isNullOrBlank()
        val isXrayWaiting = isXrayDevIntegrated && preferenceDao.isCampHubConnected() && referralType == 6 &&
            !xrayStatus.equals("POLLING_TIMEOUT", ignoreCase = true) &&
            !xrayStatus.equals("FAILED", ignoreCase = true) &&
            !isXrayCompleted

        if (isXrayWaiting || isXrayCompleted) {
            digitalChestXrayResult.inputType = InputType.TEXT_VIEW
            digitalChestXrayResult.isEnabled = false
            digitalChestXrayResult.required = false
            if (isYes(digitalChestXrayConducted)) {
                if (isXrayCompleted) {
                    digitalChestXrayResult.value = if (diagnosticsCache?.chestXRayResult.isNullOrBlank()) "Waiting for Result" else (getLocalValueInArray(R.array.tb_test_result, diagnosticsCache?.chestXRayResult) ?: diagnosticsCache?.chestXRayResult)
                } else {
                    if (digitalChestXrayResult.value.isNullOrBlank() || digitalChestXrayResult.value == "Waiting for Result") {
                        digitalChestXrayResult.value = "Waiting for Result"
                    }
                }
            } else {
                resetField(digitalChestXrayResult)
            }
        } else {
            digitalChestXrayResult.inputType = InputType.RADIO
            digitalChestXrayResult.isEnabled =
                shouldShowDigitalChestXray() && isYes(digitalChestXrayConducted) && !lockDigitalChestXray
            if (!shouldShowDigitalChestXray() || !isYes(digitalChestXrayConducted)) {
                resetField(digitalChestXrayResult)
            }
        }

        // Sputum section
        val sputumVisible = referralType == 7 || shouldShowSputumCollected()
        if (isTruenatDevIntegrated && referralType == 7) {
            referredForSputumCollection.value = yesValue
            referredForSputumCollection.isEnabled = false
            referredForSputumCollection.required = false
        } else {
            referredForSputumCollection.isEnabled = sputumVisible && !referralMode
            referredForSputumCollection.required = sputumVisible && !referralMode
        }
        if (!sputumVisible) resetField(referredForSputumCollection)

        val sputumReferred = isYes(referredForSputumCollection)
        val sputumDenied = isNo(referredForSputumCollection)
        sputumSampleSubmittedAt.isEnabled = !referralMode  // not editable when Submit is hidden (view mode)
        reasonForDenialSputum.isEnabled = true
        reasonForDenialSputum.required = true

        if (!sputumDenied) {
            reasonForDenialSputum.errorText = null
        }

        reasonForDenialSputumOther.isEnabled =
            sputumDenied &&
                    isLastItemSelected(
                        reasonForDenialSputum,
                        R.array.tb_reason_for_denial_sputum
                    )

        reasonForDenialSputumOther.required =
            reasonForDenialSputumOther.isEnabled

        if (!reasonForDenialSputumOther.isEnabled) {
            reasonForDenialSputumOther.errorText = null
        }

        // TrueNAT
        trueNatConducted.isEnabled = shouldShowTrueNatConducted() && !lockTrueNat
        trueNatConducted.required = shouldShowTrueNatConducted() && !lockTrueNat
        if (!shouldShowTrueNatConducted()) resetField(trueNatConducted)

        val naatConductedNo = !trueNatConducted.value.isNullOrBlank() && !isYes(trueNatConducted)
        reasonNotConductedNaat.isEnabled = naatConductedNo && !lockTrueNat
        reasonNotConductedNaat.required =
            naatConductedNo && !lockTrueNat

        if (!naatConductedNo) {
            reasonNotConductedNaat.errorText = null
        }

        reasonNotConductedNaatOther.isEnabled =
            naatConductedNo &&
                    isLastItemSelectedDropdown(
                        reasonNotConductedNaat,
                        R.array.tb_reason_not_conducted_naat
                    )

        reasonNotConductedNaatOther.required =
            reasonNotConductedNaatOther.isEnabled

        if (!reasonNotConductedNaatOther.isEnabled) {
            reasonNotConductedNaatOther.errorText = null
        }

        val mtbStatus = diagnosticsCache?.trueNatOrderStatus
        val isMtbCompleted = mtbStatus.equals("COMPLETED", ignoreCase = true) || !diagnosticsCache?.naatResult.isNullOrBlank()
        val isMtbWaiting = isTruenatDevIntegrated && preferenceDao.isCampHubConnected() && referralType == 7 &&
            !mtbStatus.equals("POLLING_TIMEOUT", ignoreCase = true) &&
            !mtbStatus.equals("FAILED", ignoreCase = true) &&
            !isMtbCompleted

        if (isMtbWaiting || isMtbCompleted) {
            trueNatResult.inputType = InputType.TEXT_VIEW
            trueNatResult.isEnabled = false
            trueNatResult.required = false
            if (isYes(trueNatConducted)) {
                if (isMtbCompleted) {
                    trueNatResult.value = if (diagnosticsCache?.naatResult.isNullOrBlank()) "Waiting for Result" else (getLocalValueInArray(R.array.tb_test_result, diagnosticsCache?.naatResult) ?: diagnosticsCache?.naatResult)
                } else {
                    if (trueNatResult.value.isNullOrBlank() || trueNatResult.value == "Waiting for Result") {
                        trueNatResult.value = "Waiting for Result"
                    }
                }
            } else {
                resetField(trueNatResult)
            }
        } else {
            trueNatResult.inputType = InputType.RADIO
            trueNatResult.isEnabled =
                shouldShowTrueNatConducted() && isYes(trueNatConducted) && !lockTrueNat
            if (!shouldShowTrueNatConducted() || !isYes(trueNatConducted)) {
                resetField(trueNatResult)
            }
        }

        // RIF Conducted & Result
        val showRif = if (referralMode) {
            diagnosticsCache?.rifOrderStatus.equals("COMPLETED", ignoreCase = true) && isMtbDetected()
        } else {
            !diagnosticsCache?.rifOrderId.isNullOrBlank() && isMtbDetected()
        }
        rifConducted.isEnabled = showRif && !lockRif
        rifConducted.required = showRif && !lockRif
        if (!showRif) {
            resetField(rifConducted)
        }

        val rifConductedNo = !rifConducted.value.isNullOrBlank() && !isYes(rifConducted)
        reasonNotConductedRif.isEnabled = rifConductedNo && !lockRif
        reasonNotConductedRif.required = rifConductedNo && !lockRif
        if (!rifConductedNo) {
            reasonNotConductedRif.errorText = null
        }

        reasonNotConductedRifOther.isEnabled = rifConductedNo && isLastItemSelectedDropdown(reasonNotConductedRif, R.array.tb_reason_not_conducted_naat)
        reasonNotConductedRifOther.required = reasonNotConductedRifOther.isEnabled
        if (!reasonNotConductedRifOther.isEnabled) {
            reasonNotConductedRifOther.errorText = null
        }

        val rifStatus = diagnosticsCache?.rifOrderStatus
        val isRifCompleted = rifStatus.equals("COMPLETED", ignoreCase = true) || !diagnosticsCache?.trueNatRifResult.isNullOrBlank()
        val isRifWaiting = isTruenatDevIntegrated && preferenceDao.isCampHubConnected() && referralType == 7 &&
            !rifStatus.equals("POLLING_TIMEOUT", ignoreCase = true) &&
            !rifStatus.equals("FAILED", ignoreCase = true) &&
            !isRifCompleted

        if (isRifWaiting || isRifCompleted) {
            trueNatRifResult.inputType = InputType.TEXT_VIEW
            trueNatRifResult.isEnabled = false
            trueNatRifResult.required = false
            val showRifResult = showRif && isYes(rifConducted)
            if (showRifResult) {
                if (isRifCompleted) {
                    trueNatRifResult.value = if (diagnosticsCache?.trueNatRifResult.isNullOrBlank()) "Waiting for Result" else (getLocalValueInArray(R.array.tb_truenat_rif_result, diagnosticsCache?.trueNatRifResult) ?: diagnosticsCache?.trueNatRifResult)
                } else {
                    if (trueNatRifResult.value.isNullOrBlank() || trueNatRifResult.value == "Waiting for Result") {
                        trueNatRifResult.value = "Waiting for Result"
                    }
                }
            } else {
                resetField(trueNatRifResult)
            }
        } else {
            trueNatRifResult.inputType = InputType.RADIO
            val showRifResult = showRif && isYes(rifConducted)
            trueNatRifResult.isEnabled = showRifResult && !lockRif
            trueNatRifResult.required = showRifResult && !lockRif
            if (!showRifResult) {
                resetField(trueNatRifResult)
            }
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
        (referralMode || referralType == 0 || referralType == 6) && isYes(referredForDigitalChestXray) && !isPregnant()

    /** Sputum section shown when patient has history/antiTB drugs/pregnant, X-Ray is positive, or any verbal symptoms are positive */
    private fun shouldShowSputumCollected(): Boolean =
        screeningCache?.historyOfTb == true ||
            isPregnant() ||
            screeningCache?.takingAntiTBDrugs == true ||
            isPositive(digitalChestXrayResult.value) ||
            screeningCache?.coughMoreThan2Weeks == true ||
            screeningCache?.bloodInSputum == true ||
            screeningCache?.feverMoreThan2Weeks == true ||
            screeningCache?.lossOfWeight == true ||
            screeningCache?.nightSweats == true ||
            screeningCache?.familySufferingFromTB == true ||
            screeningCache?.riseOfFever == true ||
            screeningCache?.lossOfAppetite == true ||
            screeningCache?.asymptomatic?.equals("NO", ignoreCase = true) == true ||
            screeningCache?.recommendedForTruenatTest == true

    /** TrueNAT shown when xray positive, sputum referred, history of TB, anti-TB drugs, or pregnant */
    private fun shouldShowTrueNatConducted(): Boolean =
        (referralMode || referralType == 0 || referralType == 7) && (
            isPositive(digitalChestXrayResult.value) ||
            isYes(referredForSputumCollection) ||
            screeningCache?.takingAntiTBDrugs == true ||
            screeningCache?.historyOfTb == true ||
            isPregnant()
        )

    /** Liquid Culture shown when both history of TB AND taking anti-TB drugs */
    private fun shouldShowLiquidCultureConducted(): Boolean =
        screeningCache?.historyOfTb == true && screeningCache?.takingAntiTBDrugs == true

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun isYes(formElement: FormElement): Boolean = formElement.value == yesValue
    private fun isNo(formElement: FormElement): Boolean = formElement.value == noValue

    fun isMtbDetected(): Boolean {
        val v = trueNatResult.value
        if (v.isNullOrBlank()) return false
        val clean = v.trim().lowercase()
        if (clean.contains("not") || clean.contains("negative") || clean.contains("invalid")) {
            return false
        }
        return clean.contains("positive") || clean.contains("detected")
    }

    private fun isPositive(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val clean = value.trim().lowercase()
        if (clean.contains("not") || clean.contains("negative") || clean.contains("invalid") || clean.contains("waiting")) {
            return false
        }
        return clean.contains("positive") || clean.contains("detected") || clean.contains("tb") || clean.contains("abnormal")
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
        // Source 1: Ben registration reproductive status
        val reproductiveStatus = benCache?.genDetails?.reproductiveStatus
        val pregnantFromBen = benCache?.genDetails?.reproductiveStatusId == 1 ||
            reproductiveStatus.equals("Yes", ignoreCase = true)

        // Source 2: Vital Screen → Key Population / Risk Factors = "PREGNANCY" (stored as code, language-independent)
        val pregnantFromVital = vitalCache?.keyPopulationRiskFactors
            ?.any { it.equals("PREGNANCY", ignoreCase = true) } == true

        return pregnantFromBen || pregnantFromVital
    }
}
