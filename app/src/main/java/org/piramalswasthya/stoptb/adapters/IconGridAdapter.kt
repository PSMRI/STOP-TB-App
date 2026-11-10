package org.piramalswasthya.stoptb.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavDirections
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.databinding.RvItemIconGridBinding
import org.piramalswasthya.stoptb.model.Icon
import org.piramalswasthya.stoptb.ui.setCountBadgeText
import timber.log.Timber

/**
 * Card grid with a live count badge per card.
 *
 * Pass the fragment's viewLifecycleOwner. Each ViewHolder owns at most one count collector:
 * it is cancelled when the holder is rebound or recycled, and it only runs while the view is
 * RESUMED, so a grid on an off-screen ViewPager2 page (kept STARTED) or behind another screen
 * stops re-running its Room COUNT queries.
 */
class IconGridAdapter(
    private val clickListener: GridIconClickListener,
    private val lifecycleOwner: LifecycleOwner
) :
    ListAdapter<Icon, IconGridAdapter.IconViewHolder>(IconDiffCallback) {
    object IconDiffCallback : DiffUtil.ItemCallback<Icon>() {
        override fun areItemsTheSame(oldItem: Icon, newItem: Icon) =
            oldItem.title == newItem.title

        override fun areContentsTheSame(oldItem: Icon, newItem: Icon) =
            (oldItem == newItem)

    }


    class IconViewHolder private constructor(private val binding: RvItemIconGridBinding) :
        RecyclerView.ViewHolder(binding.root) {

        companion object {
            fun from(parent: ViewGroup): IconViewHolder {
                val layoutInflater = LayoutInflater.from(parent.context)
                val binding = RvItemIconGridBinding.inflate(layoutInflater, parent, false)
                return IconViewHolder(binding)
            }
        }

        private var countJob: Job? = null

        fun bind(item: Icon, clickListener: GridIconClickListener, lifecycleOwner: LifecycleOwner) {
            binding.homeIcon = item
            binding.clickListener = clickListener
            binding.executePendingBindings()
            collectCount(item, lifecycleOwner)
        }

        private fun collectCount(item: Icon, lifecycleOwner: LifecycleOwner) {
            countJob?.cancel()
            val countFlow = item.count
            if (countFlow == null) {
                binding.tvCount.text = null
                countJob = null
                return
            }
            countJob = lifecycleOwner.lifecycleScope.launch {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    try {
                        countFlow.collect { count -> binding.tvCount.setCountBadgeText(count) }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        Timber.d("Exception at record count : $e collected")
                    }
                }
            }
        }

        fun stopCount() {
            countJob?.cancel()
            countJob = null
        }

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        IconViewHolder.from(parent)

    override fun onBindViewHolder(holder: IconViewHolder, position: Int) {
        holder.bind(getItem(position), clickListener, lifecycleOwner)
    }

    override fun onViewRecycled(holder: IconViewHolder) {
        holder.stopCount()
        super.onViewRecycled(holder)
    }

    class GridIconClickListener(
        val disabledClickListener: ((message: String) -> Unit)? = null,
        val selectedListener: (dest: NavDirections) -> Unit
    ) {
        fun onClicked(icon: Icon) {
            if (!icon.enabled) {
                icon.disabledMessage?.let { disabledClickListener?.invoke(it) }
                return
            }
            // TEMPORARY PERF INSTRUMENTATION - starts the clock at the tap.
            icon.navAction?.let(selectedListener)
        }
    }
}