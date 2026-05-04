package org.piramalswasthya.stoptb.ui.home_activity.all_ben.new_ben_registration.ben_form

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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.piramalswasthya.stoptb.configuration.BenRegFormDataset
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.model.BenRegCache
import org.piramalswasthya.stoptb.model.BenRegGen
import org.piramalswasthya.stoptb.model.HouseholdCache
import org.piramalswasthya.stoptb.model.LocationEntity
import org.piramalswasthya.stoptb.model.LocationRecord
import org.piramalswasthya.stoptb.model.PreviewItem
import org.piramalswasthya.stoptb.model.User
import org.piramalswasthya.stoptb.repositories.BenRepo
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class NewBenRegViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val preferenceDao: PreferenceDao,
    @ApplicationContext context: Context,
    private val benRepo: BenRepo,
) : ViewModel() {

    enum class State { IDLE, SAVING, SAVE_SUCCESS, SAVE_FAILED }

    sealed class ListUpdateState {
        object Idle    : ListUpdateState()
        object Updating : ListUpdateState()
        class Updated(val formElementId: Int) : ListUpdateState()
    }

    // ─── Args ───────────────────────────────────────────────────────────
    val hhId          = NewBenRegFragmentArgs.fromSavedStateHandle(savedStateHandle).hhId
    val relToHeadId   = NewBenRegFragmentArgs.fromSavedStateHandle(savedStateHandle).relToHeadId
    val benIdFromArgs = NewBenRegFragmentArgs.fromSavedStateHandle(savedStateHandle).benId

    // StopTB has no HoF / spouse / child concept — kept for nav-graph compat
    val isHoF = false
    val SelectedbenIdFromArgs = 0L

    companion object { var isOtpVerified = false }

    // ─── State ──────────────────────────────────────────────────────────
    private val _state = MutableLiveData(State.IDLE)
    val state: LiveData<State> get() = _state

    private val _listUpdateState = MutableLiveData<ListUpdateState>(ListUpdateState.Idle)
    val listUpdateState: LiveData<ListUpdateState> get() = _listUpdateState

    private val _recordExists = MutableLiveData(benIdFromArgs != 0L)
    val recordExists: LiveData<Boolean> get() = _recordExists

    private val _isDeath = MutableLiveData<Boolean>()
    val isDeath: LiveData<Boolean> get() = _isDeath

    private var isConsentAgreed = false
    fun setConsentAgreed()    { isConsentAgreed = true }
    fun getIsConsentAgreed()  = isConsentAgreed

    // Geolocation
    var capturedLatitude: Double = 0.0
    var capturedLongitude: Double = 0.0

    // ─── Data ────────────────────────────────────────────────────────────
    private lateinit var user:           User
    private lateinit var household:      HouseholdCache
    private lateinit var ben:            BenRegCache
    private lateinit var locationRecord: LocationRecord

    val dataset  = BenRegFormDataset(context, preferenceDao.getCurrentLanguage())
    val formList = dataset.listFlow

    private var lastImageFormId: Int = 0
    fun setCurrentImageFormId(id: Int) { lastImageFormId = id }
    fun getDocumentFormId(): Int = lastImageFormId
    fun setCurrentDocumentFormId(id: Int) { lastImageFormId = id }

    // Not used in StopTB but kept for Fragment compat
    fun getIndexOfBirthCertificateFront() = -1
    fun getIndexOfBirthCertificateBack()  = -1

    // ─── Init ────────────────────────────────────────────────────────────
    private var isPageSetUp = false

    init {
        viewModelScope.launch(Dispatchers.IO) { setUpPage() }
    }

    suspend fun setUpPage() {
        if (isPageSetUp) return
        isPageSetUp = true

        user = preferenceDao.getLoggedInUser()!!

        household = benRepo.getHousehold(hhId) ?: HouseholdCache(
            householdId = 0,
            ashaId      = user.userId,
            processed   = "N",
            isDraft     = false,
            locationRecord = LocationRecord(
                country  = LocationEntity(1, "India"),
                state    = LocationEntity(0, ""),
                district = LocationEntity(0, ""),
                block    = LocationEntity(0, ""),
                village  = LocationEntity(0, "")
            )
        )

        locationRecord = preferenceDao.getLocationRecord() ?: LocationRecord(
            country  = LocationEntity(1, "India"),
            state    = LocationEntity(0, ""),
            district = LocationEntity(0, ""),
            block    = LocationEntity(0, ""),
            village  = LocationEntity(0, "")
        )

        if (benIdFromArgs != 0L) {
            ben = benRepo.getBeneficiaryRecord(benIdFromArgs, hhId)!!
            _isDeath.postValue(ben.isDeath ?: false)
            isOtpVerified = ben.isConsent
            val villageNames = user.villages.map { it.name }.toTypedArray()
            // View mode — show existing data read-only
            dataset.setFirstPageToRead(
                ben,
                familyHeadPhoneNo = household.family?.familyHeadPhoneNo,
                villageName = locationRecord.village.name,
                villageNames = villageNames,
                villageEntityList = user.villages,
                subCentreName = user.subCentre
            )
        } else {
            val villageNames = user.villages.map { it.name }.toTypedArray()
            // New registration
            dataset.setUpPage(
                null,
                household.family?.familyHeadPhoneNo,
                locationRecord.village.name,
                villageNames,
                user.villages,
                user.subCentre
            )
        }
    }

    // ─── Save ────────────────────────────────────────────────────────────
    fun saveForm() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    _state.postValue(State.SAVING)

                    if (!this@NewBenRegViewModel::ben.isInitialized) {
                        val benIdToSet = minOf(benRepo.getMinBenId() - 1L, -1L)
                        ben = BenRegCache(
                            ashaId         = user.userId,
                            beneficiaryId  = benIdToSet,
                            isDeath        = false,
                            isDeathValue   = "",
                            dateOfDeath    = "",
                            timeOfDeath    = "",
                            reasonOfDeath  = "",
                            reasonOfDeathId = -1,
                            placeOfDeath   = "",
                            placeOfDeathId = -1,
                            otherPlaceOfDeath = "",
                            householdId    = 0L,
                            isAdult        = false,
                            isKid          = false,
                            isDraft        = true,
                            kidDetails     = null,
                            genDetails     = BenRegGen(),
                            syncState      = SyncState.UNSYNCED,
                            locationRecord = locationRecord,
                            isConsent      = isOtpVerified,
                        )
                    }

                    dataset.mapValues(ben, 1)

                    // Set captured geolocation
                    ben.latitude = capturedLatitude
                    ben.longitude = capturedLongitude

                    ben.apply {
                        serverUpdatedStatus = if (beneficiaryId < 0L) 1 else 2
                        processed           = if (beneficiaryId < 0L) "N" else "U"
                        syncState           = SyncState.UNSYNCED
                        if (createdDate == null) {
                            createdDate = System.currentTimeMillis()
                            createdBy   = user.userName
                        }
                        updatedDate = System.currentTimeMillis()
                        updatedBy   = user.userName
                    }

                    benRepo.persistRecord(ben, updateIfExists = benIdFromArgs != 0L)
                    _state.postValue(State.SAVE_SUCCESS)

                } catch (e: Exception) {
                    Timber.e("saving Ben data failed!! $e")
                    _state.postValue(State.SAVE_FAILED)
                }
            }
        }
    }

    // ─── List updates ────────────────────────────────────────────────────
    fun updateListOnValueChanged(formId: Int, index: Int) {
        viewModelScope.launch {
            _listUpdateState.value = ListUpdateState.Updating
            dataset.updateList(formId, index)
            _listUpdateState.value = ListUpdateState.Updated(formId)
        }
    }

    fun resetListUpdateState() { _listUpdateState.value = ListUpdateState.Idle }

    // ─── Helpers ─────────────────────────────────────────────────────────
    fun getBenGender()  = if (this::ben.isInitialized) ben.gender else null
    fun getBenName()    = if (this::ben.isInitialized) "${ben.firstName} ${ben.lastName ?: ""}" else ""
    fun isHoFMarried()  = false  // StopTB has no HoF concept

    fun setImageUriToFormElement(dpUri: Uri) {
        dataset.setImageUriToFormElement(lastImageFormId, dpUri)
    }

    fun updateValueByIdAndReturnListIndex(id: Int, value: String?): Int {
        dataset.setValueById(id, value)
        return dataset.getIndexById(id)
    }

    fun getIndexOfAgeAtMarriage()   = dataset.getIndexOfAgeAtMarriage()
    fun getIndexOfMaritalStatus()   = dataset.getIndexOfMaritalStatus()
    fun getIndexOfContactNumber()   = dataset.getIndexOfContactNumber()
    fun getIndexofTempraryNumber()  = dataset.getTempMobileNoStatus()
    fun getCurrentBenId(): Long = if (this::ben.isInitialized) ben.beneficiaryId else benIdFromArgs

    fun setRecordExist(b: Boolean) { _recordExists.value = b }
    fun enableEditMode() { dataset.enableEditMode() }

    // ─── Preview ─────────────────────────────────────────────────────────
    suspend fun getFormPreviewData(): List<PreviewItem> = withContext(Dispatchers.Default) {
        val elements = dataset.listFlow.first()
        val out = mutableListOf<PreviewItem>()
        for (el in elements) {
            if (el.inputType.name == "IMAGE_VIEW") {
                val uri = runCatching { el.value?.let { Uri.parse(it.toString()) } }.getOrNull()
                out.add(PreviewItem(label = el.title ?: "", value = "", isImage = true, imageUri = uri))
                continue
            }
            val display = when {
                el.value == null                                         -> "-"
                el.value is String && el.value.toString().isBlank()     -> "-"
                el.value is String && el.value.toString().contains(",") ->
                    el.value.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
                else -> el.value.toString()
            }
            val trimmed = if (display.length > 400) display.substring(0, 400) + "…" else display
            out.add(PreviewItem(label = el.title ?: "", value = trimmed, isImage = false))
        }
        out
    }

    override fun onCleared() {
        super.onCleared()
        isOtpVerified = false
    }
}
