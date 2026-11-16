package org.piramalswasthya.stoptb.helpers

import android.content.Context
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.model.ChestXrayResult
import org.piramalswasthya.stoptb.model.MtbResult
import org.piramalswasthya.stoptb.model.RifResult

/**
 * Results are stored and sent in the server's wording ("TB Positive") and shown in the app's
 * ("MTB Detected"). Every screen maps through these so the two never drift apart.
 */
object DiagnosticResultLabels {

    fun mtb(context: Context, stored: String?): String? {
        if (stored.isNullOrBlank()) return null
        return when (MtbResult.fromResultText(stored)) {
            MtbResult.TB_POSITIVE -> context.getString(R.string.result_display_mtb_detected)
            MtbResult.TB_NEGATIVE -> context.getString(R.string.result_display_mtb_not_detected)
            MtbResult.INVALID_ERROR -> context.getString(R.string.result_display_invalid_error)
            null -> stored
        }
    }

    fun rif(context: Context, stored: String?): String? {
        if (stored.isNullOrBlank()) return null
        return when (RifResult.fromResultText(stored)) {
            RifResult.DR_TB -> context.getString(R.string.result_display_rif_detected)
            RifResult.NON_DR_TB -> context.getString(R.string.result_display_rif_not_detected)
            RifResult.INDETERMINATE -> context.getString(R.string.result_display_indeterminate)
            RifResult.INVALID_ERROR -> context.getString(R.string.result_display_invalid_error)
            null -> stored
        }
    }

    fun xray(stored: String?): String? =
        if (stored.isNullOrBlank()) null else ChestXrayResult.fromResultText(stored)?.displayValue ?: stored

    /** The result of [orderType] (XRAY_CHEST / SPUTUM_TRUENAT / MDR_RIF) in the app's wording. */
    fun forOrderType(context: Context, orderType: String, stored: String?): String? = when (orderType) {
        "XRAY_CHEST" -> xray(stored)
        "MDR_RIF" -> rif(context, stored)
        else -> mtb(context, stored)
    }

    /**
     * True when the stored (server) result differs in meaning from the one the user entered, i.e. a
     * vendor result replaced it. Old local synonyms ("MTB detected") don't count as a change.
     */
    fun replacedByDevice(orderType: String, enteredByUser: String?, stored: String?): Boolean {
        if (enteredByUser.isNullOrBlank() || stored.isNullOrBlank()) return false
        return when (orderType) {
            "XRAY_CHEST" -> ChestXrayResult.fromResultText(enteredByUser) != ChestXrayResult.fromResultText(stored)
            "MDR_RIF" -> RifResult.fromResultText(enteredByUser) != RifResult.fromResultText(stored)
            else -> MtbResult.fromResultText(enteredByUser) != MtbResult.fromResultText(stored)
        }
    }

    fun testName(context: Context, orderType: String): String = context.getString(
        when (orderType) {
            "XRAY_CHEST" -> R.string.confirm_test_xray
            "MDR_RIF" -> R.string.confirm_test_rif
            else -> R.string.confirm_test_mtb
        }
    )
}
