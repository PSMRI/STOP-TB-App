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
        // Time Period dropdown
        val timePeriodAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            viewModel.timePeriodOptions
        )
        binding.actvTimePeriod.setAdapter(timePeriodAdapter)
        binding.actvTimePeriod.setOnItemClickListener { _, _, position, _ ->
            viewModel.setTimePeriod(viewModel.timePeriodOptions[position])
        }

        // Village dropdown
        val villages = viewModel.villageList
        val villageNames = mutableListOf("All Villages")
        villageNames.addAll(villages.map { it.name })

        val villageAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            villageNames
        )
        binding.actvVillage.setAdapter(villageAdapter)
        binding.actvVillage.setOnItemClickListener { _, _, position, _ ->
            if (position == 0) {
                viewModel.clearVillageFilter()
            } else {
                val village = villages[position - 1]
                viewModel.setVillage(village.name, village.id)
            }
        }
    }

    private fun observeData() {
        // TB Screening card
        viewModel.tbScreening.observe(viewLifecycleOwner) { data ->
            binding.tvTbScreeningTotal.text = data.total.toString()
            binding.tvTbScreeningMale.text = "Male: ${data.male}"
            binding.tvTbScreeningFemale.text = "Female: ${data.female}"
            binding.tvTbScreeningChildren.text = "Children: ${data.children}"
            binding.tvTbScreeningOthers.text = "Others: ${viewModel.getOthersCount(data)}"
        }

        // TB Suspected card
        viewModel.tbSuspected.observe(viewLifecycleOwner) { data ->
            binding.tvTbSuspectedTotal.text = data.total.toString()
            binding.tvTbSuspectedMale.text = "Male: ${data.male}"
            binding.tvTbSuspectedFemale.text = "Female: ${data.female}"
            binding.tvTbSuspectedChildren.text = "Children: ${data.children}"
            binding.tvTbSuspectedOthers.text = "Others: ${viewModel.getOthersCount(data)}"
        }

        // TB Confirmed card
        viewModel.tbConfirmed.observe(viewLifecycleOwner) { data ->
            binding.tvTbConfirmedTotal.text = data.total.toString()
            binding.tvTbConfirmedMale.text = "Male: ${data.male}"
            binding.tvTbConfirmedFemale.text = "Female: ${data.female}"
            binding.tvTbConfirmedChildren.text = "Children: ${data.children}"
            binding.tvTbConfirmedOthers.text = "Others: ${viewModel.getOthersCount(data)}"
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
