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

@Dao
interface TBDao {

    @Query("SELECT * FROM TB_SCREENING WHERE benId =:benId limit 1")
    suspend fun getTbScreening(benId: Long): TBScreeningCache?

    @Query("SELECT * FROM TB_SCREENING WHERE benId =:benId and (visitDate = :visitDate or visitDate = :visitDateGMT) limit 1")
    suspend fun getTbScreening(benId: Long, visitDate: Long, visitDateGMT: Long): TBScreeningCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTbScreening(tbScreeningCache: TBScreeningCache)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTbScreening(tbScreeningCaches: List<TBScreeningCache>)

    /** Existing rows for a whole pull chunk in one statement instead of one per record. */
    @Query("SELECT * FROM TB_SCREENING WHERE benId IN (:benIds)")
    suspend fun getTbScreeningByBenIds(benIds: List<Long>): List<TBScreeningCache>

    @Query("SELECT * FROM GENERAL_OPD WHERE benId =:benId limit 1")
    suspend fun getGeneralOpd(benId: Long): GeneralOpdCache?

    @Query("SELECT benId FROM TB_SCREENING")
    fun getAllTbScreeningBenIds(): Flow<List<Long>>

    /** id + syncState in one pass; callers split it instead of running three queries. */
    @Query("SELECT benId, syncState FROM TB_SCREENING")
    fun getTbScreeningBenIdSyncStates(): Flow<List<org.piramalswasthya.stoptb.model.BenIdSyncState>>

    /** id + syncState in one pass; callers split it instead of running three queries. */
    @Query("SELECT benId, syncState FROM GENERAL_OPD")
    fun getGeneralOpdBenIdSyncStates(): Flow<List<org.piramalswasthya.stoptb.model.BenIdSyncState>>

    @Query("SELECT benId FROM TB_SCREENING WHERE syncState = :syncState")
    fun getTbScreeningBenIdsBySyncState(syncState: SyncState): Flow<List<Long>>

    @Query("SELECT benId FROM GENERAL_OPD")
    fun getAllGeneralOpdBenIds(): Flow<List<Long>>

    @Query("SELECT benId FROM GENERAL_OPD WHERE syncState = :syncState")
    fun getGeneralOpdBenIdsBySyncState(syncState: SyncState): Flow<List<Long>>

    @Query("SELECT benId FROM TB_SUSPECTED")
    fun getAllTbSuspectedBenIds(): Flow<List<Long>>

    @Query("SELECT benId FROM TB_DIAGNOSTICS")
    fun getAllTbDiagnosticsBenIds(): Flow<List<Long>>

    /**
     * The card-rendering subset of TB_DIAGNOSTICS - see [TbDiagnosticsCardFields] for why the
     * list screens must not observe the whole 46-column entity for every beneficiary.
     */
    @Query(
        "SELECT benId, isReferredForDigitalChestXray, reasonForDenialChestXray, " +
                "reasonForDenialChestXrayOther, reasonNotConductedChestXray, " +
                "reasonNotConductedChestXrayOther, chestXRayResult, isReferredForSputum, " +
                "reasonForDenialSputum, reasonForDenialSputumOther, reasonNotConductedNaat, " +
                "reasonNotConductedNaatOther, naatResult, reasonNotConductedRif, " +
                "reasonNotConductedRifOther, trueNatRifResult, liquidCultureResult, " +
                "xrayOrderId, xrayOrderStatus, trueNatOrderId, trueNatOrderStatus, " +
                "rifOrderId, rifOrderStatus, errorMsgXray, errorMsgTrueNat, errorMsgRif, " +
                "xrayManualResultPendingSync, trueNatManualResultPendingSync, " +
                "rifManualResultPendingSync FROM TB_DIAGNOSTICS"
    )
    fun getAllTbDiagnosticsCardFields(): Flow<List<TbDiagnosticsCardFields>>

    /**
     * The eleven values the beneficiary cards derive "Presumptive / Asymptomatic" from. Kept
     * narrow deliberately: `SELECT *` here loaded 50 columns per beneficiary, four of them
     * TypeConverter-parsed lists, to render one label. See [TbScreeningSymptoms].
     */
    @Query(
        "SELECT benId, coughMoreThan2Weeks, bloodInSputum, feverMoreThan2Weeks, riseOfFever, " +
                "lossOfAppetite, lossOfWeight, nightSweats, historyOfTb, takingAntiTBDrugs, " +
                "familySufferingFromTB, asymptomatic FROM TB_SCREENING"
    )
    fun getAllTbScreeningSymptoms(): Flow<List<TbScreeningSymptoms>>

