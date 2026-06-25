package org.piramalswasthya.stoptb.ui.home_activity.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.database.room.dao.BenDao
import org.piramalswasthya.stoptb.database.room.dao.TBDao
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.model.LocationEntity
import java.util.Calendar
import javax.inject.Inject

data class TbGenderBreakdown(
    val total: Int = 0,
    val male: Int = 0,
    val female: Int = 0,
    val children: Int = 0,
    val others: Int = 0,
    val seniorCitizen: Int = 0

    )

data class PositiveNegativeCount(
    val positive: Int = 0,
    val negative: Int = 0,
) {
    val total: Int get() = positive + negative
}

data class TbPositiveNegativeBreakdown(
    val total: PositiveNegativeCount = PositiveNegativeCount(),
    val male: PositiveNegativeCount = PositiveNegativeCount(),
    val female: PositiveNegativeCount = PositiveNegativeCount(),
    val children: PositiveNegativeCount = PositiveNegativeCount(),
    val others: PositiveNegativeCount = PositiveNegativeCount(),
)

private enum class PositiveNegativeGroup {
    TOTAL,
    MALE,
    FEMALE,
    CHILDREN,
    OTHERS
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val benDao: BenDao,
    private val tbDao: TBDao,
    private val preferenceDao: PreferenceDao,
) : ViewModel() {

    // Filter state
    private val _selectedTimePeriod = MutableLiveData("Today")
    val selectedTimePeriod: LiveData<String> get() = _selectedTimePeriod

    private val _selectedVillageName = MutableLiveData("All Villages")
    val selectedVillageName: LiveData<String> get() = _selectedVillageName

    private var selectedVillageId: Int = 0 // 0 = all villages

    // Village list for dropdown
    val villageList: List<LocationEntity>
        get() {
            val user = preferenceDao.getLoggedInUser() ?: return emptyList()
            return user.villages
        }

    // Time period options
    val timePeriodOptions = listOf(
        "Today", "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    // Dashboard data
    private val _tbScreening = MutableLiveData(TbGenderBreakdown())
    val tbScreening: LiveData<TbGenderBreakdown> get() = _tbScreening

    private val _pastHistoryTb = MutableLiveData(TbPositiveNegativeBreakdown())
    val pastHistoryTb: LiveData<TbPositiveNegativeBreakdown> get() = _pastHistoryTb

    private val _antiTbDrugs = MutableLiveData(TbPositiveNegativeBreakdown())
    val antiTbDrugs: LiveData<TbPositiveNegativeBreakdown> get() = _antiTbDrugs

    private val _tbSuspected = MutableLiveData(TbGenderBreakdown())
    val tbSuspected: LiveData<TbGenderBreakdown> get() = _tbSuspected

    private val _tbConfirmed = MutableLiveData(TbGenderBreakdown())
    val tbConfirmed: LiveData<TbGenderBreakdown> get() = _tbConfirmed

    private val _digitalChestXray = MutableLiveData(TbGenderBreakdown())
    val digitalChestXray: LiveData<TbGenderBreakdown> get() = _digitalChestXray

    private val _sputumCollection = MutableLiveData(TbGenderBreakdown())
    val sputumCollection: LiveData<TbGenderBreakdown> get() = _sputumCollection

    private val _trueNat = MutableLiveData(TbGenderBreakdown())
    val trueNat: LiveData<TbGenderBreakdown> get() = _trueNat

    private val _liquidCulture = MutableLiveData(TbGenderBreakdown())
    val liquidCulture: LiveData<TbGenderBreakdown> get() = _liquidCulture

    private val _hwcReferral = MutableLiveData(TbGenderBreakdown())
    val hwcReferral: LiveData<TbGenderBreakdown> get() = _hwcReferral

    private val _nikshayCount = MutableLiveData(0)
    val nikshayCount: LiveData<Int> get() = _nikshayCount

    private val _abhaCount = MutableLiveData(0)
    val abhaCount: LiveData<Int> get() = _abhaCount

    private var collectJobs = mutableListOf<Job>()

    init {
        loadDashboardData()
    }

    fun setTimePeriod(period: String) {
        _selectedTimePeriod.value = period
        loadDashboardData()
    }

    fun setVillage(villageName: String, villageId: Int) {
        _selectedVillageName.value = villageName
        selectedVillageId = villageId
        loadDashboardData()
    }

    fun clearVillageFilter() {
        _selectedVillageName.value = "All Villages"
        selectedVillageId = 0
        loadDashboardData()
    }

    private fun getAssignedVillageIds(): List<Int> =
        villageList.map { it.id }.ifEmpty { listOf(-1) }

    private fun getTimeRange(): Pair<Long, Long> {
        val period = _selectedTimePeriod.value ?: "Today"
        val cal = Calendar.getInstance()

        if (period == "Today") {
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis

            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            val end = cal.timeInMillis
            return Pair(start, end)
        }

        // Month filter
        val monthIndex = timePeriodOptions.indexOf(period) - 1 // 0=Jan, 11=Dec
        if (monthIndex < 0) return Pair(0L, 0L)

        cal.set(Calendar.MONTH, monthIndex)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis

        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis

        return Pair(start, end)
    }

    private fun loadDashboardData() {
        // Cancel previous collectors
        collectJobs.forEach { it.cancel() }
        collectJobs.clear()

        val (startTime, endTime) = getTimeRange()
        val village = selectedVillageId
        val assignedVillageIds = getAssignedVillageIds()

        _tbScreening.value = TbGenderBreakdown()
        _pastHistoryTb.value = TbPositiveNegativeBreakdown()
        _antiTbDrugs.value = TbPositiveNegativeBreakdown()
        _tbSuspected.value = TbGenderBreakdown()
        _tbConfirmed.value = TbGenderBreakdown()
        _digitalChestXray.value = TbGenderBreakdown()
        _sputumCollection.value = TbGenderBreakdown()
        _trueNat.value = TbGenderBreakdown()
        _liquidCulture.value = TbGenderBreakdown()
        _hwcReferral.value = TbGenderBreakdown()

        // TB Screening breakdown
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardTbScreeningCount(village, assignedVillageIds, startTime, endTime, "", 0,0).collect { total ->
                val current = _tbScreening.value ?: TbGenderBreakdown()
                _tbScreening.value = current.copy(total = total)
            }
        }
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardTbScreeningCount(village, assignedVillageIds, startTime, endTime, "MALE", 0,0).collect { male ->
                val current = _tbScreening.value ?: TbGenderBreakdown()
                _tbScreening.value = current.copy(male = male)
            }
        }
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardTbScreeningCount(village, assignedVillageIds, startTime, endTime, "FEMALE", 0,0).collect { female ->
                val current = _tbScreening.value ?: TbGenderBreakdown()
                _tbScreening.value = current.copy(female = female)
            }
        }
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardTbScreeningCount(village, assignedVillageIds, startTime, endTime, "", 1,0).collect { children ->
                val current = _tbScreening.value ?: TbGenderBreakdown()
                _tbScreening.value = current.copy(children = children)
            }
        }
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardTbScreeningCount(village, assignedVillageIds, startTime, endTime, "OTHERS", 0,0).collect { others ->
                val current = _tbScreening.value ?: TbGenderBreakdown()
                _tbScreening.value = current.copy(others = others)
            }
        }

        collectJobs += viewModelScope.launch {
            tbDao.getDashboardTbScreeningCount(village, assignedVillageIds, startTime, endTime, "", 0,1).collect { seniorCitizen ->
                val current = _tbScreening.value ?: TbGenderBreakdown()
                _tbScreening.value = current.copy(seniorCitizen = seniorCitizen)
            }
        }

        collectPositiveNegativeBreakdown(
            target = _pastHistoryTb,
            countQuery = { gender, isChild, positive ->
                tbDao.getDashboardPastHistoryTbCount(
                    village,
                    assignedVillageIds,
                    startTime,
                    endTime,
                    gender,
                    isChild,
                    positive
                )
            }
        )

        collectPositiveNegativeBreakdown(
            target = _antiTbDrugs,
            countQuery = { gender, isChild, positive ->
                tbDao.getDashboardAntiTbDrugsCount(
                    village,
                    assignedVillageIds,
                    startTime,
                    endTime,
                    gender,
                    isChild,
                    positive
                )
            }
        )

        // TB Suspected breakdown
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardTbSuspectedCount(village, assignedVillageIds, startTime, endTime, "", 0,0).collect { total ->
                val current = _tbSuspected.value ?: TbGenderBreakdown()
                _tbSuspected.value = current.copy(total = total)
            }
        }
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardTbSuspectedCount(village, assignedVillageIds, startTime, endTime, "MALE", 0,0).collect { male ->
                val current = _tbSuspected.value ?: TbGenderBreakdown()
                _tbSuspected.value = current.copy(male = male)
            }
        }
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardTbSuspectedCount(village, assignedVillageIds, startTime, endTime, "FEMALE", 0,0).collect { female ->
                val current = _tbSuspected.value ?: TbGenderBreakdown()
                _tbSuspected.value = current.copy(female = female)
            }
        }
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardTbSuspectedCount(village, assignedVillageIds, startTime, endTime, "", 1,0).collect { children ->
                val current = _tbSuspected.value ?: TbGenderBreakdown()
                _tbSuspected.value = current.copy(children = children)
            }
        }
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardTbSuspectedCount(village, assignedVillageIds, startTime, endTime, "OTHERS", 0,0).collect { others ->
                val current = _tbSuspected.value ?: TbGenderBreakdown()
                _tbSuspected.value = current.copy(others = others)
            }
        }

        collectJobs += viewModelScope.launch {
            tbDao.getDashboardTbSuspectedCount(village, assignedVillageIds, startTime, endTime, "", 0,1).collect { seniorCitizen ->
                val current = _tbSuspected.value ?: TbGenderBreakdown()
                _tbSuspected.value = current.copy(seniorCitizen = seniorCitizen)
            }
        }

        // TB Confirmed breakdown
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardTbConfirmedCount(village, assignedVillageIds, startTime, endTime, "", 0,0).collect { total ->
                val current = _tbConfirmed.value ?: TbGenderBreakdown()
                _tbConfirmed.value = current.copy(total = total)
            }
        }
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardTbConfirmedCount(village, assignedVillageIds, startTime, endTime, "MALE", 0,0).collect { male ->
                val current = _tbConfirmed.value ?: TbGenderBreakdown()
                _tbConfirmed.value = current.copy(male = male)
            }
        }
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardTbConfirmedCount(village, assignedVillageIds, startTime, endTime, "FEMALE", 0,0).collect { female ->
                val current = _tbConfirmed.value ?: TbGenderBreakdown()
                _tbConfirmed.value = current.copy(female = female)
            }
        }
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardTbConfirmedCount(village, assignedVillageIds, startTime, endTime, "", 1,0).collect { children ->
                val current = _tbConfirmed.value ?: TbGenderBreakdown()
                _tbConfirmed.value = current.copy(children = children)
            }
        }
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardTbConfirmedCount(village, assignedVillageIds, startTime, endTime, "OTHERS", 0,0).collect { others ->
                val current = _tbConfirmed.value ?: TbGenderBreakdown()
                _tbConfirmed.value = current.copy(others = others)
            }
        }

        collectJobs += viewModelScope.launch {
            tbDao.getDashboardTbConfirmedCount(village, assignedVillageIds, startTime, endTime, "", 0,1).collect { seniorCitizen ->
                val current = _tbConfirmed.value ?: TbGenderBreakdown()
                _tbConfirmed.value = current.copy(seniorCitizen = seniorCitizen)
            }
        }



        collectBreakdown(
            target = _digitalChestXray,
            totalQuery = { tbDao.getDashboardDigitalChestXRayCount(village, assignedVillageIds, startTime, endTime, "", 0,0) },
            maleQuery = { tbDao.getDashboardDigitalChestXRayCount(village, assignedVillageIds, startTime, endTime, "MALE", 0,0) },
            femaleQuery = { tbDao.getDashboardDigitalChestXRayCount(village, assignedVillageIds, startTime, endTime, "FEMALE", 0,0) },
            childrenQuery = { tbDao.getDashboardDigitalChestXRayCount(village, assignedVillageIds, startTime, endTime, "", 1,0) },
            othersQuery = { tbDao.getDashboardDigitalChestXRayCount(village, assignedVillageIds, startTime, endTime, "OTHERS", 0,0) },
            seniorCitizenQuery = { tbDao.getDashboardDigitalChestXRayCount(village, assignedVillageIds, startTime, endTime, "", 0,1) }

            )

        collectBreakdown(
            target = _sputumCollection,
            totalQuery = { tbDao.getDashboardSputumCollectionCount(village, assignedVillageIds, startTime, endTime, "", 0,0) },
            maleQuery = { tbDao.getDashboardSputumCollectionCount(village, assignedVillageIds, startTime, endTime, "MALE", 0,0) },
            femaleQuery = { tbDao.getDashboardSputumCollectionCount(village, assignedVillageIds, startTime, endTime, "FEMALE", 0,0) },
            childrenQuery = { tbDao.getDashboardSputumCollectionCount(village, assignedVillageIds, startTime, endTime, "", 1,0) },
            othersQuery = { tbDao.getDashboardSputumCollectionCount(village, assignedVillageIds, startTime, endTime, "OTHERS", 0,0) },
            seniorCitizenQuery = { tbDao.getDashboardSputumCollectionCount(village, assignedVillageIds, startTime, endTime, "", 0,1) }

            )

        collectBreakdown(
            target = _trueNat,
            totalQuery = { tbDao.getDashboardTrueNatCount(village, assignedVillageIds, startTime, endTime, "", 0,0) },
            maleQuery = { tbDao.getDashboardTrueNatCount(village, assignedVillageIds, startTime, endTime, "MALE", 0,0) },
            femaleQuery = { tbDao.getDashboardTrueNatCount(village, assignedVillageIds, startTime, endTime, "FEMALE", 0,0) },
            childrenQuery = { tbDao.getDashboardTrueNatCount(village, assignedVillageIds, startTime, endTime, "", 1,0) },
            othersQuery = { tbDao.getDashboardTrueNatCount(village, assignedVillageIds, startTime, endTime, "OTHERS", 0,0) },
            seniorCitizenQuery = { tbDao.getDashboardTrueNatCount(village, assignedVillageIds, startTime, endTime, "", 0,1) }

            )

        collectBreakdown(
            target = _liquidCulture,
            totalQuery = { tbDao.getDashboardLiquidCultureCount(village, assignedVillageIds, startTime, endTime, "", 0,0) },
            maleQuery = { tbDao.getDashboardLiquidCultureCount(village, assignedVillageIds, startTime, endTime, "MALE", 0,0) },
            femaleQuery = { tbDao.getDashboardLiquidCultureCount(village, assignedVillageIds, startTime, endTime, "FEMALE", 0,0) },
            childrenQuery = { tbDao.getDashboardLiquidCultureCount(village, assignedVillageIds, startTime, endTime, "", 1,0) },
            othersQuery = { tbDao.getDashboardLiquidCultureCount(village, assignedVillageIds, startTime, endTime, "OTHERS", 0,0) },
            seniorCitizenQuery = { tbDao.getDashboardLiquidCultureCount(village, assignedVillageIds, startTime, endTime, "", 0,1) },

            )

        collectBreakdown(
            target = _hwcReferral,
            totalQuery = { tbDao.getDashboardHwcReferralCount(village, assignedVillageIds, startTime, endTime, "", 0,0) },
            maleQuery = { tbDao.getDashboardHwcReferralCount(village, assignedVillageIds, startTime, endTime, "MALE", 0,0) },
            femaleQuery = { tbDao.getDashboardHwcReferralCount(village, assignedVillageIds, startTime, endTime, "FEMALE", 0,0) },
            childrenQuery = { tbDao.getDashboardHwcReferralCount(village, assignedVillageIds, startTime, endTime, "", 1,0) },
            othersQuery = { tbDao.getDashboardHwcReferralCount(village, assignedVillageIds, startTime, endTime, "OTHERS", 0,0) },
            seniorCitizenQuery = { tbDao.getDashboardHwcReferralCount(village, assignedVillageIds, startTime, endTime, "", 0,1) }

            )

        // NIKSHAY count
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardNikshayCount(village, assignedVillageIds, startTime, endTime).collect {
                _nikshayCount.value = it
            }
        }

        // ABHA count
        collectJobs += viewModelScope.launch {
            benDao.getDashboardAbhaCount(village, assignedVillageIds, startTime, endTime).collect {
                _abhaCount.value = it
            }
        }
    }

    private fun collectBreakdown(
        target: MutableLiveData<TbGenderBreakdown>,
        totalQuery: () -> Flow<Int>,
        maleQuery: () -> Flow<Int>,
        femaleQuery: () -> Flow<Int>,
        childrenQuery: () -> Flow<Int>,

        othersQuery: () -> Flow<Int>,
        seniorCitizenQuery: () -> Flow<Int>,
    ) {
        collectJobs += viewModelScope.launch {
            totalQuery().collect { total ->
                val current = target.value ?: TbGenderBreakdown()
                target.value = current.copy(total = total)
            }
        }
        collectJobs += viewModelScope.launch {
            maleQuery().collect { male ->
                val current = target.value ?: TbGenderBreakdown()
                target.value = current.copy(male = male)
            }
        }
        collectJobs += viewModelScope.launch {
            femaleQuery().collect { female ->
                val current = target.value ?: TbGenderBreakdown()
                target.value = current.copy(female = female)
            }
        }
        collectJobs += viewModelScope.launch {
            childrenQuery().collect { children ->
                val current = target.value ?: TbGenderBreakdown()
                target.value = current.copy(children = children)
            }
        }
        collectJobs += viewModelScope.launch {
            seniorCitizenQuery().collect { seniorCitizen ->
                val current = target.value ?: TbGenderBreakdown()
                target.value = current.copy(seniorCitizen = seniorCitizen)
            }
        }
        collectJobs += viewModelScope.launch {
            othersQuery().collect { others ->
                val current = target.value ?: TbGenderBreakdown()
                target.value = current.copy(others = others)
            }
        }
    }

    private fun collectPositiveNegativeBreakdown(
        target: MutableLiveData<TbPositiveNegativeBreakdown>,
        countQuery: (gender: String, isChild: Int, positive: Int) -> Flow<Int>,
    ) {
        collectPositiveNegativeGroup(target, PositiveNegativeGroup.TOTAL, "", 0, countQuery)
        collectPositiveNegativeGroup(target, PositiveNegativeGroup.MALE, "MALE", 0, countQuery)
        collectPositiveNegativeGroup(target, PositiveNegativeGroup.FEMALE, "FEMALE", 0, countQuery)
        collectPositiveNegativeGroup(target, PositiveNegativeGroup.CHILDREN, "", 1, countQuery)
        collectPositiveNegativeGroup(target, PositiveNegativeGroup.OTHERS, "OTHERS", 0, countQuery)
    }

    private fun collectPositiveNegativeGroup(
        target: MutableLiveData<TbPositiveNegativeBreakdown>,
        group: PositiveNegativeGroup,
        gender: String,
        isChild: Int,
        countQuery: (gender: String, isChild: Int, positive: Int) -> Flow<Int>,
    ) {
        collectJobs += viewModelScope.launch {
            countQuery(gender, isChild, 1).collect { count ->
                val current = target.value ?: TbPositiveNegativeBreakdown()
                target.value = current.updateGroup(group) { it.copy(positive = count) }
            }
        }
        collectJobs += viewModelScope.launch {
            countQuery(gender, isChild, 0).collect { count ->
                val current = target.value ?: TbPositiveNegativeBreakdown()
                target.value = current.updateGroup(group) { it.copy(negative = count) }
            }
        }
    }

    private fun TbPositiveNegativeBreakdown.updateGroup(
        group: PositiveNegativeGroup,
        update: (PositiveNegativeCount) -> PositiveNegativeCount,
    ): TbPositiveNegativeBreakdown =
        when (group) {
            PositiveNegativeGroup.TOTAL -> copy(total = update(total))
            PositiveNegativeGroup.MALE -> copy(male = update(male))
            PositiveNegativeGroup.FEMALE -> copy(female = update(female))
            PositiveNegativeGroup.CHILDREN -> copy(children = update(children))
            PositiveNegativeGroup.OTHERS -> copy(others = update(others))
        }

}
