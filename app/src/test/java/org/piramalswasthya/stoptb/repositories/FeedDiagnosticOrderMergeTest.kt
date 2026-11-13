package org.piramalswasthya.stoptb.repositories

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.Mockito.mock
import org.piramalswasthya.stoptb.database.room.InAppDb
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.database.room.dao.BenDao
import org.piramalswasthya.stoptb.database.room.dao.TBDao
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.database.shared_preferences.SyncRetryStore
import org.piramalswasthya.stoptb.model.DiagnosticResultEntry
import org.piramalswasthya.stoptb.model.OrderResultData
import org.piramalswasthya.stoptb.model.OrderStatus
import org.piramalswasthya.stoptb.model.TBDiagnosticsCache
import org.piramalswasthya.stoptb.network.AmritApiService

/**
 * The pure pieces the changes-feed order apply is built from. The feed applies a page of orders
 * in one transaction and skips rows that come out unchanged, so applying the same result twice
 * must give an identical row.
 */
class FeedDiagnosticOrderMergeTest {

    private val benId = 422455468513L

    private val repo = TBRepo(
        mock(TBDao::class.java, RETURNS_DEEP_STUBS), mock(BenDao::class.java), mock(PreferenceDao::class.java),
        mock(UserRepo::class.java), mock(AmritApiService::class.java), mock(InAppDb::class.java),
        mock(DrugMasterRepo::class.java), mock(SyncRetryStore::class.java), mock(Context::class.java)
    )

    private fun result(status: String, summary: String? = null, id: String = "ext-1") =
        OrderResultData(externalOrderId = id, status = status, resultSummary = summary)

    private fun merge(
        existing: TBDiagnosticsCache?, type: String, data: OrderResultData,
        truenatIntegrated: Boolean = true, hubCreatesOrders: Boolean = false
    ) = repo.mergeOrderResult(existing, benId, type, data, data.status ?: "IN_PROGRESS", truenatIntegrated, hubCreatesOrders)

    /** An order in the STOP-800 shape: one `results` entry per test. */
    private fun mtbOrder(
        status: String, mtb: String? = null, rif: String? = null, id: String = "ext-mtb",
        reasonToCloseMTB: String? = null, reasonToCloseMDRRIF: String? = null
    ) = OrderResultData(
        externalOrderId = id, orderType = "MTB", status = status,
        results = listOfNotNull(
            mtb?.let { DiagnosticResultEntry(resultType = "MTB", providerStatus = status, resultSummary = it) },
            rif?.let { DiagnosticResultEntry(resultType = "MDR_RIF", providerStatus = "COMPLETED", resultSummary = it) }
        ),
        reasonToCloseMTB = reasonToCloseMTB, reasonToCloseMDRRIF = reasonToCloseMDRRIF
    )

    @Test
    fun pendingXrayOrderOnANewRow() {
        val merged = merge(null, "XRAY_CHEST", result("PENDING"))
        assertEquals(OrderStatus.PENDING.name, merged.cache.xrayOrderStatus)
        assertEquals("ext-1", merged.cache.xrayOrderId)
        assertEquals(true, merged.cache.isReferredForDigitalChestXray)
        assertFalse(merged.triggerTrueNat)
    }

    @Test
    fun unrecognisedStatusIsHeldAsPending() {
        val merged = merge(null, "SPUTUM_TRUENAT", result("SOMETHING_NEW"))
        assertEquals(OrderStatus.PENDING.name, merged.cache.trueNatOrderStatus)
        assertEquals("ext-1", merged.cache.trueNatOrderId)
    }

    @Test
    fun applyingTheSameResultTwiceGivesTheSameRow() {
        for ((type, data) in listOf(
            "XRAY_CHEST" to result("PENDING"),
            "XRAY_CHEST" to result("COMPLETED", "Normal"),
            "SPUTUM_TRUENAT" to result("COMPLETED", "MTB Not Detected"),
            "MDR_RIF" to result("FAILED")
        )) {
            val once = merge(null, type, data).cache.copy(syncState = SyncState.SYNCED)
            val twice = merge(once, type, data).cache.copy(syncState = SyncState.SYNCED)
            assertEquals("$type ${data.status}", once, twice)
        }
    }

    @Test
    fun presumptiveXrayAsksForTrueNatOnlyWhenNoneExists() {
        val presumptive = result("COMPLETED", "TB Presumptive")
        assertTrue(merge(null, "XRAY_CHEST", presumptive).triggerTrueNat)
        val withTrueNat = TBDiagnosticsCache(benId = benId, trueNatOrderStatus = OrderStatus.PENDING.name)
        assertFalse(merge(withTrueNat, "XRAY_CHEST", presumptive).triggerTrueNat)
        assertFalse(merge(null, "XRAY_CHEST", presumptive, truenatIntegrated = false).triggerTrueNat)
    }

    @Test
    fun hubModeNeverAsksTheTabletForTheTrueNatOrder() {
        val presumptive = result("COMPLETED", "TB Presumptive")
        assertFalse(merge(null, "XRAY_CHEST", presumptive, hubCreatesOrders = true).triggerTrueNat)
    }

