package org.piramalswasthya.stoptb.database.room.dao

import androidx.lifecycle.LiveData
import androidx.paging.PagingSource
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.helpers.Konstants
import org.piramalswasthya.stoptb.model.*

data class VillageHeadcount(
    val population: Int = 0,
    val unscreened: Int = 0,
)

@Dao
interface BenDao :
    BenSearchQueries,
    BenCountQueries,
    BenSyncQueries,
    BenTbListQueries,
    BenHouseholdQueries,
    BenScreeningListQueries {

    /**
     * Insert, or update in place when the beneficiary already exists (PR 7.1). This was
     * @Insert(REPLACE), which for an existing row is DELETE + INSERT: every table referencing
     * BENEFICIARY with ON DELETE CASCADE (TB screening/suspected/diagnostics/confirmed, vitals,
     * OPD, CBAC, ...) lost that beneficiary's rows - e.g. editing a child in NewChildBenViewModel.
     * BENEFICIARY's only unique key is the primary key, so the stored row is the same as before.
     */
    @Upsert
    suspend fun upsert(vararg ben: BenRegCache)

    @Update
    suspend fun updateBen(ben: BenRegCache)

    @Query("UPDATE BENEFICIARY SET nikshayId = :nikshayId WHERE beneficiaryId = :benId")
    suspend fun updateNikshayId(benId: Long, nikshayId: String)

    @Query("UPDATE  BENEFICIARY SET isSpouseAdded = 1 , syncState = :unsynced ,processed = :proccess , serverUpdatedStatus =:updateStatus WHERE householdId = :householdId AND familyHeadRelationPosition = 19")
    suspend fun updateHofSpouseAdded(
        householdId: Long,
        unsynced: SyncState,
        proccess: String,
        updateStatus: Int
    )

    @Query(
        "UPDATE BENEFICIARY " +
                "SET isChildrenAdded = 1 , syncState = :unsynced , processed = :proccess , serverUpdatedStatus =:updateStatus WHERE householdId = :householdId AND beneficiaryId = :benId"
    )
    suspend fun updateBeneficiaryChildrenAdded(
        householdId: Long,
        benId: Long,
        unsynced: SyncState,
        proccess: String,
        updateStatus: Int
    )

    @Query(
        "UPDATE BENEFICIARY " +
                "SET fatherName = :benName , syncState = :unsynced , processed = :proccess , serverUpdatedStatus =:updateStatus WHERE householdId = :householdId AND fatherName = :parentName AND (familyHeadRelationPosition = 9 OR familyHeadRelationPosition = 10)"
    )
    suspend fun updateFatherInChildren(
        benName: String,
        householdId: Long,
        parentName: String,
        unsynced: SyncState,
        proccess: String,
        updateStatus: Int
    )

    @Query(
        "UPDATE BENEFICIARY " +
                "SET motherName = :benName , syncState = :unsynced , processed = :proccess , serverUpdatedStatus =:updateStatus WHERE householdId = :householdId AND motherName = :parentName AND (familyHeadRelationPosition = 9 OR familyHeadRelationPosition = 10)"
    )
    suspend fun updateMotherInChildren(
        benName: String,
        householdId: Long,
        parentName: String,
        unsynced: SyncState,
        proccess: String,
        updateStatus: Int
    )

    @Query(
        "UPDATE BENEFICIARY " +
                "SET gen_spouseName = :benName , syncState = :unsynced , processed = :proccess , serverUpdatedStatus =:updateStatus WHERE gen_maritalStatusId = 2 AND householdId = :householdId AND gen_spouseName = :spouseName AND (familyHeadRelationPosition = 5 OR familyHeadRelationPosition = 6)"
    )
    suspend fun updateSpouseOfHoF(
        benName: String,
        householdId: Long,
        spouseName: String,
        unsynced: SyncState,
        proccess: String,
        updateStatus: Int
    )

    @Query(
        "UPDATE BENEFICIARY " +
                "SET gen_marriageDate = :marriageDate , gen_ageAtMarriage = :ageAtMarriage, syncState = :unsynced , processed = :proccess , serverUpdatedStatus =:updateStatus WHERE householdId = :householdId AND gen_spouseName = :spouseName"
    )
    suspend fun updateMarriageAgeOfWife(
        marriageDate: Long,
        ageAtMarriage: Int,
        householdId: Long,
        spouseName: String,
        unsynced: SyncState,
        proccess: String,
        updateStatus: Int
    )

    @Query(
        "UPDATE BENEFICIARY " +
                "SET gen_marriageDate = :marriageDate , gen_ageAtMarriage = :ageAtMarriage, syncState = :unsynced , processed = :proccess , serverUpdatedStatus =:updateStatus WHERE householdId = :householdId AND gen_spouseName = :spouseName"
    )
    suspend fun updateMarriageAgeOfHusband(
        marriageDate: Long,
        ageAtMarriage: Int,
        householdId: Long,
        spouseName: String,
        unsynced: SyncState,
        proccess: String,
        updateStatus: Int
    )

    @Query(
        "UPDATE BENEFICIARY " +
                "SET gen_spouseName = :benName , syncState = :unsynced , processed = :proccess , serverUpdatedStatus =:updateStatus WHERE gen_maritalStatusId = 2 AND householdId = :householdId AND gen_spouseName = :spouseName"
    )
    suspend fun updateSpouse(
        benName: String,
        householdId: Long,
        spouseName: String,
        unsynced: SyncState,
        proccess: String,
        updateStatus: Int
    )

    @Query(
        "UPDATE BENEFICIARY " +
                "SET fatherName = :benName , syncState = :unsynced , processed = :proccess , serverUpdatedStatus =:updateStatus WHERE householdId = :householdId AND fatherName = :parentName"
    )
    suspend fun updateFather(
        benName: String,
        householdId: Long,
        parentName: String,
        unsynced: SyncState,
        proccess: String,
        updateStatus: Int
    )

    @Query(
        "UPDATE BENEFICIARY " +
                "SET motherName = :benName , syncState = :unsynced , processed = :proccess , serverUpdatedStatus =:updateStatus WHERE householdId = :householdId AND motherName = :parentName"
    )
    suspend fun updateMother(
        benName: String,
        householdId: Long,
        parentName: String,
        unsynced: SyncState,
        proccess: String,
        updateStatus: Int
    )

    @Query(
        "UPDATE BENEFICIARY " +
                "SET firstName = :babyName , syncState = :unsynced , processed = :proccess , serverUpdatedStatus =:updateStatus WHERE householdId = :householdId AND motherName = :parentName AND age < 1"
    )
    suspend fun updateBabyName(
        babyName: String,
        householdId: Long,
        parentName: String,
        unsynced: SyncState,
        proccess: String,
        updateStatus: Int
    )

    @Query(
        "UPDATE BENEFICIARY " +
                "SET lastName = :lastName , syncState = :unsynced , processed = :proccess , serverUpdatedStatus =:updateStatus WHERE gen_maritalStatusId = 2 AND householdId = :householdId AND gen_spouseName = :spouseName"
    )
    suspend fun updateSpouseLastName(
        lastName: String,
        householdId: Long,
        spouseName: String,
        unsynced: SyncState,
        proccess: String,
        updateStatus: Int
    )

    @Query(
        "UPDATE BENEFICIARY " +
                "SET lastName = :lastName , syncState = :unsynced , processed = :proccess , serverUpdatedStatus =:updateStatus WHERE householdId = :householdId AND fatherName = :parentName"
    )
    suspend fun updateChildrenLastName(
        lastName: String,
        householdId: Long,
        parentName: String,
        unsynced: SyncState,
        proccess: String,
        updateStatus: Int
    )

    @Query("UPDATE  BENEFICIARY SET isSpouseAdded = 1 , syncState = :unsynced , processed = :proccess , serverUpdatedStatus =:updateStatus WHERE householdId = :householdId AND beneficiaryId = :benId")
    suspend fun updateBeneficiarySpouseAdded(householdId: Long,benId: Long, unsynced: SyncState,  proccess: String,
                                             updateStatus: Int)

    @Query("SELECT * FROM BEN_BASIC_CACHE where villageId = :selectedVillage AND isDeactivate = 0 ORDER BY BEN_BASIC_CACHE.updatedDate DESC")
    fun getAllBen(selectedVillage: Int): Flow<List<BenBasicCache>>


    // Not filtered by village: the ben list can show other-village beneficiaries (block search),
        // and TB Screening status isn't village-filtered either.
        @Query("""
            SELECT beneficiaryId FROM BENEFICIARY
            WHERE isDeactivate = 0
            AND isDraft = 0
            AND (
                height IS NOT NULL
                OR weight IS NOT NULL
                OR bmi IS NOT NULL
                OR temperature IS NOT NULL
            )
        """)


    fun getAnthropometryFilledBenIds(): Flow<List<Long>>

    /** PR 6: [getAnthropometryFilledBenIds] for the rows a paged list has loaded. */
    @Query("""
        SELECT beneficiaryId FROM BENEFICIARY
        WHERE beneficiaryId IN (:benIds) AND isDeactivate = 0 AND isDraft = 0
        AND (height IS NOT NULL OR weight IS NOT NULL OR bmi IS NOT NULL OR temperature IS NOT NULL)
    """)
    fun getAnthropometryFilledBenIdsIn(benIds: Collection<Long>): Flow<List<Long>>

    @Query("SELECT * FROM BEN_BASIC_CACHE where villageId = :selectedVillage AND abhaId IS NOT NULL AND isDeactivate = 0 ORDER BY BEN_BASIC_CACHE.updatedDate DESC")
    fun getAllBenWithAbha(selectedVillage: Int): Flow<List<BenBasicCache>>

    @Query("SELECT * FROM BEN_BASIC_CACHE where villageId = :selectedVillage AND abhaId IS NULL and isDeactivate=0 ORDER BY BEN_BASIC_CACHE.updatedDate DESC")
    fun getAllBenWithoutAbha(selectedVillage: Int): Flow<List<BenBasicCache>>

    @Query("SELECT * FROM BEN_BASIC_CACHE where villageId = :selectedVillage AND rchId IS NOT NULL AND rchId != '' AND isDeactivate = 0 ORDER BY BEN_BASIC_CACHE.updatedDate DESC")
    fun getAllBenWithRch(selectedVillage: Int): Flow<List<BenBasicCache>>

    @Query("SELECT * FROM BEN_BASIC_CACHE where villageId = :selectedVillage AND  isDeactivate = 0 AND CAST((strftime('%s','now') - dob/1000)/60/60/24/365 AS INTEGER) >= 30 AND isDeath = 0 ORDER BY BEN_BASIC_CACHE.updatedDate DESC")
    fun getAllBenAboveThirty(selectedVillage: Int): Flow<List<BenBasicCache>>

    @Query("SELECT * FROM BEN_BASIC_CACHE WHERE villageId = :selectedVillage AND isDeactivate=0 AND gender = 'Female' AND isDeath = 0 AND CAST((strftime('%s','now') - dob/1000)/60/60/24/365 AS INTEGER) BETWEEN 20 AND 49 AND (reproductiveStatusId = 1 OR reproductiveStatusId = 2) ORDER BY BEN_BASIC_CACHE.updatedDate DESC")
    fun getAllBenWARA(selectedVillage: Int): Flow<List<BenBasicCache>>

    @Query("SELECT * FROM BEN_BASIC_CACHE where villageId = :selectedVillage and gender = :gender and isDeactivate=0 ORDER BY BEN_BASIC_CACHE.updatedDate DESC")
    fun getAllBenGender(selectedVillage: Int, gender: String): Flow<List<BenBasicCache>>

    @Query("SELECT * FROM BENEFICIARY WHERE beneficiaryId =:benId AND householdId = :hhId LIMIT 1")
    suspend fun getBen(hhId: Long, benId: Long): BenRegCache?

    @Query("SELECT * FROM BENEFICIARY WHERE beneficiaryId =:benId LIMIT 1")
    suspend fun getBen(benId: Long): BenRegCache?

    @Query("SELECT * FROM BENEFICIARY WHERE benRegId = :benRegId LIMIT 1")
    suspend fun getBenByRegId(benRegId: Long): BenRegCache?

    /**
     * Resolves a whole chunk of server benRegIds to local beneficiaryIds in one statement.
     * [getBenByRegId] scans the beneficiary table for a single id because benRegId is not
     * indexed, so a pull calling it per record scans the table once per record.
     */
    @Query("SELECT benRegId, beneficiaryId FROM BENEFICIARY WHERE benRegId IN (:benRegIds)")
    suspend fun getBenIdsByRegIds(benRegIds: List<Long>): List<org.piramalswasthya.stoptb.model.BenRegIdMapping>

    /**
     * Which of [benIds] exist as local beneficiaryIds. Callers that resolve a server id first
     * by benRegId and then fall back to treating it as a local beneficiaryId use this for the
     * fallback leg, so the fallback is one statement per chunk rather than one per id.
     */
    @Query("SELECT beneficiaryId FROM BENEFICIARY WHERE beneficiaryId IN (:benIds)")
    suspend fun getExistingBenIds(benIds: List<Long>): List<Long>

    @Query("SELECT beneficiaryId FROM BENEFICIARY WHERE loc_village_id = :selectedVillage AND isDeactivate = 0 AND isDraft = 0")
    suspend fun getActiveBeneficiaryIds(selectedVillage: Int): List<Long>

    @Query("SELECT EXISTS(SELECT 1 FROM BENEFICIARY WHERE beneficiaryId = :benId AND isDeath = 1)")
    suspend fun isBenDead(benId: Long): Boolean

    @Query("DELETE FROM BENEFICIARY WHERE householdId = :hhId and isKid = :kid")
    suspend fun deleteBen(hhId: Long, kid: Boolean)

    @Query("UPDATE BENEFICIARY SET beneficiaryId = :newId, benRegId = :benRegId WHERE householdId IS :hhId AND beneficiaryId =:oldId")
    suspend fun substituteBenId(hhId: Long?, oldId: Long, newId: Long, benRegId: Long)

    @Query("SELECT beneficiaryId FROM BENEFICIARY WHERE beneficiaryId IN (:list)")
    fun getAllBeneficiaryFromList(list: List<Long>): LiveData<List<Long>>



    @Query("SELECT * FROM BEN_BASIC_CACHE  WHERE pwHrp = 1 and villageId=:selectedVillage and  isDeactivate=0 ORDER BY BEN_BASIC_CACHE.updatedDate DESC")
    fun getAllWomenListForPmsma(selectedVillage: Int): Flow<List<BenBasicCache>>


    @Query("""
    SELECT * FROM BEN_BASIC_CACHE 
    WHERE 
        CAST((strftime('%s','now') - dob/1000)/60/60/24/365 AS INTEGER) >= 15
        AND isDeath = 1
        and isDeactivate=0 
        AND (reasonOfDeath IS NULL OR reasonOfDeath != 'Maternal Death')
        AND isMdsr = 0
        AND villageId = :selectedVillage
        ORDER BY BEN_BASIC_CACHE.updatedDate DESC
    """)
    fun getAllGeneralDeathsList(selectedVillage: Int): Flow<List<BenBasicCache>>

    @Query("""
    SELECT * FROM BEN_BASIC_CACHE 
    WHERE 
        gender = 'FEMALE'
        AND CAST((strftime('%s','now') - dob/1000)/60/60/24/365 AS INTEGER) BETWEEN 15 AND 49
        AND isDeath = 1
        AND  isDeactivate=0 
        AND (reasonOfDeath IS NULL OR reasonOfDeath != 'Maternal Death')
        AND isMdsr = 0
        AND villageId = :selectedVillage
        ORDER BY BEN_BASIC_CACHE.updatedDate DESC
    """)
    fun getAllNonMaternalDeathsList(selectedVillage: Int): Flow<List<BenBasicCache>>

    // have to add those as well who we are adding to menopause entries manually from app
    @Query("SELECT * FROM BEN_BASIC_CACHE WHERE reproductiveStatusId = 5 and  isDeactivate=0  and villageId=:selectedVillage ORDER BY BEN_BASIC_CACHE.updatedDate DESC")
    fun getAllMenopauseStageList(selectedVillage: Int): Flow<List<BenBasicCache>>

    @Query("SELECT * FROM BEN_BASIC_CACHE WHERE gender = :female and CAST((strftime('%s','now') - dob/1000)/60/60/24/365 AS INTEGER) BETWEEN :min and :max and villageId=:selectedVillage and isDeactivate=0 ORDER BY BEN_BASIC_CACHE.updatedDate DESC")
    fun getAllReproductiveAgeList(
        selectedVillage: Int,
        min: Int = Konstants.minAgeForReproductiveAge,
        max: Int = Konstants.maxAgeForReproductiveAge,
        female: Gender = Gender.FEMALE
    ): Flow<List<BenBasicCache>>



    @Query("SELECT * FROM BEN_BASIC_CACHE WHERE CAST(((strftime('%s','now') - dob/1000)/60/60/24) AS INTEGER) <= :max and villageId=:selectedVillage and isDeactivate=0 ORDER BY BEN_BASIC_CACHE.updatedDate DESC")
    fun getAllInfantList(
        selectedVillage: Int, max: Int = Konstants.maxAgeForInfant
    ): Flow<List<BenBasicCache>>

    @Query("SELECT * FROM BEN_BASIC_CACHE WHERE benId = :benId LIMIT 1")
    suspend fun getBenById(benId: Long): BenBasicCache?

    @Query("SELECT * FROM BEN_BASIC_CACHE WHERE CAST(((strftime('%s','now') - dob/1000)/60/60/24) AS INTEGER) <= :max and villageId=:selectedVillage and rchId is not null and rchId != '' ORDER BY BEN_BASIC_CACHE.updatedDate DESC")
    fun getAllInfantWithRchList(
        selectedVillage: Int, max: Int = Konstants.maxAgeForInfant
    ): Flow<List<BenBasicCache>>

    @Query("SELECT * FROM BEN_BASIC_CACHE WHERE  CAST(((strftime('%s','now') - dob/1000)/60/60/24) AS INTEGER) BETWEEN :min and :max and villageId=:selectedVillage and isDeactivate =0 ORDER BY BEN_BASIC_CACHE.updatedDate DESC")
    fun getAllChildList(
        selectedVillage: Int,
        min: Int = Konstants.minAgeForChild,
        max: Int = Konstants.maxAgeForChild
    ): Flow<List<BenBasicCache>>

    @Query("SELECT * FROM BEN_BASIC_CACHE WHERE  CAST(((strftime('%s','now') - dob/1000)/60/60/24) AS INTEGER) BETWEEN :min and :max and villageId=:selectedVillage and rchId is not null and rchId != '' ORDER BY BEN_BASIC_CACHE.updatedDate DESC")
    fun getAllChildWithRchList(
        selectedVillage: Int,
        min: Int = Konstants.minAgeForChild,
        max: Int = Konstants.maxAgeForChild
    ): Flow<List<BenBasicCache>>

    @Query("SELECT * FROM BEN_BASIC_CACHE WHERE CAST((strftime('%s','now') - dob/1000)/60/60/24/365 AS INTEGER) <= :max and villageId=:selectedVillage AND isDeath = 1 and  isDeactivate=0 ORDER BY BEN_BASIC_CACHE.updatedDate DESC" )
    fun getAllCDRList(
        selectedVillage: Int, max: Int = Konstants.maxAgeForCdr
    ): Flow<List<BenBasicCache>>

    @Query("select min(beneficiaryId) from beneficiary")
    suspend fun getMinBenId(): Long?



    @Query("SELECT COUNT(*) > 0 FROM BENEFICIARY WHERE beneficiaryId = :benId AND reasonOfDeath = :cause")
    suspend fun isDeathByCauseAnc(benId: Long, cause: String): Boolean

    @Query("UPDATE BENEFICIARY SET processed = 'N', syncState = 0 WHERE isDraft = 0 AND syncState != 2 AND (beneficiaryId <= 0 OR benRegId <= 0)")
    suspend fun requeueTempBeneficiariesForRegistration()

    @Query("UPDATE BENEFICIARY SET symptomsScreenedDate = :date, screeningStatus = 'SYMPTOMS_SCREENED', syncState = :unsynced, processed = 'U', serverUpdatedStatus = 2 WHERE beneficiaryId = :benId")
    suspend fun markSymptomsScreened(benId: Long, date: Long = System.currentTimeMillis(), unsynced: SyncState = SyncState.UNSYNCED)

    @Query("UPDATE BENEFICIARY SET trunatTestDoneDate = :date, screeningStatus = 'TRUNAT_TEST_DONE', syncState = :unsynced, processed = 'U', serverUpdatedStatus = 2 WHERE beneficiaryId = :benId")
    suspend fun markTrunatTestDone(benId: Long, date: Long = System.currentTimeMillis(), unsynced: SyncState = SyncState.UNSYNCED)
}
