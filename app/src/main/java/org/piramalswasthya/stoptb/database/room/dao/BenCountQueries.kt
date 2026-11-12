package org.piramalswasthya.stoptb.database.room.dao

import androidx.lifecycle.LiveData
import androidx.paging.PagingSource
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.helpers.Konstants
import org.piramalswasthya.stoptb.model.*

/**
 * Counts for the home cards, dashboard and village headcount.
 *
 * Part of [BenDao] (which extends this interface); split out only to keep the DAO
 * readable. Room generates these queries as part of BenDao_Impl - SQL and callers unchanged.
 */
interface BenCountQueries {

    @Query("SELECT COUNT(*) FROM BEN_BASIC_CACHE where villageId = :selectedVillage and gender = :gender and isDeactivate=0")
    fun getAllBenGenderCount(selectedVillage: Int, gender: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM BEN_BASIC_CACHE where villageId = :selectedVillage and isDeactivate=0 and EXISTS (SELECT 1 FROM TB_SCREENING s WHERE s.benId = BEN_BASIC_CACHE.benId)")
    fun getPendingTbScreeningCount(selectedVillage: Int): Flow<Int>

    /** Count of beneficiaries who have a TB_SCREENING record (directly from TB_SCREENING table — reliable for both local and server-synced data) */
    @Query("""
        SELECT COUNT(DISTINCT ts.benId) FROM TB_SCREENING ts
        INNER JOIN BEN_BASIC_CACHE b ON b.benId = ts.benId
        WHERE b.villageId = :selectedVillage AND b.isDeactivate = 0 AND b.isDeath = 0
    """)
    fun getTbScreenedBenCount(selectedVillage: Int): Flow<Int>

    @Query("""
    SELECT COUNT(*) 
    FROM BEN_BASIC_CACHE b 
    INNER JOIN LEPROSY_SCREENING l ON b.benId = l.benId 
    WHERE b.villageId = :selectedVillage 
    AND b.isDeactivate=0
    AND l.leprosySymptomsPosition = :symptomsPosition
    AND l.isConfirmed = 0
""")
    fun getLeprosyScreeningBenCountBySymptoms(selectedVillage: Int, symptomsPosition: Int): Flow<Int>

    @Query("""
    SELECT COUNT(*)
    FROM BEN_BASIC_CACHE b
    INNER JOIN LEPROSY_SCREENING l ON b.benId = l.benId
    WHERE b.villageId = :selectedVillage
    AND  isDeactivate=0
    AND l.isConfirmed = 1
""")
    fun getConfirmedLeprosyCaseCount(
        selectedVillage: Int
    ): Flow<Int>


    @Query("SELECT COUNT(*) FROM BEN_BASIC_CACHE where villageId = :selectedVillage and isDeactivate=0")
    fun getAllBenCount(selectedVillage: Int): Flow<Int>

    /**
     * TEMPORARY PERFORMANCE INSTRUMENTATION - remove with the rest of the trace scaffolding.
     *
     * Costs nothing to execute, so however long it takes to come back is time spent queueing on
     * Room's query executor rather than doing work. Timing it either side of a paging load splits
     * "this query is expensive" from "the database is busy with the sync".
     */

    @Query("SELECT COUNT(*) FROM BEN_BASIC_CACHE where villageId = :selectedVillage AND isDeactivate=0 AND abhaId IS NOT NULL")
    fun getAllBenWithAbhaCount(selectedVillage: Int): Flow<Int>

    @Query("SELECT COUNT(*) FROM BEN_BASIC_CACHE where villageId = :selectedVillage AND isDeactivate=0 AND abhaId IS NOT NULL AND isNewAbha = 0")
    fun getAllBenWithOldAbhaCount(selectedVillage: Int): Flow<Int>

    @Query("SELECT COUNT(*) FROM BEN_BASIC_CACHE where villageId = :selectedVillage AND isDeactivate=0 AND abhaId IS NOT NULL AND isNewAbha = 1")
    fun getAllBenWithNewAbhaCount(selectedVillage: Int): Flow<Int>

    // Dashboard ABHA count with time + village filter
    @Query("""
        SELECT COUNT(*) FROM BEN_BASIC_CACHE
        WHERE isDeactivate = 0 AND isDeath = 0 AND abhaId IS NOT NULL
        AND villageId IN (:assignedVillageIds)
        AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN IFNULL(regDate, 0) >= 100000000000 THEN regDate WHEN IFNULL(regDate, 0) > 0 THEN regDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime)
        AND (:endTime = :endTime)
        AND (:gender = '' OR (:gender != 'OTHERS' AND UPPER(COALESCE(gender, '')) = UPPER(:gender)) OR (:gender = 'OTHERS' AND UPPER(COALESCE(gender, '')) NOT IN ('MALE', 'FEMALE')))
        AND (:isChild = 0 OR (CAST((strftime('%s','now') - dob/1000)/60/60/24/365 AS INTEGER) < 15))
        AND (:isSeniorCitizen = 0 OR (CAST((strftime('%s','now') - dob/1000)/60/60/24/365 AS INTEGER) >= 60))
    """)
    fun getDashboardAbhaCount(
        assignedVillageIds: List<Int>,
        startTime: Long,
        endTime: Long,
        gender: String,
        isChild: Int,
        isSeniorCitizen: Int,
    ): Flow<Int>

    @Query("SELECT COUNT(*) FROM BEN_BASIC_CACHE where villageId = :selectedVillage AND isDeactivate=0 AND rchId IS NOT NULL AND rchId != ''")
    fun getAllBenWithRchCount(selectedVillage: Int): Flow<Int>

    @Query("""
        SELECT parent.beneficiaryId as benId, COUNT(child.beneficiaryId) as childCount
        FROM BENEFICIARY parent
        LEFT JOIN BENEFICIARY child
            ON child.householdId = parent.householdId
            AND child.beneficiaryId != parent.beneficiaryId
            AND (parent.firstName IS NULL OR parent.firstName = '' OR child.motherName LIKE parent.firstName || '%')
        WHERE parent.isDraft = 0
            AND parent.loc_village_id = :selectedVillage
            AND parent.isDeactivate = 0
        GROUP BY parent.beneficiaryId
    """)
    fun getChildCountsForAllBen(selectedVillage: Int): Flow<List<BenChildCount>>

    @Query("""
        SELECT householdId AS hhId, COUNT(*) AS memberCount
        FROM BENEFICIARY
        WHERE isDraft = 0
          AND isDeactivate = 0
          AND loc_village_id = :selectedVillage
          AND householdId IS NOT NULL
        GROUP BY householdId
    """)
    fun getHouseholdMemberCounts(selectedVillage: Int): Flow<List<HouseholdMemberCount>>

    @Query("""
        SELECT COUNT(child.beneficiaryId)
        FROM BENEFICIARY parent
        LEFT JOIN BENEFICIARY child
            ON child.householdId = parent.householdId
            AND child.beneficiaryId != parent.beneficiaryId
            AND (parent.firstName IS NULL OR parent.firstName = '' OR child.motherName LIKE parent.firstName || '%')
        WHERE parent.beneficiaryId = :benId
            AND parent.isDraft = 0
            AND parent.isDeactivate = 0
    """)
    suspend fun getChildCountForBen(benId: Long): Int

    @Query("""
    SELECT COUNT(*) FROM BENEFICIARY
    WHERE householdId = :hhId
    AND beneficiaryId != :selectedbenIdFromArgs
    AND age < 15
    AND (:firstName IS NULL OR :firstName = '' OR motherName LIKE :firstName || '%')
    """)
    suspend fun getBelow15Count(
        hhId: Long,
        selectedbenIdFromArgs: Long,
        firstName: String?
    ): Int

    @Query("""
    SELECT COUNT(*) FROM BENEFICIARY
    WHERE householdId = :hhId
    AND beneficiaryId != :selectedbenIdFromArgs
    AND age >= 15
    AND (:firstName IS NULL OR :firstName = '' OR motherName LIKE :firstName || '%')
    """)
    suspend fun get15aboveCount(
        hhId: Long,
        selectedbenIdFromArgs: Long,
        firstName: String?
    ): Int

    @Query("SELECT COUNT(*) FROM BENEFICIARY WHERE isDraft = 0 AND (processed = 'N' OR processed = 'U') AND syncState =0")
    fun getUnProcessedRecordCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM BENEFICIARY WHERE isDraft = 0 AND (processed = 'N' OR processed = 'U') AND syncState =0")
    fun getAllUnProcessedRecordCount(): Flow<Int>




    @Query("SELECT COUNT(*) FROM BEN_BASIC_CACHE WHERE CAST((strftime('%s','now') - dob/1000)/60/60/24/365 AS INTEGER) BETWEEN :min and :max and reproductiveStatusId = 1 and villageId=:selectedVillage")
    fun getAllEligibleCoupleListCount(
        selectedVillage: Int,
        min: Int = Konstants.minAgeForEligibleCouple, max: Int = Konstants.maxAgeForEligibleCouple
    ): Flow<Int>



    @Transaction
    @Query("SELECT count(*) FROM BEN_BASIC_CACHE WHERE reproductiveStatusId = 2 and isDeactivate=0 and villageId=:selectedVillage")
    fun getAllPregnancyWomenForHRListCount(selectedVillage: Int): Flow<Int>


    @Query("SELECT COUNT(*) FROM BEN_BASIC_CACHE WHERE reproductiveStatusId = 2 and isDeactivate=0 and villageId=:selectedVillage")
    fun getAllPregnancyWomenListCount(selectedVillage: Int): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM BEN_BASIC_CACHE
        WHERE villageId = :selectedVillage
          AND isDeactivate = 0
          AND isDeath = 0
          AND CAST((strftime('%s','now') - dob/1000)/60/60/24/365 AS INTEGER) < 5
    """)
    fun getUnderFiveChildrenCount(selectedVillage: Int): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM BEN_BASIC_CACHE
        WHERE villageId = :selectedVillage
          AND isDeactivate = 0
          AND isDeath = 0
          AND benId IN (
            SELECT b2.beneficiaryId FROM BENEFICIARY b2
            WHERE b2.temperature IS NOT NULL AND b2.temperature >= 100.0
            UNION
            SELECT v.benId FROM BEN_VITALS v
            WHERE ${ABNORMAL_VITALS_CONDITION}
            UNION
            SELECT ts.benId FROM TB_SUSPECTED ts
            WHERE UPPER(IFNULL(ts.chestXRayResult, '')) IN ('POSITIVE', 'TB PRESUMPTIVE', 'ABNORMAL BUT NOT TB PRESUMPTIVE')
              AND UPPER(IFNULL(ts.naatResult, '')) IN ('NEGATIVE', 'MTB NOT DETECTED', 'TB NEGATIVE')
            UNION
            SELECT td.benId FROM TB_DIAGNOSTICS td
            WHERE UPPER(IFNULL(td.chestXRayResult, '')) IN ('POSITIVE', 'TB PRESUMPTIVE', 'ABNORMAL BUT NOT TB PRESUMPTIVE')
              AND UPPER(IFNULL(td.naatResult, '')) IN ('NEGATIVE', 'MTB NOT DETECTED', 'TB NEGATIVE')
          )
    """)
    fun getHwcBenDataCount(selectedVillage: Int): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM BEN_BASIC_CACHE
        WHERE villageId = :selectedVillage
          AND isDeactivate = 0
          AND isDeath = 0
          -- Same membership as source 6 in searchBenPaged: pregnant beneficiaries (by
          -- reproductive status or recorded in vitals) are not referred for X-ray at all.
          AND reproductiveStatusId != 1
          AND benId NOT IN (SELECT v.benId FROM BEN_VITALS v WHERE v.keyPopulationRiskFactors LIKE '%PREGNANCY%')
          AND (
            benId IN (
              SELECT ts.benId FROM TB_SUSPECTED ts
              WHERE ts.isChestXRayDone IS NOT NULL
            )
            OR benId IN (
              SELECT td.benId FROM TB_DIAGNOSTICS td
              WHERE td.isChestXRayDone IS NOT NULL
            )
            OR benId IN (
              SELECT tbs.benId FROM TB_SCREENING tbs
            )
          )
    """)
    fun getDigitalChestXRayBenCount(selectedVillage: Int): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM BEN_BASIC_CACHE
        WHERE villageId = :selectedVillage
          AND isDeactivate = 0
          AND isDeath = 0
          AND (
            benId IN (
              SELECT ts.benId FROM TB_SUSPECTED ts
              WHERE ts.isNaatConducted IS NOT NULL
                 OR UPPER(IFNULL(ts.chestXRayResult, '')) IN ('POSITIVE', 'TB PRESUMPTIVE', 'ABNORMAL BUT NOT TB PRESUMPTIVE')
            )
            OR benId IN (
              SELECT td.benId FROM TB_DIAGNOSTICS td
              -- Must match the source = 7 membership rule in searchBenPaged exactly, or the
              -- card's badge disagrees with the list it opens. See the note there.
              WHERE td.trueNatOrderId IS NOT NULL
                 OR td.trueNatOrderStatus IS NOT NULL
                 OR td.isNaatConducted IS NOT NULL
                 OR UPPER(IFNULL(td.chestXRayResult, '')) IN ('POSITIVE', 'TB PRESUMPTIVE', 'ABNORMAL BUT NOT TB PRESUMPTIVE')
            )
            -- reproductiveStatusId is a BEN_BASIC_CACHE column, not a TB_SCREENING one. Having
            -- it inside this subquery made the subquery correlated, so SQLite re-scanned the
            -- whole of TB_SCREENING once per beneficiary - O(beneficiaries x screenings), which
            -- measured 5.3 SECONDS on a mid-range device at 5,000 beneficiaries and grows
            -- quadratically. Hoisted to the outer level, which is logically identical (same
            -- row counts verified at 5k and 25k) and uncorrelated, it runs in ~44ms.
            -- Same pregnancy rule as source 7 in searchBenPaged: by reproductive status or as
            -- recorded in vitals.
            OR ((reproductiveStatusId = 1 OR benId IN (
                  SELECT v.benId FROM BEN_VITALS v WHERE v.keyPopulationRiskFactors LIKE '%PREGNANCY%'
                )) AND benId IN (SELECT tbs.benId FROM TB_SCREENING tbs))
            OR benId IN (
              SELECT tbs.benId FROM TB_SCREENING tbs
              WHERE
                tbs.recommendedForTruenatTest = 1
                OR tbs.referredForSputumCollection = 1
                OR (
                  tbs.bloodInSputum = 1
                  OR tbs.coughMoreThan2Weeks = 1
                  OR tbs.feverMoreThan2Weeks = 1
                  OR tbs.riseOfFever = 1
                  OR tbs.lossOfAppetite = 1
                  OR tbs.lossOfWeight = 1
                  OR tbs.nightSweats = 1
                  OR tbs.historyOfTb = 1
                  OR tbs.takingAntiTBDrugs = 1
                  OR tbs.familySufferingFromTB = 1
                  OR UPPER(IFNULL(tbs.asymptomatic, '')) = 'NO'
                )
            )
          )
    """)
    fun getTrueNatBenCount(selectedVillage: Int): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM BEN_BASIC_CACHE
        WHERE villageId = :selectedVillage
          AND isDeactivate = 0
          AND isDeath = 0
          AND (
            ${LIQUID_CULTURE_REFERRAL_CONDITION}
          )
    """)
    fun getLiquidCultureBenCount(selectedVillage: Int): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM BEN_BASIC_CACHE
        WHERE villageId = :selectedVillage AND isDeactivate = 0 AND isDeath = 0
          AND benId IN (
            SELECT ts.benId FROM TB_SUSPECTED ts
            WHERE UPPER(TRIM(IFNULL(ts.chestXRayResult,''))) IN ('TB PRESUMPTIVE','ABNORMAL BUT NOT TB PRESUMPTIVE')
              AND UPPER(TRIM(IFNULL(ts.naatResult,''))) IN ('TB NEGATIVE','MTB NOT DETECTED')
            UNION
            SELECT td.benId FROM TB_DIAGNOSTICS td
            WHERE (UPPER(TRIM(IFNULL(td.chestXRayResult,''))) IN ('TB PRESUMPTIVE','ABNORMAL BUT NOT TB PRESUMPTIVE')
                   AND UPPER(TRIM(IFNULL(td.naatResult,''))) IN ('TB NEGATIVE','MTB NOT DETECTED'))
               OR UPPER(TRIM(IFNULL(td.trueNatRifResult,''))) = 'INDETERMINATE'
          )
    """)
    fun getClinicalAssessmentBenCount(selectedVillage: Int): Flow<Int>


    @Query("SELECT COUNT(*) FROM BEN_BASIC_CACHE WHERE reproductiveStatusId = 1 and gender = 'FEMALE' and isDeactivate=0 and villageId=:selectedVillage")
    fun getAllNonPregnancyWomenListCount(selectedVillage: Int): Flow<Int>

    @Query("SELECT COUNT(*) FROM BEN_BASIC_CACHE WHERE pwHrp = 1 and isDeactivate=0 and villageId=:selectedVillage")
    fun getAllWomenListForPmsmaCount(selectedVillage: Int): Flow<Int>

    @Query("SELECT COUNT(*) FROM BEN_BASIC_CACHE WHERE CAST((strftime('%s','now') - dob/1000)/60/60/24/365 AS INTEGER) <= :max and villageId=:selectedVillage AND isDeath = 1 and isDeactivate=0" )
    fun getAllCDRListCount(
        selectedVillage: Int, max: Int = Konstants.maxAgeForCdr
    ): Flow<Int>



    @Transaction
    @Query("SELECT COUNT(*) FROM BEN_BASIC_CACHE WHERE reproductiveStatusId in (2, 3, 4) and isMdsr = 1 and villageId=:selectedVillage and isDeactivate=0")
    fun getAllMDSRCount(selectedVillage: Int): Flow<Int>

    // Dashboard TB Confirmed tile. Definition shared with the Confirmed list: [TbCaseSql.CONFIRMED];
    // each record type is confirmed within its own visit date for the time filter.
    @Query("""
        SELECT COUNT(DISTINCT b.benId)
        FROM BEN_BASIC_CACHE b
        LEFT JOIN TB_SUSPECTED sus ON b.benId = sus.benId
        LEFT JOIN TB_DIAGNOSTICS dx ON b.benId = dx.benId
        WHERE b.isDeactivate = 0
          AND b.isDeath = 0
          AND b.villageId IN (:assignedVillageIds)
          AND (:gender = '' OR (:gender != 'OTHERS' AND UPPER(COALESCE(b.gender, '')) = UPPER(:gender)) OR (:gender = 'OTHERS' AND UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE')))
          AND (:isChild = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15))
          AND (:isSeniorCitizen = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) >= 60))
          AND (:endTime = :endTime)
          AND (
                (""" + TbCaseSql.SUSPECTED_CONFIRMED + """
                    AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN sus.visitDate >= 100000000000 THEN sus.visitDate WHEN sus.visitDate > 0 THEN sus.visitDate * 1000 WHEN IFNULL(sus.serverUpdatedDate, 0) >= 100000000000 THEN sus.serverUpdatedDate WHEN IFNULL(sus.serverUpdatedDate, 0) > 0 THEN sus.serverUpdatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime))
                OR
                (""" + TbCaseSql.DIAGNOSTICS_CONFIRMED + """
                    AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN dx.visitDate >= 100000000000 THEN dx.visitDate WHEN dx.visitDate > 0 THEN dx.visitDate * 1000 WHEN IFNULL(dx.serverUpdatedDate, 0) >= 100000000000 THEN dx.serverUpdatedDate WHEN IFNULL(dx.serverUpdatedDate, 0) > 0 THEN dx.serverUpdatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime))
              )
    """)
    fun getDashboardFilteredTbConfirmedCount(
        assignedVillageIds: List<Int>,
        startTime: Long,
        endTime: Long,
        gender: String,
        isChild: Int,
        isSeniorCitizen: Int
    ): Flow<Int>


    @Query("""
      SELECT COUNT(b.benId)
        FROM BEN_BASIC_CACHE b
        INNER JOIN NCD_REFER r ON b.benId = r.benId
          AND b.villageId = :selectedVillage
          AND b.isDeactivate=0
          AND (
            r.type = "TB"
            OR (
              CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) >= :min
              AND b.reproductiveStatusId != 2
            )
          )
    """)
    fun getReferredBenCount(
        selectedVillage: Int,
        min: Int = Konstants.minAgeForNcd
    ): Flow<Int>

    @Query("""
  SELECT COUNT(b.benId)
    FROM BEN_BASIC_CACHE b
    INNER JOIN NCD_REFER r ON b.benId = r.benId
      AND b.villageId = :selectedVillage
      AND r.type = "MATERNAL"
       AND b.villageId = :selectedVillage
       AND isDeactivate=0
""")
    fun getReferredHWCBenCount(
        selectedVillage: Int,
    ): Flow<Int>

    @Query("""
        SELECT COUNT(b.benId)
        FROM BEN_BASIC_CACHE b
        INNER JOIN NCD_REFER r ON b.benId = r.benId
        WHERE b.villageId = :selectedVillage
          AND b.isDeactivate = 0
          AND r.type = "TB"
          AND r.refrredToAdditionalServiceList IS NOT NULL
          AND r.refrredToAdditionalServiceList LIKE '%' || :serviceName || '%'
    """)
    fun getTbReferralServiceCount(
        selectedVillage: Int,
        serviceName: String
    ): Flow<Int>

    @Query("""
        SELECT COUNT(b.benId)
        FROM BEN_BASIC_CACHE b
        INNER JOIN NCD_REFER r ON b.benId = r.benId
        WHERE b.villageId = :selectedVillage
          AND b.isDeactivate = 0
          AND r.referredToInstituteName = :instituteName
    """)
    fun getReferralInstituteCount(
        selectedVillage: Int,
        instituteName: String
    ): Flow<Int>


    @Query("SELECT COUNT(*) FROM BEN_BASIC_CACHE b where CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER)  >= :min and b.reproductiveStatusId!=2 and isDeactivate=0 and b.villageId=:selectedVillage")
    fun getBenWithCbacCount(
        selectedVillage: Int, min: Int = Konstants.minAgeForNcd
    ): Flow<Int>

    // Drafts are never pushed, so they don't count as pending local work.
    @Query("SELECT COUNT(*) FROM BENEFICIARY WHERE householdId = :hhId AND syncState != 2 AND isDraft = 0")
    suspend fun countNotSyncedInHousehold(hhId: Long): Int


    @Query("""
    SELECT COUNT(*)
    FROM BEN_BASIC_CACHE b
    WHERE b.isDeactivate = 0
      AND b.screeningStatus = 'UNSCREENED'
      AND (:selectedVillage = 0 OR b.villageId = :selectedVillage)
      AND NOT EXISTS (
          SELECT 1
          FROM TB_SCREENING ts
          WHERE ts.benId = b.benId
      )
""")
    fun getUnscreenedCount(selectedVillage: Int): Flow<Int>

    /**
     * Living, active people in the given villages. Home Total Population and dashboard
     * Village Population both use this so the two cards stay on the same count.
     */
    @Query("""
        SELECT
            COUNT(*) AS population,
            COUNT(CASE WHEN screeningStatus = 'UNSCREENED'
                       AND NOT EXISTS (SELECT 1 FROM TB_SCREENING s WHERE s.benId = BEN_BASIC_CACHE.benId)
                  THEN 1 END) AS unscreened
        FROM BEN_BASIC_CACHE
        WHERE isDeactivate = 0 AND isDeath = 0
          AND villageId IN (:villageIds)
    """)
    fun getVillageHeadcount(villageIds: List<Int>): Flow<VillageHeadcount>
}