    // ── STOP-800: MTB and RIF on one order ───────────────────────────────────

    @Test
    fun mtbPositiveWithoutRifKeepsTheOrderOpenForRif() { // M2 after the manual entry
        val row = merge(null, "SPUTUM_TRUENAT", mtbOrder("PENDING", mtb = "TB Positive")).cache
        assertEquals(OrderStatus.PENDING.name, row.trueNatOrderStatus)
        assertEquals("TB Positive", row.naatResult)
        assertEquals(true, row.isTBConfirmed)
        assertEquals("ext-mtb", row.rifOrderId)
        assertEquals(OrderStatus.PENDING.name, row.rifOrderStatus)
        assertNull(row.trueNatRifResult)
    }

    @Test
    fun rifResultComesFromTheSameOrder() { // M2 after the poll, M6
        val row = merge(null, "SPUTUM_TRUENAT", mtbOrder("COMPLETED", mtb = "TB Positive", rif = "DR TB")).cache
        assertEquals(OrderStatus.COMPLETED.name, row.trueNatOrderStatus)
        assertEquals("DR TB", row.trueNatRifResult)
        assertEquals(OrderStatus.COMPLETED.name, row.rifOrderStatus)
        assertEquals("ext-mtb", row.rifOrderId)
        assertEquals(true, row.isDrTbConfirmed)
    }

    @Test
    fun vendorNegativeReplacingAManualPositiveClearsRif() {
        val manualPositive = merge(null, "SPUTUM_TRUENAT", mtbOrder("PENDING", mtb = "TB Positive")).cache
        val row = merge(manualPositive, "SPUTUM_TRUENAT", mtbOrder("COMPLETED", mtb = "TB Negative")).cache
        assertEquals("TB Negative", row.naatResult)
        assertEquals(false, row.isTBConfirmed)
        assertNull(row.rifOrderStatus)
        assertNull(row.rifOrderId)
        assertNull(row.trueNatRifResult)
        assertEquals(false, row.isDrTbConfirmed)
    }

    @Test
    fun vendorPositiveReplacingAManualNegativeAddsRif() { // M3
        val manualNegative = merge(null, "SPUTUM_TRUENAT", mtbOrder("COMPLETED", mtb = "TB Negative")).cache
        val row = merge(manualNegative, "SPUTUM_TRUENAT", mtbOrder("COMPLETED", mtb = "TB Positive", rif = "Non DR TB")).cache
        assertEquals("TB Positive", row.naatResult)
        assertEquals("Non DR TB", row.trueNatRifResult)
        assertEquals(false, row.isDrTbConfirmed)
    }

    @Test
    fun vendorRifReplacesTheManualRif() { // M4
        val manual = merge(null, "SPUTUM_TRUENAT", mtbOrder("COMPLETED", mtb = "TB Positive", rif = "Non DR TB")).cache
        val row = merge(manual, "SPUTUM_TRUENAT", mtbOrder("COMPLETED", mtb = "TB Positive", rif = "DR TB")).cache
        assertEquals("DR TB", row.trueNatRifResult)
        assertEquals(true, row.isDrTbConfirmed)
    }

    @Test
    fun mtbClosedBeforeAnyResult() { // C1
        val row = merge(null, "SPUTUM_TRUENAT",
            mtbOrder("CLOSED", reasonToCloseMTB = "Other: Patient refused sputum collection")).cache
        assertEquals(OrderStatus.CLOSED.name, row.trueNatOrderStatus)
        assertNull(row.naatResult)
        assertEquals(false, row.isNaatConducted)
        assertEquals("Other", row.reasonNotConductedNaat)
        assertEquals("Patient refused sputum collection", row.reasonNotConductedNaatOther)
        assertNull(row.rifOrderStatus)
    }

    @Test
    fun rifClosedKeepsTheMtbResult() { // C3
        val row = merge(null, "SPUTUM_TRUENAT",
            mtbOrder("CLOSED", mtb = "TB Positive", reasonToCloseMDRRIF = "Sample insufficient for MDR RIF")).cache
        assertEquals("TB Positive", row.naatResult)
        assertEquals(OrderStatus.CLOSED.name, row.rifOrderStatus)
        assertEquals("Sample insufficient for MDR RIF", row.reasonNotConductedRif)
        assertNull(row.trueNatRifResult)
    }

    @Test
    fun aManualResultStillWaitingToSyncIsNotOverwritten() {
        val local = TBDiagnosticsCache(
            benId = benId, trueNatOrderId = "ext-mtb", trueNatOrderStatus = OrderStatus.PENDING.name,
            naatResult = "TB Positive", trueNatManualResultPendingSync = true
        )
        val row = merge(local, "SPUTUM_TRUENAT", mtbOrder("PENDING")).cache
        assertEquals("TB Positive", row.naatResult)
        assertEquals(true, row.trueNatManualResultPendingSync)
    }

