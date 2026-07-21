package org.piramalswasthya.stoptb.ui.contact_tracing

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.model.contactTracing.ContactTracingCompleteResponse
import org.piramalswasthya.stoptb.model.contactTracing.ContactTracingConditionRefDto
import org.piramalswasthya.stoptb.model.contactTracing.ContactTracingQuestionResponseEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.CounsellingConditionDto
import org.piramalswasthya.stoptb.model.dynamicEntity.CounsellingOptionDto
import org.piramalswasthya.stoptb.model.dynamicEntity.CounsellingQuestionDto
import org.piramalswasthya.stoptb.model.dynamicEntity.CounsellingValidationDto
import org.piramalswasthya.stoptb.model.dynamicEntity.FormSectionWithQuestions
import org.piramalswasthya.stoptb.model.dynamicEntity.OptionConditionEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.SectionQuestionEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.SectionQuestionWithDetails
import org.piramalswasthya.stoptb.repositories.contactTracing.IContactTracingRepository
import org.piramalswasthya.stoptb.ui.counselling_activity.FormType
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

/**
 * Drives any Contact Tracing form (Selector, HHC, Community, Occupational) — the same
 * generic staged-section engine parameterized by which form/version to load. Reuses
 * CounsellingQuestionDto/CounsellingOptionDto/CounsellingValidationDto as the runtime
 * rendering model (per agreed direction) but keeps its own condition-evaluation logic
 * here rather than in CounsellingViewModel, since Contact Tracing's action-type vocabulary
 * (OPEN_FORM, SET_DEFAULT_VALUE, GENERATE_ALERT, ...) is materially different.
 */
