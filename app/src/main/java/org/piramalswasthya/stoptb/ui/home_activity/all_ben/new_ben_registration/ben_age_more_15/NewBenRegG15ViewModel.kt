package org.piramalswasthya.stoptb.ui.home_activity.all_ben.new_ben_registration.ben_age_more_15

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.piramalswasthya.stoptb.configuration.BenRegFormDataset
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.model.BenRegCache
import org.piramalswasthya.stoptb.model.BenRegGen
import org.piramalswasthya.stoptb.model.HouseholdCache
import org.piramalswasthya.stoptb.model.User
import org.piramalswasthya.stoptb.repositories.BenRepo
import org.piramalswasthya.stoptb.repositories.UserRepo
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class NewBenRegG15ViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext context: Context,
    private val benRepo: BenRepo,
    preferenceDao: PreferenceDao,
    userRepo: UserRepo
) : ViewModel() {
    enum class State {
        IDLE, SAVING, SAVE_SUCCESS, SAVE_FAILED
    }

    private val hhId = NewBenRegG15FragmentArgs.fromSavedStateHandle(savedStateHandle).hhId
    private val benIdFromArgs =
        NewBenRegG15FragmentArgs.fromSavedStateHandle(savedStateHandle).benId

    private val _currentPage = MutableStateFlow(1)
    val currentPage = _currentPage.asStateFlow()

    private lateinit var user: User
    private val dataset: BenRegFormDataset =
        BenRegFormDataset(context, preferenceDao.getCurrentLanguage())
    val formList = dataset.listFlow

    val prevPageButtonVisibility = currentPage.transform {
        emit(it > 1)
    }
    val nextPageButtonVisibility = currentPage.transform {
        emit(it == 1 || (it == 2 && dataset.hasThirdPage()))
    }
    val submitPageButtonVisibility = currentPage.transform {
        emit(((it == 2 && !dataset.hasThirdPage()) || it == 3) && recordExists.value == false)
    }

    private val _state = MutableLiveData(State.IDLE)
    val state: LiveData<State>
        get() = _state

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?>
        get() = _errorMessage

    private val _recordExists = MutableLiveData(benIdFromArgs > 0)
    val recordExists: LiveData<Boolean>
        get() = _recordExists

    private lateinit var household: HouseholdCache
    private lateinit var ben: BenRegCache

    private var lastImageFormId: Int = 0

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                user = preferenceDao.getLoggedInUser()!!
                household = benRepo.getHousehold(hhId)!!
                ben = benRepo.getBeneficiaryRecord(benIdFromArgs, hhId) ?: BenRegCache(
                    ashaId = user.userId,
                    beneficiaryId = -1,
                    isDeath = false,
                    isDeathValue = "",
                    dateOfDeath = "",
                    timeOfDeath = "",
                    reasonOfDeath = "",
                    reasonOfDeathId = -1,
                    placeOfDeath = "",
                    placeOfDeathId = -1,
                    otherPlaceOfDeath = "",
                    householdId = hhId,
                    isAdult = true,
                    isKid = false,
                    isDraft = true,
                    genDetails = BenRegGen(),
                    syncState = SyncState.UNSYNCED,
                    locationRecord = preferenceDao.getLocationRecord()!!,
                    isConsent = false
                )
                currentPage.collect {
                    when (it) {
                        1 -> dataset.setFirstPageToRead(ben, household.family?.familyHeadPhoneNo)
//                        2 -> dataset.setSecondPage(ben)
                        3 -> dataset.setFirstPageToRead(ben, household.family?.familyHeadPhoneNo)
                    }
                }
            }
        }
    }

    fun getIndexOfRelationToHead() = dataset.getIndexById(15) // relationToHead id
    fun getIndexOfAgeAtMarriage() = dataset.getIndexOfAgeAtMarriage()
    fun getIndexOfFatherName() = dataset.getIndexById(10) // fatherName id
    fun getIndexOfMotherName() = dataset.getIndexById(11) // motherName id
    fun getIndexOfSpouseName() = dataset.getIndexById(1009) // husbandName id
    fun getIndexOfMaritalStatus() = dataset.getIndexOfMaritalStatus()
    fun getIndexOfElement(id: Int) = dataset.getIndexById(id)

    fun hasThirdPage() = dataset.hasThirdPage()

    fun updateValueByIdAndReturnListIndex(id: Int, value: String?): Int {
        dataset.setValueById(id, value)
        return dataset.getIndexById(id)
    }

    fun saveForm() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    _state.postValue(State.SAVING)
                    dataset.mapValues(ben, 2)
                    ben.apply {
                        if (beneficiaryId == -1L) {
                            benRepo.substituteBenIdForDraft(ben)
                            serverUpdatedStatus = 1
                            processed = "N"
                        } else {
                            serverUpdatedStatus = 2
                            processed = "U"
                        }
                        if (createdDate == null) {
                            createdDate = System.currentTimeMillis()
                            createdBy = user.userName
                        }
                        updatedDate = System.currentTimeMillis()
                        updatedBy = user.userName
                    }
                    benRepo.persistRecord(ben)
                    _state.postValue(State.SAVE_SUCCESS)
                } catch (e: IllegalAccessError) {
                    Timber.d("saving Ben data failed!! $e")
                    _state.postValue(State.SAVE_FAILED)
                }
            }
        }
    }

    fun goToPreviousPage() {
        viewModelScope.launch { _currentPage.emit(currentPage.value - 1) }
    }

    fun goToNextPage() {
        viewModelScope.launch { _currentPage.emit(currentPage.value + 1) }
    }

    fun updateListOnValueChanged(formId: Int, index: Int) {
        viewModelScope.launch { dataset.updateList(formId, index) }
    }

    fun setCurrentImageFormId(id: Int) {
        lastImageFormId = id
    }

    fun setImageUriToFormElement(dpUri: Uri) {
        dataset.setImageUriToFormElement(lastImageFormId, dpUri)
    }

    fun resetErrorMessage() {
        _errorMessage.value = null
    }
}