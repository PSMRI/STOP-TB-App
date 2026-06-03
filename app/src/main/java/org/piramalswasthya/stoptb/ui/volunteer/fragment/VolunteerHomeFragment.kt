package org.piramalswasthya.stoptb.ui.volunteer.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
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

@AndroidEntryPoint
class VolunteerHomeFragment : Fragment() {
    @Inject
    lateinit var pref: PreferenceDao

    private var _binding: FragmentHomeBinding? = null
    private val binding: FragmentHomeBinding
        get() = _binding!!

    private var manualHomeRefreshRequested = false

    /**
     * Resets the "Refreshing..." state immediately when the camp hub connection
     * drops (the interceptor flips the preference on an IO thread).
     */
    private val campHubPrefListener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == pref.getCampHubConnectedKey() && !pref.isCampHubConnected()
                && manualHomeRefreshRequested
            ) {
                activity?.runOnUiThread {
                    manualHomeRefreshRequested = false
                    setQuickRefreshButtonEnabled(true)
                    if (_binding != null) {
                        binding.tvQuickRefreshStatus.text =
                            getString(R.string.quick_refresh_camp_disconnected)
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
            if (!pref.isCampHubConnected()) {
                binding.tvQuickRefreshStatus.text = getString(R.string.quick_refresh_camp_disconnected)
                setQuickRefreshButtonEnabled(true)
                return@setOnClickListener
            }
            manualHomeRefreshRequested = true
            setQuickRefreshButtonEnabled(false)
            binding.tvQuickRefreshStatus.text = getString(R.string.quick_refresh_refreshing)
            WorkerUtils.triggerAmritPushWorker(requireContext().applicationContext)
            WorkerUtils.triggerAmritPullWorker(requireContext().applicationContext)
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
                if (!pref.isCampHubConnected()) {
                    manualHomeRefreshRequested = false
                    setQuickRefreshButtonEnabled(true)
                    binding.tvQuickRefreshStatus.text = getString(R.string.quick_refresh_camp_disconnected)
                    return@observe
                }

                val isRunning = workInfos.any {
                    it.state == WorkInfo.State.ENQUEUED ||
                            it.state == WorkInfo.State.RUNNING ||
                            it.state == WorkInfo.State.BLOCKED
                }
                val isFailed = workInfos.any {
                    it.state == WorkInfo.State.FAILED || it.state == WorkInfo.State.CANCELLED
                }
                val isFinished = workInfos.all { it.state.isFinished }

                when {
                    isRunning -> {
                        setQuickRefreshButtonEnabled(false)
                        binding.tvQuickRefreshStatus.text = getString(R.string.quick_refresh_refreshing)
                    }

                    isFailed -> {
                        manualHomeRefreshRequested = false
                        setQuickRefreshButtonEnabled(true)
                        binding.tvQuickRefreshStatus.text = getString(R.string.quick_refresh_failed)
                    }

                    isFinished -> {
                        manualHomeRefreshRequested = false
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
