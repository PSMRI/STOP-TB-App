package org.piramalswasthya.stoptb.ui.contact_tracing

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.helpers.NetworkResponse
import org.piramalswasthya.stoptb.model.dynamicEntity.CompleteFormResponse
import org.piramalswasthya.stoptb.model.dynamicEntity.ConditionRefDto
import org.piramalswasthya.stoptb.model.dynamicEntity.QuestionResponseEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.CounsellingConditionDto
import org.piramalswasthya.stoptb.model.dynamicEntity.CounsellingOptionDto
import org.piramalswasthya.stoptb.model.dynamicEntity.CounsellingQuestionDto
import org.piramalswasthya.stoptb.model.dynamicEntity.CounsellingValidationDto
import org.piramalswasthya.stoptb.model.dynamicEntity.FormSectionWithQuestions
import org.piramalswasthya.stoptb.model.dynamicEntity.OptionConditionEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.SectionQuestionEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.SectionQuestionWithDetails
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.model.TBConfirmedTreatmentCache
import org.piramalswasthya.stoptb.model.TBSuspectedCache
import org.piramalswasthya.stoptb.repositories.TBRepo
import org.piramalswasthya.stoptb.repositories.contactTracing.IContactTracingRepository
import org.piramalswasthya.stoptb.ui.counselling_activity.ActionType
import org.piramalswasthya.stoptb.ui.counselling_activity.FormType
import org.piramalswasthya.stoptb.ui.counselling_activity.QuestionType
import org.piramalswasthya.stoptb.ui.counselling_activity.SectionPhase
import org.piramalswasthya.stoptb.work.ContactTracingSyncWorker
import org.piramalswasthya.stoptb.work.WorkerUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

private const val QUESTION_UUID_TFU_REGISTRATION_DATE = "TFU_REGISTRATION_DATE"

