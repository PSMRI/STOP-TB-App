package org.piramalswasthya.stoptb.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.piramalswasthya.stoptb.model.TBDiagnosticsCache

/**
 * Covers the two ways the "has the backend already recorded a result?" question was answered
 * wrongly. Both produced the same user-visible failure: the worker's own unsynced entry handed
 * back to them as the server's result, on a screen that then refuses their submission.
 */
class ServerOrderResultTest {

    private fun rowWith(
        xray: String? = null,
        naat: String? = null,
        rif: String? = null
    ) = TBDiagnosticsCache(
        benId = 1L,
        chestXRayResult = xray,
        naatResult = naat,
        trueNatRifResult = rif
    )

    @Test
    fun `a failed fetch is never treated as the server's result`() {
        // fetchOrderResult reports failure by returning Error rather than throwing, so the row
        // still holds whatever the user typed offline.
        val result = serverResultOrNull(
            NetworkResponse.Error("no connection"),
            rowWith(xray = "TB Presumptive"),
            "XRAY_CHEST"
        )
        assertNull(result)
    }

    @Test
    fun `a pending order is never treated as the server's result`() {
        // A successful call for a non-completed order leaves the stored result untouched.
        val result = serverResultOrNull(
            NetworkResponse.Success("PENDING"),
            rowWith(xray = "TB Presumptive"),
            "XRAY_CHEST"
        )
        assertNull(result)
    }

    @Test
    fun `a completed order reports the stored result`() {
        val result = serverResultOrNull(
            NetworkResponse.Success("COMPLETED"),
            rowWith(xray = "Normal"),
            "XRAY_CHEST"
        )
        assertEquals("Normal", result)
    }

    @Test
    fun `completed status is matched regardless of case`() {
        val result = serverResultOrNull(
            NetworkResponse.Success("completed"),
            rowWith(xray = "Normal"),
            "XRAY_CHEST"
        )
        assertEquals("Normal", result)
    }

    @Test
    fun `a completed order with no stored result reports nothing`() {
        assertNull(serverResultOrNull(NetworkResponse.Success("COMPLETED"), rowWith(), "XRAY_CHEST"))
        assertNull(
            serverResultOrNull(NetworkResponse.Success("COMPLETED"), rowWith(xray = "  "), "XRAY_CHEST")
        )
    }

    @Test
    fun `a missing row reports nothing`() {
        assertNull(serverResultOrNull(NetworkResponse.Success("COMPLETED"), null, "XRAY_CHEST"))
    }

    @Test
    fun `each order type reads its own field`() {
        val row = rowWith(xray = "Normal", naat = "MTB not detected", rif = "Rif Resistance Not Detected")
        val completed = NetworkResponse.Success<String>("COMPLETED")
        assertEquals("Normal", serverResultOrNull(completed, row, "XRAY_CHEST"))
        assertEquals("MTB not detected", serverResultOrNull(completed, row, "SPUTUM_TRUENAT"))
        assertEquals("Rif Resistance Not Detected", serverResultOrNull(completed, row, "MDR_RIF"))
    }

    @Test
    fun `a result for one order type is not reported for another`() {
        // The conflict check is keyed on the order that actually failed; reading the wrong
        // field would name an unrelated test's result in the dialog.
        val xrayOnly = rowWith(xray = "Normal")
        assertNull(serverResultOrNull(NetworkResponse.Success("COMPLETED"), xrayOnly, "SPUTUM_TRUENAT"))
    }
}
