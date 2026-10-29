package org.piramalswasthya.stoptb.repositories

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*
import org.piramalswasthya.stoptb.configuration.GeneralOpdMasterConfig
import org.piramalswasthya.stoptb.database.room.InAppDb
import org.piramalswasthya.stoptb.database.room.dao.BenDao
import org.piramalswasthya.stoptb.database.room.dao.TBDao
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.network.AmritApiService
import org.piramalswasthya.stoptb.model.GeneralOpdCache

class GeneralOpdStaticMasterModeTest {
    @Test
    fun staticDrugsNeverReadOrWriteApiCacheOrCallNetworkEvenOnRefresh() = runBlocking {
        assertFalse(GeneralOpdMasterConfig.USE_API_MASTERS)
        val db = mock(InAppDb::class.java)
        val preferences = mock(PreferenceDao::class.java)
        val api = mock(AmritApiService::class.java)
        val repo = DrugMasterRepo(db, preferences, api)

        val before = repo.getCachedMasters()
        assertEquals(9, before.items.size)
        assertEquals(3, before.forms.size)
        assertEquals(8, before.frequencies.size)
        assertEquals(3, before.durationUnits.size)
        assertEquals((900001..900009).toSet(), before.items.map { it.itemId }.toSet())
        assertFalse(repo.refreshMasters())
        assertEquals(before, repo.getCachedMasters())
        verifyNoInteractions(db, preferences, api)
    }

    @Test
    fun staticChiefComplaintsAndCategoriesSkipAllMasterCacheAndNetworkAccess() = runBlocking {
        val db = mock(InAppDb::class.java)
        val preferences = mock(PreferenceDao::class.java)
        val api = mock(AmritApiService::class.java)
        val repo = TBRepo(
            mock(TBDao::class.java, RETURNS_DEEP_STUBS), mock(BenDao::class.java), preferences,
            mock(UserRepo::class.java), api, db, mock(DrugMasterRepo::class.java), mock(Context::class.java)
        )

        // Empty master names deliberately select the dataset's static resource list.
        assertTrue(repo.getCachedChiefComplaintNames().isEmpty())
        assertFalse(repo.refreshChiefComplaintMasters())
        assertFalse(repo.refreshVisitCategories())
        verifyNoInteractions(db, preferences, api)
    }

    @Test
    fun pendingComplaintIdsResolveWithoutCacheAndSavedIdsArePreserved() = runBlocking {
        val db = mock(InAppDb::class.java)
        val preferences = mock(PreferenceDao::class.java)
        val api = mock(AmritApiService::class.java)
        val repo = TBRepo(
            mock(TBDao::class.java, RETURNS_DEEP_STUBS), mock(BenDao::class.java), preferences,
            mock(UserRepo::class.java), api, db, mock(DrugMasterRepo::class.java), mock(Context::class.java)
        )
        val pending = GeneralOpdCache(benId = 1L, chiefComplaints = listOf("Fever", "Cough"))
        assertEquals(listOf(1, 9), repo.resolveChiefComplaintIds(pending))
        assertEquals(listOf(167, 92), repo.resolveChiefComplaintIds(pending.copy(chiefComplaintIds = listOf(167, 92))))
        assertNull(repo.resolveChiefComplaintIds(pending.copy(chiefComplaints = listOf("Unknown complaint"))))
        assertEquals(emptyList<Int>(), repo.resolveChiefComplaintIds(pending.copy(chiefComplaints = emptyList())))
        verifyNoInteractions(db, preferences, api)
    }
}
