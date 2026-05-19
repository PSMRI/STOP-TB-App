package org.piramalswasthya.stoptb.ui.home_activity.non_communicable_diseases.general_opd

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.adapters.FormInputAdapter
import org.piramalswasthya.stoptb.databinding.FragmentNewFormBinding
import org.piramalswasthya.stoptb.helpers.applyManagedFlowBackPolicyOnResume
import org.piramalswasthya.stoptb.helpers.blockBackNavigationInManagedFlow
import org.piramalswasthya.stoptb.ui.home_activity.HomeActivity
import org.piramalswasthya.stoptb.ui.volunteer.VolunteerActivity
import timber.log.Timber

@AndroidEntryPoint
class GeneralOpdFormFragment : Fragment() {

    private var _binding: FragmentNewFormBinding? = null
    private val binding: FragmentNewFormBinding
        get() = _binding!!

    private val viewModel: GeneralOpdFormViewModel by viewModels()

    private val isManagedFlow: Boolean
        get() = viewModel.autoFlow || viewModel.generalOpdFlow

    /** First screen in General OPD flow; nurse auto-flow blocks back on this step. */
    private val allowBackNavigation: Boolean
        get() = viewModel.generalOpdFlow && !viewModel.autoFlow

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        blockBackNavigationInManagedFlow(isManagedFlow, allowBackNavigation)

        viewModel.recordExists.observe(viewLifecycleOwner) { exists ->
            exists?.let { recordExists ->
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
                lifecycleScope.launch {
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
        val businessRuleResult = viewModel.validateBusinessRules()
        if (businessRuleResult != -1) {
            binding.form.rvInputForm.adapter?.notifyItemChanged(businessRuleResult)
            binding.form.rvInputForm.scrollToPosition(businessRuleResult)
            return
        }
        if (validateCurrentPage()) {
            viewModel.saveForm()
        }
    }

    private fun validateCurrentPage(): Boolean {
        val result = binding.form.rvInputForm.adapter?.let {
            (it as FormInputAdapter).validateInput(resources)
        }
        Timber.d("Validation : $result")
        return if (result == -1) true
        else {
            if (result != null) {
                binding.form.rvInputForm.scrollToPosition(result)
            }
            false
        }
    }

    private fun navigateToDiagnostics() {
        findNavController().navigate(
            R.id.TBSuspectedQuickFragment,
            bundleOf(
                "benId" to viewModel.benId,
                "autoFlow" to viewModel.autoFlow,
                "generalOpdFlow" to viewModel.generalOpdFlow
            )
        )
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
    }
}
