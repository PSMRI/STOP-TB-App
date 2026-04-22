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
import timber.log.Timber
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

    init {
        Timber.d("RecordsRepo INIT: selectedVillage = $selectedVillage, locationRecord = ${preferenceDao.getLocationRecord()}")
    }

    private val localizedResources = HelperUtil.getLocalizedResources(context, preferenceDao.getCurrentLanguage())

    val hhList get() = householdDao.getAllHouseholdWithNumMembers(selectedVillage)
        .map { list -> list.map { it.asBasicDomainModel() } }
    val hhListCount get() = householdDao.getAllHouseholdsCount(selectedVillage)

    val hhListforAsha get() = householdDao.getAllHouseholdForAshaFamilyMembers(selectedVillage)
        .map { list -> list.map { it.asBasicDomainModel() } }

    val allBenList get() = benDao.getAllBen(selectedVillage)
        .map { list -> list.map { it.asBasicDomainModel() } }

    val childCountsByBen: Flow<Map<Long, Int>> get() =
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

    val allBenListCount get() = benDao.getAllBenCount(selectedVillage)
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
    val digitalChestXrayReferralCount get() = benDao.getDigitalChestXRayBenCount(selectedVillage)
    val trueNatReferralCount get() = benDao.getTrueNatBenCount(selectedVillage)
    val liquidCultureReferralCount get() = benDao.getLiquidCultureBenCount(selectedVillage)
    val hwcReferralCount get() = benDao.getHwcBenDataCount(selectedVillage)

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

    val tbScreeningList get() = benDao.getPendingTbScreeningBen(selectedVillage)
        .map { list -> list.map { it.asTbScreeningDomainModel() } }
    val tbScreeningListCount get() = benDao.getPendingTbScreeningCount(selectedVillage)

    val tbSuspectedList get() = benDao.getTbScreeningList(selectedVillage)
        .map { list -> list.map { it.asTbSuspectedDomainModel() } }
    val tbSuspectedListCount get() = tbSuspectedList.map { it.size }

    val tbConfirmedList get() = benDao.getTbConfirmedList(selectedVillage)
        .map { list -> list.map { it.asTbSuspectedDomainModel() } }
    val tbConfirmedListCount get() = tbConfirmedList.map { it.size }

    val leprosySuspectedListCount get() = benDao.getLeprosyScreeningBenCountBySymptoms(selectedVillage, 0)
    val leprosyConfirmedCasesListCount get() = benDao.getConfirmedLeprosyCaseCount(selectedVillage = selectedVillage)


    val benWithAbhaCount get() = benWithAbhaListCount

    suspend fun getBenById(benId: Long): BenBasicDomain? {
        return benDao.getBenById(benId)?.asBasicDomainModel()
    }
}
