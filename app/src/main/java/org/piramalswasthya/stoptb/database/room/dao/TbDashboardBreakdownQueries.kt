package org.piramalswasthya.stoptb.database.room.dao

import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.piramalswasthya.stoptb.model.DashboardBreakdownCounts

/**
 * One query per dashboard section returning total / male / female / children / others / senior
 * together (v63 dashboard). Each is the matching TbDashboardQueries COUNT with the gender, child
 * and senior filters moved into the select list, so a section costs one query per write instead
 * of six. Part of [TBDao].
 */
interface TbDashboardBreakdownQueries {

    /** All six dashboard numbers of [TbDashboardQueries.getDashboardTbScreeningCount] in one query. */
    @Query("""
        SELECT COUNT(*) AS total, COALESCE(SUM(CASE WHEN UPPER(COALESCE(b.gender, '')) = 'MALE' THEN 1 ELSE 0 END), 0) AS male, COALESCE(SUM(CASE WHEN UPPER(COALESCE(b.gender, '')) = 'FEMALE' THEN 1 ELSE 0 END), 0) AS female, COALESCE(SUM(CASE WHEN CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15 THEN 1 ELSE 0 END), 0) AS children, COALESCE(SUM(CASE WHEN UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE') THEN 1 ELSE 0 END), 0) AS others, COALESCE(SUM(CASE WHEN CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) >= 60 THEN 1 ELSE 0 END), 0) AS seniorCitizen FROM TB_SCREENING ts
        INNER JOIN beneficiary b ON b.beneficiaryId = ts.benId
        WHERE (b.loc_village_id IN (:assignedVillageIds) AND (:villageName = '' OR b.loc_village_name = :villageName OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || ' (%' ESCAPE CHAR(92) OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || '(%' ESCAPE CHAR(92)))
        AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN ts.visitDate >= 100000000000 THEN ts.visitDate WHEN ts.visitDate > 0 THEN ts.visitDate * 1000 WHEN IFNULL(ts.serverUpdatedDate, 0) >= 100000000000 THEN ts.serverUpdatedDate WHEN IFNULL(ts.serverUpdatedDate, 0) > 0 THEN ts.serverUpdatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime)
        AND (:endTime = :endTime)
        AND IFNULL(b.isDeath, 0) = 0
    """)
    fun getDashboardTbScreeningBreakdown(assignedVillageIds: List<Int>, villageName: String, startTime: Long, endTime: Long): Flow<DashboardBreakdownCounts>

    /** All six dashboard numbers of [TbDashboardQueries.getDashboardPresumptiveTbCount] in one query. */
    @Query("SELECT COUNT(DISTINCT scr.benId) AS total," +
        " COUNT(DISTINCT CASE WHEN UPPER(COALESCE(b.gender, '')) = 'MALE' THEN scr.benId END) AS male," +
        " COUNT(DISTINCT CASE WHEN UPPER(COALESCE(b.gender, '')) = 'FEMALE' THEN scr.benId END) AS female," +
        " COUNT(DISTINCT CASE WHEN CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15 THEN scr.benId END) AS children," +
        " COUNT(DISTINCT CASE WHEN UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE') THEN scr.benId END) AS others," +
        " 0 AS seniorCitizen FROM TB_SCREENING scr" +
        " INNER JOIN beneficiary b ON b.beneficiaryId = scr.benId" +
        " LEFT JOIN TB_SUSPECTED sus ON b.beneficiaryId = sus.benId" +
        " LEFT JOIN TB_DIAGNOSTICS dx ON b.beneficiaryId = dx.benId" +
        " WHERE " + TbCaseSql.PRESUMPTIVE +
        " AND b.isDraft = 0 AND b.isDeactivate = 0 AND IFNULL(b.isDeath, 0) = 0" +
        """
    AND (b.loc_village_id IN (:assignedVillageIds) AND (:villageName = '' OR b.loc_village_name = :villageName OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || ' (%' ESCAPE CHAR(92) OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || '(%' ESCAPE CHAR(92)))
    AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN scr.visitDate >= 100000000000 THEN scr.visitDate WHEN scr.visitDate > 0 THEN scr.visitDate * 1000 WHEN IFNULL(scr.serverUpdatedDate, 0) >= 100000000000 THEN scr.serverUpdatedDate WHEN IFNULL(scr.serverUpdatedDate, 0) > 0 THEN scr.serverUpdatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime)
    AND (:endTime = :endTime)
    """)
    fun getDashboardPresumptiveTbBreakdown(assignedVillageIds: List<Int>, villageName: String, startTime: Long, endTime: Long): Flow<DashboardBreakdownCounts>

