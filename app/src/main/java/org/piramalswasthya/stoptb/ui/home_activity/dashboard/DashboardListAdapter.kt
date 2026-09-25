package org.piramalswasthya.stoptb.ui.home_activity.dashboard

import android.content.res.ColorStateList
import android.graphics.Rect
import android.provider.Settings.Global.getString
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.custom_views.DonutChartView
import org.piramalswasthya.stoptb.databinding.ItemDashboardDemoAccordionBinding
import org.piramalswasthya.stoptb.databinding.ItemDashboardDemoRowBinding
import org.piramalswasthya.stoptb.databinding.ItemDashboardHeaderBinding
import org.piramalswasthya.stoptb.databinding.ItemDashboardIndicatorBinding

internal data class DashboardHeaderState(
    val coverage: CoverageStats = CoverageStats(),
    val screened: TbGenderBreakdown = TbGenderBreakdown(),
    val scopeName: String = "",
    val periodLabel: String = "",
)

internal data class DashboardIndicatorItem(
    val id: Int,
    @ColorRes val backgroundColor: Int,
    @DrawableRes val iconBackground: Int,
    @ColorRes val accentColor: Int,
    @DrawableRes val icon: Int,
    @StringRes val title: Int,
    val showPregnant: Boolean = true,
    val showSenior: Boolean = true,
    val classifications: List<DashboardClassDef>? = null,
    val data: TbGenderBreakdown = TbGenderBreakdown(),
    val expanded: Boolean = false,
)

