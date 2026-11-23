package org.piramalswasthya.stoptb.ui.volunteer.fragment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.piramalswasthya.stoptb.database.room.dao.HouseholdDao
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.repositories.VillageCountsRepo
import javax.inject.Inject

data class HomeGlance(
    val presumptiveReferral: Int = 0,
    val households: Int = 0,
    val population: Int = 0,
    val unscreened: Int = 0,
)

@HiltViewModel
class VolunteerHomeGlanceViewModel @Inject constructor(
    householdDao: HouseholdDao,
    preferenceDao: PreferenceDao,
    villageCountsRepo: VillageCountsRepo,
) : ViewModel() {

    // Login (or the home village switch) stores one village on the location record.
    // Counts follow that village by ID, same as the household and headcount queries.
    private val selectedVillageId: Int = preferenceDao.getLocationRecord()
        ?.village
        ?.id
        ?.takeIf { it != 0 }
        ?: -1
    // Room IN-lists of size 1 are expanded unreliably; pass the id twice.
    private val selectedVillageIds: List<Int> = listOf(selectedVillageId, selectedVillageId)

    val glance: StateFlow<HomeGlance> = combine(
        villageCountsRepo.presumptive(selectedVillageIds),
        householdDao.getAllHouseholdsCount(selectedVillageId),
        // Same shared query as the home cards' All Beneficiaries / Non-Household counts.
        villageCountsRepo.counts(selectedVillageId),
    ) { presumptive, households, headcount ->
        HomeGlance(
            presumptiveReferral = presumptive,
            households = households,
            population = headcount.population,
            unscreened = headcount.unscreened,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeGlance())
}