    /** All six dashboard numbers of [TbDashboardQueries.getDashboardPastHistoryTbCount] in one query. */
    @Query("""
        SELECT COUNT(*) AS total, COALESCE(SUM(CASE WHEN UPPER(COALESCE(b.gender, '')) = 'MALE' THEN 1 ELSE 0 END), 0) AS male, COALESCE(SUM(CASE WHEN UPPER(COALESCE(b.gender, '')) = 'FEMALE' THEN 1 ELSE 0 END), 0) AS female, COALESCE(SUM(CASE WHEN CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15 THEN 1 ELSE 0 END), 0) AS children, COALESCE(SUM(CASE WHEN UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE') THEN 1 ELSE 0 END), 0) AS others, COALESCE(SUM(CASE WHEN CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) >= 60 THEN 1 ELSE 0 END), 0) AS seniorCitizen FROM TB_SCREENING ts
        INNER JOIN beneficiary b ON b.beneficiaryId = ts.benId
        WHERE (b.loc_village_id IN (:assignedVillageIds) AND (:villageName = '' OR b.loc_village_name = :villageName OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || ' (%' ESCAPE CHAR(92) OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || '(%' ESCAPE CHAR(92)))
        AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN ts.visitDate >= 100000000000 THEN ts.visitDate WHEN ts.visitDate > 0 THEN ts.visitDate * 1000 WHEN IFNULL(ts.serverUpdatedDate, 0) >= 100000000000 THEN ts.serverUpdatedDate WHEN IFNULL(ts.serverUpdatedDate, 0) > 0 THEN ts.serverUpdatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime)
        AND (:endTime = :endTime)
        AND IFNULL(b.isDeath, 0) = 0 
        
        
        AND ts.historyOfTb = 1
    """)
    fun getDashboardPastHistoryTbBreakdown(assignedVillageIds: List<Int>, villageName: String, startTime: Long, endTime: Long): Flow<DashboardBreakdownCounts>

    /** All six dashboard numbers of [TbDashboardQueries.getDashboardAntiTbDrugsCount] in one query. */
    @Query("""
        SELECT COUNT(*) AS total, COALESCE(SUM(CASE WHEN UPPER(COALESCE(b.gender, '')) = 'MALE' THEN 1 ELSE 0 END), 0) AS male, COALESCE(SUM(CASE WHEN UPPER(COALESCE(b.gender, '')) = 'FEMALE' THEN 1 ELSE 0 END), 0) AS female, COALESCE(SUM(CASE WHEN CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15 THEN 1 ELSE 0 END), 0) AS children, COALESCE(SUM(CASE WHEN UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE') THEN 1 ELSE 0 END), 0) AS others, COALESCE(SUM(CASE WHEN CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) >= 60 THEN 1 ELSE 0 END), 0) AS seniorCitizen FROM TB_SCREENING ts
        INNER JOIN beneficiary b ON b.beneficiaryId = ts.benId
        WHERE (b.loc_village_id IN (:assignedVillageIds) AND (:villageName = '' OR b.loc_village_name = :villageName OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || ' (%' ESCAPE CHAR(92) OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || '(%' ESCAPE CHAR(92)))
        AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN ts.visitDate >= 100000000000 THEN ts.visitDate WHEN ts.visitDate > 0 THEN ts.visitDate * 1000 WHEN IFNULL(ts.serverUpdatedDate, 0) >= 100000000000 THEN ts.serverUpdatedDate WHEN IFNULL(ts.serverUpdatedDate, 0) > 0 THEN ts.serverUpdatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime)
        AND (:endTime = :endTime)
        AND IFNULL(b.isDeath, 0) = 0 
        
        
        AND ts.takingAntiTBDrugs = 1
    """)
    fun getDashboardAntiTbDrugsBreakdown(assignedVillageIds: List<Int>, villageName: String, startTime: Long, endTime: Long): Flow<DashboardBreakdownCounts>

