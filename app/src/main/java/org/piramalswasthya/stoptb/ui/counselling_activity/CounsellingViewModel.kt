package org.piramalswasthya.stoptb.ui.counselling_activity

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.helpers.NetworkResponse
import org.piramalswasthya.stoptb.model.CounsellingOverviewData
import org.piramalswasthya.stoptb.model.dynamicEntity.CounsellingFormSchemaDto
import org.piramalswasthya.stoptb.model.dynamicEntity.CounsellingQuestionDto
import org.piramalswasthya.stoptb.model.dynamicEntity.CounsellingSectionDto
import org.piramalswasthya.stoptb.repositories.CounsellingRepo
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class CounsellingViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val counsellingRepo: CounsellingRepo
) : ViewModel() {

    companion object {
        const val EXTRA_BEN_ID = "extra_ben_id"
        const val EXTRA_OVERVIEW_DATA = "extra_overview_data"
    }

    val benId: Long = savedStateHandle.get<Long>(EXTRA_BEN_ID) ?: -1L

    private val _currentStep = MutableLiveData(0)
    val currentStep: LiveData<Int> get() = _currentStep

    private val _overview = MutableLiveData<NetworkResponse<CounsellingOverviewData>>(NetworkResponse.Idle())
    val overview: LiveData<NetworkResponse<CounsellingOverviewData>> get() = _overview

    private val _counsellingDate = MutableLiveData(System.currentTimeMillis())
    val counsellingDate: LiveData<Long> get() = _counsellingDate

    private val _formSchema = MutableLiveData<NetworkResponse<CounsellingFormSchemaDto>>(NetworkResponse.Idle())
    val formSchema: LiveData<NetworkResponse<CounsellingFormSchemaDto>> get() = _formSchema

    private val _activeQuestions = MutableLiveData<List<CounsellingQuestionDto>>()
    val activeQuestions: LiveData<List<CounsellingQuestionDto>> get() = _activeQuestions

    private val _formSubmitted = MutableLiveData<Boolean>()
    val formSubmitted: LiveData<Boolean> get() = _formSubmitted

    private val _saveError = MutableLiveData<String?>()
    val saveError: LiveData<String?> get() = _saveError

    private var lastRequestedPhase: SectionPhase = SectionPhase.PRE_SUBMIT

    var schemaData: CounsellingFormSchemaDto? = null
    private val disabledValidationSections = mutableSetOf<String>()

    init {
        loadOverview()
    }

    fun loadOverview() {
        viewModelScope.launch {
            _overview.value = NetworkResponse.Loading()
            _overview.value = counsellingRepo.getCounsellingOverview(benId)
        }
    }
    fun setCounsellingDate(dateInMillis: Long) {
        _counsellingDate.value = dateInMillis
    }

    fun loadFormSchema(phase: SectionPhase) {
        lastRequestedPhase = phase
        viewModelScope.launch {
            _formSchema.value = NetworkResponse.Loading()
            val response = counsellingRepo.getFormSchema(benId, phase)
            if (response is NetworkResponse.Success) {
                schemaData = response.data

                // Gson's unsafe deserializer bypasses Kotlin default values, so set runtime
                // visibility explicitly from the backend's visibleByDefault field.
                schemaData?.sections?.forEach { sec ->
                    sec.questions.forEach { q ->
                        q.visible = q.visibleByDefault
                        q.originalIsMandatory = q.isMandatory
                    }
                }

                // Check for last visited section in saved response
                val draft = counsellingRepo.getDraftResponse(benId)
                var startIndex = 0
                if (draft != null) {
                    val lastVisitedId = draft.formResponse.lastVisitedSectionId
                    if (lastVisitedId != null) {
                        val idx = schemaData?.sections?.indexOfFirst { it.sectionId == lastVisitedId } ?: -1
                        if (idx != -1) {
                            startIndex = idx
                        }
                    }
                }

                _formSchema.value = response
                loadSection(startIndex)
            } else {
                _formSchema.value = response
            }
        }
    }

    fun retryLoadFormSchema() {
        loadFormSchema(lastRequestedPhase)
    }

    fun resetSaveError() {
        _saveError.value = null
    }

    fun startCounselling() {
        loadFormSchema(SectionPhase.PRE_SUBMIT)
    }

    fun startFollowUp() {
        loadFormSchema(SectionPhase.POST_SUBMIT)
    }

    fun resetFormSubmitted() {
        _formSubmitted.value = false
    }

    fun loadSection(index: Int) {
        val section = schemaData?.sections?.getOrNull(index) ?: return
        _currentStep.value = index

        evaluateAllConditions(section)

        _activeQuestions.value = section.questions.toList()
    }

    fun evaluateConditions(q: CounsellingQuestionDto) {
        val activeSection =
            schemaData?.sections?.getOrNull(_currentStep.value ?: 0)
                ?: return

        val beforeStates = activeSection.questions.map {
            Triple(it.questionId, it.visible, it.isMandatory) to it.errorMessage
        }

        evaluateAllConditions(activeSection)

        // Re-evaluate validation state for visible questions to clear or update errors in real-time
        activeSection.questions.filter { it.visible }.forEach { activeQ ->
            val qError = validateQuestion(activeQ, activeSection)
            if (activeQ.errorMessage != qError) {
                if (activeQ.errorMessage != null || qError == null) {
                    activeQ.errorMessage = qError
                }
            }
        }

        val afterStates = activeSection.questions.map {
            Triple(it.questionId, it.visible, it.isMandatory) to it.errorMessage
        }

        if (beforeStates != afterStates) {
            _activeQuestions.value = activeSection.questions.toList()
        }
    }

    fun evaluateAllConditions(activeSection: CounsellingSectionDto) {
        // Ensure originalIsMandatory is initialized
        activeSection.questions.forEach { q ->
            if (q.originalIsMandatory == null) {
                q.originalIsMandatory = q.isMandatory
            }
        }

        // Initialize states
        disabledValidationSections.clear()
        activeSection.questions.forEach { q ->
            q.visible = q.visibleByDefault
            q.isMandatory = q.originalIsMandatory ?: false
        }

        var changed = true
        var passes = 0
        while (changed && passes < 10) {
            changed = false
            passes++

            for (q in activeSection.questions) {
                if (!q.visible) continue

                val selectedValues = when (val v = q.value) {
                    is List<*> -> v.filterIsInstance<String>()
                    is String -> listOf(v)
                    else -> emptyList()
                }

                q.options?.forEach { opt ->
                    val isSelected = selectedValues.contains(opt.optionValue)
                    if (isSelected) {
                        opt.conditions?.forEach { cond ->
                            val targetId = cond.targetQuestionId ?: return@forEach
                            val targetQ = activeSection.questions.find { it.questionId == targetId } ?: return@forEach

                            when (cond.actionType) {
                                "SHOW", "SHOW_QUESTION" -> {
                                    if (!targetQ.visible) {
                                        targetQ.visible = true
                                        changed = true
                                    }
                                    if (cond.actionType == "SHOW_QUESTION") {
                                        if (!targetQ.isMandatory) {
                                            targetQ.isMandatory = true
                                            changed = true
                                        }
                                    }
                                }
                                "MANDATORY" -> {
                                    if (!targetQ.isMandatory) {
                                        targetQ.isMandatory = true
                                        changed = true
                                    }
                                }
                                "DISABLE_SECTION_VALIDATION" -> {
                                    val targetCode = cond.targetSectionUuid ?: return@forEach
                                    if (!disabledValidationSections.contains(targetCode)) {
                                        disabledValidationSections.add(targetCode)
                                        changed = true
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Post-evaluation cleanup for hidden questions
        activeSection.questions.forEach { q ->
            if (!q.visible) {
                q.value = null
                q.errorMessage = null
                q.isMandatory = q.originalIsMandatory ?: false
            }
        }
    }

    private fun getMandatoryError(q: CounsellingQuestionDto, section: CounsellingSectionDto): String? {
        if (q.isMandatory) return "This field is required"
        val mandatoryIf = q.validations?.firstOrNull { it.validationType == "MANDATORY_IF" } ?: return null
        val parts = mandatoryIf.validationParam.split("=")
        if (parts.size != 2) return null
        val refQuestion = section.questions.find { it.questionUuid == parts[0] } ?: return null
        return if (refQuestion.value?.toString() == parts[1]) mandatoryIf.errorMessage else null
    }

    private fun validateQuestion(q: CounsellingQuestionDto, section: CounsellingSectionDto): String? {
        val isEmpty = q.value == null
                || q.value.toString().isBlank()
                || (q.value as? List<*>)?.isEmpty() == true

        var qError: String? = null

        if (isEmpty) {
            qError = getMandatoryError(q, section)
        } else {
            q.validations?.forEach { valDto ->
                if (qError == null) {
                    when (valDto.validationType) {
                        "MAX_LENGTH" -> {
                            val maxLen = valDto.validationParam.toIntOrNull()
                            if (maxLen != null && q.value.toString().length > maxLen) {
                                qError = valDto.errorMessage
                            }
                        }
                        "REGEX" -> {
                            val regexStr = valDto.validationParam
                            try {
                                val regex = regexStr.toRegex()
                                if (!regex.matches(q.value.toString())) {
                                    qError = valDto.errorMessage
                                }
                            } catch (e: Exception) {
                                // Ignore invalid regex pattern
                            }
                        }
                        "MIN_DATE", "MAX_DATE" -> {
                            val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH)
                            try {
                                val dateVal = sdf.parse(q.value.toString())
                                if (dateVal != null) {
                                    val param = valDto.validationParam
                                    val targetDate: java.util.Date? = if (param.equals("TODAY", ignoreCase = true)) {
                                        Calendar.getInstance().apply {
                                            set(Calendar.HOUR_OF_DAY, 0)
                                            set(Calendar.MINUTE, 0)
                                            set(Calendar.SECOND, 0)
                                            set(Calendar.MILLISECOND, 0)
                                        }.time
                                    } else {
                                        SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(param)
                                    }
                                    if (targetDate != null) {
                                        if (valDto.validationType == "MIN_DATE") {
                                            if (dateVal.before(targetDate)) {
                                                qError = valDto.errorMessage
                                            }
                                        } else {
                                            if (dateVal.after(targetDate)) {
                                                qError = valDto.errorMessage
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                // ignore date parse issues
                            }
                        }
                    }
                }
            }
        }
        return qError
    }

    fun validateCurrentSection(): Boolean {
        val activeSection = schemaData?.sections?.getOrNull(_currentStep.value ?: 0) ?: return true
        if (disabledValidationSections.contains(activeSection.sectionUuid)) return true

        var isValid = true
        for (q in activeSection.questions.filter { it.visible }) {
            val qError = validateQuestion(q, activeSection)
            if (qError != null) {
                q.errorMessage = qError
                isValid = false
            } else {
                q.errorMessage = null
            }
        }

        if (!isValid) {
            _activeQuestions.value = activeSection.questions.toList()
        }
        return isValid
    }

    fun nextSection() {
        if (!validateCurrentSection()) return
        val current = _currentStep.value ?: 0
        val section = schemaData?.sections?.getOrNull(current) ?: return
        val formId = schemaData?.formId ?: 2
        val versionNumber = schemaData?.versionNumber ?: 1

        viewModelScope.launch {
            val success = counsellingRepo.saveSectionAnswers(benId, formId, section, versionNumber)
            if (success) {
                if (current < (schemaData?.sections?.size ?: 1) - 1) {
                    loadSection(current + 1)
                } else {
                    _formSubmitted.value = true
                }
            } else {
                _saveError.value = "Failed to save section answers. Please try again."
            }
        }
    }

    fun previousSection() {
        val current = _currentStep.value ?: 0
        if (current > 0) {
            val section = schemaData?.sections?.getOrNull(current) ?: return
            val formId = schemaData?.formId ?: 2
            val versionNumber = schemaData?.versionNumber ?: 1
            val previousSectionId = schemaData?.sections?.getOrNull(current - 1)?.sectionId
            viewModelScope.launch {
                counsellingRepo.saveSectionAnswers(
                    benId, formId, section, versionNumber,
                    overrideTargetSectionId = previousSectionId
                )
                loadSection(current - 1)
            }
        }
    }
}