package org.piramalswasthya.stoptb.ui.home_activity.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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

data class CoverageStats(
    val population: Int = 0,
    val screened: Int = 0,
    val unscreened: Int = 0,
) {
    val coveragePercent: Int
        get() = if (population <= 0) 0 else ((screened * 100f) / population).toInt()

    val unscreenedPercent: Int
        get() = if (population <= 0) 0 else ((unscreened * 100f) / population).toInt()
}

data class DashboardFilterState(
    val districtId: Int = 0,
    val blockId: Int = 0,
    val villageId: Int = 0,
    val periodKey: String = DashboardViewModel.PERIOD_MONTH,
)

// data class PositiveNegativeCount(
//     val positive: Int = 0,
//     val negative: Int = 0,
// ) {
//     val total: Int get() = positive + negative
// }

// data class TbPositiveNegativeBreakdown(
//     val total: PositiveNegativeCount = PositiveNegativeCount(),
//     val male: PositiveNegativeCount = PositiveNegativeCount(),
//     val female: PositiveNegativeCount = PositiveNegativeCount(),
//     val children: PositiveNegativeCount = PositiveNegativeCount(),
//     val others: PositiveNegativeCount = PositiveNegativeCount(),
// )

// private enum class PositiveNegativeGroup {
//     TOTAL,
//     MALE,
//     FEMALE,
//     CHILDREN,
//     OTHERS
// }

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val benDao: BenDao,
    private val tbDao: TBDao,
    private val preferenceDao: PreferenceDao,
) : ViewModel() {

    companion object {
        const val PERIOD_TODAY = "today"
        const val PERIOD_YESTERDAY = "yesterday"
        const val PERIOD_WEEK = "week"
        const val PERIOD_MONTH = "month"
        const val PERIOD_YEAR = "year"
        const val PERIOD_ALL = "all"
    }

    private val _unscreened = MutableLiveData(TbGenderBreakdown())
    val unscreened: LiveData<TbGenderBreakdown> get() = _unscreened

    private val _filters = MutableLiveData(DashboardFilterState())
    val filters: LiveData<DashboardFilterState> get() = _filters

    val villageList: List<LocationEntity>
        get() = preferenceDao.getLoggedInUser()?.villages.orEmpty()

    val districtList: List<LocationEntity>
        get() = preferenceDao.getLoggedInUser()?.district?.let { listOf(it) }.orEmpty()

    val blockList: List<LocationEntity>
        get() = preferenceDao.getLoggedInUser()?.block?.let { listOf(it) }.orEmpty()

    val periodKeys = listOf(
        PERIOD_TODAY,
        PERIOD_YESTERDAY,
        PERIOD_WEEK,
        PERIOD_MONTH,
        PERIOD_YEAR,
        PERIOD_ALL
    )

    // Dashboard data
    private val _tbScreening = MutableLiveData(TbGenderBreakdown())
    val tbScreening: LiveData<TbGenderBreakdown> get() = _tbScreening

    private val _presumptiveTb = MutableLiveData(TbGenderBreakdown())
    val presumptiveTb: LiveData<TbGenderBreakdown> get() = _presumptiveTb

    private val _pastHistoryTb = MutableLiveData(TbGenderBreakdown())
    val pastHistoryTb: LiveData<TbGenderBreakdown> get() = _pastHistoryTb

    private val _antiTbDrugs = MutableLiveData(TbGenderBreakdown())
    val antiTbDrugs: LiveData<TbGenderBreakdown> get() = _antiTbDrugs

    // private val _tbSuspected = MutableLiveData(TbGenderBreakdown())
    // val tbSuspected: LiveData<TbGenderBreakdown> get() = _tbSuspected

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

    private val _nikshayCount = MutableLiveData(TbGenderBreakdown())
    val nikshayCount: LiveData<TbGenderBreakdown> get() = _nikshayCount

    private val _abhaCount = MutableLiveData(TbGenderBreakdown())
    val abhaCount: LiveData<TbGenderBreakdown> get() = _abhaCount

    private val _coverage = MutableLiveData(CoverageStats())
    val coverage: LiveData<CoverageStats> get() = _coverage

    private var collectJobs = mutableListOf<Job>()

    init {
        loadDashboardData()
    }

    fun applyFilters(state: DashboardFilterState) {
        _filters.value = state
        loadDashboardData()
    }

    private fun getAssignedVillageIds(): List<Int> =
        villageList.map { it.id }.ifEmpty { listOf(-1) }

    private fun getTimeRange(): Pair<Long, Long> {
        val period = _filters.value?.periodKey ?: PERIOD_MONTH
        val cal = Calendar.getInstance()

        fun startOfDay(calendar: Calendar): Long {
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            return calendar.timeInMillis
        }

        fun endOfDay(calendar: Calendar): Long {
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            return calendar.timeInMillis
        }

        return when (period) {
            PERIOD_TODAY -> {
                val start = startOfDay(cal)
                val end = endOfDay(cal)
                Pair(start, end)
            }
            PERIOD_YESTERDAY -> {
                cal.add(Calendar.DAY_OF_MONTH, -1)
                val start = startOfDay(cal)
                val end = endOfDay(cal)
                Pair(start, end)
            }
            PERIOD_WEEK -> {
                cal.firstDayOfWeek = Calendar.MONDAY
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                val start = startOfDay(cal)
                val endCal = Calendar.getInstance()
                Pair(start, endOfDay(endCal))
            }
            PERIOD_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val start = startOfDay(cal)
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                Pair(start, endOfDay(cal))
            }
            PERIOD_YEAR -> {
                cal.set(Calendar.MONTH, Calendar.JANUARY)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val start = startOfDay(cal)
                cal.set(Calendar.MONTH, Calendar.DECEMBER)
                cal.set(Calendar.DAY_OF_MONTH, 31)
                Pair(start, endOfDay(cal))
            }
            else -> Pair(0L, 0L)
        }
    }



    private fun loadDashboardData() {
        // Cancel previous collectors
        collectJobs.forEach { it.cancel() }
        collectJobs.clear()

        val (startTime, endTime) = getTimeRange()
        val village = _filters.value?.villageId ?: 0
        val assignedVillageIds = getAssignedVillageIds()

        _tbScreening.value = TbGenderBreakdown()
        _presumptiveTb.value = TbGenderBreakdown()
        _pastHistoryTb.value = TbGenderBreakdown()
        _antiTbDrugs.value = TbGenderBreakdown()
        // _tbSuspected.value = TbGenderBreakdown()
        _tbConfirmed.value = TbGenderBreakdown()
        _digitalChestXray.value = TbGenderBreakdown()
        _sputumCollection.value = TbGenderBreakdown()
        _trueNat.value = TbGenderBreakdown()
        _liquidCulture.value = TbGenderBreakdown()
        _hwcReferral.value = TbGenderBreakdown()
        _nikshayCount.value = TbGenderBreakdown()
        _abhaCount.value = TbGenderBreakdown()
        _coverage.value = CoverageStats()

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

        collectBreakdown(
            target = _pastHistoryTb,
            totalQuery = { tbDao.getDashboardPastHistoryTbCount(village, assignedVillageIds, startTime, endTime, "", 0, 0) },
            maleQuery = { tbDao.getDashboardPastHistoryTbCount(village, assignedVillageIds, startTime, endTime, "MALE", 0, 0) },
            femaleQuery = { tbDao.getDashboardPastHistoryTbCount(village, assignedVillageIds, startTime, endTime, "FEMALE", 0, 0) },
            childrenQuery = { tbDao.getDashboardPastHistoryTbCount(village, assignedVillageIds, startTime, endTime, "", 1, 0) },
            othersQuery = { tbDao.getDashboardPastHistoryTbCount(village, assignedVillageIds, startTime, endTime, "OTHERS", 0, 0) },
            seniorCitizenQuery = { tbDao.getDashboardPastHistoryTbCount(village, assignedVillageIds, startTime, endTime, "", 0, 1) }
        )

        collectBreakdown(
            target = _antiTbDrugs,
            totalQuery = { tbDao.getDashboardAntiTbDrugsCount(village, assignedVillageIds, startTime, endTime, "", 0, 0) },
            maleQuery = { tbDao.getDashboardAntiTbDrugsCount(village, assignedVillageIds, startTime, endTime, "MALE", 0, 0) },
            femaleQuery = { tbDao.getDashboardAntiTbDrugsCount(village, assignedVillageIds, startTime, endTime, "FEMALE", 0, 0) },
            childrenQuery = { tbDao.getDashboardAntiTbDrugsCount(village, assignedVillageIds, startTime, endTime, "", 1, 0) },
            othersQuery = { tbDao.getDashboardAntiTbDrugsCount(village, assignedVillageIds, startTime, endTime, "OTHERS", 0, 0) },
            seniorCitizenQuery = { tbDao.getDashboardAntiTbDrugsCount(village, assignedVillageIds, startTime, endTime, "", 0, 1) }
        )


        // Presumptive TB breakdown
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardPresumptiveTbCount(village, assignedVillageIds, startTime, endTime, "", 0).collect { total ->
                val current = _presumptiveTb.value ?: TbGenderBreakdown()
                _presumptiveTb.value = current.copy(total = total)
            }
        }
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardPresumptiveTbCount(village, assignedVillageIds, startTime, endTime, "MALE", 0).collect { male ->
                val current = _presumptiveTb.value ?: TbGenderBreakdown()
                _presumptiveTb.value = current.copy(male = male)
            }
        }
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardPresumptiveTbCount(village, assignedVillageIds, startTime, endTime, "FEMALE", 0).collect { female ->
                val current = _presumptiveTb.value ?: TbGenderBreakdown()
                _presumptiveTb.value = current.copy(female = female)
            }
        }
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardPresumptiveTbCount(village, assignedVillageIds, startTime, endTime, "", 1).collect { children ->
                val current = _presumptiveTb.value ?: TbGenderBreakdown()
                _presumptiveTb.value = current.copy(children = children)
            }
        }
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardPresumptiveTbCount(village, assignedVillageIds, startTime, endTime, "OTHERS", 0).collect { others ->
                val current = _presumptiveTb.value ?: TbGenderBreakdown()
                _presumptiveTb.value = current.copy(others = others)
            }
        }

        // Unscreened breakdown
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardUnscreenedCount(village, assignedVillageIds, startTime, endTime, "", 0).collect { total ->
                val current = _unscreened.value ?: TbGenderBreakdown()
                _unscreened.value = current.copy(total = total)
            }
        }
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardUnscreenedCount(village, assignedVillageIds, startTime, endTime, "MALE", 0).collect { male ->
                val current = _unscreened.value ?: TbGenderBreakdown()
                _unscreened.value = current.copy(male = male)
            }
        }
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardUnscreenedCount(village, assignedVillageIds, startTime, endTime, "FEMALE", 0).collect { female ->
                val current = _unscreened.value ?: TbGenderBreakdown()
                _unscreened.value = current.copy(female = female)
            }
        }
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardUnscreenedCount(village, assignedVillageIds, startTime, endTime, "", 1).collect { children ->
                val current = _unscreened.value ?: TbGenderBreakdown()
                _unscreened.value = current.copy(children = children)
            }
        }
        collectJobs += viewModelScope.launch {
            tbDao.getDashboardUnscreenedCount(village, assignedVillageIds, startTime, endTime, "OTHERS", 0).collect { others ->
                val current = _unscreened.value ?: TbGenderBreakdown()
                _unscreened.value = current.copy(others = others)
            }
        }

        // TB Suspected breakdown commented out
        /*
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
        */

        // TB Confirmed breakdown
        collectJobs += viewModelScope.launch {
            benDao.getDashboardFilteredTbConfirmedCount(village, assignedVillageIds, startTime, endTime, "", 0,0).collect { total ->
                val current = _tbConfirmed.value ?: TbGenderBreakdown()
                _tbConfirmed.value = current.copy(total = total)
            }
        }
        collectJobs += viewModelScope.launch {
            benDao.getDashboardFilteredTbConfirmedCount(village, assignedVillageIds, startTime, endTime, "MALE", 0,0).collect { male ->
                val current = _tbConfirmed.value ?: TbGenderBreakdown()
                _tbConfirmed.value = current.copy(male = male)
            }
        }
        collectJobs += viewModelScope.launch {
            benDao.getDashboardFilteredTbConfirmedCount(village, assignedVillageIds, startTime, endTime, "FEMALE", 0,0).collect { female ->
                val current = _tbConfirmed.value ?: TbGenderBreakdown()
                _tbConfirmed.value = current.copy(female = female)
            }
        }
        collectJobs += viewModelScope.launch {
            benDao.getDashboardFilteredTbConfirmedCount(village, assignedVillageIds, startTime, endTime, "", 1,0).collect { children ->
                val current = _tbConfirmed.value ?: TbGenderBreakdown()
                _tbConfirmed.value = current.copy(children = children)
            }
        }
        collectJobs += viewModelScope.launch {
            benDao.getDashboardFilteredTbConfirmedCount(village, assignedVillageIds, startTime, endTime, "OTHERS", 0,0).collect { others ->
                val current = _tbConfirmed.value ?: TbGenderBreakdown()
                _tbConfirmed.value = current.copy(others = others)
            }
        }

        collectJobs += viewModelScope.launch {
            benDao.getDashboardFilteredTbConfirmedCount(village, assignedVillageIds, startTime, endTime, "", 0,1).collect { seniorCitizen ->
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

        collectJobs += viewModelScope.launch {
            combine(
                tbDao.getDashboardTbScreeningCount(village, assignedVillageIds, 0, 0, "", 0, 0),
                tbDao.getDashboardUnscreenedCount(village, assignedVillageIds, 0, 0, "", 0)
            ) { screened, unscreened ->
                CoverageStats(
                    population = screened + unscreened,
                    screened = screened,
                    unscreened = unscreened
                )
            }.collect { _coverage.value = it }
        }

        collectBreakdown(
            target = _nikshayCount,
            totalQuery = { tbDao.getDashboardNikshayCount(village, assignedVillageIds, startTime, endTime, "", 0, 0) },
            maleQuery = { tbDao.getDashboardNikshayCount(village, assignedVillageIds, startTime, endTime, "MALE", 0, 0) },
            femaleQuery = { tbDao.getDashboardNikshayCount(village, assignedVillageIds, startTime, endTime, "FEMALE", 0, 0) },
            childrenQuery = { tbDao.getDashboardNikshayCount(village, assignedVillageIds, startTime, endTime, "", 1, 0) },
            othersQuery = { tbDao.getDashboardNikshayCount(village, assignedVillageIds, startTime, endTime, "OTHERS", 0, 0) },
            seniorCitizenQuery = { tbDao.getDashboardNikshayCount(village, assignedVillageIds, startTime, endTime, "", 0, 1) },
        )

        collectBreakdown(
            target = _abhaCount,
            totalQuery = { benDao.getDashboardAbhaCount(village, assignedVillageIds, startTime, endTime, "", 0, 0) },
            maleQuery = { benDao.getDashboardAbhaCount(village, assignedVillageIds, startTime, endTime, "MALE", 0, 0) },
            femaleQuery = { benDao.getDashboardAbhaCount(village, assignedVillageIds, startTime, endTime, "FEMALE", 0, 0) },
            childrenQuery = { benDao.getDashboardAbhaCount(village, assignedVillageIds, startTime, endTime, "", 1, 0) },
            othersQuery = { benDao.getDashboardAbhaCount(village, assignedVillageIds, startTime, endTime, "OTHERS", 0, 0) },
            seniorCitizenQuery = { benDao.getDashboardAbhaCount(village, assignedVillageIds, startTime, endTime, "", 0, 1) },
        )
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
            kotlinx.coroutines.flow.combine(
                totalQuery(),
                maleQuery(),
                femaleQuery(),
                childrenQuery(),
                othersQuery(),
                seniorCitizenQuery()
            ) { values ->
                TbGenderBreakdown(
                    total = values[0],
                    male = values[1],
                    female = values[2],
                    children = values[3],
                    others = values[4],
                    seniorCitizen = values[5]
                )
            }.collect { breakdown ->
                target.value = breakdown
            }
        }
    }

    // private fun collectPositiveNegativeBreakdown(
    //     target: MutableLiveData<TbPositiveNegativeBreakdown>,
    //     countQuery: (gender: String, isChild: Int, positive: Int) -> Flow<Int>,
    // ) {
    //     collectPositiveNegativeGroup(target, PositiveNegativeGroup.TOTAL, "", 0, countQuery)
    //     collectPositiveNegativeGroup(target, PositiveNegativeGroup.MALE, "MALE", 0, countQuery)
    //     collectPositiveNegativeGroup(target, PositiveNegativeGroup.FEMALE, "FEMALE", 0, countQuery)
    //     collectPositiveNegativeGroup(target, PositiveNegativeGroup.CHILDREN, "", 1, countQuery)
    //     collectPositiveNegativeGroup(target, PositiveNegativeGroup.OTHERS, "OTHERS", 0, countQuery)
    // }

    // private fun collectPositiveNegativeGroup(
    //     target: MutableLiveData<TbPositiveNegativeBreakdown>,
    //     group: PositiveNegativeGroup,
    //     gender: String,
    //     isChild: Int,
    //     countQuery: (gender: String, isChild: Int, positive: Int) -> Flow<Int>,
    // ) {
    //     collectJobs += viewModelScope.launch {
    //         countQuery(gender, isChild, 1).collect { count ->
    //             val current = target.value ?: TbPositiveNegativeBreakdown()
    //             target.value = current.updateGroup(group) { it.copy(positive = count) }
    //         }
    //     }
    //     collectJobs += viewModelScope.launch {
    //         countQuery(gender, isChild, 0).collect { count ->
    //             val current = target.value ?: TbPositiveNegativeBreakdown()
    //             target.value = current.updateGroup(group) { it.copy(negative = count) }
    //         }
    //     }
    // }

    // private fun TbPositiveNegativeBreakdown.updateGroup(
    //     group: PositiveNegativeGroup,
    //     update: (PositiveNegativeCount) -> PositiveNegativeCount,
    // ): TbPositiveNegativeBreakdown =
    //     when (group) {
    //         PositiveNegativeGroup.TOTAL -> copy(total = update(total))
    //         PositiveNegativeGroup.MALE -> copy(male = update(male))
    //         PositiveNegativeGroup.FEMALE -> copy(female = update(female))
    //         PositiveNegativeGroup.CHILDREN -> copy(children = update(children))
    //         PositiveNegativeGroup.OTHERS -> copy(others = update(others))
    //     }

}
