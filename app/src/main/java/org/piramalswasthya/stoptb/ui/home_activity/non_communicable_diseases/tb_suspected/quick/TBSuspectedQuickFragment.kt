package org.piramalswasthya.stoptb.ui.home_activity.non_communicable_diseases.tb_suspected.quick

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
import org.piramalswasthya.stoptb.helpers.setAutoFlowBackNavigationBlocked
import org.piramalswasthya.stoptb.ui.home_activity.HomeActivity
import org.piramalswasthya.stoptb.ui.volunteer.VolunteerActivity
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.work.WorkerUtils
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class TBSuspectedQuickFragment : Fragment() {

    @Inject lateinit var preferenceDao: PreferenceDao

    private var _binding: FragmentNewFormBinding? = null
    private val binding: FragmentNewFormBinding
        get() = _binding!!

    private val viewModel: TBSuspectedQuickViewModel by viewModels()

    private val isManagedFlow: Boolean
        get() = viewModel.autoFlow || viewModel.generalOpdFlow

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: android.os.Bundle?
    ): View {
        _binding = FragmentNewFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Back navigation allowed — each form now returns to AllBenFragment independently
        // blockBackNavigationInManagedFlow(isManagedFlow, allowBack = false)
        val adapter = FormInputAdapter(
            formValueListener = FormInputAdapter.FormValueListener { formId, index ->
                viewModel.updateListOnValueChanged(formId, index)
            },
            isEnabled = true
        )
        binding.form.rvInputForm.adapter = adapter
        binding.btnCancel.visibility = View.GONE
        binding.fabEdit.visibility = View.GONE
        binding.btnSubmit.visibility = if (viewModel.viewOnly) View.GONE else View.VISIBLE

        lifecycleScope.launch {
            viewModel.formList.collect {
                if (it.isNotEmpty()) {
                    adapter.submitList(it)
                    
                    if (viewModel.viewOnly && viewModel.referralType == 7) {
                        val naatRes = viewModel.getNaatResult()
                        val rifRes = viewModel.getTrueNatRifResult()
                        if (naatRes != null && naatRes.equals("Invalid", ignoreCase = true)) {
                            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                .setTitle("Invalid Test Result")
                                .setMessage("The test results are invalid, repeat the test")
                                .setPositiveButton("REPEAT TEST") { d, _ ->
                                    viewModel.repeatTest("SPUTUM_TRUENAT")
                                    Toast.makeText(requireContext(), "Repeat test order created. Please mark test complete on the listing page.", Toast.LENGTH_LONG).show()
                                    d.dismiss()
                                    findNavController().popBackStack()
                                }
                                .setNegativeButton("CANCEL") { d, _ -> d.dismiss() }
                                .show()
                        } else if (rifRes != null && rifRes.equals("Indeterminate", ignoreCase = true)) {
                            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                .setTitle("Indeterminate Test Result")
                                .setMessage("The test results are indeterminate, repeat the test")
                                .setPositiveButton("REPEAT TEST") { d, _ ->
                                    viewModel.repeatTest("MDR_RIF")
                                    Toast.makeText(requireContext(), "Repeat test order created. Please mark test complete on the listing page.", Toast.LENGTH_LONG).show()
                                    d.dismiss()
                                    findNavController().popBackStack()
                                }
                                .setNegativeButton("CANCEL") { d, _ -> d.dismiss() }
                                .show()
                        }
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
        viewModel.showSubmit.observe(viewLifecycleOwner) {
            val isXrayDone = viewModel.viewOnly && viewModel.referralType == 6 && viewModel.getIsChestXRayDone() == true
            val isNaatDone = viewModel.viewOnly && viewModel.referralType == 7 && viewModel.getIsNaatConducted() == true
            if (!(isXrayDone || isNaatDone)) {
                binding.btnSubmit.visibility = if (it) View.VISIBLE else View.GONE
                if (!viewModel.viewOnly && (viewModel.referralType == 6 || viewModel.referralType == 7)) {
                    binding.btnSubmit.text = "SUBMIT REFERRAL"
                }
            }
        }
        val isXrayDone = viewModel.viewOnly && viewModel.referralType == 6 && viewModel.getIsChestXRayDone() == true
        val isNaatDone = viewModel.viewOnly && viewModel.referralType == 7 && viewModel.getIsNaatConducted() == true
        if (!(isXrayDone || isNaatDone)) {
            binding.btnSubmit.setOnClickListener {
                submitForm()
            }
        }
        viewModel.state.observe(viewLifecycleOwner) {
            when (it) {
                TBSuspectedQuickViewModel.State.SAVE_SUCCESS -> {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.diagnostics_submitted),
                        Toast.LENGTH_SHORT
                    ).show()
                    WorkerUtils.triggerCampAwarePushWorker(requireContext(), preferenceDao)
                    findNavController().navigateUp()
                }

                TBSuspectedQuickViewModel.State.SAVE_FAILED -> {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.tb_suspected_quick_save_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                    viewModel.resetState()
                }

                TBSuspectedQuickViewModel.State.SAVING,
                TBSuspectedQuickViewModel.State.IDLE -> Unit
            }
        }
    }

    private fun submitForm() {
        val result = binding.form.rvInputForm.adapter?.let {
            (it as FormInputAdapter).validateInput(resources, binding.form.rvInputForm)
        } ?: -1
        Timber.d("Validation : $result")
        if (result == -1) {
            viewModel.saveForm()
        }
    }

    override fun onStart() {
        super.onStart()
        activity?.let {
            when (it) {
//                is HomeActivity -> it.updateActionBar(
//                    R.drawable.ic__ncd,
//                    getString(R.string.tb_suspected_quick_title)
//                ).also { _ -> it.setToolbarNavigationVisible(!viewModel.autoFlow) }
//                is VolunteerActivity -> it.updateActionBar(
//                    R.drawable.ic__ncd,
//                    getString(R.string.tb_suspected_quick_title)
//                ).also { _ -> it.setToolbarNavigationVisible(!viewModel.autoFlow) }

                is HomeActivity -> it.updateActionBar(
                    R.drawable.ic__ncd,
                    getString(R.string.tb_suspected_quick_title)
                )
                is VolunteerActivity -> it.updateActionBar(
                    R.drawable.ic__ncd,
                    getString(R.string.tb_suspected_quick_title)
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyManagedFlowBackPolicyOnResume(
            isManagedFlow = isManagedFlow,
            allowBack = false
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (isManagedFlow) {
            setAutoFlowBackNavigationBlocked(false)
        }
        _binding = null
    }
}
