package org.piramalswasthya.stoptb.ui.home_activity.all_household

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
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
import org.piramalswasthya.stoptb.adapters.HouseHoldListAdapter
import org.piramalswasthya.stoptb.contracts.SpeechToTextContract
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.databinding.AlertNewBenBinding
import org.piramalswasthya.stoptb.databinding.FragmentDisplaySearchRvButtonBinding
import org.piramalswasthya.stoptb.helpers.isRegistrationOfficerRole
import org.piramalswasthya.stoptb.model.Gender
import org.piramalswasthya.stoptb.model.HouseHoldBasicDomain
import org.piramalswasthya.stoptb.ui.volunteer.VolunteerActivity
import javax.inject.Inject

@AndroidEntryPoint
class AllHouseholdFragment : Fragment() {

    @Inject
    lateinit var prefDao: PreferenceDao

    private var _binding: FragmentDisplaySearchRvButtonBinding? = null
    private val binding: FragmentDisplaySearchRvButtonBinding get() = _binding!!
    private val viewModel: AllHouseholdViewModel by viewModels()

    private var hasDraft = false
    private var addBenAlert: AlertDialog? = null
    private var addBenAlertBinding: AlertNewBenBinding? = null

    private val sttContract = registerForActivityResult(SpeechToTextContract()) { value ->
        val lowerValue = value.lowercase()
        binding.searchView.setText(lowerValue)
        binding.searchView.setSelection(lowerValue.length)
        viewModel.filterText(lowerValue)
    }

