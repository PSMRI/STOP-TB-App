package org.piramalswasthya.stoptb.ui.home_activity.household_members

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
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
import org.piramalswasthya.stoptb.ui.home_activity.HomeActivity
import javax.inject.Inject

@AndroidEntryPoint
class HouseholdMembersFragment : Fragment() {

    @Inject
    lateinit var prefDao: PreferenceDao

    private var _binding: FragmentHouseholdMembersBinding? = null
    private val binding: FragmentHouseholdMembersBinding get() = _binding!!
    private val viewModel: HouseholdMembersViewModel by viewModels()

    private var addBenAlert: AlertDialog? = null
    private var addBenAlertBinding: AlertNewBenBinding? = null
    private var selectedRelationIndex = -1

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
        (activity as? HomeActivity)?.updateActionBar(
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
                clickedWifeBen = { _, hhId, benId, relToHeadId ->
                    openMemberForm(hhId, benId, relToHeadId)
                },
                clickedHusbandBen = { _, hhId, benId, relToHeadId ->
                    openMemberForm(hhId, benId, relToHeadId)
                },
                clickedChildben = { _, hhId, benId, relToHeadId ->
                    openMemberForm(hhId, benId, relToHeadId)
                },
                clickedHousehold = { _, _ -> },
                clickedABHA = { _, _, _ -> },
                clickedAddAllBenBtn = { _, _, _, _, _ -> },
                callBen = {},
                softDeleteBen = {}
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

        binding.fabAddMember.setOnClickListener {
            addBenAlert?.show()
        }
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