internal class DashboardListAdapter(
    private val onOpenFilters: () -> Unit,
    private val onUnscreenedClick: () -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_INDICATOR = 1
        const val PAYLOAD_COUNT = "count"
        const val ID_PRESUMPTIVE = 1
        const val ID_PAST_HISTORY = 2
        const val ID_ANTI_TB = 3
        const val ID_XRAY = 4
        const val ID_SPUTUM = 5
        const val ID_MTB = 6
        const val ID_RIF = 7
        const val ID_LIQUID = 8
        const val ID_HWC = 9
        const val ID_CLINICAL = 10
        const val ID_CONFIRMED = 11
        const val ID_TPT = 12
        const val ID_NIKSHAY = 13
        const val ID_ABHA = 14
    }

    var header = DashboardHeaderState()
        private set

    private val items = defaultIndicators()
    private val openDemoRows = mutableMapOf<Int, MutableSet<Int>>()

    init {
        setHasStableIds(true)
    }

    fun defaultIndicators(): MutableList<DashboardIndicatorItem> = mutableListOf(
        DashboardIndicatorItem(
            ID_PRESUMPTIVE, R.color.dashboard_card_orange, R.drawable.bg_dashboard_icon_orange,
            R.color.dashboard_icon_orange, R.drawable.ic_health_symptom, R.string.dashboard_presumptive_summary
        ),
        DashboardIndicatorItem(
            ID_PAST_HISTORY, R.color.dashboard_card_teal, R.drawable.bg_dashboard_icon_teal,
            R.color.dashboard_icon_teal, R.drawable.ic_pass_history, R.string.dashboard_past_history_summary
        ),
        DashboardIndicatorItem(
            ID_ANTI_TB, R.color.dashboard_card_orange, R.drawable.bg_dashboard_icon_orange,
            R.color.dashboard_icon_orange, R.drawable.ic_health_medicines, R.string.dashboard_anti_tb_summary
        ),
        DashboardIndicatorItem(
            ID_XRAY, R.color.dashboard_card_orange, R.drawable.bg_dashboard_icon_orange,
            R.color.dashboard_icon_orange, R.drawable.ic_health_xray, R.string.dashboard_xray_summary,
            showPregnant = false, classifications = DashboardClassifications.chestXray
        ),
        DashboardIndicatorItem(
            ID_SPUTUM, R.color.dashboard_card_teal, R.drawable.bg_dashboard_icon_teal,
            R.color.dashboard_icon_teal, R.drawable.ic_health_medical_sample, R.string.dashboard_sputum_summary,
            classifications = DashboardClassifications.sputum
        ),
        DashboardIndicatorItem(
            ID_MTB, R.color.dashboard_card_orange, R.drawable.bg_dashboard_icon_orange,
            R.color.dashboard_icon_orange, R.drawable.ic_truenat_device, R.string.dashboard_mtb_summary,
            classifications = DashboardClassifications.mtb
        ),
        DashboardIndicatorItem(
            ID_RIF, R.color.dashboard_card_teal, R.drawable.bg_dashboard_icon_teal,
            R.color.dashboard_icon_teal, R.drawable.ic_truenat_device, R.string.dashboard_rif_summary,
            classifications = DashboardClassifications.rif
        ),
        DashboardIndicatorItem(
            ID_LIQUID, R.color.dashboard_card_orange, R.drawable.bg_dashboard_icon_orange,
            R.color.dashboard_icon_orange, R.drawable.ic_health_test_tubes, R.string.dashboard_liquid_culture_summary,
            classifications = DashboardClassifications.liquidCulture
        ),
        DashboardIndicatorItem(
            ID_HWC, R.color.dashboard_card_teal, R.drawable.bg_dashboard_icon_teal,
            R.color.dashboard_icon_teal, R.drawable.ic_health_rural_post, R.string.dashboard_hwc_summary
        ),
        DashboardIndicatorItem(
            ID_CLINICAL, R.color.dashboard_card_orange, R.drawable.bg_dashboard_icon_orange,
            R.color.dashboard_icon_orange, R.drawable.ic_clinical_assessment, R.string.dashboard_clinical_summary,
            classifications = DashboardClassifications.clinical
        ),
        DashboardIndicatorItem(
            ID_CONFIRMED, R.color.dashboard_card_teal, R.drawable.bg_dashboard_icon_teal,
            R.color.dashboard_icon_teal, R.drawable.ic_health_tuberculosis, R.string.dashboard_confirmed_summary,
            classifications = DashboardClassifications.confirmed
        ),
        DashboardIndicatorItem(
            ID_TPT, R.color.dashboard_card_orange, R.drawable.bg_dashboard_icon_orange,
            R.color.dashboard_icon_orange, R.drawable.ic_tpt_module, R.string.dashboard_tpt_summary,
            showPregnant = false, classifications = DashboardClassifications.tpt
        ),
        DashboardIndicatorItem(
            ID_NIKSHAY, R.color.dashboard_card_teal, R.drawable.bg_dashboard_icon_green,
            R.color.dashboard_icon_teal, R.drawable.ic_nikshay_id, R.string.dashboard_nikshay_summary
        ),
        DashboardIndicatorItem(
            ID_ABHA, R.color.dashboard_card_blue, R.drawable.bg_dashboard_icon_blue,
            R.color.dashboard_icon_blue, R.drawable.ic_abha_id_card, R.string.dashboard_abha_summary
        ),
    )

    fun updateHeader(state: DashboardHeaderState) {
        header = state
        notifyItemChanged(0)
    }

    fun updateCoverage(stats: CoverageStats) {
        header = header.copy(coverage = stats)
        notifyItemChanged(0)
    }

    fun updateScreened(data: TbGenderBreakdown) {
        header = header.copy(screened = data)
        notifyItemChanged(0)
    }

    fun updateScope(scopeName: String, periodLabel: String) {
        header = header.copy(scopeName = scopeName, periodLabel = periodLabel)
        notifyItemChanged(0)
    }

    fun updateIndicator(id: Int, data: TbGenderBreakdown) {
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return
        items[index] = items[index].copy(data = data)
        notifyItemChanged(index + 1)
    }

    override fun getItemViewType(position: Int): Int =
        if (position == 0) TYPE_HEADER else TYPE_INDICATOR

    override fun getItemId(position: Int): Long =
        if (position == 0) Long.MIN_VALUE else items[position - 1].id.toLong()

    override fun getItemCount(): Int = items.size + 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(ItemDashboardHeaderBinding.inflate(inflater, parent, false))
        } else {
            IndicatorHolder(ItemDashboardIndicatorBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        onBindViewHolder(holder, position, mutableListOf())
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        if (holder is HeaderHolder) {
            holder.bind(header, payloads)
        } else if (holder is IndicatorHolder) {
            holder.bind(items[position - 1], payloads)
        }
    }

    inner class HeaderHolder(
        private val binding: ItemDashboardHeaderBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        private var rowsReady = false

        fun bind(state: DashboardHeaderState, payloads: List<Any>) {
            if (!rowsReady) {
                setupHeaderRows()
                rowsReady = true
            }
            bindCoverage(state.coverage)
            bindScreened(state.screened)
            binding.tvFilterScope.text = itemView.context.getString(R.string.home_village_at_a_glance, state.scopeName.substringBefore("(").trim())
            binding.tvFilterScopePeriod.text = state.periodLabel
            binding.tvScreenedPeriodLabel.text = binding.root.context.getString(
                R.string.dashboard_screened_period_label,
                state.periodLabel
            )
            if (payloads.isEmpty()) {
                binding.btnOpenFilters.setOnClickListener { onOpenFilters() }
                binding.rowUnscreened.setOnClickListener { onUnscreenedClick() }
            }
        }

        private fun setupHeaderRows() {
            setupDemoRow(binding.rowScreenedMale, R.string.dashboard_demo_male, R.color.dashboard_demo_male)
            setupDemoRow(binding.rowScreenedFemale, R.string.dashboard_demo_female, R.color.dashboard_demo_female)
            setupDemoRow(binding.rowScreenedPregnant, R.string.dashboard_demo_pregnant, R.color.dashboard_demo_pregnant)
            setupDemoRow(binding.rowScreenedChildren, R.string.dashboard_demo_children, R.color.dashboard_demo_children)
            setupDemoRow(binding.rowScreenedSenior, R.string.dashboard_demo_senior, R.color.dashboard_demo_senior)
            setupDemoRow(binding.rowScreenedTransgender, R.string.dashboard_demo_transgender, R.color.dashboard_demo_transgender)
        }

        private fun bindCoverage(stats: CoverageStats) {
            val screenedColor = color(binding.root, R.color.dashboard_demo_male)
            val unscreenedColor = color(binding.root, R.color.dashboard_demo_female)
            binding.donutCoverage.setChart(
                segments = listOf(
                    DonutChartView.Segment(stats.screened.toFloat(), screenedColor),
                    DonutChartView.Segment(stats.unscreened.toFloat(), unscreenedColor)
                ),
                trackColor = unscreenedColor,
            )
            binding.tvCoveragePercent.text = binding.root.context.getString(
                R.string.dashboard_percent,
                stats.coveragePercent
            )
            binding.tvCoveragePercent.setTextColor(screenedColor)
            binding.tvCoveragePopulation.text = stats.population.toString()
            binding.tvCoverageScreened.text = binding.root.context.getString(
                R.string.dashboard_value_with_percent,
                stats.screened,
                stats.coveragePercent
            )
            binding.tvCoverageUnscreened.text = binding.root.context.getString(
                R.string.dashboard_value_with_percent,
                stats.unscreened,
                stats.unscreenedPercent
            )
            binding.tvCoverageFormula.text = binding.root.context.getString(
                R.string.dashboard_coverage_formula,
                stats.screened,
                stats.population,
                stats.coveragePercent
            )
        }

        private fun bindScreened(data: TbGenderBreakdown) {
            binding.tvScreenedDemoCount.text = data.total.toString()
            binding.donutScreenedDemo.setChart(
                segments = listOf(
                    DonutChartView.Segment(data.male.toFloat(), color(binding.root, R.color.dashboard_demo_male)),
                    DonutChartView.Segment(data.female.toFloat(), color(binding.root, R.color.dashboard_demo_female)),
                    DonutChartView.Segment(0f, color(binding.root, R.color.dashboard_demo_pregnant)),
                    DonutChartView.Segment(data.children.toFloat(), color(binding.root, R.color.dashboard_demo_children)),
                    DonutChartView.Segment(data.seniorCitizen.toFloat(), color(binding.root, R.color.dashboard_demo_senior)),
                    DonutChartView.Segment(data.others.toFloat(), color(binding.root, R.color.dashboard_demo_transgender)),
                )
            )
            bindDemoCount(binding.rowScreenedMale, data.male, null)
            bindDemoCount(binding.rowScreenedFemale, data.female, null)
            bindDemoCount(binding.rowScreenedPregnant, 0, null)
            bindDemoCount(binding.rowScreenedChildren, data.children, null)
            bindDemoCount(binding.rowScreenedSenior, data.seniorCitizen, null)
            bindDemoCount(binding.rowScreenedTransgender, data.others, null)
        }
    }

    inner class IndicatorHolder(
        private val card: ItemDashboardIndicatorBinding,
    ) : RecyclerView.ViewHolder(card.root) {

        private var accordion: ItemDashboardDemoAccordionBinding? = null
        private var boundItemId: Int = -1

        fun bind(item: DashboardIndicatorItem, payloads: List<Any>) {
            // Always fully rebind so filtered counts are never stuck at stale/zero payload state.
            boundItemId = item.id
            (card.root as MaterialCardView).setCardBackgroundColor(color(card.root, item.backgroundColor))
            card.flIndicatorIcon.setBackgroundResource(item.iconBackground)
            card.ivIndicatorIcon.setImageResource(item.icon)
            card.tvIndicatorCount.setTextColor(color(card.root, item.accentColor))
            card.tvIndicatorTitle.setText(item.title)
            card.tvIndicatorCount.text = item.data.total.toString()
            card.ivDemographicChevron.rotation = if (item.expanded) 180f else 0f
            card.btnViewDemographic.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                val index = pos - 1
                val expanded = !items[index].expanded
                items[index] = items[index].copy(expanded = expanded)
                val item = items[index]
                card.ivDemographicChevron.rotation = if (expanded) 180f else 0f
                if (expanded) {
                    val details = ensureAccordion()
                    details.root.visibility = View.VISIBLE
                    details.pregnantSection.visibility =
                        if (item.showPregnant) View.VISIBLE else View.GONE
                    details.seniorSection.visibility =
                        if (item.showSenior) View.VISIBLE else View.GONE
                    bindAccordion(details, item)
                } else {
                    accordion?.root?.visibility = View.GONE
                }
                relayoutItem(itemView)
            }
            if (item.expanded) {
                val details = ensureAccordion()
                details.root.visibility = View.VISIBLE
                details.pregnantSection.visibility = if (item.showPregnant) View.VISIBLE else View.GONE
                details.seniorSection.visibility = if (item.showSenior) View.VISIBLE else View.GONE
                bindAccordion(details, item)
            } else {
                accordion?.root?.visibility = View.GONE
            }
        }

        private fun ensureAccordion(): ItemDashboardDemoAccordionBinding {
            accordion?.let { return it }
            val inflated = card.stubDemographicDetails.inflate()
            val details = ItemDashboardDemoAccordionBinding.bind(inflated)
            accordion = details
            setupDemoRow(details.rowMale, R.string.dashboard_demo_male, R.color.dashboard_demo_male)
            setupDemoRow(details.rowFemale, R.string.dashboard_demo_female, R.color.dashboard_demo_female)
            setupDemoRow(details.rowPregnant, R.string.dashboard_demo_pregnant, R.color.dashboard_demo_pregnant)
            setupDemoRow(details.rowChildren, R.string.dashboard_demo_children, R.color.dashboard_demo_children)
            setupDemoRow(details.rowSenior, R.string.dashboard_demo_senior, R.color.dashboard_demo_senior)
            setupDemoRow(details.rowTransgender, R.string.dashboard_demo_transgender, R.color.dashboard_demo_transgender)
            return details
        }

        private fun bindAccordion(
            details: ItemDashboardDemoAccordionBinding,
            item: DashboardIndicatorItem,
        ) {
            val open = openDemoRows.getOrPut(item.id) { mutableSetOf() }
            bindDemoRowState(details.rowMale, item.data.male, item.classifications, 0, open)
            bindDemoRowState(details.rowFemale, item.data.female, item.classifications, 1, open)
            bindDemoRowState(details.rowPregnant, 0, item.classifications, 2, open)
            bindDemoRowState(details.rowChildren, item.data.children, item.classifications, 3, open)
            bindDemoRowState(details.rowSenior, item.data.seniorCitizen, item.classifications, 4, open)
            bindDemoRowState(details.rowTransgender, item.data.others, item.classifications, 5, open)
        }

        private fun bindDemoRowState(
            row: ItemDashboardDemoRowBinding,
            count: Int,
            classifications: List<DashboardClassDef>?,
            key: Int,
            openKeys: MutableSet<Int>,
        ) {
            row.tvDemoValue.text = count.toString()
            val canExpand = !classifications.isNullOrEmpty()
            if (!canExpand) {
                openKeys.remove(key)
                row.layoutDemoDetail.visibility = View.GONE
                row.layoutDemoDetail.removeAllViews()
                row.ivDemoChevron.visibility = View.GONE
                row.ivDemoChevron.rotation = 0f
                row.btnDemoHeader.setOnClickListener(null)
                row.btnDemoHeader.isClickable = false
                return
            }
            row.ivDemoChevron.visibility = View.VISIBLE
            row.btnDemoHeader.isClickable = true
            val expanded = key in openKeys
            row.layoutDemoDetail.visibility = if (expanded) View.VISIBLE else View.GONE
            row.ivDemoChevron.rotation = if (expanded) 180f else 0f
            if (expanded) fillDemoDetail(row, count, classifications)
            row.btnDemoHeader.setOnClickListener {
                if (key in openKeys) openKeys.remove(key) else openKeys.add(key)
                val nowOpen = key in openKeys
                if (nowOpen) fillDemoDetail(row, count, classifications)
                row.layoutDemoDetail.visibility = if (nowOpen) View.VISIBLE else View.GONE
                row.ivDemoChevron.rotation = if (nowOpen) 180f else 0f
                relayoutItem(itemView)
            }
        }
    }

    private fun setupDemoRow(
        row: ItemDashboardDemoRowBinding,
        @StringRes label: Int,
        @ColorRes dotColor: Int,
    ) {
        row.tvDemoLabel.setText(label)
        row.demoDot.backgroundTintList = ColorStateList.valueOf(color(row.root, dotColor))
        row.layoutDemoDetail.visibility = View.GONE
        row.ivDemoChevron.rotation = 0f
    }

    private fun bindDemoCount(
        row: ItemDashboardDemoRowBinding,
        count: Int,
        classifications: List<DashboardClassDef>?,
    ) {
        row.tvDemoValue.text = count.toString()
        val canExpand = !classifications.isNullOrEmpty()
        if (!canExpand) {
            row.layoutDemoDetail.visibility = View.GONE
            row.layoutDemoDetail.removeAllViews()
            row.ivDemoChevron.visibility = View.GONE
            row.ivDemoChevron.rotation = 0f
            row.btnDemoHeader.setOnClickListener(null)
            row.btnDemoHeader.isClickable = false
            return
        }
        row.ivDemoChevron.visibility = View.VISIBLE
        row.btnDemoHeader.isClickable = true
        row.btnDemoHeader.setOnClickListener {
            val expand = row.layoutDemoDetail.visibility != View.VISIBLE
            if (expand) fillDemoDetail(row, count, classifications)
            row.layoutDemoDetail.visibility = if (expand) View.VISIBLE else View.GONE
            row.ivDemoChevron.rotation = if (expand) 180f else 0f
            relayoutItem(row.root)
        }
        if (row.layoutDemoDetail.visibility == View.VISIBLE) {
            fillDemoDetail(row, count, classifications)
        }
    }

    private fun fillDemoDetail(
        row: ItemDashboardDemoRowBinding,
        count: Int,
        classifications: List<DashboardClassDef>?,
    ) {
        row.layoutDemoDetail.removeAllViews()
        val inflater = LayoutInflater.from(row.root.context)
        if (classifications.isNullOrEmpty()) {
            addClassRow(inflater, row.layoutDemoDetail, row.root.context.getString(R.string.dashboard_demo_total), count, R.color.dashboard_ink)
            return
        }
        DashboardClassifications.split(count, classifications).forEachIndexed { index, (def, value) ->
            addClassRow(
                inflater,
                row.layoutDemoDetail,
                row.root.context.getString(def.labelRes),
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
            ColorStateList.valueOf(color(parent, dotColor))
        row.findViewById<TextView>(R.id.tvClassLabel).text = label
        row.findViewById<TextView>(R.id.tvClassValue).text = value.toString()
        parent.addView(row)
    }

    private fun relayoutItem(itemView: View) {
        val card = generateSequence(itemView) { it.parent as? View }
            .firstOrNull { it.parent is RecyclerView } ?: itemView
        card.layoutParams?.height = ViewGroup.LayoutParams.WRAP_CONTENT
        card.forceLayout()
        card.requestLayout()
        val recyclerView = card.parent as? RecyclerView ?: return
        recyclerView.invalidateItemDecorations()
        recyclerView.requestLayout()
    }

    private fun color(view: View, @ColorRes colorRes: Int): Int =
        ContextCompat.getColor(view.context, colorRes)
}

internal class DashboardGridSpacingDecoration(
    private val spanCount: Int,
    private val gapPx: Int,
) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position <= 0) {
            outRect.set(0, 0, 0, 0)
            return
        }
        val index = position - 1
        val column = index % spanCount
        val half = gapPx / 2
        outRect.left = if (spanCount == 1 || column == 0) 0 else half
        outRect.right = if (spanCount == 1 || column == spanCount - 1) 0 else half
        outRect.bottom = gapPx
    }
}
