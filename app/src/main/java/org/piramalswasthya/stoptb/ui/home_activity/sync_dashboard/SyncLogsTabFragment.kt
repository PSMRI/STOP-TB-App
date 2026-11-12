package org.piramalswasthya.stoptb.ui.home_activity.sync_dashboard

import android.app.AlertDialog
import android.widget.ScrollView
import android.widget.TextView
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.withContext
import org.piramalswasthya.stoptb.helpers.SYNC_LOG_DISPLAY_INTERVAL_MS
import org.piramalswasthya.stoptb.helpers.syncLogDisplaySnapshot
import org.piramalswasthya.stoptb.helpers.interactiveSyncLogSession
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.adapters.SyncLogAdapter
import org.piramalswasthya.stoptb.databinding.FragmentSyncLogsTabBinding

@AndroidEntryPoint
class SyncLogsTabFragment : Fragment() {

    private var _binding: FragmentSyncLogsTabBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SyncDashboardViewModel by viewModels({ requireParentFragment() })

    private var isPaused = false
    private var detailDialog: AlertDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSyncLogsTabBinding.inflate(inflater, container, false)
        return binding.root
    }

    @OptIn(FlowPreview::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = binding
        val appContext = requireContext().applicationContext
        val adapter = SyncLogAdapter { entry ->
            viewLifecycleOwner.lifecycleScope.launch {
                val sessionId = interactiveSyncLogSession.session.value ?: return@launch
                val original = withContext(Dispatchers.Default) { viewModel.syncLogs.value.find { it.id == entry.id } }
                if (!interactiveSyncLogSession.isCurrent(sessionId) || original == null) return@launch
                val text = original.message
                val label = TextView(requireContext()).apply {
                    setPadding(24, 16, 24, 16)
                    this.text = text
                    setTextIsSelectable(true)
                }
                detailDialog?.dismiss()
                detailDialog = AlertDialog.Builder(requireContext()).setTitle("${entry.tag} details")
                    .setView(ScrollView(requireContext()).apply { addView(label) })
                    .setPositiveButton("Close", null).show()
            }
        }
        val layoutManager = binding.rvSyncLogs.layoutManager as LinearLayoutManager
        binding.rvSyncLogs.adapter = adapter
        binding.rvSyncLogs.itemAnimator = null

        viewLifecycleOwner.lifecycleScope.launch {
            interactiveSyncLogSession.session.collect { sessionId ->
                detailDialog?.dismiss()
                detailDialog = null
                adapter.showSnapshot(emptyList())
                viewBinding.rvSyncLogs.visibility = View.GONE
                viewBinding.tvEmpty.visibility = View.VISIBLE
                viewBinding.tvSyncIdentity.text = ""
                if (sessionId != null) {
                    val identity = withContext(Dispatchers.IO) {
                        org.piramalswasthya.stoptb.helpers.SyncDiagnostics.identity(appContext)
                    }
                    if (interactiveSyncLogSession.isCurrent(sessionId)) viewBinding.tvSyncIdentity.text = identity
                }
            }
        }

        fun showLogs(logs: List<org.piramalswasthya.stoptb.model.SyncLogEntry>) {
            if (isPaused) return
            val last = logs.lastOrNull()
            if (last != null && viewModel.syncLogs.value.none { it.id == last.id }) return
            val wasAtBottom = !viewBinding.rvSyncLogs.canScrollVertically(1)
            viewBinding.tvEmpty.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
            viewBinding.rvSyncLogs.visibility = if (logs.isEmpty()) View.GONE else View.VISIBLE
            adapter.showSnapshot(logs)
            if (wasAtBottom && logs.isNotEmpty()) layoutManager.scrollToPosition(logs.size - 1)
        }

        binding.fabClear.setOnClickListener {
            viewModel.clearLogs()
        }

        binding.fabPause.setOnClickListener {
            isPaused = !isPaused
            binding.fabPause.setImageResource(
                if (isPaused) android.R.drawable.ic_media_play
                else android.R.drawable.ic_media_pause
            )
            if (!isPaused) viewLifecycleOwner.lifecycleScope.launch {
                val logs = withContext(Dispatchers.Default) { syncLogDisplaySnapshot(viewModel.syncLogs.value) }
                if (logs.lastOrNull()?.id == viewModel.syncLogs.value.lastOrNull()?.id) showLogs(logs)
            }
        }

        binding.fabExport.setOnClickListener {
            showSyncShareDialog(viewModel, fromSummary = false)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            // ViewPager off-screen tabs stay STARTED. Only the visible tab is RESUMED.
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch {
                    viewModel.syncLogs.sample(SYNC_LOG_DISPLAY_INTERVAL_MS)
                        .map(::syncLogDisplaySnapshot).flowOn(Dispatchers.Default).collect { logs ->
                            showLogs(logs)
                        }
                }
            }
        }
    }

    override fun onDestroyView() {
        detailDialog?.dismiss()
        detailDialog = null
        _binding?.rvSyncLogs?.adapter = null
        super.onDestroyView()
        _binding = null
    }
}
