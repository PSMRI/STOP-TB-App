package org.piramalswasthya.stoptb.ui.home_activity.household_members

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import org.piramalswasthya.stoptb.model.BenBasicDomain
import org.piramalswasthya.stoptb.repositories.BenRepo
import javax.inject.Inject

@HiltViewModel
class HouseholdMembersViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    benRepo: BenRepo
) : ViewModel() {

    val hhId: Long = HouseholdMembersFragmentArgs.fromSavedStateHandle(savedStateHandle).hhId

    val benList = benRepo.getBenBasicListFromHousehold(hhId).map { list ->
        list.sortedWith(
            compareBy<BenBasicDomain> { it.isDeactivate }
                .thenBy { it.benName }
                .thenBy { it.benId }
        )
    }
}
