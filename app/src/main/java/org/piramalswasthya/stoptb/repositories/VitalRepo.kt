package org.piramalswasthya.stoptb.repositories

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.database.room.dao.BenDao
import org.piramalswasthya.stoptb.database.room.dao.VitalDao
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.model.VitalCache
import org.piramalswasthya.stoptb.network.AmritApiService
import org.piramalswasthya.stoptb.network.GeneralExaminationGetRequest
import org.piramalswasthya.stoptb.network.GeneralExaminationRecord
import org.piramalswasthya.stoptb.network.GeneralExaminationSaveRequest
import timber.log.Timber
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

class VitalRepo @Inject constructor(
    private val vitalDao: VitalDao,
    private val benDao: BenDao,
    private val preferenceDao: PreferenceDao,
    private val userRepo: UserRepo,
    private val tmcNetworkApiService: AmritApiService
) {

    suspend fun getVitals(benId: Long): VitalCache? {
        return withContext(Dispatchers.IO) {
            vitalDao.getVitals(benId)
        }
    }

    val vitalBenIds: Flow<List<Long>> = vitalDao.getAllVitalBenIds()

    suspend fun saveVitals(vitalCache: VitalCache) {
        withContext(Dispatchers.IO) {
            vitalDao.saveVitals(vitalCache)
        }
    }

    suspend fun saveVitalsAndSync(vitalCache: VitalCache): Boolean {
        return withContext(Dispatchers.IO) {
            val localData = vitalCache.copy(syncState = SyncState.UNSYNCED)

            vitalDao.saveVitals(localData)

            val synced = pushSingleVital(localData)

            if (synced) {
                vitalDao.saveVitals(localData.copy(syncState = SyncState.SYNCED))
            }

            synced
        }
    }

    suspend fun fetchGeneralExaminationsFromApi(
        request: GeneralExaminationGetRequest
    ): List<GeneralExaminationRecord> {
        return withContext(Dispatchers.IO) {
            try {
                val response = tmcNetworkApiService.getGeneralExaminations(request)
                val responseString = response.body()?.string()

                if (response.isSuccessful && !responseString.isNullOrBlank()) {
                    val jsonObj = JSONObject(responseString)
                    if (jsonObj.optInt("statusCode") == 200) {
                        parseGeneralExaminationRecords(jsonObj)
                    } else {
                        Timber.e("General examination get failed. statusCode=%s", jsonObj.optInt("statusCode"))
                        emptyList()
                    }
                } else {
                    Timber.e(
                        "General examination get failed. code=%s message=%s",
                        response.code(),
                        response.message()
                    )
                    emptyList()
                }
            } catch (e: Exception) {
                Timber.e(e, "General examination get API failed")
                emptyList()
            }
        }
    }

    suspend fun getGeneralExaminationDetailsFromServer(): Int {
        return withContext(Dispatchers.IO) {
            val user = preferenceDao.getLoggedInUser()
                ?: throw IllegalStateException("No user logged in!!")
            val villageId = preferenceDao.getLocationRecord()?.village?.id ?: return@withContext 0

            try {
                val records = fetchGeneralExaminationsFromApi(
                    GeneralExaminationGetRequest(
                        providerServiceMapID = user.serviceMapId,
                        villageID = villageId
                    )
                )

                records.forEach { record ->
                    val beneficiary = record.beneficiaryID?.let { benDao.getBen(it) }
                        ?: record.beneficiaryRegID?.let { benDao.getBenByRegId(it) }
                    val benId = beneficiary?.beneficiaryId ?: record.beneficiaryID ?: return@forEach
                    val benRegId = record.beneficiaryRegID ?: beneficiary?.benRegId ?: return@forEach

                    val existing = vitalDao.getVitals(benId)
                    val serverUpdatedDate = parseServerUpdateDate(record.updateDate)
                    if (!shouldApplyServerRecord(existing?.syncState, existing?.serverUpdatedDate, serverUpdatedDate)) {
                        return@forEach
                    }
                    vitalDao.saveVitals(
                        (existing ?: VitalCache(benId = benId, benRegId = benRegId)).copy(
                            benRegId = benRegId,
                            pulseRate = record.pulseRate,
                            bpSystolic = record.systolicBP,
                            bpDiastolic = record.diastolicBP,
                            rbs = record.randomBloodSugar,
                            pallorId = record.pallorId,
                            pallor = record.pallor,
                            icterusId = record.icterusId,
                            icterus = record.icterus,
                            cyanosisId = record.cyanosisId,
                            cyanosis = record.cyanosis,
                            clubbingId = record.clubbingId,
                            clubbing = record.clubbing,
                            lymphadenopathyId = record.lymphadenopathyId,
                            lymphadenopathy = record.lymphadenopathy,
                            oedemaId = record.oedemaId,
                            oedema = record.oedema,
                            keyPopulationRiskFactorIds = parseIntList(record.keyPopulationRiskFactorIds),
                            keyPopulationRiskFactors = parseStringList(record.keyPopulationRiskFactors),
                            hivStatusId = record.hivStatusId,
                            hivStatus = record.hivStatus,
                            referralToHwcNeeded = record.referralToHWCNeeded,
                            serverUpdatedDate = serverUpdatedDate.takeIf { it > 0L },
                            syncState = SyncState.SYNCED
                        )
                    )
                }

                if (records.isEmpty()) 0 else 1
            } catch (e: SocketTimeoutException) {
                Timber.e("get_general_examination error : $e")
                -2
            } catch (e: IllegalStateException) {
                Timber.e("get_general_examination error : $e")
                -1
            } catch (e: Exception) {
                Timber.e(e, "General examination pull failed")
                -1
            }
        }
    }

    private suspend fun pushSingleVital(vitalCache: VitalCache): Boolean {
        val user = preferenceDao.getLoggedInUser() ?: return false

        return try {
            val response = tmcNetworkApiService.saveGeneralExamination(
                listOf(GeneralExaminationSaveRequest.from(vitalCache, user))
            )

            val responseBody = response.body()?.string()

            if (!response.isSuccessful || responseBody.isNullOrBlank()) {
                return false
            }

            val statusCode = JSONObject(responseBody).optInt("statusCode", 0)

            when (statusCode) {
                200 -> true

                401, 5002 -> {
                    if (userRepo.refreshTokenTmc(user.userName, user.password)) {
                        pushSingleVital(vitalCache)
                    } else {
                        false
                    }
                }

                else -> false
            }

        } catch (e: SocketTimeoutException) {
            if (e.message == "Refreshed Token") {
                pushSingleVital(vitalCache)
            } else {
                false
            }

        } catch (e: Exception) {
            if (shouldRetryWithRefresh(e, user.userName, user.password)) {
                pushSingleVital(vitalCache)
            } else {
                Timber.e(e, "Vital sync failed for benId=%s", vitalCache.benId)
                false
            }
        }
    }

    private suspend fun shouldRetryWithRefresh(
        error: Exception,
        userName: String,
        password: String
    ): Boolean {
        val message = error.message.orEmpty()

        return if (
            message.contains("401") ||
            message.contains("5002") ||
            message.contains("Invalid login key", ignoreCase = true)
        ) {
            userRepo.refreshTokenTmc(userName, password)
        } else {
            false
        }
    }

    private fun parseIntList(value: String?): List<Int>? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            val jsonArray = JSONArray(value)
            List(jsonArray.length()) { index -> jsonArray.optInt(index) }
        }.getOrNull()
    }

    private fun parseStringList(value: String?): List<String>? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            val jsonArray = JSONArray(value)
            List(jsonArray.length()) { index -> jsonArray.optString(index) }
                .filter { it.isNotBlank() }
        }.getOrNull()
    }

    private fun parseGeneralExaminationRecords(jsonObj: JSONObject): List<GeneralExaminationRecord> {
        val records = when (val data = jsonObj.opt("data")) {
            is JSONArray -> data
            is JSONObject -> data.optJSONArray("data") ?: JSONArray()
            else -> JSONArray()
        }

        return List(records.length()) { index -> records.optJSONObject(index) }
            .mapNotNull { item ->
                item ?: return@mapNotNull null
                GeneralExaminationRecord(
                    id = item.optLongOrNull("id"),
                    beneficiaryID = item.optLongOrNull("beneficiaryID"),
                    beneficiaryRegID = item.optLongOrNull("beneficiaryRegID"),
                    createdBy = item.optStringOrNull("createdBy"),
                    providerServiceMapID = item.optIntOrNull("providerServiceMapID"),
                    pulseRate = item.optIntOrNull("pulseRate"),
                    systolicBP = item.optIntOrNull("systolicBP"),
                    diastolicBP = item.optIntOrNull("diastolicBP"),
                    randomBloodSugar = item.optDoubleOrNull("randomBloodSugar"),
                    pallorId = item.optIntOrNull("pallorId"),
                    pallor = item.optStringOrNull("pallor"),
                    icterusId = item.optIntOrNull("icterusId"),
                    icterus = item.optStringOrNull("icterus"),
                    cyanosisId = item.optIntOrNull("cyanosisId"),
                    cyanosis = item.optStringOrNull("cyanosis"),
                    clubbingId = item.optIntOrNull("clubbingId"),
                    clubbing = item.optStringOrNull("clubbing"),
                    lymphadenopathyId = item.optIntOrNull("lymphadenopathyId"),
                    lymphadenopathy = item.optStringOrNull("lymphadenopathy"),
                    oedemaId = item.optIntOrNull("oedemaId"),
                    oedema = item.optStringOrNull("oedema"),
                    keyPopulationRiskFactorIds = item.optJsonArrayString("keyPopulationRiskFactorIds"),
                    keyPopulationRiskFactors = item.optJsonArrayString("keyPopulationRiskFactors"),
                    hivStatusId = item.optIntOrNull("hivStatusId"),
                    hivStatus = item.optStringOrNull("hivStatus"),
                    referralToHWCNeeded = item.optBooleanOrNull("referralToHWCNeeded"),
                    createdDate = item.optStringOrNull("createdDate"),
                    updateDate = item.optStringOrNull("updateDate")
                        ?: item.optStringOrNull("updatedDate")
                )
            }
    }

    private fun parseServerUpdateDate(dateString: String?): Long {
        if (dateString.isNullOrBlank() || dateString.equals("null", ignoreCase = true)) return 0L
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "MMM dd, yyyy h:mm:ss a",
            "MMM d, yyyy h:mm:ss a"
        )
        patterns.forEach { pattern ->
            runCatching {
                SimpleDateFormat(pattern, Locale.ENGLISH).parse(dateString)?.time
            }.getOrNull()?.let { return it }
        }
        return 0L
    }

    private fun shouldApplyServerRecord(
        existingSyncState: SyncState?,
        savedServerUpdatedDate: Long?,
        serverUpdatedDate: Long
    ): Boolean {
        if (existingSyncState != null && existingSyncState != SyncState.SYNCED) return false
        if (serverUpdatedDate <= 0L) return true
        return serverUpdatedDate > (savedServerUpdatedDate ?: 0L)
    }

    private fun JSONObject.optLongOrNull(name: String): Long? =
        if (!has(name) || isNull(name)) null else optLong(name)

    private fun JSONObject.optIntOrNull(name: String): Int? =
        if (!has(name) || isNull(name)) null else optInt(name)

    private fun JSONObject.optDoubleOrNull(name: String): Double? =
        if (!has(name) || isNull(name)) null else optDouble(name).takeIf { !it.isNaN() }

    private fun JSONObject.optBooleanOrNull(name: String): Boolean? =
        if (!has(name) || isNull(name)) null else optBoolean(name)

    private fun JSONObject.optJsonArrayString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return when (val value = opt(name)) {
            is JSONArray -> value.toString()
            is String -> value.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            else -> null
        }
    }

    private fun JSONObject.optStringOrNull(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name).takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
    }
}
