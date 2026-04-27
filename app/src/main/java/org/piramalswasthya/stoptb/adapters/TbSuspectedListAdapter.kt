package org.piramalswasthya.stoptb.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.databinding.RvItemTbSuspectedListBinding
import org.piramalswasthya.stoptb.model.BenWithTbSuspectedDomain

class TbSuspectedListAdapter(
    private val clickListener: ClickListener? = null,
    private val pref: PreferenceDao? = null
) :
    ListAdapter<BenWithTbSuspectedDomain, TbSuspectedListAdapter.BenViewHolder>
        (BenDiffUtilCallBack) {
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

    class BenViewHolder private constructor(private val binding: RvItemTbSuspectedListBinding) :
        RecyclerView.ViewHolder(binding.root) {
        companion object {
            fun from(parent: ViewGroup): BenViewHolder {
                val layoutInflater = LayoutInflater.from(parent.context)
                val binding = RvItemTbSuspectedListBinding.inflate(layoutInflater, parent, false)
                return BenViewHolder(binding)
            }
        }

        fun bind(
            item: BenWithTbSuspectedDomain,
            clickListener: ClickListener?,
            pref: PreferenceDao?
        ) {
            val isFullFormSubmitted = !item.tbSuspected?.visitLabel.isNullOrBlank()

            binding.btnFormTb.visibility = View.VISIBLE

            binding.benWithTb = item

            binding.ivSyncState.visibility = if (isFullFormSubmitted) View.VISIBLE else View.INVISIBLE

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

            binding.btnFormTb.text = if (isFullFormSubmitted) binding.root.resources.getString(R.string.view) else binding.root.resources.getString(R.string.track)
            binding.btnFormTb.setBackgroundColor(
                binding.root.resources.getColor(
                    if (isFullFormSubmitted) android.R.color.holo_green_dark else android.R.color.holo_red_dark
                )
            )
            binding.clickListener = clickListener

            binding.executePendingBindings()

        }

    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ) =
        BenViewHolder.from(parent)

    override fun onBindViewHolder(holder: BenViewHolder, position: Int) {
        holder.bind(getItem(position), clickListener, pref)
    }


    class ClickListener(
        private val clickedForm: ((hhId: Long, benId: Long, viewOnly: Boolean) -> Unit)? = null

    ) {
        fun onClickForm(item: BenWithTbSuspectedDomain) =
            clickedForm?.let { it(item.ben.hhId, item.ben.benId, !item.tbSuspected?.visitLabel.isNullOrBlank()) }
    }

}
