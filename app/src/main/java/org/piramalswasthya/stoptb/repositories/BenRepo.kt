package org.piramalswasthya.stoptb.repositories

import org.piramalswasthya.stoptb.helpers.logSyncSkip
import org.piramalswasthya.stoptb.helpers.SyncDailyStore

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.piramalswasthya.stoptb.database.room.BeneficiaryIdsAvail
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.database.room.dao.BenDao
import org.piramalswasthya.stoptb.database.room.dao.BeneficiaryIdsAvailDao
import org.piramalswasthya.stoptb.database.room.dao.HouseholdDao
import org.piramalswasthya.stoptb.database.room.dao.dynamicSchemaDao.FormResponseJsonDao
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.database.shared_preferences.SyncRetryStore
import org.piramalswasthya.stoptb.helpers.ImageUtils
import org.piramalswasthya.stoptb.helpers.Konstants
import org.piramalswasthya.stoptb.helpers.logSyncModule
import org.piramalswasthya.stoptb.helpers.syncPushOutcome
import androidx.room.withTransaction
import org.piramalswasthya.stoptb.database.room.InAppDb
import org.piramalswasthya.stoptb.helpers.dynamicMapper.PayloadBuilder
import org.piramalswasthya.stoptb.model.*
import org.piramalswasthya.stoptb.model.getPlaceOfCurrentLivingIndex
import org.piramalswasthya.stoptb.model.getPlaceOfCurrentLivingCode
import org.piramalswasthya.stoptb.network.*
import org.piramalswasthya.stoptb.ui.home_activity.all_ben.new_ben_registration.ben_form.NewBenRegViewModel
import org.piramalswasthya.stoptb.utils.sync.BatchIsolation
import org.piramalswasthya.stoptb.utils.Log
import org.piramalswasthya.stoptb.work.WorkerUtils
import timber.log.Timber
import java.io.File
import java.lang.Long.min
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

/**
 * Singleton because [bufferServerPage] accumulates pulled pages in instance state and
 * [flushPendingServerBeneficiaries] commits them. A second injected instance would get its own
 * buffer that nothing ever flushes, silently dropping up to a batch of pulled beneficiaries.
 */
