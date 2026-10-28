package org.piramalswasthya.stoptb.database.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.model.VitalCache

@Dao
interface VitalDao {

    @Query("SELECT * FROM BEN_VITALS WHERE benId = :benId LIMIT 1")
    suspend fun getVitals(benId: Long): VitalCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveVitals(vitalCache: VitalCache)

    @Query("SELECT * FROM BEN_VITALS WHERE syncState = :syncState")
    suspend fun getVitals(syncState: SyncState): List<VitalCache>

    // Marks the pushed vital SYNCED only if the stored row is unchanged since it
    // was read for push; a newer edit stays UNSYNCED for the next push.
    @Transaction
    suspend fun markSyncedIfUnchanged(pushed: VitalCache) {
        val fresh = getVitals(pushed.benId) ?: return
        if (fresh.copy(syncState = SyncState.SYNCED) == pushed.copy(syncState = SyncState.SYNCED)) {
            saveVitals(fresh.copy(syncState = SyncState.SYNCED))
        }
    }

    @Query("SELECT benId FROM BEN_VITALS")
    fun getAllVitalBenIds(): Flow<List<Long>>

    @Query("SELECT benId FROM BEN_VITALS WHERE syncState = :syncState")
    fun getVitalBenIdsBySyncState(syncState: SyncState): Flow<List<Long>>
}
