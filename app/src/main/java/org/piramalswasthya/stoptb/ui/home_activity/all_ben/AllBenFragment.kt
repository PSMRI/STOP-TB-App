package org.piramalswasthya.stoptb.ui.home_activity.all_ben

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.helpers.RoleManager
import org.piramalswasthya.stoptb.adapters.BenListAdapter
import org.piramalswasthya.stoptb.adapters.BenPagingAdapter
import org.piramalswasthya.stoptb.contracts.SpeechToTextContract
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.databinding.AlertFilterBinding
import org.piramalswasthya.stoptb.databinding.AlertNewBenBinding
import android.widget.ArrayAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import kotlinx.coroutines.delay
import org.piramalswasthya.stoptb.databinding.FragmentDisplaySearchAndToggleRvButtonBinding
import org.piramalswasthya.stoptb.model.AppRole
import org.piramalswasthya.stoptb.ui.abha_id_activity.AbhaIdActivity
import org.piramalswasthya.stoptb.ui.home_activity.HomeActivity
import org.piramalswasthya.stoptb.ui.home_activity.all_ben.examine.ExamineBottomSheetFragment
import org.piramalswasthya.stoptb.ui.volunteer.VolunteerActivity
import org.piramalswasthya.stoptb.utils.ScreenLoadTrace
import org.piramalswasthya.stoptb.utils.callPhoneNumber
import org.piramalswasthya.stoptb.work.WorkerUtils
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class AllBenFragment : Fragment(), ExamineBottomSheetFragment.ExamineCallback {

    private companion object {
        val READ_ONLY_REFERRAL_SOURCES = setOf(5, 6, 7, 8, 10)
    }

    private data class SearchUiState(
        val isLoading: Boolean,
        val isEmpty: Boolean,
        val canExpand: Boolean,
        val isSearching: Boolean,
        val generation: Int
    )

    /**
     * Tracks an in-progress Examine flow for a given benId.
     * Set when user navigates to a form via the BottomSheet.
     * Cleared when BottomSheet is manually dismissed (user swipes away).
     * Non-null => show BottomSheet whenever AllBenFragment resumes.
     */
    private var pendingExamineBenId: Long? = null

    @Inject
    lateinit var prefDao: PreferenceDao

    @Inject
    lateinit var roleManager: RoleManager

    private var _binding: FragmentDisplaySearchAndToggleRvButtonBinding? = null

    private val binding: FragmentDisplaySearchAndToggleRvButtonBinding
        get() = _binding!!

    val args: AllBenFragmentArgs by lazy {
        AllBenFragmentArgs.fromBundle(requireArguments())
    }

    private lateinit var benAdapter: BenPagingAdapter

    private var selectedAbha = Abha.ALL

    private var addMemberAlert: androidx.appcompat.app.AlertDialog? = null
    private var addMemberAlertBinding: AlertNewBenBinding? = null

    private val viewModel: AllBenViewModel by viewModels()

    private val sttContract = registerForActivityResult(SpeechToTextContract()) { value ->
        val lowerValue = value.lowercase()
        binding.searchView.setText(lowerValue)
        binding.searchView.setSelection(lowerValue.length)
        viewModel.filterText(lowerValue)
    }

    private val abhaDisclaimer by lazy {
        AlertDialog.Builder(requireContext())
            .setTitle(resources.getString(R.string.beneficiary_abha_number))
            .setMessage("it")
            .setPositiveButton(resources.getString(R.string.ok)) { dialog, _ -> dialog.dismiss() }
            .create()
    }

    enum class Abha {
        ALL, WITH, WITHOUT, AGE_ABOVE_30,
    }

    private val filterAlert by lazy {
        val filterAlertBinding = AlertFilterBinding.inflate(layoutInflater, binding.root, false)
        filterAlertBinding.rgAbha.setOnCheckedChangeListener { radioGroup, i ->
            Timber.d("RG Gender selected id : $i")
            selectedAbha = when (i) {
                filterAlertBinding.rbAll.id -> Abha.ALL
                filterAlertBinding.rbWith.id -> Abha.WITH
                filterAlertBinding.rbWithout.id -> Abha.WITHOUT
                filterAlertBinding.rbAgeAboveThirty.id -> Abha.AGE_ABOVE_30
                else -> Abha.ALL
            }
        }

        filterAlertBinding.tvRch.visibility = View.GONE
        filterAlertBinding.cbRch.visibility = View.GONE

        val alert = MaterialAlertDialogBuilder(requireContext())
            .setView(filterAlertBinding.root)
            .setOnCancelListener {}
            .create()

        filterAlertBinding.btnOk.setOnClickListener {
            val filter = when (selectedAbha) {
                Abha.WITH -> 1
                Abha.WITHOUT -> 2
                Abha.AGE_ABOVE_30 -> 3
                else -> 0
            }
            viewModel.filterType(filter)
            alert.cancel()
        }
        filterAlertBinding.btnCancel.setOnClickListener {
            alert.cancel()
        }
        alert
    }

    override fun onAttach(context: android.content.Context) {
        super.onAttach(context)
        ScreenLoadTrace.markCurrent("Fragment.onAttach")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ScreenLoadTrace.markCurrent("Fragment.onCreate")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // TEMPORARY PERF INSTRUMENTATION
        ScreenLoadTrace.markCurrent("Fragment.onCreateView: inflating layout")
        _binding = FragmentDisplaySearchAndToggleRvButtonBinding.inflate(layoutInflater)
        ScreenLoadTrace.markCurrent("Fragment.onCreateView: layout inflated")
        return binding.root
    }

    override fun onDestroyView() {
        org.piramalswasthya.stoptb.utils.DbContentionSampler.stop()
        // onViewCreated builds a fresh benAdapter, so the decorations must be re-collected for
        // it. Without this reset the guard would keep the collectors pointed at the discarded
        // adapter and the new one would never be decorated.
        rowDecorationsStarted = false
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // TEMPORARY PERF INSTRUMENTATION
        ScreenLoadTrace.markCurrent("Fragment.onViewCreated: entered")
        org.piramalswasthya.stoptb.utils.DbContentionSampler.start(viewLifecycleOwner.lifecycleScope) {
            viewModel.dbNoOpProbe()
        }

        // Legacy, kept for reference:
//        val roleName = prefDao.getLoggedInUser()?.role
//        val isRegistrar = roleName.isRegistrationOfficerRole()
//        val isNurse = roleName.isNurseRole()
//        val isCounsellor = roleName.isCounsellingOfficerRole()
//        val isKnownRestrictedRole = isRegistrar || isNurse || isCounsellor
//        val allowLegacyAccess = !isKnownRestrictedRole
        val isReadOnlyReferralList = args.source in READ_ONLY_REFERRAL_SOURCES
        val showResultButton = args.source == 6 || args.source == 7 || args.source == 8
        val privilege = roleManager.privilegesUnion()
        Timber.d("RoleManager: showAbhaButton=${privilege.showAbhaButton}, showCallButton=${privilege.showCallButton}")
        // showAnthropometryButton/showBenActionButtons: confirmed dead code, not used anywhere.
//        val showAnthropometryButton = isRegistrar && !isReadOnlyReferralList
//        val showBenActionButtons = (isNurse || allowLegacyAccess) && !isReadOnlyReferralList
//        val showAbhaButton = (isRegistrar || isNurse || allowLegacyAccess || isCounsellor) && !isReadOnlyReferralList
//        val showCallButton = (isNurse || isRegistrar || allowLegacyAccess) && !isReadOnlyReferralList
        // Legacy, kept for reference:
//        val showAnthropometryButton = isRegistrar && !isReadOnlyReferralList
//        val showBenActionButtons = (isNurse || allowLegacyAccess) && !isReadOnlyReferralList
//        val showAbhaButton = (isRegistrar || isNurse || allowLegacyAccess || isCounsellor) &&
//                !isReadOnlyReferralList && !args.showContactTracingForms
//        val showCallButton = (isNurse || isRegistrar || allowLegacyAccess) && !isReadOnlyReferralList
        val showAbhaButton = privilege.showAbhaButton && !isReadOnlyReferralList && !args.showContactTracingForms
        val showCallButton = privilege.showCallButton && !isReadOnlyReferralList

        buildAddMemberDialog()

        binding.llQuickRefresh.visibility = View.GONE

        // Add Ben button hidden — ben registration only via Household flow
        binding.btnNextPage.visibility = View.GONE

        // Download and Filter icons hidden for now
        binding.ibFilter.visibility = View.GONE
        binding.ibDownload.visibility = View.GONE

        binding.ibFilter.setOnClickListener {
            filterAlert.show()
        }

        binding.ibDownload.setOnClickListener {
            viewModel.downloadCsv(requireContext())
        }

        var lastClickTime = 0L
        benAdapter = BenPagingAdapter(
            clickListener = BenListAdapter.BenClickListener(
                { item, hhId, benId, relToHeadId ->
                    if (isReadOnlyReferralList) return@BenClickListener
                    val now = System.currentTimeMillis()
                    if (now - lastClickTime > 800) {
                        lastClickTime = now
                        val navOptions = NavOptions.Builder()
                            .setEnterAnim(0)
                            .setExitAnim(0)
                            .setPopEnterAnim(0)
                            .setPopExitAnim(0)
                            .setLaunchSingleTop(true)
                            .build()
                        findNavController().navigate(
                            AllBenFragmentDirections.actionAllBenFragmentToNewBenRegFragment(
                                hhId = hhId,
                                benId = benId,
                                relToHeadId = relToHeadId,
                                isAddSpouse = 0,
                                gender = 0
                            ),
                            navOptions
                        )
                    }
                },
                clickedWifeBen = { _, hhId, benId, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        if (viewModel.isHouseholdMemberLimitReached(hhId)) {
                            showHouseholdMemberLimitReachedMessage(hhId)
                            return@launch
                        }
                        findNavController().navigate(
                            AllBenFragmentDirections.actionAllBenFragmentToNewBenRegFragment(
                                hhId = hhId,
                                relToHeadId = 4,       // Wife
                                gender = 2,            // Female
                                selectedBenId = benId, // husband's benId → mark isSpouseAdded after save
                                isAddSpouse = 1
                            )
                        )
                    }
                },
                clickedHusbandBen = { _, hhId, benId, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        if (viewModel.isHouseholdMemberLimitReached(hhId)) {
                            showHouseholdMemberLimitReachedMessage(hhId)
                            return@launch
                        }
                        findNavController().navigate(
                            AllBenFragmentDirections.actionAllBenFragmentToNewBenRegFragment(
                                hhId = hhId,
                                relToHeadId = 5,       // Husband
                                gender = 1,            // Male
                                selectedBenId = benId, // wife's benId → mark isSpouseAdded after save
                                isAddSpouse = 1
                            )
                        )
                    }
                },
                clickedChildben = { item, hhId, benId, relToHeadId -> },
                { item, hhid -> },
                { item, benId, hhId ->
                    checkAndGenerateABHA(benId)
                },
                { item, benId, hhId, isViewMode, isIFA -> },
                { ben -> callPhoneNumber(ben.mobileNo) },
                { },
                { item, benId, hhId ->
                    if (isReadOnlyReferralList) return@BenClickListener
                    viewLifecycleOwner.lifecycleScope.launch {
                        val benRegId = viewModel.getBenFromId(benId)
                        val vitalAutoFlow = AppRole.NURSE in roleManager.assignedRoles
                        Timber.d("RoleManager: VitalScreenFragment autoFlow=$vitalAutoFlow")
                        findNavController().navigate(
                            AllBenFragmentDirections.actionAllBenFragmentToVitalScreenFragment(
                                benId = benId,
                                benRegId = benRegId,
                                autoFlow = vitalAutoFlow
                            )
                        )
                    }
                },
                clickedResult = { item, benId, hhId ->
                    if (!showResultButton) return@BenClickListener
                    findNavController().navigate(
                        AllBenFragmentDirections.actionAllBenFragmentToTBSuspectedQuickFragment(
                            benId = benId,
                            viewOnly = true
                        )
                    )
                },
                clickedOrderAction = { item, action, orderType ->
                    if (!showResultButton) return@BenClickListener
                    when (action) {
                        "REFER" -> {
                            findNavController().navigate(
                                AllBenFragmentDirections.actionAllBenFragmentToTBSuspectedQuickFragment(
                                    benId = item.benId,
                                    viewOnly = false,
                                    referralType = if (orderType == "XRAY_CHEST") 6 else 7
                                )
                            )
                        }
                        "COMPLETE" -> {
                            findNavController().navigate(
                                AllBenFragmentDirections.actionAllBenFragmentToTBSuspectedQuickFragment(
                                    benId = item.benId,
                                    viewOnly = false,
                                    referralType = if (orderType == "XRAY_CHEST") 6 else 7,
                                    manualEntryAction = "COMPLETE"
                                )
                            )
                        }
                        "NOT_CONDUCTED" -> {
                            findNavController().navigate(
                                AllBenFragmentDirections.actionAllBenFragmentToTBSuspectedQuickFragment(
                                    benId = item.benId,
                                    viewOnly = false,
                                    referralType = if (orderType == "XRAY_CHEST") 6 else 7,
                                    manualEntryAction = "NOT_CONDUCTED"
                                )
                            )
                        }
                        "REORDER" -> {
                            viewModel.createNewOrder(item.benId, orderType, requireContext())
                        }
                        "COMPLETE_RIF" -> {
                            findNavController().navigate(
                                AllBenFragmentDirections.actionAllBenFragmentToTBSuspectedQuickFragment(
                                    benId = item.benId,
                                    viewOnly = false,
                                    referralType = 7,
                                    manualEntryAction = "COMPLETE"
                                )
                            )
                        }
                        "POLL" -> {
                            viewModel.pollOrderResult(item.benId, orderType)
                        }
                        "RETRY_POLL" -> {
                            viewModel.retryResultFetch(item.benId, orderType, requireContext())
                        }
                        "RETRY_PUSH" -> {
                            viewModel.retryTest(item.benId, orderType, requireContext())
                        }
                        "CHECK_PUSH_ACTIVE" -> {
                            viewModel.checkAndTrackInFlightPush(item.benId, requireContext())
                        }
                        "RETRY_RIF_POLL" -> {
                            viewModel.retryResultFetch(item.benId, "MDR_RIF", requireContext())
                        }
                        "VIEW" -> {
                            findNavController().navigate(
                                AllBenFragmentDirections.actionAllBenFragmentToTBSuspectedQuickFragment(
                                    benId = item.benId,
                                    viewOnly = true,
                                    referralType = if (orderType == "XRAY_CHEST") 6 else 7
                                )
                            )
                        }
                        "VIEW_RIF" -> {
                            findNavController().navigate(
                                AllBenFragmentDirections.actionAllBenFragmentToTBSuspectedQuickFragment(
                                    benId = item.benId,
                                    viewOnly = true,
                                    referralType = 7
                                )
                            )
                        }
                        "ENTER_LC", "VIEW_LC" -> {
                            findNavController().navigate(
                                AllBenFragmentDirections.actionAllBenFragmentToTBSuspectedQuickFragment(
                                    benId = item.benId,
                                    viewOnly = false,
                                    referralType = 8
                                )
                            )
                        }
                    }
                },
                { item, benId, hhId, viewOnly ->
                    if (isReadOnlyReferralList) return@BenClickListener
                    findNavController().navigate(
                        R.id.GeneralOpdFormFragment,
                        bundleOf(
                            "benId" to benId,
                            "viewOnly" to viewOnly,
                            "autoFlow" to false,
                            "generalOpdFlow" to !viewOnly
                        )
                    )
                },
                { item, benId, hhId, viewOnly ->
                    // Dead code — the Anthropometry icon this guards is unreachable: the
                    // adapter always passes showAnthropometryButton = false below, so this
                    // listener never fires regardless of role. Kept as-is, not deleted.
//                    if (!showAnthropometryButton) return@BenClickListener
                    if (true) return@BenClickListener
                    findNavController().navigate(
                        R.id.anthropometryFragment,
                        bundleOf(
                            "benId" to benId,
                            "autoFlow" to false
                        )
                    )
                },
                clickedExamine = { item, benId ->
                    pendingExamineBenId = benId
                    showExamineBottomSheet(benId)
                },
                clickedAddMember = { item ->
                    if (!isReadOnlyReferralList) addMemberToBeneficiaryHousehold(item)
                }
            ),
            showBeneficiaries = true,
            showRegistrationDate = true,
            showSyncIcon = true,
            showAbha = showAbhaButton,
            showCall = showCallButton,
            pref = prefDao,
            context = requireActivity(),
            roleManager = roleManager,
            showActionButtons = false,
            showResultButton = showResultButton,
            showAnthropometryButton = false,
            showExamineButton = !isReadOnlyReferralList,
            showScreeningStatus = true,
            showRedesignedCard = true,
            source = args.source,
            showContactTracingForms = args.showContactTracingForms,
            showAddMemberButton = !isReadOnlyReferralList

        )

        // TEMPORARY PERF INSTRUMENTATION
        ScreenLoadTrace.markCurrent("adapter built, attaching to RecyclerView")
        binding.rvAny.adapter = benAdapter
        // Fires on the first frame that actually has rows laid out - this is the moment the
        // user sees content, so it closes the trace.
        //
        // This runs on every frame and can still fire after the view is gone, so it holds the
        // RecyclerView directly and never touches `binding` (which is `_binding!!` and would
        // throw once the view is destroyed).
        val tracedRecyclerView = binding.rvAny
        tracedRecyclerView.viewTreeObserver.addOnPreDrawListener(
            object : android.view.ViewTreeObserver.OnPreDrawListener {
                // A list that legitimately stays empty would otherwise keep this listener on
                // every frame for the fragment's whole life, so give up after a bound - and say
                // so, because "content never appeared" is itself the thing being investigated.
                private val giveUpAt = android.os.SystemClock.elapsedRealtime() + 120_000L

                private fun stopListening() {
                    val observer = tracedRecyclerView.viewTreeObserver
                    if (observer.isAlive) observer.removeOnPreDrawListener(this)
                }

                override fun onPreDraw(): Boolean {
                    if (_binding == null) {
                        stopListening()
                        return true
                    }
                    if (benAdapter.itemCount > 0 && tracedRecyclerView.childCount > 0) {
                        stopListening()
                        ScreenLoadTrace.endCurrent(
                            "FIRST CONTENT DRAWN (${benAdapter.itemCount} items loaded, " +
                                    "${tracedRecyclerView.childCount} rows on screen)"
                        )
                        // Backstop: rows are visible, so decorations are due even if the
                        // load-state collector has not reported NotLoading yet.
                        startRowDecorationCollectors()
                    } else if (android.os.SystemClock.elapsedRealtime() > giveUpAt) {
                        stopListening()
                        ScreenLoadTrace.endCurrent(
                            "GAVE UP after 120s - no content ever drawn (itemCount=${benAdapter.itemCount})"
                        )
                    }
                    return true
                }
            }
        )
        binding.rvAny.setHasFixedSize(true)
        (binding.rvAny.itemAnimator as? SimpleItemAnimator)
            ?.supportsChangeAnimations = false
        binding.rvAny.setItemViewCacheSize(20)
        (binding.rvAny.layoutManager as? LinearLayoutManager)?.apply {
            initialPrefetchItemCount = 10
        }

        benAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                val rv = _binding?.rvAny ?: return
                val lm = rv.layoutManager as? LinearLayoutManager
                    ?: return
                if (positionStart == 0 && lm.findFirstCompletelyVisibleItemPosition() <= 0) {
                    rv.scrollToPosition(0)
                }
            }
        })

        // Search generation of the PagingData currently submitted to the adapter.
        // flatMapLatest bumps the generation before building the new Pager, so the value read here belongs to `it`.
        var submittedGeneration = -1

        // TEMPORARY PERF INSTRUMENTATION
        ScreenLoadTrace.markCurrent("onViewCreated: collectors wired, waiting for first page")
        var firstPagingDataSeen = false
        var firstRefreshSettled = false

        lifecycleScope.launch {
            viewModel.benList.collectLatest {
                if (!firstPagingDataSeen) {
                    firstPagingDataSeen = true
                    ScreenLoadTrace.markCurrent("first PagingData reached the adapter")
                }
                submittedGeneration = viewModel.searchGeneration.value
                benAdapter.submitData(it)
            }
        }

        // TEMPORARY PERF INSTRUMENTATION - how long Paging's refresh (COUNT + page 0) takes,
        // and how many times it restarts before it settles.
        lifecycleScope.launch {
            var refreshAttempts = 0
            benAdapter.loadStateFlow.collect { loadStates ->
                if (loadStates.refresh is LoadState.Loading) refreshAttempts++
                if (loadStates.refresh is LoadState.NotLoading && !firstRefreshSettled) {
                    firstRefreshSettled = true
                    ScreenLoadTrace.markCurrent(
                        "Paging refresh settled after $refreshAttempts load-state change(s), itemCount=${benAdapter.itemCount}"
                    )
                    // Paging is done competing for the DB - safe to start the row decorations.
                    // This fires whether the list has rows or is empty, so it is the trigger
                    // rather than first-content-drawn (which never happens on an empty list).
                    startRowDecorationCollectors()
                }
            }
        }

        var lastSearchGeneration = -1
        var settledForGeneration = false
        var currentlyShowingEmpty = false
        // Tracks the search generation when "Search in other villages" is tapped, while the expanded search is running.
        var expandPendingSinceGeneration: Int? = null

        binding.btnExpandSearchBlock.setOnClickListener {
            expandPendingSinceGeneration = viewModel.searchGeneration.value
            currentlyShowingEmpty = false
            binding.flEmpty.visibility = View.GONE
            binding.btnExpandSearchBlock.visibility = View.GONE
            binding.pbSearchLoading.visibility = View.VISIBLE
            viewModel.expandSearchToBlock()
        }

        lifecycleScope.launch {
            // Settles only once the expanded search's PagingData is submitted and its refresh has completed,
            // so page updates from the previous search can't end the loading state early.
            benAdapter.onPagesUpdatedFlow.collect {
                val pendingGeneration = expandPendingSinceGeneration ?: return@collect
                if (submittedGeneration <= pendingGeneration) return@collect
                if (benAdapter.loadStateFlow.first().refresh is LoadState.Loading) return@collect
                expandPendingSinceGeneration = null
                settledForGeneration = true
                val isEmpty = benAdapter.itemCount == 0
                binding.pbSearchLoading.visibility = View.GONE
                binding.flEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
                binding.btnExpandSearchBlock.visibility = View.GONE
                currentlyShowingEmpty = isEmpty
            }
        }

        lifecycleScope.launch {
            // A failed refresh never produces a page update, so settle the expanded search's error here
            // instead of leaving the loader up; Retry re-runs the same refresh via benAdapter.retry().
            benAdapter.loadStateFlow.collect { loadStates ->
                val pendingGeneration = expandPendingSinceGeneration ?: return@collect
                if (submittedGeneration <= pendingGeneration) return@collect
                if (loadStates.refresh !is LoadState.Error) return@collect
                expandPendingSinceGeneration = null
                settledForGeneration = true
                currentlyShowingEmpty = false
                binding.pbSearchLoading.visibility = View.GONE
                binding.flEmpty.visibility = View.GONE
                binding.btnExpandSearchBlock.visibility = View.GONE
                Snackbar.make(binding.root, R.string.search_other_villages_failed, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.search_other_villages_retry) {
                        expandPendingSinceGeneration = pendingGeneration
                        binding.pbSearchLoading.visibility = View.VISIBLE
                        benAdapter.retry()
                    }
                    .show()
            }
        }

        lifecycleScope.launch {
            combine(
                benAdapter.loadStateFlow.map { it.refresh is LoadState.Loading },
                benAdapter.loadStateFlow.map { loadStates ->
                    loadStates.refresh is LoadState.NotLoading && benAdapter.itemCount == 0
                },
                viewModel.canExpandSearchToBlock,
                viewModel.isSearching,
                viewModel.searchGeneration
            ) { isLoading, isEmpty, canExpand, isSearching, generation ->
                SearchUiState(isLoading, isEmpty, canExpand, isSearching, generation)
            }
                .distinctUntilChanged()
                .collectLatest { state ->
                    if (state.generation != lastSearchGeneration) {
                        // A genuinely new search started (typed text/filter/expand changed) - reset.
                        lastSearchGeneration = state.generation
                        settledForGeneration = false
                    }

                    // Expanded search still running - keep the loader; onPagesUpdatedFlow above settles the UI.
                    if (expandPendingSinceGeneration != null) {
                        return@collectLatest
                    }

                    // The loader belongs to the FIRST refresh of a search only. Background
                    // writes keep invalidating the PagingSource (one search term was rebuilt 131
                    // times in six minutes), so tying the spinner to "is loading" left it up
                    // forever and the "no results" view could never appear. Once a refresh has
                    // completed for this search, later reloads are silent.
                    val awaitingFirstResult = state.isLoading && !settledForGeneration
                    if (awaitingFirstResult) {
                        binding.pbSearchLoading.visibility = View.VISIBLE
                        binding.flEmpty.visibility = View.GONE
                        binding.btnExpandSearchBlock.visibility = View.GONE
                        currentlyShowingEmpty = false
                        return@collectLatest
                    }
                    binding.pbSearchLoading.visibility = View.GONE

                    // Settled already - a background reload must not disturb what is on screen.
                    if (settledForGeneration && state.isLoading) {
                        return@collectLatest
                    }
                    settledForGeneration = true

                    if (state.isEmpty) {
                        if (currentlyShowingEmpty) {
                            binding.btnExpandSearchBlock.visibility =
                                if (state.canExpand) View.VISIBLE else View.GONE
                            return@collectLatest
                        }
                        binding.pbSearchLoading.visibility = View.GONE
                        binding.flEmpty.visibility = View.GONE
                        binding.btnExpandSearchBlock.visibility = View.GONE
                        delay(300)
                        binding.pbSearchLoading.visibility = View.GONE
                        binding.flEmpty.visibility = View.VISIBLE
                        binding.btnExpandSearchBlock.visibility = if (state.canExpand) View.VISIBLE else View.GONE
                        currentlyShowingEmpty = true
                    } else {
                        binding.pbSearchLoading.visibility = View.GONE
                        binding.flEmpty.visibility = View.GONE
                        binding.btnExpandSearchBlock.visibility = View.GONE
                        currentlyShowingEmpty = false
                    }
                }
        }

        // Row decorations - the badges, counts and status icons painted ON each row - are not
        // first-paint content: nothing here decides whether the list appears, only how a row
        // that is already on screen is annotated. Every one of them is a Room Flow over a whole
        // table (allTbDiagnostics alone returned 10,163 rows of 46 columns on the camp device),
        // and launching all seventeen in onViewCreated put them in the same executor and the
        // same SQLite connection pool as Paging's refresh - the one query the user is actually
        // waiting on. Measured during a camp pull: that refresh took 4,606 ms against the
        // ~300 ms it costs on its own, and a no-op "SELECT 1" waited up to 8,548 ms behind the
        // same queue.
        //
        // They start once Paging's first refresh has settled instead (see the loadStateFlow
        // collector above, with first-content-drawn as a backstop). The rows are drawn from the
        // page data either way; the decorations land a frame later.


        lifecycleScope.launch {
            viewModel.orderActionState.collectLatest { state ->
                when (state) {
                    is AllBenViewModel.OrderActionResult.Idle -> {}
                    is AllBenViewModel.OrderActionResult.Loading -> {}
                    is AllBenViewModel.OrderActionResult.Success -> {
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                        viewModel.resetOrderActionState()
                        if (state.message.contains("completed", ignoreCase = true) || state.orderType.equals("MDR_RIF", ignoreCase = true)) {
                            if (state.orderType.equals("MDR_RIF", ignoreCase = true)) {
                                WorkerUtils.triggerRifDiagnosticResultPollWorker(requireContext())
                            } else if (state.orderType.equals("SPUTUM_TRUENAT", ignoreCase = true)) {
                                WorkerUtils.triggerTrueNatDiagnosticResultPollWorker(requireContext())
                            } else {
                                WorkerUtils.triggerDiagnosticResultPollWorker(requireContext())
                            }
                        }
                    }
                    is AllBenViewModel.OrderActionResult.Error -> {
                        val isNetworkError = state.error.contains("Unable to resolve host", ignoreCase = true) ||
                                state.error.contains("failed to connect", ignoreCase = true) ||
                                state.error.contains("timeout", ignoreCase = true) ||
                                state.error.contains("timed out", ignoreCase = true) ||
                                state.error.contains("Network is unreachable", ignoreCase = true) ||
                                state.error.contains("No address associated with hostname", ignoreCase = true)
                        val message = if (isNetworkError) {
                            "Please connect to the camp hub to retry the referral."
                        } else {
                            state.error
                        }
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                        viewModel.resetOrderActionState()
                    }
                }
            }
        }

        // Opening this list used to kick off three result-poll runs (all three helpers enqueue
        // the same unique DiagnosticResultPollWorker with REPLACE, so it was one worker started
        // three times). That worker writes TB_DIAGNOSTICS and TB_SUSPECTED per beneficiary, both
        // of which back BEN_BASIC_CACHE — so the screen was invalidating the very query it was
        // waiting on, roughly every 400ms, and the Paging refresh could never settle.
        //
        // Polling still starts from every path that actually needs it: the worker reschedules
        // itself every 60s while an order is in progress, and it is triggered on order
        // creation/retry, on a completed result, and on camp-hub reconnect.

        ScreenLoadTrace.markCurrent("onViewCreated: all collectors launched")
        binding.ibSearch.visibility = View.VISIBLE
        binding.ibSearch.setOnClickListener { sttContract.launch(Unit) }

        val searchTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun afterTextChanged(p0: Editable?) {
                viewModel.filterText(p0?.toString()?.trim() ?: "")
            }
        }

        binding.searchView.setOnFocusChangeListener { searchView, b ->
            if (b) (searchView as EditText).addTextChangedListener(searchTextWatcher)
            else (searchView as EditText).removeTextChangedListener(searchTextWatcher)
        }

        viewModel.abha.observe(viewLifecycleOwner) {
            it.let {
                if (it != null) {
                    abhaDisclaimer.setMessage(it)
                    abhaDisclaimer.show()
                }
            }
        }

        viewModel.benRegId.observe(viewLifecycleOwner) {
            if (it != null) {
                val intent = Intent(requireActivity(), AbhaIdActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                intent.putExtra("benId", viewModel.benId.value)
                intent.putExtra("benRegId", it)
                requireActivity().startActivity(intent)
                viewModel.resetBenRegId()
            }
        }

    }

    private suspend fun showHouseholdMemberLimitReachedMessage(hhId: Long) {
        Toast.makeText(
            requireContext(),
            getString(R.string.hh_member_limit_reached, viewModel.getTotalHhMembers(hhId) ?: 0),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun checkAndGenerateABHA(benId: Long) {
        lifecycleScope.launch {
            if (viewModel.getBenFromId(benId) == 0L) {
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Alert!")
                    .setMessage("Please wait for the record to sync and try again.")
                    .setCancelable(false)
                    .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                    .show()
            } else {
                viewModel.fetchAbha(benId)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Examine flow
    // -------------------------------------------------------------------------

    private fun showExamineBottomSheet(benId: Long) {
        val existing = childFragmentManager.findFragmentByTag(ExamineBottomSheetFragment.TAG)
        if (existing != null) return // already visible
        // Always show without autoFlow — user decides whether to continue or close.
        // autoFlow=true caused the form to re-open automatically when back was pressed.

        ExamineBottomSheetFragment.newInstance(benId, autoFlow = false, showContactTracingForms = args.showContactTracingForms)
            .show(childFragmentManager, ExamineBottomSheetFragment.TAG)
    }

    /** Navigate to the correct form based on [formIndex]. */
    override fun onNavigateToExamineForm(benId: Long, formIndex: Int, viewOnly: Boolean) {
        pendingExamineBenId = benId
        when (formIndex) {
            ExamineBottomSheetFragment.FORM_ANTHROPOMETRY -> {
                findNavController().navigate(
                    R.id.anthropometryFragment,
                    bundleOf(
                        "benId" to benId,
                        "autoFlow" to false,
                        "examineFlow" to !viewOnly,
                        "viewOnly" to viewOnly
                    )
                )
            }
            ExamineBottomSheetFragment.FORM_GENERAL_EXAM -> {
                viewLifecycleOwner.lifecycleScope.launch {
                    val benRegId = viewModel.getBenFromId(benId)
                    findNavController().navigate(
                        AllBenFragmentDirections.actionAllBenFragmentToVitalScreenFragment(
                            benId = benId,
                            benRegId = benRegId,
                            autoFlow = !viewOnly
                        )
                    )
                }
            }
            ExamineBottomSheetFragment.FORM_TB_SCREENING -> {
                findNavController().navigate(
                    R.id.TBScreeningFormFragment,
                    bundleOf(
                        "benId" to benId,
                        "autoFlow" to !viewOnly,
                        "viewOnly" to viewOnly
                    )
                )
            }
            ExamineBottomSheetFragment.FORM_GENERAL_OPD -> {
                findNavController().navigate(
                    R.id.GeneralOpdFormFragment,
                    bundleOf(
                        "benId" to benId,
                        "viewOnly" to viewOnly,
                        "autoFlow" to !viewOnly,
                        "generalOpdFlow" to !viewOnly
                    )
                )
            }
            /*ExamineBottomSheetFragment.FORM_DIAGNOSIS -> {
                findNavController().navigate(
                    AllBenFragmentDirections.actionAllBenFragmentToTBSuspectedQuickFragment(
                        benId = benId,
                        viewOnly = viewOnly
                    )
                )
            }
             */
        }
    }

    /** User manually swiped/dismissed BottomSheet — clear examine flow state. */
    override fun onExamineDismissed() {
        pendingExamineBenId = null
    }

    // -------------------------------------------------------------------------

    override fun onStart() {
        ScreenLoadTrace.markCurrent("Fragment.onStart")
        super.onStart()
        updateToolbarTitle()
    }

    override fun onResume() {
        super.onResume()
        ScreenLoadTrace.markCurrent("Fragment.onResume")
        updateToolbarTitle()
        viewModel.invalidateCheckedNoPushBenIds()
        viewModel.fetchBeneficiaryStatuses()
        
        val sh = findNavController().currentBackStackEntry?.savedStateHandle
        if (sh?.remove<Boolean>("examine_flow_done") == true) {
            pendingExamineBenId = null
        }

        // Re-show BottomSheet only if we're still mid-flow (user pressed back
        // without submitting a form, so they need to continue or cancel).
        val benId = pendingExamineBenId
        if (benId != null) {
            showExamineBottomSheet(benId)
        }
    }

    /** Guards [startRowDecorationCollectors] - first refresh settling and first content drawn both call it. */
    private var rowDecorationsStarted = false


    /**
     * Collectors for the per-row badges/counts/status icons. See the call site for why these do
     * not run until Paging's first refresh has settled.
     */
    private fun startRowDecorationCollectors() {
        // Binding first, then the guard: claiming the guard for a call that then bails on a
        // destroyed view would stop the next, legitimate call from ever starting them.
        if (_binding == null || rowDecorationsStarted) return
        rowDecorationsStarted = true
        lifecycleScope.launch {
            viewModel.childCounts.collectLatest { countMap ->
                ScreenLoadTrace.flowEmission("childCounts", countMap.size)
                benAdapter.submitChildCounts(countMap)
            }
        }

        lifecycleScope.launch {
            viewModel.householdMemberCounts.collectLatest { countMap ->
                ScreenLoadTrace.flowEmission("householdMemberCounts", countMap.size)
                benAdapter.submitHouseholdMemberCounts(countMap)
            }
        }

        lifecycleScope.launch {
            viewModel.householdHeadNames.collectLatest { nameMap ->
                ScreenLoadTrace.flowEmission("householdHeadNames", nameMap.size)
                benAdapter.submitHouseholdHeadNames(nameMap)
            }
        }

        lifecycleScope.launch {
            viewModel.vitalBenIds.collectLatest { benIds ->
                ScreenLoadTrace.flowEmission("vitalBenIds", benIds.size)
                benAdapter.submitBenIds(benIds)
            }
        }
        lifecycleScope.launch {
            viewModel.unsyncedVitalBenIds.collectLatest { benIds ->
                ScreenLoadTrace.flowEmission("unsyncedVitalBenIds", benIds.size)
                benAdapter.submitUnsyncedVitalBenIds(benIds)
            }
        }
        lifecycleScope.launch {
            viewModel.syncingVitalBenIds.collectLatest { benIds ->
                ScreenLoadTrace.flowEmission("syncingVitalBenIds", benIds.size)
                benAdapter.submitSyncingVitalBenIds(benIds)
            }
        }

        lifecycleScope.launch {
            viewModel.tbScreeningBenIds.collectLatest { benIds ->
                ScreenLoadTrace.flowEmission("tbScreeningBenIds", benIds.size)
                benAdapter.submitTbScreeningBenIds(benIds)
            }
        }

        lifecycleScope.launch {
            viewModel.unsyncedTbScreeningBenIds.collectLatest { benIds ->
                ScreenLoadTrace.flowEmission("unsyncedTbScreeningBenIds", benIds.size)
                benAdapter.submitUnsyncedTbScreeningBenIds(benIds)
            }
        }
        lifecycleScope.launch {
            viewModel.syncingTbScreeningBenIds.collectLatest { benIds ->
                ScreenLoadTrace.flowEmission("syncingTbScreeningBenIds", benIds.size)
                benAdapter.submitSyncingTbScreeningBenIds(benIds)
            }
        }

        lifecycleScope.launch {
            viewModel.generalOpdBenIds.collectLatest { benIds ->
                ScreenLoadTrace.flowEmission("generalOpdBenIds", benIds.size)
                benAdapter.submitGeneralOpdBenIds(benIds)
            }
        }
        lifecycleScope.launch {
            viewModel.unsyncedGeneralOpdBenIds.collectLatest { benIds ->
                ScreenLoadTrace.flowEmission("unsyncedGeneralOpdBenIds", benIds.size)
                benAdapter.submitUnsyncedGeneralOpdBenIds(benIds)
            }
        }
        lifecycleScope.launch {
            viewModel.syncingGeneralOpdBenIds.collectLatest { benIds ->
                ScreenLoadTrace.flowEmission("syncingGeneralOpdBenIds", benIds.size)
                benAdapter.submitSyncingGeneralOpdBenIds(benIds)
            }
        }

        lifecycleScope.launch {
            viewModel.anthropometryFilledBenIds.collectLatest { benIds ->
                ScreenLoadTrace.flowEmission("anthropometryFilledBenIds", benIds.size)
                benAdapter.submitAnthropometryBenIds(benIds)
            }
        }

        lifecycleScope.launch {
            viewModel.diagnosisBenIds.collectLatest { benIds ->
                ScreenLoadTrace.flowEmission("diagnosisBenIds", benIds.size)
                benAdapter.submitDiagnosisBenIds(benIds)
            }
        }

        lifecycleScope.launch {
            viewModel.contactFollowUpDoneBenIds.collectLatest { benIds ->
                ScreenLoadTrace.flowEmission("contactFollowUpDoneBenIds", benIds.size)
                benAdapter.submitContactFollowUpDoneBenIds(benIds)
            }
        }

        lifecycleScope.launch {
            viewModel.tptFollowUpDoneBenIds.collectLatest { benIds ->
                ScreenLoadTrace.flowEmission("tptFollowUpDoneBenIds", benIds.size)
                benAdapter.submitTptFollowUpDoneBenIds(benIds)
            }
        }

        lifecycleScope.launch {
            viewModel.tptEligibleBenIds.collectLatest { benIds ->
                ScreenLoadTrace.flowEmission("tptEligibleBenIds", benIds.size)
                benAdapter.submitTptEligibleBenIds(benIds)
            }
        }

        lifecycleScope.launch {
            viewModel.allTbDiagnostics.collectLatest { diagnosticsList ->
                ScreenLoadTrace.flowEmission("allTbDiagnostics", diagnosticsList.size)
                benAdapter.submitTBDiagnostics(diagnosticsList)
            }
        }

        lifecycleScope.launch {
            viewModel.allTbScreening.collectLatest { screeningList ->
                ScreenLoadTrace.flowEmission("allTbScreening", screeningList.size)
                benAdapter.submitTbScreeningList(screeningList)
            }
        }

        lifecycleScope.launch {
            viewModel.retryingBenIds.collectLatest { benIds ->
                ScreenLoadTrace.flowEmission("retryingBenIds", benIds.size)
                benAdapter.submitRetryingBenIds(benIds)
            }
        }
    }

    private fun updateToolbarTitle() {
        activity?.let {
            val title = if (args.source == 1) {
                getString(R.string.icon_title_abhas)
            } else if (args.source == 2) {
                getString(R.string.icon_title_rchs)
            } else if (args.source == 5) {
                getString(R.string.referral_hwc)
            } else if (args.source == 6) {
                getString(R.string.referral_digital_chest_xray)
            } else if (args.source == 7) {
                getString(R.string.referral_true_nat)
            } else if (args.source == 8) {
                getString(R.string.referral_liquid_culture)
            } else if (args.source == 9) {
                getString(R.string.icon_title_tpt_module)
            } else if (args.source == 10) {
                getString(R.string.referral_clinical_assessment)
            } else {
                getString(R.string.icon_title_ben)
            }

            val icon = if (args.source == 6) {
                R.drawable.ic_health_xray
            } else if (args.source == 7) {
                R.drawable.ic_truenat_device
            } else {
                R.drawable.ic__ben
            }

            when (it) {
                is HomeActivity -> it.updateActionBar(icon, title)
                is VolunteerActivity -> it.updateActionBar(icon, title)
            }
        }
    }

    private fun addMemberToBeneficiaryHousehold(item: org.piramalswasthya.stoptb.model.BenBasicDomain) {
        if (item.isNonHH || item.isDeath || item.isDeactivate) return
        viewLifecycleOwner.lifecycleScope.launch {
            if (viewModel.isHouseholdMemberLimitReached(item.hhId)) {
                showHouseholdMemberLimitReachedMessage(item.hhId)
                return@launch
            }
            viewModel.setSelectedHouseholdForAddMember(item.hhId)
            addMemberAlert?.show()
        }
    }

    private data class HofContext(
        val hof: org.piramalswasthya.stoptb.model.BenRegCache?,
        val fatherRegistered: Boolean,
        val motherRegistered: Boolean,
        val unmarried: Boolean,
        val married: Boolean
    )

    private fun computeHofContextForAddMember(): HofContext {
        val list = viewModel.householdBenListForAddMember
        val hof = list.firstOrNull { it.familyHeadRelationPosition == 19 }
        val fatherRegistered = list.any { it.familyHeadRelationPosition == 2 }
        val motherRegistered = list.any { it.familyHeadRelationPosition == 1 }
        val unmarried = hof?.genDetails?.maritalStatusId == 1
        val married = hof?.genDetails?.maritalStatusId == 2
        return HofContext(hof, fatherRegistered, motherRegistered, unmarried, married)
    }

    private fun baseRelationDropdownForAddMember(selectedGender: org.piramalswasthya.stoptb.model.Gender?): List<String> {
        val relationArray = when (selectedGender) {
            org.piramalswasthya.stoptb.model.Gender.FEMALE -> resources.getStringArray(R.array.nbr_relationship_to_head_female)
            org.piramalswasthya.stoptb.model.Gender.MALE, org.piramalswasthya.stoptb.model.Gender.TRANSGENDER -> resources.getStringArray(R.array.nbr_relationship_to_head_male)
            else -> null
        }
        return relationArray?.toList().orEmpty()
    }

    private fun filterRelationsForAddMember(
        selectedGender: org.piramalswasthya.stoptb.model.Gender?,
        baseList: List<String>,
        context: HofContext
    ): List<String> {
        if (context.hof == null) return baseList

        val relationList = baseList.toMutableList()
        val commonRelations = resources.getStringArray(R.array.nbr_relationship_to_head)
        val unmarriedFilter =
            resources.getStringArray(R.array.nbr_relationship_to_head_unmarried_filter).toSet()

        if (context.fatherRegistered) relationList.remove(commonRelations[1])
        if (context.motherRegistered) relationList.remove(commonRelations[0])

        if (context.unmarried) {
            relationList.removeAll(unmarriedFilter)
        } else if (!context.married) {
            relationList.remove(commonRelations[5])
            relationList.remove(commonRelations[4])
        }

        val hofGender = context.hof.gender
        if (hofGender == org.piramalswasthya.stoptb.model.Gender.MALE && selectedGender == org.piramalswasthya.stoptb.model.Gender.MALE) {
            relationList.remove(commonRelations[5])
        }
        if (hofGender == org.piramalswasthya.stoptb.model.Gender.FEMALE && selectedGender == org.piramalswasthya.stoptb.model.Gender.FEMALE) {
            relationList.remove(commonRelations[4])
        }

        return relationList
    }

    private fun genderFromRadioIdForAddMember(alertBinding: AlertNewBenBinding, checkedId: Int): org.piramalswasthya.stoptb.model.Gender? =
        when (checkedId) {
            alertBinding.rbMale.id -> org.piramalswasthya.stoptb.model.Gender.MALE
            alertBinding.rbFemale.id -> org.piramalswasthya.stoptb.model.Gender.FEMALE
            alertBinding.rbTrans.id -> org.piramalswasthya.stoptb.model.Gender.TRANSGENDER
            else -> null
        }

    private fun genderIntFromRadioIdForAddMember(alertBinding: AlertNewBenBinding): Int =
        when (alertBinding.rgGender.checkedRadioButtonId) {
            alertBinding.rbMale.id -> 1
            alertBinding.rbFemale.id -> 2
            alertBinding.rbTrans.id -> 3
            else -> 0
        }

    private fun applyRelationAdapterForAddMember(alertBinding: AlertNewBenBinding, items: List<String>) {
        alertBinding.actvRth.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, items)
        )
    }

    private fun buildAddMemberDialog() {
        val alertBinding = AlertNewBenBinding.inflate(layoutInflater, binding.root, false)
        addMemberAlertBinding = alertBinding
        alertBinding.btnOk.isEnabled = false

        alertBinding.rgGender.setOnCheckedChangeListener { _, checkedId ->
            val selectedGender = genderFromRadioIdForAddMember(alertBinding, checkedId) ?: run {
                alertBinding.linearLayout4.visibility = View.GONE
                return@setOnCheckedChangeListener
            }
            alertBinding.linearLayout4.visibility = View.VISIBLE
            alertBinding.actvRth.text = null
            alertBinding.btnOk.isEnabled = false
            val items = filterRelationsForAddMember(
                selectedGender = selectedGender,
                baseList = baseRelationDropdownForAddMember(selectedGender),
                context = computeHofContextForAddMember()
            )
            applyRelationAdapterForAddMember(alertBinding, items)
        }

        alertBinding.actvRth.setOnItemClickListener { _, _, _, _ ->
            alertBinding.btnOk.isEnabled = true
        }

        addMemberAlert = MaterialAlertDialogBuilder(requireContext())
            .setView(alertBinding.root)
            .setOnCancelListener {
                viewModel.resetSelectedHouseholdForAddMember()
                alertBinding.rgGender.clearCheck()
                alertBinding.linearLayout4.visibility = View.GONE
                alertBinding.actvRth.text = null
                alertBinding.btnOk.isEnabled = false
            }
            .create()

        alertBinding.btnOk.setOnClickListener {
            val relIndex = resources.getStringArray(R.array.nbr_relationship_to_head_src)
                .indexOf(alertBinding.actvRth.text.toString())
            val gender = genderIntFromRadioIdForAddMember(alertBinding)
            if (relIndex < 0 || gender == 0) return@setOnClickListener

            findNavController().navigate(
                AllBenFragmentDirections.actionAllBenFragmentToNewBenRegFragment(
                    hhId = viewModel.selectedHouseholdIdForAddMember,
                    benId = 0L,
                    relToHeadId = relIndex,
                    isAddSpouse = 0,
                    gender = gender
                )
            )
            viewModel.resetSelectedHouseholdForAddMember()
            addMemberAlert?.dismiss()
        }

        alertBinding.btnCancel.setOnClickListener {
            viewModel.resetSelectedHouseholdForAddMember()
            addMemberAlert?.dismiss()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        addMemberAlert?.dismiss()
        addMemberAlert = null
        addMemberAlertBinding = null
        _binding = null
    }
}
