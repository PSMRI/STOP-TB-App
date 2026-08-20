package org.piramalswasthya.stoptb.repositories

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.piramalswasthya.stoptb.helpers.NetworkResponse
import org.piramalswasthya.stoptb.model.CounsellingOverviewData
import timber.log.Timber
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.helpers.Languages
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
import org.piramalswasthya.stoptb.helpers.isInternetAvailable
import org.piramalswasthya.stoptb.ui.counselling_activity.ActionType
import org.piramalswasthya.stoptb.ui.counselling_activity.FormType
import org.piramalswasthya.stoptb.ui.counselling_activity.QuestionType
import org.piramalswasthya.stoptb.ui.counselling_activity.SectionPhase
import org.piramalswasthya.stoptb.work.CounsellingSyncWorker
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
                val tbSuspected = tbDao.getTbSuspected(benId)   // <-- also pull the suspected-flow record
                val loggedInUser = preferenceDao.getLoggedInUser()?.name ?: ""

                val results = mutableListOf<String>()
                (tbDiag?.chestXRayResult ?: tbSuspected?.chestXRayResult)?.let { results.add("X-Ray: $it") }
                (tbDiag?.naatResult ?: tbSuspected?.naatResult)?.let { results.add("NAAT: $it") }
                (tbDiag?.liquidCultureResult ?: tbSuspected?.liquidCultureResult)?.let { results.add("Liquid Culture: $it") }
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

                var completeForm = counsellingRepository.getFormDefinition(FormType.TB_COUNSELLING_V2)
                try {
                    counsellingRepository.downloadAndStoreAllForms()
                    completeForm = counsellingRepository.getFormDefinition(FormType.TB_COUNSELLING_V2)
                    completeForm?.form?.formUuid?.let { formUuid ->
                        counsellingRepository.fetchAndStoreCounsellingResponse(benId, formUuid)
                    }
                } catch (e: Exception) {
                    Timber.w(e, "fetchAndStoreCounsellingResponse failed during overview pull for benId=$benId")
                }
                val activeFormVersionId = completeForm?.versions?.find { it.version.isActive }?.version?.versionId
                    ?: completeForm?.versions?.maxByOrNull { it.version.versionNumber }?.version?.versionId
                    ?: return@withContext NetworkResponse.Error("Schema definition not found")

                val formResponse = db.counsellingFormResponseDao().getFormResponseForBeneficiary(benId, activeFormVersionId)
                var currentStep = 0
                var completedSteps = 0
                var status = "DRAFT"
                
                val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH)
                var displayDateStr = sdf.format(java.util.Date())

                var preSubmitAnsweredCount = 0
                if (formResponse != null) {
                    status = formResponse.formResponse.status
                    displayDateStr = sdf.format(java.util.Date(formResponse.formResponse.createdAt))
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


                    val preSubmitSectionIds = sections
                        .filter { it.section.sectionPhase == "PRE_SUBMIT" }
                        .map { it.section.sectionId }
                        .toSet()
                    preSubmitAnsweredCount = formResponse.sectionResponses.count {
                        it.sectionResponse.sectionId in preSubmitSectionIds && it.questionResponses.isNotEmpty()
                    }
                }

                // A separate status-filtered query drives the Follow-Up button. This is
                // necessary because after post-submit there are two rows for the same
                // beneficiaryId and getFormResponseForBeneficiary (LIMIT 1) may return
                // either one, making the status unreliable for button-visibility decisions.
                val preSubmitSubmitted = db.counsellingFormResponseDao()
                    .getSubmittedOrCompleteResponseForBeneficiary(benId, activeFormVersionId) != null
                val preSubmitInProgress = !preSubmitSubmitted && preSubmitAnsweredCount > 0


                val overviewData = CounsellingOverviewData(
                    benId = benId,
                    patientName = "${ben.firstName ?: ""} ${ben.lastName ?: ""}".trim(),
                    nikshayId = ben.nikshayId ?: "",
                    counsellingDate = displayDateStr,
                    counsellingOfficer = loggedInUser,
                    regDate = ben.regDate,
                    beneficiaryId = ben.beneficiaryId.toString(),
                    ageGender = ageGender,
                    diagnosis = diagnosis,
                    currentStep = currentStep,
                    completedSteps = completedSteps,
                    status = status,
                    preSubmitSubmitted = preSubmitSubmitted,
                    preSubmitInProgress = preSubmitInProgress
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
                var completeForm = counsellingRepository.getFormDefinition(FormType.TB_COUNSELLING_V2)
                val hindiMissing = completeForm?.versions
                    ?.firstOrNull()?.sections
                    ?.any { it.section.sectionNameHindi.isNullOrEmpty() } ?: false
                if (completeForm == null || hindiMissing) {
                    val success = counsellingRepository.downloadAndStoreAllForms()
                    if (success) {
                        completeForm = counsellingRepository.getFormDefinition(FormType.TB_COUNSELLING_V2)
                    }
                }

                if (completeForm == null) {
                    return@withContext NetworkResponse.Error("Schema definition not found")
                }

                val activeVersionWithSections = completeForm.versions.find { it.version.isActive }
                    ?: completeForm.versions.maxByOrNull { it.version.versionNumber }
                    ?: return@withContext NetworkResponse.Error("No active version found")

                val filteredSectionsFromDb = counsellingRepository.getSectionsByPhase(FormType.TB_COUNSELLING_V2, phase)

                val isHindi = preferenceDao.getCurrentLanguage() == Languages.HINDI

                val showConditionTargets = mutableSetOf<Int>()
                filteredSectionsFromDb.forEach { sec ->
                    sec.questions.forEach { q ->
                        q.options.forEach { opt ->
                            opt.conditions.forEach { cond ->
                                val actionType = ActionType.from(cond.actionType)
                                if (actionType == ActionType.SHOW || actionType == ActionType.SHOW_QUESTION) {
                                    cond.targetQuestionId.let { showConditionTargets.add(it) }
                                }
                            }
                        }
                    }
                }

                val sectionsList = filteredSectionsFromDb.sortedBy { it.section.sectionOrder }.map { formSecWithQuestions ->
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
                                optionLabel = if (isHindi) opt.optionTextHindi.takeIf { !it.isNullOrEmpty() } ?: opt.optionText else opt.optionText,
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
                            questionUuid = q.questionUuid ?: getQuestionCode(q.questionId),
                            questionText = if (isHindi) q.questionTextHindi.takeIf { !it.isNullOrEmpty() } ?: q.questionText else q.questionText,
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
                        sectionName = if (isHindi) sec.sectionNameHindi.takeIf { !it.isNullOrEmpty() } ?: sec.sectionName else sec.sectionName,
                        sectionPhase = sec.sectionPhase,
                        isRequired = true,
                        displayOrder = sec.sectionOrder,
                        hasSubmitButton = (sec.sectionPhase == SectionPhase.PRE_SUBMIT.value && filteredSectionsFromDb.lastOrNull { it.section.sectionPhase == "PRE_SUBMIT" }?.section?.sectionId == sec.sectionId) || (sec.sectionPhase == "POST_SUBMIT"),
                        isEditable = sec.isEditable,
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

                val formUuid = completeForm.form.formUuid
                val fetchSuccess = counsellingRepository.fetchAndStoreCounsellingResponse(benId, formUuid)

                val draftResponse = counsellingRepository.getOrCreateDraft(benId, activeVersionWithSections.version.versionId)

                val isReadOnly = draftResponse.formResponse.status == "SUBMITTED" ||
                        draftResponse.formResponse.status == "COMPLETE" ||
                        draftResponse.formResponse.status == "COMPLETED"
                val hasLocalAnswers = draftResponse.sectionResponses.any { it.questionResponses.isNotEmpty() }
                
                if (isReadOnly && !hasLocalAnswers && !fetchSuccess) {
                    val isCampMode = preferenceDao.isCampModeEnabled()
                    val isHubConnected = preferenceDao.isCampHubConnected()
                    val isInternet = isInternetAvailable(context)
                    val isOffline = (isCampMode && !isHubConnected) || (!isCampMode && !isInternet)
                    if (isOffline) {
                        return@withContext NetworkResponse.Error("Unable to load counselling details. Please connect to the internet and try again.")
                    } else {
                        return@withContext NetworkResponse.Error("Failed to fetch counselling details from the server.")
                    }
                }

                schemaDto.sections.forEach { sec ->
                    val secResponse = draftResponse.sectionResponses.find { it.sectionResponse.sectionId == sec.sectionId }
                    if (secResponse != null) {
                        sec.isSubmitted = secResponse.sectionResponse.completedAt != null
                        sec.questions.forEach { q ->
                            val qResponses = secResponse.questionResponses.filter { it.questionId == q.questionId }
                            if (qResponses.isNotEmpty()) {
                                when (QuestionType.from(q.questionType)) {
                                    QuestionType.RADIO, QuestionType.DROPDOWN -> {
                                        val optId = qResponses.first().optionId
                                        val opt = q.options?.find { it.optionId == optId }
                                        q.value = opt?.optionValue
                                    }
                                    QuestionType.MCQ,  QuestionType.CHECKBOX -> {
                                        val selectedVals = qResponses.mapNotNull { resp ->
                                            q.options?.find { it.optionId == resp.optionId }?.optionValue
                                        }
                                        q.value = selectedVals
                                    }
                                    QuestionType.TEXT, QuestionType.DATE, QuestionType.NUMBER -> {
                                        q.value = qResponses.first().answerText
                                    }

                                    QuestionType.CHECKBOX_MULTI,
                                    QuestionType.DROPDOWN_MULTI,
                                    QuestionType.READONLY_NUMBER,
                                    QuestionType.READONLY_TEXT,
                                    QuestionType.NUMBER_PICKER,
                                    null -> {
                                        // No action required
                                    }
                                }
                            }
                        }
                    }
                }

                NetworkResponse.Success(schemaDto)
            } catch (e: Exception) {
                Timber.e(e, "getFormSchema failed for benId=$benId, phase=$phase")
                NetworkResponse.Error("Failed to load form schema")
            }
        }
    }

    // overrideTargetSectionId: when navigating backward, pass the previous section's ID so that
    // lastVisitedSectionId is recorded correctly for form-resume (forward nav leaves it null to
    // let the function derive the natural next section).
    suspend fun saveSectionAnswers(
        benId: Long,
        formId: Int,
        section: CounsellingSectionDto,
        formVersionNumber: Int,
        overrideTargetSectionId: Int? = null,
        isBackNavigation: Boolean = false
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
                            "RADIO", "DROPDOWN" -> {
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
                            "MCQ", "CHECKBOX" -> {
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
                            "TEXT", "DATE", "NUMBER" -> {
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
                if (isBackNavigation) {
                    Timber.d("saveSectionAnswers: back navigation for sectionId=${section.sectionId}, saving draft only (no submit), targetSectionId=$overrideTargetSectionId")
                    counsellingRepository.saveDraftSection(responseId, section.sectionId, overrideTargetSectionId, answers)
                    return@withContext true
                }

                val completeForm = counsellingRepository.getFormDefinition(FormType.TB_COUNSELLING_V2)
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
                    val forwardNextSectionId = if (currentIdx != -1 && currentIdx < sections.size - 1) {
                        sections[currentIdx + 1].section.sectionId
                    } else {
                        null
                    }
                    val targetSectionId = overrideTargetSectionId ?: forwardNextSectionId
                    Timber.d("saveSectionAnswers: calling saveDraftSection, targetSectionId=$targetSectionId (override=$overrideTargetSectionId)")
                    counsellingRepository.saveDraftSection(responseId, section.sectionId, targetSectionId, answers)
                }

                var success = true
                if (shouldSync) {
                    Timber.d("saveSectionAnswers: shouldSync is true, calling syncUnsyncedRecords")
                    val syncSuccess = counsellingRepository.syncUnsyncedRecords()
                    if (syncSuccess) {
                        WorkerUtils.triggerAmritPushWorker(context)
                    } else {
                        val isCampMode = preferenceDao.isCampModeEnabled()
                        val isHubConnected = preferenceDao.isCampHubConnected()
                        val isInternet = isInternetAvailable(context)
                        val isOffline = (isCampMode && !isHubConnected) || (!isCampMode && !isInternet)

                        if (isOffline) {
                            Timber.d("saveSectionAnswers: offline mode detected during sync failure, scheduling offline sync worker instead of reverting")
                            CounsellingSyncWorker.scheduleSync(context)
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(
                                    context,
                                    "Saved offline. It will be synced when connectivity is restored.",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        } else {
                            Timber.d("saveSectionAnswers: sync failed in online mode, saved locally for automatic background sync retry")
                            CounsellingSyncWorker.scheduleSync(context)
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(
                                    context,
                                    "Saved locally. It will sync automatically in the background.",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                            success = true
                        }
                    }
                } else {
                    Timber.d("saveSectionAnswers: non-final section, calling submitSectionBulk for sectionId=${section.sectionId}")
                    val bulkSuccess = counsellingRepository.submitSectionBulk(responseId, section.sectionId)
                    if (!bulkSuccess) {
                        val isCampMode = preferenceDao.isCampModeEnabled()
                        val isHubConnected = preferenceDao.isCampHubConnected()
                        val isInternet = isInternetAvailable(context)
                        val isOffline = (isCampMode && !isHubConnected) || (!isCampMode && !isInternet)

                        CounsellingSyncWorker.scheduleSync(context)

                        if (isOffline) {
                            Timber.d("saveSectionAnswers: offline mode detected for non-final sectionId=${section.sectionId}, staying silent until the final section")
                        } else {
                            Timber.d("saveSectionAnswers: submitSectionBulk failed in online mode for sectionId=${section.sectionId}, saved locally for automatic background sync retry")
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(
                                    context,
                                    "Saved locally. It will sync automatically in the background.",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }

                success
            } catch (e: Exception) {
                Timber.e(e, "saveSectionAnswers failed")
                false
            }
        }
    }
    suspend fun submitGeneralInfoAnswers(
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
                            "RADIO", "DROPDOWN" -> {
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
                            "MCQ", "CHECKBOX" -> {
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
                            "TEXT", "DATE", "NUMBER" -> {
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

                counsellingRepository.submitSectionGeneralInfo(responseId, answers)

                val syncSuccess = counsellingRepository.syncUnsyncedRecords()
                if (syncSuccess) {
                    WorkerUtils.triggerAmritPushWorker(context)
                } else {
                    CounsellingSyncWorker.scheduleSync(context)
                    val isCampMode = preferenceDao.isCampModeEnabled()
                    val isHubConnected = preferenceDao.isCampHubConnected()
                    val isInternet = isInternetAvailable(context)
                    val isOffline = (isCampMode && !isHubConnected) || (!isCampMode && !isInternet)
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context,
                            if (isOffline) "Saved offline. It will be synced when connectivity is restored."
                            else "Saved locally. It will sync automatically in the background.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }

                true
            } catch (e: Exception) {
                Timber.e(e, "submitGeneralInfoAnswers failed")
                false
            }
        }
    }

    suspend fun getDraftResponse(benId: Long): CompleteFormResponse? {
        return withContext(Dispatchers.IO) {
            val versionId = db.dynamicFormMetadataDao().getActiveVersionId(FormType.TB_COUNSELLING_V2)
                ?: return@withContext null
            db.counsellingFormResponseDao().getFormResponseForBeneficiary(benId, versionId)
        }
    }

    suspend fun hasPreSubmitBeenSubmitted(benId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            val versionId = db.dynamicFormMetadataDao().getActiveVersionId(FormType.TB_COUNSELLING_V2)
                ?: return@withContext false
            db.counsellingFormResponseDao()
                .getSubmittedOrCompleteResponseForBeneficiary(benId, versionId) != null
        }
    }

    suspend fun getFollowUpStatus(benId: Long, formId: Int): FollowUpStatus {
        return withContext(Dispatchers.IO) {
            val form = db.dynamicFormMetadataDao().getFormById(formId)
            val versionNumber = db.dynamicFormMetadataDao().getActiveVersionNumber(formId)
            val response = if (versionNumber != null) {
                db.counsellingFormResponseDao().getFormResponseForBeneficiary(benId, formId * 1000 + versionNumber)
            } else {
                null
            }
            
            val delay = if (form?.followUpDelayDays == null || form.followUpDelayDays == -1) {
                15
            } else {
                form.followUpDelayDays
            }

            FollowUpStatus(
                syncedAt = response?.formResponse?.syncedAt,
                followUpDelayDays = delay
            )
        }
    }

/*    suspend fun updateCounsellingDate(benId: Long, dateMillis: Long) {
        withContext(Dispatchers.IO) {
            val response = db.counsellingFormResponseDao().getFormResponseForBeneficiary(benId)
            if (response != null) {
                db.counsellingFormResponseDao().updateFormResponse(
                    response.formResponse.copy(
                        createdAt = dateMillis,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }
*/
    suspend fun revertFormStatus(responseId: Long, status: String) {
        withContext(Dispatchers.IO) {
            counsellingRepository.revertFormStatus(responseId, status)
        }
    }

    suspend fun resetLastVisitedSection(responseId: Long) {
        withContext(Dispatchers.IO) {
            val response = db.counsellingFormResponseDao().getFormResponseById(responseId)
            if (response != null) {
                db.counsellingFormResponseDao().updateFormResponse(
                    response.formResponse.copy(
                        lastVisitedSectionId = null,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    /// Saves General Info answers locally without syncing to the backend.
    suspend fun saveGeneralInfoDraftLocal(
        benId: Long,
        formId: Int,
        section: CounsellingSectionDto,
        formVersionNumber: Int
    ): Long? {
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
                            "RADIO", "DROPDOWN" -> {
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
                            "MCQ", "CHECKBOX" -> {
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
                            "TEXT", "DATE", "NUMBER" -> {
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

                counsellingRepository.saveDraftSection(responseId, section.sectionId, null, answers)
                responseId
            } catch (e: Exception) {
                Timber.e(e, "saveGeneralInfoDraftLocal failed")
                null
            }
        }
    }

    /// Saves General Info locally and syncs it with the backend.
    suspend fun saveGeneralInfoDraft(
        benId: Long,
        formId: Int,
        section: CounsellingSectionDto,
        formVersionNumber: Int
    ): Boolean {
        val responseId = saveGeneralInfoDraftLocal(benId, formId, section, formVersionNumber)
            ?: return false
        return withContext(Dispatchers.IO) {
            try {
                val bulkSuccess = counsellingRepository.submitSectionBulk(responseId, section.sectionId)
                if (!bulkSuccess) {
                    val isCampMode = preferenceDao.isCampModeEnabled()
                    val isHubConnected = preferenceDao.isCampHubConnected()
                    val isInternet = isInternetAvailable(context)
                    val isOffline = (isCampMode && !isHubConnected) || (!isCampMode && !isInternet)

                    CounsellingSyncWorker.scheduleSync(context)

                    if (isOffline) {
                        Timber.d("saveGeneralInfoDraft: offline mode detected for sectionId=${section.sectionId}, saved locally for background sync retry")
                    } else {
                        Timber.d("saveGeneralInfoDraft: submitSectionBulk failed in online mode for sectionId=${section.sectionId}, saved locally for automatic background sync retry")
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(
                                context,
                                "Saved locally. It will sync automatically in the background.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }

                true
            } catch (e: Exception) {
                Timber.e(e, "saveGeneralInfoDraft failed")
                false
            }
        }
    }
}

data class FollowUpStatus(
    val syncedAt: Long?,
    val followUpDelayDays: Int
)


