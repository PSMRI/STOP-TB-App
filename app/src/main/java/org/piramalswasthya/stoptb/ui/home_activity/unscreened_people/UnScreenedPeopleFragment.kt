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
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.databinding.FragmentUnScreenedPeopleBinding
import org.piramalswasthya.stoptb.model.BenBasicDomain
import org.piramalswasthya.stoptb.ui.home_activity.all_ben.examine.ExamineBottomSheetFragment
import javax.inject.Inject

@AndroidEntryPoint
class UnScreenedPeople : Fragment(),
    ExamineBottomSheetFragment.ExamineCallback {

    @Inject
    lateinit var prefDao: PreferenceDao

    private var _binding: FragmentUnScreenedPeopleBinding? = null
    private val binding get() = _binding!!

    private val viewModel: UnScreenedPeopleViewModel by viewModels()

    private lateinit var benAdapter: BenListAdapter

    private var pendingExamineBenId: Long? = null

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
                viewModel.filterText(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        benAdapter = BenListAdapter(
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
            showSyncIcon = false,
            showCall = false,
           // role = prefDao.getRole(),
            pref = prefDao,
            context = requireActivity(),
            showActionButtons = false,
            showScreeningStatus  = true
        )

        binding.rvUnscreened.adapter = benAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.unscreenedCount.collect {
                        binding.tvUnscreenedCount.text = "Unscreened: $it"
                    }
                }
                launch {
                    viewModel.unscreenedList.collect { list ->
                        benAdapter.submitList(list)
                        binding.tvEmpty.visibility =
                            if (list.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    private fun showExamineBottomSheet(benId: Long) {
        val existing =
            childFragmentManager.findFragmentByTag(ExamineBottomSheetFragment.TAG)
        if (existing != null) return

        ExamineBottomSheetFragment
            .newInstance(benId, autoFlow = false)
            .show(childFragmentManager, ExamineBottomSheetFragment.TAG)
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
        _binding = null
    }
}