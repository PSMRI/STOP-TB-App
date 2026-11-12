package org.piramalswasthya.stoptb.ui.home_activity.unscreened_people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.model.BenBasicDomain
import org.piramalswasthya.stoptb.model.BenRegCache
import org.piramalswasthya.stoptb.repositories.BenRepo
import org.piramalswasthya.stoptb.repositories.RecordsRepo
import org.piramalswasthya.stoptb.repositories.RowBadgeRepo
import org.piramalswasthya.stoptb.ui.LoadedRowBadges
import org.piramalswasthya.stoptb.ui.debounceTyping
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class UnScreenedPeopleViewModel @Inject constructor(
    private val recordsRepo: RecordsRepo,
    private val benRepo: BenRepo,
    rowBadgeRepo: RowBadgeRepo
) : ViewModel() {

    private val filter = MutableStateFlow("")

    /**
     * PR 6: paged; the search and the "no TB screening yet" exclusion run in SQL. The screen
     * used to hold the whole village's unscreened list plus every TB_SCREENING benId in memory
     * and filter both on each keystroke and each write.
     */
    /**
     * The query the latest paged data was built for (the filter value, before any trimming).
     * The screen passes it to ListTransitions.onDataSubmitted, so new pages of the previous query
     * (a write invalidating them during the search debounce) can't take the new search's scroll.
     */
    @Volatile
    var appliedQuery: String? = null
        private set

    val unscreenedList: Flow<PagingData<BenBasicDomain>> = filter.debounceTyping()
        .flatMapLatest { query -> appliedQuery = query; recordsRepo.unscreenedPaged(query.trim().lowercase()) }
        .cachedIn(viewModelScope)

    val unscreenedCount: StateFlow<Int> = recordsRepo.unscreenedNotTbScreenedCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun filterText(text: String) {
        viewModelScope.launch { filter.emit(text) }
    }

    // ---------------- Examine Status ----------------
    // PR 6 (6.3): for the rows the list has loaded, not whole tables (RowBadgeRepo).
    private val rowBadges = LoadedRowBadges(viewModelScope, rowBadgeRepo)

    fun onRowsLoaded(benIds: Set<Long>) = rowBadges.onRowsLoaded(benIds)

    val vitalBenIds: Flow<List<Long>> = rowBadges.vitalBenIds
    val tbScreeningBenIds: Flow<List<Long>> = rowBadges.tbScreeningBenIds
    val allTbDiagnostics = rowBadges.tbDiagnostics
    val householdMemberCounts = recordsRepo.householdMemberCounts
    val generalOpdBenIds: Flow<List<Long>> = rowBadges.generalOpdBenIds
    val anthropometryBenIds: Flow<List<Long>> = rowBadges.anthropometryBenIds
    val diagnosisBenIds: Flow<List<Long>> = rowBadges.diagnosisBenIds

    // ---------------- Examine Navigation ----------------

    suspend fun getBenFromId(benId: Long): BenRegCache? {
        return benRepo.getBenFromId(benId)
    }

    // ---------------- Existing Functions ----------------


}
