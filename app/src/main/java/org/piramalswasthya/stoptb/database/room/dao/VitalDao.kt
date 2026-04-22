package org.piramalswasthya.stoptb.database.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Query("SELECT benId FROM BEN_VITALS")
    fun getAllVitalBenIds(): Flow<List<Long>>
}
