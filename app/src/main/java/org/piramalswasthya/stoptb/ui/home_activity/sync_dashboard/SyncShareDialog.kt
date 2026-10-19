package org.piramalswasthya.stoptb.ui.home_activity.sync_dashboard

import android.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.R

internal fun Fragment.showSyncShareDialog(viewModel: SyncDashboardViewModel, fromSummary: Boolean) {
    val checked = booleanArrayOf(!fromSummary, fromSummary)
    var updateSelection: () -> Unit = {}
    val dialog = AlertDialog.Builder(requireContext())
        .setTitle(R.string.sync_share_title)
        .setMultiChoiceItems(arrayOf(
            getString(R.string.sync_dashboard_tab_logs),
            getString(R.string.sync_dashboard_tab_summary)
        ), checked) { _, index, selected ->
            checked[index] = selected
            updateSelection()
        }
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(R.string.sync_share_send, null)
        .create()
    dialog.setOnShowListener {
        val send = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        fun update() {
            send.isEnabled = checked.any { it } && !viewModel.exportBusy.value &&
                (!checked[1] || viewModel.shareSummary.value != null)
        }
        updateSelection = ::update
        send.setOnClickListener {
            viewModel.exportLogs(checked[0], checked[1])
            dialog.dismiss()
        }
        val readiness = viewLifecycleOwner.lifecycleScope.launch {
            launch { viewModel.shareSummary.collect { update() } }
            launch { viewModel.exportBusy.collect { update() } }
        }
        dialog.setOnDismissListener { readiness.cancel() }
        update()
    }
    dialog.show()
    viewLifecycleOwner.lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
        override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) {
            dialog.dismiss()
            owner.lifecycle.removeObserver(this)
        }
    })
}
