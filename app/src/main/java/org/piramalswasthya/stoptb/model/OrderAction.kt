package org.piramalswasthya.stoptb.model

enum class OrderAction {
    REFER,
    COMPLETE,
    NOT_CONDUCTED,
    REORDER,
    COMPLETE_RIF,
    POLL,
    RETRY_POLL,
    RETRY_PUSH,
    CHECK_PUSH_ACTIVE,
    RETRY_RIF_POLL,
    VIEW,
    VIEW_RIF,
    ENTER_LC,
    VIEW_LC,
    NONE,

    NO_RESULT;

    companion object {
        fun fromName(value: String?): OrderAction? = entries.firstOrNull { it.name == value }
    }
}
