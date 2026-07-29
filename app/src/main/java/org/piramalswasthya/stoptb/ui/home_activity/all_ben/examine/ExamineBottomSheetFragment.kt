package org.piramalswasthya.stoptb.ui.home_activity.all_ben.examine

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.helpers.NetworkResponse
import org.piramalswasthya.stoptb.helpers.isCounsellingOfficerRole
import org.piramalswasthya.stoptb.helpers.isNurseRole
import org.piramalswasthya.stoptb.helpers.isRegistrationOfficerRole
import org.piramalswasthya.stoptb.ui.contact_tracing.ContactTracingActivity
import org.piramalswasthya.stoptb.ui.counselling_activity.SectionPhase

@AndroidEntryPoint
class ExamineBottomSheetFragment : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "examine_flow"

        /** Form indices — used in ExamineCallback */
        const val FORM_ANTHROPOMETRY = 0
        const val FORM_GENERAL_EXAM  = 1
        const val FORM_TB_SCREENING  = 2
        const val FORM_GENERAL_OPD   = 3

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

    /** True when logged-in user is Registrar — Anthropometry and TB Screening forms shown */
    private val isRegistrar: Boolean
        get() = prefDao.getLoggedInUser()?.role.isRegistrationOfficerRole()

    private val isNurse: Boolean
        get() = prefDao.getLoggedInUser()?.role.isNurseRole()
    private val isCounsellingOfficer : Boolean
        get() = prefDao.getLoggedInUser()?.role.isCounsellingOfficerRole()

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
            FormRow(view.findViewById(R.id.row_general_opd),    getString(R.string.general_opd),            FORM_GENERAL_OPD)
        )

        if (isRegistrar || isNurse) {
            val container = view as? LinearLayout
            val anthropometryRow = view.findViewById<View>(R.id.row_anthropometry)
            val tbScreeningRow = view.findViewById<View>(R.id.row_tb_screening)
            if (container != null && anthropometryRow != null && tbScreeningRow != null) {
                container.removeView(tbScreeningRow)
                val anthropometryIndex = container.indexOfChild(anthropometryRow)
                container.addView(tbScreeningRow, anthropometryIndex.coerceAtLeast(0))
            }
        }

        val fillStatusFlows = listOf(
            viewModel.isAnthropometryFilled,
            viewModel.isGeneralExamFilled,
            viewModel.isTbScreeningFilled,
            viewModel.isGeneralOpdFilled
        )

        rows.forEachIndexed { index, (rowView, formName, formIndex) ->
            // Registrar role: show Anthropometry and TB Screening; Nurse: show all 5
            if (isRegistrar && formIndex != FORM_ANTHROPOMETRY && formIndex != FORM_TB_SCREENING) {
                rowView.visibility = View.GONE
                return@forEachIndexed
            }
            // Counselling Officer: show ONLY TB Screening here (Followup is a separate row below).
            if (isCounsellingOfficer && formIndex != FORM_TB_SCREENING) {
                rowView.visibility = View.GONE
                return@forEachIndexed
            }
            rowView.visibility = View.VISIBLE
            rowView.findViewById<TextView>(R.id.tv_form_name).text = formName
            val btn = rowView.findViewById<MaterialButton>(R.id.btn_form_action)
            val notFilled = rowView.findViewById<TextView>(R.id.tv_not_filled)

            if ((isRegistrar && formIndex == FORM_ANTHROPOMETRY) ||
                (isNurse && formIndex != FORM_TB_SCREENING)
            ) {
                viewLifecycleOwner.lifecycleScope.launch {
                    combine(
                        viewModel.isTbScreeningFilled,
                        fillStatusFlows[index]
                    ) { tbScreeningDone, currentFormFilled ->
                        Pair(tbScreeningDone, currentFormFilled)
                    }.collect { (tbScreeningDone, currentFormFilled) ->
                        if (!tbScreeningDone) {
                            btn.text = getString(R.string.examine_btn_fill)
                            btn.isEnabled = true
                            btn.alpha = 1f
                            btn.backgroundTintList = ContextCompat.getColorStateList(
                                requireContext(), android.R.color.darker_gray
                            )
                            btn.setOnClickListener {
                                android.widget.Toast.makeText(
                                    requireContext(),
                                    getString(R.string.tb_screening_locked_msg),
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            btn.isEnabled = true
                            btn.alpha = 1f
                            if (currentFormFilled) {
                                btn.text = getString(R.string.examine_btn_view)
                                btn.backgroundTintList = ContextCompat.getColorStateList(
                                    requireContext(), android.R.color.holo_green_dark
                                )
                                btn.setOnClickListener {
                                    navigateToForm(benId, formIndex, viewOnly = true)
                                }
                            } else {
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
            } else {
                observeFormStatus(fillStatusFlows[index], btn, notFilled, benId, formIndex)
            }
        }

        // Shows Fill/View for the "TPT Followup" row based solely on isTptFollowUpPreSubmitDone, since PRE_SUBMIT completion already implies CONTACT_FOLLOW_UP was submitted.
        val followupRow = view.findViewById<View>(R.id.row_followup)
        if (isCounsellingOfficer) {
            followupRow.visibility = View.VISIBLE
            followupRow.findViewById<TextView>(R.id.tv_form_name).text = getString(R.string.contact_tracing_follow_up)
            followupRow.findViewById<TextView>(R.id.tv_not_filled).visibility = View.GONE
            val followupBtn = followupRow.findViewById<MaterialButton>(R.id.btn_form_action)
            followupBtn.visibility = View.VISIBLE
            followupBtn.isEnabled = true
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.isTptFollowUpPreSubmitDone.collect { filled ->
                    if (filled) {
                        followupBtn.text = getString(R.string.examine_btn_view)
                        followupBtn.backgroundTintList = ContextCompat.getColorStateList(
                            requireContext(), android.R.color.holo_green_dark
                        )
                    } else {
                        followupBtn.text = getString(R.string.examine_btn_fill)
                        followupBtn.backgroundTintList = ContextCompat.getColorStateList(
                            requireContext(), android.R.color.holo_red_dark
                        )
                    }
                    followupBtn.setOnClickListener {
                        ContactTracingActivity.startForType(
                            requireContext(), benId, ContactTracingActivity.CONTACT_TYPE_FOLLOW_UP
                        )
                    }
                }
            }
        } else {
            followupRow.visibility = View.GONE
        }

        // TPT Followup — Counselling Officer only.
        val tptFollowupRow = view.findViewById<View>(R.id.row_tpt_followup)
        if (isCounsellingOfficer) {
            viewLifecycleOwner.lifecycleScope.launch {
                combine(
                    viewModel.isTptFollowUpPreSubmitDone,
                    viewModel.isTptFollowUpFillAvailable
                ) { preSubmitDone, fillAvailable -> preSubmitDone to fillAvailable }
                    .collect { (preSubmitDone, fillAvailable) ->
                        tptFollowupRow.visibility = if (preSubmitDone) View.VISIBLE else View.GONE
                        if (!preSubmitDone) return@collect

                        tptFollowupRow.findViewById<TextView>(R.id.tv_form_name).text =
                            getString(R.string.tpt_follow_up)
                        tptFollowupRow.findViewById<TextView>(R.id.tv_not_filled).visibility = View.GONE


                        val historyBtn = tptFollowupRow.findViewById<MaterialButton>(R.id.btn_form_history)
                        historyBtn.visibility = View.VISIBLE
                        historyBtn.text = getString(R.string.examine_btn_history)

                        historyBtn.backgroundTintList = ContextCompat.getColorStateList(
                            requireContext(), android.R.color.holo_green_dark
                        )
                        historyBtn.setOnClickListener {
                            viewModel.onHistoryClicked()
                        }

                        val fillBtn = tptFollowupRow.findViewById<MaterialButton>(R.id.btn_form_action)
                        if (!fillAvailable) {
                            fillBtn.visibility = View.GONE
                            return@collect
                        }
                        fillBtn.visibility = View.VISIBLE
                        fillBtn.isEnabled = true
                        fillBtn.text = getString(R.string.examine_btn_fill)
                        fillBtn.backgroundTintList = ContextCompat.getColorStateList(
                            requireContext(), android.R.color.holo_red_dark
                        )
                        fillBtn.setOnClickListener {
                            ContactTracingActivity.startForType(
                                requireContext(), benId, ContactTracingActivity.CONTACT_TYPE_TPT_FOLLOW_UP,
                                SectionPhase.POST_SUBMIT
                            )
                        }
                    }
            }
        } else {
            tptFollowupRow.visibility = View.GONE
        }

        viewModel.historyState.observe(viewLifecycleOwner) { state ->
            val historyBtn = tptFollowupRow.findViewById<MaterialButton>(R.id.btn_form_history)
            when (state) {
                is NetworkResponse.Idle -> Unit
                is NetworkResponse.Loading -> {
                    historyBtn.isEnabled = false
                }
                is NetworkResponse.Success -> {
                    historyBtn.isEnabled = true
                    android.widget.Toast.makeText(
                        requireContext(), "History refreshed", android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                is NetworkResponse.Error -> {
                    historyBtn.isEnabled = true
                    Snackbar.make(
                        view,
                        state.message ?: getString(R.string.contact_tracing_load_error),
                        Snackbar.LENGTH_LONG
                    ).setAction(getString(R.string.counselling_retry)) {
                        viewModel.onHistoryClicked()
                    }.show()
                }
            }
        }

        // Auto-flow: if opened with autoFlow=true, immediately navigate to next unfilled form
        if (autoFlow) {
            viewLifecycleOwner.lifecycleScope.launch {
                val nextIndex = if (isRegistrar) {
                    val tbFilled = viewModel.isTbScreeningFilled.first()
                    val anthropometryFilled = viewModel.isAnthropometryFilled.first()
                    when {
                        !tbFilled -> FORM_TB_SCREENING
                        !anthropometryFilled -> FORM_ANTHROPOMETRY
                        else -> null
                    }
                } else if (isNurse) {
                    val tbFilled = viewModel.isTbScreeningFilled.first()
                    val anthropometryFilled = viewModel.isAnthropometryFilled.first()
                    val generalExamFilled = viewModel.isGeneralExamFilled.first()
                    val generalOpdFilled = viewModel.isGeneralOpdFilled.first()
                    when {
                        !tbFilled -> FORM_TB_SCREENING
                        !anthropometryFilled -> FORM_ANTHROPOMETRY
                        !generalExamFilled -> FORM_GENERAL_EXAM
                        !generalOpdFilled -> FORM_GENERAL_OPD
                        else -> null
                    }
                } else {
                    viewModel.nextUnfilledFormIndex.first()
                }
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
        notFilled: TextView,
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
                    if(isCounsellingOfficer){
                        btn.visibility = View.GONE
                        notFilled.visibility = View.VISIBLE
                    }else{
                        btn.visibility = View.VISIBLE
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