package org.piramalswasthya.stoptb.ui.contact_tracing

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dagger.hilt.android.AndroidEntryPoint
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.adapters.dynamicAdapter.CounsellingDynamicAdapter
import org.piramalswasthya.stoptb.databinding.FragmentContactTracingFormBinding
import org.piramalswasthya.stoptb.helpers.NetworkResponse
import org.piramalswasthya.stoptb.ui.counselling_activity.FormType
import org.piramalswasthya.stoptb.ui.counselling_activity.SectionPhase

/**
 * Generic staged-section form screen for Community/Occupational/TPT_followup / Contact_FollowUp Contact Tracing — they
 * differ only by which FormType is loaded, both driven by ContactTracingFormViewModel.
 */
@AndroidEntryPoint
class ContactTracingFormFragment : Fragment() {

    private val viewModel: ContactTracingFormViewModel by viewModels()
    private lateinit var adapter: CounsellingDynamicAdapter

    private var binding: FragmentContactTracingFormBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentContactTracingFormBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val formType = FormType.valueOf(
            arguments?.getString(ARG_FORM_TYPE)
                ?: FormType.COMMUNITY_CONTACT_TRACING.name
        )
        val indexCaseBenId = arguments?.getLong(ARG_INDEX_CASE_BEN_ID) ?: 0L
        val contactType = arguments?.getString(ARG_CONTACT_TYPE) ?: "COMMUNITY"
        val sectionPhase = arguments?.getString(ARG_SECTION_PHASE)
            ?.let { runCatching { SectionPhase.valueOf(it) }.getOrNull() }
        val viewHistory = arguments?.getBoolean(ARG_VIEW_HISTORY) ?: false

        binding?.apply {
            ViewCompat.setOnApplyWindowInsetsListener(llCtContent) { view, insets ->
                val bottomInset = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
                ).bottom
                view.updatePadding(bottom = bottomInset)
                insets
            }

            rvCtForm.layoutManager = LinearLayoutManager(requireContext())
            (rvCtForm.itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)
                ?.supportsChangeAnimations = false
            adapter = CounsellingDynamicAdapter(
                questions = emptyList(),
                onValueChanged = { updatedQ ->
                    val nonKeystrokeTypes = setOf(
                        "RADIO", "MCQ", "CHECKBOX_MULTI", "CHECKBOX", "DATE", "DROPDOWN", "NUMBER_PICKER",
                        "READONLY_NUMBER", "READONLY_TEXT"
                    )
                    val isKeystrokeInput = updatedQ.questionType !in nonKeystrokeTypes
                    viewModel.onQuestionValueChanged(
                        updatedQ,
                        reevaluate = !isKeystrokeInput
                    )
                }
            )
            rvCtForm.adapter = adapter

            fun renderQuestions() {
                val questions = viewModel.activeQuestions.value ?: return
                adapter.submitList(questions, viewModel.isEditable.value ?: true)
            }


            fun updateNextButton() {
                val editable = viewModel.isEditable.value ?: true
                val (current, total) = viewModel.progress.value ?: (1 to 1)
                val isLastSection = current >= total

                val showContinueTpt = !editable && viewModel.showContinueTpt.value == true
                val tptAlreadySubmitted = viewModel.tptPreSubmitAlreadySubmitted.value == true

//                btnCtNext.visibility = if (editable || showContinueTpt ) View.VISIBLE else View.GONE
                val hiddenSections = setOf("Contact & Exposure Details", "Occupation & Exposure Details")
                btnCtNext.visibility = if (!editable && viewModel.currentSectionName.value in hiddenSections) View.GONE else View.VISIBLE
                btnCtNext.text = when {
                    showContinueTpt && tptAlreadySubmitted -> getString(R.string.view_tpt_follow_up)
                    showContinueTpt -> getString(R.string.tpt_follow_up)
                    isLastSection -> getString(R.string.btn_submit)
                    else -> getString(R.string.next)
                }
                btnCtNext.setOnClickListener {
                    when {
                        showContinueTpt -> viewModel.continueToTpt()
                        isLastSection -> viewModel.onSubmit()
                        else -> viewModel.onNext()
                    }
                }
            }

            viewModel.activeQuestions.observe(viewLifecycleOwner) {
                renderQuestions()
            }

            viewModel.currentSectionName.observe(viewLifecycleOwner) {
                tvCtSectionName.text = it
            }

            viewModel.progress.observe(viewLifecycleOwner) { (current, total) ->
                tvCtProgress.text = getString(
                    R.string.contact_tracing_section_progress,
                    current,
                    total
                )
                updateNextButton()
            }

