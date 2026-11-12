package org.piramalswasthya.stoptb.ui.counselling_activity

enum class QuestionType(val value: String) {
    RADIO("RADIO"),
    DROPDOWN("DROPDOWN"),
    DROPDOWN_MULTI("DROPDOWN_MULTI"),
    CHECKBOX("CHECKBOX"),
    CHECKBOX_MULTI("CHECKBOX_MULTI"),
    MCQ("MCQ"),
    NUMBER("NUMBER"),
    DATE("DATE"),
    TEXT("TEXT"),
    READONLY_NUMBER("READONLY_NUMBER"),
    READONLY_TEXT("READONLY_TEXT"),
    NUMBER_PICKER("NUMBER_PICKER"),
    // Read-only value filled by the app (e.g. Contact Follow Up's camp X-ray / NAAT result), saved as text.
    AUTO_FILL("AUTO_FILL");

    companion object {
        fun from(value: String?): QuestionType? =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
    }
}