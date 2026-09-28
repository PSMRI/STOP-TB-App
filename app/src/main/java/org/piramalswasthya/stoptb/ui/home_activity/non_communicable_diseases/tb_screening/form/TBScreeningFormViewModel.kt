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
import org.piramalswasthya.stoptb.work.WorkerUtils
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

            // Whether an order already exists must be decided from the state BEFORE the
            // preemptive PENDING placeholder below is written — otherwise that placeholder
            // (written purely so the UI has something to show while the push is in flight)
            // would itself satisfy this check, and a genuinely new referral would never
            // actually get pushed.
            val hasXrayOrder = !existingDiag?.xrayOrderId.isNullOrBlank() ||
                    existingDiag?.xrayOrderStatus.equals(OrderStatus.COMPLETED.name, ignoreCase = true) ||
                    existingDiag?.xrayOrderStatus.equals(OrderStatus.PENDING.name, ignoreCase = true) ||
                    existingDiag?.xrayOrderStatus.equals(OrderStatus.CLOSED.name, ignoreCase = true)
            val hasTruenatOrder = !existingDiag?.trueNatOrderId.isNullOrBlank() ||
                    existingDiag?.trueNatOrderStatus.equals(OrderStatus.COMPLETED.name, ignoreCase = true) ||
                    existingDiag?.trueNatOrderStatus.equals(OrderStatus.PENDING.name, ignoreCase = true) ||
                    existingDiag?.trueNatOrderStatus.equals(OrderStatus.CLOSED.name, ignoreCase = true)

            if (refersXray) {
                // "NONE" was never actually written as a status value anywhere — a blank status
                // is what "no order yet" looks like.
                if (currentDiag.xrayOrderStatus.isNullOrBlank()) {
                    currentDiag = currentDiag.copy(xrayOrderStatus = OrderStatus.PENDING.name, isReferredForDigitalChestXray = true)
                }
            }
            if (refersTruenat) {
                if (currentDiag.trueNatOrderStatus.isNullOrBlank()) {
                    currentDiag = currentDiag.copy(trueNatOrderStatus = OrderStatus.PENDING.name)
                }
            }
            tbRepo.saveTBDiagnostics(currentDiag)

            // Chain X-ray then TrueNat (same order the automated referral cascade has always
            // used) as a single background work chain instead of two awaited inline calls —
            // createOrder() is a network call that can block for up to the configured 60s
            // OkHttp timeout when the device/camp hub is unreachable, which used to stall form
            // submission for that long (QA-reported bug). createOrder() already writes the
            // correct PENDING/FAILED status (and isChestXRayDone/isReferredForDigitalChestXray)
            // internally, same as before — the worker chain just stops it from blocking here.
            val ordersToPush = mutableListOf<String>()
            if (refersXray && !hasXrayOrder) ordersToPush.add("XRAY_CHEST")
            if (refersTruenat && !hasTruenatOrder) ordersToPush.add("SPUTUM_TRUENAT")
            if (ordersToPush.isNotEmpty()) {
                WorkerUtils.triggerDiagnosticOrderPushWorkers(context, benId, ordersToPush)
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

