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
        AND (:isPregnant = 0 OR (UPPER(COALESCE(gender, '')) = 'FEMALE' AND (
            reproductiveStatusId = 1 OR EXISTS (SELECT 1 FROM BEN_VITALS v WHERE v.benId = BEN_BASIC_CACHE.benId AND v.keyPopulationRiskFactors LIKE '%PREGNANCY%')
        )))
    """)
    fun getDashboardAbhaCount(
        assignedVillageIds: List<Int>,
        startTime: Long,
        endTime: Long,
        gender: String,
        isChild: Int,
        isSeniorCitizen: Int,
        isPregnant: Int = 0,
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

    /**
     * [getChildCountsForAllBen] for the given beneficiaries only. The lists need it for the rows
     * they have loaded; the village-wide form took 8-27 s per run on a tablet at 1M and re-ran on
     * every beneficiary write.
     */
    @Query("""
        SELECT parent.beneficiaryId as benId, COUNT(child.beneficiaryId) as childCount
        FROM BENEFICIARY parent
        LEFT JOIN BENEFICIARY child
            ON child.householdId = parent.householdId
            AND child.beneficiaryId != parent.beneficiaryId
            AND (parent.firstName IS NULL OR parent.firstName = '' OR child.motherName LIKE parent.firstName || '%')
        WHERE parent.beneficiaryId IN (:benIds)
            AND parent.isDraft = 0
            AND parent.loc_village_id = :selectedVillage
            AND parent.isDeactivate = 0
        GROUP BY parent.beneficiaryId
    """)
    fun getChildCountsForBens(selectedVillage: Int, benIds: List<Long>): Flow<List<BenChildCount>>

    /** [getHouseholdMemberCounts] for the households of the given beneficiaries only. */
    @Query("""
        SELECT householdId AS hhId, COUNT(*) AS memberCount
        -- The household index: SQLite otherwise walks the whole village (1M DB 0.9 s -> 0.01 s).
        FROM BENEFICIARY INDEXED BY index_BENEFICIARY_householdId
        WHERE householdId IN (SELECT householdId FROM BENEFICIARY WHERE beneficiaryId IN (:benIds))
          AND isDraft = 0
          AND isDeactivate = 0
          AND loc_village_id = :selectedVillage
          AND householdId IS NOT NULL
        GROUP BY householdId
    """)
    fun getHouseholdMemberCountsForBens(selectedVillage: Int, benIds: List<Long>): Flow<List<HouseholdMemberCount>>

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
          AND benId IN (${ReferralListSql.HWC_BEN_IDS})
    """)
    fun getHwcBenDataCount(selectedVillage: Int): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM BEN_BASIC_CACHE
        WHERE villageId = :selectedVillage
          AND isDeactivate = 0
          AND isDeath = 0
          -- Same membership as source 6 in searchBenPage.
          AND ${ReferralListSql.XRAY}
    """)
    fun getDigitalChestXRayBenCount(selectedVillage: Int): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM BEN_BASIC_CACHE
        WHERE villageId = :selectedVillage
          AND isDeactivate = 0
          AND isDeath = 0
          -- Same membership as source 7 in searchBenPage.
          AND ${ReferralListSql.TRUENAT}
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
    """ + ClinicalAssessmentSql.MATCHING_BEN_IDS + """
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
          AND (:isPregnant = 0 OR (UPPER(COALESCE(b.gender, '')) = 'FEMALE' AND (
              b.reproductiveStatusId = 1 OR EXISTS (SELECT 1 FROM BEN_VITALS v WHERE v.benId = b.benId AND v.keyPopulationRiskFactors LIKE '%PREGNANCY%')
          )))
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
        isSeniorCitizen: Int,
        isPregnant: Int = 0
    ): Flow<Int>

    /**
     * All seven Confirmed tile numbers in one query: [getDashboardFilteredTbConfirmedCount] for
     * total / MALE / FEMALE / children / OTHERS / senior / pregnant. The confirmed set is the same;
     * gender, age and pregnancy are beneficiary columns, so they are grouped over it.
     */
    @Query("""
        SELECT COUNT(*) AS total,
            COALESCE(SUM(CASE WHEN UPPER(COALESCE(c.gender, '')) = 'MALE' THEN 1 ELSE 0 END), 0) AS male,
            COALESCE(SUM(CASE WHEN UPPER(COALESCE(c.gender, '')) = 'FEMALE' THEN 1 ELSE 0 END), 0) AS female,
            COALESCE(SUM(CASE WHEN CAST((strftime('%s','now') - c.dob/1000)/60/60/24/365 AS INTEGER) < 15 THEN 1 ELSE 0 END), 0) AS children,
            COALESCE(SUM(CASE WHEN UPPER(COALESCE(c.gender, '')) NOT IN ('MALE', 'FEMALE') THEN 1 ELSE 0 END), 0) AS others,
            COALESCE(SUM(CASE WHEN CAST((strftime('%s','now') - c.dob/1000)/60/60/24/365 AS INTEGER) >= 60 THEN 1 ELSE 0 END), 0) AS seniorCitizen,
            COALESCE(SUM(CASE WHEN UPPER(COALESCE(c.gender, '')) = 'FEMALE' AND (c.reproductiveStatusId = 1
                OR EXISTS (SELECT 1 FROM BEN_VITALS v WHERE v.benId = c.benId AND v.keyPopulationRiskFactors LIKE '%PREGNANCY%')) THEN 1 ELSE 0 END), 0) AS pregnant
        FROM (
            SELECT DISTINCT b.benId, b.gender, b.dob, b.reproductiveStatusId
            FROM BEN_BASIC_CACHE b
            LEFT JOIN TB_SUSPECTED sus ON b.benId = sus.benId
            LEFT JOIN TB_DIAGNOSTICS dx ON b.benId = dx.benId
            WHERE b.isDeactivate = 0
              AND b.isDeath = 0
              AND b.villageId IN (:assignedVillageIds)
              AND (:endTime = :endTime)
              AND (
                    (""" + TbCaseSql.SUSPECTED_CONFIRMED + """
                        AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN sus.visitDate >= 100000000000 THEN sus.visitDate WHEN sus.visitDate > 0 THEN sus.visitDate * 1000 WHEN IFNULL(sus.serverUpdatedDate, 0) >= 100000000000 THEN sus.serverUpdatedDate WHEN IFNULL(sus.serverUpdatedDate, 0) > 0 THEN sus.serverUpdatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime))
                    OR
                    (""" + TbCaseSql.DIAGNOSTICS_CONFIRMED + """
                        AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN dx.visitDate >= 100000000000 THEN dx.visitDate WHEN dx.visitDate > 0 THEN dx.visitDate * 1000 WHEN IFNULL(dx.serverUpdatedDate, 0) >= 100000000000 THEN dx.serverUpdatedDate WHEN IFNULL(dx.serverUpdatedDate, 0) > 0 THEN dx.serverUpdatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime))
                  )
        ) c
    """)
    fun getDashboardTbConfirmedBreakdown(assignedVillageIds: List<Int>, startTime: Long, endTime: Long): Flow<DashboardBreakdownCounts>

    /** All seven ABHA tile numbers in one query: [getDashboardAbhaCount] grouped the same way. */
    @Query("""
        SELECT COUNT(*) AS total,
            COALESCE(SUM(CASE WHEN UPPER(COALESCE(BEN_BASIC_CACHE.gender, '')) = 'MALE' THEN 1 ELSE 0 END), 0) AS male,
            COALESCE(SUM(CASE WHEN UPPER(COALESCE(BEN_BASIC_CACHE.gender, '')) = 'FEMALE' THEN 1 ELSE 0 END), 0) AS female,
            COALESCE(SUM(CASE WHEN CAST((strftime('%s','now') - BEN_BASIC_CACHE.dob/1000)/60/60/24/365 AS INTEGER) < 15 THEN 1 ELSE 0 END), 0) AS children,
            COALESCE(SUM(CASE WHEN UPPER(COALESCE(BEN_BASIC_CACHE.gender, '')) NOT IN ('MALE', 'FEMALE') THEN 1 ELSE 0 END), 0) AS others,
            COALESCE(SUM(CASE WHEN CAST((strftime('%s','now') - BEN_BASIC_CACHE.dob/1000)/60/60/24/365 AS INTEGER) >= 60 THEN 1 ELSE 0 END), 0) AS seniorCitizen,
            COALESCE(SUM(CASE WHEN UPPER(COALESCE(BEN_BASIC_CACHE.gender, '')) = 'FEMALE' AND (BEN_BASIC_CACHE.reproductiveStatusId = 1
                OR EXISTS (SELECT 1 FROM BEN_VITALS v WHERE v.benId = BEN_BASIC_CACHE.benId AND v.keyPopulationRiskFactors LIKE '%PREGNANCY%')) THEN 1 ELSE 0 END), 0) AS pregnant
        FROM BEN_BASIC_CACHE
        WHERE isDeactivate = 0 AND isDeath = 0 AND abhaId IS NOT NULL
        AND villageId IN (:assignedVillageIds)
        AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN IFNULL(regDate, 0) >= 100000000000 THEN regDate WHEN IFNULL(regDate, 0) > 0 THEN regDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime)
        AND (:endTime = :endTime)
    """)
    fun getDashboardAbhaBreakdown(assignedVillageIds: List<Int>, startTime: Long, endTime: Long): Flow<DashboardBreakdownCounts>


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

    /**
     * The home screen's village counts in one pass: All Beneficiaries ([getAllBenCount]),
     * Non-Household ([getNonHHCount]) and population / unscreened ([getVillageHeadcount]).
     * Each of those reads every beneficiary of the village; at 1M the three ran side by side
     * at startup and took 2-7 s each on a tablet.
     */
    @Query("""
        SELECT
            COUNT(*) AS allBen,
            COUNT(CASE WHEN isNonHH = 1 THEN 1 END) AS nonHH,
            COUNT(CASE WHEN isDeath = 0 THEN 1 END) AS population,
            COUNT(CASE WHEN isDeath = 0 AND screeningStatus = 'UNSCREENED'
                       AND NOT EXISTS (SELECT 1 FROM TB_SCREENING s WHERE s.benId = BEN_BASIC_CACHE.benId)
                  THEN 1 END) AS unscreened
        FROM BEN_BASIC_CACHE
        WHERE villageId = :villageId AND isDeactivate = 0
    """)
    suspend fun villageBenCounts(villageId: Int): VillageBenCounts

    /**
     * Home glance "presumptive" count: TbDao.getDashboardPresumptiveTbCount with no filters
     * (all dates, any gender), read from TB_BEN_SUMMARY.presumptive.
     */
    @Query(
        "SELECT COUNT(*) FROM TB_BEN_SUMMARY s CROSS JOIN BENEFICIARY b ON b.beneficiaryId = s.beneficiaryId" +
            " WHERE s.presumptive = 1 AND b.isDraft = 0 AND b.isDeactivate = 0 AND IFNULL(b.isDeath, 0) = 0" +
            " AND b.loc_village_id IN (:villageIds)"
    )
    suspend fun villagePresumptiveCount(villageIds: List<Int>): Int

    /**
     * The three Tuberculosis cards in one pass: [getTbScreenedBenCount],
     * [BenTbListQueries.getTbPresumptiveListCount] and [BenTbListQueries.getTbConfirmedListCount].
     */
    @Query(
        "SELECT COUNT(CASE WHEN s.hasScreening = 1 THEN 1 END) AS screened," +
            " COUNT(CASE WHEN s.presumptive = 1 THEN 1 END) AS presumptive," +
            " COUNT(CASE WHEN b.benId IN " + TbCaseSql.CONFIRMED_BEN_IDS + " THEN 1 END) AS confirmed" +
            " FROM BEN_BASIC_CACHE b LEFT JOIN TB_BEN_SUMMARY s ON s.beneficiaryId = b.benId" +
            " WHERE b.villageId = :villageId AND b.isDeactivate = 0 AND b.isDeath = 0"
    )
    suspend fun villageTbCardCounts(villageId: Int): TbCardCounts

    /**
     * The five Referrals cards in one pass, same rules as [getDigitalChestXRayBenCount],
     * [getTrueNatBenCount], [getHwcBenDataCount], [getLiquidCultureBenCount] and
     * [getClinicalAssessmentBenCount] (see ReferralListSql / ClinicalAssessmentSql).
     */
    @Query("""
        SELECT
            COUNT(CASE WHEN b.reproductiveStatusId != 1 AND s.xrayList = 1 AND s.pregVitals = 0 THEN 1 END) AS xray,
            COUNT(CASE WHEN s.tnOther = 1
                       OR ((b.reproductiveStatusId = 1 OR s.pregVitals = 1) AND s.hasScreening = 1) THEN 1 END) AS trueNat,
            COUNT(CASE WHEN s.hwc = 1 OR b.benId IN (
                       SELECT b2.beneficiaryId FROM BENEFICIARY b2 WHERE b2.temperature IS NOT NULL AND b2.temperature >= 100.0
                  ) THEN 1 END) AS hwc,
            COUNT(CASE WHEN s.lc = 1 THEN 1 END) AS liquidCulture,
            COUNT(CASE WHEN s.caFixed = 1 OR (s.caD0 = 1 AND (s.caSym = 1 OR b.reproductiveStatusId = 1)
                       AND (b.reproductiveStatusId = 1
                            OR CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 14
                            OR s.caElig = 1)) THEN 1 END) AS clinical
        FROM BEN_BASIC_CACHE b LEFT JOIN TB_BEN_SUMMARY s ON s.beneficiaryId = b.benId
        WHERE b.villageId = :villageId AND b.isDeactivate = 0 AND b.isDeath = 0
    """)
    suspend fun villageReferralCardCounts(villageId: Int): ReferralCardCounts
}
