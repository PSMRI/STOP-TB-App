package org.piramalswasthya.stoptb.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.piramalswasthya.stoptb.helpers.NetworkResponse
import org.piramalswasthya.stoptb.repositories.TBRepo
import timber.log.Timber

// Creates a single diagnostic order (order/push) in the background. This used to be an awaited,
// inline call from TBScreeningFormViewModel's save flow — when the device/camp hub is
// unreachable, that call can block for up to the configured 60s OkHttp timeout, stalling form
// submission for as long as QA reported (40-60s+, one call per referred test type). createOrder()
// already writes PENDING/FAILED status and beneficiary flags internally on its own, same as it
// did when called inline, so offloading it here doesn't change any downstream logic — it only
// stops it from blocking the caller.
@HiltWorker
class DiagnosticOrderPushWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val tbRepo: TBRepo,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val name = "DiagnosticOrderPushWorker"
        const val KEY_BEN_ID = "ben_id"
        const val KEY_TEST_TYPE = "test_type"
    }

    override suspend fun doWork(): Result {
        val benId = inputData.getLong(KEY_BEN_ID, -1L)
        val testType = inputData.getString(KEY_TEST_TYPE)
        if (benId <= 0 || testType.isNullOrBlank()) {
            Timber.e("DiagnosticOrderPushWorker: missing benId/testType input")
            return Result.failure()
        }
        return try {
            withContext(Dispatchers.IO) {
                // Refresh the cached integration flags first — they're only ever set by this
                // call, and a stale cached "not integrated" (e.g. fresh login, or the last
                // refresh ran while the hub was disconnected) would otherwise suppress the
                // triggerDiagnosticResultPollWorker() call below even though the order that was
                // just created actually is device-integrated.
                tbRepo.refreshDeviceIntegrationConfig()
                val response = tbRepo.createOrder(benId, testType)
                val isIntegrated = when (testType) {
                    "XRAY_CHEST" -> tbRepo.isXrayIntegrated()
                    "SPUTUM_TRUENAT", "MDR_RIF" -> tbRepo.isTruenatIntegrated()
                    else -> false
                }
                if (response is NetworkResponse.Success && isIntegrated) {
                    WorkerUtils.triggerDiagnosticResultPollWorker(appContext)
                }
                // createOrder() already handles a business-level push failure on its own (writes
                // FAILED status internally, surfaced via the existing "Retry Referral" action) —
                // that's a successfully-completed unit of work from this worker's point of view,
                // not a crash, so this stays Result.success() even when response is an Error.
                // WorkManager cancels every remaining .then() item in the chain once one item
                // reports failure() — returning failure() here for a handled business error
                // would silently drop a chained sibling push (e.g. TrueNat queued after X-ray)
                // instead of letting it run.
                Result.success()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Let WorkManager's own cancellation (work stopped/replaced, constraints no longer
            // met) propagate normally instead of being reported as a failure.
            throw e
        } catch (e: Exception) {
            Timber.e(e, "DiagnosticOrderPushWorker failed for benId=$benId testType=$testType")
            Result.failure()
        }
    }
}