    @Query("SELECT * FROM TB_DIAGNOSTICS")
    suspend fun getDiagnosticsList(): List<TBDiagnosticsCache>

    /**
     * The beneficiaries in [benIds] that already hold a usable order id for one test type.
     *
     * The order-id backfill in [TBRepo.fetchBeneficiariesByStatus] used to decide this one
     * beneficiary at a time - a `getBenByRegId` plus a whole-row `getTbDiagnosticsByBenId` per
     * entry, over the entire in-flight backlog - which is thousands of statements queued on the
     * single Room executor before the first one is even needed. Asking SQLite which ids are
     * already present answers the same question in one statement per chunk, and reads one
     * column instead of forty-six.
     *
     * "Usable" means the same thing the backfill means by it: non-null, non-blank, not the
     * "N/A" placeholder the backend sends when an order exists but carries no external id.
     */
    @Query(
        "SELECT benId FROM TB_DIAGNOSTICS WHERE benId IN (:benIds) " +
                "AND xrayOrderId IS NOT NULL AND TRIM(xrayOrderId) <> '' " +
                "AND UPPER(TRIM(xrayOrderId)) <> 'N/A'"
    )
    suspend fun getBenIdsWithXrayOrderId(benIds: List<Long>): List<Long>

    @Query(
        "SELECT benId FROM TB_DIAGNOSTICS WHERE benId IN (:benIds) " +
                "AND trueNatOrderId IS NOT NULL AND TRIM(trueNatOrderId) <> '' " +
                "AND UPPER(TRIM(trueNatOrderId)) <> 'N/A'"
    )
    suspend fun getBenIdsWithTrueNatOrderId(benIds: List<Long>): List<Long>