    /** All six dashboard numbers of [TbDashboardQueries.getDashboardTbSuspectedCount] in one query. */
    @Query("""
        SELECT COUNT(*) AS total, COALESCE(SUM(CASE WHEN UPPER(COALESCE(bd_gender, '')) = 'MALE' THEN 1 ELSE 0 END), 0) AS male, COALESCE(SUM(CASE WHEN UPPER(COALESCE(bd_gender, '')) = 'FEMALE' THEN 1 ELSE 0 END), 0) AS female, COALESCE(SUM(CASE WHEN CAST((strftime('%s','now') - bd_dob/1000)/60/60/24/365 AS INTEGER) < 15 THEN 1 ELSE 0 END), 0) AS children, COALESCE(SUM(CASE WHEN UPPER(COALESCE(bd_gender, '')) NOT IN ('MALE', 'FEMALE') THEN 1 ELSE 0 END), 0) AS others, COALESCE(SUM(CASE WHEN CAST((strftime('%s','now') - bd_dob/1000)/60/60/24/365 AS INTEGER) >= 60 THEN 1 ELSE 0 END), 0) AS seniorCitizen FROM (
            SELECT b.beneficiaryId, b.gender AS bd_gender, b.dob AS bd_dob FROM TB_SUSPECTED ts
            INNER JOIN beneficiary b ON b.beneficiaryId = ts.benId
            WHERE (b.loc_village_id IN (:assignedVillageIds) AND (:villageName = '' OR b.loc_village_name = :villageName OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || ' (%' ESCAPE CHAR(92) OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || '(%' ESCAPE CHAR(92)))
            AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN ts.visitDate >= 100000000000 THEN ts.visitDate WHEN ts.visitDate > 0 THEN ts.visitDate * 1000 WHEN IFNULL(ts.serverUpdatedDate, 0) >= 100000000000 THEN ts.serverUpdatedDate WHEN IFNULL(ts.serverUpdatedDate, 0) > 0 THEN ts.serverUpdatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime)
            AND (:endTime = :endTime)
            AND IFNULL(b.isDeath, 0) = 0 
            
            
            UNION
            SELECT b.beneficiaryId, b.gender AS bd_gender, b.dob AS bd_dob FROM TB_DIAGNOSTICS td
            INNER JOIN beneficiary b ON b.beneficiaryId = td.benId
            WHERE (b.loc_village_id IN (:assignedVillageIds) AND (:villageName = '' OR b.loc_village_name = :villageName OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || ' (%' ESCAPE CHAR(92) OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || '(%' ESCAPE CHAR(92)))
            AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN td.visitDate >= 100000000000 THEN td.visitDate WHEN td.visitDate > 0 THEN td.visitDate * 1000 WHEN IFNULL(td.serverUpdatedDate, 0) >= 100000000000 THEN td.serverUpdatedDate WHEN IFNULL(td.serverUpdatedDate, 0) > 0 THEN td.serverUpdatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime)
            AND (:endTime = :endTime)
            AND IFNULL(b.isDeath, 0) = 0 
            
            
        )
    """)
    fun getDashboardTbSuspectedBreakdown(assignedVillageIds: List<Int>, villageName: String, startTime: Long, endTime: Long): Flow<DashboardBreakdownCounts>


    /** All six dashboard numbers of [TbDashboardQueries.getDashboardNikshayCount] in one query. */
    @Query("""
        SELECT COUNT(*) AS total, COALESCE(SUM(CASE WHEN UPPER(COALESCE(b.gender, '')) = 'MALE' THEN 1 ELSE 0 END), 0) AS male, COALESCE(SUM(CASE WHEN UPPER(COALESCE(b.gender, '')) = 'FEMALE' THEN 1 ELSE 0 END), 0) AS female, COALESCE(SUM(CASE WHEN CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15 THEN 1 ELSE 0 END), 0) AS children, COALESCE(SUM(CASE WHEN UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE') THEN 1 ELSE 0 END), 0) AS others, COALESCE(SUM(CASE WHEN CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) >= 60 THEN 1 ELSE 0 END), 0) AS seniorCitizen FROM TB_SUSPECTED ts
        INNER JOIN beneficiary b ON b.beneficiaryId = ts.benId
        WHERE ts.nikshayId IS NOT NULL AND ts.nikshayId != ''
        AND (b.loc_village_id IN (:assignedVillageIds) AND (:villageName = '' OR b.loc_village_name = :villageName OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || ' (%' ESCAPE CHAR(92) OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || '(%' ESCAPE CHAR(92)))
        AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN ts.visitDate >= 100000000000 THEN ts.visitDate WHEN ts.visitDate > 0 THEN ts.visitDate * 1000 WHEN IFNULL(ts.serverUpdatedDate, 0) >= 100000000000 THEN ts.serverUpdatedDate WHEN IFNULL(ts.serverUpdatedDate, 0) > 0 THEN ts.serverUpdatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime)
        AND (:endTime = :endTime)
        AND IFNULL(b.isDeath, 0) = 0
    """)
    fun getDashboardNikshayBreakdown(assignedVillageIds: List<Int>, villageName: String, startTime: Long, endTime: Long): Flow<DashboardBreakdownCounts>

