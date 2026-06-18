package org.piramalswasthya.stoptb.model.dynamicEntity

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

// API envelope: { "success": true, "message": null, "data": { ...form... } }
data class CounsellingApiResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("data") val data: CounsellingFormSchemaDto
)

data class CounsellingFormSchemaDto(
    @SerializedName("formId") val formId: Int,
    @SerializedName("formUuid") val formUuid: String? = null,
    @SerializedName("formName") val formName: String,
    @SerializedName("formType") val formType: String,
    @SerializedName("isActive") val isActive: Boolean,
    @SerializedName("versionNumber") val versionNumber: Int,
    @SerializedName("sections") val sections: List<CounsellingSectionDto>
) {
    companion object {
        fun fromJson(json: String): CounsellingFormSchemaDto =
            Gson().fromJson(json, CounsellingApiResponse::class.java).data
    }
}

data class CounsellingSectionDto(
    @SerializedName("sectionId") val sectionId: Int,
    @SerializedName("sectionUuid") val sectionUuid: String,
    @SerializedName("sectionName") val sectionName: String,
    @SerializedName("sectionPhase") val sectionPhase: String,
    @SerializedName("isRequired") val isRequired: Boolean,
    @SerializedName("displayOrder") val displayOrder: Int,
    @SerializedName("hasSubmitButton") val hasSubmitButton: Boolean,
    @SerializedName("questions") var questions: List<CounsellingQuestionDto>
)

data class CounsellingQuestionDto(
    @SerializedName("questionId") val questionId: Int,
    @SerializedName("questionUuid") val questionUuid: String,
    @SerializedName("questionText") val questionText: String,
    @SerializedName("questionType") val questionType: String, // TEXT, RADIO, MCQ, DATE
    @SerializedName("isMandatory") val isMandatory: Boolean,
    @SerializedName("displayOrder") val displayOrder: Int,
    @SerializedName("maxLength") val maxLength: Int? = null,
    @SerializedName("defaultValue") val defaultValue: String? = null,
    @SerializedName("containsPii") val containsPii: Boolean = false,
    @SerializedName("visibleByDefault") val visibleByDefault: Boolean = true,
    @SerializedName("validations") val validations: List<CounsellingValidationDto>? = null,
    @SerializedName("options") val options: List<CounsellingOptionDto>? = null,

    // Runtime UI state — not from JSON
    @Transient var value: Any? = null,
    @Transient var visible: Boolean = true,
    @Transient var errorMessage: String? = null
)

data class CounsellingValidationDto(
    @SerializedName("validationId") val validationId: Int? = null,
    @SerializedName("validationType") val validationType: String, // MAX_LENGTH, REGEX, MIN_DATE
    @SerializedName("validationParam") val validationParam: String,
    @SerializedName("errorMessage") val errorMessage: String
)

data class CounsellingOptionDto(
    @SerializedName("optionId") val optionId: Int,
    @SerializedName("optionLabel") val optionLabel: String,
    @SerializedName("optionValue") val optionValue: String,
    @SerializedName("displayOrder") val displayOrder: Int,
    @SerializedName("conditions") val conditions: List<CounsellingConditionDto>? = null
)

data class CounsellingConditionDto(
    @SerializedName("conditionId") val conditionId: Int? = null,
    // New backend uses "SHOW"; keep "LOCK_FORM" and "DISABLE_SECTION_VALIDATION" as known future values
    @SerializedName("actionType") val actionType: String,
    @SerializedName("targetQuestionId") val targetQuestionId: Int? = null,
    @SerializedName("targetSectionId") val targetSectionId: Int? = null,
    @SerializedName("targetQuestionUuid") val targetQuestionUuid: String? = null,
    @SerializedName("targetSectionUuid") val targetSectionUuid: String? = null
)
