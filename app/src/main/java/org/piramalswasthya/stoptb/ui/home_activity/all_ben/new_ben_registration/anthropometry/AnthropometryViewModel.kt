package org.piramalswasthya.stoptb.ui.home_activity.all_ben.new_ben_registration.anthropometry

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.database.shared_preferences.ReferralStatusManager
import org.piramalswasthya.stoptb.model.BenRegCache
import org.piramalswasthya.stoptb.model.ReferalCache
import org.piramalswasthya.stoptb.repositories.BenRepo
import org.piramalswasthya.stoptb.repositories.NcdReferalRepo
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AnthropometryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val benRepo: BenRepo,
    private val preferenceDao: PreferenceDao,
    private val referralStatusManager: ReferralStatusManager,
    private val ncdReferalRepo: NcdReferalRepo
) : ViewModel() {

    enum class State {
        IDLE, SAVING, SAVE_SUCCESS, SAVE_FAILED
    }

    val benId = AnthropometryFragmentArgs.fromSavedStateHandle(savedStateHandle).benId
    val autoFlow = AnthropometryFragmentArgs.fromSavedStateHandle(savedStateHandle).autoFlow

    private val _benName = MutableLiveData<String>()
    val benName: LiveData<String> = _benName

    private val _benAgeGender = MutableLiveData<String>()
    val benAgeGender: LiveData<String> = _benAgeGender

    private val _existingAnthropometry = MutableLiveData<BenRegCache?>()
    val existingAnthropometry: LiveData<BenRegCache?> = _existingAnthropometry

    private val _state = MutableLiveData(State.IDLE)
    val state: LiveData<State> = _state

    private var benCache: BenRegCache? = null

    init {
        viewModelScope.launch {
            benRepo.getBenFromId(benId)?.let { ben ->
                benCache = ben
                _benName.value = listOfNotNull(ben.firstName, ben.lastName).joinToString(" ")
                _benAgeGender.value = "${ben.age} ${ben.ageUnit?.name} | ${ben.gender?.name}"
                _existingAnthropometry.value = ben
            }
        }
    }

    fun calculateBmi(heightCm: String?, weightKg: String?): Double? {
        val height = heightCm?.trim()?.toDoubleOrNull()
        val weight = weightKg?.trim()?.toDoubleOrNull()
        if (height == null || weight == null || height <= 0.0 || weight <= 0.0) return null
        val heightInMeter = height / 100.0
        return String.format("%.1f", weight / (heightInMeter * heightInMeter)).toDoubleOrNull()
    }

    fun saveAnthropometry(
        weightKg: String?,
        heightCm: String?,
        temperatureF: String?
    ) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    _state.postValue(State.SAVING)
                    val ben = benCache ?: benRepo.getBenFromId(benId)
                    if (ben == null) {
                        _state.postValue(State.SAVE_FAILED)
                        return@withContext
                    }

                    val temperature = temperatureF?.trim()?.toDoubleOrNull()
                    ben.weight = weightKg?.trim()?.toDoubleOrNull()
                    ben.height = heightCm?.trim()?.toDoubleOrNull()
                    ben.bmi = calculateBmi(heightCm, weightKg)
                    ben.temperature = temperature
                    ben.processed = if (ben.beneficiaryId < 0L) "N" else "U"
                    ben.syncState = SyncState.UNSYNCED
                    ben.updatedDate = System.currentTimeMillis()
                    ben.updatedBy = preferenceDao.getLoggedInUser()?.userName

                    benRepo.updateRecord(ben)
                    if (temperature != null && temperature >= 100.0) {
                        buildHwcReferral(ben)?.let {
                            ncdReferalRepo.saveReferedNCD(it)
                            referralStatusManager.markAsReferred(benId, "TB")
                        }
                    }
                    _state.postValue(State.SAVE_SUCCESS)
                } catch (e: Exception) {
                    Timber.e(e, "Saving anthropometry failed for benId=%s", benId)
                    _state.postValue(State.SAVE_FAILED)
                }
            }
        }
    }

    fun resetState() {
        _state.value = State.IDLE
    }

    private fun buildHwcReferral(ben: BenRegCache): ReferalCache? {
        val user = preferenceDao.getLoggedInUser() ?: return null
        return ReferalCache(
            benId = benId,
            referredToInstituteID = 2,
            refrredToAdditionalServiceList = listOf("Health and Wellness Centre"),
            referredToInstituteName = "HWC",
            referralReason = "Temperature >= 100 F",
            revisitDate = System.currentTimeMillis(),
            vanID = user.vanId,
            parkingPlaceID = user.serviceMapId,
            beneficiaryRegID = ben.benRegId,
            benVisitID = 0L,
            visitCode = 0L,
            providerServiceMapID = user.serviceMapId,
            createdBy = user.userName,
            type = "TB",
            isSpecialist = false,
            syncState = SyncState.UNSYNCED
        )
    }
}
