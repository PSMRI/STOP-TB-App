package org.piramalswasthya.stoptb.repositories

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.piramalswasthya.stoptb.helpers.NetworkResponse
import org.piramalswasthya.stoptb.model.CounsellingOverviewData
import timber.log.Timber
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.model.dynamicEntity.*
import org.piramalswasthya.stoptb.database.room.InAppDb
import org.piramalswasthya.stoptb.repositories.dynamicRepo.ICounsellingRepository
import org.piramalswasthya.stoptb.model.Gender
import org.piramalswasthya.stoptb.model.AgeUnit
import org.piramalswasthya.stoptb.helpers.dynamicMapper.PayloadBuilder.getQuestionCode
import org.piramalswasthya.stoptb.helpers.dynamicMapper.PayloadBuilder.getSectionCode
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.piramalswasthya.stoptb.ui.counselling_activity.FormType
import org.piramalswasthya.stoptb.ui.counselling_activity.SectionPhase
import org.piramalswasthya.stoptb.work.WorkerUtils

@Singleton
class CounsellingRepo @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferenceDao: PreferenceDao,
    private val db: InAppDb,
    private val counsellingRepository: ICounsellingRepository
) {

    private val benDao = db.benDao
    private val tbDao = db.tbDao

    suspend fun getCounsellingOverview(benId: Long): NetworkResponse<CounsellingOverviewData> {
        return withContext(Dispatchers.IO) {
            try {
                val ben = benDao.getBen(benId) ?: return@withContext NetworkResponse.Error("Beneficiary not found")
                val tbDiag = tbDao.getTbDiagnosticsByBenId(benId)
                val loggedInUser = preferenceDao.getLoggedInUser()?.name ?: ""

                val results = mutableListOf<String>()
                tbDiag?.chestXRayResult?.let { results.add("X-Ray: $it") }
                tbDiag?.naatResult?.let { results.add("NAAT: $it") }
                tbDiag?.liquidCultureResult?.let { results.add("Liquid Culture: $it") }
                val diagnosis = if (results.isNotEmpty()) results.joinToString(" / ") else "N/A"

                val genderText = when (ben.gender) {
                    Gender.MALE -> "Male"
                    Gender.FEMALE -> "Female"
                    Gender.TRANSGENDER -> "Transgender"
                    else -> "Other"
                }
                val ageUnitText = when (ben.ageUnit) {
                    AgeUnit.YEARS -> "Y"
                    AgeUnit.MONTHS -> "M"
                    AgeUnit.DAYS -> "D"
                    else -> "Y"
                }
                val ageGender = "${ben.age} $ageUnitText / $genderText"

                val formResponse = db.counsellingFormResponseDao().getFormResponseForBeneficiary(benId)
                var currentStep = 0
                var completedSteps = 0
                var status = "DRAFT"
                if (formResponse != null) {
                    status = formResponse.formResponse.status
                    val versionId = formResponse.formResponse.formVersionId
                    val formDef = db.dynamicFormMetadataDao().getFormDefinitionByVersionId(versionId)
                    val activeVersion = formDef?.versions?.find { it.version.versionId == versionId }
                    val sections = activeVersion?.sections?.sortedBy { it.section.sectionOrder } ?: emptyList()
                    val lastVisitedId = formResponse.formResponse.lastVisitedSectionId
                    if (lastVisitedId != null) {
                        val idx = sections.indexOfFirst { it.section.sectionId == lastVisitedId }
                        if (idx != -1) {
                            currentStep = idx
                        }
                    }
                    completedSteps = formResponse.sectionResponses.count { it.questionResponses.isNotEmpty() }
                }

                val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH)
                val todayStr = sdf.format(java.util.Date())

                val overviewData = CounsellingOverviewData(
                    benId = benId,
                    patientName = "${ben.firstName ?: ""} ${ben.lastName ?: ""}".trim(),
                    nikshayId = ben.nikshayId ?: "",
                    counsellingDate = todayStr,
                    counsellingOfficer = loggedInUser,
                    regDate = ben.regDate,
                    beneficiaryId = ben.beneficiaryId.toString(),
                    ageGender = ageGender,
                    diagnosis = diagnosis,
                    currentStep = currentStep,
                    completedSteps = completedSteps,
                    status = status
                )
                NetworkResponse.Success(overviewData)
            } catch (e: Exception) {
                Timber.e(e, "getCounsellingOverview failed for benId=$benId")
                NetworkResponse.Error("Failed to load patient data")
            }
        }
    }

    suspend fun getFormSchema(benId: Long, phase: SectionPhase): NetworkResponse<CounsellingFormSchemaDto> {
        return withContext(Dispatchers.IO) {
            try {
                var completeForm = counsellingRepository.getFormDefinition(FormType.TB_COUNSELLING)
                if (completeForm == null) {
                    val success = counsellingRepository.downloadAndStoreAllForms()
                    if (success) {
                        completeForm = counsellingRepository.getFormDefinition(FormType.TB_COUNSELLING)
                    }
                }

                if (completeForm == null) {
                    return@withContext NetworkResponse.Error("Schema definition not found")
                }

                val activeVersionWithSections = completeForm.versions.find { it.version.isActive }
                    ?: completeForm.versions.maxByOrNull { it.version.versionNumber }
                    ?: return@withContext NetworkResponse.Error("No active version found")

                val filteredSectionsFromDb = counsellingRepository.getSectionsByPhase(FormType.TB_COUNSELLING, phase)

                val showConditionTargets = mutableSetOf<Int>()
                filteredSectionsFromDb.forEach { sec ->
                    sec.questions.forEach { q ->
                        q.options.forEach { opt ->
                            opt.conditions.forEach { cond ->
                                if (cond.actionType == "SHOW" || cond.actionType == "SHOW_QUESTION") {
                                    cond.targetQuestionId.let { showConditionTargets.add(it) }
                                }
                            }
                        }
                    }
                }

                val sectionsList = filteredSectionsFromDb.map { formSecWithQuestions ->
                    val sec = formSecWithQuestions.section
                    val questionsList = formSecWithQuestions.questions.sortedBy { it.question.questionOrder }.map { secQWithDetails ->
                        val q = secQWithDetails.question
                        val optionsList = secQWithDetails.options.sortedBy { it.option.optionOrder }.map { qOptWithConditions ->
                            val opt = qOptWithConditions.option
                            val conditionsList = qOptWithConditions.conditions.map { cond ->
                                CounsellingConditionDto(
                                    conditionId = cond.conditionId,
                                    actionType = cond.actionType,
                                    targetQuestionId = cond.targetQuestionId
                                )
                            }
                            CounsellingOptionDto(
                                optionId = opt.optionId,
                                optionLabel = opt.optionText,
                                optionValue = opt.optionValue,
                                displayOrder = opt.optionOrder,
                                conditions = conditionsList
                            )
                        }

                        val validationsList = secQWithDetails.validations.map { valEntity ->
                            CounsellingValidationDto(
                                validationId = valEntity.validationId,
                                validationType = valEntity.validationType,
                                validationParam = valEntity.validationValue ?: "",
                                errorMessage = valEntity.errorMessage
                            )
                        }

                        val defaultVisible = !showConditionTargets.contains(q.questionId)

                        CounsellingQuestionDto(
                            questionId = q.questionId,
                            questionUuid = getQuestionCode(q.questionId),
                            questionText = q.questionText,
                            questionType = q.questionType,
                            isMandatory = q.isRequired,
                            displayOrder = q.questionOrder,
                            maxLength = validationsList.find { it.validationType == "MAX_LENGTH" }?.validationParam?.toIntOrNull(),
                            defaultValue = null,
                            containsPii = false,
                            visibleByDefault = defaultVisible,
                            validations = validationsList,
                            options = optionsList,
                            value = null,
                            visible = defaultVisible,
                            errorMessage = null
                        )
                    }
                    CounsellingSectionDto(
                        sectionId = sec.sectionId,
                        sectionUuid = getSectionCode(sec.sectionId),
                        sectionName = sec.sectionName,
                        sectionPhase = sec.sectionPhase,
                        isRequired = true,
                        displayOrder = sec.sectionOrder,
                        hasSubmitButton = (sec.sectionPhase == SectionPhase.PRE_SUBMIT.value && filteredSectionsFromDb.lastOrNull { it.section.sectionPhase == "PRE_SUBMIT" }?.section?.sectionId == sec.sectionId) || (sec.sectionPhase == "POST_SUBMIT"),
                        questions = questionsList
                    )
                }

                val schemaDto = CounsellingFormSchemaDto(
                    formId = completeForm.form.formId,
                    formUuid = completeForm.form.formUuid,
                    formName = completeForm.form.formName,
                    formType = completeForm.form.formType,
                    isActive = activeVersionWithSections.version.isActive,
                    versionNumber = activeVersionWithSections.version.versionNumber,
                    sections = sectionsList
                )

                val draftResponse = counsellingRepository.getOrCreateDraft(benId, activeVersionWithSections.version.versionId)

                schemaDto.sections.forEach { sec ->
                    val secResponse = draftResponse.sectionResponses.find { it.sectionResponse.sectionId == sec.sectionId }
                    if (secResponse != null) {
                        sec.questions.forEach { q ->
                            val qResponses = secResponse.questionResponses.filter { it.questionId == q.questionId }
                            if (qResponses.isNotEmpty()) {
                                when (q.questionType) {
                                    "RADIO" -> {
                                        val optId = qResponses.first().optionId
                                        val opt = q.options?.find { it.optionId == optId }
                                        q.value = opt?.optionValue
                                    }
                                    "MCQ" -> {
                                        val selectedVals = qResponses.mapNotNull { resp ->
                                            q.options?.find { it.optionId == resp.optionId }?.optionValue
                                        }
                                        q.value = selectedVals
                                    }
                                    "TEXT", "DATE" -> {
                                        q.value = qResponses.first().answerText
                                    }
                                }
                            }
                        }
                    }
                }

                NetworkResponse.Success(schemaDto)
            } catch (e: Exception) {
                NetworkResponse.Error("Failed to load form schema")
            }
        }
    }

    suspend fun saveSectionAnswers(
        benId: Long,
        formId: Int,
        section: CounsellingSectionDto,
        formVersionNumber: Int
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val versionId = formId * 1000 + formVersionNumber

                val draftResponse = counsellingRepository.getOrCreateDraft(benId, versionId)
                val responseId = draftResponse.formResponse.responseId

                val answers = mutableListOf<QuestionResponseEntity>()
                section.questions.filter { it.visible }.forEach { q ->
                    val valObj = q.value
                    if (valObj != null) {
                        when (q.questionType) {
                            "RADIO" -> {
                                val opt = q.options?.find { it.optionValue == valObj.toString() }
                                if (opt != null) {
                                    answers.add(
                                        QuestionResponseEntity(
                                            sectionResponseId = 0L,
                                            questionId = q.questionId,
                                            optionId = opt.optionId,
                                            answerText = null
                                        )
                                    )
                                }
                            }
                            "MCQ" -> {
                                val list = valObj as? List<*> ?: emptyList<Any>()
                                list.forEach { optVal ->
                                    val opt = q.options?.find { it.optionValue == optVal.toString() }
                                    if (opt != null) {
                                        answers.add(
                                            QuestionResponseEntity(
                                                sectionResponseId = 0L,
                                                questionId = q.questionId,
                                                optionId = opt.optionId,
                                                answerText = null
                                            )
                                        )
                                    }
                                }
                            }
                            "TEXT", "DATE" -> {
                                val textVal = valObj.toString()
                                if (textVal.isNotBlank()) {
                                    answers.add(
                                        QuestionResponseEntity(
                                            sectionResponseId = 0L,
                                            questionId = q.questionId,
                                            optionId = null,
                                            answerText = textVal
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                val completeForm = counsellingRepository.getFormDefinition(FormType.TB_COUNSELLING)
                Timber.d("saveSectionAnswers: formId=$formId, version=$formVersionNumber, versionId=$versionId, completeFormFound=${completeForm != null}")

                val activeVersionWithSections = completeForm?.versions?.find { it.version.versionId == versionId }
                Timber.d("saveSectionAnswers: activeVersionWithSectionsFound=${activeVersionWithSections != null}")

                val hasPostSubmit = activeVersionWithSections?.sections?.any { it.section.sectionPhase == "POST_SUBMIT" } ?: false

                var isFinalPreSubmit = false
                var isFinalPostSubmit = false
                var isLastSection = false

                if (hasPostSubmit) {
                    val preSubmitSections = activeVersionWithSections?.sections
                        ?.filter { it.section.sectionPhase == "PRE_SUBMIT" } ?: emptyList()
                    val finalPreSubmitSectionId = preSubmitSections.maxByOrNull { it.section.sectionOrder }?.section?.sectionId
                    isFinalPreSubmit = section.sectionPhase == "PRE_SUBMIT" && section.sectionId == finalPreSubmitSectionId
                    isFinalPostSubmit = section.sectionPhase == "POST_SUBMIT"
                    Timber.d("saveSectionAnswers (hasPostSubmit=true): sectionId=${section.sectionId}, phase=${section.sectionPhase}, finalPreSubmitSectionId=$finalPreSubmitSectionId, isFinalPreSubmit=$isFinalPreSubmit, isFinalPostSubmit=$isFinalPostSubmit")
                } else {
                    val sections = activeVersionWithSections?.sections?.sortedBy { it.section.sectionOrder } ?: emptyList()
                    isLastSection = sections.isNotEmpty() && sections.last().section.sectionId == section.sectionId
                    Timber.d("saveSectionAnswers (hasPostSubmit=false): sectionId=${section.sectionId}, lastSectionId=${sections.lastOrNull()?.section?.sectionId}, isLastSection=$isLastSection")
                }

                val shouldSync = isFinalPreSubmit || isFinalPostSubmit || isLastSection
                Timber.d("saveSectionAnswers: shouldSync=$shouldSync")

                if (isFinalPreSubmit) {
                    Timber.d("saveSectionAnswers: calling submitSectionE")
                    counsellingRepository.submitSectionE(responseId, answers)
                } else if (isFinalPostSubmit || isLastSection) {
                    Timber.d("saveSectionAnswers: calling submitSectionF")
                    counsellingRepository.submitSectionF(responseId, answers)
                } else {
                    val sections = activeVersionWithSections?.sections?.sortedBy { it.section.sectionOrder } ?: emptyList()
                    val currentIdx = sections.indexOfFirst { it.section.sectionId == section.sectionId }
                    val nextSectionId = if (currentIdx != -1 && currentIdx < sections.size - 1) {
                        sections[currentIdx + 1].section.sectionId
                    } else {
                        null
                    }
                    Timber.d("saveSectionAnswers: calling saveDraftSection, nextSectionId=$nextSectionId")
                    counsellingRepository.saveDraftSection(responseId, section.sectionId, nextSectionId, answers)
                }

                if (shouldSync) {
                    Timber.d("saveSectionAnswers: shouldSync is true, calling syncUnsyncedRecords")
                    counsellingRepository.syncUnsyncedRecords()
                    WorkerUtils.triggerAmritPushWorker(context)
                }

                true
            } catch (e: Exception) {
                Timber.e(e, "saveSectionAnswers failed")
                false
            }
        }
    }

    suspend fun getDraftResponse(benId: Long): CompleteFormResponse? {
        return withContext(Dispatchers.IO) {
            db.counsellingFormResponseDao().getFormResponseForBeneficiary(benId)
        }
    }
}


