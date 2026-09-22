package org.piramalswasthya.stoptb.ui.home_activity.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.databinding.FragmentDashboardBinding
import org.piramalswasthya.stoptb.ui.home_activity.HomeActivity

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels()
    private val adapter = DashboardListAdapter(
        onOpenFilters = {
            if (childFragmentManager.findFragmentByTag(DashboardFilterBottomSheet.TAG) == null) {
                DashboardFilterBottomSheet().show(childFragmentManager, DashboardFilterBottomSheet.TAG)
            }
        },
        onUnscreenedClick = {
            findNavController().navigate(
                org.piramalswasthya.stoptb.ui.volunteer.fragment.VolunteerHomeFragmentDirections
                    .actionVolunteerHomeFragmentToUnScreenedPeople()
            )
        }
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupList()
        observeData()
        bindFilterScope(viewModel.filters.value ?: DashboardFilterState())
        viewModel.filters.observe(viewLifecycleOwner) { bindFilterScope(it) }
    }

    private fun setupList() {
        val spanCount = if (resources.configuration.smallestScreenWidthDp >= 600) 2 else 1
        val layoutManager = GridLayoutManager(requireContext(), spanCount)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int =
                if (position == 0) spanCount else 1
        }
        layoutManager.isMeasurementCacheEnabled = false
        binding.dashboardList.layoutManager = layoutManager
        binding.dashboardList.adapter = adapter
        binding.dashboardList.setHasFixedSize(false)
        binding.dashboardList.setItemViewCacheSize(8)
        binding.dashboardList.itemAnimator = null
        binding.dashboardList.addItemDecoration(
            DashboardGridSpacingDecoration(
                spanCount = spanCount,
                gapPx = (12 * resources.displayMetrics.density).toInt()
            )
        )
    }

//    Observe data

    private fun observeData() {
        viewModel.coverage.observe(viewLifecycleOwner) { adapter.updateCoverage(it) }
        viewModel.tbScreening.observe(viewLifecycleOwner) { adapter.updateScreened(it) }
        viewModel.presumptiveTb.observe(viewLifecycleOwner) {
            adapter.updateIndicator(DashboardListAdapter.ID_PRESUMPTIVE, it)
        }
        viewModel.pastHistoryTb.observe(viewLifecycleOwner) {
            adapter.updateIndicator(DashboardListAdapter.ID_PAST_HISTORY, it)
        }
        viewModel.antiTbDrugs.observe(viewLifecycleOwner) {
            adapter.updateIndicator(DashboardListAdapter.ID_ANTI_TB, it)
        }
        viewModel.digitalChestXray.observe(viewLifecycleOwner) {
            adapter.updateIndicator(DashboardListAdapter.ID_XRAY, it)
        }
        viewModel.sputumCollection.observe(viewLifecycleOwner) {
            adapter.updateIndicator(DashboardListAdapter.ID_SPUTUM, it)
        }
        viewModel.trueNat.observe(viewLifecycleOwner) {
            adapter.updateIndicator(DashboardListAdapter.ID_MTB, it)
            adapter.updateIndicator(DashboardListAdapter.ID_RIF, it)
        }
        viewModel.liquidCulture.observe(viewLifecycleOwner) {
            adapter.updateIndicator(DashboardListAdapter.ID_LIQUID, it)
        }
        viewModel.hwcReferral.observe(viewLifecycleOwner) {
            adapter.updateIndicator(DashboardListAdapter.ID_HWC, it)
        }
        viewModel.tbConfirmed.observe(viewLifecycleOwner) {
            adapter.updateIndicator(DashboardListAdapter.ID_CONFIRMED, it)
        }
        viewModel.nikshayCount.observe(viewLifecycleOwner) {
            adapter.updateIndicator(DashboardListAdapter.ID_NIKSHAY, it)
        }
        viewModel.abhaCount.observe(viewLifecycleOwner) {
            adapter.updateIndicator(DashboardListAdapter.ID_ABHA, it)
        }
    }

    private fun bindFilterScope(state: DashboardFilterState) {
        val scopeName = when {
            state.villageId != 0 -> viewModel.villageList.firstOrNull { it.id == state.villageId }?.name
            state.blockId != 0 -> viewModel.blockList.firstOrNull { it.id == state.blockId }?.name
            state.districtId != 0 -> viewModel.districtList.firstOrNull { it.id == state.districtId }?.name
            else -> null
        } ?: getString(R.string.filter_all_villages)
        adapter.updateScope(scopeName, periodLabel(state.periodKey))
    }

    private fun periodLabel(key: String): String = getString(
        when (key) {
            DashboardViewModel.PERIOD_TODAY -> R.string.filter_today
            DashboardViewModel.PERIOD_YESTERDAY -> R.string.filter_yesterday
            DashboardViewModel.PERIOD_WEEK -> R.string.filter_this_week
            DashboardViewModel.PERIOD_MONTH -> R.string.filter_this_month
            DashboardViewModel.PERIOD_YEAR -> R.string.filter_this_year
            else -> R.string.filter_all_time
        }
    )

    override fun onResume() {
        super.onResume()
        (activity as? HomeActivity)?.updateActionBar(
            R.drawable.ic_dashboard,
            getString(R.string.dashboard)
        )
    }

    override fun onDestroyView() {
        binding.dashboardList.adapter = null
        super.onDestroyView()
        _binding = null
    }
}
