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
import org.piramalswasthya.stoptb.database.shared_preferences.SyncRetryStore
import org.piramalswasthya.stoptb.model.TBSuspectedCache
import org.piramalswasthya.stoptb.network.AmritApiService

/** The UI reads the Nikshay ID from BENEFICIARY, so saving a TB record must copy it there. */
class NikshayIdLinkTest {

    private val benId = 422455468513L
    private val benDao: BenDao = mock(BenDao::class.java)
    private val tbDao: TBDao = mock(TBDao::class.java, RETURNS_DEEP_STUBS)

    private val repo = TBRepo(
        tbDao, benDao, mock(PreferenceDao::class.java),
        mock(UserRepo::class.java), mock(AmritApiService::class.java), mock(InAppDb::class.java),
        mock(DrugMasterRepo::class.java), mock(SyncRetryStore::class.java), mock(Context::class.java)
    )

    @Test
    fun savingSuspectedCopiesNikshayIdToBeneficiary() = runBlocking<Unit> {
        val suspected = TBSuspectedCache(benId = benId, nikshayId = "  NK12345 ")
        whenever(tbDao.saveTbSuspected(suspected)).thenReturn(1L)
        repo.saveTBSuspected(suspected)
        verify(benDao).updateNikshayId(benId, "NK12345")
    }

    @Test
    fun blankOrPlaceholderNikshayIdIsNotCopied() = runBlocking<Unit> {
        for (value in listOf(null, "", "   ", "N/A", "n/a")) {
            val suspected = TBSuspectedCache(benId = benId, nikshayId = value)
            whenever(tbDao.saveTbSuspected(suspected)).thenReturn(1L)
            repo.saveTBSuspected(suspected)
        }
        verify(benDao, never()).updateNikshayId(anyLong(), anyString())
    }

    @Test
    fun backfillRunsOncePerProcess() = runBlocking<Unit> {
        whenever(benDao.backfillNikshayIdFromTbRecords()).thenReturn(3)
        repo.backfillNikshayIdsOnce()
        repo.backfillNikshayIdsOnce()
        verify(benDao, times(1)).backfillNikshayIdFromTbRecords()
    }

    @Test
    fun failedBackfillIsRetriedOnTheNextPull() = runBlocking<Unit> {
        whenever(benDao.backfillNikshayIdFromTbRecords())
            .thenThrow(IllegalStateException("db busy"))
            .thenReturn(0)
        repo.backfillNikshayIdsOnce()
        repo.backfillNikshayIdsOnce()
        verify(benDao, times(2)).backfillNikshayIdFromTbRecords()
    }
}
