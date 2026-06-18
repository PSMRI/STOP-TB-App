package org.piramalswasthya.stoptb

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.piramalswasthya.stoptb.database.room.InAppDb
import org.piramalswasthya.stoptb.model.dynamicEntity.*
import org.piramalswasthya.stoptb.network.AmritApiService
import org.piramalswasthya.stoptb.ui.counselling_activity.FormType
import java.lang.reflect.Proxy

/**
 * Technical demonstration demonstrating the mapping of a Dynamic Form JSON response 
 * into 10 Room Database tables (7 metadata tables and 3 response tables).
 *
 * Flow demonstrated:
 * API JSON Response -> Parsed DTO (FormSchemaDto) -> Room Database Entities -> Room Database Tables -> Stored Data
 */
@RunWith(AndroidJUnit4::class)
class DynamicFormPersistDemo {

    private lateinit var db: InAppDb
    private lateinit var metadataDao: org.piramalswasthya.stoptb.database.room.dao.dynamicSchemaDao.DynamicFormMetadataDao
    private lateinit var responseDao: org.piramalswasthya.stoptb.database.room.dao.dynamicSchemaDao.CounsellingFormResponseDao

    @Before
    fun initDb() {
        // Create an in-memory instance of Room Database to isolate demo data
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            InAppDb::class.java
        ).allowMainThreadQueries().build()

