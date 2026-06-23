package org.piramalswasthya.stoptb.model.dynamicEntity

import com.google.gson.annotations.SerializedName

data class FormSchemaDto(
    @SerializedName("formId")
    val formId: String,

    @SerializedName("formUuid")
    val formUuid: String? = null,

    @SerializedName("formName")
    val formName: String,

    @SerializedName("formType")
    val formType: String? = null,

    @SerializedName("isActive")
    val isActive: Boolean = true,

    @SerializedName("version", alternate = ["versionNumber"])
    val version: Int = 1,

    @SerializedName("sections")
    val sections: List<FormSectionDto> = emptyList(),

    @SerializedName("followUpDelayDays")
    val followUpDelayDays: Int = 0
) {
    val versionNumber: Int
        get() = version

    companion object {
        fun fromJson(json: String): FormSchemaDto = com.google.gson.Gson().fromJson(json, FormSchemaDto::class.java)
    }

    fun toJson(): String = com.google.gson.Gson().toJson(this)
}

data class FormSectionDto(
    @SerializedName("sectionId")
    val sectionId: String = "",

    @SerializedName("sectionUuid")
    val sectionUuid: String? = null,

    @SerializedName("sectionTitle", alternate = ["sectionName"])
    val sectionTitle: String = "",

    @SerializedName("sectionPhase")
    val sectionPhase: String? = null,

    @SerializedName("isRequired")
    val isRequired: Boolean? = null,

    @SerializedName("displayOrder")
    val displayOrder: Int? = null,

    @SerializedName("hasSubmitButton")
    val hasSubmitButton: Boolean? = null,

    @SerializedName("fields", alternate = ["questions"])
    val fields: List<FormFieldDto> = emptyList()
) {
    val sectionName: String
        get() = sectionTitle

    val questions: List<FormFieldDto>
        get() = fields
}

data class FormFieldDto(
    @SerializedName("fieldId", alternate = ["questionUuid"])
    val fieldId: String = "",

    @SerializedName("questionId")
    val questionId: Int? = null,

    @SerializedName("label", alternate = ["questionText"])
    val label: String = "",

    @SerializedName("type", alternate = ["questionType"])
    val type: String = "",

    @SerializedName("options")
    val options: List<Any>? = null,

    @SerializedName("isRequired", alternate = ["isMandatory"])
    val required: Boolean = false,

    @SerializedName("conditional")
    val conditional: ConditionalLogic? = null,

    @SerializedName("validation")
    val validation: FieldValidationDto? = null,

    @SerializedName("validations")
    val validations: List<ValidationItemDto> = emptyList(),

    @SerializedName("placeholder")
    val placeholder: String? = null,

    @SerializedName("defaultValue")
    val defaultValue: String? = null,

    @SerializedName("default")
    val default: Any? = null,

    @SerializedName("value")
    var value: Any? = null,

    @SerializedName("displayOrder")
    val displayOrder: Int? = null,

    @Transient var visible: Boolean = true,
    @Transient var errorMessage: String? = null,
    @Transient var isEditable: Boolean = true
) {
    val isMandatory: Boolean
        get() = required

    fun getOptionStrings(): List<String> {
        val list = options ?: return emptyList()
        return list.map {
            when (it) {
                is String -> it
                is Map<*, *> -> (it["optionLabel"] ?: it["label"] ?: it["optionValue"] ?: it["value"] ?: "").toString()
                else -> it.toString()
            }
        }
    }

    fun getOptionItems(): List<OptionItemDto> {
        val list = options ?: return emptyList()
        return list.mapIndexed { index, it ->
            when (it) {
                is Map<*, *> -> {
                    val conds = (it["conditions"] as? List<*>)?.mapNotNull { condMap ->
                        if (condMap is Map<*, *>) {
                            ConditionDto(
                                conditionId = (condMap["conditionId"] as? Number)?.toInt() ?: 0,
                                actionType = (condMap["actionType"] ?: "").toString(),
                                targetQuestionId = (condMap["targetQuestionId"] as? Number)?.toInt(),
                                targetSectionId = (condMap["targetSectionId"] as? Number)?.toInt(),
                                targetQuestionUuid = condMap["targetQuestionUuid"]?.toString(),
                                targetSectionUuid = condMap["targetSectionUuid"]?.toString()
                            )
                        } else null
                    } ?: emptyList()

                    val labelFallback = it["optionLabel"] ?: it["label"] ?: ""
                    val valueFallback = it["optionValue"] ?: it["value"] ?: ""
                    OptionItemDto(
                        optionId = (it["optionId"] as? Number)?.toInt() ?: (index + 1),
                        optionLabel = labelFallback.toString(),
                        optionValue = valueFallback.toString(),
                        displayOrder = (it["displayOrder"] as? Number)?.toInt() ?: (index + 1),
                        conditions = conds
                    )
                }
                else -> {
                    OptionItemDto(
                        optionId = index + 1,
                        optionLabel = it.toString(),
                        optionValue = it.toString(),
                        displayOrder = index + 1,
                        conditions = emptyList()
                    )
                }
            }
        }
    }

    fun getMergedValidation(): FieldValidationDto? {
        if (validation != null) return validation
        if (validations.isEmpty()) return null

        var min: Float? = null
        var max: Float? = null
        var maxLengthVal: Int? = null
        var regexVal: String? = null
        var errMsg: String? = null

        validations.forEach {
            errMsg = it.errorMessage
            when (it.validationType) {
                "MIN" -> min = it.validationParam?.toFloatOrNull()
                "MAX" -> max = it.validationParam?.toFloatOrNull()
                "MAX_LENGTH" -> maxLengthVal = it.validationParam?.toIntOrNull()
                "REGEX" -> regexVal = it.validationParam
            }
        }
        return FieldValidationDto(
            min = min,
            max = max,
            maxLength = maxLengthVal,
            regex = regexVal,
            errorMessage = errMsg ?: ""
        )
    }
}

data class ConditionalLogic(
    @SerializedName("dependsOn")
    val dependsOn: String? = null,

    @SerializedName("expectedValue")
    val expectedValue: String? = null
)

data class FieldValidationDto(
    val min: Float? = null,
    val max: Float? = null,
    val minDate: String? = null,
    val maxDate: String? = null,
    val maxLength: Int? = null,
    val regex: String? = null,
    val errorMessage: String? = null,
    val decimalPlaces: Int? = null,
    val maxSizeMB: Int? = null,
    val afterField: String? = null,
    val beforeField: String? = null,
    val incremental: Boolean? = null
)

data class OptionItemDto(
    val optionId: Int,
    val optionLabel: String,
    val optionValue: String,
    val displayOrder: Int,
    val conditions: List<ConditionDto>
)

data class ConditionDto(
    val conditionId: Int,
    val actionType: String,
    val targetQuestionId: Int?,
    val targetSectionId: Int?,
    val targetQuestionUuid: String?,
    val targetSectionUuid: String?
)

data class ValidationItemDto(
    val validationId: Int,
    val validationType: String,
    val validationParam: String?,
    val errorMessage: String
)

data class OptionItem(
    val label: String = "",
    val value: String = ""
)