            viewModel.isEditable.observe(viewLifecycleOwner) { editable ->

                fabEdit.visibility = if (editable == true || formType == FormType.CONTACT_FOLLOW_UP || formType == FormType.TPT_FOLLOW_UP) {
                    View.GONE
                } else {
                    View.VISIBLE
                }
                updateNextButton()
                renderQuestions()
            }

            fabEdit.setOnClickListener {
                viewModel.enterEditMode()
            }

            viewModel.showContinueTpt.observe(viewLifecycleOwner) {
                updateNextButton()
            }

            viewModel.tptPreSubmitAlreadySubmitted.observe(viewLifecycleOwner) {
                updateNextButton()
            }

            viewModel.alertMessage.observe(viewLifecycleOwner) { message ->
                if (!message.isNullOrBlank()) {
                    AlertDialog.Builder(requireContext())
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            viewModel.consumeAlert()
                        }
                        .setOnDismissListener {
                            viewModel.consumeAlert()
                        }
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
                if (completed == true) {
                    Toast.makeText(requireContext(), R.string.form_submitted_successfully, Toast.LENGTH_SHORT).show()
                    (requireActivity() as? ContactTracingNavigator)?.onFormCompleted()
                }
            }

            viewModel.exitRequested.observe(viewLifecycleOwner) { requested ->
                if (requested == true) {
                    viewModel.consumeExit()
                    (requireActivity() as? ContactTracingNavigator)?.onBackNavigation()
                }
            }

            viewModel.formSchemaState.observe(viewLifecycleOwner) { state ->
                when (state) {
                    is NetworkResponse.Idle -> Unit
                    is NetworkResponse.Loading -> {
                        llCtContent.visibility = View.INVISIBLE
                        llCtError.visibility = View.GONE
                        pbCtLoading.visibility = View.VISIBLE
                    }
                    is NetworkResponse.Success -> {
                        llCtContent.visibility = View.VISIBLE
                        llCtError.visibility = View.GONE
                        pbCtLoading.visibility = View.GONE
                    }
                    is NetworkResponse.Error -> {
                        llCtContent.visibility = View.INVISIBLE
                        pbCtLoading.visibility = View.GONE
                        tvCtErrorMessage.text = state.message ?: getString(R.string.contact_tracing_load_error)
                        btnCtRetry.setOnClickListener {
                            viewModel.retryLoad()
                        }
                        llCtError.visibility = View.VISIBLE
                    }
                }
            }
        }

        viewModel.open(formType, indexCaseBenId, contactType, sectionPhase, viewHistory)
        viewModel.loadResultForm(indexCaseBenId)
    }

    /** Called by ContactTracingActivity's back-press handling - saves whatever's filled in */
    fun saveDraftAndGoBack() {
        viewModel.onBack()
    }

    private fun handleOpenForm(targetFormUuid: String) {
        val navigator = requireActivity() as? ContactTracingNavigator ?: return

        when (targetFormUuid) {
            "COMMUNITY_CONTACT_TRACING" -> {
                navigator.openContactForm(
                    FormType.COMMUNITY_CONTACT_TRACING,
                    "COMMUNITY"
                )
            }

            "OCCUPATIONAL_CONTACT_TRACING" -> {
                navigator.openContactForm(
                    FormType.OCCUPATION_CONTACT_TRACING,
                    "OCCUPATIONAL"
                )
            }

            "TPT_FOLLOW_UP" -> {
                navigator.openContactForm(
                    FormType.TPT_FOLLOW_UP,
                    "TPT_FOLLOW_UP",
                    SectionPhase.PRE_SUBMIT,
                    addToBackStack = true
                )
            }

            else -> Unit
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    companion object {
        private const val ARG_FORM_TYPE = "formType"
        private const val ARG_INDEX_CASE_BEN_ID = "indexCaseBenId"
        private const val ARG_CONTACT_TYPE = "contactType"
        private const val ARG_SECTION_PHASE = "sectionPhase"
        private const val ARG_VIEW_HISTORY = "viewHistory"

        fun newInstance(
            formType: FormType,
            indexCaseBenId: Long,
            contactType: String,
            sectionPhase: SectionPhase? = null,
            viewHistory: Boolean = false
        ) = ContactTracingFormFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_FORM_TYPE, formType.name)
                putLong(ARG_INDEX_CASE_BEN_ID, indexCaseBenId)
                putString(ARG_CONTACT_TYPE, contactType)
                sectionPhase?.let { putString(ARG_SECTION_PHASE, it.name) }
                putBoolean(ARG_VIEW_HISTORY, viewHistory)
            }
        }
    }
}