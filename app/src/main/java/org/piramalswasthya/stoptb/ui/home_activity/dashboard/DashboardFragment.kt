package org.piramalswasthya.stoptb.ui.home_activity.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
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
        val timePeriodAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            timePeriodLabels
        )
        binding.actvTimePeriod.setAdapter(timePeriodAdapter)
        binding.actvTimePeriod.setText(timePeriodLabels[0], false)
        binding.actvTimePeriod.setOnItemClickListener { _, _, position, _ ->
            viewModel.setTimePeriod(viewModel.timePeriodOptions[position])
            binding.actvTimePeriod.setText(timePeriodLabels[position], false)
        }

        // Village dropdown
        val villages = viewModel.villageList
        val villageNames = mutableListOf(getString(R.string.filter_all_villages))
        villageNames.addAll(villages.map { it.name })

        val villageAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            villageNames
        )
        binding.actvVillage.setAdapter(villageAdapter)
        binding.actvVillage.setText(villageNames[0], false)
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
            binding.tvTbScreeningMale.text = getString(R.string.label_male, data.male)
            binding.tvTbScreeningFemale.text = getString(R.string.label_female, data.female)
            binding.tvTbScreeningChildren.text = getString(R.string.label_children, data.children)
            binding.tvTbScreeningOthers.text = getString(R.string.label_others, data.others)
        }

        // TB Suspected card
        viewModel.tbSuspected.observe(viewLifecycleOwner) { data ->
            binding.tvTbSuspectedTotal.text = data.total.toString()
            binding.tvTbSuspectedMale.text = getString(R.string.label_male, data.male)
            binding.tvTbSuspectedFemale.text = getString(R.string.label_female, data.female)
            binding.tvTbSuspectedChildren.text = getString(R.string.label_children, data.children)
            binding.tvTbSuspectedOthers.text = getString(R.string.label_others, data.others)
        }

        // TB Confirmed card
        viewModel.tbConfirmed.observe(viewLifecycleOwner) { data ->
            binding.tvTbConfirmedTotal.text = data.total.toString()
            binding.tvTbConfirmedMale.text = getString(R.string.label_male, data.male)
            binding.tvTbConfirmedFemale.text = getString(R.string.label_female, data.female)
            binding.tvTbConfirmedChildren.text = getString(R.string.label_children, data.children)
            binding.tvTbConfirmedOthers.text = getString(R.string.label_others, data.others)
        }

        viewModel.digitalChestXray.observe(viewLifecycleOwner) { data ->
            binding.tvDigitalXrayTotal.text = data.total.toString()
            binding.tvDigitalXrayMale.text = getString(R.string.label_male, data.male)
            binding.tvDigitalXrayFemale.text = getString(R.string.label_female, data.female)
            binding.tvDigitalXrayChildren.text = getString(R.string.label_children, data.children)
            binding.tvDigitalXrayOthers.text = getString(R.string.label_others, data.others)
        }

        viewModel.sputumCollection.observe(viewLifecycleOwner) { data ->
            binding.tvSputumTotal.text = data.total.toString()
            binding.tvSputumMale.text = getString(R.string.label_male, data.male)
            binding.tvSputumFemale.text = getString(R.string.label_female, data.female)
            binding.tvSputumChildren.text = getString(R.string.label_children, data.children)
            binding.tvSputumOthers.text = getString(R.string.label_others, data.others)
        }

        viewModel.trueNat.observe(viewLifecycleOwner) { data ->
            binding.tvTrueNatTotal.text = data.total.toString()
            binding.tvTrueNatMale.text = getString(R.string.label_male, data.male)
            binding.tvTrueNatFemale.text = getString(R.string.label_female, data.female)
            binding.tvTrueNatChildren.text = getString(R.string.label_children, data.children)
            binding.tvTrueNatOthers.text = getString(R.string.label_others, data.others)
        }

        viewModel.liquidCulture.observe(viewLifecycleOwner) { data ->
            binding.tvLiquidCultureTotal.text = data.total.toString()
            binding.tvLiquidCultureMale.text = getString(R.string.label_male, data.male)
            binding.tvLiquidCultureFemale.text = getString(R.string.label_female, data.female)
            binding.tvLiquidCultureChildren.text = getString(R.string.label_children, data.children)
            binding.tvLiquidCultureOthers.text = getString(R.string.label_others, data.others)
        }

        viewModel.hwcReferral.observe(viewLifecycleOwner) { data ->
            binding.tvHwcReferralTotal.text = data.total.toString()
            binding.tvHwcReferralMale.text = getString(R.string.label_male, data.male)
            binding.tvHwcReferralFemale.text = getString(R.string.label_female, data.female)
            binding.tvHwcReferralChildren.text = getString(R.string.label_children, data.children)
            binding.tvHwcReferralOthers.text = getString(R.string.label_others, data.others)
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
}
