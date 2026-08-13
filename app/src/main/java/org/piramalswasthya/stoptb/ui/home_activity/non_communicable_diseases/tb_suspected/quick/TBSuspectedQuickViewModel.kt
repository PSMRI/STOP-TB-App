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
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.model.TBDiagnosticsCache
import org.piramalswasthya.stoptb.model.BenRegCache
import org.piramalswasthya.stoptb.model.TBScreeningCache
import org.piramalswasthya.stoptb.model.VitalCache
import org.piramalswasthya.stoptb.model.getAgeGenderDisplayString
import org.piramalswasthya.stoptb.repositories.BenRepo
import org.piramalswasthya.stoptb.repositories.TBRepo
import org.piramalswasthya.stoptb.repositories.VitalRepo
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class TBSuspectedQuickViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val preferenceDao: PreferenceDao,
    @ApplicationContext private val context: Context,
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

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    private val _showSubmit = MutableLiveData(true)
    val showSubmit: LiveData<Boolean> = _showSubmit

    private lateinit var tbDiagnostics: TBDiagnosticsCache

    init {
        viewModelScope.launch {
            var ben: BenRegCache? = null
            var tbScreening: TBScreeningCache? = null
            var vital: VitalCache? = null

            withContext(Dispatchers.IO) {
                tbRepo.getTBDiagnostics(benId)?.let {
                    tbDiagnostics = it
                } ?: tbRepo.getTBSuspected(benId)?.let { legacySuspected ->
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
                        isTBConfirmed = legacySuspected.isTBConfirmed
                    )
                } ?: run {
                    tbDiagnostics = TBDiagnosticsCache(benId = benId)
                }

                tbScreening = tbRepo.getTBScreening(benId)
                vitalRepo.getVitals(benId)?.let {
                    vital = it
                }
                benRepo.getBenFromId(benId)?.let {
                    ben = it
                    _benName.postValue(it.firstName + " " + it.lastName)
                    val age = it.age
                    val ageUnit = it.ageUnit?.name
                    val gender = it.gender?.name
                    _benAgeGender.postValue("$age $ageUnit / $gender")
                }

                val orderType = if (referralType == 6) "XRAY_CHEST" else "SPUTUM_TRUENAT"
                val hasLocalResult = if (orderType == "XRAY_CHEST") {
                    !tbDiagnostics.chestXRayResult.isNullOrBlank()
                } else {
                    !tbDiagnostics.naatResult.isNullOrBlank()
                }
                val isOrderActive = if (orderType == "XRAY_CHEST") {
                    val status = tbDiagnostics.xrayOrderStatus
                    status.equals("COMPLETED", ignoreCase = true) || status.equals("IN_PROGRESS", ignoreCase = true) || status.equals("AWAITING_PROVIDER_RESULT", ignoreCase = true)
                } else {
                    val status = tbDiagnostics.trueNatOrderStatus
                    status.equals("COMPLETED", ignoreCase = true) || status.equals("IN_PROGRESS", ignoreCase = true) || status.equals("AWAITING_PROVIDER_RESULT", ignoreCase = true)
                }
                if (!hasLocalResult && isOrderActive) {
                    try {
                        tbRepo.fetchOrderResult(benId, orderType)
                    } catch (e: Exception) {
                        Timber.e(e, "Pre-fetching results failed for $orderType")
                    }
                }
                if (orderType == "SPUTUM_TRUENAT" && tbDiagnostics.naatResult.equals("MTB detected", ignoreCase = true)) {
                    val hasLocalRifResult = !tbDiagnostics.trueNatRifResult.isNullOrBlank()
                    val isRifActive = tbDiagnostics.rifOrderStatus.equals("COMPLETED", ignoreCase = true) ||
                            tbDiagnostics.rifOrderStatus.equals("IN_PROGRESS", ignoreCase = true) ||
                            tbDiagnostics.rifOrderStatus.equals("AWAITING_PROVIDER_RESULT", ignoreCase = true)
                    if (!hasLocalRifResult && isRifActive) {
                        try {
                            tbRepo.fetchOrderResult(benId, "MDR_RIF")
                        } catch (e: Exception) {
                            Timber.e(e, "Pre-fetching results failed for MDR_RIF")
                        }
                    }
                }
                tbRepo.getTBDiagnostics(benId)?.let {
                    tbDiagnostics = it
                }
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

    private suspend fun updateDiagnosticsOrderStatus(benId: Long, orderType: String, status: String) {
        val existing = tbRepo.getTBDiagnosticsById(benId)
        val cache = (existing ?: TBDiagnosticsCache(benId = benId)).let {
            if (orderType.equals("XRAY_CHEST", ignoreCase = true)) {
                it.copy(xrayOrderStatus = status, syncState = SyncState.UNSYNCED)
            } else if (orderType.equals("MDR_RIF", ignoreCase = true)) {
                it.copy(rifOrderStatus = status, syncState = SyncState.UNSYNCED)
            } else {
                it.copy(trueNatOrderStatus = status, syncState = SyncState.UNSYNCED)
            }
        }
        tbRepo.saveTBDiagnostics(cache)
    }

    fun saveForm() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    _state.postValue(State.SAVING)
                    dataset.mapValues(tbDiagnostics, 1)

                    val isXrayDevIntegrated = tbRepo.isXrayIntegrated()
                    val isTruenatDevIntegrated = tbRepo.isTruenatIntegrated()

                    var apiSuccess = true
                    var apiError: String? = null

                    val existingDiag = tbRepo.getTBDiagnosticsById(benId)
                    val oldXrayStatus = existingDiag?.xrayOrderStatus
                    val oldTrueNatStatus = existingDiag?.trueNatOrderStatus
                    val oldRifStatus = existingDiag?.rifOrderStatus

                    if (referralType == 6) {
                        val isXrayManual = !isXrayDevIntegrated || 
                                oldXrayStatus.equals("POLLING_TIMEOUT", ignoreCase = true) || 
                                oldXrayStatus.equals("MANUAL_ENTRY", ignoreCase = true)

                        val refusalReason = {
                            val r = tbDiagnostics.reasonNotConductedChestXray
                            val o = tbDiagnostics.reasonNotConductedChestXrayOther
                            if (r.equals("Other", ignoreCase = true) && !o.isNullOrBlank()) "Other: $o" else r
                        }()

                        if (isXrayManual) {
                            if (tbDiagnostics.isChestXRayDone == true) {
                                val isXrayPositive = dataset.digitalChestXrayResult.value == dataset.digitalChestXrayResult.entries?.firstOrNull()
                                val resultString = if (isXrayPositive) "TB Presumptive" else "Normal"
                                val res = tbRepo.submitManualResult(benId, "XRAY_CHEST", resultString)
                                if (res is org.piramalswasthya.stoptb.helpers.NetworkResponse.Success) {
                                    tbDiagnostics.xrayOrderStatus = "COMPLETED"
                                    tbDiagnostics.chestXRayResult = resultString
                                    tbDiagnostics.isChestXRayDone = true
                                } else {
                                    apiSuccess = false
                                    apiError = (res as? org.piramalswasthya.stoptb.helpers.NetworkResponse.Error)?.message ?: "Submit Manual Result Failed"
                                }
                            } else {
                                val res = tbRepo.createProdigiOrder(benId, "XRAY_CHEST", reasonForRefusal = refusalReason)
                                if (res is org.piramalswasthya.stoptb.helpers.NetworkResponse.Success) {
                                    tbDiagnostics.xrayOrderStatus = "REFUSED"
                                    tbDiagnostics.isChestXRayDone = false
                                } else {
                                    apiSuccess = false
                                    apiError = (res as? org.piramalswasthya.stoptb.helpers.NetworkResponse.Error)?.message ?: "Push Order Failed"
                                }
                            }
                        } else {
                            // Integrated, initial track
                            if (tbDiagnostics.isChestXRayDone == true) {
                                tbDiagnostics.xrayOrderStatus = "AWAITING_PROVIDER_RESULT"
                                tbRepo.preferenceDao.setTrackSubmitTime(benId, "XRAY_CHEST", System.currentTimeMillis())
                                tbRepo.preferenceDao.setDiagPollActualStartTime(benId, "XRAY_CHEST", 0L)
                            } else {
                                val res = tbRepo.createProdigiOrder(benId, "XRAY_CHEST", reasonForRefusal = refusalReason)
                                if (res is org.piramalswasthya.stoptb.helpers.NetworkResponse.Success) {
                                    tbDiagnostics.xrayOrderStatus = "REFUSED"
                                    tbDiagnostics.isChestXRayDone = false
                                } else {
                                    apiSuccess = false
                                    apiError = (res as? org.piramalswasthya.stoptb.helpers.NetworkResponse.Error)?.message ?: "Push Order Failed"
                                }
                            }
                        }
                    } else if (referralType == 7) {
                        val isHubConnected = preferenceDao.isCampHubConnected()
                        val isMtbManual = !isTruenatDevIntegrated || !isHubConnected ||
                                oldTrueNatStatus.equals("POLLING_TIMEOUT", ignoreCase = true) || 
                                oldTrueNatStatus.equals("MANUAL_ENTRY", ignoreCase = true)

                        val mtbRefusalReason = {
                            if (tbDiagnostics.isSputumCollected == false) {
                                val r = tbDiagnostics.reasonForDenialSputum
                                val o = tbDiagnostics.reasonForDenialSputumOther
                                if (r.equals("Other", ignoreCase = true) && !o.isNullOrBlank()) "Other: $o" else r
                            } else {
                                val r = tbDiagnostics.reasonNotConductedNaat
                                val o = tbDiagnostics.reasonNotConductedNaatOther
                                if (r.equals("Other", ignoreCase = true) && !o.isNullOrBlank()) "Other: $o" else r
                            }
                        }()

                        val isMtbAlreadyCompleted = oldTrueNatStatus.equals("COMPLETED", ignoreCase = true)
                        if (isMtbAlreadyCompleted) {
                            // ── RIF Manual Submission Flow ──────────────────
                            val rifConductedVal = dataset.rifConducted.value
                            if (rifConductedVal == dataset.yesValue) {
                                val isRifDetected = dataset.trueNatRifResult.value == dataset.trueNatRifResult.entries?.getOrNull(0)
                                val isRifNotDetected = dataset.trueNatRifResult.value == dataset.trueNatRifResult.entries?.getOrNull(1)
                                val rifResultString = when {
                                    isRifDetected -> "DR TB"
                                    isRifNotDetected -> "Non DR TB"
                                    else -> "Indeterminate"
                                }
                                val rifRes = tbRepo.submitManualResult(benId, "MDR_RIF", rifResultString)
                                if (rifRes is org.piramalswasthya.stoptb.helpers.NetworkResponse.Success) {
                                    tbDiagnostics.rifOrderStatus = "COMPLETED"
                                    tbDiagnostics.trueNatRifResult = when {
                                        isRifDetected -> "Rif Resistance Detected"
                                        isRifNotDetected -> "Rif Resistance Not Detected"
                                        else -> "Indeterminate"
                                    }
                                } else {
                                    apiSuccess = false
                                    apiError = (rifRes as? org.piramalswasthya.stoptb.helpers.NetworkResponse.Error)?.message ?: "Submit RIF Manual Result Failed"
                                }
                            } else if (rifConductedVal == dataset.noValue) {
                                val rifRefusalReason = {
                                    val r = dataset.reasonNotConductedRif.value
                                    val o = dataset.reasonNotConductedRifOther.value
                                    val selReason = dataset.getEnglishValueInArray(R.array.tb_reason_not_conducted_naat, r) ?: r
                                    if (selReason.equals("Other", ignoreCase = true) && !o.isNullOrBlank()) "Other: $o" else selReason
                                }()
                                val rifRes = tbRepo.createProdigiOrder(benId, "MDR_RIF", reasonForRefusal = rifRefusalReason)
                                if (rifRes is org.piramalswasthya.stoptb.helpers.NetworkResponse.Success) {
                                    tbDiagnostics.rifOrderStatus = "REFUSED"
                                } else {
                                    apiSuccess = false
                                    apiError = (rifRes as? org.piramalswasthya.stoptb.helpers.NetworkResponse.Error)?.message ?: "Push RIF Order Failed"
                                }
                            }
                        } else {
                            if (isMtbManual) {
                                // ── MTB Manual Submission Flow ──────────────────
                                if (dataset.trueNatConducted.value == dataset.yesValue) {
                                    val isMtbDetected = dataset.isMtbDetected()
                                    val mtbResultString = if (isMtbDetected) "TB Positive" else "TB Negative"
                                    val res = tbRepo.submitManualResult(benId, "SPUTUM_TRUENAT", mtbResultString)
                                    if (res is org.piramalswasthya.stoptb.helpers.NetworkResponse.Success) {
                                        tbDiagnostics.trueNatOrderStatus = "COMPLETED"
                                        tbDiagnostics.naatResult = if (isMtbDetected) "MTB detected" else "MTB not detected"
                                        tbDiagnostics.isNaatConducted = true

                                        if (isMtbDetected) {
                                            val rifRes = tbRepo.createProdigiOrder(benId, "MDR_RIF")
                                            if (rifRes is org.piramalswasthya.stoptb.helpers.NetworkResponse.Success) {
                                                val updatedDiag = tbRepo.getTBDiagnosticsById(benId)
                                                if (updatedDiag != null) {
                                                    tbDiagnostics.rifOrderStatus = updatedDiag.rifOrderStatus
                                                    tbDiagnostics.rifOrderId = updatedDiag.rifOrderId
                                                }
                                            } else {
                                                apiSuccess = false
                                                apiError = (rifRes as? org.piramalswasthya.stoptb.helpers.NetworkResponse.Error)?.message ?: "Push MDR_RIF Order Failed"
                                            }
                                        }
                                    } else {
                                        apiSuccess = false
                                        apiError = (res as? org.piramalswasthya.stoptb.helpers.NetworkResponse.Error)?.message ?: "Submit Manual Result Failed"
                                    }
                                } else {
                                    val res = tbRepo.createProdigiOrder(benId, "SPUTUM_TRUENAT", reasonForRefusal = mtbRefusalReason)
                                    if (res is org.piramalswasthya.stoptb.helpers.NetworkResponse.Success) {
                                        tbDiagnostics.trueNatOrderStatus = "REFUSED"
                                        tbDiagnostics.isNaatConducted = false
                                    } else {
                                        apiSuccess = false
                                        apiError = (res as? org.piramalswasthya.stoptb.helpers.NetworkResponse.Error)?.message ?: "Push Order Failed"
                                    }
                                }
                            }
                        }
                    }

                    if (apiSuccess) {

                        // xrayOrderId/trueNatOrderId/rifOrderId to Room internally; tbDiagnostics
                        // here is the stale snapshot loaded at init{}, so merge those ids forward
                        // instead of only carrying the id, or the final save below wipes them out.
                        val freshDiag = tbRepo.getTBDiagnosticsById(benId)
                        if (freshDiag != null) {
                            tbDiagnostics = tbDiagnostics.copy(
                                id = freshDiag.id,
                                xrayOrderId = freshDiag.xrayOrderId ?: tbDiagnostics.xrayOrderId,
                                trueNatOrderId = freshDiag.trueNatOrderId ?: tbDiagnostics.trueNatOrderId,
                                rifOrderId = freshDiag.rifOrderId ?: tbDiagnostics.rifOrderId
                            )
                        }
                        tbDiagnostics.syncState = SyncState.UNSYNCED
                        tbRepo.saveTBDiagnostics(tbDiagnostics)
                        tbRepo.syncTBSuspectedFromDiagnostics(benId, tbDiagnostics)

                        val updatedDiag = tbRepo.getTBDiagnosticsById(benId)
                        val xrayAwaiting = updatedDiag?.xrayOrderStatus.equals("AWAITING_PROVIDER_RESULT", ignoreCase = true)
                        val truenatAwaiting = updatedDiag?.trueNatOrderStatus.equals("AWAITING_PROVIDER_RESULT", ignoreCase = true)
                        val rifAwaiting = updatedDiag?.rifOrderStatus.equals("AWAITING_PROVIDER_RESULT", ignoreCase = true)
                        
                        if ((xrayAwaiting && isXrayDevIntegrated) || 
                            ((truenatAwaiting || rifAwaiting) && isTruenatDevIntegrated)) {
                            org.piramalswasthya.stoptb.work.WorkerUtils.triggerDiagnosticResultPollWorker(context)
                        }

                        _state.postValue(State.SAVE_SUCCESS)
                    } else {
                        Timber.e("API submission failed for benId=%s: %s", benId, apiError)
                        _errorMessage.postValue(apiError ?: "Failed to save data")
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
        _errorMessage.value = null
    }
}