    /** All six dashboard numbers of [TbDashboardQueries.getDashboardDigitalChestXRayCount] in one query. */
    @Query("""
        SELECT COUNT(*) AS total, COALESCE(SUM(CASE WHEN UPPER(COALESCE(bd_gender, '')) = 'MALE' THEN 1 ELSE 0 END), 0) AS male, COALESCE(SUM(CASE WHEN UPPER(COALESCE(bd_gender, '')) = 'FEMALE' THEN 1 ELSE 0 END), 0) AS female, COALESCE(SUM(CASE WHEN CAST((strftime('%s','now') - bd_dob/1000)/60/60/24/365 AS INTEGER) < 15 THEN 1 ELSE 0 END), 0) AS children, COALESCE(SUM(CASE WHEN UPPER(COALESCE(bd_gender, '')) NOT IN ('MALE', 'FEMALE') THEN 1 ELSE 0 END), 0) AS others, COALESCE(SUM(CASE WHEN CAST((strftime('%s','now') - bd_dob/1000)/60/60/24/365 AS INTEGER) >= 60 THEN 1 ELSE 0 END), 0) AS seniorCitizen FROM (
            SELECT b.beneficiaryId, b.gender AS bd_gender, b.dob AS bd_dob FROM TB_SUSPECTED ts
            INNER JOIN beneficiary b ON b.beneficiaryId = ts.benId
            LEFT JOIN TB_DIAGNOSTICS td ON td.benId = ts.benId
            WHERE ts.isChestXRayDone = 1 AND (td.benId IS NULL OR td.xrayOrderStatus = 'COMPLETED')
            AND (b.loc_village_id IN (:assignedVillageIds) AND (:villageName = '' OR b.loc_village_name = :villageName OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || ' (%' ESCAPE CHAR(92) OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || '(%' ESCAPE CHAR(92)))
            AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN ts.visitDate >= 100000000000 THEN ts.visitDate WHEN ts.visitDate > 0 THEN ts.visitDate * 1000 WHEN IFNULL(ts.serverUpdatedDate, 0) >= 100000000000 THEN ts.serverUpdatedDate WHEN IFNULL(ts.serverUpdatedDate, 0) > 0 THEN ts.serverUpdatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime)
            AND (:endTime = :endTime)
            AND IFNULL(b.isDeath, 0) = 0 
            
            
            UNION
            SELECT b.beneficiaryId, b.gender AS bd_gender, b.dob AS bd_dob FROM TB_DIAGNOSTICS td
            INNER JOIN beneficiary b ON b.beneficiaryId = td.benId
            WHERE td.isChestXRayDone = 1 AND td.xrayOrderStatus = 'COMPLETED'
            AND (b.loc_village_id IN (:assignedVillageIds) AND (:villageName = '' OR b.loc_village_name = :villageName OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || ' (%' ESCAPE CHAR(92) OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || '(%' ESCAPE CHAR(92)))
            AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN td.visitDate >= 100000000000 THEN td.visitDate WHEN td.visitDate > 0 THEN td.visitDate * 1000 WHEN IFNULL(td.serverUpdatedDate, 0) >= 100000000000 THEN td.serverUpdatedDate WHEN IFNULL(td.serverUpdatedDate, 0) > 0 THEN td.serverUpdatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime)
            AND (:endTime = :endTime)
            AND IFNULL(b.isDeath, 0) = 0 
            
            
        )
    """)
    fun getDashboardDigitalChestXRayBreakdown(assignedVillageIds: List<Int>, villageName: String, startTime: Long, endTime: Long): Flow<DashboardBreakdownCounts>

