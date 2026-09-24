package org.piramalswasthya.stoptb.ui.volunteer.fragment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.piramalswasthya.stoptb.database.room.dao.BenDao
import org.piramalswasthya.stoptb.database.room.dao.HouseholdDao
import org.piramalswasthya.stoptb.database.room.dao.TBDao
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
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
    benDao: BenDao,
    tbDao: TBDao,
    preferenceDao: PreferenceDao,
) : ViewModel() {

    private val villageId = preferenceDao.getLocationRecord()?.village?.id ?: 0
    private val assignedVillageIds = preferenceDao.getLoggedInUser()
        ?.villages
        .orEmpty()
        .map { it.id }
        .ifEmpty { listOf(-1) }

    val glance: StateFlow<HomeGlance> = combine(
        tbDao.getDashboardPresumptiveTbCount(villageId, assignedVillageIds, 0L, 0L, "", 0),
        householdDao.getGlanceHouseholdCount(villageId, assignedVillageIds),
        benDao.getGlancePopulationCount(villageId, assignedVillageIds),
        benDao.getGlanceUnscreenedCount(villageId, assignedVillageIds),
    ) { presumptive, households, population, unscreened ->
        HomeGlance(
            presumptiveReferral = presumptive,
            households = households,
            population = population,
            unscreened = unscreened,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeGlance())
}
