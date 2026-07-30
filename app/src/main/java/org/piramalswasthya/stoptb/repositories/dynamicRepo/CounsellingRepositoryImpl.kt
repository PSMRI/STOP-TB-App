package org.piramalswasthya.stoptb.repositories.dynamicRepo

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.room.withTransaction
import org.piramalswasthya.stoptb.database.room.InAppDb
import org.piramalswasthya.stoptb.model.dynamicEntity.*
import org.piramalswasthya.stoptb.network.AmritApiService
import org.piramalswasthya.stoptb.helpers.dynamicMapper.PayloadBuilder
import org.piramalswasthya.stoptb.helpers.dynamicMapper.QuestionIdStrategy
import org.piramalswasthya.stoptb.helpers.dynamicMapper.storeFormSchemaInDb
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.ui.counselling_activity.FormType
import org.piramalswasthya.stoptb.ui.counselling_activity.SectionPhase
import timber.log.Timber
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class CounsellingRepositoryImpl @Inject constructor(
    private val db: InAppDb,
    @Named("gsonAmritApi") private val amritApiService: AmritApiService,
    private val preferenceDao: PreferenceDao
) : ICounsellingRepository {

    private val metadataDao = db.dynamicFormMetadataDao()
    private val responseDao = db.counsellingFormResponseDao()
    override suspend fun getFormDefinition(formType: FormType): CompleteFormDefinition? {
        return metadataDao.getFormDefinition(formType)
    }

    override suspend fun getSectionsByPhase(
        formType: FormType,
        phase: SectionPhase
    ): List<FormSectionWithQuestions> {
        val versionId = metadataDao.getActiveVersionId(formType) ?: return emptyList()
        return metadataDao.getSectionsByPhase(versionId, phase.value)
    }

    override suspend fun downloadAndStoreAllForms(): Boolean {
        return try {
            val jwt = preferenceDao.getJWTAmritToken()
            val authHeader = jwt ?: run {
                Timber.w("downloadAndStoreAllForms: JWT token is null, API call will likely fail")
                return false
            }
            val response = amritApiService.getAllForms(authHeader)
            if (response.isSuccessful) {
                val apiSchemas = response.body()?.data ?: return false
                db.withTransaction {
                    val nullQuestions = metadataDao.getQuestionsWithNullServerIdCount()
                    val nullOptions = metadataDao.getOptionsWithNullServerIdCount()
                    val forceRefresh = nullQuestions > 0 || nullOptions > 0
                    if (forceRefresh) {
                        Timber.d("downloadAndStoreAllForms: Detected null server ID columns in metadata database. Forcing schema updates.")
                    }

                    apiSchemas.forEach { apiSchema ->
                        val formId = apiSchema.formId.toIntOrNull() ?: 0
                        val activeVersion = metadataDao.getActiveVersionNumber(formId)
                        if (activeVersion == null || apiSchema.versionNumber > activeVersion || forceRefresh) {
                            storeFormSchemaInDb(apiSchema)
                        }
                    }
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "downloadAndStoreAllForms failed")
            false
        }
    }

    private val gson = com.google.gson.Gson()

    private suspend fun storeFormSchemaInDb(apiSchema: FormSchemaDto) {
        storeFormSchemaInDb(
            metadataDao = metadataDao,
            gson = gson,
            apiSchema = apiSchema,
            idStrategy = QuestionIdStrategy.HASH_BASED,
            wipeExistingVersions = true
        )
    }


    override suspend fun getOrCreateDraft(beneficiaryId: Long, formVersionId: Int): CompleteFormResponse {
        return db.withTransaction {
            val existing = responseDao.getFormResponseForBeneficiary(beneficiaryId, formVersionId)
            if (existing != null) {
                existing
            } else {
                val newForm = FormResponseEntity(
                    beneficiaryId = beneficiaryId,
                    formVersionId = formVersionId,
                    status = "DRAFT",
                    lastVisitedSectionId = null,
                    syncStatus = "UNSYNCED"
                )
                val responseId = responseDao.insertFormResponse(newForm)

                val formDef = metadataDao.getFormDefinitionByVersionId(formVersionId)
                val activeVersion = formDef?.versions?.find { it.version.versionId == formVersionId }
                val sections = activeVersion?.sections ?: emptyList()

                val sectionResponses = sections.map {
                    SectionResponseEntity(
                        formResponseId = responseId,
                        sectionId = it.section.sectionId
                    )
                }
                responseDao.insertSectionResponses(sectionResponses)

                responseDao.getFormResponseForBeneficiary(beneficiaryId, formVersionId)!!
            }
        }
    }

    override suspend fun saveDraftSection(
        responseId: Long,
        sectionId: Int,
        nextSectionId: Int?,
        answers: List<QuestionResponseEntity>
    ) {
        db.withTransaction {
            val formResponseWithDetails = responseDao.getFormResponseById(responseId)
                ?: return@withTransaction

            val sectionResponse = formResponseWithDetails.sectionResponses.find { it.sectionResponse.sectionId == sectionId }
                ?: return@withTransaction

            responseDao.deleteQuestionResponsesForSection(sectionResponse.sectionResponse.sectionResponseId)

            val mappedAnswers = answers.map { it.copy(sectionResponseId = sectionResponse.sectionResponse.sectionResponseId) }
            responseDao.insertQuestionResponses(mappedAnswers)
            Timber.d("saveDraftSection: responseId=$responseId, sectionId=$sectionId, savedAnswers=${mappedAnswers.size}")

            // Mark this section pending re-submission via submitBulk, whether it's the first
            // save or a re-edit of an already-synced section (Navigate Up + edit + Next again).
            responseDao.updateSectionResponse(
                sectionResponse.sectionResponse.copy(
                    completedAt = null,
                    updatedAt = System.currentTimeMillis()
                )
            )

            responseDao.updateFormResponse(
                formResponseWithDetails.formResponse.copy(
                    lastVisitedSectionId = nextSectionId ?: sectionId,
                    syncStatus = "UNSYNCED",
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private suspend fun submitSectionWithPhase(
        responseId: Long,
        answers: List<QuestionResponseEntity>,
        phase: String,
        status: String
    ) {
        db.withTransaction {
            val formResponseWithDetails = responseDao.getFormResponseById(responseId)
                ?: return@withTransaction

            val formVersionId = formResponseWithDetails.formResponse.formVersionId
            val formDef = metadataDao.getFormDefinitionByVersionId(formVersionId)
                ?: return@withTransaction

            val activeVersion = formDef.versions.find { it.version.versionId == formVersionId }
                ?: return@withTransaction

            val sectionDef = when (phase) {
                "PRE_SUBMIT" -> activeVersion.sections
                    .filter { it.section.sectionPhase == "PRE_SUBMIT" }
                    .maxByOrNull { it.section.sectionOrder }
                "GENERAL_INFO" -> activeVersion.sections
                    .find { it.section.sectionPhase == "GENERAL_INFO" }
                else -> activeVersion.sections
                    .find { it.section.sectionPhase == "POST_SUBMIT" }
            } ?: activeVersion.sections.maxByOrNull { it.section.sectionOrder }
            ?: return@withTransaction

            val sectionResponse = formResponseWithDetails.sectionResponses.find { it.sectionResponse.sectionId == sectionDef.section.sectionId }
                ?: return@withTransaction

            val secId = sectionResponse.sectionResponse.sectionResponseId
            responseDao.deleteQuestionResponsesForSection(secId)

            val mappedAnswers = answers.map { it.copy(sectionResponseId = secId) }
            responseDao.insertQuestionResponses(mappedAnswers)
            Timber.d("submitSectionWithPhase: responseId=$responseId, phase=$phase, sectionId=${sectionDef.section.sectionId}, savedAnswers=${mappedAnswers.size}")

            responseDao.updateFormResponse(
                formResponseWithDetails.formResponse.copy(
                    status = status,
                    syncStatus = "UNSYNCED",
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    override suspend fun submitSectionE(responseId: Long, answers: List<QuestionResponseEntity>) {
        submitSectionWithPhase(responseId, answers, "PRE_SUBMIT", "SUBMITTED")
    }

    override suspend fun submitSectionF(responseId: Long, answers: List<QuestionResponseEntity>) {
        submitSectionWithPhase(responseId, answers, "POST_SUBMIT", "COMPLETED")
    }

    override suspend fun submitSectionGeneralInfo(responseId: Long, answers: List<QuestionResponseEntity>) {
        submitSectionWithPhase(responseId, answers, "GENERAL_INFO", "REFUSED")
    }

    override suspend fun submitSectionBulk(responseId: Long, sectionId: Int): Boolean {
        val resp = responseDao.getFormResponseById(responseId) ?: return true
        val sectionResponse = resp.sectionResponses.find { it.sectionResponse.sectionId == sectionId }
            ?: return true

        val formDef = metadataDao.getFormDefinitionByVersionId(resp.formResponse.formVersionId)
        val officerId = preferenceDao.getLoggedInUser()?.userId?.toLong() ?: DEFAULT_OFFICER_ID
        val payload = PayloadBuilder.buildBulkPayload(resp, formDef, officerId, sectionIdFilter = sectionId)

        if (payload.sections.isEmpty()) {
            Timber.d("submitSectionBulk: sectionId=$sectionId has no answers, skipping API call")
            return true
        }

        val jwt = preferenceDao.getJWTAmritToken()
        val authHeader = jwt ?: ""

        val success = try {
            val apiResponse = amritApiService.submitBulkCounselling(authHeader, listOf(payload))
            val statusCode = apiResponse.code()
            Timber.d("submitSectionBulk: sectionId=$sectionId, httpStatus=$statusCode")

            if (statusCode == 200) {
                val responseString = apiResponse.body()?.string()
                if (responseString != null) {
                    val jsonObj = org.json.JSONObject(responseString)
                    val isSuccess = jsonObj.optBoolean("success", false)
                    if (isSuccess) {
                        Timber.d("submitSectionBulk: sectionId=$sectionId success")
                        true
                    } else {
                        Timber.e("submitSectionBulk: sectionId=$sectionId failed: success=false")
                        false
                    }
                } else {
                    Timber.e("submitSectionBulk: sectionId=$sectionId failed: body is null")
                    false
                }
            } else {
                Timber.e("submitSectionBulk: sectionId=$sectionId failed: status=$statusCode")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "submitSectionBulk: sectionId=$sectionId error")
            false
        }

        if (success) {
            responseDao.updateSectionResponse(
                sectionResponse.sectionResponse.copy(
                    completedAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

        return success
    }

    private data class SectionClassification(
        val finalSectionId: Int?,
        // Non-GENERAL_INFO sections other than finalSectionId, ordered by sectionOrder. These
        // must already be synced via submitBulk before finalSectionId's complete call can fire.
        val nonFinalSectionIdsInOrder: List<Int>
    )

    // GENERAL_INFO (refusal) is a separate, out-of-scope flow left on its own path in
    // syncUnsyncedRecords — this only classifies the main stepper sections.
    private fun classifySections(sections: List<FormSectionWithQuestions>): SectionClassification {
        val stepperSections = sections
            .filter { it.section.sectionPhase != "GENERAL_INFO" }
            .sortedBy { it.section.sectionOrder }
        val finalSectionId = stepperSections.lastOrNull()?.section?.sectionId
        val nonFinalSectionIdsInOrder = stepperSections
            .filter { it.section.sectionId != finalSectionId }
            .map { it.section.sectionId }
        return SectionClassification(finalSectionId, nonFinalSectionIdsInOrder)
    }

    override suspend fun syncUnsyncedRecords(): Boolean {
        Timber.d("syncUnsyncedRecords: querying all local form responses for debugging...")
        val allResponses = responseDao.getAllFormResponses()
        val allDetails = allResponses.map {
            "Response[id=${it.formResponse.responseId}, benId=${it.formResponse.beneficiaryId}, status=${it.formResponse.status}, syncStatus=${it.formResponse.syncStatus}, sectionsCount=${it.sectionResponses.size}]"
        }
        Timber.d("syncUnsyncedRecords: ALL records currently in DB: $allDetails")

        Timber.d("syncUnsyncedRecords: querying unsynced records...")
        val unsynced = responseDao.getUnsyncedFormResponsesForTypes(
            listOf(FormType.TB_COUNSELLING_V2.name, FormType.TB_COUNSELLING.name)
        )
        Timber.d("syncUnsyncedRecords: found ${unsynced.size} unsynced records")
        if (unsynced.isNotEmpty()) {
            val unsyncedDetails = unsynced.map {
                "UnsyncedResponse[id=${it.formResponse.responseId}, benId=${it.formResponse.beneficiaryId}, status=${it.formResponse.status}]"
            }
            Timber.d("syncUnsyncedRecords: unsynced records details: $unsyncedDetails")
        }
        if (unsynced.isEmpty()) return true

        val officerId = preferenceDao.getLoggedInUser()?.userId?.toLong() ?: DEFAULT_OFFICER_ID
        val jwt = preferenceDao.getJWTAmritToken()
        val authHeader = jwt ?: ""
        var allSuccess = true

        for (resp in unsynced) {
            val responseId = resp.formResponse.responseId
            val formDef = metadataDao.getFormDefinitionByVersionId(resp.formResponse.formVersionId)

            // General Info refusal now pushes via submitBulk (not complete), reusing the same
            // single-section submitSectionBulk() used by the main stepper's non-final sections.
            if (resp.formResponse.status == "REFUSED") {
                val activeVersion = formDef?.versions?.find { it.version.versionId == resp.formResponse.formVersionId }
                val generalInfoSectionId = activeVersion?.sections
                    ?.find { it.section.sectionPhase == "GENERAL_INFO" }?.section?.sectionId

                val recordSuccess = if (generalInfoSectionId != null) {
                    submitSectionBulk(responseId, generalInfoSectionId)
                } else {
                    Timber.e("syncUnsyncedRecords: no GENERAL_INFO section found for responseId=$responseId")
                    false
                }

                if (recordSuccess) {
                    responseDao.updateFormResponse(
                        resp.formResponse.copy(syncStatus = "SYNCED", syncedAt = System.currentTimeMillis())
                    )
                } else {
                    allSuccess = false
                }
                continue
            }

            val activeVersion = formDef?.versions?.find { it.version.versionId == resp.formResponse.formVersionId }
            val classification = classifySections(activeVersion?.sections ?: emptyList())
            val finalSectionId = classification.finalSectionId

            var nonFinalSuccess = true
            for (sectionId in classification.nonFinalSectionIdsInOrder) {
                val sectionResponse = resp.sectionResponses.find { it.sectionResponse.sectionId == sectionId }
                val hasAnswers = sectionResponse?.questionResponses?.isNotEmpty() == true
                val alreadySynced = sectionResponse?.sectionResponse?.completedAt != null
                if (!hasAnswers || alreadySynced) continue

                Timber.d("syncUnsyncedRecords: catching up sectionId=$sectionId for responseId=$responseId")
                if (!submitSectionBulk(responseId, sectionId)) {
                    nonFinalSuccess = false
                    break
                }
            }

            if (!nonFinalSuccess) {
                allSuccess = false
                continue
            }

            val finalSectionReached = resp.formResponse.status != "DRAFT"
            if (!finalSectionReached || finalSectionId == null || resp.formResponse.syncStatus == "SYNCED") {
                continue
            }

            val payload = PayloadBuilder.buildBulkPayload(resp, formDef, officerId, sectionIdFilter = finalSectionId)
            val recordSuccess = executeCompleteCounselling(authHeader, payload, "responseId=$responseId, sectionId=$finalSectionId")

            if (recordSuccess) {
                responseDao.updateFormResponse(
                    resp.formResponse.copy(syncStatus = "SYNCED", syncedAt = System.currentTimeMillis())
                )

                val finalSectionResponse = resp.sectionResponses.find { it.sectionResponse.sectionId == finalSectionId }
                if (finalSectionResponse != null) {
                    responseDao.updateSectionResponse(
                        finalSectionResponse.sectionResponse.copy(completedAt = System.currentTimeMillis())
                    )
                }
            } else {
                allSuccess = false
            }
        }

        return allSuccess
    }

    private suspend fun executeCompleteCounselling(
        authHeader: String,
        payload: CounsellingBulkSubmitRequest,
        logLabel: String
    ): Boolean {
        return try {
            try {
                val jsonPayload = com.google.gson.Gson().toJson(payload)
                Timber.d("Amrit push complete payload ($logLabel): $jsonPayload")
            } catch (e: Exception) {
                Timber.e(e, "Failed to serialize complete payload to JSON for logging ($logLabel)")
            }

            val apiResponse = amritApiService.completeCounselling(authHeader, payload)
            val statusCode = apiResponse.code()
            Timber.d("Amrit push complete response ($logLabel): httpStatus=$statusCode")

            if (statusCode == 200) {
                val responseString: String? = apiResponse.body()?.string()
                if (responseString != null) {
                    val jsonObj = org.json.JSONObject(responseString)
                    val isSuccess = jsonObj.optBoolean("success", false)
                    if (isSuccess) {
                        Timber.d("Amrit push complete success ($logLabel)")
                        true
                    } else {
                        Timber.e("Amrit push complete failed ($logLabel): success=false")
                        false
                    }
                } else {
                    Timber.e("Amrit push complete failed ($logLabel): body is null")
                    false
                }
            } else {
                Timber.e("Amrit push complete failed ($logLabel): status=$statusCode")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Amrit push complete error ($logLabel)")
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun fetchAndStoreCounsellingResponse(
        beneficiaryId: Long,
        formUuid: String
    ): Boolean {
        try {
            val formDef = metadataDao.getFormDefinition(FormType.TB_COUNSELLING_V2) ?: return false
            val activeVersion = formDef.versions.find { it.version.isActive }
                ?: formDef.versions.maxByOrNull { it.version.versionNumber }
                ?: return false
            val formVersionId = activeVersion.version.versionId

            val localResponse = responseDao.getFormResponseForBeneficiary(beneficiaryId, formVersionId)
            val hasLocalAnswers = localResponse?.sectionResponses?.any { it.questionResponses.isNotEmpty() } == true
            if (localResponse != null && localResponse.formResponse.syncStatus == "SYNCED" &&
                (localResponse.formResponse.status == "SUBMITTED" || localResponse.formResponse.status == "COMPLETE" || localResponse.formResponse.status == "COMPLETED" || localResponse.formResponse.status == "REFUSED") &&
                hasLocalAnswers
            ) {
                Timber.d("fetchAndStoreCounsellingResponse: Synced response with answers already exists locally. Skipping fetch to preserve data.")
                return true
            }

            val jwt = preferenceDao.getJWTAmritToken() ?: return false
            val response = amritApiService.getBeneficiaryFormResponses(jwt, beneficiaryId, formUuid)
            if (!response.isSuccessful) return false
            val apiResponses = response.body()?.data
            if (apiResponses.isNullOrEmpty()) return false

            db.withTransaction {
                // Preserve any locally edited (UNSYNCED) responses — do not overwrite them
                // with server data, as that would permanently discard unsaved user edits.
                val unsyncedLocal = responseDao.getUnsyncedResponseForBeneficiary(beneficiaryId, formVersionId)
                if (unsyncedLocal != null) return@withTransaction

                val existingCreatedAt = responseDao.getFormResponseForBeneficiary(beneficiaryId, formVersionId)
                    ?.formResponse?.createdAt

                responseDao.deleteFormResponseForBeneficiary(beneficiaryId, formVersionId)

                val apiResponse = apiResponses.first()

                val serverDate: Long? = try {
                    apiResponse.submittedAt?.let { OffsetDateTime.parse(it).toInstant().toEpochMilli() }
                } catch (e: Exception) {
                    Timber.w(e, "fetchAndStoreCounsellingResponse: failed to parse submittedAt=${apiResponse.submittedAt}")
                    null
                }

                val questionsMap = activeVersion.sections
                    .flatMap { it.questions }
                    .filter { it.question.serverQuestionId != null }
                    .associateBy { it.question.serverQuestionId!! }

                val optionsMap = mutableMapOf<Pair<Int, Int>, Int>()
                activeVersion.sections.forEach { sec ->
                    sec.questions.forEach { qDetails ->
                        val serverQId = qDetails.question.serverQuestionId
                        if (serverQId != null) {
                            qDetails.options.forEach { optDetails ->
                                val serverOptId = optDetails.option.serverOptionId
                                if (serverOptId != null) {
                                    optionsMap[Pair(serverQId, serverOptId)] = optDetails.option.optionId
                                }
                            }
                        }
                    }
                }

                val formResponse = FormResponseEntity(
                    beneficiaryId = beneficiaryId,
                    formVersionId = activeVersion.version.versionId,
                    status = "SUBMITTED",
                    lastVisitedSectionId = null,
                    syncStatus = "SYNCED",
                    syncedAt = System.currentTimeMillis(),
                    createdAt = serverDate ?: existingCreatedAt ?: System.currentTimeMillis()
                )
                val responseId = responseDao.insertFormResponse(formResponse)

                val sectionResponses = activeVersion.sections.map {
                    SectionResponseEntity(
                        formResponseId = responseId,
                        sectionId = it.section.sectionId
                    )
                }
                responseDao.insertSectionResponses(sectionResponses)

                val insertedSections = responseDao.getFormResponseById(responseId)?.sectionResponses ?: emptyList()
                val sectionIdToResponseIdMap = insertedSections.associate {
                    it.sectionResponse.sectionId to it.sectionResponse.sectionResponseId
                }

                val questionResponsesToInsert = mutableListOf<QuestionResponseEntity>()
                var hasPostSubmitAnswers = false
                val answeredSectionIds = mutableSetOf<Int>()

                apiResponse.sections.forEach { apiSec ->
                    val sectionId = apiSec.sectionId
                    val sectionDef = activeVersion.sections.find { it.section.sectionId == sectionId }
                    if (sectionDef != null) {
                        val sectionResponseId = sectionIdToResponseIdMap[sectionId]
                        if (sectionResponseId != null) {
                            if (apiSec.answers.isNotEmpty()) {
                                answeredSectionIds.add(sectionId)
                            }
                            if (sectionDef.section.sectionPhase == "POST_SUBMIT" && apiSec.answers.isNotEmpty()) {
                                hasPostSubmitAnswers = true
                            }

                            apiSec.answers.forEach { apiAns ->
                                val serverQId = apiAns.questionId
                                val qDetails = questionsMap[serverQId]
                                if (qDetails != null) {
                                    val qId = qDetails.question.questionId

                                    val serverOptId = apiAns.optionId
                                    val localOptId = if (serverOptId != null) {
                                        optionsMap[Pair(serverQId, serverOptId)]
                                    } else {
                                        null
                                    }

                                    questionResponsesToInsert.add(
                                        QuestionResponseEntity(
                                            sectionResponseId = sectionResponseId,
                                            questionId = qId,
                                            optionId = localOptId,
                                            answerText = apiAns.answerText
                                        )
                                    )
                                } else {
                                    Timber.w("fetchAndStoreCounsellingResponse: No local question found for serverQuestionId=$serverQId")
                                }
                            }
                        }
                    } else {
                        Timber.w("fetchAndStoreCounsellingResponse: No local section found for serverSectionId=$sectionId")
                    }
                }

                if (questionResponsesToInsert.isNotEmpty()) {
                    responseDao.insertQuestionResponses(questionResponsesToInsert)
                }

                insertedSections
                    .filter { it.sectionResponse.sectionId in answeredSectionIds }
                    .forEach { sr ->
                        responseDao.updateSectionResponse(
                            sr.sectionResponse.copy(completedAt = serverDate ?: System.currentTimeMillis())
                        )
                    }

                val preSubmitSectionIds = activeVersion.sections
                    .filter { it.section.sectionPhase == "PRE_SUBMIT" }
                    .map { it.section.sectionId }
                val allPreSubmitAnswered = preSubmitSectionIds.isNotEmpty() &&
                        preSubmitSectionIds.all { it in answeredSectionIds }

                val finalStatus = when {
                    apiResponse.status?.uppercase() == "REFUSED" -> "REFUSED"
                    hasPostSubmitAnswers || apiResponse.status?.uppercase() == "COMPLETE" || apiResponse.status?.uppercase() == "COMPLETED" -> "COMPLETE"

                    apiResponse.status?.uppercase() == "SUBMITTED" || allPreSubmitAnswered -> "SUBMITTED"
                    else -> "DRAFT"
                }

                responseDao.updateFormResponse(
                    formResponse.copy(responseId = responseId, status = finalStatus)
                )
            }
            return true
        } catch (e: Exception) {
            Timber.e(e, "fetchAndStoreCounsellingResponse failed for benId=$beneficiaryId")
            return false
        }
    }    override suspend fun fetchAndStoreCompletedBeneficiaries(): List<CompletedBeneficiaryStatus>? {
        try {
            val jwt = preferenceDao.getJWTAmritToken()
            val authHeader = jwt ?: run {
                Timber.w("fetchAndStoreCompletedBeneficiaries: JWT token is null")
                return null
            }
            val user = preferenceDao.getLoggedInUser() ?: run {
                Timber.w("fetchAndStoreCompletedBeneficiaries: Logged-in user is null")
                return null
            }
            val villageId = preferenceDao.getLocationRecord()?.village?.id ?: run {
                Timber.w("fetchAndStoreCompletedBeneficiaries: LocationRecord/Village is null")
                return null
            }
            val providerServiceMapId = user.serviceMapId
            val response = amritApiService.getCompletedBeneficiaries(
                authHeader = authHeader,
                formType = FormType.TB_COUNSELLING_V2.name,
                villageId = villageId,
                providerServiceMapId = providerServiceMapId
            )
            if (response.isSuccessful) {
                val statuses = response.body()?.data ?: return null

                var formDef = metadataDao.getFormDefinition(FormType.TB_COUNSELLING_V2)
                if (formDef == null) {
                    downloadAndStoreAllForms()
                    formDef = metadataDao.getFormDefinition(FormType.TB_COUNSELLING_V2)
                }
                val activeVersion = formDef?.versions?.find { it.version.isActive }
                    ?: formDef?.versions?.maxByOrNull { it.version.versionNumber }
                val versionId = activeVersion?.version?.versionId

                if (versionId != null) {
                    val statusesByBenId = statuses.groupBy { it.beneficiaryId }
                    db.withTransaction {
                        for ((benId, items) in statusesByBenId) {
                            val item = items.find { it.refused } ?: items.first()
                            Timber.d("fetchAndStoreCompletedBeneficiaries: benId=$benId, entries=${items.size}, chosen refused=${item.refused}, sectionsFilled=${item.sectionsFilled}, totalSections=${item.totalSections}")
                            // Untouched beneficiaries (sectionsFilled == 0, not refused) keep the
                            // current behavior of having no Room row at all unless one already exists.
                            if (!item.refused && item.sectionsFilled == 0) {
                                continue
                            }
                            val newStatus = when {
                                item.refused -> "REFUSED"
                                item.totalSections > 0 && item.sectionsFilled == item.totalSections -> "COMPLETE"
                                else -> null // in-progress: keep whatever local status already exists
                            }
                            val existing = responseDao.getFormResponseForBeneficiary(benId, versionId)
                            if (existing == null) {
                                responseDao.insertFormResponse(
                                    FormResponseEntity(
                                        beneficiaryId = benId,
                                        formVersionId = versionId,
                                        status = newStatus ?: "DRAFT",
                                        syncStatus = "SYNCED",
                                        syncedAt = System.currentTimeMillis(),
                                        lastVisitedSectionId = null,
                                        sectionsFilled = item.sectionsFilled,
                                        totalSections = item.totalSections
                                    )
                                )
                            } else {
                                val currentFr = existing.formResponse
                                if (newStatus == null ||
                                    currentFr.status == newStatus ||
                                    (newStatus == "COMPLETE" && currentFr.status == "COMPLETED")
//                                    // Never let this list-level reconciliation downgrade an
//                                    // already-known REFUSED status — it's a terminal state
//                                    // established via the more reliable per-section detail fetch
//                                    // (fetchAndStoreCounsellingResponse), and this endpoint's
//                                    // own refused flag can be wrong for the same beneficiary.
//                                    (currentFr.status == "REFUSED" && newStatus != "REFUSED")
                                ) {
                                    responseDao.updateFormResponse(
                                        currentFr.copy(
                                            sectionsFilled = item.sectionsFilled,
                                            totalSections = item.totalSections
                                        )
                                    )
                                } else {
                                    responseDao.updateFormResponse(
                                        currentFr.copy(
                                            status = newStatus,
                                            syncStatus = "SYNCED",
                                            syncedAt = System.currentTimeMillis(),
                                            sectionsFilled = item.sectionsFilled,
                                            totalSections = item.totalSections
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                return statuses
            } else {
                Timber.w("fetchAndStoreCompletedBeneficiaries failed: status code ${response.code()}")
                return null
            }
        } catch (e: Exception) {
            Timber.e(e, "fetchAndStoreCompletedBeneficiaries exception")
            return null
        }
    }

    override suspend fun revertFormStatus(responseId: Long, status: String) {
        db.withTransaction {
            val formResponseWithDetails = responseDao.getFormResponseById(responseId)
            if (formResponseWithDetails != null) {
                responseDao.updateFormResponse(
                    formResponseWithDetails.formResponse.copy(
                        status = status,
                        syncStatus = "UNSYNCED",
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    override suspend fun getLocalPreSubmitFilledCounts(): Map<Long, Int> {
        val raw = responseDao.getLocalPreSubmitFilledCounts()
        Timber.d("getLocalPreSubmitFilledCounts: ${raw.map { "benId=${it.beneficiaryId} filledCount=${it.filledCount}" }}")
        return raw.associate { it.beneficiaryId to it.filledCount }
    }

    companion object {
        private const val DEFAULT_OFFICER_ID = 501L
    }
}
