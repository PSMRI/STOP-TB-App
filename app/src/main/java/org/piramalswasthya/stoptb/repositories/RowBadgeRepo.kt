package org.piramalswasthya.stoptb.repositories

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.database.room.dao.BenDao
import org.piramalswasthya.stoptb.database.room.dao.TBDao
import org.piramalswasthya.stoptb.database.room.dao.VitalDao
import org.piramalswasthya.stoptb.model.BenIdSyncState
import org.piramalswasthya.stoptb.model.TbDiagnosticsCardFields
import org.piramalswasthya.stoptb.model.TbScreeningSymptoms
import org.piramalswasthya.stoptb.utils.throttleLatest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything a beneficiary card shows besides the beneficiary itself (vitals / screening / OPD
 * done and their sync state, anthropometry, diagnosis, the diagnostics card fields, screening
 * symptoms), for just the rows a paged list has loaded.
 *
 * PR 6 (6.3): the list screens used to observe each of these for every row of its table -
 * ~15 whole-table reads, re-run on every write to the table - to decorate the 30-300 rows on
 * screen. Paging keeps at most ~300 rows loaded (PagingConfig.maxSize), so the IN lists stay far
 * below SQLite's 999-variable limit on old devices; [MAX_IDS] is a backstop.
 */
@Singleton
class RowBadgeRepo @Inject constructor(
    private val benDao: BenDao,
    private val tbDao: TBDao,
    private val vitalDao: VitalDao
) {

    data class RowBadges(
        val vitalBenIds: List<Long> = emptyList(),
        val unsyncedVitalBenIds: List<Long> = emptyList(),
        val syncingVitalBenIds: List<Long> = emptyList(),
        val tbScreeningBenIds: List<Long> = emptyList(),
        val unsyncedTbScreeningBenIds: List<Long> = emptyList(),
        val syncingTbScreeningBenIds: List<Long> = emptyList(),
        val generalOpdBenIds: List<Long> = emptyList(),
        val unsyncedGeneralOpdBenIds: List<Long> = emptyList(),
        val syncingGeneralOpdBenIds: List<Long> = emptyList(),
        val anthropometryBenIds: List<Long> = emptyList(),
        /** TB_DIAGNOSTICS (new saves) or TB_SUSPECTED (legacy saves), as AllBenViewModel defined it. */
        val diagnosisBenIds: List<Long> = emptyList(),
        val tbDiagnostics: List<TbDiagnosticsCardFields> = emptyList(),
        val tbScreeningSymptoms: List<TbScreeningSymptoms> = emptyList(),
        /**
         * The benIds this snapshot was read for. A row outside it has not been looked up yet,
         * so a missing entry above means "not loaded", not "none" (e.g. a page that has just
         * loaded while the previous snapshot is still the latest).
         */
        val queriedBenIds: Set<Long> = emptySet()
    )

    fun badgesFor(benIds: Collection<Long>): Flow<RowBadges> {
        if (benIds.isEmpty()) return flowOf(RowBadges())
        val ids = if (benIds.size > MAX_IDS) benIds.take(MAX_IDS) else benIds
        val queriedBenIds = ids.toSet()
        val sources: List<Flow<List<*>>> = listOf(
            vitalDao.getVitalBenIdSyncStatesIn(ids),
            tbDao.getTbScreeningBenIdSyncStatesIn(ids),
            tbDao.getGeneralOpdBenIdSyncStatesIn(ids),
            benDao.getAnthropometryFilledBenIdsIn(ids),
            tbDao.getTbSuspectedBenIdsIn(ids),
            tbDao.getTbDiagnosticsCardFieldsIn(ids),
            tbDao.getTbScreeningSymptomsIn(ids)
        )
        @Suppress("UNCHECKED_CAST")
        return combine(sources) { results ->
            val vitals = results[0] as List<BenIdSyncState>
            val screening = results[1] as List<BenIdSyncState>
            val opd = results[2] as List<BenIdSyncState>
            val diagnostics = results[5] as List<TbDiagnosticsCardFields>
            RowBadges(
                vitalBenIds = vitals.map { row -> row.benId },
                unsyncedVitalBenIds = vitals.withState(SyncState.UNSYNCED),
                syncingVitalBenIds = vitals.withState(SyncState.SYNCING),
                tbScreeningBenIds = screening.map { row -> row.benId },
                unsyncedTbScreeningBenIds = screening.withState(SyncState.UNSYNCED),
                syncingTbScreeningBenIds = screening.withState(SyncState.SYNCING),
                generalOpdBenIds = opd.map { row -> row.benId },
                unsyncedGeneralOpdBenIds = opd.withState(SyncState.UNSYNCED),
                syncingGeneralOpdBenIds = opd.withState(SyncState.SYNCING),
                anthropometryBenIds = results[3] as List<Long>,
                diagnosisBenIds = (diagnostics.map { row -> row.benId } + results[4] as List<Long>).distinct(),
                tbDiagnostics = diagnostics,
                tbScreeningSymptoms = results[6] as List<TbScreeningSymptoms>,
                queriedBenIds = queriedBenIds
            )
        }.throttleLatest(BADGE_UPDATE_PERIOD_MS).flowOn(Dispatchers.Default)
    }

    private fun List<BenIdSyncState>.withState(state: SyncState) =
        filter { row -> row.syncState == state }.map { row -> row.benId }

    private companion object {
        const val MAX_IDS = 900
        /** Same coalescing as the whole-table badge flows (TBRepo): a sync writes many rows a second. */
        const val BADGE_UPDATE_PERIOD_MS = 500L
    }
}
