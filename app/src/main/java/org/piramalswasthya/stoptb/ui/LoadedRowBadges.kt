package org.piramalswasthya.stoptb.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import org.piramalswasthya.stoptb.model.TbDiagnosticsCardFields
import org.piramalswasthya.stoptb.model.TbScreeningSymptoms
import org.piramalswasthya.stoptb.repositories.RowBadgeRepo

/**
 * Row badges for a paged beneficiary list, read only for the rows it has loaded (PR 6, 6.3).
 * The fragment reports the loaded ids ([onRowsLoaded], from `PagingDataAdapter.loadedIds`) and
 * collects the flows below into the adapter exactly as it collected the whole-table ones.
 */
data class TbDiagnosticsSnapshot(
    val rows: List<TbDiagnosticsCardFields>,
    val coveredBenIds: Set<Long>
)

@OptIn(ExperimentalCoroutinesApi::class)
class LoadedRowBadges(scope: CoroutineScope, rowBadgeRepo: RowBadgeRepo) {

    private val loadedBenIds = MutableStateFlow<Set<Long>>(emptySet())

    fun onRowsLoaded(benIds: Set<Long>) {
        loadedBenIds.value = benIds
    }

    private val badges: Flow<RowBadgeRepo.RowBadges> = loadedBenIds
        .flatMapLatest { benIds -> rowBadgeRepo.badgesFor(benIds) }
        .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private fun <T> field(pick: (RowBadgeRepo.RowBadges) -> T): Flow<T> = badges.map(pick).distinctUntilChanged()

    val vitalBenIds: Flow<List<Long>> = field { row -> row.vitalBenIds }
    val unsyncedVitalBenIds: Flow<List<Long>> = field { row -> row.unsyncedVitalBenIds }
    val syncingVitalBenIds: Flow<List<Long>> = field { row -> row.syncingVitalBenIds }
    val tbScreeningBenIds: Flow<List<Long>> = field { row -> row.tbScreeningBenIds }
    val unsyncedTbScreeningBenIds: Flow<List<Long>> = field { row -> row.unsyncedTbScreeningBenIds }
    val syncingTbScreeningBenIds: Flow<List<Long>> = field { row -> row.syncingTbScreeningBenIds }
    val generalOpdBenIds: Flow<List<Long>> = field { row -> row.generalOpdBenIds }
    val unsyncedGeneralOpdBenIds: Flow<List<Long>> = field { row -> row.unsyncedGeneralOpdBenIds }
    val syncingGeneralOpdBenIds: Flow<List<Long>> = field { row -> row.syncingGeneralOpdBenIds }
    val anthropometryBenIds: Flow<List<Long>> = field { row -> row.anthropometryBenIds }
    val diagnosisBenIds: Flow<List<Long>> = field { row -> row.diagnosisBenIds }
    /**
     * The diagnostics card fields together with the benIds they were read for, in one value so
     * the two can't come from different snapshots. Adapters treat a row as "loaded" only when its
     * benId is covered: a missing row then means "no order", which drives the Retry Referral
     * branch - showing that for a row not looked up yet could raise a second order.
     */
    val tbDiagnostics: Flow<TbDiagnosticsSnapshot> =
        field { row -> TbDiagnosticsSnapshot(row.tbDiagnostics, row.queriedBenIds) }
    val tbScreeningSymptoms: Flow<List<TbScreeningSymptoms>> = field { row -> row.tbScreeningSymptoms }
}
