package org.piramalswasthya.stoptb.database.room.dao

import androidx.room.Dao
import androidx.room.Query
import org.piramalswasthya.stoptb.model.LeprosyFollowUpCache

@Dao
interface LeprosyDao {
    @Query("SELECT * FROM LEPROSY_FOLLOW_UP WHERE benId = :benId ORDER BY visitNumber DESC, followUpDate DESC")
    suspend fun getAllFollowUpsForBeneficiary(benId: Long): List<LeprosyFollowUpCache>

    @Query("UPDATE LEPROSY_SCREENING SET syncState = 0 WHERE syncState = 1")
    suspend fun resetScreeningSyncingToUnsynced()

    @Query("UPDATE LEPROSY_FOLLOW_UP SET syncState = 0 WHERE syncState = 1")
    suspend fun resetFollowUpSyncingToUnsynced()
}
