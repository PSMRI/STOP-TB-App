package org.piramalswasthya.stoptb.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.databinding.RvItemTbScreeningListBinding
import org.piramalswasthya.stoptb.model.BenWithTbScreeningDomain

class TbScreeningListAdapter(
    private val clickListener: ClickListener? = null
) :
    ListAdapter<BenWithTbScreeningDomain, TbScreeningListAdapter.BenViewHolder>
        (BenDiffUtilCallBack) {
    private object BenDiffUtilCallBack : DiffUtil.ItemCallback<BenWithTbScreeningDomain>() {
        override fun areItemsTheSame(
            oldItem: BenWithTbScreeningDomain,
            newItem: BenWithTbScreeningDomain
        ) = oldItem.ben.benId == newItem.ben.benId

        override fun areContentsTheSame(
            oldItem: BenWithTbScreeningDomain,
            newItem: BenWithTbScreeningDomain
        ) = oldItem == newItem

    }

    class BenViewHolder private constructor(private val binding: RvItemTbScreeningListBinding) :
        RecyclerView.ViewHolder(binding.root) {
        companion object {
            fun from(parent: ViewGroup): BenViewHolder {
                val layoutInflater = LayoutInflater.from(parent.context)
                val binding = RvItemTbScreeningListBinding.inflate(layoutInflater, parent, false)
                return BenViewHolder(binding)
            }
        }

        fun bind(
            item: BenWithTbScreeningDomain,
            clickListener: ClickListener?,
        ) {
            binding.benWithTb = item
            binding.cvContent.visibility = View.VISIBLE

            binding.ivSyncState.visibility = if (item.tb == null) View.INVISIBLE else View.VISIBLE

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

            val isScreened = item.tb != null
            binding.btnFormTb.text = binding.root.context.getString(
                if (isScreened) R.string.view_screen else R.string.screening
            )
            binding.btnFormTb.setBackgroundColor(
                ContextCompat.getColor(
                    binding.root.context,
                    if (isScreened) android.R.color.holo_green_dark else android.R.color.holo_red_dark
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
        holder.bind(getItem(position), clickListener)
    }


    class ClickListener(
        private val clickedForm: ((hhId: Long, benId: Long, viewOnly: Boolean) -> Unit)? = null

    ) {
        fun onClickForm(item: BenWithTbScreeningDomain) =
            clickedForm?.let { it(item.ben.hhId, item.ben.benId, item.tb != null) }
    }

}
