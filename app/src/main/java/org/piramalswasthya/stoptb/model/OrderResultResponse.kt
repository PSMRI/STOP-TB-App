package org.piramalswasthya.stoptb.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OrderResultResponse(
    val data: OrderResultData = OrderResultData(),
    val errorMessage: String? = null,
    val status: String? = null,
    val statusCode: Int = 0
)

/**
 * One diagnostic order as order/result, order/manualResult and the changes feed return it.
 *
 * STOP-800: an MTB / MTB_PLUS order now carries the MTB result and, once MTB is positive, the
 * MDR RIF result, each as its own entry in [results] (main test first, then MDR_RIF). There is
 * no separate MDR_RIF order any more. The flat [providerStatus] / [resultSummary] /
 * [tbPresence] / [drugResistancePresence] fields are what servers before that change send;
 * they are read only when [results] is absent (see [perTest]).
 */
@JsonClass(generateAdapter = true)
data class OrderResultData(
    val externalOrderId: String? = null,
    val orderType: String? = null,
    val status: String? = null,
    val providerStatus: String? = null,
    val resultSummary: String? = null,
    val tbPresence: Boolean? = null,
    val drugResistancePresence: Boolean? = null,
    val errorMessage: String? = null,
    val results: List<DiagnosticResultEntry>? = null,
    val reasonToCloseXray: String? = null,
    val reasonToCloseMTB: String? = null,
    val reasonToCloseMDRRIF: String? = null
) {
    /**
     * The per-test view of this order, from [results] or, on an older server, the flat fields.
     * [fallbackType] is the order type the caller asked about, used when the response itself
     * carries no [orderType].
     */
    fun perTest(fallbackType: String? = null): PerTestResults {
        val entries = results
        if (entries != null) {
            fun ofType(vararg types: String) = entries.firstOrNull { entry ->
                types.any { type -> entry.resultType.equals(type, ignoreCase = true) }
            }
            return PerTestResults(
                hasResultsList = true,
                xray = ofType("XRAY_CHEST"),
                mtb = ofType("MTB", "MTB_PLUS", "SPUTUM_TRUENAT"),
                rif = ofType("MDR_RIF")
            )
        }
        // Older server: one flat result, belonging to whatever this order's type is.
        val flat = if (resultSummary == null && providerStatus == null) null else DiagnosticResultEntry(
            resultType = orderType,
            providerStatus = providerStatus,
            resultSummary = resultSummary,
            tbPresence = tbPresence,
            drugResistancePresence = drugResistancePresence
        )
        val type = (orderType ?: fallbackType)?.uppercase()
        return PerTestResults(
            hasResultsList = false,
            xray = flat?.takeIf { type == "XRAY_CHEST" },
            mtb = flat?.takeIf { type == "MTB" || type == "MTB_PLUS" || type == "SPUTUM_TRUENAT" },
            rif = flat?.takeIf { type == "MDR_RIF" }
        )
    }
}

/** One test's result row on an order (`tb_diagnostic_result`, one per `result_type`). */
@JsonClass(generateAdapter = true)
data class DiagnosticResultEntry(
    val resultType: String? = null,
    val providerStatus: String? = null,
    val resultSummary: String? = null,
    val tbPresence: Boolean? = null,
    val tbConfidence: Double? = null,
    val drugResistancePresence: Boolean? = null
)

/**
 * [hasResultsList] is true when the server sent the STOP-800 `results` list. Then a missing
 * entry means "this test has no result", and the stored result is cleared — that is how a
 * vendor result that replaced a manual one (e.g. MTB Positive -> Negative) also removes the RIF
 * result that no longer applies. With the old flat shape a missing value means "not sent".
 */
data class PerTestResults(
    val hasResultsList: Boolean,
    val xray: DiagnosticResultEntry?,
    val mtb: DiagnosticResultEntry?,
    val rif: DiagnosticResultEntry?
)
