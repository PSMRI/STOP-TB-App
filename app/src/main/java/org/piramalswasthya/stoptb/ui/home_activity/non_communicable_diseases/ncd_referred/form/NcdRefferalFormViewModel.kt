package org.piramalswasthya.stoptb.ui.home_activity.non_communicable_diseases.ncd_referred.form

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
import org.piramalswasthya.stoptb.configuration.dynamicDataSet.ReferalFormDataset
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.database.room.dao.BenDao
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.model.BenRegCache
import org.piramalswasthya.stoptb.model.Gender
import org.piramalswasthya.stoptb.model.ReferalCache
import org.piramalswasthya.stoptb.repositories.CbacRepo
import org.piramalswasthya.stoptb.repositories.NcdReferalRepo
import javax.inject.Inject

@HiltViewModel
class NcdRefferalFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    preferenceDao: PreferenceDao,
    var cbacRepo: CbacRepo,
    var benDao: BenDao,
    var referalRepo: NcdReferalRepo,
    @ApplicationContext context: Context,
) : ViewModel() {

    enum class State { IDLE, SAVING, SAVE_SUCCESS, SAVE_FAILED }

    private lateinit var ben: BenRegCache

    private val _state = MutableLiveData(State.IDLE)
    val state: LiveData<State> get() = _state

    private val _benName = MutableLiveData<String>()
    val benName: LiveData<String> get() = _benName

    private val _benAgeGender = MutableLiveData<String>()
    val benAgeGender: LiveData<String> get() = _benAgeGender

    private val _recordExists = MutableLiveData<Boolean>()
    val recordExists: LiveData<Boolean> get() = _recordExists

    private val dataset = ReferalFormDataset(context, preferenceDao.getCurrentLanguage(), preferenceDao)
    val formList = dataset.listFlow

    lateinit var referalCache: ReferalCache

    private val _gender = MutableLiveData<Gender>()
    val gender: LiveData<Gender> get() = _gender

    private val _age = MutableLiveData<Int>()
    val age: LiveData<Int> get() = _age

    val benId = NCDReferalFormFragmentArgs.fromSavedStateHandle(savedStateHandle).benId
    val referralReason = NCDReferalFormFragmentArgs.fromSavedStateHandle(savedStateHandle).referral
    val cbacId = NCDReferalFormFragmentArgs.fromSavedStateHandle(savedStateHandle).cbacId
    val referraltype = NCDReferalFormFragmentArgs.fromSavedStateHandle(savedStateHandle).referralType

    init {
        viewModelScope.launch {
            ben = benDao.getBen(benId)!!
            ben.gender?.let { _gender.value = it }
            _age.value = ben.age
            _benName.value = "${ben.firstName} ${ben.lastName ?: ""}"
            _benAgeGender.value = "${ben.age} ${ben.ageUnit?.name} | ${ben.gender?.name}"
            _recordExists.value = false
            referalCache = ReferalCache(
                benId = benId,
                id = 0,
                syncState = SyncState.UNSYNCED,
                createdBy = preferenceDao.getLoggedInUser()?.userName
            )
            dataset.setUpPage(referralReason, referraltype)
        }
    }

    fun updateListOnValueChanged(formId: Int, index: Int) {
        viewModelScope.launch { dataset.updateList(formId, index) }
    }

    fun saveForm() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    _state.postValue(State.SAVING)
                    dataset.mapValues(referalCache)
                    _state.postValue(State.SAVE_SUCCESS)
                } catch (e: Exception) {
                    _state.postValue(State.SAVE_FAILED)
                }
            }
        }
    }

    fun resetState() { _state.value = State.IDLE }
}