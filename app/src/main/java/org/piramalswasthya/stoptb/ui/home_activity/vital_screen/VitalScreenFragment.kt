package org.piramalswasthya.stoptb.ui.home_activity.vital_screen

import android.os.Bundle
import android.text.InputFilter
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.RadioButton
import android.widget.RadioGroup
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import androidx.appcompat.app.AlertDialog
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.databinding.FragmentVitalScreenBinding
import org.piramalswasthya.stoptb.helpers.applyAutoFlowBackPolicyOnResume
import org.piramalswasthya.stoptb.ui.home_activity.HomeActivity
import org.piramalswasthya.stoptb.ui.volunteer.VolunteerActivity
import org.piramalswasthya.stoptb.utils.scrollToFormValidationField
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.work.WorkerUtils
import javax.inject.Inject

@AndroidEntryPoint
class VitalScreenFragment : Fragment() {

    @Inject lateinit var preferenceDao: PreferenceDao

    private var _binding: FragmentVitalScreenBinding? = null
    private val binding: FragmentVitalScreenBinding
        get() = _binding!!

    private val viewModel: VitalScreenViewModel by viewModels()
    private var referralAlert: AlertDialog? = null
    private var riskFactorOptions: List<CodedOption> = emptyList()
    private var selectedRiskFactors = BooleanArray(0)

    private data class CodedOption(
        val id: Int,
        val code: String,
        val label: String
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVitalScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupInputLimits()
        setupDropdowns()
        setupValidationClearers()
        observeUi()
        binding.btnSubmit.setOnClickListener {
            submitVitals()
        }
    }

    private fun setupInputLimits() {
        binding.etPulseRate.filters = arrayOf(InputFilter.LengthFilter(3))
        binding.etBpSystolic.filters = arrayOf(InputFilter.LengthFilter(3))
        binding.etBpDiastolic.filters = arrayOf(InputFilter.LengthFilter(3))
        binding.etRbs.filters = arrayOf(InputFilter.LengthFilter(6))

        // Clear validation error as soon as user starts correcting the field
        binding.etPulseRate.doAfterTextChanged   { binding.tilPulseRate.error = null }
        binding.etBpSystolic.doAfterTextChanged  { binding.tilBpSystolic.error = null }
        binding.etBpDiastolic.doAfterTextChanged { binding.tilBpDiastolic.error = null }
        binding.etRbs.doAfterTextChanged         { binding.tilRbs.error = null }
    }

    private fun setupValidationClearers() {

        binding.etPulseRate.doAfterTextChanged {
            val value = parsePulse(it.toString())

            binding.tilPulseRate.error =
                if (it.isNullOrBlank() || (value != null && value in 40..220))
                    null
                else
                    "Enter pulse rate between 40 and 220"
        }

        binding.etBpSystolic.doAfterTextChanged {
            validateBpFieldsLive()
        }

        binding.etBpDiastolic.doAfterTextChanged {
            validateBpFieldsLive()
        }

        binding.etRbs.doAfterTextChanged {
            val value = it.toString().toDoubleOrNull()

            binding.tilRbs.error =
                if (it.isNullOrBlank() || (value != null && value in 20.0..600.0))
                    null
                else
                    "Enter Random Blood Sugar between 20 and 600"
        }

        binding.etHivStatus.doAfterTextChanged {
            binding.tilHivStatus.error =
                if (it.isNullOrBlank())
                    getString(R.string.hiv_status_required)
                else
                    null
        }
    }

