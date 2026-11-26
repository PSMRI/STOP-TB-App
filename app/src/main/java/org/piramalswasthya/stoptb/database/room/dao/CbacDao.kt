package org.piramalswasthya.stoptb.database.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import org.piramalswasthya.stoptb.model.CbacCache
import org.piramalswasthya.stoptb.model.CbacCachePush

@Dao
interface CbacDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(vararg cbacCache: CbacCache)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cbacCache: List<CbacCache>)
    @Update
    suspend fun update(vararg cbacCache: CbacCache)

    @Query("SELECT * FROM CBAC WHERE id = :cbacId LIMIT 1")
    suspend fun getCbacFromBenId(cbacId: Int): CbacCache?



    @Query("SELECT * FROM CBAC WHERE benId = :benId  order by fillDate desc LIMIT 1")
    suspend fun getLastFilledCbacFromBenId(benId: Long): CbacCache?


    @Query("SELECT c.*, b.householdId as hhId, b.gender as benGender FROM CBAC c join beneficiary b on c.benId= b.beneficiaryId WHERE c.processed in ('N','U') ")
    suspend fun getAllUnprocessedCbac(): List<CbacCachePush>

    @Query("UPDATE cbac SET isReffered = :status WHERE benId = :benId")
    suspend fun updateReferralStatus(benId: Long, status: Boolean)

    @Query("UPDATE CBAC SET syncState = 0 WHERE syncState = 1")
    suspend fun resetSyncingToUnsynced()
}