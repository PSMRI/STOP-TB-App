package org.piramalswasthya.stoptb.ui.home_activity.dashboard

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.card.MaterialCardView
import dagger.hilt.android.AndroidEntryPoint
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.custom_views.DonutChartView
import org.piramalswasthya.stoptb.databinding.FragmentDashboardBinding
import org.piramalswasthya.stoptb.databinding.ItemDashboardDemoRowBinding
import org.piramalswasthya.stoptb.databinding.ItemDashboardIndicatorBinding
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
        setupStaticContent()
        observeData()
    }

    private fun setupFilters() {
        bindFilterScope(viewModel.filters.value ?: DashboardFilterState())
        binding.btnOpenFilters.setOnClickListener {
            if (childFragmentManager.findFragmentByTag(DashboardFilterBottomSheet.TAG) == null) {
                DashboardFilterBottomSheet().show(childFragmentManager, DashboardFilterBottomSheet.TAG)
            }
        }
        binding.rowUnscreened.setOnClickListener {
            findNavController().navigate(
                org.piramalswasthya.stoptb.ui.volunteer.fragment.VolunteerHomeFragmentDirections
                    .actionVolunteerHomeFragmentToUnScreenedPeople()
            )
        }
        viewModel.filters.observe(viewLifecycleOwner) { state ->
            bindFilterScope(state)
        }
    }

    private fun setupStaticContent() {
        bindDemoRow(binding.rowScreenedMale, R.string.dashboard_demo_male, R.color.dashboard_demo_male)
        bindDemoRow(binding.rowScreenedFemale, R.string.dashboard_demo_female, R.color.dashboard_demo_female)
        bindDemoRow(binding.rowScreenedChildren, R.string.dashboard_demo_children, R.color.dashboard_demo_children)
        bindDemoRow(binding.rowScreenedOthers, R.string.dashboard_demo_others, R.color.dashboard_demo_others)
        bindDemoRow(binding.rowScreenedSenior, R.string.dashboard_demo_senior, R.color.dashboard_demo_senior)

        styleIndicator(
            card = binding.cardPresumptive,
            backgroundColor = R.color.dashboard_card_orange,
            iconBackground = R.drawable.bg_dashboard_icon_orange,
            accentColor = R.color.dashboard_icon_orange,
            icon = R.drawable.ic_health_symptom,
            title = R.string.dashboard_presumptive_summary,
            showSenior = false
        )
        styleIndicator(
            card = binding.cardPastHistory,
            backgroundColor = R.color.dashboard_card_teal,
            iconBackground = R.drawable.bg_dashboard_icon_teal,
            accentColor = R.color.dashboard_icon_teal,
            icon = R.drawable.ic_health_tuberculosis,
            title = R.string.dashboard_past_history_summary
        )
        styleIndicator(
            card = binding.cardAntiTb,
            backgroundColor = R.color.dashboard_card_orange,
            iconBackground = R.drawable.bg_dashboard_icon_orange,
            accentColor = R.color.dashboard_icon_orange,
            icon = R.drawable.ic_health_medicines,
            title = R.string.dashboard_anti_tb_summary
        )
        styleIndicator(
            card = binding.cardXray,
            backgroundColor = R.color.dashboard_card_orange,
            iconBackground = R.drawable.bg_dashboard_icon_orange,
            accentColor = R.color.dashboard_icon_orange,
            icon = R.drawable.ic_health_xray,
            title = R.string.dashboard_xray_summary
        )
        styleIndicator(
            card = binding.cardSputum,
            backgroundColor = R.color.dashboard_card_teal,
            iconBackground = R.drawable.bg_dashboard_icon_teal,
            accentColor = R.color.dashboard_icon_teal,
            icon = R.drawable.ic_health_medical_sample,
            title = R.string.dashboard_sputum_summary
        )
        styleIndicator(
            card = binding.cardTrueNat,
            backgroundColor = R.color.dashboard_card_orange,
            iconBackground = R.drawable.bg_dashboard_icon_orange,
            accentColor = R.color.dashboard_icon_orange,
            icon = R.drawable.ic_health_test_tubes,
            title = R.string.dashboard_mtb_summary
        )
        styleIndicator(
            card = binding.cardLiquidCulture,
            backgroundColor = R.color.dashboard_card_orange,
            iconBackground = R.drawable.bg_dashboard_icon_orange,
            accentColor = R.color.dashboard_icon_orange,
            icon = R.drawable.ic_health_test_tubes,
            title = R.string.dashboard_liquid_culture_summary
        )
        styleIndicator(
            card = binding.cardHwc,
            backgroundColor = R.color.dashboard_card_teal,
            iconBackground = R.drawable.bg_dashboard_icon_teal,
            accentColor = R.color.dashboard_icon_teal,
            icon = R.drawable.ic_health_rural_post,
            title = R.string.dashboard_hwc_summary
        )
        styleIndicator(
            card = binding.cardConfirmed,
            backgroundColor = R.color.dashboard_card_teal,
            iconBackground = R.drawable.bg_dashboard_icon_teal,
            accentColor = R.color.dashboard_icon_teal,
            icon = R.drawable.ic_health_tuberculosis,
            title = R.string.dashboard_confirmed_summary
        )
        styleIndicator(
            card = binding.cardNikshay,
            backgroundColor = R.color.dashboard_card_teal,
            iconBackground = R.drawable.bg_dashboard_icon_green,
            accentColor = R.color.dashboard_icon_teal,
            icon = R.drawable.ic_health_register_book,
            title = R.string.dashboard_nikshay_summary,
            expandable = false
        )
        styleIndicator(
            card = binding.cardAbha,
            backgroundColor = R.color.dashboard_card_blue,
            iconBackground = R.drawable.bg_dashboard_icon_blue,
            accentColor = R.color.dashboard_icon_blue,
            icon = R.drawable.ic_health_data_security,
            title = R.string.dashboard_abha_summary,
            expandable = false
        )
    }

    private fun observeData() {
        viewModel.coverage.observe(viewLifecycleOwner) { stats ->
            bindCoverage(stats)
        }

        viewModel.tbScreening.observe(viewLifecycleOwner) { data ->
            bindScreenedPopulation(data)
        }

        viewModel.presumptiveTb.observe(viewLifecycleOwner) {
            bindBreakdown(binding.cardPresumptive, it)
        }
        viewModel.pastHistoryTb.observe(viewLifecycleOwner) {
            bindBreakdown(binding.cardPastHistory, it)
        }
        viewModel.antiTbDrugs.observe(viewLifecycleOwner) {
            bindBreakdown(binding.cardAntiTb, it)
        }
        viewModel.digitalChestXray.observe(viewLifecycleOwner) {
            bindBreakdown(binding.cardXray, it)
        }
        viewModel.sputumCollection.observe(viewLifecycleOwner) {
            bindBreakdown(binding.cardSputum, it)
        }
        viewModel.trueNat.observe(viewLifecycleOwner) {
            bindBreakdown(binding.cardTrueNat, it)
        }
        viewModel.liquidCulture.observe(viewLifecycleOwner) {
            bindBreakdown(binding.cardLiquidCulture, it)
        }
        viewModel.hwcReferral.observe(viewLifecycleOwner) {
            bindBreakdown(binding.cardHwc, it)
        }
        viewModel.tbConfirmed.observe(viewLifecycleOwner) {
            bindBreakdown(binding.cardConfirmed, it)
        }
        viewModel.nikshayCount.observe(viewLifecycleOwner) {
            binding.cardNikshay.tvIndicatorCount.text = it.toString()
        }
        viewModel.abhaCount.observe(viewLifecycleOwner) {
            binding.cardAbha.tvIndicatorCount.text = it.toString()
        }
    }

    private fun bindCoverage(stats: CoverageStats) {
        val coverageColorRes = when {
            stats.coveragePercent >= 70 -> R.color.dashboard_coverage_high
            stats.coveragePercent >= 40 -> R.color.dashboard_coverage_mid
            else -> R.color.dashboard_coverage_low
        }
        val coverageColor = color(coverageColorRes)
        binding.donutCoverage.setChart(
            segments = listOf(
                DonutChartView.Segment(stats.screened.toFloat(), coverageColor),
                DonutChartView.Segment(stats.unscreened.toFloat(), color(R.color.dashboard_donut_track))
            )
        )
        binding.tvCoverageCount.text = getString(
            R.string.dashboard_count_of_total,
            stats.screened,
            stats.population
        )
        binding.tvCoveragePercent.text = getString(R.string.dashboard_percent, stats.coveragePercent)
        binding.tvCoveragePercent.setTextColor(coverageColor)
        binding.tvCoveragePopulation.text = stats.population.toString()
        binding.tvCoverageScreened.text = getString(
            R.string.dashboard_value_with_percent,
            stats.screened,
            stats.coveragePercent
        )
        binding.tvCoverageUnscreened.text = getString(
            R.string.dashboard_value_with_percent,
            stats.unscreened,
            stats.unscreenedPercent
        )
        binding.tvCoverageFormula.text = getString(
            R.string.dashboard_coverage_formula,
            stats.screened,
            stats.population,
            stats.coveragePercent
        )
    }

    private fun bindScreenedPopulation(data: TbGenderBreakdown) {
        binding.tvScreenedDemoCount.text = data.total.toString()
        binding.donutScreenedDemo.setChart(
            segments = listOf(
                DonutChartView.Segment(data.male.toFloat(), color(R.color.dashboard_demo_male)),
                DonutChartView.Segment(data.female.toFloat(), color(R.color.dashboard_demo_female)),
                DonutChartView.Segment(data.others.toFloat(), color(R.color.dashboard_demo_others)),
            )
        )
        binding.rowScreenedMale.tvDemoValue.text = data.male.toString()
        binding.rowScreenedFemale.tvDemoValue.text = data.female.toString()
        binding.rowScreenedChildren.tvDemoValue.text = data.children.toString()
        binding.rowScreenedOthers.tvDemoValue.text = data.others.toString()
        binding.rowScreenedSenior.tvDemoValue.text = data.seniorCitizen.toString()
    }

    private fun styleIndicator(
        card: ItemDashboardIndicatorBinding,
        @ColorRes backgroundColor: Int,
        @DrawableRes iconBackground: Int,
        @ColorRes accentColor: Int,
        @DrawableRes icon: Int,
        @StringRes title: Int,
        showSenior: Boolean = true,
        expandable: Boolean = true,
    ) {
        (card.root as MaterialCardView).setCardBackgroundColor(color(backgroundColor))
        card.flIndicatorIcon.setBackgroundResource(iconBackground)
        card.ivIndicatorIcon.setImageResource(icon)
        card.tvIndicatorCount.setTextColor(color(accentColor))
        card.tvIndicatorTitle.setText(title)

        bindDemoRow(card.rowMale, R.string.dashboard_demo_male, R.color.dashboard_demo_male)
        bindDemoRow(card.rowFemale, R.string.dashboard_demo_female, R.color.dashboard_demo_female)
        bindDemoRow(card.rowChildren, R.string.dashboard_demo_children, R.color.dashboard_demo_children)
        bindDemoRow(card.rowOthers, R.string.dashboard_demo_others, R.color.dashboard_demo_others)
        bindDemoRow(card.rowSenior, R.string.dashboard_demo_senior, R.color.dashboard_demo_senior)
        card.seniorSection.visibility = if (showSenior) View.VISIBLE else View.GONE
        card.viewDemographicDivider.visibility = if (expandable) View.VISIBLE else View.GONE
        card.btnViewDemographic.visibility = if (expandable) View.VISIBLE else View.GONE
        card.layoutDemographicDetails.visibility = View.GONE
        card.ivDemographicChevron.rotation = 0f
        if (expandable) {
            card.btnViewDemographic.setOnClickListener { toggleDetails(card) }
        }
    }

    private fun bindBreakdown(card: ItemDashboardIndicatorBinding, data: TbGenderBreakdown) {
        card.tvIndicatorCount.text = data.total.toString()
        card.rowMale.tvDemoValue.text = data.male.toString()
        card.rowFemale.tvDemoValue.text = data.female.toString()
        card.rowChildren.tvDemoValue.text = data.children.toString()
        card.rowOthers.tvDemoValue.text = data.others.toString()
        card.rowSenior.tvDemoValue.text = data.seniorCitizen.toString()
    }

    private fun bindDemoRow(
        row: ItemDashboardDemoRowBinding,
        @StringRes label: Int,
        @ColorRes dotColor: Int,
    ) {
        row.tvDemoLabel.setText(label)
        row.demoDot.backgroundTintList = ColorStateList.valueOf(color(dotColor))
    }

    private fun toggleDetails(card: ItemDashboardIndicatorBinding) {
        val expand = card.layoutDemographicDetails.visibility != View.VISIBLE
        card.layoutDemographicDetails.visibility = if (expand) View.VISIBLE else View.GONE
        card.ivDemographicChevron.animate()
            .rotation(if (expand) 180f else 0f)
            .setDuration(180)
            .start()
    }

    private fun bindFilterScope(state: DashboardFilterState) {
        val scopeName = when {
            state.villageId != 0 -> viewModel.villageList.firstOrNull { it.id == state.villageId }?.name
            state.blockId != 0 -> viewModel.blockList.firstOrNull { it.id == state.blockId }?.name
            state.districtId != 0 -> viewModel.districtList.firstOrNull { it.id == state.districtId }?.name
            else -> null
        } ?: getString(R.string.filter_all_villages)
        val periodLabel = periodLabel(state.periodKey)
        binding.tvFilterScope.text = scopeName
        binding.tvFilterScopePeriod.text = periodLabel
        updateScreenedPeriodLabel(periodLabel)
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

    private fun updateScreenedPeriodLabel(periodLabel: String) {
        binding.tvScreenedPeriodLabel.text =
            getString(R.string.dashboard_screened_period_label, periodLabel)
    }

    private fun color(@ColorRes colorRes: Int): Int =
        ContextCompat.getColor(requireContext(), colorRes)

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
