package org.piramalswasthya.stoptb.repositories.contactTracing

import androidx.room.withTransaction
import com.google.gson.Gson
import org.piramalswasthya.stoptb.database.room.InAppDb
import org.piramalswasthya.stoptb.model.contactTracing.ContactTracingApiResponse
import org.piramalswasthya.stoptb.model.dynamicEntity.CompleteFormDefinition
import org.piramalswasthya.stoptb.ui.counselling_activity.FormType
import org.piramalswasthya.stoptb.model.contactTracing.ContactTracingFormDto
import org.piramalswasthya.stoptb.model.contactTracing.ContactTracingQuestionResponseEntity
import org.piramalswasthya.stoptb.model.contactTracing.ContactTracingResponseEntity
import org.piramalswasthya.stoptb.model.contactTracing.ContactTracingSectionResponseEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.DynamicFormEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.FormSectionEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.FormVersionEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.OptionConditionEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.QuestionOptionEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.QuestionValidationEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.SectionQuestionEntity
import org.piramalswasthya.stoptb.ui.contact_tracing.Contact_Tracing_Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Contact Tracing has its own DTOs and its own response tables (see IContactTracingRepository),
 * fully independent of Counselling. It reuses the generic dynamic-form SCHEMA tables
 * (DynamicFormEntity...QuestionValidationEntity) and their unmodified DynamicFormMetadataDao only.
 * Nothing here calls into or is called by CounsellingRepositoryImpl.
 */
