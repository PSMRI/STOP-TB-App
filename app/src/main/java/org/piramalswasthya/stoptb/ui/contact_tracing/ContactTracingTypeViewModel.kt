package org.piramalswasthya.stoptb.ui.contact_tracing

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.model.contactTracing.ContactTracingStatus
import org.piramalswasthya.stoptb.repositories.contactTracing.IContactTracingRepository
import javax.inject.Inject

@HiltViewModel
class ContactTracingTypeViewModel @Inject constructor(
    private val repository: IContactTracingRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val indexCaseBenId: Long = savedStateHandle["indexCaseBenId"] ?: 0L

    // Red-cross default (both false) until the fetch resolves — matches the PRD's
    // "keep red cross mark as default" for the not-yet-submitted/pending/loading/failed cases.
    private val _status = MutableLiveData(ContactTracingStatus())
    val status: LiveData<ContactTracingStatus> get() = _status

    init {
        // Every bottom-sheet open creates a fresh instance of this ViewModel, so this fetch
        // naturally re-runs (no stale caching) whenever the sheet is reopened.
        viewModelScope.launch {
            _status.value = repository.getContactTracingStatus(indexCaseBenId)
        }
    }
}
