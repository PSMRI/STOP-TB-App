package org.piramalswasthya.stoptb.ui.home_activity.non_communicable_diseases.tb_confirmed.list

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.helpers.filterTbSuspectedList
import org.piramalswasthya.stoptb.repositories.RecordsRepo
import org.piramalswasthya.stoptb.repositories.dynamicRepo.ICounsellingRepository
import javax.inject.Inject

@HiltViewModel
class TBConfirmedListViewModel @Inject constructor(
    val recordsRepo: RecordsRepo,
    private val counsellingRepository: ICounsellingRepository
): ViewModel() {
    private val allBenList = recordsRepo.tbConfirmedList
    private val filter = MutableStateFlow("")

    private val _beneficiaryIdArray : MutableLiveData<List<Long>> = MutableLiveData()
    val beneficiaryIdArray : LiveData<List<Long>> = _beneficiaryIdArray
    val benList = allBenList.combine(filter) { list, filter ->
        filterTbSuspectedList(list, filter)
    }

    init {
        fetchCompletedBeneficiaries()
    }

    fun fetchCompletedBeneficiaries() {
        viewModelScope.launch {
            try {
                _beneficiaryIdArray.value = counsellingRepository.fetchAndStoreCompletedBeneficiaries()
            } catch (e: Exception) {
                // Ignore or log error
            }
        }
    }

    fun filterText(text: String) {
        viewModelScope.launch {
            filter.emit(text)
        }

    }
}