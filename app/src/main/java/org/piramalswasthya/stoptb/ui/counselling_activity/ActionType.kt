package org.piramalswasthya.stoptb.ui.counselling_activity

enum class ActionType(val value: String) {

    SHOW("SHOW"),
    VISIBLE("VISIBLE"),

    SHOW_QUESTION("SHOW_QUESTION"),
    ENABLE_QUESTION("ENABLE_QUESTION"),
    DISABLE_QUESTION("DISABLE_QUESTION"),
    MANDATORY("MANDATORY"),
    MANDATORY_IF("MANDATORY_IF"),
    SET_DEFAULT_VALUE("SET_DEFAULT_VALUE"),
    GENERATE_ALERT("GENERATE_ALERT"),
    OPEN_FORM("OPEN_FORM"),
    REDIRECT_TO_FORM("REDIRECT_TO_FORM"),
    DISABLE_SECTION_VALIDATION("DISABLE_SECTION_VALIDATION"),


    // Reserved / Future actions
    MOVE_CARD_TO_LIST("MOVE_CARD_TO_LIST"),
    SKIP_TO_SUBMIT("SKIP_TO_SUBMIT"),
    GO_TO_SUBMIT("GO_TO_SUBMIT"),
    SHOW_DIRECT_SUBMIT_OPTION("SHOW_DIRECT_SUBMIT_OPTION"),
    DISABLE_ALL_TPT_FIELDS("DISABLE_ALL_TPT_FIELDS");

    companion object {
        fun from(value: String?): ActionType? =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
    }
}