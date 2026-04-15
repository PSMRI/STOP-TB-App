package org.piramalswasthya.stoptb.database.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.model.TBConfirmedTreatmentCache
import org.piramalswasthya.stoptb.model.TBScreeningCache
import org.piramalswasthya.stoptb.model.TBSuspectedCache

@Dao
interface TBDao {

    @Query("SELECT * FROM TB_SCREENING WHERE benId =:benId limit 1")
    suspend fun getTbScreening(benId: Long): TBScreeningCache?

    @Query("SELECT * FROM TB_SCREENING WHERE benId =:benId and (visitDate = :visitDate or visitDate = :visitDateGMT) limit 1")
    suspend fun getTbScreening(benId: Long, visitDate: Long, visitDateGMT: Long): TBScreeningCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTbScreening(tbScreeningCache: TBScreeningCache)

    @Query("SELECT * FROM TB_SUSPECTED WHERE benId =:benId limit 1")
    suspend fun getTbSuspected(benId: Long): TBSuspectedCache?

    @Query("""
    SELECT *
    FROM TB_CONFIRMED_TREATMENT
    WHERE benId = :benId
      AND followUpDate IS NOT NULL
    ORDER BY followUpDate DESC
    LIMIT 1
""")    suspend fun getTbConfirmed(benId: Long): TBConfirmedTreatmentCache?

    @Query("""
    SELECT *
    FROM TB_CONFIRMED_TREATMENT
    WHERE benId = :benId
      AND followUpDate IS NOT NULL
    ORDER BY followUpDate DESC
    limit 1
""")    suspend fun getALLTbConfirmed(benId: Long): TBConfirmedTreatmentCache?

    @Query("SELECT * FROM TB_SUSPECTED WHERE benId =:benId and (visitDate = :visitDate or visitDate = :visitDateGMT) limit 1")
    suspend fun getTbSuspected(benId: Long, visitDate: Long, visitDateGMT: Long): TBSuspectedCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTbSuspected(tbSuspectedCache: TBSuspectedCache)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTbConfirmed(tbConfirmedCache: TBConfirmedTreatmentCache)



    @Query("SELECT * FROM TB_SCREENING WHERE  syncState = :syncState")
    suspend fun getTBScreening(syncState: SyncState): List<TBScreeningCache>

    @Query("SELECT * FROM TB_SUSPECTED WHERE  syncState = :syncState")
    suspend fun getTbSuspected(syncState: SyncState): List<TBSuspectedCache>

    @Query("SELECT * FROM TB_CONFIRMED_TREATMENT WHERE  syncState = :syncState")
    suspend fun getTbConfirmed(syncState: SyncState): List<TBConfirmedTreatmentCache>

    @Query("SELECT * FROM TB_CONFIRMED_TREATMENT WHERE benId = :benId ORDER BY followUpDate DESC")
    suspend fun getAllFollowUpsForBeneficiary(benId: Long): List<TBConfirmedTreatmentCache>

    @Query("UPDATE TB_SCREENING SET syncState = 0 WHERE syncState = 1")
    suspend fun resetScreeningSyncingToUnsynced()

    @Query("UPDATE TB_SUSPECTED SET syncState = 0 WHERE syncState = 1")
    suspend fun resetSuspectedSyncingToUnsynced()

    @Query("UPDATE TB_CONFIRMED_TREATMENT SET syncState = 0 WHERE syncState = 1")
    suspend fun resetConfirmedSyncingToUnsynced()

    // Dashboard queries - TB Screening count by gender with time + village filter
    @Query("""
        SELECT COUNT(*) FROM TB_SCREENING ts
        INNER JOIN beneficiary b ON b.beneficiaryId = ts.benId
        WHERE (:villageId = 0 OR b.loc_village_id = :villageId)
        AND (:startTime = 0 OR ts.visitDate >= :startTime)
        AND (:endTime = 0 OR ts.visitDate <= :endTime)
        AND (:gender = '' OR b.gender = :gender)
        AND (:isChild = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15))
    """)
    fun getDashboardTbScreeningCount(villageId: Int, startTime: Long, endTime: Long, gender: String, isChild: Int): Flow<Int>

    // Dashboard queries - TB Suspected count by gender with time + village filter
    @Query("""
        SELECT COUNT(*) FROM TB_SUSPECTED ts
        INNER JOIN beneficiary b ON b.beneficiaryId = ts.benId
        WHERE (:villageId = 0 OR b.loc_village_id = :villageId)
        AND (:startTime = 0 OR ts.visitDate >= :startTime)
        AND (:endTime = 0 OR ts.visitDate <= :endTime)
        AND (:gender = '' OR b.gender = :gender)
        AND (:isChild = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15))
    """)
    fun getDashboardTbSuspectedCount(villageId: Int, startTime: Long, endTime: Long, gender: String, isChild: Int): Flow<Int>

    // Dashboard queries - TB Confirmed count by gender with time + village filter
    @Query("""
        SELECT COUNT(*) FROM TB_SUSPECTED ts
        INNER JOIN beneficiary b ON b.beneficiaryId = ts.benId
        WHERE ts.isConfirmed = 1
        AND (:villageId = 0 OR b.loc_village_id = :villageId)
        AND (:startTime = 0 OR ts.visitDate >= :startTime)
        AND (:endTime = 0 OR ts.visitDate <= :endTime)
        AND (:gender = '' OR b.gender = :gender)
        AND (:isChild = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15))
    """)
    fun getDashboardTbConfirmedCount(villageId: Int, startTime: Long, endTime: Long, gender: String, isChild: Int): Flow<Int>

    // NIKSHAY IDs count with time + village filter
    @Query("""
        SELECT COUNT(*) FROM TB_SUSPECTED ts
        INNER JOIN beneficiary b ON b.beneficiaryId = ts.benId
        WHERE ts.nikshayId IS NOT NULL AND ts.nikshayId != ''
        AND (:villageId = 0 OR b.loc_village_id = :villageId)
        AND (:startTime = 0 OR ts.visitDate >= :startTime)
        AND (:endTime = 0 OR ts.visitDate <= :endTime)
    """)
    fun getDashboardNikshayCount(villageId: Int, startTime: Long, endTime: Long): Flow<Int>
}