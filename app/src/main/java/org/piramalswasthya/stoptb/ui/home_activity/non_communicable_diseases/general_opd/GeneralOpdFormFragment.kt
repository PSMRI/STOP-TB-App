package org.piramalswasthya.stoptb.ui.home_activity.non_communicable_diseases.general_opd

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.adapters.FormInputAdapter
import org.piramalswasthya.stoptb.databinding.DialogCareContextOtpBinding
import org.piramalswasthya.stoptb.databinding.FragmentNewFormBinding
import org.piramalswasthya.stoptb.helpers.applyManagedFlowBackPolicyOnResume
import org.piramalswasthya.stoptb.helpers.blockBackNavigationInManagedFlow
import org.piramalswasthya.stoptb.helpers.isInternetAvailable
import org.piramalswasthya.stoptb.ui.home_activity.HomeActivity
import org.piramalswasthya.stoptb.ui.volunteer.VolunteerActivity
import org.piramalswasthya.stoptb.utils.scrollToFormValidationError
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.work.WorkerUtils
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class GeneralOpdFormFragment : Fragment() {

    @Inject lateinit var preferenceDao: PreferenceDao

    private var _binding: FragmentNewFormBinding? = null
    private val binding: FragmentNewFormBinding
        get() = _binding!!
    private var careContextDialog: AlertDialog? = null
    private var loadingDialog: AlertDialog? = null
    private var careContextConfirmDialog: AlertDialog? = null
    private var otpSentDialog: AlertDialog? = null
    private var otpVerifiedDialog: AlertDialog? = null
    private var pendingOtpState: GeneralOpdFormViewModel.CareContextState.OtpRequired? = null

    private val viewModel: GeneralOpdFormViewModel by viewModels()
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
        _binding = FragmentNewFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Back navigation allowed — each form now returns to AllBenFragment independently
        // blockBackNavigationInManagedFlow(isManagedFlow, allowBackNavigation)

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
                    WorkerUtils.triggerCampAwarePushWorker(requireContext(), preferenceDao)
                    if (isInternetAvailable(requireContext())) {
                        showCareContextConfirmationDialog()
                    } else {
                        navigateToDiagnostics()
                    }
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

        viewModel.careContextState.observe(viewLifecycleOwner) { careContextState ->
            when (careContextState) {
                is GeneralOpdFormViewModel.CareContextState.Idle -> {
                    dismissLoadingDialog()
                }

                is GeneralOpdFormViewModel.CareContextState.Loading -> {
                    showLoadingDialog(careContextState.message)
                }

                is GeneralOpdFormViewModel.CareContextState.OtpRequired -> {
                    dismissLoadingDialog()
                    pendingOtpState = careContextState
                    showOtpSentDialog()
                }

                is GeneralOpdFormViewModel.CareContextState.Completed -> {
                    dismissLoadingDialog()
                    dismissCareContextDialog()
                    showOtpVerifiedDialog(careContextState.message)
                    viewModel.clearCareContextState()
                }

                is GeneralOpdFormViewModel.CareContextState.Unavailable -> {
                    dismissLoadingDialog()
                    dismissCareContextDialog()
                    showCareContextUnavailableDialog(careContextState.message)
                    viewModel.clearCareContextState()
                }
            }
        }
    }

    private fun submitGeneralOpdForm() {
        val businessRuleResult = viewModel.validateBusinessRules()
        if (businessRuleResult != -1) {
            binding.form.rvInputForm.adapter?.notifyItemChanged(businessRuleResult)
            binding.form.rvInputForm.scrollToFormValidationError(businessRuleResult)
            return
        }
        if (validateCurrentPage()) {
            viewModel.saveForm()
        }
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

    private fun showCareContextConfirmationDialog() {
        dismissCareContextConfirmationDialog()
        careContextConfirmDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.care_context_confirmation_title)
            .setMessage(R.string.care_context_confirmation_message)
            .setPositiveButton(R.string.yes) { dialog, _ ->
                dialog.dismiss()
                viewModel.startCareContextFlow()
            }
            .setNegativeButton(R.string.no) { dialog, _ ->
                dialog.dismiss()
                navigateToDiagnostics()
            }
            .setCancelable(false)
            .create()
        careContextConfirmDialog?.show()
    }

    private fun showLoadingDialog(message: String) {
        if (loadingDialog?.isShowing == true) return
        loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.care_context_creation_title)
            .setMessage(message)
            .setCancelable(false)
            .create()
        loadingDialog?.show()
    }

    private fun dismissLoadingDialog() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }

    private fun showOtpSentDialog() {
        dismissOtpSentDialog()
        otpSentDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.save_successful)
            .setMessage(R.string.otp_sent)
            .setPositiveButton("Ok") { dialog, _ ->
                dialog.dismiss()
                pendingOtpState?.let { showCareContextOtpDialog(it) }
            }
            .setCancelable(false)
            .create()
        otpSentDialog?.show()
    }

    private fun showCareContextOtpDialog(state: GeneralOpdFormViewModel.CareContextState.OtpRequired) {
        dismissCareContextDialog()
        val dialogBinding = DialogCareContextOtpBinding.inflate(layoutInflater)
        dialogBinding.tvAbhaNumber.text = getString(R.string.care_context_abha_label) + ": " + state.abhaNumber
        dialogBinding.tvVisitCode.text = getString(R.string.care_context_visit_code_value, state.visitCode)
        dialogBinding.tvOtpError.apply {
            visibility = if (state.message.isNullOrBlank()) View.GONE else View.VISIBLE
            text = state.message.orEmpty()
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()

        dialogBinding.btnCancel.setOnClickListener {
            viewModel.skipCareContext()
        }

        dialogBinding.btnSubmit.setOnClickListener {
            val otp = dialogBinding.ettOtp.text?.toString()?.trim().orEmpty()
            if (otp.isBlank()) {
                dialogBinding.tvOtpError.visibility = View.VISIBLE
                dialogBinding.tvOtpError.text = getString(R.string.care_context_enter_otp)
                return@setOnClickListener
            }
            dialogBinding.tvOtpError.visibility = View.GONE
            viewModel.verifyCareContextOtp(otp)
        }

        careContextDialog = dialog
        dialog.show()
    }

    private fun dismissCareContextDialog() {
        careContextDialog?.dismiss()
        careContextDialog = null
    }

    private fun showOtpVerifiedDialog(message: String) {
        dismissOtpVerifiedDialog()
        otpVerifiedDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.save_successful)
            .setMessage(message.ifBlank { getString(R.string.otp_verified_success) })
            .setPositiveButton("Ok") { dialog, _ ->
                dialog.dismiss()
                navigateToDiagnostics()
            }
            .setCancelable(false)
            .create()
        otpVerifiedDialog?.show()
    }

    private fun showCareContextUnavailableDialog(message: String) {
        val dialogMessage = if (message.contains("ABHA", ignoreCase = true)) {
            getString(R.string.care_context_no_abha_message)
        } else {
            message
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.care_context_creation_title)
            .setMessage(dialogMessage)
            .setPositiveButton("Ok") { dialog, _ ->
                dialog.dismiss()
                navigateToDiagnostics()
            }
            .setCancelable(false)
            .show()
    }

    private fun dismissCareContextConfirmationDialog() {
        careContextConfirmDialog?.dismiss()
        careContextConfirmDialog = null
    }

    private fun dismissOtpSentDialog() {
        otpSentDialog?.dismiss()
        otpSentDialog = null
    }

    private fun dismissOtpVerifiedDialog() {
        otpVerifiedDialog?.dismiss()
        otpVerifiedDialog = null
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
        dismissLoadingDialog()
        dismissCareContextConfirmationDialog()
        dismissOtpSentDialog()
        dismissOtpVerifiedDialog()
        dismissCareContextDialog()
        _binding = null
    }
}
