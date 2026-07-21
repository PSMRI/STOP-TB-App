package org.piramalswasthya.stoptb.model.contactTracing

import com.google.gson.annotations.SerializedName

data class ContactTracingApiResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: List<ContactTracingFormDto> = emptyList()
)

data class ContactTracingFormDto(
    @SerializedName("formId") val formId: Int,
    @SerializedName("formUuid") val formUuid: String,
    @SerializedName("formName") val formName: String,
    @SerializedName("formType") val formType: String,
    @SerializedName("isActive") val isActive: Boolean = true,
    @SerializedName("followUpDelayDays") val followUpDelayDays: Int = 0,
    @SerializedName("versionNumber") val versionNumber: Int = 1,
    @SerializedName("definition") val definition: String? = null,
    @SerializedName("triggerRule") val triggerRule: ContactTracingTriggerRuleDto? = null,
    @SerializedName("globalRule") val globalRule: ContactTracingGlobalRuleDto? = null,
    @SerializedName("enabledIf") val enabledIf: ContactTracingFormEnabledIfDto? = null,
    @SerializedName("sections") val sections: List<ContactTracingSectionDto> = emptyList()
)

data class ContactTracingTriggerRuleDto(
    @SerializedName("description") val description: String? = null,
    @SerializedName("triggerCondition") val triggerCondition: String? = null
)

data class ContactTracingGlobalRuleDto(
    @SerializedName("description") val description: String? = null,
    @SerializedName("actionType") val actionType: String? = null,
    @SerializedName("targetFormUuid") val targetFormUuid: String? = null,
    @SerializedName("bypassFlow") val bypassFlow: String? = null
)

data class ContactTracingFormEnabledIfDto(
    @SerializedName("sourceFormUuid") val sourceFormUuid: String? = null,
    @SerializedName("questionUuid") val questionUuid: String? = null,
    @SerializedName("containsValue") val containsValue: String? = null
)

data class ContactTracingSectionDto(
    @SerializedName("sectionId") val sectionId: Int,
    @SerializedName("sectionUuid") val sectionUuid: String,
    @SerializedName("sectionName") val sectionName: String,
    @SerializedName("sectionNameHindi") val sectionNameHindi: String? = null,
    @SerializedName("sectionPhase") val sectionPhase: String,
    @SerializedName("isRequired") val isRequired: Boolean = false,
    @SerializedName("displayOrder") val displayOrder: Int = 0,
    @SerializedName("hasSubmitButton") val hasSubmitButton: Boolean = false,
    @SerializedName("questions") val questions: List<ContactTracingQuestionDto> = emptyList()
)

data class ContactTracingQuestionDto(
    @SerializedName("questionId") val questionId: Int,
    @SerializedName("questionUuid") val questionUuid: String,
    @SerializedName("questionText") val questionText: String,
    @SerializedName("questionTextHindi") val questionTextHindi: String? = null,
    @SerializedName("questionType") val questionType: String,
    @SerializedName("allowMultiple") val allowMultiple: Boolean = false,
    @SerializedName("isMandatory") val isMandatory: Boolean = false,
    @SerializedName("displayOrder") val displayOrder: Int = 0,
    @SerializedName("maxLength") val maxLength: Int? = null,
    @SerializedName("defaultValue") val defaultValue: String? = null,
    @SerializedName("containsPii") val containsPii: Boolean = false,
    @SerializedName("visibleByDefault") val visibleByDefault: Boolean = true,
    @SerializedName("enabledIf") val enabledIf: ContactTracingConditionRefDto? = null,
    @SerializedName("disabledIf") val disabledIf: ContactTracingConditionRefDto? = null,
    @SerializedName("mandatoryIf") val mandatoryIf: ContactTracingConditionRefDto? = null,
    @SerializedName("autoPopulated") val autoPopulated: Boolean = false,
    @SerializedName("autoPopulateLogic") val autoPopulateLogic: String? = null,
    @SerializedName("autoPopulateNote") val autoPopulateNote: String? = null,
    @SerializedName("unit") val unit: String? = null,
    @SerializedName("exampleValues") val exampleValues: List<String>? = null,
    @SerializedName("note") val note: String? = null,
    @SerializedName("displayFormat") val displayFormat: String? = null,
    @SerializedName("options") val options: List<ContactTracingOptionDto> = emptyList(),
    @SerializedName("validations") val validations: List<ContactTracingValidationDto> = emptyList()
)

data class ContactTracingConditionRefDto(
    @SerializedName("questionUuid") val questionUuid: String,
    @SerializedName("equals") val equalsValue: String? = null,
    @SerializedName("inValues") val inValues: List<String>? = null,
    @SerializedName("containsValue") val containsValue: String? = null,
    @SerializedName("isNotEmpty") val isNotEmpty: Boolean? = null
)

data class ContactTracingOptionDto(
    @SerializedName("optionId") val optionId: Int,
    @SerializedName("optionLabel") val optionLabel: String,
    @SerializedName("optionLabelHindi") val optionLabelHindi: String? = null,
    @SerializedName("optionValue") val optionValue: String,
    @SerializedName("optionValueHindi") val optionValueHindi: String? = null,
    @SerializedName("displayOrder") val displayOrder: Int = 0,
    @SerializedName("isExclusive") val isExclusive: Boolean = false,
    @SerializedName("conditions") val conditions: List<ContactTracingOptionConditionDto> = emptyList()
)

data class ContactTracingOptionConditionDto(
    @SerializedName("conditionId") val conditionId: Int = 0,
    @SerializedName("actionType") val actionType: String,
    @SerializedName("targetQuestionId") val targetQuestionId: Int? = null,
    @SerializedName("targetQuestionUuid") val targetQuestionUuid: String? = null,
    @SerializedName("targetSectionId") val targetSectionId: Int? = null,
    @SerializedName("targetSectionUuid") val targetSectionUuid: String? = null,
    @SerializedName("targetFormUuid") val targetFormUuid: String? = null,
    @SerializedName("alertMessage") val alertMessage: String? = null,
    @SerializedName("targetList") val targetList: String? = null,
    @SerializedName("value") val value: String? = null,
    @SerializedName("note") val note: String? = null,
    @SerializedName("reEnableCondition") val reEnableCondition: String? = null
)

data class ContactTracingValidationDto(
    @SerializedName("validationId") val validationId: Int? = null,
    @SerializedName("validationType") val validationType: String,
    @SerializedName("validationParam") val validationParam: String? = null,
    @SerializedName("errorMessage") val errorMessage: String? = null
)
