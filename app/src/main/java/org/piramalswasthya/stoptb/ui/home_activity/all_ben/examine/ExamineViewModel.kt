package org.piramalswasthya.stoptb.ui.home_activity.all_ben.examine

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.piramalswasthya.stoptb.repositories.RecordsRepo
import org.piramalswasthya.stoptb.repositories.TBRepo
import org.piramalswasthya.stoptb.repositories.VitalRepo
import javax.inject.Inject

@HiltViewModel
class ExamineViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recordsRepo: RecordsRepo,
    private val vitalRepo: VitalRepo,
    private val tbRepo: TBRepo
) : ViewModel() {

    val benId: Long = savedStateHandle["benId"] ?: -1L

    /** 1. Anthropometric — height/weight stored directly in BENEFICIARY table */
    val isAnthropometryFilled: Flow<Boolean> =
        recordsRepo.anthropometryFilledBenIds.map { benId in it }

    /** 2. General Exam (Vitals) — BEN_VITALS table */
    val isGeneralExamFilled: Flow<Boolean> =
        vitalRepo.vitalBenIds.map { benId in it }

    /** 3. TB Screening — TB_SCREENING table */
    val isTbScreeningFilled: Flow<Boolean> =
        tbRepo.tbScreeningBenIds.map { benId in it }

    /** 4. General OPD — GENERAL_OPD table */
    val isGeneralOpdFilled: Flow<Boolean> =
        tbRepo.generalOpdBenIds.map { benId in it }

    /** 5. Diagnosis — checks TB_DIAGNOSTICS (new saves) OR TB_SUSPECTED (legacy saves) */
    val isDiagnosisFilled: Flow<Boolean> =
        combine(
            tbRepo.tbDiagnosticsBenIds,
            tbRepo.tbSuspectedBenIds
        ) { diagnosticsIds, suspectedIds ->
            benId in diagnosticsIds || benId in suspectedIds
        }

    /**
     * Next unfilled form index in auto-flow order (0→1→2→3→4).
     * Diagnosis (4) is only included if TB Screening (2) is already done.
     * Returns null when all applicable forms are filled.
     */
    val nextUnfilledFormIndex: Flow<Int?> = combine(
        isAnthropometryFilled,
        isGeneralExamFilled,
        isTbScreeningFilled,
        isGeneralOpdFilled,
        isDiagnosisFilled
    ) { anthro, genExam, tbScreen, genOpd, diagnosis ->
        when {
            !anthro   -> 0  // FORM_ANTHROPOMETRY
            !genExam  -> 1  // FORM_GENERAL_EXAM
            !tbScreen -> 2  // FORM_TB_SCREENING
            !genOpd   -> 3  // FORM_GENERAL_OPD
            tbScreen && !diagnosis -> 4  // FORM_DIAGNOSIS — only after TB Screening
            else      -> null  // all done
        }
    }
}
