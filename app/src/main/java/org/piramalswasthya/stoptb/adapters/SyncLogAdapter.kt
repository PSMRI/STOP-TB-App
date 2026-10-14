package org.piramalswasthya.stoptb.adapters

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.piramalswasthya.stoptb.databinding.RvItemSyncLogBinding
import org.piramalswasthya.stoptb.model.LogLevel
import org.piramalswasthya.stoptb.model.SyncLogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SyncLogAdapter :
    RecyclerView.Adapter<SyncLogAdapter.ViewHolder>() {

    private var entries: List<SyncLogEntry> = emptyList()

    fun showSnapshot(logs: List<SyncLogEntry>) {
        if (entries == logs) return
        entries = logs
        // Rolling log windows can generate hundreds of queued remove animations.
        // A bounded, animation-free snapshot avoids that AdapterHelper backlog.
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = entries.size

    companion object {
        private const val MAX_DISPLAY_LENGTH = 500
    }

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    class ViewHolder(
        private val binding: RvItemSyncLogBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SyncLogEntry, timeFormat: SimpleDateFormat) {
            val (label, bgColor, textColor) = when (item.level) {
                LogLevel.ERROR -> Triple("E", Color.parseColor("#D32F2F"), Color.parseColor("#D32F2F"))
                LogLevel.WARN -> Triple("W", Color.parseColor("#F57C00"), Color.parseColor("#F57C00"))
                LogLevel.INFO -> Triple("I", Color.parseColor("#1976D2"), Color.parseColor("#333333"))
                LogLevel.DEBUG -> Triple("D", Color.parseColor("#757575"), Color.parseColor("#757575"))
            }

            binding.tvLevel.text = label
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 4f
                setColor(bgColor)
            }
            binding.tvLevel.background = bg

            binding.tvTimestamp.text = "${timeFormat.format(Date(item.timestamp))}  ${item.tag}"
            binding.tvMessage.text = if (item.message.length > MAX_DISPLAY_LENGTH)
                item.message.substring(0, MAX_DISPLAY_LENGTH) + "…"
            else
                item.message
            binding.tvMessage.setTextColor(textColor)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvItemSyncLogBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(entries[position], timeFormat)
    }
}
