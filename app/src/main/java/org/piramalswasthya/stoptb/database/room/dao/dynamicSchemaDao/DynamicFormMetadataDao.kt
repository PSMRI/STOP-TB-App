package org.piramalswasthya.stoptb.database.room.dao.dynamicSchemaDao

import androidx.room.*
import org.piramalswasthya.stoptb.model.dynamicEntity.*
import org.piramalswasthya.stoptb.ui.counselling_activity.FormType

@Dao
interface DynamicFormMetadataDao {
    @Upsert
    suspend fun insertForm(form: DynamicFormEntity)

    @Upsert
    suspend fun insertVersion(version: FormVersionEntity)

    @Upsert
    suspend fun insertSections(sections: List<FormSectionEntity>)

    @Upsert
    suspend fun insertQuestions(questions: List<SectionQuestionEntity>)

    @Upsert
    suspend fun insertOptions(options: List<QuestionOptionEntity>)

    @Upsert
    suspend fun insertValidations(validations: List<QuestionValidationEntity>)

    @Upsert
    suspend fun insertConditions(conditions: List<OptionConditionEntity>)

    @Transaction
    @Query("SELECT * FROM t_dynamic_form WHERE formType = :formType LIMIT 1")
    suspend fun getFormDefinition(formType: FormType): CompleteFormDefinition?

    @Transaction
    @Query("SELECT * FROM t_form_section WHERE versionId = :versionId AND sectionPhase = :phase ORDER BY sectionOrder ASC")
    suspend fun getSectionsByPhase(versionId: Int, phase: String): List<FormSectionWithQuestions>

    @Query("SELECT versionId FROM t_form_version WHERE formId = (SELECT formId FROM t_dynamic_form WHERE formType = :formType LIMIT 1) AND isActive = 1 ORDER BY versionId DESC LIMIT 1")
    suspend fun getActiveVersionId(formType: FormType): Int?

    @Transaction
    @Query("SELECT f.* FROM t_dynamic_form f JOIN t_form_version v ON f.formId = v.formId WHERE v.versionId = :versionId LIMIT 1")
    suspend fun getFormDefinitionByVersionId(versionId: Int): CompleteFormDefinition?

    @Query("SELECT versionNumber FROM t_form_version WHERE formId = :formId AND isActive = 1 ORDER BY versionNumber DESC LIMIT 1")
    suspend fun getActiveVersionNumber(formId: Int): Int?
    @Transaction
    @Query("SELECT * FROM t_dynamic_form WHERE formId = :formId LIMIT 1")
    suspend fun getFormById(formId: Int): DynamicFormEntity?

    @Query("SELECT COUNT(*) FROM t_section_question WHERE serverQuestionId IS NULL")
    suspend fun getQuestionsWithNullServerIdCount(): Int

    @Query("SELECT COUNT(*) FROM t_question_option WHERE serverOptionId IS NULL")
    suspend fun getOptionsWithNullServerIdCount(): Int
}
