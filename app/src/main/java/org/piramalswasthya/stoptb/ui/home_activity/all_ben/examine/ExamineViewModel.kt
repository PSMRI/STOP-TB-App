package org.piramalswasthya.stoptb.ui.home_activity.all_ben.examine

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.helpers.NetworkResponse
import org.piramalswasthya.stoptb.repositories.BenRepo
import org.piramalswasthya.stoptb.repositories.RecordsRepo
import org.piramalswasthya.stoptb.repositories.TBRepo
import org.piramalswasthya.stoptb.repositories.VitalRepo
import org.piramalswasthya.stoptb.repositories.contactTracing.IContactTracingRepository
import org.piramalswasthya.stoptb.ui.counselling_activity.FormType
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ExamineViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recordsRepo: RecordsRepo,
    private val vitalRepo: VitalRepo,
    private val tbRepo: TBRepo,
    private val benRepo: BenRepo,
    private val contactTracingRepo: IContactTracingRepository
) : ViewModel() {

    val benId: Long = savedStateHandle["benId"] ?: -1L

    private val _benName = MutableLiveData<String>()
    val benName: LiveData<String> get() = _benName

    init {
        viewModelScope.launch {
            benRepo.getBenFromId(benId)?.let { ben ->
                _benName.value = listOfNotNull(ben.firstName, ben.lastName)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
            }
        }
    }

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
    private val tptFollowUpStatus: Flow<String?> =
        contactTracingRepo.observeResponseStatus(benId, FormType.TPT_FOLLOW_UP)

    /** Drives the "TPT Followup" row's visibility — true once PRE_SUBMIT has been submitted. */
    val isTptFollowUpPreSubmitDone: Flow<Boolean> =
        tptFollowUpStatus.map { it == "SUBMITTED" || it == "COMPLETE" }

    private val tptFormVersionId: Flow<Int?> = flow {
        emit(
            contactTracingRepo.getFormDefinition(FormType.TPT_FOLLOW_UP)
                ?.versions?.firstOrNull { it.version.isActive }?.version?.versionId
        )
    }
    val requiredFollowUpCount: Flow<Int?> = tptFormVersionId.map { versionId ->
        versionId?.let { contactTracingRepo.getRegimenAdvised(benId, it) }?.requiredFollowUpCount
    }

    val submittedFollowUpCount: Flow<Int> = tptFormVersionId.flatMapLatest { versionId ->
        versionId?.let { contactTracingRepo.observeSubmittedFollowUpCount(benId, it) } ?: flowOf(0)
    }

    // Shows Fill for "TPT Followup" repeatedly until submittedFollowUpCount reaches requiredFollowUpCount, then hides it permanently in favor of History.
    val isTptFollowUpFillAvailable: Flow<Boolean> =
        combine(requiredFollowUpCount, submittedFollowUpCount) { required, submitted ->
            required == null || submitted < required
        }

    private val _historyState = MutableLiveData<NetworkResponse<Unit>>(NetworkResponse.Idle())
    val historyState: LiveData<NetworkResponse<Unit>> get() = _historyState

    fun onHistoryClicked() {
        if (_historyState.value is NetworkResponse.Loading) return
        _historyState.value = NetworkResponse.Loading()
        viewModelScope.launch {
            _historyState.value = try {
                val activeVersion = contactTracingRepo.getFormDefinition(FormType.TPT_FOLLOW_UP)
                    ?.versions?.firstOrNull { it.version.isActive }
                val formVersionId = activeVersion?.version?.versionId

                if (formVersionId == null) {
                    NetworkResponse.Error("Schema definition not found")
                } else if (contactTracingRepo.fetchAndRefreshTptHistory(benId, formVersionId)) {
                    NetworkResponse.Success(Unit)
                } else {
                    NetworkResponse.Error("Failed to fetch TPT follow-up history. Please try again.")
                }
            } catch (e: Exception) {
                Timber.e(e, "onHistoryClicked failed for benId=$benId")
                NetworkResponse.Error("Failed to fetch TPT follow-up history. Please try again.")
            }
        }
    }
}