    private fun validateBpFieldsLive() {

        val systolicText = binding.etBpSystolic.text?.toString()?.trim().orEmpty()
        val diastolicText = binding.etBpDiastolic.text?.toString()?.trim().orEmpty()

        binding.tilBpSystolic.error = null
        binding.tilBpDiastolic.error = null

        val systolic = systolicText.toIntOrNull()
        val diastolic = diastolicText.toIntOrNull()

        if (systolicText.isNotEmpty() &&
            (systolic == null || systolic !in 40..320)
        ) {
            binding.tilBpSystolic.error =
                "Enter Systolic BP between 40 and 320"
        }

        if (diastolicText.isNotEmpty() &&
            (diastolic == null || diastolic !in 10..180)
        ) {
            binding.tilBpDiastolic.error =
                "Enter Diastolic BP between 10 and 180"
        }

        if (systolic != null &&
            diastolic != null &&
            systolic <= diastolic
        ) {
            binding.tilBpSystolic.error =
                "Systolic BP must be greater than diastolic BP"

            binding.tilBpDiastolic.error =
                "Diastolic BP must be less than systolic BP"
        }
    }


    private fun observeUi() {
        viewModel.benName.observe(viewLifecycleOwner) {
            binding.tvBenName.text = it
        }
        viewModel.benAgeGender.observe(viewLifecycleOwner) {
            binding.tvAgeGender.text = it
        }
        viewModel.existingVitals.observe(viewLifecycleOwner) { vital ->
            val hasExistingVitals = vital != null
            binding.btnSubmit.visibility = if (hasExistingVitals) View.GONE else View.VISIBLE
            setFormEditable(!hasExistingVitals)

            // benCache is set before existingVitals is posted, so isMale / isPregnant are
            // reliable here. Re-build the options list (may filter PREGNANCY / LACTATING_MOTHER
            // for male beneficiaries) and reset the selection array.
            riskFactorOptions = getRiskFactorOptions()
            selectedRiskFactors = BooleanArray(riskFactorOptions.size)

            if (vital == null) {
                // New form — auto-select Pregnancy when ben registration says "Yes"
                autoSelectPregnancyIfApplicable()
                return@observe
            }
            binding.etPulseRate.setText(viewModel.getPulseDisplayValue(vital.pulseRate))
            binding.etBpSystolic.setText(vital.bpSystolic?.toString().orEmpty())
            binding.etBpDiastolic.setText(vital.bpDiastolic?.toString().orEmpty())
            binding.etRbs.setText(vital.rbs?.stripTrailingZeros())
            setPresentAbsentRadioGroupValue(binding.rgPallor, vital.pallorId, vital.pallor)
            setPresentAbsentRadioGroupValue(binding.rgIcterus, vital.icterusId, vital.icterus)
            setPresentAbsentRadioGroupValue(binding.rgCyanosis, vital.cyanosisId, vital.cyanosis)
            setPresentAbsentRadioGroupValue(binding.rgClubbing, vital.clubbingId, vital.clubbing)
            setPresentAbsentRadioGroupValue(binding.rgLymphadenopathy, vital.lymphadenopathyId, vital.lymphadenopathy)
            setPresentAbsentRadioGroupValue(binding.rgOedema, vital.oedemaId, vital.oedema)
            applyRiskFactorSelection(vital.keyPopulationRiskFactorIds.orEmpty(), vital.keyPopulationRiskFactors.orEmpty())
            setHivStatusValue(vital.hivStatusId, vital.hivStatus)
        }
        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                VitalScreenViewModel.State.SAVING -> {
                    binding.loadingOverlay.visibility = View.VISIBLE
                    binding.btnSubmit.isEnabled = false
                }
                VitalScreenViewModel.State.SAVE_SUCCESS -> {
                    binding.loadingOverlay.visibility = View.GONE
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.vitals_saved_successfully),
                        Toast.LENGTH_SHORT
                    ).show()
                    WorkerUtils.triggerCampAwarePushWorker(requireContext(), preferenceDao)
                    navigateAfterVitals()
                }
                VitalScreenViewModel.State.SAVE_FAILED -> {
                    binding.loadingOverlay.visibility = View.GONE
                    binding.btnSubmit.isEnabled = true
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.vitals_save_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                    viewModel.resetState()
                }
                VitalScreenViewModel.State.IDLE -> {
                    binding.loadingOverlay.visibility = View.GONE
                    binding.btnSubmit.isEnabled = true
                }
            }
        }
    }

    private fun setFormEditable(isEditable: Boolean) {
        binding.etPulseRate.isEnabled = isEditable
        binding.etBpSystolic.isEnabled = isEditable
        binding.etBpDiastolic.isEnabled = isEditable
        binding.etRbs.isEnabled = isEditable
        setRadioGroupEnabled(binding.rgPallor, isEditable)
        setRadioGroupEnabled(binding.rgIcterus, isEditable)
        setRadioGroupEnabled(binding.rgCyanosis, isEditable)
        setRadioGroupEnabled(binding.rgClubbing, isEditable)
        setRadioGroupEnabled(binding.rgLymphadenopathy, isEditable)
        setRadioGroupEnabled(binding.rgOedema, isEditable)
        binding.etRiskFactors.isEnabled = isEditable
        binding.tilRiskFactors.isEnabled = isEditable
        binding.etHivStatus.isEnabled = isEditable
        binding.tilHivStatus.isEnabled = isEditable
    }

    private fun setupDropdowns() {
        val hivLabels = getHivStatusOptions().map { it.label }
        binding.etHivStatus.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, hivLabels)
        )
        if (binding.etHivStatus.text.isNullOrBlank()) {
            binding.etHivStatus.setText(getHivStatusOptions().first { it.code == "UNKNOWN" }.label, false)
        }
        setupRiskFactorSelection()
        enableDropdownInteraction(binding.tilHivStatus, binding.etHivStatus)
    }

    private fun enableDropdownInteraction(
        inputLayout: TextInputLayout,
        autoCompleteTextView: MaterialAutoCompleteTextView
    ) {
        autoCompleteTextView.setOnClickListener {
            autoCompleteTextView.showDropDown()
        }
        autoCompleteTextView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                autoCompleteTextView.post { autoCompleteTextView.showDropDown() }
            }
        }
        inputLayout.setEndIconOnClickListener {
            autoCompleteTextView.requestFocus()
            autoCompleteTextView.showDropDown()
        }
    }

    private fun submitVitals() {
        if (!validateFields()) return
        if (shouldShowReferralAlert()) {
            showReferralAlert {
                saveVitals()
            }
            return
        }
        saveVitals()
    }

    private fun saveVitals() {
        val pallor = getPresentAbsentSelection(binding.rgPallor)
        val icterus = getPresentAbsentSelection(binding.rgIcterus)
        val cyanosis = getPresentAbsentSelection(binding.rgCyanosis)
        val clubbing = getPresentAbsentSelection(binding.rgClubbing)
        val lymphadenopathy = getPresentAbsentSelection(binding.rgLymphadenopathy)
        val oedema = getPresentAbsentSelection(binding.rgOedema)
        val riskFactors = getSelectedRiskFactorOptions()
        val hivStatus = getHivStatusSelection()
        viewModel.saveVitals(
            temperatureOption = null,
            pulseRateOption = binding.etPulseRate.text?.toString(),
            bpSystolic = binding.etBpSystolic.text?.toString(),
            bpDiastolic = binding.etBpDiastolic.text?.toString(),
            height = null,
            weight = null,
            bmi = null,
            rbs = binding.etRbs.text?.toString(),
            pallorId = pallor?.id,
            pallor = pallor?.code,
            icterusId = icterus?.id,
            icterus = icterus?.code,
            cyanosisId = cyanosis?.id,
            cyanosis = cyanosis?.code,
            clubbingId = clubbing?.id,
            clubbing = clubbing?.code,
            lymphadenopathyId = lymphadenopathy?.id,
            lymphadenopathy = lymphadenopathy?.code,
            oedemaId = oedema?.id,
            oedema = oedema?.code,
            keyPopulationRiskFactorIds = riskFactors.map { it.id },
            keyPopulationRiskFactors = riskFactors.map { it.code },
            hivStatusId = hivStatus?.id,
            hivStatus = hivStatus?.code
        )
    }

    private fun navigateAfterVitals() {
        if (!viewModel.autoFlow) {
            findNavController().navigateUp()
            return
        }
        // Examine flow — return to AllBenFragment so user picks the next form
        val popped = findNavController().popBackStack(R.id.allBenFragment, false)
        if (!popped) findNavController().navigate(R.id.allBenFragment, bundleOf("source" to 0))
    }

    private fun validateFields(): Boolean {
        clearErrors()
        var isValid = true

        val pulse = binding.etPulseRate.text?.toString()?.trim().orEmpty()
        if (pulse.isNotEmpty()) {
            val value = parsePulse(pulse)
            if (value == null || value !in 40..220) {
                binding.tilPulseRate.error = "Enter pulse rate between 40 and 220"
                isValid = false
            }
        }

        val systolicText = binding.etBpSystolic.text?.toString()?.trim().orEmpty()
        val diastolicText = binding.etBpDiastolic.text?.toString()?.trim().orEmpty()
        val systolic = validateWholeNumber(
            value = systolicText,
            inputLayout = binding.tilBpSystolic,
            label = "Systolic BP",
            min = 40,
            max = 320
        )
        val diastolic = validateWholeNumber(
            value = diastolicText,
            inputLayout = binding.tilBpDiastolic,
            label = "Diastolic BP",
            min = 10,
            max = 180
        )
        if (systolicText.isNotEmpty() && diastolicText.isEmpty()) {
            binding.tilBpDiastolic.error = "Enter diastolic BP also"
            isValid = false
        }
        if (diastolicText.isNotEmpty() && systolicText.isEmpty()) {
            binding.tilBpSystolic.error = "Enter systolic BP also"
            isValid = false
        }
        if (systolic != null && diastolic != null && systolic <= diastolic) {
            binding.tilBpSystolic.error = "Systolic BP must be greater than diastolic BP"
            binding.tilBpDiastolic.error = "Diastolic BP must be less than systolic BP"
            isValid = false
        }
        if ((systolicText.isNotEmpty() && systolic == null) || (diastolicText.isNotEmpty() && diastolic == null)) {
            isValid = false
        }

        val rbsText = binding.etRbs.text?.toString()?.trim().orEmpty()
        if (rbsText.isNotEmpty() && validateDecimal(
                value = rbsText,
                inputLayout = binding.tilRbs,
                label = "Random Blood Sugar",
                min = 20.0,
                max = 600.0
            ) == null
        ) {
            isValid = false
        }

        if (getSelectedRiskFactors().isEmpty()) {
            binding.tilRiskFactors.error = getString(R.string.risk_factor_required)
            isValid = false
        }

        if (binding.etHivStatus.text?.toString()?.trim().isNullOrEmpty()) {
            binding.tilHivStatus.error = getString(R.string.hiv_status_required)
            isValid = false
        }

        if (!isValid) {
            scrollToFirstFieldWithError()
        }
        return isValid
    }

    private fun scrollToFirstFieldWithError() {
        val fieldsInVisualOrder = listOf(
            binding.tilPulseRate,
            binding.tilBpSystolic,
            binding.tilBpDiastolic,
            binding.tilRbs,
            binding.tilRiskFactors,
            binding.tilHivStatus,
        )
        fieldsInVisualOrder
            .firstOrNull { !it.error.isNullOrBlank() }
            ?.scrollToFormValidationField()
    }

    private fun parsePulse(value: String): Int? {
        return value.trim().toIntOrNull()
    }

    private fun validateWholeNumber(
        value: String,
        inputLayout: TextInputLayout,
        label: String,
        min: Int,
        max: Int
    ): Int? {
        if (value.isBlank()) return null
        val parsed = value.toIntOrNull()
        if (parsed == null || parsed !in min..max) {
            inputLayout.error = "Enter $label between $min and $max"
            return null
        }
        return parsed
    }

    private fun validateDecimal(
        value: String,
        inputLayout: TextInputLayout,
        label: String,
        min: Double,
        max: Double
    ): Double? {
        val parsed = value.toDoubleOrNull()
        if (parsed == null || parsed < min || parsed > max) {
            inputLayout.error = "Enter $label between ${min.stripTrailingZeros()} and ${max.stripTrailingZeros()}"
            return null
        }
        return parsed
    }

    private fun shouldShowReferralAlert(): Boolean =
        viewModel.shouldShowPulseReferral(binding.etPulseRate.text?.toString()) ||
            viewModel.shouldShowBpReferral(
                binding.etBpSystolic.text?.toString(),
                binding.etBpDiastolic.text?.toString()
            ) ||
            viewModel.shouldShowRbsReferral(binding.etRbs.text?.toString())

    private fun clearErrors() {
        binding.tilPulseRate.error = null
        binding.tilBpSystolic.error = null
        binding.tilBpDiastolic.error = null
        binding.tilRbs.error = null
        binding.tilRiskFactors.error = null
        binding.tilHivStatus.error = null
    }

    private fun setupRiskFactorSelection() {
        riskFactorOptions = getRiskFactorOptions()
        selectedRiskFactors = BooleanArray(riskFactorOptions.size)
        binding.etRiskFactors.setOnClickListener {
            showRiskFactorDialog()
        }
        binding.tilRiskFactors.setEndIconOnClickListener {
            showRiskFactorDialog()
        }
    }

    private fun showRiskFactorDialog() {
        val labels = riskFactorOptions.map { it.label }.toTypedArray()
        val notApplicableIndex = riskFactorOptions.indexOfFirst { it.code == "NOT_APPLICABLE" }
        val pregnancyIndex = riskFactorOptions.indexOfFirst { it.code == "PREGNANCY" }
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.select_key_population_risk_factors))
            .setMultiChoiceItems(labels, selectedRiskFactors) { dialog, which, isChecked ->
                // "Pregnancy" is locked when it was auto-populated from ben registration —
                // the user cannot uncheck it here.
                if (!isChecked && which == pregnancyIndex && viewModel.isPregnant) {
                    selectedRiskFactors[which] = true
                    (dialog as? AlertDialog)?.listView?.setItemChecked(which, true)
                    return@setMultiChoiceItems
                }
                selectedRiskFactors[which] = isChecked
                if (isChecked && which == notApplicableIndex) {
                    selectedRiskFactors.indices
                        .filter { it != notApplicableIndex }
                        .forEach { selectedRiskFactors[it] = false }
                } else if (isChecked && notApplicableIndex >= 0) {
                    selectedRiskFactors[notApplicableIndex] = false
                }
                val listView = (dialog as? AlertDialog)?.listView
                selectedRiskFactors.indices.forEach { index ->
                    listView?.setItemChecked(index, selectedRiskFactors[index])
                }
            }
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                refreshRiskFactorText()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun applyRiskFactorSelection(ids: List<Int>, values: List<String>) {
        if (riskFactorOptions.isEmpty()) return
        selectedRiskFactors = BooleanArray(riskFactorOptions.size) { index ->
            val option = riskFactorOptions[index]
            ids.contains(option.id) || values.any { saved ->
                saved.equals(option.code, ignoreCase = true) ||
                    saved.equals(option.label, ignoreCase = true)
            }
        }
        refreshRiskFactorText()
    }

    /**
     * Pre-selects the "Pregnancy" risk-factor option when the beneficiary's ben-registration
     * answer to "Are you Pregnant?" is Yes.  No-op for male beneficiaries (PREGNANCY is not
     * in [riskFactorOptions] for them) or when the ben is not pregnant.
     */
    private fun autoSelectPregnancyIfApplicable() {
        if (!viewModel.isPregnant) return
        val pregnancyIndex = riskFactorOptions.indexOfFirst { it.code == "PREGNANCY" }
        if (pregnancyIndex >= 0) {
            selectedRiskFactors[pregnancyIndex] = true
            refreshRiskFactorText()
        }
    }

    private fun refreshRiskFactorText() {
        binding.etRiskFactors.setText(getSelectedRiskFactorOptions().joinToString(", ") { it.label })
        binding.tilRiskFactors.error = null
    }

    private fun getSelectedRiskFactors(): List<String> =
        getSelectedRiskFactorOptions().map { it.label }

    private fun getSelectedRiskFactorOptions(): List<CodedOption> =
        riskFactorOptions.filterIndexed { index, _ ->
            selectedRiskFactors.getOrNull(index) == true
        }

    private fun getPresentAbsentSelection(group: RadioGroup): CodedOption? {
        val selectedId = group.checkedRadioButtonId
        if (selectedId == View.NO_ID) return null
        return when (selectedId) {
            (group.getChildAt(0) as? RadioButton)?.id -> getPresentAbsentOptions()[0]
            (group.getChildAt(1) as? RadioButton)?.id -> getPresentAbsentOptions()[1]
            else -> null
        }
    }

    private fun setPresentAbsentRadioGroupValue(group: RadioGroup, id: Int?, value: String?) {
        val optionId = id ?: getPresentAbsentOptions().firstOrNull { option ->
            value.equals(option.code, ignoreCase = true) ||
                value.equals(option.label, ignoreCase = true) ||
                value.matchesLegacyPresentAbsent(option.code)
        }?.id
        if (optionId == null) {
            group.clearCheck()
            return
        }
        val childIndex = getPresentAbsentOptions().indexOfFirst { it.id == optionId }
        if (childIndex !in 0 until group.childCount) {
            group.clearCheck()
            return
        }
        val radioButton = group.getChildAt(childIndex) as? RadioButton
        if (radioButton != null) {
            group.check(radioButton.id)
        }
    }

    private fun getPresentAbsentOptions(): List<CodedOption> = listOf(
        CodedOption(1, "PRESENT", getString(R.string.present)),
        CodedOption(2, "ABSENT", getString(R.string.absent))
    )

    private fun getHivStatusOptions(): List<CodedOption> = listOf(
        CodedOption(1, "POSITIVE", getString(R.string.positive)),
        CodedOption(2, "REACTIVE", getString(R.string.reactive)),
        CodedOption(3, "NEGATIVE", getString(R.string.negative)),
        CodedOption(4, "UNKNOWN", getString(R.string.unknown))
    )

    private fun getHivStatusSelection(): CodedOption? {
        val selectedText = binding.etHivStatus.text?.toString()?.trim().orEmpty()
        return getHivStatusOptions().firstOrNull {
            selectedText.equals(it.label, ignoreCase = true) ||
                selectedText.equals(it.code, ignoreCase = true)
        }
    }

    private fun setHivStatusValue(id: Int?, value: String?) {
        val option = getHivStatusOptions().firstOrNull { it.id == id } ?: getHivStatusOptions().firstOrNull {
            value.equals(it.code, ignoreCase = true) ||
                value.equals(it.label, ignoreCase = true) ||
                value.matchesLegacyHivStatus(it.code)
        } ?: getHivStatusOptions().first { it.code == "UNKNOWN" }
        binding.etHivStatus.setText(option.label, false)
    }

    private fun String?.matchesLegacyPresentAbsent(code: String): Boolean {
        val normalized = this?.trim()?.lowercase() ?: return false
        return when (code) {
            "PRESENT" -> normalized in listOf("present", "yes")
            "ABSENT" -> normalized in listOf("absent", "no")
            else -> false
        }
    }

    private fun String?.matchesLegacyHivStatus(code: String): Boolean {
        val normalized = this?.trim()?.lowercase() ?: return false
        return when (code) {
            "POSITIVE" -> normalized == "positive"
            "REACTIVE" -> normalized == "reactive"
            "NEGATIVE" -> normalized == "negative"
            "UNKNOWN" -> normalized == "unknown"
            else -> false
        }
    }

    private fun getRiskFactorOptions(): List<CodedOption> {
        val labels = resources.getStringArray(R.array.key_population_risk_factor_options)
        val codes = listOf(
            "PREGNANCY",
            "LACTATING_MOTHER",
            "ANTI_TNF_TREATMENT",
            "BRONCHIAL_ASTHMA",
            "CANCER",
            "CARDIOVASCULAR_DISORDER",
            "CONTACT_OF_KNOWN_TB_PATIENTS",
            "COPD",
            "COVID_RECOVERED_PATIENTS",
            "DIABETES",
            "DIALYSIS",
            "HEALTH_CARE_WORKER",
            "HYPERTENSIVE",
            "LIVER_IMPAIRMENT",
            "MIGRANT",
            "MINER",
            "PALLIATIVE_CARE",
            "PATIENT_ON_IMMUNOSUPPRESSANTS",
            "PRISON",
            "ILLEGAL_IMMIGRANT",
            "RENAL_IMPAIRMENT",
            "TRANSPLANTATION",
            "URBAN_SLUM",
            "HISTORY_OF_ADULT_BCG_VACCINATION",
            "UNDERNOURISHED_MALNOURISHED",
            "ELDERLY",
            "WORKPLACE_SETTINGS",
            "TEA_GARDEN_WORKER",
            "CONSTRUCTION_SITE_WORKER",
            "CONGREGATE_SETTINGS",
            "ATTENDEES_OF_DE_ADDICTION_CENTERS",
            "INDOOR_AIR_POLLUTION_EXPOSURE",
            "MARGINALIZED_POPULATIONS_AT_RISK_OF_HIV",
            "LGBTQAI_PLUS_PLUS",
            "SUBSTANCE_ABUSE",
            "TOBACCO_SMOKER",
            "SILICA_EXPOSURE_SILICOSIS",
            "OTHER",
            "NOT_APPLICABLE"
        )
        val all = labels.mapIndexed { index, label ->
            CodedOption(index + 1, codes.getOrElse(index) { label.uppercase().replace(" ", "_") }, label)
        }
        // Hide pregnancy-specific options for male beneficiaries
        return if (viewModel.isMale) {
            all.filter { it.code != "PREGNANCY" && it.code != "LACTATING_MOTHER" }
        } else {
            all
        }
    }

    private fun setRadioGroupEnabled(group: RadioGroup, isEnabled: Boolean) {
        group.isEnabled = isEnabled
        for (index in 0 until group.childCount) {
            group.getChildAt(index).isEnabled = isEnabled
        }
    }

    override fun onStart() {
        super.onStart()
        activity?.let {
            when (it) {
//                is HomeActivity -> it.updateActionBar(
//                    R.drawable.ic__ncd,
//                    getString(R.string.vital_screen)
//                ).also { _ -> it.setToolbarNavigationVisible(!viewModel.autoFlow) }
//                is VolunteerActivity -> it.updateActionBar(
//                    R.drawable.ic__ncd,
//                    getString(R.string.vital_screen)
//                ).also { _ -> it.setToolbarNavigationVisible(!viewModel.autoFlow) }



            is HomeActivity -> it.updateActionBar(R.drawable.ic__ben, getString(R.string.vital_screen))
            is VolunteerActivity -> it.updateActionBar(R.drawable.ic__ben, getString(R.string.vital_screen))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyAutoFlowBackPolicyOnResume(
            isAutoFlow = viewModel.autoFlow,
            allowBack = true
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        referralAlert?.dismiss()
        referralAlert = null
        _binding = null
    }

    private fun showReferralAlert(onConfirmed: () -> Unit) {
        if (referralAlert?.isShowing == true) return
        referralAlert = AlertDialog.Builder(requireContext())
            .setMessage(getString(R.string.refer_to_hwc_alert))
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                dialog.dismiss()
                onConfirmed()
            }
            .setOnDismissListener {
                referralAlert = null
            }
            .show()
    }

    private fun Double.stripTrailingZeros(): String {
        return if (this % 1.0 == 0.0) toInt().toString() else toString()
    }

}
