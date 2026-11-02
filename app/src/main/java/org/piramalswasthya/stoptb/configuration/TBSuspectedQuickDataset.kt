package org.piramalswasthya.stoptb.configuration

import android.content.Context
import android.widget.LinearLayout
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.helpers.Languages
import org.piramalswasthya.stoptb.model.AgeUnit
import org.piramalswasthya.stoptb.model.BenRegCache
import org.piramalswasthya.stoptb.model.ChestXrayResult
import org.piramalswasthya.stoptb.model.FormElement
import org.piramalswasthya.stoptb.model.InputType
import org.piramalswasthya.stoptb.model.OrderStatus
import org.piramalswasthya.stoptb.model.RifResult
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
    // "COMPLETE" or "NOT_CONDUCTED" — which action button was tapped on the beneficiary card to
    // reach this screen, so the corresponding Conducted field can be pre-set and locked instead
    // of asking the user to re-decide something they already told us. Null for any other entry
    // path (e.g. the first-time REFER flow, or a read-only VIEW), where Conducted stays as-is.
    private var manualEntryAction: String? = null

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

    // Chest X-Ray order lifecycle redesign: 4 standardized results (Normal, TB Presumptive,
    // Abnormal but not TB Presumptive, AI Invalid Result) instead of a generic Positive/Negative
    // binary — see model.ChestXrayResult for the mapping used at save time.
    val digitalChestXrayResult = FormElement(
        id = 5,
        inputType = InputType.RADIO,
        orientation = LinearLayout.VERTICAL,
        title = resources.getString(R.string.tb_digital_chest_xray_result),
        arrayId = R.array.tb_digital_xray_result,
        entries = resources.getStringArray(R.array.tb_digital_xray_result),
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

    private val isSputumCollectedField = FormElement(
        id = 24,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.tb_is_sputum_collected),
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
        title = resources.getString(R.string.tb_quick_naat_conducted),
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
        orientation = LinearLayout.VERTICAL,
        title = resources.getString(R.string.tb_quick_naat_result),
        arrayId = R.array.tb_truenat_mtb_result,
        entries = resources.getStringArray(R.array.tb_truenat_mtb_result),
        required = false,
        hasDependants = true
    )

    val trueNatRifResult = FormElement(
        id = 20,
        inputType = InputType.RADIO,
        orientation = LinearLayout.VERTICAL,
        title = resources.getString(R.string.tb_quick_rif_result),
        arrayId = R.array.tb_truenat_rif_result,
        entries = resources.getStringArray(R.array.tb_truenat_rif_result),
        required = false
    )

    val rifConducted = FormElement(
        id = 21,
        inputType = InputType.RADIO,
        title = resources.getString(R.string.tb_quick_rif_conducted),
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
        referralType: Int = 0,
        manualEntryAction: String? = null
    ) {
        benCache = ben
        screeningCache = screening
        vitalCache = vital
        diagnosticsCache = saved
        this.referralMode = referralMode
        this.referralType = referralType
        this.manualEntryAction = manualEntryAction

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
        
        if (!saved?.chestXRayResult.isNullOrBlank()) {
            digitalChestXrayResult.inputType = InputType.TEXT_VIEW
            digitalChestXrayResult.value = getLocalValueInArray(R.array.tb_digital_xray_result, saved?.chestXRayResult) ?: saved?.chestXRayResult
        } else if (isXrayDeviceIntegrated && referralType == 6 && isYes(digitalChestXrayConducted) && manualEntryAction != "COMPLETE") {
            digitalChestXrayResult.inputType = InputType.TEXT_VIEW
            digitalChestXrayResult.value = "Waiting for Result"
        } else {
            digitalChestXrayResult.inputType = InputType.RADIO
            digitalChestXrayResult.value = null
        }

        // ── Sputum Collection ──────────────────────────────────────────────
        // Always Yes in the device-integrated flow (PENDING, FAILED, or MANUAL_ENTRY alike) — see
        // syncFieldStates()'s matching unconditional lock for why.
        val isReferredForSputumVal = saved?.isReferredForSputum == true || !saved?.naatResult.isNullOrBlank()
        referredForSputumCollection.value = boolToYesNo(if (isTruenatDeviceIntegrated && referralType == 7) true else isReferredForSputumVal)
        // Defaults Yes unless the user already explicitly saved "No" — unlike trueNatConducted's
        // "was it already done" pattern, this must default Yes even with no order/result yet (e.g.
        // a fresh Not-Conducted-eligible order), while staying editable so No is still selectable.
        isSputumCollectedField.value = boolToYesNo(saved?.isSputumCollected ?: true)
        reasonForDenialSputum.value = englishPipeToIndexPipe(
            saved?.reasonForDenialSputum, R.array.tb_reason_for_denial_sputum
        )
        reasonForDenialSputumOther.value = saved?.reasonForDenialSputumOther
        sputumSampleSubmittedAt.value = getLocalValueInArray(
            R.array.tb_diagnostics_sputum_submitted_at, saved?.sputumSubmittedAt
        )

        // ── TrueNAT ───────────────────────────────────────────────────────
        val isTrueNatDone = saved?.isNaatConducted == true || !saved?.naatResult.isNullOrBlank()
        trueNatConducted.value = boolToYesNo(if (isTrueNatDone) true else saved?.isNaatConducted)
        reasonNotConductedNaat.value = getLocalValueInArray(
            R.array.tb_reason_not_conducted_naat, saved?.reasonNotConductedNaat
        )
        reasonNotConductedNaatOther.value = saved?.reasonNotConductedNaatOther

        if (!saved?.naatResult.isNullOrBlank()) {
            trueNatResult.inputType = InputType.TEXT_VIEW
            trueNatResult.value = getLocalValueInArray(R.array.tb_truenat_mtb_result, mapMtbResultForUi(saved?.naatResult)) ?: mapMtbResultForUi(saved?.naatResult)
        } else if (isTruenatDeviceIntegrated && referralType == 7 && isYes(trueNatConducted) && manualEntryAction != "COMPLETE") {
            trueNatResult.inputType = InputType.TEXT_VIEW
            trueNatResult.value = "Waiting for Result"
        } else {
            trueNatResult.inputType = InputType.RADIO
            trueNatResult.value = null
        }

        // ── RIF Conducted & Results ───────────────────────────────────────
        val isRifConductedVal = saved?.rifOrderStatus.equals("COMPLETED", ignoreCase = true) ||
                saved?.rifOrderStatus.equals("PENDING", ignoreCase = true) ||
                !saved?.trueNatRifResult.isNullOrBlank()
        // RIF order lifecycle redesign: "Not Conducted" is now a Closed order/manualResult
        // closure (see TBRepo.submitManualResult) using RIF's own reasonNotConductedRif/Other
        // columns, not the old rifOrderStatus == "NOT_CONDUCTED" (never a real wire value) +
        // PreferenceDao-backed reason.
        rifConducted.value = boolToYesNo(
            if (isRifConductedVal) true
            else if (saved?.rifOrderStatus.equals(OrderStatus.CLOSED.name, ignoreCase = true)) false
            else null
        )
        reasonNotConductedRif.value = getLocalValueInArray(
            R.array.tb_reason_not_conducted_naat, saved?.reasonNotConductedRif
        )
        reasonNotConductedRifOther.value = saved?.reasonNotConductedRifOther

        if (!saved?.trueNatRifResult.isNullOrBlank()) {
            trueNatRifResult.inputType = InputType.TEXT_VIEW
            trueNatRifResult.value = getLocalValueInArray(R.array.tb_truenat_rif_result, mapRifResultForUi(saved?.trueNatRifResult)) ?: mapRifResultForUi(saved?.trueNatRifResult)
        } else if (isTruenatDeviceIntegrated && referralType == 7 && isYes(rifConducted) && manualEntryAction != "COMPLETE") {
            trueNatRifResult.inputType = InputType.TEXT_VIEW
            trueNatRifResult.value = "Waiting for Result"
        } else {
            trueNatRifResult.inputType = InputType.RADIO
            trueNatRifResult.value = null
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
            isSputumCollectedField.value.isNullOrBlank()
        ) {
            isSputumCollectedField.value = yesValue
        }
        val sputumCollectedNow = isYes(referredForSputumCollection) && isYes(isSputumCollectedField)
        if (sputumVisible && sputumCollectedNow &&
            sputumSampleSubmittedAt.value.isNullOrBlank()
        ) {
            sputumSampleSubmittedAt.value = sputumSampleSubmittedAt.entries?.firstOrNull()
        }
        if (sputumVisible && !sputumCollectedNow &&
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
                        if (isSputumCollectedField.value.isNullOrBlank()) {
                            isSputumCollectedField.value = yesValue
                        }
                        addItems.add(isSputumCollectedField)
                        if (isYes(isSputumCollectedField)) {
                            if (sputumSampleSubmittedAt.value.isNullOrBlank()) {
                                sputumSampleSubmittedAt.value = sputumSampleSubmittedAt.entries?.firstOrNull()
                            }
                            addItems.add(sputumSampleSubmittedAt)
                        } else {
                            if (reasonForDenialSputum.value.isNullOrBlank()) {
                                reasonForDenialSputum.value = "0"
                            }
                            addItems.add(reasonForDenialSputum)
                            if (isLastItemSelected(reasonForDenialSputum, R.array.tb_reason_for_denial_sputum)) {
                                addItems.add(reasonForDenialSputumOther)
                            }
                        }
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
                        isSputumCollectedField,
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
                    // Yes: reveal "Is sputum collected?" (default Yes if blank), remove denial
                    if (isSputumCollectedField.value.isNullOrBlank()) {
                        isSputumCollectedField.value = yesValue
                    }
                    val addItems = mutableListOf<FormElement>(isSputumCollectedField)
                    if (isYes(isSputumCollectedField)) {
                        sputumSampleSubmittedAt.isEnabled = !referralMode  // explicitly enable when first shown
                        if (sputumSampleSubmittedAt.value.isNullOrBlank()) {
                            sputumSampleSubmittedAt.value = sputumSampleSubmittedAt.entries?.firstOrNull()
                        }
                        addItems.add(sputumSampleSubmittedAt)
                        if (shouldShowTrueNatConducted()) {
                            addItems.add(trueNatConducted)
                            if (isYes(trueNatConducted)) {
                                addItems.add(trueNatResult)
                            } else if (!trueNatConducted.value.isNullOrBlank() && !isYes(trueNatConducted)) {
                                addItems.add(reasonNotConductedNaat)
                                if (isLastItemSelectedDropdown(reasonNotConductedNaat, R.array.tb_reason_not_conducted_naat)) {
                                    addItems.add(reasonNotConductedNaatOther)
                                }
                            }
                        }
                    } else {
                        if (reasonForDenialSputum.value.isNullOrBlank()) {
                            reasonForDenialSputum.value = "0"
                        }
                        addItems.add(reasonForDenialSputum)
                        if (isLastItemSelected(reasonForDenialSputum, R.array.tb_reason_for_denial_sputum)) {
                            addItems.add(reasonForDenialSputumOther)
                        }
                    }
                    triggerDependants(
                        source = referredForSputumCollection,
                        removeItems = listOf(reasonForDenialSputum, reasonForDenialSputumOther),
                        addItems = addItems
                    )
                } else {
                    // No: show denial (default to Patient refused if blank), remove everything below
                    // "Is sputum collected?" including that field itself (referral declined outright).
                    if (reasonForDenialSputum.value.isNullOrBlank()) {
                        reasonForDenialSputum.value = "0"
                    }
                    triggerDependants(
                        source = referredForSputumCollection,
                        removeItems = listOf(
                            isSputumCollectedField,
                            sputumSampleSubmittedAt,
                            trueNatConducted,
                            reasonNotConductedNaat,
                            reasonNotConductedNaatOther,
                            trueNatResult
                        ),
                        addItems = listOf(reasonForDenialSputum)
                    )
                }
            }

            isSputumCollectedField.id -> {
                isSputumCollectedField.value = if (index == 0) yesValue else noValue
                syncFieldStates()
                if (index == 0) {
                    // Yes: reveal submitted-at (default to TB Screening Camp if blank) + NAAT chain
                    sputumSampleSubmittedAt.isEnabled = !referralMode
                    if (sputumSampleSubmittedAt.value.isNullOrBlank()) {
                        sputumSampleSubmittedAt.value = sputumSampleSubmittedAt.entries?.firstOrNull()
                    }
                    val addItems = mutableListOf<FormElement>(sputumSampleSubmittedAt)
                    if (shouldShowTrueNatConducted()) {
                        addItems.add(trueNatConducted)
                        if (isYes(trueNatConducted)) {
                            addItems.add(trueNatResult)
                        } else if (!trueNatConducted.value.isNullOrBlank() && !isYes(trueNatConducted)) {
                            addItems.add(reasonNotConductedNaat)
                            if (isLastItemSelectedDropdown(reasonNotConductedNaat, R.array.tb_reason_not_conducted_naat)) {
                                addItems.add(reasonNotConductedNaatOther)
                            }
                        }
                    }
                    triggerDependants(
                        source = isSputumCollectedField,
                        removeItems = listOf(reasonForDenialSputum, reasonForDenialSputumOther),
                        addItems = addItems
                    )
                } else {
                    // No: sample never obtained — show the reason, remove everything downstream
                    // (nothing about the TrueNat test itself is answerable without a sample).
                    if (reasonForDenialSputum.value.isNullOrBlank()) {
                        reasonForDenialSputum.value = "0"
                    }
                    triggerDependants(
                        source = isSputumCollectedField,
                        removeItems = listOf(
                            sputumSampleSubmittedAt,
                            trueNatConducted,
                            reasonNotConductedNaat,
                            reasonNotConductedNaatOther,
                            trueNatResult
                        ),
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
                        getEnglishValueInArray(R.array.tb_digital_xray_result, digitalChestXrayResult.value)
                    else null
            }

            // Sputum Collection & TrueNAT (referralType == 0 or 7)
            if (referralType == 0 || referralType == 7) {
                val sputumVisible = referralType == 7 || shouldShowSputumCollected()
                val isReferred = isYes(referredForSputumCollection)
                val isSputumCollectedNow = isReferred && isYes(isSputumCollectedField)
                form.isReferredForSputum =
                    if (sputumVisible) isReferred else null
                form.isSputumCollected =
                    if (sputumVisible && isReferred) isYes(isSputumCollectedField) else null
                form.reasonForDenialSputum =
                    if (sputumVisible && (!isReferred || (isReferred && !isSputumCollectedNow)))
                        indexPipeToEnglishPipe(reasonForDenialSputum, R.array.tb_reason_for_denial_sputum)
                    else null
                form.reasonForDenialSputumOther =
                    if (sputumVisible && (!isReferred || (isReferred && !isSputumCollectedNow)))
                        reasonForDenialSputumOther.value?.takeIf { it.isNotBlank() }
                    else null
                form.sputumSubmittedAt =
                    if (sputumVisible && isSputumCollectedNow)
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
                val showRifSection = shouldShowTrueNatConducted() && isYes(trueNatConducted) && isMtbDetected()
                form.trueNatRifResult =
                    if (showRifSection && isYes(rifConducted))
                        getEnglishValueInArray(R.array.tb_truenat_rif_result, trueNatRifResult.value)
                    else null
                // RIF's own "Not Conducted" reason — distinct columns from NAAT's
                // reasonNotConductedNaat/Other above (RIF order lifecycle redesign).
                form.reasonNotConductedRif =
                    if (showRifSection && isNo(rifConducted))
                        getEnglishValueInArray(R.array.tb_reason_not_conducted_naat, reasonNotConductedRif.value)
                    else null
                form.reasonNotConductedRifOther =
                    if (showRifSection && isNo(rifConducted))
                        reasonNotConductedRifOther.value?.takeIf { it.isNotBlank() }
                    else null
                // "Confirmed DR-TB Case" — distinguishable from the generic isConfirmed/
                // isTBConfirmed below (only true for RIF's DR TB result specifically).
                if (showRifSection && isYes(rifConducted)) {
                    form.isDrTbConfirmed =
                        RifResult.fromResultText(form.trueNatRifResult) ==
                                RifResult.DR_TB
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

            val isConfirmed = isPositive(form.naatResult) ||
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
                    add(isSputumCollectedField)
                    if (isYes(isSputumCollectedField)) {
                        add(sputumSampleSubmittedAt)
                        if (referralMode || referralType == 0 || referralType == 7) {
                            add(trueNatConducted)
                            if (isYes(trueNatConducted)) {
                                add(trueNatResult)

                                val isRifCompleted = diagnosticsCache?.rifOrderStatus.equals("COMPLETED", ignoreCase = true)
                                val showRif = if (referralMode) {
                                    isRifCompleted && isMtbDetected()
                                } else {
                                    isMtbDetected()
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
                    } else {
                        add(reasonForDenialSputum)
                        if (isLastItemSelected(reasonForDenialSputum, R.array.tb_reason_for_denial_sputum)) {
                            add(reasonForDenialSputumOther)
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
        val isTruenatDevIntegrated = preferenceDao.getTruenatIntegrated()
        val xrayStatus = diagnosticsCache?.xrayOrderStatus
        val isXrayFailed = xrayStatus.equals("FAILED", ignoreCase = true)

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
        val canForceXrayConducted = manualEntryAction != null && shouldShowDigitalChestXray() && !lockDigitalChestXray && !isXrayFailed
        if (canForceXrayConducted) {
            digitalChestXrayConducted.value = if (manualEntryAction == "COMPLETE") yesValue else noValue
        }
        digitalChestXrayConducted.isEnabled = shouldShowDigitalChestXray() && !lockDigitalChestXray && !isXrayFailed && !canForceXrayConducted
        digitalChestXrayConducted.required = shouldShowDigitalChestXray() && !lockDigitalChestXray && !isXrayFailed
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

        // X-Ray result — Enter Result is now a standing action whenever the order is
        // Pending/Awaiting Manual Entry, not gated on device integration or camp-hub
        // connectivity (see the Chest X-Ray order lifecycle redesign). Role gating happens one
        // level up, at the beneficiary-list card's canActOnReferral check before this screen is
        // even reachable, so no device/connectivity gate is needed here any more.
        val isXrayCompleted = xrayStatus.equals("COMPLETED", ignoreCase = true) || !diagnosticsCache?.chestXRayResult.isNullOrBlank()

        if (isXrayCompleted || isXrayFailed) {
            digitalChestXrayResult.inputType = InputType.TEXT_VIEW
            digitalChestXrayResult.isEnabled = false
            digitalChestXrayResult.required = false
            if (isYes(digitalChestXrayConducted)) {
                if (isXrayCompleted) {
                    digitalChestXrayResult.value = if (diagnosticsCache?.chestXRayResult.isNullOrBlank()) "Waiting for Result" else (getLocalValueInArray(R.array.tb_digital_xray_result, diagnosticsCache?.chestXRayResult) ?: diagnosticsCache?.chestXRayResult)
                } else if (isXrayFailed) {
                    digitalChestXrayResult.value = "Referral Failed"
                } else {
                    if (digitalChestXrayResult.value.isNullOrBlank() || digitalChestXrayResult.value == "Waiting for Result") {
                        digitalChestXrayResult.value = "Waiting for Result"
                    }
                }
            } else {
                if (isXrayFailed) {
                    digitalChestXrayResult.value = "Referral Failed"
                } else {
                    resetField(digitalChestXrayResult)
                }
            }
        } else {
            digitalChestXrayResult.inputType = InputType.RADIO
            val showXrayResult = shouldShowDigitalChestXray() && isYes(digitalChestXrayConducted)
            digitalChestXrayResult.isEnabled = showXrayResult && !lockDigitalChestXray
            digitalChestXrayResult.required = showXrayResult && !lockDigitalChestXray
            if (!showXrayResult) {
                resetField(digitalChestXrayResult)
            }
        }

        // Sputum section
        val sputumVisible = referralType == 7 || shouldShowSputumCollected()

        // "Referred for sputum collection/TrueNat Test?" always stays locked at Yes in the
        // device-integrated flow — PENDING, FAILED, or MANUAL_ENTRY alike. Only "Is sputum
        // collected?" is meant to be user-editable in those states (see below), so the user can
        // refuse sputum collection at any point before a real result exists, without also being
        // able to re-open the referral decision itself.
        if (isTruenatDevIntegrated && referralType == 7) {
            referredForSputumCollection.value = yesValue
            referredForSputumCollection.isEnabled = false
            referredForSputumCollection.required = false
        } else {
            referredForSputumCollection.isEnabled = sputumVisible && !referralMode
            referredForSputumCollection.required = sputumVisible && !referralMode
        }
        if (!sputumVisible) resetField(referredForSputumCollection)

        // "Is sputum collected?" locks the same way trueNatConducted/trueNatResult already do
        // (lockTrueNat: referralMode or a real result already exists) — editable for as long as
        // the order is still unresolved (PENDING, FAILED, or MANUAL_ENTRY), letting the user
        // refuse sputum collection at any of those points, not just once it's failed outright.
        val sputumCollectedVisible = shouldShowIsSputumCollected()
        isSputumCollectedField.isEnabled = sputumCollectedVisible && !lockTrueNat
        isSputumCollectedField.required = sputumCollectedVisible && !lockTrueNat
        if (!sputumCollectedVisible) resetField(isSputumCollectedField)

        val sputumReferred = isYes(referredForSputumCollection)
        val sputumDenied = isNo(referredForSputumCollection) ||
                (isYes(referredForSputumCollection) && isNo(isSputumCollectedField))
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
        val mtbStatus = diagnosticsCache?.trueNatOrderStatus
        val isMtbFailed = mtbStatus.equals("FAILED", ignoreCase = true)
        val canForceTrueNatConducted = manualEntryAction != null && shouldShowTrueNatConducted() && !lockTrueNat && !isMtbFailed
        if (canForceTrueNatConducted) {
            trueNatConducted.value = if (manualEntryAction == "COMPLETE") yesValue else noValue
        }
        trueNatConducted.isEnabled = shouldShowTrueNatConducted() && !lockTrueNat && !isMtbFailed && !canForceTrueNatConducted
        trueNatConducted.required = shouldShowTrueNatConducted() && !lockTrueNat && !isMtbFailed
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

        val isMtbCompleted = mtbStatus.equals("COMPLETED", ignoreCase = true) || !diagnosticsCache?.naatResult.isNullOrBlank()
        // manualEntryAction == "COMPLETE" means the user explicitly tapped "Enter Result
        // Manually" for this order — that's a standing action regardless of device
        // integration/camp-hub state, so it must not be overridden back into a locked
        // "Waiting for Result" view here.
        val isMtbWaiting = isTruenatDevIntegrated && preferenceDao.isCampHubConnected() && referralType == 7 &&
            !mtbStatus.equals("FAILED", ignoreCase = true) &&
            !mtbStatus.equals("MANUAL_ENTRY", ignoreCase = true) &&
            !isMtbCompleted &&
            manualEntryAction != "COMPLETE"

        if (isMtbWaiting || isMtbCompleted || isMtbFailed) {
            trueNatResult.inputType = InputType.TEXT_VIEW
            trueNatResult.isEnabled = false
            trueNatResult.required = false
            if (isYes(trueNatConducted)) {
                if (isMtbCompleted) {
                    trueNatResult.value = if (diagnosticsCache?.naatResult.isNullOrBlank()) "Waiting for Result" else (getLocalValueInArray(R.array.tb_truenat_mtb_result, mapMtbResultForUi(diagnosticsCache?.naatResult)) ?: mapMtbResultForUi(diagnosticsCache?.naatResult))
                } else if (isMtbFailed) {
                    trueNatResult.value = "Referral Failed"
                } else {
                    if (trueNatResult.value.isNullOrBlank() || trueNatResult.value == "Waiting for Result") {
                        trueNatResult.value = "Waiting for Result"
                    }
                }
            } else {
                if (isMtbFailed) {
                    trueNatResult.value = "Referral Failed"
                } else {
                    resetField(trueNatResult)
                }
            }
        } else {
            trueNatResult.inputType = InputType.RADIO
            val showTrueNatResult = shouldShowTrueNatConducted() && isYes(trueNatConducted)
            trueNatResult.isEnabled = showTrueNatResult && !lockTrueNat
            trueNatResult.required = showTrueNatResult && !lockTrueNat
            if (!showTrueNatResult) {
                resetField(trueNatResult)
            }
        }

        // Only force RIF fields once an order actually exists — not the save that queues it.
        val showRif = if (referralMode) {
            diagnosticsCache?.rifOrderStatus.equals("COMPLETED", ignoreCase = true) && isMtbDetected()
        } else {
            !diagnosticsCache?.rifOrderStatus.isNullOrBlank() && isMtbDetected()
        }
        val rifStatus = diagnosticsCache?.rifOrderStatus
        val isRifFailed = rifStatus.equals("FAILED", ignoreCase = true)
        val canForceRifConducted = manualEntryAction != null && showRif && !lockRif && !isRifFailed
        if (canForceRifConducted) {
            rifConducted.value = if (manualEntryAction == "COMPLETE") yesValue else noValue
        }
        rifConducted.isEnabled = showRif && !lockRif && !isRifFailed && !canForceRifConducted
        rifConducted.required = showRif && !lockRif && !isRifFailed
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

        val isRifCompleted = rifStatus.equals("COMPLETED", ignoreCase = true) || !diagnosticsCache?.trueNatRifResult.isNullOrBlank()
        // Same reasoning as isMtbWaiting above — an explicit "Enter Result Manually" tap must
        // not be overridden back into a locked "Waiting for Result" view.
        val isRifWaiting = isTruenatDevIntegrated && preferenceDao.isCampHubConnected() && referralType == 7 &&
            !rifStatus.equals("FAILED", ignoreCase = true) &&
            !rifStatus.equals("MANUAL_ENTRY", ignoreCase = true) &&
            !isRifCompleted &&
            manualEntryAction != "COMPLETE"

        if (isRifWaiting || isRifCompleted || isRifFailed) {
            trueNatRifResult.inputType = InputType.TEXT_VIEW
            trueNatRifResult.isEnabled = false
            trueNatRifResult.required = false
            val showRifResult = showRif && isYes(rifConducted)
            if (showRifResult) {
                if (isRifCompleted) {
                    trueNatRifResult.value = if (diagnosticsCache?.trueNatRifResult.isNullOrBlank()) "Waiting for Result" else (getLocalValueInArray(R.array.tb_truenat_rif_result, mapRifResultForUi(diagnosticsCache?.trueNatRifResult)) ?: mapRifResultForUi(diagnosticsCache?.trueNatRifResult))
                } else if (isRifFailed) {
                    trueNatRifResult.value = "Referral Failed"
                } else {
                    if (trueNatRifResult.value.isNullOrBlank() || trueNatRifResult.value == "Waiting for Result") {
                        trueNatRifResult.value = "Waiting for Result"
                    }
                }
            } else {
                if (isRifFailed) {
                    trueNatRifResult.value = "Referral Failed"
                } else {
                    resetField(trueNatRifResult)
                }
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
            isXrayResultReferable(digitalChestXrayResult.value) ||
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

    /** "Is sputum collected?" shown whenever the referral itself is Yes — same condition
     *  shouldShowTrueNatConducted() used before the new sputum-collected gate was inserted. */
    private fun shouldShowIsSputumCollected(): Boolean =
        (referralMode || referralType == 0 || referralType == 7) &&
        isYes(referredForSputumCollection)

    /** TrueNAT shown when xray positive, sputum referred, history of TB, anti-TB drugs, or
     *  pregnant — AND a sample was actually collected (new gate; a sample never obtained can't
     *  have its TrueNat test conducted). */
    private fun shouldShowTrueNatConducted(): Boolean =
        shouldShowIsSputumCollected() && isYes(isSputumCollectedField)

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

    /** True when the (localized) selected/displayed Chest X-Ray result is one of the two
     *  standardized results that should show the Sputum/TrueNat referral section — TB
     *  Presumptive or Abnormal-but-not-presumptive. Replaces the old generic [isPositive]
     *  keyword match for this field now that "Abnormal but not TB Presumptive" is a real,
     *  selectable value (it contains "not", which [isPositive] would have misread as negative). */
    private fun isXrayResultReferable(localizedValue: String?): Boolean {
        val englishValue = getEnglishValueInArray(R.array.tb_digital_xray_result, localizedValue)
        return ChestXrayResult.fromResultText(englishValue)?.triggersTrueNatReferral == true
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

    private fun mapMtbResultForUi(value: String?): String? {
        if (value == null) return null
        return when {
            value.equals("TB Positive", ignoreCase = true) || value.equals("MTB detected", ignoreCase = true) || value.equals("MTB Positive", ignoreCase = true) -> "MTB Positive"
            value.equals("TB Negative", ignoreCase = true) || value.equals("MTB not detected", ignoreCase = true) || value.equals("MTB Negative", ignoreCase = true) -> "MTB Negative"
            value.equals("Invalid", ignoreCase = true) || value.equals("Invalid/Error", ignoreCase = true) -> "Invalid/Error"
            else -> value
        }
    }


    private fun mapRifResultForUi(value: String?): String? {
        if (value == null) return null
        return when {
            value.equals("DR TB", ignoreCase = true) || value.equals("Rif Resistance Detected", ignoreCase = true) -> "Rif Resistance Detected"
            value.equals("Non DR TB", ignoreCase = true) || value.equals("Rif Resistance Not Detected", ignoreCase = true) -> "Rif Resistance Not Detected"
            else -> value
        }
    }

}
