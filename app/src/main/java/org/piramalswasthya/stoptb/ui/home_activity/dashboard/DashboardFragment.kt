package org.piramalswasthya.stoptb.ui.home_activity.dashboard

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.databinding.FragmentDashboardBinding
import org.piramalswasthya.stoptb.ui.home_activity.HomeActivity

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels()

    // Kept as fields so onResume can re-apply them after Android state restoration
    // resets AutoCompleteTextView filtering (causing dropdown to show only 1 item).
    private var timePeriodAdapter: ArrayAdapter<String>? = null
    private var villageAdapter: ArrayAdapter<String>? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupFilters()
        observeData()
    }

    /**
     * Re-apply adapters every time the fragment becomes visible.
     *
     * Android's view-state restoration calls AutoCompleteTextView.setText() with
     * filter=true, which hides all dropdown options except the one matching the
     * saved text ("Today" / "All Villages"). Re-setting the adapter in onResume
     * (which runs AFTER onViewStateRestored) clears the stale filter state.
     */
    override fun onResume() {
        super.onResume()
        timePeriodAdapter?.let { binding.actvTimePeriod.setAdapter(it) }
        villageAdapter?.let { binding.actvVillage.setAdapter(it) }
    }

    private fun setupFilters() {
        // Time Period dropdown - localized labels
        val timePeriodLabels = listOf(
            getString(R.string.filter_today),
            getString(R.string.month_january),
            getString(R.string.month_february),
            getString(R.string.month_march),
            getString(R.string.month_april),
            getString(R.string.month_may),
            getString(R.string.month_june),
            getString(R.string.month_july),
            getString(R.string.month_august),
            getString(R.string.month_september),
            getString(R.string.month_october),
            getString(R.string.month_november),
            getString(R.string.month_december),
        )
        timePeriodAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            timePeriodLabels
        )
        binding.actvTimePeriod.setAdapter(timePeriodAdapter)
        // Restore previously selected time period (ViewModel persists across view recreation)
        val savedPeriod = viewModel.selectedTimePeriod.value ?: viewModel.timePeriodOptions[0]
        val savedPeriodIndex = viewModel.timePeriodOptions.indexOf(savedPeriod).coerceAtLeast(0)
        binding.actvTimePeriod.setText(timePeriodLabels[savedPeriodIndex], false)
        // Show ALL items when dropdown opens (prevent AutoCompleteTextView from filtering by selected text)
        binding.actvTimePeriod.setOnClickListener {
            timePeriodAdapter?.filter?.filter("")
            binding.actvTimePeriod.showDropDown()
        }
        binding.actvTimePeriod.setOnItemClickListener { _, _, position, _ ->
            viewModel.setTimePeriod(viewModel.timePeriodOptions[position])
            binding.actvTimePeriod.setText(timePeriodLabels[position], false)
        }

        // Village dropdown
        val villages = viewModel.villageList
        val villageNames = mutableListOf(getString(R.string.filter_all_villages))
        villageNames.addAll(villages.map { it.name })

        villageAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            villageNames
        )
        binding.actvVillage.setAdapter(villageAdapter)
        // Restore previously selected village (ViewModel persists across view recreation)
        binding.actvVillage.setText(viewModel.selectedVillageName.value ?: villageNames[0], false)
        // Show ALL items when dropdown opens
        binding.actvVillage.setOnClickListener {
            villageAdapter?.filter?.filter("")
            binding.actvVillage.showDropDown()
        }
        binding.actvVillage.setOnItemClickListener { _, _, position, _ ->
            if (position == 0) {
                viewModel.clearVillageFilter()
            } else {
                val village = villages[position - 1]
                viewModel.setVillage(village.name, village.id)
            }
            binding.actvVillage.setText(villageNames[position], false)
        }
    }

    private fun observeData() {
        // TB Screening card
        viewModel.tbScreening.observe(viewLifecycleOwner) { data ->
            binding.tvTbScreeningTotal.text = data.total.toString()
           // binding.tvTbScreeningMale.text = getString(R.string.label_male, data.male)
            binding.tvTbScreeningMale.text = requireContext().getBoldSecondValue(R.string.label_male, data.male)
            binding.tvTbScreeningFemale.text = requireContext().getBoldSecondValue(R.string.label_female, data.female)
            binding.tvTbScreeningChildren.text = requireContext().getBoldSecondValue(R.string.label_children, data.children)
            binding.tvTbScreeningOthers.text = requireContext().getBoldSecondValue(R.string.label_others, data.others)

        }

        // TB Suspected card
        viewModel.tbSuspected.observe(viewLifecycleOwner) { data ->
            binding.tvTbSuspectedTotal.text = data.total.toString()
            binding.tvTbSuspectedMale.text = requireContext().getBoldSecondValue(R.string.label_male, data.male)
            binding.tvTbSuspectedFemale.text = requireContext().getBoldSecondValue(R.string.label_female, data.female)
            binding.tvTbSuspectedChildren.text = requireContext().getBoldSecondValue(R.string.label_children, data.children)
            binding.tvTbSuspectedOthers.text = requireContext().getBoldSecondValue(R.string.label_others, data.others)
        }

        // TB Confirmed card
        viewModel.tbConfirmed.observe(viewLifecycleOwner) { data ->
            binding.tvTbConfirmedTotal.text = data.total.toString()
            binding.tvTbConfirmedMale.text = requireContext().getBoldSecondValue(R.string.label_male, data.male)
            binding.tvTbConfirmedFemale.text = requireContext().getBoldSecondValue(R.string.label_female, data.female)
            binding.tvTbConfirmedChildren.text = requireContext().getBoldSecondValue(R.string.label_children, data.children)
            binding.tvTbConfirmedOthers.text = requireContext().getBoldSecondValue(R.string.label_others, data.others)
        }

        viewModel.digitalChestXray.observe(viewLifecycleOwner) { data ->
            binding.tvDigitalXrayTotal.text = data.total.toString()
            binding.tvDigitalXrayMale.text = requireContext().getBoldSecondValue(R.string.label_male, data.male)
            binding.tvDigitalXrayFemale.text = requireContext().getBoldSecondValue(R.string.label_female, data.female)
            binding.tvDigitalXrayChildren.text = requireContext().getBoldSecondValue(R.string.label_children, data.children)
            binding.tvDigitalXrayOthers.text = requireContext().getBoldSecondValue(R.string.label_others, data.others)
            binding.tvDigitalXraySeniorCitizen.text = requireContext().getBoldSecondValue(R.string.label_senior_citizen, data.seniorCitizen)
        }

        viewModel.sputumCollection.observe(viewLifecycleOwner) { data ->
            binding.tvSputumTotal.text = data.total.toString()
            binding.tvSputumMale.text = requireContext().getBoldSecondValue(R.string.label_male, data.male)
            binding.tvSputumFemale.text = requireContext().getBoldSecondValue(R.string.label_female, data.female)
            binding.tvSputumChildren.text = requireContext().getBoldSecondValue(R.string.label_children, data.children)
            binding.tvSputumOthers.text = requireContext().getBoldSecondValue(R.string.label_others, data.others)
            binding.tvSputumSeniorCitizen.text = requireContext().getBoldSecondValue(R.string.label_senior_citizen, data.seniorCitizen)
        }

        viewModel.trueNat.observe(viewLifecycleOwner) { data ->
            binding.tvTrueNatTotal.text = data.total.toString()
            binding.tvTrueNatMale.text = requireContext().getBoldSecondValue(R.string.label_male, data.male)
            binding.tvTrueNatFemale.text = requireContext().getBoldSecondValue(R.string.label_female, data.female)
            binding.tvTrueNatChildren.text = requireContext().getBoldSecondValue(R.string.label_children, data.children)
            binding.tvTrueNatOthers.text = requireContext().getBoldSecondValue(R.string.label_others, data.others)
            binding.tvTrueNatSeniorCitizen.text = requireContext().getBoldSecondValue(R.string.label_senior_citizen, data.seniorCitizen)
        }

        viewModel.liquidCulture.observe(viewLifecycleOwner) { data ->
            binding.tvLiquidCultureTotal.text = data.total.toString()
            binding.tvLiquidCultureMale.text = requireContext().getBoldSecondValue(R.string.label_male, data.male)
            binding.tvLiquidCultureFemale.text = requireContext().getBoldSecondValue(R.string.label_female, data.female)
            binding.tvLiquidCultureChildren.text = requireContext().getBoldSecondValue(R.string.label_children, data.children)
            binding.tvLiquidCultureOthers.text = requireContext().getBoldSecondValue(R.string.label_others, data.others)
            binding.tvLiquidCultureSeniorCitizen.text = requireContext().getBoldSecondValue(R.string.label_senior_citizen, data.seniorCitizen)
        }

        viewModel.hwcReferral.observe(viewLifecycleOwner) { data ->
            binding.tvHwcReferralTotal.text = data.total.toString()
            binding.tvHwcReferralMale.text = requireContext().getBoldSecondValue(R.string.label_male, data.male)
            binding.tvHwcReferralFemale.text = requireContext().getBoldSecondValue(R.string.label_female, data.female)
            binding.tvHwcReferralChildren.text = requireContext().getBoldSecondValue(R.string.label_children, data.children)
            binding.tvHwcReferralOthers.text = requireContext().getBoldSecondValue(R.string.label_others, data.others)
            binding.tvHwcReferralSeniorCitizen.text = requireContext().getBoldSecondValue(R.string.label_senior_citizen, data.seniorCitizen)
        }

        // NIKSHAY count
        viewModel.nikshayCount.observe(viewLifecycleOwner) {
            binding.tvNikshayCount.text = it.toString()
        }

        // ABHA count
        viewModel.abhaCount.observe(viewLifecycleOwner) {
            binding.tvAbhaCount.text = it.toString()
        }
    }

    override fun onStart() {
        super.onStart()
        (activity as? HomeActivity)?.updateActionBar(
            R.drawable.ic_dashboard,
            getString(R.string.dashboard)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    private fun Context.getBoldSecondValue(
        @StringRes labelRes: Int,
        value: Any
    ): SpannableString {

        val fullText = getString(labelRes, value)
        val valueText = value.toString()

        val start = fullText.lastIndexOf(valueText)
        val end = start + valueText.length

        return SpannableString(fullText).apply {
            setSpan(
                StyleSpan(Typeface.BOLD),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }
}
