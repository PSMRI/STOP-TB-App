package org.piramalswasthya.stoptb.ui.home_activity.all_ben.new_ben_registration.anthropometry

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.InputFilter
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.databinding.FragmentAnthropometryBinding
import org.piramalswasthya.stoptb.model.AgeUnit
import org.piramalswasthya.stoptb.model.BenRegCache
import org.piramalswasthya.stoptb.model.Gender
import org.piramalswasthya.stoptb.ui.home_activity.HomeActivity
import org.piramalswasthya.stoptb.ui.volunteer.VolunteerActivity
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.work.WorkerUtils
import javax.inject.Inject

@AndroidEntryPoint
class AnthropometryFragment : Fragment() {

    @Inject lateinit var preferenceDao: PreferenceDao

    private companion object {
        const val MIN_WEIGHT_KG = 1.0
        const val MAX_WEIGHT_KG = 250.0
        const val MIN_HEIGHT_CM = 30.0
        const val MAX_HEIGHT_CM = 250.0
        const val MIN_TEMPERATURE_F = 90.0
        const val MAX_TEMPERATURE_F = 110.0
        const val FEVER_TEMPERATURE_F = 99.0
    }

    private var _binding: FragmentAnthropometryBinding? = null
    private val binding: FragmentAnthropometryBinding
        get() = _binding!!

    private val viewModel: AnthropometryViewModel by viewModels()
    private var highTemperatureAlertShown = false
    private var isFormLocked = false
    private var isUpdatingTemperatureSelection = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnthropometryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.etWeight.filters = arrayOf(decimalInputFilter())
        binding.etHeight.filters = arrayOf(decimalInputFilter())
        binding.etTemperature.filters = arrayOf(decimalInputFilter())

        viewModel.benName.observe(viewLifecycleOwner) {
            binding.tvBenName.text = it
        }
        viewModel.benAgeGender.observe(viewLifecycleOwner) {
            binding.tvAgeGender.text = it
        }
        viewModel.existingAnthropometry.observe(viewLifecycleOwner) { ben ->
            ben ?: return@observe
            // Lock form FIRST so that setText below does not trigger the HWC alert in view mode
            lockFormIfExistingData(ben)
            binding.tvAgeGender.text = formatAgeGender(ben)
            binding.etWeight.setText(ben.weight?.formatOneDecimal().orEmpty())
            binding.etHeight.setText(ben.height?.formatOneDecimal().orEmpty())
            binding.etBmi.setText(ben.bmi?.formatOneDecimal().orEmpty())
            binding.etTemperature.setText(ben.temperature?.formatOneDecimal().orEmpty())
            selectTemperatureRange(ben.temperature)
        }

        binding.etWeight.doAfterTextChanged {
            validateWeight(showBlankError = false)
            updateBmi()
        }
        binding.etHeight.doAfterTextChanged {
            validateHeight(showBlankError = false)
            updateBmi()
        }
        binding.etTemperature.doAfterTextChanged {
            validateTemperature(showBlankError = false)
            selectTemperatureRange(binding.etTemperature.text?.toString()?.toDoubleOrNull())
            if (isHighTemperature()) showHighTemperatureAlert()
        }

        binding.rgTemperature.setOnCheckedChangeListener { _, checkedId ->
            // isFormLocked = true when loading existing data in view mode —
            // don't overwrite the actual saved temperature value in that case
            if (isUpdatingTemperatureSelection) return@setOnCheckedChangeListener
            when (checkedId) {
                R.id.rbTempHypothermia -> if (!isFormLocked) binding.etTemperature.setText("96.0")
                R.id.rbTempNormal -> if (!isFormLocked) binding.etTemperature.setText("98.0")
                R.id.rbTempHigh -> {
                    if (!isFormLocked) binding.etTemperature.setText("100.0")
                    showHighTemperatureAlert()
                }
            }
        }

