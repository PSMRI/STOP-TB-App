package org.piramalswasthya.stoptb.database.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.model.GeneralOpdCache
import org.piramalswasthya.stoptb.model.TBConfirmedTreatmentCache
import org.piramalswasthya.stoptb.model.TBDiagnosticsCache
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

    @Query("SELECT * FROM GENERAL_OPD WHERE benId =:benId limit 1")
    suspend fun getGeneralOpd(benId: Long): GeneralOpdCache?

    @Query("SELECT benId FROM TB_SCREENING")
    fun getAllTbScreeningBenIds(): Flow<List<Long>>

    @Query("SELECT benId FROM GENERAL_OPD")
    fun getAllGeneralOpdBenIds(): Flow<List<Long>>

    @Query("SELECT benId FROM TB_SUSPECTED")
    fun getAllTbSuspectedBenIds(): Flow<List<Long>>

    @Query("SELECT benId FROM TB_DIAGNOSTICS")
    fun getAllTbDiagnosticsBenIds(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveGeneralOpd(generalOpdCache: GeneralOpdCache)

    @Query("SELECT * FROM TB_DIAGNOSTICS WHERE benId =:benId limit 1")
    suspend fun getTbDiagnostics(benId: Long): TBDiagnosticsCache?

    @Query("SELECT * FROM TB_DIAGNOSTICS WHERE benId =:benId and (visitDate = :visitDate or visitDate = :visitDateGMT) limit 1")
    suspend fun getTbDiagnostics(benId: Long, visitDate: Long, visitDateGMT: Long): TBDiagnosticsCache?

    /** Get latest TB_DIAGNOSTICS record for ben — used to retrieve existing id before save */
    @Query("SELECT * FROM TB_DIAGNOSTICS WHERE benId = :benId ORDER BY id DESC LIMIT 1")
    suspend fun getTbDiagnosticsByBenId(benId: Long): TBDiagnosticsCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTbDiagnostics(tbDiagnosticsCache: TBDiagnosticsCache)

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

    @Query("SELECT * FROM GENERAL_OPD WHERE syncState = :syncState")
    suspend fun getGeneralOpd(syncState: SyncState): List<GeneralOpdCache>

    @Query("SELECT * FROM TB_DIAGNOSTICS WHERE syncState = :syncState")
    suspend fun getTbDiagnostics(syncState: SyncState): List<TBDiagnosticsCache>

    @Query("SELECT * FROM TB_SUSPECTED WHERE  syncState = :syncState")
    suspend fun getTbSuspected(syncState: SyncState): List<TBSuspectedCache>

    @Query("SELECT * FROM TB_CONFIRMED_TREATMENT WHERE  syncState = :syncState")
    suspend fun getTbConfirmed(syncState: SyncState): List<TBConfirmedTreatmentCache>

    @Query("SELECT * FROM TB_CONFIRMED_TREATMENT WHERE benId = :benId ORDER BY followUpDate DESC")
    suspend fun getAllFollowUpsForBeneficiary(benId: Long): List<TBConfirmedTreatmentCache>

    @Query("UPDATE TB_SCREENING SET syncState = 0 WHERE syncState = 1")
    suspend fun resetScreeningSyncingToUnsynced()

    @Query("UPDATE GENERAL_OPD SET syncState = 0 WHERE syncState = 1")
    suspend fun resetGeneralOpdSyncingToUnsynced()

    @Query("UPDATE TB_DIAGNOSTICS SET syncState = 0 WHERE syncState = 1")
    suspend fun resetDiagnosticsSyncingToUnsynced()

    @Query("UPDATE TB_SUSPECTED SET syncState = 0 WHERE syncState = 1")
    suspend fun resetSuspectedSyncingToUnsynced()

    @Query("UPDATE TB_CONFIRMED_TREATMENT SET syncState = 0 WHERE syncState = 1")
    suspend fun resetConfirmedSyncingToUnsynced()

    // Dashboard queries - TB Screening count by gender with time + village filter
    @Query("""
        SELECT COUNT(*) FROM TB_SCREENING ts
        INNER JOIN beneficiary b ON b.beneficiaryId = ts.benId
        WHERE ((:villageId != 0 AND b.loc_village_id = :villageId) OR (:villageId = 0 AND b.loc_village_id IN (:assignedVillageIds)))
        AND (:startTime = 0 OR ts.visitDate >= :startTime)
        AND (:endTime = 0 OR ts.visitDate <= :endTime)
        AND (:gender = '' OR (:gender != 'OTHERS' AND UPPER(COALESCE(b.gender, '')) = UPPER(:gender)) OR (:gender = 'OTHERS' AND UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE')))
        AND (:isChild = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15))
        AND (:isSeniorCitizen  = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) >= 60))
    """)
    fun getDashboardTbScreeningCount(villageId: Int, assignedVillageIds: List<Int>, startTime: Long, endTime: Long, gender: String, isChild: Int,    isSeniorCitizen: Int
    ): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM TB_SCREENING ts
        INNER JOIN beneficiary b ON b.beneficiaryId = ts.benId
        WHERE ((:villageId != 0 AND b.loc_village_id = :villageId) OR (:villageId = 0 AND b.loc_village_id IN (:assignedVillageIds)))
        AND (:startTime = 0 OR ts.visitDate >= :startTime)
        AND (:endTime = 0 OR ts.visitDate <= :endTime)
        AND (:gender = '' OR (:gender != 'OTHERS' AND UPPER(COALESCE(b.gender, '')) = UPPER(:gender)) OR (:gender = 'OTHERS' AND UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE')))
        AND (:isChild = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15))
        AND ((:positive = 1 AND ts.historyOfTb = 1) OR (:positive = 0 AND ts.historyOfTb = 0))
    """)
    fun getDashboardPastHistoryTbCount(
        villageId: Int,
        assignedVillageIds: List<Int>,
        startTime: Long,
        endTime: Long,
        gender: String,
        isChild: Int,
        positive: Int,
    ): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM TB_SCREENING ts
        INNER JOIN beneficiary b ON b.beneficiaryId = ts.benId
        WHERE ((:villageId != 0 AND b.loc_village_id = :villageId) OR (:villageId = 0 AND b.loc_village_id IN (:assignedVillageIds)))
        AND (:startTime = 0 OR ts.visitDate >= :startTime)
        AND (:endTime = 0 OR ts.visitDate <= :endTime)
        AND (:gender = '' OR (:gender != 'OTHERS' AND UPPER(COALESCE(b.gender, '')) = UPPER(:gender)) OR (:gender = 'OTHERS' AND UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE')))
        AND (:isChild = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15))
        AND ((:positive = 1 AND ts.takingAntiTBDrugs = 1) OR (:positive = 0 AND ts.takingAntiTBDrugs = 0))
    """)
    fun getDashboardAntiTbDrugsCount(
        villageId: Int,
        assignedVillageIds: List<Int>,
        startTime: Long,
        endTime: Long,
        gender: String,
        isChild: Int,
        positive: Int,
    ): Flow<Int>

    // Dashboard queries - TB Suspected count by gender with time + village filter
    @Query("""
        SELECT COUNT(*) FROM (
            SELECT b.beneficiaryId FROM TB_SUSPECTED ts
            INNER JOIN beneficiary b ON b.beneficiaryId = ts.benId
            WHERE ((:villageId != 0 AND b.loc_village_id = :villageId) OR (:villageId = 0 AND b.loc_village_id IN (:assignedVillageIds)))
            AND (:startTime = 0 OR ts.visitDate >= :startTime)
            AND (:endTime = 0 OR ts.visitDate <= :endTime)
            AND (:gender = '' OR (:gender != 'OTHERS' AND UPPER(COALESCE(b.gender, '')) = UPPER(:gender)) OR (:gender = 'OTHERS' AND UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE')))
            AND (:isChild = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15))
            AND (:isSeniorCitizen = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) >= 60))
            UNION
            SELECT b.beneficiaryId FROM TB_DIAGNOSTICS td
            INNER JOIN beneficiary b ON b.beneficiaryId = td.benId
            WHERE ((:villageId != 0 AND b.loc_village_id = :villageId) OR (:villageId = 0 AND b.loc_village_id IN (:assignedVillageIds)))
            AND (:startTime = 0 OR td.visitDate >= :startTime)
            AND (:endTime = 0 OR td.visitDate <= :endTime)
            AND (:gender = '' OR (:gender != 'OTHERS' AND UPPER(COALESCE(b.gender, '')) = UPPER(:gender)) OR (:gender = 'OTHERS' AND UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE')))
            AND (:isChild = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15))
            AND (:isSeniorCitizen = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) >= 60))
        )
    """)
    fun getDashboardTbSuspectedCount(villageId: Int, assignedVillageIds: List<Int>, startTime: Long, endTime: Long, gender: String, isChild: Int,isSeniorCitizen: Int): Flow<Int>

    // Dashboard queries - TB Confirmed count by gender with time + village filter
    @Query("""
        SELECT COUNT(*) FROM (
            SELECT b.beneficiaryId FROM TB_SUSPECTED ts
            INNER JOIN beneficiary b ON b.beneficiaryId = ts.benId
            WHERE ts.isConfirmed = 1
            AND ((:villageId != 0 AND b.loc_village_id = :villageId) OR (:villageId = 0 AND b.loc_village_id IN (:assignedVillageIds)))
            AND (:startTime = 0 OR ts.visitDate >= :startTime)
            AND (:endTime = 0 OR ts.visitDate <= :endTime)
            AND (:gender = '' OR (:gender != 'OTHERS' AND UPPER(COALESCE(b.gender, '')) = UPPER(:gender)) OR (:gender = 'OTHERS' AND UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE')))
            AND (:isChild = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15))
            AND (:isSeniorCitizen = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) >= 60))
            UNION
            SELECT b.beneficiaryId FROM TB_DIAGNOSTICS td
            INNER JOIN beneficiary b ON b.beneficiaryId = td.benId
            WHERE td.isConfirmed = 1
            AND ((:villageId != 0 AND b.loc_village_id = :villageId) OR (:villageId = 0 AND b.loc_village_id IN (:assignedVillageIds)))
            AND (:startTime = 0 OR td.visitDate >= :startTime)
            AND (:endTime = 0 OR td.visitDate <= :endTime)
            AND (:gender = '' OR (:gender != 'OTHERS' AND UPPER(COALESCE(b.gender, '')) = UPPER(:gender)) OR (:gender = 'OTHERS' AND UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE')))
            AND (:isChild = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15))
            AND (:isSeniorCitizen = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) >= 60))
        )
    """)
    fun getDashboardTbConfirmedCount(villageId: Int, assignedVillageIds: List<Int>, startTime: Long, endTime: Long, gender: String, isChild: Int, isSeniorCitizen: Int): Flow<Int>

    // NIKSHAY IDs count with time + village filter
    @Query("""
        SELECT COUNT(*) FROM TB_SUSPECTED ts
        INNER JOIN beneficiary b ON b.beneficiaryId = ts.benId
        WHERE ts.nikshayId IS NOT NULL AND ts.nikshayId != ''
        AND ((:villageId != 0 AND b.loc_village_id = :villageId) OR (:villageId = 0 AND b.loc_village_id IN (:assignedVillageIds)))
        AND (:startTime = 0 OR ts.visitDate >= :startTime)
        AND (:endTime = 0 OR ts.visitDate <= :endTime)
    """)
    fun getDashboardNikshayCount(villageId: Int, assignedVillageIds: List<Int>, startTime: Long, endTime: Long): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM (
            SELECT b.beneficiaryId FROM TB_SUSPECTED ts
            INNER JOIN beneficiary b ON b.beneficiaryId = ts.benId
            WHERE ts.isChestXRayDone IS NOT NULL
            AND ((:villageId != 0 AND b.loc_village_id = :villageId) OR (:villageId = 0 AND b.loc_village_id IN (:assignedVillageIds)))
            AND (:startTime = 0 OR ts.visitDate >= :startTime)
            AND (:endTime = 0 OR ts.visitDate <= :endTime)
            AND (:gender = '' OR (:gender != 'OTHERS' AND UPPER(COALESCE(b.gender, '')) = UPPER(:gender)) OR (:gender = 'OTHERS' AND UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE')))
            AND (:isChild = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15))
            AND (:isSeniorCitizen = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) >= 60))
            UNION
            SELECT b.beneficiaryId FROM TB_DIAGNOSTICS td
            INNER JOIN beneficiary b ON b.beneficiaryId = td.benId
            WHERE td.isChestXRayDone IS NOT NULL
            AND ((:villageId != 0 AND b.loc_village_id = :villageId) OR (:villageId = 0 AND b.loc_village_id IN (:assignedVillageIds)))
            AND (:startTime = 0 OR td.visitDate >= :startTime)
            AND (:endTime = 0 OR td.visitDate <= :endTime)
            AND (:gender = '' OR (:gender != 'OTHERS' AND UPPER(COALESCE(b.gender, '')) = UPPER(:gender)) OR (:gender = 'OTHERS' AND UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE')))
            AND (:isChild = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15))
            AND (:isSeniorCitizen = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) >= 60))
        )
    """)
    fun getDashboardDigitalChestXRayCount(villageId: Int, assignedVillageIds: List<Int>, startTime: Long, endTime: Long, gender: String, isChild: Int, isSeniorCitizen: Int): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM (
            SELECT b.beneficiaryId FROM TB_SUSPECTED ts
            INNER JOIN beneficiary b ON b.beneficiaryId = ts.benId
            WHERE ts.isSputumCollected IS NOT NULL
            AND ((:villageId != 0 AND b.loc_village_id = :villageId) OR (:villageId = 0 AND b.loc_village_id IN (:assignedVillageIds)))
            AND (:startTime = 0 OR ts.visitDate >= :startTime)
            AND (:endTime = 0 OR ts.visitDate <= :endTime)
            AND (:gender = '' OR (:gender != 'OTHERS' AND UPPER(COALESCE(b.gender, '')) = UPPER(:gender)) OR (:gender = 'OTHERS' AND UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE')))
            AND (:isChild = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15))
            AND (:isSeniorCitizen = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) >= 60))
            UNION
            SELECT b.beneficiaryId FROM TB_DIAGNOSTICS td
            INNER JOIN beneficiary b ON b.beneficiaryId = td.benId
            WHERE td.isSputumCollected IS NOT NULL
            AND ((:villageId != 0 AND b.loc_village_id = :villageId) OR (:villageId = 0 AND b.loc_village_id IN (:assignedVillageIds)))
            AND (:startTime = 0 OR td.visitDate >= :startTime)
            AND (:endTime = 0 OR td.visitDate <= :endTime)
            AND (:gender = '' OR (:gender != 'OTHERS' AND UPPER(COALESCE(b.gender, '')) = UPPER(:gender)) OR (:gender = 'OTHERS' AND UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE')))
            AND (:isChild = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15))
            AND (:isSeniorCitizen = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) >= 60))
        )
    """)
    fun getDashboardSputumCollectionCount(villageId: Int, assignedVillageIds: List<Int>, startTime: Long, endTime: Long, gender: String, isChild: Int, isSeniorCitizen: Int): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM (
            SELECT b.beneficiaryId FROM TB_SUSPECTED ts
            INNER JOIN beneficiary b ON b.beneficiaryId = ts.benId
            WHERE ts.isNaatConducted IS NOT NULL
            AND ((:villageId != 0 AND b.loc_village_id = :villageId) OR (:villageId = 0 AND b.loc_village_id IN (:assignedVillageIds)))
            AND (:startTime = 0 OR ts.visitDate >= :startTime)
            AND (:endTime = 0 OR ts.visitDate <= :endTime)
            AND (:gender = '' OR (:gender != 'OTHERS' AND UPPER(COALESCE(b.gender, '')) = UPPER(:gender)) OR (:gender = 'OTHERS' AND UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE')))
            AND (:isChild = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15))
            AND (:isSeniorCitizen = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) >= 60))
            UNION
            SELECT b.beneficiaryId FROM TB_DIAGNOSTICS td
            INNER JOIN beneficiary b ON b.beneficiaryId = td.benId
            WHERE td.isNaatConducted IS NOT NULL
            AND ((:villageId != 0 AND b.loc_village_id = :villageId) OR (:villageId = 0 AND b.loc_village_id IN (:assignedVillageIds)))
            AND (:startTime = 0 OR td.visitDate >= :startTime)
            AND (:endTime = 0 OR td.visitDate <= :endTime)
            AND (:gender = '' OR (:gender != 'OTHERS' AND UPPER(COALESCE(b.gender, '')) = UPPER(:gender)) OR (:gender = 'OTHERS' AND UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE')))
            AND (:isChild = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15))
            AND (:isSeniorCitizen = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) >= 60))
        )
    """)
    fun getDashboardTrueNatCount(villageId: Int, assignedVillageIds: List<Int>, startTime: Long, endTime: Long, gender: String, isChild: Int, isSeniorCitizen: Int): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM (
            SELECT b.beneficiaryId FROM TB_SUSPECTED ts
            INNER JOIN beneficiary b ON b.beneficiaryId = ts.benId
            WHERE ts.isLiquidCultureConducted IS NOT NULL
            AND ((:villageId != 0 AND b.loc_village_id = :villageId) OR (:villageId = 0 AND b.loc_village_id IN (:assignedVillageIds)))
            AND (:startTime = 0 OR ts.visitDate >= :startTime)
            AND (:endTime = 0 OR ts.visitDate <= :endTime)
            AND (:gender = '' OR (:gender != 'OTHERS' AND UPPER(COALESCE(b.gender, '')) = UPPER(:gender)) OR (:gender = 'OTHERS' AND UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE')))
            AND (:isChild = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15))
            AND (:isSeniorCitizen = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) >= 60))
            UNION
            SELECT b.beneficiaryId FROM TB_DIAGNOSTICS td
            INNER JOIN beneficiary b ON b.beneficiaryId = td.benId
            WHERE td.isLiquidCultureConducted IS NOT NULL
            AND ((:villageId != 0 AND b.loc_village_id = :villageId) OR (:villageId = 0 AND b.loc_village_id IN (:assignedVillageIds)))
            AND (:startTime = 0 OR td.visitDate >= :startTime)
            AND (:endTime = 0 OR td.visitDate <= :endTime)
            AND (:gender = '' OR (:gender != 'OTHERS' AND UPPER(COALESCE(b.gender, '')) = UPPER(:gender)) OR (:gender = 'OTHERS' AND UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE')))
            AND (:isChild = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15))
            AND (:isSeniorCitizen = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) >= 60))
        )
    """)
    fun getDashboardLiquidCultureCount(villageId: Int, assignedVillageIds: List<Int>, startTime: Long, endTime: Long, gender: String, isChild: Int,isSeniorCitizen: Int): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM (
            SELECT b.beneficiaryId FROM beneficiary b
            WHERE b.temperature IS NOT NULL AND b.temperature >= 100.0
            AND ((:villageId != 0 AND b.loc_village_id = :villageId) OR (:villageId = 0 AND b.loc_village_id IN (:assignedVillageIds)))
            AND (:startTime = 0 OR b.updatedDate >= :startTime)
            AND (:endTime = 0 OR b.updatedDate <= :endTime)
            AND (:gender = '' OR (:gender != 'OTHERS' AND UPPER(COALESCE(b.gender, '')) = UPPER(:gender)) OR (:gender = 'OTHERS' AND UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE')))
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
               OR (v.rbs IS NOT NULL AND v.rbs >= 100))
            AND ((:villageId != 0 AND b.loc_village_id = :villageId) OR (:villageId = 0 AND b.loc_village_id IN (:assignedVillageIds)))
            AND (:startTime = 0 OR v.capturedAt >= :startTime)
            AND (:endTime = 0 OR v.capturedAt <= :endTime)
            AND (:gender = '' OR (:gender != 'OTHERS' AND UPPER(COALESCE(b.gender, '')) = UPPER(:gender)) OR (:gender = 'OTHERS' AND UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE')))
            AND (:isChild = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15))
            AND (:isSeniorCitizen = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) >= 60))
        )
    """)
    fun getDashboardHwcReferralCount(villageId: Int, assignedVillageIds: List<Int>, startTime: Long, endTime: Long, gender: String, isChild: Int,isSeniorCitizen: Int): Flow<Int>
}
