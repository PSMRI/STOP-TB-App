package org.piramalswasthya.stoptb.ui.home_activity.household_members

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.piramalswasthya.stoptb.model.BenBasicDomain
import org.piramalswasthya.stoptb.repositories.BenRepo
import org.piramalswasthya.stoptb.repositories.RecordsRepo
import org.piramalswasthya.stoptb.repositories.TBRepo
import org.piramalswasthya.stoptb.repositories.VitalRepo
import javax.inject.Inject

@HiltViewModel
class HouseholdMembersViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val benRepo: BenRepo,
    private val vitalRepo: VitalRepo,
    private val tbRepo: TBRepo,
    private val recordsRepo: RecordsRepo
) : ViewModel() {

    val hhId: Long = HouseholdMembersFragmentArgs.fromSavedStateHandle(savedStateHandle).hhId

    val benList = benRepo.getBenBasicListFromHousehold(hhId).map { list ->
        list.sortedWith(
            compareBy<BenBasicDomain> { it.isDeactivate }
                .thenBy { it.benName }
                .thenBy { it.benId }
        )
    }

    // ── Examine form fill status ──────────────────────────────────────────────
    val vitalBenIds: Flow<List<Long>>          = vitalRepo.vitalBenIds
    val tbScreeningBenIds: Flow<List<Long>>    = tbRepo.tbScreeningBenIds
    val generalOpdBenIds: Flow<List<Long>>     = tbRepo.generalOpdBenIds
    val anthropometryBenIds: Flow<List<Long>>  = recordsRepo.anthropometryFilledBenIds

    /** Diagnosis = TB_DIAGNOSTICS (new) OR TB_SUSPECTED (legacy) */
    val diagnosisBenIds: Flow<List<Long>> = combine(
        tbRepo.tbDiagnosticsBenIds,
        tbRepo.tbSuspectedBenIds
    ) { diagnostics, suspected -> (diagnostics + suspected).distinct() }

    suspend fun getBenRegId(benId: Long): Long {
        return withContext(Dispatchers.IO) {
            benRepo.getBenFromId(benId)?.benRegId ?: 0L
        }
    }
}
