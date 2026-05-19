package org.piramalswasthya.stoptb.ui.home_activity.non_communicable_diseases.general_opd

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
import org.piramalswasthya.stoptb.configuration.GeneralOpdDataset
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.model.GeneralOpdCache
import org.piramalswasthya.stoptb.repositories.BenRepo
import org.piramalswasthya.stoptb.repositories.TBRepo
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class GeneralOpdFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    preferenceDao: PreferenceDao,
    @ApplicationContext context: Context,
    private val benRepo: BenRepo,
    private val tbRepo: TBRepo
) : ViewModel() {

    val benId = GeneralOpdFormFragmentArgs.fromSavedStateHandle(savedStateHandle).benId
    val viewOnly = GeneralOpdFormFragmentArgs.fromSavedStateHandle(savedStateHandle).viewOnly
    val autoFlow = GeneralOpdFormFragmentArgs.fromSavedStateHandle(savedStateHandle).autoFlow
    val generalOpdFlow = GeneralOpdFormFragmentArgs.fromSavedStateHandle(savedStateHandle).generalOpdFlow

    enum class State {
        IDLE, SAVING, SAVE_SUCCESS, SAVE_FAILED, SKIP_SUCCESS
    }

    private val _state = MutableLiveData(State.IDLE)
    val state: LiveData<State> = _state

    private val _benName = MutableLiveData<String>()
    val benName: LiveData<String> = _benName

    private val _benAgeGender = MutableLiveData<String>()
    val benAgeGender: LiveData<String> = _benAgeGender

    private val _recordExists = MutableLiveData<Boolean>()
    val recordExists: LiveData<Boolean> = _recordExists

    private val dataset = GeneralOpdDataset(context, preferenceDao.getCurrentLanguage())
    val formList = dataset.listFlow

    private lateinit var generalOpdCache: GeneralOpdCache

    init {
        viewModelScope.launch {
            val ben = benRepo.getBenFromId(benId)
            ben?.let {
                _benName.value = "${it.firstName} ${it.lastName.orEmpty()}".trim()
                _benAgeGender.value = "${it.age} ${it.ageUnit?.name} | ${it.gender?.name}"
                generalOpdCache = GeneralOpdCache(benId = it.beneficiaryId)
            }

            tbRepo.getGeneralOpd(benId)?.let {
                generalOpdCache = it
                _recordExists.value = true
            } ?: run {
                _recordExists.value = false
            }

            dataset.setUpPage(if (_recordExists.value == true) generalOpdCache else null)
        }
    }

    fun updateListOnValueChanged(formId: Int, index: Int) {
        viewModelScope.launch {
            dataset.updateList(formId, index)
        }
    }

    fun validateBusinessRules(): Int = dataset.validateBusinessRules()

    fun saveForm() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    _state.postValue(State.SAVING)
                    if (dataset.hasAnyData()) {
                        dataset.mapValues(generalOpdCache)
                        generalOpdCache.syncState = SyncState.UNSYNCED
                        tbRepo.saveGeneralOpd(generalOpdCache)
                    }
                    _state.postValue(State.SAVE_SUCCESS)
                } catch (e: Exception) {
                    Timber.e(e, "saving general opd data failed")
                    _state.postValue(State.SAVE_FAILED)
                }
            }
        }
    }

    fun skipForm() {
        _state.value = State.SKIP_SUCCESS
    }
}
