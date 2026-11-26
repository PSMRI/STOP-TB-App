package org.piramalswasthya.stoptb.database.room.dao

import androidx.room.*
import org.piramalswasthya.stoptb.database.room.BeneficiaryIdsAvail

@Dao
interface BeneficiaryIdsAvailDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg idEntry: BeneficiaryIdsAvail)

    @Query("SELECT COUNT(*) FROM BEN_ID_LIST")
    suspend fun count(): Int

    @Query("SELECT * FROM BEN_ID_LIST WHERE userId=:userId order by benId desc LIMIT 1")
    suspend fun getEntry(userId: Int): BeneficiaryIdsAvail

    @Delete
    suspend fun delete(idEntry: BeneficiaryIdsAvail)

    @Query("SELECT MIN(benId) FROM BEN_ID_LIST")
    suspend fun getMinBenId(): Long?


}