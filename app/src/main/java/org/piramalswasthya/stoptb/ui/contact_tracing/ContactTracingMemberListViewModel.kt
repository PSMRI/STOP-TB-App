package org.piramalswasthya.stoptb.ui.contact_tracing

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.repositories.contactTracing.IContactTracingRepository
import javax.inject.Inject

data class ContactMemberItem(
    val responseId: Long,
    val displayName: String,
    val status: String
)

/**
 * Backs the Community/Occupational contact list. Household never uses this — those
 * contacts are existing Ben records reached through the existing household roster screen.
 * There is no backend "fetch members" endpoint yet, so this simply lists whatever contact
 * records have already been created locally for this index case + type.
 */
@HiltViewModel
class ContactTracingMemberListViewModel @Inject constructor(
    private val repository: IContactTracingRepository
) : ViewModel() {

    private val _members = MutableLiveData<List<ContactMemberItem>>(emptyList())
    val members: LiveData<List<ContactMemberItem>> get() = _members

    fun load(indexCaseBenId: Long, contactType: String) {
        val nameQuestionUuid = if (contactType == "OCCUPATIONAL") "OCC_Q1" else "COM_Q1"
        viewModelScope.launch {
            repository.getResponsesForIndexCase(indexCaseBenId, contactType).collectLatest { responses ->
                _members.value = responses.map { r ->
                    val name = repository.getContactDisplayName(r.responseId, nameQuestionUuid)
                    ContactMemberItem(
                        responseId = r.responseId,
                        displayName = name?.takeIf { it.isNotBlank() } ?: "Unnamed contact",
                        status = r.status
                    )
                }
            }
        }
    }
}
