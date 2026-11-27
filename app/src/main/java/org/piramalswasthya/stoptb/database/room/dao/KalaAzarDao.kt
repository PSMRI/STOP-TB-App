package org.piramalswasthya.stoptb.database.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.piramalswasthya.stoptb.model.KalaAzarScreeningCache
@Dao
interface KalaAzarDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTbSuspected(tbSuspectedCache: KalaAzarScreeningCache)

    @Query("UPDATE KALAZAR_SCREENING SET syncState = 0 WHERE syncState = 1")
    suspend fun resetSyncingToUnsynced()
}