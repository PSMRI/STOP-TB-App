package org.piramalswasthya.stoptb.repositories.contactTracing

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.room.withTransaction
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.piramalswasthya.stoptb.database.room.InAppDb
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.helpers.NetworkResponse
import org.piramalswasthya.stoptb.helpers.dynamicMapper.PayloadBuilder
import org.piramalswasthya.stoptb.model.contactTracing.ContactTracingStatus
import org.piramalswasthya.stoptb.model.dynamicEntity.CompleteFormDefinition
import org.piramalswasthya.stoptb.model.dynamicEntity.CompleteFormResponse
import org.piramalswasthya.stoptb.model.dynamicEntity.FormResponseEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.FormSectionWithQuestions
import org.piramalswasthya.stoptb.model.dynamicEntity.QuestionResponseEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.SectionResponseEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.ServerCounsellingResponseDto
import org.piramalswasthya.stoptb.model.dynamicEntity.ServerSectionResponseDto
import org.piramalswasthya.stoptb.network.AmritApiService
import org.piramalswasthya.stoptb.repositories.dynamicRepo.ICounsellingRepository
import org.piramalswasthya.stoptb.ui.contact_tracing.ClinicalScreeningStatus
import org.piramalswasthya.stoptb.ui.contact_tracing.QUESTION_UUID_CLINICAL_SCREENING_STATUS
import org.piramalswasthya.stoptb.ui.contact_tracing.QUESTION_UUID_REGIMEN_ADVISED
import org.piramalswasthya.stoptb.ui.contact_tracing.RegimenAdvised
import org.piramalswasthya.stoptb.ui.counselling_activity.FormType
import org.piramalswasthya.stoptb.ui.counselling_activity.SectionPhase
import org.piramalswasthya.stoptb.utils.Log
import timber.log.Timber
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class ContactTracingRepositoryImpl @Inject constructor(
    private val db: InAppDb,
    private val counsellingRepository: ICounsellingRepository,
    @Named("gsonAmritApi") private val amritApiService: AmritApiService,
    private val preferenceDao: PreferenceDao
) : IContactTracingRepository {

    private val metadataDao = db.dynamicFormMetadataDao()
    private val responseDao = db.counsellingFormResponseDao()

    override suspend fun getFormDefinition(formType: FormType): CompleteFormDefinition? =
        metadataDao.getFormDefinition(formType)

    override suspend fun getFormSchema(formType: FormType): NetworkResponse<CompleteFormDefinition> {
        return try {
            var definition = getFormDefinition(formType)
            if (definition == null && counsellingRepository.downloadAndStoreAllForms()) {
                definition = getFormDefinition(formType)
            }
            if (definition == null) {
                NetworkResponse.Error("Schema definition not found")
            } else {
                NetworkResponse.Success(definition)
            }
        } catch (e: Exception) {
            Timber.e(e, "getFormSchema failed for formType=$formType")
            NetworkResponse.Error("Schema definition not found")
        }
    }

    override suspend fun getCompleteResponse(responseId: Long): CompleteFormResponse? =
        responseDao.getFormResponseById(responseId)

    override suspend fun getExistingContactResponse(
        indexCaseBenId: Long,
        formVersionId: Int
    ): FormResponseEntity? =
        responseDao.getFormResponseForBeneficiary(indexCaseBenId, formVersionId)?.formResponse

    override suspend fun getOrCreateContactResponse(
        indexCaseBenId: Long,
        formVersionId: Int
    ): FormResponseEntity =
        counsellingRepository.getOrCreateDraft(indexCaseBenId, formVersionId).formResponse


    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun fetchAndStoreContactResponse(
        beneficiaryId: Long,
        formType: FormType,
        formVersionId: Int
    ): Boolean {
        try {
            val formDef = metadataDao.getFormDefinition(formType) ?: return false
            val activeVersion = formDef.versions.find { it.version.versionId == formVersionId }
                ?: return false

            val localResponse = responseDao.getFormResponseForBeneficiary(beneficiaryId, formVersionId)
            val hasLocalAnswers = localResponse?.sectionResponses?.any { it.questionResponses.isNotEmpty() } == true
            if (localResponse != null && localResponse.formResponse.syncStatus == "SYNCED" &&
                (localResponse.formResponse.status == "SUBMITTED" || localResponse.formResponse.status == "COMPLETE" || localResponse.formResponse.status == "COMPLETED" || localResponse.formResponse.status == "REFUSED") &&
                hasLocalAnswers
            ) {
                Timber.d("fetchAndStoreContactResponse: Synced response with answers already exists locally. Skipping fetch to preserve data.")
                return true
            }
            if (hasLocalAnswers) return true

            val jwt = preferenceDao.getJWTAmritToken() ?: return false
            val response = amritApiService.getBeneficiaryFormResponses(
                jwtToken = jwt,
                beneficiaryId = beneficiaryId,
                formUuid = formType.name,
                villageId = null,
                providerServiceMapId = null
            )
            if (!response.isSuccessful) return false
            val apiResponses = response.body()?.data
            if (apiResponses.isNullOrEmpty()) return false

            db.withTransaction {
                val unsyncedLocal = responseDao.getUnsyncedResponseForBeneficiary(beneficiaryId, formVersionId)
                if (unsyncedLocal != null) return@withTransaction

                val activeSectionIds = activeVersion.sections.map { it.section.sectionId }.toSet()
                val activeSectionUuids = activeVersion.sections.map { it.section.sectionUuid }.toSet()

                val apiResponse = apiResponses.find { resp ->
                    resp.sections.any { sec ->
                        sec.sectionId in activeSectionIds || sec.sectionUuid in activeSectionUuids
                    }
                }

                if (apiResponse == null) {
                    Timber.d("fetchAndStoreContactResponse: No API response matching formType=$formType schema for benId=$beneficiaryId")
                    responseDao.deleteFormResponseForBeneficiary(beneficiaryId, formVersionId)
                    return@withTransaction
                }

                val existingCreatedAt = responseDao.getFormResponseForBeneficiary(beneficiaryId, formVersionId)
                    ?.formResponse?.createdAt

                responseDao.deleteFormResponseForBeneficiary(beneficiaryId, formVersionId)

                val serverDate: Long? = try {
                    apiResponse.submittedAt?.let { OffsetDateTime.parse(it).toInstant().toEpochMilli() }
                } catch (e: Exception) {
                    Timber.w(e, "fetchAndStoreContactResponse: failed to parse submittedAt=${apiResponse.submittedAt}")
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

                val questionResponsesToInsert = mutableListOf<QuestionResponseEntity>()
                var hasPostSubmitAnswers = false

                // Pre-calculate answers to verify data presence before creating FormResponseEntity
                val backendSectionResponseIdBySectionId = apiResponse.sections
                    .associate { it.sectionId to it.sectionResponseId }

                apiResponse.sections.forEach { apiSec ->
                    val sectionId = apiSec.sectionId
                    val sectionDef = activeVersion.sections.find { it.section.sectionId == sectionId }
                    if (sectionDef != null) {
                        if (sectionDef.section.sectionPhase == "POST_SUBMIT" && apiSec.answers.isNotEmpty()) {
                            hasPostSubmitAnswers = true
                        }
                    }
                }

                if (apiResponse.sections.none { apiSec -> activeVersion.sections.any { it.section.sectionId == apiSec.sectionId } }) {
                    Timber.d("fetchAndStoreContactResponse: API response has no sections matching active local version for benId=$beneficiaryId")
                    return@withTransaction
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
                        sectionId = it.section.sectionId,
                        backendSectionResponseId = backendSectionResponseIdBySectionId[it.section.sectionId]
                    )
                }
                responseDao.insertSectionResponses(sectionResponses)

                val insertedSections = responseDao.getFormResponseById(responseId)?.sectionResponses ?: emptyList()
                val sectionIdToResponseIdMap = insertedSections.associate {
                    it.sectionResponse.sectionId to it.sectionResponse.sectionResponseId
                }

                apiResponse.sections.forEach { apiSec ->
                    val sectionId = apiSec.sectionId
                    val sectionDef = activeVersion.sections.find { it.section.sectionId == sectionId }
                    if (sectionDef != null) {
                        val sectionResponseId = sectionIdToResponseIdMap[sectionId]
                        if (sectionResponseId != null) {
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
                                    Timber.w("fetchAndStoreContactResponse: No local question found for serverQuestionId=$serverQId")
                                }
                            }
                        }
                    } else {
                        Timber.w("fetchAndStoreContactResponse: No local section found for serverSectionId=$sectionId")
                    }
                }

                if (questionResponsesToInsert.isNotEmpty()) {
                    responseDao.insertQuestionResponses(questionResponsesToInsert)
                }

                val hasAnswers = questionResponsesToInsert.isNotEmpty()
                if (!hasAnswers && apiResponse.status?.uppercase() != "REFUSED") {
                    Timber.d("fetchAndStoreContactResponse: 0 valid question answers found. Deleting empty form response shell for benId=$beneficiaryId")
                    responseDao.deleteFormResponseForBeneficiary(beneficiaryId, formVersionId)
                    return@withTransaction
                }

                val finalStatus = when {
                    apiResponse.status?.uppercase() == "REFUSED" -> "REFUSED"
                    hasPostSubmitAnswers || apiResponse.status?.uppercase() == "COMPLETE" || apiResponse.status?.uppercase() == "COMPLETED" -> "COMPLETE"
                    hasAnswers -> "SUBMITTED"
                    else -> "DRAFT"
                }

                responseDao.updateFormResponse(
                    formResponse.copy(responseId = responseId, status = finalStatus)
                )
            }
            return true
        } catch (e: Exception) {
            Timber.e(e, "fetchAndStoreContactResponse failed for benId=$beneficiaryId, formType=$formType")
            return false
        }
    }

    override suspend fun saveSectionAnswers(
        responseId: Long,
        sectionId: Int,
        answers: List<QuestionResponseEntity>,
        status: String
    ) {
        db.withTransaction {
            val resp = responseDao.getFormResponseById(responseId) ?: return@withTransaction
            val sectionResponse = resp.sectionResponses.find { it.sectionResponse.sectionId == sectionId }
                ?: return@withTransaction
            val sectionResponseId = sectionResponse.sectionResponse.sectionResponseId
            responseDao.deleteQuestionResponsesForSection(sectionResponseId)
            if (answers.isNotEmpty()) {
                responseDao.insertQuestionResponses(answers.map { it.copy(sectionResponseId = sectionResponseId) })
            }
            responseDao.updateFormResponse(
                resp.formResponse.copy(
                    status = status,
                    syncStatus = "UNSYNCED",
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    override suspend fun submitResponse(responseId: Long, finalStatus: String) {
        val resp = responseDao.getFormResponseById(responseId) ?: return
        responseDao.updateFormResponse(
            resp.formResponse.copy(
                status = finalStatus,
                syncStatus = "UNSYNCED",
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun submitResponseBulk(responseId: Long, phaseFilter: String?): Boolean {
        val resp = responseDao.getFormResponseById(responseId) ?: return true

        val formDef = metadataDao.getFormDefinitionByVersionId(resp.formResponse.formVersionId)
        val officerId = preferenceDao.getLoggedInUser()?.userId?.toLong() ?: DEFAULT_OFFICER_ID
        val payload = PayloadBuilder.buildBulkPayload(resp, formDef, officerId, phaseFilter = phaseFilter)

        if (payload.sections.isEmpty()) {
            Timber.d("submitResponseBulk: responseId=$responseId has no answers, skipping API call")
            return true
        }

        val jwt = preferenceDao.getJWTAmritToken()
        val authHeader = jwt ?: ""

        val success = try {
            val apiResponse = amritApiService.submitBulkCounselling(authHeader, listOf(payload))
            val statusCode = apiResponse.code()
            Timber.d("submitResponseBulk: responseId=$responseId, httpStatus=$statusCode")

            if (statusCode == 200) {
                val responseString = apiResponse.body()?.string()
                if (responseString != null) {
                    val jsonObj = org.json.JSONObject(responseString)
                    val isSuccess = jsonObj.optBoolean("success", false)
                    if (isSuccess) {
                        // Best-effort capture — backend contract doesn't yet guarantee this
                        // field is present on every submitBulk response.
                        if (jsonObj.has("responseId")) {
                            responseDao.updateBackendResponseId(responseId, jsonObj.optLong("responseId"))
                        }
                       //store backendSectionResponse id inside t_section_response for history list purpose
                        val sectionsArray = jsonObj.optJSONArray("sections")
                        if (sectionsArray != null) {
                            for (i in 0 until sectionsArray.length()) {
                                val sectionObj = sectionsArray.optJSONObject(i) ?: continue
                                val sectionUuid = sectionObj.optString("sectionUuid", "")
                                val backendSectionResponseId = sectionObj.optLong("sectionResponseId", -1L)
                                if (sectionUuid.isBlank() || backendSectionResponseId <= 0) continue
                                val localSectionId = metadataDao.getSectionInfoByUuids(listOf(sectionUuid))
                                    .firstOrNull()?.sectionId ?: continue
                                responseDao.updateBackendSectionResponseId(responseId, localSectionId, backendSectionResponseId)
                            }
                        }
                        Timber.d("submitResponseBulk: responseId=$responseId success")
                        true
                    } else {
                        Timber.e("submitResponseBulk: responseId=$responseId failed: success=false")
                        false
                    }
                } else {
                    Timber.e("submitResponseBulk: responseId=$responseId failed: body is null")
                    false
                }
            } else {
                Timber.e("submitResponseBulk: responseId=$responseId failed: status=$statusCode")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "submitResponseBulk: responseId=$responseId error")
            false
        }

        if (success) {
            responseDao.updateFormResponse(
                resp.formResponse.copy(syncStatus = "SYNCED", syncedAt = System.currentTimeMillis())
            )
        }

        return success
    }

    override fun observeResponseStatus(beneficiaryId: Long, formType: FormType): Flow<String?> =
        responseDao.observeFormResponseStatus(beneficiaryId, formType.name)

    override fun observeResponseFormVersionId(beneficiaryId: Long, formType: FormType): Flow<Int?> =
        responseDao.observeFormResponseVersionId(beneficiaryId, formType.name)

    override fun observePreSubmitResponseStatus(beneficiaryId: Long, formType: FormType): Flow<String?> =
        responseDao.observePreSubmitResponseStatus(beneficiaryId, formType.name)

    override fun getTptHistory(beneficiaryId: Long, formVersionId: Int): Flow<List<CompleteFormResponse>> =
        responseDao.getTptHistory(beneficiaryId, formVersionId)

    override suspend fun fetchAndRefreshTptHistory(beneficiaryId: Long, formVersionId: Int): Boolean {
        val authHeader = preferenceDao.getJWTAmritToken()?:""
        val villageId = preferenceDao.getLocationRecord()?.village?.id
        val providerServiceMapId = preferenceDao.getLoggedInUser()?.serviceMapId

        val apiRecords: List<ServerCounsellingResponseDto> = try {
            val apiResponse = amritApiService.getBeneficiaryFormResponses(
                jwtToken = authHeader,
                beneficiaryId = beneficiaryId,
                formUuid = FormType.TPT_FOLLOW_UP.name,
                villageId = villageId,
                providerServiceMapId = providerServiceMapId
            )
            if (!apiResponse.isSuccessful) {
                Timber.e("fetchAndRefreshTptHistory: beneficiaryId=$beneficiaryId failed: status=${apiResponse.code()}")
                return false
            }
            val body = apiResponse.body()
            if (body?.success != true) {
                Timber.e("fetchAndRefreshTptHistory: beneficiaryId=$beneficiaryId failed: success=false or empty body")
                return false
            }
            body.data ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "fetchAndRefreshTptHistory: beneficiaryId=$beneficiaryId error")
            return false
        }

        if (apiRecords.isEmpty()) return true


        val activeVersion = metadataDao.getFormDefinition(FormType.TPT_FOLLOW_UP)
            ?.versions?.find { it.version.versionId == formVersionId }
            ?: return false

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

        val sectionByIdMap = activeVersion.sections.associateBy { it.section.sectionId }
        val sectionByUuidMap = activeVersion.sections.mapNotNull { sec ->
            sec.section.sectionUuid?.let { uuid -> uuid to sec }
        }.toMap()

        fun getLocalSection(sectionDto: ServerSectionResponseDto): FormSectionWithQuestions? {
            return sectionByIdMap[sectionDto.sectionId]
                ?: sectionDto.sectionUuid.let { sectionByUuidMap[it] }
        }

        suspend fun insertSingleSectionResponse(formResponseId: Long, sectionDto: ServerSectionResponseDto) {
            val localSection = getLocalSection(sectionDto) ?: return
            val sectionId = localSection.section.sectionId
            val sectionResponseId = responseDao.insertSectionResponse(
                SectionResponseEntity(
                    formResponseId = formResponseId,
                    sectionId = sectionId,
                    backendSectionResponseId = sectionDto.sectionResponseId
                )
            )
            val questionResponses = sectionDto.answers.mapNotNull { a ->
                val qDetails = questionsMap[a.questionId]
                if (qDetails == null) {
                    Timber.w("fetchAndRefreshTptHistory: No local question found for serverQuestionId=${a.questionId}")
                    return@mapNotNull null
                }
                val localOptId = a.optionId?.let { optionsMap[Pair(a.questionId, it)] }
                QuestionResponseEntity(
                    sectionResponseId = sectionResponseId,
                    questionId = qDetails.question.questionId,
                    optionId = localOptId,
                    answerText = a.answerText
                )
            }
            if (questionResponses.isNotEmpty()) {
                responseDao.insertQuestionResponses(questionResponses)
            }
        }

        db.withTransaction {
            val postSubmitSectionDtos = apiRecords.flatMap { record ->
                record.sections.filter { sectionDto ->
                    getLocalSection(sectionDto)?.section?.sectionPhase == SectionPhase.POST_SUBMIT.value
                }.map { record to it }
            }
            if (postSubmitSectionDtos.isNotEmpty()) {
                val candidateIds = postSubmitSectionDtos.map { it.second.sectionResponseId }
                val alreadyStored = responseDao.getExistingBackendSectionResponseIds(candidateIds).toSet()

                postSubmitSectionDtos.forEach { (record, sectionDto) ->
                    if (sectionDto.sectionResponseId in alreadyStored) {
                        Timber.d(
                            "fetchAndRefreshTptHistory: sectionResponseId=${sectionDto.sectionResponseId} " +
                                "already stored locally, skipping"
                        )
                        return@forEach
                    }
                    val newResponseId = responseDao.insertFormResponse(
                        FormResponseEntity(
                            beneficiaryId = beneficiaryId,
                            formVersionId = formVersionId,
                            status = "COMPLETE",
                            lastVisitedSectionId = null,
                            syncStatus = "SYNCED",
                            backendResponseId = record.responseId,
                            isHistorySnapshot = true
                        )
                    )
                    insertSingleSectionResponse(newResponseId, sectionDto)
                }
            }

            val hasLivePreSubmit = responseDao.getLatestResponseForPhase(
                beneficiaryId, formVersionId, SectionPhase.PRE_SUBMIT.value
            ) != null
            if (!hasLivePreSubmit) {
                val preSubmitRecord = apiRecords.maxByOrNull { it.responseId }
                val preSubmitSections = preSubmitRecord?.sections?.filter { sectionDto ->
                    getLocalSection(sectionDto)?.section?.sectionPhase == SectionPhase.PRE_SUBMIT.value
                }
                if (!preSubmitSections.isNullOrEmpty()) {
                    val liveResponseId = responseDao.insertFormResponse(
                        FormResponseEntity(
                            beneficiaryId = beneficiaryId,
                            formVersionId = formVersionId,
                            status = preSubmitRecord.status ?: "SUBMITTED",
                            lastVisitedSectionId = null,
                            syncStatus = "SYNCED",
                            backendResponseId = preSubmitRecord.responseId,
                            isHistorySnapshot = false
                        )
                    )
                    preSubmitSections.forEach { sectionDto -> insertSingleSectionResponse(liveResponseId, sectionDto) }
                }
            }
        }

        return true
    }

    override suspend fun getExistingContactResponseForPhase(
        beneficiaryId: Long,
        formVersionId: Int,
        phase: SectionPhase
    ): FormResponseEntity? {
        val latest = responseDao.getLatestResponseForPhase(beneficiaryId, formVersionId, phase.value)
            ?.formResponse ?: return null

        return if (phase == SectionPhase.POST_SUBMIT && latest.status == "COMPLETE") null else latest
    }

    override suspend fun createPhaseScopedResponse(
        beneficiaryId: Long,
        formVersionId: Int,
        sectionIds: List<Int>
    ): FormResponseEntity {
        return db.withTransaction {
            val newForm = FormResponseEntity(
                beneficiaryId = beneficiaryId,
                formVersionId = formVersionId,
                status = "DRAFT",
                lastVisitedSectionId = null,
                syncStatus = "UNSYNCED"
            )
            val newResponseId = responseDao.insertFormResponse(newForm)
            responseDao.insertSectionResponses(
                sectionIds.map { SectionResponseEntity(formResponseId = newResponseId, sectionId = it) }
            )
            newForm.copy(responseId = newResponseId)
        }
    }

    override suspend fun getRegimenAdvised(beneficiaryId: Long, formVersionId: Int): RegimenAdvised? {
        val optionValue = responseDao.getAnsweredOptionValue(
            beneficiaryId, formVersionId, SectionPhase.PRE_SUBMIT.value, QUESTION_UUID_REGIMEN_ADVISED
        )
        return RegimenAdvised.fromValue(optionValue)
    }

    override suspend fun getClinicalScreeningStatus(beneficiaryId: Long, formVersionId: Int): ClinicalScreeningStatus? {
        val optionValue = responseDao.getAnsweredOptionValueAnyPhase(
            beneficiaryId, formVersionId, QUESTION_UUID_CLINICAL_SCREENING_STATUS
        )
        return ClinicalScreeningStatus.fromValue(optionValue)
    }

    override fun observeSubmittedFollowUpCount(beneficiaryId: Long, formVersionId: Int): Flow<Int> =
        responseDao.observeSubmittedFollowUpCount(beneficiaryId, formVersionId)

    override suspend fun isFollowUpTargetReached(beneficiaryId: Long, formVersionId: Int): Boolean {
        val regimen = getRegimenAdvised(beneficiaryId, formVersionId) ?: return false
        val submitted = responseDao.getSubmittedFollowUpCount(beneficiaryId, formVersionId)
        return submitted >= regimen.requiredFollowUpCount
    }

    override suspend fun getContactTracingStatus(beneficiaryId: Long): ContactTracingStatus = coroutineScope {
        // Local-first: a form submitted offline is written to Room immediately (see
        // submitResponse), well before it ever syncs to the backend.
        val localCommunityDeferred = async {
            responseDao.isFormSubmittedLocally(beneficiaryId, FormType.COMMUNITY_CONTACT_TRACING.name)
        }
        val localOccupationalDeferred = async {
            responseDao.isFormSubmittedLocally(beneficiaryId, FormType.OCCUPATION_CONTACT_TRACING.name)
        }

        val authHeader = preferenceDao.getJWTAmritToken()
        val villageId = preferenceDao.getLocationRecord()?.village?.id
        val providerServiceMapId = preferenceDao.getLoggedInUser()?.serviceMapId

        var remoteCommunitySubmitted = false
        var remoteOccupationalSubmitted = false

        if (authHeader == null || villageId == null || providerServiceMapId == null) {
            Timber.w("getContactTracingStatus: missing JWT/village/logged-in user - skipping remote check")
        } else {
            val communityDeferred = async {
                runCatching {
                    amritApiService.getCompletedBeneficiaries(
                        authHeader = authHeader,
                        formType = FormType.COMMUNITY_CONTACT_TRACING.name,
                        villageId = villageId,
                        providerServiceMapId = providerServiceMapId
                    )
                }.onFailure { Timber.e(it, "getContactTracingStatus: COMMUNITY_CONTACT_TRACING call failed") }
                    .getOrNull()
            }
            val occupationalDeferred = async {
                runCatching {
                    amritApiService.getCompletedBeneficiaries(
                        authHeader = authHeader,
                        formType = FormType.OCCUPATION_CONTACT_TRACING.name,
                        villageId = villageId,
                        providerServiceMapId = providerServiceMapId
                    )
                }.onFailure { Timber.e(it, "getContactTracingStatus: OCCUPATION_CONTACT_TRACING call failed") }
                    .getOrNull()
            }

            // Any failure (exception, or a non-2xx response - e.g. no connectivity) falls back to
            // an empty list here, leaving the final status to whatever the local check above found.
            remoteCommunitySubmitted = communityDeferred.await()?.takeIf { it.isSuccessful }?.body()?.data
                ?.any { it.beneficiaryId == beneficiaryId } ?: false
            remoteOccupationalSubmitted = occupationalDeferred.await()?.takeIf { it.isSuccessful }?.body()?.data
                ?.any { it.beneficiaryId == beneficiaryId } ?: false
        }

        val localCommunitySubmitted = localCommunityDeferred.await()
        val localOccupationalSubmitted = localOccupationalDeferred.await()
        /*Timber.d(
            "getContactTracingStatus: beneficiaryId=$beneficiaryId " +
                "localCommunity=$localCommunitySubmitted remoteCommunity=$remoteCommunitySubmitted " +
                "localOccupational=$localOccupationalSubmitted remoteOccupational=$remoteOccupationalSubmitted"
        )*/

        ContactTracingStatus(
            isCommunitySubmitted = localCommunitySubmitted || remoteCommunitySubmitted,
            isOccupationalSubmitted = localOccupationalSubmitted || remoteOccupationalSubmitted
        )
    }

    override suspend fun syncUnsyncedResponses(): Boolean {
        val unsynced = responseDao.getUnsyncedFormResponsesForTypes(
            listOf(
                FormType.COMMUNITY_CONTACT_TRACING.name,
                FormType.OCCUPATION_CONTACT_TRACING.name,
                FormType.CONTACT_FOLLOW_UP.name,
                FormType.TPT_FOLLOW_UP.name
            )
        )
        Timber.d("syncUnsyncedResponses: ${unsynced.size} contact tracing record(s) pending sync")
        var allSuccess = true
        for (resp in unsynced) {
            if (!submitResponseBulk(resp.formResponse.responseId)) {
                allSuccess = false
            }
        }
        return allSuccess
    }


    override fun observeContactFollowUpDoneBenIds(): Flow<List<Long>> =
        combine(
            responseDao.getFormDoneBenIds(FormType.CONTACT_FOLLOW_UP.name),
            observeTptEligibleBenIds(),
            responseDao.getPreSubmitDoneBenIds(FormType.TPT_FOLLOW_UP.name)
        ) { cfuDoneBenIds, tptEligibleBenIds, tptPreSubmitDoneBenIds ->
            cfuDoneBenIds.filter { benId -> benId !in tptEligibleBenIds || benId in tptPreSubmitDoneBenIds }
        }

    override fun observeTptFollowUpTargetReachedBenIds(): Flow<List<Long>> =
        combine(
            responseDao.getAllSubmittedFollowUpCounts(),
            responseDao.getAllRegimenAnswers(QUESTION_UUID_REGIMEN_ADVISED)
        ) { counts, regimens ->
            val regimenByKey = regimens.associateBy { it.beneficiaryId to it.formVersionId }
            counts.mapNotNull { c ->
                val required = RegimenAdvised.fromValue(regimenByKey[c.beneficiaryId to c.formVersionId]?.optionValue)
                    ?.requiredFollowUpCount
                c.beneficiaryId.takeIf { required != null && c.submittedCount >= required }
            }.distinct()
        }

    override fun observeTptEligibleBenIds(): Flow<List<Long>> =
        responseDao.getAllClinicalScreeningStatusAnswers(QUESTION_UUID_CLINICAL_SCREENING_STATUS)
            .map { answers ->
                answers
                    .filter { ClinicalScreeningStatus.fromValue(it.optionValue) == ClinicalScreeningStatus.TPT_ELIGIBLE }
                    .map { it.beneficiaryId }
                    .distinct()
            }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun fetchAndStoreVillageContactResponses(
        villageId: Int,
        formType: FormType,
        formVersionId: Int
    ): Boolean {
        try {
            val formDef = metadataDao.getFormDefinition(formType) ?: return false
            val activeVersion = formDef.versions.find { it.version.versionId == formVersionId }
                ?: return false

            val providerServiceMapId = preferenceDao.getLoggedInUser()?.serviceMapId
            val jwt = preferenceDao.getJWTAmritToken() ?: return false
            val response = amritApiService.getBeneficiaryFormResponses(
                jwtToken = jwt,
                beneficiaryId = null,
                formUuid = formType.name,
                villageId = villageId,
                providerServiceMapId = providerServiceMapId
            )
            if (!response.isSuccessful) return false
            val apiResponses = response.body()?.data
            if (apiResponses.isNullOrEmpty()) return true

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

            val activeSectionIds = activeVersion.sections.map { it.section.sectionId }.toSet()
            val activeSectionUuids = activeVersion.sections.map { it.section.sectionUuid }.toSet()

            db.withTransaction {
                apiResponses.forEach { apiResponse ->
                    val beneficiaryId = apiResponse.beneficiaryId ?: return@forEach

                    val matchesFormSchema = apiResponse.sections.any { sec ->
                        sec.sectionId in activeSectionIds || sec.sectionUuid in activeSectionUuids
                    }
                    if (!matchesFormSchema) {
                        return@forEach
                    }

                    val unsyncedLocal = responseDao.getUnsyncedResponseForBeneficiary(beneficiaryId, formVersionId)
                    if (unsyncedLocal != null) return@forEach

                    val existingCreatedAt = responseDao.getFormResponseForBeneficiary(beneficiaryId, formVersionId)
                        ?.formResponse?.createdAt

                    responseDao.deleteFormResponseForBeneficiary(beneficiaryId, formVersionId)

                    val serverDate: Long? = try {
                        apiResponse.submittedAt?.let { OffsetDateTime.parse(it).toInstant().toEpochMilli() }
                    } catch (e: Exception) {
                        Timber.w(e, "fetchAndStoreVillageContactResponses: failed to parse submittedAt=${apiResponse.submittedAt}")
                        null
                    }

                    val questionResponsesToInsert = mutableListOf<QuestionResponseEntity>()
                    var hasPostSubmitAnswers = false

                    val backendSectionResponseIdBySectionId = apiResponse.sections
                        .associate { it.sectionId to it.sectionResponseId }

                    apiResponse.sections.forEach { apiSec ->
                        val sectionId = apiSec.sectionId
                        val sectionDef = activeVersion.sections.find { it.section.sectionId == sectionId }
                        if (sectionDef != null) {
                            if (sectionDef.section.sectionPhase == "POST_SUBMIT" && apiSec.answers.isNotEmpty()) {
                                hasPostSubmitAnswers = true
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
                            sectionId = it.section.sectionId,
                            backendSectionResponseId = backendSectionResponseIdBySectionId[it.section.sectionId]
                        )
                    }
                    responseDao.insertSectionResponses(sectionResponses)

                    val insertedSections = responseDao.getFormResponseById(responseId)?.sectionResponses ?: emptyList()
                    val sectionIdToResponseIdMap = insertedSections.associate {
                        it.sectionResponse.sectionId to it.sectionResponse.sectionResponseId
                    }

                    apiResponse.sections.forEach { apiSec ->
                        val sectionId = apiSec.sectionId
                        val sectionDef = activeVersion.sections.find { it.section.sectionId == sectionId }
                        if (sectionDef != null) {
                            val sectionResponseId = sectionIdToResponseIdMap[sectionId]
                            if (sectionResponseId != null) {
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
                                        Timber.w("fetchAndStoreVillageContactResponses: No local question found for serverQuestionId=$serverQId")
                                    }
                                }
                            }
                        } else {
                            Timber.w("fetchAndStoreVillageContactResponses: No local section found for serverSectionId=$sectionId")
                        }
                    }

                    if (questionResponsesToInsert.isNotEmpty()) {
                        responseDao.insertQuestionResponses(questionResponsesToInsert)
                    }

                    val hasAnswers = questionResponsesToInsert.isNotEmpty()
                    if (!hasAnswers && apiResponse.status?.uppercase() != "REFUSED") {
                        Timber.d("fetchAndStoreVillageContactResponses: 0 valid question answers found. Deleting empty form response shell for benId=$beneficiaryId")
                        responseDao.deleteFormResponseForBeneficiary(beneficiaryId, formVersionId)
                        return@forEach
                    }

                    val finalStatus = when {
                        apiResponse.status?.uppercase() == "REFUSED" -> "REFUSED"
                        hasPostSubmitAnswers || apiResponse.status?.uppercase() == "COMPLETE" || apiResponse.status?.uppercase() == "COMPLETED" -> "COMPLETE"
                        hasAnswers -> "SUBMITTED"
                        else -> "DRAFT"
                    }

                    responseDao.updateFormResponse(
                        formResponse.copy(status = finalStatus)
                    )
                }
            }
            return true
        } catch (e: Exception) {
            Timber.e(e, "fetchAndStoreVillageContactResponses failed for villageId=$villageId, formType=$formType")
            return false
        }
    }

    private companion object {
        private const val DEFAULT_OFFICER_ID = 501L
    }
}
