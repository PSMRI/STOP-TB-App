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

    private fun result(status: String, summary: String? = null, id: String = "ext-1") =
        OrderResultData(externalOrderId = id, status = status, resultSummary = summary)

    private fun merge(existing: TBDiagnosticsCache?, type: String, data: OrderResultData, truenatIntegrated: Boolean = true) =
        repo.mergeOrderResult(existing, benId, type, data, data.status ?: "IN_PROGRESS", null, truenatIntegrated)

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
    fun onlyAPositiveMtbWithoutAKnownRifNeedsTheCascade() {
        val positive = result("COMPLETED", "TB Positive")
        assertTrue(repo.needsRifCascade("SPUTUM_TRUENAT", positive, rifOrderKnown = false))
        assertFalse(repo.needsRifCascade("SPUTUM_TRUENAT", positive, rifOrderKnown = true))
        assertFalse(repo.needsRifCascade("SPUTUM_TRUENAT", result("COMPLETED", "TB Negative"), false))
        assertFalse(repo.needsRifCascade("SPUTUM_TRUENAT", result("PENDING"), false))
        assertFalse(repo.needsRifCascade("XRAY_CHEST", positive, false))
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
