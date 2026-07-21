package org.piramalswasthya.stoptb.ui.home_activity.non_hh

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.model.BenBasicDomain
import org.piramalswasthya.stoptb.model.HouseholdBasicCache
import org.piramalswasthya.stoptb.repositories.BenRepo
import org.piramalswasthya.stoptb.repositories.RecordsRepo
import org.piramalswasthya.stoptb.repositories.TBRepo
import org.piramalswasthya.stoptb.repositories.VitalRepo
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NonHHViewModel @Inject constructor(
    private val recordsRepo: RecordsRepo,
    private val benRepo: BenRepo,
    private val vitalRepo: VitalRepo,
    private val tbRepo: TBRepo,
    private val preferenceDao: PreferenceDao
) : ViewModel() {

    // ── Examine form fill status ──────────────────────────────────────────────
    val vitalBenIds: StateFlow<List<Long>> = vitalRepo.vitalBenIds
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val tbScreeningBenIds: StateFlow<List<Long>> = tbRepo.tbScreeningBenIds
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val generalOpdBenIds: StateFlow<List<Long>> = tbRepo.generalOpdBenIds
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val anthropometryBenIds: StateFlow<List<Long>> = recordsRepo.anthropometryFilledBenIds
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val diagnosisBenIds: StateFlow<List<Long>> = combine(
        tbRepo.tbDiagnosticsBenIds,
        tbRepo.tbSuspectedBenIds
    ) { diagnostics, suspected -> (diagnostics + suspected).distinct() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> get() = _searchQuery

    val nonHHList: StateFlow<List<BenBasicDomain>> = _searchQuery
        .flatMapLatest { query ->
            recordsRepo.searchNonHH(query)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val hhList: Flow<List<HouseholdBasicCache>>
        get() {
            val location = preferenceDao.getLocationRecord()
            val selectedVillage = location?.village?.id ?: 0
            return benRepo.getHouseholds(selectedVillage)
        }

    fun filterText(query: String) {
        _searchQuery.value = query
    }

    suspend fun getBenFromId(benId: Long) = benRepo.getBenFromId(benId)

    fun linkBenToHousehold(benId: Long, hhId: Long, relationPos: Int, relationName: String) {
        viewModelScope.launch {
            benRepo.linkBenToHousehold(benId, hhId, relationPos, relationName)
        }
    }
}