    /** All six dashboard numbers of [TbDashboardQueries.getDashboardSputumCollectionCount] in one query. */
    @Query("""
        SELECT COUNT(*) AS total, COALESCE(SUM(CASE WHEN UPPER(COALESCE(bd_gender, '')) = 'MALE' THEN 1 ELSE 0 END), 0) AS male, COALESCE(SUM(CASE WHEN UPPER(COALESCE(bd_gender, '')) = 'FEMALE' THEN 1 ELSE 0 END), 0) AS female, COALESCE(SUM(CASE WHEN CAST((strftime('%s','now') - bd_dob/1000)/60/60/24/365 AS INTEGER) < 15 THEN 1 ELSE 0 END), 0) AS children, COALESCE(SUM(CASE WHEN UPPER(COALESCE(bd_gender, '')) NOT IN ('MALE', 'FEMALE') THEN 1 ELSE 0 END), 0) AS others, COALESCE(SUM(CASE WHEN CAST((strftime('%s','now') - bd_dob/1000)/60/60/24/365 AS INTEGER) >= 60 THEN 1 ELSE 0 END), 0) AS seniorCitizen FROM (
            SELECT b.beneficiaryId, b.gender AS bd_gender, b.dob AS bd_dob FROM TB_SUSPECTED ts
            INNER JOIN beneficiary b ON b.beneficiaryId = ts.benId
            LEFT JOIN TB_DIAGNOSTICS td ON td.benId = ts.benId
            WHERE ts.isSputumCollected = 1 AND (td.benId IS NULL OR td.trueNatOrderStatus = 'COMPLETED' OR (td.trueNatOrderStatus = 'CLOSED' AND td.reasonForDenialSputum IS NOT NULL))
            AND (b.loc_village_id IN (:assignedVillageIds) AND (:villageName = '' OR b.loc_village_name = :villageName OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || ' (%' ESCAPE CHAR(92) OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || '(%' ESCAPE CHAR(92)))
            AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN ts.visitDate >= 100000000000 THEN ts.visitDate WHEN ts.visitDate > 0 THEN ts.visitDate * 1000 WHEN IFNULL(ts.serverUpdatedDate, 0) >= 100000000000 THEN ts.serverUpdatedDate WHEN IFNULL(ts.serverUpdatedDate, 0) > 0 THEN ts.serverUpdatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime)
            AND (:endTime = :endTime)
            AND IFNULL(b.isDeath, 0) = 0 
            
            
            UNION
            SELECT b.beneficiaryId, b.gender AS bd_gender, b.dob AS bd_dob FROM TB_DIAGNOSTICS td
            INNER JOIN beneficiary b ON b.beneficiaryId = td.benId
            WHERE td.isReferredForSputum = 1 AND (td.trueNatOrderStatus = 'COMPLETED' OR (td.trueNatOrderStatus = 'CLOSED' AND td.reasonForDenialSputum IS NOT NULL))
            AND (b.loc_village_id IN (:assignedVillageIds) AND (:villageName = '' OR b.loc_village_name = :villageName OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || ' (%' ESCAPE CHAR(92) OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || '(%' ESCAPE CHAR(92)))
            AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN td.visitDate >= 100000000000 THEN td.visitDate WHEN td.visitDate > 0 THEN td.visitDate * 1000 WHEN IFNULL(td.serverUpdatedDate, 0) >= 100000000000 THEN td.serverUpdatedDate WHEN IFNULL(td.serverUpdatedDate, 0) > 0 THEN td.serverUpdatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime)
            AND (:endTime = :endTime)
            AND IFNULL(b.isDeath, 0) = 0 
            
            
        )
    """)
    fun getDashboardSputumCollectionBreakdown(assignedVillageIds: List<Int>, villageName: String, startTime: Long, endTime: Long): Flow<DashboardBreakdownCounts>

