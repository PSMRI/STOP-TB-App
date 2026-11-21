package org.piramalswasthya.stoptb.repositories

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.map
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.piramalswasthya.stoptb.model.BenBasicCache
import org.piramalswasthya.stoptb.database.room.BenSearchSql
import org.piramalswasthya.stoptb.database.room.InAppDb
import org.piramalswasthya.stoptb.database.room.dao.BenDao
import org.piramalswasthya.stoptb.database.room.paging.NoCountPagingSource
import org.piramalswasthya.stoptb.database.room.dao.DataMissingBenInfoDao
import org.piramalswasthya.stoptb.database.room.dao.HouseholdDao
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.model.BenBasicDomain
import org.piramalswasthya.stoptb.utils.HelperUtil
import kotlinx.coroutines.flow.distinctUntilChanged
import timber.log.Timber
import javax.inject.Inject

@ActivityRetainedScoped
class RecordsRepo @Inject constructor(
    @ApplicationContext private val context: Context,
    private val householdDao: HouseholdDao,
    private val benDao: BenDao,
    private val missingBenInfoDao: DataMissingBenInfoDao,
    private val  preferenceDao: PreferenceDao,
    private val database: InAppDb,
    private val villageCountsRepo: VillageCountsRepo,
) {
//    private val selectedVillage = preferenceDao.getLocationRecord()?.village?.id ?: 0
    private val selectedVillage get() = preferenceDao.getLocationRecord()?.village?.id ?: 0

    private val allVillageIds: List<Int>
        get() = preferenceDao.getLoggedInUser()?.villages
            ?.map { it.id }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(selectedVillage)

    init {
        Timber.d("RecordsRepo INIT: selectedVillage = $selectedVillage, locationRecord = ${preferenceDao.getLocationRecord()}")
    }

    private val localizedResources = HelperUtil.getLocalizedResources(context, preferenceDao.getCurrentLanguage())

    val hhList get() = householdDao.getAllHouseholdWithNumMembers(selectedVillage)
        .map { list -> list.map { it.asBasicDomainModel() } }
    val hhListCount get() = householdDao.getAllHouseholdsCount(selectedVillage)
    val doorLockList get() = householdDao.getDoorLockHouseholds(selectedVillage)
        .map { list -> list.map { it.asBasicDomainModel() } }
    val doorLockListCount get() = householdDao.getDoorLockHouseholdsCount(selectedVillage)

    // PR 6: All Household paged, searched and sorted in SQL. doorLocked: the door-locked list.
    fun householdsPaged(query: String, doorLocked: Boolean = false) =
        Pager(tbListPagingConfig) { noCountSource("HOUSEHOLD", "BENEFICIARY") { limit, offset -> householdDao.getHouseholdsWithNumMembersPage(selectedVillage, doorLocked, query, limit, offset) } }
            .flow.map { page -> page.map { row -> row.asBasicDomainModel() } }

    val hhListforAsha get() = householdDao.getAllHouseholdForAshaFamilyMembers(selectedVillage)
        .map { list -> list.map { it.asBasicDomainModel() } }

    val allBenList get() = benDao.getAllBen(selectedVillage)
        .map { list -> list.map { it.asBasicDomainModel() } }

    val childCountsByBen: Flow<Map<Long, Int>> get() =
        benDao.getChildCountsForAllBen(selectedVillage)
            .map { list -> list.associate { it.benId to it.childCount } }

    val householdMemberCounts: Flow<Map<Long, Int>> get() =
        benDao.getHouseholdMemberCounts(selectedVillage)
            .map { list -> list.associate { it.hhId to it.memberCount } }

    val householdHeadNames: Flow<Map<Long, String>> get() =
        benDao.getHouseholdHeadNames(selectedVillage)
            .map { list -> list.associate { it.hhId to it.fullName } }

    // For the rows a list has loaded (LoadedRowBadges ids): same values, without reading the
    // whole village.
    fun childCountsFor(benIds: Set<Long>): Flow<Map<Long, Int>> =
        benDao.getChildCountsForBens(selectedVillage, benIds.toList())
            .map { list -> list.associate { it.benId to it.childCount } }

    fun householdMemberCountsFor(benIds: Set<Long>): Flow<Map<Long, Int>> =
        benDao.getHouseholdMemberCountsForBens(selectedVillage, benIds.toList())
            .map { list -> list.associate { it.hhId to it.memberCount } }

    fun householdHeadNamesFor(benIds: Set<Long>): Flow<Map<Long, String>> =
        benDao.getHouseholdHeadNamesForBens(selectedVillage, benIds.toList())
            .map { list -> list.associate { it.hhId to it.fullName } }

    fun isAnthropometryFilled(benId: Long): Flow<Boolean> =
        benDao.isAnthropometryFilled(benId).distinctUntilChanged()

    /** Sources whose list touches no TB table: 0 All, 1 ABHA, 2 RCH, 3 age 30+, 4 eligible couple. */
    private val sourcesWithoutTbPredicates = setOf(0, 1, 2, 3, 4)

    /**
     * One page of the beneficiary list for [source] (BenSearchQueries has a query per list).
     * vulnerabilityCode only applies to source 11 (Vulnerable Population): "" = All, "TRIBAL",
     * or a risk-factor code. A search that has no words to look for lists everyone.
     */
    private suspend fun benListPage(
        source: Int, villageIds: List<Int>, filterType: Int, query: String, vulnerabilityCode: String, limit: Int, offset: Int
    ): List<BenBasicCache> {
        val fts = BenSearchSql.toFtsQuery(query)
        val q = if (fts.isEmpty()) "" else query
        return when (source) {
            1 -> benDao.abhaBenPage(villageIds, filterType, q, fts, limit, offset)
            2 -> benDao.rchBenPage(villageIds, filterType, q, fts, limit, offset)
            3 -> benDao.age30PlusPage(villageIds, filterType, q, fts, limit, offset)
            4 -> benDao.eligibleCouplePage(villageIds, filterType, q, fts, limit, offset)
            5 -> benDao.hwcReferralPage(villageIds, filterType, q, fts, limit, offset)
            6 -> benDao.xrayReferralPage(villageIds, filterType, q, fts, limit, offset)
            7 -> benDao.trueNatReferralPage(villageIds, filterType, q, fts, limit, offset)
            8 -> benDao.liquidCultureReferralPage(villageIds, filterType, q, fts, limit, offset)
            9 -> benDao.tptPage(villageIds, filterType, q, fts, limit, offset)
            10 -> benDao.clinicalAssessmentPage(villageIds, filterType, q, fts, limit, offset)
            11 -> benDao.vulnerablePopulationPage(villageIds, filterType, q, fts, vulnerabilityCode, limit, offset)
            else -> benDao.allBenPage(villageIds, filterType, q, fts, limit, offset)
        }
    }

    fun searchBenPagedSource(
        query: String,
        filterType: Int,
        source: Int,
        expandToBlock: Boolean = false,
        vulnerabilityCode: String = ""
    ): PagingSource<Int, BenBasicCache> {
        val villageIds = if (expandToBlock) allVillageIds else listOf(selectedVillage)
        // Lists without a TB rule don't name a TB table, so TB writes during sync don't reload them.
        val tables = if (source in sourcesWithoutTbPredicates) {
            arrayOf(BEN_VIEW, BenSearchSql.TABLE)
        } else {
            arrayOf(BEN_VIEW, BenSearchSql.TABLE, "BENEFICIARY", "BEN_VITALS", "TB_SUSPECTED", "TB_DIAGNOSTICS", "TB_SCREENING", "TB_BEN_SUMMARY")
        }
        return noCountSource(*tables) { limit, offset ->
            benListPage(source, villageIds, filterType, query, vulnerabilityCode, limit, offset)
        }
    }

    /** The whole list for [source] (CSV export), read through the same query and order as the screen. */
    suspend fun searchBenOnce(query: String, filterType: Int, source: Int, expandToBlock: Boolean = false, vulnerabilityCode: String = ""): List<BenBasicDomain> {
        val villageIds = if (expandToBlock) allVillageIds else listOf(selectedVillage)
        val rows = mutableListOf<BenBasicCache>()
        while (true) {
            val page = benListPage(source, villageIds, filterType, query, vulnerabilityCode, CSV_PAGE, rows.size)
            rows += page
            if (page.size < CSV_PAGE) break
        }
        return rows.map { it.asBasicDomainModel() }
    }

    // Home cards: one shared, throttled query for the village (VillageCountsRepo).
    // Tuberculosis / Referrals cards: one shared, cached query per screen (VillageCountsRepo).
    private fun tbCard(pick: (org.piramalswasthya.stoptb.database.room.dao.TbCardCounts) -> Int) =
        villageCountsRepo.tbCards(selectedVillage).map(pick).distinctUntilChanged()

    private fun referralCard(pick: (org.piramalswasthya.stoptb.database.room.dao.ReferralCardCounts) -> Int) =
        villageCountsRepo.referralCards(selectedVillage).map(pick).distinctUntilChanged()

    val allBenListCount get() = villageCountsRepo.counts(selectedVillage).map { it.allBen }.distinctUntilChanged()
    val allMissingDataBenListCount get() = missingBenInfoDao.getDataCount(selectedVillage)
    val nonHHListCount get() = villageCountsRepo.counts(selectedVillage).map { it.nonHH }.distinctUntilChanged()
    val nonHHList get() = benDao.getNonHHBeneficiaries(selectedVillage)
        .map { list -> list.map { it.asBasicDomainModel() } }



    val unscreenedListCount get() = benDao.getUnscreenedCount(selectedVillage)
    // PR 6: paged, searched in SQL, TB-screened beneficiaries excluded in SQL.
    fun unscreenedPaged(query: String) =
        Pager(tbListPagingConfig) { noCountSource(BEN_VIEW, "TB_SCREENING") { limit, offset -> benDao.getUnscreenedPage(selectedVillage, query, limit, offset) } }
            .flow.map { page -> page.map { row -> row.asBasicDomainModel() } }
    val unscreenedNotTbScreenedCount get() = benDao.getUnscreenedNotTbScreenedCount(selectedVillage)



    fun searchNonHH(query: String) = benDao.searchNonHHBeneficiaries(selectedVillage, query)
        .map { list -> list.map { it.asBasicDomainModel() } }

    fun searchNonHHPagedSource(query: String): PagingSource<Int, BenBasicCache> =
        noCountSource(BEN_VIEW) { limit, offset -> benDao.searchNonHHBeneficiariesPage(selectedVillage, query, limit, offset) }

    val allBenWithoutAbhaList get() = benDao.getAllBenWithoutAbha(selectedVillage)
        .map { list -> list.map { it.asBasicDomainModel() } }
    val allBenWithAbhaList get() = benDao.getAllBenWithAbha(selectedVillage)
        .map { list -> list.map { it.asBasicDomainModel() } }

    val benWithAbhaListCount get() = benDao.getAllBenWithAbhaCount(selectedVillage)
    val benWithOldAbhaListCount get() = benDao.getAllBenWithOldAbhaCount(selectedVillage)
    val benWithNewAbhaListCount get() = benDao.getAllBenWithNewAbhaCount(selectedVillage)

    val allBenWithRchList get() = benDao.getAllBenWithRch(selectedVillage)
        .map { list -> list.map { it.asBasicDomainModel() } }
    val benWithRchListCount get() = benDao.getAllBenWithRchCount(selectedVillage)

    val allBenAboveThirtyList get() = benDao.getAllBenAboveThirty(selectedVillage)
        .map { list -> list.map { it.asBasicDomainModel() } }
    val allBenWARAList get() = benDao.getAllBenWARA(selectedVillage)
        .map { list -> list.map { it.asBasicDomainModel() } }

    fun getBenList() = benDao.getAllBen(selectedVillage)
        .map { list -> list.map { it.asBasicDomainModel() } }

    fun getBenListCHO() = benDao.getAllBenGender(selectedVillage, "FEMALE")
        .map { list -> list.map { it.asBasicDomainModelCHO() } }

    fun getBenListCount() = benDao.getAllBenGenderCount(selectedVillage, "FEMALE")

    val ncdList get() = allBenList
    val ncdListCount get() = allBenListCount

    val getNcdEligibleList get() = benDao.getBenWithCbac(selectedVillage)
    val getNcdrefferedList get() = benDao.getBenWithReferredCbac(selectedVillage)
    val getHwcRefferedList get() = benDao.getReferredHWCBenList(selectedVillage)

    val getNcdEligibleListCount get() = benDao.getBenWithCbacCount(selectedVillage)
    val getNcdrefferedListCount get() = benDao.getReferredBenCount(selectedVillage)
    val getHwcReferedListCount get() = benDao.getReferredHWCBenCount(selectedVillage)
    val digitalChestXrayReferralCount get() = referralCard { it.xray }
    val trueNatReferralCount get() = referralCard { it.trueNat }
    val liquidCultureReferralCount get() = referralCard { it.liquidCulture }
    val hwcReferralCount get() = referralCard { it.hwc }
    val clinicalAssessmentReferralCount get() = referralCard { it.clinical }

    val getNcdPriorityList get() = getNcdEligibleList.map {
        it.filter { it.savedCbacRecords.isNotEmpty() && it.savedCbacRecords.maxBy { it.createdDate }.total_score > 4 }
    }
    val getNcdPriorityListCount get() = getNcdPriorityList.map { it.count() }

    val getNcdNonEligibleList get() = getNcdEligibleList.map {
        it.filter { it.savedCbacRecords.isNotEmpty() && it.savedCbacRecords.maxBy { it.createdDate }.total_score <= 4 }
    }
    val getNcdNonEligibleListCount get() = getNcdNonEligibleList.map { it.count() }

    fun malariaScreeningList(hhId: Long) = benDao.getAllMalariaScreeningBen(selectedVillage, hhId = hhId)
        .map { list -> list.map { it.asMalariaScreeningDomainModel() } }

    fun aesScreeningList(hhId: Long) = benDao.getAllAESScreeningBen(selectedVillage, hhId = hhId)
        .map { list -> list.map { it.asAESScreeningDomainModel() } }


    fun KalazarScreeningList(hhId: Long) = benDao.getAllKALAZARScreeningBen(selectedVillage, hhId = hhId)
        .map { list -> list.map { it.asKALAZARScreeningDomainModel() } }

    fun LeprosyScreeningList(hhId: Long) = benDao.getAllLeprosyScreeningBen(selectedVillage, hhId = hhId)
        .map { list -> list.map { it.asLeprosyScreeningDomainModel() } }

    fun LeprosySuspectedList() = benDao.getLeprosyScreeningBenBySymptoms(selectedVillage, 0)
        .map { list -> list.map { it.asLeprosyScreeningDomainModel() } }

    fun LeprosyConfirmedList() = benDao.getConfirmedLeprosyCases(selectedVillage = selectedVillage)
        .map { list -> list.map { it.asLeprosyScreeningDomainModel() } }

    fun filariaScreeningList(hhId: Long) = benDao.getAllFilariaScreeningBen(selectedVillage, hhId = hhId)
        .map { list -> list.map { it.asFilariaScreeningDomainModel() } }

    // PR 6: the TB lists page from the database and search in SQL; they used to load (and
    // filter) the whole village with every row's @Relation children on each emission.
    fun tbScreeningPaged(query: String) =
        Pager(tbListPagingConfig) { noCountSource(BEN_VIEW, "TB_SCREENING") { limit, offset -> benDao.getTbScreeningBenPage(selectedVillage, query, limit, offset) } }
            .flow.map { page -> page.map { row -> row.asTbScreeningDomainModel() } }
    val tbScreeningListCount get() = tbCard { it.screened }

    fun tbPresumptivePaged(query: String) =
        Pager(tbListPagingConfig) { noCountSource(*TB_CASE_LIST_TABLES, "TB_SCREENING", "TB_BEN_SUMMARY") { limit, offset -> benDao.getTbPresumptivePage(selectedVillage, query, limit, offset) } }
            .flow.map { page -> page.map { row -> row.asTbSuspectedDomainModel() } }
    // Counted in SQL: was the whole Presumptive list (with @Relation children) loaded just for .size.
    val tbSuspectedListCount get() = tbCard { it.presumptive }

    fun tbConfirmedPaged(query: String) =
        Pager(tbListPagingConfig) { noCountSource(*TB_CASE_LIST_TABLES) { limit, offset -> benDao.getTbConfirmedPage(selectedVillage, query, limit, offset) } }
            .flow.map { page -> page.map { row -> row.asTbSuspectedDomainModel() } }
    // Counted in SQL: was the whole Confirmed list loaded just for .size.
    val tbConfirmedListCount get() = tbCard { it.confirmed }

    val leprosySuspectedListCount get() = benDao.getLeprosyScreeningBenCountBySymptoms(selectedVillage, 0)
    val leprosyConfirmedCasesListCount get() = benDao.getConfirmedLeprosyCaseCount(selectedVillage = selectedVillage)


    val benWithAbhaCount get() = benWithAbhaListCount

    suspend fun getBenById(benId: Long): BenBasicDomain? {
        return benDao.getBenById(benId)?.asBasicDomainModel()
    }

    /**
     * A page-at-a-time source over [loadRange] that never counts the whole result (see
     * [NoCountPagingSource]). [tables] are the tables/views the query reads - the ones Room listed
     * for the former @Query PagingSource - so the list still refreshes when any of them changes.
     */
    private fun <T : Any> noCountSource(vararg tables: String, loadRange: suspend (limit: Int, offset: Int) -> List<T>) =
        NoCountPagingSource(database, arrayOf(*tables), loadRange)

    companion object {
        /**
         * Bounded memory for long lists: ~10 pages kept, older ones dropped while scrolling.
         * Placeholders off: the sources do not count the result, so the list grows as it scrolls.
         */
        val tbListPagingConfig = PagingConfig(pageSize = 30, prefetchDistance = 10, initialLoadSize = 60, maxSize = 300, enablePlaceholders = false)

        private const val BEN_VIEW = "BEN_BASIC_CACHE"
        /** Read by the Presumptive / Confirmed rows and their @Relation children. */
        private const val CSV_PAGE = 1_000
        private val TB_CASE_LIST_TABLES = arrayOf(BEN_VIEW, "TB_SUSPECTED", "TB_DIAGNOSTICS", "TB_CONFIRMED_TREATMENT", "COUNSELLING_FORM_RESPONSE")
    }
}
