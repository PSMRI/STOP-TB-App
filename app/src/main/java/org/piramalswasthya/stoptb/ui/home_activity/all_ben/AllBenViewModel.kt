package org.piramalswasthya.stoptb.ui.home_activity.all_ben

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.piramalswasthya.stoptb.model.TBDiagnosticsCache
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.model.BenBasicDomain
import org.piramalswasthya.stoptb.repositories.ABHAGenratedRepo
import org.piramalswasthya.stoptb.repositories.BenRepo
import org.piramalswasthya.stoptb.repositories.RecordsRepo
import org.piramalswasthya.stoptb.repositories.TBRepo
import org.piramalswasthya.stoptb.repositories.VitalRepo
import org.piramalswasthya.stoptb.repositories.contactTracing.IContactTracingRepository
import java.io.File
import java.io.FileWriter
import javax.inject.Inject

import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import org.piramalswasthya.stoptb.database.room.SyncState

@HiltViewModel
class AllBenViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recordsRepo: RecordsRepo,
    abhaGenratedRepo: ABHAGenratedRepo,
    private val benRepo: BenRepo,
    private val vitalRepo: VitalRepo,
    val tbRepo: TBRepo,
    private val contactTracingRepo: IContactTracingRepository
) : ViewModel() {

    private var sourceFromArgs = AllBenFragmentArgs.fromSavedStateHandle(savedStateHandle).source

    private val filterOrg = MutableStateFlow("")
    private val kindOrg = MutableStateFlow(0)

    init {
        fetchBeneficiaryStatuses()
        viewModelScope.launch {
            tbRepo.refreshDeviceIntegrationConfig()
        }
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val benList: Flow<PagingData<BenBasicDomain>> = combine(filterOrg, kindOrg) { text, kind ->
        Pair(text, kind)
    }.debounce { (text, _) ->
        if (text.isEmpty()) 0L else 300L
    }.flatMapLatest { (text, kind) ->
        Pager(
            config = PagingConfig(pageSize = 30, prefetchDistance = 10)
        ) {
            recordsRepo.searchBenPagedSource(text, kind, sourceFromArgs)
        }.flow.map { pagingData ->
            pagingData.map { it.asBasicDomainModel() }
        }
    }

    val childCounts: Flow<Map<Long, Int>> = recordsRepo.childCountsByBen
    val vitalBenIds: Flow<List<Long>> = vitalRepo.vitalBenIds
    val tbScreeningBenIds: Flow<List<Long>> = tbRepo.tbScreeningBenIds
    val generalOpdBenIds: Flow<List<Long>> = tbRepo.generalOpdBenIds
    val anthropometryFilledBenIds: Flow<List<Long>> = recordsRepo.anthropometryFilledBenIds
    val contactFollowUpDoneBenIds: Flow<List<Long>> = contactTracingRepo.observeContactFollowUpDoneBenIds()
    val tptFollowUpDoneBenIds: Flow<List<Long>> = contactTracingRepo.observeTptFollowUpTargetReachedBenIds()
    val tptEligibleBenIds: Flow<List<Long>> = contactTracingRepo.observeTptEligibleBenIds()

    /** Diagnosis = TB_DIAGNOSTICS (new saves) OR TB_SUSPECTED (legacy saves) */
    val diagnosisBenIds: Flow<List<Long>> = combine(
        tbRepo.tbDiagnosticsBenIds,
        tbRepo.tbSuspectedBenIds
    ) { diagnostics, suspected -> (diagnostics + suspected).distinct() }

    private val _abha = MutableLiveData<String?>()
    val abha: LiveData<String?>
        get() = _abha

    private val _benId = MutableLiveData<Long?>()
    val benId: LiveData<Long?>
        get() = _benId

    private val _benRegId = MutableLiveData<Long?>()
    val benRegId: LiveData<Long?>
        get() = _benRegId

    fun filterText(text: String) {
        viewModelScope.launch {
            filterOrg.emit(text)
        }

    }

    fun filterType(type: Int) {
        viewModelScope.launch {
            kindOrg.emit(type)
        }

    }

    fun fetchAbha(benId: Long) {
        _abha.value = null
        _benRegId.value = null
        _benId.value = benId
        viewModelScope.launch {
            benRepo.getBenFromId(benId)?.let {
                _benRegId.value = it.benRegId
            }
        }
    }

    suspend fun getBenFromId(benId: Long):Long{
        var benRegId = 0L
             val result = benRepo.getBenFromId(benId)
             if (result != null) {
                 benRegId = result.benRegId
             }
         return benRegId
    }
    fun resetBenRegId() {
        _benRegId.value = null
    }

    fun downloadCsv(context: Context) {
        viewModelScope.launch {
            val users = recordsRepo.searchBenOnce(filterOrg.value, kindOrg.value, sourceFromArgs)
            if (users.isNotEmpty()) {
                createCsvFile(context, users)
            } else {
                Toast.makeText(context, "No data to export", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createCsvFile(context: Context, users: List<BenBasicDomain>): File? {
        return try {
            val fileName = "ABHAUsers_${System.currentTimeMillis()}.csv"
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()

            val file = File(downloadsDir, fileName)

            FileWriter(file).use { writer ->

                writer.append("Ben ID,Beneficiary Name,Mobile,ABHA ID,Age,IsNewAbha,RCH ID\n")
                for (user in users) {
                    writer.append("${user.benId}\t,${user.benFullName},${user.mobileNo},${user.abhaId},${user.age},${user.isNewAbha},${user.rchId}\t\n")
                }
            }
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)

            Toast.makeText(context, "CSV Downloaded: ${file.name}", Toast.LENGTH_LONG).show()

            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    val allTbDiagnostics: Flow<List<TBDiagnosticsCache>> = tbRepo.allTbDiagnostics

    sealed class OrderActionResult {
        object Idle : OrderActionResult()
        object Loading : OrderActionResult()
        data class Success(val message: String, val orderType: String = "") : OrderActionResult()
        data class Error(val error: String) : OrderActionResult()
    }

    private val _orderActionState = MutableStateFlow<OrderActionResult>(OrderActionResult.Idle)
    val orderActionState: StateFlow<OrderActionResult> = _orderActionState.asStateFlow()

    fun resetOrderActionState() {
        _orderActionState.value = OrderActionResult.Idle
    }

    fun initiateProdigiOrder(benId: Long, orderType: String) {
        viewModelScope.launch {
            _orderActionState.value = OrderActionResult.Loading
            when (val response = tbRepo.createProdigiOrder(benId, orderType)) {
                is org.piramalswasthya.stoptb.helpers.NetworkResponse.Success -> {
                    _orderActionState.value = OrderActionResult.Success("Order created successfully. Order ID: ${response.data}", orderType)
                }
                is org.piramalswasthya.stoptb.helpers.NetworkResponse.Error -> {
                    _orderActionState.value = OrderActionResult.Error(response.message ?: "Failed to create order")
                }
                else -> {}
            }
        }
    }

    fun fetchBeneficiaryStatuses(orderType: String? = null) {
        viewModelScope.launch {
            if (orderType != null) {
                tbRepo.fetchBeneficiariesByStatus(orderType)
            } else {
                when (sourceFromArgs) {
                    6 -> tbRepo.fetchBeneficiariesByStatus("XRAY_CHEST")
                    7 -> {
                        tbRepo.fetchBeneficiariesByStatus("SPUTUM_TRUENAT")
                        tbRepo.fetchBeneficiariesByStatus("MDR_RIF")
                    }
                    else -> {}
                }
            }
        }
    }

    fun markOrderTestCompleted(benId: Long, orderType: String) {
        viewModelScope.launch {
            _orderActionState.value = OrderActionResult.Loading
            when (val response = tbRepo.markTestCompleted(benId, orderType)) {
                is org.piramalswasthya.stoptb.helpers.NetworkResponse.Success -> {
                    fetchBeneficiaryStatuses(orderType)
                    _orderActionState.value = OrderActionResult.Success("Test marked as completed. Status: ${response.data}", orderType)
                }
                is org.piramalswasthya.stoptb.helpers.NetworkResponse.Error -> {
                    _orderActionState.value = OrderActionResult.Error(response.message ?: "Failed to mark test completed")
                }
                else -> {}
            }
        }
    }

    fun pollOrderResult(benId: Long, orderType: String) {
        viewModelScope.launch {
            _orderActionState.value = OrderActionResult.Loading
            when (val response = tbRepo.fetchOrderResult(benId, orderType)) {
                is org.piramalswasthya.stoptb.helpers.NetworkResponse.Success -> {
                    fetchBeneficiaryStatuses(orderType)
                    _orderActionState.value = OrderActionResult.Success("Result fetched successfully. Status: ${response.data}", orderType)
                }
                is org.piramalswasthya.stoptb.helpers.NetworkResponse.Error -> {
                    _orderActionState.value = OrderActionResult.Error(response.message ?: "Failed to fetch result")
                }
                else -> {}
            }
        }
    }

    fun repeatTest(benId: Long, orderType: String, customVisitCode: Int? = null) {
        viewModelScope.launch {
            _orderActionState.value = OrderActionResult.Loading
            when (val response = tbRepo.createProdigiOrder(benId, orderType, customVisitCode)) {
                is org.piramalswasthya.stoptb.helpers.NetworkResponse.Success -> {
                    _orderActionState.value = OrderActionResult.Success("Fresh repeat test order created. Status: ${response.data}", orderType)
                }
                is org.piramalswasthya.stoptb.helpers.NetworkResponse.Error -> {
                    _orderActionState.value = OrderActionResult.Error(response.message ?: "Failed to create repeat test order")
                }
                else -> {}
            }
        }
    }

    private val activePushingBenIds = java.util.concurrent.ConcurrentHashMap<Long, Boolean>()



    suspend fun triggerXrayOrderPush(benId: Long) {
        val maxRetries = 1
        var attempt = 0
        var success = false
        while (attempt <= maxRetries && !success) {
            updateDiagnosticsOrderStatus(benId, "XRAY_CHEST", "CREATING")
            val response = tbRepo.createProdigiOrder(benId, "XRAY_CHEST")
            if (response is org.piramalswasthya.stoptb.helpers.NetworkResponse.Success) {
                success = true
            } else {
                attempt++
                if (attempt <= maxRetries) {
                    delay(5000L)
                }
            }
        }
        if (!success) {
            updateDiagnosticsOrderStatus(benId, "XRAY_CHEST", "FAILED")
        }
    }

    suspend fun triggerMtbOrderPush(benId: Long) {
        val maxRetries = 1
        var attempt = 0
        var success = false
        while (attempt <= maxRetries && !success) {
            updateDiagnosticsOrderStatus(benId, "SPUTUM_TRUENAT", "CREATING")
            val response = tbRepo.createProdigiOrder(benId, "SPUTUM_TRUENAT")
            if (response is org.piramalswasthya.stoptb.helpers.NetworkResponse.Success) {
                success = true
            } else {
                attempt++
                if (attempt <= maxRetries) {
                    delay(5000L)
                }
            }
        }
        if (!success) {
            updateDiagnosticsOrderStatus(benId, "SPUTUM_TRUENAT", "FAILED")
        }
    }

    suspend fun triggerRifOrderPush(benId: Long) {
        val maxRetries = 1
        var attempt = 0
        var success = false
        while (attempt <= maxRetries && !success) {
            updateDiagnosticsOrderStatus(benId, "MDR_RIF", "CREATING")
            val response = tbRepo.createProdigiOrder(benId, "MDR_RIF")
            if (response is org.piramalswasthya.stoptb.helpers.NetworkResponse.Success) {
                success = true
            } else {
                attempt++
                if (attempt <= maxRetries) {
                    delay(5000L)
                }
            }
        }
        if (!success) {
            updateDiagnosticsOrderStatus(benId, "MDR_RIF", "FAILED")
        }
    }

    fun retryXrayOrder(benId: Long) {
        viewModelScope.launch {
            triggerXrayOrderPush(benId)
        }
    }

    fun retryMtbOrder(benId: Long) {
        viewModelScope.launch {
            triggerMtbOrderPush(benId)
        }
    }

    fun retryRifOrder(benId: Long) {
        viewModelScope.launch {
            triggerRifOrderPush(benId)
        }
    }

    private suspend fun updateDiagnosticsOrderStatus(benId: Long, orderType: String, status: String) {
        val existing = tbRepo.getTBDiagnosticsById(benId)
        val cache = (existing ?: TBDiagnosticsCache(benId = benId)).let {
            if (orderType.equals("XRAY_CHEST", ignoreCase = true)) {
                it.copy(xrayOrderStatus = status, syncState = SyncState.SYNCED)
            } else if (orderType.equals("MDR_RIF", ignoreCase = true)) {
                it.copy(rifOrderStatus = status, syncState = SyncState.SYNCED)
            } else {
                it.copy(trueNatOrderStatus = status, syncState = SyncState.SYNCED)
            }
        }
        tbRepo.saveTBDiagnostics(cache)
    }

    fun retryResultFetch(benId: Long, orderType: String, context: Context) {
        viewModelScope.launch {
            _orderActionState.value = OrderActionResult.Loading
            val response = tbRepo.retryProdigiOrder(benId, orderType)
            if (response is org.piramalswasthya.stoptb.helpers.NetworkResponse.Success) {
                val existing = tbRepo.getTBDiagnosticsById(benId)
                existing?.let {
                    val cache = if (orderType.equals("XRAY_CHEST", ignoreCase = true)) {
                        it.copy(xrayOrderStatus = "AWAITING_PROVIDER_RESULT", syncState = SyncState.SYNCED)
                    } else if (orderType.equals("MDR_RIF", ignoreCase = true)) {
                        it.copy(rifOrderStatus = "AWAITING_PROVIDER_RESULT", syncState = SyncState.SYNCED)
                    } else {
                        it.copy(trueNatOrderStatus = "AWAITING_PROVIDER_RESULT", syncState = SyncState.SYNCED)
                    }
                    tbRepo.saveTBDiagnostics(cache)
                }
                if (orderType.equals("XRAY_CHEST", ignoreCase = true)) {
                    org.piramalswasthya.stoptb.work.WorkerUtils.triggerDiagnosticResultPollWorker(context)
                } else if (orderType.equals("MDR_RIF", ignoreCase = true)) {
                    org.piramalswasthya.stoptb.work.WorkerUtils.triggerRifDiagnosticResultPollWorker(context, tbRepo.useMockApi)
                } else {
                    org.piramalswasthya.stoptb.work.WorkerUtils.triggerTrueNatDiagnosticResultPollWorker(context, tbRepo.useMockApi)
                }
                _orderActionState.value = OrderActionResult.Success("Result fetch retried successfully.", orderType)
            } else {
                val errorMsg = (response as? org.piramalswasthya.stoptb.helpers.NetworkResponse.Error)?.message ?: "Failed to retry order"
                _orderActionState.value = OrderActionResult.Error(errorMsg)
            }
        }
    }

    fun retryTest(benId: Long, orderType: String, context: Context) {
        viewModelScope.launch {
            _orderActionState.value = OrderActionResult.Loading
            val response = tbRepo.createProdigiOrder(benId, orderType)
            if (response is org.piramalswasthya.stoptb.helpers.NetworkResponse.Success) {
                val existing = tbRepo.getTBDiagnosticsById(benId)
                existing?.let {
                    val cache = if (orderType.equals("XRAY_CHEST", ignoreCase = true)) {
                        it.copy(
                            xrayOrderStatus = "AWAITING_PROVIDER_RESULT",
                            isChestXRayDone = true,
                            chestXRayResult = null,
                            syncState = SyncState.SYNCED
                        )
                    } else if (orderType.equals("MDR_RIF", ignoreCase = true)) {
                        it.copy(
                            rifOrderStatus = "AWAITING_PROVIDER_RESULT",
                            trueNatRifResult = null,
                            syncState = SyncState.SYNCED
                        )
                    } else {
                        it.copy(
                            trueNatOrderStatus = "AWAITING_PROVIDER_RESULT",
                            isNaatConducted = true,
                            naatResult = null,
                            rifOrderId = null,
                            rifOrderStatus = null,
                            trueNatRifResult = null,
                            syncState = SyncState.SYNCED
                        )
                    }
                    tbRepo.saveTBDiagnostics(cache)
                }

                if (orderType.equals("XRAY_CHEST", ignoreCase = true)) {
                    org.piramalswasthya.stoptb.work.WorkerUtils.triggerDiagnosticResultPollWorker(context)
                } else if (orderType.equals("MDR_RIF", ignoreCase = true)) {
                    org.piramalswasthya.stoptb.work.WorkerUtils.triggerRifDiagnosticResultPollWorker(context, tbRepo.useMockApi)
                } else {
                    org.piramalswasthya.stoptb.work.WorkerUtils.triggerTrueNatDiagnosticResultPollWorker(context, tbRepo.useMockApi)
                }
                
                _orderActionState.value = OrderActionResult.Success("New order created and workflow restarted.", orderType)
            } else {
                val errorMsg = (response as? org.piramalswasthya.stoptb.helpers.NetworkResponse.Error)?.message ?: "Failed to create new order"
                _orderActionState.value = OrderActionResult.Error(errorMsg)
            }
        }
    }
}
