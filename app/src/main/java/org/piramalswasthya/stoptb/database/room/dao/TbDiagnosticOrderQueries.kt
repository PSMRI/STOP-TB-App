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
 * Diagnostic order lookups by order id / external id and order-status updates.
 *
 * Part of [TBDao] (which extends this interface); split out only to keep the DAO
 * readable. Room generates these queries as part of TBDao_Impl - SQL and callers unchanged.
 */
interface TbDiagnosticOrderQueries {

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

    /**
     * Rows with at least one PENDING order. The result poll only acts on these, so it no longer
     * loads every TB_DIAGNOSTICS row each cycle. NOCASE matches the callers'
     * equals(ignoreCase = true) for the ASCII status values.
     */
    @Query(
        """SELECT * FROM TB_DIAGNOSTICS
        WHERE xrayOrderStatus = 'PENDING' COLLATE NOCASE
           OR trueNatOrderStatus = 'PENDING' COLLATE NOCASE
           OR rifOrderStatus = 'PENDING' COLLATE NOCASE"""
    )
    suspend fun getDiagnosticsWithPendingOrder(): List<TBDiagnosticsCache>

    /** Rows holding a manually entered result that hasn't been accepted by the server yet. */
    @Query(
        """SELECT * FROM TB_DIAGNOSTICS
        WHERE xrayManualResultPendingSync = 1
           OR trueNatManualResultPendingSync = 1
           OR rifManualResultPendingSync = 1"""
    )
    suspend fun getDiagnosticsWithPendingManualResult(): List<TBDiagnosticsCache>

    @Query("SELECT * FROM TB_DIAGNOSTICS WHERE benId IN (:benIds)")
    suspend fun getDiagnosticsForBenIds(benIds: List<Long>): List<TBDiagnosticsCache>
}
