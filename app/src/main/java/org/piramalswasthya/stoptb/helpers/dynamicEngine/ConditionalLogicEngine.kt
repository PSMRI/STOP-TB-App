package org.piramalswasthya.stoptb.helpers.dynamicEngine

import org.piramalswasthya.stoptb.model.dynamicEntity.CompleteFormDefinition
import org.piramalswasthya.stoptb.model.dynamicEntity.QuestionResponseEntity

class ConditionalLogicEngine(
    private val formDefinition: CompleteFormDefinition
) {
    fun evaluate(
        currentAnswers: Map<Int, List<QuestionResponseEntity>>
    ): Map<Int, FieldVisibilityState> {
        val visibilityMap = mutableMapOf<Int, FieldVisibilityState>()
        val allQuestions = formDefinition.versions.find { it.version.isActive }?.sections?.flatMap { it.questions } ?: emptyList()

        // 1. Initialize visibility map with default schema configurations
        allQuestions.forEach { q ->
            visibilityMap[q.question.questionId] = FieldVisibilityState(
                visible = true,
                required = q.question.isRequired
            )
        }

        // 2. Evaluate option conditions based on current answers
        allQuestions.forEach { q ->
            q.options.forEach { opt ->
                val conditions = opt.conditions
                if (conditions.isNotEmpty()) {
                    val isSelected = currentAnswers[q.question.questionId]?.any { it.optionId == opt.option.optionId } == true
                    conditions.forEach { cond ->
                        val targetId = cond.targetQuestionId
                        val currentState = visibilityMap[targetId] ?: FieldVisibilityState(visible = true, required = false)

                        val isVisibleAction = cond.actionType == "VISIBLE" || cond.actionType == "SHOW"
                        val isMandatoryAction = cond.actionType == "MANDATORY" || cond.actionType == "SHOW_QUESTION"

                        if (isSelected) {
                            val newVisible = if (isVisibleAction) cond.isFulfilledValue else currentState.visible
                            val newRequired = if (isMandatoryAction) cond.isFulfilledValue else currentState.required
                            visibilityMap[targetId] = FieldVisibilityState(visible = newVisible, required = newRequired)
                        } else {
                            if (isVisibleAction && cond.isFulfilledValue) {
                                visibilityMap[targetId] = currentState.copy(visible = false)
                            }
                        }
                    }
                }
            }
        }
        return visibilityMap
    }
}

data class FieldVisibilityState(
    val visible: Boolean,
    val required: Boolean
)
