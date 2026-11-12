package org.piramalswasthya.stoptb.ui.volunteer.fragment

import android.os.SystemClock
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
import org.piramalswasthya.stoptb.helpers.RoleManager
import org.piramalswasthya.stoptb.ui.volunteer.VolunteerActivity
import org.piramalswasthya.stoptb.work.WorkerUtils
import org.piramalswasthya.stoptb.utils.sync.CampHubReachability
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class VolunteerHomeFragment : Fragment() {
    companion object {
        private const val manualRefreshReleaseTimeoutMs = 60_000L
    }

    @Inject
    lateinit var pref: PreferenceDao

    @Inject
    lateinit var roleManager: RoleManager

    private var _binding: FragmentHomeBinding? = null
    private val binding: FragmentHomeBinding
        get() = _binding!!

    private var manualHomeRefreshRequested = false
    private var manualRefreshStartedAtMs = 0L
    private val manualRefreshWorkIds = mutableListOf<java.util.UUID>()
    /** Bumped when a refresh starts or ends, so a late result from an ended refresh is dropped. */
    private var manualRefreshGeneration = 0
    private var manualRefreshTimeoutJob: Job? = null
    private var manualRefreshProgressJob: Job? = null

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
                        finishManualRefresh(getString(R.string.quick_refresh_camp_disconnected))
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

    fun requestQuickRefresh() {
        if (_binding == null || manualHomeRefreshRequested) return
        if (!pref.isCampModeEnabled() || !pref.isCampHubConnected()) {
            val unreachableStatus = getString(
                R.string.quick_refresh_ip_not_reachable,
                pref.getCampHubUrl()
            )
            if (binding.tvQuickRefreshStatus.text.toString() != unreachableStatus) {
                showRefreshStatus(getString(R.string.quick_refresh_camp_disconnected))
            }
            setQuickRefreshButtonEnabled(true)
            return
        }
        setQuickRefreshButtonEnabled(false)
        showRefreshStatus(getString(R.string.quick_refresh_refreshing))
        manualRefreshStartedAtMs = SystemClock.elapsedRealtime()
        startManualRefreshProgress()
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val isReachable = pingCampHub()
            launch(Dispatchers.Main) {
                if (_binding == null) return@launch
                if (!isReachable) {
                    pref.setCampHubConnected(false)
                    showRefreshStatus(
                        getString(
                            R.string.quick_refresh_ip_not_reachable,
                            pref.getCampHubUrl()
                        )
                    )
                    setQuickRefreshButtonEnabled(true)
                    manualRefreshProgressJob?.cancel()
                    manualRefreshProgressJob = null
                    return@launch
                }
                manualHomeRefreshRequested = true
                manualRefreshWorkIds.clear()
                val generation = ++manualRefreshGeneration
                val appContext = requireContext().applicationContext
                // Deadline first: if starting the workers blocks on WorkManager, the timeout must
                // already be running or the button stays disabled.
                startManualRefreshTimeout()
                // Queries WorkManager's DB synchronously: run it off the main thread.
                val refreshWorkIds = try {
                    withContext(Dispatchers.IO) {
                        WorkerUtils.startManualCampRefresh(appContext, pref)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Without this the button stayed disabled and the spinner ran until the hub
                    // disconnected: no IDs and no timeout were ever registered.
                    Timber.e(e, "Manual camp refresh could not start")
                    if (generation == manualRefreshGeneration) finishManualRefresh(getString(R.string.quick_refresh_failed))
                    return@launch
                }
                // The refresh may have ended (hub lost, view gone) while this ran, and another
                // may have started: its IDs must not join that one's list.
                if (generation != manualRefreshGeneration || !manualHomeRefreshRequested || _binding == null) {
                    return@launch
                }
                manualRefreshWorkIds.addAll(refreshWorkIds)
                // A WorkInfo update that arrived before the IDs were registered was ignored by the
                // observer; evaluate the current state once now so a quick finish isn't missed.
                val currentInfos = try {
                    withContext(Dispatchers.IO) {
                        WorkManager.getInstance(appContext).getWorkInfos(refreshWorkQuery()).get()
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyList() // the observer and the timeout still settle the refresh
                }
                if (generation == manualRefreshGeneration && _binding != null) {
                    onManualRefreshWorkInfos(currentInfos)
                }
            }
        }
    }

    private fun setupNurseQuickRefresh() {
        // Legacy, kept for reference:
//        val role = pref.getLoggedInUser()?.role
//        val canUseQuickRefresh = role.isNurseRole() ||
//                role.isRegistrationOfficerRole() ||
//                role.isCounsellingOfficerRole()
        val canUseQuickRefresh = roleManager.privilegesUnion().allowQuickRefresh
        Timber.d("RoleManager: allowQuickRefresh=$canUseQuickRefresh")
        binding.llQuickRefresh.visibility = View.GONE
        val activity = activity as? VolunteerActivity
        if (!canUseQuickRefresh) {
            activity?.setQuickRefreshActionVisible(false)
            return
        }

        activity?.setQuickRefreshActionVisible(true)
        updateQuickRefreshStatus()
        setQuickRefreshButtonEnabled(true)

        WorkManager.getInstance(requireContext().applicationContext)
            .getWorkInfosLiveData(refreshWorkQuery())
            .observe(viewLifecycleOwner) { workInfos -> onManualRefreshWorkInfos(workInfos) }
    }

    private fun refreshWorkQuery() = WorkQuery.fromUniqueWorkNames(
        WorkerUtils.pushWorkerUniqueName,
        WorkerUtils.pullWorkerUniqueName
    )

    private fun onManualRefreshWorkInfos(workInfos: List<WorkInfo>?) {
        if (!manualHomeRefreshRequested || workInfos.isNullOrEmpty()) return

        // If camp hub disconnected while a refresh was running, abort immediately
        // (the worker may stay BLOCKED forever waiting for connectivity).
        if (!pref.isCampModeEnabled() || !pref.isCampHubConnected()) {
            finishManualRefresh(getString(R.string.quick_refresh_camp_disconnected))
            return
        }

        val filteredInfos = workInfos.filter { manualRefreshWorkIds.contains(it.id) }
        if (filteredInfos.isEmpty()) {
            val refreshTimedOut = manualRefreshStartedAtMs > 0L &&
                    SystemClock.elapsedRealtime() - manualRefreshStartedAtMs >=
                    manualRefreshReleaseTimeoutMs
            if (refreshTimedOut) {
                finishManualRefresh(getString(R.string.quick_refresh_failed))
            }
            return
        }

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
                finishManualRefresh(getString(R.string.quick_refresh_failed))
            }

            isFinished -> {
                finishManualRefresh()
                pref.lastQuickRefreshTimestamp = System.currentTimeMillis()
                updateQuickRefreshStatus()
            }
        }
    }

    private fun showRefreshStatus(status: String) {
        if (_binding == null) return
        binding.tvQuickRefreshStatus.text = status
        if (status.isNotBlank()) {
            Toast.makeText(requireContext(), status, Toast.LENGTH_SHORT).show()
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
            ""
        }
    }

    private fun setQuickRefreshButtonEnabled(enabled: Boolean) {
        if (_binding != null) {
            binding.btnQuickRefresh.isEnabled = enabled
            binding.btnQuickRefresh.isClickable = enabled
            binding.btnQuickRefresh.alpha = if (enabled) 1f else 0.55f
        }
        (activity as? VolunteerActivity)?.setQuickRefreshActionEnabled(enabled)
        (activity as? VolunteerActivity)?.setQuickRefreshProgressVisible(!enabled)
    }

    private fun finishManualRefresh(statusText: String? = null) {
        manualRefreshTimeoutJob?.cancel()
        manualRefreshTimeoutJob = null
        manualRefreshProgressJob?.cancel()
        manualRefreshProgressJob = null
        manualHomeRefreshRequested = false
        manualRefreshGeneration++
        manualRefreshStartedAtMs = 0L
        manualRefreshWorkIds.clear()
        WorkerUtils.finishManualCampRefresh()
        setQuickRefreshButtonEnabled(true)
        statusText?.let { showRefreshStatus(it) }
    }

    private fun startManualRefreshTimeout() {
        manualRefreshTimeoutJob?.cancel()
        val generation = manualRefreshGeneration
        manualRefreshTimeoutJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(manualRefreshReleaseTimeoutMs)
            if (generation != manualRefreshGeneration || !manualHomeRefreshRequested || _binding == null) return@launch
            finishManualRefresh(getString(R.string.quick_refresh_failed))
        }
        startManualRefreshProgress()
    }

    private fun startManualRefreshProgress() {
        manualRefreshProgressJob?.cancel()
        (activity as? VolunteerActivity)?.updateQuickRefreshProgress(0)
        val startedAt = manualRefreshStartedAtMs
        manualRefreshProgressJob = viewLifecycleOwner.lifecycleScope.launch {
            while (manualHomeRefreshRequested && _binding != null) {
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                val percent = ((elapsed.toFloat() / manualRefreshReleaseTimeoutMs) * 100f)
                    .coerceIn(0f, 100f)
                    .roundToInt()
                (activity as? VolunteerActivity)?.updateQuickRefreshProgress(percent)
                if (percent >= 100) break
                delay(100)
            }
        }
    }

    // Same reachability rule as the sign-in screen, interceptor and sync gate
    // (GET /common-api/health, 2xx). Blocking: call off the main thread.
    private fun pingCampHub(): Boolean {
        val app = context?.applicationContext
        val hub = pref.getCampHubUrl()
        return if (app == null) CampHubReachability.isReachable(hub)
        else org.piramalswasthya.stoptb.helpers.CampConnectionDiagnostics.checkReachability(app, hub, "MANUAL_REFRESH_CHECK")
    }

    private fun setUpViewPager() {
        binding.vp2Home.offscreenPageLimit = 1
        binding.vp2Home.adapter = VolunteerPagerAdapter(childFragmentManager, viewLifecycleOwner.lifecycle)
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
        if (manualHomeRefreshRequested) {
            binding.tvQuickRefreshStatus.text = getString(R.string.quick_refresh_refreshing)
            setQuickRefreshButtonEnabled(false)
            startManualRefreshProgress()
        } else if (pref.isCampHubConnected()) {
            updateQuickRefreshStatus()
            setQuickRefreshButtonEnabled(true)
        }
        activity?.let {
            (it as VolunteerActivity).updateActionBar(
                R.drawable.ic_game_village_toolbar,
                getHomeToolbarTitle()
            )
            it.addClickListenerToHomepageActionBarTitle()
            if (roleManager.privilegesUnion().allowQuickRefresh) {
                it.setQuickRefreshActionVisible(true)
            }
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
            it.setQuickRefreshProgressVisible(false)
            it.setQuickRefreshActionVisible(false)
        }
        super.onStop()
    }

    override fun onDestroyView() {
        // End a refresh this view started: its startup and timeout are view-scoped and are
        // cancelled here, so on a new view it would show "Refreshing..." and refuse a new
        // refresh with nothing left to end it. Bumps the generation, so a late result is dropped.
        if (manualHomeRefreshRequested) finishManualRefresh()
        manualRefreshTimeoutJob?.cancel()
        manualRefreshTimeoutJob = null
        manualRefreshProgressJob?.cancel()
        manualRefreshProgressJob = null
        super.onDestroyView()
        _binding = null
    }
}
