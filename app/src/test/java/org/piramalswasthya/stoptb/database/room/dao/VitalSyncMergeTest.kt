package org.piramalswasthya.stoptb.database.room.dao

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.model.BenIdSyncState
import org.piramalswasthya.stoptb.model.VitalCache

class VitalSyncMergeTest {
    private class MemoryDao(var row: VitalCache?) : VitalDao {
        var writes = 0
        override suspend fun getVitals(benId: Long) = row?.takeIf { it.benId == benId }
        override suspend fun getVitals(syncState: SyncState) = listOfNotNull(row?.takeIf { it.syncState == syncState })
        override suspend fun saveVitals(vitalCache: VitalCache) { row = vitalCache; writes++ }
        override fun getAllVitalBenIds(): Flow<List<Long>> = flowOf(emptyList())
        override fun getVitalBenIdSyncStates(): Flow<List<BenIdSyncState>> = flowOf(emptyList())
        override fun getVitalBenIdsBySyncState(syncState: SyncState): Flow<List<Long>> = flowOf(emptyList())
        override fun getVitalBenIdSyncStatesIn(benIds: Collection<Long>): Flow<List<BenIdSyncState>> = flowOf(emptyList())
    }

    @Test fun unchangedRecordReportsActualSyncAndWritesOnce() = runBlocking {
        val pushed = VitalCache(benId = 1L, benRegId = 2L, capturedAt = 100L, pulseRate = 70)
        val dao = MemoryDao(pushed)
        assertTrue(dao.markSyncedIfUnchanged(pushed))
        assertEquals(SyncState.SYNCED, dao.row?.syncState)
        assertEquals(1, dao.writes)
    }

    @Test fun newerLocalEditIsNeitherOverwrittenNorReportedSynced() = runBlocking {
        val pushed = VitalCache(benId = 1L, benRegId = 2L, capturedAt = 100L, pulseRate = 70)
        val edited = pushed.copy(pulseRate = 85, capturedAt = 200L)
        val dao = MemoryDao(edited)
        assertFalse(dao.markSyncedIfUnchanged(pushed))
        assertEquals(edited, dao.row)
        assertEquals(0, dao.writes)
    }

    @Test fun deletedRecordIsNotRecreatedOrReportedSynced() = runBlocking {
        val dao = MemoryDao(null)
        assertFalse(dao.markSyncedIfUnchanged(VitalCache(1L, 2L)))
        assertNull(dao.row)
        assertEquals(0, dao.writes)
    }
}