    @Query(
        "SELECT benId FROM TB_DIAGNOSTICS WHERE benId IN (:benIds) " +
                "AND rifOrderId IS NOT NULL AND TRIM(rifOrderId) <> '' " +
                "AND UPPER(TRIM(rifOrderId)) <> 'N/A'"
    )
    suspend fun getBenIdsWithRifOrderId(benIds: List<Long>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGeneralOpd(generalOpdCache: GeneralOpdCache): Long

    @Update
    suspend fun updateGeneralOpd(generalOpdCache: GeneralOpdCache)

    @Query("SELECT * FROM GENERAL_OPD WHERE id = :opdId")
    suspend fun getGeneralOpdById(opdId: Int): GeneralOpdCache?

    @Query("SELECT COUNT(*) FROM GENERAL_OPD WHERE submissionId = :submissionId")
    suspend fun countGeneralOpdSubmissionId(submissionId: String): Int

    @Query("UPDATE GENERAL_OPD SET submissionId = :submissionId WHERE id = :opdId AND submissionId IS NULL")
    suspend fun assignGeneralOpdSubmissionId(opdId: Int, submissionId: String)

    // REPLACE deletes the parent row and cascades to its saved prescriptions.
    @Transaction
    suspend fun saveGeneralOpd(generalOpdCache: GeneralOpdCache): Long {
        val insertedId = insertGeneralOpd(generalOpdCache)
        if (insertedId != -1L) return insertedId
        val existing = getGeneralOpdById(generalOpdCache.id)
        updateGeneralOpd(generalOpdCache.copy(
            submissionId = existing?.submissionId ?: generalOpdCache.submissionId,
            chiefComplaintIds = generalOpdCache.chiefComplaintIds ?:
                existing?.chiefComplaintIds?.takeIf { existing?.chiefComplaints == generalOpdCache.chiefComplaints }
        ))
        return generalOpdCache.id.toLong()
    }

    @Transaction
    suspend fun ensureGeneralOpdSubmissionId(opdId: Int): String {
        getGeneralOpdById(opdId)?.submissionId?.let { return it }
        var candidate: String
        do { candidate = java.util.UUID.randomUUID().toString() }
        while (countGeneralOpdSubmissionId(candidate) > 0)
        assignGeneralOpdSubmissionId(opdId, candidate)
        return requireNotNull(getGeneralOpdById(opdId)?.submissionId)
    }

    @Query("SELECT * FROM GENERAL_OPD_PRESCRIPTION WHERE opdId = :opdId ORDER BY position")
    suspend fun getGeneralOpdPrescriptions(opdId: Int): List<GeneralOpdPrescription>

    @Query("DELETE FROM GENERAL_OPD_PRESCRIPTION WHERE opdId = :opdId")
    suspend fun deleteGeneralOpdPrescriptions(opdId: Int)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGeneralOpdPrescriptions(rows: List<GeneralOpdPrescription>)

    @Query("SELECT * FROM TB_DIAGNOSTICS WHERE benId =:benId limit 1")
    suspend fun getTbDiagnostics(benId: Long): TBDiagnosticsCache?

    @Query("SELECT * FROM TB_DIAGNOSTICS WHERE benId =:benId and (visitDate = :visitDate or visitDate = :visitDateGMT) limit 1")
    suspend fun getTbDiagnostics(benId: Long, visitDate: Long, visitDateGMT: Long): TBDiagnosticsCache?

    /** Get latest TB_DIAGNOSTICS record for ben — used to retrieve existing id before save */
    @Query("SELECT * FROM TB_DIAGNOSTICS WHERE benId = :benId ORDER BY id DESC LIMIT 1")
    suspend fun getTbDiagnosticsByBenId(benId: Long): TBDiagnosticsCache?

    // @Upsert, not @Insert(REPLACE): REPLACE is DELETE + INSERT in SQLite, so every
    // save rewrote the whole row twice and churned its rowid. Both entities have an
    // autoGenerate primary key and only a non-unique index on benId, so @Upsert is
    // equivalent - id 0 inserts, an existing id updates - as a single statement.
    @Upsert
    suspend fun saveTbDiagnostics(tbDiagnosticsCache: TBDiagnosticsCache)

    @Query("SELECT * FROM TB_SUSPECTED WHERE benId =:benId ORDER BY id DESC limit 1")
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

    // @Upsert, not @Insert(REPLACE): REPLACE is DELETE + INSERT in SQLite, so every
    // save rewrote the whole row twice and churned its rowid. Both entities have an
    // autoGenerate primary key and only a non-unique index on benId, so @Upsert is
    // equivalent - id 0 inserts, an existing id updates - as a single statement.
    @Upsert
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
        WHERE (b.loc_village_id IN (:assignedVillageIds) AND (:villageName = '' OR b.loc_village_name = :villageName OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || ' (%' ESCAPE CHAR(92) OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || '(%' ESCAPE CHAR(92)))
        AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN ts.visitDate >= 100000000000 THEN ts.visitDate WHEN ts.visitDate > 0 THEN ts.visitDate * 1000 WHEN IFNULL(ts.serverUpdatedDate, 0) >= 100000000000 THEN ts.serverUpdatedDate WHEN IFNULL(ts.serverUpdatedDate, 0) > 0 THEN ts.serverUpdatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime)
        AND (:endTime = :endTime)
        AND IFNULL(b.isDeath, 0) = 0 AND (:gender = '' OR (:gender != 'OTHERS' AND UPPER(COALESCE(b.gender, '')) = UPPER(:gender)) OR (:gender = 'OTHERS' AND UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE')))
        AND (:isChild = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15))
        AND (:isSeniorCitizen  = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) >= 60))
    """)
    fun getDashboardTbScreeningCount(assignedVillageIds: List<Int>, villageName: String, startTime: Long, endTime: Long, gender: String, isChild: Int,    isSeniorCitizen: Int
    ): Flow<Int>


    // Dashboard query - Presumptive TB: any starred verbal screening symptom = Yes OR Positive Chest X-ray
    @Query("""
    SELECT COUNT(DISTINCT ts.benId) FROM TB_SCREENING ts
    INNER JOIN beneficiary b ON b.beneficiaryId = ts.benId
    LEFT JOIN TB_SUSPECTED tsu ON b.beneficiaryId = tsu.benId
    LEFT JOIN TB_DIAGNOSTICS td ON b.beneficiaryId = td.benId
    WHERE (
        ts.coughMoreThan2Weeks = 1
        OR ts.bloodInSputum = 1
        OR ts.feverMoreThan2Weeks = 1
        OR ts.lossOfWeight = 1
        OR ts.nightSweats = 1
        OR ts.riseOfFever = 1
        OR ts.lossOfAppetite = 1
        OR ts.familySufferingFromTB = 1
        OR ts.contactWithTBPatient = 1
        OR ts.historyOfTBInLastFiveYrs = 1
        OR ts.historyOfTb = 1
        OR ts.takingAntiTBDrugs = 1
        OR UPPER(IFNULL(tsu.chestXRayResult, '')) IN ('POSITIVE', 'TB PRESUMPTIVE')
        OR UPPER(IFNULL(td.chestXRayResult, '')) IN ('POSITIVE', 'TB PRESUMPTIVE')
    )
    AND IFNULL(tsu.isConfirmed, 0) = 0
    AND IFNULL(tsu.isTBConfirmed, 0) = 0
    AND IFNULL(td.isTBConfirmed, 0) = 0
    AND UPPER(IFNULL(td.naatResult, '')) NOT IN ('POSITIVE', 'MTB DETECTED', 'TB POSITIVE')
    AND UPPER(IFNULL(td.liquidCultureResult, '')) != 'POSITIVE'
    AND NOT EXISTS (SELECT 1 FROM TB_CONFIRMED_TREATMENT tc WHERE tc.benId = b.beneficiaryId)
    AND (b.loc_village_id IN (:assignedVillageIds) AND (:villageName = '' OR b.loc_village_name = :villageName OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || ' (%' ESCAPE CHAR(92) OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || '(%' ESCAPE CHAR(92)))
    AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN ts.visitDate >= 100000000000 THEN ts.visitDate WHEN ts.visitDate > 0 THEN ts.visitDate * 1000 WHEN IFNULL(ts.serverUpdatedDate, 0) >= 100000000000 THEN ts.serverUpdatedDate WHEN IFNULL(ts.serverUpdatedDate, 0) > 0 THEN ts.serverUpdatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime)
    AND (:endTime = :endTime)
    AND IFNULL(b.isDeath, 0) = 0 AND (:gender = '' OR (:gender != 'OTHERS' AND UPPER(COALESCE(b.gender, '')) = UPPER(:gender)) OR (:gender = 'OTHERS' AND UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE')))
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

    // Dashboard queries - TB Confirmed count by gender with time + village filter
    @Query("""
        SELECT COUNT(*) FROM (
            SELECT b.beneficiaryId FROM TB_SUSPECTED ts
            INNER JOIN beneficiary b ON b.beneficiaryId = ts.benId
            WHERE ts.isConfirmed = 1
            AND (b.loc_village_id IN (:assignedVillageIds) AND (:villageName = '' OR b.loc_village_name = :villageName OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || ' (%' ESCAPE CHAR(92) OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || '(%' ESCAPE CHAR(92)))
            AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN ts.visitDate >= 100000000000 THEN ts.visitDate WHEN ts.visitDate > 0 THEN ts.visitDate * 1000 WHEN IFNULL(ts.serverUpdatedDate, 0) >= 100000000000 THEN ts.serverUpdatedDate WHEN IFNULL(ts.serverUpdatedDate, 0) > 0 THEN ts.serverUpdatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime)
            AND (:endTime = :endTime)
            AND IFNULL(b.isDeath, 0) = 0 AND (:gender = '' OR (:gender != 'OTHERS' AND UPPER(COALESCE(b.gender, '')) = UPPER(:gender)) OR (:gender = 'OTHERS' AND UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE')))
            AND (:isChild = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15))
            AND (:isSeniorCitizen = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) >= 60))
            UNION
            SELECT b.beneficiaryId FROM TB_DIAGNOSTICS td
            INNER JOIN beneficiary b ON b.beneficiaryId = td.benId
            WHERE td.isConfirmed = 1
            AND (b.loc_village_id IN (:assignedVillageIds) AND (:villageName = '' OR b.loc_village_name = :villageName OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || ' (%' ESCAPE CHAR(92) OR b.loc_village_name LIKE REPLACE(REPLACE(REPLACE(:villageName, CHAR(92), CHAR(92) || CHAR(92)), '%', CHAR(92) || '%'), '_', CHAR(92) || '_') || '(%' ESCAPE CHAR(92)))
            AND (:startTime = 0 OR :endTime = 0 OR (CASE WHEN td.visitDate >= 100000000000 THEN td.visitDate WHEN td.visitDate > 0 THEN td.visitDate * 1000 WHEN IFNULL(td.serverUpdatedDate, 0) >= 100000000000 THEN td.serverUpdatedDate WHEN IFNULL(td.serverUpdatedDate, 0) > 0 THEN td.serverUpdatedDate * 1000 ELSE 0 END) BETWEEN :startTime AND :endTime)
            AND (:endTime = :endTime)
            AND IFNULL(b.isDeath, 0) = 0 AND (:gender = '' OR (:gender != 'OTHERS' AND UPPER(COALESCE(b.gender, '')) = UPPER(:gender)) OR (:gender = 'OTHERS' AND UPPER(COALESCE(b.gender, '')) NOT IN ('MALE', 'FEMALE')))
            AND (:isChild = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 15))
            AND (:isSeniorCitizen = 0 OR (CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) >= 60))
        )
    """)
    fun getDashboardTbConfirmedCount(assignedVillageIds: List<Int>, villageName: String, startTime: Long, endTime: Long, gender: String, isChild: Int, isSeniorCitizen: Int): Flow<Int>

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
               OR (v.rbs IS NOT NULL AND v.rbs >= 100))
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

