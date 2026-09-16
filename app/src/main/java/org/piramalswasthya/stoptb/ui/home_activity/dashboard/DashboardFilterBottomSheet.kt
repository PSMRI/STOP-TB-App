package org.piramalswasthya.stoptb.ui.home_activity.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.databinding.BottomsheetDashboardFiltersBinding

@AndroidEntryPoint
class DashboardFilterBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "dashboard_filters"
    }

    private var _binding: BottomsheetDashboardFiltersBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels({ requireParentFragment() })

    private var draftDistrictId: Int = 0
    private var draftBlockId: Int = 0
    private var draftVillageId: Int = 0
    private var draftPeriodKey: String = DashboardViewModel.PERIOD_MONTH

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomsheetDashboardFiltersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val applied = viewModel.filters.value ?: DashboardFilterState()
        draftDistrictId = applied.districtId
        draftBlockId = applied.blockId
        draftVillageId = applied.villageId
        draftPeriodKey = applied.periodKey

        setupLocationDropdowns()
        setupPeriodDropdown()

        binding.btnCloseFilters.setOnClickListener { dismiss() }
        binding.btnResetFilters.setOnClickListener { resetDraft() }
        binding.btnApplyFilters.setOnClickListener {
            viewModel.applyFilters(
                DashboardFilterState(
                    districtId = draftDistrictId,
                    blockId = draftBlockId,
                    villageId = draftVillageId,
                    periodKey = draftPeriodKey
                )
            )
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog
        dialog?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun setupLocationDropdowns() {
        val districtNames = mutableListOf(getString(R.string.filter_all_districts))
        districtNames.addAll(viewModel.districtList.map { it.name })
        bindDropdown(binding.actvDistrict, districtNames) { position ->
            draftDistrictId = if (position == 0) 0 else viewModel.districtList[position - 1].id
        }
        val districtIndex = viewModel.districtList.indexOfFirst { it.id == draftDistrictId }
            .takeIf { draftDistrictId != 0 }?.plus(1) ?: 0
        binding.actvDistrict.setText(districtNames.getOrElse(districtIndex) { districtNames.first() }, false)

        val blockNames = mutableListOf(getString(R.string.filter_all_blocks))
        blockNames.addAll(viewModel.blockList.map { it.name })
        bindDropdown(binding.actvBlock, blockNames) { position ->
            draftBlockId = if (position == 0) 0 else viewModel.blockList[position - 1].id
        }
        val blockIndex = viewModel.blockList.indexOfFirst { it.id == draftBlockId }
            .takeIf { draftBlockId != 0 }?.plus(1) ?: 0
        binding.actvBlock.setText(blockNames.getOrElse(blockIndex) { blockNames.first() }, false)

        val villageNames = mutableListOf(getString(R.string.filter_all_villages))
        villageNames.addAll(viewModel.villageList.map { it.name })
        bindDropdown(binding.actvVillage, villageNames) { position ->
            draftVillageId = if (position == 0) 0 else viewModel.villageList[position - 1].id
        }
        val villageIndex = viewModel.villageList.indexOfFirst { it.id == draftVillageId }
            .takeIf { draftVillageId != 0 }?.plus(1) ?: 0
        binding.actvVillage.setText(villageNames.getOrElse(villageIndex) { villageNames.first() }, false)
    }

    private fun setupPeriodDropdown() {
        val labels = viewModel.periodKeys.map { periodLabel(it) }
        bindDropdown(binding.actvPeriod, labels) { position ->
            draftPeriodKey = viewModel.periodKeys[position]
        }
        val periodIndex = viewModel.periodKeys.indexOf(draftPeriodKey).coerceAtLeast(0)
        binding.actvPeriod.setText(labels[periodIndex], false)
    }

    private fun resetDraft() {
        draftDistrictId = 0
        draftBlockId = 0
        draftVillageId = 0
        draftPeriodKey = DashboardViewModel.PERIOD_MONTH
        binding.actvDistrict.setText(getString(R.string.filter_all_districts), false)
        binding.actvBlock.setText(getString(R.string.filter_all_blocks), false)
        binding.actvVillage.setText(getString(R.string.filter_all_villages), false)
        binding.actvPeriod.setText(periodLabel(draftPeriodKey), false)
    }

    private fun bindDropdown(
        view: AutoCompleteTextView,
        items: List<String>,
        onSelected: (Int) -> Unit,
    ) {
        view.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, items)
        )
        view.threshold = 0
        view.setOnClickListener {
            val current = view.text.toString()
            view.setText("", false)
            view.showDropDown()
            view.setOnDismissListener {
                if (view.text.isNullOrBlank()) {
                    view.setText(current, false)
                }
            }
        }
        view.setOnItemClickListener { _, _, position, _ ->
            onSelected(position)
            view.setText(items[position], false)
        }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
