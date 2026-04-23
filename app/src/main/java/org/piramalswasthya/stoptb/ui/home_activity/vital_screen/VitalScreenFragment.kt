package org.piramalswasthya.stoptb.ui.home_activity.vital_screen

import android.os.Bundle
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
        binding.etTemperature.setOnItemClickListener { _, _, _, _ ->
            if (viewModel.shouldShowTemperatureReferral(binding.etTemperature.text?.toString())) {
                showReferralAlert()
            }
        }
        binding.etPulseRate.setOnItemClickListener { _, _, _, _ ->
            if (viewModel.shouldShowPulseReferral(binding.etPulseRate.text?.toString())) {
                showReferralAlert()
            }
        }
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
            .actionVitalScreenFragmentToTBSuspectedQuickFragment(viewModel.benId)
        findNavController().navigate(directions)
    }

    private fun validateFields(): Boolean {
        clearErrors()
        if (
            viewModel.shouldShowBpReferral(
                binding.etBpSystolic.text?.toString(),
                binding.etBpDiastolic.text?.toString()
            ) ||
            viewModel.shouldShowRbsReferral(binding.etRbs.text?.toString())
        ) {
            showReferralAlert()
        }
        return true
    }

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
                )
                is VolunteerActivity -> it.updateActionBar(
                    R.drawable.ic__ncd,
                    getString(R.string.vital_screen)
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun showReferralAlert() {
        AlertDialog.Builder(requireContext())
            .setMessage(getString(R.string.refer_to_hwc_alert))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun Double.stripTrailingZeros(): String {
        return if (this % 1.0 == 0.0) toInt().toString() else toString()
    }
}
