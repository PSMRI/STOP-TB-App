package org.piramalswasthya.stoptb.ui.home_activity.non_communicable_diseases.tb_suspected.quick

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.piramalswasthya.stoptb.configuration.TBSuspectedQuickDataset
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.model.TBSuspectedCache
import org.piramalswasthya.stoptb.repositories.BenRepo
import org.piramalswasthya.stoptb.repositories.TBRepo
import javax.inject.Inject

@HiltViewModel
class TBSuspectedQuickViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    preferenceDao: PreferenceDao,
    @ApplicationContext context: Context,
    private val tbRepo: TBRepo,
    private val benRepo: BenRepo
) : ViewModel() {

    enum class State {
        IDLE, SAVING, SAVE_SUCCESS, SAVE_FAILED
    }

    private val args = TBSuspectedQuickFragmentArgs.fromSavedStateHandle(savedStateHandle)
    val benId = args.benId
    val viewOnly = args.viewOnly

    private val dataset = TBSuspectedQuickDataset(context, preferenceDao.getCurrentLanguage())
    val formList = dataset.listFlow

    private val _benName = MutableLiveData<String>()
    val benName: LiveData<String> = _benName

    private val _benAgeGender = MutableLiveData<String>()
    val benAgeGender: LiveData<String> = _benAgeGender

    private val _state = MutableLiveData(State.IDLE)
    val state: LiveData<State> = _state

    private val _showSubmit = MutableLiveData(true)
    val showSubmit: LiveData<Boolean> = _showSubmit

    private lateinit var tbSuspected: TBSuspectedCache

    init {
        viewModelScope.launch {
            val ben = benRepo.getBenFromId(benId)?.also { beneficiary ->
                _benName.value = listOfNotNull(beneficiary.firstName, beneficiary.lastName).joinToString(" ")
                _benAgeGender.value = "${beneficiary.age} ${beneficiary.ageUnit?.name} | ${beneficiary.gender?.name}"
                tbSuspected = TBSuspectedCache(benId = beneficiary.beneficiaryId)
            }
            val tbScreening = tbRepo.getTBScreening(benId)
            tbRepo.getTBSuspected(benId)?.let {
                tbSuspected = it
            }
            dataset.setUpPage(
                ben,
                tbScreening,
                if (::tbSuspected.isInitialized) tbSuspected else null,
                referralMode = viewOnly
            )
            _showSubmit.value = dataset.shouldShowSubmit()
        }
    }

    fun saveForm() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    _state.postValue(State.SAVING)
                    dataset.mapValues(tbSuspected, 1)
                    tbRepo.saveTBSuspected(tbSuspected)
                    _state.postValue(State.SAVE_SUCCESS)
                } catch (_: Exception) {
                    _state.postValue(State.SAVE_FAILED)
                }
            }
        }
    }

    fun updateListOnValueChanged(formId: Int, index: Int) {
        viewModelScope.launch {
            dataset.updateList(formId, index)
            _showSubmit.value = dataset.shouldShowSubmit()
        }
    }

    fun resetState() {
        _state.value = State.IDLE
    }
}