    /** All six dashboard numbers of [TbDashboardQueries.getDashboardTrueNatCount] in one query. */
    @Query("""
        SELECT COUNT(*) AS total, COALESCE(SUM(CASE WHEN UPPER(COALESCE(bd_gender, '')) = 'MALE' THEN 1 ELSE 0 END), 0) AS male, COALESCE(SUM(CASE WHEN UPPER(COALESCE(bd_gender, '')) = 'FEMALE' THEN 1 ELSE 0 END), 0) AS female, COALESCE(SUM(CASE WHEN CAST((strftime('%s','now') - bd_dob/1000)/60/60/24/365 AS INTEGER) < 15 THEN 1 ELSE 0 END), 0) AS children, COALESCE(SUM(CASE WHEN UPPER(COALESCE(bd_gender, '')) NOT IN ('MALE', 'FEMALE') THEN 1 ELSE 0 END), 0) AS others, COALESCE(SUM(CASE WHEN CAST((strftime('%s','now') - bd_dob/1000)/60/60/24/365 AS INTEGER) >= 60 THEN 1 ELSE 0 END), 0) AS seniorCitizen FROM (
            SELECT b.beneficiaryId, b.gender AS bd_gender, b.dob AS bd_dob FROM TB_SUSPECTED ts
            INNER JOIN beneficiary b ON b.beneficiaryId = ts.benId
            LEFT JOIN TB_DIAGNOSTICS td ON td.benId = ts.benId
            WHERE ts.isNaatConducted = 1 AND (td.benId IS NULL OR td.trueNatOrderStatus = 'COMPLETED')
            AND (b.loc_village_id IN (:assignedVillageIds) AND (:villageName = '' OR b.loc_village_name = :villageName OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || ' (%' ESCAPE CHAR(92) OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || '(%' ESCAPE CHAR(92)))
            AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN ts.visitDate >= 100000000000 THEN ts.visitDate WHEN ts.visitDate > 0 THEN ts.visitDate * 1000 WHEN IFNULL(ts.serverUpdatedDate, 0) >= 100000000000 THEN ts.serverUpdatedDate WHEN IFNULL(ts.serverUpdatedDate, 0) > 0 THEN ts.serverUpdatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime)
            AND (:endTime = :endTime)
            AND IFNULL(b.isDeath, 0) = 0 
            
            
            UNION
            SELECT b.beneficiaryId, b.gender AS bd_gender, b.dob AS bd_dob FROM TB_DIAGNOSTICS td
            INNER JOIN beneficiary b ON b.beneficiaryId = td.benId
            WHERE td.isNaatConducted = 1 AND td.trueNatOrderStatus = 'COMPLETED'
            AND (b.loc_village_id IN (:assignedVillageIds) AND (:villageName = '' OR b.loc_village_name = :villageName OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || ' (%' ESCAPE CHAR(92) OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || '(%' ESCAPE CHAR(92)))
            AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN td.visitDate >= 100000000000 THEN td.visitDate WHEN td.visitDate > 0 THEN td.visitDate * 1000 WHEN IFNULL(td.serverUpdatedDate, 0) >= 100000000000 THEN td.serverUpdatedDate WHEN IFNULL(td.serverUpdatedDate, 0) > 0 THEN td.serverUpdatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime)
            AND (:endTime = :endTime)
            AND IFNULL(b.isDeath, 0) = 0 
            
            
        )
    """)
    fun getDashboardTrueNatBreakdown(assignedVillageIds: List<Int>, villageName: String, startTime: Long, endTime: Long): Flow<DashboardBreakdownCounts>

