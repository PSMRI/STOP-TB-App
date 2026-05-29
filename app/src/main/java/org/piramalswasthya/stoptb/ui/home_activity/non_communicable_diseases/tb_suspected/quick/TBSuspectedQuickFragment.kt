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
        blockBackNavigationInManagedFlow(isManagedFlow, allowBack = false)
        val adapter = FormInputAdapter(
            formValueListener = FormInputAdapter.FormValueListener { formId, index ->
                viewModel.updateListOnValueChanged(formId, index)
            },
            isEnabled = true
        )
        binding.form.rvInputForm.adapter = adapter
        binding.btnCancel.visibility = View.GONE
        binding.fabEdit.visibility = View.GONE
        binding.btnSubmit.visibility = View.VISIBLE

        lifecycleScope.launch {
            viewModel.formList.collect {
                if (it.isNotEmpty()) {
                    adapter.submitList(it)
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
            binding.btnSubmit.visibility = if (it) View.VISIBLE else View.GONE
        }
        binding.btnSubmit.setOnClickListener {
            submitForm()
        }
        viewModel.state.observe(viewLifecycleOwner) {
            when (it) {
                TBSuspectedQuickViewModel.State.SAVE_SUCCESS -> {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.diagnostics_submitted),
                        Toast.LENGTH_SHORT
                    ).show()
                    if (!viewModel.viewOnly) {
                        WorkerUtils.triggerCampAwarePushWorker(requireContext(), preferenceDao)
                    }
                    if (viewModel.viewOnly) {
                        findNavController().navigateUp()
                    } else {
                        // Pop all examine-flow forms off the back stack and return to the
                        // existing AllBenFragment instance (not a new one).
                        // Using navigate+setPopUpTo here incorrectly pushed a NEW AllBenFragment
                        // on top of the un-popped forms, so pressing back revealed those forms.
                        val returnedToList = findNavController().popBackStack(R.id.allBenFragment, false)
                        if (!returnedToList) {
                            // AllBenFragment not found in back stack (rare edge case)
                            findNavController().navigate(R.id.allBenFragment, null)
                        }
                    }
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
