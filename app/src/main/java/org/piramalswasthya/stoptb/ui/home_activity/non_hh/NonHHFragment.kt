package org.piramalswasthya.stoptb.ui.home_activity.non_hh

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.adapters.BenListAdapter
import org.piramalswasthya.stoptb.contracts.SpeechToTextContract
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.databinding.FragmentDisplaySearchAndToggleRvButtonBinding
import org.piramalswasthya.stoptb.helpers.isNurseRole
import org.piramalswasthya.stoptb.model.BenBasicDomain
import org.piramalswasthya.stoptb.ui.home_activity.all_ben.examine.ExamineBottomSheetFragment
import androidx.core.os.bundleOf
import javax.inject.Inject

@AndroidEntryPoint
class NonHHFragment : Fragment(), ExamineBottomSheetFragment.ExamineCallback {

    @Inject
    lateinit var prefDao: PreferenceDao

    private var _binding: FragmentDisplaySearchAndToggleRvButtonBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NonHHViewModel by viewModels()

    private lateinit var benAdapter: BenListAdapter
    private var pendingExamineBenId: Long? = null

    private val sttContract = registerForActivityResult(SpeechToTextContract()) { value ->
        val lowerValue = value.lowercase()
        binding.searchView.setText(lowerValue)
        binding.searchView.setSelection(lowerValue.length)
        viewModel.filterText(lowerValue)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDisplaySearchAndToggleRvButtonBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnNextPage.text = "Add Beneficiary"
        binding.btnNextPage.visibility = View.VISIBLE
        binding.ibFilter.visibility = View.GONE
        binding.ibDownload.visibility = View.GONE
        binding.llQuickRefresh.visibility = View.GONE

        binding.btnNextPage.setOnClickListener {
            findNavController().navigate(NonHHFragmentDirections.actionNonHHFragmentToCurrentLivingInfoFragment())
        }

        binding.searchView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.filterText(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.ibSearch.setOnClickListener {
            sttContract.launch(Unit)
        }

        val roleName = prefDao.getLoggedInUser()?.role
        val isNurse = roleName.isNurseRole()

        benAdapter = BenListAdapter(
            clickListener = BenListAdapter.BenClickListener(
                clickedBen = { _, hhId, benId, relToHeadId ->
                    findNavController().navigate(
                        NonHHFragmentDirections.actionNonHHFragmentToNewBenRegFragment(
                            hhId = hhId,
                            benId = benId,
                            relToHeadId = relToHeadId,
                            isAddSpouse = 0,
                            gender = 0,
                            isNonHH = true
                        )
                    )
                },
                clickedWifeBen = { _, _, _, _ -> },
                clickedHusbandBen = { _, _, _, _ -> },
                clickedChildben = { _, _, _, _ -> },
                clickedHousehold = { _, _ -> },
                clickedABHA = { _, _, _ -> },
                clickedAddAllBenBtn = { _, _, _, _, _ -> },
                callBen = { },
                softDeleteBen = { },
                clickedNonHHHousehold = { item ->
                    triggerLinkHouseholdFlow(item.benId)
                },
                clickedExamine = { _, benId ->
                    pendingExamineBenId = benId
                    showExamineBottomSheet(benId)
                }
            ),
            showBeneficiaries = true,
            showRegistrationDate = true,
            showSyncIcon = true,
            showCall = true,
            role = roleName?.let { if (it.isNurseRole()) 2 else 0 } ?: 0,
            pref = prefDao,
            context = requireActivity(),
            showActionButtons = false
        )

        binding.rvAny.adapter = benAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.nonHHList.collectLatest { list ->
                benAdapter.submitList(list) {
                    if (_binding != null && list.isNotEmpty()) {
                        binding.rvAny.scrollToPosition(0)
                    }
                }
                if (_binding != null) {
                    if (list.isEmpty()) {
                        binding.flEmpty.visibility = View.VISIBLE
                        binding.rvAny.visibility = View.GONE
                    } else {
                        binding.flEmpty.visibility = View.GONE
                        binding.rvAny.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun triggerLinkHouseholdFlow(benId: Long) {
        val options = arrayOf("Link to Existing Household", "Create New Household")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Link Household")
            .setItems(options) { dialog, which ->
                dialog.dismiss()
                if (which == 0) {
                    showExistingHouseholdSelectionDialog(benId)
                } else {
                    findNavController().navigate(
                        NonHHFragmentDirections.actionNonHHFragmentToNewHouseholdFragment(
                            hhId = 0L,
                            isAshaFamily = "No",
                            linkBenId = benId
                        )
                    )
                }
            }
            .show()
    }

    private fun showExistingHouseholdSelectionDialog(benId: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.hhList.firstOrNull()?.let { hhList ->
                if (hhList.isEmpty()) {
                    Toast.makeText(requireContext(), "No existing households found in this village", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                data class HouseholdSearchItem(
                    val displayText: String,
                    val hhId: Long,
                    val headName: String,
                    val famName: String
                )

                val searchItems = hhList.map { hh ->
                    val headName = hh.household.family?.familyHeadName ?: "No Head Name"
                    val famName = hh.household.family?.familyName ?: ""
                    val displayText = "Head: $headName $famName (HH ID: ${hh.household.householdId})"
                    HouseholdSearchItem(displayText, hh.household.householdId, headName, famName)
                }

                val context = requireContext()
                val container = android.widget.LinearLayout(context).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    val paddingPx = (16 * context.resources.displayMetrics.density).toInt()
                    setPadding(paddingPx, paddingPx, paddingPx, 0)
                }

                val searchInput = android.widget.EditText(context).apply {
                    hint = "Search by Head Name or HH ID..."
                    setSingleLine(true)
                    maxLines = 1
                    val searchIcon = androidx.core.content.ContextCompat.getDrawable(context, android.R.drawable.ic_menu_search)
                    setCompoundDrawablesWithIntrinsicBounds(searchIcon, null, null, null)
                    compoundDrawablePadding = (8 * context.resources.displayMetrics.density).toInt()
                    val p = (10 * context.resources.displayMetrics.density).toInt()
                    setPadding(p, p, p, p)
                }
                container.addView(searchInput)

                val adapterList = searchItems.map { it.displayText }.toMutableList()
                val arrayAdapter = android.widget.ArrayAdapter(context, android.R.layout.simple_list_item_1, adapterList)

                var filteredItems = searchItems.toList()

                val listView = android.widget.ListView(context).apply {
                    adapter = arrayAdapter
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        (300 * context.resources.displayMetrics.density).toInt()
                    )
                }
                container.addView(listView)

                val tvEmptyResult = android.widget.TextView(context).apply {
                    text = "No matching result found"
                    gravity = android.view.Gravity.CENTER
                    visibility = android.view.View.GONE
                    val p = (16 * context.resources.displayMetrics.density).toInt()
                    setPadding(p, p, p, p)
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                }
                container.addView(tvEmptyResult)

                val dialog = MaterialAlertDialogBuilder(context)
                    .setTitle("Select Household")
                    .setView(container)
                    .setNegativeButton("Cancel", null)
                    .create()

                searchInput.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        val query = s.toString().trim()
                        filteredItems = if (query.isEmpty()) {
                            searchItems
                        } else {
                            searchItems.filter { item ->
                                item.headName.contains(query, ignoreCase = true) ||
                                item.famName.contains(query, ignoreCase = true) ||
                                item.hhId.toString().contains(query, ignoreCase = true)
                            }
                        }
                        arrayAdapter.clear()
                        arrayAdapter.addAll(filteredItems.map { it.displayText })
                        arrayAdapter.notifyDataSetChanged()

                        if (filteredItems.isEmpty()) {
                            listView.visibility = android.view.View.GONE
                            tvEmptyResult.visibility = android.view.View.VISIBLE
                        } else {
                            listView.visibility = android.view.View.VISIBLE
                            tvEmptyResult.visibility = android.view.View.GONE
                        }
                    }
                    override fun afterTextChanged(s: Editable?) {}
                })

                listView.setOnItemClickListener { _, _, position, _ ->
                    dialog.dismiss()
                    if (position in filteredItems.indices) {
                        val selected = filteredItems[position]
                        showRelationshipSelectionDialog(benId, selected.hhId)
                    }
                }

                dialog.show()
            }
        }
    }

    private fun showRelationshipSelectionDialog(benId: Long, hhId: Long) {
        val relations = arrayOf("Self", "Spouse", "Son", "Daughter", "Mother", "Father", "Brother", "Sister", "Other")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select Relationship to HOF")
            .setItems(relations) { dialog, index ->
                dialog.dismiss()
                val selectedRelation = relations[index]
                viewLifecycleOwner.lifecycleScope.launch {
                    val ben = viewModel.getBenFromId(benId) ?: return@launch
                    val relationPos: Int
                    val relationName: String
                    when (selectedRelation) {
                        "Self" -> {
                            relationPos = 19
                            relationName = "Self"
                        }
                        "Spouse" -> {
                            if (ben.genderId == 1) {
                                relationPos = 6
                                relationName = "Husband"
                            } else {
                                relationPos = 5
                                relationName = "Wife"
                            }
                        }
                        "Son" -> {
                            relationPos = 9
                            relationName = "Son"
                        }
                        "Daughter" -> {
                            relationPos = 10
                            relationName = "Daughter"
                        }
                        "Mother" -> {
                            relationPos = 1
                            relationName = "Mother"
                        }
                        "Father" -> {
                            relationPos = 2
                            relationName = "Father"
                        }
                        "Brother" -> {
                            relationPos = 3
                            relationName = "Brother"
                        }
                        "Sister" -> {
                            relationPos = 4
                            relationName = "Sister"
                        }
                        else -> {
                            relationPos = 21
                            relationName = "Other"
                        }
                    }
                    viewModel.linkBenToHousehold(benId, hhId, relationPos, relationName)
                    Toast.makeText(requireContext(), "Linked to household successfully", Toast.LENGTH_SHORT).show()
                    org.piramalswasthya.stoptb.work.WorkerUtils.triggerAmritPushWorker(requireContext())
                }
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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
                    bundleOf(
                        "benId" to benId,
                        "autoFlow" to false,
                        "examineFlow" to !viewOnly
                    )
                )
            }
            ExamineBottomSheetFragment.FORM_GENERAL_EXAM -> {
                viewLifecycleOwner.lifecycleScope.launch {
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
            /*ExamineBottomSheetFragment.FORM_DIAGNOSIS -> {
                findNavController().navigate(
                    R.id.TBSuspectedQuickFragment,
                    bundleOf(
                        "benId" to benId,
                        "viewOnly" to viewOnly
                    )
                )
            }
             */
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
        val benId = pendingExamineBenId
        if (benId != null) {
            showExamineBottomSheet(benId)
        }
    }
}