    /** All six dashboard numbers of [TbDashboardQueries.getDashboardLiquidCultureCount] in one query. */
    @Query("""
        SELECT COUNT(*) AS total, COALESCE(SUM(CASE WHEN UPPER(COALESCE(bd_gender, '')) = 'MALE' THEN 1 ELSE 0 END), 0) AS male, COALESCE(SUM(CASE WHEN UPPER(COALESCE(bd_gender, '')) = 'FEMALE' THEN 1 ELSE 0 END), 0) AS female, COALESCE(SUM(CASE WHEN CAST((strftime('%s','now') - bd_dob/1000)/60/60/24/365 AS INTEGER) < 15 THEN 1 ELSE 0 END), 0) AS children, COALESCE(SUM(CASE WHEN UPPER(COALESCE(bd_gender, '')) NOT IN ('MALE', 'FEMALE') THEN 1 ELSE 0 END), 0) AS others, COALESCE(SUM(CASE WHEN CAST((strftime('%s','now') - bd_dob/1000)/60/60/24/365 AS INTEGER) >= 60 THEN 1 ELSE 0 END), 0) AS seniorCitizen FROM (
            SELECT b.beneficiaryId, b.gender AS bd_gender, b.dob AS bd_dob FROM TB_SUSPECTED ts
            INNER JOIN beneficiary b ON b.beneficiaryId = ts.benId
            WHERE ts.isLiquidCultureConducted = 1
            AND (b.loc_village_id IN (:assignedVillageIds) AND (:villageName = '' OR b.loc_village_name = :villageName OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || ' (%' ESCAPE CHAR(92) OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || '(%' ESCAPE CHAR(92)))
            AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN ts.visitDate >= 100000000000 THEN ts.visitDate WHEN ts.visitDate > 0 THEN ts.visitDate * 1000 WHEN IFNULL(ts.serverUpdatedDate, 0) >= 100000000000 THEN ts.serverUpdatedDate WHEN IFNULL(ts.serverUpdatedDate, 0) > 0 THEN ts.serverUpdatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime)
            AND (:endTime = :endTime)
            AND IFNULL(b.isDeath, 0) = 0 
            
            
            UNION
            SELECT b.beneficiaryId, b.gender AS bd_gender, b.dob AS bd_dob FROM TB_DIAGNOSTICS td
            INNER JOIN beneficiary b ON b.beneficiaryId = td.benId
            WHERE td.isLiquidCultureConducted = 1
            AND (b.loc_village_id IN (:assignedVillageIds) AND (:villageName = '' OR b.loc_village_name = :villageName OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || ' (%' ESCAPE CHAR(92) OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || '(%' ESCAPE CHAR(92)))
            AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN td.visitDate >= 100000000000 THEN td.visitDate WHEN td.visitDate > 0 THEN td.visitDate * 1000 WHEN IFNULL(td.serverUpdatedDate, 0) >= 100000000000 THEN td.serverUpdatedDate WHEN IFNULL(td.serverUpdatedDate, 0) > 0 THEN td.serverUpdatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime)
            AND (:endTime = :endTime)
            AND IFNULL(b.isDeath, 0) = 0 
            
            
        )
    """)
    fun getDashboardLiquidCultureBreakdown(assignedVillageIds: List<Int>, villageName: String, startTime: Long, endTime: Long): Flow<DashboardBreakdownCounts>