@HiltViewModel
class ContactTracingFormViewModel @Inject constructor(
    private val repository: IContactTracingRepository,
    private val tbRepo: TBRepo,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val gson = Gson()

    private var sections: List<FormSectionWithQuestions> = emptyList()
    private var rawQuestionsByUuid: Map<String, SectionQuestionEntity> = emptyMap()
    private var conditionsByOptionId: Map<Int, List<OptionConditionEntity>> = emptyMap()
    private var questionsByUuid: Map<String, CounsellingQuestionDto> = emptyMap()
    private var currentFormType: FormType? = null

    private var responseId: Long = 0L
    private var currentSectionIndex = 0
    private var persistedStatus: String? = null
    private var currentSectionPhase: SectionPhase? = null

    private var isSubmitting = false
    private var pendingIndexCaseBenId: Long = 0
    private var pendingFormVersionId: Int = 0

    private var lastIndexCaseBenId: Long = 0L
    private var lastContactType: String = "COMMUNITY"
    private var lastViewHistory: Boolean = false
    private var isHistoryMode: Boolean = false
    private var historyResponses: List<CompleteFormResponse> = emptyList()
    private var historyResponseIndex: Int = 0
    private var historyVisitIndex: Int = 0
    private var historyVisitCounts: List<Int> = emptyList()

    private val _activeQuestions = MutableLiveData<List<CounsellingQuestionDto>>()
    val activeQuestions: LiveData<List<CounsellingQuestionDto>> get() = _activeQuestions

    private val _currentSectionName = MutableLiveData<String>()
    val currentSectionName: LiveData<String> get() = _currentSectionName

    private val _progress = MutableLiveData<Pair<Int, Int>>()
    val progress: LiveData<Pair<Int, Int>> get() = _progress

    private val _alertMessage = MutableLiveData<String?>()
    val alertMessage: LiveData<String?> get() = _alertMessage

    private val _navigateToFormUuid = MutableLiveData<String?>()
    val navigateToFormUuid: LiveData<String?> get() = _navigateToFormUuid

    private val _formCompleted = MutableLiveData<Boolean>()
    val formCompleted: LiveData<Boolean> get() = _formCompleted

    private val _exitRequested = MutableLiveData<Boolean>()
    val exitRequested: LiveData<Boolean> get() = _exitRequested

    private val _formSchemaState = MutableLiveData<NetworkResponse<Unit>>(NetworkResponse.Idle())
    val formSchemaState: LiveData<NetworkResponse<Unit>> get() = _formSchemaState

    private val _isEditable = MutableLiveData<Boolean>(true)
    val isEditable: LiveData<Boolean> get() = _isEditable

    private val _showContinueTpt = MutableLiveData(false)
    val showContinueTpt: LiveData<Boolean> get() = _showContinueTpt
    private val _tptPreSubmitAlreadySubmitted = MutableLiveData(false)
    val tptPreSubmitAlreadySubmitted: LiveData<Boolean> get() = _tptPreSubmitAlreadySubmitted

    // Tracks pending TPT Follow-Up resolution; disable submit while network calls determine whether this already-submitted CONTACT_FOLLOW_UP should continue to TPT_FOLLOW_UP.
    private val _resolvingContinueTpt = MutableLiveData(false)
    val resolvingContinueTpt: LiveData<Boolean> get() = _resolvingContinueTpt

    fun open(
        formType: FormType,
        indexCaseBenId: Long,
        @Suppress("UNUSED_PARAMETER") contactType: String,
        sectionPhase: SectionPhase? = null,
        viewHistory: Boolean = false
    ) {
        currentFormType = formType
        currentSectionPhase = sectionPhase
        lastIndexCaseBenId = indexCaseBenId
        lastContactType = contactType
        lastViewHistory = viewHistory
        isHistoryMode = viewHistory
        _formSchemaState.value = NetworkResponse.Loading()
        viewModelScope.launch {
            val response = repository.getFormSchema(formType)
            if (response is NetworkResponse.Error) {
                _formSchemaState.value = NetworkResponse.Error(response.message ?: "Schema definition not found")
                return@launch
            }
            val definition = (response as? NetworkResponse.Success)?.data
            val activeVersion = definition?.versions?.firstOrNull { it.version.isActive }
                ?: definition?.versions?.maxByOrNull { it.version.versionNumber }
            if (activeVersion == null) {
                _formSchemaState.value = NetworkResponse.Error("Schema definition not found")
                return@launch
            }
            val allSections = activeVersion.sections.sortedBy { it.section.sectionOrder }

            sections = sectionPhase?.let { p -> allSections.filter { it.section.sectionPhase == p.value } }
                ?: allSections

            rawQuestionsByUuid = sections.flatMap { it.questions }
                .associateBy { it.question.questionUuid ?: it.question.questionId.toString() }
                .mapValues { it.value.question }

            conditionsByOptionId = sections.flatMap { it.questions }
                .flatMap { it.options }
                .associate { it.option.optionId to it.conditions }

            pendingIndexCaseBenId = indexCaseBenId
            pendingFormVersionId = activeVersion.version.versionId

            if (viewHistory) {
                historyResponses = repository.getTptHistory(indexCaseBenId, activeVersion.version.versionId).first()
                if (historyResponses.isEmpty()) {
                    _formSchemaState.value = NetworkResponse.Error(
                        context.getString(R.string.tpt_history_empty)
                    )
                    return@launch
                }
                val sectionIds = sections.map { it.section.sectionId }.toSet()
                historyVisitCounts = historyResponses.map { resp ->
                    resp.sectionResponses
                        .filter { it.sectionResponse.sectionId in sectionIds }
                        .groupBy { it.sectionResponse.sectionId }
                        .values
                        .maxOfOrNull { it.size } ?: 1
                }
                historyResponseIndex = 0
                historyVisitIndex = 0
                responseId = historyResponses[historyResponseIndex].formResponse.responseId
                persistedStatus = "COMPLETE"
                _isEditable.value = false
                currentSectionIndex = 0
                loadSection(currentSectionIndex)
                return@launch
            }

            if (formType == FormType.TPT_FOLLOW_UP) {
                repository.fetchAndRefreshTptHistory(indexCaseBenId, activeVersion.version.versionId)
            } else {
                repository.fetchAndStoreContactResponse(indexCaseBenId, formType, activeVersion.version.versionId)
            }

            val existing = if (formType == FormType.TPT_FOLLOW_UP && sectionPhase != null) {
                repository.getExistingContactResponseForPhase(indexCaseBenId, activeVersion.version.versionId, sectionPhase)
            } else {
                repository.getExistingContactResponse(indexCaseBenId, activeVersion.version.versionId)
            }
            val followUpTargetReached = formType == FormType.TPT_FOLLOW_UP && sectionPhase == SectionPhase.POST_SUBMIT &&
                repository.isFollowUpTargetReached(indexCaseBenId, activeVersion.version.versionId)

            if (existing != null) {
                responseId = existing.responseId
                persistedStatus = existing.status
                _isEditable.value = !followUpTargetReached && isEditableFor(existing.status, sectionPhase)
            } else {
                responseId = 0L
                persistedStatus = null
                _isEditable.value = !followUpTargetReached
            }

            currentSectionIndex = 0
            loadSection(currentSectionIndex)

            if (formType == FormType.CONTACT_FOLLOW_UP && persistedStatus == "SUBMITTED") {
                _resolvingContinueTpt.value = true
                try {
                    val (eligible, alreadySubmitted) = resolveContinueTptState(indexCaseBenId, responseId)
                    _showContinueTpt.value = eligible
                    _tptPreSubmitAlreadySubmitted.value = alreadySubmitted
                } finally {
                    _resolvingContinueTpt.value = false
                }
            }
        }
    }
    private fun isEditableFor(status: String?, phase: SectionPhase?): Boolean = when (phase) {
        SectionPhase.PRE_SUBMIT -> status == null || status == "DRAFT"
        SectionPhase.POST_SUBMIT -> status == null || status == "DRAFT" || status == "SUBMITTED"
        else -> status != "SUBMITTED"
    }

    fun enterEditMode() {
        _isEditable.value = true
    }
    fun retryLoad() {
        val formType = currentFormType ?: return
        open(formType, lastIndexCaseBenId, lastContactType, currentSectionPhase, lastViewHistory)
    }
    fun continueToTpt() {
        _navigateToFormUuid.value = "TPT_FOLLOW_UP"
    }

    fun loadResultForm(benId : Long){

    }

    private suspend fun resolveContinueTptState(benId: Long, cfuResponseId: Long): Pair<Boolean, Boolean> {
        val screeningQuestion = rawQuestionsByUuid[QUESTION_UUID_CLINICAL_SCREENING_STATUS] ?: return false to false
        val complete = repository.getCompleteResponse(cfuResponseId) ?: return false to false
        val answerRow = complete.sectionResponses.flatMap { it.questionResponses }
            .firstOrNull { it.questionId == screeningQuestion.questionId } ?: return false to false
        val optionValue = answerRow.optionId?.let { oid ->
            sections.flatMap { it.questions }
                .firstOrNull { it.question.questionId == screeningQuestion.questionId }
                ?.options?.firstOrNull { it.option.optionId == oid }?.option?.optionValue
        } ?: answerRow.answerText
        if (ClinicalScreeningStatus.fromValue(optionValue) != ClinicalScreeningStatus.TPT_ELIGIBLE) return false to false

        val tptDefinition = (repository.getFormSchema(FormType.TPT_FOLLOW_UP) as? NetworkResponse.Success)?.data
            ?: return true to false
        val tptVersion = tptDefinition.versions.firstOrNull { it.version.isActive }
            ?: tptDefinition.versions.maxByOrNull { it.version.versionNumber } ?: return true to false

        var preSubmitResponse = repository.getExistingContactResponseForPhase(
            benId, tptVersion.version.versionId, SectionPhase.PRE_SUBMIT
        )
        if (preSubmitResponse == null) {

            repository.fetchAndRefreshTptHistory(benId, tptVersion.version.versionId)
            preSubmitResponse = repository.getExistingContactResponseForPhase(
                benId, tptVersion.version.versionId, SectionPhase.PRE_SUBMIT
            )
        }
        return true to (preSubmitResponse?.status == "SUBMITTED")
    }

    /** Moves to the next visit within the current history response, or into the next response
     * if this one is exhausted. Returns false once there's nothing further (last visit overall). */
    private fun advanceToNextHistoryVisit(): Boolean {
        val visitsInCurrentResponse = historyVisitCounts.getOrNull(historyResponseIndex) ?: 1
        if (historyVisitIndex < visitsInCurrentResponse - 1) {
            historyVisitIndex++
            return true
        }
        if (historyResponseIndex < historyResponses.size - 1) {
            historyResponseIndex++
            historyVisitIndex = 0
            responseId = historyResponses[historyResponseIndex].formResponse.responseId
            return true
        }
        return false
    }

    /** Symmetric counterpart of advanceToNextHistoryVisit — steps back a visit, crossing into
     * the previous response's last visit if the current one is already at its first. */
    private fun retreatToPreviousHistoryVisit(): Boolean {
        if (historyVisitIndex > 0) {
            historyVisitIndex--
            return true
        }
        if (historyResponseIndex > 0) {
            historyResponseIndex--
            historyVisitIndex = (historyVisitCounts.getOrNull(historyResponseIndex) ?: 1) - 1
            responseId = historyResponses[historyResponseIndex].formResponse.responseId
            return true
        }
        return false
    }

    private fun loadSection(index: Int) {
        if (index >= sections.size) {
            _formCompleted.value = true
            return
        }
        val sectionWithQuestions = sections[index]

        val builtQuestions = sectionWithQuestions.questions
            .sortedBy { it.question.questionOrder }
            .map { it.toCounsellingQuestionDto() }

        questionsByUuid = questionsByUuid + builtQuestions.associateBy { it.questionUuid }

        viewModelScope.launch {
            val existing = if (isHistoryMode) {
                historyResponses.getOrNull(historyResponseIndex)
            } else {
                repository.getCompleteResponse(responseId)
            }
            populateAnswers(builtQuestions, sectionWithQuestions.section.sectionId, existing, historyVisitIndex)
            questionsByUuid = questionsByUuid + builtQuestions.associateBy { it.questionUuid }
            evaluateAllConditions(builtQuestions)
            ensureTptRegistrationDate(builtQuestions, sectionWithQuestions.section.sectionId)
            ensureDateNotBeforeScreening(builtQuestions, sectionWithQuestions.section.sectionId)
            ensureExpectedCompletionDate(builtQuestions, sectionWithQuestions.section.sectionId)

            _activeQuestions.value = builtQuestions.filter { it.visible }
            if (isHistoryMode) {
                val visitsBeforeThisResponse = historyVisitCounts.take(historyResponseIndex).sum()
                val totalVisits = historyVisitCounts.sum().coerceAtLeast(1)
                val followUpNumber = visitsBeforeThisResponse + historyVisitIndex + 1
                _currentSectionName.value = context.getString(
                    R.string.tpt_history_section_label,
                    sectionWithQuestions.section.sectionName, followUpNumber, totalVisits
                )
                _progress.value = ((followUpNumber - 1) * sections.size + index + 1) to
                    (totalVisits * sections.size)
            } else {
                _currentSectionName.value = sectionWithQuestions.section.sectionName
                _progress.value = (index + 1) to sections.size
            }
            _formSchemaState.value = NetworkResponse.Success(Unit)
        }
    }

    /** Called by the adapter's onValueChanged. Full section reevaluate (conditions +
     * _activeQuestions reassignment, which drives a RecyclerView relayout) is skipped for plain
     * TEXT edits to avoid doing that on every keystroke — mirrors Counselling's same guard. A
     * keystroke still clears/updates that one field's own already-shown error though, via
     * refreshErrorIfNeeded — a single validateQuestion() call, not a relayout. */
    fun onQuestionValueChanged(question: CounsellingQuestionDto, reevaluate: Boolean) {
        questionsByUuid = questionsByUuid + (question.questionUuid to question)
        if (!reevaluate) {
            refreshErrorIfNeeded(question)
            updateComputedNoOfContactsErrorIfNeeded(question)
            return
        }
        val current = _activeQuestions.value ?: return
        val fullSection = sections.getOrNull(currentSectionIndex)?.questions
            ?.map { it.question.questionUuid ?: it.question.questionId.toString() }
            ?: return
        val allSectionQuestions = fullSection.mapNotNull { questionsByUuid[it] }
        evaluateAllConditions(allSectionQuestions)

        updateExpectedCompletionDateIfNeeded(question)
        allSectionQuestions.filter { it.visible }.forEach { q -> refreshErrorIfNeeded(q) }
        updateComputedNoOfContactsErrorIfNeeded(question)

        _activeQuestions.value = allSectionQuestions.filter { it.visible }
    }

    /** Recomputes Expected Completion Date when Regimen or TPT Start Date changes. */
    private fun updateExpectedCompletionDateIfNeeded(question: CounsellingQuestionDto) {
        if (question.questionUuid != QUESTION_UUID_REGIMEN_ADVISED &&
            question.questionUuid != QUESTION_UUID_TPT_START_DATE) return

        val regimen = RegimenAdvised.fromValue(questionsByUuid[QUESTION_UUID_REGIMEN_ADVISED]?.value?.toString()) ?: return
        val startDateStr = questionsByUuid[QUESTION_UUID_TPT_START_DATE]?.value?.toString() ?: return
        val completionQ = questionsByUuid[QUESTION_UUID_EXPECTED_COMPLETION_DATE] ?: return

        val computed = computeExpectedCompletionDate(startDateStr, regimen) ?: return
        completionQ.value = computed
        questionsByUuid = questionsByUuid + (completionQ.questionUuid to completionQ)
        refreshErrorIfNeeded(completionQ)
    }

    private fun updateComputedNoOfContactsErrorIfNeeded(question: CounsellingQuestionDto) {
        val noOfContactsQ = questionsByUuid["CCT_NO_OF_CONTACTS"] ?: return
        val relQ = questionsByUuid["CCT_RELATIONSHIP"]
        val countFieldIds = relQ?.options.orEmpty()
            .flatMap { it.conditions.orEmpty() }
            .filter { it.actionType == ActionType.SHOW_QUESTION.value }
            .mapNotNull { it.targetQuestionId }
            .toSet()

        if (question.questionUuid == "CCT_NO_OF_CONTACTS" || question.questionId in countFieldIds) {
            val allQuestions = questionsByUuid.values.toList()
            val newSum = allQuestions
                .filter { it.questionId in countFieldIds }
                .sumOf { it.value?.toString()?.toIntOrNull() ?: 0 }
                .toString()

            noOfContactsQ.value = newSum
            refreshErrorIfNeeded(noOfContactsQ)
        }
    }

    private fun refreshErrorIfNeeded(q: CounsellingQuestionDto) {
        val newError = validateQuestion(q)
        if (q.errorMessage != newError && (q.errorMessage != null || newError == null)) {
            q.errorMessage = newError
        }
    }
    fun onNext() {
        val isReadOnlyFollowUp = _isEditable.value != true &&
                (currentFormType == FormType.CONTACT_FOLLOW_UP || currentFormType == FormType.TPT_FOLLOW_UP)

        if (_isEditable.value != true && !isReadOnlyFollowUp) return

        if (isReadOnlyFollowUp) {
            val totalSections = sections.size
            if (currentSectionIndex < totalSections - 1) {
                currentSectionIndex++
                loadSection(currentSectionIndex)
            } else if (isHistoryMode && advanceToNextHistoryVisit()) {
                currentSectionIndex = 0
                loadSection(currentSectionIndex)
            }
            return
        }

        val current = _activeQuestions.value ?: return
        if (!validateSection(current)) {
            _activeQuestions.value = current
            return
        }

        viewModelScope.launch {
            saveCurrentSection(current)
            currentSectionIndex++
            loadSection(currentSectionIndex)
        }
    }

    fun onSubmit() {
        if (_isEditable.value != true || isSubmitting) {
            if(currentFormType == FormType.CONTACT_FOLLOW_UP || currentFormType == FormType.TPT_FOLLOW_UP){
                _showContinueTpt.value = false
                _formCompleted.value = true
                return
            }
            return
        }
        val current = _activeQuestions.value ?: return
        if (!validateSection(current)) {
            _activeQuestions.value = current
            return
        }
        isSubmitting = true
        viewModelScope.launch {
            try {
                saveCurrentSection(current)
                val finalStatus = if (currentFormType == FormType.TPT_FOLLOW_UP && currentSectionPhase == SectionPhase.POST_SUBMIT)
                    "COMPLETE" else "SUBMITTED"
                repository.submitResponse(responseId, finalStatus)
                persistedStatus = finalStatus
                if (responseId > 0) {
                    val pushed = repository.submitResponseBulk(responseId, currentSectionPhase?.value)
                    if (!pushed) {
                        ContactTracingSyncWorker.scheduleSync(context)
                    }
                }
                val screeningStatusAnswer = questionsByUuid[QUESTION_UUID_CLINICAL_SCREENING_STATUS]?.value?.toString()
                if (currentFormType == FormType.CONTACT_FOLLOW_UP && screeningStatusAnswer != null) {
                    handleClinicalScreeningStatusSubmit(pendingIndexCaseBenId, screeningStatusAnswer)
                } else {
                    _formCompleted.value = true
                }
            } finally {
                isSubmitting = false
            }
        }
    }

    /** Branches on the CFU_CLINICAL_SCREENING_STATUS answer just submitted on a
     * CONTACT_FOLLOW_UP form for [benId] (the contact's own beneficiary id). */
    private suspend fun handleClinicalScreeningStatusSubmit(benId: Long, answerValue: String?) {
        when (ClinicalScreeningStatus.fromValue(answerValue)) {
            ClinicalScreeningStatus.FULL_TREATMENT -> {
                markBeneficiaryAsConfirmedTbCase(benId)
                _formCompleted.value = true
            }

            ClinicalScreeningStatus.TPT_ELIGIBLE -> {
                _navigateToFormUuid.value = "TPT_FOLLOW_UP"
            }

            ClinicalScreeningStatus.NO_TREATMENT, null -> {
                _formCompleted.value = true
            }
        }
    }
    private suspend fun markBeneficiaryAsConfirmedTbCase(benId: Long) {
        if (tbRepo.getTBConfirmed(benId) == null) {
            tbRepo.saveTBConfirmed(TBConfirmedTreatmentCache(benId = benId))
        }
        val tbSuspected = tbRepo.getTBSuspected(benId) ?: TBSuspectedCache(benId = benId)
        tbSuspected.isConfirmed = true
        tbSuspected.isTBConfirmed = true
        tbSuspected.syncState = SyncState.UNSYNCED
        tbRepo.saveTBSuspected(tbSuspected)

        // Auto-Sync TB rows on SAVE_SUCCESS
        WorkerUtils.triggerAmritPushWorker(context)
    }

    fun onBack() {
        if (isSubmitting) return
        val current = _activeQuestions.value
        val editable = _isEditable.value == true
        viewModelScope.launch {
            if (editable && current != null) saveCurrentSection(current)
            if (currentSectionIndex > 0) {
                currentSectionIndex--
                loadSection(currentSectionIndex)
            } else if (isHistoryMode && retreatToPreviousHistoryVisit()) {
                currentSectionIndex = sections.size - 1
                loadSection(currentSectionIndex)
            } else {
                _exitRequested.value = true
            }
        }
    }

    fun consumeExit() {
        _exitRequested.value = false
    }

    fun consumeAlert() {
        _alertMessage.value = null
    }

    fun consumeNavigation() {
        _navigateToFormUuid.value = null
    }

    private suspend fun saveCurrentSection(questions: List<CounsellingQuestionDto>) {
        val section = sections.getOrNull(currentSectionIndex) ?: return
        val answerRows = buildAnswerRows(questions)

        if (answerRows.isEmpty()) return
        val id = ensureResponseCreated()
        val status = persistedStatus?.takeIf { it != "DRAFT" } ?: "DRAFT"
        repository.saveSectionAnswers(id, section.section.sectionId, answerRows, status)
    }

    /** Returns the existing responseId, or creates the response row now (first save only). */
    private suspend fun ensureResponseCreated(): Long {
        if (responseId > 0) return responseId

        val contactResponse = if (currentFormType == FormType.TPT_FOLLOW_UP) {
            repository.createPhaseScopedResponse(
                pendingIndexCaseBenId, pendingFormVersionId, sections.map { it.section.sectionId }
            )
        } else {
            repository.getOrCreateContactResponse(pendingIndexCaseBenId, pendingFormVersionId)
        }
        responseId = contactResponse.responseId
        return responseId
    }

    private fun buildAnswerRows(questions: List<CounsellingQuestionDto>): List<QuestionResponseEntity> {
        return questions.filter { it.value != null }.flatMap { q ->
            when (val v = q.value) {
                is List<*> -> v.mapNotNull { optVal ->
                    q.options?.firstOrNull { it.optionValue == optVal }?.let { opt ->
                        QuestionResponseEntity(sectionResponseId = 0, questionId = q.questionId, optionId = opt.optionId)
                    }
                }
                else -> {
                    val matchedOption = q.options?.firstOrNull { it.optionValue == v?.toString() }
                    listOf(
                        QuestionResponseEntity(
                            sectionResponseId = 0,
                            questionId = q.questionId,
                            optionId = matchedOption?.optionId,
                            answerText = if (matchedOption == null) v?.toString() else null
                        )
                    )
                }
            }
        }
    }

    private fun populateAnswers(
        questions: List<CounsellingQuestionDto>,
        sectionId: Int,
        existing: CompleteFormResponse?,
        visitIndex: Int = 0
    ) {
        val matches = existing?.sectionResponses
            ?.filter { it.sectionResponse.sectionId == sectionId }
            ?.sortedBy { it.sectionResponse.sectionResponseId }
            ?: emptyList()
        val sectionResponse = if (matches.isEmpty()) null else matches[visitIndex.coerceIn(0, matches.size - 1)]
        val answersByQuestionId = sectionResponse?.questionResponses?.groupBy { it.questionId } ?: emptyMap()
        questions.forEach { q ->
            val rows = answersByQuestionId[q.questionId] ?: return@forEach
            val isMultiSelect = q.questionType == QuestionType.CHECKBOX_MULTI.value ||
                q.questionType == QuestionType.DROPDOWN_MULTI.value
            q.value = if (isMultiSelect) {
                rows.mapNotNull { row -> q.options?.firstOrNull { it.optionId == row.optionId }?.optionValue }
            } else {
                val row = rows.first()
                row.optionId?.let { oid -> q.options?.firstOrNull { it.optionId == oid }?.optionValue } ?: row.answerText
            }
        }
    }
    /**For the Registration Date field (UUID: TFU_REGISTRATION_DATE) in the TPT_FOLLOWUP PreSubmit Form.**/
    private suspend fun ensureTptRegistrationDate(questions: List<CounsellingQuestionDto>, sectionId: Int) {
        if (currentFormType != FormType.TPT_FOLLOW_UP || currentSectionPhase != SectionPhase.PRE_SUBMIT) return
        val dateQuestion = questions.firstOrNull { it.questionUuid == QUESTION_UUID_TFU_REGISTRATION_DATE } ?: return
        if (dateQuestion.value != null) return
        dateQuestion.value = SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH).format(Date())
        val id = ensureResponseCreated()
        val status = persistedStatus?.takeIf { it != "DRAFT" } ?: "DRAFT"
        repository.saveSectionAnswers(id, sectionId, buildAnswerRows(questions.filter { it.visible }), status)
    }

    /** Bounds TFU_START_DATE and TFU_VISIT_DATE to not precede the beneficiary's TB screening visit date. */
    private suspend fun ensureDateNotBeforeScreening(questions: List<CounsellingQuestionDto>, sectionId: Int) {
        if (currentFormType != FormType.TPT_FOLLOW_UP) return
        val targetUuid = when (currentSectionPhase) {
            SectionPhase.PRE_SUBMIT -> QUESTION_UUID_TPT_START_DATE
            SectionPhase.POST_SUBMIT -> QUESTION_UUID_TPT_VISIT_DATE
            else -> return
        }
        val dateQuestion = questions.firstOrNull { it.questionUuid == targetUuid } ?: return
        if (dateQuestion.validations.orEmpty().any { it.validationType == "MIN_DATE" }) return

        val visitDateMillis = tbRepo.getTBScreening(pendingIndexCaseBenId)?.visitDate ?: return
        val isoScreeningDate = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date(visitDateMillis))

        val existingMaxDate = dateQuestion.validations.orEmpty().firstOrNull { it.validationType == "MAX_DATE" }
        if (existingMaxDate != null) {
            val maxDateStr = if (existingMaxDate.validationParam.equals("TODAY", true)) {
                SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())
            } else {
                existingMaxDate.validationParam
            }
            if (isoScreeningDate > maxDateStr) return
        }

        val errorMsg = "${dateQuestion.questionText} cannot be before the Screening Date"
        dateQuestion.validations = dateQuestion.validations.orEmpty() + listOf(
            CounsellingValidationDto(null, "MIN_DATE", isoScreeningDate, errorMsg),
            CounsellingValidationDto(null, "DATE_NOT_BEFORE", isoScreeningDate, errorMsg)
        )
        questionsByUuid = questionsByUuid + (dateQuestion.questionUuid to dateQuestion)
    }

    /** Computes and persists Expected Completion Date when Regimen and Start Date are already set. */
    private suspend fun ensureExpectedCompletionDate(questions: List<CounsellingQuestionDto>, sectionId: Int) {
        if (currentFormType != FormType.TPT_FOLLOW_UP || currentSectionPhase != SectionPhase.PRE_SUBMIT) return
        val completionQ = questions.firstOrNull { it.questionUuid == QUESTION_UUID_EXPECTED_COMPLETION_DATE } ?: return
        if (completionQ.value != null) return
        val regimen = RegimenAdvised.fromValue(
            questions.firstOrNull { it.questionUuid == QUESTION_UUID_REGIMEN_ADVISED }?.value?.toString()
        ) ?: return
        val startDateStr = questions.firstOrNull { it.questionUuid == QUESTION_UUID_TPT_START_DATE }?.value?.toString() ?: return
        val computed = computeExpectedCompletionDate(startDateStr, regimen) ?: return

        completionQ.value = computed
        val id = ensureResponseCreated()
        val status = persistedStatus?.takeIf { it != "DRAFT" } ?: "DRAFT"
        repository.saveSectionAnswers(id, sectionId, buildAnswerRows(questions.filter { it.visible }), status)
    }

    private fun evaluateAllConditions(questions: List<CounsellingQuestionDto>) {
        questions.forEach { q ->
            val raw = rawQuestionsByUuid[q.questionUuid]
            q.visible = parseRef(raw?.enabledIfJson)?.let { matchesCondition(it) } ?: q.visibleByDefault
            parseRef(raw?.disabledIfJson)?.let { if (matchesCondition(it)) q.visible = false }
            q.isMandatory = (parseRef(raw?.mandatoryIfJson)?.let { matchesCondition(it) } ?: (q.originalIsMandatory ?: q.isMandatory))
                    || matchesMandatoryIfValidation(q)
        }

        questions.forEach { q ->
            val selectedValues = when (val v = q.value) {
                is List<*> -> v.mapNotNull { it?.toString() }
                null -> emptyList()
                else -> listOf(v.toString())
            }
            q.options?.filter { selectedValues.contains(it.optionValue) }?.forEach { opt ->
                conditionsByOptionId[opt.optionId]?.forEach { applyCondition(it) }
            }
        }
    }

    // Determines if the question's mandatory-if condition is satisfied.
    private fun matchesMandatoryIfValidation(q: CounsellingQuestionDto): Boolean {
        val mandatoryIf = q.validations?.firstOrNull { it.validationType == ActionType.MANDATORY_IF.value } ?: return false
        val parts = mandatoryIf.validationParam.split("=")
        if (parts.size != 2) return false
        return questionsByUuid[parts[0]]?.value?.toString() == parts[1]
    }

    private fun parseRef(json: String?): ConditionRefDto? =
        json?.let { runCatching { gson.fromJson(it, ConditionRefDto::class.java) }.getOrNull() }

    private fun matchesCondition(ref: ConditionRefDto): Boolean {
        val target = questionsByUuid[ref.questionUuid] ?: return false
        val value = target.value
        return when {
            ref.equalsValue != null -> value?.toString() == ref.equalsValue
            ref.inValues != null -> ref.inValues.contains(value?.toString())
            ref.containsValue != null -> (value as? List<*>)?.map { it.toString() }?.contains(ref.containsValue) == true
            ref.isNotEmpty == true -> !value?.toString().isNullOrEmpty()
            else -> false
        }
    }

    private fun applyCondition(cond: OptionConditionEntity) {
        val target = questionsByUuid.values.firstOrNull { it.questionId == cond.targetQuestionId }
        when (cond.actionType) {
            ActionType.SHOW_QUESTION.value,
            ActionType.ENABLE_QUESTION.value -> target?.visible = true
            ActionType.DISABLE_QUESTION.value -> target?.visible = false
            ActionType.MANDATORY.value -> target?.isMandatory = true
            ActionType.SET_DEFAULT_VALUE.value -> if (cond.actionValue != null) target?.value = cond.actionValue
            ActionType.GENERATE_ALERT.value -> _alertMessage.value = cond.alertMessage
            ActionType.OPEN_FORM.value -> _navigateToFormUuid.value = cond.targetFormUuid
            ActionType.REDIRECT_TO_FORM.value -> _navigateToFormUuid.value = cond.targetFormUuid
            // MOVE_CARD_TO_LIST, SKIP_TO_SUBMIT, GO_TO_SUBMIT, SHOW_DIRECT_SUBMIT_OPTION,
            // DISABLE_ALL_TPT_FIELDS: either already redundant with the declarative enabledIf
            // handled above, or require cross-module navigation not yet built. No-op for now.
        }
    }

    private fun validateSection(questions: List<CounsellingQuestionDto>): Boolean {
        var allValid = true
        questions.filter { it.visible }.forEach { q ->
            q.errorMessage = validateQuestion(q)
            if (q.errorMessage != null) allValid = false
        }
        return allValid
    }

    private fun getMandatoryError(q: CounsellingQuestionDto): String? {
        val mandatoryIf = q.validations?.firstOrNull { it.validationType == ActionType.MANDATORY_IF.value }
        if (mandatoryIf != null && matchesMandatoryIfValidation(q)) return mandatoryIf.errorMessage
        return if (q.isMandatory) "This field is required" else null
    }


    /** Validate each question based on the question Type **/
    private fun validateQuestion(q: CounsellingQuestionDto): String? {
        val value = q.value
        val isEmpty = when (value) {
            null -> true
            is String -> value.isBlank()
            is List<*> -> value.isEmpty()
            else -> false
        }
        if (isEmpty) return getMandatoryError(q)

        q.validations?.forEach { v ->
            when (v.validationType) {
                "MAX_LENGTH" -> {
                    val max = v.validationParam.toIntOrNull()
                    if (max != null && value.toString().length > max) return v.errorMessage
                }
                "EXACT_LENGTH" -> {
                    val len = v.validationParam.toIntOrNull()
                    if (len != null && value.toString().length != len) return v.errorMessage
                }
                "RANGE" -> {
                    val parts = v.validationParam.split("-")
                    val min = parts.getOrNull(0)?.toIntOrNull()
                    val max = parts.getOrNull(1)?.toIntOrNull()
                    val num = value.toString().toIntOrNull()
                    if (num == null || (min != null && num < min) || (max != null && num > max)) return v.errorMessage
                }
                "NUMERIC_ONLY" -> {
                    if (value.toString().toIntOrNull() == null) return v.errorMessage
                }
                "REGEX" -> {
                    val regexStr = v.validationParam
                    try {
                        val regex = regexStr.toRegex()
                        if (!regex.matches(q.value.toString())) {
                            return v.errorMessage
                        }
                    } catch (e: Exception) {
                        // Ignore invalid regex pattern
                    }
                }
                "MIN_MAX_WORDS" -> {
                    val parts = v.validationParam.split("-")
                    val min = parts.getOrNull(0)?.toIntOrNull() ?: 0
                    val max = parts.getOrNull(1)?.toIntOrNull() ?: Int.MAX_VALUE
                    val wordCount = value.toString().trim().split(Regex("\\s+")).size
                    if (wordCount < min || wordCount > max) return v.errorMessage
                }
                "DATE_NOT_BEFORE" -> {
                    val param = v.validationParam
                    val boundDateStr = when {
                        param.equals("TODAY", true) ->
                            SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH).format(Date())
                        param.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) ->
                            try {
                                SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(param)
                                    ?.let { SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH).format(it) }
                            } catch (_: Exception) {
                                null
                            }
                        else -> questionsByUuid[param]?.value?.toString() // reference to another question's answer
                    }
                    if (boundDateStr != null) {
                        try {
                            val fmt = SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH)
                            val bound = fmt.parse(boundDateStr)
                            val actual = fmt.parse(value.toString())
                            if (bound != null && actual != null && actual.before(bound)) return v.errorMessage
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }
        return null
    }
}

