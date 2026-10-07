package org.piramalswasthya.stoptb.helpers.dynamicMapper

import org.piramalswasthya.stoptb.model.dynamicEntity.CounsellingQuestionDto
import org.piramalswasthya.stoptb.model.dynamicEntity.SavedAnswerByUuid
import org.piramalswasthya.stoptb.ui.counselling_activity.QuestionType

/**
 * Carries answers saved against one form version over to the questions of another (usually the
 * current) version. Matching is by questionUuid only — server questionIds change between versions —
 * and options are matched by optionValue, so this works for any upgrade (V1→V2, V2→V3, V1→V3, ...):
 * - questionUuid present in the target version -> value carried over
 * - questionUuid absent from the target version (field removed) -> dropped
 * - target question with no saved answer (field added) -> left empty
 * - saved option value no longer offered by the target question -> left empty
 */
object FormVersionAnswerMapper {

    fun groupByUuid(answers: List<SavedAnswerByUuid>): Map<String, List<SavedAnswerByUuid>> =
        answers.filter { !it.questionUuid.isNullOrEmpty() }.groupBy { it.questionUuid!! }

    /** Sets [CounsellingQuestionDto.value] on each question that has a carried-over answer;
     * returns the questionUuids that received a value. */
    fun applyAnswers(
        questions: List<CounsellingQuestionDto>,
        answersByUuid: Map<String, List<SavedAnswerByUuid>>
    ): Set<String> {
        val populated = mutableSetOf<String>()
        questions.forEach { q ->
            val rows = answersByUuid[q.questionUuid] ?: return@forEach
            val value = resolveValue(q, rows) ?: return@forEach
            q.value = value
            populated += q.questionUuid
        }
        return populated
    }

    /** questionUuids that were answered but no longer exist in [targetUuids] (removed fields). */
    fun removedAnsweredUuids(
        answersByUuid: Map<String, List<SavedAnswerByUuid>>,
        targetUuids: Set<String>
    ): Set<String> = answersByUuid.keys - targetUuids

    fun resolveValue(question: CounsellingQuestionDto, rows: List<SavedAnswerByUuid>): Any? {
        if (rows.isEmpty()) return null
        val optionValues = question.options.orEmpty().map { it.optionValue }.toSet()
        val isMultiSelect = question.questionType == QuestionType.CHECKBOX_MULTI.value ||
            question.questionType == QuestionType.DROPDOWN_MULTI.value

        return when {
            isMultiSelect -> rows.mapNotNull { it.optionValue ?: it.answerText }
                .filter { it in optionValues }
                .distinct()
                .takeIf { it.isNotEmpty() }

            optionValues.isNotEmpty() -> rows.first().let { it.optionValue ?: it.answerText }
                ?.takeIf { it in optionValues }

            else -> rows.first().let { it.answerText ?: it.optionValue }
        }
    }
}
