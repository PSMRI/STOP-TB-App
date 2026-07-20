package org.piramalswasthya.stoptb.ui.home_activity.non_communicable_diseases.tb_suspected.quick

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.piramalswasthya.stoptb.configuration.TBSuspectedQuickDataset
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.model.TBDiagnosticsCache
import org.piramalswasthya.stoptb.model.getAgeGenderDisplayString
import org.piramalswasthya.stoptb.repositories.BenRepo
import org.piramalswasthya.stoptb.repositories.TBRepo
import org.piramalswasthya.stoptb.repositories.VitalRepo
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class TBSuspectedQuickViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    preferenceDao: PreferenceDao,
    @ApplicationContext context: Context,
    private val tbRepo: TBRepo,
    private val benRepo: BenRepo,
    private val vitalRepo: VitalRepo
) : ViewModel() {

    enum class State {
        IDLE, SAVING, SAVE_SUCCESS, SAVE_FAILED
    }

    private val args = TBSuspectedQuickFragmentArgs.fromSavedStateHandle(savedStateHandle)
    val benId = args.benId
    val viewOnly = args.viewOnly
    val autoFlow = args.autoFlow
    val generalOpdFlow = args.generalOpdFlow
    val referralType = args.referralType

    private val dataset = TBSuspectedQuickDataset(context, preferenceDao.getCurrentLanguage())
    val formList = dataset.listFlow

    private val _benName = MutableLiveData<String>()
    val benName: LiveData<String> = _benName

    private val _benAgeGender = MutableLiveData<String>()
    val benAgeGender: LiveData<String> = _benAgeGender

    private val _state = MutableLiveData(State.IDLE)
    val state: LiveData<State> = _state

    private val _showSubmit = MutableLiveData(true)
    val showSubmit: LiveData<Boolean> = _showSubmit

    private lateinit var tbDiagnostics: TBDiagnosticsCache

    init {
        viewModelScope.launch {
            val ben = benRepo.getBenFromId(benId)?.also { beneficiary ->
                _benName.value = listOfNotNull(beneficiary.firstName, beneficiary.lastName).joinToString(" ")
                _benAgeGender.value = beneficiary.getAgeGenderDisplayString()
                tbDiagnostics = TBDiagnosticsCache(benId = beneficiary.beneficiaryId)
            }
            val tbScreening = tbRepo.getTBScreening(benId)
            val vital = vitalRepo.getVitals(benId)
            tbRepo.getTBDiagnostics(benId)?.let {
                tbDiagnostics = it
            } ?: tbRepo.getTBSuspected(benId)?.let { legacySuspected ->
                // Existing installs may already have diagnostics captured in TB_SUSPECTED.
                // Use it only as a read fallback; new saves go to TB_DIAGNOSTICS.
                tbDiagnostics = TBDiagnosticsCache(
                    benId = legacySuspected.benId,
                    visitDate = legacySuspected.visitDate,
                    nikshayId = legacySuspected.nikshayId,
                    isChestXRayDone = legacySuspected.isChestXRayDone,
                    chestXRayResult = legacySuspected.chestXRayResult,
                    isSputumCollected = legacySuspected.isSputumCollected,
                    sputumSubmittedAt = legacySuspected.sputumSubmittedAt,
                    isNaatConducted = legacySuspected.isNaatConducted,
                    naatResult = legacySuspected.naatResult,
                    recommendedForLiquidCultureTest = legacySuspected.recommendedForLiquidCultureTest,
                    isLiquidCultureConducted = legacySuspected.isLiquidCultureConducted,
                    liquidCultureResult = legacySuspected.liquidCultureResult,
                    isTBConfirmed = legacySuspected.isTBConfirmed,
                    isConfirmed = legacySuspected.isConfirmed,
                    latitude = legacySuspected.latitude,
                    longitude = legacySuspected.longitude,
                    address = legacySuspected.address
                )
            }
            dataset.setUpPage(
                ben,
                tbScreening,
                if (::tbDiagnostics.isInitialized) tbDiagnostics else null,
                vital = vital,
                referralMode = viewOnly,
                referralType = referralType
            )
            _showSubmit.value = dataset.shouldShowSubmit()
        }
    }

    fun getChestXRayResult(): String? {
        return if (::tbDiagnostics.isInitialized) tbDiagnostics.chestXRayResult else null
    }

    fun getIsChestXRayDone(): Boolean? {
        return if (::tbDiagnostics.isInitialized) tbDiagnostics.isChestXRayDone else null
    }

    fun getNaatResult(): String? {
        return if (::tbDiagnostics.isInitialized) tbDiagnostics.naatResult else null
    }

    fun getTrueNatRifResult(): String? {
        return if (::tbDiagnostics.isInitialized) tbDiagnostics.trueNatRifResult else null
    }

    fun getIsNaatConducted(): Boolean? {
        return if (::tbDiagnostics.isInitialized) tbDiagnostics.isNaatConducted else null
    }

    fun repeatTest(orderType: String, customVisitCode: Int? = null) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    tbRepo.createProdigiOrder(benId, orderType, customVisitCode)
                } catch (e: Exception) {
                    Timber.e(e, "repeatTest failed for benId=%s", benId)
                }
            }
        }
    }

    fun saveForm() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    _state.postValue(State.SAVING)
                    dataset.mapValues(tbDiagnostics, 1)
                    
                    if (referralType == 6) {
                        if (tbDiagnostics.isReferredForDigitalChestXray == false) {
                            tbDiagnostics.xrayOrderStatus = "REFERRAL_DECLINED"
                        }
                    } else if (referralType == 7) {
                        if (tbDiagnostics.isSputumCollected == false) {
                            tbDiagnostics.trueNatOrderStatus = "REFERRAL_DECLINED"
                        }
                    }
                    
                    // Load existing row's id to UPDATE instead of INSERT new row
                    val existingId = tbRepo.getTBDiagnosticsById(benId)?.id ?: 0
                    if (existingId > 0) tbDiagnostics = tbDiagnostics.copy(id = existingId)
                    tbDiagnostics.syncState = SyncState.UNSYNCED
                    tbRepo.saveTBDiagnostics(tbDiagnostics)
                    
                    // Synchronously push order to server before completing form save
                    var orderPushSuccess = true
                    var orderPushError: String? = null
                    val hasXrayOrder = !tbDiagnostics.xrayOrderId.isNullOrBlank() && !tbDiagnostics.xrayOrderStatus.equals("FAILED", ignoreCase = true)
                    val hasTrueNatOrder = !tbDiagnostics.trueNatOrderId.isNullOrBlank() && !tbDiagnostics.trueNatOrderStatus.equals("FAILED", ignoreCase = true)

                    if (referralType == 6 && tbDiagnostics.isReferredForDigitalChestXray == true && !hasXrayOrder) {
                        val response = tbRepo.createProdigiOrder(benId, "XRAY_CHEST")
                        if (response is org.piramalswasthya.stoptb.helpers.NetworkResponse.Error) {
                            orderPushSuccess = false
                            orderPushError = response.message
                        }
                    } else if (referralType == 7 && tbDiagnostics.isSputumCollected == true && !hasTrueNatOrder) {
                        val response = tbRepo.createProdigiOrder(benId, "SPUTUM_TRUENAT")
                        if (response is org.piramalswasthya.stoptb.helpers.NetworkResponse.Error) {
                            orderPushSuccess = false
                            orderPushError = response.message
                        }
                    }
                    
                    if (orderPushSuccess) {
                        _state.postValue(State.SAVE_SUCCESS)
                    } else {
                        Timber.e("Pushing diagnostic order failed for benId=%s: %s", benId, orderPushError)
                        _state.postValue(State.SAVE_FAILED)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Saving diagnostics failed for benId=%s", benId)
                    _state.postValue(State.SAVE_FAILED)
                }
            }
        }
    }

    fun updateListOnValueChanged(formId: Int, index: Int) {
        viewModelScope.launch {
            dataset.updateList(formId, index)
            _showSubmit.value = dataset.shouldShowSubmit()
        }
    }

    fun resetState() {
        _state.value = State.IDLE
    }
}
