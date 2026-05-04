package org.piramalswasthya.stoptb.ui.home_activity.vital_screen

import android.os.Bundle
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
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
import org.piramalswasthya.stoptb.ui.home_activity.HomeActivity
import org.piramalswasthya.stoptb.ui.volunteer.VolunteerActivity

@AndroidEntryPoint
class VitalScreenFragment : Fragment() {

    private var _binding: FragmentVitalScreenBinding? = null
    private val binding: FragmentVitalScreenBinding
        get() = _binding!!

    private val viewModel: VitalScreenViewModel by viewModels()
    private var referralAlert: AlertDialog? = null

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
        setupBmiCalculation()
        observeUi()
        binding.btnSubmit.setOnClickListener {
            submitVitals()
        }
        binding.btnSkip.setOnClickListener {
            skipVitals()
        }
    }

    private fun setupInputLimits() {
        binding.etHeight.filters = arrayOf(InputFilter.LengthFilter(6))
        binding.etWeight.filters = arrayOf(InputFilter.LengthFilter(6))
        binding.etTemperature.filters = arrayOf(InputFilter.LengthFilter(5))
        binding.etPulseRate.filters = arrayOf(InputFilter.LengthFilter(3))
        binding.etBpSystolic.filters = arrayOf(InputFilter.LengthFilter(3))
        binding.etBpDiastolic.filters = arrayOf(InputFilter.LengthFilter(3))
        binding.etRbs.filters = arrayOf(InputFilter.LengthFilter(6))
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
            binding.btnSkip.visibility = if (hasExistingVitals) View.GONE else View.VISIBLE
            setFormEditable(!hasExistingVitals)
            vital ?: return@observe
            binding.etTemperature.setText(viewModel.getTemperatureDisplayValue(vital.temperature), false)
            binding.etPulseRate.setText(viewModel.getPulseDisplayValue(vital.pulseRate), false)
            binding.etBpSystolic.setText(vital.bpSystolic?.toString().orEmpty())
            binding.etBpDiastolic.setText(vital.bpDiastolic?.toString().orEmpty())
            binding.etRbs.setText(vital.rbs?.stripTrailingZeros())
            binding.etHeight.setText(vital.height?.stripTrailingZeros())
            binding.etWeight.setText(vital.weight?.stripTrailingZeros())
            binding.etBmi.setText(vital.bmi?.stripTrailingZeros().orEmpty())
        }
        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                VitalScreenViewModel.State.SAVING -> {
                    binding.loadingOverlay.visibility = View.VISIBLE
                    binding.btnSubmit.isEnabled = false
                    binding.btnSkip.isEnabled = false
                }
                VitalScreenViewModel.State.SAVE_SUCCESS -> {
                    binding.loadingOverlay.visibility = View.GONE
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.vitals_saved_successfully),
                        Toast.LENGTH_SHORT
                    ).show()
                    navigateAfterVitals()
                }
                VitalScreenViewModel.State.SAVE_FAILED -> {
                    binding.loadingOverlay.visibility = View.GONE
                    binding.btnSubmit.isEnabled = true
                    binding.btnSkip.isEnabled = true
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
                    binding.btnSkip.isEnabled = true
                }
            }
        }
    }

    private fun setFormEditable(isEditable: Boolean) {
        binding.etHeight.isEnabled = isEditable
        binding.etWeight.isEnabled = isEditable
        binding.etTemperature.isEnabled = isEditable
        binding.etPulseRate.isEnabled = isEditable
        binding.etBpSystolic.isEnabled = isEditable
        binding.etBpDiastolic.isEnabled = isEditable
        binding.etRbs.isEnabled = isEditable
    }

    private fun setupDropdowns() {
        binding.etTemperature.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,
                resources.getStringArray(R.array.vital_temperature_options)
            )
        )
        binding.etPulseRate.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,
                resources.getStringArray(R.array.vital_pulse_rate_options)
            )
        )
        enableDropdownInteraction(binding.tilTemperature, binding.etTemperature)
        enableDropdownInteraction(binding.tilPulseRate, binding.etPulseRate)
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

    private fun setupBmiCalculation() {
        val bmiWatcher: (CharSequence?) -> Unit = {
            val bmi = viewModel.calculateBmi(
                binding.etHeight.text?.toString(),
                binding.etWeight.text?.toString()
            )
            binding.etBmi.setText(bmi?.stripTrailingZeros().orEmpty())
        }
        binding.etHeight.doAfterTextChanged(bmiWatcher)
        binding.etWeight.doAfterTextChanged(bmiWatcher)
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
        viewModel.saveVitals(
            temperatureOption = binding.etTemperature.text?.toString(),
            pulseRateOption = binding.etPulseRate.text?.toString(),
            bpSystolic = binding.etBpSystolic.text?.toString(),
            bpDiastolic = binding.etBpDiastolic.text?.toString(),
            height = binding.etHeight.text?.toString(),
            weight = binding.etWeight.text?.toString(),
            bmi = binding.etBmi.text?.toString(),
            rbs = binding.etRbs.text?.toString()
        )
    }

    private fun skipVitals() {
        navigateAfterVitals()
    }

    private fun navigateAfterVitals() {
        if (!viewModel.autoFlow) {
            findNavController().navigateUp()
            return
        }
        val directions = VitalScreenFragmentDirections
            .actionVitalScreenFragmentToTBSuspectedQuickFragment(
                benId = viewModel.benId,
                autoFlow = true
            )
        findNavController().navigate(directions)
    }

    private fun validateFields(): Boolean {
        clearErrors()
        var isValid = true

        val temperature = binding.etTemperature.text?.toString()?.trim().orEmpty()
        if (temperature.isNotEmpty()) {
            val value = parseTemperature(temperature)
            if (value == null || value !in 90.0..110.0) {
                binding.tilTemperature.error = "Enter temperature between 90 and 110"
                isValid = false
            }
        }

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

        val heightText = binding.etHeight.text?.toString()?.trim().orEmpty()
        if (heightText.isNotEmpty() && validateDecimal(
                value = heightText,
                inputLayout = binding.tilHeight,
                label = "Height",
                min = 35.0,
                max = 250.0
            ) == null
        ) {
            isValid = false
        }

        val weightText = binding.etWeight.text?.toString()?.trim().orEmpty()
        if (weightText.isNotEmpty() && validateDecimal(
                value = weightText,
                inputLayout = binding.tilWeight,
                label = "Weight",
                min = 2.0,
                max = 250.0
            ) == null
        ) {
            isValid = false
        }

        return isValid
    }

    private fun parseTemperature(value: String): Double? =
        value.removePrefix(">=").trim().toDoubleOrNull()

    private fun parsePulse(value: String): Int? {
        return when (value.trim()) {
            "Less than 60", "less than 60" -> 59
            "60-70" -> 65
            "70-80" -> 75
            "More than 90", "more than 90" -> 91
            else -> value.trim().toIntOrNull()
        }
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
        viewModel.shouldShowTemperatureReferral(binding.etTemperature.text?.toString()) ||
            viewModel.shouldShowPulseReferral(binding.etPulseRate.text?.toString()) ||
            viewModel.shouldShowBpReferral(
                binding.etBpSystolic.text?.toString(),
                binding.etBpDiastolic.text?.toString()
            ) ||
            viewModel.shouldShowRbsReferral(binding.etRbs.text?.toString())

    private fun clearErrors() {
        binding.tilTemperature.error = null
        binding.tilPulseRate.error = null
        binding.tilBpSystolic.error = null
        binding.tilBpDiastolic.error = null
        binding.tilRbs.error = null
        binding.tilHeight.error = null
        binding.tilWeight.error = null
        binding.tilBmi.error = null
    }

    override fun onStart() {
        super.onStart()
        activity?.let {
            when (it) {
                is HomeActivity -> it.updateActionBar(
                    R.drawable.ic__ncd,
                    getString(R.string.vital_screen)
                ).also { _ -> it.setToolbarNavigationVisible(!viewModel.autoFlow) }
                is VolunteerActivity -> it.updateActionBar(
                    R.drawable.ic__ncd,
                    getString(R.string.vital_screen)
                ).also { _ -> it.setToolbarNavigationVisible(!viewModel.autoFlow) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        referralAlert?.dismiss()
        referralAlert = null
        if (viewModel.autoFlow) {
            (activity as? HomeActivity)?.setToolbarNavigationVisible(true)
            (activity as? VolunteerActivity)?.setToolbarNavigationVisible(true)
        }
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
