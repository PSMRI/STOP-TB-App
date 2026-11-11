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
 * Whole-table badge/decoration queries used by the beneficiary list screens (ids, sync states, card fields).
 *
 * Part of [TBDao] (which extends this interface); split out only to keep the DAO
 * readable. Room generates these queries as part of TBDao_Impl - SQL and callers unchanged.
 */
interface TbBadgeQueries {

    /** Existing rows for a whole pull chunk in one statement instead of one per record. */
    @Query("SELECT * FROM TB_SCREENING WHERE benId IN (:benIds)")
    suspend fun getTbScreeningByBenIds(benIds: List<Long>): List<TBScreeningCache>

    @Query("SELECT benId FROM TB_SCREENING")
    fun getAllTbScreeningBenIds(): Flow<List<Long>>

    /** id + syncState in one pass; callers split it instead of running three queries. */
    @Query("SELECT benId, syncState FROM TB_SCREENING")
    fun getTbScreeningBenIdSyncStates(): Flow<List<org.piramalswasthya.stoptb.model.BenIdSyncState>>

    /** id + syncState in one pass; callers split it instead of running three queries. */
    @Query("SELECT benId, syncState FROM GENERAL_OPD")
    fun getGeneralOpdBenIdSyncStates(): Flow<List<org.piramalswasthya.stoptb.model.BenIdSyncState>>

    @Query("SELECT benId FROM GENERAL_OPD")
    fun getAllGeneralOpdBenIds(): Flow<List<Long>>

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
                "rifManualResultPendingSync, " +
                "(SELECT MAX(s.visitDate) FROM TB_SCREENING s WHERE s.benId = TB_DIAGNOSTICS.benId) " +
                "AS latestScreeningAt FROM TB_DIAGNOSTICS"
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

    // ── PR 6: the same badges scoped to the rows a paged list has loaded (<= ~300 ids) ──
    // The list screens used to observe these for every row of the table, re-read on each write.

    @Query("SELECT benId, syncState FROM TB_SCREENING WHERE benId IN (:benIds)")
    fun getTbScreeningBenIdSyncStatesIn(benIds: Collection<Long>): Flow<List<org.piramalswasthya.stoptb.model.BenIdSyncState>>

    @Query("SELECT benId, syncState FROM GENERAL_OPD WHERE benId IN (:benIds)")
    fun getGeneralOpdBenIdSyncStatesIn(benIds: Collection<Long>): Flow<List<org.piramalswasthya.stoptb.model.BenIdSyncState>>

    @Query("SELECT benId FROM TB_SUSPECTED WHERE benId IN (:benIds)")
    fun getTbSuspectedBenIdsIn(benIds: Collection<Long>): Flow<List<Long>>

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
                "rifManualResultPendingSync, " +
                "(SELECT MAX(s.visitDate) FROM TB_SCREENING s WHERE s.benId = TB_DIAGNOSTICS.benId) " +
                "AS latestScreeningAt FROM TB_DIAGNOSTICS" +
                " WHERE benId IN (:benIds)"
    )
    fun getTbDiagnosticsCardFieldsIn(benIds: Collection<Long>): Flow<List<TbDiagnosticsCardFields>>

    @Query(
        "SELECT benId, coughMoreThan2Weeks, bloodInSputum, feverMoreThan2Weeks, riseOfFever, " +
                "lossOfAppetite, lossOfWeight, nightSweats, historyOfTb, takingAntiTBDrugs, " +
                "familySufferingFromTB, asymptomatic FROM TB_SCREENING" +
                " WHERE benId IN (:benIds)"
    )
    fun getTbScreeningSymptomsIn(benIds: Collection<Long>): Flow<List<TbScreeningSymptoms>>
}
