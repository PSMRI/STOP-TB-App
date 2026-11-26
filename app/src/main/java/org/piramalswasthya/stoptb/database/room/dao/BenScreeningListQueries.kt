package org.piramalswasthya.stoptb.database.room.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.helpers.Konstants
import org.piramalswasthya.stoptb.model.*

/**
 * Disease screening, NCD, immunization and other programme lists.
 *
 * Part of [BenDao] (which extends this interface); split out only to keep the DAO
 * readable. Room generates these queries as part of BenDao_Impl - SQL and callers unchanged.
 */
interface BenScreeningListQueries {

    @Transaction
    @Query("SELECT * FROM BEN_BASIC_CACHE where villageId = :selectedVillage and hhId = :hhId  and isDeactivate=0 ORDER BY BEN_BASIC_CACHE.updatedDate DESC")
    fun getAllMalariaScreeningBen(selectedVillage: Int,hhId: Long): Flow<List<BenWithMalariaScreeningCache>>

    @Transaction
    @Query("SELECT * FROM BEN_BASIC_CACHE where villageId = :selectedVillage and hhId = :hhId and isDeactivate=0 ORDER BY BEN_BASIC_CACHE.updatedDate DESC")
    fun getAllAESScreeningBen(selectedVillage: Int,hhId: Long): Flow<List<BenWithAESScreeningCache>>

    @Transaction
    @Query("SELECT * FROM BEN_BASIC_CACHE where villageId = :selectedVillage and hhId = :hhId and isDeactivate=0 ORDER BY BEN_BASIC_CACHE.updatedDate DESC")
    fun getAllKALAZARScreeningBen(selectedVillage: Int,hhId: Long): Flow<List<BenWithKALAZARScreeningCache>>

    @Transaction
    @Query("SELECT * FROM BEN_BASIC_CACHE where villageId = :selectedVillage and hhId = :hhId and isDeactivate=0 ORDER BY BEN_BASIC_CACHE.updatedDate DESC")
    fun getAllLeprosyScreeningBen(selectedVillage: Int,hhId: Long): Flow<List<BenWithLeprosyScreeningCache>>


    @Transaction
    @Query("""
    SELECT b.*, l.leprosySymptomsPosition 
    FROM BEN_BASIC_CACHE b 
    INNER JOIN LEPROSY_SCREENING l ON b.benId = l.benId 
    WHERE b.villageId = :selectedVillage
    AND b.isDeactivate=0
    AND l.leprosySymptomsPosition = :symptomsPosition
    AND l.isConfirmed = 0
        ORDER BY b.updatedDate DESC
    """)
    fun getLeprosyScreeningBenBySymptoms(selectedVillage: Int,  symptomsPosition: Int): Flow<List<BenWithLeprosyScreeningCache>>

    @Transaction
    @Query("""
    SELECT b.*, l.isConfirmed
    FROM BEN_BASIC_CACHE b
    INNER JOIN LEPROSY_SCREENING l ON b.benId = l.benId
    WHERE b.villageId = :selectedVillage
    AND b.isDeactivate=0
      AND l.isConfirmed = 1
        ORDER BY b.updatedDate DESC
    """)
    fun getConfirmedLeprosyCases(
        selectedVillage: Int
    ): Flow<List<BenWithLeprosyScreeningCache>>

    @Transaction
    @Query("SELECT * FROM BEN_BASIC_CACHE where villageId = :selectedVillage and hhId = :hhId and isDeactivate=0 ORDER BY BEN_BASIC_CACHE.updatedDate DESC")
    fun getAllFilariaScreeningBen(selectedVillage: Int,hhId: Long): Flow<List<BenWithFilariaScreeningCache>>

    @Transaction
    @Query("SELECT * FROM BEN_BASIC_CACHE b where CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER)  >= :min and b.reproductiveStatusId!=2 and b.isDeactivate=0 and b.villageId=:selectedVillage group by b.benId order by b.regDate desc")
    fun getBenWithCbac(
        selectedVillage: Int, min: Int = Konstants.minAgeForNcd
    ): Flow<List<BenWithCbacCache>>

    @Query("""
        SELECT * FROM BEN_BASIC_CACHE
        WHERE villageId = :selectedVillage AND isDeactivate = 0 AND screeningStatus = 'UNSCREENED'
        ORDER BY BEN_BASIC_CACHE.updatedDate DESC
    """)
    fun getUnscreenedList(selectedVillage: Int): Flow<List<BenBasicCache>>

    /**
     * PR 6: the Unscreened screen, paged. Same rows as [getUnscreenedList] minus beneficiaries
     * with a TB screening row (the screen dropped those in Kotlin against a whole-table id list),
     * searched in SQL on ID, full name and mobile as before; `query` lowercased, '' = all.
     */
    @Query("""
        SELECT * FROM BEN_BASIC_CACHE b
        WHERE b.villageId = :selectedVillage AND b.isDeactivate = 0 AND b.screeningStatus = 'UNSCREENED'
        AND NOT EXISTS (SELECT 1 FROM TB_SCREENING s WHERE s.benId = b.benId)
        AND (:query = ''
            OR CAST(b.benId AS TEXT) LIKE '%' || :query || '%'
            OR (IFNULL(b.benName, '') || ' ' || IFNULL(b.benSurname, '')) LIKE '%' || :query || '%'
            OR CAST(b.mobileNo AS TEXT) LIKE '%' || :query || '%')
        ORDER BY b.updatedDate DESC, b.benId DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getUnscreenedPage(selectedVillage: Int, query: String, limit: Int, offset: Int): List<BenBasicCache>

    /** Count for the same rows as [getUnscreenedPaged] with no search. */
    @Query("""
        SELECT COUNT(*) FROM BEN_BASIC_CACHE b
        WHERE b.villageId = :selectedVillage AND b.isDeactivate = 0 AND b.screeningStatus = 'UNSCREENED'
        AND NOT EXISTS (SELECT 1 FROM TB_SCREENING s WHERE s.benId = b.benId)
    """)
    fun getUnscreenedNotTbScreenedCount(selectedVillage: Int): Flow<Int>

    @Query("UPDATE BENEFICIARY SET screeningStatus = :status, syncState = :unsynced, processed = 'U', serverUpdatedStatus = 2 WHERE beneficiaryId = :benId")
    suspend fun updateScreeningStatus(benId: Long, status: String, unsynced: SyncState = SyncState.UNSYNCED)
}
