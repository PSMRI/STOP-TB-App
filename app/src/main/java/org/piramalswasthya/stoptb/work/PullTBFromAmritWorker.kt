package org.piramalswasthya.stoptb.work

import android.content.pm.ServiceInfo
import android.os.Build
import android.content.Context
import android.os.SystemClock
import android.database.sqlite.SQLiteConstraintException
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.database.room.InAppDb
import org.piramalswasthya.stoptb.repositories.contactTracing.IContactTracingRepository
import org.piramalswasthya.stoptb.ui.counselling_activity.FormType
import org.piramalswasthya.stoptb.helpers.NetworkResponse
import org.piramalswasthya.stoptb.helpers.Konstants
import org.piramalswasthya.stoptb.repositories.TBRepo
import org.piramalswasthya.stoptb.repositories.DrugMasterRepo
import org.piramalswasthya.stoptb.repositories.VitalRepo
import org.piramalswasthya.stoptb.repositories.dynamicRepo.ICounsellingRepository
import timber.log.Timber
import java.util.concurrent.TimeUnit

@HiltWorker
class PullTBFromAmritWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val tbRepo: TBRepo,
    private val drugMasterRepo: DrugMasterRepo,
    private val vitalRepo: VitalRepo,
    private val counsellingRepository: ICounsellingRepository,
    private val preferenceDao: PreferenceDao,
    private val db: InAppDb,
    private val contactTracingRepo: IContactTracingRepository,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val name = "PullTBFromAmritWorker"
        const val Progress = "Progress"
        private const val MAX_RUN_ATTEMPTS = 5

        // How many of the nine domain pulls may hold the database at once. Sized below the
        // transaction executor's 4 threads so a pull in progress can never own every one of
        // them and leave UI queries - Paging's load() in particular - unscheduled.
        private const val MAX_CONCURRENT_DOMAIN_PULLS = 3
    }


    override suspend fun getForegroundInfo(): ForegroundInfo = createForegroundInfo("Syncing data...")

    override suspend fun doWork(): Result {
        val triggerSource = inputData.getString(WorkerUtils.pullTriggerSourceKey) ?: "DIRECT"
        val startedAt = inputData.getLong(WorkerUtils.pullStartedAtKey, SystemClock.elapsedRealtime())
        // Set by PullFromAmritWorker when the changes feed already brought these datasets up to date.
        val feedApplied = inputData.getBoolean(WorkerUtils.changesFeedAppliedKey, false)
        Timber.i("Pull [$triggerSource] TB data started${if (feedApplied) " (TB datasets from the changes feed)" else ""}")

        return try {
            withContext(Dispatchers.IO) {
                val startTime = System.currentTimeMillis()
                var numPages: Int
                val startPage =
                    if (preferenceDao.getLastSyncedTimeStamp() == Konstants.defaultTimeStamp)
                        preferenceDao.getFirstSyncLastSyncedPage()
                    else 0

                try {
                    // All nine domain pulls used to run at once against one database. Measured
                    // on a 9,987-beneficiary camp device, that is what makes the app feel slow:
                    // with a sync running, All Beneficiaries took a median 4,394 ms to show
                    // content and half the opens never showed any, while the same build offline
                    // took 772 ms and presented every time. A no-op `SELECT 1` waited up to
                    // 5,423 ms during the pull against 84-244 ms offline.
                    //
                    // Bounding the fan-out keeps every domain in the same pull and the same
                    // result ordering - it only stops them all holding the database at once, so
                    // reads the UI is waiting on can still be scheduled.
                    //
                    // When the changes feed already brought the six TB datasets up to date, only
                    // counselling, contact tracing / TPT and masters are pulled here.
                    val domainPullSemaphore = Semaphore(MAX_CONCURRENT_DOMAIN_PULLS)
                    val datasetPulls = if (feedApplied) emptyList() else listOf<suspend () -> Boolean>(
                        ::getGeneralExaminationDetails,
                        ::getTbScreeningDetails,
                        ::getGeneralOpdDetails,
                        ::getTbDiagnosticsDetails,
                        ::getTbSuspectedDetails,
                        ::getTbConfirmedDetails,
                    )
                    val domainPulls = datasetPulls + listOf<suspend () -> Boolean>(
                        ::getCounsellingCompletedDetails,
                        ::getContactAndTptFollowUpDetails,
                        ::refreshVisitCategories
                    )
                    val result1 = awaitAll(
                        *domainPulls
                            .map { domainPull -> async { domainPullSemaphore.withPermit { domainPull() } } }
                            .toTypedArray()
                    )

                    val endTime = System.currentTimeMillis()
                    val timeTaken = TimeUnit.MILLISECONDS.toSeconds(endTime - startTime)
                    Timber.d(
                        "Pull [$triggerSource] TB data completed in " +
                                "${TimeUnit.MILLISECONDS.toSeconds(SystemClock.elapsedRealtime() - startedAt)} seconds " +
                                "(worker stage $timeTaken seconds)"
                    )

                    if (result1.all { it }) {
                        syncDiagnosticOrderStatuses()
                        return@withContext Result.success()
                    }
                    // Some TB datasets failed (network or server): try again with
                    // backoff rather than reporting the pull as complete.
                    if (runAttemptCount < MAX_RUN_ATTEMPTS) return@withContext Result.retry()
                    return@withContext Result.failure(workDataOf("worker_name" to "PullTBFromAmritWorker", "error" to "Pull operation returned incomplete results"))
                } catch (e: SQLiteConstraintException) {
                    Timber.e("exception $e raised ${e.message} with stacktrace : ${e.stackTrace}")
                    return@withContext Result.failure(workDataOf("worker_name" to "PullTBFromAmritWorker", "error" to "SQLite constraint: ${e.message}"))
                }

            }

        } catch (e: CancellationException) {
            throw e
        } catch (e: java.lang.Exception) {
            Timber.e("Error occurred in PullTBFromAmritWorker $e ${e.stackTrace}")

            Result.failure(workDataOf("worker_name" to "PullTBFromAmritWorker", "error" to (e.message ?: "Unknown error")))
        }
    }

    private fun createForegroundInfo(progress: String): ForegroundInfo {

        val notification = NotificationCompat.Builder(
            appContext,
            appContext.getString(org.piramalswasthya.stoptb.R.string.notification_sync_channel_id)
        )
            .setContentTitle("Syncing Data")
            .setContentText(progress)
            .setSmallIcon(org.piramalswasthya.stoptb.R.drawable.ic_launcher_foreground)
            .setProgress(100, 0, true)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(1003, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(1003, notification)
        }
    }


    private suspend fun getTbScreeningDetails(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val res = tbRepo.getTBScreeningDetailsFromServer()
                return@withContext res == 1 || res == 0
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e("exception $e raised ${e.message} with stacktrace : ${e.stackTrace}")
            }
            false
        }
    }

    private suspend fun getGeneralExaminationDetails(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val res = vitalRepo.getGeneralExaminationDetailsFromServer()
                return@withContext res == 1 || res == 0
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e("exception $e raised ${e.message} with stacktrace : ${e.stackTrace}")
            }
            false
        }
    }

    private suspend fun refreshVisitCategories(): Boolean {
        tbRepo.refreshVisitCategories()
        tbRepo.refreshChiefComplaintMasters()
        drugMasterRepo.refreshMasters()
        // A failed master refresh must not block the core beneficiary/form pull.
        return true
    }

    private suspend fun getTbSuspectedDetails(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val res = tbRepo.getTbSuspectedDetailsFromServer()
                return@withContext res == 1 || res == 0
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e("exception $e raised ${e.message} with stacktrace : ${e.stackTrace}")
            }
            false
        }
    }

    private suspend fun getGeneralOpdDetails(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val res = tbRepo.getGeneralOpdDetailsFromServer()
                return@withContext res == 1 || res == 0
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e("exception $e raised ${e.message} with stacktrace : ${e.stackTrace}")
            }
            false
        }
    }

    private suspend fun getTbDiagnosticsDetails(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val res = tbRepo.getTbDiagnosticsDetailsFromServer()
                return@withContext res == 1 || res == 0
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e("exception $e raised ${e.message} with stacktrace : ${e.stackTrace}")
            }
            false
        }
    }


    private suspend fun getTbConfirmedDetails(): Boolean {
        return  withContext(Dispatchers.IO) {
            try {
                val res = tbRepo.getTbConfirmedDetailsFromServer()
                return@withContext res == 1 || res == 0
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e("exception $e raised ${e.message} with stacktrace : ${e.stackTrace}")
            }
            false
        }
    }

    private suspend fun getCounsellingCompletedDetails(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                counsellingRepository.fetchAndStoreCompletedBeneficiaries()
            } catch (e: Exception) {
                Timber.e("exception $e raised ${e.message} with stacktrace : ${e.stackTrace}")
            }
            true
        }
    }

    private suspend fun getContactAndTptFollowUpDetails(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val villageId = preferenceDao.getLocationRecord()?.village?.id ?: return@withContext true

                val cfuDefinition = (contactTracingRepo.getFormSchema(FormType.CONTACT_FOLLOW_UP) as? NetworkResponse.Success)?.data
                val cfuActiveVersion = cfuDefinition?.versions?.firstOrNull { it.version.isActive }
                    ?: cfuDefinition?.versions?.maxByOrNull { it.version.versionNumber }
                val cfuVersionId = cfuActiveVersion?.version?.versionId

                val tptDefinition = (contactTracingRepo.getFormSchema(FormType.TPT_FOLLOW_UP) as? NetworkResponse.Success)?.data
                val tptActiveVersion = tptDefinition?.versions?.firstOrNull { it.version.isActive }
                    ?: tptDefinition?.versions?.maxByOrNull { it.version.versionNumber }
                val tptVersionId = tptActiveVersion?.version?.versionId

                val cctDefinition = (contactTracingRepo.getFormSchema(FormType.COMMUNITY_CONTACT_TRACING) as? NetworkResponse.Success)?.data
                val cctActiveVersion = cctDefinition?.versions?.firstOrNull { it.version.isActive }
                    ?: cctDefinition?.versions?.maxByOrNull { it.version.versionNumber }
                val cctVersionId = cctActiveVersion?.version?.versionId

                val octDefinition = (contactTracingRepo.getFormSchema(FormType.OCCUPATION_CONTACT_TRACING) as? NetworkResponse.Success)?.data
                val octActiveVersion = octDefinition?.versions?.firstOrNull { it.version.isActive }
                    ?: octDefinition?.versions?.maxByOrNull { it.version.versionNumber }
                val octVersionId = octActiveVersion?.version?.versionId

                if (cctVersionId != null) {
                    contactTracingRepo.fetchAndStoreVillageContactResponses(villageId, FormType.COMMUNITY_CONTACT_TRACING, cctVersionId)
                }
                if (octVersionId != null) {
                    contactTracingRepo.fetchAndStoreVillageContactResponses(villageId, FormType.OCCUPATION_CONTACT_TRACING, octVersionId)
                }

                if (cfuVersionId != null) {
                    contactTracingRepo.fetchAndStoreVillageContactResponses(villageId, FormType.CONTACT_FOLLOW_UP, cfuVersionId)
                }
                if (tptVersionId != null) {
                    contactTracingRepo.fetchAndRefreshVillageTptHistory(villageId, tptVersionId)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error pre-fetching contact follow-up / TPT responses in PullTBFromAmritWorker")
            }
            true
        }
    }
    private suspend fun syncDiagnosticOrderStatuses() {
        withContext(Dispatchers.IO) {
            try {
                // 1. Fetch statuses for all orders first (populates all local cache entries)
                tbRepo.fetchBeneficiariesByStatus("XRAY_CHEST", fetchResult = false)
                tbRepo.fetchBeneficiariesByStatus("SPUTUM_TRUENAT", fetchResult = false)
                tbRepo.fetchBeneficiariesByStatus("MDR_RIF", fetchResult = false)

                // 2. Fetch results for completed orders (safely references populated caches to avoid duplicate pushes)
                tbRepo.fetchBeneficiariesByStatus("XRAY_CHEST", fetchResult = true)
                tbRepo.fetchBeneficiariesByStatus("SPUTUM_TRUENAT", fetchResult = true)
                tbRepo.fetchBeneficiariesByStatus("MDR_RIF", fetchResult = true)
            } catch (e: Exception) {
                Timber.e(e, "syncDiagnosticOrderStatuses failed")
            }
        }
    }

}
