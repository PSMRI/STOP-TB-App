package org.piramalswasthya.stoptb.database.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Upsert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import org.piramalswasthya.stoptb.model.GeneralOpdPrescription
import kotlinx.coroutines.flow.Flow
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.model.GeneralOpdCache
import org.piramalswasthya.stoptb.model.TBConfirmedTreatmentCache
import org.piramalswasthya.stoptb.model.TBDiagnosticsCache
import org.piramalswasthya.stoptb.model.TbDiagnosticsCardFields
import org.piramalswasthya.stoptb.model.TBScreeningCache
import org.piramalswasthya.stoptb.model.TBSuspectedCache
import org.piramalswasthya.stoptb.model.TbScreeningSymptoms

/**
 * Dashboard COUNT queries (TB screening, presumptive, diagnostics, confirmed, Nikshay) with time and village filters.
 *
 * Part of [TBDao] (which extends this interface); split out only to keep the DAO
 * readable. Room generates these queries as part of TBDao_Impl - SQL and callers unchanged.
 */
interface TbDashboardQueries {

    // Dashboard queries - TB Screening count by gender with time + village filter
    @Query("""
        SELECT COUNT(*) FROM TB_SCREENING ts
        INNER JOIN beneficiary b ON b.beneficiaryId = ts.benId
        WHERE (b.loc_village_id IN (:assignedVillageIds) AND (:villageName = '' OR b.loc_village_name = :villageName OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || ' (%' ESCAPE CHAR(92) OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || '(%' ESCAPE CHAR(92)))
        AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN ts.visitDate >= 100000000000 THEN ts.visitDate WHEN ts.visitDate > 0 THEN ts.visitDate * 1000 WHEN IFNULL(ts.serverUpdatedDate, 0) >= 100000000000 THEN ts.serverUpdatedDate WHEN IFNULL(ts.serverUpdatedDate, 0) > 0 THEN ts.serverUpdatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime)
        AND (:endTime = :endTime)
        AND IFNULL(b.isDeath, 0) = 0 AND (:gender = '' OR (:gender != 'OTHERS' AND UPPER(COALESCE(b.gender, '')) = UPPER(:gender)) OR (:gender = 'OTHERS' AND UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE')))
        AND (:isChild = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15))
        AND (:isSeniorCitizen  = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) >= 60))
    """)
    fun getDashboardTbScreeningCount(assignedVillageIds: List<Int>, villageName: String, startTime: Long, endTime: Long, gender: String, isChild: Int,    isSeniorCitizen: Int
    ): Flow<Int>


