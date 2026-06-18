package org.piramalswasthya.stoptb.database.room.dao.dynamicSchemaDao

import androidx.room.*
import org.piramalswasthya.stoptb.model.dynamicEntity.*
import org.piramalswasthya.stoptb.ui.counselling_activity.FormType

@Dao
interface DynamicFormMetadataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertForm(form: DynamicFormEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVersion(version: FormVersionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSections(sections: List<FormSectionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<SectionQuestionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOptions(options: List<QuestionOptionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertValidations(validations: List<QuestionValidationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConditions(conditions: List<OptionConditionEntity>)

    @Transaction
    @Query("SELECT * FROM t_dynamic_form WHERE formType = :formType LIMIT 1")
    suspend fun getFormDefinition(formType: FormType): CompleteFormDefinition?

    @Transaction
    @Query("SELECT * FROM t_form_section WHERE versionId = :versionId AND sectionPhase = :phase ORDER BY sectionOrder ASC")
    suspend fun getSectionsByPhase(versionId: Int, phase: String): List<FormSectionWithQuestions>

    @Query("SELECT versionId FROM t_form_version WHERE formId = (SELECT formId FROM t_dynamic_form WHERE formType = :formType LIMIT 1) AND isActive = 1 LIMIT 1")
    suspend fun getActiveVersionId(formType: FormType): Int?

    @Transaction
    @Query("SELECT f.* FROM t_dynamic_form f JOIN t_form_version v ON f.formId = v.formId WHERE v.versionId = :versionId LIMIT 1")
    suspend fun getFormDefinitionByVersionId(versionId: Int): CompleteFormDefinition?

    @Query("SELECT versionNumber FROM t_form_version WHERE formId = :formId AND isActive = 1 LIMIT 1")
    suspend fun getActiveVersionNumber(formId: Int): Int?
}
