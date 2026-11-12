package org.piramalswasthya.stoptb.repositories.tb

import android.content.Context
import androidx.room.withTransaction
import com.google.gson.Gson
import java.io.IOException
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.configuration.GeneralOpdMasterConfig
import org.piramalswasthya.stoptb.configuration.KeyPopulationRiskFactorAutoSelect
import org.piramalswasthya.stoptb.database.room.InAppDb
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.database.room.dao.BenDao
import org.piramalswasthya.stoptb.database.room.dao.TBDao
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.database.shared_preferences.SyncRetryStore
import org.piramalswasthya.stoptb.helpers.Konstants
import org.piramalswasthya.stoptb.helpers.NetworkResponse
import org.piramalswasthya.stoptb.helpers.SyncDailyStore
import org.piramalswasthya.stoptb.helpers.manualResultTextToStore
import org.piramalswasthya.stoptb.helpers.serverResultOrNull
import org.piramalswasthya.stoptb.model.BenRegCache
import org.piramalswasthya.stoptb.model.ChestXrayResult
import org.piramalswasthya.stoptb.model.ChiefComplaintMasterCache
import org.piramalswasthya.stoptb.model.GeneralOpdCache
import org.piramalswasthya.stoptb.model.GeneralOpdPrescription
import org.piramalswasthya.stoptb.model.MtbResult
import org.piramalswasthya.stoptb.model.OpdDrugMasters
import org.piramalswasthya.stoptb.model.OpdMedicineDraft
import org.piramalswasthya.stoptb.model.OrderResultData
import org.piramalswasthya.stoptb.model.OrderStatus
import org.piramalswasthya.stoptb.model.RifResult
import org.piramalswasthya.stoptb.model.TBConfirmedTreatmentCache
import org.piramalswasthya.stoptb.model.TBDiagnosticsCache
import org.piramalswasthya.stoptb.model.TBScreeningCache
import org.piramalswasthya.stoptb.model.TBSuspectedCache
import org.piramalswasthya.stoptb.model.TbScreeningSymptoms
import org.piramalswasthya.stoptb.model.VisitCategoryMasterCache
import org.piramalswasthya.stoptb.network.AmritApiService
import org.piramalswasthya.stoptb.network.DiagnosticBeneficiaryStatusData
import org.piramalswasthya.stoptb.network.DiagnosticManualResultRequest
import org.piramalswasthya.stoptb.network.DiagnosticOrderPushRequest
import org.piramalswasthya.stoptb.network.GeneralOpdDrugResponse
import org.piramalswasthya.stoptb.network.GeneralOpdRequestDTO
import org.piramalswasthya.stoptb.network.GeneralOpdSaveRequest
import org.piramalswasthya.stoptb.network.GetDataPaginatedRequest
import org.piramalswasthya.stoptb.network.PatientRequest
import org.piramalswasthya.stoptb.network.StopTbVillageRequest
import org.piramalswasthya.stoptb.network.TBConfirmedRequestDTO
import org.piramalswasthya.stoptb.network.TBDiagnosticsRequestDTO
import org.piramalswasthya.stoptb.network.TBDiagnosticsSaveRequest
import org.piramalswasthya.stoptb.network.TBScreeningRequestDTO
import org.piramalswasthya.stoptb.network.TBScreeningSaveRequest
import org.piramalswasthya.stoptb.network.TBSuspectedRequestDTO
import org.piramalswasthya.stoptb.utils.ServerDateParser
import org.piramalswasthya.stoptb.utils.throttleLatest
import org.piramalswasthya.stoptb.work.WorkerUtils
import timber.log.Timber

/**
 * Local reads and writes of TB screening, diagnostics, suspected and confirmed records, moved
 * out of TBRepo unchanged. Saves copy the beneficiary's GPS onto the record, and diagnostics
 * saves keep order fields that changed underneath (under the per-beneficiary lock).
 */
