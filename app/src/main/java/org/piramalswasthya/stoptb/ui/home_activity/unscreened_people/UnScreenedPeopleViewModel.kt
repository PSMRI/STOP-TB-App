    package org.piramalswasthya.stoptb.ui.home_activity.unscreened_people

    import androidx.lifecycle.ViewModel
    import androidx.lifecycle.viewModelScope
    import dagger.hilt.android.lifecycle.HiltViewModel
    import kotlinx.coroutines.ExperimentalCoroutinesApi
    import kotlinx.coroutines.flow.MutableStateFlow
    import kotlinx.coroutines.flow.SharingStarted
    import kotlinx.coroutines.flow.StateFlow
    import kotlinx.coroutines.flow.combine
    import kotlinx.coroutines.flow.stateIn
    import kotlinx.coroutines.launch
    import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
    import org.piramalswasthya.stoptb.model.BenBasicDomain
    import org.piramalswasthya.stoptb.model.BenRegCache
    import org.piramalswasthya.stoptb.repositories.BenRepo
    import org.piramalswasthya.stoptb.repositories.RecordsRepo
    import org.piramalswasthya.stoptb.repositories.TBRepo
    import org.piramalswasthya.stoptb.repositories.VitalRepo
    import javax.inject.Inject

    @OptIn(ExperimentalCoroutinesApi::class)
    @HiltViewModel
    class UnScreenedPeopleViewModel @Inject constructor(
        private val recordsRepo: RecordsRepo,
        private val benRepo: BenRepo,
        private val vitalRepo: VitalRepo,
        private val tbRepo: TBRepo,
        private val preferenceDao: PreferenceDao
    ) : ViewModel() {

        private val filter = MutableStateFlow("")



        val unscreenedList: StateFlow<List<BenBasicDomain>> =
            recordsRepo.unscreenedList
                .combine(filter) { list, text -> filterUnscreened(list, text) }
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(5000),
                    emptyList()
                )

        val unscreenedCount: StateFlow<Int> =
            recordsRepo.unscreenedListCount
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(5000),
                    0
                )





        fun filterText(text: String) {
            viewModelScope.launch { filter.emit(text) }
        }

        private fun filterUnscreened(
            list: List<BenBasicDomain>,
            filter: String
        ): List<BenBasicDomain> {
            if (filter.isBlank()) return list
            val filterText = filter.lowercase()
            return list.filter {
                it.benId.toString().contains(filterText) ||
                        "${it.benName} ${it.benSurname}".lowercase().contains(filterText) ||
                        it.mobileNo?.contains(filterText) == true
            }
        }

        // ---------------- Examine Status ----------------

        val vitalBenIds: StateFlow<List<Long>> =
            vitalRepo.vitalBenIds
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        val tbScreeningBenIds: StateFlow<List<Long>> =
            tbRepo.tbScreeningBenIds
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        val generalOpdBenIds: StateFlow<List<Long>> =
            tbRepo.generalOpdBenIds
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        val anthropometryBenIds: StateFlow<List<Long>> =
            recordsRepo.anthropometryFilledBenIds
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        val diagnosisBenIds: StateFlow<List<Long>> =
            combine(
                tbRepo.tbDiagnosticsBenIds,
                tbRepo.tbSuspectedBenIds
            ) { diagnostics, suspected ->
                (diagnostics + suspected).distinct()
            }.stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                emptyList()
            )

        // ---------------- Examine Navigation ----------------

        suspend fun getBenFromId(benId: Long): BenRegCache? {
            return benRepo.getBenFromId(benId)
        }

        // ---------------- Existing Functions ----------------

        fun markSymptomsScreened(benId: Long) {
            viewModelScope.launch {
                recordsRepo.markSymptomsScreened(benId)
            }
        }

        fun markChestXrayDone(benId: Long) {
            viewModelScope.launch {
                recordsRepo.markChestXrayDone(benId)
            }

        }

        fun markTrunatTestDone(benId: Long) {
            viewModelScope.launch {
                recordsRepo.markTrunatTestDone(benId)
            }
        }
    }