package org.piramalswasthya.stoptb.helpers.dynamicMapper

import com.google.gson.Gson
import org.piramalswasthya.stoptb.database.room.dao.dynamicSchemaDao.DynamicFormMetadataDao
import org.piramalswasthya.stoptb.model.dynamicEntity.DynamicFormEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.FormSchemaDto
import org.piramalswasthya.stoptb.model.dynamicEntity.FormSectionEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.FormVersionEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.OptionConditionEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.QuestionOptionEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.QuestionValidationEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.SectionQuestionEntity
import org.piramalswasthya.stoptb.ui.counselling_activity.FormType
import timber.log.Timber

enum class QuestionIdStrategy { HASH_BASED, RAW_SERVER_ID }

// Forms whose versions are stored side by side (see FormSchemaDto.versions). Their versions share
// questionUuids, so HASH_BASED ids get a version prefix from version 2 on; otherwise V1 and V2 would
// share one question row and its options would be both versions' options combined. Version 1 keeps
// the plain ids, so rows and answers already saved against it stay valid.
val VERSION_SCOPED_ID_FORMS = setOf(FormType.CONTACT_FOLLOW_UP.name, FormType.TPT_FOLLOW_UP.name)

private fun hashIdPrefix(apiSchema: FormSchemaDto): String =
    if (apiSchema.formType in VERSION_SCOPED_ID_FORMS && apiSchema.versionNumber > 1) {
        "v${apiSchema.versionNumber}|"
    } else ""

/**
 * Shared schema-seeding helper for every dynamic form module (Counselling, Contact Tracing, ...):
 * maps one FormSchemaDto into the generic t_dynamic_form/t_form_version/t_form_section/
 * t_section_question/t_question_option/t_option_condition/t_question_validation tables.
 * Reused as-is by both CounsellingRepositoryImpl and ContactTracingRepositoryImpl.
 */
