package org.piramalswasthya.stoptb.ui.home_activity.non_communicable_diseases.tb_confirmed.list

import org.piramalswasthya.stoptb.ui.debounceTyping
import org.piramalswasthya.stoptb.ui.pagedByQuery
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import androidx.paging.cachedIn
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.repositories.RecordsRepo
import org.piramalswasthya.stoptb.repositories.dynamicRepo.ICounsellingRepository
import org.piramalswasthya.stoptb.ui.counselling_activity.FormType
import org.piramalswasthya.stoptb.ui.counselling_activity.SectionPhase
import javax.inject.Inject

@HiltViewModel
class TBConfirmedListViewModel @Inject constructor(
    val recordsRepo: RecordsRepo,
    private val counsellingRepository: ICounsellingRepository
): ViewModel() {
    private val filter = MutableStateFlow("")

    private val _beneficiaryIdArray : MutableLiveData<List<Long>> = MutableLiveData()
    val beneficiaryIdArray : LiveData<List<Long>> = _beneficiaryIdArray

    // Fallback total-section count (from the locally cached form schema) used only for
    // beneficiaries with no persisted counselling progress yet, so the progress tracker
    // never has to hardcode the number of sections.
    private val _totalSectionsFallback: MutableLiveData<Int> = MutableLiveData()
    val totalSectionsFallback: LiveData<Int> = _totalSectionsFallback


    private val _localFilledCounts: MutableLiveData<Map<Long, Int>> = MutableLiveData(emptyMap())
    val localFilledCounts: LiveData<Map<Long, Int>> = _localFilledCounts

    // PR 6: paged, and the search runs in SQL. The whole village used to be loaded and
    // filtered in Kotlin on every keystroke and every database write.
    @OptIn(ExperimentalCoroutinesApi::class)
    val benList = filter.debounceTyping()
        .pagedByQuery(viewModelScope) { query -> recordsRepo.tbConfirmedPaged(query) }

    init {
        // Completed-beneficiary status is fetched by the fragment's onResume.
        fetchTotalSectionsFallback()
    }

    fun fetchCompletedBeneficiaries() {
        viewModelScope.launch {
            try {
                _beneficiaryIdArray.value = counsellingRepository.getCompletedBeneficiaries()
                    ?.filterNot{ !it.refused && it.sectionsFilled == 0 }
                    ?.map { it.beneficiaryId }
            } catch (e: Exception) {
                // Ignore or log error
            }
        }
        fetchLocalFilledCounts()
    }

    private fun fetchLocalFilledCounts() {
        viewModelScope.launch {
            try {
                _localFilledCounts.value = counsellingRepository.getLocalPreSubmitFilledCounts()
            } catch (e: Exception) {
                // Ignore; the adapter simply falls back to the API's sectionsFilled alone
            }
        }
    }

    private fun fetchTotalSectionsFallback() {
        viewModelScope.launch {
            try {
                val preSubmitSections = counsellingRepository.getSectionsByPhase(
                    FormType.TB_COUNSELLING_V2,
                    SectionPhase.PRE_SUBMIT
                )
                if (preSubmitSections.isNotEmpty()) {
                    _totalSectionsFallback.value = preSubmitSections.size
                }
            } catch (e: Exception) {
                // Ignore; the progress tracker simply has no fallback until this succeeds
            }
        }
    }

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