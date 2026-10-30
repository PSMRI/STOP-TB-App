package org.piramalswasthya.stoptb.ui.home_activity.all_ben.data_missing_ben

import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.R.attr.colorOnPrimary
import com.google.android.material.color.MaterialColors
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.adapters.BenListAdapter
import org.piramalswasthya.stoptb.adapters.BenListAdapter.Companion.formatScreeningResult
import org.piramalswasthya.stoptb.configuration.TBScreeningDataset
import org.piramalswasthya.stoptb.databinding.ItemMissingDataBenBinding
import org.piramalswasthya.stoptb.helpers.getDateFromLong
import org.piramalswasthya.stoptb.helpers.getPatientTypeByAge
import org.piramalswasthya.stoptb.model.BenBasicDomain
import org.piramalswasthya.stoptb.model.Gender
import org.piramalswasthya.stoptb.model.TBDiagnosticsCache
import org.piramalswasthya.stoptb.model.TBScreeningCache

class MissingDataBenAdapter(val onItemClick: (MissingDataBenItem) -> Unit) :
    PagingDataAdapter<MissingDataBenItem, MissingDataBenAdapter.ViewHolder>(DiffCallback) {

    private val tbScreeningBenIds = mutableListOf<Long>()
    private val tbDiagnosticsList =
        mutableListOf<TBDiagnosticsCache>()
    private val tbScreeningMap =
        mutableMapOf<Long, TBScreeningCache>()
    private val householdMemberCountMap = mutableMapOf<Long, Int>()
    private val householdHeadNameMap = mutableMapOf<Long, String>()


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMissingDataBenBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position) ?: return
        holder.bind(item)
    }

    inner class ViewHolder(
        private val binding: ItemMissingDataBenBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MissingDataBenItem) {
            binding.bind(
                item = item,
                householdHeadNameMap = householdHeadNameMap
            )
        }
    }

    private fun ItemMissingDataBenBinding.bind(
        item: MissingDataBenItem,
        householdHeadNameMap: Map<Long, String> = emptyMap(),
    ) = apply {

        val benItem: BenBasicDomain = item.benBasicDomain

        btnEditBen.setOnClickListener {
            onItemClick.invoke(item)
        }

        tvRedesignAbhaLabelId.isVisible = !benItem.abhaId.isNullOrEmpty()
        tvRedesignAbhaId.isVisible = !benItem.abhaId.isNullOrEmpty()

        val hasTbScreening = tbScreeningBenIds.contains(benItem.benId)

        val isNonHH = benItem.isNonHH
        val isHeadOfFamily = !isNonHH && benItem.relToHeadId == 19

        val householdHeadName = householdHeadNameMap[benItem.hhId]
            ?.trim()
            ?.takeUnless { it.isBlank() }
            ?: benItem.familyHeadName
        val hasFamilyHeadName =
            !isNonHH && householdHeadName.isNotBlank() && householdHeadName != "Not Available"

        tvAgeGender.visibility = View.VISIBLE
        tvAgeGender.text = "${benItem.gender.replaceFirstChar { it.titlecase() }} | ${
            BenListAdapter.compactAge(benItem.age)
        }"

        llIsDeath.isVisible = benItem.isDeath
        tvDateOfDeath.text = benItem.dobString

        renderContextCard(
            benItem,
            hasFamilyHeadName,
            isHeadOfFamily,
            householdHeadName,
            isNonHH
        )

        if (isNonHH) {
            ivIsHead.visibility = View.VISIBLE
            ivIsHead.setImageResource(R.drawable.icon_non_household)
            ivIsHead.imageTintList = null
        } else {
            ivIsHead.setImageResource(R.drawable.ic_icon_head_of_family)
            ivIsHead.imageTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(root.context, android.R.color.white)
            )
            ivIsHead.visibility = if (isHeadOfFamily) View.VISIBLE else View.GONE
        }
        head.visibility = if (isHeadOfFamily) View.VISIBLE else View.GONE

        bindErrorMsg(null)

        detailsDivider.visibility = View.VISIBLE
        tvScreeningSectionTitle.visibility = View.GONE
        llScreeningStatus.visibility = View.VISIBLE

        val tbDiagForStatus = tbDiagnosticsList.find { it.benId == benItem.benId }
        val tbScreeningCache = tbScreeningMap[benItem.benId]
        val isPresumptive = TBScreeningDataset.TbScreeningUtils.isPresumptive(tbScreeningCache)

        ivSymptoms.setImageResource(
            if (hasTbScreening || tbScreeningCache != null) R.drawable.circle_check
            else R.drawable.circle_uncheck
        )

        val context = root.context

        val symptomsResultText = when (isPresumptive) {
            true -> context.getString(R.string.screening_presumptive)
            false -> context.getString(R.string.screening_asymptomatic)
            null -> ""
        }

        tvSymptomsResult.text = context.formatScreeningResult(symptomsResultText)
        tvSymptomsResult.visibility = View.VISIBLE

        val xrayResult = tbDiagForStatus?.chestXRayResult
        val isPregnantFemale = benItem.gender.equals(Gender.FEMALE.name, ignoreCase = true) &&
                benItem.reproductiveStatusId == 1
        val isNegativeXray = xrayResult?.contains("negative", ignoreCase = true) == true ||
                xrayResult.equals("Normal", ignoreCase = true)

        llXrayTile.visibility = if (isPregnantFemale) View.GONE else View.VISIBLE
        llTruenatTile.visibility = if (isNegativeXray) View.GONE else View.VISIBLE

        llScreeningStatus.weightSum = listOf(
            true,
            !isPregnantFemale,
            !isNegativeXray
        ).count { it }.toFloat()
        ivXray.setImageResource(
            if (!xrayResult.isNullOrBlank()) R.drawable.circle_check else R.drawable.circle_uncheck
        )
        tvXrayStatus.text = if (xrayResult.isNullOrBlank()) "Unscreened" else "Screened"
        tvXrayStatus.visibility = View.GONE
        tvXrayResult.text = context.formatScreeningResult(xrayResult)
        tvXrayResult.visibility = View.VISIBLE


        val truenatResult = tbDiagForStatus?.naatResult
        ivTruenat.setImageResource(
            if (!truenatResult.isNullOrBlank()) R.drawable.circle_check else R.drawable.circle_uncheck
        )
        tvTruenatStatus.text = if (truenatResult.isNullOrBlank()) "Unscreened" else "Screened"
        tvTruenatStatus.visibility = View.GONE
        tvTruenatResult.text = context.formatScreeningResult(truenatResult)
        tvTruenatResult.visibility = View.VISIBLE

        tvTitleDuplicaterecord.visibility = View.GONE

        val type = getPatientTypeByAge(getDateFromLong(benItem.dob))
        val gender = benItem.gender
        val iconRes = when (type) {
            "new_born_baby" -> R.drawable.ic_icon_baby
            "infant" -> R.drawable.ic_infant
            "child", "adolescence" -> when (gender) {
                Gender.MALE.name -> R.drawable.ic_icon_boy_ben
                Gender.FEMALE.name -> R.drawable.ic_girl
                else -> null
            }

            "adult" -> when (gender) {
                Gender.MALE.name -> if (benItem.ageInt >= 60) R.drawable.ic_health_old_man else R.drawable.ic_males
                Gender.FEMALE.name -> if (benItem.ageInt >= 60) R.drawable.ic_health_old_woman else R.drawable.ic_icon_female_2
                else -> R.drawable.ic_unisex
            }

            else -> null
        }
        iconRes?.let { ivHhLogo.setImageResource(it) }
        tvHhId.text = benItem.benFullName

        setCardBackgroundColor(benItem.isDeath, benItem.isDeactivate)

    }

    private fun ItemMissingDataBenBinding.setCardBackgroundColor(isDeathCardTheme: Boolean, isDeactivateCardTheme: Boolean) {
        when {
            isDeathCardTheme -> {
                contentLayout.setBackgroundColor(
                    ContextCompat.getColor(
                        contentLayout.context,
                        R.color.md_theme_dark_outline
                    )
                )
            }

            isDeactivateCardTheme -> {
                contentLayout.setBackgroundColor(
                    ContextCompat.getColor(
                        contentLayout.context,
                        R.color.Quartenary
                    )
                )
                tvTitleDuplicaterecord.visibility = View.VISIBLE
            }

            else -> {
                contentLayout.setBackgroundColor(
                    ContextCompat.getColor(
                        contentLayout.context,
                        R.color.md_theme_light_primary
                    )
                )
            }
        }
    }

    private fun ItemMissingDataBenBinding.renderContextCard(
        benItem: BenBasicDomain,
        hasFamilyHeadName: Boolean,
        isHeadOfFamily: Boolean,
        householdHeadName: String,
        isNonHH: Boolean
    ) {
        val fatherName = benItem.fatherName
            ?.trim()
            ?.takeUnless { it.isBlank() || it.equals("Not Available", ignoreCase = true) }
        val showSeparateFatherName = hasFamilyHeadName &&
                !isHeadOfFamily &&
                fatherName != null &&
                BenListAdapter.normalizedName(householdHeadName) != BenListAdapter.normalizedName(
            fatherName
        )
        val relation = when {
            isHeadOfFamily -> null
            hasFamilyHeadName -> root.context.getString(R.string.hof) to householdHeadName
            !benItem.spouseName.isNullOrBlank() && benItem.spouseName != "Not Available" ->
                root.context.getString(R.string.tv_spouse_name_ph) to benItem.spouseName

            else -> root.context.getString(R.string.tv_father_name_ph) to
                    (benItem.fatherName?.takeIf { it.isNotBlank() } ?: "N/A")
        }
        llRedesignRelation.visibility = if (relation == null) View.GONE else View.VISIBLE
        tvRedesignRelationLabel.text = relation?.first
        tvRedesignRelationValue.text = relation?.second
        llRedesignFatherName.visibility =
            if (showSeparateFatherName) View.VISIBLE else View.GONE
        tvRedesignFatherName.text = fatherName
        tvRedesignRegistrationDate.text = benItem.regDate
        llRedesignFamilyMembers.visibility = if (isNonHH) View.GONE else View.VISIBLE
        tvRedesignFamilyMembers.text = (
                householdMemberCountMap[benItem.hhId] ?: if (isNonHH) 1 else 0
                ).toString()
        tvRedesignBeneficiaryId.text = benItem.benId.toString()
        tvRedesignNikshayId.text = benItem.nikshayIdDisplay
        tvRedesignMobileNumber.text =
            if (benItem.isPlaceholderMobileNo) "N/A" else benItem.mobileNoDisplay
    }

    private fun ItemMissingDataBenBinding.bindErrorMsg(text: String?) {
        if (text.isNullOrBlank()) {
            tvErrorMsg.visibility = View.GONE
            tvErrorMsg.movementMethod = null
            return
        }
        tvErrorMsg.visibility = View.VISIBLE
        tvErrorMsg.highlightColor = Color.TRANSPARENT
        tvErrorMsg.movementMethod = LinkMovementMethod.getInstance()
        renderErrorMsg(text, expanded = false)
    }

    private fun ItemMissingDataBenBinding.renderErrorMsg(text: String, expanded: Boolean) {
        val prefix = root.context.getString(R.string.error_message)
        val onPrimaryColor = MaterialColors.getColor(
            root, colorOnPrimary, Color.WHITE
        )
        val isTruncated = !expanded && text.length > BenListAdapter.ERROR_MSG_COLLAPSED_BODY_LENGTH

        val builder = SpannableStringBuilder(prefix)
        builder.setSpan(
            StyleSpan(Typeface.BOLD),
            0, prefix.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        builder.setSpan(
            ForegroundColorSpan(onPrimaryColor),
            0, prefix.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        if (expanded) {
            tvErrorMsg.maxLines = Int.MAX_VALUE
            builder.append(text)
        } else {
            tvErrorMsg.maxLines = 2
            builder.append(
                if (isTruncated) text.take(BenListAdapter.ERROR_MSG_COLLAPSED_BODY_LENGTH)
                    .trimEnd() + "…" else text
            )
        }

        if (expanded || isTruncated) {
            builder.append(" ")
            val toggleStart = builder.length
            builder.append(
                if (expanded) root.context.getString(R.string.see_less) else root.context.getString(
                    R.string.see_more
                )
            )
            builder.setSpan(
                object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        renderErrorMsg(text, expanded = !expanded)
                    }

                    override fun updateDrawState(ds: TextPaint) {
                        ds.color = onPrimaryColor
                        ds.isFakeBoldText = true
                        ds.isUnderlineText = false
                    }
                },
                toggleStart, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        tvErrorMsg.text = builder
    }

    fun submitTbScreeningBenIds(list: List<Long>) {
        val oldIds = tbScreeningBenIds.toSet()
        tbScreeningBenIds.clear()
        tbScreeningBenIds.addAll(list)
        notifyChangedIds(oldIds, tbScreeningBenIds.toSet())
    }

    private fun notifyChangedIds(oldIds: Set<Long>, newIds: Set<Long>) {
        val changed = (oldIds - newIds) + (newIds - oldIds)
        if (changed.isNotEmpty()) {
            val items = snapshot()
            items.forEachIndexed { index, item ->
                if (item != null && item.benBasicDomain.benId in changed) {
                    notifyItemChanged(index)
                }
            }
        }
    }

    fun submitTBDiagnostics(list: List<TBDiagnosticsCache>) {
        val oldByBenId = tbDiagnosticsList.associateBy { it.benId }
        val newByBenId = list.associateBy { it.benId }
        tbDiagnosticsList.clear()
        tbDiagnosticsList.addAll(list)
        val changedBenIds = (oldByBenId.keys + newByBenId.keys)
            .filterTo(mutableSetOf()) { benId -> oldByBenId[benId] != newByBenId[benId] }
        if (changedBenIds.isNotEmpty()) {
            val items = snapshot()
            items.forEachIndexed { index, item ->
                if (item != null && item.benBasicDomain.benId in changedBenIds) {
                    notifyItemChanged(index)
                }
            }
        }
    }

    fun submitTbScreeningList(list: List<TBScreeningCache>) {
        tbScreeningMap.clear()
        tbScreeningMap.putAll(list.associateBy { it.benId })
        notifyDataSetChanged()
    }

    fun submitHouseholdMemberCounts(map: Map<Long, Int>) {
        val old = householdMemberCountMap.toMap()
        householdMemberCountMap.clear()
        householdMemberCountMap.putAll(map)
        val changedHouseholds = (old.keys + map.keys).filterTo(mutableSetOf()) { old[it] != map[it] }
        if (changedHouseholds.isNotEmpty()) {
            snapshot().forEachIndexed { index, item ->
                if (item != null && item.benBasicDomain.hhId in changedHouseholds) notifyItemChanged(index)
            }
        }
    }

    fun submitHouseholdHeadNames(map: Map<Long, String>) {
        val old = householdHeadNameMap.toMap()
        householdHeadNameMap.clear()
        householdHeadNameMap.putAll(map)
        val changedHouseholds = (old.keys + map.keys).filterTo(mutableSetOf()) { old[it] != map[it] }
        if (changedHouseholds.isNotEmpty()) {
            snapshot().forEachIndexed { index, item ->
                if (item != null && item.benBasicDomain.hhId in changedHouseholds) notifyItemChanged(index)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<MissingDataBenItem>() {
        override fun areItemsTheSame(
            oldItem: MissingDataBenItem,
            newItem: MissingDataBenItem
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: MissingDataBenItem,
            newItem: MissingDataBenItem
        ): Boolean {
            return oldItem == newItem
        }
    }
}
