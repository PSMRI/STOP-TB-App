package org.piramalswasthya.stoptb.repositories

import android.content.Context
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyBlocking
import org.piramalswasthya.stoptb.database.room.InAppDb
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.database.room.dao.BenDao
import org.piramalswasthya.stoptb.database.room.dao.TBDao
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.helpers.NetworkResponse
import org.piramalswasthya.stoptb.model.BenRegCache
import org.piramalswasthya.stoptb.model.LocationEntity
import org.piramalswasthya.stoptb.model.LocationRecord
import org.piramalswasthya.stoptb.model.OrderStatus
import org.piramalswasthya.stoptb.model.TBDiagnosticsCache
import org.piramalswasthya.stoptb.model.User
import org.piramalswasthya.stoptb.network.AmritApiService
import retrofit2.Response

/**
 * fetchBeneficiariesByStatus runs on every list open and every 60 s poll. Rewriting rows whose
 * status did not change invalidates every list/badge query on TB_DIAGNOSTICS — the main cause of
 * TrueNat/X-ray list lag at 1000+ beneficiaries.
 */
class TBRepoStatusSweepTest {

    private val tbDao: TBDao = mock()
    private val benDao: BenDao = mock()
    private val preferenceDao: PreferenceDao = mock()
    private val api: AmritApiService = mock()
    private lateinit var repo: TBRepo

    private val benId = 1L
    private val regId = 101L

    @Before
    fun setUp() {
        val user: User = mock { on { serviceMapId } doReturn 1 }
        val village: LocationEntity = mock { on { id } doReturn 10 }
        val location: LocationRecord = mock { on { this.village } doReturn village }
        val ben: BenRegCache = mock { on { beneficiaryId } doReturn benId }
        preferenceDao.stub {
            on { getLoggedInUser() } doReturn user
            on { getLocationRecord() } doReturn location
        }
        benDao.stub { onBlocking { getBenByRegId(regId) } doReturn ben }
        repo = TBRepo(
            tbDao, benDao, preferenceDao, mock(), api, mock<InAppDb>(), mock(), mock<Context>()
        )
    }

    private fun serverReturns(bucket: String) {
        val json = """{"success":true,"statusCode":200,"data":{"$bucket":[$regId]}}"""
        api.stub {
            onBlocking { getBeneficiariesByStatus(any(), any(), any()) } doReturn
                Response.success(json.toResponseBody())
        }
    }

    private fun stored(status: String) = TBDiagnosticsCache(
        id = 5,
        benId = benId,
        visitDate = 1L,
        isReferredForSputum = true,
        trueNatOrderStatus = status,
        trueNatOrderId = "ORD-1",
        syncState = SyncState.SYNCED
    )

    @Test
    fun awaitingResult_rowAlreadyPending_isNotRewritten() = runBlocking {
        serverReturns("awaitingProviderResult")
        tbDao.stub { onBlocking { getTbDiagnosticsByBenId(benId) } doReturn stored(OrderStatus.PENDING.name) }

        val result = repo.fetchBeneficiariesByStatus("SPUTUM_TRUENAT")

        assertThat(result).isInstanceOf(NetworkResponse.Success::class.java)
        verifyBlocking(tbDao, never()) { saveTbDiagnostics(any()) }
    }

    @Test
    fun awaitingResult_rowStatusChanged_isWrittenOnce() = runBlocking {
        serverReturns("awaitingProviderResult")
        tbDao.stub { onBlocking { getTbDiagnosticsByBenId(benId) } doReturn stored(OrderStatus.FAILED.name) }

        repo.fetchBeneficiariesByStatus("SPUTUM_TRUENAT")

        verifyBlocking(tbDao, times(1)) {
            saveTbDiagnostics(argThat { trueNatOrderStatus == OrderStatus.PENDING.name })
        }
    }

    @Test
    fun failedBucket_readsDiagnosticsTableOnce_andSkipsUnchangedRow() = runBlocking {
        val json = """{"success":true,"statusCode":200,"data":{"failed":[$regId,102,103]}}"""
        api.stub {
            onBlocking { getBeneficiariesByStatus(any(), any(), any()) } doReturn
                Response.success(json.toResponseBody())
        }
        val row = stored(OrderStatus.FAILED.name).copy(errorMsgTrueNat = "device error")
        tbDao.stub {
            onBlocking { getDiagnosticsList() } doReturn listOf(row)
            onBlocking { getTbDiagnosticsByBenId(benId) } doReturn row
        }

        repo.fetchBeneficiariesByStatus("SPUTUM_TRUENAT")

        verifyBlocking(tbDao, times(1)) { getDiagnosticsList() }
        verifyBlocking(tbDao, never()) { saveTbDiagnostics(any()) }
    }
}
