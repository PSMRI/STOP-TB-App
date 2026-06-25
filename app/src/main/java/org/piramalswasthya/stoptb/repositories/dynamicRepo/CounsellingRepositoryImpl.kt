package org.piramalswasthya.stoptb.repositories.dynamicRepo

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.piramalswasthya.stoptb.database.room.InAppDb
import org.piramalswasthya.stoptb.model.dynamicEntity.*
import org.piramalswasthya.stoptb.network.AmritApiService
import org.piramalswasthya.stoptb.helpers.dynamicMapper.PayloadBuilder
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.ui.counselling_activity.FormType
import org.piramalswasthya.stoptb.ui.counselling_activity.SectionPhase
import timber.log.Timber
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
                    apiSchemas.forEach { apiSchema ->
                        val formId = apiSchema.formId.toIntOrNull() ?: 0
                        val activeVersion = metadataDao.getActiveVersionNumber(formId)
                        if (activeVersion == null || apiSchema.versionNumber > activeVersion) {
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

    private suspend fun storeFormSchemaInDb(apiSchema: FormSchemaDto) {
        val formId = apiSchema.formId.toIntOrNull() ?: 0
        val formUuid = apiSchema.formUuid ?: "FORM_${formId}"
        val followUpDelayDays =  apiSchema.followUpDelayDays
        Timber.d("storeFormSchemaInDb: Inserting formId = $formId, formUuid = $formUuid, formName = ${apiSchema.formName}")
        val formEntity = DynamicFormEntity(
            formId = formId,
            formUuid = formUuid,
            formName = apiSchema.formName,
            formType = apiSchema.formType ?: "",
            followUpDelayDays = followUpDelayDays
        )
        metadataDao.insertForm(formEntity)

        val versionId = formId * 1000 + apiSchema.versionNumber
        Timber.d("storeFormSchemaInDb: Inserting versionId = $versionId, versionNumber = ${apiSchema.versionNumber}")
        val versionEntity = FormVersionEntity(
            versionId = versionId,
            formId = formId,
            versionNumber = apiSchema.versionNumber,
            isActive = apiSchema.isActive
        )
        metadataDao.insertVersion(versionEntity)

        val sectionsToInsert = mutableListOf<FormSectionEntity>()
        val questionsToInsert = mutableListOf<SectionQuestionEntity>()
        val optionsToInsert = mutableListOf<QuestionOptionEntity>()
        val conditionsToInsert = mutableListOf<OptionConditionEntity>()
        val validationsToInsert = mutableListOf<QuestionValidationEntity>()

        // Build questionId -> fieldId map for mapping condition target IDs
        val questionIdToFieldIdMap = mutableMapOf<Int, String>()
        apiSchema.sections.forEach { sectionDto ->
            sectionDto.questions.forEach { questionDto ->
                questionDto.questionId?.let { qId ->
                    questionIdToFieldIdMap[qId] = questionDto.fieldId
                }
            }
        }

        Timber.d("storeFormSchemaInDb: Mapping ${apiSchema.sections.size} sections...")
        apiSchema.sections.forEach { sectionDto ->
            val sectionIdInt = sectionDto.sectionId.toIntOrNull() ?: 0
            val sectionEntity = FormSectionEntity(
                sectionId = sectionIdInt,
                versionId = versionId,
                sectionName = sectionDto.sectionName,
                sectionNameHindi = sectionDto.sectionNameHindi,
                sectionOrder = sectionDto.displayOrder ?: 0,
                sectionPhase = sectionDto.sectionPhase ?: "",
                sectionUuid = sectionDto.sectionUuid
            )
            sectionsToInsert.add(sectionEntity)

            Timber.d("storeFormSchemaInDb: Mapping ${sectionDto.questions.size} questions for section $sectionIdInt...")
            sectionDto.questions.forEach { questionDto ->
                val questionIdInt = questionDto.fieldId.hashCode()
                val questionEntity = SectionQuestionEntity(
                    questionId = questionIdInt,
                    sectionId = sectionIdInt,
                    questionText = questionDto.label,
                    questionTextHindi = questionDto.labelHindi,
                    questionType = questionDto.type,
                    questionOrder = questionDto.displayOrder ?: 0,
                    isRequired = questionDto.isMandatory,
                    questionUuid = questionDto.fieldId,
                    serverQuestionId = questionDto.questionId
                )
                questionsToInsert.add(questionEntity)

                val optionItems = questionDto.getOptionItems()
                Timber.d("storeFormSchemaInDb: Mapping ${optionItems.size} options for question $questionIdInt...")
                optionItems.forEach { optionDto ->
                    val optionIdInt = (questionDto.fieldId + "_" + optionDto.optionValue).hashCode()
                    val optionEntity = QuestionOptionEntity(
                        optionId = optionIdInt,
                        questionId = questionIdInt,
                        optionText = optionDto.optionLabel,
                        optionTextHindi = optionDto.optionLabelHindi,
                        optionValue = optionDto.optionValue,
                        optionOrder = optionDto.displayOrder,
                        serverOptionId = optionDto.optionId
                    )
                    optionsToInsert.add(optionEntity)

                    optionDto.conditions.forEach conditionLoop@{ conditionDto ->
                        val targetQId = conditionDto.targetQuestionId ?: return@conditionLoop
                        val mappedTargetQId = conditionDto.targetQuestionUuid?.hashCode()
                            ?: questionIdToFieldIdMap[targetQId]?.hashCode()
                            ?: run {
                                Timber.w("conditionLoop: cannot map targetQId=$targetQId for option ${optionDto.optionValue}, skipping condition")
                                return@conditionLoop
                            }
                        val conditionIdInt = (questionDto.fieldId + "_" + optionDto.optionValue + "_" + targetQId).hashCode()
                        val conditionEntity = OptionConditionEntity(
                            conditionId = conditionIdInt,
                            optionId = optionIdInt,
                            targetQuestionId = mappedTargetQId,
                            actionType = conditionDto.actionType,
                            isFulfilledValue = true
                        )
                        conditionsToInsert.add(conditionEntity)
                    }
                }

                questionDto.validations.forEach { validationDto ->
                    val validationIdInt = (questionDto.fieldId + "_" + validationDto.validationType).hashCode()
                    val validationEntity = QuestionValidationEntity(
                        validationId = validationIdInt,
                        questionId = questionIdInt,
                        validationType = validationDto.validationType,
                        validationValue = validationDto.validationParam,
                        errorMessage = validationDto.errorMessage
                    )
                    validationsToInsert.add(validationEntity)
                }
            }
        }

        Timber.d("storeFormSchemaInDb: Inserting sections: ${sectionsToInsert.size}, questions: ${questionsToInsert.size}, options: ${optionsToInsert.size}, conditions: ${conditionsToInsert.size}, validations: ${validationsToInsert.size}")
        if (sectionsToInsert.isNotEmpty()) metadataDao.insertSections(sectionsToInsert)
        if (questionsToInsert.isNotEmpty()) metadataDao.insertQuestions(questionsToInsert)
        if (optionsToInsert.isNotEmpty()) metadataDao.insertOptions(optionsToInsert)
        if (conditionsToInsert.isNotEmpty()) metadataDao.insertConditions(conditionsToInsert)
        if (validationsToInsert.isNotEmpty()) metadataDao.insertValidations(validationsToInsert)
    }


    override suspend fun getOrCreateDraft(beneficiaryId: Long, formVersionId: Int): CompleteFormResponse {
        return db.withTransaction {
            val existing = responseDao.getFormResponseForBeneficiary(beneficiaryId)
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

                responseDao.getFormResponseForBeneficiary(beneficiaryId)!!
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

            val sectionDef = if (phase == "PRE_SUBMIT") {
                activeVersion.sections
                    .filter { it.section.sectionPhase == "PRE_SUBMIT" }
                    .maxByOrNull { it.section.sectionOrder }
            } else {
                activeVersion.sections
                    .find { it.section.sectionPhase == "POST_SUBMIT" }
            } ?: activeVersion.sections.maxByOrNull { it.section.sectionOrder }
              ?: return@withTransaction

            val sectionResponse = formResponseWithDetails.sectionResponses.find { it.sectionResponse.sectionId == sectionDef.section.sectionId }
                ?: return@withTransaction

            val secId = sectionResponse.sectionResponse.sectionResponseId
            responseDao.deleteQuestionResponsesForSection(secId)

            val mappedAnswers = answers.map { it.copy(sectionResponseId = secId) }
            responseDao.insertQuestionResponses(mappedAnswers)

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

    override suspend fun getCounsellingRecord(beneficiaryId: Long): Flow<CompleteFormResponse?> {
        return responseDao.getFormResponseForBeneficiaryFlow(beneficiaryId)
    }

    override suspend fun syncUnsyncedRecords(): Boolean {
        Timber.d("syncUnsyncedRecords: querying all local form responses for debugging...")
        val allResponses = responseDao.getAllFormResponses()
        val allDetails = allResponses.map {
            "Response[id=${it.formResponse.responseId}, benId=${it.formResponse.beneficiaryId}, status=${it.formResponse.status}, syncStatus=${it.formResponse.syncStatus}, sectionsCount=${it.sectionResponses.size}]"
        }
        Timber.d("syncUnsyncedRecords: ALL records currently in DB: $allDetails")

        Timber.d("syncUnsyncedRecords: querying unsynced records...")
        val unsynced = responseDao.getUnsyncedFormResponses()
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
            val formVersionId = resp.formResponse.formVersionId
            val formDef = metadataDao.getFormDefinitionByVersionId(formVersionId)
            val payload = PayloadBuilder.buildBulkPayload(resp, formDef, officerId)

            val recordSuccess = try {
                if (resp.formResponse.status == "COMPLETE" || resp.formResponse.status == "COMPLETED") {
                    val preSubmitPayload = PayloadBuilder.buildBulkPayload(resp, formDef, officerId, phaseFilter = "PRE_SUBMIT")
                    var preSubmitSuccess = true
                    if (preSubmitPayload.sections.isNotEmpty()) {
                        val bulkPayload = listOf(preSubmitPayload)
                        try {
                            val jsonPayload = com.google.gson.Gson().toJson(bulkPayload)
                            Timber.d("Amrit push complete dynamic pre-submit payload JSON: $jsonPayload")
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to serialize pre-submit payload to JSON for logging")
                        }
                        val apiResponse = amritApiService.submitBulkCounselling(authHeader, bulkPayload)
                        val statusCode = apiResponse.code()
                        Timber.d("Amrit push complete dynamic pre-submit response: httpStatus=$statusCode")
                        if (statusCode == 200) {
                            val responseString = apiResponse.body()?.string()
                            val isSuccess = responseString?.let { org.json.JSONObject(it).optBoolean("success", false) } ?: false
                            if (isSuccess) {
                                Timber.d("Amrit push complete dynamic pre-submit success")
                            } else {
                                Timber.e("Amrit push complete dynamic pre-submit failed: success=false")
                                preSubmitSuccess = false
                            }
                        } else {
                            Timber.e("Amrit push complete dynamic pre-submit failed: status=$statusCode")
                            preSubmitSuccess = false
                        }
                    }

                    if (preSubmitSuccess) {
                        val postSubmitPayload = PayloadBuilder.buildBulkPayload(resp, formDef, officerId, phaseFilter = "POST_SUBMIT")
                        try {
                            val jsonPayload = com.google.gson.Gson().toJson(postSubmitPayload)
                            Timber.d("Amrit push complete dynamic post-submit payload JSON: $jsonPayload")
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to serialize complete payload to JSON for logging")
                        }

                        val apiResponse = amritApiService.completeCounselling(authHeader, postSubmitPayload)
                        val statusCode = apiResponse.code()
                        Timber.d("Amrit push complete dynamic response: httpStatus=$statusCode")

                        if (statusCode == 200) {
                            val responseString: String? = apiResponse.body()?.string()
                            if (responseString != null) {
                                val jsonObj = org.json.JSONObject(responseString)
                                val isSuccess = jsonObj.optBoolean("success", false)
                                if (isSuccess) {
                                    Timber.d("Amrit push complete dynamic success: $jsonObj")
                                    true
                                } else {
                                    Timber.e("Amrit push complete dynamic failed: success=false")
                                    false
                                }
                            } else {
                                Timber.e("Amrit push complete dynamic failed: body is null")
                                false
                            }
                        } else {
                            Timber.e("Amrit push complete dynamic failed: status=$statusCode")
                            false
                        }
                    } else {
                        false
                    }
                } else {
                    val bulkPayload = listOf(payload)
                    try {
                        val jsonPayload = com.google.gson.Gson().toJson(bulkPayload)
                        Timber.d("Amrit push submitBulk standalone payload JSON: $jsonPayload")
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to serialize bulk counselling to JSON for logging")
                    }

                    val apiResponse = amritApiService.submitBulkCounselling(authHeader, bulkPayload)
                    val statusCode = apiResponse.code()
                    Timber.d("Amrit push submitBulk standalone response: httpStatus=$statusCode")

                    if (statusCode == 200) {
                        val responseString: String? = apiResponse.body()?.string()
                        if (responseString != null) {
                            val jsonObj = org.json.JSONObject(responseString)
                            val isSuccess = jsonObj.optBoolean("success", false)
                            if (isSuccess) {
                                Timber.d("Amrit push submitBulk standalone success: $jsonObj")
                                true
                            } else {
                                Timber.e("Amrit push submitBulk standalone failed: success=false")
                                false
                            }
                        } else {
                            Timber.e("Amrit push submitBulk standalone failed: body is null")
                            false
                        }
                    } else {
                        Timber.e("Amrit push submitBulk standalone failed: status=$statusCode")
                        false
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Amrit push sync error for responseId=${resp.formResponse.responseId}")
                false
            }

            if (recordSuccess) {
                val hasPostSubmitResponse = resp.sectionResponses.any { secResp ->
                    val secDef = formDef?.versions?.find { it.version.versionId == formVersionId }
                        ?.sections?.find { it.section.sectionId == secResp.sectionResponse.sectionId }
                    secDef?.section?.sectionPhase == "POST_SUBMIT" && secResp.questionResponses.isNotEmpty()
                }
                val targetStatus = if (hasPostSubmitResponse) "COMPLETE" else resp.formResponse.status
                responseDao.updateFormResponse(
                    resp.formResponse.copy(
                        status = targetStatus,
                        syncStatus = "SYNCED",
                        syncedAt = System.currentTimeMillis()
                    )
                )
            } else {
                allSuccess = false
            }
        }

        return allSuccess
    }

    override suspend fun fetchAndStoreCounsellingResponse(
        beneficiaryId: Long,
        formUuid: String
    ): Boolean {
        try {
            val formDef = metadataDao.getFormDefinition(FormType.TB_COUNSELLING) ?: return false
            val activeVersion = formDef.versions.find { it.version.isActive }
                ?: formDef.versions.maxByOrNull { it.version.versionNumber }
                ?: return false

            val jwt = preferenceDao.getJWTAmritToken() ?: return false
            val response = amritApiService.getCounsellingResponse(jwt, beneficiaryId, formUuid)
            if (!response.isSuccessful) return false
            val apiResponses = response.body()?.data
            if (apiResponses.isNullOrEmpty()) return false

            db.withTransaction {
                // Preserve any locally edited (UNSYNCED) responses — do not overwrite them
                // with server data, as that would permanently discard unsaved user edits.
                val unsyncedLocal = responseDao.getUnsyncedResponseForBeneficiary(beneficiaryId)
                if (unsyncedLocal != null) return@withTransaction

                responseDao.deleteFormResponseForBeneficiary(beneficiaryId)

                val apiResponse = apiResponses.first()

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
                    syncedAt = System.currentTimeMillis()
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

                apiResponse.sections.forEach { apiSec ->
                    val sectionId = apiSec.sectionId
                    val sectionDef = activeVersion.sections.find { it.section.sectionId == sectionId }
                    if (sectionDef != null) {
                        val sectionResponseId = sectionIdToResponseIdMap[sectionId]
                        if (sectionResponseId != null) {
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

                val finalStatus = if (hasPostSubmitAnswers || apiResponse.status?.uppercase() == "COMPLETE" || apiResponse.status?.uppercase() == "COMPLETED") {
                    "COMPLETE"
                } else {
                    "SUBMITTED"
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
    }

    override suspend fun fetchAndStoreCompletedBeneficiaries(): List<Long>? {
        try {
            val jwt = preferenceDao.getJWTAmritToken()
            val authHeader = jwt ?: run {
                Timber.w("fetchAndStoreCompletedBeneficiaries: JWT token is null")
                return null
            }
            val response = amritApiService.getCompletedBeneficiaries(authHeader, "TB_COUNSELLING")
            if (response.isSuccessful) {
                return response.body()?.data as List<Long> ?: return null
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


    companion object {
        private const val DEFAULT_OFFICER_ID = 501L
    }
}