    private val draftLoadAlert by lazy {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.incomplete_form_found))
            .setMessage(getString(R.string.do_you_want_to_continue_with_previous_form_or_create_a_new_form_and_discard_the_previous_form))
            .setPositiveButton(getString(R.string.open_draft)) { dialog, _ ->
                viewModel.navigateToNewHouseholdRegistration(false)
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.create_new)) { dialog, _ ->
                viewModel.navigateToNewHouseholdRegistration(true)
                dialog.dismiss()
            }
            .create()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDisplaySearchRvButtonBinding.inflate(inflater, container, false)
        viewModel.checkDraft()
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        (activity as? VolunteerActivity)?.updateActionBar(
            R.drawable.ic__hh,
            getString(R.string.icon_title_household)
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        buildAddBenDialog()

        binding.tvEmptyContent.text = getString(R.string.no_records_found_hh)
        binding.btnNextPage.text = getString(R.string.btn_text_frag_home_nhhr)
        binding.btnNextPage.visibility = View.VISIBLE

        val householdAdapter = HouseHoldListAdapter(
            diseaseType = "",
            isDisease = false,
            pref = prefDao,
            isSoftDeleteEnabled = false,
            clickListener = HouseHoldListAdapter.HouseholdClickListener(
                hhDetails = { household -> openHouseholdDetails(household) },
                showMember = { household -> openHouseholdMembers(household) },
                newBen = { household -> addMemberToHousehold(household) },
                addMDA = {},
                softDeleteHh = {}
            )
        )
        binding.rvAny.adapter = householdAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.householdList.collect { list ->
                    binding.flEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    householdAdapter.submitList(list)
                }
            }
        }

        viewModel.hasDraft.observe(viewLifecycleOwner) { hasDraft = it }
        viewModel.navigateToNewHouseholdRegistration.observe(viewLifecycleOwner) {
            if (it) {
                findNavController().navigate(
                    AllHouseholdFragmentDirections.actionAllHouseholdFragmentToNewHouseholdFragment()
                )
                viewModel.navigateToNewHouseholdRegistrationCompleted()
            }
        }

        binding.btnNextPage.setOnClickListener {
            if (hasDraft) draftLoadAlert.show()
            else viewModel.navigateToNewHouseholdRegistration(false)
        }

        binding.ibSearch.visibility = View.VISIBLE
        binding.ibSearch.setOnClickListener { sttContract.launch(Unit) }

        val searchTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                viewModel.filterText(s?.toString().orEmpty())
            }
        }

        binding.searchView.setOnFocusChangeListener { searchView, hasFocus ->
            if (hasFocus) (searchView as EditText).addTextChangedListener(searchTextWatcher)
            else (searchView as EditText).removeTextChangedListener(searchTextWatcher)
        }
    }

    private fun openHouseholdDetails(household: HouseHoldBasicDomain) {
        if (household.isDeactivate) return
        findNavController().navigate(
            AllHouseholdFragmentDirections.actionAllHouseholdFragmentToNewHouseholdFragment(household.hhId)
        )
    }

    private fun openHouseholdMembers(household: HouseHoldBasicDomain) {
        if (household.isDeactivate) return
        findNavController().navigate(
            AllHouseholdFragmentDirections.actionAllHouseholdFragmentToHouseholdMembersFragment(household.hhId)
        )
    }

    private fun addMemberToHousehold(household: HouseHoldBasicDomain) {
        if (household.isDeactivate) return
        if (household.numMembers == 0) {
            findNavController().navigate(
                AllHouseholdFragmentDirections.actionAllHouseholdFragmentToNewBenRegFragment(
                    hhId = household.hhId,
                    relToHeadId = 18
                )
            )
        } else {
            viewModel.setSelectedHouseholdId(household.hhId)
            addBenAlert?.show()
        }
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
            val items = filterRelations(
                selectedGender = selectedGender,
                baseList = baseRelationDropdown(selectedGender),
                context = computeHofContext()
            )
            applyRelationAdapter(alertBinding, items)
        }

        alertBinding.actvRth.setOnItemClickListener { _, _, _, _ ->
            alertBinding.btnOk.isEnabled = true
        }

        addBenAlert = MaterialAlertDialogBuilder(requireContext())
            .setView(alertBinding.root)
            .setOnCancelListener {
                viewModel.resetSelectedHouseholdId()
                alertBinding.rgGender.clearCheck()
                alertBinding.linearLayout4.visibility = View.GONE
                alertBinding.actvRth.text = null
                alertBinding.btnOk.isEnabled = false
            }
            .create()

        alertBinding.btnOk.setOnClickListener {
            val relIndex = resources.getStringArray(R.array.nbr_relationship_to_head_src)
                .indexOf(alertBinding.actvRth.text.toString())
            val gender = genderIntFromRadioId(alertBinding)
            if (relIndex < 0 || gender == 0) return@setOnClickListener

            findNavController().navigate(
                AllHouseholdFragmentDirections.actionAllHouseholdFragmentToNewBenRegFragment(
                    hhId = viewModel.selectedHouseholdId,
                    relToHeadId = relIndex,
                    gender = gender
                )
            )
            viewModel.resetSelectedHouseholdId()
            addBenAlert?.dismiss()
        }

        alertBinding.btnCancel.setOnClickListener {
            viewModel.resetSelectedHouseholdId()
            addBenAlert?.dismiss()
        }
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

    private data class HofContext(
        val hof: org.piramalswasthya.stoptb.model.BenRegCache?,
        val fatherRegistered: Boolean,
        val motherRegistered: Boolean,
        val unmarried: Boolean,
        val married: Boolean
    )

    private fun computeHofContext(): HofContext {
        val hof = viewModel.householdBenList.firstOrNull { it.familyHeadRelationPosition == 19 }
        val fatherRegistered = viewModel.householdBenList.any { it.familyHeadRelationPosition == 2 }
        val motherRegistered = viewModel.householdBenList.any { it.familyHeadRelationPosition == 1 }
        val unmarried = hof?.genDetails?.maritalStatusId == 1
        val married = hof?.genDetails?.maritalStatusId == 2
        return HofContext(hof, fatherRegistered, motherRegistered, unmarried, married)
    }

    private fun baseRelationDropdown(selectedGender: Gender?): List<String> {
        val relationArray = when (selectedGender) {
            Gender.FEMALE -> resources.getStringArray(R.array.nbr_relationship_to_head_female)
            Gender.MALE, Gender.TRANSGENDER -> resources.getStringArray(R.array.nbr_relationship_to_head_male)
            else -> null
        }
        return relationArray?.toList().orEmpty()
    }

    private fun filterRelations(
        selectedGender: Gender?,
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
        if (hofGender == Gender.MALE && selectedGender == Gender.MALE) {
            relationList.remove(commonRelations[5])
        }
        if (hofGender == Gender.FEMALE && selectedGender == Gender.FEMALE) {
            relationList.remove(commonRelations[4])
        }

        return relationList
    }

    private fun applyRelationAdapter(alertBinding: AlertNewBenBinding, items: List<String>) {
        alertBinding.actvRth.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, items)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        addBenAlert?.dismiss()
        addBenAlert = null
        addBenAlertBinding = null
        _binding = null
    }
}
