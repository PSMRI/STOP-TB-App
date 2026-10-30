package org.piramalswasthya.stoptb.ui.home_activity.all_ben.data_missing_ben

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import org.piramalswasthya.stoptb.model.TBDiagnosticsCache
import org.piramalswasthya.stoptb.model.TBScreeningCache
import javax.inject.Inject

@HiltViewModel
class MissingDataBenViewModel @Inject constructor(
    private val repository: MissingDataRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MissingDataBenUiState())
    val uiState: StateFlow<MissingDataBenUiState> = _uiState.asStateFlow()

    val tbScreeningBenIds: Flow<List<Long>> = repository.tbScreeningBenIds
    val allTbDiagnostics: Flow<List<TBDiagnosticsCache>> = repository.allTbDiagnostics
    val allTbScreening: Flow<List<TBScreeningCache>> = repository.allTbScreening
    val householdMemberCounts: Flow<Map<Long, Int>> = repository.householdMemberCounts
    val householdHeadNames: Flow<Map<Long, String>> = repository.householdHeadNames
    val benPagingData: Flow<PagingData<MissingDataBenItem>> =
        uiState
            .map { it.searchQuery }
            .debounce{ if (it.isEmpty()) 0L else 1000L }
            .distinctUntilChanged()
            .flatMapLatest { query ->
                repository.getMissingBeneficiaries(
                    searchQuery = query.trim()
                )
            }
            .flowOn(Dispatchers.IO)
            .cachedIn(viewModelScope)

    private val _effect = Channel<MissingDataBenEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    private val intentChannel = Channel<MissingDataBenIntent>(Channel.UNLIMITED)

    init {
        intentChannel
            .receiveAsFlow()
            .onEach { intent ->
                handleIntent(intent)
            }
            .launchIn(viewModelScope)
    }

    private suspend fun handleIntent(intent: MissingDataBenIntent) {
        when (intent) {
            is MissingDataBenIntent.SearchQueryChanged -> handleSearch(intent.query)
            is MissingDataBenIntent.ItemClicked -> handleItemClicked(intent.item)
            is MissingDataBenIntent.EditItemClicked -> handleEditItemClicked(intent.item)
            is MissingDataBenIntent.UpdateListState -> {
                _uiState.update { it.copy(isEmpty = intent.isEmpty) }
            }
            is MissingDataBenIntent.UpdateLoadingState -> {
                _uiState.update { it.copy(isLoading = intent.isLoading) }
            }
        }
    }

    fun processIntent(intent: MissingDataBenIntent) {
        intentChannel.trySend(intent)
    }

    private fun handleSearch(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    private fun handleItemClicked(item: MissingDataBenItem) {
        _effect.trySend(MissingDataBenEffect.ShowDetailsDialog(item))
    }

    private fun handleEditItemClicked(item: MissingDataBenItem) {
        _effect.trySend(MissingDataBenEffect.OpenEditDetails(item))
    }
}
