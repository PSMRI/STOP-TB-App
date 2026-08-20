package org.piramalswasthya.stoptb.ui.home_activity.non_communicable_diseases.tb_screening.form

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.piramalswasthya.stoptb.configuration.TBScreeningDataset
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.model.OrderStatus
import org.piramalswasthya.stoptb.model.TBScreeningCache
import org.piramalswasthya.stoptb.model.getAgeGenderDisplayString
import org.piramalswasthya.stoptb.repositories.BenRepo
import org.piramalswasthya.stoptb.repositories.TBRepo
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class TBScreeningFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val preferenceDao: PreferenceDao,
    @ApplicationContext private val context: Context,
    private val tbRepo: TBRepo,
    private val benRepo: BenRepo
) : ViewModel() {
    val benId =
        TBScreeningFormFragmentArgs.fromSavedStateHandle(savedStateHandle).benId
    var benRegId: Long = 0L
    val viewOnly =
        TBScreeningFormFragmentArgs.fromSavedStateHandle(savedStateHandle).viewOnly
    val autoFlow =
        TBScreeningFormFragmentArgs.fromSavedStateHandle(savedStateHandle).autoFlow
    private val syncImmediately =
        TBScreeningFormFragmentArgs.fromSavedStateHandle(savedStateHandle).syncImmediately

    enum class State {
        IDLE, SAVING, SAVE_SUCCESS, SAVE_FAILED
    }

    private val _state = MutableLiveData(State.IDLE)
    val state: LiveData<State>
        get() = _state

    private val _benName = MutableLiveData<String>()
    val benName: LiveData<String>
        get() = _benName
    private val _benAgeGender = MutableLiveData<String>()
    val benAgeGender: LiveData<String>
        get() = _benAgeGender

    private val _recordExists = MutableLiveData<Boolean>()
    val recordExists: LiveData<Boolean>
        get() = _recordExists

    //    private lateinit var user: UserDomain
    private val dataset =
        TBScreeningDataset(context, preferenceDao.getCurrentLanguage())
    val formList = dataset.listFlow

    private lateinit var tbScreeningCache: TBScreeningCache
    var capturedLatitude: Double? = null
    var capturedLongitude: Double? = null
    var capturedAddress: String? = preferenceDao.getLocationRecord()?.let {
        listOf(it.village.name, it.block.name, it.district.name, it.state.name)
            .filter { name -> name.isNotBlank() }
            .distinct()
            .joinToString(", ")
    }

    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.IO

    init {
        viewModelScope.launch {
            val ben = benRepo.getBenFromId(benId)?.also { ben ->
                benRegId = ben.benRegId
                _benName.value =
                    "${ben.firstName} ${if (ben.lastName == null) "" else ben.lastName}"
                _benAgeGender.value = ben.getAgeGenderDisplayString()
                tbScreeningCache = TBScreeningCache(
                    benId = ben.beneficiaryId,
                )
            }

            tbRepo.getTBScreening(benId)?.let {
                tbScreeningCache = it
                _recordExists.value = true
            } ?: run {
                _recordExists.value = false
            }

            dataset.setUpPage(
                ben,
                if (recordExists.value == true) tbScreeningCache else null
            )
        }
    }

    fun updateListOnValueChanged(formId: Int, index: Int) {
        viewModelScope.launch {
            dataset.updateList(formId, index)
        }

    }

    fun getSubmitAlertMessage(): String? = dataset.getPresumptiveTbAlert()

    fun saveForm() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    _state.postValue(State.SAVING)
                    dataset.mapValues(tbScreeningCache, 1)
                    tbScreeningCache.latitude = capturedLatitude
                    tbScreeningCache.longitude = capturedLongitude
                    tbScreeningCache.address = capturedAddress
                    tbScreeningCache.syncState = SyncState.UNSYNCED
//                    tbRepo.saveTBScreening(tbScreeningCache)

                    initializeDiagnosticsAndPush(tbScreeningCache)

                    if (syncImmediately) {
                        try {
                            tbRepo.pushUnSyncedTBScreeningRecords()
                        } catch (e: Exception) {
                            Timber.e(e, "Immediate sync failed, will sync in background")
                        }
                    }
                    _state.postValue(State.SAVE_SUCCESS)
                } catch (e: Exception) {
                    Timber.d(e, "saving tb screening data failed!!")
                    _state.postValue(State.SAVE_FAILED)
                }
            }
        }
    }

    fun saveFormDirectlyfromCbac() {
        viewModelScope.launch {
            withContext(defaultDispatcher) {
                try {
                    saveValues()
                    _state.postValue(State.SAVING)
                    tbRepo.saveTBScreening(tbScreeningCache)

                    initializeDiagnosticsAndPush(tbScreeningCache)

                    _state.postValue(State.SAVE_SUCCESS)
                } catch (e: Exception) {
                    Timber.d("saving tb screening data failed!!")
                    _state.postValue(State.SAVE_FAILED)
                }
            }
        }
    }

    private suspend fun saveValues() {
        tbScreeningCache = TBScreeningCache(
            benId = benRepo.getBenFromId(benId)!!.beneficiaryId,
            coughMoreThan2Weeks = true,
            lossOfWeight = true,
            feverMoreThan2Weeks = true,
            nightSweats = true,
            bloodInSputum = true,
            historyOfTb = true,
        )
    }

    private suspend fun initializeDiagnosticsAndPush(tbScreeningCache: org.piramalswasthya.stoptb.model.TBScreeningCache) {
        try {
            val isPresumptive = tbScreeningCache.coughMoreThan2Weeks == true ||
                    tbScreeningCache.bloodInSputum == true ||
                    tbScreeningCache.feverMoreThan2Weeks == true ||
                    tbScreeningCache.riseOfFever == true ||
                    tbScreeningCache.lossOfAppetite == true ||
                    tbScreeningCache.lossOfWeight == true ||
                    tbScreeningCache.nightSweats == true ||
                    tbScreeningCache.historyOfTb == true ||
                    tbScreeningCache.takingAntiTBDrugs == true ||
                    tbScreeningCache.familySufferingFromTB == true

            val ben = benRepo.getBenFromId(benId)
            val reproductiveStatus = ben?.genDetails?.reproductiveStatus
            val isPregnant = ben?.genDetails?.reproductiveStatusId == 1 || reproductiveStatus.equals("Yes", ignoreCase = true)

            val refersXray = !isPregnant
            val refersTruenat = isPresumptive || isPregnant

            // Persist the computed referral eligibility back to local screening record
            tbScreeningCache.referredForDigitalChestXray = refersXray
            tbScreeningCache.referredForSputumCollection = refersTruenat
            tbRepo.saveTBScreening(tbScreeningCache)

            val existingDiag = tbRepo.getTBDiagnosticsById(benId)
            var currentDiag = existingDiag ?: org.piramalswasthya.stoptb.model.TBDiagnosticsCache(benId = benId, syncState = SyncState.UNSYNCED)
            
            if (refersXray) {
                if (currentDiag.xrayOrderStatus.isNullOrBlank() || currentDiag.xrayOrderStatus == OrderStatus.NONE.name) {
                    currentDiag = currentDiag.copy(xrayOrderStatus = OrderStatus.PENDING.name, isReferredForDigitalChestXray = true)
                }
            }
            if (refersTruenat) {
                if (currentDiag.trueNatOrderStatus.isNullOrBlank() || currentDiag.trueNatOrderStatus == OrderStatus.NONE.name) {
                    currentDiag = currentDiag.copy(trueNatOrderStatus = OrderStatus.PENDING.name)
                }
            }
            tbRepo.saveTBDiagnostics(currentDiag)

            if (refersXray) {
                try {
                    val current = tbRepo.getTBDiagnosticsById(benId)
                    val hasOrder = !current?.xrayOrderId.isNullOrBlank() ||
                            current?.xrayOrderStatus.equals(OrderStatus.COMPLETED.name, ignoreCase = true) ||
                            current?.xrayOrderStatus.equals(OrderStatus.AWAITING_PROVIDER_RESULT.name, ignoreCase = true) ||
                            current?.xrayOrderStatus.equals(OrderStatus.REFUSED.name, ignoreCase = true)
                    if (!hasOrder) {
                        val response = tbRepo.createOrder(benId, "XRAY_CHEST")
                        if (response is org.piramalswasthya.stoptb.helpers.NetworkResponse.Success) {
                            val isIntegrated = tbRepo.isXrayIntegrated()
                            val fresh = tbRepo.getTBDiagnosticsById(benId)
                            fresh?.let {
                                val updated = it.copy(
                                    xrayOrderStatus = if (isIntegrated) OrderStatus.AWAITING_PROVIDER_RESULT.name else OrderStatus.PENDING.name,
                                    isChestXRayDone = isIntegrated,
                                    isReferredForDigitalChestXray = true,
                                    syncState = SyncState.UNSYNCED
                                )
                                tbRepo.saveTBDiagnostics(updated)
                            }
                            if (isIntegrated) {
                                org.piramalswasthya.stoptb.work.WorkerUtils.triggerDiagnosticResultPollWorker(context)
                            }
                        } else {
                            val isIntegrated = tbRepo.isXrayIntegrated()
                            if (isIntegrated) {
                                val fresh = tbRepo.getTBDiagnosticsById(benId)
                                fresh?.let {
                                    val updated = it.copy(
                                        xrayOrderStatus = OrderStatus.FAILED.name,
                                        syncState = SyncState.UNSYNCED
                                    )
                                    tbRepo.saveTBDiagnostics(updated)
                                }
                            }
                        }
                    }
                } catch (e: java.lang.Exception) {
                    Timber.e(e, "Automatic X-Ray order push failed")
                }
            }

            if (refersTruenat) {
                try {
                    val current = tbRepo.getTBDiagnosticsById(benId)
                    val hasOrder = !current?.trueNatOrderId.isNullOrBlank() ||
                            current?.trueNatOrderStatus.equals(OrderStatus.COMPLETED.name, ignoreCase = true) ||
                            current?.trueNatOrderStatus.equals(OrderStatus.AWAITING_PROVIDER_RESULT.name, ignoreCase = true) ||
                            current?.trueNatOrderStatus.equals(OrderStatus.REFUSED.name, ignoreCase = true)
                    if (!hasOrder) {
                        val response = tbRepo.createOrder(benId, "SPUTUM_TRUENAT")
                        if (response is org.piramalswasthya.stoptb.helpers.NetworkResponse.Success) {
                            val isIntegrated = tbRepo.isTruenatIntegrated()
                            val fresh = tbRepo.getTBDiagnosticsById(benId)
                            fresh?.let {
                                val updated = it.copy(
                                    trueNatOrderStatus = if (isIntegrated) OrderStatus.AWAITING_PROVIDER_RESULT.name else OrderStatus.PENDING.name,
                                    isSputumCollected = true,
                                    isNaatConducted = isIntegrated,
                                    syncState = SyncState.UNSYNCED
                                )
                                tbRepo.saveTBDiagnostics(updated)
                            }
                            if (isIntegrated) {
                                org.piramalswasthya.stoptb.work.WorkerUtils.triggerTrueNatDiagnosticResultPollWorker(context)
                            }
                        } else {
                            val isIntegrated = tbRepo.isTruenatIntegrated()
                            if (isIntegrated) {
                                val fresh = tbRepo.getTBDiagnosticsById(benId)
                                fresh?.let {
                                    val updated = it.copy(
                                        trueNatOrderStatus = OrderStatus.FAILED.name,
                                        syncState = SyncState.UNSYNCED
                                    )
                                    tbRepo.saveTBDiagnostics(updated)
                                }
                            }
                        }
                    }
                } catch (e: java.lang.Exception) {
                    Timber.e(e, "Automatic TrueNat order push failed")
                }
            }
        } catch (e: java.lang.Exception) {
            Timber.e(e, "Error initializing diagnostic record and pushing orders")
        }
    }

    fun resetState() {
        _state.value = State.IDLE
    }

    fun getIndexOfDate(): Int        = dataset.getIndexOfDate()
    fun getIndexOfAsymptomatic(): Int = dataset.getIndexOfAsymptomatic()
}

