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

    // The merge / cascade / suspected helpers live in DiagnosticOrderRepo (split out of TBRepo).
    private val repo = org.piramalswasthya.stoptb.repositories.tb.DiagnosticOrderRepo(
        mock(TBDao::class.java, RETURNS_DEEP_STUBS), mock(BenDao::class.java), mock(PreferenceDao::class.java),
        mock(UserRepo::class.java), mock(AmritApiService::class.java), mock(InAppDb::class.java),
        mock(SyncRetryStore::class.java), mock(Context::class.java),
        mock(org.piramalswasthya.stoptb.repositories.tb.TbLocalRepo::class.java),
        mock(org.piramalswasthya.stoptb.repositories.tb.TbPushRepo::class.java),
        org.piramalswasthya.stoptb.repositories.tb.DeviceIntegrationRepo(
            mock(PreferenceDao::class.java), mock(AmritApiService::class.java), mock(Context::class.java)),
        org.piramalswasthya.stoptb.repositories.tb.BenIdLocks()
    )

    // The form-save merge lives in TbLocalRepo.
    private val local = org.piramalswasthya.stoptb.repositories.tb.TbLocalRepo(
        mock(TBDao::class.java), mock(BenDao::class.java),
        org.piramalswasthya.stoptb.repositories.tb.BenIdLocks(), mock(InAppDb::class.java)
    )

    private fun result(status: String, summary: String? = null, id: String = "ext-1") =
        OrderResultData(externalOrderId = id, status = status, resultSummary = summary)

    private fun merge(
        existing: TBDiagnosticsCache?, type: String, data: OrderResultData,
        truenatIntegrated: Boolean = true, hubCreatesOrders: Boolean = false
    ) = repo.mergeOrderResult(existing, benId, type, data, data.status ?: "IN_PROGRESS", truenatIntegrated, hubCreatesOrders)

    /** An order in the `results` list shape: one entry per test. */
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

    // ── MTB and RIF on one order ─────────────────────────────────────────────

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

    // An older server: the MTB order comes in the flat shape and RIF on its own MDR_RIF order.
    @Test
    fun aLegacyRifOrderFillsRifOfALegacyMtbOrder() {
        val mtbPositive = merge(null, "SPUTUM_TRUENAT",
            OrderResultData(externalOrderId = "ext-mtb", orderType = "MTB", status = "COMPLETED", resultSummary = "MTB detected")).cache
        val legacy = OrderResultData(externalOrderId = "ext-rif", orderType = "MDR_RIF", status = "COMPLETED", resultSummary = "Rif Resistance Detected")
        val withLegacy = merge(mtbPositive, "MDR_RIF", legacy).cache
        assertEquals("DR TB", withLegacy.trueNatRifResult)

        val ownRif = merge(null, "SPUTUM_TRUENAT", mtbOrder("COMPLETED", mtb = "TB Positive", rif = "Non DR TB")).cache
        assertEquals("Non DR TB", merge(ownRif, "MDR_RIF", legacy).cache.trueNatRifResult)
    }
    @Test
    fun aLegacyRifOrderNeverFillsANewMtbOrderWaitingForItsRif() {
        val waiting = merge(null, "SPUTUM_TRUENAT", mtbOrder("PENDING", mtb = "TB Positive", id = "ext-new")).cache
        assertEquals("ext-new", waiting.rifOrderId)
        val legacy = OrderResultData(externalOrderId = "ext-old-rif", orderType = "MDR_RIF", status = "COMPLETED", resultSummary = "Rif Resistance Detected")
        val after = merge(waiting, "MDR_RIF", legacy).cache
        assertNull(after.trueNatRifResult)
        assertEquals("ext-new", after.rifOrderId)
        assertEquals(OrderStatus.PENDING.name, after.rifOrderStatus)
    }

    // An MTB Positive the machine then reports as Error-2 no longer confirms TB on
    // TB_DIAGNOSTICS either (isKnownTbCase reads these flags).
    @Test
    fun error2AfterMtbPositiveClearsTheConfirmation() {
        val positive = merge(null, "SPUTUM_TRUENAT", mtbOrder("PENDING", mtb = "TB Positive")).cache
        assertEquals(true, positive.isTBConfirmed)
        val invalid = merge(positive, "SPUTUM_TRUENAT", mtbOrder("INVALID", mtb = "Error-2")).cache
        assertNull(invalid.naatResult)
        assertEquals(false, invalid.isTBConfirmed)
        assertEquals(false, invalid.isConfirmed)
    }

    @Test
    fun aPositiveLiquidCultureStillConfirmsWithoutAnMtbResult() {
        val positive = merge(null, "SPUTUM_TRUENAT", mtbOrder("PENDING", mtb = "TB Positive")).cache
            .copy(liquidCultureResult = "Positive")
        val invalid = merge(positive, "SPUTUM_TRUENAT", mtbOrder("INVALID", mtb = "Error-2")).cache
        assertEquals(true, invalid.isTBConfirmed)
    }

    // The form save takes results, statuses and the RIF close reason from the stored row,
    // and derives confirmation from the final values.
    @Test
    fun formSaveKeepsRepositoryOwnedFieldsFromTheStoredRow() {
        val stored = merge(null, "SPUTUM_TRUENAT",
            mtbOrder("CLOSED", mtb = "TB Positive", reasonToCloseMDRRIF = "Patient refused")).cache
        assertEquals("Patient refused", stored.reasonNotConductedRif)
        // The form, with RIF not open for entry, mapped the RIF reason away and holds a stale result.
        val form = stored.copy(reasonNotConductedRif = null, naatResult = "TB Negative", trueNatOrderStatus = "PENDING")
        val saved = local.formRowOverStored(form, stored)
        assertEquals("Patient refused", saved.reasonNotConductedRif)
        assertEquals("TB Positive", saved.naatResult)
        assertEquals(OrderStatus.CLOSED.name, saved.trueNatOrderStatus)
        assertEquals(true, saved.isTBConfirmed)
    }

    @Test
    fun formSaveConfirmsTbOnAPositiveLiquidCultureItEntered() {
        val stored = merge(null, "SPUTUM_TRUENAT", mtbOrder("COMPLETED", mtb = "TB Negative")).cache
        assertEquals(false, stored.isTBConfirmed)
        val form = stored.copy(isLiquidCultureConducted = true, liquidCultureResult = "Positive", isTBConfirmed = true, isConfirmed = true)
        val saved = local.formRowOverStored(form, stored)
        assertEquals("Positive", saved.liquidCultureResult)
        assertEquals(true, saved.isTBConfirmed)
        assertEquals(true, saved.isConfirmed)
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

    // A manual MTB Detected later reported as Error-2 must not leave TB_SUSPECTED "TB confirmed".
    @Test
    fun suspectedDropsMtbResultWhenTheMachineReportsError2() {
        val positive = merge(null, "SPUTUM_TRUENAT", mtbOrder("PENDING", mtb = "TB Positive")).cache
        val suspected = repo.suspectedFromDiagnostics(null, benId, positive)!!
        assertEquals(true, suspected.isTBConfirmed)
        val invalid = merge(positive, "SPUTUM_TRUENAT", mtbOrder("INVALID", mtb = "Error-2")).cache
        assertNull(invalid.naatResult)
        val updated = repo.suspectedFromDiagnostics(suspected.copy(syncState = SyncState.SYNCED), benId, invalid)!!
        assertNull(updated.sputumTestResult)
        assertNull(updated.naatResult)
        assertEquals(false, updated.isTBConfirmed)
        assertNull(updated.mdrRifResult)
    }

    // A new MTB order (Create New Order) is a new test: until its result arrives the beneficiary
    // is not TB confirmed and the earlier MTB / RIF results go.
    @Test
    fun suspectedDropsEarlierResultsWhileANewOrderWaitsForItsResult() {
        val earlier = merge(null, "SPUTUM_TRUENAT",
            mtbOrderWithRifStatus("CLOSED", mtb = "TB Positive", rif = "Invalid/Error", rifStatus = "CLOSED")).cache
        val existing = repo.suspectedFromDiagnostics(null, benId, earlier)!!.copy(syncState = SyncState.SYNCED)
        assertEquals("TB Positive", existing.sputumTestResult)
        assertEquals("Invalid/Error", existing.mdrRifResult)

        val newOrder = merge(earlier, "SPUTUM_TRUENAT", mtbOrder("PENDING", id = "ext-new")).cache
        assertEquals(false, newOrder.isTBConfirmed)
        val updated = repo.suspectedFromDiagnostics(existing, benId, newOrder)!!
        assertNull(updated.sputumTestResult)
        assertNull(updated.naatResult)
        assertNull(updated.mdrRifResult)
        assertEquals(false, updated.isTBConfirmed)
    }

    // RIF Invalid/Error, Create New Order, MTB Detected on the new order: TB_SUSPECTED must not
    // keep the earlier order's RIF "Invalid/Error" while the new RIF is pending.
    @Test
    fun suspectedDropsAnEarlierRifResultWhileTheNewOrderWaitsForItsRif() {
        val earlier = merge(null, "SPUTUM_TRUENAT",
            mtbOrderWithRifStatus("CLOSED", mtb = "TB Positive", rif = "Invalid/Error", rifStatus = "CLOSED")).cache
        val existing = repo.suspectedFromDiagnostics(null, benId, earlier)!!.copy(syncState = SyncState.SYNCED)
        assertEquals("Invalid/Error", existing.mdrRifResult)

        val newOrderMtbPositive = merge(earlier, "SPUTUM_TRUENAT", mtbOrder("PENDING", mtb = "TB Positive", id = "ext-new")).cache
        assertEquals("ext-new", newOrderMtbPositive.rifOrderId)
        val updated = repo.suspectedFromDiagnostics(existing, benId, newOrderMtbPositive)!!
        assertEquals("TB Positive", updated.sputumTestResult)
        assertNull(updated.mdrRifResult)
        assertEquals(false, updated.isDRTBConfirmed)
        assertEquals(true, updated.isTBConfirmed)
    }

    // Without any MTB order the diagnostics row says nothing about MTB, so the stored value stays.
    @Test
    fun suspectedKeepsItsMtbResultWhenThereIsNoMtbOrder() {
        val withResult = merge(null, "SPUTUM_TRUENAT", mtbOrder("COMPLETED", mtb = "TB Positive")).cache
        val existing = repo.suspectedFromDiagnostics(null, benId, withResult)!!.copy(syncState = SyncState.SYNCED)
        val noMtbOrder = merge(null, "XRAY_CHEST", result("PENDING")).cache
        val updated = repo.suspectedFromDiagnostics(existing, benId, noMtbOrder) ?: existing
        assertEquals("TB Positive", updated.sputumTestResult)
        assertEquals(true, updated.isTBConfirmed)
    }

    // MTB Negative with a positive liquid culture confirms TB_SUSPECTED, as on TB_DIAGNOSTICS.
    @Test
    fun suspectedIsConfirmedByAPositiveLiquidCulture() {
        val negative = merge(null, "SPUTUM_TRUENAT", mtbOrder("COMPLETED", mtb = "TB Negative")).cache
        val existing = repo.suspectedFromDiagnostics(null, benId, negative)!!.copy(syncState = SyncState.SYNCED)
        assertEquals(false, existing.isTBConfirmed)
        val withCulture = negative.copy(isLiquidCultureConducted = true, liquidCultureResult = "Positive")
        val updated = repo.suspectedFromDiagnostics(existing, benId, withCulture)!!
        assertEquals("TB Negative", updated.sputumTestResult)
        assertEquals(true, updated.isTBConfirmed)
        assertEquals(true, updated.isConfirmed)
    }

    // After RIF Not Conducted and Create New Order, the previous "RIF not conducted" reason
    // must not stay on TB_SUSPECTED while the new order waits for RIF.
    @Test
    fun suspectedDropsTheRifReasonOfAPreviousOrder() {
        val closed = merge(null, "SPUTUM_TRUENAT",
            mtbOrder("CLOSED", mtb = "TB Positive", reasonToCloseMDRRIF = "Sample rejected", id = "ext-old")).cache
        val suspected = repo.suspectedFromDiagnostics(null, benId, closed)!!.copy(syncState = SyncState.SYNCED)
        assertEquals("Sample rejected", suspected.reasonForRefusalMDRRIF)

        val newOrderWaitingForRif = merge(closed, "SPUTUM_TRUENAT", mtbOrder("PENDING", mtb = "TB Positive", id = "ext-new")).cache
        assertNull(newOrderWaitingForRif.reasonNotConductedRif)
        val updated = repo.suspectedFromDiagnostics(suspected, benId, newOrderWaitingForRif)!!
        assertNull(updated.reasonForRefusalMDRRIF)
    }

    // After MTB Not Conducted and Create New Order, the old reason must not stay on TB_SUSPECTED.
    @Test
    fun suspectedDropsTheMtbReasonOfAPreviousOrder() {
        val closed = merge(null, "SPUTUM_TRUENAT",
            mtbOrder("CLOSED", reasonToCloseMTB = "Cartridge unavailable", id = "ext-old")).cache
        val suspected = repo.suspectedFromDiagnostics(null, benId, closed)!!.copy(syncState = SyncState.SYNCED)
        assertEquals("Cartridge unavailable", suspected.reasonForRefusalMTB)

        // A new order through the feed (e.g. raised by the server): the merge itself drops the reason.
        val newOrder = merge(closed, "SPUTUM_TRUENAT", mtbOrder("PENDING", id = "ext-new")).cache
        assertNull(newOrder.reasonNotConductedNaat)
        val updated = repo.suspectedFromDiagnostics(suspected, benId, newOrder)!!
        assertNull(updated.reasonForRefusalMTB)
    }

    // After X-ray Not Conducted and Create New X-Ray Order, the old reason must not stay on
    // TB_SUSPECTED while the new X-ray order is pending.
    @Test
    fun suspectedDropsTheXrayReasonOfAPreviousOrder() {
        val closed = merge(null, "XRAY_CHEST", OrderResultData(
            externalOrderId = "ext-x-old", orderType = "XRAY_CHEST", status = "CLOSED", results = emptyList(),
            reasonToCloseXray = "Other: Film jammed")).cache
        val suspected = repo.suspectedFromDiagnostics(null, benId, closed)!!.copy(syncState = SyncState.SYNCED)
        assertEquals("Other", suspected.reasonForRefusalXray)

        val newOrder = merge(closed, "XRAY_CHEST", result("PENDING", id = "ext-x-new")).cache
        assertNull(newOrder.reasonNotConductedChestXray)
        val updated = repo.suspectedFromDiagnostics(suspected, benId, newOrder)!!
        assertNull(updated.reasonForRefusalXray)
    }

    // A result on the same X-ray order replaces its "not conducted" reason.
    @Test
    fun anXrayResultClearsTheNotConductedReasonOfTheSameOrder() {
        val closed = merge(null, "XRAY_CHEST", OrderResultData(
            externalOrderId = "ext-x", orderType = "XRAY_CHEST", status = "CLOSED", results = emptyList(),
            reasonToCloseXray = "Power issue")).cache
        val withResult = merge(closed, "XRAY_CHEST", OrderResultData(
            externalOrderId = "ext-x", orderType = "XRAY_CHEST", status = "COMPLETED",
            results = listOf(DiagnosticResultEntry(resultType = "XRAY_CHEST", providerStatus = "COMPLETED", resultSummary = "Normal")))).cache
        assertEquals("Normal", withResult.chestXRayResult)
        assertNull(withResult.reasonNotConductedChestXray)
        val suspected = repo.suspectedFromDiagnostics(null, benId, withResult)!!
        assertNull(suspected.reasonForRefusalXray)
    }

    // General form (referralType 0): with RIF read-only the form holds no RIF reason, so the
    // stored ones stay; its own X-ray / MTB results are kept.
    @Test
    fun generalFormSaveKeepsStoredRifReasons() {
        val stored = merge(null, "SPUTUM_TRUENAT",
            mtbOrder("CLOSED", mtb = "TB Positive", reasonToCloseMDRRIF = "Other: Cartridge expired")).cache
        assertEquals("Other", stored.reasonNotConductedRif)
        assertEquals("Cartridge expired", stored.reasonNotConductedRifOther)
        val form = stored.copy(reasonNotConductedRif = null, reasonNotConductedRifOther = null, chestXRayResult = "Normal")
        val saved = local.withStoredRifReasons(form, stored)
        assertEquals("Other", saved.reasonNotConductedRif)
        assertEquals("Cartridge expired", saved.reasonNotConductedRifOther)
        assertEquals("Normal", saved.chestXRayResult)
        assertEquals("TB Positive", saved.naatResult)
    }

    // The form save keeps the MTB / X-ray close reasons written by the feed while the form was open.
    @Test
    fun formSaveKeepsStoredMtbAndXrayCloseReasons() {
        val stored = merge(null, "SPUTUM_TRUENAT",
            mtbOrder("CLOSED", reasonToCloseMTB = "Other: Sample leaked")).cache
            .copy(reasonNotConductedChestXray = "Power issue")
        val form = stored.copy(reasonNotConductedNaat = null, reasonNotConductedNaatOther = null, reasonNotConductedChestXray = null)
        val saved = local.formRowOverStored(form, stored)
        assertEquals("Other", saved.reasonNotConductedNaat)
        assertEquals("Sample leaked", saved.reasonNotConductedNaatOther)
        assertEquals("Power issue", saved.reasonNotConductedChestXray)
    }

    @Test
    fun suspectedKeepsTheRifReasonWhileTheRifTestIsClosed() {
        val closed = merge(null, "SPUTUM_TRUENAT",
            mtbOrder("CLOSED", mtb = "TB Positive", reasonToCloseMDRRIF = "Sample rejected")).cache
        val suspected = repo.suspectedFromDiagnostics(null, benId, closed)!!.copy(syncState = SyncState.SYNCED)
        // A later save of the same closed order (reason not carried on this copy) keeps it.
        val updated = repo.suspectedFromDiagnostics(suspected, benId, closed.copy(reasonNotConductedRif = null))
            ?: suspected
        assertEquals("Sample rejected", updated.reasonForRefusalMDRRIF)
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
