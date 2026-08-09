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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.adapters.BenListAdapter
import org.piramalswasthya.stoptb.adapters.BenPagingAdapter
import org.piramalswasthya.stoptb.contracts.SpeechToTextContract
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.databinding.AlertFilterBinding
import org.piramalswasthya.stoptb.databinding.FragmentDisplaySearchAndToggleRvButtonBinding
import org.piramalswasthya.stoptb.helpers.isCounsellingOfficerRole
import org.piramalswasthya.stoptb.helpers.isNurseRole
import org.piramalswasthya.stoptb.helpers.isRegistrationOfficerRole
import org.piramalswasthya.stoptb.ui.abha_id_activity.AbhaIdActivity
import org.piramalswasthya.stoptb.ui.home_activity.HomeActivity
import org.piramalswasthya.stoptb.ui.home_activity.all_ben.examine.ExamineBottomSheetFragment
import org.piramalswasthya.stoptb.ui.volunteer.VolunteerActivity
import org.piramalswasthya.stoptb.utils.callPhoneNumber
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class AllBenFragment : Fragment(), ExamineBottomSheetFragment.ExamineCallback {

    private companion object {
        val READ_ONLY_REFERRAL_SOURCES = setOf(5, 6, 7, 8)
    }

    /**
     * Tracks an in-progress Examine flow for a given benId.
     * Set when user navigates to a form via the BottomSheet.
     * Cleared when BottomSheet is manually dismissed (user swipes away).
     * Non-null => show BottomSheet whenever AllBenFragment resumes.
     */
    private var pendingExamineBenId: Long? = null

    @Inject
    lateinit var prefDao: PreferenceDao

    private var _binding: FragmentDisplaySearchAndToggleRvButtonBinding? = null

    private val binding: FragmentDisplaySearchAndToggleRvButtonBinding
        get() = _binding!!

    val args: AllBenFragmentArgs by lazy {
        AllBenFragmentArgs.fromBundle(requireArguments())
    }

    private lateinit var benAdapter: BenPagingAdapter

    private var selectedAbha = Abha.ALL

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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDisplaySearchAndToggleRvButtonBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val roleName = prefDao.getLoggedInUser()?.role
        val isRegistrar = roleName.isRegistrationOfficerRole()
        val isNurse = roleName.isNurseRole()
        val isCounsellor = roleName.isCounsellingOfficerRole()
        val isKnownRestrictedRole = isRegistrar || isNurse || isCounsellor
        val allowLegacyAccess = !isKnownRestrictedRole
        val isReadOnlyReferralList = args.source in READ_ONLY_REFERRAL_SOURCES
        val showResultButton = args.source == 6 || args.source == 7 || args.source == 8
        val showAnthropometryButton = isRegistrar && !isReadOnlyReferralList
        val showBenActionButtons = (isNurse || allowLegacyAccess) && !isReadOnlyReferralList
        val showAbhaButton = (isRegistrar || isNurse || allowLegacyAccess) && !isReadOnlyReferralList
        val showCallButton = (isNurse || isRegistrar || allowLegacyAccess) && !isReadOnlyReferralList
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
                    findNavController().navigate(
                        AllBenFragmentDirections.actionAllBenFragmentToNewBenRegFragment(
                            hhId = hhId,
                            relToHeadId = 4,       // Wife
                            gender = 2,            // Female
                            selectedBenId = benId, // husband's benId → mark isSpouseAdded after save
                            isAddSpouse = 1
                        )
                    )
                },
                clickedHusbandBen = { _, hhId, benId, _ ->
                    findNavController().navigate(
                        AllBenFragmentDirections.actionAllBenFragmentToNewBenRegFragment(
                            hhId = hhId,
                            relToHeadId = 5,       // Husband
                            gender = 1,            // Male
                            selectedBenId = benId, // wife's benId → mark isSpouseAdded after save
                            isAddSpouse = 1
                        )
                    )
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
                        findNavController().navigate(
                            AllBenFragmentDirections.actionAllBenFragmentToVitalScreenFragment(
                                benId = benId,
                                benRegId = benRegId,
                                autoFlow = isNurse
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
                                    referralType = if (orderType == "XRAY_CHEST") 6 else 7
                                )
                            )
                        }
                        "COMPLETE_RIF" -> {
                            findNavController().navigate(
                                AllBenFragmentDirections.actionAllBenFragmentToTBSuspectedQuickFragment(
                                    benId = item.benId,
                                    viewOnly = false,
                                    referralType = 7
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
                        "RETRY_RIF_POLL" -> {
                            viewModel.retryResultFetch(item.benId, "MDR_RIF", requireContext())
                        }
                        "REPEAT_TEST" -> {
                            lifecycleScope.launch {
                                val diag = viewModel.tbRepo.getTBDiagnosticsById(item.benId)
                                val naatRes = diag?.naatResult
                                val rifRes = diag?.trueNatRifResult

                                val isInvalidMtb = naatRes.equals("Invalid", ignoreCase = true)
                                val isIndeterminateRif = rifRes.equals("Indeterminate", ignoreCase = true)

                                val title = when {
                                    isInvalidMtb -> "Invalid Test Result"
                                    isIndeterminateRif -> "Indeterminate Test Result"
                                    else -> "Repeat Test"
                                }

                                val msg = when {
                                    isInvalidMtb -> "The test results are invalid. Repeat the test."
                                    isIndeterminateRif -> "The test results are indeterminate. Repeat the test."
                                    else -> "Are you sure you want to repeat this test? A new test order will be created."
                                }

                                val targetOrder = when {
                                    isIndeterminateRif -> "MDR_RIF"
                                    else -> "MTB_PLUS"
                                }

                                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                    .setTitle(title)
                                    .setMessage(msg)
                                    .setPositiveButton("REPEAT TEST") { d, _ ->
                                        d.dismiss()
                                        viewModel.repeatTest(item.benId, targetOrder)
                                    }
                                    .setNegativeButton("CANCEL") { d, _ -> d.dismiss() }
                                    .show()
                            }
                        }
                        "VIEW" -> {
                            lifecycleScope.launch {
                                val diag = viewModel.tbRepo.getTBDiagnosticsById(item.benId)
                                if (diag?.naatResult.equals("Invalid", ignoreCase = true)) {
                                    androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                        .setTitle("Invalid Test Result")
                                        .setMessage("The test result is invalid. Please repeat the test.")
                                        .setPositiveButton("REPEAT TEST") { d, _ ->
                                            d.dismiss()
                                            viewModel.repeatTest(item.benId, "MTB_PLUS")
                                        }
                                        .setNegativeButton("CANCEL") { d, _ -> d.dismiss() }
                                        .show()
                                } else {
                                    findNavController().navigate(
                                        AllBenFragmentDirections.actionAllBenFragmentToTBSuspectedQuickFragment(
                                            benId = item.benId,
                                            viewOnly = true,
                                            referralType = if (orderType == "XRAY_CHEST") 6 else 7
                                        )
                                    )
                                }
                            }
                        }
                        "VIEW_RIF" -> {
                            lifecycleScope.launch {
                                val diag = viewModel.tbRepo.getTBDiagnosticsById(item.benId)
                                if (diag?.trueNatRifResult.equals("Indeterminate", ignoreCase = true)) {
                                    androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                        .setTitle("Indeterminate Test Result")
                                        .setMessage("The test result is indeterminate. Please repeat the test.")
                                        .setPositiveButton("REPEAT TEST") { d, _ ->
                                            d.dismiss()
                                            viewModel.repeatTest(item.benId, "MDR_RIF")
                                        }
                                        .setNegativeButton("CANCEL") { d, _ -> d.dismiss() }
                                        .show()
                                } else {
                                    findNavController().navigate(
                                        AllBenFragmentDirections.actionAllBenFragmentToTBSuspectedQuickFragment(
                                            benId = item.benId,
                                            viewOnly = true,
                                            referralType = 7
                                        )
                                    )
                                }
                            }
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
                    if (!showAnthropometryButton) return@BenClickListener
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
                }
            ),
            showBeneficiaries = true,
            showRegistrationDate = true,
            showSyncIcon = true,
            showAbha = showAbhaButton,
            showCall = showCallButton,
            pref = prefDao,
            context = requireActivity(),
            showActionButtons = false,
            showResultButton = showResultButton,
            showAnthropometryButton = false,
            showExamineButton = !isReadOnlyReferralList && !isCounsellor ,
            source = args.source
        )

        binding.rvAny.adapter = benAdapter
        binding.rvAny.setHasFixedSize(true)
        binding.rvAny.setItemViewCacheSize(20)
        (binding.rvAny.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager)?.apply {
            initialPrefetchItemCount = 10
        }

        lifecycleScope.launch {
            viewModel.benList.collectLatest {
                benAdapter.submitData(it)
            }
        }

        lifecycleScope.launch {
            benAdapter.loadStateFlow.collectLatest { loadStates ->
                val isEmpty = loadStates.refresh is LoadState.NotLoading
                        && benAdapter.itemCount == 0
                binding.flEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            viewModel.childCounts.collectLatest { countMap ->
                benAdapter.submitChildCounts(countMap)
            }
        }

        lifecycleScope.launch {
            viewModel.vitalBenIds.collectLatest { benIds ->
                benAdapter.submitBenIds(benIds)
            }
        }
        lifecycleScope.launch {
            viewModel.unsyncedVitalBenIds.collectLatest { benIds ->
                benAdapter.submitUnsyncedVitalBenIds(benIds)
            }
        }
        lifecycleScope.launch {
            viewModel.syncingVitalBenIds.collectLatest { benIds ->
                benAdapter.submitSyncingVitalBenIds(benIds)
            }
        }

        lifecycleScope.launch {
            viewModel.tbScreeningBenIds.collectLatest { benIds ->
                Timber.e("TB Screening IDs: $benIds")
                benAdapter.submitTbScreeningBenIds(benIds)
            }
        }
        lifecycleScope.launch {
            viewModel.unsyncedTbScreeningBenIds.collectLatest { benIds ->
                benAdapter.submitUnsyncedTbScreeningBenIds(benIds)
            }
        }
        lifecycleScope.launch {
            viewModel.syncingTbScreeningBenIds.collectLatest { benIds ->
                benAdapter.submitSyncingTbScreeningBenIds(benIds)
            }
        }

        lifecycleScope.launch {
            viewModel.generalOpdBenIds.collectLatest { benIds ->
                benAdapter.submitGeneralOpdBenIds(benIds)
            }
        }
        lifecycleScope.launch {
            viewModel.unsyncedGeneralOpdBenIds.collectLatest { benIds ->
                benAdapter.submitUnsyncedGeneralOpdBenIds(benIds)
            }
        }
        lifecycleScope.launch {
            viewModel.syncingGeneralOpdBenIds.collectLatest { benIds ->
                benAdapter.submitSyncingGeneralOpdBenIds(benIds)
            }
        }

        lifecycleScope.launch {
            viewModel.anthropometryFilledBenIds.collectLatest { benIds ->
                benAdapter.submitAnthropometryBenIds(benIds)
            }
        }

        lifecycleScope.launch {
            viewModel.diagnosisBenIds.collectLatest { benIds ->
                benAdapter.submitDiagnosisBenIds(benIds)
            }
        }

        lifecycleScope.launch {
            viewModel.contactFollowUpDoneBenIds.collectLatest { benIds ->
                benAdapter.submitContactFollowUpDoneBenIds(benIds)
            }
        }

        lifecycleScope.launch {
            viewModel.tptFollowUpDoneBenIds.collectLatest { benIds ->
                benAdapter.submitTptFollowUpDoneBenIds(benIds)
            }
        }

        lifecycleScope.launch {
            viewModel.tptEligibleBenIds.collectLatest { benIds ->
                benAdapter.submitTptEligibleBenIds(benIds)
            }
        }

        lifecycleScope.launch {
            viewModel.allTbDiagnostics.collectLatest { diagnosticsList ->
                benAdapter.submitTBDiagnostics(diagnosticsList)
            }
        }

        lifecycleScope.launch {
            viewModel.retryingBenIds.collectLatest { benIds ->
                benAdapter.submitRetryingBenIds(benIds)
            }
        }

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
                                org.piramalswasthya.stoptb.work.WorkerUtils.triggerRifDiagnosticResultPollWorker(requireContext(), viewModel.tbRepo.useMockApi)
                            } else if (state.orderType.equals("SPUTUM_TRUENAT", ignoreCase = true) || state.orderType.equals("MTB_PLUS", ignoreCase = true)) {
                                org.piramalswasthya.stoptb.work.WorkerUtils.triggerTrueNatDiagnosticResultPollWorker(requireContext(), viewModel.tbRepo.useMockApi)
                            } else {
                                org.piramalswasthya.stoptb.work.WorkerUtils.triggerDiagnosticResultPollWorker(requireContext())
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

        org.piramalswasthya.stoptb.work.WorkerUtils.triggerDiagnosticResultPollWorker(requireContext())
        org.piramalswasthya.stoptb.work.WorkerUtils.triggerTrueNatDiagnosticResultPollWorker(requireContext(), viewModel.tbRepo.useMockApi)
        org.piramalswasthya.stoptb.work.WorkerUtils.triggerRifDiagnosticResultPollWorker(requireContext(), viewModel.tbRepo.useMockApi)

        binding.ibSearch.visibility = View.VISIBLE
        binding.ibSearch.setOnClickListener { sttContract.launch(Unit) }

        val searchTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun afterTextChanged(p0: Editable?) {
                viewModel.filterText(p0?.toString() ?: "")
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

        lifecycleScope.launch {
            while (viewLifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                benAdapter.notifyDataSetChanged()
                kotlinx.coroutines.delay(1000L)
            }
        }
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
        ExamineBottomSheetFragment.newInstance(benId, autoFlow = false)
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
                        "examineFlow" to !viewOnly
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
                        "autoFlow" to !viewOnly
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
        super.onStart()
        updateToolbarTitle()
    }

    override fun onResume() {
        super.onResume()
        updateToolbarTitle()
        viewModel.fetchBeneficiaryStatuses()

        // If TBSuspectedQuickFragment (Diagnosis) signalled that the examine flow
        // is fully complete, clear pendingExamineBenId so the BottomSheet does NOT
        // re-open — otherwise the back button would have to dismiss the BottomSheet
        // before it could navigate away from this screen.
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
            } else {
                getString(R.string.icon_title_ben)
            }

            when (it) {
                is HomeActivity -> it.updateActionBar(R.drawable.ic__ben, title)
                is VolunteerActivity -> it.updateActionBar(R.drawable.ic__ben, title)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