suspend fun storeFormSchemaInDb(
    metadataDao: DynamicFormMetadataDao,
    gson: Gson,
    apiSchema: FormSchemaDto,
    idStrategy: QuestionIdStrategy,
    wipeExistingVersions: Boolean
) {
    val formId = apiSchema.formId.toIntOrNull() ?: 0
    val formUuid = apiSchema.formUuid ?: "FORM_$formId"
    val idPrefix = hashIdPrefix(apiSchema)
    fun hashId(key: String): Int = (idPrefix + key).hashCode()
    Timber.d("storeFormSchemaInDb: Inserting formId=$formId, formUuid=$formUuid, formName=${apiSchema.formName}, strategy=$idStrategy")

    if (wipeExistingVersions) {
        metadataDao.deleteVersionsByFormId(formId)
    }
    metadataDao.insertForm(
        DynamicFormEntity(
            formId = formId,
            formUuid = formUuid,
            formName = apiSchema.formName,
            formType = apiSchema.formType ?: "",
            followUpDelayDays = apiSchema.followUpDelayDays
        )
    )

    val versionId = formId * 1000 + apiSchema.versionNumber
    metadataDao.insertVersion(
        FormVersionEntity(
            versionId = versionId,
            formId = formId,
            versionNumber = apiSchema.versionNumber,
            isActive = apiSchema.isActive
        )
    )

    // Only needed by HASH_BASED: resolves a condition's targetQuestionId (a raw server int)
    // back to the fieldId whose hash is the question's actual local primary key.
    val questionIdToFieldIdMap = mutableMapOf<Int, String>()
    if (idStrategy == QuestionIdStrategy.HASH_BASED) {
        apiSchema.sections.forEach { sectionDto ->
            sectionDto.questions.forEach { questionDto ->
                questionDto.questionId?.let { qId -> questionIdToFieldIdMap[qId] = questionDto.fieldId }
            }
        }
    }

    val sectionsToInsert = mutableListOf<FormSectionEntity>()
    val questionsToInsert = mutableListOf<SectionQuestionEntity>()
    val optionsToInsert = mutableListOf<QuestionOptionEntity>()
    val conditionsToInsert = mutableListOf<OptionConditionEntity>()
    val validationsToInsert = mutableListOf<QuestionValidationEntity>()

    apiSchema.sections.forEach { sectionDto ->
        val sectionIdInt = sectionDto.sectionId.toIntOrNull() ?: 0
        sectionsToInsert.add(
            FormSectionEntity(
                sectionId = sectionIdInt,
                versionId = versionId,
                sectionName = sectionDto.sectionName,
                sectionNameHindi = sectionDto.sectionNameHindi,
                sectionOrder = sectionDto.displayOrder ?: 0,
                sectionPhase = sectionDto.sectionPhase ?: "",
                sectionUuid = sectionDto.sectionUuid,
                isEditable = sectionDto.isEditable,
                hasSubmitButton = sectionDto.hasSubmitButton ?: false
            )
        )

        sectionDto.questions.forEach { questionDto ->
            val questionIdInt = when (idStrategy) {
                QuestionIdStrategy.HASH_BASED -> hashId(questionDto.fieldId)
                QuestionIdStrategy.RAW_SERVER_ID -> questionDto.questionId
                    ?: error("RAW_SERVER_ID strategy requires questionId, missing for fieldId=${questionDto.fieldId}")
            }
            questionsToInsert.add(
                SectionQuestionEntity(
                    questionId = questionIdInt,
                    sectionId = sectionIdInt,
                    questionText = questionDto.label,
                    questionTextHindi = questionDto.labelHindi,
                    questionType = questionDto.type,
                    questionOrder = questionDto.displayOrder ?: 0,
                    isRequired = questionDto.isMandatory,
                    questionUuid = questionDto.fieldId,
                    serverQuestionId = questionDto.questionId,
                    visibleByDefault = questionDto.visibleByDefault,
                    requiresBackFill = questionDto.requiresBackFill,
                    editableAfterSubmit = questionDto.editableAfterSubmit,
                    enabledIfJson = questionDto.enabledIf?.let { gson.toJson(it) },
                    disabledIfJson = questionDto.disabledIf?.let { gson.toJson(it) },
                    mandatoryIfJson = questionDto.mandatoryIf?.let { gson.toJson(it) }
                )
            )

            questionDto.getOptionItems().forEach { optionDto ->
                val optionIdInt = when (idStrategy) {
                    QuestionIdStrategy.HASH_BASED -> hashId(questionDto.fieldId + "_" + optionDto.optionValue)
                    QuestionIdStrategy.RAW_SERVER_ID -> optionDto.optionId
                }
                optionsToInsert.add(
                    QuestionOptionEntity(
                        optionId = optionIdInt,
                        questionId = questionIdInt,
                        optionText = optionDto.optionLabel,
                        optionTextHindi = optionDto.optionLabelHindi,
                        optionValue = optionDto.optionValue,
                        optionOrder = optionDto.displayOrder,
                        serverOptionId = optionDto.optionId,
                        optionUuid = optionDto.optionUuid
                    )
                )

                optionDto.conditions.forEach conditionLoop@{ conditionDto ->
                    val resolvedTargetQuestionId = when (idStrategy) {
                        QuestionIdStrategy.HASH_BASED -> {
                            val targetQId = conditionDto.targetQuestionId ?: return@conditionLoop
                            conditionDto.targetQuestionUuid?.let { hashId(it) }
                                ?: questionIdToFieldIdMap[targetQId]?.let { hashId(it) }
                                ?: run {
                                    Timber.w("storeFormSchemaInDb: cannot map targetQId=$targetQId for option ${optionDto.optionValue}, skipping condition")
                                    return@conditionLoop
                                }
                        }
                        QuestionIdStrategy.RAW_SERVER_ID -> conditionDto.targetQuestionId ?: 0
                    }
                    val conditionIdInt = when (idStrategy) {
                        QuestionIdStrategy.HASH_BASED ->
                            hashId(questionDto.fieldId + "_" + optionDto.optionValue + "_" + (conditionDto.targetQuestionId ?: 0))
                        QuestionIdStrategy.RAW_SERVER_ID -> conditionDto.conditionId
                    }
                    conditionsToInsert.add(
                        OptionConditionEntity(
                            conditionId = conditionIdInt,
                            optionId = optionIdInt,
                            targetQuestionId = resolvedTargetQuestionId,
                            actionType = conditionDto.actionType,
                            isFulfilledValue = true,
                            targetFormUuid = conditionDto.targetFormUuid,
                            alertMessage = conditionDto.alertMessage,
                            actionValue = conditionDto.actionValue
                        )
                    )
                }
            }

            val helperIndexByPosition = QuestionHelperText.helperRowIndexes(questionDto.validations) { validationDto ->
                validationDto.validationType to validationDto.validationId
            }
            questionDto.validations.forEachIndexed { position, validationDto ->
                val indexWithinType = helperIndexByPosition[position] ?: 0
                val validationIdInt = when (idStrategy) {
                    QuestionIdStrategy.HASH_BASED -> QuestionHelperText.hashValidationId(
                        idPrefix + questionDto.fieldId, validationDto.validationType, indexWithinType
                    )
                    QuestionIdStrategy.RAW_SERVER_ID -> validationDto.validationId
                        ?: (questionIdInt * 100 + validationsToInsert.size)
                }
                validationsToInsert.add(
                    QuestionValidationEntity(
                        validationId = validationIdInt,
                        questionId = questionIdInt,
                        validationType = validationDto.validationType,
                        validationValue = validationDto.validationParam,
                        errorMessage = validationDto.errorMessage ?: ""
                    )
                )
            }
        }
    }

    Timber.d("storeFormSchemaInDb: Inserting sections=${sectionsToInsert.size}, questions=${questionsToInsert.size}, options=${optionsToInsert.size}, conditions=${conditionsToInsert.size}, validations=${validationsToInsert.size}")
    if (sectionsToInsert.isNotEmpty()) metadataDao.insertSections(sectionsToInsert)
    if (questionsToInsert.isNotEmpty()) metadataDao.insertQuestions(questionsToInsert)
    if (optionsToInsert.isNotEmpty()) metadataDao.insertOptions(optionsToInsert)
    if (optionsToInsert.isNotEmpty()) metadataDao.deleteConditionsForOptions(optionsToInsert.map { it.optionId })
    if (questionsToInsert.isNotEmpty()) metadataDao.deleteValidationsForQuestions(questionsToInsert.map { it.questionId })

    if (conditionsToInsert.isNotEmpty()) metadataDao.insertConditions(conditionsToInsert)
    if (validationsToInsert.isNotEmpty()) metadataDao.insertValidations(validationsToInsert)
}
