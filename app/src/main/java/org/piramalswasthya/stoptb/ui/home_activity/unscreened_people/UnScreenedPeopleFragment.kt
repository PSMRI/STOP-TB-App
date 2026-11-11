package org.piramalswasthya.stoptb.ui.home_activity.unscreened_people

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.adapters.BenListAdapter
import org.piramalswasthya.stoptb.adapters.BenPagingAdapter
import kotlinx.coroutines.flow.collectLatest
import org.piramalswasthya.stoptb.ui.loadedIds
import org.piramalswasthya.stoptb.ui.ListTransitions
import org.piramalswasthya.stoptb.ui.renderListStates
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.databinding.FragmentUnScreenedPeopleBinding
import org.piramalswasthya.stoptb.helpers.RoleManager
import org.piramalswasthya.stoptb.repositories.BenRepo
import org.piramalswasthya.stoptb.model.BenBasicDomain
import org.piramalswasthya.stoptb.ui.home_activity.all_ben.examine.ExamineBottomSheetFragment
import javax.inject.Inject

@AndroidEntryPoint
class UnScreenedPeople : Fragment(),
    ExamineBottomSheetFragment.ExamineCallback {

    @Inject
    lateinit var prefDao: PreferenceDao

    @Inject
    lateinit var roleManager: RoleManager

    @Inject
    lateinit var benRepo: BenRepo

    private var _binding: FragmentUnScreenedPeopleBinding? = null
    private val binding get() = _binding!!

    private val viewModel: UnScreenedPeopleViewModel by viewModels()

    private lateinit var benAdapter: BenPagingAdapter
    private var listTransitions: ListTransitions? = null

    private var pendingExamineBenId: Long? = null
    // Latest showExamineBottomSheet call; an older call whose ID lookup finishes late is dropped.
    private var examineRequestId = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUnScreenedPeopleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)



        binding.searchView.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                listTransitions?.onQueryChanged(query)
                viewModel.filterText(query)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        benAdapter = BenPagingAdapter(
            clickListener = BenListAdapter.BenClickListener(

                clickedBen = { _, _, _, _ ->

                },

                clickedWifeBen = { _, _, _, _ -> },

                clickedHusbandBen = { _, _, _, _ -> },

                clickedChildben = { _, _, _, _ -> },

                clickedHousehold = { _, _ -> },

                clickedABHA = { _, _, _ -> },

                clickedAddAllBenBtn = { _, _, _, _, _ -> },

                callBen = { },

                softDeleteBen = { },

                clickedNonHHHousehold = { },

                clickedExamine = { _, benId ->
                    pendingExamineBenId = benId
                    showExamineBottomSheet(benId)
                },

//                clickedUnscreened = { ben ->
//                    viewModel.markSymptomsScreened(ben.benId)
//                }
            ),
            showBeneficiaries = true,
            showRegistrationDate = true,
            showSyncIcon = true,
            showCall = false,
            // role = prefDao.getRole(),
            pref = prefDao,
            context = requireActivity(),
            showActionButtons = false,
            showScreeningStatus  = true,
            showRedesignedCard = true,
            roleManager = roleManager

        )

        // Add Ben button hidden — ben registration only via UnScreenPeople flow
        binding.btnNextPage.visibility = View.GONE


        binding.rvUnscreened.adapter = benAdapter
        listTransitions = ListTransitions(binding.rvUnscreened)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.unscreenedList.collectLatest { page -> benAdapter.submitData(page) } }
                launch {
                    benAdapter.renderListStates(onSettled = { listTransitions?.onSettled() }, errorAnchor = binding.root, render = { awaitingFirstLoad, isEmpty ->
                        _binding?.let { b ->
                            b.flLoading.visibility = if (awaitingFirstLoad) View.VISIBLE else View.GONE
                            b.tvEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
                            b.rvUnscreened.visibility = if (awaitingFirstLoad || isEmpty) View.INVISIBLE else View.VISIBLE
                        }
                    })
                }
                launch { benAdapter.loadedIds { item -> item.benId }.collect { benIds -> viewModel.onRowsLoaded(benIds) } }
                launch { viewModel.vitalBenIds.collect { benAdapter.submitBenIds(it) } }
                launch { viewModel.tbScreeningBenIds.collect { benAdapter.submitTbScreeningBenIds(it) } }
                launch { viewModel.generalOpdBenIds.collect { benAdapter.submitGeneralOpdBenIds(it) } }
                launch { viewModel.anthropometryBenIds.collect { benAdapter.submitAnthropometryBenIds(it) } }
                launch { viewModel.diagnosisBenIds.collect { benAdapter.submitDiagnosisBenIds(it) } }
                launch { viewModel.allTbDiagnostics.collect { benAdapter.submitTBDiagnostics(it) } }
                launch { viewModel.householdMemberCounts.collect { benAdapter.submitHouseholdMemberCounts(it) } }
            }
        }
    }

    private fun showExamineBottomSheet(benId: Long) {
        val existing =
            childFragmentManager.findFragmentByTag(ExamineBottomSheetFragment.TAG)
        if (existing != null) return

        val requestId = ++examineRequestId
        viewLifecycleOwner.lifecycleScope.launch {
            // In Camp Mode the push can replace a temp benId after this screen captured it;
            // open the sheet (and the forms it launches) with the current ID.
            val currentBenId = benRepo.resolveCurrentBenId(benId)
            if (requestId != examineRequestId) return@launch
            if (pendingExamineBenId == benId) pendingExamineBenId = currentBenId
            if (childFragmentManager.isStateSaved ||
                childFragmentManager.findFragmentByTag(ExamineBottomSheetFragment.TAG) != null
            ) return@launch
            ExamineBottomSheetFragment
                .newInstance(currentBenId, autoFlow = false)
                .show(childFragmentManager, ExamineBottomSheetFragment.TAG)
        }
    }

    override fun onNavigateToExamineForm(
        benId: Long,
        formIndex: Int,
        viewOnly: Boolean
    ) {

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

                lifecycleScope.launch {

                    val ben = viewModel.getBenFromId(benId)
                    val benRegId = ben?.benRegId ?: 0L

                    findNavController().navigate(
                        R.id.vitalScreenFragment,
                        bundleOf(
                            "benId" to benId,
                            "benRegId" to benRegId,
                            "autoFlow" to !viewOnly
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
        }
    }

    override fun onExamineDismissed() {
        pendingExamineBenId = null
    }

    override fun onResume() {
        super.onResume()

        val sh = findNavController().currentBackStackEntry?.savedStateHandle

        if (sh?.remove<Boolean>("examine_flow_done") == true) {
            pendingExamineBenId = null
        }

        pendingExamineBenId?.let {
            showExamineBottomSheet(it)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // ListTransitions holds the RecyclerView; drop it with the view.
        listTransitions = null
        _binding = null
    }


    override fun onStart() {
        super.onStart()
        updateToolbarTitle()
    }

    private fun updateToolbarTitle() {
        activity?.let {
            val title = getString(R.string.unscreened_population_title)
            when (it) {
                is org.piramalswasthya.stoptb.ui.home_activity.HomeActivity ->
                    it.updateActionBar(R.drawable.ic__ben, title)
                is org.piramalswasthya.stoptb.ui.volunteer.VolunteerActivity ->
                    it.updateActionBar(R.drawable.ic__ben, title)
            }
        }
    }
}
