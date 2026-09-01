package org.piramalswasthya.stoptb.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.databinding.RvItemTbConfirmedListBinding
import org.piramalswasthya.stoptb.helpers.getDateFromLong
import org.piramalswasthya.stoptb.helpers.getPatientTypeByAge
import org.piramalswasthya.stoptb.helpers.isCounsellingOfficerRole
import org.piramalswasthya.stoptb.model.Gender
import org.piramalswasthya.stoptb.model.BenWithTbSuspectedDomain

class TbConfirmedListAdapter(
    private val clickListener: ClickListener? = null,
    private val pref: PreferenceDao? = null,
    private val restrictToAction: Action? = null
) :
ListAdapter<BenWithTbSuspectedDomain, TbConfirmedListAdapter.BenViewHolder>
(BenDiffUtilCallBack) {

    enum class Action { COUNSELLING, CONTACT_TRACING, FOLLOW_UP, VIEW_ONLY }

    private var benIdList: MutableList<Long>? = null
    private var totalSectionsFallback: Int? = null
    private var localFilledCounts: Map<Long, Int>? = null
    private object BenDiffUtilCallBack : DiffUtil.ItemCallback<BenWithTbSuspectedDomain>() {
        override fun areItemsTheSame(
            oldItem: BenWithTbSuspectedDomain,
            newItem: BenWithTbSuspectedDomain
        ) = oldItem.ben.benId == newItem.ben.benId

        override fun areContentsTheSame(
            oldItem: BenWithTbSuspectedDomain,
            newItem: BenWithTbSuspectedDomain
        ) = oldItem == newItem

    }

    class BenViewHolder private constructor(private val binding: RvItemTbConfirmedListBinding) :
        RecyclerView.ViewHolder(binding.root) {
        companion object {
            fun from(parent: ViewGroup): BenViewHolder {
                val layoutInflater = LayoutInflater.from(parent.context)
                val binding = RvItemTbConfirmedListBinding.inflate(layoutInflater, parent, false)
                return BenViewHolder(binding)
            }
        }

        fun bind(
            item: BenWithTbSuspectedDomain,
            clickListener: ClickListener?,
            pref: PreferenceDao?,
            benIdList: List<Long>?,
            totalSectionsFallback: Int?,
            localFilledCounts: Map<Long, Int>?,
            restrictToAction: Action?
        ) {
            binding.btnFormTb.visibility = View.VISIBLE

            binding.benWithTb = item
            bindTitleIcon(item)
            bindHeadOfFamilyIndicator(item)

            val isRefused = item.formResponse?.status == "REFUSED"
            val apiSectionsFilled = item.formResponse?.sectionsFilled ?: 0
            val localSectionsFilled = localFilledCounts?.get(item.ben.benId) ?: 0
            val sectionsFilled = maxOf(apiSectionsFilled, localSectionsFilled)
            val totalSections = item.formResponse?.totalSections ?: totalSectionsFallback ?: 0
            val isInProgress = !isRefused && sectionsFilled > 0 && sectionsFilled < totalSections
            val isCounselledByProgress = !isRefused && totalSections > 0 && sectionsFilled >= totalSections
            val isBenAlreadyCounselled = (benIdList != null && benIdList.contains(item.ben.benId)) &&
                    sectionsFilled == 0
            // Provides a single source of truth for determining whether a row should show “Counselled.”
            val isCounselledFinal = !isRefused && (isCounselledByProgress || item.isCounselled || isBenAlreadyCounselled)
            val role = pref?.getLoggedInUser()?.role

            binding.ivSyncState.visibility = if (item.tbConfirmedList == null) View.INVISIBLE else View.VISIBLE

            binding.counsellingSectionProgress.setProgress(sectionsFilled, totalSections)
            binding.counsellingSectionProgress.visibility = if(role.isCounsellingOfficerRole()) View.VISIBLE else View.INVISIBLE
            binding.btnContactTracing.visibility = if(role.isCounsellingOfficerRole()) View.VISIBLE else View.GONE

            if (isRefused) {
                binding.btnCounselling.visibility = View.GONE
                binding.btnCounselled.text = binding.root.context.getString(org.piramalswasthya.stoptb.R.string.refused)
                binding.btnCounselled.setBackgroundColor(binding.root.resources.getColor(android.R.color.holo_red_dark))
            } else if (isInProgress) {
                binding.btnCounselled.visibility = View.GONE
                binding.btnCounselling.visibility = View.VISIBLE
                binding.btnCounselling.text = binding.root.context.getString(org.piramalswasthya.stoptb.R.string.counselling_in_progress)
            } else if (isCounselledFinal) {
                binding.btnCounselling.visibility = View.GONE
                binding.btnCounselled.text = binding.root.context.getString(org.piramalswasthya.stoptb.R.string.counselled)
                binding.btnCounselled.setBackgroundColor(binding.root.resources.getColor(android.R.color.holo_green_dark))
            } else {
                binding.btnCounselled.visibility = View.GONE
                binding.btnCounselled.text = binding.root.context.getString(org.piramalswasthya.stoptb.R.string.counselled)
                binding.btnCounselled.setBackgroundColor(binding.root.resources.getColor(android.R.color.holo_green_dark))
                binding.btnCounselling.visibility = View.VISIBLE
                binding.btnCounselling.text = binding.root.context.getString(org.piramalswasthya.stoptb.R.string.counselling_start_button)
            }


            if (role != null) {
                checkIfCounsellingOfficerOrNot(role, (isRefused || isCounselledFinal))
            } else {
                binding.btnFormTb.visibility = View.GONE
                binding.btnCounselling.visibility = View.GONE
                binding.btnCounselled.visibility = View.GONE
            }

            if (restrictToAction != null) {
                if (restrictToAction != Action.FOLLOW_UP) {
                    binding.btnFormTb.visibility = View.GONE
                    binding.ivViewMember.visibility = View.VISIBLE
                } else {
                    binding.ivViewMember.visibility = View.GONE
                }

                if (restrictToAction != Action.CONTACT_TRACING) {
                    binding.btnContactTracing.visibility = View.GONE
                }

                if (restrictToAction != Action.COUNSELLING) {
                    binding.btnCounselling.visibility = View.GONE
                    binding.btnCounselled.visibility = View.GONE
                    binding.counsellingSectionProgress.visibility = View.INVISIBLE
                }
            }
            if (item.ben.spouseName == "Not Available" && item.ben.fatherName == "Not Available") {
                binding.father = true
                binding.husband = false
                binding.spouse = false
            } else {
                if (item.ben.gender == "MALE") {
                    binding.father = true
                    binding.husband = false
                    binding.spouse = false
                } else if (item.ben.gender == "FEMALE") {
                    if (item.ben.ageInt > 15) {
                        binding.father =
                            item.ben.fatherName != "Not Available" && item.ben.spouseName == "Not Available"
                        binding.husband = item.ben.spouseName != "Not Available"
                        binding.spouse = false
                    } else {
                        binding.father = true
                        binding.husband = false
                        binding.spouse = false
                    }
                } else {
                    binding.father =
                        item.ben.fatherName != "Not Available" && item.ben.spouseName == "Not Available"
                    binding.spouse = item.ben.spouseName != "Not Available"
                    binding.husband = false
                }
            }


            binding.btnFormTb.setBackgroundColor(binding.root.resources.getColor(if (item.tbConfirmedList == null) android.R.color.holo_red_dark else android.R.color.holo_green_dark))
            binding.clickListener = clickListener

            binding.executePendingBindings()

        }

        private fun bindTitleIcon(item: BenWithTbSuspectedDomain) {
            val ben = item.ben
            val type = getPatientTypeByAge(getDateFromLong(ben.dob))
            val iconRes = when (type) {
                "new_born_baby" -> R.drawable.ic_icon_baby
                "infant" -> R.drawable.ic_infant
                "child", "adolescence" -> when (ben.gender) {
                    Gender.MALE.name -> R.drawable.ic_icon_boy_ben
                    Gender.FEMALE.name -> R.drawable.ic_girl
                    else -> R.drawable.ic_unisex
                }
                "adult" -> when (ben.gender) {
                    Gender.MALE.name -> R.drawable.ic_males
                    Gender.FEMALE.name -> R.drawable.ic_icon_female_2
                    else -> R.drawable.ic_unisex
                }
                else -> R.drawable.ic_unisex
            }
            val drawable = AppCompatResources.getDrawable(binding.root.context, iconRes)?.mutate()?.apply {
                setTint(ContextCompat.getColor(binding.root.context, R.color.md_theme_light_primary))
            }
            binding.tvBenName.setCompoundDrawablesRelativeWithIntrinsicBounds(drawable, null, null, null)
        }

        private fun bindHeadOfFamilyIndicator(item: BenWithTbSuspectedDomain) {
            val isNonHH = item.ben.isNonHH
            val isHeadOfFamily = !isNonHH && item.ben.relToHeadId == 19
            if (isNonHH) {
                binding.ivIsHead.visibility = View.VISIBLE
                binding.ivIsHead.setImageResource(R.drawable.ic_no_hh)
                binding.ivIsHead.imageTintList = null
            } else {
                binding.ivIsHead.setImageResource(R.drawable.ic__hh)
                binding.ivIsHead.imageTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(binding.root.context, R.color.md_theme_light_primary)
                )
                binding.ivIsHead.visibility = if (isHeadOfFamily) View.VISIBLE else View.GONE
            }
            binding.head.visibility = if (isHeadOfFamily) View.VISIBLE else View.GONE
        }

        private fun checkIfCounsellingOfficerOrNot(
            role: String,
            isCounselled: Boolean
        ) {
            val isCounsellingOfficer = role.isCounsellingOfficerRole()

            binding.btnFormTb.visibility =
                if (isCounsellingOfficer) View.VISIBLE else View.GONE

            binding.btnCounselling.visibility =
                if (isCounsellingOfficer && !isCounselled) View.VISIBLE else View.GONE

            binding.btnCounselled.visibility =
                if (isCounsellingOfficer && isCounselled) View.VISIBLE else View.GONE

            binding.ivViewMember.visibility =
                if(isCounsellingOfficer) View.VISIBLE else View.GONE
        }

    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    )= BenViewHolder.from(parent)

    override fun onBindViewHolder(
        holder: BenViewHolder,
        position: Int
    ) {
        holder.bind(getItem(position), clickListener, pref, benIdList, totalSectionsFallback, localFilledCounts, restrictToAction)
    }

    /*override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ) =
        BenViewHolder.from(parent)

    override fun onBindViewHolder(holder: BenViewHolder, position: Int) {
        holder.bind(getItem(position), clickListener, pref)
    }*/


    class ClickListener(
        private val clickedForm: ((hhId: Long, benId: Long) -> Unit)? = null,
        private val clickedCounselling: ((item: BenWithTbSuspectedDomain) -> Unit)? = null,
        private val clickedCounselled: ((item: BenWithTbSuspectedDomain) -> Unit)? = null,
        private val clickedViewMember : ((item : BenWithTbSuspectedDomain) -> Unit)? = null,
        private val clickedContactTracing: ((item: BenWithTbSuspectedDomain) -> Unit)? = null
    ) {
        fun onClickForm(item: BenWithTbSuspectedDomain) =
            clickedForm?.let { it(item.ben.hhId, item.ben.benId) }
        fun onClickCounselling(item: BenWithTbSuspectedDomain) =
            clickedCounselling?.let { it(item) }
        fun onClickCounselled(item: BenWithTbSuspectedDomain) =
            clickedCounselled?.let { it(item) }
        fun onClickViewMember(item : BenWithTbSuspectedDomain) =
            clickedViewMember?.let { it(item) }
        fun onClickContactTracing(item: BenWithTbSuspectedDomain) =
            clickedContactTracing?.let { it(item) }
    }
    fun submitBenIds(list: List<Long>?) {
        if (list != null) {
            if (benIdList == null) benIdList = mutableListOf()
            benIdList!!.clear()
            benIdList!!.addAll(list)
        }
        notifyDataSetChanged()
    }

    fun submitTotalSectionsFallback(totalSections: Int) {
        totalSectionsFallback = totalSections
        notifyDataSetChanged()
    }

    fun submitLocalFilledCounts(counts: Map<Long, Int>) {
        localFilledCounts = counts
        notifyDataSetChanged()
    }

}
