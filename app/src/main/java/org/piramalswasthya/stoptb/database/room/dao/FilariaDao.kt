package org.piramalswasthya.stoptb.database.room.dao

import androidx.room.Dao
import androidx.room.Query

@Dao
interface FilariaDao {
    @Query("UPDATE FILARIA_SCREENING SET syncState = 0 WHERE syncState = 1")
    suspend fun resetSyncingToUnsynced()
}