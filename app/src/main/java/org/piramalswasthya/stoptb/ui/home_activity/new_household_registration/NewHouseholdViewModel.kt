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
import org.piramalswasthya.stoptb.helpers.GpsDiagnostics
import org.piramalswasthya.stoptb.model.HouseholdCache
import org.piramalswasthya.stoptb.model.User
import org.piramalswasthya.stoptb.repositories.BenRepo
import org.piramalswasthya.stoptb.repositories.HouseholdRepo
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    val linkBenId = NewHouseholdFragmentArgs.fromSavedStateHandle(savedStateHandle).linkBenId

    private val _readRecord = MutableLiveData(hhIdFromArgs > 0)
    val readRecord: LiveData<Boolean> get() = _readRecord

    private var isConsentAgreed = false

    fun setConsentAgreed() {
        isConsentAgreed = true
    }

    fun getIsConsentAgreed() = isConsentAgreed

    private lateinit var user: User
    private lateinit var household: HouseholdCache
    private val dataset = HouseholdFormDataset(context, preferenceDao.getCurrentLanguage())
    val formList = dataset.listFlow

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // ─── Location state ───────────────────────────────────────────────────────

    private companion object {
        const val CAMP_SITE_REASON = "Registration done at camp site" // stored in existing gpsUnavailableReason
    }

    private val _isCampSite = MutableStateFlow(false)
    val isCampSite: StateFlow<Boolean> = _isCampSite.asStateFlow()

    private val _isDoorLocked = MutableStateFlow(false)
    val isDoorLocked: StateFlow<Boolean> = _isDoorLocked.asStateFlow()

    private var savedAsDoorLock = false
    private var promptMemberRegistration = false


    private val _locationState = MutableStateFlow<LocationState>(LocationState.Idle)
    val locationState: StateFlow<LocationState> = _locationState.asStateFlow()

    private val _isGpsUnavailable = MutableStateFlow(false)
    val isGpsUnavailable: StateFlow<Boolean> = _isGpsUnavailable.asStateFlow()

    private val _gpsUnavailableReason = MutableStateFlow<String?>(null)
    val gpsUnavailableReason: StateFlow<String?> = _gpsUnavailableReason.asStateFlow()

    private val _locationDetail = MutableStateFlow<String?>(null)
    val locationDetail: StateFlow<String?> = _locationDetail.asStateFlow()

    // ─── Init ─────────────────────────────────────────────────────────────────

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isLoading.value = true
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

                val villageNames = user.villages.map { it.name }.toTypedArray()


                val registeredMemberCount = if (household.householdId != 0L) {
                    householdRepo.getAllBenOfHousehold(household.householdId).size
                } else 0

                dataset.setupPage(household, villageNames, user.villages, registeredMemberCount)
                _isCampSite.value = dataset.isRegisteredAtCampSite()
                _isDoorLocked.value = household.isDoorLocked
                if (household.isDoorLocked) {
                    dataset.enableEditMode()
                    _readRecord.postValue(false)
                }

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
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ─── Location callbacks (called from Fragment after GPS hardware interaction) ─

    fun setFetching() {
        _locationState.value = LocationState.Fetching
    }


    fun onLocationResult(lat: Double, lon: Double, detail: String? = null) {

        if (_isCampSite.value) return

        val digipin = DigiPinHelper.generate(lat, lon)
        if (digipin == null) {
            Timber.tag(GpsDiagnostics.TAG).w("onLocationResult rejected: lat=$lat lon=$lon is outside supported bounds")
            _locationDetail.value = "lat=$lat, lon=$lon is outside the supported India bounding box"
            _locationState.value = LocationState.Failed.OutsideIndia
            return
        }
        Timber.tag(GpsDiagnostics.TAG).i("onLocationResult captured: lat=$lat lon=$lon digipin=$digipin detail=$detail")
        val ts = System.currentTimeMillis().toString()
        _locationDetail.value = detail
        _locationState.value = LocationState.Captured(lat, lon, digipin, formatTimestamp(ts))
    }

    fun formatTimestamp(ts: String): String {
        val date = Date(ts.toLong())
        val formatter = SimpleDateFormat("MMM d, yyyy h:mm:ss a", Locale.ENGLISH)
        return formatter.format(date)
    }

    fun onLocationFailed(reason: LocationState.Failed, detail: String? = null) {
        if (_isCampSite.value) return
        Timber.tag(GpsDiagnostics.TAG).w("onLocationFailed: reason=$reason detail=$detail")
        _locationDetail.value = detail
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


    fun onDoorLockedToggled(checked: Boolean) {
        _isDoorLocked.value = checked
    }

    fun consumeSaveNavigation(): Pair<Boolean, Boolean> {
        val result = savedAsDoorLock to promptMemberRegistration
        savedAsDoorLock = false
        promptMemberRegistration = false
        return result
    }

    fun onCampSiteChanged(camp: Boolean) {
        _isCampSite.value = camp
        if (camp) {
            _locationDetail.value = null
            _locationState.value = LocationState.Idle
        }
    }

    fun restoreLocationState(state: LocationState) {
        _locationDetail.value = null
        _locationState.value = state
    }


    // ─── Validation ───────────────────────────────────────────────────────────



    fun isLocationValid(): Boolean =
        _isCampSite.value || locationState.value is LocationState.Captured

    // ─── Save ─────────────────────────────────────────────────────────────────

    fun saveForm(asDoorLock: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    _state.postValue(State.SAVING)
                    dataset.mapValues(household, 1)
                    dataset.mapValues(household, 2)
                    dataset.mapValues(household, 3)

                    if (asDoorLock) {
                        persistDoorLockVisit()
                    } else {
                        persistCompletedHousehold()
                    }
                    _state.postValue(State.SAVE_SUCCESS)
                } catch (e: Exception) {
                    Timber.e(e, "saving HH data failed")
                    savedAsDoorLock = false
                    promptMemberRegistration = false
                    _state.postValue(State.SAVE_FAILED)
                }
            }
        }
    }

    /**
     * Locked visit. The beneficiary row is not updated: no household link and no sync flag.
     * [HouseholdCache.benId] only remembers who to link once the registration is completed.
     */
    private suspend fun persistDoorLockVisit() {
        val alreadyOnServer = household.processed == "P"
        household.isDoorLocked = true
        household.doorLockVisitDate = System.currentTimeMillis()
        savedAsDoorLock = true
        promptMemberRegistration = false
        if (linkBenId != 0L) household.benId = linkBenId
        writeHousehold(alreadyOnServer = alreadyOnServer, asDoorLock = true)
        householdRepo.persistRecord(household)
    }

    /** Finished registration. Linking and beneficiary sync happen only on this path. */
    private suspend fun persistCompletedHousehold() {
        val wasDoorLocked = household.isDoorLocked
        val isFirstRegistration = household.householdId == 0L
        val alreadyOnServer = household.processed == "P"
        val pendingLinkId = if (linkBenId != 0L) linkBenId else household.benId ?: 0L
        household.isDoorLocked = false
        savedAsDoorLock = false
        promptMemberRegistration = linkBenId != 0L ||
            (pendingLinkId == 0L && (wasDoorLocked || isFirstRegistration))
        if (pendingLinkId != 0L) household.benId = pendingLinkId
        writeHousehold(alreadyOnServer = alreadyOnServer, asDoorLock = false)
        householdRepo.persistRecord(household)
        linkBeneficiaryIfStillUnassigned(pendingLinkId)
        benRepo.updateBenToSync(household.householdId, SyncState.UNSYNCED)
    }

    private suspend fun linkBeneficiaryIfStillUnassigned(pendingLinkId: Long) {
        if (!DoorLockSubmit.shouldLinkBeneficiary(asDoorLock = false, benId = pendingLinkId)) return
        val ben = benRepo.getBenFromId(pendingLinkId) ?: return
        if (!ben.isNonHH && ben.householdId == household.householdId) return
        benRepo.linkBenToHousehold(pendingLinkId, household.householdId, 19, "Self")
    }

    private suspend fun writeHousehold(alreadyOnServer: Boolean, asDoorLock: Boolean) {
        applyLocationToHousehold()
        household.apply {
            if (householdId == 0L) {
                householdId = System.currentTimeMillis()
                householdRepo.substituteHouseholdIdForDraft(this)
            }
            DoorLockSubmit.syncMarker(alreadyOnServer, asDoorLock)?.let { marker ->
                serverUpdatedStatus = marker.serverUpdatedStatus
                processed = marker.processed
            }
            if (createdTimeStamp == null) {
                createdTimeStamp = System.currentTimeMillis()
                createdBy = user.userName
            }
            updatedTimeStamp = System.currentTimeMillis()
            updatedBy = user.userName
        }
    }

    private fun applyLocationToHousehold() {
        val locState = locationState.value
        if (_isCampSite.value) {
            household.gpsLatitude = null
            household.gpsLongitude = null
            household.digipin = null
            household.gpsTimestamp = null
            household.isGpsUnavailable = true
            household.gpsUnavailableReason = CAMP_SITE_REASON
        } else if (locState is LocationState.Captured) {
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

    fun isTotalMembersValid(): Boolean = dataset.isTotalMembersValid()

    fun getTotalMembers(): Int = dataset.getTotalMembers()

    fun updateValueByIdAndReturnListIndex(id: Int, value: String): Int {
        dataset.setValueById(id, value)
        return dataset.getIndexById(id)
    }
}
