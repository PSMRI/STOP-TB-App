package org.piramalswasthya.stoptb.ui.home_activity.vital_screen

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
import org.piramalswasthya.stoptb.model.VitalCache
import org.piramalswasthya.stoptb.repositories.BenRepo
import org.piramalswasthya.stoptb.repositories.NcdReferalRepo
import org.piramalswasthya.stoptb.repositories.VitalRepo
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class VitalScreenViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val benRepo: BenRepo,
    private val vitalRepo: VitalRepo,
    private val preferenceDao: PreferenceDao,
    private val referralStatusManager: ReferralStatusManager,
    private val ncdReferalRepo: NcdReferalRepo
) : ViewModel() {

    enum class ReferralType {
        TB
    }

    data class ThresholdEvaluation(
        val shouldRefer: Boolean,
        val referredFor: List<String>
    )

    enum class State {
        IDLE, SAVING, SAVE_SUCCESS, SAVE_FAILED
    }

    val benId = VitalScreenFragmentArgs.fromSavedStateHandle(savedStateHandle).benId
    val benRegId = VitalScreenFragmentArgs.fromSavedStateHandle(savedStateHandle).benRegId
    val autoFlow = VitalScreenFragmentArgs.fromSavedStateHandle(savedStateHandle).autoFlow

    private val _benName = MutableLiveData<String>()
    val benName: LiveData<String>
        get() = _benName

    private val _benAgeGender = MutableLiveData<String>()
    val benAgeGender: LiveData<String>
        get() = _benAgeGender

    private val _existingVitals = MutableLiveData<VitalCache?>()
    val existingVitals: LiveData<VitalCache?>
        get() = _existingVitals

    private val _referredFor = MutableLiveData("")
    val referredFor: LiveData<String>
        get() = _referredFor

    private val _state = MutableLiveData(State.IDLE)
    val state: LiveData<State>
        get() = _state

    private var benCache: BenRegCache? = null

    init {
        viewModelScope.launch {
            benRepo.getBenFromId(benId)?.let { ben ->
                benCache = ben
                _benName.value = listOfNotNull(ben.firstName, ben.lastName).joinToString(" ")
                _benAgeGender.value = "${ben.age} ${ben.ageUnit?.name} | ${ben.gender?.name}"
                _referredFor.value = getDefaultReferralTests(ben).joinToString(", ")
            }
            _existingVitals.value = vitalRepo.getVitals(benId)
        }
    }

    fun calculateBmi(heightCm: String?, weightKg: String?): Double? {
        val height = heightCm?.trim()?.toDoubleOrNull()
        val weight = weightKg?.trim()?.toDoubleOrNull()
        if (height == null || weight == null || height <= 0.0 || weight <= 0.0) return null
        val heightInMeter = height / 100.0
        return ((weight / (heightInMeter * heightInMeter)) * 100).toInt() / 100.0
    }

    fun saveVitals(
        temperatureOption: String?,
        pulseRateOption: String?,
        bpSystolic: String?,
        bpDiastolic: String?,
        height: String?,
        weight: String?,
        bmi: String?,
        rbs: String?
    ) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    if (!hasAnyVitalsInput(
                            temperatureOption = temperatureOption,
                            pulseRateOption = pulseRateOption,
                            bpSystolic = bpSystolic,
                            bpDiastolic = bpDiastolic,
                            height = height,
                            weight = weight,
                            rbs = rbs
                        )
                    ) {
                        _state.postValue(State.SAVE_SUCCESS)
                        return@withContext
                    }
                    _state.postValue(State.SAVING)
                    val thresholdEvaluation = evaluateThresholds(
                        temperatureOption = temperatureOption,
                        pulseRateOption = pulseRateOption,
                        bpSystolic = bpSystolic,
                        bpDiastolic = bpDiastolic,
                        rbs = rbs
                    )
                    val cache = (_existingVitals.value ?: VitalCache(benId = benId,benRegId = benRegId)).copy(
                        capturedAt = System.currentTimeMillis(),
                        temperature = mapTemperatureOptionToValue(temperatureOption),
                        pulseRate = mapPulseOptionToValue(pulseRateOption),
                        bpSystolic = bpSystolic?.trim()?.toIntOrNull(),
                        bpDiastolic = bpDiastolic?.trim()?.toIntOrNull(),
                        respiratoryRate = null,
                        spo2 = null,
                        height = height?.trim()?.toDoubleOrNull(),
                        weight = weight?.trim()?.toDoubleOrNull(),
                        bmi = bmi?.trim()?.toDoubleOrNull(),
                        rbs = rbs?.trim()?.toDoubleOrNull(),
                        syncState = SyncState.UNSYNCED
                    )
                    vitalRepo.saveVitalsAndSync(cache)
                    if (thresholdEvaluation.shouldRefer) {
                        buildAutoReferral(thresholdEvaluation.referredFor)?.let {
                            ncdReferalRepo.saveReferedNCD(it)
                            referralStatusManager.markAsReferred(benId, ReferralType.TB.name)
                        }
                    }
                    _existingVitals.postValue(cache)
                    _state.postValue(State.SAVE_SUCCESS)
                } catch (e: Exception) {
                    Timber.e(e, "Saving vitals failed for benId=%s", benId)
                    _state.postValue(State.SAVE_FAILED)
                }
            }
        }
    }

    private fun hasAnyVitalsInput(
        temperatureOption: String?,
        pulseRateOption: String?,
        bpSystolic: String?,
        bpDiastolic: String?,
        height: String?,
        weight: String?,
        rbs: String?
    ): Boolean {
        return !temperatureOption.isNullOrBlank() ||
            !pulseRateOption.isNullOrBlank() ||
            !bpSystolic.isNullOrBlank() ||
            !bpDiastolic.isNullOrBlank() ||
            !height.isNullOrBlank() ||
            !weight.isNullOrBlank() ||
            !rbs.isNullOrBlank()
    }

    fun resetState() {
        _state.value = State.IDLE
    }

    fun getTemperatureDisplayValue(value: Double?): String? {
        return when {
            value == null -> null
            value >= 100.0 -> ">= 100"
            value >= 99.5 -> "99.5"
            value >= 98.5 -> "98.5"
            else -> "97.5"
        }
    }

    fun getPulseDisplayValue(value: Int?): String? {
        return when {
            value == null -> null
            value < 60 -> "Less than 60"
            value <= 70 -> "60-70"
            value <= 80 -> "70-80"
            value > 90 -> "More than 90"
            else -> null
        }
    }

    fun shouldShowTemperatureReferral(option: String?): Boolean = mapTemperatureOptionToValue(option)?.let { it >= 100.0 } == true

    fun shouldShowPulseReferral(option: String?): Boolean = mapPulseOptionToValue(option)?.let { it < 60 || it > 90 } == true

    fun shouldShowBpReferral(bpSystolic: String?, bpDiastolic: String?): Boolean {
        val systolic = bpSystolic?.trim()?.toIntOrNull()
        val diastolic = bpDiastolic?.trim()?.toIntOrNull()
        return (systolic != null && (systolic < 90 || systolic > 140)) ||
            (diastolic != null && (diastolic < 60 || diastolic > 90))
    }

    fun shouldShowRbsReferral(rbs: String?): Boolean {
        val rbsValue = rbs?.trim()?.toDoubleOrNull() ?: return false
        return rbsValue < 60.0 || rbsValue > 90.0
    }

    private fun evaluateThresholds(
        temperatureOption: String?,
        pulseRateOption: String?,
        bpSystolic: String?,
        bpDiastolic: String?,
        rbs: String?
    ): ThresholdEvaluation {
        val shouldRefer = shouldShowTemperatureReferral(temperatureOption) ||
            shouldShowPulseReferral(pulseRateOption) ||
            shouldShowBpReferral(bpSystolic, bpDiastolic) ||
            shouldShowRbsReferral(rbs)
        return ThresholdEvaluation(
            shouldRefer = shouldRefer,
            referredFor = getDefaultReferralTests(benCache)
        )
    }

    private fun buildAutoReferral(selectedTests: List<String>): ReferalCache? {
        if (selectedTests.isEmpty()) return null
        val user = preferenceDao.getLoggedInUser() ?: return null
        return ReferalCache(
            benId = benId,
            referredToInstituteID = 2,
            refrredToAdditionalServiceList = selectedTests,
            referredToInstituteName = "HWC",
            referralReason = "Vital Screening Referral",
            revisitDate = System.currentTimeMillis(),
            vanID = user.vanId,
            parkingPlaceID = user.serviceMapId,
            beneficiaryRegID = benRegId,
            benVisitID = 0L,
            visitCode = 0L,
            providerServiceMapID = user.serviceMapId,
            createdBy = user.userName,
            type = ReferralType.TB.name,
            isSpecialist = false,
            syncState = SyncState.UNSYNCED
        )
    }

    private fun getDefaultReferralTests(ben: BenRegCache?): List<String> {
        val isUnderFive = ben?.let {
            when (it.ageUnit?.name) {
                "YEARS" -> it.age <= 5
                null -> false
                else -> true
            }
        } == true
        val isPregnant = ben?.genDetails?.reproductiveStatus?.contains("preg", ignoreCase = true) == true
        return if (isUnderFive && isPregnant) {
            listOf("True NAT")
        } else {
            listOf("Digital Chest X-ray")
        }
    }

    private fun mapTemperatureOptionToValue(option: String?): Double? {
        val normalized = option?.trim()
        return when (normalized) {
            "97.5" -> 97.5
            "98.5" -> 98.5
            "99.5" -> 99.5
            ">= 100" -> 100.0
            else -> normalized
                ?.removePrefix(">=")
                ?.trim()
                ?.toDoubleOrNull()
        }
    }

    private fun mapPulseOptionToValue(option: String?): Int? {
        val normalized = option?.trim()
        return when (normalized) {
            "Less than 60" -> 59
            "less than 60" -> 59
            "60-70" -> 65
            "70-80" -> 75
            "More than 90" -> 91
            "more than 90" -> 91
            else -> normalized?.toIntOrNull()
        }
    }
}
