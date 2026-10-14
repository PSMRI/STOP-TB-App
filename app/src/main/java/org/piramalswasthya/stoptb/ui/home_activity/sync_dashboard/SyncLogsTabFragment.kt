package org.piramalswasthya.stoptb.ui.home_activity.sync_dashboard

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.adapters.SyncLogAdapter
import org.piramalswasthya.stoptb.databinding.FragmentSyncLogsTabBinding

@AndroidEntryPoint
class SyncLogsTabFragment : Fragment() {

    private var _binding: FragmentSyncLogsTabBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SyncDashboardViewModel by viewModels({ requireParentFragment() })

    private var isPaused = false

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
        viewLifecycleOwner.lifecycleScope.launch {
            val identity = withContext(Dispatchers.IO) {
                org.piramalswasthya.stoptb.helpers.SyncDiagnostics.identity(appContext)
            }
            viewBinding.tvSyncIdentity.text = "$identity\nLive view: latest 200 entries, refreshed once per second. Export includes stored logs."
        }

        val adapter = SyncLogAdapter()
        val layoutManager = binding.rvSyncLogs.layoutManager as LinearLayoutManager
        binding.rvSyncLogs.adapter = adapter
        binding.rvSyncLogs.itemAnimator = null

        binding.fabClear.setOnClickListener {
            viewModel.clearLogs()
        }

        binding.fabPause.setOnClickListener {
            isPaused = !isPaused
            binding.fabPause.setImageResource(
                if (isPaused) android.R.drawable.ic_media_play
                else android.R.drawable.ic_media_pause
            )
        }

        binding.fabExport.setOnClickListener {
            viewModel.exportLogs()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            // ViewPager off-screen tabs stay STARTED. Only the visible tab is RESUMED.
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch {
                    viewModel.exportIntent.collect { intent ->
                        if (intent != null) {
                            viewModel.onExportHandled()
                            startActivity(Intent.createChooser(intent, getString(R.string.sync_dashboard_export_logs)))
                        }
                    }
                }
                launch {
                    viewModel.exportEmpty.collect { empty ->
                        if (empty) {
                            viewModel.onExportEmptyHandled()
                            Toast.makeText(requireContext(), R.string.sync_dashboard_no_log_files, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                launch {
                    viewModel.syncLogs.sample(SYNC_LOG_DISPLAY_INTERVAL_MS)
                        .map(::syncLogDisplaySnapshot).flowOn(Dispatchers.Default).collect { logs ->
                            if (isPaused) return@collect
                            val wasAtBottom = !viewBinding.rvSyncLogs.canScrollVertically(1)
                            viewBinding.tvEmpty.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
                            viewBinding.rvSyncLogs.visibility = if (logs.isEmpty()) View.GONE else View.VISIBLE
                            adapter.showSnapshot(logs)
                            if (wasAtBottom && logs.isNotEmpty()) layoutManager.scrollToPosition(logs.size - 1)
                        }
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding?.rvSyncLogs?.adapter = null
        super.onDestroyView()
        _binding = null
    }
}
