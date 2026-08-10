package org.piramalswasthya.stoptb.model

enum class OrderStatus {
    PENDING,
    AWAITING_PROVIDER_RESULT,
    COMPLETED,
    REFUSED,
    FAILED,
    POLLING_TIMEOUT,
    NONE,
    CREATED,
    AWAITING_TEST_COMPLETION
}
