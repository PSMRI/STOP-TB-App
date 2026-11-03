package org.piramalswasthya.stoptb.ui.service_location_activity

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.helpers.Languages

import org.piramalswasthya.stoptb.helpers.Languages.ASSAMESE
import org.piramalswasthya.stoptb.helpers.Languages.ENGLISH
import org.piramalswasthya.stoptb.model.LocationEntity
import org.piramalswasthya.stoptb.model.LocationRecord
import org.piramalswasthya.stoptb.model.User
import org.piramalswasthya.stoptb.model.VillageDefaults
import javax.inject.Inject

@HiltViewModel
class ServiceTypeViewModel @Inject constructor(
    private val pref: PreferenceDao,
) : ViewModel() {

    enum class State {
        IDLE,
        LOADING,
        SUCCESS
    }

    private lateinit var stateDropdownEntry: String
    val stateList: Array<String>
        get() = arrayOf(stateDropdownEntry)

    private lateinit var districtDropdownEntry: String
    val districtList: Array<String>
        get() = arrayOf(districtDropdownEntry)

    private lateinit var blockDropdownEntry: String
    val blockList: Array<String>
        get() = arrayOf(blockDropdownEntry)

    private lateinit var tuDropdownEntries: Array<String>
    val tuList: Array<String>
        get() = tuDropdownEntries

    private lateinit var healthFacilityDropdownEntries: Array<String>
    val healthFacilityList: Array<String>
        get() = healthFacilityDropdownEntries

    private lateinit var villageDropdownEntries: Array<String>
    val villageList: Array<String>
        get() = villageDropdownEntries

    private lateinit var _userName: String
    val userName: String
        get() = _userName

    private var _selectedVillage: LocationEntity? = null
    val selectedVillage: LocationEntity?
        get() = _selectedVillage
    private var _selectedTu: LocationEntity? = null
    val selectedTu: LocationEntity?
        get() = _selectedTu
    private var _selectedHealthFacility: LocationEntity? = null
    val selectedHealthFacility: LocationEntity?
        get() = _selectedHealthFacility
    val selectedVillageName: String?
        get() = selectedVillage?.localizedName()
    val selectedTuName: String?
        get() = selectedTu?.localizedName()
    val selectedHealthFacilityName: String?
        get() = selectedHealthFacility?.localizedName()


    // Pincode / Residential Area for the selected village: backend master value (getUserDetail) where it
    // has one, otherwise the value entered earlier on this device (pre-filled, still editable here).
    private var _selectedVillageDefaults: VillageDefaults? = null
    val selectedVillageDefaults: VillageDefaults?
        get() = _selectedVillageDefaults

    // Backend master values for the selected village; these are shown locked on the page.
    val selectedVillageMasterDefaults: VillageDefaults?
        get() = _selectedVillage?.let { village -> pref.getVillageMasterDefaults(village.id) }

    private val _state = MutableLiveData(State.LOADING)
    val state: LiveData<State>
        get() = _state


    private var currentLocation: LocationRecord? = null
    private lateinit var user: User
    private val tus: List<LocationEntity>
        get() = user.tus.orEmpty()
    private val healthFacilities: List<LocationEntity>
        get() = user.healthFacilities.orEmpty()

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                user = pref.getLoggedInUser()!!
                _userName = user.name
                currentLocation = pref.getLocationRecord()
                _selectedVillage = currentLocation?.village
                _selectedVillageDefaults = _selectedVillage?.let { pref.getVillageDefaults(it.id) }
                _selectedTu = currentLocation?.tu
                _selectedHealthFacility = currentLocation?.healthFacility
                when (pref.getCurrentLanguage()) {
                    ENGLISH -> {
                        stateDropdownEntry = user.state.name
                        districtDropdownEntry = user.district.name
                        blockDropdownEntry = user.block.name
                        tuDropdownEntries = tus.map { it.name }.toTypedArray()
                        healthFacilityDropdownEntries =
                            healthFacilities.map { it.name }.toTypedArray()
                        villageDropdownEntries = user.villages.map { it.name }.toTypedArray()

                    }

                   Languages.HINDI -> {
                        stateDropdownEntry =
                            user.state.let { it.nameHindi ?: it.name }
                        districtDropdownEntry =
                            user.district.let { it.nameHindi ?: it.name }
                        blockDropdownEntry =
                            user.block.let { it.nameHindi ?: it.name }
                        tuDropdownEntries =
                            tus.map { it.nameHindi ?: it.name }.toTypedArray()
                        healthFacilityDropdownEntries =
                            healthFacilities.map { it.nameHindi ?: it.name }.toTypedArray()
                        villageDropdownEntries =
                            user.villages.map { it.nameHindi ?: it.name }.toTypedArray()
                    }

                    ASSAMESE -> {
                        stateDropdownEntry =
                            user.state.let { it.nameAssamese ?: it.name }
                        districtDropdownEntry =
                            user.district.let { it.nameAssamese ?: it.name }
                        blockDropdownEntry =
                            user.block.let { it.nameAssamese ?: it.name }
                        tuDropdownEntries =
                            tus.map { it.nameAssamese ?: it.name }.toTypedArray()
                        healthFacilityDropdownEntries =
                            healthFacilities.map { it.nameAssamese ?: it.name }.toTypedArray()
                        villageDropdownEntries =
                            user.villages.map { it.nameAssamese ?: it.name }.toTypedArray()
                    }

                }

            }
            _state.value = State.SUCCESS
        }
    }

    fun isLocationSet(): Boolean {
        return if (state.value == State.LOADING)
            false
        else
            currentLocation?.let { pref.getVillageDefaults(it.village.id)?.isComplete == true } == true
    }

    fun setVillage(i: Int) {
        _selectedVillage = user.villages[i]
        _selectedVillageDefaults = pref.getVillageDefaults(user.villages[i].id)

    }

    fun setTu(i: Int) {
        _selectedTu = tus[i]
    }

    fun setHealthFacility(i: Int) {
        _selectedHealthFacility = healthFacilities[i]
    }

    fun isTuRequired(): Boolean = tus.isNotEmpty()

    fun isHealthFacilityRequired(): Boolean = healthFacilities.isNotEmpty()

    fun saveCurrentLocation(pinCode: String, residentialAreaId: Int) {
        val locationRecord = LocationRecord(
            country = LocationEntity(1, "India"),
            state = user.state,
            district = user.district,
            block = user.block,
            tu = selectedTu,
            healthFacility = selectedHealthFacility,
            village = selectedVillage!!
        )
        pref.saveLocationRecord(locationRecord)
        pref.saveVillageDefaults(
            locationRecord.village.id,
            VillageDefaults(pinCode = pinCode, residentialAreaId = residentialAreaId)
        )
    }

    private fun LocationEntity.localizedName(): String {
        return when (pref.getCurrentLanguage()) {
            ENGLISH -> name
            Languages.HINDI -> nameHindi ?: name
            ASSAMESE -> nameAssamese ?: name
        }
    }

}
