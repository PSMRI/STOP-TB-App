package org.piramalswasthya.stoptb.model.dynamicEntity

import androidx.room.Embedded
import androidx.room.Relation

data class QuestionOptionWithConditions(
    @Embedded val option: QuestionOptionEntity,
    @Relation(parentColumn = "optionId", entityColumn = "optionId")
    val conditions: List<OptionConditionEntity>
)

data class SectionQuestionWithDetails(
    @Embedded val question: SectionQuestionEntity,
    @Relation(
        parentColumn = "questionId",
        entityColumn = "questionId",
        entity = QuestionOptionEntity::class
    )
    val options: List<QuestionOptionWithConditions>,
    @Relation(
        parentColumn = "questionId",
        entityColumn = "questionId",
        entity = QuestionValidationEntity::class
    )
    val validations: List<QuestionValidationEntity>
)

data class FormSectionWithQuestions(
    @Embedded val section: FormSectionEntity,
    @Relation(
        parentColumn = "sectionId",
        entityColumn = "sectionId",
        entity = SectionQuestionEntity::class
    )
    val questions: List<SectionQuestionWithDetails>
)

data class FormVersionWithSections(
    @Embedded val version: FormVersionEntity,
    @Relation(
        parentColumn = "versionId",
        entityColumn = "versionId",
        entity = FormSectionEntity::class
    )
    val sections: List<FormSectionWithQuestions>
)

data class CompleteFormDefinition(
    @Embedded val form: DynamicFormEntity,
    @Relation(
        parentColumn = "formId",
        entityColumn = "formId",
        entity = FormVersionEntity::class
    )
    val versions: List<FormVersionWithSections>
)

data class SectionResponseWithQuestions(
    @Embedded val sectionResponse: SectionResponseEntity,
    @Relation(
        parentColumn = "sectionResponseId",
        entityColumn = "sectionResponseId",
        entity = QuestionResponseEntity::class
    )
    val questionResponses: List<QuestionResponseEntity>
)

data class CompleteFormResponse(
    @Embedded val formResponse: FormResponseEntity,
    @Relation(
        parentColumn = "responseId",
        entityColumn = "formResponseId",
        entity = SectionResponseEntity::class
    )
    val sectionResponses: List<SectionResponseWithQuestions>
)
