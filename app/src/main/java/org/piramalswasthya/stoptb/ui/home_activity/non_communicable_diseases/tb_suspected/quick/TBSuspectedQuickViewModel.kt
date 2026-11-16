package org.piramalswasthya.stoptb.ui.home_activity.non_communicable_diseases.tb_suspected.quick

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.piramalswasthya.stoptb.configuration.TBSuspectedQuickDataset
import org.piramalswasthya.stoptb.R
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.helpers.NetworkResponse
import org.piramalswasthya.stoptb.helpers.serverResultOrNull
import org.piramalswasthya.stoptb.model.TBDiagnosticsCache
import org.piramalswasthya.stoptb.model.BenRegCache
import org.piramalswasthya.stoptb.model.ChestXrayResult
import org.piramalswasthya.stoptb.model.OrderStatus
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
        IDLE, SAVING, SAVE_SUCCESS, SAVE_FAILED, RESULT_ALREADY_RECORDED
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

    /** True only while the local row is being read, which is a handful of indexed lookups. */
    private val _isLoading = MutableLiveData(true)
    val isLoading: LiveData<Boolean> = _isLoading

    /** State of the advisory background check for a newer order result. */
    enum class ResultCheck { IDLE, CHECKING, FAILED, NEWER_AVAILABLE }

    /**
     * The check is advisory, never a gate: it can fail, time out, or find nothing, and the user
     * may still enter a result either way. It is surfaced as a status line beside the form rather
     * than by disabling Submit - the backend is the authority on a duplicate manual result and
     * already rejects one ("order already COMPLETED"), so blocking the only action on this screen
     * while an optional network read runs would cost a field worker up to the client's 60 s read
     * timeout to prevent an error the server handles correctly.
     */
    private val _resultCheck = MutableLiveData(ResultCheck.IDLE)
    val resultCheck: LiveData<ResultCheck> = _resultCheck

    /** Advisory check gets its own bound; OkHttp's own read timeout is 60 s. */
    private val RESULT_CHECK_TIMEOUT_MS = 8_000L

    /** The result the backend already held when it rejected this submission. */
    private val _alreadyRecordedResult = MutableLiveData<String?>(null)
    val alreadyRecordedResult: LiveData<String?> = _alreadyRecordedResult

    /** Set as soon as the user touches a field; see [refreshLatestOrderResult]. */
    private var userHasEditedForm = false

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
                        isReferredForSputum = legacySuspected.isSputumCollected,
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

            }

            // Build the form from the local row and show it straight away. Fetching the latest
            // order result used to run inside the withContext above, so formList stayed empty -
            // and the screen blank, with no spinner - for the whole order/result round trip,
            // measured at 108 ms to 18.7 s against this backend.
            renderPage(ben, tbScreening, vital)
            _isLoading.value = false

            observeLatestOrderResult(ben, tbScreening, vital)
        }
    }

    private fun resultFor(diagnostics: TBDiagnosticsCache, orderType: String): String? =
        when (orderType) {
            "XRAY_CHEST" -> diagnostics.chestXRayResult
            "MDR_RIF" -> diagnostics.trueNatRifResult
            else -> diagnostics.naatResult
        }

    /**
     * Asks the backend what it actually holds after it refused this submission, instead of
     * deciding from the rejection's wording - the message is backend- and locale-dependent, and
     * the question we care about ("is there already a result?") has a direct answer.
     * [TBRepo.fetchOrderResult] stores the authoritative row, so the card shows it afterwards.
     */
    private suspend fun resultAlreadyOnServer(orderType: String): String? {
        return try {
            // The order's own status decides this, not whether the local row happens to hold a
            // string. fetchOrderResult returns Success for a PENDING order too, and
            // applyOrderResult leaves the stored result untouched for a non-completed order - so
            // a stale local value would otherwise be reported back as "the server already has a
            // result", which is the opposite of true.
            val fetched = tbRepo.fetchOrderResult(benId, orderType, pushSuspectedAfter = false)
            serverResultOrNull(fetched, tbRepo.getTBDiagnosticsById(benId), orderType)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Could not read back the server's result after a rejected submission")
            null
        }
    }

    private suspend fun renderPage(
        ben: BenRegCache?,
        tbScreening: TBScreeningCache?,
        vital: VitalCache?
    ) {
        dataset.setUpPage(
            ben,
            tbScreening,
            // A copy: saveForm() maps the form's values onto tbDiagnostics, and the dataset's
            // view of what is saved (canEnterRif, locks) must not change with them — otherwise a
            // RIF result being entered looks already saved and is never sent (STOP-800).
            if (::tbDiagnostics.isInitialized) tbDiagnostics.copy() else null,
            vital = vital,
            referralMode = viewOnly,
            referralType = referralType,
            manualEntryAction = manualEntryAction
        )
        _showSubmit.value = dataset.shouldShowSubmit()
    }

    /**
     * STOP-800: results reach the tablet only through the changes feed, so this screen does not
     * call the server when it opens. It watches the beneficiary's diagnostics row instead: when
     * the feed brings a newer result (a vendor result can also replace a manual one), an untouched
     * form is rebuilt from it; a form the user has started filling is left as typed and flagged.
     */
    private suspend fun observeLatestOrderResult(
        ben: BenRegCache?,
        tbScreening: TBScreeningCache?,
        vital: VitalCache?
    ) {
        if (!::tbDiagnostics.isInitialized) return
        val orderType = if (referralType == 6) "XRAY_CHEST" else "SPUTUM_TRUENAT"
        tbRepo.observeTBDiagnostics(benId).collect { latest ->
            if (latest == null || !orderStateDiffers(latest, tbDiagnostics)) return@collect
            // This screen's own save writes the row too; that is not a newer result.
            val state = _state.value
            if (state == State.SAVING || state == State.SAVE_SUCCESS) return@collect
            if (!userHasEditedForm) {
                tbDiagnostics = latest
                renderPage(ben, tbScreening, vital)
                _showSubmit.value = dataset.shouldShowSubmit()
            } else if (resultFor(latest, orderType) != resultFor(tbDiagnostics, orderType) ||
                resultFor(latest, "MDR_RIF") != resultFor(tbDiagnostics, "MDR_RIF")
            ) {
                _resultCheck.value = ResultCheck.NEWER_AVAILABLE
            }
        }
    }

    /** Whether the order/result fields differ (form-only fields are ignored). */
    private fun orderStateDiffers(a: TBDiagnosticsCache, b: TBDiagnosticsCache): Boolean =
        a.xrayOrderId != b.xrayOrderId || a.xrayOrderStatus != b.xrayOrderStatus || a.chestXRayResult != b.chestXRayResult ||
            a.trueNatOrderId != b.trueNatOrderId || a.trueNatOrderStatus != b.trueNatOrderStatus || a.naatResult != b.naatResult ||
            a.rifOrderStatus != b.rifOrderStatus || a.trueNatRifResult != b.trueNatRifResult ||
            a.trueNatManualResultPendingSync != b.trueNatManualResultPendingSync ||
            a.rifManualResultPendingSync != b.rifManualResultPendingSync ||
            a.xrayManualResultPendingSync != b.xrayManualResultPendingSync

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

    /** False until the saved diagnostics row has been read; the getters above return null till then. */
    fun isDiagnosticsLoaded(): Boolean = ::tbDiagnostics.isInitialized

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

    /** One manual result or "Not Conducted" this save sends to order/manualResult. */
    data class ManualSubmission(
        /** XRAY_CHEST, SPUTUM_TRUENAT (MTB) or MDR_RIF (the RIF test of the MTB order). */
        val orderType: String,
        /** The server's wording ("TB Positive", "DR TB" ...); null for Not Conducted. */
        val wireResult: String?,
        /** The Not Conducted reason ("Other: text" for a free-text one); null for a result. */
        val reason: String?
    )

    /**
     * What a save of the form, as currently filled in, would send — one test at most per save.
     * The single source of truth for both the confirmation dialog ([previewSubmissions]) and
     * [saveForm], so the dialog can never show something other than what is sent.
     * [mapped] is the row after the form's values were mapped onto it; [stored] the row in Room.
     */
    private fun planSubmissions(mapped: TBDiagnosticsCache, stored: TBDiagnosticsCache?): List<ManualSubmission> {
        fun reason(r: String?, other: String?) =
            if (r.equals("Other", ignoreCase = true) && !other.isNullOrBlank()) "Other: $other" else r
        return when (referralType) {
            6 -> {
                val entered = ChestXrayResult.fromResultText(mapped.chestXRayResult)
                when {
                    mapped.isReferredForDigitalChestXray != true -> emptyList()
                    mapped.isChestXRayDone != true -> listOf(
                        ManualSubmission("XRAY_CHEST", null, reason(mapped.reasonNotConductedChestXray, mapped.reasonNotConductedChestXrayOther))
                    )
                    entered != null && !stored?.xrayOrderStatus.equals("COMPLETED", ignoreCase = true) &&
                            stored?.chestXRayResult.isNullOrBlank() ->
                        listOf(ManualSubmission("XRAY_CHEST", entered.displayValue, null))
                    else -> emptyList()
                }
            }
            7 -> {
                val entered = MtbResult.fromResultText(mapped.naatResult)
                val mtb = when {
                    mapped.isReferredForSputum != true -> null
                    mapped.isSputumCollected != true ->
                        ManualSubmission("SPUTUM_TRUENAT", null, reason(mapped.reasonForDenialSputum, mapped.reasonForDenialSputumOther))
                    mapped.isNaatConducted != true ->
                        ManualSubmission("SPUTUM_TRUENAT", null, reason(mapped.reasonNotConductedNaat, mapped.reasonNotConductedNaatOther))
                    entered != null && stored?.naatResult.isNullOrBlank() ->
                        ManualSubmission("SPUTUM_TRUENAT", entered.displayValue, null)
                    else -> null
                }
                if (mtb != null) return listOf(mtb)
                // STOP-800: RIF goes on its own save, once MTB Detected is on the server.
                if (!dataset.canEnterRif()) return emptyList()
                val enteredRif = RifResult.fromResultText(mapped.trueNatRifResult)
                when {
                    dataset.rifConducted.value == dataset.noValue -> listOf(
                        ManualSubmission("MDR_RIF", null, reason(mapped.reasonNotConductedRif, mapped.reasonNotConductedRifOther))
                    )
                    enteredRif != null -> listOf(ManualSubmission("MDR_RIF", enteredRif.displayValue, null))
                    else -> emptyList()
                }
            }
            else -> emptyList()
        }
    }

    /**
     * The submissions a save would make right now, for the confirmation dialog. Maps the form onto
     * a copy, so nothing is changed until the user confirms. Empty when the save sends no manual
     * result (the dialog is then skipped).
     */
    suspend fun previewSubmissions(): List<ManualSubmission> = withContext(Dispatchers.IO) {
        if (viewOnly || !::tbDiagnostics.isInitialized) return@withContext emptyList()
        val copy = tbDiagnostics.copy()
        dataset.mapValues(copy, 1)
        planSubmissions(copy, tbRepo.getTBDiagnosticsById(benId))
    }

    fun saveForm() {
        // Both set on the caller's (main) thread, before the coroutine starts. SAVING used to be
        // posted from inside the IO block below, which left a window where a second tap still
        // saw IDLE and fired a second order/manualResult POST for the same result.
        if (_state.value == State.SAVING) return
        _state.value = State.SAVING
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    dataset.mapValues(tbDiagnostics, 1)
                    val storedAtStart = tbRepo.getTBDiagnosticsById(benId)
                    val plan = planSubmissions(tbDiagnostics, storedAtStart)
                    fun planned(orderType: String, isResult: Boolean) =
                        plan.firstOrNull { it.orderType == orderType && (it.wireResult != null) == isResult }

                    val isXrayDevIntegrated = tbRepo.isXrayIntegrated()
                    val isTruenatDevIntegrated = tbRepo.isTruenatIntegrated()

                    var apiSuccess = true
                    var apiError: String? = null
                    // Which order the rejection was about, so the conflict check asks the
                    // backend about that order rather than the screen's primary one.
                    var failedOrderType: String? = null
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
                                    failedOrderType = "XRAY_CHEST"
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
                                    if (res.data == TBRepo.PENDING_SYNC) anyPendingManualResultSync = true
                                    tbDiagnostics.xrayOrderStatus = "CLOSED"
                                    tbDiagnostics.chestXRayResult = null
                                } else {
                                    apiSuccess = false
                                    apiError = (res as? NetworkResponse.Error)?.message ?: "Not Conducted Submission Failed"
                                    failedOrderType = "XRAY_CHEST"
                                }
                            }
                            planned("XRAY_CHEST", isResult = true) != null && enteredXrayResult != null -> {
                                // Enter Result manually — a standing action whenever the order is
                                // Pending/Awaiting Manual Entry, not just a device-integration
                                // fallback. Excludes an already-COMPLETED order so re-saving this
                                // screen (e.g. only to touch an unrelated field) doesn't resubmit
                                // an identical result the backend already accepted.
                                val res = tbRepo.submitManualResult(benId, "XRAY_CHEST", resultSummary = enteredXrayResult.displayValue)
                                if (res is NetworkResponse.Success) {
                                    if (res.data == TBRepo.PENDING_SYNC) anyPendingManualResultSync = true
                                    // Status and result are the server's answer, already in Room
                                    // (TBRepo.submitManualResult); taken over below.

                                    // Only once the backend has accepted it: an entry that is merely
                                    // stored locally may still be refused, and the orders it raises are
                                    // not undone when it is. The replay sweep cascades on acceptance.
                                    if (res.data != TBRepo.PENDING_SYNC) {
                                        ordersToPushAfterSave += tbRepo.cascadeOrdersForConfirmedResult(
                                            benId, "XRAY_CHEST", enteredXrayResult.displayValue
                                        )
                                    }
                                } else {
                                    apiSuccess = false
                                    apiError = (res as? NetworkResponse.Error)?.message ?: "Submit Manual Result Failed"
                                    failedOrderType = "XRAY_CHEST"
                                }
                            }
                            else -> {
                                if (enteredXrayResult == null) {
                                    // Conducted = Yes but no manual value chosen yet — device/AI
                                    // result is still pending automatically.
                                    tbDiagnostics.xrayOrderStatus = "PENDING"
                                    tbRepo.preferenceDao.setTrackSubmitTime(benId, "XRAY_CHEST", System.currentTimeMillis())
                                    tbRepo.preferenceDao.setDiagPollActualStartTime(benId, "XRAY_CHEST", 0L)
                                }
                                // else: already COMPLETED and nothing new entered — leave as-is.
                            }
                        }
                    } else if (referralType == 7) {
                        // TrueNat (MTB) & RIF order lifecycle redesign — the same 3-way
                        // Enter-Result/Not-Conducted/decline restructure Chest X-Ray's
                        // referralType == 6 branch already got above: keyed off what the user
                        // actually did on this screen (form state, already mapped onto
                        // tbDiagnostics by dataset.mapValues() above), NOT device-integration/
                        // camp-hub status — Enter Result / Not Conducted are standing actions now.
                        val isReferredForSputum = tbDiagnostics.isReferredForSputum == true
                        val isSputumCollectedNow = tbDiagnostics.isSputumCollected == true
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
                                    failedOrderType = "SPUTUM_TRUENAT"
                                }
                            }
                            !isSputumCollectedNow -> {
                                // Sample never obtained — closure via order/manualResult, mirrors
                                // the !isMtbConducted branch below exactly, just sourced from
                                // reasonForDenialSputum instead of reasonNotConductedNaat.
                                val sputumNotCollectedReason = {
                                    val r = tbDiagnostics.reasonForDenialSputum
                                    val o = tbDiagnostics.reasonForDenialSputumOther
                                    if (r.equals("Other", ignoreCase = true) && !o.isNullOrBlank()) "Other: $o" else r
                                }()
                                val res = tbRepo.submitManualResult(
                                    benId, "SPUTUM_TRUENAT", resultSummary = null, reasonForRefusal = sputumNotCollectedReason
                                )
                                if (res is NetworkResponse.Success) {
                                    if (res.data == TBRepo.PENDING_SYNC) anyPendingManualResultSync = true
                                    tbDiagnostics.trueNatOrderStatus = "CLOSED"
                                    tbDiagnostics.naatResult = null
                                } else {
                                    apiSuccess = false
                                    apiError = (res as? NetworkResponse.Error)?.message ?: "Sputum Not Collected Submission Failed"
                                    failedOrderType = "SPUTUM_TRUENAT"
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
                                    if (res.data == TBRepo.PENDING_SYNC) anyPendingManualResultSync = true
                                    tbDiagnostics.trueNatOrderStatus = "CLOSED"
                                    tbDiagnostics.naatResult = null
                                } else {
                                    apiSuccess = false
                                    apiError = (res as? NetworkResponse.Error)?.message ?: "Not Conducted Submission Failed"
                                    failedOrderType = "SPUTUM_TRUENAT"
                                }
                            }
                            planned("SPUTUM_TRUENAT", isResult = true) != null && enteredMtbResult != null -> {
                                // Enter Result manually — a standing action whenever the order is
                                // Pending/Awaiting Manual Entry, not just a device-integration
                                // fallback. Excludes an already-COMPLETED order — otherwise a save
                                // whose only intent is to fill in the (now-unlocked) RIF section
                                // below would resubmit this already-accepted MTB result too, and a
                                // backend rejection of that redundant resubmit would fail the whole
                                // save before the RIF section ever runs.
                                val res = tbRepo.submitManualResult(benId, "SPUTUM_TRUENAT", resultSummary = enteredMtbResult.displayValue)
                                if (res is NetworkResponse.Success) {
                                    if (res.data == TBRepo.PENDING_SYNC) anyPendingManualResultSync = true
                                    // STOP-800: the server decides the status (MTB Detected keeps the
                                    // order open for RIF); it is already in Room and taken over below.

                                    // Only once the backend has accepted it: an entry that is merely
                                    // stored locally may still be refused, and the orders it raises are
                                    // not undone when it is. The replay sweep cascades on acceptance.
                                    if (res.data != TBRepo.PENDING_SYNC) {
                                        ordersToPushAfterSave += tbRepo.cascadeOrdersForConfirmedResult(
                                            benId, "SPUTUM_TRUENAT", enteredMtbResult.displayValue
                                        )
                                    }
                                } else {
                                    apiSuccess = false
                                    apiError = (res as? NetworkResponse.Error)?.message ?: "Submit Manual Result Failed"
                                    failedOrderType = "SPUTUM_TRUENAT"
                                }
                            }
                            else -> {
                                if (enteredMtbResult == null) {
                                    // Conducted = Yes but no manual value chosen yet — device/AI
                                    // result is still pending automatically.
                                    tbDiagnostics.trueNatOrderStatus = "PENDING"
                                }
                                // else: already COMPLETED and nothing new entered — leave as-is.
                            }
                        }

                        // ── RIF — STOP-800: a test on the same MTB order, entered on its own save
                        // once MTB Detected is on the server (planSubmissions / canEnterRif).
                        val rifPlan = plan.firstOrNull { it.orderType == "MDR_RIF" }
                        if (apiSuccess && rifPlan != null) {
                            val rifRes = tbRepo.submitManualResult(
                                benId, "MDR_RIF", resultSummary = rifPlan.wireResult,
                                reasonForRefusal = if (rifPlan.wireResult == null) rifPlan.reason else null
                            )
                            if (rifRes is NetworkResponse.Success) {
                                if (rifRes.data == TBRepo.PENDING_SYNC) anyPendingManualResultSync = true
                            } else {
                                apiSuccess = false
                                apiError = (rifRes as? NetworkResponse.Error)?.message ?: "Submit RIF Manual Result Failed"
                                failedOrderType = "MDR_RIF"
                            }
                        }
                    }

                    if (apiSuccess) {
                        tbDiagnostics.syncState = SyncState.UNSYNCED
                        tbDiagnostics = tbRepo.saveTBDiagnosticsFromForm(tbDiagnostics)
                        tbRepo.syncTBSuspectedFromDiagnostics(benId, tbDiagnostics)

                        // Enqueued only now that the save has landed — see ordersToPushAfterSave above.
                        if (ordersToPushAfterSave.isNotEmpty()) {
                            delay(300L)
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
                        val serverResult = failedOrderType?.let { resultAlreadyOnServer(it) }
                        if (serverResult != null) {
                            _alreadyRecordedResult.postValue(serverResult)
                            _state.postValue(State.RESULT_ALREADY_RECORDED)
                        } else {
                            _errorMessage.postValue(apiError ?: "Failed to save data")
                            _state.postValue(State.SAVE_FAILED)
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Saving diagnostics failed for benId=%s", benId)
                    _state.postValue(State.SAVE_FAILED)
                }
            }
        }
    }

    fun updateListOnValueChanged(formId: Int, index: Int) {
        userHasEditedForm = true
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
