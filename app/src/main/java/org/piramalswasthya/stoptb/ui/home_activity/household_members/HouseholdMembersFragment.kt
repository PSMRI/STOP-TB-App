package org.piramalswasthya.stoptb.ui.home_activity.household_members

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.adapters.BenListAdapter
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.databinding.AlertNewBenBinding
import org.piramalswasthya.stoptb.databinding.FragmentHouseholdMembersBinding
import org.piramalswasthya.stoptb.model.Gender
import org.piramalswasthya.stoptb.ui.home_activity.all_ben.examine.ExamineBottomSheetFragment
import org.piramalswasthya.stoptb.helpers.isNurseRole
import org.piramalswasthya.stoptb.ui.volunteer.VolunteerActivity
import javax.inject.Inject

@AndroidEntryPoint
class HouseholdMembersFragment : Fragment(), ExamineBottomSheetFragment.ExamineCallback {

    @Inject
    lateinit var prefDao: PreferenceDao

    private var _binding: FragmentHouseholdMembersBinding? = null
    private val binding: FragmentHouseholdMembersBinding get() = _binding!!
    private val viewModel: HouseholdMembersViewModel by viewModels()

    private var addBenAlert: AlertDialog? = null
    private var addBenAlertBinding: AlertNewBenBinding? = null
    private var selectedRelationIndex = -1
    private var pendingExamineBenId: Long? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHouseholdMembersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        (activity as? VolunteerActivity)?.updateActionBar(
            R.drawable.ic__hh,
            getString(R.string.household_members)
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        buildAddBenDialog()

        val benAdapter = BenListAdapter(
            clickListener = BenListAdapter.BenClickListener(
                clickedBen = { _, hhId, benId, relToHeadId ->
                    openMemberForm(hhId, benId, relToHeadId)
                },
                clickedWifeBen = { _, hhId, benId, _ ->
                    // "Register Wife" button — navigate to new ben reg as Wife (index 4 = Wife)
                    findNavController().navigate(
                        HouseholdMembersFragmentDirections.actionHouseholdMembersFragmentToNewBenRegFragment(
                            hhId = hhId,
                            relToHeadId = 4,        // "Wife" index in nbr_relationship_to_head_src
                            gender = 2,             // Female
                            selectedBenId = benId,  // original member's ID → mark isSpouseAdded after save
                            isAddSpouse = 1
                        )
                    )
                },
                clickedHusbandBen = { _, hhId, benId, _ ->
                    // "Register Husband" button — navigate to new ben reg as Husband (index 5 = Husband)
                    findNavController().navigate(
                        HouseholdMembersFragmentDirections.actionHouseholdMembersFragmentToNewBenRegFragment(
                            hhId = hhId,
                            relToHeadId = 5,        // "Husband" index in nbr_relationship_to_head_src
                            gender = 1,             // Male
                            selectedBenId = benId,  // original member's ID → mark isSpouseAdded after save
                            isAddSpouse = 1
                        )
                    )
                },
                clickedChildben = { _, hhId, benId, relToHeadId ->
                    openMemberForm(hhId, benId, relToHeadId)
                },
                clickedHousehold = { _, _ -> },
                clickedABHA = { _, _, _ -> },
                clickedAddAllBenBtn = { _, _, _, _, _ -> },
                callBen = {},
                softDeleteBen = {},
                clickedExamine = { _, benId ->
                    showExamineBottomSheet(benId)
                }
            ),
            showBeneficiaries = true,
            showRegistrationDate = true,
            showSyncIcon = true,
            pref = prefDao,
            context = requireActivity()
        )
        binding.rvAny.adapter = benAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.benList.collect { list ->
                    binding.flEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    benAdapter.submitList(list)
                }
            }
        }

        // Examine count — same data as AllBenFragment so buttons show correct fill status
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.vitalBenIds.collect { benAdapter.submitBenIds(it) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.tbScreeningBenIds.collect { benAdapter.submitTbScreeningBenIds(it) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.generalOpdBenIds.collect { benAdapter.submitGeneralOpdBenIds(it) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.anthropometryBenIds.collect { benAdapter.submitAnthropometryBenIds(it) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.diagnosisBenIds.collect { benAdapter.submitDiagnosisBenIds(it) }
            }
        }

        // Nurse role: invisible (takes space but not visible/clickable)
        val isNurse = prefDao.getLoggedInUser()?.role.isNurseRole()
        binding.fabAddMember.visibility = if (isNurse) View.INVISIBLE else View.VISIBLE
        binding.fabAddMember.setOnClickListener {
            addBenAlert?.show()
        }
    }

    override fun onResume() {
        super.onResume()
        val benId = pendingExamineBenId
        if (benId != null) {
            // Always without autoFlow — prevents form re-opening when back is pressed
            showExamineBottomSheet(benId)
        }
    }

    private fun showExamineBottomSheet(benId: Long) {
        val existing = childFragmentManager.findFragmentByTag(ExamineBottomSheetFragment.TAG)
        if (existing != null) return
        ExamineBottomSheetFragment.newInstance(benId, autoFlow = false)
            .show(childFragmentManager, ExamineBottomSheetFragment.TAG)
    }

    override fun onNavigateToExamineForm(benId: Long, formIndex: Int, viewOnly: Boolean) {
        pendingExamineBenId = benId
        when (formIndex) {
            ExamineBottomSheetFragment.FORM_ANTHROPOMETRY -> {
                findNavController().navigate(
                    R.id.anthropometryFragment,
                    bundleOf("benId" to benId, "autoFlow" to false, "examineFlow" to !viewOnly)
                )
            }
            ExamineBottomSheetFragment.FORM_GENERAL_EXAM -> {
                viewLifecycleOwner.lifecycleScope.launch {
                    val benRegId = viewModel.getBenRegId(benId)
                    findNavController().navigate(
                        R.id.vitalScreenFragment,
                        bundleOf("benId" to benId, "benRegId" to benRegId, "autoFlow" to !viewOnly)
                    )
                }
            }
            ExamineBottomSheetFragment.FORM_TB_SCREENING -> {
                findNavController().navigate(
                    R.id.TBScreeningFormFragment,
                    bundleOf(
                        "benId" to benId,
                        "autoFlow" to !viewOnly,
                        "openedFromHousehold" to true
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
            ExamineBottomSheetFragment.FORM_DIAGNOSIS -> {
                findNavController().navigate(
                    R.id.TBSuspectedQuickFragment,
                    bundleOf("benId" to benId, "viewOnly" to viewOnly)
                )
            }
        }
    }

    override fun onExamineDismissed() {
        pendingExamineBenId = null
    }

    private fun openMemberForm(hhId: Long, benId: Long, relToHeadId: Int) {
        findNavController().navigate(
            HouseholdMembersFragmentDirections.actionHouseholdMembersFragmentToNewBenRegFragment(
                hhId = hhId,
                relToHeadId = relToHeadId,
                benId = benId
            )
        )
    }

    private fun buildAddBenDialog() {
        val alertBinding = AlertNewBenBinding.inflate(layoutInflater, binding.root, false)
        addBenAlertBinding = alertBinding
        alertBinding.btnOk.isEnabled = false

        alertBinding.rgGender.setOnCheckedChangeListener { _, checkedId ->
            val selectedGender = genderFromRadioId(alertBinding, checkedId) ?: run {
                alertBinding.linearLayout4.visibility = View.GONE
                return@setOnCheckedChangeListener
            }
            alertBinding.linearLayout4.visibility = View.VISIBLE
            alertBinding.actvRth.text = null
            alertBinding.btnOk.isEnabled = false
            selectedRelationIndex = -1
            val sourceItems = resources.getStringArray(R.array.nbr_relationship_to_head_src)
            val items = when (selectedGender) {
                Gender.FEMALE -> resources.getStringArray(R.array.nbr_relationship_to_head_female)
                else -> resources.getStringArray(R.array.nbr_relationship_to_head_male)
            }.map { item -> item to sourceItems.indexOf(item) }
            val visibleItems = items.map { it.first }
            alertBinding.actvRth.setAdapter(
                ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, visibleItems)
            )
        }

        alertBinding.actvRth.setOnItemClickListener { _, _, position, _ ->
            val selectedGender = genderFromRadioId(alertBinding, alertBinding.rgGender.checkedRadioButtonId)
            selectedRelationIndex = getRelationIndex(selectedGender, position)
            alertBinding.btnOk.isEnabled = true
        }

        addBenAlert = MaterialAlertDialogBuilder(requireContext())
            .setView(alertBinding.root)
            .setOnCancelListener { resetAddBenDialog(alertBinding) }
            .create()

        alertBinding.btnOk.setOnClickListener {
            val gender = genderIntFromRadioId(alertBinding)
            if (selectedRelationIndex < 0 || gender == 0) return@setOnClickListener

            findNavController().navigate(
                HouseholdMembersFragmentDirections.actionHouseholdMembersFragmentToNewBenRegFragment(
                    hhId = viewModel.hhId,
                    relToHeadId = selectedRelationIndex,
                    gender = gender
                )
            )
            addBenAlert?.dismiss()
        }

        alertBinding.btnCancel.setOnClickListener {
            addBenAlert?.dismiss()
        }
    }

    private fun resetAddBenDialog(alertBinding: AlertNewBenBinding) {
        alertBinding.rgGender.clearCheck()
        alertBinding.linearLayout4.visibility = View.GONE
        alertBinding.actvRth.text = null
        alertBinding.btnOk.isEnabled = false
        selectedRelationIndex = -1
    }

    private fun getRelationIndex(selectedGender: Gender?, position: Int): Int {
        val sourceItems = resources.getStringArray(R.array.nbr_relationship_to_head_src)
        val selectedItems = when (selectedGender) {
            Gender.FEMALE -> resources.getStringArray(R.array.nbr_relationship_to_head_female)
            Gender.MALE, Gender.TRANSGENDER, Gender.PREFER_NOT_TO_SAY ->
                resources.getStringArray(R.array.nbr_relationship_to_head_male)
            null -> return -1
        }
        return sourceItems.indexOf(selectedItems.getOrNull(position))
    }

    private fun genderFromRadioId(alertBinding: AlertNewBenBinding, checkedId: Int): Gender? =
        when (checkedId) {
            alertBinding.rbMale.id -> Gender.MALE
            alertBinding.rbFemale.id -> Gender.FEMALE
            alertBinding.rbTrans.id -> Gender.TRANSGENDER
            else -> null
        }

    private fun genderIntFromRadioId(alertBinding: AlertNewBenBinding): Int =
        when (alertBinding.rgGender.checkedRadioButtonId) {
            alertBinding.rbMale.id -> 1
            alertBinding.rbFemale.id -> 2
            alertBinding.rbTrans.id -> 3
            else -> 0
        }

    override fun onDestroyView() {
        super.onDestroyView()
        addBenAlert?.dismiss()
        addBenAlert = null
        addBenAlertBinding = null
        _binding = null
    }
}
