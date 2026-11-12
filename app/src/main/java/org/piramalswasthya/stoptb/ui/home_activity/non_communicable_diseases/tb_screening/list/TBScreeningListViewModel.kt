package org.piramalswasthya.stoptb.ui.home_activity.non_communicable_diseases.tb_screening.list

import org.piramalswasthya.stoptb.ui.debounceTyping
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import androidx.paging.cachedIn
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.repositories.RecordsRepo
import javax.inject.Inject

@HiltViewModel
class TBScreeningListViewModel @Inject constructor(
    private val recordsRepo: RecordsRepo
) : ViewModel() {

    private val filter = MutableStateFlow("")
    // PR 6: paged, and the search runs in SQL. The whole village used to be loaded and
    // filtered in Kotlin on every keystroke and every database write.
    @OptIn(ExperimentalCoroutinesApi::class)
    /**
     * The query the latest paged data was built for (the filter value, before any trimming).
     * The screen passes it to ListTransitions.onDataSubmitted, so new pages of the previous query
     * (a write invalidating them during the search debounce) can't take the new search's scroll.
     */
    @Volatile
    var appliedQuery: String? = null
        private set

    val benList = filter.debounceTyping()
        .flatMapLatest { query -> appliedQuery = query; recordsRepo.tbScreeningPaged(query) }
        .cachedIn(viewModelScope)

    fun filterText(text: String) {
        viewModelScope.launch {
            // The filter helpers lowercase the record's fields but not the query, so a typed
            // "Sundar" never matched "sundar singh" - and Android capitalises the first letter
            // by default, which made most typed searches return nothing. The speech-to-text path
            // already passed a lowercased string, which is why only typing looked broken.
            filter.emit(text.lowercase())
        }
    }
}