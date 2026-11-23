package org.piramalswasthya.stoptb.ui.home_activity.household_members

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.piramalswasthya.stoptb.model.BenBasicDomain
import org.piramalswasthya.stoptb.repositories.BenRepo
import org.piramalswasthya.stoptb.repositories.HouseholdRepo
import org.piramalswasthya.stoptb.repositories.RowBadgeRepo
import org.piramalswasthya.stoptb.ui.LoadedRowBadges
import org.piramalswasthya.stoptb.repositories.contactTracing.IContactTracingRepository
import javax.inject.Inject

@HiltViewModel
class HouseholdMembersViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val benRepo: BenRepo,
    private val householdRepo: HouseholdRepo,
    private val contactTracingRepo: IContactTracingRepository,
    rowBadgeRepo: RowBadgeRepo
) : ViewModel() {

    val hhId: Long = HouseholdMembersFragmentArgs.fromSavedStateHandle(savedStateHandle).hhId

    val benList = benRepo.getBenBasicListFromHousehold(hhId).map { list ->
        list.sortedWith(
            compareBy<BenBasicDomain> { it.isDeactivate }
                .thenBy { it.benName }
                .thenBy { it.benId }
        )
    }

    var totalHhMembers: Int? = null
        private set

    val memberLimitReached: Flow<Boolean> = benList.map { list ->
        if (totalHhMembers == null) totalHhMembers = householdRepo.getTotalHhMembers(hhId)
        val total = totalHhMembers
        total != null && list.size >= total
    }

    // ── Examine form fill status ──────────────────────────────────────────────
    // PR 6 (6.4): for this household's members only (reported by the fragment), not for every
    // row of each table. See RowBadgeRepo.
    private val rowBadges = LoadedRowBadges(viewModelScope, rowBadgeRepo)

    fun onRowsLoaded(benIds: Set<Long>) = rowBadges.onRowsLoaded(benIds)

    val vitalBenIds: Flow<List<Long>>          = rowBadges.vitalBenIds
    val unsyncedVitalBenIds: Flow<List<Long>>  = rowBadges.unsyncedVitalBenIds
    val syncingVitalBenIds: Flow<List<Long>>   = rowBadges.syncingVitalBenIds
    val tbScreeningBenIds: Flow<List<Long>>    = rowBadges.tbScreeningBenIds
    val allTbDiagnostics = rowBadges.tbDiagnostics
    val unsyncedTbScreeningBenIds: Flow<List<Long>> = rowBadges.unsyncedTbScreeningBenIds
    val syncingTbScreeningBenIds: Flow<List<Long>> = rowBadges.syncingTbScreeningBenIds
    val generalOpdBenIds: Flow<List<Long>>     = rowBadges.generalOpdBenIds
    val unsyncedGeneralOpdBenIds: Flow<List<Long>> = rowBadges.unsyncedGeneralOpdBenIds
    val syncingGeneralOpdBenIds: Flow<List<Long>> = rowBadges.syncingGeneralOpdBenIds
    val anthropometryBenIds: Flow<List<Long>>  = rowBadges.anthropometryBenIds

    /** Diagnosis = TB_DIAGNOSTICS (new) OR TB_SUSPECTED (legacy) */
    val diagnosisBenIds: Flow<List<Long>> = rowBadges.diagnosisBenIds

    val contactFollowUpDoneBenIds: Flow<List<Long>> = contactTracingRepo.observeContactFollowUpDoneBenIds()
    val tptFollowUpDoneBenIds: Flow<List<Long>> = contactTracingRepo.observeTptFollowUpTargetReachedBenIds()
    val tptEligibleBenIds: Flow<List<Long>> = contactTracingRepo.observeTptEligibleBenIds()
    val tptRegistrationDoneBenIds: Flow<List<Long>> = contactTracingRepo.observeTptRegistrationDoneBenIds()

    suspend fun getBenRegId(benId: Long): Long {
        return withContext(Dispatchers.IO) {
            benRepo.getBenFromId(benId)?.benRegId ?: 0L
        }
    }
}
