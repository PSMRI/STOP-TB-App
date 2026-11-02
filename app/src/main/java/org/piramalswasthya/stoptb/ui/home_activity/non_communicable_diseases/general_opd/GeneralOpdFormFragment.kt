package org.piramalswasthya.stoptb.ui.home_activity.non_communicable_diseases.general_opd

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.adapters.FormInputAdapter
import org.piramalswasthya.stoptb.databinding.FragmentGeneralOpdFormBinding
import org.piramalswasthya.stoptb.helpers.applyManagedFlowBackPolicyOnResume
import org.piramalswasthya.stoptb.ui.home_activity.HomeActivity
import org.piramalswasthya.stoptb.ui.volunteer.VolunteerActivity
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.work.WorkerUtils
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class GeneralOpdFormFragment : Fragment() {

    @Inject lateinit var preferenceDao: PreferenceDao

    private var _binding: FragmentGeneralOpdFormBinding? = null
    private val binding: FragmentGeneralOpdFormBinding
        get() = _binding!!

    private val viewModel: GeneralOpdFormViewModel by viewModels()
    private var prescriptionBound = false
    private val openedFromHousehold: Boolean
        get() = arguments?.getBoolean("openedFromHousehold", false) == true

    private val isManagedFlow: Boolean
        get() = viewModel.autoFlow || viewModel.generalOpdFlow

    /** Always allow back — matches VitalScreen behaviour.
     *  autoFlow / generalOpdFlow only controls the forward-chain (auto-navigate to
     *  Diagnostics after submit), not whether the user can go back. */
    private val allowBackNavigation: Boolean
        get() = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGeneralOpdFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Back navigation allowed — each form now returns to AllBenFragment independently
        // blockBackNavigationInManagedFlow(isManagedFlow, allowBackNavigation)

        viewModel.recordExists.observe(viewLifecycleOwner) { exists ->
            exists?.let { recordExists ->
                val readOnly = recordExists || viewModel.viewOnly
                binding.form.root.visibility = if (readOnly) View.GONE else View.VISIBLE
                binding.savedChiefComplaintLayout.visibility = if (readOnly) View.VISIBLE else View.GONE
                binding.savedChiefComplaint.setText(viewModel.savedChiefComplaintText.value.orEmpty())
                val adapter = FormInputAdapter(
                    formValueListener = FormInputAdapter.FormValueListener { formId, index ->
                        viewModel.updateListOnValueChanged(formId, index)
                    },
                    isEnabled = !(recordExists || viewModel.viewOnly)
                )
                binding.btnSubmit.visibility =
                    if (recordExists || viewModel.viewOnly) View.GONE else View.VISIBLE
                binding.btnCancel.visibility =
                    if (recordExists || viewModel.viewOnly) View.GONE else View.VISIBLE
                binding.btnCancel.text = getString(R.string.btn_skip)
                binding.form.rvInputForm.adapter = adapter
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.formList.collect {
                        adapter.submitList(it)
                    }
                }
            }
        }

        viewModel.benName.observe(viewLifecycleOwner) {
            binding.tvBenName.text = it
        }
        viewModel.benAgeGender.observe(viewLifecycleOwner) {
            binding.tvAgeGender.text = it
        }
        viewModel.savedChiefComplaintText.observe(viewLifecycleOwner) {
            binding.savedChiefComplaint.setText(it)
        }
        viewModel.drugMasters.observe(viewLifecycleOwner) {
            binding.prescriptionEditor.setMasters(it)
        }

        viewModel.medicines.observe(viewLifecycleOwner) { rows ->
            // Card edits already update their views; rebinding would interrupt dropdowns.
            if (prescriptionBound) return@observe
            binding.prescriptionEditor.bind(rows, !(viewModel.recordExists.value == true || viewModel.viewOnly))
            binding.prescriptionEditor.onChanged = medicineListener
            prescriptionBound = true
        }
        viewModel.prescriptionVisible.observe(viewLifecycleOwner) {
            binding.prescriptionSection.visibility = if (it) View.VISIBLE else View.GONE
        }
        viewModel.notes.observe(viewLifecycleOwner) {
            if (binding.notes.text.toString() != it) binding.notes.setText(it)
            binding.notes.isEnabled = !(viewModel.recordExists.value == true || viewModel.viewOnly)
        }
        binding.notes.doAfterTextChanged { viewModel.updateNotes(it?.toString().orEmpty()) }

        binding.btnCancel.setOnClickListener {
            viewModel.skipForm()
        }
        binding.btnSubmit.setOnClickListener {
            submitGeneralOpdForm()
        }

        viewModel.state.observe(viewLifecycleOwner) {
            when (it) {
                GeneralOpdFormViewModel.State.SAVE_SUCCESS -> {
//                    Toast.makeText(
//                        requireContext(),
//                        getString(R.string.general_opd_submitted),
//                        Toast.LENGTH_SHORT
//                    ).show()
                    WorkerUtils.triggerCampAwarePushWorker(requireContext(), preferenceDao)
                    navigateToDiagnostics()
                }

                GeneralOpdFormViewModel.State.SKIP_SUCCESS -> {
                    navigateToDiagnostics()
                }

                GeneralOpdFormViewModel.State.SAVE_FAILED -> {
                    Toast.makeText(
                        requireContext(),
                        resources.getString(R.string.something_went_wrong_try_again),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                else -> {}
            }
        }
    }

    private fun submitGeneralOpdForm() {
        if (!binding.prescriptionEditor.validate(viewModel.requiresMedicine())) {
            Toast.makeText(requireContext(), R.string.opd_complete_medicine, Toast.LENGTH_SHORT).show()
            return
        }
        if (validateCurrentPage()) {
            viewModel.saveForm()
        }
    }

    private val medicineListener: (List<org.piramalswasthya.stoptb.model.OpdMedicineDraft>) -> Unit = {
        viewModel.updateMedicines(it)
    }

    private fun validateCurrentPage(): Boolean {
        val result = binding.form.rvInputForm.adapter?.let {
            (it as FormInputAdapter).validateInput(resources, binding.form.rvInputForm)
        } ?: -1
        Timber.d("Validation : $result")
        return result == -1
    }

    private fun navigateToDiagnostics() {
        if (openedFromHousehold || isManagedFlow) {
            findNavController().navigateUp()
            return
        } else {
            findNavController().navigate(
                R.id.TBSuspectedQuickFragment,
                bundleOf(
                    "benId" to viewModel.benId,
                    "autoFlow" to viewModel.autoFlow,
                    "generalOpdFlow" to viewModel.generalOpdFlow
                )
            )
        }
    }

    override fun onStart() {
        super.onStart()
        activity?.let {
            when (it) {
//                is HomeActivity -> it.updateActionBar(
//                    R.drawable.ic__ncd,
//                    getString(R.string.general_opd)
//                ).also { _ -> it.setToolbarNavigationVisible(!viewModel.autoFlow)
//                }
//
//                is VolunteerActivity -> it.updateActionBar(
//                    R.drawable.ic__ncd,
//                    getString(R.string.general_opd)
//                ).also { _ -> it.setToolbarNavigationVisible(!viewModel.autoFlow)
//                }

                is HomeActivity -> it.updateActionBar(R.drawable.ic__ben, getString(R.string.general_opd))
                is VolunteerActivity -> it.updateActionBar(R.drawable.ic__ben, getString(R.string.general_opd))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyManagedFlowBackPolicyOnResume(
            isManagedFlow = isManagedFlow,
            allowBack = allowBackNavigation
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        prescriptionBound = false
    }
}
