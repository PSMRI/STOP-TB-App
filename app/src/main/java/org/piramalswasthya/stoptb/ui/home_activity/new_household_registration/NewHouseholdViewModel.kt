package org.piramalswasthya.stoptb.ui.home_activity.new_household_registration

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.piramalswasthya.stoptb.model.LocationState
import org.piramalswasthya.stoptb.configuration.HouseholdFormDataset
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.helpers.DigiPinHelper
import org.piramalswasthya.stoptb.model.HouseholdCache
import org.piramalswasthya.stoptb.model.User
import org.piramalswasthya.stoptb.repositories.BenRepo
import org.piramalswasthya.stoptb.repositories.HouseholdRepo
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class NewHouseholdViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    preferenceDao: PreferenceDao,
    @ApplicationContext context: Context,
    private val benRepo: BenRepo,
    private val householdRepo: HouseholdRepo
) : ViewModel() {

    // ─── Form state ───────────────────────────────────────────────────────────

    enum class State { IDLE, SAVING, SAVE_SUCCESS, SAVE_FAILED }

    private val _state = MutableLiveData(State.IDLE)
    val state: LiveData<State> get() = _state

    private val hhIdFromArgs = NewHouseholdFragmentArgs.fromSavedStateHandle(savedStateHandle).hhId
    private val isAshaFamily =
        NewHouseholdFragmentArgs.fromSavedStateHandle(savedStateHandle).isAshaFamily

    private val _readRecord = MutableLiveData(hhIdFromArgs > 0)
    val readRecord: LiveData<Boolean> get() = _readRecord

    private lateinit var user: User
    private lateinit var household: HouseholdCache
    private val dataset = HouseholdFormDataset(context, preferenceDao.getCurrentLanguage())
    val formList = dataset.listFlow

    // ─── Location state ───────────────────────────────────────────────────────

    private val _locationState = MutableStateFlow<LocationState>(LocationState.Idle)
    val locationState: StateFlow<LocationState> = _locationState.asStateFlow()

    private val _isGpsUnavailable = MutableStateFlow(false)
    val isGpsUnavailable: StateFlow<Boolean> = _isGpsUnavailable.asStateFlow()

    private val _gpsUnavailableReason = MutableStateFlow<String?>(null)
    val gpsUnavailableReason: StateFlow<String?> = _gpsUnavailableReason.asStateFlow()

    // ─── Init ─────────────────────────────────────────────────────────────────

    init {
        viewModelScope.launch(Dispatchers.IO) {
            user = preferenceDao.getLoggedInUser()!!
            val locationRecord = preferenceDao.getLocationRecord()!!
            household = householdRepo.getRecord(hhIdFromArgs) ?: householdRepo.getDraftRecord()
                ?: HouseholdCache(
                    householdId = 0,
                    ashaId = user.userId,
                    isDraft = true,
                    processed = "N",
                    registrationType = isAshaFamily,
                    locationRecord = locationRecord
                )
            dataset.setupPage(household)

            // Restore previously saved location state if editing an existing record
            household.let { hh ->
                when {
                    hh.isGpsUnavailable -> {
                        _isGpsUnavailable.emit(true)
                        _gpsUnavailableReason.emit(hh.gpsUnavailableReason)
                        _locationState.emit(LocationState.Idle)
                    }
                    hh.gpsLatitude != null && hh.gpsLongitude != null && hh.digipin != null -> {
                        _locationState.emit(
                            LocationState.Captured(
                                lat = hh.gpsLatitude!!,
                                lon = hh.gpsLongitude!!,
                                digipin = hh.digipin!!,
                                timestamp = hh.gpsTimestamp.orEmpty()
                            )
                        )
                    }
                    else -> Unit
                }
            }
        }
    }

    // ─── Location callbacks (called from Fragment after GPS hardware interaction) ─

    fun setFetching() {
        _locationState.value = LocationState.Fetching
    }

    fun onLocationResult(lat: Double, lon: Double) {
        val digipin = DigiPinHelper.generate(lat, lon)
        if (digipin == null) {
            _locationState.value = LocationState.Failed.OutsideIndia
            return
        }
        val ts = System.currentTimeMillis().toString()
        _locationState.value = LocationState.Captured(lat, lon, digipin, ts)
    }

    fun onLocationFailed(reason: LocationState.Failed) {
        _locationState.value = reason
    }

    fun onGpsUnavailableToggled(checked: Boolean) {
        _isGpsUnavailable.value = checked
        if (checked) {
            _locationState.value = LocationState.Idle
        } else {
            _gpsUnavailableReason.value = null
        }
    }

    fun onGpsUnavailableReasonSelected(reason: String?) {
        _gpsUnavailableReason.value = reason
    }

    // ─── Validation ───────────────────────────────────────────────────────────

    fun isLocationValid(): Boolean {
        return when {
            locationState.value is LocationState.Captured -> true
            isGpsUnavailable.value && !gpsUnavailableReason.value.isNullOrBlank() -> true
            else -> false
        }
    }

    // ─── Save ─────────────────────────────────────────────────────────────────

    fun saveForm() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    _state.postValue(State.SAVING)
                    dataset.mapValues(household, 1)
                    dataset.mapValues(household, 2)
                    dataset.mapValues(household, 3)

                    // Map location fields onto entity
                    val locState = locationState.value
                    if (locState is LocationState.Captured) {
                        household.gpsLatitude = locState.lat
                        household.gpsLongitude = locState.lon
                        household.digipin = locState.digipin
                        household.gpsTimestamp = locState.timestamp
                        household.isGpsUnavailable = false
                        household.gpsUnavailableReason = null
                    } else if (isGpsUnavailable.value) {
                        household.gpsLatitude = null
                        household.gpsLongitude = null
                        household.digipin = null
                        household.gpsTimestamp = null
                        household.isGpsUnavailable = true
                        household.gpsUnavailableReason = gpsUnavailableReason.value
                    }

                    household.apply {
                        if (householdId == 0L) {
                            householdId = System.currentTimeMillis()
                            householdRepo.substituteHouseholdIdForDraft(this)
                            serverUpdatedStatus = 1
                            processed = "N"
                        } else {
                            serverUpdatedStatus = 2
                            processed = "U"
                        }
                        if (createdTimeStamp == null) {
                            createdTimeStamp = System.currentTimeMillis()
                            createdBy = user.userName
                        }
                        updatedTimeStamp = System.currentTimeMillis()
                        updatedBy = user.userName
                    }
                    householdRepo.persistRecord(household)
                    benRepo.updateBenToSync(household.householdId, SyncState.UNSYNCED)
                    _state.postValue(State.SAVE_SUCCESS)
                } catch (e: Exception) {
                    Timber.e(e, "saving HH data failed")
                    _state.postValue(State.SAVE_FAILED)
                }
            }
        }
    }

    // ─── Helpers used by Fragment ─────────────────────────────────────────────

    fun getHHId() = household.householdId

    fun getHoFName() = "${household.family?.familyHeadName.orEmpty()} ${household.family?.familyName.orEmpty()}".trim()

    fun resetState() {
        _state.value = State.IDLE
    }

    fun updateListOnValueChanged(formId: Int, index: Int) {
        viewModelScope.launch {
            dataset.updateList(formId, index)
        }
    }

    fun setRecordExists(recordExists: Boolean) {
        _readRecord.value = recordExists
    }

    fun enableEditMode() {
        dataset.enableEditMode()
    }

    fun updateValueByIdAndReturnListIndex(id: Int, value: String): Int {
        dataset.setValueById(id, value)
        return dataset.getIndexById(id)
    }
}