        binding.btnSubmit.setOnClickListener {
            if (!validateInput()) return@setOnClickListener
            if (isHighTemperature()) showHighTemperatureAlert()
            viewModel.saveAnthropometry(
                weightKg = binding.etWeight.text?.toString(),
                heightCm = binding.etHeight.text?.toString(),
                temperatureF = binding.etTemperature.text?.toString()
            )
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                AnthropometryViewModel.State.SAVING -> binding.loadingOverlay.visibility = View.VISIBLE
                AnthropometryViewModel.State.SAVE_SUCCESS -> {
                    binding.loadingOverlay.visibility = View.GONE
                    WorkerUtils.triggerCampAwarePushWorker(requireContext(), preferenceDao)
                    Toast.makeText(requireContext(), R.string.save_successful, Toast.LENGTH_SHORT).show()
                    when {
                        viewModel.examineFlow -> {
                            // Examine flow — return to AllBenFragment so user picks the next form
                            val popped = findNavController().popBackStack(R.id.allBenFragment, false)
                            if (!popped) findNavController().navigate(R.id.allBenFragment, bundleOf("source" to 0))
                        }
                        viewModel.autoFlow -> {
                            val returnedToList = findNavController().popBackStack(R.id.allBenFragment, false)
                            if (!returnedToList) {
                                findNavController().navigate(
                                    R.id.allBenFragment,
                                    bundleOf("source" to 0)
                                )
                            }
                        }
                        else -> findNavController().popBackStack()
                    }
                    viewModel.resetState()
                }
                AnthropometryViewModel.State.SAVE_FAILED -> {
                    binding.loadingOverlay.visibility = View.GONE
                    Toast.makeText(
                        requireContext(),
                        R.string.something_went_wrong_try_again,
                        Toast.LENGTH_SHORT
                    ).show()
                    viewModel.resetState()
                }
                else -> binding.loadingOverlay.visibility = View.GONE
            }
        }
    }

    private fun updateBmi() {
        val bmi = viewModel.calculateBmi(
            heightCm = binding.etHeight.text?.toString(),
            weightKg = binding.etWeight.text?.toString()
        )
        binding.etBmi.setText(bmi?.formatOneDecimal().orEmpty())
    }

    private fun validateInput(): Boolean {
        return validateWeight(showBlankError = false) &&
            validateHeight(showBlankError = false) &&
            validateTemperature(showBlankError = false)
    }

    private fun validateWeight(showBlankError: Boolean): Boolean {
        return validateOptionalRange(
            value = binding.etWeight.text?.toString(),
            min = MIN_WEIGHT_KG,
            max = MAX_WEIGHT_KG,
            errorMessage = getString(R.string.enter_weight_between_1_250_kg),
            showBlankError = showBlankError,
            onErrorChanged = { binding.tilWeight.error = it }
        )
    }

    private fun validateHeight(showBlankError: Boolean): Boolean {
        return validateOptionalRange(
            value = binding.etHeight.text?.toString(),
            min = MIN_HEIGHT_CM,
            max = MAX_HEIGHT_CM,
            errorMessage = getString(R.string.enter_height_between_30_250_cm),
            showBlankError = showBlankError,
            onErrorChanged = { binding.tilHeight.error = it }
        )
    }

    private fun validateTemperature(showBlankError: Boolean): Boolean {
        return validateOptionalRange(
            value = binding.etTemperature.text?.toString(),
            min = MIN_TEMPERATURE_F,
            max = MAX_TEMPERATURE_F,
            errorMessage = getString(R.string.enter_temperature_between_90_110_f),
            showBlankError = showBlankError,
            onErrorChanged = { binding.tilTemperature.error = it }
        )
    }

    private fun validateOptionalRange(
        value: String?,
        min: Double,
        max: Double,
        errorMessage: String,
        showBlankError: Boolean,
        onErrorChanged: (String?) -> Unit
    ): Boolean {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isBlank()) {
            onErrorChanged(if (showBlankError) errorMessage else null)
            return !showBlankError
        }
        val valid = isWithinRange(trimmed, min, max)
        onErrorChanged(if (valid) null else errorMessage)
        return valid
    }

    private fun isWithinRange(value: String?, min: Double, max: Double): Boolean {
        val number = value?.trim()?.toDoubleOrNull() ?: return false
        return number in min..max
    }

    private fun formatAgeGender(ben: BenRegCache): String {
        val ageUnit = when (ben.ageUnit) {
            AgeUnit.DAYS -> getString(R.string.age_unit_days)
            AgeUnit.MONTHS -> getString(R.string.age_unit_months)
            AgeUnit.YEARS, null -> getString(R.string.age_unit_years)
        }
        val gender = when (ben.gender) {
            Gender.MALE -> getString(R.string.gender_male)
            Gender.FEMALE -> getString(R.string.gender_female)
            Gender.TRANSGENDER -> getString(R.string.gender_transgender)
            Gender.PREFER_NOT_TO_SAY -> getString(R.string.gender_prefer_not_to_say)
            null -> ""
        }
        return getString(R.string.anthropometry_age_gender_format, ben.age, ageUnit, gender)
    }

    private fun isHighTemperature(): Boolean =
        (binding.etTemperature.text?.toString()?.toDoubleOrNull() ?: 0.0) > FEVER_TEMPERATURE_F

    private fun showHighTemperatureAlert() {
        if (highTemperatureAlertShown) return
        if (isFormLocked) return  // view mode — don't show alert for already-referred beneficiary
        highTemperatureAlertShown = true
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.refer_to_hwc_alert)
            .setPositiveButton(R.string.ok) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun selectTemperatureRange(temperature: Double?) {
        isUpdatingTemperatureSelection = true
        when {
            temperature == null -> binding.rgTemperature.clearCheck()
            temperature < 97.0 -> binding.rbTempHypothermia.isChecked = true
            temperature > FEVER_TEMPERATURE_F -> binding.rbTempHigh.isChecked = true
            else -> binding.rbTempNormal.isChecked = true
        }
        isUpdatingTemperatureSelection = false
    }

    private fun lockFormIfExistingData(ben: org.piramalswasthya.stoptb.model.BenRegCache) {
        val hasSavedAnthropometry =
            ben.weight != null || ben.height != null || ben.bmi != null || ben.temperature != null
        if (!hasSavedAnthropometry) return

        isFormLocked = true  // set before any setText triggers doAfterTextChanged
        binding.etWeight.isEnabled = false
        binding.etHeight.isEnabled = false
        binding.etTemperature.isEnabled = false
        binding.rbTempHypothermia.isEnabled = false
        binding.rbTempNormal.isEnabled = false
        binding.rbTempHigh.isEnabled = false
        binding.btnSubmit.isEnabled = false

        val disabledGray = ContextCompat.getColor(requireContext(), R.color.read_only)
        val disabledGrayStateList = ColorStateList.valueOf(disabledGray)
        binding.rbTempHypothermia.setTextColor(disabledGray)
        binding.rbTempNormal.setTextColor(disabledGray)
        binding.rbTempHigh.setTextColor(disabledGray)
        binding.rbTempHypothermia.buttonTintList = disabledGrayStateList
        binding.rbTempNormal.buttonTintList = disabledGrayStateList
        binding.rbTempHigh.buttonTintList = disabledGrayStateList
        binding.btnSubmit.backgroundTintList = disabledGrayStateList
    }

    private fun decimalInputFilter(): InputFilter {
        val regex = Regex("^\\d{0,3}(\\.\\d{0,1})?$")
        return InputFilter { source: CharSequence, start: Int, end: Int, dest: Spanned, dstart: Int, dend: Int ->
            val current = dest.toString()
            val updated = current.substring(0, dstart) +
                source.subSequence(start, end).toString() +
                current.substring(dend)
            if (updated.isEmpty() || regex.matches(updated)) null else ""
        }
    }

    override fun onStart() {
        super.onStart()
        activity?.let {
            when (it) {
                is HomeActivity -> it.updateActionBar(
                    R.drawable.ic__ncd,
                    getString(R.string.anthropometry_screen)
                )
                is VolunteerActivity -> it.updateActionBar(
                    R.drawable.ic__ncd,
                    getString(R.string.anthropometry_screen)
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (viewModel.autoFlow) {
            (activity as? HomeActivity)?.setToolbarNavigationVisible(true)
            (activity as? VolunteerActivity)?.setToolbarNavigationVisible(true)
        }
        _binding = null
    }

    private fun Double.formatOneDecimal(): String = String.format("%.1f", this)

    private fun Double.stripTrailingZero(): String =
        if (this % 1.0 == 0.0) this.toInt().toString() else this.toString()
}
