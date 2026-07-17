package org.piramalswasthya.stoptb.database.room.dao.dynamicSchemaDao

import androidx.room.*
import org.piramalswasthya.stoptb.model.dynamicEntity.*

@Dao
interface CounsellingFormResponseDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFormResponse(response: FormResponseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSectionResponses(sections: List<SectionResponseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestionResponses(responses: List<QuestionResponseEntity>)

    @Update
    suspend fun updateFormResponse(response: FormResponseEntity)

    @Update
    suspend fun updateSectionResponse(section: SectionResponseEntity)

    @Upsert
    suspend fun upsertQuestionResponses(responses: List<QuestionResponseEntity>)

    @Transaction
    @Query("SELECT * FROM t_form_response WHERE beneficiaryId = :beneficiaryId LIMIT 1")
    suspend fun getFormResponseForBeneficiary(beneficiaryId: Long): CompleteFormResponse?

    @Transaction
    @Query("SELECT * FROM t_form_response WHERE beneficiaryId = :beneficiaryId LIMIT 1")
    fun getFormResponseForBeneficiaryFlow(beneficiaryId: Long): kotlinx.coroutines.flow.Flow<CompleteFormResponse?>

    @Transaction
    @Query("SELECT * FROM t_form_response WHERE responseId = :responseId LIMIT 1")
    suspend fun getFormResponseById(responseId: Long): CompleteFormResponse?

    @Transaction
    @Query("SELECT * FROM t_form_response WHERE beneficiaryId = :beneficiaryId AND status = 'DRAFT' LIMIT 1")
    suspend fun getDraftFormResponse(beneficiaryId: Long): CompleteFormResponse?

    @Transaction
    @Query("SELECT * FROM t_form_response WHERE beneficiaryId = :beneficiaryId AND status = 'SUBMITTED' LIMIT 1")
    suspend fun getSubmittedFormResponse(beneficiaryId: Long): CompleteFormResponse?

    @Transaction
    @Query("SELECT * FROM t_form_response WHERE beneficiaryId = :beneficiaryId AND status = 'COMPLETE' LIMIT 1")
    suspend fun getCompletedFormResponse(beneficiaryId: Long): CompleteFormResponse?

    @Transaction
    @Query("SELECT * FROM t_form_response WHERE syncStatus = 'UNSYNCED'")
    suspend fun getUnsyncedFormResponses(): List<CompleteFormResponse>

    @Query("SELECT * FROM t_form_response WHERE beneficiaryId = :beneficiaryId AND syncStatus = 'UNSYNCED' LIMIT 1")
    suspend fun getUnsyncedResponseForBeneficiary(beneficiaryId: Long): FormResponseEntity?

    @Query("SELECT * FROM t_form_response WHERE beneficiaryId = :beneficiaryId AND (status = 'SUBMITTED' OR status = 'COMPLETE' OR status = 'COMPLETED') LIMIT 1")
    suspend fun getSubmittedOrCompleteResponseForBeneficiary(beneficiaryId: Long): FormResponseEntity?

    @Transaction
    @Query("SELECT * FROM t_form_response")
    suspend fun getAllFormResponses(): List<CompleteFormResponse>

    @Query("DELETE FROM t_form_response WHERE beneficiaryId = :beneficiaryId")
    suspend fun deleteFormResponseForBeneficiary(beneficiaryId: Long)

    @Query("DELETE FROM t_question_response WHERE sectionResponseId = :sectionResponseId")
    suspend fun deleteQuestionResponsesForSection(sectionResponseId: Long)
    @Query(
        """
        SELECT fr.beneficiaryId AS beneficiaryId, COUNT(DISTINCT sr.sectionId) AS filledCount
        FROM t_form_response fr
        JOIN t_section_response sr ON sr.formResponseId = fr.responseId
        JOIN t_form_section fs ON fs.sectionId = sr.sectionId AND fs.versionId = fr.formVersionId
        WHERE fs.sectionPhase = 'PRE_SUBMIT'
          AND (
            fr.status IN ('SUBMITTED', 'COMPLETE', 'COMPLETED')
            OR sr.completedAt IS NOT NULL
            OR EXISTS (SELECT 1 FROM t_question_response qr WHERE qr.sectionResponseId = sr.sectionResponseId)
          )
        GROUP BY fr.beneficiaryId
        """
    )
    suspend fun getLocalPreSubmitFilledCounts(): List<BeneficiaryPreSubmitFilledCount>
}

data class BeneficiaryPreSubmitFilledCount(
    val beneficiaryId: Long,
    val filledCount: Int
)
