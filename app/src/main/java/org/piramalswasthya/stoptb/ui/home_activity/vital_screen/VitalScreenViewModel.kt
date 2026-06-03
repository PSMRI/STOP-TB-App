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
import org.piramalswasthya.stoptb.model.Gender
import org.piramalswasthya.stoptb.model.ReferalCache
import org.piramalswasthya.stoptb.model.VitalCache
import java.util.Locale
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
        val referredFor: List<String>,
        val triggers: List<String>
    )

    data class RiskFactorUiState(
        val isMale: Boolean,
        val isPregnant: Boolean
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

    private val _riskFactorUiState = MutableLiveData<RiskFactorUiState>()
    val riskFactorUiState: LiveData<RiskFactorUiState>
        get() = _riskFactorUiState

    private var benCache: BenRegCache? = null

    init {
        viewModelScope.launch {
            benRepo.getBenFromId(benId)?.let { ben ->
                benCache = ben
                _benName.value = listOfNotNull(ben.firstName, ben.lastName).joinToString(" ")
                _benAgeGender.value = "${ben.age} ${ben.ageUnit?.name} | ${ben.gender?.name}"
                _referredFor.value = getDefaultReferralTests(ben).joinToString(", ")
                _riskFactorUiState.value = RiskFactorUiState(
                    isMale = ben.gender == Gender.MALE,
                    isPregnant = isBeneficiaryPregnant(ben)
                )
            } ?: run {
                _riskFactorUiState.value = RiskFactorUiState(isMale = false, isPregnant = false)
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
        rbs: String?,
        pallorId: Int?,
        pallor: String?,
        icterusId: Int?,
        icterus: String?,
        cyanosisId: Int?,
        cyanosis: String?,
        clubbingId: Int?,
        clubbing: String?,
        lymphadenopathyId: Int?,
        lymphadenopathy: String?,
        oedemaId: Int?,
        oedema: String?,
        keyPopulationRiskFactorIds: List<Int>,
        keyPopulationRiskFactors: List<String>,
        hivStatusId: Int?,
        hivStatus: String?
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
                            rbs = rbs,
                            pallorId = pallorId,
                            pallor = pallor,
                            icterusId = icterusId,
                            icterus = icterus,
                            cyanosisId = cyanosisId,
                            cyanosis = cyanosis,
                            clubbingId = clubbingId,
                            clubbing = clubbing,
                            lymphadenopathyId = lymphadenopathyId,
                            lymphadenopathy = lymphadenopathy,
                            oedemaId = oedemaId,
                            oedema = oedema,
                            keyPopulationRiskFactorIds = keyPopulationRiskFactorIds,
                            keyPopulationRiskFactors = keyPopulationRiskFactors,
                            hivStatusId = hivStatusId,
                            hivStatus = hivStatus
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
                        pallorId = pallorId,
                        pallor = pallor?.trim()?.takeIf { it.isNotBlank() },
                        icterusId = icterusId,
                        icterus = icterus?.trim()?.takeIf { it.isNotBlank() },
                        cyanosisId = cyanosisId,
                        cyanosis = cyanosis?.trim()?.takeIf { it.isNotBlank() },
                        clubbingId = clubbingId,
                        clubbing = clubbing?.trim()?.takeIf { it.isNotBlank() },
                        lymphadenopathyId = lymphadenopathyId,
                        lymphadenopathy = lymphadenopathy?.trim()?.takeIf { it.isNotBlank() },
                        oedemaId = oedemaId,
                        oedema = oedema?.trim()?.takeIf { it.isNotBlank() },
                        keyPopulationRiskFactorIds = keyPopulationRiskFactorIds,
                        keyPopulationRiskFactors = keyPopulationRiskFactors,
                        hivStatusId = hivStatusId,
                        hivStatus = hivStatus?.trim()?.takeIf { it.isNotBlank() },
                        referralToHwcNeeded = thresholdEvaluation.shouldRefer,
                        referralTriggers = thresholdEvaluation.triggers,
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
        rbs: String?,
        pallorId: Int?,
        pallor: String?,
        icterusId: Int?,
        icterus: String?,
        cyanosisId: Int?,
        cyanosis: String?,
        clubbingId: Int?,
        clubbing: String?,
        lymphadenopathyId: Int?,
        lymphadenopathy: String?,
        oedemaId: Int?,
        oedema: String?,
        keyPopulationRiskFactorIds: List<Int>,
        keyPopulationRiskFactors: List<String>,
        hivStatusId: Int?,
        hivStatus: String?
    ): Boolean {
        return !temperatureOption.isNullOrBlank() ||
                !pulseRateOption.isNullOrBlank() ||
                !bpSystolic.isNullOrBlank() ||
                !bpDiastolic.isNullOrBlank() ||
                !height.isNullOrBlank() ||
                !weight.isNullOrBlank() ||
                !rbs.isNullOrBlank() ||
                pallorId != null ||
                !pallor.isNullOrBlank() ||
                icterusId != null ||
                !icterus.isNullOrBlank() ||
                cyanosisId != null ||
                !cyanosis.isNullOrBlank() ||
                clubbingId != null ||
                !clubbing.isNullOrBlank() ||
                lymphadenopathyId != null ||
                !lymphadenopathy.isNullOrBlank() ||
                oedemaId != null ||
                !oedema.isNullOrBlank() ||
                keyPopulationRiskFactorIds.isNotEmpty() ||
                keyPopulationRiskFactors.isNotEmpty() ||
                hivStatusId != null ||
                !hivStatus.isNullOrBlank()
    }

    fun resetState() {
        _state.value = State.IDLE
    }

    /**
     * True when the beneficiary's ben-registration answer to "Are you Pregnant?" is Yes.
     * Evaluated lazily from benCache (loaded asynchronously in init).
     */
    val isPregnant: Boolean
        get() = benCache?.let { ben ->
            val rs = ben.genDetails?.reproductiveStatus
            ben.genDetails?.reproductiveStatusId == 1 ||
                rs.equals("Yes", ignoreCase = true) ||
                rs?.trim()?.lowercase()?.contains("pregnant") == true
        } ?: false

    /** True when the beneficiary's registered gender is Male. */
    val isMale: Boolean
        get() = benCache?.gender == Gender.MALE

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
        return value?.toString()
    }

    fun shouldShowTemperatureReferral(option: String?): Boolean = mapTemperatureOptionToValue(option)?.let { it >= 100.0 } == true

    fun shouldShowPulseReferral(option: String?): Boolean = mapPulseOptionToValue(option)?.let { it < 60 || it > 90 } == true






    fun shouldShowBpReferral(bpSystolic: String?, bpDiastolic: String?): Boolean {
        val systolic = bpSystolic?.trim()?.toIntOrNull()
        val diastolic = bpDiastolic?.trim()?.toIntOrNull()
        return (systolic != null && (systolic < 90 || systolic >= 140)) ||
                (diastolic != null && (diastolic < 60 || diastolic >= 90))
    }

    fun shouldShowRbsReferral(rbs: String?): Boolean {
        val rbsValue = rbs?.trim()?.toDoubleOrNull() ?: return false
        return rbsValue >= 100.0
    }

    private fun evaluateThresholds(
        temperatureOption: String?,
        pulseRateOption: String?,
        bpSystolic: String?,
        bpDiastolic: String?,
        rbs: String?
    ): ThresholdEvaluation {
        val triggers = getReferralTriggers(
            temperatureOption = temperatureOption,
            pulseRateOption = pulseRateOption,
            bpSystolic = bpSystolic,
            bpDiastolic = bpDiastolic,
            rbs = rbs
        )
        val shouldRefer = triggers.isNotEmpty()
        return ThresholdEvaluation(
            shouldRefer = shouldRefer,
            referredFor = getDefaultReferralTests(benCache),
            triggers = triggers
        )
    }

    private fun getReferralTriggers(
        temperatureOption: String?,
        pulseRateOption: String?,
        bpSystolic: String?,
        bpDiastolic: String?,
        rbs: String?
    ): List<String> {
        val triggers = mutableListOf<String>()
        val temperature = mapTemperatureOptionToValue(temperatureOption)
        if (temperature != null && temperature >= 100.0) {
            triggers += "High Temperature: ${temperature.stripTrailingZeros()} F"
        }

        val pulse = mapPulseOptionToValue(pulseRateOption)
        if (pulse != null && pulse > 90) {
            triggers += "High Pulse: $pulse BPM"
        } else if (pulse != null && pulse < 60) {
            triggers += "Low Pulse: $pulse BPM"
        }

        val systolic = bpSystolic?.trim()?.toIntOrNull()
        val diastolic = bpDiastolic?.trim()?.toIntOrNull()
        if (systolic != null && diastolic != null) {
            when {
                systolic >= 140 || diastolic >= 90 -> triggers += "High BP: $systolic/$diastolic mmHg"
                systolic < 90 || diastolic < 60 -> triggers += "Low BP: $systolic/$diastolic mmHg"
            }
        }

        val rbsValue = rbs?.trim()?.toDoubleOrNull()
        if (rbsValue != null && rbsValue >= 100.0) {
            triggers += "High RBS: ${rbsValue.stripTrailingZeros()} mg/dl"
        }
        return triggers
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
        return if (ben != null && isBeneficiaryPregnant(ben)) {
            listOf("True NAT")
        } else {
            listOf("Digital Chest X-ray")
        }
    }

    companion object {
        fun isBeneficiaryPregnant(ben: BenRegCache): Boolean {
            val gen = ben.genDetails ?: return false
            if (gen.reproductiveStatusId == 1) return true
            val status = gen.reproductiveStatus?.trim()?.lowercase(Locale.ENGLISH).orEmpty()
            return status == "yes" ||
                status == "pregnant woman" ||
                status == "women pregnant"
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
        return option?.trim()?.toIntOrNull()
    }

    private fun Double.stripTrailingZeros(): String =
        if (this % 1.0 == 0.0) toInt().toString() else toString()
}
