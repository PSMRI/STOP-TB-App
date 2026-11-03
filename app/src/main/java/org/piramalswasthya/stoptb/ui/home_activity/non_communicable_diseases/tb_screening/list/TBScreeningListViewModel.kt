package org.piramalswasthya.stoptb.ui.home_activity.non_communicable_diseases.tb_screening.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.helpers.filterTbScreeningList
import org.piramalswasthya.stoptb.repositories.RecordsRepo
import javax.inject.Inject

@HiltViewModel
class TBScreeningListViewModel @Inject constructor(
    recordsRepo: RecordsRepo
) : ViewModel() {

    private val allBenList = recordsRepo.tbScreeningList
    private val filter = MutableStateFlow("")
    // The filter runs over the whole list on every keystroke and on every database
    // emission. Collected on lifecycleScope, that ran on the main thread - thousands of
    // rows per typed character on a field device.
    val benList = allBenList.combine(filter) { list, filter ->
        filterTbScreeningList(list, filter)
    }.flowOn(Dispatchers.Default)

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