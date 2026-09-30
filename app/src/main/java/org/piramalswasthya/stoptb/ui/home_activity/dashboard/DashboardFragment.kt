package org.piramalswasthya.stoptb.ui.home_activity.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ListPopupWindow
import com.google.android.material.textfield.TextInputLayout
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
        setupFilters()
        observeData()
        viewModel.filters.observe(viewLifecycleOwner) { bindFilterSelection(it) }
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

    private fun setupFilters() {
        val periodLabels = viewModel.periodKeys.map { periodLabel(it) }
        bindDropdown(binding.actvPeriod, periodLabels) { position ->
            val periodKey = viewModel.periodKeys[position]
            val current = viewModel.filters.value ?: DashboardFilterState()
            if (current.periodKey != periodKey) {
                viewModel.applyFilters(current.copy(periodKey = periodKey))
            }
        }

        val villageNames = mutableListOf(getString(R.string.filter_all_villages))
        villageNames.addAll(viewModel.villageList.map { viewModel.villageDisplayName(it) })
        bindDropdown(binding.actvVillage, villageNames) { position ->
            val villageId = if (position == 0) 0 else viewModel.villageList[position - 1].id
            val current = viewModel.filters.value ?: DashboardFilterState()
            if (current.villageId != villageId) {
                viewModel.applyFilters(current.copy(villageId = villageId))
            }
        }
    }

    private fun bindFilterSelection(state: DashboardFilterState) {
        val periodText = periodLabel(state.periodKey)
        if (binding.actvPeriod.text?.toString() != periodText) {
            binding.actvPeriod.setText(periodText, false)
        }
        val villageText = villageLabel(state.villageId)
        if (binding.actvVillage.text?.toString() != villageText) {
            binding.actvVillage.setText(villageText, false)
        }
        adapter.updatePeriod(periodText)
    }

    private fun villageLabel(villageId: Int): String {
        if (villageId == 0) return getString(R.string.filter_all_villages)
        val village = viewModel.villageList.firstOrNull { it.id == villageId }
            ?: return getString(R.string.filter_all_villages)
        return viewModel.villageDisplayName(village)
    }

    private fun bindDropdown(
        view: AutoCompleteTextView,
        items: List<String>,
        onSelected: (Int) -> Unit,
    ) {
        view.setAdapter(null)
        view.keyListener = null
        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                showChoicePopup(view, items, onSelected)
            }
            true
        }
        (view.parent?.parent as? TextInputLayout)?.setEndIconOnClickListener {
            showChoicePopup(view, items, onSelected)
        }
    }

    private fun showChoicePopup(
        anchor: AutoCompleteTextView,
        items: List<String>,
        onSelected: (Int) -> Unit,
    ) {
        if (items.isEmpty()) return
        val popup = ListPopupWindow(requireContext())
        popup.anchorView = anchor
        popup.width = anchor.width.coerceAtLeast(anchor.measuredWidth)
        popup.isModal = true
        popup.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, items)
        )
        popup.setOnItemClickListener { _, _, position, _ ->
            val index = position.coerceIn(items.indices)
            anchor.setText(items[index], false)
            onSelected(index)
            popup.dismiss()
        }
        popup.show()
    }

    private fun periodLabel(key: String): String {
        if (key.startsWith(DashboardViewModel.PERIOD_MONTH_PREFIX)) {
            val month = key.removePrefix(DashboardViewModel.PERIOD_MONTH_PREFIX).toIntOrNull()
            val monthNames = listOf(
                R.string.month_january,
                R.string.month_february,
                R.string.month_march,
                R.string.month_april,
                R.string.month_may,
                R.string.month_june,
                R.string.month_july,
                R.string.month_august,
                R.string.month_september,
                R.string.month_october,
                R.string.month_november,
                R.string.month_december,
            )
            if (month != null && month in monthNames.indices) {
                return getString(monthNames[month])
            }
        }
        return getString(
            when (key) {
                DashboardViewModel.PERIOD_TODAY -> R.string.filter_today
                DashboardViewModel.PERIOD_YESTERDAY -> R.string.filter_yesterday
                DashboardViewModel.PERIOD_WEEK -> R.string.filter_this_week
                DashboardViewModel.PERIOD_MONTH -> R.string.filter_this_month
                else -> R.string.filter_all_time
            }
        )
    }

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
