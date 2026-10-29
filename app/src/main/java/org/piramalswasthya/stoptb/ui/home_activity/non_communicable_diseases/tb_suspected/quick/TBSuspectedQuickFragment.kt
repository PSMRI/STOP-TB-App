package org.piramalswasthya.stoptb.ui.home_activity.non_communicable_diseases.tb_suspected.quick

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
                    // TrueNat & RIF order lifecycle redesign: the AlertDialog-based "REPEAT TEST"
                    // prompt that used to fire here on naatResult == "Invalid" /
                    // trueNatRifResult == "Indeterminate" is fully superseded by the
                    // Closed-status-driven "Create New Order" button on the beneficiary card
                    // (Invalid/Error is the repeat-trigger now; Indeterminate is terminal) — see
                    // AllBenFragment's "REORDER" action.
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
                    binding.btnSubmit.text = getString(R.string.btn_submit)
                }
            }
        }
        // Form-load state — same llContent/llLoading pair NewBenRegFragment uses.
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.llContent.visibility = if (loading) View.GONE else View.VISIBLE
            binding.llLoading.visibility = if (loading) View.VISIBLE else View.GONE
        }

        // The advisory result check reports itself in a live region beside the form, and Submit
        // keeps its own label and stays usable. Relabelling the primary action to report
        // background work read as "my submission is in flight", carried the only copy of that
        // status on a disabled control that TalkBack never announces, and could hold the one
        // action on this screen for as long as the 60 s client read timeout.
        viewModel.resultCheck.observe(viewLifecycleOwner) { state ->
            binding.llCheckStatus.removeCallbacks(showCheckingStatus)
            when (state!!) {
                // Delayed so a check that answers in tens of milliseconds — the common case on a
                // healthy hub — never flashes a status line at the user.
                TBSuspectedQuickViewModel.ResultCheck.CHECKING ->
                    binding.llCheckStatus.postDelayed(showCheckingStatus, CHECK_STATUS_DELAY_MS)

                TBSuspectedQuickViewModel.ResultCheck.IDLE ->
                    binding.llCheckStatus.visibility = View.INVISIBLE

                TBSuspectedQuickViewModel.ResultCheck.FAILED -> {
                    binding.llCheckStatus.visibility = View.VISIBLE
                    binding.pbCheckStatus.visibility = View.GONE
                    binding.tvCheckStatus.text = getString(R.string.check_newer_result_failed)
                }

                // A result landed while the form was being filled. The typed values are left
                // alone; this only says so, rather than discarding the arrival silently.
                TBSuspectedQuickViewModel.ResultCheck.NEWER_AVAILABLE -> {
                    binding.llCheckStatus.visibility = View.VISIBLE
                    binding.pbCheckStatus.visibility = View.GONE
                    binding.tvCheckStatus.text = getString(R.string.result_newer_available)
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
                // The form stays on screen, so the beneficiary this result belongs to is still
                // visible while the one irreversible step runs. Hiding it behind a full-screen
                // spinner removed that context and was indistinguishable from a frozen app.
                TBSuspectedQuickViewModel.State.SAVING -> setSubmitting(true)

                TBSuspectedQuickViewModel.State.SAVE_SUCCESS -> {
                    setSubmitting(false)
                    val message = if (viewModel.savedOfflinePendingSync.value == true) {
                        getString(R.string.diagnostics_saved_offline_pending_sync)
                    } else {
                        getString(R.string.diagnostics_submitted)
                    }
                    Toast.makeText(
                        requireContext(),
                        message,
                        Toast.LENGTH_SHORT
                    ).show()
                    WorkerUtils.triggerCampAwarePushWorker(requireContext(), preferenceDao)
                    findNavController().navigateUp()
                }

                // The backend refused the entry because it already holds a result. Naming that
                // result is the point: the raw rejection string left the user on the form with
                // no idea what the real result was, or that pressing Submit again was futile.
                TBSuspectedQuickViewModel.State.RESULT_ALREADY_RECORDED -> {
                    setSubmitting(false)
                    val recorded = viewModel.alreadyRecordedResult.value.orEmpty()
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.result_already_recorded_title)
                        .setMessage(getString(R.string.result_already_recorded_message, recorded))
                        .setCancelable(false)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            findNavController().navigateUp()
                        }
                        .show()
                    viewModel.resetState()
                }

                TBSuspectedQuickViewModel.State.SAVE_FAILED -> {
                    // Everything the user typed is still on screen — they can correct and retry.
                    setSubmitting(false)
                    val msg = viewModel.errorMessage.value ?: getString(R.string.tb_suspected_quick_save_failed)
                    Toast.makeText(
                        requireContext(),
                        msg,
                        Toast.LENGTH_LONG
                    ).show()
                    viewModel.resetState()
                }

                TBSuspectedQuickViewModel.State.IDLE -> Unit
            }
        }
    }

    /** Posted, not shown immediately — see the resultCheck observer. */
    private val showCheckingStatus = Runnable {
        _binding?.let { b ->
            b.pbCheckStatus.visibility = View.VISIBLE
            b.tvCheckStatus.text = getString(R.string.checking_newer_result)
            b.llCheckStatus.visibility = View.VISIBLE
        }
    }

    /**
     * Submitting is a commitment, so it reports itself on the control the user pressed rather
     * than through the same anonymous spinner the screen uses while it is still loading.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setSubmitting(submitting: Boolean) {
        binding.btnSubmit.isEnabled = !submitting
        binding.btnSubmit.text =
            getString(if (submitting) R.string.submitting else R.string.btn_submit)
        if (submitting) {
            binding.btnSubmit.icon = CircularProgressDrawable(requireContext()).apply {
                setStyle(CircularProgressDrawable.DEFAULT)
                setColorSchemeColors(Color.WHITE)
                start()
            }
            binding.btnSubmit.iconGravity = MaterialButton.ICON_GRAVITY_END
            // The values were snapshotted when the save began, so edits made now would be
            // silently dropped; swallow touches for the duration instead of accepting them.
            binding.form.rvInputForm.setOnTouchListener { _, _ -> true }
        } else {
            (binding.btnSubmit.icon as? CircularProgressDrawable)?.stop()
            binding.btnSubmit.icon = null
            binding.form.rvInputForm.setOnTouchListener(null)
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
        // Title/icon now match the specific test this screen is for (same icons the dashboard
        // uses for its Chest X-Ray / TrueNat tiles) instead of a generic "Diagnostics" label —
        // RIF shares the TrueNat icon/title since it's the same referralType (7) and the same
        // order family as TrueNat, just a follow-on sub-test.
        val (icon, title) = if (viewModel.referralType == 6) {
            R.drawable.ic_health_xray to getString(R.string.ben_card_chest_xray)
        } else {
            R.drawable.ic_truenat_device to getString(R.string.ben_card_truenat)
        }
        activity?.let {
            when (it) {
                is HomeActivity -> it.updateActionBar(icon, title)
                is VolunteerActivity -> it.updateActionBar(icon, title)
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
        _binding?.llCheckStatus?.removeCallbacks(showCheckingStatus)
        if (isManagedFlow) {
            setAutoFlowBackNavigationBlocked(false)
        }
        _binding = null
    }

    companion object {
        /** Long enough that a healthy hub's check never flashes a status line. */
        private const val CHECK_STATUS_DELAY_MS = 300L
    }
}
