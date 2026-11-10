package org.piramalswasthya.stoptb.ui.home_activity.non_hh

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.FlowPreview
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.model.BenBasicDomain
import org.piramalswasthya.stoptb.model.HouseholdBasicCache
import org.piramalswasthya.stoptb.repositories.BenRepo
import org.piramalswasthya.stoptb.repositories.RecordsRepo
import org.piramalswasthya.stoptb.repositories.TBRepo
import org.piramalswasthya.stoptb.repositories.VitalRepo
import org.piramalswasthya.stoptb.repositories.RowBadgeRepo
import org.piramalswasthya.stoptb.ui.LoadedRowBadges
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class NonHHViewModel @Inject constructor(
    private val recordsRepo: RecordsRepo,
    private val benRepo: BenRepo,
    private val vitalRepo: VitalRepo,
    private val tbRepo: TBRepo,
    private val preferenceDao: PreferenceDao,
    private val rowBadgeRepo: RowBadgeRepo
) : ViewModel() {

    // ── Examine form fill status ──────────────────────────────────────────────
    // PR 6 (6.3): for the rows the list has loaded, not whole tables (RowBadgeRepo).
    private val rowBadges = LoadedRowBadges(viewModelScope, rowBadgeRepo)

    fun onRowsLoaded(benIds: Set<Long>) = rowBadges.onRowsLoaded(benIds)

    val vitalBenIds: Flow<List<Long>> = rowBadges.vitalBenIds
    val unsyncedVitalBenIds: Flow<List<Long>> = rowBadges.unsyncedVitalBenIds
    val syncingVitalBenIds: Flow<List<Long>> = rowBadges.syncingVitalBenIds
    val tbScreeningBenIds: Flow<List<Long>> = rowBadges.tbScreeningBenIds
    val allTbDiagnostics = rowBadges.tbDiagnostics
    val unsyncedTbScreeningBenIds: Flow<List<Long>> = rowBadges.unsyncedTbScreeningBenIds
    val syncingTbScreeningBenIds: Flow<List<Long>> = rowBadges.syncingTbScreeningBenIds
    val generalOpdBenIds: Flow<List<Long>> = rowBadges.generalOpdBenIds
    val unsyncedGeneralOpdBenIds: Flow<List<Long>> = rowBadges.unsyncedGeneralOpdBenIds
    val syncingGeneralOpdBenIds: Flow<List<Long>> = rowBadges.syncingGeneralOpdBenIds
    val anthropometryBenIds: Flow<List<Long>> = rowBadges.anthropometryBenIds
    val diagnosisBenIds: Flow<List<Long>> = rowBadges.diagnosisBenIds

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> get() = _searchQuery

    /**
     * Pages the non-household list instead of materialising it.
     *
     * The previous `Flow<List<BenBasicDomain>>` loaded every matching row - on a camp device
     * that is the entire village (~20k rows of 92 columns), rebuilt from scratch on every
     * database invalidation. Measured on the device, just walking that cursor took 2.5s, and
     * the resulting allocation churn kept the heap pinned near its limit, so the whole app
     * stalled in GC. Paging keeps it to one 30-row page.
     */
    val nonHHList: Flow<PagingData<BenBasicDomain>> = _searchQuery
        // Typing fires onTextChanged per keystroke, and each distinct query starts a fresh
        // query. Collapse a burst of keystrokes into one.
        .debounce { query -> if (query.isEmpty()) 0L else searchDebounceMs }
        .distinctUntilChanged()
        .flatMapLatest { query ->
            Pager(config = PagingConfig(pageSize = 30, prefetchDistance = 10, maxSize = 300)) {
                recordsRepo.searchNonHHPagedSource(query)
            }.flow.map { pagingData -> pagingData.map { it.asBasicDomainModel() } }
        }
        .cachedIn(viewModelScope)

    val hhList: Flow<List<HouseholdBasicCache>>
        get() {
            val location = preferenceDao.getLocationRecord()
            val selectedVillage = location?.village?.id ?: 0
            return benRepo.getHouseholds(selectedVillage)
        }

    fun filterText(query: String) {
        _searchQuery.value = query
    }

    private companion object {
        const val searchDebounceMs = 300L
    }

    suspend fun getBenFromId(benId: Long) = benRepo.getBenFromId(benId)

    fun linkBenToHousehold(benId: Long, hhId: Long, relationPos: Int, relationName: String) {
        viewModelScope.launch {
            benRepo.linkBenToHousehold(benId, hhId, relationPos, relationName)
        }
    }
}
