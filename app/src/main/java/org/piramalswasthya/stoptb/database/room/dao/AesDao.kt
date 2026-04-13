package org.piramalswasthya.stoptb.database.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.model.AESScreeningCache

@Dao
interface AesDao {
    @Query("SELECT * FROM AES_SCREENING WHERE benId =:benId limit 1")
    suspend fun getAESScreening(benId: Long): AESScreeningCache?

    @Query("SELECT * FROM AES_SCREENING WHERE benId =:benId and (visitDate = :visitDate or visitDate = :visitDateGMT) limit 1")
    suspend fun getAESScreening(benId: Long, visitDate: Long, visitDateGMT: Long): AESScreeningCache?

    @Query("SELECT * FROM AES_SCREENING WHERE  syncState = :syncState")
    suspend fun getAESScreening(syncState: SyncState): List<AESScreeningCache>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAESScreening(malariaScreeningCache: AESScreeningCache)

    @Query("UPDATE AES_SCREENING SET syncState = 0 WHERE syncState = 1")
    suspend fun resetSyncingToUnsynced()
}