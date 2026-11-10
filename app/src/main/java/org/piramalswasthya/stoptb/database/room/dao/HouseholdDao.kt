package org.piramalswasthya.stoptb.database.room.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.piramalswasthya.stoptb.model.HouseholdBasicCache
import org.piramalswasthya.stoptb.model.HouseholdCache

@Dao
interface HouseholdDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(vararg household: HouseholdCache)

    @Update
    suspend fun update(household: HouseholdCache)

    @Query("UPDATE HOUSEHOLD SET processed = :proccess , serverUpdatedStatus =:updateStatus WHERE householdId = :householdId")
    suspend fun updateHouseholdToSync(
        householdId: Long,
        proccess: String,
        updateStatus: Int
    )

    @Query("SELECT * FROM HOUSEHOLD WHERE isDraft = 1 LIMIT 1")
    suspend fun getDraftHousehold(): HouseholdCache?

    @Query("SELECT * FROM HOUSEHOLD WHERE isDraft = 0 and loc_village_Id = :selectedVillage")
    fun getAllHouseholds(selectedVillage: Int): Flow<List<HouseholdCache>>

    @Query("SELECT h.*, count(b.householdId) as numMembers FROM HOUSEHOLD as h left outer join BENEFICIARY b on b.householdId = h.householdId WHERE h.isDraft = 0 and h.loc_village_Id = :selectedVillage group by h.householdId")
    fun getAllHouseholdWithNumMembers(selectedVillage: Int): Flow<List<HouseholdBasicCache>>

    /**
     * PR 6: All Household, paged. Same rows as [getAllHouseholdWithNumMembers]; the search (ID,
     * head's full name, head's phone) and the sort (active first, newest first) the screen ran
     * in Kotlin over the whole list now run here. `query` lowercased, '' = all. The household
     * id tie-break keeps the order the list had from the GROUP BY.
     */
    @Query("""
        SELECT h.*, COUNT(b.householdId) AS numMembers FROM HOUSEHOLD AS h
        LEFT OUTER JOIN BENEFICIARY b ON b.householdId = h.householdId
        WHERE h.isDraft = 0 AND h.loc_village_Id = :selectedVillage
        AND (:query = ''
            OR CAST(h.householdId AS TEXT) LIKE '%' || :query || '%'
            OR (IFNULL(h.fam_familyHeadName, '') || ' ' || IFNULL(h.fam_familyName, '')) LIKE '%' || :query || '%'
            OR CAST(h.fam_familyHeadPhoneNo AS TEXT) LIKE '%' || :query || '%')
        GROUP BY h.householdId
        ORDER BY h.isDeactivate ASC, h.createdTimeStamp DESC, h.householdId ASC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getHouseholdsWithNumMembersPage(selectedVillage: Int, query: String, limit: Int, offset: Int): List<HouseholdBasicCache>

    @Query("SELECT h.*, count(b.householdId) as numMembers FROM HOUSEHOLD as h left outer join BENEFICIARY b on b.householdId = h.householdId WHERE h.isDraft = 0 and h.loc_village_Id = :selectedVillage AND h.registrationType = 'Yes' group by h.householdId")
    fun getAllHouseholdForAshaFamilyMembers(selectedVillage: Int): Flow<List<HouseholdBasicCache>>

    @Query("SELECT COUNT(*) FROM HOUSEHOLD WHERE isDraft = 0 and loc_village_Id = :selectedVillage and isDeactivate =0")
    fun getAllHouseholdsCount(selectedVillage: Int): Flow<Int>

    @Query("SELECT * FROM HOUSEHOLD WHERE householdId =:hhId LIMIT 1")
    suspend fun getHousehold(hhId: Long): HouseholdCache?

    @Query("DELETE  FROM HOUSEHOLD WHERE isDraft=1")
    suspend fun deleteDraftHousehold()

    @Query("UPDATE HOUSEHOLD SET processed = 'P' WHERE householdId in(:hhId)")
    suspend fun householdSyncedWithServer(vararg hhId: Long)

    @Query("UPDATE HOUSEHOLD SET householdId  = :newId WHERE householdId = :oldId ")
    suspend fun substituteHouseholdId(oldId: Long, newId: Long)

    @Query("SELECT * FROM household WHERE isDraft = 0 AND processed = 'N'")
    suspend fun getAllUnprocessedHousehold(): List<HouseholdCache>

}