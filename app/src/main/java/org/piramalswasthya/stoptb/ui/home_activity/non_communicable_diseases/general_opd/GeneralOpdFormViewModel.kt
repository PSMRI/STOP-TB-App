package org.piramalswasthya.stoptb.ui.home_activity.non_communicable_diseases.general_opd

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.piramalswasthya.stoptb.configuration.GeneralOpdDataset
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.model.GeneralOpdCache
import org.piramalswasthya.stoptb.model.getAgeGenderDisplayString
import org.piramalswasthya.stoptb.repositories.AbdmCareContextRepo
import org.piramalswasthya.stoptb.repositories.BenRepo
import org.piramalswasthya.stoptb.repositories.TBRepo
import org.piramalswasthya.stoptb.network.NetworkResult
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class GeneralOpdFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    preferenceDao: PreferenceDao,
    @ApplicationContext context: Context,
    private val benRepo: BenRepo,
    private val tbRepo: TBRepo,
    private val abdmCareContextRepo: AbdmCareContextRepo
) : ViewModel() {

    val benId = GeneralOpdFormFragmentArgs.fromSavedStateHandle(savedStateHandle).benId
    val viewOnly = GeneralOpdFormFragmentArgs.fromSavedStateHandle(savedStateHandle).viewOnly
    val autoFlow = GeneralOpdFormFragmentArgs.fromSavedStateHandle(savedStateHandle).autoFlow
    val generalOpdFlow = GeneralOpdFormFragmentArgs.fromSavedStateHandle(savedStateHandle).generalOpdFlow

    enum class State {
        IDLE, SAVING, SAVE_SUCCESS, SAVE_FAILED, SKIP_SUCCESS
    }

    sealed class CareContextState {
        object Idle : CareContextState()
        data class Loading(val message: String) : CareContextState()
        data class OtpRequired(
            val abhaNumber: String,
            val healthId: String,
            val visitCode: Long,
            val message: String? = null
        ) : CareContextState()
        data class Completed(val message: String) : CareContextState()
        data class Unavailable(val message: String) : CareContextState()
    }

    data class PendingCareContext(
        val txnId: String,
        val beneficiaryId: Long,
        val healthId: String,
        val healthIdNumber: String,
        val visitCode: Long,
        val providerServiceMapId: Int
    )

    private val _state = MutableLiveData(State.IDLE)
    val state: LiveData<State> = _state

    private val _careContextState = MutableLiveData<CareContextState>(CareContextState.Idle)
    val careContextState: LiveData<CareContextState> = _careContextState

    private val _benName = MutableLiveData<String>()
    val benName: LiveData<String> = _benName

    private val _benAgeGender = MutableLiveData<String>()
    val benAgeGender: LiveData<String> = _benAgeGender

    private val _recordExists = MutableLiveData<Boolean>()
    val recordExists: LiveData<Boolean> = _recordExists

    private val dataset = GeneralOpdDataset(context, preferenceDao.getCurrentLanguage())
    val formList = dataset.listFlow
    private val providerServiceMapId = preferenceDao.getLoggedInUser()?.serviceMapId ?: -1

    private lateinit var generalOpdCache: GeneralOpdCache
    private var pendingCareContext: PendingCareContext? = null

    init {
        viewModelScope.launch {
            val ben = benRepo.getBenFromId(benId)
            ben?.let {
                _benName.value = "${it.firstName} ${it.lastName.orEmpty()}".trim()
                _benAgeGender.value = it.getAgeGenderDisplayString()
                generalOpdCache = GeneralOpdCache(benId = it.beneficiaryId)
            }

            tbRepo.getGeneralOpd(benId)?.let {
                generalOpdCache = it
                _recordExists.value = true
            } ?: run {
                _recordExists.value = false
            }

            dataset.setUpPage(if (_recordExists.value == true) generalOpdCache else null)
        }
    }

    fun updateListOnValueChanged(formId: Int, index: Int) {
        viewModelScope.launch {
            dataset.updateList(formId, index)
        }
    }

    fun validateBusinessRules(): Int = dataset.validateBusinessRules()

    fun saveForm() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    _state.postValue(State.SAVING)
                    if (dataset.hasAnyData()) {
                        dataset.mapValues(generalOpdCache)
                        generalOpdCache.syncState = SyncState.UNSYNCED
                        tbRepo.saveGeneralOpd(generalOpdCache)
                    }
                    _state.postValue(State.SAVE_SUCCESS)
                } catch (e: Exception) {
                    Timber.e(e, "saving general opd data failed")
                    _state.postValue(State.SAVE_FAILED)
                }
            }
        }
    }

    fun skipForm() {
        _state.value = State.SKIP_SUCCESS
    }

    fun startCareContextFlow() {
        viewModelScope.launch {
            _careContextState.value = CareContextState.Loading("Preparing Care Context...")
            try {
                val ben = benRepo.getBenFromId(benId)
                    ?: run {
                        _careContextState.value = CareContextState.Unavailable("Beneficiary not found.")
                        return@launch
                    }

                val benRegId = ben.benRegId.takeIf { it > 0L }
                    ?: run {
                        _careContextState.value = CareContextState.Unavailable(
                            "Beneficiary sync is pending. Care Context cannot be created yet."
                        )
                        return@launch
                    }

                val visitCode = waitForVisitCode()
                    ?: run {
                        _careContextState.value = CareContextState.Unavailable(
                            "Visit code has not been synced yet. Please try again after sync."
                        )
                        return@launch
                    }

                when (val facilityResult = abdmCareContextRepo.saveFacilityAgainstVisit(
                    visitCode = visitCode,
                    providerServiceMapId = providerServiceMapId
                )) {
                    is NetworkResult.Error -> {
                        _careContextState.value = CareContextState.Unavailable(facilityResult.message)
                        return@launch
                    }

                    NetworkResult.NetworkError -> {
                        _careContextState.value = CareContextState.Unavailable(
                            "Internet issue. Unable to save ABDM facility for visit."
                        )
                        return@launch
                    }

                    is NetworkResult.Success -> Unit
                }

                when (val healthResult = abdmCareContextRepo.getBeneficiaryHealthId(
                    beneficiaryRegID = benRegId,
                    beneficiaryID = ben.beneficiaryId
                )) {
                    is NetworkResult.Success -> {
                        val health = healthResult.data
                        when (val otpResult = abdmCareContextRepo.generateOtpForCareContext(
                            healthID = health.healthId,
                            healthIdNumber = health.healthIdNumber,
                            providerServiceMapId = providerServiceMapId
                        )) {
                            is NetworkResult.Success -> {
                                val txnId = otpResult.data.txnId
                                if (txnId.isNullOrBlank()) {
                                    _careContextState.value = CareContextState.Unavailable(
                                        "Unable to generate Care Context OTP."
                                    )
                                    return@launch
                                }
                                pendingCareContext = PendingCareContext(
                                    txnId = txnId,
                                    beneficiaryId = ben.beneficiaryId,
                                    healthId = health.healthId,
                                    healthIdNumber = health.healthIdNumber,
                                    visitCode = visitCode,
                                    providerServiceMapId = providerServiceMapId
                                )
                                _careContextState.value = CareContextState.OtpRequired(
                                    abhaNumber = health.healthIdNumber,
                                    healthId = health.healthId,
                                    visitCode = visitCode
                                )
                            }

                            is NetworkResult.Error -> {
                                _careContextState.value = CareContextState.Unavailable(otpResult.message)
                            }

                            NetworkResult.NetworkError -> {
                                _careContextState.value = CareContextState.Unavailable(
                                    "Internet issue. Unable to generate Care Context OTP."
                                )
                            }
                        }
                    }

                    is NetworkResult.Error -> {
                        _careContextState.value = CareContextState.Unavailable(healthResult.message)
                    }

                    NetworkResult.NetworkError -> {
                        _careContextState.value = CareContextState.Unavailable(
                            "Internet issue. Unable to fetch ABHA details."
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Care Context flow failed")
                _careContextState.value = CareContextState.Unavailable(
                    e.message ?: "Care Context flow failed"
                )
            }
        }
    }

    fun verifyCareContextOtp(otp: String) {
        val pending = pendingCareContext ?: run {
            _careContextState.value = CareContextState.Unavailable("Care Context session expired. Please try again.")
            return
        }
        viewModelScope.launch {
            _careContextState.value = CareContextState.Loading("Verifying OTP and creating Care Context...")
            when (val result = abdmCareContextRepo.validateOtpAndCreateCareContext(
                otp = otp,
                txnId = pending.txnId,
                beneficiaryID = pending.beneficiaryId,
                healthID = pending.healthId,
                healthIdNumber = pending.healthIdNumber,
                visitCode = pending.visitCode,
                visitCategory = "General OPD",
                providerServiceMapId = pending.providerServiceMapId
            )) {
                is NetworkResult.Success -> {
                    pendingCareContext = null
                    _careContextState.value = CareContextState.Completed(
                        result.data.response ?: "Care Context added successfully"
                    )
                }

                is NetworkResult.Error -> {
                    _careContextState.value = CareContextState.OtpRequired(
                        abhaNumber = pending.healthIdNumber,
                        healthId = pending.healthId,
                        visitCode = pending.visitCode,
                        message = result.message
                    )
                }

                NetworkResult.NetworkError -> {
                    _careContextState.value = CareContextState.OtpRequired(
                        abhaNumber = pending.healthIdNumber,
                        healthId = pending.healthId,
                        visitCode = pending.visitCode,
                        message = "Internet issue. Please try again."
                    )
                }
            }
        }
    }

    fun skipCareContext() {
        pendingCareContext = null
        _careContextState.value = CareContextState.Completed("Care Context skipped.")
    }

    fun clearCareContextState() {
        _careContextState.value = CareContextState.Idle
    }

    private suspend fun waitForVisitCode(): Long? {
        repeat(15) {
            val code = tbRepo.getGeneralOpd(benId)?.visitCode?.takeIf { visitCode -> visitCode > 0L }
            if (code != null) return code
            delay(1000)
        }
        return null
    }
}
