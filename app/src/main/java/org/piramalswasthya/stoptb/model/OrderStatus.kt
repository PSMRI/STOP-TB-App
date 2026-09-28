package org.piramalswasthya.stoptb.model

// Confirmed real backend contract for `order/result`'s `status` field: exactly PENDING,
// IN_PROGRESS, COMPLETED, FAILED, CLOSED, MANUAL_ENTRY — used identically for Chest X-Ray,
// TrueNat/MTB, and RIF. IN_PROGRESS has no distinct UI treatment from PENDING, so it's not
// given its own stored value — `TBRepo.reducedOrderStatus()` collapses it into PENDING at the
// repository boundary, meaning nothing outside that one function needs to know IN_PROGRESS
// exists at all. A beneficiary declining a referral before any order exists is also stored as
// CLOSED (with the decline reason in reasonForDenialX) rather than a separate status — CLOSED
// is the one lifecycle-terminal state, whatever the reason (declined, not conducted, expired),
// and "Create New Order" is always the recovery action for it.
enum class OrderStatus {
    PENDING,
    COMPLETED,
    FAILED,
    CLOSED,
    MANUAL_ENTRY
}

/**
 * Standardized Chest X-Ray result values used by `order/result` and `order/manualResult`.
 *
 * NOTE: [wireValue] is a placeholder pending backend confirmation of the exact casing/format
 * the API will send/expect — kept as a named constant here so a later change is a one-line
 * edit rather than a re-plumb. [fromResultText] matches on the free-text English phrases the
 * backend/dataset already use today (e.g. "TB Presumptive"), which is how existing
 * `resultSummary` values and the `tb_digital_xray_result` dropdown are represented locally.
 */
enum class ChestXrayResult(val wireValue: String, val displayValue: String) {
    NORMAL("NORMAL", "Normal"),
    ABNORMAL_NOT_PRESUMPTIVE("ABNORMAL_NOT_PRESUMPTIVE", "Abnormal but not TB Presumptive"),
    TB_PRESUMPTIVE("TB_PRESUMPTIVE", "TB Presumptive"),
    AI_INVALID("AI_INVALID", "AI Invalid Result");

    /** True for the two results that should trigger the automatic SPUTUM_TRUENAT referral
     *  cascade — both non-Normal, non-AI-Invalid results now trigger it (previously only an
     *  exact "TB Presumptive" match did). */
    val triggersTrueNatReferral: Boolean
        get() = this == TB_PRESUMPTIVE || this == ABNORMAL_NOT_PRESUMPTIVE

    companion object {
        fun fromResultText(value: String?): ChestXrayResult? {
            if (value.isNullOrBlank()) return null
            return when (value.trim().lowercase()) {
                "tb presumptive", "positive" -> TB_PRESUMPTIVE
                "abnormal but not tb presumptive", "abnormal (non-tb presumptive)", "abnormal" -> ABNORMAL_NOT_PRESUMPTIVE
                "normal", "negative" -> NORMAL
                "ai invalid result", "ai invalid", "invalid" -> AI_INVALID
                else -> null
            }
        }
    }
}

/**
 * Standardized TrueNat/MTB result values used by `order/result` and `order/manualResult` for the
 * `SPUTUM_TRUENAT`/`MTB` order type.
 *
 * NOTE: [wireValue] is a placeholder pending backend confirmation of the exact casing/format the
 * API will send/expect — same treatment as [ChestXrayResult.wireValue]. [fromResultText] matches
 * on the free-text phrases already used locally today (e.g. "MTB detected", the manual-entry
 * "TB Positive"/"TB Negative" values from `TBSuspectedQuickDataset`).
 */
enum class MtbResult(val wireValue: String, val displayValue: String) {
    TB_POSITIVE("TB_POSITIVE", "TB Positive"),
    TB_NEGATIVE("TB_NEGATIVE", "TB Negative"),
    INVALID_ERROR("INVALID_ERROR", "Invalid/Error");

    companion object {
        fun fromResultText(value: String?): MtbResult? {
            if (value.isNullOrBlank()) return null
            return when (value.trim().lowercase()) {
                "tb positive", "mtb detected", "mtb positive", "positive", "detected" -> TB_POSITIVE
                "tb negative", "mtb not detected", "mtb negative", "negative", "not detected" -> TB_NEGATIVE
                "invalid/error", "invalid error", "invalid", "error" -> INVALID_ERROR
                else -> null
            }
        }
    }
}

/**
 * Standardized RIF result values used by `order/result` and `order/manualResult` for the
 * `MDR_RIF` order type.
 *
 * NOTE: [wireValue] is a placeholder pending backend confirmation — same treatment as
 * [ChestXrayResult.wireValue]/[MtbResult.wireValue]. [fromResultText] matches on the free-text
 * phrases already used locally today (e.g. "Rif Resistance Detected", the manual-entry
 * "DR TB"/"Non DR TB" values from `TBSuspectedQuickDataset`).
 *
 * TrueNat (MTB) & RIF order lifecycle redesign — behavior INVERTED from the pre-redesign code:
 * [INDETERMINATE] is now terminal (Completed, eligible for the Clinical Assessment & Diagnosis
 * referral tile) instead of auto-repeating the order; [INVALID_ERROR] is the new repeat-trigger
 * instead (previously had no handling at all).
 */
enum class RifResult(val wireValue: String, val displayValue: String) {
    DR_TB("DR_TB", "DR TB"),
    NON_DR_TB("NON_DR_TB", "Non DR TB"),
    INDETERMINATE("INDETERMINATE", "Indeterminate"),
    INVALID_ERROR("INVALID_ERROR", "Invalid/Error");

    companion object {
        fun fromResultText(value: String?): RifResult? {
            if (value.isNullOrBlank()) return null
            return when (value.trim().lowercase()) {
                "dr tb", "rif resistance detected" -> DR_TB
                "non dr tb", "rif resistance not detected" -> NON_DR_TB
                "indeterminate" -> INDETERMINATE
                "invalid/error", "invalid error", "invalid", "error" -> INVALID_ERROR
                else -> null
            }
        }
    }
}
