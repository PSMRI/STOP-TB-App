package org.piramalswasthya.stoptb.ui.home_activity.dashboard

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
        setupDemoRow(binding.rowScreenedMale, R.string.dashboard_demo_male, R.color.dashboard_demo_male)
        setupDemoRow(binding.rowScreenedFemale, R.string.dashboard_demo_female, R.color.dashboard_demo_female)
        setupDemoRow(binding.rowScreenedPregnant, R.string.dashboard_demo_pregnant, R.color.dashboard_demo_pregnant)
        setupDemoRow(binding.rowScreenedChildren, R.string.dashboard_demo_children, R.color.dashboard_demo_children)
        setupDemoRow(binding.rowScreenedSenior, R.string.dashboard_demo_senior, R.color.dashboard_demo_senior)
        setupDemoRow(binding.rowScreenedTransgender, R.string.dashboard_demo_transgender, R.color.dashboard_demo_transgender)

        styleIndicator(
            card = binding.cardPresumptive,
            backgroundColor = R.color.dashboard_card_orange,
            iconBackground = R.drawable.bg_dashboard_icon_orange,
            accentColor = R.color.dashboard_icon_orange,
            icon = R.drawable.ic_health_symptom,
            title = R.string.dashboard_presumptive_summary,
            showSenior = true,
            showPregnant = true
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
            title = R.string.dashboard_xray_summary,
            showPregnant = false,
            classifications = DashboardClassifications.chestXray
        )
        styleIndicator(
            card = binding.cardSputum,
            backgroundColor = R.color.dashboard_card_teal,
            iconBackground = R.drawable.bg_dashboard_icon_teal,
            accentColor = R.color.dashboard_icon_teal,
            icon = R.drawable.ic_health_medical_sample,
            title = R.string.dashboard_sputum_summary,
            classifications = DashboardClassifications.sputum
        )
        styleIndicator(
            card = binding.cardTrueNat,
            backgroundColor = R.color.dashboard_card_orange,
            iconBackground = R.drawable.bg_dashboard_icon_orange,
            accentColor = R.color.dashboard_icon_orange,
            icon = R.drawable.ic_health_test_tubes,
            title = R.string.dashboard_mtb_summary,
            classifications = DashboardClassifications.mtb
        )
        styleIndicator(
            card = binding.cardRif,
            backgroundColor = R.color.dashboard_card_teal,
            iconBackground = R.drawable.bg_dashboard_icon_teal,
            accentColor = R.color.dashboard_icon_teal,
            icon = R.drawable.ic_health_test_tubes,
            title = R.string.dashboard_rif_summary,
            classifications = DashboardClassifications.rif
        )
        styleIndicator(
            card = binding.cardLiquidCulture,
            backgroundColor = R.color.dashboard_card_orange,
            iconBackground = R.drawable.bg_dashboard_icon_orange,
            accentColor = R.color.dashboard_icon_orange,
            icon = R.drawable.ic_health_test_tubes,
            title = R.string.dashboard_liquid_culture_summary,
            classifications = DashboardClassifications.liquidCulture
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
            card = binding.cardClinical,
            backgroundColor = R.color.dashboard_card_orange,
            iconBackground = R.drawable.bg_dashboard_icon_orange,
            accentColor = R.color.dashboard_icon_orange,
            icon = R.drawable.ic_health_medicines,
            title = R.string.dashboard_clinical_summary,
            classifications = DashboardClassifications.clinical
        )
        styleIndicator(
            card = binding.cardConfirmed,
            backgroundColor = R.color.dashboard_card_teal,
            iconBackground = R.drawable.bg_dashboard_icon_teal,
            accentColor = R.color.dashboard_icon_teal,
            icon = R.drawable.ic_health_tuberculosis,
            title = R.string.dashboard_confirmed_summary,
            classifications = DashboardClassifications.confirmed
        )
        styleIndicator(
            card = binding.cardTpt,
            backgroundColor = R.color.dashboard_card_orange,
            iconBackground = R.drawable.bg_dashboard_icon_orange,
            accentColor = R.color.dashboard_icon_orange,
            icon = R.drawable.ic_tpt_module,
            title = R.string.dashboard_tpt_summary,
            showPregnant = false,
            classifications = DashboardClassifications.tpt
        )
        styleIndicator(
            card = binding.cardNikshay,
            backgroundColor = R.color.dashboard_card_teal,
            iconBackground = R.drawable.bg_dashboard_icon_green,
            accentColor = R.color.dashboard_icon_teal,
            icon = R.drawable.ic_health_register_book,
            title = R.string.dashboard_nikshay_summary
        )
        styleIndicator(
            card = binding.cardAbha,
            backgroundColor = R.color.dashboard_card_blue,
            iconBackground = R.drawable.bg_dashboard_icon_blue,
            accentColor = R.color.dashboard_icon_blue,
            icon = R.drawable.ic_health_data_security,
            title = R.string.dashboard_abha_summary
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
            bindBreakdown(binding.cardRif, it)
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
        bindBreakdown(binding.cardClinical, TbGenderBreakdown())
        bindBreakdown(binding.cardTpt, TbGenderBreakdown())
        viewModel.nikshayCount.observe(viewLifecycleOwner) {
            binding.cardNikshay.tvIndicatorCount.text = it.toString()
            bindBreakdown(binding.cardNikshay, TbGenderBreakdown(total = it))
        }
        viewModel.abhaCount.observe(viewLifecycleOwner) {
            binding.cardAbha.tvIndicatorCount.text = it.toString()
            bindBreakdown(binding.cardAbha, TbGenderBreakdown(total = it))
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
                DonutChartView.Segment(0f, color(R.color.dashboard_demo_pregnant)),
                DonutChartView.Segment(data.children.toFloat(), color(R.color.dashboard_demo_children)),
                DonutChartView.Segment(data.seniorCitizen.toFloat(), color(R.color.dashboard_demo_senior)),
                DonutChartView.Segment(data.others.toFloat(), color(R.color.dashboard_demo_transgender)),
            )
        )
        bindDemoCount(binding.rowScreenedMale, data.male, null)
        bindDemoCount(binding.rowScreenedFemale, data.female, null)
        bindDemoCount(binding.rowScreenedPregnant, 0, null)
        bindDemoCount(binding.rowScreenedChildren, data.children, null)
        bindDemoCount(binding.rowScreenedSenior, data.seniorCitizen, null)
        bindDemoCount(binding.rowScreenedTransgender, data.others, null)
    }

    private fun styleIndicator(
        card: ItemDashboardIndicatorBinding,
        @ColorRes backgroundColor: Int,
        @DrawableRes iconBackground: Int,
        @ColorRes accentColor: Int,
        @DrawableRes icon: Int,
        @StringRes title: Int,
        showSenior: Boolean = true,
        showPregnant: Boolean = true,
        classifications: List<DashboardClassDef>? = null,
    ) {
        (card.root as MaterialCardView).setCardBackgroundColor(color(backgroundColor))
        card.flIndicatorIcon.setBackgroundResource(iconBackground)
        card.ivIndicatorIcon.setImageResource(icon)
        card.tvIndicatorCount.setTextColor(color(accentColor))
        card.tvIndicatorTitle.setText(title)
        card.root.tag = classifications

        setupDemoRow(card.rowMale, R.string.dashboard_demo_male, R.color.dashboard_demo_male, classifications)
        setupDemoRow(card.rowFemale, R.string.dashboard_demo_female, R.color.dashboard_demo_female, classifications)
        setupDemoRow(card.rowPregnant, R.string.dashboard_demo_pregnant, R.color.dashboard_demo_pregnant, classifications)
        setupDemoRow(card.rowChildren, R.string.dashboard_demo_children, R.color.dashboard_demo_children, classifications)
        setupDemoRow(card.rowSenior, R.string.dashboard_demo_senior, R.color.dashboard_demo_senior, classifications)
        setupDemoRow(card.rowTransgender, R.string.dashboard_demo_transgender, R.color.dashboard_demo_transgender, classifications)

        card.pregnantSection.visibility = if (showPregnant) View.VISIBLE else View.GONE
        card.seniorSection.visibility = if (showSenior) View.VISIBLE else View.GONE
        card.layoutDemographicDetails.visibility = View.GONE
        card.ivDemographicChevron.rotation = 0f
        card.btnViewDemographic.setOnClickListener { toggleDetails(card) }
    }

    private fun setupDemoRow(
        row: ItemDashboardDemoRowBinding,
        @StringRes label: Int,
        @ColorRes dotColor: Int,
        classifications: List<DashboardClassDef>? = null,
    ) {
        row.tvDemoLabel.setText(label)
        row.demoDot.backgroundTintList = ColorStateList.valueOf(color(dotColor))
        fillDemoDetail(row, 0, classifications)
        row.btnDemoHeader.setOnClickListener { toggleDemoRow(row) }
    }

    private fun bindBreakdown(card: ItemDashboardIndicatorBinding, data: TbGenderBreakdown) {
        @Suppress("UNCHECKED_CAST")
        val classifications = card.root.tag as? List<DashboardClassDef>
        card.tvIndicatorCount.text = data.total.toString()
        bindDemoCount(card.rowMale, data.male, classifications)
        bindDemoCount(card.rowFemale, data.female, classifications)
        bindDemoCount(card.rowPregnant, 0, classifications)
        bindDemoCount(card.rowChildren, data.children, classifications)
        bindDemoCount(card.rowSenior, data.seniorCitizen, classifications)
        bindDemoCount(card.rowTransgender, data.others, classifications)
    }

    private fun bindDemoCount(
        row: ItemDashboardDemoRowBinding,
        count: Int,
        classifications: List<DashboardClassDef>?,
    ) {
        row.tvDemoValue.text = count.toString()
        fillDemoDetail(row, count, classifications)
    }

    private fun fillDemoDetail(
        row: ItemDashboardDemoRowBinding,
        count: Int,
        classifications: List<DashboardClassDef>?,
    ) {
        row.layoutDemoDetail.removeAllViews()
        val inflater = layoutInflater
        if (classifications.isNullOrEmpty()) {
            addClassRow(inflater, row.layoutDemoDetail, getString(R.string.dashboard_demo_total), count, R.color.dashboard_ink)
            return
        }
        DashboardClassifications.split(count, classifications).forEachIndexed { index, (def, value) ->
            addClassRow(
                inflater,
                row.layoutDemoDetail,
                getString(def.labelRes),
                value,
                DashboardClassifications.palette[index % DashboardClassifications.palette.size]
            )
        }
    }

    private fun addClassRow(
        inflater: LayoutInflater,
        parent: ViewGroup,
        label: String,
        value: Int,
        @ColorRes dotColor: Int,
    ) {
        val row = inflater.inflate(R.layout.item_dashboard_class_row, parent, false)
        row.findViewById<View>(R.id.classDot).backgroundTintList =
            ColorStateList.valueOf(color(dotColor))
        row.findViewById<TextView>(R.id.tvClassLabel).text = label
        row.findViewById<TextView>(R.id.tvClassValue).text = value.toString()
        parent.addView(row)
    }

    private fun toggleDemoRow(row: ItemDashboardDemoRowBinding) {
        val expand = row.layoutDemoDetail.visibility != View.VISIBLE
        row.layoutDemoDetail.visibility = if (expand) View.VISIBLE else View.GONE
        row.ivDemoChevron.animate()
            .rotation(if (expand) 180f else 0f)
            .setDuration(180)
            .start()
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
