package org.piramalswasthya.stoptb.adapters.contactTracing

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.piramalswasthya.stoptb.databinding.ItemContactMemberBinding
import org.piramalswasthya.stoptb.ui.contact_tracing.ContactMemberItem

class ContactMemberListAdapter(
    private val onClick: (ContactMemberItem) -> Unit
) : ListAdapter<ContactMemberItem, ContactMemberListAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemContactMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemContactMemberBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ContactMemberItem) {
            binding.tvMemberName.text = item.displayName
            binding.tvMemberStatus.text = item.status
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ContactMemberItem>() {
            override fun areItemsTheSame(oldItem: ContactMemberItem, newItem: ContactMemberItem) =
                oldItem.responseId == newItem.responseId

            override fun areContentsTheSame(oldItem: ContactMemberItem, newItem: ContactMemberItem) =
                oldItem == newItem
        }
    }
}
