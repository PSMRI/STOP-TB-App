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
import org.piramalswasthya.stoptb.helpers.NetworkResponse
import org.piramalswasthya.stoptb.model.TBDiagnosticsCache
import org.piramalswasthya.stoptb.model.BenRegCache
import org.piramalswasthya.stoptb.model.ChestXrayResult
import org.piramalswasthya.stoptb.model.MtbResult
import org.piramalswasthya.stoptb.model.RifResult
import org.piramalswasthya.stoptb.model.TBScreeningCache
import org.piramalswasthya.stoptb.model.VitalCache
import org.piramalswasthya.stoptb.repositories.BenRepo
import org.piramalswasthya.stoptb.repositories.TBRepo
import org.piramalswasthya.stoptb.repositories.VitalRepo
import org.piramalswasthya.stoptb.work.WorkerUtils
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
    val manualEntryAction = args.manualEntryAction

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

    // True when this save succeeded but at least one manually-entered result fell back to its
    // offline-first path (camp hub disconnected/unreachable) — the Fragment uses this to show a
    // "saved locally, will sync later" message instead of the normal success toast.
    private val _savedOfflinePendingSync = MutableLiveData(false)
    val savedOfflinePendingSync: LiveData<Boolean> = _savedOfflinePendingSync

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
                // Real order/result contract: PENDING is the only "in flight" value our own
                // writes ever produce (IN_PROGRESS/AWAITING_PROVIDER_RESULT are never actually
                // stored — see TBRepo.reducedOrderStatus).
                val isOrderActive = if (orderType == "XRAY_CHEST") {
                    val status = tbDiagnostics.xrayOrderStatus
                    status.equals("COMPLETED", ignoreCase = true) || status.equals("PENDING", ignoreCase = true)
                } else {
                    val status = tbDiagnostics.trueNatOrderStatus
                    status.equals("COMPLETED", ignoreCase = true) || status.equals("PENDING", ignoreCase = true)
                }
                if (!hasLocalResult && isOrderActive) {
                    try {
                        tbRepo.fetchOrderResult(benId, orderType)
                    } catch (e: Exception) {
                        Timber.e(e, "Pre-fetching results failed for $orderType")
                    }
                }
                if (orderType == "SPUTUM_TRUENAT" && MtbResult.fromResultText(tbDiagnostics.naatResult) == MtbResult.TB_POSITIVE) {
                    val hasLocalRifResult = !tbDiagnostics.trueNatRifResult.isNullOrBlank()
                    val isRifActive = tbDiagnostics.rifOrderStatus.equals("COMPLETED", ignoreCase = true) ||
                            tbDiagnostics.rifOrderStatus.equals("PENDING", ignoreCase = true)
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
                referralType = referralType,
                manualEntryAction = manualEntryAction
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

    fun repeatTest(orderType: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    tbRepo.createOrder(benId, orderType)
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
                    // True if any submitManualResult() call this save fell back to its
                    // offline-first path (camp hub disconnected / unreachable) — the result is
                    // still saved and this save still succeeds, but the user should be told it's
                    // pending sync rather than confirmed.
                    var anyPendingManualResultSync = false
                    // Deferred — enqueued only after the final save below, so the worker's own
                    // status write can't be clobbered by this save's stale snapshot.
                    val ordersToPushAfterSave = mutableListOf<String>()

                    if (referralType == 6) {
                        // Chest X-Ray order lifecycle redesign — 3 mutually exclusive cases,
                        // keyed off what the user actually did on this screen rather than device
                        // integration status (Enter Result / Not Conducted are standing actions
                        // now, not integration fallbacks):
                        val isReferredForXray = tbDiagnostics.isReferredForDigitalChestXray == true
                        val isXrayConducted = tbDiagnostics.isChestXRayDone == true
                        // Non-null only when the user actually picked one of the 4 standardized
                        // results (mapValues() above already ran the selected display value
                        // through getEnglishValueInArray) — locked/placeholder display text like
                        // "Waiting for Result"/"Referral Failed" won't match, so this doubles as
                        // the "did the user enter a result" check.
                        val enteredXrayResult = org.piramalswasthya.stoptb.model.ChestXrayResult
                            .fromResultText(tbDiagnostics.chestXRayResult)

                        when {
                            !isReferredForXray -> {
                                // Beneficiary declines the referral before any order exists —
                                // unchanged path (order/push with reasonForRefusal). See
                                // TBSuspectedQuickDataset's reasonForDenialChestXray gating,
                                // left as-is by this redesign.
                                val denialReason = {
                                    val r = tbDiagnostics.reasonForDenialChestXray
                                    val o = tbDiagnostics.reasonForDenialChestXrayOther
                                    if (r.equals("Other", ignoreCase = true) && !o.isNullOrBlank()) "Other: $o" else r
                                }()
                                val res = tbRepo.createOrder(benId, "XRAY_CHEST", reasonForRefusal = denialReason)
                                if (res is NetworkResponse.Success) {
                                    tbDiagnostics.xrayOrderStatus = "CLOSED"
                                    tbDiagnostics.isChestXRayDone = false
                                } else {
                                    apiSuccess = false
                                    apiError = (res as? NetworkResponse.Error)?.message ?: "Push Order Failed"
                                }
                            }
                            !isXrayConducted -> {
                                // Order exists but the test wasn't performed — mandatory-reason
                                // "Not Conducted" closure via order/manualResult (reused
                                // reasonForRefusal field), not order/push.
                                val notConductedReason = {
                                    val r = tbDiagnostics.reasonNotConductedChestXray
                                    val o = tbDiagnostics.reasonNotConductedChestXrayOther
                                    if (r.equals("Other", ignoreCase = true) && !o.isNullOrBlank()) "Other: $o" else r
                                }()
                                val res = tbRepo.submitManualResult(
                                    benId, "XRAY_CHEST", resultSummary = null, reasonForRefusal = notConductedReason
                                )
                                if (res is NetworkResponse.Success) {
                                    if (res.data == "PENDING_SYNC") anyPendingManualResultSync = true
                                    tbDiagnostics.xrayOrderStatus = "CLOSED"
                                    tbDiagnostics.chestXRayResult = null
                                } else {
                                    apiSuccess = false
                                    apiError = (res as? NetworkResponse.Error)?.message ?: "Not Conducted Submission Failed"
                                }
                            }
                            enteredXrayResult != null -> {
                                // Enter Result manually — a standing action whenever the order is
                                // Pending/Awaiting Manual Entry, not just a device-integration
                                // fallback.
                                val res = tbRepo.submitManualResult(benId, "XRAY_CHEST", resultSummary = enteredXrayResult.displayValue)
                                if (res is NetworkResponse.Success) {
                                    if (res.data == "PENDING_SYNC") anyPendingManualResultSync = true
                                    tbDiagnostics.xrayOrderStatus = "COMPLETED"
                                    tbDiagnostics.isChestXRayDone = true
                                    tbRepo.getTBDiagnosticsById(benId)?.xrayOrderId?.let { tbDiagnostics.xrayOrderId = it }

                                    when {
                                        enteredXrayResult.triggersTrueNatReferral -> {
                                            // TB Presumptive / Abnormal-but-not-presumptive — both
                                            // now trigger the same referral cascade (unchanged
                                            // TrueNat call).
                                            val currentDiag = tbRepo.getTBDiagnosticsById(benId)
                                            val hasTruenat = !currentDiag?.trueNatOrderId.isNullOrBlank() ||
                                                    currentDiag?.trueNatOrderStatus.equals("COMPLETED", ignoreCase = true) ||
                                                    currentDiag?.trueNatOrderStatus.equals("PENDING", ignoreCase = true) ||
                                                    currentDiag?.trueNatOrderStatus.equals("CLOSED", ignoreCase = true)
                                            if (!hasTruenat) {
                                                ordersToPushAfterSave += "SPUTUM_TRUENAT"
                                            }
                                        }
                                        enteredXrayResult == ChestXrayResult.AI_INVALID -> {
                                            // Manual AI-Invalid: re-order immediately, mirrors RIF
                                            // Indeterminate / MTB TB-Positive.
                                            ordersToPushAfterSave += "XRAY_CHEST"
                                        }
                                        else -> Unit // Normal — no cascade
                                    }
                                } else {
                                    apiSuccess = false
                                    apiError = (res as? NetworkResponse.Error)?.message ?: "Submit Manual Result Failed"
                                }
                            }
                            else -> {
                                // Conducted = Yes but no manual value chosen yet — device/AI
                                // result is still pending automatically.
                                tbDiagnostics.xrayOrderStatus = "PENDING"
                                tbRepo.preferenceDao.setTrackSubmitTime(benId, "XRAY_CHEST", System.currentTimeMillis())
                                tbRepo.preferenceDao.setDiagPollActualStartTime(benId, "XRAY_CHEST", 0L)
                            }
                        }
                    } else if (referralType == 7) {
                        // TrueNat (MTB) & RIF order lifecycle redesign — the same 3-way
                        // Enter-Result/Not-Conducted/decline restructure Chest X-Ray's
                        // referralType == 6 branch already got above: keyed off what the user
                        // actually did on this screen (form state, already mapped onto
                        // tbDiagnostics by dataset.mapValues() above), NOT device-integration/
                        // camp-hub status — Enter Result / Not Conducted are standing actions now.
                        val isReferredForSputum = tbDiagnostics.isSputumCollected == true
                        val isMtbConducted = tbDiagnostics.isNaatConducted == true
                        // Non-null only when the user actually picked one of the 3 standardized
                        // MTB results (mapValues() above already ran the selection through
                        // getEnglishValueInArray) — locked/placeholder display text like "Waiting
                        // for Result" won't match, so this doubles as "did the user enter a
                        // result".
                        val enteredMtbResult = org.piramalswasthya.stoptb.model.MtbResult
                            .fromResultText(tbDiagnostics.naatResult)

                        when {
                            !isReferredForSputum -> {
                                // Beneficiary declines the referral before any order exists —
                                // unchanged path (order/push with reasonForRefusal).
                                val denialReason = {
                                    val r = tbDiagnostics.reasonForDenialSputum
                                    val o = tbDiagnostics.reasonForDenialSputumOther
                                    if (r.equals("Other", ignoreCase = true) && !o.isNullOrBlank()) "Other: $o" else r
                                }()
                                val res = tbRepo.createOrder(benId, "SPUTUM_TRUENAT", reasonForRefusal = denialReason)
                                if (res is NetworkResponse.Success) {
                                    tbDiagnostics.trueNatOrderStatus = "CLOSED"
                                    tbDiagnostics.isNaatConducted = false
                                } else {
                                    apiSuccess = false
                                    apiError = (res as? NetworkResponse.Error)?.message ?: "Push Order Failed"
                                }
                            }
                            !isMtbConducted -> {
                                // Order exists but the test wasn't performed — mandatory-reason
                                // "Not Conducted" closure via order/manualResult (reused
                                // reasonForRefusal field), not order/push.
                                val notConductedReason = {
                                    val r = tbDiagnostics.reasonNotConductedNaat
                                    val o = tbDiagnostics.reasonNotConductedNaatOther
                                    if (r.equals("Other", ignoreCase = true) && !o.isNullOrBlank()) "Other: $o" else r
                                }()
                                val res = tbRepo.submitManualResult(
                                    benId, "SPUTUM_TRUENAT", resultSummary = null, reasonForRefusal = notConductedReason
                                )
                                if (res is NetworkResponse.Success) {
                                    if (res.data == "PENDING_SYNC") anyPendingManualResultSync = true
                                    tbDiagnostics.trueNatOrderStatus = "CLOSED"
                                    tbDiagnostics.naatResult = null
                                } else {
                                    apiSuccess = false
                                    apiError = (res as? NetworkResponse.Error)?.message ?: "Not Conducted Submission Failed"
                                }
                            }
                            enteredMtbResult != null -> {
                                // Enter Result manually — a standing action whenever the order is
                                // Pending/Awaiting Manual Entry, not just a device-integration
                                // fallback.
                                val res = tbRepo.submitManualResult(benId, "SPUTUM_TRUENAT", resultSummary = enteredMtbResult.displayValue)
                                if (res is NetworkResponse.Success) {
                                    if (res.data == "PENDING_SYNC") anyPendingManualResultSync = true
                                    tbDiagnostics.trueNatOrderStatus = "COMPLETED"
                                    tbDiagnostics.isNaatConducted = true
                                    tbDiagnostics.naatResult = when (enteredMtbResult) {
                                        MtbResult.TB_POSITIVE -> "MTB detected"
                                        MtbResult.TB_NEGATIVE -> "MTB not detected"
                                        else -> enteredMtbResult.displayValue
                                    }
                                    tbDiagnostics.isTBConfirmed = enteredMtbResult == MtbResult.TB_POSITIVE
                                    tbDiagnostics.isConfirmed = enteredMtbResult == MtbResult.TB_POSITIVE
                                    tbRepo.getTBDiagnosticsById(benId)?.trueNatOrderId?.let { tbDiagnostics.trueNatOrderId = it }

                                    when (enteredMtbResult) {
                                        MtbResult.TB_POSITIVE -> {
                                            // TB Positive always creates a RIF order, regardless of X-ray.
                                            ordersToPushAfterSave += "MDR_RIF"
                                        }
                                        MtbResult.INVALID_ERROR -> {
                                            // Manual Invalid/Error: re-order immediately, mirrors X-Ray's AI-Invalid.
                                            ordersToPushAfterSave += "SPUTUM_TRUENAT"
                                        }
                                        else -> Unit // TB Negative — no cascade
                                    }
                                } else {
                                    apiSuccess = false
                                    apiError = (res as? NetworkResponse.Error)?.message ?: "Submit Manual Result Failed"
                                }
                            }
                            else -> {
                                // Conducted = Yes but no manual value chosen yet — device/AI
                                // result is still pending automatically.
                                tbDiagnostics.trueNatOrderStatus = "PENDING"
                            }
                        }

                        // ── RIF — only reachable once MTB is Completed + Positive; the RIF order
                        // itself is always auto-created by the MTB-Positive cascade above (no
                        // "not referred" state of its own), so this is a 2-way
                        // Enter-Result/Not-Conducted restructure, same standing-actions principle.
                        // rifOrderExisted must be true, or this is the save that's still queuing
                        // the RIF order — submitting a result now would race it.
                        val isMtbAlreadyCompleted = tbDiagnostics.trueNatOrderStatus.equals("COMPLETED", ignoreCase = true)
                        val rifOrderExisted = !tbRepo.getTBDiagnosticsById(benId)?.rifOrderStatus.isNullOrBlank()
                        if (apiSuccess && isMtbAlreadyCompleted && rifOrderExisted && dataset.isMtbDetected()) {
                            val rifConductedVal = dataset.rifConducted.value
                            val enteredRifResult = org.piramalswasthya.stoptb.model.RifResult
                                .fromResultText(tbDiagnostics.trueNatRifResult)
                            when {
                                rifConductedVal == dataset.noValue -> {
                                    val rifNotConductedReason = {
                                        val r = tbDiagnostics.reasonNotConductedRif
                                        val o = tbDiagnostics.reasonNotConductedRifOther
                                        if (r.equals("Other", ignoreCase = true) && !o.isNullOrBlank()) "Other: $o" else r
                                    }()
                                    val rifRes = tbRepo.submitManualResult(
                                        benId, "MDR_RIF", resultSummary = null, reasonForRefusal = rifNotConductedReason
                                    )
                                    if (rifRes is NetworkResponse.Success) {
                                        if (rifRes.data == "PENDING_SYNC") anyPendingManualResultSync = true
                                        tbDiagnostics.rifOrderStatus = "CLOSED"
                                        tbDiagnostics.trueNatRifResult = null
                                    } else {
                                        apiSuccess = false
                                        apiError = (rifRes as? NetworkResponse.Error)?.message ?: "RIF Not Conducted Submission Failed"
                                    }
                                }
                                enteredRifResult != null -> {
                                    val rifRes = tbRepo.submitManualResult(benId, "MDR_RIF", resultSummary = enteredRifResult.displayValue)
                                    if (rifRes is NetworkResponse.Success) {
                                        if (rifRes.data == "PENDING_SYNC") anyPendingManualResultSync = true
                                        tbDiagnostics.rifOrderStatus = "COMPLETED"
                                        tbDiagnostics.trueNatRifResult = when (enteredRifResult) {
                                            RifResult.DR_TB -> "Rif Resistance Detected"
                                            RifResult.NON_DR_TB -> "Rif Resistance Not Detected"
                                            else -> enteredRifResult.displayValue
                                        }
                                        tbDiagnostics.isDrTbConfirmed = enteredRifResult == RifResult.DR_TB
                                        if (enteredRifResult == RifResult.DR_TB ||
                                            enteredRifResult == RifResult.NON_DR_TB
                                        ) {
                                            tbDiagnostics.isConfirmed = true
                                            tbDiagnostics.isTBConfirmed = true
                                        }
                                        tbRepo.getTBDiagnosticsById(benId)?.rifOrderId?.let { tbDiagnostics.rifOrderId = it }

                                        // Indeterminate is terminal; Invalid/Error re-orders, mirroring MTB.
                                        if (enteredRifResult == RifResult.INVALID_ERROR) {
                                            ordersToPushAfterSave += "MDR_RIF"
                                        }
                                    } else {
                                        apiSuccess = false
                                        apiError = (rifRes as? NetworkResponse.Error)?.message ?: "Submit RIF Manual Result Failed"
                                    }
                                }
                                else -> Unit // RIF section not answered on this save
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
                                rifOrderId = freshDiag.rifOrderId ?: tbDiagnostics.rifOrderId,
                                // Owned exclusively by TBRepo.submitManualResult's offline-first
                                // path — never take these from the stale snapshot, or a result
                                // saved while the hub was disconnected never gets replayed.
                                xrayManualResultPendingSync = freshDiag.xrayManualResultPendingSync,
                                trueNatManualResultPendingSync = freshDiag.trueNatManualResultPendingSync,
                                rifManualResultPendingSync = freshDiag.rifManualResultPendingSync
                            )
                        }
                        tbDiagnostics.syncState = SyncState.UNSYNCED
                        tbRepo.saveTBDiagnostics(tbDiagnostics)
                        tbRepo.syncTBSuspectedFromDiagnostics(benId, tbDiagnostics)

                        // Enqueued only now that the save has landed — see ordersToPushAfterSave above.
                        if (ordersToPushAfterSave.isNotEmpty()) {
                            WorkerUtils.triggerDiagnosticOrderPushWorkers(context, benId, ordersToPushAfterSave)
                        }

                        val updatedDiag = tbRepo.getTBDiagnosticsById(benId)
                        // Real order/result contract: all three order types represent "awaiting
                        // automated result" as PENDING (AWAITING_PROVIDER_RESULT is never
                        // actually stored — see TBRepo.reducedOrderStatus).
                        val xrayAwaiting = updatedDiag?.xrayOrderStatus.equals("PENDING", ignoreCase = true)
                        val truenatAwaiting = updatedDiag?.trueNatOrderStatus.equals("PENDING", ignoreCase = true)
                        val rifAwaiting = updatedDiag?.rifOrderStatus.equals("PENDING", ignoreCase = true)
                        
                        if ((xrayAwaiting && isXrayDevIntegrated) || 
                            ((truenatAwaiting || rifAwaiting) && isTruenatDevIntegrated)) {
                            WorkerUtils.triggerDiagnosticResultPollWorker(context)
                        }

                        _savedOfflinePendingSync.postValue(anyPendingManualResultSync)
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