    /** All six dashboard numbers of [TbDashboardQueries.getDashboardHwcReferralCount] in one query. */
    @Query("""
        SELECT COUNT(*) AS total, COALESCE(SUM(CASE WHEN UPPER(COALESCE(bd_gender, '')) = 'MALE' THEN 1 ELSE 0 END), 0) AS male, COALESCE(SUM(CASE WHEN UPPER(COALESCE(bd_gender, '')) = 'FEMALE' THEN 1 ELSE 0 END), 0) AS female, COALESCE(SUM(CASE WHEN CAST((strftime('%s','now') - bd_dob/1000)/60/60/24/365 AS INTEGER) < 15 THEN 1 ELSE 0 END), 0) AS children, COALESCE(SUM(CASE WHEN UPPER(COALESCE(bd_gender, '')) NOT IN ('MALE', 'FEMALE') THEN 1 ELSE 0 END), 0) AS others, COALESCE(SUM(CASE WHEN CAST((strftime('%s','now') - bd_dob/1000)/60/60/24/365 AS INTEGER) >= 60 THEN 1 ELSE 0 END), 0) AS seniorCitizen FROM (
            SELECT b.beneficiaryId, b.gender AS bd_gender, b.dob AS bd_dob FROM beneficiary b
            WHERE b.temperature IS NOT NULL AND b.temperature >= 100.0
            AND (b.loc_village_id IN (:assignedVillageIds) AND (:villageName = '' OR b.loc_village_name = :villageName OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || ' (%' ESCAPE CHAR(92) OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || '(%' ESCAPE CHAR(92)))
            AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN IFNULL(b.updatedDate, 0) >= 100000000000 THEN b.updatedDate WHEN IFNULL(b.updatedDate, 0) > 0 THEN b.updatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime)
            AND (:endTime = :endTime)
            AND IFNULL(b.isDeath, 0) = 0 
            
            
            UNION
            SELECT b.beneficiaryId, b.gender AS bd_gender, b.dob AS bd_dob FROM BEN_VITALS v
            INNER JOIN beneficiary b ON b.beneficiaryId = v.benId
            WHERE ((v.pulseRate IS NOT NULL AND v.pulseRate < 60)
               OR (v.pulseRate IS NOT NULL AND v.pulseRate > 90)
               OR (v.bpSystolic IS NOT NULL AND v.bpSystolic < 90)
               OR (v.bpSystolic IS NOT NULL AND v.bpSystolic >= 140)
               OR (v.bpDiastolic IS NOT NULL AND v.bpDiastolic < 60)
               OR (v.bpDiastolic IS NOT NULL AND v.bpDiastolic >= 90)
               OR (v.rbs IS NOT NULL AND v.rbs >= 100)
               OR (v.pallorId = 1 OR UPPER(TRIM(IFNULL(v.pallor, ''))) IN ('PRESENT', 'YES'))
               OR (v.icterusId = 1 OR UPPER(TRIM(IFNULL(v.icterus, ''))) IN ('PRESENT', 'YES'))
               OR (v.cyanosisId = 1 OR UPPER(TRIM(IFNULL(v.cyanosis, ''))) IN ('PRESENT', 'YES'))
               OR (v.clubbingId = 1 OR UPPER(TRIM(IFNULL(v.clubbing, ''))) IN ('PRESENT', 'YES'))
               OR (v.lymphadenopathyId = 1 OR UPPER(TRIM(IFNULL(v.lymphadenopathy, ''))) IN ('PRESENT', 'YES'))
               OR (v.oedemaId = 1 OR UPPER(TRIM(IFNULL(v.oedema, ''))) IN ('PRESENT', 'YES')))
            AND (b.loc_village_id IN (:assignedVillageIds) AND (:villageName = '' OR b.loc_village_name = :villageName OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || ' (%' ESCAPE CHAR(92) OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || '(%' ESCAPE CHAR(92)))
            AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN IFNULL(v.capturedAt, 0) >= 100000000000 THEN v.capturedAt WHEN IFNULL(v.capturedAt, 0) > 0 THEN v.capturedAt * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime)
            AND (:endTime = :endTime)
            AND IFNULL(b.isDeath, 0) = 0 
            
            
        )
    """)
    fun getDashboardHwcReferralBreakdown(assignedVillageIds: List<Int>, villageName: String, startTime: Long, endTime: Long): Flow<DashboardBreakdownCounts>

    /** All six dashboard numbers of [TbDashboardQueries.getDashboardUnscreenedCount] in one query. */
    @Query("""
        SELECT COUNT(*) AS total, COALESCE(SUM(CASE WHEN UPPER(COALESCE(b.gender, '')) = 'MALE' THEN 1 ELSE 0 END), 0) AS male, COALESCE(SUM(CASE WHEN UPPER(COALESCE(b.gender, '')) = 'FEMALE' THEN 1 ELSE 0 END), 0) AS female, COALESCE(SUM(CASE WHEN CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15 THEN 1 ELSE 0 END), 0) AS children, COALESCE(SUM(CASE WHEN UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE') THEN 1 ELSE 0 END), 0) AS others, 0 AS seniorCitizen FROM beneficiary b
        WHERE b.beneficiaryId NOT IN (SELECT benId FROM TB_SCREENING)
        AND (b.loc_village_id IN (:assignedVillageIds) AND (:villageName = '' OR b.loc_village_name = :villageName OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || ' (%' ESCAPE CHAR(92) OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || '(%' ESCAPE CHAR(92)))
        AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN IFNULL(b.updatedDate, 0) >= 100000000000 THEN b.updatedDate WHEN IFNULL(b.updatedDate, 0) > 0 THEN b.updatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime)
        AND (:endTime = :endTime)
        AND IFNULL(b.isDeath, 0) = 0
    """)
    fun getDashboardUnscreenedBreakdown(assignedVillageIds: List<Int>, villageName: String, startTime: Long, endTime: Long): Flow<DashboardBreakdownCounts>
}