        metadataDao = db.dynamicFormMetadataDao()
        responseDao = db.counsellingFormResponseDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun demonstrateDynamicFormPersistenceFlow() = runBlocking {
        // STEP 1: Define a Sample Dynamic Form API JSON Response (Definition Schema)
        val dummyFormJson = """
        {
          "success": true,
          "message": null,
          "data": {
            "formId": 101,
            "formUuid": "TB_COUNSELLING_DEMO",
            "formName": "TB Counselling Demo Form",
            "formType": "TB_COUNSELLING",
            "isActive": true,
            "versionNumber": 1,
            "sections": [
              {
                "sectionId": 10101,
                "sectionUuid": "DEMO_SECTION_A",
                "sectionName": "A. Patient Info",
                "sectionPhase": "PRE_SUBMIT",
                "isRequired": true,
                "displayOrder": 1,
                "hasSubmitButton": false,
                "questions": [
                  {
                    "questionId": 10101001,
                    "questionUuid": "PATIENT_NAME",
                    "questionText": "What is the patient's name?",
                    "questionType": "TEXT",
                    "isMandatory": true,
                    "displayOrder": 1,
                    "maxLength": 100,
                    "defaultValue": null,
                    "containsPii": true,
                    "visibleByDefault": true,
                    "options": [],
                    "validations": [
                      {
                        "validationId": 101010011,
                        "validationType": "REGEX",
                        "validationParam": "^[A-Za-z ]+$",
                        "errorMessage": "Name must contain only alphabets and spaces"
                      }
                    ]
                  },
                  {
                    "questionId": 10101002,
                    "questionUuid": "HIV_STATUS",
                    "questionText": "What is the patient's HIV status?",
                    "questionType": "RADIO",
                    "isMandatory": true,
                    "displayOrder": 2,
                    "maxLength": null,
                    "defaultValue": null,
                    "containsPii": false,
                    "visibleByDefault": true,
                    "options": [
                      {
                        "optionId": 1010100201,
                        "optionLabel": "Reactive",
                        "optionValue": "REACTIVE",
                        "displayOrder": 1,
                        "conditions": [
                          {
                            "conditionId": 10101002011,
                            "actionType": "SHOW",
                            "targetQuestionId": 10101003,
                            "targetSectionId": null,
                            "targetQuestionUuid": "ART_STATUS",
                            "targetSectionUuid": null
                          }
                        ]
                      },
                      {
                        "optionId": 1010100202,
                        "optionLabel": "Non-Reactive",
                        "optionValue": "NON_REACTIVE",
                        "displayOrder": 2,
                        "conditions": []
                      }
                    ],
                    "validations": []
                  },
                  {
                    "questionId": 10101003,
                    "questionUuid": "ART_STATUS",
                    "questionText": "Is the patient on ART treatment?",
                    "questionType": "RADIO",
                    "isMandatory": false,
                    "displayOrder": 3,
                    "maxLength": null,
                    "defaultValue": null,
                    "containsPii": false,
                    "visibleByDefault": false,
                    "options": [
                      {
                        "optionId": 1010100301,
                        "optionLabel": "Yes",
                        "optionValue": "YES",
                        "displayOrder": 1,
                        "conditions": []
                      },
                      {
                        "optionId": 1010100302,
                        "optionLabel": "No",
                        "optionValue": "NO",
                        "displayOrder": 2,
                        "conditions": []
                      }
                    ],
                    "validations": []
                  }
                ]
              }
            ]
          }
        }
        """.trimIndent()

        // STEP 2: Parse JSON into Data Transfer Object (DTO)
        // This parses the JSON response using Gson with a type-safe TypeToken
        val type = object : com.google.gson.reflect.TypeToken<org.piramalswasthya.stoptb.model.dynamicModel.ApiResponse<FormSchemaDto>>() {}.type
        val apiResponse = Gson().fromJson<org.piramalswasthya.stoptb.model.dynamicModel.ApiResponse<FormSchemaDto>>(dummyFormJson, type)
        assertNotNull(apiResponse)
        assertTrue(apiResponse.success)

        val formSchemaDto = apiResponse.data!!
        assertEquals("TB_COUNSELLING_DEMO", formSchemaDto.formUuid)

        // STEP 3: Map Parsed DTO properties into 7 Room Metadata Entities and Insert Step-by-Step
        
        // A. Table: t_dynamic_form
        val formEntity = DynamicFormEntity(
            formId = formSchemaDto.formId.toInt(),
            formUuid = formSchemaDto.formUuid ?: "FORM_${formSchemaDto.formId}",
            formName = formSchemaDto.formName,
            formType = formSchemaDto.formType ?: ""
        )
        metadataDao.insertForm(formEntity)

        // B. Table: t_form_version
        // Primary key versionId is generated as: formId * 1000 + versionNumber
        val versionId = formEntity.formId * 1000 + formSchemaDto.versionNumber
        val versionEntity = FormVersionEntity(
            versionId = versionId,
            formId = formEntity.formId,
            versionNumber = formSchemaDto.versionNumber,
            isActive = formSchemaDto.isActive
        )
        metadataDao.insertVersion(versionEntity)

        // Lists to store nested entities for bulk insertion
        val sectionsList = mutableListOf<FormSectionEntity>()
        val questionsList = mutableListOf<SectionQuestionEntity>()
        val optionsList = mutableListOf<QuestionOptionEntity>()
        val validationsList = mutableListOf<QuestionValidationEntity>()
        val conditionsList = mutableListOf<OptionConditionEntity>()

        // Map Sections, Questions, Options, Conditions, Validations
        formSchemaDto.sections.forEach { sectionDto ->
            val sectionId = sectionDto.sectionId.toInt()
            
            // C. Table: t_form_section
            sectionsList.add(
                FormSectionEntity(
                    sectionId = sectionId,
                    versionId = versionId,
                    sectionName = sectionDto.sectionName,
                    sectionOrder = sectionDto.displayOrder ?: 1,
                    sectionPhase = sectionDto.sectionPhase ?: "PRE_SUBMIT"
                )
            )

            sectionDto.questions.forEach { questionDto ->
                val questionId = questionDto.questionId ?: 0
                
                // D. Table: t_section_question
                questionsList.add(
                    SectionQuestionEntity(
                        questionId = questionId,
                        sectionId = sectionId,
                        questionText = questionDto.label,
                        questionType = questionDto.type,
                        questionOrder = questionDto.displayOrder ?: 1,
                        isRequired = questionDto.isMandatory
                    )
                )

                // E. Table: t_question_option (if RADIO or MCQ)
                questionDto.getOptionItems().forEach { optionDto ->
                    optionsList.add(
                        QuestionOptionEntity(
                            optionId = optionDto.optionId,
                            questionId = questionId,
                            optionText = optionDto.optionLabel,
                            optionValue = optionDto.optionValue,
                            optionOrder = optionDto.displayOrder
                        )
                    )

                    // F. Table: t_option_condition
                    optionDto.conditions.forEach { condDto ->
                        conditionsList.add(
                            OptionConditionEntity(
                                conditionId = condDto.conditionId,
                                optionId = optionDto.optionId,
                                targetQuestionId = condDto.targetQuestionId ?: 0,
                                actionType = condDto.actionType,
                                isFulfilledValue = true
                            )
                        )
                    }
                }

                // G. Table: t_question_validation
                questionDto.validations.forEach { valDto ->
                    validationsList.add(
                        QuestionValidationEntity(
                            validationId = valDto.validationId,
                            questionId = questionId,
                            validationType = valDto.validationType,
                            validationValue = valDto.validationParam,
                            errorMessage = valDto.errorMessage
                        )
                    )
                }
            }
        }

        // Bulk insert all items into the database metadata tables
        metadataDao.insertSections(sectionsList)
        metadataDao.insertQuestions(questionsList)
        metadataDao.insertOptions(optionsList)
        metadataDao.insertConditions(conditionsList)
        metadataDao.insertValidations(validationsList)

        // STEP 4: Verify Metadata Tables are Stored Correctly via CompleteFormDefinition Relation POJO
        val completeDef = metadataDao.getFormDefinition(FormType.TB_COUNSELLING)
        assertNotNull(completeDef)
        assertEquals(101, completeDef!!.form.formId)
        assertEquals("TB Counselling Demo Form", completeDef.form.formName)

        val retrievedVersion = completeDef.versions[0]
        assertEquals(1, retrievedVersion.version.versionNumber)
        assertEquals(1, retrievedVersion.sections.size)

        val retrievedSection = retrievedVersion.sections[0]
        assertEquals(10101, retrievedSection.section.sectionId)
        assertEquals("A. Patient Info", retrievedSection.section.sectionName)
        assertEquals(3, retrievedSection.questions.size)

        // Verify TEXT Question (PATIENT_NAME) and Validation
        val nameQuestion = retrievedSection.questions.find { it.question.questionId == 10101001 }
        assertNotNull(nameQuestion)
        assertEquals("TEXT", nameQuestion!!.question.questionType)
        assertEquals(1, nameQuestion.validations.size)
        assertEquals("REGEX", nameQuestion.validations[0].validationType)
        assertEquals("^[A-Za-z ]+$", nameQuestion.validations[0].validationValue)

        // Verify RADIO Question (HIV_STATUS) and Option Conditions
        val hivQuestion = retrievedSection.questions.find { it.question.questionId == 10101002 }
        assertNotNull(hivQuestion)
        assertEquals("RADIO", hivQuestion!!.question.questionType)
        assertEquals(2, hivQuestion.options.size)

        val reactiveOption = hivQuestion.options.find { it.option.optionId == 1010100201 }
        assertNotNull(reactiveOption)
        assertEquals("Reactive", reactiveOption!!.option.optionText)
        assertEquals(1, reactiveOption.conditions.size)
        assertEquals("SHOW", reactiveOption.conditions[0].actionType)
        assertEquals(10101003, reactiveOption.conditions[0].targetQuestionId) // Points to ART_STATUS

        // STEP 5: Simulate User Session / Inputs Stored in Response Tables
        val beneficiaryId = 999888777L
        
        // H. Table: t_form_response
        // Setup initial draft response for this beneficiary
        val formResponse = FormResponseEntity(
            responseId = 5001L,
            beneficiaryId = beneficiaryId,
            formVersionId = versionId,
            status = "DRAFT",
            lastVisitedSectionId = null,
            syncStatus = "UNSYNCED"
        )
        val insertedFormResponseId = responseDao.insertFormResponse(formResponse)
        assertEquals(5001L, insertedFormResponseId)

        // I. Table: t_section_response
        // Setup initial response markers for each section of the form
        val sectionResponse = SectionResponseEntity(
            sectionResponseId = 6001L,
            formResponseId = insertedFormResponseId,
            sectionId = 10101,
            completedAt = null
        )
        responseDao.insertSectionResponses(listOf(sectionResponse))

        // J. Table: t_question_response
        // User answers the questions in Section A:
        // - PATIENT_NAME answer is "Amit Kumar" (TEXT input)
        // - HIV_STATUS option is reactive option (optionId 1010100201) (RADIO selection)
        // - ART_STATUS option is Yes option (optionId 1010100301) (RADIO selection)
        val answers = listOf(
            QuestionResponseEntity(
                questionResponseId = 7001L,
                sectionResponseId = 6001L,
                questionId = 10101001,
                optionId = null,
                answerText = "Amit Kumar"
            ),
            QuestionResponseEntity(
                questionResponseId = 7002L,
                sectionResponseId = 6001L,
                questionId = 10101002,
                optionId = 1010100201,
                answerText = null
            ),
            QuestionResponseEntity(
                questionResponseId = 7003L,
                sectionResponseId = 6001L,
                questionId = 10101003,
                optionId = 1010100301,
                answerText = null
            )
        )
        responseDao.insertQuestionResponses(answers)

        // STEP 6: Retrieve and Validate Stored User Responses
        val completeResponse = responseDao.getFormResponseForBeneficiary(beneficiaryId)
        assertNotNull(completeResponse)
        assertEquals("DRAFT", completeResponse!!.formResponse.status)
        assertEquals(1, completeResponse.sectionResponses.size)

        val retrievedSecResponse = completeResponse.sectionResponses[0]
        assertEquals(10101, retrievedSecResponse.sectionResponse.sectionId)
        assertEquals(3, retrievedSecResponse.questionResponses.size)

        // Assert individual answers
        val nameAnswer = retrievedSecResponse.questionResponses.find { it.questionId == 10101001 }
        assertNotNull(nameAnswer)
        assertNull(nameAnswer!!.optionId)
        assertEquals("Amit Kumar", nameAnswer.answerText)

        val hivAnswer = retrievedSecResponse.questionResponses.find { it.questionId == 10101002 }
        assertNotNull(hivAnswer)
        assertEquals(1010100201, hivAnswer!!.optionId)
        assertNull(hivAnswer.answerText)

        val artAnswer = retrievedSecResponse.questionResponses.find { it.questionId == 10101003 }
        assertNotNull(artAnswer)
        assertEquals(1010100301, artAnswer!!.optionId)
        assertNull(artAnswer.answerText)
    }
}
