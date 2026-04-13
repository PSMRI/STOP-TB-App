package org.piramalswasthya.stoptb.work

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkContinuation
import androidx.work.WorkManager
import org.piramalswasthya.stoptb.work.dynamicWoker.FormSyncWorker
import org.piramalswasthya.stoptb.work.dynamicWoker.NCDFollowUpSyncWorker
import org.piramalswasthya.stoptb.work.dynamicWoker.NDCFollowUpPushWorker
import java.util.concurrent.TimeUnit

object WorkerUtils {

    const val pushWorkerUniqueName = "PUSH-TO-AMRIT"
    const val pullWorkerUniqueName = "PULL-FROM-AMRIT"

    private val networkOnlyConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    private inline fun <reified W : androidx.work.ListenableWorker> syncRequestBuilder() =
        OneTimeWorkRequestBuilder<W>()
            .setConstraints(networkOnlyConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)

    fun triggerAmritPushWorker(context: Context) {
        val workManager = WorkManager.getInstance(context)

        val registration = syncRequestBuilder<PushToAmritWorker>()
            .addTag("push_group1_registration").build()

        val afterRegistration = workManager.beginUniqueWork(
            pushWorkerUniqueName, ExistingWorkPolicy.APPEND_OR_REPLACE, registration)

        val group2Screening = listOf(
            syncRequestBuilder<CbacPushToAmritWorker>().addTag("push_group2_screening").build(),
            syncRequestBuilder<NCDReferPushtoAmritWorker>().addTag("push_group2_screening").build(),
            syncRequestBuilder<NDCFollowUpPushWorker>().addTag("push_group2_screening").build(),
        )

        val group5CommDisease = listOf(
            syncRequestBuilder<PushTBToAmritWorker>().addTag("push_group5_comm_disease").build(),
            syncRequestBuilder<PushMalariaAmritWorker>().addTag("push_group5_comm_disease").build(),
            syncRequestBuilder<pushAesAmritWorker>().addTag("push_group5_comm_disease").build(),
            syncRequestBuilder<pushKalaAzarAmritWorker>().addTag("push_group5_comm_disease").build(),
            syncRequestBuilder<pushLeprosyAmritWorker>().addTag("push_group5_comm_disease").build(),
            syncRequestBuilder<PushLeprosyFollowUpAmritWorker>().addTag("push_group5_comm_disease").build(),
            syncRequestBuilder<PushFilariaAmritWorker>().addTag("push_group5_comm_disease").build(),
        )

        val group9Abha = syncRequestBuilder<PushMapAbhatoBenficiaryWorker>()
            .addTag("push_group9_digital_health").build()

        val chainB = afterRegistration.then(group2Screening)
        val chainC = afterRegistration.then(group5CommDisease)
        val chainE = afterRegistration.then(group9Abha)

        WorkContinuation.combine(listOf(chainB, chainC, chainE)).enqueue()
    }

    fun triggerAmritPullWorker(context: Context) {
        val workManager = WorkManager.getInstance(context)

        val pullWorkRequest = syncRequestBuilder<PullFromAmritWorker>()
            .addTag("pull_phase1_foundation").build()

        val afterFoundation = workManager.beginUniqueWork(
            pullWorkerUniqueName, ExistingWorkPolicy.KEEP, pullWorkRequest)

        val group1Forms = listOf(
            syncRequestBuilder<FormSyncWorker>().addTag("pull_group1_forms").build(),
            syncRequestBuilder<NCDFollowUpSyncWorker>().addTag("pull_group1_forms").build(),
        )

        val group2Screening = listOf(
            syncRequestBuilder<CbacPullFromAmritWorker>().addTag("pull_group2_screening").build(),
            syncRequestBuilder<ReferPullFromAmritWorker>().addTag("pull_group2_screening").build(),
        )

        val group5CommDisease = listOf(
            syncRequestBuilder<PullTBFromAmritWorker>().addTag("pull_group5_comm_disease").build(),
            syncRequestBuilder<PullMalariaFromAmritWorker>().addTag("pull_group5_comm_disease").build(),
            syncRequestBuilder<pullAesFormAmritWorker>().addTag("pull_group5_comm_disease").build(),
            syncRequestBuilder<PullFilariaFromAmritWorker>().addTag("pull_group5_comm_disease").build(),
        )

        val setSyncCompleteWorker = OneTimeWorkRequestBuilder<UpdatePrefForPullCompleteWorker>().build()

        val chainA = afterFoundation.then(group1Forms)
        val chainB = afterFoundation.then(group2Screening)
        val chainE = afterFoundation.then(group5CommDisease)

        WorkContinuation.combine(listOf(chainA, chainB, chainE))
            .then(setSyncCompleteWorker)
            .enqueue()
    }

    fun triggerD2dSyncWorker(context: Context) {}

    fun triggerCbacPullWorker(context: Context) {
        val workRequest = syncRequestBuilder<CbacPullFromAmritWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            CbacPullFromAmritWorker.name, ExistingWorkPolicy.APPEND_OR_REPLACE, workRequest)
    }

    fun triggerCbacPushWorker(context: Context) {
        val workRequest = syncRequestBuilder<CbacPushToAmritWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            CbacPushToAmritWorker.name, ExistingWorkPolicy.APPEND_OR_REPLACE, workRequest)
    }

    fun triggerGenBenIdWorker(context: Context) {
        val workRequest = OneTimeWorkRequestBuilder<GenerateBenIdsWorker>()
            .setConstraints(GenerateBenIdsWorker.constraint).build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(GenerateBenIdsWorker.name, ExistingWorkPolicy.KEEP, workRequest)
    }

    fun triggerDownloadCardWorker(
        context: Context, fileName: String, otpTxnID: MutableLiveData<String?>
    ): LiveData<Operation.State> {
        val workRequest = syncRequestBuilder<DownloadCardWorker>()
            .setInputData(Data.Builder().apply {
                putString(DownloadCardWorker.file_name, fileName)
            }.build()).build()
        return WorkManager.getInstance(context).enqueueUniqueWork(
            DownloadCardWorker.name, ExistingWorkPolicy.REPLACE, workRequest).state
    }

    fun cancelAllWork(context: Context) {
        WorkManager.getInstance(context).cancelAllWork()
    }
}