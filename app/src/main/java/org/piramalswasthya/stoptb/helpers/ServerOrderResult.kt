package org.piramalswasthya.stoptb.helpers

import org.piramalswasthya.stoptb.model.OrderStatus
import org.piramalswasthya.stoptb.model.TBDiagnosticsCache

/**
 * The result the backend actually holds for [orderType], or null.
 *
 * Both the form's submit-conflict check and the offline replay's reconciliation need this answer,
 * and both got it wrong in the same way: they read the stored row straight after calling
 * `fetchOrderResult` and treated any non-blank string as the server's. Two things make that
 * unsound, so both conditions are checked here rather than at each call site - having two copies
 * is how one of them kept the bug after the other was fixed.
 *
 *  - `fetchOrderResult` reports failure by *returning* [NetworkResponse.Error], not by throwing,
 *    so a failed call leaves the row untouched and the old value looks like a fresh answer.
 *  - A successful call for an order that is still PENDING also leaves the stored result alone
 *    (see `TBRepo.applyOrderResult`'s non-completed branches), so only a COMPLETED order's value
 *    is the backend's.
 *
 * Getting this wrong tells a health worker their own unsynced entry is the result already on the
 * server, which is the opposite of true.
 */
fun serverResultOrNull(
    fetched: NetworkResponse<String>,
    row: TBDiagnosticsCache?,
    orderType: String
): String? {
    val status = (fetched as? NetworkResponse.Success)?.data ?: return null
    if (!status.equals(OrderStatus.COMPLETED.name, ignoreCase = true)) return null
    val result = when {
        orderType.equals("XRAY_CHEST", ignoreCase = true) -> row?.chestXRayResult
        orderType.equals("MDR_RIF", ignoreCase = true) -> row?.trueNatRifResult
        else -> row?.naatResult
    }
    return result?.takeIf { it.isNotBlank() }
}
