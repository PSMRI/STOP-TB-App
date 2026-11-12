package org.piramalswasthya.stoptb.helpers.dynamicMapper

import org.piramalswasthya.stoptb.model.dynamicEntity.CounsellingOptionDto
import org.piramalswasthya.stoptb.model.dynamicEntity.CounsellingQuestionDto
import org.piramalswasthya.stoptb.model.dynamicEntity.SavedAnswerByUuid
import org.piramalswasthya.stoptb.ui.counselling_activity.QuestionType

/**
 * Carries answers saved against one form version over to the questions of another (usually the
 * current) version. Matching is by questionUuid only — server questionIds change between versions —
 * and options are matched by optionUuid (stable even if optionValue is renamed, e.g. "YES" -> "TRUE"),
 * so this works for any upgrade (V1→V2, V2→V3, V1→V3, ...):
 * - questionUuid present in the target version -> value carried over
 * - questionUuid absent from the target version (field removed) -> dropped
 * - target question with no saved answer (field added) -> left empty
 * - saved option no longer offered by the target question -> left empty
 * optionValue is only a fallback for option rows stored before optionUuid was synced.
 */
object FormVersionAnswerMapper {
//If the entire form is unlocked, historical valid data can be accidentally overwritten.

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

    /** questionUuids among [questions] whose saved answer carries over in full: an answer exists and,
     * for choice questions, every selected option is still offered (a multi-select with any removed
     * option doesn't count). */
    fun compatibleAnswerUuids(
        questions: List<CounsellingQuestionDto>,
        answersByUuid: Map<String, List<SavedAnswerByUuid>>
    ): Set<String> = questions.filter { q ->
        val rows = answersByUuid[q.questionUuid] ?: return@filter false
        when (val value = resolveValue(q, rows)) {
            null -> false
            is List<*> -> value.size == rows.mapNotNull { it.optionUuid ?: it.optionValue ?: it.answerText }.distinct().size
            else -> value.toString().isNotBlank()
        }
    }.map { it.questionUuid }.toSet()

    /** questionUuids that were answered but no longer exist in [targetUuids] (removed fields). */
    fun removedAnsweredUuids(
        answersByUuid: Map<String, List<SavedAnswerByUuid>>,
        targetUuids: Set<String>
    ): Set<String> = answersByUuid.keys - targetUuids

    fun resolveValue(question: CounsellingQuestionDto, rows: List<SavedAnswerByUuid>): Any? {
        if (rows.isEmpty()) return null
        val options = question.options.orEmpty()
        val isMultiSelect = question.questionType == QuestionType.CHECKBOX_MULTI.value ||
            question.questionType == QuestionType.DROPDOWN_MULTI.value

        return when {
            isMultiSelect -> rows.mapNotNull { targetOptionValue(options, it) }
                .distinct()
                .takeIf { it.isNotEmpty() }

            options.isNotEmpty() -> targetOptionValue(options, rows.first())

            else -> rows.first().let { it.answerText ?: it.optionValue }
        }
    }

    /** The target question's optionValue for a saved answer row, or null if that option was removed. */
    private fun targetOptionValue(options: List<CounsellingOptionDto>, row: SavedAnswerByUuid): String? {
        val uuid = row.optionUuid
        if (uuid != null && options.any { it.optionUuid != null }) {
            return options.firstOrNull { it.optionUuid == uuid }?.optionValue
        }
        return (row.optionValue ?: row.answerText)?.takeIf { v -> options.any { it.optionValue == v } }
    }
}
