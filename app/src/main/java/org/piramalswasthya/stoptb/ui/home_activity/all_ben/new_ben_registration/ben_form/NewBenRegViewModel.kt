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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.piramalswasthya.stoptb.model.LocationState
import org.piramalswasthya.stoptb.helpers.DigiPinHelper
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
    private val genderFromArgs = NewBenRegFragmentArgs.fromSavedStateHandle(savedStateHandle).gender

    // StopTB has no HoF / spouse / child concept — kept for nav-graph compat
    val isHoF = false
    private val selectedBenIdFromArgs = NewBenRegFragmentArgs.fromSavedStateHandle(savedStateHandle).selectedBenId
    private val isAddSpouseFromArgs   = NewBenRegFragmentArgs.fromSavedStateHandle(savedStateHandle).isAddSpouse

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

    // True when registering without an associated household
    val isStandalone: Boolean get() = hhId == 0L

    // Location state — only used for standalone registrations
    private val _locationState = MutableStateFlow<LocationState>(LocationState.Idle)
    val locationState: StateFlow<LocationState> = _locationState.asStateFlow()

    private val _isGpsUnavailable = MutableStateFlow(false)
    val isGpsUnavailable: StateFlow<Boolean> = _isGpsUnavailable.asStateFlow()

    private val _gpsUnavailableReason = MutableStateFlow<String?>(null)
    val gpsUnavailableReason: StateFlow<String?> = _gpsUnavailableReason.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // ─── Data ────────────────────────────────────────────────────────────
    private lateinit var user:           User
    private lateinit var household:      HouseholdCache
    private lateinit var ben:            BenRegCache
    private lateinit var locationRecord: LocationRecord

    private val dataset by lazy(LazyThreadSafetyMode.NONE) {
        BenRegFormDataset(context, preferenceDao.getCurrentLanguage())
    }
    private val _formList = MutableStateFlow(emptyList<org.piramalswasthya.stoptb.model.FormElement>())
    val formList: StateFlow<List<org.piramalswasthya.stoptb.model.FormElement>> = _formList.asStateFlow()

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
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isLoading.value = true
                setUpPage()
                dataset.listFlow.collectLatest { _formList.value = it }
            } finally {
                _isLoading.value = false
            }
        }
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
                subCentreName = user.subCentre,
                // familyHeadRelationPosition is stored 1-indexed (getPosition = indexOf+1),
                // but setUpPage uses it as 0-indexed with getOrNull(). Subtract 1 to align.
                relToHeadId = (ben.familyHeadRelationPosition - 1).takeIf { it >= 0 } ?: relToHeadId
            )
        } else {
            val villageNames = user.villages.map { it.name }.toTypedArray()
            // New registration
            // For wife/husband: look up spouse's first name from the matching member's stored spouseName
            val selectedSpouseMember =
                if (relToHeadId == 4 || relToHeadId == 5)
                    benRepo.getBeneficiaryRecord(selectedBenIdFromArgs, hhId)
                else null
            val selectedMemberSpouseName =
                selectedSpouseMember
                    ?.genDetails
                    ?.spouseName
                    ?.takeIf { it.isNotBlank() }
            val spouseFirstName: String? = selectedMemberSpouseName ?: if (selectedBenIdFromArgs == 0L) when (relToHeadId) {
                4 -> benRepo.getBenListFromHousehold(hhId)          // wife reg → get male member's stored wife name
                        .filter { it.genderId == 1 }
                        .mapNotNull { it.genDetails?.spouseName }
                        .firstOrNull { it.isNotBlank() }
                5 -> benRepo.getBenListFromHousehold(hhId)          // husband reg → get female member's stored husband name
                        .filter { it.genderId == 2 }
                        .mapNotNull { it.genDetails?.spouseName }
                        .firstOrNull { it.isNotBlank() }
                else -> null
            } else null
            val selectedSpouseMemberName = selectedSpouseMember?.fullName()
            val effectiveRelToHeadId =
                getSpouseRegistrationRelationToHeadId(relToHeadId, selectedSpouseMember)

            // For Son (8) / Daughter (9): pre-fill Father's Name and Mother's Name
            // Logic matches FLW2.9: use HoF as primary parent; spouse record (relPos 5/6) as the other parent;
            // fallback to HoF's stored genDetails.spouseName if no spouse is registered yet.
            val (prefillFatherName, prefillMotherName) = if (relToHeadId == 8 || relToHeadId == 9) {
                val members = benRepo.getBenListFromHousehold(hhId)
                // HoF = Self (familyHeadRelationPosition = 19: index 18 in array + 1 for 1-indexed storage)
                // Note: household.benId is not set in NikshayMitra, so we cannot use it for lookup.
                val hofBen = members.firstOrNull { it.familyHeadRelationPosition == 19 }
                    ?: members.firstOrNull()   // fallback: first registered member if HoF not found

                // Spouse of HoF = Wife (pos 5) or Husband (pos 6), but NOT the HoF themselves
                val hofSpouse = members.firstOrNull {
                    (it.familyHeadRelationPosition == 5 || it.familyHeadRelationPosition == 6) &&
                    it.beneficiaryId != hofBen?.beneficiaryId
                }

                if (hofBen != null) {
                    val hofFullName = listOfNotNull(hofBen.firstName?.trim(), hofBen.lastName?.trim())
                        .filter { it.isNotBlank() }.joinToString(" ").takeIf { it.isNotBlank() }
                    val spouseFullName = hofSpouse?.let {
                        listOfNotNull(it.firstName?.trim(), it.lastName?.trim())
                            .filter { n -> n.isNotBlank() }.joinToString(" ").takeIf { n -> n.isNotBlank() }
                    } ?: hofBen.genDetails?.spouseName?.takeIf { it.isNotBlank() }

                    if (hofBen.genderId == 1) {
                        // HoF is male → he is the father; his spouse is the mother
                        Pair(hofFullName, spouseFullName)
                    } else {
                        // HoF is female → she is the mother; her spouse is the father
                        Pair(spouseFullName, hofFullName)
                    }
                } else {
                    Pair(null, null)
                }
            } else {
                Pair(null, null)
            }

            val prefillBen = getHouseholdPrefillBen(
                spouseFirstName,
                prefillFatherName,
                prefillMotherName,
                selectedSpouseMemberName
            )
            val prefillLocation = prefillBen?.locationRecord ?: locationRecord
            dataset.setUpPage(
                prefillBen,
                household.family?.familyHeadPhoneNo,
                prefillLocation.village.name,
                villageNames,
                user.villages,
                user.subCentre,
                relToHeadId = effectiveRelToHeadId,
                spouseRegistrationRelToHeadId = relToHeadId
            )
        }
        // Restore/Inherit Location details
        if (!isStandalone) {
            if (household.isGpsUnavailable) {
                _isGpsUnavailable.value = true
                _gpsUnavailableReason.value = household.gpsUnavailableReason
            } else if (household.gpsLatitude != null && household.gpsLongitude != null && household.digipin != null) {
                _locationState.value = LocationState.Captured(
                    lat = household.gpsLatitude!!,
                    lon = household.gpsLongitude!!,
                    digipin = household.digipin!!,
                    timestamp = household.gpsTimestamp.orEmpty()
                )
            }
        } else if (benIdFromArgs != 0L) {
            if (ben.isGpsUnavailable) {
                _isGpsUnavailable.value = true
                _gpsUnavailableReason.value = ben.gpsUnavailableReason
            } else if (ben.gpsLatitude != null && ben.gpsLongitude != null && ben.digipin != null) {
                _locationState.value = LocationState.Captured(
                    lat = ben.gpsLatitude!!,
                    lon = ben.gpsLongitude!!,
                    digipin = ben.digipin!!,
                    timestamp = ben.gpsTimestamp.orEmpty()
                )
            }
        }
    }

    // ─── Location callbacks (standalone registrations only) ─────────────
    fun setFetching() { _locationState.value = LocationState.Fetching }

    fun onLocationResult(lat: Double, lon: Double) {
        val digipin = DigiPinHelper.generate(lat, lon)
        if (digipin == null) {
            _locationState.value = LocationState.Failed.OutsideIndia
            return
        }
        val ts = System.currentTimeMillis().toString()
        _locationState.value = LocationState.Captured(lat, lon, digipin, ts)
    }

    fun onLocationFailed(reason: LocationState.Failed) { _locationState.value = reason }

    fun onGpsUnavailableToggled(checked: Boolean) {
        _isGpsUnavailable.value = checked
        if (checked) _locationState.value = LocationState.Idle
        else _gpsUnavailableReason.value = null
    }

    fun onGpsUnavailableReasonSelected(reason: String?) { _gpsUnavailableReason.value = reason }

    fun isLocationValid(): Boolean = when {
        locationState.value is LocationState.Captured -> true
        isGpsUnavailable.value && !gpsUnavailableReason.value.isNullOrBlank() -> true
        else -> false
    }

    // ─── Save ────────────────────────────────────────────────────────────
    private fun getSpouseRegistrationRelationToHeadId(
        spouseRegistrationRelToHeadId: Int,
        selectedMember: BenRegCache?
    ): Int {
        val selectedRelationPosition = selectedMember?.familyHeadRelationPosition
            ?: return spouseRegistrationRelToHeadId
        return when (spouseRegistrationRelToHeadId) {
            4 -> when (selectedRelationPosition) {
                19 -> 4
                2 -> 0
                3 -> 19
                9 -> 17
                else -> 20
            }
            5 -> when (selectedRelationPosition) {
                19 -> 5
                1 -> 1
                10 -> 16
                else -> 20
            }
            else -> spouseRegistrationRelToHeadId
        }
    }

    private fun BenRegCache.fullName(): String? =
        listOfNotNull(firstName?.trim(), lastName?.trim())
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .takeIf { it.isNotBlank() }

    private fun getHouseholdPrefillBen(
        overrideFirstName: String? = null,
        prefillFatherName: String? = null,
        prefillMotherName: String? = null,
        spouseNameOverride: String? = null
    ): BenRegCache? {
        if (hhId <= 0L || household.householdId <= 0L) return null

        val family = household.family
        val details = household.details
        val address = listOfNotNull(
            family?.houseNo,
            family?.mohallaName,
            family?.wardName,
            details?.street,
            details?.colony
        ).mapNotNull { value ->
            value.trim().takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
        }.joinToString(", ")

        return BenRegCache(
            ashaId = user.userId,
            beneficiaryId = 0L,
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
            isAdult = false,
            isKid = false,
            isDraft = true,
            kidDetails = null,
            genDetails = when (relToHeadId) {
                // Wife (4) or Husband (5): pre-fill Married + HoF name as spouse
                4, 5 -> BenRegGen(
                    maritalStatusId = 2,
                    spouseName = spouseNameOverride ?: family?.familyHeadName
                )
                else -> BenRegGen()
            },
            syncState = SyncState.UNSYNCED,
            locationRecord = household.locationRecord,
            firstName = overrideFirstName
                ?: family?.familyHeadName?.takeIf { relToHeadId == 18 },
            lastName = family?.familyName,
            fatherName = prefillFatherName,
            motherName = prefillMotherName,
            genderId = genderFromArgs,
            contactNumber = family?.familyHeadPhoneNo,
            address = address.ifBlank { null },
            economicStatus = family?.povertyLine,
            economicStatusId = family?.povertyLineId,
            residentialArea = details?.residentialArea,
            residentialAreaId = details?.residentialAreaId,
        )
    }

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
                            householdId    = if (hhId > 0L) hhId else 0L,
                            isAdult        = false,
                            isKid          = false,
                            isDraft        = true,
                            kidDetails     = null,
                            genDetails     = BenRegGen(),
                            syncState      = SyncState.UNSYNCED,
                            // Use household's locationRecord when registering from household flow
                            // to ensure ben's loc_village_id matches household's village
                            locationRecord = if (hhId > 0L && household.householdId > 0L)
                                household.locationRecord else locationRecord,
                            isConsent      = isOtpVerified,
                        )
                    }

                    dataset.mapValues(ben, 1)

                    // Set GPS location: inherit from household when registered under one,
                    // otherwise persist the standalone-captured GPS state
                    if (!isStandalone) {
                        ben.gpsLatitude = household.gpsLatitude
                        ben.gpsLongitude = household.gpsLongitude
                        ben.digipin = household.digipin
                        ben.gpsTimestamp = household.gpsTimestamp
                        ben.isGpsUnavailable = household.isGpsUnavailable
                        ben.gpsUnavailableReason = household.gpsUnavailableReason
                    } else {
                        val locState = locationState.value
                        if (locState is LocationState.Captured) {
                            ben.gpsLatitude = locState.lat
                            ben.gpsLongitude = locState.lon
                            ben.digipin = locState.digipin
                            ben.gpsTimestamp = locState.timestamp
                            ben.isGpsUnavailable = false
                            ben.gpsUnavailableReason = null
                        } else if (isGpsUnavailable.value) {
                            ben.gpsLatitude = null
                            ben.gpsLongitude = null
                            ben.digipin = null
                            ben.gpsTimestamp = null
                            ben.isGpsUnavailable = true
                            ben.gpsUnavailableReason = gpsUnavailableReason.value
                        }
                    }

                    ben.apply {
                        if (hhId > 0L) householdId = hhId
                        serverUpdatedStatus = if (beneficiaryId < 0L) 1 else 2
                        processed           = if (beneficiaryId < 0L) "N" else "U"
                        syncState           = SyncState.UNSYNCED
                        isDraft             = false
                        // If registering as spouse, mark isSpouseAdded directly here so
                        // processed stays "N" (new ben needs server ID via createBenId API).
                        // Do NOT use updateBeneficiarySpouseAdded() for the new ben — that
                        // DAO sets processed="U" which routes it to the wrong push path.
                        if (isAddSpouseFromArgs == 1) isSpouseAdded = true
                        if (createdDate == null) {
                            createdDate = System.currentTimeMillis()
                            createdBy   = user.userName
                        }
                        updatedDate = System.currentTimeMillis()
                        updatedBy   = user.userName
                    }

                    val spouseLinkBenId = findExistingSpouseLinkForFamilyMember(ben)
                    if (spouseLinkBenId != null) {
                        ben.isSpouseAdded = true
                    }

                    benRepo.persistRecord(ben, updateIfExists = benIdFromArgs != 0L)

                    // Mark both members' isSpouseAdded = true (hides "Register Wife/Husband" on both cards)
                    if (isAddSpouseFromArgs == 1 && selectedBenIdFromArgs != 0L && hhId > 0L) {
                        // Mark the original member (e.g. husband) — hides "Register Wife" on his card
                        // Works for both online (positive benId) and offline (negative benId) members.
                        benRepo.updateBeneficiarySpouseAdded(hhId, selectedBenIdFromArgs, SyncState.UNSYNCED)
                        // NOTE: newly registered spouse (wife) — isSpouseAdded already set in apply{} above,
                        // so we do NOT call updateBeneficiarySpouseAdded here (it would overwrite processed="N" with "U")

                        // Back-link: update the original member's spouseName with the new spouse's name
                        // so that when original member's form is viewed, Wife's/Husband's Name is filled.
                        val newSpouseName = listOfNotNull(ben.firstName?.trim(), ben.lastName?.trim())
                            .filter { it.isNotBlank() }
                            .joinToString(" ")
                        if (newSpouseName.isNotBlank()) {
                            benRepo.getBeneficiaryRecord(selectedBenIdFromArgs, hhId)?.let { originalBen ->
                                // Only update if spouseName was blank (don't overwrite manually entered value)
                                if (originalBen.genDetails?.spouseName.isNullOrBlank()) {
                                    if (originalBen.genDetails == null) {
                                        originalBen.genDetails = BenRegGen(spouseName = newSpouseName)
                                    } else {
                                        originalBen.genDetails!!.spouseName = newSpouseName
                                    }
                                    originalBen.syncState           = SyncState.UNSYNCED
                                    originalBen.serverUpdatedStatus = 2
                                    originalBen.processed           = "U"
                                    originalBen.updatedDate         = System.currentTimeMillis()
                                    originalBen.updatedBy           = user.userName
                                    benRepo.persistRecord(originalBen, updateIfExists = true)
                                }
                            }
                        }
                    }
                    spouseLinkBenId?.let { linkedBenId ->
                        benRepo.updateBeneficiarySpouseAdded(hhId, linkedBenId, SyncState.UNSYNCED)
                    }

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

    fun isDeathSelected(): Boolean {
        return dataset.isDeathSelected()
    }

    fun setImageUriToFormElement(dpUri: Uri) {
        dataset.setImageUriToFormElement(lastImageFormId, dpUri)
    }

    fun hasBeneficiaryPhoto(): Boolean = dataset.hasBeneficiaryPhoto()

    fun updateValueByIdAndReturnListIndex(id: Int, value: String?): Int {
        dataset.setValueById(id, value)
        return dataset.getIndexById(id)
    }

    fun getIndexOfAgeAtMarriage()   = dataset.getIndexOfAgeAtMarriage()
    fun getAgeAtMarriageLength(): Int = dataset.ageAtMarriage.value?.length ?: 0
    fun getIndexOfMaritalStatus()   = dataset.getIndexOfMaritalStatus()
    fun getIndexOfContactNumber()   = dataset.getIndexOfContactNumber()
    fun getIndexofTempraryNumber()  = dataset.getTempMobileNoStatus()
    fun getCurrentBenId(): Long = if (this::ben.isInitialized) ben.beneficiaryId else benIdFromArgs

    fun setRecordExist(b: Boolean) { _recordExists.value = b }
    fun enableEditMode() { dataset.enableEditMode() }

    private suspend fun findExistingSpouseLinkForFamilyMember(newBen: BenRegCache): Long? {
        if (isAddSpouseFromArgs == 1 || hhId <= 0L || benIdFromArgs != 0L) return null
        if (relToHeadId != 4 && relToHeadId != 5) return null

        val newFirstName = newBen.firstName.normalizeNameForMatch()
        val newFullName = newBen.fullName().normalizeNameForMatch()
        if (newFirstName.isBlank() && newFullName.isBlank()) return null

        val expectedExistingGenderId = if (relToHeadId == 4) 1 else 2
        val matches = benRepo.getBenListFromHousehold(hhId).filter { existing ->
            existing.beneficiaryId != newBen.beneficiaryId &&
                existing.genderId == expectedExistingGenderId &&
                existing.isMarried &&
                (relToHeadId == 4 || !existing.isSpouseAdded) &&
                existing.genDetails?.spouseName.normalizeNameForMatch().let { spouseName ->
                    spouseName.isNotBlank() &&
                        (spouseName == newFirstName || spouseName == newFullName)
                }
        }

        return matches.singleOrNull()?.beneficiaryId
    }

    private fun String?.normalizeNameForMatch(): String =
        this?.trim()
            ?.replace(Regex("\\s+"), " ")
            ?.uppercase()
            .orEmpty()

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
