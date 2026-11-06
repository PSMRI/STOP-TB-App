package org.piramalswasthya.stoptb.ui.home_activity.sync_dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import android.content.Intent
import android.widget.Toast
import kotlinx.coroutines.launch
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.databinding.FragmentSyncDashboardBinding

@AndroidEntryPoint
class SyncDashboardFragment : Fragment() {
    private val viewModel: SyncDashboardViewModel by viewModels()

    private var _binding: FragmentSyncDashboardBinding? = null
    private val binding get() = _binding!!

    private val tabTitles by lazy {
        arrayOf(
            getString(R.string.sync_dashboard_tab_status),
            getString(R.string.sync_dashboard_tab_logs),
            getString(R.string.sync_dashboard_tab_summary)
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSyncDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.viewPager.adapter = SyncDashboardPagerAdapter(this)
        // Logs has Summary as its neighbour, so its default snapshot is prepared for sharing.
        binding.viewPager.offscreenPageLimit = 1

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch {
                    viewModel.exportIntent.collect { intent ->
                        if (intent != null) {
                            viewModel.onExportHandled()
                            try {
                                startActivity(Intent.createChooser(intent, getString(R.string.sync_share_title)))
                            } catch (_: android.content.ActivityNotFoundException) {
                                Toast.makeText(requireContext(), R.string.sync_share_unavailable, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                launch {
                    viewModel.exportEmpty.collect { empty ->
                        if (empty) {
                            viewModel.onExportEmptyHandled()
                            Toast.makeText(requireContext(), R.string.sync_share_unavailable, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
