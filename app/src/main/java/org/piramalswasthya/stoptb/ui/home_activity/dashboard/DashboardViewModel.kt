package org.piramalswasthya.stoptb.ui.home_activity.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
    val others: Int = 0
)

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

    private val _tbSuspected = MutableLiveData(TbGenderBreakdown())
    val tbSuspected: LiveData<TbGenderBreakdown> get() = _tbSuspected

    private val _tbConfirmed = MutableLiveData(TbGenderBreakdown())
    val tbConfirmed: LiveData<TbGenderBreakdown> get() = _tbConfirmed

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

        _tbScreening.value = TbGenderBreakdown()
        _tbSuspected.value = TbGenderBreakdown()
        _tbConfirmed.value = TbGenderBreakdown()

        // TB Screening breakdown
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardTbScreeningCount(village, startTime, endTime, "", 0).collect { total ->
                val current = _tbScreening.value ?: TbGenderBreakdown()
                _tbScreening.value = current.copy(total = total)
            }
        }
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardTbScreeningCount(village, startTime, endTime, "MALE", 0).collect { male ->
                val current = _tbScreening.value ?: TbGenderBreakdown()
                _tbScreening.value = current.copy(male = male)
            }
        }
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardTbScreeningCount(village, startTime, endTime, "FEMALE", 0).collect { female ->
                val current = _tbScreening.value ?: TbGenderBreakdown()
                _tbScreening.value = current.copy(female = female)
            }
        }
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardTbScreeningCount(village, startTime, endTime, "", 1).collect { children ->
                val current = _tbScreening.value ?: TbGenderBreakdown()
                _tbScreening.value = current.copy(children = children)
            }
        }
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardTbScreeningCount(village, startTime, endTime, "TRANSGENDER", 0).collect { others ->
                val current = _tbScreening.value ?: TbGenderBreakdown()
                _tbScreening.value = current.copy(others = others)
            }
        }

        // TB Suspected breakdown
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardTbSuspectedCount(village, startTime, endTime, "", 0).collect { total ->
                val current = _tbSuspected.value ?: TbGenderBreakdown()
                _tbSuspected.value = current.copy(total = total)
            }
        }
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardTbSuspectedCount(village, startTime, endTime, "MALE", 0).collect { male ->
                val current = _tbSuspected.value ?: TbGenderBreakdown()
                _tbSuspected.value = current.copy(male = male)
            }
        }
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardTbSuspectedCount(village, startTime, endTime, "FEMALE", 0).collect { female ->
                val current = _tbSuspected.value ?: TbGenderBreakdown()
                _tbSuspected.value = current.copy(female = female)
            }
        }
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardTbSuspectedCount(village, startTime, endTime, "", 1).collect { children ->
                val current = _tbSuspected.value ?: TbGenderBreakdown()
                _tbSuspected.value = current.copy(children = children)
            }
        }
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardTbSuspectedCount(village, startTime, endTime, "TRANSGENDER", 0).collect { others ->
                val current = _tbSuspected.value ?: TbGenderBreakdown()
                _tbSuspected.value = current.copy(others = others)
            }
        }

        // TB Confirmed breakdown
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardTbConfirmedCount(village, startTime, endTime, "", 0).collect { total ->
                val current = _tbConfirmed.value ?: TbGenderBreakdown()
                _tbConfirmed.value = current.copy(total = total)
            }
        }
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardTbConfirmedCount(village, startTime, endTime, "MALE", 0).collect { male ->
                val current = _tbConfirmed.value ?: TbGenderBreakdown()
                _tbConfirmed.value = current.copy(male = male)
            }
        }
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardTbConfirmedCount(village, startTime, endTime, "FEMALE", 0).collect { female ->
                val current = _tbConfirmed.value ?: TbGenderBreakdown()
                _tbConfirmed.value = current.copy(female = female)
            }
        }
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardTbConfirmedCount(village, startTime, endTime, "", 1).collect { children ->
                val current = _tbConfirmed.value ?: TbGenderBreakdown()
                _tbConfirmed.value = current.copy(children = children)
            }
        }
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardTbConfirmedCount(village, startTime, endTime, "TRANSGENDER", 0).collect { others ->
                val current = _tbConfirmed.value ?: TbGenderBreakdown()
                _tbConfirmed.value = current.copy(others = others)
            }
        }

        // NIKSHAY count
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardNikshayCount(village, startTime, endTime).collect {
                _nikshayCount.value = it
            }
        }

        // ABHA count
        collectJobs += viewModelScope.launch {
            benDao.getDashboardAbhaCount(village, startTime, endTime).collect {
                _abhaCount.value = it
            }
        }
    }

}
