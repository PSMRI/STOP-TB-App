package org.piramalswasthya.stoptb.database.room.dao.dynamicSchemaDao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.piramalswasthya.stoptb.model.dynamicEntity.*

@Dao
interface CounsellingFormResponseDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFormResponse(response: FormResponseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSectionResponses(sections: List<SectionResponseEntity>)

    // Single-row variant returning the generated id — needed when a caller (e.g. TPT Follow-up
    // History's refresh) must know a section snapshot's own id to insert its child answers.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSectionResponse(section: SectionResponseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestionResponses(responses: List<QuestionResponseEntity>)

    @Update
    suspend fun updateFormResponse(response: FormResponseEntity)

    @Update
    suspend fun updateSectionResponse(section: SectionResponseEntity)

    @Upsert
    suspend fun upsertQuestionResponses(responses: List<QuestionResponseEntity>)

    // A beneficiary can have more than one form response now (Counselling + Community CT +
    // Occupational CT, etc.), so every lookup below is scoped by formVersionId as well -
    // beneficiaryId alone no longer uniquely identifies a response. isHistorySnapshot = 0 is
    // required on every beneficiary+formVersion lookup below so a TPT Follow-up History
    // snapshot row (see refreshTptHistory) is never mistaken for the live response.
    @Transaction
    @Query("SELECT * FROM t_form_response WHERE beneficiaryId = :beneficiaryId AND formVersionId = :formVersionId AND isHistorySnapshot = 0 LIMIT 1")
    suspend fun getFormResponseForBeneficiary(beneficiaryId: Long, formVersionId: Int): CompleteFormResponse?

    @Transaction
    @Query("SELECT * FROM t_form_response WHERE responseId = :responseId LIMIT 1")
    suspend fun getFormResponseById(responseId: Long): CompleteFormResponse?

    @Transaction
    @Query("SELECT * FROM t_form_response WHERE syncStatus = 'UNSYNCED'")
    suspend fun getUnsyncedFormResponses(): List<CompleteFormResponse>

    // Scopes the unsynced sweep to specific form types (joins through the shared schema tables)
    // so Counselling's and Contact Tracing's sync workers only ever process their own rows now
    // that both live in this same table. History rows are always inserted with
    // syncStatus = 'SYNCED' directly so wouldn't normally match anyway; the isHistorySnapshot
    // filter is kept for defensive clarity.
    @Transaction
    @Query(
        """
        SELECT r.* FROM t_form_response r
        JOIN t_form_version v ON r.formVersionId = v.versionId
        JOIN t_dynamic_form f ON v.formId = f.formId
        WHERE r.syncStatus = 'UNSYNCED' AND f.formType IN (:formTypes) AND r.isHistorySnapshot = 0
        """
    )
    suspend fun getUnsyncedFormResponsesForTypes(formTypes: List<String>): List<CompleteFormResponse>

    @Query("SELECT * FROM t_form_response WHERE beneficiaryId = :beneficiaryId AND formVersionId = :formVersionId AND syncStatus = 'UNSYNCED' AND isHistorySnapshot = 0 LIMIT 1")
    suspend fun getUnsyncedResponseForBeneficiary(beneficiaryId: Long, formVersionId: Int): FormResponseEntity?

    @Query("SELECT * FROM t_form_response WHERE beneficiaryId = :beneficiaryId AND formVersionId = :formVersionId AND isHistorySnapshot = 0 AND (status = 'SUBMITTED' OR status = 'COMPLETE' OR status = 'COMPLETED') LIMIT 1")
    suspend fun getSubmittedOrCompleteResponseForBeneficiary(beneficiaryId: Long, formVersionId: Int): FormResponseEntity?

    @Transaction
    @Query("SELECT * FROM t_form_response")
    suspend fun getAllFormResponses(): List<CompleteFormResponse>

    @Query("DELETE FROM t_form_response WHERE beneficiaryId = :beneficiaryId AND formVersionId = :formVersionId")
    suspend fun deleteFormResponseForBeneficiary(beneficiaryId: Long, formVersionId: Int)

    @Query("DELETE FROM t_question_response WHERE sectionResponseId = :sectionResponseId")
    suspend fun deleteQuestionResponsesForSection(sectionResponseId: Long)


    // Captures the backend's own responseId (from submitBulkCounselling's response) onto the
    // live row that was just pushed — see ContactTracingRepositoryImpl.submitResponseBulk.
    @Query("UPDATE t_form_response SET backendResponseId = :backendResponseId WHERE responseId = :responseId")
    suspend fun updateBackendResponseId(responseId: Long, backendResponseId: Long)

    // Drives the "TPT Followup" row's visibility/Fill-vs-View state on ExamineBottomSheetFragment
    // — Room-backed so it survives process death/reopen rather than an in-memory Fragment flag.
    @Query(
        """
        SELECT r.status FROM t_form_response r
        JOIN t_form_version v ON r.formVersionId = v.versionId
        JOIN t_dynamic_form f ON v.formId = f.formId
        WHERE r.beneficiaryId = :beneficiaryId AND f.formType = :formType AND r.isHistorySnapshot = 0
        ORDER BY r.responseId DESC LIMIT 1
        """
    )
    fun observeFormResponseStatus(beneficiaryId: Long, formType: String): Flow<String?>

    // TPT Follow-up History — read-only snapshot rows (isHistorySnapshot = 1) synced in from the
    // History API, see ContactTracingRepositoryImpl.refreshTptHistory. Sorted by the backend's
    // own numeric responseId (INTEGER column, so this is a correct numeric sort, not
    // lexicographic) descending — latest submission first.
    @Transaction
    @Query(
        """
        SELECT * FROM t_form_response
        WHERE beneficiaryId = :beneficiaryId AND formVersionId = :formVersionId AND isHistorySnapshot = 1
        ORDER BY backendResponseId DESC
        """
    )
    fun getTptHistory(beneficiaryId: Long, formVersionId: Int): Flow<List<CompleteFormResponse>>

    // Delete-then-reinsert refresh: wipes only the exact historical rows the fresh History API
    // fetch is about to replace (matched by the backend's own responseId), cascading via the
    // existing FK(formResponseId -> responseId, CASCADE) on t_section_response to also clear
    // their section/question rows. Never touches a live (isHistorySnapshot = 0) row.
    @Query("DELETE FROM t_form_response WHERE isHistorySnapshot = 1 AND backendResponseId IN (:backendResponseIds)")
    suspend fun deleteHistoryByBackendResponseIds(backendResponseIds: List<Long>)

    // Phase-aware "existing response" lookup for TPT_FOLLOW_UP: unlike every other Contact
    // Tracing form (at most one live row per beneficiary+formVersion), TPT_FOLLOW_UP's
    // POST_SUBMIT phase is resubmitted repeatedly — one row per follow-up visit — so plain
    // beneficiary+formVersionId lookup can no longer tell which row is "the current" one.
    // Joining to a section_response whose section belongs to [phase] disambiguates it; each
    // TPT_FOLLOW_UP row only ever has section_responses for the ONE phase it was created for
    // (see ContactTracingRepositoryImpl.createPhaseScopedResponse), so this is unambiguous.
    @Transaction
    @Query("""
        SELECT DISTINCT r.* FROM t_form_response r
        JOIN t_section_response sr ON sr.formResponseId = r.responseId
        JOIN t_form_section fs ON fs.sectionId = sr.sectionId
        WHERE r.beneficiaryId = :beneficiaryId AND r.formVersionId = :formVersionId
          AND r.isHistorySnapshot = 0 AND fs.sectionPhase = :phase
        ORDER BY r.responseId DESC LIMIT 1
    """)
    suspend fun getLatestResponseForPhase(beneficiaryId: Long, formVersionId: Int, phase: String): CompleteFormResponse?

    // Count of completed POST_SUBMIT follow-up forms for this beneficiary — computed fresh from
    // actual submitted rows (not a manually-incremented counter), matching TPT_FOLLOW_UP's
    // repeatable, count-target follow-up model (see RegimenAdvised.requiredFollowUpCount).
    @Query("""
        SELECT COUNT(DISTINCT r.responseId) FROM t_form_response r
        JOIN t_section_response sr ON sr.formResponseId = r.responseId
        JOIN t_form_section fs ON fs.sectionId = sr.sectionId
        WHERE r.beneficiaryId = :beneficiaryId AND r.formVersionId = :formVersionId
          AND r.isHistorySnapshot = 0 AND r.status = 'COMPLETE' AND fs.sectionPhase = 'POST_SUBMIT'
    """)
    fun observeSubmittedFollowUpCount(beneficiaryId: Long, formVersionId: Int): Flow<Int>

    // One-shot variant of observeSubmittedFollowUpCount (same query, suspend instead of Flow) —
    // used by ContactTracingRepositoryImpl.isFollowUpTargetReached to enforce the regimen's
    // requiredFollowUpCount at form-open time itself, not just via the Examine row's Fill
    // button visibility (a hidden button alone can't stop a submission already in flight).
    @Query("""
        SELECT COUNT(DISTINCT r.responseId) FROM t_form_response r
        JOIN t_section_response sr ON sr.formResponseId = r.responseId
        JOIN t_form_section fs ON fs.sectionId = sr.sectionId
        WHERE r.beneficiaryId = :beneficiaryId AND r.formVersionId = :formVersionId
          AND r.isHistorySnapshot = 0 AND r.status = 'COMPLETE' AND fs.sectionPhase = 'POST_SUBMIT'
    """)
    suspend fun getSubmittedFollowUpCount(beneficiaryId: Long, formVersionId: Int): Int

    // Resolves a single-select (RADIO/DROPDOWN) question's answered optionValue from the
    // beneficiary's response for a given phase — used to read back TFU_REGIMEN_ADVISED's
    // selected regimen from the already-persisted PRE_SUBMIT answer, reusing the existing
    // save flow rather than storing the regimen in a separate/duplicate field.
    @Query("""
        SELECT qo.optionValue FROM t_form_response r
        JOIN t_section_response sr ON sr.formResponseId = r.responseId
        JOIN t_form_section fs ON fs.sectionId = sr.sectionId
        JOIN t_question_response qr ON qr.sectionResponseId = sr.sectionResponseId
        JOIN t_section_question sq ON sq.questionId = qr.questionId
        JOIN t_question_option qo ON qo.optionId = qr.optionId
        WHERE r.beneficiaryId = :beneficiaryId AND r.formVersionId = :formVersionId
          AND r.isHistorySnapshot = 0 AND fs.sectionPhase = :phase AND sq.questionUuid = :questionUuid
        LIMIT 1
    """)
    suspend fun getAnsweredOptionValue(beneficiaryId: Long, formVersionId: Int, phase: String, questionUuid: String): String?

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
