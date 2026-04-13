package org.piramalswasthya.stoptb.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.gson.Gson
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.json.JSONObject
import org.piramalswasthya.stoptb.database.room.dao.ABHAGenratedDao
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.network.ABHAProfile
import org.piramalswasthya.stoptb.network.MapHIDtoBeneficiary
import org.piramalswasthya.stoptb.network.NetworkResult
import org.piramalswasthya.stoptb.repositories.AbhaIdRepo
import org.piramalswasthya.stoptb.repositories.BenRepo
import timber.log.Timber
import java.net.SocketTimeoutException

@HiltWorker
class PushMapAbhatoBenficiaryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val benRepo: BenRepo,
    private val abhaGenratedDao: ABHAGenratedDao,
    override val preferenceDao: PreferenceDao,
    private val abhaIdRepo: AbhaIdRepo,

    ) : BasePushWorker(appContext, params) {
    companion object {
        const val name = "PushMapAbhatoBenficiaryWorker"
    }

    override val workerName = "PushMapAbhatoBenficiaryWorker"

    override suspend fun doSyncWork(): Result {
        val unsyncedList = abhaGenratedDao.getAllAbha()
        var allSuccessful = true
        for (model in unsyncedList) {
            val ben = benRepo.getBenFromId(model?.beneficiaryID!!)
            val gson = Gson()
            val profile = gson.fromJson(model.abhaProfileJson, ABHAProfile::class.java)
            val request = MapHIDtoBeneficiary(
                beneficiaryID = model?.beneficiaryID,
                beneficiaryRegID = model?.beneficiaryRegID,
                healthId = model?.healthId,
                healthIdNumber = model?.healthIdNumber,
                providerServiceMapId = model?.providerServiceMapId,
                createdBy = model?.createdBy,
                message = model?.message,
                txnId = model?.txnId,
                ABHAProfile = profile,
                isNew = model?.isNewAbha
            )

            try {
                when (val result = abhaIdRepo.mapHealthIDToBeneficiary(request,ben)) {
                    is NetworkResult.Success -> {
                        val response = JSONObject(result.data)
                        Timber.d("Success: ${response}")
                        abhaGenratedDao.deleteAbhaByBenId(request.beneficiaryID!!)
                    }

                    is NetworkResult.Error -> {
                        Timber.e("Error [${result.code}]: ${result.message}")
                        allSuccessful = false
                    }

                    is NetworkResult.NetworkError -> {
                        Timber.e("Network connection failed.")
                        allSuccessful = false
                    }
                }
            } catch (e: Exception) {
                Timber.e("Exception syncing ABHA for benId=${model.beneficiaryID}: $e")
                return if (e is SocketTimeoutException) Result.retry() else Result.failure(workDataOf(KEY_WORKER_NAME to workerName, KEY_ERROR to "ABHA sync failed: ${e.message}"))
            }
        }

        return if (allSuccessful) Result.success() else Result.retry()
    }
}
