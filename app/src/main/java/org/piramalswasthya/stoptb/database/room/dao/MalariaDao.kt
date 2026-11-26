package org.piramalswasthya.stoptb.database.room.dao

import androidx.room.Dao
import androidx.room.Query

@Dao
interface MalariaDao {

    @Query("UPDATE MALARIA_SCREENING SET syncState = 0 WHERE syncState = 1")
    suspend fun resetScreeningSyncingToUnsynced()

    @Query("UPDATE MALARIA_CONFIRMED SET syncState = 0 WHERE syncState = 1")
    suspend fun resetConfirmedSyncingToUnsynced()
}