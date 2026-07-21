package org.piramalswasthya.stoptb.ui.contact_tracing

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.adapters.dynamicAdapter.CounsellingDynamicAdapter
import org.piramalswasthya.stoptb.ui.counselling_activity.FormType

/**
 * Generic staged-section form screen, reused for the Contact Tracing Selector and every
 * per-contact form (HHC/Community/Occupational) — they differ only by which FormType is
 * loaded and what happens on completion, both driven by ContactTracingFormViewModel.
 */
@AndroidEntryPoint
class ContactTracingFormFragment : Fragment() {

    private val viewModel: ContactTracingFormViewModel by viewModels()
    private lateinit var adapter: CounsellingDynamicAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_contact_tracing_form, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rv = view.findViewById<RecyclerView>(R.id.rv_ct_form)
        rv.layoutManager = LinearLayoutManager(requireContext())
        adapter = CounsellingDynamicAdapter(
            questions = emptyList(),
            onValueChanged = { updatedQ ->
                viewModel.onQuestionValueChanged(updatedQ, reevaluate = updatedQ.questionType != "TEXT")
            }
        )
        rv.adapter = adapter

        val tvSectionName = view.findViewById<TextView>(R.id.tv_ct_section_name)
        val tvProgress = view.findViewById<TextView>(R.id.tv_ct_progress)
        val btnNext = view.findViewById<MaterialButton>(R.id.btn_ct_next)

        viewModel.activeQuestions.observe(viewLifecycleOwner) { adapter.submitList(it, true) }
        viewModel.currentSectionName.observe(viewLifecycleOwner) { tvSectionName.text = it }
        viewModel.progress.observe(viewLifecycleOwner) { (current, total) ->
            tvProgress.text = getString(R.string.contact_tracing_section_progress, current, total)
        }
        viewModel.hasSubmitButton.observe(viewLifecycleOwner) { isSubmit ->
            btnNext.text = if (isSubmit) getString(R.string.btn_submit) else getString(R.string.next)
            btnNext.setOnClickListener {
                if (isSubmit) viewModel.onSubmit() else viewModel.onNext()
            }
        }
        viewModel.alertMessage.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrBlank()) {
                AlertDialog.Builder(requireContext())
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> viewModel.consumeAlert() }
                    .setOnDismissListener { viewModel.consumeAlert() }
                    .show()
            }
        }
        viewModel.navigateToFormUuid.observe(viewLifecycleOwner) { formUuid ->
            if (formUuid != null) {
                handleOpenForm(formUuid)
                viewModel.consumeNavigation()
            }
        }
        viewModel.formCompleted.observe(viewLifecycleOwner) { completed ->
            if (completed == true) (requireActivity() as? ContactTracingNavigator)?.onFormCompleted()
        }
        viewModel.loadFailed.observe(viewLifecycleOwner) { failed ->
            if (failed == true) {
                android.widget.Toast.makeText(requireContext(), R.string.contact_tracing_load_error, android.widget.Toast.LENGTH_LONG).show()
            }
        }

        val existingResponseId = arguments?.getLong(ARG_EXISTING_RESPONSE_ID, -1L) ?: -1L
        val formType = FormType.valueOf(arguments?.getString(ARG_FORM_TYPE) ?: FormType.CONTACT_TRACING_SELECTOR.name)
        if (existingResponseId > 0) {
            viewModel.resume(formType, existingResponseId)
        } else {
            val indexCaseBenId = arguments?.getLong(ARG_INDEX_CASE_BEN_ID) ?: 0L
            val contactBenIdArg = arguments?.getLong(ARG_CONTACT_BEN_ID, -1L) ?: -1L
            val contactBenId = if (contactBenIdArg > 0) contactBenIdArg else null
            val contactType = arguments?.getString(ARG_CONTACT_TYPE) ?: "SELECTOR"
            viewModel.start(formType, indexCaseBenId, contactBenId, contactType)
        }
    }

    private fun handleOpenForm(targetFormUuid: String) {
        val navigator = requireActivity() as? ContactTracingNavigator ?: return
        when (targetFormUuid) {
            "HHC_CONTACT_TRACING" -> navigator.showHouseholdRoutingNote()
            "COMMUNITY_CONTACT_TRACING" -> navigator.openMemberList("COMMUNITY")
            "OCCUPATIONAL_CONTACT_TRACING" -> navigator.openMemberList("OCCUPATIONAL")
            else -> Unit // TB_CONFIRMED_CASE_FORM / TB_PRESUMPTIVE_CASE: cross-module redirect, not yet built.
        }
    }

    companion object {
        private const val ARG_FORM_TYPE = "formType"
        private const val ARG_INDEX_CASE_BEN_ID = "indexCaseBenId"
        private const val ARG_CONTACT_BEN_ID = "contactBenId"
        private const val ARG_CONTACT_TYPE = "contactType"
        private const val ARG_EXISTING_RESPONSE_ID = "existingResponseId"

        fun newInstance(
            formType: FormType,
            indexCaseBenId: Long,
            contactBenId: Long?,
            contactType: String
        ) = ContactTracingFormFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_FORM_TYPE, formType.name)
                putLong(ARG_INDEX_CASE_BEN_ID, indexCaseBenId)
                contactBenId?.let { putLong(ARG_CONTACT_BEN_ID, it) }
                putString(ARG_CONTACT_TYPE, contactType)
            }
        }

        fun resumeInstance(formType: FormType, existingResponseId: Long) = ContactTracingFormFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_FORM_TYPE, formType.name)
                putLong(ARG_EXISTING_RESPONSE_ID, existingResponseId)
            }
        }
    }
}
