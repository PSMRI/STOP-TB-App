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
interface TBDao :
    TbDashboardQueries,
    TbBadgeQueries,
    TbDiagnosticOrderQueries,
    TbDashboardBreakdownQueries {

    @Query("SELECT * FROM TB_SCREENING WHERE benId =:benId limit 1")
    suspend fun getTbScreening(benId: Long): TBScreeningCache?

    @Query("SELECT * FROM TB_SCREENING WHERE benId =:benId and (visitDate = :visitDate or visitDate = :visitDateGMT) limit 1")
    suspend fun getTbScreening(benId: Long, visitDate: Long, visitDateGMT: Long): TBScreeningCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTbScreening(tbScreeningCache: TBScreeningCache): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTbScreening(tbScreeningCaches: List<TBScreeningCache>)

    @Query("SELECT * FROM GENERAL_OPD WHERE benId =:benId limit 1")
    suspend fun getGeneralOpd(benId: Long): GeneralOpdCache?

    @Query("SELECT benId FROM TB_SCREENING WHERE syncState = :syncState")
    fun getTbScreeningBenIdsBySyncState(syncState: SyncState): Flow<List<Long>>

    @Query("SELECT benId FROM GENERAL_OPD WHERE syncState = :syncState")
    fun getGeneralOpdBenIdsBySyncState(syncState: SyncState): Flow<List<Long>>

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
    suspend fun upsertTbDiagnostics(tbDiagnosticsCache: TBDiagnosticsCache): Long

    suspend fun saveTbDiagnostics(tbDiagnosticsCache: TBDiagnosticsCache): Long {
        val insertedId = upsertTbDiagnostics(tbDiagnosticsCache)
        return if (tbDiagnosticsCache.id > 0) tbDiagnosticsCache.id.toLong() else insertedId
    }

    @Query("SELECT * FROM TB_SUSPECTED WHERE benId =:benId ORDER BY id DESC limit 1")
    suspend fun getTbSuspected(benId: Long): TBSuspectedCache?

    @Query("SELECT * FROM TB_SUSPECTED WHERE id = :id")
    suspend fun getTbSuspectedById(id: Int): TBSuspectedCache?

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
    suspend fun upsertTbSuspected(tbSuspectedCache: TBSuspectedCache): Long

    suspend fun saveTbSuspected(tbSuspectedCache: TBSuspectedCache): Long {
        val insertedId = upsertTbSuspected(tbSuspectedCache)
        return if (tbSuspectedCache.id > 0) tbSuspectedCache.id.toLong() else insertedId
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTbConfirmed(tbConfirmedCache: TBConfirmedTreatmentCache): Long



    @Query("SELECT * FROM TB_SCREENING WHERE  syncState = :syncState")
    suspend fun getTBScreening(syncState: SyncState): List<TBScreeningCache>

    @Query("SELECT * FROM TB_SCREENING WHERE id = :id")
    suspend fun getTbScreeningById(id: Int): TBScreeningCache?

    @Query("SELECT * FROM TB_CONFIRMED_TREATMENT WHERE id = :id")
    suspend fun getTbConfirmedById(id: Int): TBConfirmedTreatmentCache?

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

    /**
     * True when another living household member is a confirmed TB case.
     * Pass householdId 0 or the person's own id to get false.
     */
    @Query("""
        SELECT EXISTS(
            SELECT 1
            FROM BENEFICIARY other
            LEFT JOIN TB_SUSPECTED ts ON other.beneficiaryId = ts.benId
            LEFT JOIN TB_DIAGNOSTICS td ON other.beneficiaryId = td.benId
            WHERE other.householdId = :householdId
              AND :householdId > 0
              AND other.beneficiaryId != :benId
              AND IFNULL(other.isDeactivate, 0) = 0
              AND IFNULL(other.isDeath, 0) = 0
              AND IFNULL(other.isDraft, 0) = 0
              AND (
                    IFNULL(ts.isTBConfirmed, 0) = 1
                    OR IFNULL(ts.isConfirmed, 0) = 1
                    OR IFNULL(td.isTBConfirmed, 0) = 1
                    OR IFNULL(td.isConfirmed, 0) = 1
                    OR UPPER(IFNULL(td.naatResult, '')) IN ('POSITIVE', 'MTB DETECTED', 'TB POSITIVE')
                    OR UPPER(IFNULL(td.liquidCultureResult, '')) = 'POSITIVE'
                    OR EXISTS (
                        SELECT 1 FROM TB_CONFIRMED_TREATMENT tc
                        WHERE tc.benId = other.beneficiaryId
                    )
              )
        )
    """)
    suspend fun householdHasOtherConfirmedTbCase(householdId: Long, benId: Long): Boolean

    /**
     * Screenings for household members who are contacts of a confirmed TB case.
     * householdId 0 returns every such screening.
     */
    @Query("""
        SELECT t.*
        FROM TB_SCREENING t
        INNER JOIN BENEFICIARY self ON self.beneficiaryId = t.benId
        WHERE IFNULL(self.householdId, 0) > 0
          AND (:householdId = 0 OR self.householdId = :householdId)
          AND IFNULL(self.isDeactivate, 0) = 0
          AND IFNULL(self.isDeath, 0) = 0
          AND IFNULL(self.isDraft, 0) = 0
          AND EXISTS (
                SELECT 1
                FROM BENEFICIARY other
                LEFT JOIN TB_SUSPECTED ts ON other.beneficiaryId = ts.benId
                LEFT JOIN TB_DIAGNOSTICS td ON other.beneficiaryId = td.benId
                WHERE other.householdId = self.householdId
                  AND other.beneficiaryId != self.beneficiaryId
                  AND IFNULL(other.isDeactivate, 0) = 0
                  AND IFNULL(other.isDeath, 0) = 0
                  AND IFNULL(other.isDraft, 0) = 0
                  AND (
                        IFNULL(ts.isTBConfirmed, 0) = 1
                        OR IFNULL(ts.isConfirmed, 0) = 1
                        OR IFNULL(td.isTBConfirmed, 0) = 1
                        OR IFNULL(td.isConfirmed, 0) = 1
                        OR UPPER(IFNULL(td.naatResult, '')) IN ('POSITIVE', 'MTB DETECTED', 'TB POSITIVE')
                        OR UPPER(IFNULL(td.liquidCultureResult, '')) = 'POSITIVE'
                        OR EXISTS (
                            SELECT 1 FROM TB_CONFIRMED_TREATMENT tc
                            WHERE tc.benId = other.beneficiaryId
                        )
                  )
          )
    """)
    suspend fun getScreeningsForKnownTbContacts(householdId: Long): List<TBScreeningCache>
}

