package org.piramalswasthya.stoptb.repositories

import android.content.Context
import android.content.res.Resources
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.piramalswasthya.stoptb.database.room.InAppDb
import org.piramalswasthya.stoptb.database.room.NcdReferalDao
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.model.BenRegCache
import org.piramalswasthya.stoptb.model.CbacCache
import org.piramalswasthya.stoptb.model.CbacRequest
import org.piramalswasthya.stoptb.model.CbacResponseDto
import org.piramalswasthya.stoptb.model.CbacVisitDetails
import org.piramalswasthya.stoptb.model.VisitDetailsWrapper
import org.piramalswasthya.stoptb.model.toEntity
import org.piramalswasthya.stoptb.network.AmritApiService
import org.piramalswasthya.stoptb.network.GetCBACRequest
import timber.log.Timber
import javax.inject.Inject

class CbacRepo @Inject constructor(
    @ApplicationContext var context: Context,
    private val database: InAppDb,
    private val userRepo: UserRepo,
    private val amritApiService: AmritApiService,
    private val prefDao: PreferenceDao,
    private val referalDao: NcdReferalDao,

) {

    private val resources: Resources

    init {
        resources = context.resources
    }

    suspend fun updateReferStatus(benId: Long,status: Boolean) {
        database.cbacDao.updateReferralStatus(benId,status)

    }

    suspend fun saveCbacData(cbacCache: CbacCache, ben: BenRegCache): Boolean {
        return withContext(Dispatchers.IO) {

            val user =
                prefDao.getLoggedInUser()
                    ?: throw IllegalStateException("No user logged in!!")
            try {
                cbacCache.apply {
                    createdBy = user.userName
                    createdDate = System.currentTimeMillis()
                    serverUpdatedStatus = 0
                    cbac_tracing_all_fm =
                        if (cbac_sufferingtb_pos == 1 || cbac_antitbdrugs_pos == 1)
                            "1"
                        else
                            "0"
                    cbac_sputemcollection = if (cbac_tbhistory_pos == 1 ||
                        cbac_coughing_pos == 1 ||
                        cbac_bloodsputum_pos == 1 ||
                        cbac_fivermore_pos == 1 ||
                        cbac_loseofweight_pos == 1 ||
                        cbac_nightsweats_pos == 1
                    )
                        "1"
                    else
                        "0"
                    Processed = "N"
//                    ProviderServiceMapID = user.serviceMapId
//                    VanID = user.vanId

                }

                database.cbacDao.upsert(cbacCache)
                database.benDao.updateBen(ben)
                true
            } catch (e: java.lang.Exception) {
                Timber.e("Error : $e raised at saveCbacData")
                false
            }
        }
    }

    suspend fun getCbacCacheFromId(cbacId: Int): CbacCache {
        return withContext(Dispatchers.IO) {
            database.cbacDao.getCbacFromBenId(cbacId)
                ?: throw IllegalStateException("No CBAC entry found!")
        }

    }




    suspend fun pullAndPersistCbacRecord(page: Int = 0): Int {
        val userName = prefDao.getLoggedInUser()?.userName!!
        val cbacRequest = GetCBACRequest(userName)

        val response = amritApiService.getCbacData(
            cbacRequest
        )
        val body = response.body()?.string()?.let { JSONObject(it) }
        body?.getInt("statusCode")?.takeIf { it == 5002 ||it ==401 }?.let {
            val user = prefDao.getLoggedInUser()!!
            userRepo.refreshTokenTmc(user.userName, user.password)
            pullAndPersistCbacRecord(page)
        }
        val dataArray = body?.optJSONArray("data")

        if (dataArray != null && dataArray.length() > 0) {
            val gson = Gson()
            val cbacEntities = mutableListOf<CbacCache>()

            for (i in 0 until dataArray.length()) {
                val item = dataArray.getJSONObject(i)
                val dto = gson.fromJson(item.toString(), CbacResponseDto::class.java)
                cbacEntities.add(dto.toEntity())
            }
            database.cbacDao.insertAll(cbacEntities)


        }
        return 0
    }

    private fun getNumPages(body: JSONObject): Int {
        return body.getJSONObject("data").getInt("totalPage")
    }

    suspend fun pushAndUpdateCbacRecord() {
        val unProcessedList = database.cbacDao.getAllUnprocessedCbac()

        val cbacDataList = unProcessedList.mapNotNull { record ->
            val cbacPostModel = record.cbac.asPostModel(record.hhId, record.benGender, resources)
            record.cbac.benId?.let { benId ->
                Pair(benId, cbacPostModel)
            }
        }

        if (cbacDataList.isEmpty()) return

        // RECORD-LEVEL ISOLATION: Each CBAC record is processed
        // independently with try/catch. If one record fails, remaining
        // records continue. Failed records stay UNSYNCED for next cycle.
        var successCount = 0
        var failCount = 0

        for ((benId, cbac) in cbacDataList) {
            try {
                val request = CbacRequest(
                    visitDetails = VisitDetailsWrapper(
                        visitDetails = CbacVisitDetails(
                            beneficiaryRegID = benId,
                            providerServiceMapID = prefDao.getLoggedInUser()!!.serviceMapId,
                            visitNo = null,
                            visitReason = "New Chief Complaint",
                            visitCategory = "NCD screening",
                            IdrsOrCbac = "CBAC",
                            createdBy = prefDao.getLoggedInUser()?.userName.toString(),
                            vanID = prefDao.getLoggedInUser()?.vanId!!,
                            parkingPlaceID = prefDao.getLoggedInUser()?.serviceMapId!!,
                            subVisitCategory = null,
                            pregnancyStatus = null,
                            followUpForFpMethod = null,
                            sideEffects = null,
                            otherSideEffects = null,
                            fileIDs = null,
                            reportFilePath = null,
                            otherFollowUpForFpMethod = null,
                            rCHID = null,
                            healthFacilityType = null,
                            healthFacilityLocation = null
                        )
                    ),
                    cbac = cbac,
                    benFlowID = benId,
                    beneficiaryID = benId,
                    sessionID = 3,
                    parkingPlaceID = prefDao.getLoggedInUser()?.serviceMapId,
                    createdBy = prefDao.getLoggedInUser()?.userName.toString(),
                    vanID = prefDao.getLoggedInUser()?.vanId,
                    beneficiaryRegID = benId,
                    benVisitID = null,
                    providerServiceMapID = prefDao.getLoggedInUser()?.serviceMapId,
                    isFlw = true
                )

                val response = amritApiService.postCbacs(request)

                response?.body()?.string()?.let { body ->
                    val jsonBody = JSONObject(body)

                    val isSuccess = jsonBody.getString("status") == "Success"

                    if (isSuccess) {
                        val matchingRecord = unProcessedList.firstOrNull { it.cbac.benId == benId }
                        matchingRecord?.let { record ->
                            updateSyncStatusCbac(record.cbac)
                        }
                        val dataObject = jsonBody.getJSONObject("data")
                        val visitCode = dataObject.getString("visitCode")
                        val benVisitID = dataObject.getString("benVisitID")
                        val referRecord = referalDao.getReferalFromBenId(benId)
                        referRecord?.let { refer ->
                            refer.visitCode = visitCode.toLongOrNull()
                            refer.benVisitID = benVisitID.toLongOrNull()
                            referalDao.update(refer)
                        }
                        successCount++
                    } else {
                        failCount++
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "CBAC push failed for benId: $benId")
                failCount++
            }
        }

        Timber.d("CBAC push complete: $successCount succeeded, $failCount failed out of ${cbacDataList.size}")
    }


    private suspend fun updateSyncStatusCbac(cbac: CbacCache) {
        cbac.syncState = SyncState.SYNCED
        cbac.Processed = "P"
        database.cbacDao.upsert(cbac)
    }

    private fun compareLocalWithServer(local: Long, server: Long): Boolean {
        val localRounded = local - local % 1000
        val serverRemovingTimezoneOffset = server - 19_800_000
        return localRounded == serverRemovingTimezoneOffset
    }

    suspend fun getLastFilledCbac(benId: Long): CbacCache? {
        return withContext(Dispatchers.IO) {
            database.cbacDao.getLastFilledCbacFromBenId(benId = benId)

        }
    }



}