/** Calculates Expected Completion Date from TPT Start Date and regimen duration. */
private fun computeExpectedCompletionDate(startDateStr: String, regimen: RegimenAdvised): String? {
    return try {
        val fmt = SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH)
        val parsedStartDate = fmt.parse(startDateStr) ?: return null
        val cal = Calendar.getInstance().apply {
            time = parsedStartDate
            add(Calendar.MONTH, regimen.durationMonths)
        }
        fmt.format(cal.time)
    } catch (_: Exception) {
        null
    }
}

private fun SectionQuestionWithDetails.toCounsellingQuestionDto(): CounsellingQuestionDto {
    val q = question
    val maxLength = q.maxLength
        ?: validations.find { it.validationType == "MAX_LENGTH" }?.validationValue?.toIntOrNull()
    return CounsellingQuestionDto(
        questionId = q.questionId,
        questionUuid = q.questionUuid ?: q.questionId.toString(),
        questionText = q.questionText,
        questionType = q.questionType,
        isMandatory = q.isRequired,
        displayOrder = q.questionOrder,
        maxLength = maxLength,
        defaultValue = null,
        containsPii = q.containsPii,
        visibleByDefault = q.visibleByDefault,
        validations = validations.map {
            CounsellingValidationDto(
                validationId = it.validationId,
                validationType = it.validationType,
                validationParam = it.validationValue ?: "",
                errorMessage = it.errorMessage
            )
        } + if (q.questionUuid == QUESTION_UUID_EXPECTED_COMPLETION_DATE &&
                validations.none { it.validationType == "DATE_NOT_BEFORE" && it.validationValue == QUESTION_UUID_TPT_START_DATE }) {
            listOf(
                CounsellingValidationDto(
                    validationId = null,
                    validationType = "DATE_NOT_BEFORE",
                    validationParam = QUESTION_UUID_TPT_START_DATE,
                    errorMessage = "Expected Completion Date cannot be before TPT Start Date"
                )
            )
        } else emptyList(),
        options = options.sortedBy { it.option.optionOrder }.map { owc ->
            CounsellingOptionDto(
                optionId = owc.option.optionId,
                optionLabel = owc.option.optionText,
                optionValue = owc.option.optionValue,
                displayOrder = owc.option.optionOrder,
                conditions = owc.conditions.map { c ->
                    CounsellingConditionDto(
                        conditionId = c.conditionId,
                        actionType = c.actionType,
                        targetQuestionId = c.targetQuestionId,
                        targetSectionId = null,
                        targetQuestionUuid = null,
                        targetSectionUuid = null
                    )
                }
            )
        },
        value = null,
        visible = q.visibleByDefault,
        errorMessage = null,
        originalIsMandatory = q.isRequired
    )
}