@HiltViewModel
class ContactTracingFormViewModel @Inject constructor(
    private val repository: IContactTracingRepository
) : ViewModel() {

    private val gson = Gson()

    private var sections: List<FormSectionWithQuestions> = emptyList()
    private var rawQuestionsByUuid: Map<String, SectionQuestionEntity> = emptyMap()
    private var conditionsByOptionId: Map<Int, List<OptionConditionEntity>> = emptyMap()
    private var questionsByUuid: Map<String, CounsellingQuestionDto> = emptyMap()

    private var responseId: Long = 0L
    private var currentSectionIndex = 0

    private val _activeQuestions = MutableLiveData<List<CounsellingQuestionDto>>()
    val activeQuestions: LiveData<List<CounsellingQuestionDto>> get() = _activeQuestions

    private val _currentSectionName = MutableLiveData<String>()
    val currentSectionName: LiveData<String> get() = _currentSectionName

    private val _progress = MutableLiveData<Pair<Int, Int>>()
    val progress: LiveData<Pair<Int, Int>> get() = _progress

    private val _hasSubmitButton = MutableLiveData<Boolean>()
    val hasSubmitButton: LiveData<Boolean> get() = _hasSubmitButton

    private val _alertMessage = MutableLiveData<String?>()
    val alertMessage: LiveData<String?> get() = _alertMessage

    private val _navigateToFormUuid = MutableLiveData<String?>()
    val navigateToFormUuid: LiveData<String?> get() = _navigateToFormUuid

    private val _formCompleted = MutableLiveData<Boolean>()
    val formCompleted: LiveData<Boolean> get() = _formCompleted

    private val _loadFailed = MutableLiveData<Boolean>()
    val loadFailed: LiveData<Boolean> get() = _loadFailed

    fun start(formType: FormType, indexCaseBenId: Long, contactBenId: Long?, contactType: String) {
        viewModelScope.launch {
            val definition = repository.getFormDefinition(formType)
            val activeVersion = definition?.versions?.firstOrNull { it.version.isActive }
                ?: definition?.versions?.maxByOrNull { it.version.versionNumber }
            if (activeVersion == null) {
                _loadFailed.value = true
                return@launch
            }
            sections = activeVersion.sections.sortedBy { it.section.sectionOrder }

            rawQuestionsByUuid = sections.flatMap { it.questions }
                .associateBy { it.question.questionUuid ?: it.question.questionId.toString() }
                .mapValues { it.value.question }

            conditionsByOptionId = sections.flatMap { it.questions }
                .flatMap { it.options }
                .associate { it.option.optionId to it.conditions }

            val contactResponse = repository.getOrCreateContactResponse(
                indexCaseBenId, contactBenId, contactType, activeVersion.version.versionId
            )
            responseId = contactResponse.responseId

            currentSectionIndex = 0
            loadSection(currentSectionIndex)
        }
    }

    /**
     * Reopens an existing contact record directly by responseId, bypassing
     * getOrCreateContactResponse — needed for Community/Occupational contacts, which have
     * no contactBenId to dedupe on (every getOrCreateContactResponse call with a null
     * contactBenId creates a fresh row by design, so resuming a draft must skip that path).
     */
    fun resume(formType: FormType, existingResponseId: Long) {
        viewModelScope.launch {
            val definition = repository.getFormDefinition(formType)
            val activeVersion = definition?.versions?.firstOrNull { it.version.isActive }
                ?: definition?.versions?.maxByOrNull { it.version.versionNumber }
            if (activeVersion == null) {
                _loadFailed.value = true
                return@launch
            }
            sections = activeVersion.sections.sortedBy { it.section.sectionOrder }

            rawQuestionsByUuid = sections.flatMap { it.questions }
                .associateBy { it.question.questionUuid ?: it.question.questionId.toString() }
                .mapValues { it.value.question }

            conditionsByOptionId = sections.flatMap { it.questions }
                .flatMap { it.options }
                .associate { it.option.optionId to it.conditions }

            responseId = existingResponseId
            currentSectionIndex = 0
            loadSection(currentSectionIndex)
        }
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
            val existing = repository.getCompleteResponse(responseId)
            populateAnswers(builtQuestions, sectionWithQuestions.section.sectionId, existing)
            questionsByUuid = questionsByUuid + builtQuestions.associateBy { it.questionUuid }
            evaluateAllConditions(builtQuestions)

            _activeQuestions.value = builtQuestions.filter { it.visible }
            _currentSectionName.value = sectionWithQuestions.section.sectionName
            _progress.value = (index + 1) to sections.size
            _hasSubmitButton.value = sectionWithQuestions.section.hasSubmitButton
        }
    }

    /** Called by the adapter's onValueChanged. Skipped for plain TEXT edits to avoid
     * re-laying out the list on every keystroke — mirrors Counselling's same guard. */
    fun onQuestionValueChanged(question: CounsellingQuestionDto, reevaluate: Boolean) {
        questionsByUuid = questionsByUuid + (question.questionUuid to question)
        if (!reevaluate) return
        val current = _activeQuestions.value ?: return
        val fullSection = sections.getOrNull(currentSectionIndex)?.questions
            ?.map { it.question.questionUuid ?: it.question.questionId.toString() }
            ?: return
        val allSectionQuestions = fullSection.mapNotNull { questionsByUuid[it] }
        evaluateAllConditions(allSectionQuestions)
        _activeQuestions.value = allSectionQuestions.filter { it.visible }
    }

    fun onNext() {
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
        val current = _activeQuestions.value ?: return
        if (!validateSection(current)) {
            _activeQuestions.value = current
            return
        }
        viewModelScope.launch {
            saveCurrentSection(current)
            repository.submitResponse(responseId, "SUBMITTED")
            _formCompleted.value = true
        }
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
        repository.saveSectionAnswers(responseId, section.section.sectionId, 1, answerRows)
    }

    private fun buildAnswerRows(questions: List<CounsellingQuestionDto>): List<ContactTracingQuestionResponseEntity> {
        return questions.filter { it.value != null }.flatMap { q ->
            when (val v = q.value) {
                is List<*> -> v.mapNotNull { optVal ->
                    q.options?.firstOrNull { it.optionValue == optVal }?.let { opt ->
                        ContactTracingQuestionResponseEntity(sectionResponseId = 0, questionId = q.questionId, optionId = opt.optionId)
                    }
                }
                else -> {
                    val matchedOption = q.options?.firstOrNull { it.optionValue == v?.toString() }
                    listOf(
                        ContactTracingQuestionResponseEntity(
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
        existing: ContactTracingCompleteResponse?
    ) {
        val sectionResponse = existing?.sectionResponses
            ?.firstOrNull { it.sectionResponse.sectionId == sectionId && it.sectionResponse.visitNumber == 1 }
        val answersByQuestionId = sectionResponse?.questionResponses?.groupBy { it.questionId } ?: emptyMap()
        questions.forEach { q ->
            val rows = answersByQuestionId[q.questionId] ?: return@forEach
            val isMultiSelect = q.questionType == "CHECKBOX" || q.questionType == "MCQ"
            q.value = if (isMultiSelect) {
                rows.mapNotNull { row -> q.options?.firstOrNull { it.optionId == row.optionId }?.optionValue }
            } else {
                val row = rows.first()
                row.optionId?.let { oid -> q.options?.firstOrNull { it.optionId == oid }?.optionValue } ?: row.answerText
            }
        }
    }

    private fun evaluateAllConditions(questions: List<CounsellingQuestionDto>) {
        questions.forEach { q ->
            val raw = rawQuestionsByUuid[q.questionUuid]
            q.visible = raw == null || parseRef(raw.enabledIfJson)?.let { matchesCondition(it) } ?: true
            parseRef(raw?.disabledIfJson)?.let { if (matchesCondition(it)) q.visible = false }
            q.isMandatory = parseRef(raw?.mandatoryIfJson)?.let { matchesCondition(it) } ?: (q.originalIsMandatory ?: q.isMandatory)
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

    private fun parseRef(json: String?): ContactTracingConditionRefDto? =
        json?.let { runCatching { gson.fromJson(it, ContactTracingConditionRefDto::class.java) }.getOrNull() }

    private fun matchesCondition(ref: ContactTracingConditionRefDto): Boolean {
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
            "SHOW_QUESTION", "ENABLE_QUESTION" -> target?.visible = true
            "DISABLE_QUESTION" -> target?.visible = false
            "MANDATORY" -> target?.isMandatory = true
            "SET_DEFAULT_VALUE" -> if (cond.actionValue != null) target?.value = cond.actionValue
            "GENERATE_ALERT" -> _alertMessage.value = cond.alertMessage
            "OPEN_FORM" -> _navigateToFormUuid.value = cond.targetFormUuid
            "REDIRECT_TO_FORM" -> _navigateToFormUuid.value = cond.targetFormUuid
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

    private fun validateQuestion(q: CounsellingQuestionDto): String? {
        val value = q.value
        val isEmpty = when (value) {
            null -> true
            is String -> value.isBlank()
            is List<*> -> value.isEmpty()
            else -> false
        }
        if (q.isMandatory && isEmpty) return "This field is required"
        if (isEmpty) return null

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
                        param.equals("TODAY", true) -> null // enforced by the date picker itself
                        param.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) -> null // fixed ISO date; enforced by the picker
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

private fun SectionQuestionWithDetails.toCounsellingQuestionDto(): CounsellingQuestionDto {
    val q = question
    return CounsellingQuestionDto(
        questionId = q.questionId,
        questionUuid = q.questionUuid ?: q.questionId.toString(),
        questionText = q.questionText,
        questionType = q.questionType,
        isMandatory = q.isRequired,
        displayOrder = q.questionOrder,
        maxLength = q.maxLength,
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
        },
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
