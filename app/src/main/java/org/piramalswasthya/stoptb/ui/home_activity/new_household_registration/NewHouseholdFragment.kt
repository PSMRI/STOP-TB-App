package org.piramalswasthya.stoptb.ui.home_activity.new_household_registration

import android.os.Bundle
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
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.adapters.FormInputAdapter
import org.piramalswasthya.stoptb.contracts.SpeechToTextContract
import org.piramalswasthya.stoptb.databinding.FragmentNewFormBinding
import org.piramalswasthya.stoptb.helpers.Konstants
import org.piramalswasthya.stoptb.ui.volunteer.VolunteerActivity
import org.piramalswasthya.stoptb.ui.home_activity.new_household_registration.NewHouseholdViewModel.State
import timber.log.Timber

@AndroidEntryPoint
class NewHouseholdFragment : Fragment() {

    private var _binding: FragmentNewFormBinding? = null
    private val binding: FragmentNewFormBinding get() = _binding!!

    private val viewModel: NewHouseholdViewModel by viewModels()
    private var micClickedElementId: Int = -1
    private var editMode: Boolean = false

    private val sttContract = registerForActivityResult(SpeechToTextContract()) { value ->
        val formattedValue = value.uppercase()
        val listIndex =
            viewModel.updateValueByIdAndReturnListIndex(micClickedElementId, formattedValue)
        listIndex.takeIf { it >= 0 }?.let {
            binding.form.rvInputForm.adapter?.notifyItemChanged(it)
        }
    }

    private val nextScreenAlert by lazy {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.add_head_of_family))
            .setCancelable(false)
            .setPositiveButton(getString(R.string.yes)) { dialog, _ ->
                if (isAdded) {
                    findNavController().navigate(
                        NewHouseholdFragmentDirections.actionNewHouseholdFragmentToNewBenRegFragment(
                            hhId = viewModel.getHHId(),
                            relToHeadId = 18
                        )
                    )
                }
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.no)) { dialog, _ ->
                if (isAdded) findNavController().navigateUp()
                dialog.dismiss()
            }
            .create()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        // Title will be set dynamically in readRecord observer
        (activity as? VolunteerActivity)?.updateActionBar(
            R.drawable.ic__hh,
            getString(R.string.frag_nhhr_title)
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.cvPatientInformation.visibility = View.GONE

        viewModel.readRecord.observe(viewLifecycleOwner) { recordExists ->
            // Change toolbar title based on mode: view vs new registration
            (activity as? VolunteerActivity)?.updateActionBar(
                R.drawable.ic__hh,
                if (recordExists)
                    getString(R.string.view_household_information)
                else
                    getString(R.string.frag_nhhr_title)
            )
            binding.fabEdit.visibility = if (recordExists) View.VISIBLE else View.GONE
            binding.btnSubmit.visibility = if (!recordExists) View.VISIBLE else View.GONE

            val adapter = FormInputAdapter(
                formValueListener = FormInputAdapter.FormValueListener { formId, index ->
                    when (index) {
                        Konstants.micClickIndex -> {
                            micClickedElementId = formId
                            sttContract.launch(Unit)
                        }
                        else -> viewModel.updateListOnValueChanged(formId, index)
                    }
                },
                isEnabled = !recordExists
            )
            binding.form.rvInputForm.adapter = adapter
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.formList.collect {
                    if (it.isNotEmpty()) adapter.submitList(it)
                }
            }
        }

        binding.fabEdit.setOnClickListener {
            editMode = true
            viewModel.setRecordExists(false)
        }

        binding.btnSubmit.setOnClickListener {
            submitHouseholdForm()
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                State.IDLE -> Unit
                State.SAVING -> {
                    binding.llContent.visibility = View.GONE
                    binding.pbForm.visibility = View.VISIBLE
                }
                State.SAVE_SUCCESS -> {
                    binding.llContent.visibility = View.VISIBLE
                    binding.pbForm.visibility = View.GONE
                    Toast.makeText(context, getString(R.string.save_successful), Toast.LENGTH_LONG)
                        .show()
                    if (!editMode) {
                        // Lock form into view mode so that on back-navigation from ben registration
                        // the form doesn't reappear as editable/submittable
                        viewModel.setRecordExists(true)
                        nextScreenAlert.setMessage(
                            getString(R.string.add_head_of_family_message, viewModel.getHoFName())
                        )
                        nextScreenAlert.show()
                        viewModel.resetState()  // reset so dialog doesn't reappear on back navigation
                    } else {
                        findNavController().navigateUp()
                    }
                }
                State.SAVE_FAILED -> {
                    Toast.makeText(
                        context,
                        getString(R.string.something_wend_wong_contact_testing),
                        Toast.LENGTH_LONG
                    ).show()
                    binding.llContent.visibility = View.VISIBLE
                    binding.pbForm.visibility = View.GONE
                }
            }
        }
    }

    private fun submitHouseholdForm() {
        activity?.currentFocus?.clearFocus()
        if (validateCurrentPage()) {
            viewModel.saveForm()
        }
    }

    private fun validateCurrentPage(): Boolean {
        val result = (binding.form.rvInputForm.adapter as? FormInputAdapter)?.validateInput(resources)
        Timber.d("Validation : $result")
        return if (result == -1) true
        else {
            result?.let { binding.form.rvInputForm.scrollToPosition(it) }
            false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (nextScreenAlert.isShowing) nextScreenAlert.dismiss()
        _binding = null
    }
}