    @Test
    fun aLegacyRifOrderFillsRifOnlyWhenTheMtbOrderHasNone() {
        val mtbPositive = merge(null, "SPUTUM_TRUENAT", mtbOrder("COMPLETED", mtb = "TB Positive")).cache
        val legacy = OrderResultData(externalOrderId = "ext-rif", orderType = "MDR_RIF", status = "COMPLETED", resultSummary = "Rif Resistance Detected")
        val withLegacy = merge(mtbPositive, "MDR_RIF", legacy).cache
        assertEquals("DR TB", withLegacy.trueNatRifResult)

        val ownRif = merge(null, "SPUTUM_TRUENAT", mtbOrder("COMPLETED", mtb = "TB Positive", rif = "Non DR TB")).cache
        assertEquals("Non DR TB", merge(ownRif, "MDR_RIF", legacy).cache.trueNatRifResult)
    }

    @Test
    fun olderServerFlatShapeStillReadsTheMtbResult() {
        val row = merge(null, "SPUTUM_TRUENAT",
            OrderResultData(externalOrderId = "ext-1", orderType = "MTB", status = "COMPLETED", resultSummary = "MTB detected")).cache
        assertEquals("TB Positive", row.naatResult)
        assertEquals(true, row.isTBConfirmed)
    }

    @Test
    fun suspectedDropsRifOnceTheServerSaysMtbNegative() {
        val positive = merge(null, "SPUTUM_TRUENAT", mtbOrder("COMPLETED", mtb = "TB Positive", rif = "DR TB")).cache
        val suspected = repo.suspectedFromDiagnostics(null, benId, positive)!!
        assertEquals("DR TB", suspected.mdrRifResult)
        val negative = merge(positive, "SPUTUM_TRUENAT", mtbOrder("COMPLETED", mtb = "TB Negative")).cache
        val updated = repo.suspectedFromDiagnostics(suspected.copy(syncState = SyncState.SYNCED), benId, negative)!!
        assertEquals("TB Negative", updated.sputumTestResult)
        assertNull(updated.mdrRifResult)
        assertEquals(false, updated.isDRTBConfirmed)
    }

    private fun mtbOrderWithRifStatus(status: String, mtb: String, rif: String?, rifStatus: String) = OrderResultData(
        externalOrderId = "ext-mtb", orderType = "MTB", status = status,
        results = listOfNotNull(
            DiagnosticResultEntry(resultType = "MTB", providerStatus = "COMPLETED", resultSummary = mtb),
            DiagnosticResultEntry(resultType = "MDR_RIF", providerStatus = rifStatus, resultSummary = rif)
        )
    )

    @Test
    fun invalidMtbRunIsNotStoredAsAResult() { // v4 T1
        val row = merge(null, "SPUTUM_TRUENAT", mtbOrder("INVALID", mtb = "Error-2")).cache
        assertEquals(OrderStatus.INVALID.name, row.trueNatOrderStatus)
        assertNull(row.naatResult)
        assertNull(row.rifOrderStatus)
        val retested = merge(row, "SPUTUM_TRUENAT", mtbOrder("COMPLETED", mtb = "TB Negative")).cache // T2
        assertEquals("TB Negative", retested.naatResult)
        assertEquals(OrderStatus.COMPLETED.name, retested.trueNatOrderStatus)
    }

    @Test
    fun invalidRifRunWaitsForTheRetest() { // v4 T3
        val row = merge(null, "SPUTUM_TRUENAT", mtbOrderWithRifStatus("INVALID", "TB Positive", "Error-2", "INVALID")).cache
        assertEquals("TB Positive", row.naatResult)
        assertNull(row.trueNatRifResult)
        assertEquals(OrderStatus.INVALID.name, row.rifOrderStatus)
    }

    @Test
    fun manualRifInvalidClosesRif() {
        val row = merge(null, "SPUTUM_TRUENAT", mtbOrderWithRifStatus("CLOSED", "TB Positive", "Invalid/Error", "CLOSED")).cache
        assertEquals(OrderStatus.CLOSED.name, row.trueNatOrderStatus)
        assertEquals(OrderStatus.CLOSED.name, row.rifOrderStatus)
        assertEquals("Invalid/Error", row.trueNatRifResult)
        assertEquals(false, row.isDrTbConfirmed)
    }

    @Test
    fun failedRifKeepsMtbAndMarksRifFailed() {
        val row = merge(null, "SPUTUM_TRUENAT", mtbOrderWithRifStatus("FAILED", "TB Positive", null, "FAILED")).cache
        assertEquals("TB Positive", row.naatResult)
        assertEquals(OrderStatus.FAILED.name, row.trueNatOrderStatus)
        assertEquals(OrderStatus.FAILED.name, row.rifOrderStatus)
    }

    @Test
    fun suspectedRowIsCreatedOnceThenLeftAlone() {
        val diag = merge(null, "XRAY_CHEST", result("PENDING")).cache.copy(syncState = SyncState.SYNCED)
        val created = repo.suspectedFromDiagnostics(null, benId, diag)
        assertNotNull(created)
        assertEquals(SyncState.UNSYNCED, created!!.syncState)
        assertEquals(true, created.isChestXRayDone)
        assertNull(repo.suspectedFromDiagnostics(created.copy(syncState = SyncState.SYNCED), benId, diag))
    }
}
