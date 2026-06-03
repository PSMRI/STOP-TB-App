package org.piramalswasthya.stoptb.adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.configuration.IconDataset
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.databinding.RvItemHouseholdBinding
import org.piramalswasthya.stoptb.model.HouseHoldBasicDomain
import org.piramalswasthya.stoptb.ui.getTitle


class HouseHoldListAdapter(private val diseaseType: String, private var isDisease: Boolean, val pref: PreferenceDao,private val isSoftDeleteEnabled:Boolean = false, private val clickListener: HouseholdClickListener) :
    ListAdapter<HouseHoldBasicDomain, HouseHoldListAdapter.HouseHoldViewHolder>(
        HouseHoldDiffUtilCallBack
    ) {

    /** Controls visibility of "Add Member" button — hidden for Nurse role */
    private var showAddMember: Boolean = true

    fun setAddMemberVisible(visible: Boolean) {
        showAddMember = visible
        notifyDataSetChanged()
    }

    private object HouseHoldDiffUtilCallBack : DiffUtil.ItemCallback<HouseHoldBasicDomain>() {
        override fun areItemsTheSame(
            oldItem: HouseHoldBasicDomain,
            newItem: HouseHoldBasicDomain
        ) = oldItem.hhId == newItem.hhId

        override fun areContentsTheSame(
            oldItem: HouseHoldBasicDomain,
            newItem: HouseHoldBasicDomain
        ) = oldItem == newItem

    }

    class HouseHoldViewHolder private constructor(private val binding: RvItemHouseholdBinding) :
        RecyclerView.ViewHolder(binding.root) {
        companion object {
            fun from(parent: ViewGroup): HouseHoldViewHolder {
                val layoutInflater = LayoutInflater.from(parent.context)
                val binding = RvItemHouseholdBinding.inflate(layoutInflater, parent, false)
                return HouseHoldViewHolder(binding)
            }
        }

        fun bind(
            item: HouseHoldBasicDomain,
            clickListener: HouseholdClickListener,
            isDisease: Boolean,
            pref: PreferenceDao,
            diseaseType: String,
            isSoftDeleteEnabled: Boolean,
            showAddMember: Boolean = true
        ) {
            binding.household = item
            binding.clickListener = clickListener
            binding.executePendingBindings()


            if (isDisease) {
                binding.button4.visibility = View.GONE
                if (diseaseType == IconDataset.Disease.FILARIA.getTitle(binding.root.context) ) {
                    binding.button4.visibility = View.INVISIBLE
                    binding.btnMda.visibility = View.VISIBLE
                } else {
                    binding.btnMda.visibility = View.GONE
                }
            } else if (!isDisease) {
                // Nurse role: invisible (takes space but not visible/clickable)
                binding.button4.visibility = if (showAddMember) View.VISIBLE else View.INVISIBLE
                binding.button4.isEnabled = showAddMember
                binding.btnMda.visibility = View.GONE
            } else {
                binding.button4.visibility = View.GONE
                binding.btnMda.visibility = View.GONE
            }


            if (isSoftDeleteEnabled){
                binding.ivSoftDelete.visibility = View.VISIBLE

                if (item.isDeactivate){
                    //binding.ivSoftDelete.setImageResource(R.drawable.ic_group_on)
                    binding.parentCard.setBackgroundColor(ContextCompat.getColor(binding.parentCard.context, R.color.Quartenary))

                    binding.ivSoftDelete.visibility = View.GONE
                    binding.button4.visibility = View.INVISIBLE
                    binding.tvTitleDuplicaterecord.visibility = View.VISIBLE
                    binding.button3.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(binding.root.context, R.color.md_theme_dark_outline))

                } else{
                  //  binding.ivSoftDelete.setImageResource(R.drawable.ic_group_off)
                    binding.parentCard.setBackgroundColor(ContextCompat.getColor(binding.parentCard.context, R.color.md_theme_light_primary))

                    binding.ivSoftDelete.visibility = View.VISIBLE
                    binding.button4.visibility = View.VISIBLE
                    binding.tvTitleDuplicaterecord.visibility = View.GONE
                    binding.button3.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(binding.root.context, R.color.holo_green_dark))

                }

            } else {
                binding.ivSoftDelete.visibility = View.GONE
            }


            binding.clickListener = clickListener
            binding.executePendingBindings()
        }

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HouseHoldViewHolder {
        return HouseHoldViewHolder.from(parent)
    }

    override fun onBindViewHolder(holder: HouseHoldViewHolder, position: Int) {
        holder.bind(getItem(position), clickListener, isDisease, pref, diseaseType, isSoftDeleteEnabled, showAddMember)
    }


    class HouseholdClickListener(
        val hhDetails: (hh: HouseHoldBasicDomain) -> Unit,
        val showMember: (hh: HouseHoldBasicDomain) -> Unit,
        val newBen: (hh: HouseHoldBasicDomain) -> Unit,
        val addMDA: (hh: HouseHoldBasicDomain) -> Unit,
        val softDeleteHh: (hh: HouseHoldBasicDomain) -> Unit,
    ) {
        fun onClickedForHHDetails(item: HouseHoldBasicDomain) = hhDetails(item)
        fun onClickedForMembers(item: HouseHoldBasicDomain) = showMember(item)
        fun onClickedForNewBen(item: HouseHoldBasicDomain) = newBen(item)
        fun onClickedAddMDA(item: HouseHoldBasicDomain) = addMDA(item)
        fun onClickSoftDeleteHh(item: HouseHoldBasicDomain) = softDeleteHh(item)
    }
}