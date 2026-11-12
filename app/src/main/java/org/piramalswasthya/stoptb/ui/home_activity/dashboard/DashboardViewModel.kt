package org.piramalswasthya.stoptb.ui.home_activity.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.database.room.dao.BenDao
import org.piramalswasthya.stoptb.database.room.dao.TBDao
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.helpers.Languages
import org.piramalswasthya.stoptb.model.DashboardBreakdownCounts
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
    val periodKey: String = DashboardViewModel.PERIOD_ALL,
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
        const val PERIOD_ALL = "all"
        const val PERIOD_MONTH_PREFIX = "month_"

        fun monthPeriodKey(month: Int): String = "$PERIOD_MONTH_PREFIX$month"
    }

    private val _filters = MutableLiveData(DashboardFilterState())
    val filters: LiveData<DashboardFilterState> get() = _filters

    val villageList: List<LocationEntity>
        get() = preferenceDao.getLoggedInUser()?.villages.orEmpty()

    fun villageDisplayName(village: LocationEntity): String {
        val villageName = village.name.substringBefore("(").trim()
        val localized = when (preferenceDao.getCurrentLanguage()) {
            Languages.HINDI -> village.nameHindi
            Languages.ASSAMESE -> village.nameAssamese
            else -> villageName
        }
        return localized?.takeIf { it.isNotBlank() } ?: villageName
    }

    fun selectedVillageName(): String? {
        val village = preferenceDao.getLocationRecord()?.village ?: return null
        return villageDisplayName(village)
    }

    val districtList: List<LocationEntity>
        get() = preferenceDao.getLoggedInUser()?.district?.let { listOf(it) }.orEmpty()

    val blockList: List<LocationEntity>
        get() = preferenceDao.getLoggedInUser()?.block?.let { listOf(it) }.orEmpty()

    val periodKeys: List<String> = listOf(
        PERIOD_ALL,
        PERIOD_TODAY,
        PERIOD_YESTERDAY,
        PERIOD_WEEK,
        PERIOD_MONTH,
    ) + (Calendar.JANUARY..Calendar.DECEMBER).map { monthPeriodKey(it) }

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

    /**
     * True only while the dashboard page is RESUMED (the visible ViewPager2 page).
     * ViewPager2 keeps the off-screen page STARTED, so LiveData observers alone would keep
     * ~70 Room COUNT queries re-running on every write while the user is elsewhere. When the
     * page is hidden the queries are cancelled; the last values stay on screen and are
     * refreshed as soon as the page becomes visible again.
     */
    private val dashboardVisible = MutableStateFlow(false)

    fun setDashboardVisible(visible: Boolean) {
        dashboardVisible.value = visible
    }

    init {
        loadDashboardData()
    }

    fun applyFilters(state: DashboardFilterState) {
        _filters.value = state
        loadDashboardData()
    }

    private fun getAssignedVillageIds(villages: List<LocationEntity>): List<Int> =
        villages.flatMap { idsForVillage(it) }.distinct().ifEmpty { listOf(-1) }

    /** Same village ids and living-beneficiary rules as the home Total Population card. */
    private fun populationVillageIds(
        villages: List<LocationEntity>,
        selectedVillageId: Int,
        selectedVillage: LocationEntity?
    ): List<Int> {
        val ids = if (selectedVillageId == 0 || selectedVillage == null) {
            villages.map { it.id }.filter { it != 0 }.distinct()
        } else {
            listOf(selectedVillage.id)
        }.ifEmpty { listOf(-1) }
        return if (ids.size == 1) listOf(ids.first(), ids.first()) else ids
    }

    private fun idsForVillageFilter(
        villages: List<LocationEntity>,
        selectedVillageId: Int,
        selectedVillage: LocationEntity?
    ): List<Int> {
        val ids = if (selectedVillageId == 0 || selectedVillage == null) {
            getAssignedVillageIds(villages)
        } else {
            idsForVillage(selectedVillage)
        }
        val base = ids.ifEmpty { listOf(-1) }
        return if (base.size == 1) listOf(base.first(), base.first()) else base
    }

    private fun idsForVillage(village: LocationEntity): List<Int> {
        val parsed = Regex("\\((\\d+)\\)").find(village.name)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return listOfNotNull(village.id.takeIf { it != 0 }, parsed).distinct().ifEmpty { listOf(village.id) }
    }

    private fun getTimeRange(): Pair<Long, Long> {
        val period = _filters.value?.periodKey ?: PERIOD_ALL
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

        val range = when (period) {
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
                if (cal.timeInMillis > System.currentTimeMillis()) {
                    cal.add(Calendar.WEEK_OF_YEAR, -1)
                }
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
            else -> {
                if (!period.startsWith(PERIOD_MONTH_PREFIX)) return Pair(0L, 0L)
                val month = period.removePrefix(PERIOD_MONTH_PREFIX).toIntOrNull()
                if (month == null || month !in Calendar.JANUARY..Calendar.DECEMBER) {
                    Pair(0L, 0L)
                } else {
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    cal.set(Calendar.MONTH, month)
                    val start = startOfDay(cal)
                    cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                    Pair(start, endOfDay(cal))
                }
            }
        }
        return range
    }



    private fun loadDashboardData() {
        // Cancel previous collectors
        collectJobs.forEach { it.cancel() }
        collectJobs.clear()

        val (startTime, endTime) = getTimeRange()
        val selectedVillageId = _filters.value?.villageId ?: 0
        // villageList decrypts and parses the logged-in user from prefs on every access: read once.
        val villages = villageList
        val selectedVillage = villages.firstOrNull { it.id == selectedVillageId }
        val villageName = if (selectedVillageId == 0) {
            ""
        } else {
            selectedVillage?.name?.substringBefore("(")?.trim().orEmpty()
        }
        val assignedVillageIds = idsForVillageFilter(villages, selectedVillageId, selectedVillage)

        // Keep previous values on screen until new filtered results arrive (avoid flashing/sticking at 0).

        collectSection(_tbScreening) { tbDao.getDashboardTbScreeningBreakdown(assignedVillageIds, villageName, startTime, endTime) }

        collectSection(_pastHistoryTb) { tbDao.getDashboardPastHistoryTbBreakdown(assignedVillageIds, villageName, startTime, endTime) }

        collectSection(_antiTbDrugs) { tbDao.getDashboardAntiTbDrugsBreakdown(assignedVillageIds, villageName, startTime, endTime) }

        collectSection(_presumptiveTb) { tbDao.getDashboardPresumptiveTbBreakdown(assignedVillageIds, villageName, startTime, endTime) }

        collectBreakdown(
            target = _tbConfirmed,
            totalQuery = { benDao.getDashboardFilteredTbConfirmedCount(assignedVillageIds, startTime, endTime, "", 0, 0) },
            maleQuery = { benDao.getDashboardFilteredTbConfirmedCount(assignedVillageIds, startTime, endTime, "MALE", 0, 0) },
            femaleQuery = { benDao.getDashboardFilteredTbConfirmedCount(assignedVillageIds, startTime, endTime, "FEMALE", 0, 0) },
            childrenQuery = { benDao.getDashboardFilteredTbConfirmedCount(assignedVillageIds, startTime, endTime, "", 1, 0) },
            othersQuery = { benDao.getDashboardFilteredTbConfirmedCount(assignedVillageIds, startTime, endTime, "OTHERS", 0, 0) },
            seniorCitizenQuery = { benDao.getDashboardFilteredTbConfirmedCount(assignedVillageIds, startTime, endTime, "", 0, 1) },
        )

        collectSection(_digitalChestXray) { tbDao.getDashboardDigitalChestXRayBreakdown(assignedVillageIds, villageName, startTime, endTime) }

        collectSection(_sputumCollection) { tbDao.getDashboardSputumCollectionBreakdown(assignedVillageIds, villageName, startTime, endTime) }

        collectSection(_trueNat) { tbDao.getDashboardTrueNatBreakdown(assignedVillageIds, villageName, startTime, endTime) }

        collectSection(_liquidCulture) { tbDao.getDashboardLiquidCultureBreakdown(assignedVillageIds, villageName, startTime, endTime) }

        collectSection(_hwcReferral) { tbDao.getDashboardHwcReferralBreakdown(assignedVillageIds, villageName, startTime, endTime) }

        val populationIds = populationVillageIds(villages, selectedVillageId, selectedVillage)
        collectJobs += viewModelScope.launch {
            whileVisible { benDao.getVillageHeadcount(populationIds) }
                .collect { headcount ->
                    _coverage.value = CoverageStats(
                        population = headcount.population,
                        screened = (headcount.population - headcount.unscreened).coerceAtLeast(0),
                        unscreened = headcount.unscreened,
                    )
                }
        }

        collectSection(_nikshayCount) { tbDao.getDashboardNikshayBreakdown(assignedVillageIds, villageName, startTime, endTime) }

        collectBreakdown(
            target = _abhaCount,
            totalQuery = { benDao.getDashboardAbhaCount(assignedVillageIds, startTime, endTime, "", 0, 0) },
            maleQuery = { benDao.getDashboardAbhaCount(assignedVillageIds, startTime, endTime, "MALE", 0, 0) },
            femaleQuery = { benDao.getDashboardAbhaCount(assignedVillageIds, startTime, endTime, "FEMALE", 0, 0) },
            childrenQuery = { benDao.getDashboardAbhaCount(assignedVillageIds, startTime, endTime, "", 1, 0) },
            othersQuery = { benDao.getDashboardAbhaCount(assignedVillageIds, startTime, endTime, "OTHERS", 0, 0) },
            seniorCitizenQuery = { benDao.getDashboardAbhaCount(assignedVillageIds, startTime, endTime, "", 0, 1) },
        )
    }

    private fun zeroCountFlow(): Flow<Int> = flow {
        emit(0)
        awaitCancellation()
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
            whileVisible {
                combine(
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
                }
            }.distinctUntilChanged().collect { breakdown ->
                target.value = breakdown
            }
        }
    }

    /**
     * One query returns all six numbers of a section (TbDashboardBreakdownQueries), instead of
     * six COUNT queries that each re-ran on every write.
     */
    private fun collectSection(target: MutableLiveData<TbGenderBreakdown>, query: () -> Flow<DashboardBreakdownCounts>) {
        collectJobs += viewModelScope.launch {
            whileVisible(query).distinctUntilChanged().collect { counts ->
                target.value = TbGenderBreakdown(
                    total = counts.total,
                    male = counts.male,
                    female = counts.female,
                    children = counts.children,
                    others = counts.others,
                    seniorCitizen = counts.seniorCitizen
                )
            }
        }
    }

    /** Runs the Room queries built by [queries] only while the dashboard is visible. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> whileVisible(queries: () -> Flow<T>): Flow<T> =
        dashboardVisible.flatMapLatest { visible -> if (visible) queries() else emptyFlow() }

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
