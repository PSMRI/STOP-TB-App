package org.piramalswasthya.stoptb.ui.home_activity.all_ben.examine

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.helpers.isNurseRole
import org.piramalswasthya.stoptb.helpers.isRegistrationOfficerRole

@AndroidEntryPoint
class ExamineBottomSheetFragment : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "examine_flow"

        /** Form indices — used in ExamineCallback */
        const val FORM_ANTHROPOMETRY = 0
        const val FORM_GENERAL_EXAM  = 1
        const val FORM_TB_SCREENING  = 2
        const val FORM_GENERAL_OPD   = 3
        const val FORM_DIAGNOSIS     = 4

        fun newInstance(benId: Long, autoFlow: Boolean = false) = ExamineBottomSheetFragment().apply {
            arguments = bundleOf("benId" to benId, "autoFlow" to autoFlow)
        }
    }

    /** Callback implemented by AllBenFragment */
    interface ExamineCallback {
        fun onNavigateToExamineForm(benId: Long, formIndex: Int, viewOnly: Boolean)
        fun onExamineDismissed()
    }

    @Inject
    lateinit var prefDao: PreferenceDao

    private val viewModel: ExamineViewModel by viewModels()

    // Set to true when we dismiss programmatically for navigation (not user swipe)
    private var isDismissingForNavigation = false

    /** True when logged-in user is Registrar — only Anthropometry form shown */
    private val isRegistrar: Boolean
        get() = prefDao.getLoggedInUser()?.role.isRegistrationOfficerRole()

    private val isNurse: Boolean
        get() = prefDao.getLoggedInUser()?.role.isNurseRole()

    private val autoFlow: Boolean
        get() = arguments?.getBoolean("autoFlow", false) ?: false

    private val examineCallback: ExamineCallback?
        get() = parentFragment as? ExamineCallback

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_examine_bottom_sheet, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Show beneficiary name so the user knows whose forms are open
        val tvBenName = view.findViewById<TextView>(R.id.tv_ben_name)
        viewModel.benName.observe(viewLifecycleOwner) { name ->
            tvBenName.text = name
        }

        val benId = viewModel.benId

        // Map each included row (View) → form label + form index
        data class FormRow(val rowView: View, val formName: String, val formIndex: Int)

        val rows = listOf(
            FormRow(view.findViewById(R.id.row_anthropometry),  getString(R.string.anthropometry_screen),  FORM_ANTHROPOMETRY),
            FormRow(view.findViewById(R.id.row_general_exam),   getString(R.string.vital_screen),           FORM_GENERAL_EXAM),
            FormRow(view.findViewById(R.id.row_tb_screening),   getString(R.string.tb_screening_form),      FORM_TB_SCREENING),
            FormRow(view.findViewById(R.id.row_general_opd),    getString(R.string.general_opd),            FORM_GENERAL_OPD),
            FormRow(view.findViewById(R.id.row_diagnosis),      getString(R.string.tb_suspected_quick_title), FORM_DIAGNOSIS)
        )

        val fillStatusFlows = listOf(
            viewModel.isAnthropometryFilled,
            viewModel.isGeneralExamFilled,
            viewModel.isTbScreeningFilled,
            viewModel.isGeneralOpdFilled,
            viewModel.isDiagnosisFilled
        )

        rows.forEachIndexed { index, (rowView, formName, formIndex) ->
            // Registrar role: show Anthropometry + TB Screening; Nurse: hide Diagnosis only
            if (isRegistrar && formIndex != FORM_ANTHROPOMETRY && formIndex != FORM_TB_SCREENING) {
                rowView.visibility = View.GONE
                return@forEachIndexed
            }
            if (isNurse && formIndex == FORM_DIAGNOSIS) {
                rowView.visibility = View.GONE
                return@forEachIndexed
            }
            rowView.visibility = View.VISIBLE
            rowView.findViewById<TextView>(R.id.tv_form_name).text = formName
            val btn = rowView.findViewById<MaterialButton>(R.id.btn_form_action)

            if (formIndex == FORM_DIAGNOSIS) {
                // Diagnosis is only enabled after TB Screening is completed
                viewLifecycleOwner.lifecycleScope.launch {
                    combine(
                        viewModel.isTbScreeningFilled,
                        fillStatusFlows[index]
                    ) { tbScreeningDone, diagnosisFilled ->
                        Pair(tbScreeningDone, diagnosisFilled)
                    }.collect { (tbScreeningDone, diagnosisFilled) ->
                        if (!tbScreeningDone) {
                            // TB Screening not done — show grey button, toast on click
                            btn.text = getString(R.string.examine_btn_fill)
                            btn.isEnabled = false
                            btn.alpha = 1f
                            btn.backgroundTintList = ContextCompat.getColorStateList(
                                requireContext(), android.R.color.darker_gray
                            )
                            // Allow tap to show hint message even when disabled
                            btn.isEnabled = true
                            btn.setOnClickListener {
                                android.widget.Toast.makeText(
                                    requireContext(),
                                    getString(R.string.diagnosis_locked_msg),
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            // TB Screening done — normal behavior
                            btn.isEnabled = true
                            btn.alpha = 1f
                            if (diagnosisFilled) {
                                btn.text = getString(R.string.examine_btn_view)
                                btn.backgroundTintList = ContextCompat.getColorStateList(
                                    requireContext(), android.R.color.holo_green_dark
                                )
                                btn.setOnClickListener { navigateToForm(benId, formIndex, viewOnly = true) }
                            } else {
                                btn.text = getString(R.string.examine_btn_fill)
                                btn.backgroundTintList = ContextCompat.getColorStateList(
                                    requireContext(), android.R.color.holo_red_dark
                                )
                                btn.setOnClickListener { navigateToForm(benId, formIndex, viewOnly = false) }
                            }
                        }
                    }
                }
            } else {
                observeFormStatus(fillStatusFlows[index], btn, benId, formIndex)
            }
        }

        // Auto-flow: if opened with autoFlow=true, immediately navigate to next unfilled form
        if (autoFlow) {
            viewLifecycleOwner.lifecycleScope.launch {
                val nextIndex = viewModel.nextUnfilledFormIndex.first()
                if (nextIndex != null) {
                    navigateToForm(benId, nextIndex, viewOnly = false)
                } else {
                    // All forms done — just dismiss cleanly
                    isDismissingForNavigation = true
                    dismiss()
                    examineCallback?.onExamineDismissed()
                }
            }
        }
    }

    private fun observeFormStatus(
        filledFlow: Flow<Boolean>,
        btn: MaterialButton,
        benId: Long,
        formIndex: Int
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            filledFlow.collect { isFilled ->
                if (isFilled) {
                    // Green — View
                    btn.text = getString(R.string.examine_btn_view)
                    btn.backgroundTintList = ContextCompat.getColorStateList(
                        requireContext(), android.R.color.holo_green_dark
                    )
                    btn.setOnClickListener {
                        navigateToForm(benId, formIndex, viewOnly = true)
                    }
                } else {
                    // Red — Fill
                    btn.text = getString(R.string.examine_btn_fill)
                    btn.backgroundTintList = ContextCompat.getColorStateList(
                        requireContext(), android.R.color.holo_red_dark
                    )
                    btn.setOnClickListener {
                        navigateToForm(benId, formIndex, viewOnly = false)
                    }
                }
            }
        }
    }

    private fun navigateToForm(benId: Long, formIndex: Int, viewOnly: Boolean) {
        isDismissingForNavigation = true
        dismiss()
        examineCallback?.onNavigateToExamineForm(benId, formIndex, viewOnly)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (!isDismissingForNavigation) {
            examineCallback?.onExamineDismissed()
        }
    }
}
