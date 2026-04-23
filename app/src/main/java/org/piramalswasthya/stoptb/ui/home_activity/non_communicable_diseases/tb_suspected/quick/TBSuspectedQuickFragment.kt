package org.piramalswasthya.stoptb.ui.home_activity.non_communicable_diseases.tb_suspected.quick

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.adapters.FormInputAdapter
import org.piramalswasthya.stoptb.databinding.FragmentNewFormBinding
import org.piramalswasthya.stoptb.ui.home_activity.HomeActivity
import org.piramalswasthya.stoptb.ui.volunteer.VolunteerActivity
import timber.log.Timber

@AndroidEntryPoint
class TBSuspectedQuickFragment : Fragment() {

    private var _binding: FragmentNewFormBinding? = null
    private val binding: FragmentNewFormBinding
        get() = _binding!!

    private val viewModel: TBSuspectedQuickViewModel by viewModels()

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
                        getString(R.string.tb_tracking_submitted),
                        Toast.LENGTH_SHORT
                    ).show()
                    if (viewModel.viewOnly) {
                        findNavController().navigateUp()
                    } else {
                        findNavController().navigate(
                            R.id.allBenFragment,
                            null,
                            NavOptions.Builder()
                                .setPopUpTo(R.id.allBenFragment, false)
                                .build()
                        )
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
            (it as FormInputAdapter).validateInput(resources)
        }
        Timber.d("Validation : $result")
        if (result == -1) {
            viewModel.saveForm()
        } else if (result != null) {
            binding.form.rvInputForm.scrollToPosition(result)
        }
    }

    override fun onStart() {
        super.onStart()
        activity?.let {
            when (it) {
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
