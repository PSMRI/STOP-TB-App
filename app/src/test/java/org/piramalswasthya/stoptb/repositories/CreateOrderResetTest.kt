package org.piramalswasthya.stoptb.repositories

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.invocation.InvocationOnMock
import org.piramalswasthya.stoptb.database.room.InAppDb
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.database.room.dao.BenDao
import org.piramalswasthya.stoptb.database.room.dao.TBDao
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.database.shared_preferences.SyncRetryStore
import org.piramalswasthya.stoptb.helpers.NetworkResponse
import org.piramalswasthya.stoptb.model.BenRegCache
import org.piramalswasthya.stoptb.model.OrderPushedData
import org.piramalswasthya.stoptb.model.OrderPushedResponse
import org.piramalswasthya.stoptb.model.OrderStatus
import org.piramalswasthya.stoptb.model.TBDiagnosticsCache
import org.piramalswasthya.stoptb.model.User
import org.piramalswasthya.stoptb.network.AmritApiService
import retrofit2.Response

/**
 * TBRepo.createOrder writes the row a new order starts from.
 * The network and Room are mocked; the row passed to TBDao.saveTbDiagnostics is checked.
 *
 * Suspend DAO / API methods are answered by method name: a suspend call carries a Continuation
 * argument that ordinary argument matching cannot match, and the mockito-kotlin helpers that
 * handle it are inline functions this module's JVM 1.8 test target cannot use.
 */
class CreateOrderResetTest {

    private val benId = 288697198209L

    private val savedRows = mutableListOf<TBDiagnosticsCache>()

    /** A mock that answers [answers] by method name and gives defaults otherwise. */
    private fun <T> mockAnswering(type: Class<T>, answers: Map<String, (InvocationOnMock) -> Any?>): T =
        mock(type) { invocation ->
            answers[invocation.method.name]?.invoke(invocation)
                ?: Mockito.RETURNS_DEFAULTS.answer(invocation)
        }

    /** A repository whose hub is reachable, with a logged-in user, the beneficiary, [stored] and the server's answer. */
    private fun repoForOrderPush(stored: TBDiagnosticsCache, serverStatus: String): TBRepo {
        val user = mock(User::class.java)
        Mockito.`when`(user.serviceMapId).thenReturn(1734)
        val ben = mock(BenRegCache::class.java)
        Mockito.`when`(ben.beneficiaryId).thenReturn(benId)
        Mockito.`when`(ben.firstName).thenReturn("MAC")
        Mockito.`when`(ben.lastName).thenReturn("PANDEY")

        val preferenceDao = mockAnswering(PreferenceDao::class.java, mapOf("getLoggedInUser" to { _ -> user }))
        val benDao = mockAnswering(BenDao::class.java, mapOf("getBen" to { _ -> ben }))
        val tbDao = mockAnswering(TBDao::class.java, mapOf(
            "getTbDiagnosticsByBenId" to { _ -> stored },
            "saveTbDiagnostics" to { invocation ->
                savedRows.add(invocation.arguments[0] as TBDiagnosticsCache)
                1L
            }
        ))
        val api = mockAnswering(AmritApiService::class.java, mapOf(
            "pushDiagnosticOrder" to { _ ->
                Response.success(
                    OrderPushedResponse(
                        statusCode = 200,
                        data = OrderPushedData(externalOrderId = "new-mtb", providerOrderId = "new-mtb", status = serverStatus)
                    )
                )
            }
        ))
        return TBRepo(
            tbDao, benDao, preferenceDao, mock(UserRepo::class.java), api, mock(InAppDb::class.java),
            mock(DrugMasterRepo::class.java), mock(SyncRetryStore::class.java), mock(Context::class.java)
        )
    }

    /** The previous order's "RIF not conducted" must not carry over to the new MTB order. */
    @Test
    fun newMtbOrderClearsThePreviousRifCloseReason() = runBlocking {
        val closed = TBDiagnosticsCache(benId = benId).copy(
            trueNatOrderId = "old-mtb", trueNatOrderStatus = OrderStatus.CLOSED.name, naatResult = "TB Positive",
            rifOrderId = "old-mtb", rifOrderStatus = OrderStatus.CLOSED.name,
            reasonNotConductedRif = "Other", reasonNotConductedRifOther = "Machine down",
            trueNatManualResultPendingSync = true, rifManualResultPendingSync = true,
            syncState = SyncState.SYNCED
        )
        val repo = repoForOrderPush(closed, serverStatus = "PENDING")

        val response = repo.createOrder(benId, "SPUTUM_TRUENAT")

        assertTrue(response is NetworkResponse.Success)
        val row = savedRows.first()
        assertEquals("new-mtb", row.trueNatOrderId)
        assertEquals(OrderStatus.PENDING.name, row.trueNatOrderStatus)
        assertNull(row.naatResult)
        assertNull(row.rifOrderId)
        assertNull(row.rifOrderStatus)
        assertNull(row.reasonNotConductedRif)
        assertNull(row.reasonNotConductedRifOther)
        // The old order's pending results must not be replayed onto the new order.
        assertEquals(false, row.trueNatManualResultPendingSync)
        assertEquals(false, row.rifManualResultPendingSync)
        // A new order is a new test, not TB confirmed until its result.
        assertEquals(false, row.isTBConfirmed)
        assertEquals(false, row.isConfirmed)
    }

    @Test
    fun newMtbOrderStaysConfirmedOnAPositiveLiquidCulture() = runBlocking {
        val stored = TBDiagnosticsCache(benId = benId).copy(
            trueNatOrderId = "old-mtb", trueNatOrderStatus = OrderStatus.CLOSED.name, naatResult = "TB Positive",
            liquidCultureResult = "Positive", isTBConfirmed = true, isConfirmed = true
        )
        val repo = repoForOrderPush(stored, serverStatus = "PENDING")

        repo.createOrder(benId, "SPUTUM_TRUENAT")

        assertEquals(true, savedRows.first().isTBConfirmed)
    }

    /** A declined sputum referral is stored CLOSED and not done, not as a pending test. */
    @Test
    fun declinedSputumReferralIsStoredClosedAndNotConducted() = runBlocking {
        val repo = repoForOrderPush(TBDiagnosticsCache(benId = benId), serverStatus = "CLOSED")

        val response = repo.createOrder(benId, "SPUTUM_TRUENAT", reasonForRefusal = "Patient refused")

        assertTrue(response is NetworkResponse.Success)
        val row = savedRows.first()
        assertEquals(OrderStatus.CLOSED.name, row.trueNatOrderStatus)
        assertEquals(false, row.isNaatConducted)
        assertEquals(false, row.isReferredForSputum)
    }
}
