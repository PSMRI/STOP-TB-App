package org.piramalswasthya.stoptb.ui.home_activity.non_communicable_diseases.tb_screening.form

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.piramalswasthya.stoptb.configuration.KeyPopulationRiskFactorAutoSelect
import org.piramalswasthya.stoptb.configuration.TBScreeningDataset
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.model.BenRegCache
import org.piramalswasthya.stoptb.model.OrderStatus
import org.piramalswasthya.stoptb.model.TBDiagnosticsCache
import org.piramalswasthya.stoptb.model.TBScreeningCache
import org.piramalswasthya.stoptb.model.getAgeGenderDisplayString
import org.piramalswasthya.stoptb.repositories.BenRepo
import org.piramalswasthya.stoptb.repositories.TBRepo
import org.piramalswasthya.stoptb.utils.Log
import org.piramalswasthya.stoptb.work.WorkerUtils
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class TBScreeningFormViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val preferenceDao: PreferenceDao,
    @ApplicationContext private val context: Context,
    private val tbRepo: TBRepo,
    private val benRepo: BenRepo
) : ViewModel() {
    // Re-resolved in init and before saving: the nav arg can be a temp ID the push has replaced.
    var benId =
        TBScreeningFormFragmentArgs.fromSavedStateHandle(savedStateHandle).benId
        private set(value) {
            if (field == value) return
            field = value
            // Saved so a form restored after process death uses the server ID, not the temp ID.
            viewModelScope.launch(Dispatchers.Main) { savedStateHandle["benId"] = value }
        }
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
            //Resolve the current beneficiary ID to load the correct beneficiary's data when the form opens.
            benId = benRepo.resolveCurrentBenId(benId)
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

            val residentialArea = ben?.let { resolveResidentialArea(it) }
            dataset.setUpPage(
                ben,
                if (recordExists.value == true) tbScreeningCache else null,
                residentialArea?.label,
                residentialArea?.id
            )
        }
    }

    /**
     * Picks up an age or residential-area edit made before this screening is saved.
     * Returns true when Elderly or Urban Slum was added or removed.
     */
    suspend fun refreshAutoSelectedRiskFactors(): Boolean {
        if (_recordExists.value == true) return false
        val ben = benRepo.getBenFromId(benId) ?: return false
        _benAgeGender.postValue(ben.getAgeGenderDisplayString())
        val residentialArea = resolveResidentialArea(ben)
        return dataset.refreshAutoSelectedRiskFactors(
            ben,
            residentialArea.label,
            residentialArea.id
        )
    }

    private suspend fun resolveResidentialArea(ben: BenRegCache): KeyPopulationRiskFactorAutoSelect.ResidentialArea {
        val household = ben.householdId?.takeIf { it > 0L }?.let { benRepo.getHousehold(it) }
        return KeyPopulationRiskFactorAutoSelect.resolveResidentialArea(
            benLabel = ben.residentialArea,
            benId = ben.residentialAreaId,
            householdLabel = household?.details?.residentialArea,
            householdId = household?.details?.residentialAreaId
        )
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
                    // Re-resolve the current beneficiary ID before submitting to ensure data is saved against the correct beneficiary.
                    benId = benRepo.resolveCurrentBenId(benId)
                    if (tbScreeningCache.benId != benId) {
                        tbScreeningCache = tbScreeningCache.copy(benId = benId)
                    }
                    dataset.mapValues(tbScreeningCache, 1)
                    /*Log.d("TBScreeningFormViewModel", "tbScreeningCache: $tbScreeningCache")*/
                    tbScreeningCache.latitude = capturedLatitude
                    tbScreeningCache.longitude = capturedLongitude
                    tbScreeningCache.address = capturedAddress
                    tbScreeningCache.syncState = SyncState.UNSYNCED

                    // The screening row is written inside initializeDiagnosticsAndPush(), after
                    // it has computed the referral flags that belong on the same row. Saving here
                    // as well would write the row twice, once without those flags.
                    initializeDiagnosticsAndPush(tbScreeningCache)

                    if (syncImmediately) {
                        try {
                            tbRepo.pushUnSyncedTBScreeningRecords()
                        } catch (e: Exception) {
                            Timber.e(e, "Immediate sync failed, will sync in background")
                        }
                    }
                    _state.postValue(State.SAVE_SUCCESS)
                } catch (e: CancellationException) {
                    // Leaving the form mid-save must not be reported as either success or
                    // failure - the screen is going away. CancellationException is an Exception
                    // in Kotlin, so without this it would be caught below and the user would be
                    // told the record saved.
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "saving tb screening data failed!!")
                    /*Log.d("TBScreeningFormViewModel", "saving tb screening data failed!!")*/
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

    /**
     * Writes the screening row and its derived diagnostics row, then kicks off the order push.
     *
     * The two writes deliberately propagate. This used to be wrapped end-to-end in
     * `catch (e: Exception) { Timber.e(...) }`, which swallowed a failed
     * [TBRepo.saveTBScreening] and let [saveForm] post SAVE_SUCCESS anyway - the user was told
     * the screening was saved when nothing had been written, and on an offline camp device
     * there is no second copy. Only the order-push trigger below is best-effort.
     */
    private suspend fun initializeDiagnosticsAndPush(tbScreeningCache: TBScreeningCache) {
        val isPresumptive = tbScreeningCache.coughMoreThan2Weeks == true ||
                tbScreeningCache.bloodInSputum == true ||
                tbScreeningCache.feverMoreThan2Weeks == true ||
                tbScreeningCache.riseOfFever == true ||
                tbScreeningCache.lossOfAppetite == true ||
                tbScreeningCache.lossOfWeight == true ||
                tbScreeningCache.nightSweats == true ||
                tbScreeningCache.chestPain == true ||
                tbScreeningCache.shortnessOfBreath == true ||
                tbScreeningCache.fatigue == true ||
                tbScreeningCache.failureToGainWeightInChildren == true ||
                tbScreeningCache.decreasedActivityOrPlayfulnessInChildren == true ||
                tbScreeningCache.otherSymptoms == true ||
                tbScreeningCache.historyOfTb == true ||
                tbScreeningCache.takingAntiTBDrugs == true ||
                tbScreeningCache.familySufferingFromTB == true

        val ben = benRepo.getBenFromId(benId)
        val reproductiveStatus = ben?.genDetails?.reproductiveStatus
        val isPregnant = ben?.genDetails?.reproductiveStatusId == 1 || reproductiveStatus.equals("Yes", ignoreCase = true)

        val refersXray = !isPregnant
        val refersTruenat = isPresumptive || isPregnant

        tbScreeningCache.referredForDigitalChestXray = refersXray
        tbScreeningCache.referredForSputumCollection = refersTruenat
        tbRepo.saveTBScreening(tbScreeningCache)

        val existingDiag = tbRepo.getTBDiagnosticsById(benId)
        var currentDiag = existingDiag ?: TBDiagnosticsCache(
            benId = benId,
            syncState = SyncState.UNSYNCED
        )
        val hasXrayOrder = !existingDiag?.xrayOrderId.isNullOrBlank() ||
                existingDiag?.xrayOrderStatus.equals(OrderStatus.COMPLETED.name, ignoreCase = true) ||
                existingDiag?.xrayOrderStatus.equals(OrderStatus.PENDING.name, ignoreCase = true) ||
                existingDiag?.xrayOrderStatus.equals(OrderStatus.CLOSED.name, ignoreCase = true)
        val hasTruenatOrder = !existingDiag?.trueNatOrderId.isNullOrBlank() ||
                existingDiag?.trueNatOrderStatus.equals(OrderStatus.COMPLETED.name, ignoreCase = true) ||
                existingDiag?.trueNatOrderStatus.equals(OrderStatus.PENDING.name, ignoreCase = true) ||
                existingDiag?.trueNatOrderStatus.equals(OrderStatus.CLOSED.name, ignoreCase = true)

        if (refersXray) {
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
        val ordersToPush = mutableListOf<String>()
        if (refersXray && !hasXrayOrder) ordersToPush.add("XRAY_CHEST")
        if (refersTruenat && !hasTruenatOrder) ordersToPush.add("SPUTUM_TRUENAT")
        if (ordersToPush.isNotEmpty()) {
            // Best effort from here on: the rows are already committed, and a worker owns
            // the actual push. Failing to pre-sync or to enqueue must not turn a saved
            // screening into SAVE_FAILED.
            try {
                tbRepo.pushUnSyncedTBScreeningRecords()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "TB Screening pre-order-push sync failed; proceeding with order push anyway")
            }
            try {
                WorkerUtils.triggerDiagnosticOrderPushWorkers(context, benId, ordersToPush)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to enqueue diagnostic order push workers for benId=$benId")
            }
        }
    }

    fun resetState() {
        _state.value = State.IDLE
    }

    fun getIndexOfDate(): Int        = dataset.getIndexOfDate()
    fun getIndexOfAsymptomatic(): Int = dataset.getIndexOfAsymptomatic()
    fun getIndexOfKeyPopulationRiskFactors(): Int = dataset.getIndexOfKeyPopulationRiskFactors()
}

