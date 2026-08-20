package org.piramalswasthya.stoptb.helpers.dynamicEngine

import org.piramalswasthya.stoptb.model.dynamicEntity.QuestionValidationEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.QuestionResponseEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.SectionQuestionWithDetails

class ValidationEngine {
    fun validate(
        question: SectionQuestionWithDetails,
        answers: List<QuestionResponseEntity>
    ): ValidationResult {
        if (question.question.questionType == "DISPLAY") return ValidationResult.Success

        val required = question.question.isRequired
        if (required && answers.isEmpty()) {
            return ValidationResult.Failure("This field is required.")
        }

        val validations = question.validations
        validations.forEach { validation ->
            val valueStr = answers.firstOrNull()?.answerText
            when (validation.validationType) {
                "MIN" -> {
                    val num = valueStr?.toFloatOrNull()
                    val minLimit = validation.validationValue?.toFloatOrNull()
                    if (num != null && minLimit != null && num < minLimit) {
                        return ValidationResult.Failure(validation.errorMessage)
                    }
                }
                "MAX" -> {
                    val num = valueStr?.toFloatOrNull()
                    val maxLimit = validation.validationValue?.toFloatOrNull()
                    if (num != null && maxLimit != null && num > maxLimit) {
                        return ValidationResult.Failure(validation.errorMessage)
                    }
                }
                "REGEX" -> {
                    try {
                        val regex = validation.validationValue?.toRegex()
                        if (valueStr != null && regex != null && !regex.matches(valueStr)) {
                            return ValidationResult.Failure(validation.errorMessage)
                        }
                    } catch (e: java.util.regex.PatternSyntaxException) {
                        return ValidationResult.Failure("Invalid pattern configured: ${validation.errorMessage}")
                    }
                }
                "MAX_LENGTH" -> {
                    val lengthLimit = validation.validationValue?.toIntOrNull()
                    if (valueStr != null && lengthLimit != null && valueStr.length > lengthLimit) {
                        return ValidationResult.Failure(validation.errorMessage)
                    }
                }
            }
        }
        return ValidationResult.Success
    }
}

sealed class ValidationResult {
    object Success : ValidationResult()
    data class Failure(val message: String) : ValidationResult()
}
