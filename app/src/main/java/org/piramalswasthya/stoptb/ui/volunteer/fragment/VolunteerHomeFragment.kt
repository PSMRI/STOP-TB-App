package org.piramalswasthya.stoptb.ui.volunteer.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.adapters.VolunteerPagerAdapter
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.databinding.FragmentHomeBinding
import org.piramalswasthya.stoptb.helpers.Languages
import org.piramalswasthya.stoptb.helpers.isCounsellingOfficerRole
import org.piramalswasthya.stoptb.helpers.isNurseRole
import org.piramalswasthya.stoptb.helpers.isRegistrationOfficerRole
import org.piramalswasthya.stoptb.ui.volunteer.VolunteerActivity
import org.piramalswasthya.stoptb.work.WorkerUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class VolunteerHomeFragment : Fragment() {
    @Inject
    lateinit var pref: PreferenceDao

    private var _binding: FragmentHomeBinding? = null
    private val binding: FragmentHomeBinding
        get() = _binding!!

    private var manualHomeRefreshRequested = false
    private val manualRefreshWorkIds = mutableListOf<java.util.UUID>()

    /**
     * Resets the "Refreshing..." state immediately when the camp hub connection
     * drops (the interceptor flips the preference on an IO thread).
     */
    private val campHubPrefListener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == pref.getCampHubConnectedKey()) {
                activity?.runOnUiThread {
                    if (_binding == null) return@runOnUiThread
                    if (!pref.isCampHubConnected() && manualHomeRefreshRequested) {
                        manualHomeRefreshRequested = false
                        WorkerUtils.finishManualCampRefresh()
                        setQuickRefreshButtonEnabled(true)
                        binding.tvQuickRefreshStatus.text =
                            getString(R.string.quick_refresh_camp_disconnected)
                    } else if (pref.isCampHubConnected() && !manualHomeRefreshRequested) {
                        updateQuickRefreshStatus()
                        setQuickRefreshButtonEnabled(true)
                    }
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpViewPager()
        setupNurseQuickRefresh()
    }

    private fun setupNurseQuickRefresh() {
        val role = pref.getLoggedInUser()?.role
        val canUseQuickRefresh = role.isNurseRole() ||
                role.isRegistrationOfficerRole() ||
                role.isCounsellingOfficerRole()
        if (!canUseQuickRefresh) {
            binding.llQuickRefresh.visibility = View.GONE
            return
        }

        binding.llQuickRefresh.visibility = View.VISIBLE
        updateQuickRefreshStatus()
        setQuickRefreshButtonEnabled(true)

        binding.btnQuickRefresh.setOnClickListener {
            if (manualHomeRefreshRequested || !binding.btnQuickRefresh.isEnabled) return@setOnClickListener
            if (!pref.isCampModeEnabled() || !pref.isCampHubConnected()) {
                val unreachableStatus = getString(
                    R.string.quick_refresh_ip_not_reachable,
                    pref.getCampHubUrl()
                )
                if (binding.tvQuickRefreshStatus.text.toString() != unreachableStatus) {
                    binding.tvQuickRefreshStatus.text =
                        getString(R.string.quick_refresh_camp_disconnected)
                }
                setQuickRefreshButtonEnabled(true)
                return@setOnClickListener
            }
            setQuickRefreshButtonEnabled(false)
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val isReachable = pingCampHub()
                launch(Dispatchers.Main) {
                    if (!isReachable) {
                        pref.setCampHubConnected(false)
                        binding.tvQuickRefreshStatus.text = getString(
                            R.string.quick_refresh_ip_not_reachable,
                            pref.getCampHubUrl()
                        )
                        setQuickRefreshButtonEnabled(true)
                        return@launch
                    }
                    manualHomeRefreshRequested = true
                    binding.tvQuickRefreshStatus.text = getString(R.string.quick_refresh_refreshing)
                    manualRefreshWorkIds.clear()
                    manualRefreshWorkIds.addAll(
                        WorkerUtils.startManualCampRefresh(
                            requireContext().applicationContext,
                            pref
                        )
                    )
                }
            }
        }

        WorkManager.getInstance(requireContext().applicationContext)
            .getWorkInfosLiveData(
                WorkQuery.fromUniqueWorkNames(
                    WorkerUtils.pushWorkerUniqueName,
                    WorkerUtils.pullWorkerUniqueName
                )
            )
            .observe(viewLifecycleOwner) { workInfos ->
                if (!manualHomeRefreshRequested || workInfos.isNullOrEmpty()) return@observe

                // If camp hub disconnected while a refresh was running, abort immediately
                // (the worker may stay BLOCKED forever waiting for connectivity).
                if (!pref.isCampModeEnabled() || !pref.isCampHubConnected()) {
                    manualHomeRefreshRequested = false
                    WorkerUtils.finishManualCampRefresh()
                    setQuickRefreshButtonEnabled(true)
                    binding.tvQuickRefreshStatus.text = getString(R.string.quick_refresh_camp_disconnected)
                    return@observe
                }

                val activeInfos = workInfos.filter {
                    it.state == WorkInfo.State.ENQUEUED ||
                            it.state == WorkInfo.State.RUNNING ||
                            it.state == WorkInfo.State.BLOCKED
                }
                val filteredInfos = workInfos
                    .filter { manualRefreshWorkIds.contains(it.id) }
                    .ifEmpty {
                        // ExistingWorkPolicy.KEEP can reuse an already active sync instead of
                        // enqueuing the fresh IDs returned for this click.
                        activeInfos
                    }
                if (filteredInfos.isEmpty()) return@observe

                val isRunning = filteredInfos.any {
                    it.state == WorkInfo.State.ENQUEUED ||
                            it.state == WorkInfo.State.RUNNING ||
                            it.state == WorkInfo.State.BLOCKED
                }
                val isFailed = filteredInfos.any {
                    it.state == WorkInfo.State.FAILED || it.state == WorkInfo.State.CANCELLED
                }
                val isFinished = filteredInfos.all { it.state.isFinished }

                when {
                    isRunning -> {
                        setQuickRefreshButtonEnabled(false)
                        binding.tvQuickRefreshStatus.text = getString(R.string.quick_refresh_refreshing)
                    }

                    isFailed -> {
                        manualHomeRefreshRequested = false
                        WorkerUtils.finishManualCampRefresh()
                        setQuickRefreshButtonEnabled(true)
                        binding.tvQuickRefreshStatus.text = getString(R.string.quick_refresh_failed)
                    }

                    isFinished -> {
                        manualHomeRefreshRequested = false
                        WorkerUtils.finishManualCampRefresh()
                        setQuickRefreshButtonEnabled(true)
                        pref.lastQuickRefreshTimestamp = System.currentTimeMillis()
                        updateQuickRefreshStatus()
                    }
                }
            }
    }

    private fun updateQuickRefreshStatus() {
        val lastUpdated = pref.lastQuickRefreshTimestamp
        binding.tvQuickRefreshStatus.text = if (lastUpdated > 0L) {
            getString(
                R.string.quick_refresh_last_updated,
                SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(Date(lastUpdated))
            )
        } else {
            getString(R.string.quick_refresh_not_updated)
        }
    }

    private fun setQuickRefreshButtonEnabled(enabled: Boolean) {
        binding.btnQuickRefresh.isEnabled = enabled
        binding.btnQuickRefresh.isClickable = enabled
        binding.btnQuickRefresh.alpha = if (enabled) 1f else 0.55f
    }

    private fun pingCampHub(): Boolean {
        return try {
            val url = java.net.URL(pref.getCampHubUrl())
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "GET"
            conn.connect()
            val code = conn.responseCode
            conn.disconnect()
            code in 100..499
        } catch (_: Exception) {
            false
        }
    }

    private fun setUpViewPager() {
        binding.vp2Home.adapter = VolunteerPagerAdapter(this)
        TabLayoutMediator(binding.tlHomeViewpager, binding.vp2Home) { tab, position ->
            tab.text = when (position) {
                0 -> requireActivity().getString(R.string.menu_home_home)
                1 -> requireActivity().getString(R.string.menu_home_scheduler)
                else -> "NA"
            }
        }.attach()
    }

    override fun onStart() {
        super.onStart()
        pref.addOnPreferenceChangeListener(campHubPrefListener)
        if (pref.isCampHubConnected() && !manualHomeRefreshRequested) {
            updateQuickRefreshStatus()
            setQuickRefreshButtonEnabled(true)
        }
        activity?.let {
            (it as VolunteerActivity).updateActionBar(
                R.drawable.ic_home,
                getHomeToolbarTitle()
            )
            it.addClickListenerToHomepageActionBarTitle()
        }
        binding.vp2Home.setCurrentItem(0, false)
    }

    private fun getHomeToolbarTitle(): String {
        val village = pref.getLocationRecord()?.village ?: return getString(R.string.home)
        return when (pref.getCurrentLanguage()) {
            Languages.ENGLISH -> village.name
            Languages.HINDI -> village.nameHindi ?: village.name
            Languages.ASSAMESE -> village.nameAssamese ?: village.name
        }
    }

    override fun onStop() {
        pref.removeOnPreferenceChangeListener(campHubPrefListener)
        activity?.let {
            (it as VolunteerActivity).removeClickListenerToHomepageActionBarTitle()
        }
        super.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