@Singleton
class ContactTracingRepositoryImpl @Inject constructor(
    private val db: InAppDb
) : IContactTracingRepository {

    private val metadataDao = db.dynamicFormMetadataDao()
    private val responseDao = db.contactTracingResponseDao()
    private val gson = Gson()

    override suspend fun seedFormsFromStaticJson(): Boolean {
        return try {
            val envelope = gson.fromJson(Contact_Tracing_Json, ContactTracingApiResponse::class.java)
            if (!envelope.success) {
                Timber.w("seedFormsFromStaticJson: envelope.success = false")
                return false
            }
            db.withTransaction {
                envelope.data.forEach { formDto -> storeFormSchemaInDb(formDto) }
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "seedFormsFromStaticJson failed")
            false
        }
    }

    private suspend fun storeFormSchemaInDb(formDto: ContactTracingFormDto) {
        val formEntity = DynamicFormEntity(
            formId = formDto.formId,
            formUuid = formDto.formUuid,
            formName = formDto.formName,
            formType = formDto.formType,
            followUpDelayDays = formDto.followUpDelayDays,
            definition = formDto.definition,
            triggerRuleJson = formDto.triggerRule?.let { gson.toJson(it) },
            globalRuleJson = formDto.globalRule?.let { gson.toJson(it) },
            enabledIfJson = formDto.enabledIf?.let { gson.toJson(it) }
        )
        metadataDao.insertForm(formEntity)

        val versionId = formDto.formId * 1000 + formDto.versionNumber
        metadataDao.insertVersion(
            FormVersionEntity(
                versionId = versionId,
                formId = formDto.formId,
                versionNumber = formDto.versionNumber,
                isActive = formDto.isActive
            )
        )

        val sectionsToInsert = mutableListOf<FormSectionEntity>()
        val questionsToInsert = mutableListOf<SectionQuestionEntity>()
        val optionsToInsert = mutableListOf<QuestionOptionEntity>()
        val conditionsToInsert = mutableListOf<OptionConditionEntity>()
        val validationsToInsert = mutableListOf<QuestionValidationEntity>()

        formDto.sections.forEach { sectionDto ->
            sectionsToInsert.add(
                FormSectionEntity(
                    sectionId = sectionDto.sectionId,
                    versionId = versionId,
                    sectionName = sectionDto.sectionName,
                    sectionNameHindi = sectionDto.sectionNameHindi,
                    sectionOrder = sectionDto.displayOrder,
                    sectionPhase = sectionDto.sectionPhase,
                    sectionUuid = sectionDto.sectionUuid,
                    isEditable = false,
                    hasSubmitButton = sectionDto.hasSubmitButton
                )
            )

            sectionDto.questions.forEach { questionDto ->
                questionsToInsert.add(
                    SectionQuestionEntity(
                        questionId = questionDto.questionId,
                        sectionId = sectionDto.sectionId,
                        questionText = questionDto.questionText,
                        questionTextHindi = questionDto.questionTextHindi,
                        questionType = questionDto.questionType,
                        questionOrder = questionDto.displayOrder,
                        isRequired = questionDto.isMandatory,
                        questionUuid = questionDto.questionUuid,
                        serverQuestionId = questionDto.questionId,
                        allowMultiple = questionDto.allowMultiple,
                        containsPii = questionDto.containsPii,
                        visibleByDefault = questionDto.visibleByDefault,
                        maxLength = questionDto.maxLength,
                        enabledIfJson = questionDto.enabledIf?.let { gson.toJson(it) },
                        disabledIfJson = questionDto.disabledIf?.let { gson.toJson(it) },
                        mandatoryIfJson = questionDto.mandatoryIf?.let { gson.toJson(it) },
                        autoPopulated = questionDto.autoPopulated,
                        autoPopulateLogic = questionDto.autoPopulateLogic,
                        autoPopulateNote = questionDto.autoPopulateNote,
                        unit = questionDto.unit,
                        exampleValuesJson = questionDto.exampleValues?.let { gson.toJson(it) },
                        note = questionDto.note,
                        displayFormat = questionDto.displayFormat
                    )
                )

                questionDto.options.forEach { optionDto ->
                    optionsToInsert.add(
                        QuestionOptionEntity(
                            optionId = optionDto.optionId,
                            questionId = questionDto.questionId,
                            optionText = optionDto.optionLabel,
                            optionValue = optionDto.optionValue,
                            optionOrder = optionDto.displayOrder,
                            optionTextHindi = optionDto.optionLabelHindi,
                            serverOptionId = optionDto.optionId,
                            isExclusive = optionDto.isExclusive
                        )
                    )

                    optionDto.conditions.forEach { conditionDto ->
                        conditionsToInsert.add(
                            OptionConditionEntity(
                                conditionId = conditionDto.conditionId,
                                optionId = optionDto.optionId,
                                // 0 = no real target question. Several Contact Tracing action types
                                // (OPEN_FORM, REDIRECT_TO_FORM, GENERATE_ALERT, MOVE_CARD_TO_LIST, ...)
                                // carry their target via targetFormUuid/alertMessage/targetList instead.
                                // FK constraints are not enabled at the SQLite level in this app (verified:
                                // no PRAGMA foreign_keys/setForeignKeyConstraintsEnabled anywhere), so this
                                // is safe and does not affect Counselling's own condition rows.
                                targetQuestionId = conditionDto.targetQuestionId ?: 0,
                                actionType = conditionDto.actionType,
                                isFulfilledValue = true,
                                targetFormUuid = conditionDto.targetFormUuid,
                                alertMessage = conditionDto.alertMessage,
                                targetList = conditionDto.targetList,
                                actionValue = conditionDto.value,
                                note = conditionDto.note,
                                reEnableCondition = conditionDto.reEnableCondition
                            )
                        )
                    }
                }

                questionDto.validations.forEach { validationDto ->
                    validationsToInsert.add(
                        QuestionValidationEntity(
                            validationId = validationDto.validationId
                                ?: (questionDto.questionId * 100 + validationsToInsert.size),
                            questionId = questionDto.questionId,
                            validationType = validationDto.validationType,
                            validationValue = validationDto.validationParam,
                            errorMessage = validationDto.errorMessage ?: ""
                        )
                    )
                }
            }
        }

        if (sectionsToInsert.isNotEmpty()) metadataDao.insertSections(sectionsToInsert)
        if (questionsToInsert.isNotEmpty()) metadataDao.insertQuestions(questionsToInsert)
        if (optionsToInsert.isNotEmpty()) metadataDao.insertOptions(optionsToInsert)
        if (conditionsToInsert.isNotEmpty()) metadataDao.insertConditions(conditionsToInsert)
        if (validationsToInsert.isNotEmpty()) metadataDao.insertValidations(validationsToInsert)
    }

    override suspend fun getFormDefinition(formType: FormType): CompleteFormDefinition? =
        metadataDao.getFormDefinition(formType)

    override suspend fun getCompleteResponse(responseId: Long) =
        responseDao.getCompleteResponse(responseId)

    override fun getResponsesForIndexCase(indexCaseBenId: Long, contactType: String) =
        responseDao.getResponsesForIndexCase(indexCaseBenId, contactType)

    override suspend fun getContactDisplayName(responseId: Long, nameQuestionUuid: String): String? =
        responseDao.getAnswerText(responseId, nameQuestionUuid)

    override suspend fun getOrCreateContactResponse(
        indexCaseBenId: Long,
        contactBenId: Long?,
        contactType: String,
        formVersionId: Int
    ): ContactTracingResponseEntity {
        val existing = contactBenId?.let { responseDao.getResponseForContact(it, formVersionId) }
        if (existing != null) return existing

        val newEntity = ContactTracingResponseEntity(
            indexCaseBenId = indexCaseBenId,
            contactBenId = contactBenId,
            contactType = contactType,
            formVersionId = formVersionId
        )
        val newId = responseDao.insertResponse(newEntity)
        return newEntity.copy(responseId = newId)
    }

    override suspend fun saveSectionAnswers(
        responseId: Long,
        sectionId: Int,
        visitNumber: Int,
        answers: List<ContactTracingQuestionResponseEntity>
    ) {
        val sectionResponseId = responseDao.findSectionResponseId(responseId, sectionId, visitNumber)
            ?: responseDao.insertSectionResponse(
                ContactTracingSectionResponseEntity(
                    responseId = responseId,
                    sectionId = sectionId,
                    visitNumber = visitNumber
                )
            )
        responseDao.deleteQuestionResponsesForSection(sectionResponseId)
        if (answers.isNotEmpty()) {
            responseDao.insertQuestionResponses(answers.map { it.copy(sectionResponseId = sectionResponseId) })
        }
        responseDao.updateResponseStatus(responseId, status = "DRAFT", syncStatus = "UNSYNCED")
    }

    override suspend fun submitResponse(responseId: Long, finalStatus: String) {
        responseDao.updateResponseStatus(responseId, status = finalStatus, syncStatus = "UNSYNCED")
    }

    override suspend fun syncUnsyncedResponses(): Boolean {
        // No submit/complete endpoints exist yet for Contact Tracing on the backend.
        // Records stay UNSYNCED until those are deployed and this is wired up to call them.
        val unsynced = responseDao.getUnsyncedResponses()
        Timber.d("syncUnsyncedResponses: ${unsynced.size} contact tracing record(s) pending sync (no endpoint yet)")
        return true
    }
}
