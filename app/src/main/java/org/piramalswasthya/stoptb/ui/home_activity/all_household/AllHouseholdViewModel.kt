package org.piramalswasthya.stoptb.ui.home_activity.all_household

import org.piramalswasthya.stoptb.ui.debounceTyping
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val recordsRepo: RecordsRepo,
    private val preferenceDao: PreferenceDao
) : ViewModel() {

    private val filter = MutableStateFlow("")

    private val _hasDraft = MutableLiveData(false)
    val hasDraft: LiveData<Boolean> get() = _hasDraft

    private val _navigateToNewHouseholdRegistration = MutableLiveData(false)
    val navigateToNewHouseholdRegistration: LiveData<Boolean>
        get() = _navigateToNewHouseholdRegistration

    // PR 6: paged; the search and the sort that ran over the whole list in Kotlin are in SQL.
    @OptIn(ExperimentalCoroutinesApi::class)
    val householdList: Flow<PagingData<HouseHoldBasicDomain>> = filter.debounceTyping()
        .flatMapLatest { query -> recordsRepo.householdsPaged(query.trim().lowercase()) }
        .cachedIn(viewModelScope)

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
}
