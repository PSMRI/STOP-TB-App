package org.piramalswasthya.stoptb.ui.home_activity.non_communicable_diseases.tb_suspected.list

import org.piramalswasthya.stoptb.ui.debounceTyping
import org.piramalswasthya.stoptb.ui.pagedByQuery
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
class TBSuspectedListViewModel @Inject constructor(
    private val recordsRepo: RecordsRepo
) : ViewModel() {

    private val filter = MutableStateFlow("")
    // PR 6: paged, and the search runs in SQL. The whole village used to be loaded and
    // filtered in Kotlin on every keystroke and every database write.
    @OptIn(ExperimentalCoroutinesApi::class)
    val benList = filter.debounceTyping()
        .pagedByQuery(viewModelScope) { query -> recordsRepo.tbPresumptivePaged(query) }

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