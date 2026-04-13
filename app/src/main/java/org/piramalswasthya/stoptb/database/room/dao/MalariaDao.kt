package org.piramalswasthya.stoptb.database.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.model.MalariaConfirmedCasesCache
import org.piramalswasthya.stoptb.model.MalariaScreeningCache

@Dao
interface MalariaDao {

    @Query("SELECT * FROM MALARIA_SCREENING WHERE benId =:benId limit 1")
    suspend fun getMalariaScreening(benId: Long): MalariaScreeningCache?

    @Query("SELECT * FROM MALARIA_SCREENING WHERE benId = :benId ORDER BY visitId DESC")
    fun getAllVisitsForBen(benId: Long): Flow<List<MalariaScreeningCache>>

    @Query("SELECT * FROM MALARIA_SCREENING WHERE benId = :benId ORDER BY visitId DESC LIMIT 1")
    fun getLatestVisitForBen(benId: Long): MalariaScreeningCache?

    @Query("SELECT MAX(visitId) FROM MALARIA_SCREENING WHERE benId = :benId")
    suspend fun getLastVisitIdForBen(benId: Long): Long?

    @Query("SELECT * FROM MALARIA_SCREENING WHERE benId =:benId and (caseDate = :visitDate or caseDate = :visitDateGMT) limit 1")
    suspend fun getMalariaScreening(benId: Long, visitDate: Long, visitDateGMT: Long): MalariaScreeningCache?

    @Query("SELECT * FROM MALARIA_SCREENING WHERE syncState = :syncState")
    suspend fun getMalariaScreening(syncState: SyncState): List<MalariaScreeningCache>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMalariaScreening(malariaScreeningCache: MalariaScreeningCache)

    @Query("SELECT * FROM MALARIA_CONFIRMED WHERE benId =:benId limit 1")
    suspend fun getMalariaConfirmed(benId: Long): MalariaConfirmedCasesCache?

    @Query("SELECT * FROM MALARIA_CONFIRMED WHERE benId =:benId and (dateOfDiagnosis = :visitDate or dateOfDiagnosis = :visitDateGMT) limit 1")
    suspend fun getMalariaConfirmed(benId: Long, visitDate: Long, visitDateGMT: Long): MalariaConfirmedCasesCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMalariaConfirmed(tbSuspectedCache: MalariaConfirmedCasesCache)

    @Query("SELECT * FROM MALARIA_CONFIRMED WHERE syncState = :syncState")
    suspend fun getMalariaConfirmed(syncState: SyncState): List<MalariaConfirmedCasesCache>

    @Query("UPDATE MALARIA_SCREENING SET syncState = 0 WHERE syncState = 1")
    suspend fun resetScreeningSyncingToUnsynced()

    @Query("UPDATE MALARIA_CONFIRMED SET syncState = 0 WHERE syncState = 1")
    suspend fun resetConfirmedSyncingToUnsynced()
}