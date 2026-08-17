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
import org.piramalswasthya.stoptb.databinding.RvItemTbScreeningListBinding
import org.piramalswasthya.stoptb.helpers.getDateFromLong
import org.piramalswasthya.stoptb.helpers.getPatientTypeByAge
import org.piramalswasthya.stoptb.model.Gender
import org.piramalswasthya.stoptb.model.BenWithTbScreeningDomain

class TbScreeningListAdapter(
    private val clickListener: ClickListener? = null,
    private val pref: PreferenceDao? = null
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
            pref : PreferenceDao?,
            clickListener: ClickListener?,
        ) {
            binding.benWithTb = item
            binding.cvContent.visibility = View.VISIBLE
            bindTitleIcon(item)
            bindHeadOfFamilyIndicator(item)

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
            binding.btnFormTb.visibility =  View.VISIBLE
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

        private fun bindTitleIcon(item: BenWithTbScreeningDomain) {
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

        private fun bindHeadOfFamilyIndicator(item: BenWithTbScreeningDomain) {
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

    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ) =
        BenViewHolder.from(parent)

    override fun onBindViewHolder(holder: BenViewHolder, position: Int) {
        holder.bind(getItem(position),pref, clickListener)
    }


    class ClickListener(
        private val clickedForm: ((hhId: Long, benId: Long, viewOnly: Boolean) -> Unit)? = null

    ) {
        fun onClickForm(item: BenWithTbScreeningDomain) =
            clickedForm?.let { it(item.ben.hhId, item.ben.benId, item.tb != null) }
    }

}
