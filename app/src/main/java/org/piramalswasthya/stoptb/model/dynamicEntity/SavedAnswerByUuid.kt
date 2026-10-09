package org.piramalswasthya.stoptb.model.dynamicEntity

// One stored answer row resolved to its question's stable questionUuid (and the selected option's
// optionValue), so it can be matched against any other version of the same form.
data class SavedAnswerByUuid(
    val questionUuid: String?,
    val questionId: Int,
    val optionId: Int?,
    val optionValue: String?,
    val answerText: String?
)
