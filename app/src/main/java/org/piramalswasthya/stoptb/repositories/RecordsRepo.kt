package org.piramalswasthya.stoptb.repositories

import androidx.paging.PagingSource
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.piramalswasthya.stoptb.model.BenBasicCache
import org.piramalswasthya.stoptb.database.room.dao.BenDao
import org.piramalswasthya.stoptb.database.room.dao.HouseholdDao
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.model.BenBasicDomain
import org.piramalswasthya.stoptb.utils.HelperUtil
import javax.inject.Inject

@ActivityRetainedScoped
class RecordsRepo @Inject constructor(
    @ApplicationContext private val context: Context,
    private val householdDao: HouseholdDao,
    private val benDao: BenDao,
    private val  preferenceDao: PreferenceDao
) {
//    private val selectedVillage = preferenceDao.getLocationRecord()?.village?.id ?: 0
    private val selectedVillage get() = preferenceDao.getLocationRecord()?.village?.id ?: 0

    private val localizedResources = HelperUtil.getLocalizedResources(context, preferenceDao.getCurrentLanguage())

    val hhList = householdDao.getAllHouseholdWithNumMembers(selectedVillage)
        .map { list -> list.map { it.asBasicDomainModel() } }
    val hhListCount = householdDao.getAllHouseholdsCount(selectedVillage)

    val hhListforAsha = householdDao.getAllHouseholdForAshaFamilyMembers(selectedVillage)
        .map { list -> list.map { it.asBasicDomainModel() } }

    val allBenList = benDao.getAllBen(selectedVillage)
        .map { list -> list.map { it.asBasicDomainModel() } }

    val childCountsByBen: Flow<Map<Long, Int>> =
        benDao.getChildCountsForAllBen(selectedVillage)
            .map { list -> list.associate { it.benId to it.childCount } }

    fun searchBen(query: String, filterType: Int, source: Int): Flow<List<BenBasicDomain>> =
        benDao.searchBen(selectedVillage, source, filterType, query)
            .map { list -> list.map { it.asBasicDomainModel() } }

    fun searchBenPagedSource(query: String, filterType: Int, source: Int): PagingSource<Int, BenBasicCache> =
        benDao.searchBenPaged(selectedVillage, source, filterType, query)

    suspend fun searchBenOnce(query: String, filterType: Int, source: Int): List<BenBasicDomain> =
        benDao.searchBenOnce(selectedVillage, source, filterType, query)
            .map { it.asBasicDomainModel() }

    val allBenListCount = benDao.getAllBenCount(selectedVillage)
    val allBenWithoutAbhaList = benDao.getAllBenWithoutAbha(selectedVillage)
        .map { list -> list.map { it.asBasicDomainModel() } }
    val allBenWithAbhaList = benDao.getAllBenWithAbha(selectedVillage)
        .map { list -> list.map { it.asBasicDomainModel() } }

    val benWithAbhaListCount = benDao.getAllBenWithAbhaCount(selectedVillage)
    val benWithOldAbhaListCount = benDao.getAllBenWithOldAbhaCount(selectedVillage)
    val benWithNewAbhaListCount = benDao.getAllBenWithNewAbhaCount(selectedVillage)

    val allBenWithRchList = benDao.getAllBenWithRch(selectedVillage)
        .map { list -> list.map { it.asBasicDomainModel() } }
    val benWithRchListCount = benDao.getAllBenWithRchCount(selectedVillage)

    val allBenAboveThirtyList = benDao.getAllBenAboveThirty(selectedVillage)
        .map { list -> list.map { it.asBasicDomainModel() } }
    val allBenWARAList = benDao.getAllBenWARA(selectedVillage)
        .map { list -> list.map { it.asBasicDomainModel() } }

    fun getBenList() = benDao.getAllBen(selectedVillage)
        .map { list -> list.map { it.asBasicDomainModel() } }

    fun getBenListCHO() = benDao.getAllBenGender(selectedVillage, "FEMALE")
        .map { list -> list.map { it.asBasicDomainModelCHO() } }

    fun getBenListCount() = benDao.getAllBenGenderCount(selectedVillage, "FEMALE")

    val ncdList = allBenList
    val ncdListCount = allBenListCount

    val getNcdEligibleList = benDao.getBenWithCbac(selectedVillage)
    val getNcdrefferedList = benDao.getBenWithReferredCbac(selectedVillage)
    val getHwcRefferedList = benDao.getReferredHWCBenList(selectedVillage)

    val getNcdEligibleListCount = benDao.getBenWithCbacCount(selectedVillage)
    val getNcdrefferedListCount = benDao.getReferredBenCount(selectedVillage)
    val getHwcReferedListCount = benDao.getReferredHWCBenCount(selectedVillage)

    val getNcdPriorityList = getNcdEligibleList.map {
        it.filter { it.savedCbacRecords.isNotEmpty() && it.savedCbacRecords.maxBy { it.createdDate }.total_score > 4 }
    }
    val getNcdPriorityListCount = getNcdPriorityList.map { it.count() }

    val getNcdNonEligibleList = getNcdEligibleList.map {
        it.filter { it.savedCbacRecords.isNotEmpty() && it.savedCbacRecords.maxBy { it.createdDate }.total_score <= 4 }
    }
    val getNcdNonEligibleListCount = getNcdNonEligibleList.map { it.count() }

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

    val tbScreeningList = benDao.getAllTbScreeningBen(selectedVillage)
        .map { list -> list.map { it.asTbScreeningDomainModel() } }
    val tbScreeningListCount = tbScreeningList.map { it.size }

    val tbSuspectedList = benDao.getTbScreeningList(selectedVillage)
        .map { list -> list.map { it.asTbSuspectedDomainModel() } }
    val tbSuspectedListCount = tbSuspectedList.map { it.size }

    val tbConfirmedList = benDao.getTbConfirmedList(selectedVillage)
        .map { list -> list.map { it.asTbSuspectedDomainModel() } }
    val tbConfirmedListCount = tbConfirmedList.map { it.size }

    val leprosySuspectedListCount = benDao.getLeprosyScreeningBenCountBySymptoms(selectedVillage, 0)
    val leprosyConfirmedCasesListCount = benDao.getConfirmedLeprosyCaseCount(selectedVillage = selectedVillage)


    val benWithAbhaCount = benWithAbhaListCount

    suspend fun getBenById(benId: Long): BenBasicDomain? {
        return benDao.getBenById(benId)?.asBasicDomainModel()
    }
}