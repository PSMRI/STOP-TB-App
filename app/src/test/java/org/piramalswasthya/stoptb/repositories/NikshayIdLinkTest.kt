package org.piramalswasthya.stoptb.repositories

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.Mockito.mock
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.anyString
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever
import org.piramalswasthya.stoptb.database.room.InAppDb
import org.piramalswasthya.stoptb.database.room.dao.BenDao
import org.piramalswasthya.stoptb.database.room.dao.TBDao
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.model.TBSuspectedCache
import org.piramalswasthya.stoptb.network.AmritApiService
import org.piramalswasthya.stoptb.repositories.tb.BenIdLocks
import org.piramalswasthya.stoptb.repositories.tb.DeviceIntegrationRepo
import org.piramalswasthya.stoptb.repositories.tb.DiagnosticOrderRepo
import org.piramalswasthya.stoptb.repositories.tb.GeneralOpdRepo
import org.piramalswasthya.stoptb.repositories.tb.TbLocalRepo
import org.piramalswasthya.stoptb.repositories.tb.TbPullRepo
import org.piramalswasthya.stoptb.repositories.tb.TbPushRepo

/** The UI reads the Nikshay ID from BENEFICIARY, so saving a TB record must copy it there. */
class NikshayIdLinkTest {

    private val benId = 422455468513L
    private val benDao: BenDao = mock(BenDao::class.java)
    private val tbDao: TBDao = mock(TBDao::class.java, RETURNS_DEEP_STUBS)

    // Saves live in TbLocalRepo and the backfill in TbPullRepo (split out of TBRepo).
    private val local = TbLocalRepo(tbDao, benDao, BenIdLocks(), mock(InAppDb::class.java))
    private val pull = TbPullRepo(
        tbDao, benDao, mock(PreferenceDao::class.java), mock(UserRepo::class.java),
        mock(AmritApiService::class.java), mock(InAppDb::class.java), mock(Context::class.java),
        local, mock(GeneralOpdRepo::class.java), mock(DiagnosticOrderRepo::class.java),
        mock(DeviceIntegrationRepo::class.java), mock(TbPushRepo::class.java), BenIdLocks()
    )

    @Test
    fun savingSuspectedCopiesNikshayIdToBeneficiary() = runBlocking<Unit> {
        val suspected = TBSuspectedCache(benId = benId, nikshayId = "  NK12345 ")
        whenever(tbDao.saveTbSuspected(suspected)).thenReturn(1L)
        local.saveTBSuspected(suspected)
        verify(benDao).updateNikshayId(benId, "NK12345")
    }

    @Test
    fun blankOrPlaceholderNikshayIdIsNotCopied() = runBlocking<Unit> {
        for (value in listOf(null, "", "   ", "N/A", "n/a")) {
            val suspected = TBSuspectedCache(benId = benId, nikshayId = value)
            whenever(tbDao.saveTbSuspected(suspected)).thenReturn(1L)
            local.saveTBSuspected(suspected)
        }
        verify(benDao, never()).updateNikshayId(anyLong(), anyString())
    }

    @Test
    fun backfillRunsOncePerProcess() = runBlocking<Unit> {
        whenever(benDao.backfillNikshayIdFromTbRecords()).thenReturn(3)
        pull.backfillNikshayIdsOnce()
        pull.backfillNikshayIdsOnce()
        verify(benDao, times(1)).backfillNikshayIdFromTbRecords()
    }

    @Test
    fun failedBackfillIsRetriedOnTheNextPull() = runBlocking<Unit> {
        whenever(benDao.backfillNikshayIdFromTbRecords())
            .thenThrow(IllegalStateException("db busy"))
            .thenReturn(0)
        pull.backfillNikshayIdsOnce()
        pull.backfillNikshayIdsOnce()
        verify(benDao, times(2)).backfillNikshayIdFromTbRecords()
    }
}
