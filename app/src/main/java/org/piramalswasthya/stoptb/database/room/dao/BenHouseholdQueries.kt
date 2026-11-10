package org.piramalswasthya.stoptb.database.room.dao

import androidx.lifecycle.LiveData
import androidx.paging.PagingSource
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.helpers.Konstants
import org.piramalswasthya.stoptb.model.*

/**
 * Household membership and household-level lookups.
 *
 * Part of [BenDao] (which extends this interface); split out only to keep the DAO
 * readable. Room generates these queries as part of BenDao_Impl - SQL and callers unchanged.
 */
interface BenHouseholdQueries {

    @Query("SELECT * FROM BENEFICIARY WHERE isDraft = 1 and householdId =:hhId LIMIT 1")
    suspend fun getDraftBenKidForHousehold(hhId: Long): BenRegCache?

    @Query("""
        SELECT householdId AS hhId,
               TRIM(COALESCE(firstName, '') || ' ' || COALESCE(lastName, '')) AS fullName
        FROM BENEFICIARY
        WHERE isDraft = 0
          AND isDeactivate = 0
          AND loc_village_id = :selectedVillage
          AND householdId IS NOT NULL
          AND familyHeadRelationPosition = 19
    """)
    fun getHouseholdHeadNames(selectedVillage: Int): Flow<List<HouseholdHeadName>>

    @Query("SELECT * FROM BEN_BASIC_CACHE WHERE hhId = :hhId ORDER BY BEN_BASIC_CACHE.updatedDate DESC")
    fun getAllBasicBenForHousehold(hhId: Long): Flow<List<BenBasicCache>>

    @Query("SELECT * FROM BENEFICIARY WHERE householdId = :hhId")
    suspend fun getAllBenForHousehold(hhId: Long): List<BenRegCache>

    @Query("SELECT * FROM BENEFICIARY WHERE householdId = :hhId AND beneficiaryId != :selectedbenIdFromArgs AND (:firstName IS NULL OR :firstName = '' OR motherName LIKE :firstName || '%') order by age desc")
    suspend fun getChildBenForHousehold(hhId: Long, selectedbenIdFromArgs: Long, firstName: String?): List<BenRegCache>

    @Query("SELECT * FROM BEN_BASIC_CACHE WHERE reproductiveStatusId = 4 and hhId = :hhId ORDER BY BEN_BASIC_CACHE.updatedDate DESC")
    suspend fun getAllPNCMotherListFromHousehold(hhId: Long): List<BenBasicCache>
}
