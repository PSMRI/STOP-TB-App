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
 * An MTB / MTB_PLUS order carries the MTB and (once MTB is positive) the MDR RIF result as
 * separate [results] entries. The flat fields are what older servers send; they are read only
 * when [results] is absent (see [perTest]).
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
     * [fallbackType] is used when the response carries no [orderType].
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
 * [hasResultsList]: the server sent the `results` list, so a missing entry means "no result" and
 * clears the stored one (e.g. a vendor MTB Negative also removes the RIF result). With the old
 * flat shape a missing value means "not sent".
 */
data class PerTestResults(
    val hasResultsList: Boolean,
    val xray: DiagnosticResultEntry?,
    val mtb: DiagnosticResultEntry?,
    val rif: DiagnosticResultEntry?
)