    // Dashboard query - Presumptive TB. Definition shared with the Presumptive list: [TbCaseSql.PRESUMPTIVE].
    @Query("SELECT COUNT(DISTINCT scr.benId) FROM TB_SCREENING scr" +
        " INNER JOIN beneficiary b ON b.beneficiaryId = scr.benId" +
        " LEFT JOIN TB_SUSPECTED sus ON b.beneficiaryId = sus.benId" +
        " LEFT JOIN TB_DIAGNOSTICS dx ON b.beneficiaryId = dx.benId" +
        " WHERE " + TbCaseSql.PRESUMPTIVE +
        " AND b.isDraft = 0 AND b.isDeactivate = 0 AND IFNULL(b.isDeath, 0) = 0" +
        """
    AND (b.loc_village_id IN (:assignedVillageIds) AND (:villageName = '' OR b.loc_village_name = :villageName OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || ' (%' ESCAPE CHAR(92) OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || '(%' ESCAPE CHAR(92)))
    AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN scr.visitDate >= 100000000000 THEN scr.visitDate WHEN scr.visitDate > 0 THEN scr.visitDate * 1000 WHEN IFNULL(scr.serverUpdatedDate, 0) >= 100000000000 THEN scr.serverUpdatedDate WHEN IFNULL(scr.serverUpdatedDate, 0) > 0 THEN scr.serverUpdatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime)
    AND (:endTime = :endTime)
    AND (:gender = '' OR (:gender != 'OTHERS' AND UPPER(COALESCE(b.gender, '')) = UPPER(:gender)) OR (:gender = 'OTHERS' AND UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE')))
    AND (:isChild = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15))
""")
    fun getDashboardPresumptiveTbCount(
        assignedVillageIds: List<Int>,
        villageName: String,
        startTime: Long,
        endTime: Long,
        gender: String,
        isChild: Int
    ): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM TB_SCREENING ts
        INNER JOIN beneficiary b ON b.beneficiaryId = ts.benId
        WHERE (b.loc_village_id IN (:assignedVillageIds) AND (:villageName = '' OR b.loc_village_name = :villageName OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || ' (%' ESCAPE CHAR(92) OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || '(%' ESCAPE CHAR(92)))
        AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN ts.visitDate >= 100000000000 THEN ts.visitDate WHEN ts.visitDate > 0 THEN ts.visitDate * 1000 WHEN IFNULL(ts.serverUpdatedDate, 0) >= 100000000000 THEN ts.serverUpdatedDate WHEN IFNULL(ts.serverUpdatedDate, 0) > 0 THEN ts.serverUpdatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime)
        AND (:endTime = :endTime)
        AND IFNULL(b.isDeath, 0) = 0 AND (:gender = '' OR (:gender != 'OTHERS' AND UPPER(COALESCE(b.gender, '')) = UPPER(:gender)) OR (:gender = 'OTHERS' AND UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE')))
        AND (:isChild = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15))
        AND (:isSeniorCitizen = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) >= 60))
        AND ts.historyOfTb = 1
    """)
    fun getDashboardPastHistoryTbCount(
        assignedVillageIds: List<Int>,
        villageName: String,
        startTime: Long,
        endTime: Long,
        gender: String,
        isChild: Int,
        isSeniorCitizen: Int
    ): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM TB_SCREENING ts
        INNER JOIN beneficiary b ON b.beneficiaryId = ts.benId
        WHERE (b.loc_village_id IN (:assignedVillageIds) AND (:villageName = '' OR b.loc_village_name = :villageName OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || ' (%' ESCAPE CHAR(92) OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || '(%' ESCAPE CHAR(92)))
        AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN ts.visitDate >= 100000000000 THEN ts.visitDate WHEN ts.visitDate > 0 THEN ts.visitDate * 1000 WHEN IFNULL(ts.serverUpdatedDate, 0) >= 100000000000 THEN ts.serverUpdatedDate WHEN IFNULL(ts.serverUpdatedDate, 0) > 0 THEN ts.serverUpdatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime)
        AND (:endTime = :endTime)
        AND IFNULL(b.isDeath, 0) = 0 AND (:gender = '' OR (:gender != 'OTHERS' AND UPPER(COALESCE(b.gender, '')) = UPPER(:gender)) OR (:gender = 'OTHERS' AND UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE')))
        AND (:isChild = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15))
        AND (:isSeniorCitizen = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) >= 60))
        AND ts.takingAntiTBDrugs = 1
    """)
    fun getDashboardAntiTbDrugsCount(
        assignedVillageIds: List<Int>,
        villageName: String,
        startTime: Long,
        endTime: Long,
        gender: String,
        isChild: Int,
        isSeniorCitizen: Int
    ): Flow<Int>

    // Dashboard queries - TB Suspected count by gender with time + village filter
    @Query("""
        SELECT COUNT(*) FROM (
            SELECT b.beneficiaryId FROM TB_SUSPECTED ts
            INNER JOIN beneficiary b ON b.beneficiaryId = ts.benId
            WHERE (b.loc_village_id IN (:assignedVillageIds) AND (:villageName = '' OR b.loc_village_name = :villageName OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || ' (%' ESCAPE CHAR(92) OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || '(%' ESCAPE CHAR(92)))
            AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN ts.visitDate >= 100000000000 THEN ts.visitDate WHEN ts.visitDate > 0 THEN ts.visitDate * 1000 WHEN IFNULL(ts.serverUpdatedDate, 0) >= 100000000000 THEN ts.serverUpdatedDate WHEN IFNULL(ts.serverUpdatedDate, 0) > 0 THEN ts.serverUpdatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime)
            AND (:endTime = :endTime)
            AND IFNULL(b.isDeath, 0) = 0 AND (:gender = '' OR (:gender != 'OTHERS' AND UPPER(COALESCE(b.gender, '')) = UPPER(:gender)) OR (:gender = 'OTHERS' AND UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE')))
            AND (:isChild = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15))
            AND (:isSeniorCitizen = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) >= 60))
            UNION
            SELECT b.beneficiaryId FROM TB_DIAGNOSTICS td
            INNER JOIN beneficiary b ON b.beneficiaryId = td.benId
            WHERE (b.loc_village_id IN (:assignedVillageIds) AND (:villageName = '' OR b.loc_village_name = :villageName OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || ' (%' ESCAPE CHAR(92) OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || '(%' ESCAPE CHAR(92)))
            AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN td.visitDate >= 100000000000 THEN td.visitDate WHEN td.visitDate > 0 THEN td.visitDate * 1000 WHEN IFNULL(td.serverUpdatedDate, 0) >= 100000000000 THEN td.serverUpdatedDate WHEN IFNULL(td.serverUpdatedDate, 0) > 0 THEN td.serverUpdatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime)
            AND (:endTime = :endTime)
            AND IFNULL(b.isDeath, 0) = 0 AND (:gender = '' OR (:gender != 'OTHERS' AND UPPER(COALESCE(b.gender, '')) = UPPER(:gender)) OR (:gender = 'OTHERS' AND UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE')))
            AND (:isChild = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15))
            AND (:isSeniorCitizen = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) >= 60))
        )
    """)
    fun getDashboardTbSuspectedCount(assignedVillageIds: List<Int>, villageName: String, startTime: Long, endTime: Long, gender: String, isChild: Int,isSeniorCitizen: Int): Flow<Int>

    // NIKSHAY IDs count with time + village filter
    @Query("""
        SELECT COUNT(*) FROM TB_SUSPECTED ts
        INNER JOIN beneficiary b ON b.beneficiaryId = ts.benId
        WHERE ts.nikshayId IS NOT NULL AND ts.nikshayId != ''
        AND (b.loc_village_id IN (:assignedVillageIds) AND (:villageName = '' OR b.loc_village_name = :villageName OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || ' (%' ESCAPE CHAR(92) OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || '(%' ESCAPE CHAR(92)))
        AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN ts.visitDate >= 100000000000 THEN ts.visitDate WHEN ts.visitDate > 0 THEN ts.visitDate * 1000 WHEN IFNULL(ts.serverUpdatedDate, 0) >= 100000000000 THEN ts.serverUpdatedDate WHEN IFNULL(ts.serverUpdatedDate, 0) > 0 THEN ts.serverUpdatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime)
        AND (:endTime = :endTime)
        AND IFNULL(b.isDeath, 0) = 0 AND (:gender = '' OR (:gender != 'OTHERS' AND UPPER(COALESCE(b.gender, '')) = UPPER(:gender)) OR (:gender = 'OTHERS' AND UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE')))
        AND (:isChild = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15))
        AND (:isSeniorCitizen = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) >= 60))
    """)
    fun getDashboardNikshayCount(
        assignedVillageIds: List<Int>,
        villageName: String,
        startTime: Long,
        endTime: Long,
        gender: String,
        isChild: Int,
        isSeniorCitizen: Int,
    ): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM (
            SELECT b.beneficiaryId FROM TB_SUSPECTED ts
            INNER JOIN beneficiary b ON b.beneficiaryId = ts.benId
            LEFT JOIN TB_DIAGNOSTICS td ON td.benId = ts.benId
            WHERE ts.isChestXRayDone = 1 AND (td.benId IS NULL OR td.xrayOrderStatus = 'COMPLETED')
            AND (b.loc_village_id IN (:assignedVillageIds) AND (:villageName = '' OR b.loc_village_name = :villageName OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || ' (%' ESCAPE CHAR(92) OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || '(%' ESCAPE CHAR(92)))
            AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN ts.visitDate >= 100000000000 THEN ts.visitDate WHEN ts.visitDate > 0 THEN ts.visitDate * 1000 WHEN IFNULL(ts.serverUpdatedDate, 0) >= 100000000000 THEN ts.serverUpdatedDate WHEN IFNULL(ts.serverUpdatedDate, 0) > 0 THEN ts.serverUpdatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime)
            AND (:endTime = :endTime)
            AND IFNULL(b.isDeath, 0) = 0 AND (:gender = '' OR (:gender != 'OTHERS' AND UPPER(COALESCE(b.gender, '')) = UPPER(:gender)) OR (:gender = 'OTHERS' AND UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE')))
            AND (:isChild = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15))
            AND (:isSeniorCitizen = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) >= 60))
            UNION
            SELECT b.beneficiaryId FROM TB_DIAGNOSTICS td
            INNER JOIN beneficiary b ON b.beneficiaryId = td.benId
            WHERE td.isChestXRayDone = 1 AND td.xrayOrderStatus = 'COMPLETED'
            AND (b.loc_village_id IN (:assignedVillageIds) AND (:villageName = '' OR b.loc_village_name = :villageName OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || ' (%' ESCAPE CHAR(92) OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || '(%' ESCAPE CHAR(92)))
            AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN td.visitDate >= 100000000000 THEN td.visitDate WHEN td.visitDate > 0 THEN td.visitDate * 1000 WHEN IFNULL(td.serverUpdatedDate, 0) >= 100000000000 THEN td.serverUpdatedDate WHEN IFNULL(td.serverUpdatedDate, 0) > 0 THEN td.serverUpdatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime)
            AND (:endTime = :endTime)
            AND IFNULL(b.isDeath, 0) = 0 AND (:gender = '' OR (:gender != 'OTHERS' AND UPPER(COALESCE(b.gender, '')) = UPPER(:gender)) OR (:gender = 'OTHERS' AND UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE')))
            AND (:isChild = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15))
            AND (:isSeniorCitizen = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) >= 60))
        )
    """)
    fun getDashboardDigitalChestXRayCount(assignedVillageIds: List<Int>, villageName: String, startTime: Long, endTime: Long, gender: String, isChild: Int, isSeniorCitizen: Int): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM (
            SELECT b.beneficiaryId FROM TB_SUSPECTED ts
            INNER JOIN beneficiary b ON b.beneficiaryId = ts.benId
            LEFT JOIN TB_DIAGNOSTICS td ON td.benId = ts.benId
            WHERE ts.isSputumCollected = 1 AND (td.benId IS NULL OR td.trueNatOrderStatus = 'COMPLETED' OR (td.trueNatOrderStatus = 'CLOSED' AND td.reasonForDenialSputum IS NOT NULL))
            AND (b.loc_village_id IN (:assignedVillageIds) AND (:villageName = '' OR b.loc_village_name = :villageName OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || ' (%' ESCAPE CHAR(92) OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || '(%' ESCAPE CHAR(92)))
            AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN ts.visitDate >= 100000000000 THEN ts.visitDate WHEN ts.visitDate > 0 THEN ts.visitDate * 1000 WHEN IFNULL(ts.serverUpdatedDate, 0) >= 100000000000 THEN ts.serverUpdatedDate WHEN IFNULL(ts.serverUpdatedDate, 0) > 0 THEN ts.serverUpdatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime)
            AND (:endTime = :endTime)
            AND IFNULL(b.isDeath, 0) = 0 AND (:gender = '' OR (:gender != 'OTHERS' AND UPPER(COALESCE(b.gender, '')) = UPPER(:gender)) OR (:gender = 'OTHERS' AND UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE')))
            AND (:isChild = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15))
            AND (:isSeniorCitizen = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) >= 60))
            UNION
            SELECT b.beneficiaryId FROM TB_DIAGNOSTICS td
            INNER JOIN beneficiary b ON b.beneficiaryId = td.benId
            WHERE td.isReferredForSputum = 1 AND (td.trueNatOrderStatus = 'COMPLETED' OR (td.trueNatOrderStatus = 'CLOSED' AND td.reasonForDenialSputum IS NOT NULL))
            AND (b.loc_village_id IN (:assignedVillageIds) AND (:villageName = '' OR b.loc_village_name = :villageName OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || ' (%' ESCAPE CHAR(92) OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || '(%' ESCAPE CHAR(92)))
            AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN td.visitDate >= 100000000000 THEN td.visitDate WHEN td.visitDate > 0 THEN td.visitDate * 1000 WHEN IFNULL(td.serverUpdatedDate, 0) >= 100000000000 THEN td.serverUpdatedDate WHEN IFNULL(td.serverUpdatedDate, 0) > 0 THEN td.serverUpdatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime)
            AND (:endTime = :endTime)
            AND IFNULL(b.isDeath, 0) = 0 AND (:gender = '' OR (:gender != 'OTHERS' AND UPPER(COALESCE(b.gender, '')) = UPPER(:gender)) OR (:gender = 'OTHERS' AND UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE')))
            AND (:isChild = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15))
            AND (:isSeniorCitizen = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) >= 60))
        )
    """)
    fun getDashboardSputumCollectionCount(assignedVillageIds: List<Int>, villageName: String, startTime: Long, endTime: Long, gender: String, isChild: Int, isSeniorCitizen: Int): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM (
            SELECT b.beneficiaryId FROM TB_SUSPECTED ts
            INNER JOIN beneficiary b ON b.beneficiaryId = ts.benId
            LEFT JOIN TB_DIAGNOSTICS td ON td.benId = ts.benId
            WHERE ts.isNaatConducted = 1 AND (td.benId IS NULL OR td.trueNatOrderStatus = 'COMPLETED')
            AND (b.loc_village_id IN (:assignedVillageIds) AND (:villageName = '' OR b.loc_village_name = :villageName OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || ' (%' ESCAPE CHAR(92) OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || '(%' ESCAPE CHAR(92)))
            AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN ts.visitDate >= 100000000000 THEN ts.visitDate WHEN ts.visitDate > 0 THEN ts.visitDate * 1000 WHEN IFNULL(ts.serverUpdatedDate, 0) >= 100000000000 THEN ts.serverUpdatedDate WHEN IFNULL(ts.serverUpdatedDate, 0) > 0 THEN ts.serverUpdatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime)
            AND (:endTime = :endTime)
            AND IFNULL(b.isDeath, 0) = 0 AND (:gender = '' OR (:gender != 'OTHERS' AND UPPER(COALESCE(b.gender, '')) = UPPER(:gender)) OR (:gender = 'OTHERS' AND UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE')))
            AND (:isChild = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15))
            AND (:isSeniorCitizen = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) >= 60))
            UNION
            SELECT b.beneficiaryId FROM TB_DIAGNOSTICS td
            INNER JOIN beneficiary b ON b.beneficiaryId = td.benId
            WHERE td.isNaatConducted = 1 AND td.trueNatOrderStatus = 'COMPLETED'
            AND (b.loc_village_id IN (:assignedVillageIds) AND (:villageName = '' OR b.loc_village_name = :villageName OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || ' (%' ESCAPE CHAR(92) OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || '(%' ESCAPE CHAR(92)))
            AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN td.visitDate >= 100000000000 THEN td.visitDate WHEN td.visitDate > 0 THEN td.visitDate * 1000 WHEN IFNULL(td.serverUpdatedDate, 0) >= 100000000000 THEN td.serverUpdatedDate WHEN IFNULL(td.serverUpdatedDate, 0) > 0 THEN td.serverUpdatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime)
            AND (:endTime = :endTime)
            AND IFNULL(b.isDeath, 0) = 0 AND (:gender = '' OR (:gender != 'OTHERS' AND UPPER(COALESCE(b.gender, '')) = UPPER(:gender)) OR (:gender = 'OTHERS' AND UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE')))
            AND (:isChild = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15))
            AND (:isSeniorCitizen = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) >= 60))
        )
    """)
    fun getDashboardTrueNatCount(assignedVillageIds: List<Int>, villageName: String, startTime: Long, endTime: Long, gender: String, isChild: Int, isSeniorCitizen: Int): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM (
            SELECT b.beneficiaryId FROM TB_SUSPECTED ts
            INNER JOIN beneficiary b ON b.beneficiaryId = ts.benId
            WHERE ts.isLiquidCultureConducted = 1
            AND (b.loc_village_id IN (:assignedVillageIds) AND (:villageName = '' OR b.loc_village_name = :villageName OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || ' (%' ESCAPE CHAR(92) OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || '(%' ESCAPE CHAR(92)))
            AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN ts.visitDate >= 100000000000 THEN ts.visitDate WHEN ts.visitDate > 0 THEN ts.visitDate * 1000 WHEN IFNULL(ts.serverUpdatedDate, 0) >= 100000000000 THEN ts.serverUpdatedDate WHEN IFNULL(ts.serverUpdatedDate, 0) > 0 THEN ts.serverUpdatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime)
            AND (:endTime = :endTime)
            AND IFNULL(b.isDeath, 0) = 0 AND (:gender = '' OR (:gender != 'OTHERS' AND UPPER(COALESCE(b.gender, '')) = UPPER(:gender)) OR (:gender = 'OTHERS' AND UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE')))
            AND (:isChild = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15))
            AND (:isSeniorCitizen = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) >= 60))
            UNION
            SELECT b.beneficiaryId FROM TB_DIAGNOSTICS td
            INNER JOIN beneficiary b ON b.beneficiaryId = td.benId
            WHERE td.isLiquidCultureConducted = 1
            AND (b.loc_village_id IN (:assignedVillageIds) AND (:villageName = '' OR b.loc_village_name = :villageName OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || ' (%' ESCAPE CHAR(92) OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || '(%' ESCAPE CHAR(92)))
            AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN td.visitDate >= 100000000000 THEN td.visitDate WHEN td.visitDate > 0 THEN td.visitDate * 1000 WHEN IFNULL(td.serverUpdatedDate, 0) >= 100000000000 THEN td.serverUpdatedDate WHEN IFNULL(td.serverUpdatedDate, 0) > 0 THEN td.serverUpdatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime)
            AND (:endTime = :endTime)
            AND IFNULL(b.isDeath, 0) = 0 AND (:gender = '' OR (:gender != 'OTHERS' AND UPPER(COALESCE(b.gender, '')) = UPPER(:gender)) OR (:gender = 'OTHERS' AND UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE')))
            AND (:isChild = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15))
            AND (:isSeniorCitizen = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) >= 60))
        )
    """)
    fun getDashboardLiquidCultureCount(assignedVillageIds: List<Int>, villageName: String, startTime: Long, endTime: Long, gender: String, isChild: Int,isSeniorCitizen: Int): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM (
            SELECT b.beneficiaryId FROM beneficiary b
            WHERE b.temperature IS NOT NULL AND b.temperature >= 100.0
            AND (b.loc_village_id IN (:assignedVillageIds) AND (:villageName = '' OR b.loc_village_name = :villageName OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || ' (%' ESCAPE CHAR(92) OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || '(%' ESCAPE CHAR(92)))
            AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN IFNULL(b.updatedDate, 0) >= 100000000000 THEN b.updatedDate WHEN IFNULL(b.updatedDate, 0) > 0 THEN b.updatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime)
            AND (:endTime = :endTime)
            AND IFNULL(b.isDeath, 0) = 0 AND (:gender = '' OR (:gender != 'OTHERS' AND UPPER(COALESCE(b.gender, '')) = UPPER(:gender)) OR (:gender = 'OTHERS' AND UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE')))
            AND (:isChild = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15))
            AND (:isSeniorCitizen = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) >= 60))
            UNION
            SELECT b.beneficiaryId FROM BEN_VITALS v
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
            AND IFNULL(b.isDeath, 0) = 0 AND (:gender = '' OR (:gender != 'OTHERS' AND UPPER(COALESCE(b.gender, '')) = UPPER(:gender)) OR (:gender = 'OTHERS' AND UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE')))
            AND (:isChild = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15))
            AND (:isSeniorCitizen = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) >= 60))
        )
    """)
    fun getDashboardHwcReferralCount(assignedVillageIds: List<Int>, villageName: String, startTime: Long, endTime: Long, gender: String, isChild: Int,isSeniorCitizen: Int): Flow<Int>



    // Dashboard query - Unscreened: beneficiary has no TB_SCREENING record at all
    @Query("""
        SELECT COUNT(*) FROM beneficiary b
        WHERE b.beneficiaryId NOT IN (SELECT benId FROM TB_SCREENING)
        AND (b.loc_village_id IN (:assignedVillageIds) AND (:villageName = '' OR b.loc_village_name = :villageName OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || ' (%' ESCAPE CHAR(92) OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || '(%' ESCAPE CHAR(92)))
        AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN IFNULL(b.updatedDate, 0) >= 100000000000 THEN b.updatedDate WHEN IFNULL(b.updatedDate, 0) > 0 THEN b.updatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime)
        AND (:endTime = :endTime)
        AND IFNULL(b.isDeath, 0) = 0 AND (:gender = '' OR (:gender != 'OTHERS' AND UPPER(COALESCE(b.gender, '')) = UPPER(:gender)) OR (:gender = 'OTHERS' AND UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE')))
        AND (:isChild = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15))
    """)
    fun getDashboardUnscreenedCount(
        assignedVillageIds: List<Int>,
        villageName: String,
        startTime: Long,
        endTime: Long,
        gender: String,
        isChild: Int
    ): Flow<Int>
}
