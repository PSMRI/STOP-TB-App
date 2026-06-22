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
import org.piramalswasthya.stoptb.utils.Log
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

        Timber.d("storeFormSchemaInDb: Mapping ${apiSchema.sections.size} sections...")
        apiSchema.sections.forEach { sectionDto ->
            val sectionIdInt = sectionDto.sectionId.toIntOrNull() ?: 0
            val sectionEntity = FormSectionEntity(
                sectionId = sectionIdInt,
                versionId = versionId,
                sectionName = sectionDto.sectionName,
                sectionOrder = sectionDto.displayOrder ?: 0,
                sectionPhase = sectionDto.sectionPhase ?: "",
                sectionUuid = sectionDto.sectionUuid
            )
            sectionsToInsert.add(sectionEntity)

            Timber.d("storeFormSchemaInDb: Mapping ${sectionDto.questions.size} questions for section $sectionIdInt...")
            sectionDto.questions.forEach { questionDto ->
                val questionIdInt = questionDto.questionId ?: 0
                val questionEntity = SectionQuestionEntity(
                    questionId = questionIdInt,
                    sectionId = sectionIdInt,
                    questionText = questionDto.label,
                    questionType = questionDto.type,
                    questionOrder = questionDto.displayOrder ?: 0,
                    isRequired = questionDto.isMandatory,
                    questionUuid = questionDto.fieldId
                )
                questionsToInsert.add(questionEntity)

                val optionItems = questionDto.getOptionItems()
                Timber.d("storeFormSchemaInDb: Mapping ${optionItems.size} options for question $questionIdInt...")
                optionItems.forEach { optionDto ->
                    val optionEntity = QuestionOptionEntity(
                        optionId = optionDto.optionId,
                        questionId = questionIdInt,
                        optionText = optionDto.optionLabel,
                        optionValue = optionDto.optionValue,
                        optionOrder = optionDto.displayOrder
                    )
                    optionsToInsert.add(optionEntity)

                    optionDto.conditions.forEach conditionLoop@{ conditionDto ->
                        val targetQId = conditionDto.targetQuestionId ?: return@conditionLoop
                        val conditionEntity = OptionConditionEntity(
                            conditionId = conditionDto.conditionId,
                            optionId = optionDto.optionId,
                            targetQuestionId = targetQId,
                            actionType = conditionDto.actionType,
                            isFulfilledValue = true
                        )
                        conditionsToInsert.add(conditionEntity)
                    }
                }

                questionDto.validations.forEach { validationDto ->
                    val validationEntity = QuestionValidationEntity(
                        validationId = validationDto.validationId,
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
        submitSectionWithPhase(responseId, answers, "POST_SUBMIT", "COMPLETE")
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
        val counsellingPayloadList = unsynced.map { response ->
            val formVersionId = response.formResponse.formVersionId
            val formDef = metadataDao.getFormDefinitionByVersionId(formVersionId)
            PayloadBuilder.buildBulkPayload(response, formDef, officerId)
        }

        try {
            val jsonPayload = com.google.gson.Gson().toJson(counsellingPayloadList)
            Timber.d("Amrit push submitBulk standalone payload JSON: $jsonPayload")
        } catch (e: Exception) {
            Timber.e(e, "Failed to serialize bulk counselling to JSON for logging")
        }

        val jwt = preferenceDao.getJWTAmritToken()
        val authHeader = jwt ?: ""

        return try {
            val response = amritApiService.submitBulkCounselling(authHeader, counsellingPayloadList)
            val statusCode = response.code()
            Timber.d("Amrit push submitBulk standalone response: httpStatus=$statusCode")

            if (statusCode == 200) {
                val responseString: String? = response.body()?.string()
                if (responseString != null) {
                    val jsonObj = org.json.JSONObject(responseString)
                    val isSuccess = jsonObj.optBoolean("success", false)
                    if (isSuccess) {
                        Timber.d("Amrit push submitBulk standalone success: $jsonObj")
                        unsynced.forEach { resp ->
                            responseDao.updateFormResponse(
                                resp.formResponse.copy(
                                    syncStatus = "SYNCED",
                                    syncedAt = System.currentTimeMillis()
                                )
                            )
                        }
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
        } catch (e: Exception) {
            Timber.e(e, "Amrit push submitBulk standalone error")
            false
        }
    }

    companion object {
        private const val DEFAULT_OFFICER_ID = 501L
    }
}
