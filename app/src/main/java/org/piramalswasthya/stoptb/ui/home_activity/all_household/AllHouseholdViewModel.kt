package org.piramalswasthya.stoptb.ui.home_activity.all_household

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.model.BenRegCache
import org.piramalswasthya.stoptb.model.HouseHoldBasicDomain
import org.piramalswasthya.stoptb.model.HouseholdCache
import org.piramalswasthya.stoptb.repositories.HouseholdRepo
import org.piramalswasthya.stoptb.repositories.RecordsRepo
import javax.inject.Inject

@HiltViewModel
class AllHouseholdViewModel @Inject constructor(
    private val householdRepo: HouseholdRepo,
    recordsRepo: RecordsRepo,
    private val preferenceDao: PreferenceDao
) : ViewModel() {

    private val filter = MutableStateFlow("")

    private val _hasDraft = MutableLiveData(false)
    val hasDraft: LiveData<Boolean> get() = _hasDraft

    private val _navigateToNewHouseholdRegistration = MutableLiveData(false)
    val navigateToNewHouseholdRegistration: LiveData<Boolean>
        get() = _navigateToNewHouseholdRegistration

    val householdList = recordsRepo.hhList.combine(filter) { list, text ->
        filterHouseholds(list, text)
    }

    private var _selectedHouseholdId: Long = 0L
    val selectedHouseholdId: Long get() = _selectedHouseholdId

    private val _householdBenList = mutableListOf<BenRegCache>()
    val householdBenList: List<BenRegCache> get() = _householdBenList

    fun checkDraft() {
        viewModelScope.launch {
            _hasDraft.value = householdRepo.getDraftRecord() != null
        }
    }

    fun navigateToNewHouseholdRegistration(deleteDraft: Boolean) {
        viewModelScope.launch {
            if (deleteDraft) householdRepo.deleteHouseholdDraft()
            _navigateToNewHouseholdRegistration.value = true
        }
    }

    fun navigateToNewHouseholdRegistrationCompleted() {
        _navigateToNewHouseholdRegistration.value = false
    }

    fun filterText(text: String) {
        viewModelScope.launch { filter.emit(text) }
    }

    fun resetSelectedHouseholdId() {
        _selectedHouseholdId = 0L
        _householdBenList.clear()
    }

    fun setSelectedHouseholdId(id: Long) {
        _selectedHouseholdId = id
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _householdBenList.clear()
                _householdBenList.addAll(householdRepo.getAllBenOfHousehold(id))
            }
        }
    }

    suspend fun getHousehold(id: Long): HouseholdCache? = householdRepo.getRecord(id)

    private fun filterHouseholds(
        list: List<HouseHoldBasicDomain>,
        filter: String
    ): List<HouseHoldBasicDomain> {
        val filteredList = if (filter.isBlank()) {
            list
        } else {
            val filterText = filter.lowercase()
            list.filter {
                it.hhId.toString().contains(filterText) ||
                    it.headFullName.lowercase().contains(filterText) ||
                    it.contactNumber.contains(filterText)
            }
        }

        return filteredList.sortedWith(
            compareBy<HouseHoldBasicDomain> { it.isDeactivate }
                .thenByDescending { it.createdTimeStamp }
        )
    }
}
