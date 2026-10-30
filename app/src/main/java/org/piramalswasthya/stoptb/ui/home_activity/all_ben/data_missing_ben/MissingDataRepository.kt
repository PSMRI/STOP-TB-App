package org.piramalswasthya.stoptb.ui.home_activity.all_ben.data_missing_ben

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.piramalswasthya.stoptb.database.room.InAppDb
import org.piramalswasthya.stoptb.database.room.dao.DataMissingBenInfoDao
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.model.TBDiagnosticsCache
import org.piramalswasthya.stoptb.model.TBScreeningCache
import javax.inject.Inject

class MissingDataRepository @Inject constructor(
    private val missingBenInfoDao: DataMissingBenInfoDao,
    private val  preferenceDao: PreferenceDao,
    private val db: InAppDb
) {

    private val selectedVillage get() = preferenceDao.getLocationRecord()?.village?.id ?: 0

    private val allVillageIds: List<Int>
        get() = preferenceDao.getLoggedInUser()?.villages
            ?.map { it.id }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(selectedVillage)

    val tbScreeningBenIds: Flow<List<Long>> = db.tbDao.getAllTbScreeningBenIds()
    val allTbDiagnostics: Flow<List<TBDiagnosticsCache>> = db.tbDao.getAllTbDiagnostics()
    val allTbScreening: Flow<List<TBScreeningCache>> = db.tbDao.getAllTbScreening()
    val householdMemberCounts: Flow<Map<Long, Int>> get() =
        db.benDao.getHouseholdMemberCounts(selectedVillage)
            .map { list -> list.associate { it.hhId to it.memberCount } }
    val householdHeadNames: Flow<Map<Long, String>> get() =
        db.benDao.getHouseholdHeadNames(selectedVillage)
            .map { list -> list.associate { it.hhId to it.fullName } }

    fun getMissingBeneficiaries(
        searchQuery: String,
        expandToAllVillages: Boolean = false
    ): Flow<PagingData<MissingDataBenItem>> {

        return Pager(
            config = PagingConfig(
                pageSize = 30,
                prefetchDistance = 10,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                missingBenInfoDao.getMissingBeneficiaries(
                    searchQuery,
                    if (expandToAllVillages) allVillageIds else listOf(selectedVillage)
                )
            }
        )
            .flow
            .map { pagingData ->
                pagingData.map { it.toDomain() }
            }
    }
}