@Singleton
class TbLocalRepo @Inject constructor(
    private val tbDao: TBDao,
    private val benDao: BenDao,
    private val benIdLocks: BenIdLocks,
    private val database: InAppDb
) {

    /** Only rows with a PENDING order (the poll's working set). */
    suspend fun getDiagnosticsWithPendingOrder(): List<TBDiagnosticsCache> = withContext(Dispatchers.IO) {
        tbDao.getDiagnosticsWithPendingOrder()
    }

    /** Only rows with a manual result still waiting to sync. */
    suspend fun getDiagnosticsWithPendingManualResult(): List<TBDiagnosticsCache> = withContext(Dispatchers.IO) {
        tbDao.getDiagnosticsWithPendingManualResult()
    }

    /** Yes/no forms of the two queries above, for callers that only test emptiness. */
    suspend fun hasDiagnosticsWithPendingOrder(): Boolean = withContext(Dispatchers.IO) {
        tbDao.hasDiagnosticsWithPendingOrder()
    }

    suspend fun hasDiagnosticsWithPendingManualResult(): Boolean = withContext(Dispatchers.IO) {
        tbDao.hasDiagnosticsWithPendingManualResult()
    }

    /** Whether any order of an integrated device still waits for an automated result. */
    suspend fun hasPollableOrder(xray: Boolean, truenat: Boolean): Boolean = withContext(Dispatchers.IO) {
        tbDao.hasPollableOrder(xray, truenat)
    }

    suspend fun getDiagnosticsList(): List<TBDiagnosticsCache> = withContext(Dispatchers.IO) {
        tbDao.getDiagnosticsList()
    }

    suspend fun getTBScreening(benId: Long): TBScreeningCache? {
        return withContext(Dispatchers.IO) {
            tbDao.getTbScreening(benId)
        }
    }

    suspend fun saveTBScreening(tbScreeningCache: TBScreeningCache) {
        withContext(Dispatchers.IO) {
            benDao.getBen(tbScreeningCache.benId)?.let { ben ->
                ben.gpsLatitude?.let { tbScreeningCache.latitude = it }
                ben.gpsLongitude?.let { tbScreeningCache.longitude = it }
            }
            val savedId = tbDao.saveTbScreening(tbScreeningCache)
            benDao.updateScreeningStatus(tbScreeningCache.benId,"SCREENED")   // NEW
            SyncDailyStore.record("TB_SCREENING", listOf(savedId), "SAVE", "SUCCESS")
        }
    }

    suspend fun saveGeneralOpd(generalOpdCache: GeneralOpdCache) {
        withContext(Dispatchers.IO) {
            val savedId = tbDao.saveGeneralOpd(generalOpdCache)
            SyncDailyStore.record("GENERAL_OPD", listOf(savedId), "SAVE", "SUCCESS")
        }
    }

    suspend fun getTBDiagnostics(benId: Long): TBDiagnosticsCache? {
        return withContext(Dispatchers.IO) {
            tbDao.getTbDiagnostics(benId)
        }
    }

    /** Returns latest TB_DIAGNOSTICS record by benId — used to get existing id before save */
    suspend fun getTBDiagnosticsById(benId: Long): TBDiagnosticsCache? {
        return withContext(Dispatchers.IO) {
            tbDao.getTbDiagnosticsByBenId(benId)
        }
    }

    // Called directly from ViewModels (form save flows) with a snapshot that can be stale by the
    // time it's written — most notably, a screen's own in-memory diagnostics object doesn't know
    // about an in-flight auto-reorder Worker (see createOrder()) that may have already cleared
    // naatResult/chestXRayResult/trueNatRifResult and moved to a new order underneath it. Re-read
    // fresh under the row's lock and, for each test type whose order id has moved on since this
    // caller's snapshot was taken, defer to the fresh order-lifecycle fields instead of the stale
    // ones — every other field in the caller's snapshot (its actual form edits) still applies.
    suspend fun saveTBDiagnostics(tbDiagnosticsCache: TBDiagnosticsCache) {
        withContext(Dispatchers.IO) {
            benDao.getBen(tbDiagnosticsCache.benId)?.let { ben ->
                ben.gpsLatitude?.let { tbDiagnosticsCache.latitude = it }
                ben.gpsLongitude?.let { tbDiagnosticsCache.longitude = it }
            }
            var knownTbHouseholdId: Long? = null
            benIdLocks.withLock(tbDiagnosticsCache.benId) {
                val fresh = tbDao.getTbDiagnosticsByBenId(tbDiagnosticsCache.benId)
                // id is a val (Room primary key) — .copy() to retarget the existing row instead
                // of reassigning, then apply the deferred-field overrides below onto that copy.
                val cache = if (fresh != null) tbDiagnosticsCache.copy(id = fresh.id) else tbDiagnosticsCache
                if (fresh != null) {
                    if (fresh.trueNatOrderId != cache.trueNatOrderId) {
                        cache.naatResult = fresh.naatResult
                        cache.trueNatOrderId = fresh.trueNatOrderId
                        cache.trueNatOrderStatus = fresh.trueNatOrderStatus
                    }
                    if (fresh.xrayOrderId != cache.xrayOrderId) {
                        cache.chestXRayResult = fresh.chestXRayResult
                        cache.xrayOrderId = fresh.xrayOrderId
                        cache.xrayOrderStatus = fresh.xrayOrderStatus
                    }
                    if (fresh.rifOrderId != cache.rifOrderId) {
                        cache.trueNatRifResult = fresh.trueNatRifResult
                        cache.rifOrderId = fresh.rifOrderId
                        cache.rifOrderStatus = fresh.rifOrderStatus
                    }
                    // Same order id, but the snapshot still says PENDING while a result arrived
                    // meanwhile (changes feed, poll, manual entry on another path): keep the
                    // fresh status and result. An order never goes back from a final status to
                    // PENDING through a form snapshot; a re-order gets a new order id instead.
                    // Owned by ManualResultRepo (set when a result is queued offline, cleared once
                    // the backend has it), which writes them directly; a form snapshot never
                    // changes them, so a stale one must not reset a queued result's flag.
                    cache.xrayManualResultPendingSync = fresh.xrayManualResultPendingSync
                    cache.trueNatManualResultPendingSync = fresh.trueNatManualResultPendingSync
                    cache.rifManualResultPendingSync = fresh.rifManualResultPendingSync
                    if (isStalePending(cache.xrayOrderStatus, fresh.xrayOrderStatus)) {
                        cache.xrayOrderStatus = fresh.xrayOrderStatus
                        cache.chestXRayResult = cache.chestXRayResult ?: fresh.chestXRayResult
                    }
                    if (isStalePending(cache.trueNatOrderStatus, fresh.trueNatOrderStatus)) {
                        cache.trueNatOrderStatus = fresh.trueNatOrderStatus
                        cache.naatResult = cache.naatResult ?: fresh.naatResult
                    }
                    if (isStalePending(cache.rifOrderStatus, fresh.rifOrderStatus)) {
                        cache.rifOrderStatus = fresh.rifOrderStatus
                        cache.trueNatRifResult = cache.trueNatRifResult ?: fresh.trueNatRifResult
                    }
                }
                val savedId = tbDao.saveTbDiagnostics(cache)
                if (cache.isKnownTbCase()) {
                    knownTbHouseholdId = benDao.getBen(cache.benId)?.householdId
                }
                SyncDailyStore.record("TB_DIAGNOSTICS", listOf(savedId), "SAVE", "SUCCESS")
            }
            // Outside the lock: it only rewrites other members' TB_SCREENING rows.
            knownTbHouseholdId?.takeIf { it > 0L }?.let { applyKnownTbContactRiskFactors(it) }
            if (tbDiagnosticsCache.isChestXRayDone == true) benDao.updateScreeningStatus(tbDiagnosticsCache.benId,"SCREENED")   // NEW

            if (tbDiagnosticsCache.isNaatConducted == true) benDao.updateScreeningStatus(tbDiagnosticsCache.benId,"SCREENED")   // NEW
        }
    }

    /** The snapshot's order is still PENDING (or unset) while the saved row has moved past it. */
    private fun isStalePending(snapshotStatus: String?, freshStatus: String?): Boolean {
        fun pending(status: String?) = status.isNullOrBlank() || status.equals("PENDING", ignoreCase = true)
        return pending(snapshotStatus) && !pending(freshStatus)
    }

    suspend fun getTBSuspected(benId: Long): TBSuspectedCache? {
        return withContext(Dispatchers.IO) {
            tbDao.getTbSuspected(benId)
        }
    }

    /**
     * Form saves take the beneficiary's lock, like syncTBSuspectedFromDiagnostics' read-and-write,
     * so that sync can't write a row derived from a read taken before this save.
     */
    suspend fun saveTBSuspected(tbSuspectedCache: TBSuspectedCache) {
        val confirmed = benIdLocks.withLock(tbSuspectedCache.benId) { saveTBSuspectedLocked(tbSuspectedCache) }
        if (confirmed) applyKnownTbContactRiskFactorsForBen(tbSuspectedCache.benId)
    }

    /**
     * Marks the beneficiary's TB suspected record confirmed, on the row as it stands now (read
     * under the lock), so a sync that updated other fields since a screen loaded its copy is
     * kept. Creates the record when [createIfMissing] and there is none. Returns false when
     * there is none and none was created.
     */
    suspend fun markTbSuspectedConfirmed(benId: Long, createIfMissing: Boolean): Boolean {
        val saved = benIdLocks.withLock(benId) {
            val current = withContext(Dispatchers.IO) { tbDao.getTbSuspected(benId) }
                ?: if (createIfMissing) TBSuspectedCache(benId = benId) else return@withLock false
            current.isConfirmed = true
            current.isTBConfirmed = true
            current.syncState = SyncState.UNSYNCED
            saveTBSuspectedLocked(current)
            true
        }
        if (saved) applyKnownTbContactRiskFactorsForBen(benId)
        return saved
    }

    /**
     * [saveTBSuspected] for a caller that already holds this beneficiary's lock (Mutex isn't
     * reentrant). Returns whether the saved record is a confirmed case; the caller then runs
     * [applyKnownTbContactRiskFactorsForBen] once the lock is released.
     */
    internal suspend fun saveTBSuspectedLocked(tbSuspectedCache: TBSuspectedCache): Boolean =
        withContext(Dispatchers.IO) {
            benDao.getBen(tbSuspectedCache.benId)?.let { ben ->
                ben.gpsLatitude?.let { tbSuspectedCache.latitude = it }
                ben.gpsLongitude?.let { tbSuspectedCache.longitude = it }
            }
            val savedId = tbDao.saveTbSuspected(tbSuspectedCache)
            SyncDailyStore.record("TB_SUSPECTED", listOf(savedId), "SAVE", "SUCCESS")
            tbSuspectedCache.isTBConfirmed == true || tbSuspectedCache.isConfirmed
        }

    suspend fun getTBConfirmed(benId: Long): TBConfirmedTreatmentCache? {
        return withContext(Dispatchers.IO) {
            tbDao.getTbConfirmed(benId)
        }
    }

    suspend fun saveTBConfirmed(tbConfirmedTreatmentCache: TBConfirmedTreatmentCache) {
        withContext(Dispatchers.IO)
        {
            val savedId = tbDao.saveTbConfirmed(tbConfirmedTreatmentCache)
            SyncDailyStore.record("TB_CONFIRMED", listOf(savedId), "SAVE", "SUCCESS")
            benDao.getBen(tbConfirmedTreatmentCache.benId)?.householdId
                ?.takeIf { it > 0L }
                ?.let { applyKnownTbContactRiskFactors(it) }
        }
    }

    suspend fun isContactOfKnownTbPatient(benId: Long, householdId: Long?): Boolean {
        val hhId = householdId?.takeIf { it > 0L } ?: return false
        return withContext(Dispatchers.IO) {
            tbDao.householdHasOtherConfirmedTbCase(hhId, benId)
        }
    }

    /** Runs [applyKnownTbContactRiskFactors] for [benId]'s household, if it has one. */
    internal suspend fun applyKnownTbContactRiskFactorsForBen(benId: Long) {
        withContext(Dispatchers.IO) {
            benDao.getBen(benId)?.householdId
                ?.takeIf { it > 0L }
                ?.let { applyKnownTbContactRiskFactors(it) }
        }
    }

    /**
     * Writes "Contact of Known TB Patients" onto household members' saved screenings.
     * A null or non-positive [householdId] updates every household that has a confirmed case.
     * Members who were screened before the confirmation are included.
     * Takes no beneficiary lock (TB_SCREENING writes don't use one), so callers holding a
     * BenIdLocks lock may call it, but the save paths here call it after releasing theirs.
     */
    suspend fun applyKnownTbContactRiskFactors(householdId: Long? = null) {
        withContext(Dispatchers.IO) {
            val screenings = tbDao.getScreeningsForKnownTbContacts(householdId?.takeIf { it > 0L } ?: 0L)
            var updated = 0
            for (candidate in screenings) {
                // Re-read inside a transaction and change only the risk-factor fields: the list
                // above is a snapshot, and a form save in between must not be undone by writing
                // that older row back.
                val changed = database.withTransaction {
                    val screening = tbDao.getTbScreeningById(candidate.id) ?: return@withTransaction false
                    val selection = KeyPopulationRiskFactorAutoSelect.addContactOfKnownTb(
                        screening.keyPopulationRiskFactorIds,
                        screening.keyPopulationRiskFactors
                    ) ?: return@withTransaction false
                    screening.keyPopulationRiskFactorIds = selection.ids
                    screening.keyPopulationRiskFactors = selection.codes
                    screening.syncState = SyncState.UNSYNCED
                    tbDao.saveTbScreening(screening)
                    true
                }
                if (changed) updated++
            }
            if (updated > 0) {
                Timber.d("Selected Contact of Known TB Patients on $updated screening record(s)")
            }
        }
    }

    private fun TBDiagnosticsCache.isKnownTbCase(): Boolean {
        if (isTBConfirmed == true || isConfirmed) return true
        val naat = naatResult?.trim()?.uppercase().orEmpty()
        if (naat == "POSITIVE" || naat == "MTB DETECTED" || naat == "TB POSITIVE") return true
        return liquidCultureResult?.trim()?.equals("POSITIVE", ignoreCase = true) == true
    }

    suspend fun getAllFollowUpsForBeneficiary(benId: Long): List<TBConfirmedTreatmentCache> {
        return withContext(Dispatchers.IO) {
            tbDao.getAllFollowUpsForBeneficiary(benId)
        }
    }
}
