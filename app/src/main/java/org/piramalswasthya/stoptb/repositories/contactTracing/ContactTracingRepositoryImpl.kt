package org.piramalswasthya.stoptb.repositories.contactTracing

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.room.withTransaction
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import org.piramalswasthya.stoptb.database.room.InAppDb
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.helpers.NetworkResponse
import org.piramalswasthya.stoptb.helpers.dynamicMapper.PayloadBuilder
import org.piramalswasthya.stoptb.model.contactTracing.ContactTracingStatus
import org.piramalswasthya.stoptb.model.dynamicEntity.CompleteFormDefinition
import org.piramalswasthya.stoptb.model.dynamicEntity.CompleteFormResponse
import org.piramalswasthya.stoptb.model.dynamicEntity.FormResponseEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.QuestionResponseEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.SectionResponseEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.ServerCounsellingResponseDto
import org.piramalswasthya.stoptb.network.AmritApiService
import org.piramalswasthya.stoptb.repositories.dynamicRepo.ICounsellingRepository
import org.piramalswasthya.stoptb.ui.contact_tracing.QUESTION_UUID_REGIMEN_ADVISED
import org.piramalswasthya.stoptb.ui.contact_tracing.RegimenAdvised
import org.piramalswasthya.stoptb.ui.counselling_activity.FormType
import org.piramalswasthya.stoptb.ui.counselling_activity.SectionPhase
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

            val jwt = preferenceDao.getJWTAmritToken() ?: return false
            val response = amritApiService.getBeneficiaryFormResponses(jwt, beneficiaryId, formType.name)
            if (!response.isSuccessful) return false
            val apiResponses = response.body()?.data
            if (apiResponses.isNullOrEmpty()) return false

            db.withTransaction {
                val unsyncedLocal = responseDao.getUnsyncedResponseForBeneficiary(beneficiaryId, formVersionId)
                if (unsyncedLocal != null) return@withTransaction

                val existingCreatedAt = responseDao.getFormResponseForBeneficiary(beneficiaryId, formVersionId)
                    ?.formResponse?.createdAt

                responseDao.deleteFormResponseForBeneficiary(beneficiaryId, formVersionId)

                val apiResponse = apiResponses.first()

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

                val finalStatus = when {
                    apiResponse.status?.uppercase() == "REFUSED" -> "REFUSED"
                    hasPostSubmitAnswers || apiResponse.status?.uppercase() == "COMPLETE" || apiResponse.status?.uppercase() == "COMPLETED" -> "COMPLETE"
                    else -> "SUBMITTED"
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

    override fun getTptHistory(beneficiaryId: Long, formVersionId: Int): Flow<List<CompleteFormResponse>> =
        responseDao.getTptHistory(beneficiaryId, formVersionId)

    override suspend fun fetchAndRefreshTptHistory(beneficiaryId: Long, formVersionId: Int): Boolean {
        val jwt = preferenceDao.getJWTAmritToken()
        val authHeader = jwt ?: ""

        val apiRecords: List<ServerCounsellingResponseDto> = try {
            val apiResponse = amritApiService.getBeneficiaryFormResponses(
                authHeader, beneficiaryId, FormType.TPT_FOLLOW_UP.name
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

        val allUuids = apiRecords.flatMap { it.sections }.map { it.sectionUuid }.distinct()
        val infoByUuid = metadataDao.getSectionInfoByUuids(allUuids).associateBy { it.sectionUuid }

        db.withTransaction {
            // Deletes matching historical rows by responseId before reinserting, preventing stale/duplicate history (cascades to related section/question rows).
            responseDao.deleteHistoryByBackendResponseIds(apiRecords.map { it.responseId })

            apiRecords.forEach { record ->
                val postSubmitSections = record.sections.filter {
                    infoByUuid[it.sectionUuid]?.sectionPhase == SectionPhase.POST_SUBMIT.value
                }

                if (postSubmitSections.isEmpty()) return@forEach

                val newResponseId = responseDao.insertFormResponse(
                    FormResponseEntity(
                        beneficiaryId = beneficiaryId,
                        formVersionId = formVersionId,
                        status = record.status ?: "COMPLETE",
                        lastVisitedSectionId = null,
                        syncStatus = "SYNCED",
                        backendResponseId = record.responseId,
                        isHistorySnapshot = true
                    )
                )
                postSubmitSections.forEach { sectionDto ->
                    val sectionId = infoByUuid[sectionDto.sectionUuid]?.sectionId ?: return@forEach
                    val sectionResponseId = responseDao.insertSectionResponse(
                        SectionResponseEntity(formResponseId = newResponseId, sectionId = sectionId)
                    )
                    if (sectionDto.answers.isNotEmpty()) {
                        responseDao.insertQuestionResponses(
                            sectionDto.answers.map { a ->
                                QuestionResponseEntity(
                                    sectionResponseId = sectionResponseId,
                                    questionId = a.questionId,
                                    optionId = a.optionId,
                                    answerText = a.answerText
                                )
                            }
                        )
                    }
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

    override fun observeSubmittedFollowUpCount(beneficiaryId: Long, formVersionId: Int): Flow<Int> =
        responseDao.observeSubmittedFollowUpCount(beneficiaryId, formVersionId)

    override suspend fun isFollowUpTargetReached(beneficiaryId: Long, formVersionId: Int): Boolean {
        val regimen = getRegimenAdvised(beneficiaryId, formVersionId) ?: return false
        val submitted = responseDao.getSubmittedFollowUpCount(beneficiaryId, formVersionId)
        return submitted >= regimen.requiredFollowUpCount
    }

    override suspend fun getContactTracingStatus(beneficiaryId: Long): ContactTracingStatus = coroutineScope {
        val authHeader = preferenceDao.getJWTAmritToken() ?: run {
            Timber.w("getContactTracingStatus: JWT token is null")
            return@coroutineScope ContactTracingStatus()
        }
        val villageId = preferenceDao.getLocationRecord()?.village?.id ?: run {
            Timber.w("getContactTracingStatus: LocationRecord/Village is null")
            return@coroutineScope ContactTracingStatus()
        }
        val providerServiceMapId = preferenceDao.getLoggedInUser()?.serviceMapId ?: run {
            Timber.w("getContactTracingStatus: Logged-in user is null")
            return@coroutineScope ContactTracingStatus()
        }

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

        // Any failure (exception, or a non-2xx response) falls back to an empty list here,
        // which naturally yields isSubmitted=false — the red-cross default.
        val communityList = communityDeferred.await()?.takeIf { it.isSuccessful }?.body()?.data ?: emptyList()
        val occupationalList = occupationalDeferred.await()?.takeIf { it.isSuccessful }?.body()?.data ?: emptyList()

        ContactTracingStatus(
            isCommunitySubmitted = communityList.any { it.beneficiaryId == beneficiaryId },
            isOccupationalSubmitted = occupationalList.any { it.beneficiaryId == beneficiaryId }
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

    private companion object {
        private const val DEFAULT_OFFICER_ID = 501L
    }
}
