package org.piramalswasthya.stoptb.ui.home_activity.all_ben.data_missing_ben

import org.piramalswasthya.stoptb.model.BenBasicDomain

data class MissingDataBenItem(
    val id: Long,
    val reasons: List<String> = emptyList(),
    val benBasicDomain: BenBasicDomain
)

data class MissingDataBenUiState(
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val isEmpty: Boolean = false,
)

sealed interface MissingDataBenIntent {
    data class SearchQueryChanged(val query: String) : MissingDataBenIntent
    data class ItemClicked(val item: MissingDataBenItem) : MissingDataBenIntent
    data class EditItemClicked(val item: MissingDataBenItem) : MissingDataBenIntent
    data class UpdateListState(val isEmpty: Boolean = false) : MissingDataBenIntent
    data class UpdateLoadingState(val isLoading: Boolean = false) : MissingDataBenIntent
}

sealed interface MissingDataBenEffect {
    data class ShowToast(val message: String) : MissingDataBenEffect
    data class ShowDetailsDialog(val item: MissingDataBenItem) : MissingDataBenEffect
    data class OpenEditDetails(val item: MissingDataBenItem) : MissingDataBenEffect
}