@javax.inject.Singleton
class BenRepo @Inject constructor(
    private val context: Application,
    private val benDao: BenDao,
    private val householdDao: HouseholdDao,
    private val benIdGenDao: BeneficiaryIdsAvailDao,
    private val preferenceDao: PreferenceDao,
    private val userRepo: UserRepo,
    private val tmcNetworkApiService: AmritApiService,
    private val formResponseJsonDao: FormResponseJsonDao,
    private val db: InAppDb,
    private val syncRetryStore: SyncRetryStore
) {

    private val processNewBenMutex = Mutex()

    // Last server rejection message from the beneficiary push, kept for the
    // retry store. Only written under processNewBenMutex.
    private var lastBenUploadError: String? = null

    companion object {
        /**
         * How many pulled pages share one write transaction. The server fixes the page size at
         * 10 beneficiaries, so this is 200 rows per commit and one Room invalidation per 20
         * pages instead of per page.
         */
        private const val benWriteBatchPages = 20
        private const val DEFAULT_OFFICER_ID = 501L

        /** PR 7.5: most beneficiaries per syncDataToAmrit request for new records. */
        private const val MAX_BENS_PER_PUSH = 100

        // Temp (negative) benId -> server benId, recorded when the push(server) replaces the temp ID.
        // Screens opened before that (form/Examine sheet nav args) still hold the temp ID,
        // which no longer exists in BENEFICIARY — see resolveCurrentBenId().
        // Also persisted in preferences so a form restored after process death can still resolve it.
        // Temp IDs are never reused (reserveTempBenIds), so a temp ID maps to one beneficiary only.
        private val finalBenIdByTempBenId = ConcurrentHashMap<Long, Long>()
        private val tempBenIdMutex = Mutex()
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.ENGLISH)
        fun getCurrentDate(millis: Long = System.currentTimeMillis()): String {
            val dateString = dateFormat.format(millis)
            val timeString = timeFormat.format(millis)
            return "${dateString}T${timeString}.000Z"
        }

        fun getLongFromDateStr(dateString: String): Long {
            val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
            val date = f.parse(dateString)
            return date?.time ?: throw IllegalStateException("Invalid date for dateReg")
        }
    }

    suspend fun updateBenToSync(householdId: Long, unsynced: SyncState) {
        withContext(Dispatchers.IO) {
            benDao.updateBenToSync(householdId = householdId,unsynced,"U",2)
        }
    }

    /**
     * Gives every member of a household the household's [village], [pinCode] and Residential Area, and
     * marks them UNSYNCED, in one transaction. The pull only overwrites SYNCED
     * members, so the writes must commit together: a pull landing between them would put the old
     * village back and the sync-state update would then push it.
     */
    suspend fun applyHouseholdLocationToMembers(
        householdId: Long,
        village: LocationEntity,
        pinCode: String?,
        residentialAreaId: Int,
        residentialArea: String?
    ) {
        withContext(Dispatchers.IO) {
            db.withTransaction {
                benDao.updateVillageOfHouseholdMembers(
                    householdId = householdId,
                    villageId = village.id,
                    villageName = village.name,
                    villageNameHindi = village.nameHindi,
                    villageNameAssamese = village.nameAssamese,
                    pinCode = pinCode,
                    residentialAreaId = residentialAreaId,
                    residentialArea = residentialArea
                )
                benDao.updateBenToSync(householdId = householdId, SyncState.UNSYNCED, "U", 2)
            }
        }
    }

    suspend fun updateHousehold(householdId: Long, unsynced: SyncState) {
        withContext(Dispatchers.IO) {
            benDao.updateHofSpouseAdded(householdId = householdId,unsynced,"U",2)
        }
    }

    fun getUnscreenedList(selectedVillage: Int): Flow<List<BenBasicCache>> =
        benDao.getUnscreenedList(selectedVillage)

    suspend fun updateBeneficiarySpouseAdded(householdId: Long,benID: Long,unsynced: SyncState) {
        withContext(Dispatchers.IO) {
            val processState = if (benID < 0L) "N" else "U"
            val updateStatus = if (benID < 0L) 1 else 2
            benDao.updateBeneficiarySpouseAdded(
                householdId = householdId,
                benId = benID,
                unsynced,
                processState,
                updateStatus
            )
        }
    }

    suspend fun updateFatherInChildren(benName: String, householdId: Long, parentName: String, unsynced: SyncState) {
        withContext(Dispatchers.IO) {
            benDao.updateFatherInChildren(benName = benName, householdId = householdId, parentName = parentName, unsynced, "U", 2)
        }
    }

    suspend fun updateMotherInChildren(benName: String, householdId: Long, parentName: String, unsynced: SyncState) {
        withContext(Dispatchers.IO) {
            benDao.updateMotherInChildren(benName = benName, householdId = householdId, parentName = parentName, unsynced, "U", 2)
        }
    }

    suspend fun updateSpouseOfHoF(benName: String, householdId: Long, spouseName: String, unsynced: SyncState) {
        withContext(Dispatchers.IO) {
            benDao.updateSpouseOfHoF(benName = benName, householdId = householdId, spouseName = spouseName, unsynced, "U", 2)
        }
    }

    suspend fun updateFather(benName: String, householdId: Long, parentName: String, unsynced: SyncState) {
        withContext(Dispatchers.IO) {
            benDao.updateFather(benName = benName, householdId = householdId, parentName = parentName, unsynced, "U", 2)
        }
    }

    suspend fun updateMother(benName: String, householdId: Long, parentName: String, unsynced: SyncState) {
        withContext(Dispatchers.IO) {
            benDao.updateMother(benName = benName, householdId = householdId, parentName = parentName, unsynced, "U", 2)
        }
    }

    suspend fun updateMarriageAgeOfWife(marriageDate: Long, ageAtMarriage: Int, householdId: Long, spouseName: String, unsynced: SyncState) {
        withContext(Dispatchers.IO) {
            benDao.updateMarriageAgeOfWife(marriageDate = marriageDate, ageAtMarriage = ageAtMarriage, householdId = householdId, spouseName = spouseName, unsynced, "U", 2)
        }
    }

    suspend fun updateMarriageAgeOfHusband(marriageDate: Long, ageAtMarriage: Int, householdId: Long, spouseName: String, unsynced: SyncState) {
        withContext(Dispatchers.IO) {
            benDao.updateMarriageAgeOfHusband(marriageDate = marriageDate, ageAtMarriage = ageAtMarriage, householdId = householdId, spouseName = spouseName, unsynced, "U", 2)
        }
    }

    suspend fun updateBabyName(babyName: String, householdId: Long, parentName: String, unsynced: SyncState) {
        withContext(Dispatchers.IO) {
            benDao.updateBabyName(babyName = babyName, householdId = householdId, parentName = parentName, unsynced, "U", 2)
        }
    }

    suspend fun updateSpouse(benName: String, householdId: Long, spouseName: String, unsynced: SyncState) {
        withContext(Dispatchers.IO) {
            benDao.updateSpouse(benName = benName, householdId = householdId, spouseName = spouseName, unsynced, "U", 2)
        }
    }

    suspend fun updateChildrenLastName(lastName: String, householdId: Long, parentName: String, unsynced: SyncState) {
        withContext(Dispatchers.IO) {
            benDao.updateChildrenLastName(lastName = lastName, householdId = householdId, parentName = parentName, unsynced, "U", 2)
        }
    }

    suspend fun updateSpouseLastName(lastName: String, householdId: Long, spouseName: String, unsynced: SyncState) {
        withContext(Dispatchers.IO) {
            benDao.updateSpouseLastName(lastName = lastName, householdId = householdId, spouseName = spouseName, unsynced, "U", 2)
        }
    }

    suspend fun updateBeneficiaryChildrenAdded(
        householdId: Long,
        benID: Long,
        unsynced: SyncState

    ) {
        withContext(Dispatchers.IO) {
            benDao.updateBeneficiaryChildrenAdded(householdId = householdId, benId = benID,unsynced,"U",2)
        }
    }
    fun getBenBasicListFromHousehold(hhId: Long): Flow<List<BenBasicDomain>> {
        return benDao.getAllBasicBenForHousehold(hhId).map { it.map { it.asBasicDomainModel() } }

    }

    suspend fun getBenListFromHousehold(hhId: Long): List<BenRegCache> {
        return benDao.getAllBenForHousehold(hhId)

    }

    suspend fun getChildCountForBen(benId: Long): Int {
        return benDao.getChildCountForBen(benId)
    }

    suspend fun getChildBenListFromHousehold(
        hhId: Long,
        selectedbenIdFromArgs: Long,
        firstName: String?
    ): List<BenRegCache> {
        return benDao.getChildBenForHousehold(hhId,selectedbenIdFromArgs,firstName)

    }

    suspend fun getChildBelow15(
        hhId: Long,
        selectedbenIdFromArgs: Long,
        firstName: String?
    ): Int {
        return benDao.getBelow15Count(hhId,selectedbenIdFromArgs,firstName)

    }

    suspend fun getChildAbove15(
        hhId: Long,
        selectedbenIdFromArgs: Long,
        firstName: String?
    ): Int {
        return benDao.get15aboveCount(hhId,selectedbenIdFromArgs,firstName)

    }

    suspend fun isBenDead(benId: Long): Boolean {
        return benDao.isBenDead(benId)
    }

    suspend fun getBenFromId(benId: Long): BenRegCache? {
        return withContext(Dispatchers.IO) {
            benDao.getBen(benId)
        }
    }

    /**
     * Returns the server benId if [benId] is a temp ID the push has since replaced
     * (Camp Mode pushes within seconds, often while a form for the temp ID is open);
     * otherwise returns [benId] unchanged.
     */
    suspend fun resolveCurrentBenId(benId: Long): Long {
        return withContext(Dispatchers.IO) {
            if (benDao.getBen(benId) != null) benId
            else finalBenIdByTempBenId[benId]
                ?: preferenceDao.getFinalBenId(benId).takeIf { it > 0L }
                ?: benId
        }
    }

    suspend fun linkBenToHousehold(benId: Long, hhId: Long, relationPos: Int, relationName: String) {
        withContext(Dispatchers.IO) {
            val ben = benDao.getBen(benId)
            if (ben != null) {
                ben.householdId = hhId
                ben.familyHeadRelationPosition = relationPos
                ben.familyHeadRelation = relationName
                ben.isNonHH = false
                ben.syncState = SyncState.UNSYNCED
                ben.processed = "U"
                ben.serverUpdatedStatus = 2

                // If linked as Wife (5) or Husband (6), back-link the spouse name of the Head of Family
                if (relationPos == 5 || relationPos == 6) {
                    val linkedFullName = listOfNotNull(ben.firstName?.trim(), ben.lastName?.trim())
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                    if (linkedFullName.isNotBlank()) {
                        // Find the HoF (relPosition = 19) in the same household
                        benDao.getAllBenForHousehold(hhId).firstOrNull { it.familyHeadRelationPosition == 19 }?.let { hofBen ->
                            // Update HoF's spouseName and set isSpouseAdded = true
                            if (hofBen.genDetails == null) {
                                hofBen.genDetails = BenRegGen(spouseName = linkedFullName)
                            } else {
                                hofBen.genDetails!!.spouseName = linkedFullName
                            }
                            hofBen.genDetails!!.maritalStatusId = 2
                            hofBen.genDetails!!.maritalStatus = "Married"
                            hofBen.isSpouseAdded = true
                            hofBen.syncState = SyncState.UNSYNCED
                            hofBen.processed = "U"
                            hofBen.serverUpdatedStatus = 2
                            benDao.updateBen(hofBen)

                            // Also back-link the HoF's name as the spouseName of the linked member
                            val hofFullName = listOfNotNull(hofBen.firstName?.trim(), hofBen.lastName?.trim())
                                .filter { it.isNotBlank() }
                                .joinToString(" ")
                            if (hofFullName.isNotBlank()) {
                                if (ben.genDetails == null) {
                                    ben.genDetails = BenRegGen(spouseName = hofFullName)
                                } else {
                                    ben.genDetails!!.spouseName = hofFullName
                                }
                                ben.genDetails!!.maritalStatusId = 2
                                ben.genDetails!!.maritalStatus = "Married"
                                ben.isSpouseAdded = true
                            }
                        }
                    }
                }
                // If linked as Son (9) or Daughter (10)
                else if (relationPos == 9 || relationPos == 10) {
                    benDao.getAllBenForHousehold(hhId).firstOrNull { it.familyHeadRelationPosition == 19 }?.let { hofBen ->
                        val hofFullName = listOfNotNull(hofBen.firstName?.trim(), hofBen.lastName?.trim())
                            .filter { it.isNotBlank() }
                            .joinToString(" ")
                        if (hofFullName.isNotBlank()) {
                            if (hofBen.genderId == 1) { // Male HOF is Father
                                ben.fatherName = hofFullName
                                // If HOF is married, set motherName to HOF's Wife's name
                                hofBen.genDetails?.spouseName?.takeIf { it.isNotBlank() && it != "Not Available" }?.let { mother ->
                                    ben.motherName = mother
                                }
                            } else if (hofBen.genderId == 2) { // Female HOF is Mother
                                ben.motherName = hofFullName
                                // If HOF is married, set fatherName to HOF's Husband's name
                                hofBen.genDetails?.spouseName?.takeIf { it.isNotBlank() && it != "Not Available" }?.let { father ->
                                    ben.fatherName = father
                                }
                            }
                        }
                    }
                }
                // If linked as Mother (1) or Father (2)
                else if (relationPos == 1 || relationPos == 2) {
                    val linkedFullName = listOfNotNull(ben.firstName?.trim(), ben.lastName?.trim())
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                    if (linkedFullName.isNotBlank()) {
                        val householdMembers = benDao.getAllBenForHousehold(hhId)
                        householdMembers.forEach { member ->
                            // Update HOF (19) or Sibling (Son 9 / Daughter 10)
                            if (member.familyHeadRelationPosition == 19 || member.familyHeadRelationPosition == 9 || member.familyHeadRelationPosition == 10) {
                                var updated = false
                                if (relationPos == 1) { // Linked member is Mother
                                    member.motherName = linkedFullName
                                    updated = true
                                } else if (relationPos == 2) { // Linked member is Father
                                    member.fatherName = linkedFullName
                                    updated = true
                                }
                                if (updated) {
                                    member.syncState = SyncState.UNSYNCED
                                    member.processed = "U"
                                    member.serverUpdatedStatus = 2
                                    benDao.updateBen(member)
                                }
                            }
                        }
                    }
                }
                benDao.updateBen(ben)
            }
        }
    }

    fun getHouseholds(selectedVillage: Int): Flow<List<HouseholdBasicCache>> {
        return householdDao.getAllHouseholdWithNumMembers(selectedVillage)
    }


    suspend fun getBenFromRegId(benRegId: Long): BenRegCache? {
        return withContext(Dispatchers.IO) {
            benDao.getBenByRegId(benRegId)
        }
    }

    suspend fun substituteBenIdForDraft(ben: BenRegCache) {
        val extract = extractBenId()
        ben.beneficiaryId = extract.benId
        ben.isDraft = false
    }

    suspend fun persistRecord(ben: BenRegCache, updateIfExists: Boolean = false) {
        withContext(Dispatchers.IO) {

            val originalImagePath = ben.userImage
            val finalImagePath = originalImagePath?.let { imagePath ->
                val uri = Uri.parse(imagePath)
                val filePath = uri.path
                    ?: throw IllegalStateException("Invalid image URI: $imagePath")

                val file = File(filePath)

                when {
                    uri.scheme == "content" -> {
                        Timber.d("IMAGE_DEBUG Copying content uri to permanent storage")

                        val savedPath = ImageUtils.saveBenImageFromCameraToStorage(
                            context = context,
                            uriString = imagePath,
                            benId = ben.beneficiaryId
                        )

                        if (savedPath.isNullOrBlank()) {
                            throw IllegalStateException("Failed to save beneficiary image")
                        }

                        savedPath
                    }

                    file.absolutePath.startsWith(context.filesDir.absolutePath) -> {
                        if (file.length() > ImageUtils.MAX_REGISTRATION_IMAGE_BYTES) {
                            ImageUtils.saveBenImageFromCameraToStorage(
                                context = context,
                                uriString = imagePath,
                                benId = ben.beneficiaryId
                            ) ?: imagePath
                        } else {
                            imagePath
                        }
                    }

                    else -> {
                        if (file.exists()) {
                            ImageUtils.saveBenImageFromCameraToStorage(
                                context = context,
                                uriString = imagePath,
                                benId = ben.beneficiaryId
                            ) ?: imagePath
                        } else {
                            imagePath
                        }
                    }
                }
            }
            ben.userImage = finalImagePath
            requeueIfPendingPush(ben)
            if (updateIfExists && benDao.getBen(ben.beneficiaryId) != null) {
                benDao.updateBen(ben)
            } else {
                benDao.upsert(ben)
                Timber.d("IMAGE_DEBUG Saved in DB benId=${ben.beneficiaryId} image=${ben.userImage}")
            }
            if (!ben.isDraft) SyncDailyStore.record("BENEFICIARY", listOf(ben.beneficiaryId), "SAVE", "SUCCESS")
        }
    }


    suspend fun updateRecord(ben: BenRegCache) {
        withContext(Dispatchers.IO) {
            requeueIfPendingPush(ben)
            benDao.updateBen(ben)
            if (!ben.isDraft) SyncDailyStore.record("BENEFICIARY", listOf(ben.beneficiaryId), "SAVE", "SUCCESS")
        }
    }

    // Edit flows set processed to 'N'/'U' but reuse the syncState loaded with
    // the record. The push query only picks UNSYNCED rows, so an edit to an
    // already-synced beneficiary would never be sent without this.
    private fun requeueIfPendingPush(ben: BenRegCache) {
        if (ben.processed == "N" || ben.processed == "U") {
            ben.syncState = SyncState.UNSYNCED
        }
    }

    suspend fun getHousehold(hhId: Long): HouseholdCache? {
        return withContext(Dispatchers.IO) {
            householdDao.getHousehold(hhId)
        }

    }

    suspend fun getBeneficiaryRecord(benId: Long, hhId: Long = 0L): BenRegCache? {
        return withContext(Dispatchers.IO) {
            if (benId == 0L)
                return@withContext null
            benDao.getBen(benId)
        }

    }

    private suspend fun extractBenId(): BeneficiaryIdsAvail {
        return withContext(Dispatchers.IO) {
            val user =
                preferenceDao.getLoggedInUser()
                    ?: throw IllegalStateException("No user logged in!!")

            val benIdObj = benIdGenDao.getEntry(user.userId)
            benIdGenDao.delete(benIdObj)
            benIdObj
        }

    }

    suspend fun getBenIdsGeneratedFromServer(maxCount: Int = Konstants.benIdCapacity) {
        val user =
            preferenceDao.getLoggedInUser() ?: throw IllegalStateException("No user logged in!!")
        val benIdCount = benIdGenDao.count()
        if (benIdCount > Konstants.benIdWorkerTriggerLimit) return
        val count = maxCount - benIdCount
        getBenIdsFromLocal(count, user.userId)
//        getBenIdsFromServer(count, user)


    }

    /**
     * Reserves [count] temp IDs below every temp ID issued before and returns the lowest one.
     * The last issued ID is persisted so IDs are not reused after earlier temp rows get server IDs;
     * an open form may still hold an old temp ID (see resolveCurrentBenId). -1 is never issued.
     */
    private suspend fun reserveTempBenIds(count: Int): Long = tempBenIdMutex.withLock {
        withContext(Dispatchers.IO) {
            // BEN_ID_LIST may still hold pool IDs generated before the last issued ID was persisted.
            val lowest = minOf(
                benDao.getMinBenId() ?: -1L,
                benIdGenDao.getMinBenId() ?: -1L,
                preferenceDao.getLastTempBenId(),
                -1L
            )
            val first = lowest - count
            preferenceDao.setLastTempBenId(first)
            first
        }
    }

    suspend fun nextTempBenId(): Long = reserveTempBenIds(1)

    private suspend fun getBenIdsFromLocal(count: Int, userId: Int) {
        val firstBenId = reserveTempBenIds(count)
        val benIdList = mutableListOf<BeneficiaryIdsAvail>()
        for (benId in firstBenId until firstBenId + count) {
            benIdList.add(
                BeneficiaryIdsAvail(
                    userId = userId, benId = benId, benRegId = 0
                )
            )
        }
        benIdGenDao.insert(*benIdList.toTypedArray())
    }

    // Network failures (IOException) propagate so the push worker retries with
    // backoff. A token refresh is retried at most once.
    private suspend fun createBenIdAtServerByBeneficiarySending(
        ben: BenRegCache, user: User, locationRecord: LocationRecord,
        retriedAfterRefresh: Boolean = false
    ): Boolean {
        val oldDiagnosticId = ben.beneficiaryId
        var failureRecorded = false
        val household = if (ben.householdId != null && ben.householdId!! > 0L) householdDao.getHousehold(ben.householdId!!) else null
        val sendingData = ben.asNetworkSendingModel(user, locationRecord, context, household)
        Timber.d("Amrit push beneficiary registration: benId=${ben.beneficiaryId}, hhId=${ben.householdId}")
        try {
            val response = tmcNetworkApiService.getBenIdFromBeneficiarySending(sendingData)
            val statusCode = response.code()
            val responseString = response.body()?.string()
            Timber.d("Amrit push beneficiary registration response: httpStatus=$statusCode, benId=${ben.beneficiaryId}")
            failureRecorded = SyncDailyStore.response("BENEFICIARY", listOf(oldDiagnosticId), statusCode, responseString, "REGISTER")
            if (responseString != null) {
                val jsonObj = JSONObject(responseString)
                val errorMessage = jsonObj.optString("errorMessage", "")
                val responseStatusCode: Int = jsonObj.getInt("statusCode")
                if (responseStatusCode == 200) {
                    val jsonObjectData: JSONObject = jsonObj.getJSONObject("data")
                    val newBenId = jsonObjectData.getString("benGenId").toLong()
                    val newBenRegId = jsonObjectData.getString("benRegId").toLong()
                    Timber.d("Amrit push beneficiary registration success: oldBenId=${ben.beneficiaryId}, newBenId=$newBenId, benRegId=$newBenRegId")
                    val photoUri = ImageUtils.renameImage(context, ben.beneficiaryId, newBenId)
                    // All references to the temporary ID move to the real one together,
                    // so a crash midway can't leave records pointing at an ID that no
                    // longer exists.
                    db.withTransaction {
                        benDao.updateToFinalBenId(
                            hhId = ben.householdId,
                            oldId = ben.beneficiaryId,
                            newBenRegId = newBenRegId,
                            newId = newBenId,
                            imageUri = photoUri
                        )
                        formResponseJsonDao.updateVisitBenId(
                            oldBenId = ben.beneficiaryId,
                            newBenId = newBenId
                        )
                        db.counsellingFormResponseDao().updateBeneficiaryId(
                            oldBenId = ben.beneficiaryId,
                            newBenId = newBenId
                        )
                        ben.householdId?.let { hhId ->
                            householdDao.getHousehold(hhId)
                                ?.takeIf { it.benId == ben.beneficiaryId }?.let {
                                    it.benId = newBenId
                                    householdDao.update(it)
                                }
                        }
                    }
                    // Forms opened with the temporary ID resolve to the real one (Stop-727).
                    finalBenIdByTempBenId[ben.beneficiaryId] = newBenId
                    preferenceDao.setFinalBenId(ben.beneficiaryId, newBenId)
                    ben.beneficiaryId = newBenId
                    ben.benRegId = newBenRegId
                    SyncDailyStore.alias("BENEFICIARY", oldDiagnosticId, newBenId)
                    SyncDailyStore.alias("GENERAL_EXAMINATION", oldDiagnosticId, newBenId)
                    SyncDailyStore.record("BENEFICIARY", listOf(newBenId), "REGISTER", "SUCCESS")
                    return true
                }
                Timber.e("Amrit push beneficiary registration failed: statusCode=$responseStatusCode, error=$errorMessage, benId=${ben.beneficiaryId}")
                lastBenUploadError = "statusCode=$responseStatusCode: $errorMessage"
                if (responseStatusCode == 5002 || responseStatusCode == 401) {
                    if (userRepo.refreshTokenTmc(
                            user.userName, user.password
                        )
                    ) throw SocketTimeoutException("Refreshed Token")
                }
            } else {
                Timber.e("Amrit push beneficiary registration failed: response body is null, httpStatus=$statusCode, benId=${ben.beneficiaryId}")
                lastBenUploadError = "HTTP $statusCode, empty response"
            }
            throw IllegalStateException("Response undesired!")
        } catch (se: SocketTimeoutException) {
            if (se.message == "Refreshed Token" && !retriedAfterRefresh) {
                return createBenIdAtServerByBeneficiarySending(ben, user, locationRecord, retriedAfterRefresh = true)
            }
            Timber.e("Amrit push beneficiary registration timeout: benId=${ben.beneficiaryId}, error=$se")
            if (!failureRecorded) SyncDailyStore.record("BENEFICIARY", listOf(oldDiagnosticId), "REGISTER", "FAILURE", "TIMEOUT")
            throw se
        } catch (e: IOException) {
            Timber.e("Amrit push beneficiary registration network error: benId=${ben.beneficiaryId}, error=$e")
            throw e
        } catch (e: java.lang.Exception) {
            benDao.setSyncState(ben.householdId, ben.beneficiaryId, SyncState.UNSYNCED)
            Timber.e("Amrit push beneficiary registration error: benId=${ben.beneficiaryId}, error=$e")
            if (!failureRecorded) SyncDailyStore.record("BENEFICIARY", listOf(oldDiagnosticId), "REGISTER", "FAILURE", "EXCEPTION_${e.javaClass.simpleName}")
            return false
        }
    }
    suspend fun processNewBen(): Boolean = processNewBenMutex.withLock {
        withContext(Dispatchers.IO) {
            val user =
                preferenceDao.getLoggedInUser()
                    ?: throw IllegalStateException("No user logged in!!")

            benDao.resetSyncingToUnsynced()
            benDao.requeueTempBeneficiariesForRegistration()

            val benList = benDao.getAllUnprocessedBen()
            Timber.d("YTR 419 $benList")

            // Registration is already one request per beneficiary, so a rejected
            // one never blocks the others; failures are recorded for visibility.
            var registered = 0
            benList.forEach {
                val tempId = it.beneficiaryId
                lastBenUploadError = null
                val idGenerated = createBenIdAtServerByBeneficiarySending(it, user, it.locationRecord)
                if (idGenerated) {
                    registered++
                    syncRetryStore.clear(SyncRetryStore.ENTITY_REGISTRATION, listOf(tempId))
                } else {
                    syncRetryStore.recordFailure(
                        SyncRetryStore.ENTITY_REGISTRATION, tempId,
                        lastBenUploadError ?: "Registration rejected by server", backoff = false
                    )
                    Timber.w("Amrit push beneficiary registration pending retry: benId=${it.beneficiaryId}, benRegId=${it.benRegId}, hhId=${it.householdId}")
                }
            }

            logSyncModule("BENEFICIARY_REGISTRATION", "PUSH", syncPushOutcome(benList.size, registered),
                "pending" to benList.size, "registered" to registered, "remaining" to benList.size - registered)
            val readyBens = benDao.getAllBenForSyncWithServer().filter {
                if (it.beneficiaryId <= 0L || it.benRegId <= 0L) {
                    logSyncSkip("BENEFICIARY", "PUSH", "SERVER_REGISTRATION_ID_NOT_AVAILABLE",
                        "benId" to it.beneficiaryId, "benRegId" to it.benRegId, "householdId" to it.householdId)
                    benDao.setSyncState(it.householdId, it.beneficiaryId, SyncState.UNSYNCED)
                    Timber.w("Skipping beneficiary final sync until real IDs are available: benId=${it.beneficiaryId}, benRegId=${it.benRegId}, hhId=${it.householdId}")
                    false
                } else true
            }

            // A beneficiary is sent together with its household and kid record;
            // a household's members form one group, which is accepted or
            // rejected as a unit. Groups with a rejected member that is still
            // waiting out its retry delay sit this cycle out (they stay UNSYNCED).
            fun groupKey(ben: BenRegCache) = ben.householdId?.takeIf { hh -> hh > 0L } ?: -ben.beneficiaryId
            val (waiting, dueMembers) = readyBens.groupBy(::groupKey).values.partition { members ->
                members.any { syncRetryStore.isWaiting(SyncRetryStore.ENTITY_BENEFICIARY, it.beneficiaryId) }
            }
            if (waiting.isNotEmpty()) {
                Timber.w("Beneficiary push: ${waiting.size} group(s) waiting to retry: ${waiting.flatten().map { it.beneficiaryId }}")
            }
            // Claim, then re-read: the payload is built from the rows as they are at the claim, not
            // from the read above. An edit between that read and here is sent with this push; an
            // edit after the claim sets UNSYNCED again and is not marked synced below.
            val claimed = db.withTransaction {
                dueMembers.flatten().map { it.beneficiaryId }.chunked(500).flatMap { ids ->
                    benDao.claimForSync(ids)
                    benDao.getSyncingBens(ids)
                }
            }
            val due = claimed.groupBy(::groupKey).values.map { members -> buildUploadGroup(members, user) }

            // New records go in batches of at most MAX_BENS_PER_PUSH beneficiaries (a household's
            // members are never split); records that were rejected before are retried one group
            // at a time so they can't hold a batch back. PR 7.5: new records used to go as one
            // batch - 500 beneficiaries were one 64 s request, all-or-nothing until split.
            val (retrying, fresh) = due.partition { g ->
                g.benIds.any { syncRetryStore.contains(SyncRetryStore.ENTITY_BENEFICIARY, it) }
            }
            val freshBatches = batchBySize(fresh, MAX_BENS_PER_PUSH) { g -> g.members.size }
            val batches = freshBatches + retrying.map { g -> listOf(g) }
            val outcomes = mutableListOf<BatchIsolation.Outcome<UploadGroup>>()

            // Records what the completed batches got back. Also runs when a later batch hits a
            // network error (rethrown below): otherwise groups an earlier batch got rejected
            // would lose their retry history and go out as fresh again on the next run.
            suspend fun applyOutcomes(): Boolean {
                val sent = outcomes.flatMap { it.sent }
                val rejected = outcomes.flatMap { it.rejected }

                sent.forEach { syncRetryStore.clear(SyncRetryStore.ENTITY_BENEFICIARY, it.benIds) }
                if (sent.isNotEmpty() || registered > 0) {
                    // The server is accepting data: make every waiting record due again.
                    syncRetryStore.resetBackoff()
                }
                if (rejected.isNotEmpty()) {
                    // "The server rejected everything" (an outage, not bad records: no backoff)
                    // is judged per fresh batch of several groups, as well as for the whole run:
                    // one accepted batch must not give a later batch rejected during an outage
                    // per-record backoff of up to an hour. A single retried group rejected on
                    // its own still backs off unless nothing at all was accepted.
                    val serverWide = sent.isEmpty()
                    outcomes.forEachIndexed { index, outcome ->
                        if (outcome.rejected.isEmpty()) return@forEachIndexed
                        val batchServerWide = serverWide ||
                            (index < freshBatches.size && freshBatches[index].size > 1 && outcome.nothingAccepted)
                        val ids = outcome.rejected.flatMap { it.benIds }
                        benDao.benSyncWithServerFailed(*ids.toLongArray())
                        ids.forEach {
                            syncRetryStore.recordFailure(
                                SyncRetryStore.ENTITY_BENEFICIARY, it,
                                lastBenUploadError ?: "Rejected by server", backoff = !batchServerWide
                            )
                        }
                    }
                    val ids = rejected.flatMap { it.benIds }
                    Timber.e("Beneficiary push: ${rejected.size} group(s) rejected${if (serverWide) " (server rejected everything)" else ""}: $ids")
                }
                Timber.d("Beneficiary push: ${sent.size} group(s) sent, ${rejected.size} rejected, ${waiting.size} waiting")
                val attemptedBenIds = due.flatMap { it.benIds }.distinct()
                val markedBenIds = attemptedBenIds.filter { benDao.getBen(it)?.syncState == SyncState.SYNCED }
                logSyncModule("BENEFICIARY", "PUSH", syncPushOutcome(readyBens.size, markedBenIds.size),
                    "pending" to readyBens.size, "submitted" to attemptedBenIds.size,
                    "locallyMarkedSynced" to markedBenIds.size, "remaining" to readyBens.size - markedBenIds.size,
                    "rejected" to rejected.sumOf { it.benIds.size }, "waiting" to waiting.sumOf { it.size })
                val attemptedHhIds = due.flatMap { it.households }.map { it.householdId.toLong() }.distinct()
                val markedHhIds = attemptedHhIds.filter { householdDao.getHousehold(it)?.processed == "P" }
                logSyncModule("HOUSEHOLD", "PUSH", syncPushOutcome(attemptedHhIds.size, markedHhIds.size),
                    "submitted" to attemptedHhIds.size, "locallyMarkedSynced" to markedHhIds.size)
                return rejected.isEmpty()
            }

            // What the batch in flight has settled so far. BatchIsolation returns its outcome only
            // once the whole split finishes, so without this a network error in a later half
            // would drop the halves already accepted or rejected, and the rejected groups would
            // lose their retry record and go out as fresh again on the next run.
            val inFlightSent = mutableListOf<UploadGroup>()
            val inFlightRejected = mutableListOf<UploadGroup>()
            try {
                batches.forEach { batch ->
                    inFlightSent.clear()
                    inFlightRejected.clear()
                    outcomes += BatchIsolation.send(batch) { groups ->
                        postUploadGroups(groups).also { accepted ->
                            if (accepted) inFlightSent += groups
                            else if (groups.size == 1) inFlightRejected += groups
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e // claimed rows are reset to UNSYNCED at the start of the next run
            } catch (e: Exception) {
                // Network errors, and token-refresh failures (IllegalStateException), still
                // record what the completed batches got back before the run fails.
                if (inFlightSent.isNotEmpty() || inFlightRejected.isNotEmpty()) {
                    outcomes += BatchIsolation.Outcome(inFlightSent.toList(), inFlightRejected.toList())
                }
                applyOutcomes()
                throw e
            }
            return@withContext applyOutcomes()
        }
    }

    private class UploadGroup(
        val members: List<BenRegCache>,
        val bens: List<BenPost>,
        val households: List<HouseholdNetwork>,
        val kids: List<BenRegKidNetwork>,
    ) {
        val benIds: List<Long> get() = members.map { it.beneficiaryId }
    }

    private suspend fun buildUploadGroup(members: List<BenRegCache>, user: User): UploadGroup {
        val bens = mutableListOf<BenPost>()
        val households = mutableSetOf<HouseholdNetwork>()
        val kids = mutableListOf<BenRegKidNetwork>()
        members.forEach {
            val household = it.householdId?.let { hhId -> if (hhId > 0L) householdDao.getHousehold(hhId) else null }
            bens.add(it.asNetworkPostModel(context, user, household))
            household?.let { hh -> households.add(hh.asNetworkModel(user)) }
            try {
                if (it.ageUnitId != 3 || it.age < 15) kids.add(it.asKidNetworkModel(user))
            } catch (e: Exception) {
                Timber.e("caught error in adding kidDetails : $e")
            }
        }
        return UploadGroup(members, bens, households.toList(), kids)
    }

    private suspend fun postUploadGroups(groups: List<UploadGroup>): Boolean =
        postDataToAmritServer(
            groups.flatMap { it.bens }.toMutableSet(),
            groups.flatMap { it.households }.toMutableSet(),
            groups.flatMap { it.kids }.toMutableSet(),
        )

    private suspend fun postDataToAmritServer(
        benNetworkPostSet: MutableSet<BenPost>,
        householdNetworkPostSet: MutableSet<HouseholdNetwork>,
        kidNetworkPostSet: MutableSet<BenRegKidNetwork>,
        retryCount: Int = 3,
    ): Boolean {
        if (benNetworkPostSet.isEmpty() && householdNetworkPostSet.isEmpty() && kidNetworkPostSet.isEmpty()) return true
        val benIds = benNetworkPostSet.map { it.benId }
        val hhIds = householdNetworkPostSet.map { it.householdId }
        var failureRecorded = false
        Timber.d("Amrit push syncDataToAmrit: sending ${benNetworkPostSet.size} ben(s) $benIds, ${householdNetworkPostSet.size} hh(s) $hhIds, ${kidNetworkPostSet.size} kid(s)")

        val rmnchData = SendingRMNCHData(
            houseHoldRegistrationData = householdNetworkPostSet.toList(),
            benficieryRegistrationData = benNetworkPostSet.toList(),
            cbacData = null,
            birthDetails = kidNetworkPostSet.toList(),
            counsellingDetails = null
        )
        try {
            val jsonPayload = Gson().toJson(rmnchData)
            Timber.d("Amrit push syncDataToAmrit payload JSON: $jsonPayload")
        } catch (e: Exception) {
            Timber.e(e, "Failed to serialize rmnchData to JSON for logging")
        }
        try {
            val response = tmcNetworkApiService.submitRmnchDataAmrit(rmnchData)
            Log.d("Response final",response.toString())
            val statusCode = response.code()
            Timber.d("Amrit push syncDataToAmrit response: httpStatus=$statusCode")

            if (statusCode == 200) {

                val responseString: String? = response.body()?.string()
                failureRecorded = SyncDailyStore.response("BENEFICIARY", benIds, statusCode, responseString)
                SyncDailyStore.response("HOUSEHOLD", hhIds, statusCode, responseString)
                if (responseString != null) {
                    val jsonObj = JSONObject(responseString)
                    val responseStatusCode = jsonObj.getInt("statusCode")
                    val errorMessage = jsonObj.optString("errorMessage", "")
                    if (responseStatusCode == 200) {
                        Timber.d("Amrit push syncDataToAmrit success: $jsonObj")
                        val benToUpdateList =
                            benNetworkPostSet.takeIf { it.isNotEmpty() }?.map { it.benId }
                                ?.toTypedArray()?.toLongArray()
                        val hhToUpdateList = householdNetworkPostSet.takeIf { it.isNotEmpty() }
                            ?.map { it.householdId.toLong() }?.toTypedArray()?.toLongArray()
                        Timber.d("Amrit push syncDataToAmrit marking synced: benIds=${benToUpdateList?.toList()}, hhIds=${hhToUpdateList?.toList()}")
                        benToUpdateList?.let {
                            benDao.benSyncedWithServer(*it)
                            Timber.d("Amrit push syncDataToAmrit DB updated: benIds=${it.toList()}")
                        }
                        hhToUpdateList?.let { householdDao.householdSyncedWithServer(*it) }
                        val syncedBenIds = benIds.filter { benDao.getBen(it)?.syncState == SyncState.SYNCED }
                        val syncedHhIds = hhIds.filter { householdDao.getHousehold(it.toLong())?.processed == "P" }
                        SyncDailyStore.record("BENEFICIARY", syncedBenIds, "PUSH", "SUCCESS")
                        SyncDailyStore.record("HOUSEHOLD", syncedHhIds, "PUSH", "SUCCESS")
                        
                        return true
                    } else if (responseStatusCode == 5002 || responseStatusCode == 401) {
                        val user = preferenceDao.getLoggedInUser()
                            ?: throw IllegalStateException("User not logged in according to db")
                        if (userRepo.refreshTokenTmc(
                                user.userName, user.password
                            )
                        ) throw SocketTimeoutException("Refreshed Token!")
                        else throw IllegalStateException("User seems to be logged out and refresh token not working!!!!")
                    }
                    Timber.e("Amrit push syncDataToAmrit failed: statusCode=$responseStatusCode, error=$errorMessage")
                    lastBenUploadError = "statusCode=$responseStatusCode: $errorMessage"
                } else {
                    Timber.e("Amrit push syncDataToAmrit failed: response body is null, httpStatus=$statusCode")
                    lastBenUploadError = "HTTP $statusCode, empty response"
                }
            }
            Timber.w("Amrit push syncDataToAmrit bad response: httpStatus=$statusCode, benIds=$benIds")
            if (statusCode != 200) lastBenUploadError = "HTTP $statusCode"
            if (!failureRecorded) {
                SyncDailyStore.response("BENEFICIARY", benIds, statusCode, null)
                SyncDailyStore.response("HOUSEHOLD", hhIds, statusCode, null)
            }
            return false
        } catch (e: SocketTimeoutException) {
            // "Refreshed Token!" is thrown above after a successful token refresh;
            // resend once. A real timeout propagates so the worker retries with
            // backoff (resending the batch here could duplicate it on the server).
            if (e.message == "Refreshed Token!" && retryCount > 0) return postDataToAmritServer(
                benNetworkPostSet, householdNetworkPostSet, kidNetworkPostSet, retryCount = 0
            )
            Timber.e("Amrit push syncDataToAmrit timeout: benIds=$benIds, error=$e")
            SyncDailyStore.record("BENEFICIARY", benIds, "PUSH", "FAILURE", "NETWORK_TIMEOUT")
            SyncDailyStore.record("HOUSEHOLD", hhIds, "PUSH", "FAILURE", "NETWORK_TIMEOUT")
            throw e
        } catch (e: IOException) {
            Timber.e("Amrit push syncDataToAmrit network error: benIds=$benIds, error=$e")
            SyncDailyStore.record("BENEFICIARY", benIds, "PUSH", "FAILURE", "NETWORK_${e.javaClass.simpleName}")
            SyncDailyStore.record("HOUSEHOLD", hhIds, "PUSH", "FAILURE", "NETWORK_${e.javaClass.simpleName}")
            throw e
        } catch (e: JSONException) {
            Timber.e("Amrit push syncDataToAmrit JSON error: benIds=$benIds, error=$e")
            SyncDailyStore.record("BENEFICIARY", benIds, "PUSH", "FAILURE", "MALFORMED_RESPONSE")
            SyncDailyStore.record("HOUSEHOLD", hhIds, "PUSH", "FAILURE", "MALFORMED_RESPONSE")
            lastBenUploadError = "Unreadable server response"
            return false
        } catch (e: java.lang.Exception) {
            if (e is IllegalStateException && e.message?.contains("logged out") == true) {
                // Authentication failure, not a problem with these records: let the
                // worker handle it instead of splitting the batch into more requests.
                throw e
            }
            Timber.e("Amrit push syncDataToAmrit error: benIds=$benIds, error=$e")
            if (!failureRecorded) {
                SyncDailyStore.record("BENEFICIARY", benIds, "PUSH", "FAILURE", "EXCEPTION_${e.javaClass.simpleName}")
                SyncDailyStore.record("HOUSEHOLD", hhIds, "PUSH", "FAILURE", "EXCEPTION_${e.javaClass.simpleName}")
            }
            lastBenUploadError = e.message ?: e.javaClass.simpleName
            return false
        }
    }


    suspend fun deactivateHouseHold(
        benNetworkPostSet: List<BenRegCache>,
        householdNetworkPostSet: HouseholdNetwork,
        retryCount: Int = 3,
    ): Boolean {
        val user = preferenceDao.getLoggedInUser() ?: throw IllegalStateException("No user logged in!!")
        val benNetworkPostList: List<BenPost> =
            benNetworkPostSet.map {
                val household = it.householdId?.let { hhId -> if (hhId > 0L) householdDao.getHousehold(hhId) else null }
                it.asNetworkPostModel(context, user, household)
            }


        val rmnchData = SendingRMNCHData(
            listOf(householdNetworkPostSet),
            benNetworkPostList
        )
        try {
            val jsonPayload = Gson().toJson(rmnchData)
            Timber.d("deactivateHouseHold syncDataToAmrit payload JSON: $jsonPayload")
        } catch (e: Exception) {
            Timber.e(e, "Failed to serialize deactivateHouseHold payload to JSON")
        }
        try {
            val response = tmcNetworkApiService.submitRmnchDataAmrit(rmnchData)
            val statusCode = response.code()

            if (statusCode == 200) {

                val responseString: String? = response.body()?.string()
                if (responseString != null) {
                    val jsonObj = JSONObject(responseString)
                    val responseStatusCode = jsonObj.getInt("statusCode")
                    val errorMessage = jsonObj.optString("errorMessage", "")
                    if (responseStatusCode == 200) {
                        Timber.d("response : $jsonObj")
                        WorkerUtils.triggerAmritPullWorker(context)
                        return true
                    } else if (responseStatusCode == 5002) {
                        val user = preferenceDao.getLoggedInUser()
                            ?: throw IllegalStateException("User not logged in according to db")
                        if (userRepo.refreshTokenTmc(
                                user.userName, user.password
                            )
                        ) throw SocketTimeoutException("Refreshed Token!")
                        else throw IllegalStateException("User seems to be logged out and refresh token not working!!!!")
                    }
                }
            }
            Timber.w("Bad Response from server, need to check $householdNetworkPostSet")
            return false
        } catch (e: SocketTimeoutException) {
            Timber.e("Caught exception $e here")
            if (retryCount > 0) return deactivateHouseHold(
                benNetworkPostSet, householdNetworkPostSet, retryCount - 1
            )
            Timber.e("deactivateHouseHold: max retries exhausted")
            return false
        } catch (e: JSONException) {
            Timber.e("Caught exception $e here")
            return false
        } catch (e: java.lang.Exception) {
            Timber.e("Caught exception $e here")
            return false
        }
    }

    suspend fun deactivateBeneficiary(
        benNetworkPostSet: List<BenRegCache>,
        retryCount: Int = 3,
    ): Boolean {
        val user = preferenceDao.getLoggedInUser() ?: throw IllegalStateException("No user logged in!!")
        val benNetworkPostList: List<BenPost> =
            benNetworkPostSet.map {
                val household = it.householdId?.let { hhId -> if (hhId > 0L) householdDao.getHousehold(hhId) else null }
                it.asNetworkPostModel(context, user, household)
            }


        val rmnchData = SendingRMNCHData(
            //   listOf(householdNetworkPostSet),
            benficieryRegistrationData= benNetworkPostList
        )
        try {
            val jsonPayload = Gson().toJson(rmnchData)
            Timber.d("deactivateBeneficiary syncDataToAmrit payload JSON: $jsonPayload")
        } catch (e: Exception) {
            Timber.e(e, "Failed to serialize deactivateBeneficiary payload to JSON")
        }
        try {
            val response = tmcNetworkApiService.submitRmnchDataAmrit(rmnchData)
            val statusCode = response.code()

            if (statusCode == 200) {

                val responseString: String? = response.body()?.string()
                if (responseString != null) {
                    val jsonObj = JSONObject(responseString)
                    val responseStatusCode = jsonObj.getInt("statusCode")
                    val errorMessage = jsonObj.optString("errorMessage", "")
                    if (responseStatusCode == 200) {
                        Timber.d("response : $jsonObj")
                        WorkerUtils.triggerAmritPullWorker(context)
                        return true
                    } else if (responseStatusCode == 5002) {
                        val user = preferenceDao.getLoggedInUser()
                            ?: throw IllegalStateException("User not logged in according to db")
                        if (userRepo.refreshTokenTmc(
                                user.userName, user.password
                            )
                        ) throw SocketTimeoutException("Refreshed Token!")
                        else throw IllegalStateException("User seems to be logged out and refresh token not working!!!!")
                    }
                }
            }
            Timber.w("Bad Response from server, need to check $benNetworkPostList")
            return false
        } catch (e: SocketTimeoutException) {
            Timber.e("Caught exception $e here")
            if (retryCount > 0) return deactivateBeneficiary(
                benNetworkPostSet, retryCount - 1
            )
            Timber.e("deactivateBeneficiary: max retries exhausted")
            return false
        } catch (e: JSONException) {
            Timber.e("Caught exception $e here")
            return false
        } catch (e: java.lang.Exception) {
            Timber.e("Caught exception $e here")
            return false
        }
    }

    suspend fun getBeneficiariesFromServerForWorker(pageNumber: Int): Int {
        Timber.d("=====1234:getBeneficiariesFromServerForWorker : $pageNumber")

        return withContext(Dispatchers.IO) {
            val user =
                preferenceDao.getLoggedInUser()
                    ?: throw IllegalStateException("No user logged in!!")

            try {
                val locationRecord = preferenceDao.getLocationRecord()

                val request = GetDataPaginatedRequests(
                    providerServiceMapID = user.serviceMapId,
                    villageID = locationRecord?.village?.id ?: 0,
                    pageNo = pageNumber
                )

                Timber.d("Pull beneficiary data request: $request")

                val response = tmcNetworkApiService.getBeneficiaries(request)

                val statusCode = response.code()

                if (statusCode == 200) {
                    val responseString = response.body()?.string()

                    if (responseString != null) {
                        val jsonObj = JSONObject(responseString)

                        val errorMessage = jsonObj.optString("errorMessage", "")
                        val responseStatusCode = jsonObj.getInt("statusCode")

                        Timber.d("Pull from amrit page $pageNumber response status : $responseStatusCode")

                        when (responseStatusCode) {

                            200 -> {

                                val pageSize = getResponseTotalPage(jsonObj)

                                val householdCacheList = try {
                                    getHouseholdCacheFromServerResponse(responseString)
                                } catch (e: Exception) {
                                    Timber.e(e, "Pull page $pageNumber: household entries not parsed")
                                    emptyList()
                                }

                                val benCacheList =
                                    getBenCacheFromServerResponse(responseString, pageNumber)

                                bufferServerPage(householdCacheList, benCacheList, pageNumber)

                                Timber.d("GeTBenDataList: $pageSize")

                                return@withContext pageSize
                            }

                            401, 5002 -> {

                                if (
                                    pageNumber == 0 &&
                                    userRepo.refreshTokenTmc(
                                        user.userName,
                                        user.password
                                    )
                                ) {
                                    throw SocketTimeoutException("Refreshed Token!")
                                } else {
                                    throw IllegalStateException("User Logged out!!")
                                }
                            }

                            5000 -> {

                                if (errorMessage == "No record found") {
                                    logSyncModule("BENEFICIARY", "PULL", "NO_RECORDS_RETURNED",
                                        "received" to 0, "saved" to 0, "page" to pageNumber,
                                        "villageId" to request.villageID, "serviceMapId" to request.providerServiceMapID,
                                        "httpCode" to statusCode, "backendStatus" to responseStatusCode)
                                    logSyncModule("HOUSEHOLD", "PULL", "NO_RECORDS_RETURNED",
                                        "received" to 0, "saved" to 0, "page" to pageNumber)
                                    return@withContext 0
                                }
                            }

                            else -> {
                                throw IllegalStateException(
                                    "$responseStatusCode received, dont know what todo!?"
                                )
                            }
                        }
                    }
                }

            } catch (e: SocketTimeoutException) {

                Timber.e("get_ben error : $e")
                logSyncModule("BENEFICIARY", "PULL", "RETRY",
                    "page" to pageNumber, "reason" to e.javaClass.simpleName)
                return@withContext -2

            } catch (e: CancellationException) {

                Timber.w("get_ben cancelled : $e")
                throw e

            } catch (e: IllegalStateException) {

                Timber.e("get_ben error : $e")
                return@withContext -1
            }

            -1
        }
    }

    // Beneficiaries (with their households) from the changes feed, in the same JSON getBeneficiaryData
    // returns, written at once with the same rules as a pulled batch (no buffering: the feed saves its
    // token right after this returns).
    suspend fun applyFeedBeneficiaries(records: JSONArray) {
        withContext(Dispatchers.IO) {
            val response = JSONObject().put("statusCode", 200)
                .put("data", JSONObject().put("data", records)).toString()
            // Unlike a pulled page, a household that can't be parsed fails the feed page, so the
            // token isn't saved past it.
            writeServerPageBatch(
                getHouseholdCacheFromServerResponse(response),
                getBenCacheFromServerResponse(response),
                emptyList()
            )
        }
    }

    // ── Bulk-pull write batching ──────────────────────────────────────────────
    // The server pages beneficiaries 10 at a time, so a full pull of this village is ~2,000
    // requests. Committing one transaction per page fired Room's InvalidationTracker ~once a
    // second for the entire (~40 minute) pull, and every invalidation re-ran every query the
    // open screen observes - on the non-household list that is a full, unpaged materialisation
    // of every row in BEN_BASIC_CACHE. Buffering the pages and committing in batches cuts the
    // invalidation rate by `benWriteBatchPages` without changing what ends up in the database.
    //
    // A batch that is dropped (worker cancelled, process death) is simply re-pulled on the next
    // cycle: the pull is a pure server->device download and every write is an upsert, so
    // re-applying a page is idempotent. `setFirstSyncLastSyncedPage` only ever moves backwards
    // to a page that has been flushed, so resume cannot skip an unwritten page.
    private val pendingBenPages = mutableListOf<BenRegCache>()
    private val pendingHouseholdPages = mutableListOf<HouseholdCache>()
    private val pendingPageNumbers = mutableListOf<Int>()
    private val pendingPagesMutex = Mutex()

    /**
     * The lowest page number still sitting in the write buffer, or null when nothing is
     * buffered. A first-sync resume marker must not advance past this, or the pages still in
     * memory would be skipped if the worker died before its next flush.
     */
    suspend fun lowestUnflushedPullPage(): Int? =
        pendingPagesMutex.withLock { pendingPageNumbers.minOrNull() }

    /**
     * Accumulates one pulled page. Flushes to the database once [benWriteBatchPages] pages have
     * been collected; otherwise returns immediately so the next page can be fetched.
     */
    private suspend fun bufferServerPage(
        householdCacheList: List<HouseholdCache>,
        benCacheList: List<BenRegCache>,
        pageNumber: Int
    ) {
        val batch: Triple<List<HouseholdCache>, List<BenRegCache>, List<Int>>? =
            pendingPagesMutex.withLock {
                pendingHouseholdPages += householdCacheList
                pendingBenPages += benCacheList
                pendingPageNumbers += pageNumber
                if (pendingPageNumbers.size < benWriteBatchPages) {
                    null
                } else {
                    Triple(
                        pendingHouseholdPages.toList(),
                        pendingBenPages.toList(),
                        pendingPageNumbers.toList()
                    ).also {
                        pendingHouseholdPages.clear()
                        pendingBenPages.clear()
                        pendingPageNumbers.clear()
                    }
                }
            }
        if (batch != null) writeServerPageBatch(batch.first, batch.second, batch.third)
    }

    /**
     * Writes whatever pages are still buffered. The pull worker must call this when its shards
     * finish (successfully or not) so the tail of the pull is not left in memory.
     */
    suspend fun flushPendingServerBeneficiaries() {
        val batch: Triple<List<HouseholdCache>, List<BenRegCache>, List<Int>>? =
            pendingPagesMutex.withLock {
                if (pendingPageNumbers.isEmpty()) {
                    null
                } else {
                    Triple(
                        pendingHouseholdPages.toList(),
                        pendingBenPages.toList(),
                        pendingPageNumbers.toList()
                    ).also {
                        pendingHouseholdPages.clear()
                        pendingBenPages.clear()
                        pendingPageNumbers.clear()
                    }
                }
            }
        if (batch != null) writeServerPageBatch(batch.first, batch.second, batch.third)
    }

    private suspend fun writeServerPageBatch(
        householdCacheList: List<HouseholdCache>,
        benCacheList: List<BenRegCache>,
        pageNumbers: List<Int>
    ) {
        if (householdCacheList.isEmpty() && benCacheList.isEmpty()) return
        val traceStartedAt = android.os.SystemClock.elapsedRealtime()
        var updated = 0
        var inserted = 0
        val savedHouseholds = mutableListOf<Long>()
        val savedBeneficiaries = mutableListOf<Long>()
        var protectedHouseholds = 0
        var failedHouseholds = 0
        var skippedLocallyEdited = 0
        // One transaction for the whole batch. Room's InvalidationTracker fires once per
        // transaction, so N pages now invalidate BENEFICIARY once rather than N times - and
        // every one of those invalidations re-ran every query observing the view. Households
        // ride in the same transaction for the same reason.
        db.withTransaction {
            // A household that is not processed, or has any member not SYNCED, has local work
            // (an edit or a registration still to push) and is left untouched.
            householdCacheList.forEach { serverHh ->
                val existingHh = householdDao.getHousehold(serverHh.householdId)
                val hasLocalWork = existingHh != null && (existingHh.processed != "P" ||
                        benDao.countNotSyncedInHousehold(serverHh.householdId) > 0)
                if (hasLocalWork) {
                    protectedHouseholds++
                    logSyncSkip("HOUSEHOLD", "PULL", "LOCAL_PENDING_EDITS_PROTECTED",
                        "householdId" to serverHh.householdId)
                    Timber.d("Pull: household ${serverHh.householdId} has local changes, not overwritten")
                } else {
                    try {
                        householdDao.upsert(serverHh)
                        savedHouseholds.add(serverHh.householdId)
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        failedHouseholds++
                        logSyncSkip("HOUSEHOLD", "PULL", "LOCAL_SAVE_FAILED",
                            "householdId" to serverHh.householdId, "errorType" to e.javaClass.simpleName)
                        Timber.e(e, "Pull: household ${serverHh.householdId} not saved")
                    }
                }
            }
            // Re-check sync state INSIDE the transaction. getBenCacheFromServerResponse decides
            // whether a local row is safe to overwrite at parse time, but with pages buffered
            // that decision can now be up to a whole batch of network round trips old. If the
            // user edited and saved this beneficiary in that window the row is UNSYNCED, and
            // writing the server copy over it would discard the edit AND mark it SYNCED, so no
            // later push would ever send it.
            benCacheList.forEach { serverBen ->
                val local = benDao.getBen(serverBen.beneficiaryId)
                when {
                    local == null -> {
                        benDao.upsert(serverBen)
                        inserted++
                        savedBeneficiaries.add(serverBen.beneficiaryId)
                    }
                    local.syncState != SyncState.SYNCED -> {
                        // Locally edited since this page was parsed - leave it for the push.
                        skippedLocallyEdited++
                        logSyncSkip("BENEFICIARY", "PULL", "LOCAL_PENDING_EDITS_PROTECTED",
                            "benId" to serverBen.beneficiaryId)
                    }
                    else -> {
                        benDao.updateBen(serverBen)
                        updated++
                        savedBeneficiaries.add(serverBen.beneficiaryId)
                    }
                }
            }
            if (skippedLocallyEdited > 0) {
                Timber.d("[pull] kept $skippedLocallyEdited locally-edited beneficiaries, not overwritten by the server copy")
            }
        }
        SyncDailyStore.record("BENEFICIARY", savedBeneficiaries, "PULL", "SUCCESS")
        SyncDailyStore.record("HOUSEHOLD", savedHouseholds, "PULL", "SUCCESS")
        logSyncModule("BENEFICIARY", "PULL", "SUCCESS", "parsed" to benCacheList.size,
            "saved" to inserted + updated, "skipped" to skippedLocallyEdited, "pages" to pageNumbers)
        logSyncModule("HOUSEHOLD", "PULL", if (failedHouseholds > 0) "PARTIAL_FAILURE" else "SUCCESS",
            "parsed" to householdCacheList.size, "saved" to savedHouseholds.size,
            "skipped" to protectedHouseholds, "failed" to failedHouseholds, "pages" to pageNumbers,
            "durationMs" to android.os.SystemClock.elapsedRealtime() - traceStartedAt)
    }

    private fun getLongFromDate(date: String): Long {
        return getLongFromDateOrNull(date) ?: 0L
    }

    private fun getLongFromDateOrNull(date: String?): Long? {
        if (date.isNullOrBlank()) return null

        val patterns = listOf(
            "MMM d, yyyy h:mm:ss a",
            "MMM d, yyyy HH:mm:ss",
            "yyyy-MM-dd",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        )
        for (pattern in patterns) {
            runCatching {
                SimpleDateFormat(pattern, Locale.ENGLISH).parse(date)?.time
            }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun getResponseDataArray(jsonObj: JSONObject): JSONArray {
        val data = jsonObj.opt("data")
        return when (data) {
            is JSONArray -> data
            is JSONObject -> data.optJSONArray("data") ?: JSONArray()
            else -> JSONArray()
        }
    }

    private fun getResponseTotalPage(jsonObj: JSONObject): Int {
        val data = jsonObj.opt("data")
        return when (data) {
            is JSONObject -> data.optInt("totalPage", 1)
            is JSONArray -> 1
            else -> 0
        }
    }

    private fun JSONObject.optStringOrNull(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name).takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
    }

    private fun JSONObject.optDoubleOrNull(name: String): Double? {
        if (!has(name) || isNull(name)) return null
        val value = optDouble(name)
        return if (value.isNaN()) null else value
    }

    var count = 0
    private suspend fun getBenCacheFromServerResponse(
        response: String,
        pageNumber: Int = -1
    ): MutableList<BenRegCache> {
        val jsonObj = JSONObject(response)
        val result = mutableListOf<BenRegCache>()

        // TEMPORARY PERF INSTRUMENTATION - how effective the existing change filter is.
        // Counting only; the filtering logic below is untouched.
        var seenOnServer = 0
        var skippedLocalUnsynced = 0
        var skippedUnchanged = 0
        var writtenChanged = 0

        val responseStatusCode = jsonObj.getInt("statusCode")
        if (responseStatusCode == 200) {
            val jsonArray = getResponseDataArray(jsonObj)

            if (jsonArray.length() != 0) {
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)

                    val benDataObj =
                          jsonObject.optJSONObject("beneficiaryDetails") ?: jsonObject

                    val demographicsObj =
                        jsonObject.optJSONObject("i_bendemographics")
                            ?: jsonObject.optJSONObject("benDemographics")
                            ?: JSONObject()

                    val abhaHealthDetailsObj =
                        jsonObject.optJSONObject("abhaHealthDetails") ?: JSONObject()

                    val anthropometryObj =
                        jsonObject.optJSONObject("anthropometry") ?: jsonObject

                    val stopTBDetailsObj =
                        jsonObject.optJSONObject("stopTBDetails") ?: jsonObject

                    val fullName = benDataObj.optStringOrNull("benName").orEmpty().trim()
                    val firstNameFromFullName =
                        fullName.substringBefore(" ").takeIf { it.isNotBlank() }
                    val lastNameFromFullName =
                        fullName.substringAfter(" ", "").takeIf { it.isNotBlank() }

                    val benId =
                        if (jsonObject.has("benficieryid")) jsonObject.getLong("benficieryid")
                        else -1L

                    if (benId == -1L) {
                        logSyncSkip("BENEFICIARY", "PULL", "BENEFICIARY_ID_MISSING", "recordIndex" to i)
                        continue
                    }

                    val existingBen = benDao.getBen(benId)

                    val parsedDobMillis = getLongFromDateOrNull(
                        benDataObj.optStringOrNull("dob")
                            ?: benDataObj.optStringOrNull("dOB")
                    )

                    val dobMillis = when {
                        parsedDobMillis != null -> parsedDobMillis
                        existingBen?.dob?.takeIf { it > 0L } != null -> existingBen.dob
                        else -> 0L
                    }

                    val ageUnitText =
                        benDataObj.optStringOrNull("age_unit")
                            ?: benDataObj.optStringOrNull("ageUnits")
                            ?: "Years"

                    val currentLocation = preferenceDao.getLocationRecord()

                    val createdByValue = benDataObj.optStringOrNull("createdBy")
                        ?: preferenceDao.getLoggedInUser()?.userName
                        ?: ""

                    val createdDateValue = benDataObj.optStringOrNull("createdDate")
                        ?: benDataObj.optStringOrNull("registrationDate")

                    val updatedDateValue =
                        benDataObj.optStringOrNull("updatedDate")
                            ?: benDataObj.optStringOrNull("updateDate")
                            ?: jsonObject.optStringOrNull("updatedDate")
                            ?: jsonObject.optStringOrNull("updateDate")
                            ?: createdDateValue

                    val ageValue = if (benDataObj.has("age")) {
                        benDataObj.optInt("age")
                    } else if (dobMillis > 0L) {
                        BenBasicCache.getAgeFromDob(dobMillis)
                    } else {
                        0
                    }

                    val childDataObj =
                        jsonObject.optJSONObject("bornbirthDeatils") ?: JSONObject()

                    val nikshayIdValue = benDataObj.optStringOrNull("nikshayId")
                        ?: benDataObj.optStringOrNull("nikshayID")
                        ?: jsonObject.optStringOrNull("nikshayId")
                        ?: jsonObject.optStringOrNull("nikshayID")
                        ?: stopTBDetailsObj.optStringOrNull("nikshayId")
                        ?: stopTBDetailsObj.optStringOrNull("nikshayID")

                    val benGpsPairs = listOf(
                        demographicsObj.optDoubleOrNull("latitude") to demographicsObj.optDoubleOrNull("longitude"),
                        demographicsObj.optDoubleOrNull("gpsLatitude") to demographicsObj.optDoubleOrNull("gpsLongitude"),
                        benDataObj.optDoubleOrNull("latitude") to benDataObj.optDoubleOrNull("longitude"),
                        benDataObj.optDoubleOrNull("gpsLatitude") to benDataObj.optDoubleOrNull("gpsLongitude"),
                        jsonObject.optDoubleOrNull("latitude") to jsonObject.optDoubleOrNull("longitude"),
                        jsonObject.optDoubleOrNull("gpsLatitude") to jsonObject.optDoubleOrNull("gpsLongitude"),
                    ).mapNotNull { (latitude, longitude) ->
                        if (latitude != null && longitude != null) latitude to longitude else null
                    }
                    val benGpsPair = benGpsPairs.firstOrNull { (latitude, longitude) ->
                        latitude != 0.0 || longitude != 0.0
                    } ?: benGpsPairs.firstOrNull()

                    val benGpsLatitude = benGpsPair?.first
                    val benGpsLongitude = benGpsPair?.second

                    val benDigipin = demographicsObj.optStringOrNull("digipin")
                        ?: benDataObj.optStringOrNull("digipin")
                        ?: jsonObject.optStringOrNull("digipin")

                    val benGpsTimestamp = demographicsObj.optStringOrNull("gpsTimestamp")
                        ?: benDataObj.optStringOrNull("gpsTimestamp")
                        ?: jsonObject.optStringOrNull("gpsTimestamp")

                    val benIsGpsUnavailable = demographicsObj.optBoolean("isGpsUnavailable", false)
                        || benDataObj.optBoolean("isGpsUnavailable", false)
                        || jsonObject.optBoolean("isGpsUnavailable", false)

                    val benGpsUnavailableReason = demographicsObj.optStringOrNull("gpsUnavailableReason")
                        ?: benDataObj.optStringOrNull("gpsUnavailableReason")
                        ?: jsonObject.optStringOrNull("gpsUnavailableReason")

                    try {
                        // Server sends key with typo ("houseoldId") or correct ("householdId") — check both.
                        val resolvedHouseholdId = run {
                            val fromTypo = if (jsonObject.has("houseoldId") && !jsonObject.isNull("houseoldId")) jsonObject.getLong("houseoldId").takeIf { it > 0L } else null
                            val fromCorrect = if (jsonObject.has("householdId") && !jsonObject.isNull("householdId")) jsonObject.getLong("householdId").takeIf { it > 0L } else null
                            // Server payload can omit the household key even for a genuine household member (seen when
                            // the household association hasn't fully propagated server-side at query time). Before
                            // concluding this beneficiary is truly non-household, check the familyHeadRelationPosition:
                            // a value in the normal household-relation range (1-20ish, excluding whatever code means
                            // "not applicable") strongly implies household membership even without an explicit ID.
                            val relationPosition = benDataObj.optInt(
                                "familyHeadRelationPosition",
                                jsonObject.optInt("familyHeadRelationPosition", 0)
                            )
                            val impliedByRelation = relationPosition in 1..20

                            fromTypo ?: fromCorrect
                            ?: existingBen?.householdId?.takeIf { it > 0L }
                            ?: existingBen?.takeIf { impliedByRelation && !it.isNonHH }?.householdId
                        }
                        val serverBen =
                            BenRegCache(
                                householdId = resolvedHouseholdId,

                                isNonHH = resolvedHouseholdId == null,

                                placeOfCurrentLiving = getPlaceOfCurrentLivingIndex(
                                    benDataObj.optStringOrNull("placeOfCurrentLiving")
                                ) ?: getPlaceOfCurrentLivingIndex(
                                    jsonObject.optStringOrNull("placeOfCurrentLiving")
                                ),

                                otherPlaceOfCurrentLiving = benDataObj.optStringOrNull("otherPlaceOfCurrentLiving")
                                    ?: jsonObject.optStringOrNull("otherPlaceOfCurrentLiving"),

                                institutionName = benDataObj.optStringOrNull("institutionName")
                                    ?: jsonObject.optStringOrNull("institutionName"),

                                beneficiaryId = jsonObject.getLong("benficieryid"),

                                isDeath = jsonObject.optBoolean("isDeath", false),

                                isDeathValue = jsonObject.optString("isDeath", null)
                                    .takeIf { !it.isNullOrEmpty() },

                                dateOfDeath = jsonObject.optString("dateOfDeath", null)
                                    .takeIf { !it.isNullOrEmpty() },

                                timeOfDeath = jsonObject.optString("timeOfDeath", null)
                                    .takeIf { !it.isNullOrEmpty() },

                                reasonOfDeath = jsonObject.optString("reasonOfDeath", null)
                                    .takeIf { !it.isNullOrEmpty() },

                                reasonOfDeathId = if (jsonObject.has("reasonOfDeathId")) {
                                    jsonObject.optInt("reasonOfDeathId").takeIf { it != 0 } ?: -1
                                } else -1,

                                placeOfDeath = jsonObject.optString("placeOfDeath", null)
                                    .takeIf { !it.isNullOrEmpty() },

                                placeOfDeathId = if (jsonObject.has("placeOfDeathId")) {
                                    jsonObject.optInt("placeOfDeathId").takeIf { it != 0 } ?: -1
                                } else -1,

                                otherPlaceOfDeath = jsonObject.optString("otherPlaceOfDeath", null)
                                    .takeIf { !it.isNullOrEmpty() },

                                ashaId = jsonObject.optInt("ashaId", 0),
                                benRegId = jsonObject.optLong("BenRegId", 0L),

                                gpsLatitude = benGpsLatitude,
                                gpsLongitude = benGpsLongitude,
                                digipin = benDigipin,
                                gpsTimestamp = benGpsTimestamp,
                                isGpsUnavailable = benIsGpsUnavailable,
                                gpsUnavailableReason = benGpsUnavailableReason,

                                isNewAbha = if (abhaHealthDetailsObj.has("isNewAbha"))
                                    abhaHealthDetailsObj.getBoolean("isNewAbha") else false,

                                age = ageValue,

                                isDeactivate = benDataObj.optBoolean("isDeactivate", false),

                                ageUnit = when (ageUnitText) {
                                    "Years" -> AgeUnit.YEARS
                                    "Months" -> AgeUnit.MONTHS
                                    "Days" -> AgeUnit.DAYS
                                    else -> AgeUnit.YEARS
                                },

                                ageUnitId = when (ageUnitText) {
                                    "Years" -> 3
                                    "Months" -> 2
                                    "Days" -> 1
                                    else -> 3
                                },

                                isKid = !(ageUnitText == "Years" && ageValue > 14),
                                isAdult = (ageUnitText == "Years" && ageValue > 14),

                                regDate = if (benDataObj.has("registrationDate"))
                                    getLongFromDate(benDataObj.getString("registrationDate")) else 0,

                                firstName = benDataObj.optStringOrNull("firstName") ?: firstNameFromFullName,
                                lastName = benDataObj.optStringOrNull("lastName") ?: lastNameFromFullName,

                                address = demographicsObj.optString("addressLine1")
                                    .ifEmpty { demographicsObj.optString("address") }
                                    .ifEmpty { benDataObj.optString("addressLine1") }
                                    .ifEmpty { benDataObj.optString("address") }
                                    .ifEmpty { jsonObject.optString("addressLine1") }
                                    .ifEmpty { jsonObject.optString("address") },

                                pinCode = demographicsObj.optString("pinCode")
                                    .ifEmpty { benDataObj.optString("pinCode") }
                                    .ifEmpty { jsonObject.optString("pinCode") }
                                    .ifEmpty { null },

                                genderId = benDataObj.optInt(
                                    "genderId",
                                    benDataObj.optInt("genderID", 0)
                                ),

                                gender = when (
                                    benDataObj.optInt(
                                        "genderId",
                                        benDataObj.optInt("genderID", 1)
                                    )
                                ) {
                                    1 -> Gender.MALE
                                    2 -> Gender.FEMALE
                                    3 -> Gender.TRANSGENDER
                                    4 -> Gender.PREFER_NOT_TO_SAY
                                    else -> Gender.MALE
                                },

                                dob = dobMillis,

                                fatherName = benDataObj.optStringOrNull("fatherName"),

                                personFrom = stopTBDetailsObj.optStringOrNull("personFrom"),

                                typeOfCaseFinding = stopTBDetailsObj.optStringOrNull("caseFindingType"),

                                height = anthropometryObj.optDouble("height").takeIf { !it.isNaN() },
                                weight = anthropometryObj.optDouble("weight").takeIf { !it.isNaN() },
                                bmi = anthropometryObj.optDouble("bmi").takeIf { !it.isNaN() },
                                temperature = anthropometryObj.optDouble("temperatureValue").takeIf { !it.isNaN() },
                                nikshayId = nikshayIdValue,

                                motherName = benDataObj.optStringOrNull("motherName"),

                                familyHeadRelation = benDataObj.optStringOrNull("familyHeadRelation"),
                                familyHeadRelationPosition = benDataObj.optInt("familyHeadRelationPosition", 0),

                                mobileNoOfRelation = benDataObj.optStringOrNull("mobilenoofRelation"),
                                mobileNoOfRelationId = benDataObj.optInt("mobilenoofRelationId", 0),

                                mobileOthers = benDataObj.optStringOrNull("mobileOthers"),

                                contactNumber = benDataObj.optStringOrNull("contact_number")?.toLongOrNull()
                                    ?: jsonObject.optJSONArray("benPhoneMaps")
                                        ?.optJSONObject(0)
                                        ?.optString("phoneNo")
                                        ?.toLongOrNull(),

                                literacyId = benDataObj.optInt("literacyId", 0),

                                communityId = benDataObj.optInt(
                                    "communityId",
                                    demographicsObj.optInt("communityID", 0)
                                ),

                                community = benDataObj.optStringOrNull("community")
                                    ?: demographicsObj.optStringOrNull("communityName"),

                                religion = benDataObj.optStringOrNull("religion")
                                    ?: demographicsObj.optStringOrNull("religionName"),

                                religionId = benDataObj.optInt(
                                    "religionID",
                                    demographicsObj.optInt("religionID", 0)
                                ),

                                religionOthers = benDataObj.optStringOrNull("religionOthers"),

                                rchId = benDataObj.optStringOrNull("rchid"),

                                occupation = benDataObj.optStringOrNull("occupation")
                                    ?: demographicsObj.optStringOrNull("occupation"),

                                economicStatus = benDataObj.optStringOrNull("economicStatus")
                                    ?: benDataObj.optStringOrNull("type_bpl_apl")
                                    ?: benDataObj.optStringOrNull("incomeStatusName")
                                    ?: demographicsObj.optStringOrNull("economicStatus")
                                    ?: demographicsObj.optStringOrNull("type_bpl_apl")
                                    ?: demographicsObj.optStringOrNull("incomeStatusName")
                                    ?: jsonObject.optStringOrNull("economicStatus")
                                    ?: jsonObject.optStringOrNull("type_bpl_apl")
                                    ?: jsonObject.optStringOrNull("incomeStatusName"),

                                economicStatusId = if (benDataObj.has("economicStatusId"))
                                    benDataObj.optInt("economicStatusId")
                                else if (benDataObj.has("bpl_aplId"))
                                    benDataObj.optInt("bpl_aplId")
                                else if (benDataObj.has("incomeStatusID"))
                                    benDataObj.optInt("incomeStatusID")
                                else if (benDataObj.has("incomeStatusId"))
                                    benDataObj.optInt("incomeStatusId")
                                else if (demographicsObj.has("economicStatusId"))
                                    demographicsObj.optInt("economicStatusId")
                                else if (demographicsObj.has("bpl_aplId"))
                                    demographicsObj.optInt("bpl_aplId")
                                else if (demographicsObj.has("incomeStatusID"))
                                    demographicsObj.optInt("incomeStatusID")
                                else if (demographicsObj.has("incomeStatusId"))
                                    demographicsObj.optInt("incomeStatusId")
                                else if (jsonObject.has("economicStatusId"))
                                    jsonObject.optInt("economicStatusId")
                                else if (jsonObject.has("bpl_aplId"))
                                    jsonObject.optInt("bpl_aplId")
                                else if (jsonObject.has("incomeStatusID"))
                                    jsonObject.optInt("incomeStatusID")
                                else if (jsonObject.has("incomeStatusId"))
                                    jsonObject.optInt("incomeStatusId")
                                else null,

                                residentialArea = benDataObj.optStringOrNull("residentialArea")
                                    ?: demographicsObj.optStringOrNull("residentialArea")
                                    ?: jsonObject.optStringOrNull("residentialArea"),

                                residentialAreaId = if (benDataObj.has("residentialAreaId"))
                                    benDataObj.optInt("residentialAreaId")
                                else if (demographicsObj.has("residentialAreaId"))
                                    demographicsObj.optInt("residentialAreaId")
                                else if (jsonObject.has("residentialAreaId"))
                                    jsonObject.optInt("residentialAreaId")
                                else null,

                                otherResidentialArea = benDataObj.optStringOrNull("otherResidentialArea")
                                    ?: benDataObj.optStringOrNull("other_residentialArea")
                                    ?: demographicsObj.optStringOrNull("otherResidentialArea")
                                    ?: demographicsObj.optStringOrNull("other_residentialArea")
                                    ?: jsonObject.optStringOrNull("otherResidentialArea")
                                    ?: jsonObject.optStringOrNull("other_residentialArea"),

                                latitude = benDataObj.optDouble("latitude", 0.0),
                                longitude = benDataObj.optDouble("longitude", 0.0),

                                aadharNum = benDataObj.optStringOrNull("aadhaNo"),
                                aadharNumId = benDataObj.optInt("aadha_noId", 0),

                                hasAadhar = !benDataObj.optString("aadhaNo", "").isNullOrEmpty(),
                                hasAadharId = if (benDataObj.optInt("aadha_noId", 0) == 1) 1 else 0,

                                bankAccount = benDataObj.optStringOrNull("bankAccount"),
                                nameOfBank = benDataObj.optStringOrNull("nameOfBank"),
                                ifscCode = benDataObj.optStringOrNull("ifscCode"),

                                needOpCareId = benDataObj.optInt("need_opcareId", 0),
                                ncdPriority = benDataObj.optInt("ncd_priority", 0),

                                guidelineId = benDataObj.optStringOrNull("guidelineId"),
                                isHrpStatus = benDataObj.optBoolean("hrpStatus", false),

                                locationRecord = LocationRecord(
                                    country = currentLocation?.country ?: LocationEntity(1, "India"),

                                    state = LocationEntity(
                                        demographicsObj.optInt(
                                            "stateID",
                                            benDataObj.optInt("stateId", currentLocation?.state?.id ?: 0)
                                        ),
                                        demographicsObj.optStringOrNull("stateName")
                                            ?: benDataObj.optStringOrNull("stateName")
                                            ?: currentLocation?.state?.name.orEmpty()
                                    ),

                                    district = LocationEntity(
                                        demographicsObj.optInt(
                                            "districtID",
                                            benDataObj.optInt("districtid", currentLocation?.district?.id ?: 0)
                                        ),
                                        demographicsObj.optStringOrNull("districtName")
                                            ?: benDataObj.optStringOrNull("districtname")
                                            ?: currentLocation?.district?.name.orEmpty()
                                    ),

                                    block = LocationEntity(
                                        demographicsObj.optInt(
                                            "blockID",
                                            benDataObj.optInt("blockId", currentLocation?.block?.id ?: 0)
                                        ),
                                        benDataObj.optStringOrNull("facilitySelection")
                                            ?: benDataObj.optStringOrNull("blockName")
                                            ?: currentLocation?.block?.name.orEmpty()
                                    ),

                                    village = LocationEntity(
                                        demographicsObj.optInt(
                                            "districtBranchID",
                                            benDataObj.optInt("villageId", currentLocation?.village?.id ?: 0)
                                        ),
                                        demographicsObj.optStringOrNull("districtBranchName")
                                            ?: benDataObj.optStringOrNull("villageName")
                                            ?: currentLocation?.village?.name.orEmpty()
                                    ),

                                    tu = stopTBDetailsObj.optInt("tuId").takeIf { it > 0 }?.let { id ->
                                        LocationEntity(id, stopTBDetailsObj.optStringOrNull("tuName").orEmpty())
                                    } ?: currentLocation?.tu,

                                    healthFacility = stopTBDetailsObj.optInt("healthFacilityId").takeIf { it > 0 }?.let { id ->
                                        LocationEntity(
                                            id,
                                            stopTBDetailsObj.optStringOrNull("healthFacilityName").orEmpty()
                                        )
                                    } ?: currentLocation?.healthFacility,
                                ),

                                processed = "P",
                                serverUpdatedStatus = 1,

                                createdBy = createdByValue,
                                updatedBy = benDataObj.optStringOrNull("updatedBy") ?: createdByValue,

                                createdDate = createdDateValue?.let { getLongFromDate(it) } ?: 0L,
                                updatedDate = updatedDateValue?.let { getLongFromDate(it) } ?: 0L,
                                serverUpdatedDate = updatedDateValue?.let { getLongFromDate(it) } ?: 0L,

                                userImage = if (benDataObj.has("user_image"))
                                    ImageUtils.saveBenImageFromServerToStorage(
                                        context,
                                        benDataObj.getString("user_image"),
                                        benId
                                    )
                                else null,

                                kidDetails = null,

                                genDetails = BenRegGen(
                                    maritalStatus = benDataObj.optStringOrNull("maritalstatus")
                                        ?: benDataObj.optStringOrNull("maritalStatus")
                                        ?: benDataObj.optStringOrNull("maritalStatusName"),

                                    maritalStatusId = benDataObj.optInt(
                                        "maritalstatusId",
                                        benDataObj.optString("maritalStatusID", "0").toIntOrNull() ?: 0
                                    ),

                                    spouseName = benDataObj.optStringOrNull("spousename")
                                        ?: benDataObj.optStringOrNull("spouseName"),

                                    ageAtMarriage = benDataObj.optInt("ageAtMarriage", 0),

                                    marriageDate = if (benDataObj.has("marriageDate"))
                                        getLongFromDate(benDataObj.getString("marriageDate"))
                                    else null,

                                    reproductiveStatus = benDataObj.optStringOrNull("reproductiveStatus"),

                                    reproductiveStatusId = if (benDataObj.has("reproductiveStatusId")) {
                                        val idFromServer = benDataObj.getInt("reproductiveStatusId")
                                        when (idFromServer) {
                                            0 -> 0
                                            1 -> 1
                                            2, 3 -> 2
                                            4 -> 3
                                            5 -> 4
                                            6 -> 5
                                            else -> 5
                                        }
                                    } else 0,
                                ),

                                healthIdDetails = if (abhaHealthDetailsObj.length() > 0) {
                                    BenHealthIdDetails(
                                        healthIdNumber = abhaHealthDetailsObj.getString("HealthIdNumber"),
                                        isNewAbha = if (abhaHealthDetailsObj.has("isNewAbha"))
                                            abhaHealthDetailsObj.getBoolean("isNewAbha") else false,
                                        healthId = abhaHealthDetailsObj.getString("HealthID")
                                    )
                                } else null,

                                syncState = SyncState.SYNCED,
                                isDraft = false,
                                isConsent = benDataObj.optBoolean("beneficiaryConsent", false),

                                isSpouseAdded = jsonObject.optBoolean("isSpouseAdded", false),
                                isChildrenAdded = jsonObject.optBoolean("isChildrenAdded", false),
                                isMarried = jsonObject.optBoolean("isMarried", false),

                                doYouHavechildren = jsonObject.optBoolean("doYouHavechildren", false),
                                noOfAliveChildren = jsonObject.optInt("noofAlivechildren", 0),
                                noOfChildren = jsonObject.optInt("noOfchildren", 0),
                            )

                        seenOnServer++   // TEMPORARY PERF INSTRUMENTATION

                        if (existingBen == null) {
                            result.add(serverBen)
                        } else {
                            if (existingBen.syncState != SyncState.SYNCED) {
                                logSyncSkip("BENEFICIARY", "PULL", "LOCAL_PENDING_EDITS_PROTECTED",
                                    "benId" to benId, "recordIndex" to i)
                                // Local has unpushed edits or is mid-push: don't overwrite with server data
                                skippedLocalUnsynced++   // TEMPORARY PERF INSTRUMENTATION
                                nikshayIdValue?.takeIf { it.isNotBlank() && it != existingBen.nikshayId }?.let {
                                    benDao.updateNikshayId(benId, it)
                                }
                                continue
                            }

                            // The row exactly as it would be persisted, merge rules included.
                            val serverHasLivingInfo = serverBen.placeOfCurrentLiving != null ||
                                !serverBen.otherPlaceOfCurrentLiving.isNullOrBlank() ||
                                !serverBen.institutionName.isNullOrBlank()
                            val livingSource = if (serverHasLivingInfo) serverBen else existingBen
                            val mergedBen = serverBen.copy(
                                userImage = serverBen.userImage ?: existingBen.userImage,
                                healthIdDetails = serverBen.healthIdDetails ?: existingBen.healthIdDetails,
                                height = serverBen.height ?: existingBen.height,
                                weight = serverBen.weight ?: existingBen.weight,
                                bmi = serverBen.bmi ?: existingBen.bmi,
                                temperature = serverBen.temperature ?: existingBen.temperature,
                                placeOfCurrentLiving = livingSource.placeOfCurrentLiving,
                                otherPlaceOfCurrentLiving = livingSource.otherPlaceOfCurrentLiving,
                                institutionName = livingSource.institutionName,
                                isConsent = serverBen.isConsent || existingBen.isConsent,
                                syncState = SyncState.SYNCED,
                                processed = "P"
                            )

                            // Compare the whole row we are about to write against the whole row
                            // already stored, rather than a hand-picked subset of columns. A pull
                            // then cannot rewrite a row that is already byte-identical (which cost
                            // a transaction and invalidated every list observing BENEFICIARY), and
                            // cannot silently drop a column that genuinely changed but happened to
                            // sit outside that subset.
                            //
                            // serverUpdatedDate and the created*/updated* audit columns are
                            // deliberately excluded: they move on their own and a difference there
                            // alone is not a data change. That keeps the previous intent — never
                            // skip purely on a timestamp — while making the fallback complete
                            // instead of four fields wide.
                            val localAuditFieldsCopied = mergedBen.copy(
                                serverUpdatedDate = existingBen.serverUpdatedDate,
                                createdBy = existingBen.createdBy,
                                createdDate = existingBen.createdDate,
                                updatedBy = existingBen.updatedBy,
                                updatedDate = existingBen.updatedDate
                            )
                            val storedNormalised = existingBen.copy(
                                syncState = SyncState.SYNCED,
                                processed = "P"
                            )

                            if (localAuditFieldsCopied == storedNormalised) {
                                skippedUnchanged++   // TEMPORARY PERF INSTRUMENTATION
                                nikshayIdValue?.takeIf { it.isNotBlank() && it != existingBen.nikshayId }?.let {
                                    benDao.updateNikshayId(benId, it)
                                }
                                continue
                            }

                            writtenChanged++   // TEMPORARY PERF INSTRUMENTATION

                            result.add(mergedBen)
                        }
                    } catch (e: JSONException) {
                        logSyncSkip("BENEFICIARY", "PULL", "INVALID_RECORD", "recordIndex" to i,
                            "benId" to jsonObject.optLong("benficieryid", -1L), "errorType" to e.javaClass.simpleName)
                        Timber.e("Beneficiary skipped: ${jsonObject.optLong("benficieryid", -1L)} with error $e")
                    } catch (e: NumberFormatException) {
                        logSyncSkip("BENEFICIARY", "PULL", "INVALID_NUMBER", "recordIndex" to i,
                            "benId" to jsonObject.optLong("benficieryid", -1L), "errorType" to e.javaClass.simpleName)
                        Timber.e("Beneficiary skipped: ${jsonObject.optLong("benficieryid", -1L)} with error $e")
                    }
                }
            }
        }

        return result
    }
    private suspend fun getHouseholdCacheFromServerResponse(response: String): MutableList<HouseholdCache> {
        val jsonObj = JSONObject(response)
        val result = mutableListOf<HouseholdCache>()
        val addedHouseholdIds = HashSet<Long>()

        val responseStatusCode = jsonObj.getInt("statusCode")
        if (responseStatusCode == 200) {
            val jsonArray = getResponseDataArray(jsonObj)

            if (jsonArray.length() != 0) {
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val benId =
                        if (jsonObject.has("benficieryid")) jsonObject.getLong("benficieryid") else -1L
                    val hhId = run {
                        // Check both typo and correct spelling; treat 0 as invalid
                        val fromTypo = if (jsonObject.has("houseoldId")) jsonObject.getLong("houseoldId").takeIf { it > 0L } else null
                        val fromCorrect = if (jsonObject.has("householdId")) jsonObject.getLong("householdId").takeIf { it > 0L } else null
                        fromTypo ?: fromCorrect ?: -1L
                    }
                    if (benId == -1L || hhId == -1L) {
                        logSyncSkip("HOUSEHOLD", "PULL", "BENEFICIARY_OR_HOUSEHOLD_ID_MISSING",
                            "recordIndex" to i, "benId" to benId, "householdId" to hhId)
                        continue
                    }
                    val houseDataObj = jsonObject.getJSONObject("householdDetails")
                    val benDataObj = jsonObject.getJSONObject("beneficiaryDetails")

                    // PR 7.6: a household already added from this page is skipped before its
                    // database lookup, and the check is a set lookup instead of a scan of the list
                    // built so far. Same rule as before: only households actually added count.
                    if (hhId in addedHouseholdIds) {
                        logSyncSkip("HOUSEHOLD", "PULL", "DUPLICATE_HOUSEHOLD_IN_RESPONSE",
                            "recordIndex" to i, "householdId" to hhId, "benId" to benId)
                        continue
                    }
                    val existingHh = householdDao.getHousehold(hhId)

                    val hhGpsLatitude = houseDataObj.optDoubleOrNull("latitude")
                        ?: houseDataObj.optDoubleOrNull("gpsLatitude")
                        ?: benDataObj.optDoubleOrNull("latitude")
                        ?: benDataObj.optDoubleOrNull("gpsLatitude")
                        ?: jsonObject.optDoubleOrNull("latitude")
                        ?: jsonObject.optDoubleOrNull("gpsLatitude")

                    val hhGpsLongitude = houseDataObj.optDoubleOrNull("longitude")
                        ?: houseDataObj.optDoubleOrNull("gpsLongitude")
                        ?: benDataObj.optDoubleOrNull("longitude")
                        ?: benDataObj.optDoubleOrNull("gpsLongitude")
                        ?: jsonObject.optDoubleOrNull("longitude")
                        ?: jsonObject.optDoubleOrNull("gpsLongitude")

                    val hhDigipin = houseDataObj.optStringOrNull("digipin")
                        ?: benDataObj.optStringOrNull("digipin")
                        ?: jsonObject.optStringOrNull("digipin")

                    val hhGpsTimestamp = houseDataObj.optStringOrNull("gpsTimestamp")
                        ?: benDataObj.optStringOrNull("gpsTimestamp")
                        ?: jsonObject.optStringOrNull("gpsTimestamp")

                    val hhIsGpsUnavailable = houseDataObj.optBoolean("isGpsUnavailable", false)
                        || benDataObj.optBoolean("isGpsUnavailable", false)
                        || jsonObject.optBoolean("isGpsUnavailable", false)

                    val hhGpsUnavailableReason = houseDataObj.optStringOrNull("gpsUnavailableReason")
                        ?: benDataObj.optStringOrNull("gpsUnavailableReason")
                        ?: jsonObject.optStringOrNull("gpsUnavailableReason")

                    try {
                        val serverHh = HouseholdCache(
                            householdId = hhId,  // already resolved from both spellings above
                            ashaId = jsonObject.optInt("ashaId"),
                            benId = jsonObject.optLong("benficieryid").takeIf { it > 0L },
                            family = HouseholdFamily(
                                totalHhMembers = houseDataObj.optInt("totalHhMembers").takeIf { it > 0 },
                                isRegisteredAtCampSite = houseDataObj.optStringOrNull("registeredAtCampSite"),
                                isRegisteredAtCampSiteId = houseDataObj.optInt("registeredAtCampSiteId"),
                                familyHeadName = houseDataObj.optStringOrNull("familyHeadName"),
                                familyName = houseDataObj.optStringOrNull("familyName"),
                                familyHeadPhoneNo = houseDataObj.optStringOrNull("familyHeadPhoneNo")
                                    ?.toLongOrNull() ?: 0L,
                                houseNo = houseDataObj.optStringOrNull("houseno"),
                                wardNo = houseDataObj.optStringOrNull("wardNo"),
                                wardName = houseDataObj.optStringOrNull("wardName"),
                                mohallaName = houseDataObj.optStringOrNull("mohallaName"),
//                                rationCardDetails = houseDataObj.optStringOrNull("rationCardDetails"),
                                povertyLine = houseDataObj.optStringOrNull("type_bpl_apl"),
                                povertyLineId = houseDataObj.optInt("bpl_aplId"),
                                address = houseDataObj.optStringOrNull("address"),
                                // Server field is "pincode" (FLW-API RMNCHHouseHoldDetails); 0 = not captured
                                pinCode = (houseDataObj.optInt("pincode").takeIf { it > 0 }
                                    ?: houseDataObj.optInt("Pincode").takeIf { it > 0 })?.toString(),
                            ),
                            details = HouseholdDetails(
                                residentialArea = houseDataObj.optStringOrNull("residentialArea"),
                                residentialAreaId = houseDataObj.optInt("residentialAreaId"),
                                otherResidentialArea = houseDataObj.optStringOrNull("other_residentialArea"),
                                houseType = houseDataObj.optStringOrNull("houseType"),
                                houseTypeId = houseDataObj.optInt("houseTypeId"),
                                otherHouseType = houseDataObj.optStringOrNull("other_houseType"),
                                isHouseOwned = houseDataObj.optStringOrNull("houseOwnerShip"),
                                isHouseOwnedId = houseDataObj.optInt("houseOwnerShipId"),
//                                isLandOwned = houseDataObj.optStringOrNull("landOwned") == "Yes",
//                                isLandIrrigated = houseDataObj.has("landIrregated") && houseDataObj.optStringOrNull("landIrregated") == "Yes",
//                                isLivestockOwned = houseDataObj.optStringOrNull("liveStockOwnerShip") == "Yes",
//                                street = houseDataObj.optStringOrNull("street"),
//                                colony = houseDataObj.optStringOrNull("colony"),
//                                pincode = houseDataObj.optInt("pincode"),
                            ),
                            amenities = HouseholdAmenities(
                                separateKitchen = houseDataObj.optStringOrNull("seperateKitchen"),
                                separateKitchenId = houseDataObj.optInt("seperateKitchenId"),
                                fuelUsed = houseDataObj.optStringOrNull("fuelUsed"),
                                fuelUsedId = houseDataObj.optInt("fuelUsedId"),
                                otherFuelUsed = houseDataObj.optStringOrNull("other_fuelUsed"),
                                sourceOfDrinkingWater = houseDataObj.optStringOrNull("sourceofDrinkingWater"),
                                sourceOfDrinkingWaterId = houseDataObj.optInt("sourceofDrinkingWaterId"),
                                otherSourceOfDrinkingWater = houseDataObj.optStringOrNull("other_sourceofDrinkingWater"),
                                availabilityOfElectricity = houseDataObj.optStringOrNull("avalabilityofElectricity"),
                                availabilityOfElectricityId = houseDataObj.optInt("avalabilityofElectricityId"),
                                otherAvailabilityOfElectricity = houseDataObj.optStringOrNull("other_avalabilityofElectricity"),
                                availabilityOfToilet = houseDataObj.optStringOrNull("availabilityofToilet"),
                                availabilityOfToiletId = houseDataObj.optInt("availabilityofToiletId"),
                                otherAvailabilityOfToilet = houseDataObj.optStringOrNull("other_availabilityofToilet"),
                            ),
//                                motorizedVehicle = houseDataObj.optStringOrNull("motarizedVehicle"),
//                                otherMotorizedVehicle = houseDataObj.optStringOrNull("other_motarizedVehicle"),
                            registrationType = houseDataObj.optStringOrNull("registrationType"),
                            locationRecord = LocationRecord(
                                country = preferenceDao.getLocationRecord()?.country ?: LocationEntity(1, "India"),
                                state = LocationEntity(
                                    benDataObj.getInt("stateId"),
                                    benDataObj.getString("stateName"),
                                ),
                                district = LocationEntity(
                                    benDataObj.getInt("districtid"),
                                    benDataObj.getString("districtname"),
                                ),
                                block = LocationEntity(
                                    benDataObj.getInt("blockId"),
                                    benDataObj.getString("blockName"),
                                ),
                                village = LocationEntity(
                                    benDataObj.getInt("villageId"),
                                    benDataObj.getString("villageName"),
                                ),
                            ),
                            gpsLatitude = hhGpsLatitude,
                            gpsLongitude = hhGpsLongitude,
                            digipin = hhDigipin,
                            gpsTimestamp = hhGpsTimestamp,
                            isGpsUnavailable = hhIsGpsUnavailable,
                            gpsUnavailableReason = hhGpsUnavailableReason,
                            serverUpdatedStatus = houseDataObj.optInt("serverUpdatedStatus"),
                            createdBy = houseDataObj.optStringOrNull("createdBy"),
                            createdTimeStamp = houseDataObj.optStringOrNull("createdDate")?.let { getLongFromDate(it) },
//                            updatedBy = houseDataObj.getString("other_houseType"),
//                            updatedTimeStamp = houseDataObj.getString("other_houseType"),
                            processed = "P",
                            isDraft = false,
                            isDeactivate =  if (houseDataObj.has("isDeactivate")) houseDataObj.getBoolean(
                                "isDeactivate"
                            ) else false
                        )

                        if (existingHh == null) {
                            result.add(serverHh)
                            addedHouseholdIds.add(hhId)
                        } else {
                            if (existingHh.processed == "P") {
                                result.add(serverHh)
                                addedHouseholdIds.add(hhId)
                            } else {
                                logSyncSkip("HOUSEHOLD", "PULL", "LOCAL_PENDING_EDITS_PROTECTED",
                                    "recordIndex" to i, "householdId" to hhId, "benId" to benId,
                                    "localProcessed" to existingHh.processed)
                            }
                        }
                    } catch (e: JSONException) {
                        logSyncSkip("HOUSEHOLD", "PULL", "INVALID_RECORD", "recordIndex" to i,
                            "householdId" to hhId, "benId" to benId, "errorType" to e.javaClass.simpleName)
                        Timber.e("Household skipped: ${jsonObject.getLong("houseoldId")} with error $e")
                    }

                }
            }
        }
        return result
    }


    suspend fun getBeneficiaryWithId(benRegId: Long): BenHealthDetails? {
        try {
            val response = tmcNetworkApiService
                .getBenHealthID(GetBenHealthIdRequest(benRegId, null))
            if (response.isSuccessful) {
                val responseBody = response.body()?.string()

                when (responseBody?.let { JSONObject(it).getInt("statusCode") }) {
                    200 -> {
                        val jsonObj = JSONObject(responseBody)
                        val data = jsonObj.getJSONObject("data").getJSONArray("BenHealthDetails")
                            .toString()
                        val bens = Gson().fromJson(data, Array<BenHealthDetails>::class.java)
                        return if (bens.isNotEmpty()) {
                            bens.last()
                        } else {
                            null
                        }
                    }

                    401,5000, 5002 -> {
                        if (JSONObject(responseBody).getString("errorMessage")
                                .contentEquals("Invalid login key or session is expired")
                        ) {
                            val user = preferenceDao.getLoggedInUser()!!
                            userRepo.refreshTokenTmc(user.userName, user.password)
                            return getBeneficiaryWithId(benRegId)
                        } else {
                            NetworkResult.Error(
                                0,
                                JSONObject(responseBody).getString("errorMessage")
                            )
                        }
                    }

                    else -> {
                        NetworkResult.Error(0, responseBody.toString())
                    }
                }
            }
        } catch (_: java.lang.Exception) {
        }
        return null
    }


    suspend fun sendOtp(mobileNo: String): SendOtpResponse? {
        try {
            var sendOtp = sendOtpRequest(mobileNo)
            val response = tmcNetworkApiService.sendOtp(sendOtp)
            if (response.isSuccessful) {
                val responseBody = response.body()?.string()
                when (responseBody?.let { JSONObject(it).getInt("statusCode") }) {
                    200 -> {
                        val jsonObj = JSONObject(responseBody)
                        val data = jsonObj.getJSONObject("data").toString()
                        val response = Gson().fromJson(data, SendOtpResponse::class.java)
                        Toast.makeText(context,"Otp sent successfully",Toast.LENGTH_SHORT).show()
                        return response
                    }

                    5000, 5002 -> {
                        if (JSONObject(responseBody).getString("errorMessage")
                                .contentEquals("Invalid login key or session is expired")
                        ) {
                            val user = preferenceDao.getLoggedInUser()!!
                            userRepo.refreshTokenTmc(user.userName, user.password)

                        } else {
                            NetworkResult.Error(
                                0,
                                JSONObject(responseBody).getString("errorMessage")
                            )
                        }
                    }

                    else -> {
                        NetworkResult.Error(0, responseBody.toString())
                    }
                }
            }
        } catch (_: java.lang.Exception) {
        }
        return null
    }
    suspend fun resendOtp(mobileNo: String): SendOtpResponse? {
        try {
            var sendOtp = sendOtpRequest(mobileNo)
            val response = tmcNetworkApiService.resendOtp(sendOtp)
            if (response.isSuccessful) {
                val responseBody = response.body()?.string()
                when (responseBody?.let { JSONObject(it).getInt("statusCode") }) {
                    200 -> {
                        val jsonObj = JSONObject(responseBody)
                        val data = jsonObj.getJSONObject("data").toString()
                        val response = Gson().fromJson(data, SendOtpResponse::class.java)
                        Toast.makeText(context,"Otp sent successfully",Toast.LENGTH_SHORT).show()
                        return response
                    }

                    5000, 5002 -> {
                        if (JSONObject(responseBody).getString("errorMessage")
                                .contentEquals("Invalid login key or session is expired")
                        ) {
                            val user = preferenceDao.getLoggedInUser()!!
                            userRepo.refreshTokenTmc(user.userName, user.password)

                        } else {
                            NetworkResult.Error(
                                0,
                                JSONObject(responseBody).getString("errorMessage")
                            )
                        }
                    }

                    else -> {
                        NetworkResult.Error(0, responseBody.toString())
                    }
                }
            }
        } catch (_: java.lang.Exception) {
        }
        return null
    }

    suspend fun verifyOtp(mobileNo: String,otp:Int): ValidateOtpResponse? {

        var validateOtp = ValidateOtpRequest(otp,mobileNo)
        val response = tmcNetworkApiService.validateOtp(validateOtp)
        if (response.isSuccessful) {
            val responseBody = response.body()?.string()
            when (responseBody?.let { JSONObject(it).getInt("statusCode") }) {
                200 -> {
                    val jsonObj = JSONObject(responseBody)
                    val data = jsonObj.getJSONObject("data").toString()
                    val myresponse = Gson().fromJson(responseBody, ValidateOtpResponse::class.java)
                    NewBenRegViewModel.isOtpVerified = true
                    return myresponse
                }

                5000, 5002 -> {
                    Toast.makeText(context,"Please enter valid OTP.",Toast.LENGTH_SHORT).show()

                }

                else -> {
                    NetworkResult.Error(0, responseBody.toString())
                }
            }
        }

        return null
    }



    suspend fun getMinBenId(): Long {
        return withContext(Dispatchers.IO) {
            benDao.getMinBenId() ?: 0L
        }
    }

    private fun getPlaceOfCurrentLivingIndex(code: String?): Int? {
        return when (code?.uppercase()) {
            "FOOTPATH" -> 1
            "RAILWAY_PLATFORM" -> 2
            "BUS_STATION" -> 3
            "UNDER_TREE" -> 4
            "TEMPLE" -> 5
            "MOSQUE" -> 6
            "CHURCH" -> 7
            "OTHER_PRAYING_PLACE" -> 8
            "EDUCATIONAL_INSTITUTION" -> 9
            "EKALAVYA_SCHOOL" -> 10
            "REHABILITATION_CENTRE" -> 11
            "ORPHANAGE" -> 12
            "OLD_AGE_HOME" -> 13
            "PRIVATE_HOSTEL" -> 14
            "GOVT_HOSTEL" -> 15
            "NGO_HOSTEL" -> 16
            "OTHER" -> 17
            else -> null
        }
    }
}
