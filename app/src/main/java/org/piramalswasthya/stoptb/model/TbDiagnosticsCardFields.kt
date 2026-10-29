package org.piramalswasthya.stoptb.model

/**
 * The columns of `TB_DIAGNOSTICS` that a beneficiary card actually renders.
 *
 * The list screens observed the whole entity (`SELECT *`, 46 columns) for every beneficiary on
 * the device - 10,163 rows on the camp tablet - purely to decorate the handful of rows on
 * screen. Wide rows matter here beyond the parse cost: a cursor is delivered through a 2 MB
 * CursorWindow, so the wider the row the more times SQLite has to refill that window, and each
 * refill holds one of the few read connections the pool has. A thread dump taken during a
 * 4,337 ms stall on the camp device showed three query threads inside
 * `SQLiteCursor.getCount -> nativeExecuteForCursorWindow` while eighteen others were parked in
 * `SQLiteConnectionPool.waitForConnection` - including the Paging refresh the user was waiting
 * on.
 *
 * Keep this in step with what [org.piramalswasthya.stoptb.adapters.BenListAdapter] reads: a
 * field added to the card needs adding here and to `TBDao.getAllTbDiagnosticsCardFields`.
 */
data class TbDiagnosticsCardFields(
    val benId: Long,
    // ── Digital Chest X-Ray ──────────────────────────────────────────────────
    val isReferredForDigitalChestXray: Boolean? = null,
    val reasonForDenialChestXray: String? = null,
    val reasonForDenialChestXrayOther: String? = null,
    val reasonNotConductedChestXray: String? = null,
    val reasonNotConductedChestXrayOther: String? = null,
    val chestXRayResult: String? = null,
    // ── Sputum / NAAT ────────────────────────────────────────────────────────
    val isReferredForSputum: Boolean? = null,
    val reasonForDenialSputum: String? = null,
    val reasonForDenialSputumOther: String? = null,
    val reasonNotConductedNaat: String? = null,
    val reasonNotConductedNaatOther: String? = null,
    val naatResult: String? = null,
    // ── RIF ──────────────────────────────────────────────────────────────────
    val reasonNotConductedRif: String? = null,
    val reasonNotConductedRifOther: String? = null,
    val trueNatRifResult: String? = null,
    // ── Liquid culture ───────────────────────────────────────────────────────
    val liquidCultureResult: String? = null,
    // ── Order lifecycle ──────────────────────────────────────────────────────
    val xrayOrderId: String? = null,
    val xrayOrderStatus: String? = null,
    val trueNatOrderId: String? = null,
    val trueNatOrderStatus: String? = null,
    val rifOrderId: String? = null,
    val rifOrderStatus: String? = null,
    // ── Error messages shown on the card ─────────────────────────────────────
    val errorMsgXray: String? = null,
    val errorMsgTrueNat: String? = null,
    val errorMsgRif: String? = null,
    // ── Manual result awaiting replay ────────────────────────────────────────
    val xrayManualResultPendingSync: Boolean? = null,
    val trueNatManualResultPendingSync: Boolean? = null,
    val rifManualResultPendingSync: Boolean? = null
)
