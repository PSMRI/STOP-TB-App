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

    // isHistorySnapshot = 0 keeps this scoped to the live response only, so re-syncing the
    // current response (e.g. the village-wide Contact/TPT prefetch in PullTBFromAmritWorker)
    // never deletes previously stored TPT Follow-up History snapshot rows (see refreshTptHistory),
    // which would otherwise make the History screen come back empty despite valid server data.
    @Query("DELETE FROM t_form_response WHERE beneficiaryId = :beneficiaryId AND formVersionId = :formVersionId AND isHistorySnapshot = 0")
    suspend fun deleteFormResponseForBeneficiary(beneficiaryId: Long, formVersionId: Int)

    @Query("DELETE FROM t_question_response WHERE sectionResponseId = :sectionResponseId")
    suspend fun deleteQuestionResponsesForSection(sectionResponseId: Long)


    @Query("SELECT backendSectionResponseId FROM t_section_response WHERE backendSectionResponseId IN (:backendSectionResponseIds)")
    suspend fun getExistingBackendSectionResponseIds(backendSectionResponseIds: List<Long>): List<Long>


    @Query("UPDATE t_section_response SET backendSectionResponseId = :backendSectionResponseId WHERE formResponseId = :formResponseId AND sectionId = :sectionId")
    suspend fun updateBackendSectionResponseId(formResponseId: Long, sectionId: Int, backendSectionResponseId: Long)


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

    // The formVersionId the beneficiary's latest response is actually stored under — may differ
    // from the form schema's current "active" version, so callers must use this (not the active
    // version) when querying version-scoped data like getAnsweredOptionValueAnyPhase for that response.
    @Query(
        """
        SELECT r.formVersionId FROM t_form_response r
        JOIN t_form_version v ON r.formVersionId = v.versionId
        JOIN t_dynamic_form f ON v.formId = f.formId
        WHERE r.beneficiaryId = :beneficiaryId AND f.formType = :formType AND r.isHistorySnapshot = 0
        ORDER BY r.responseId DESC LIMIT 1
        """
    )
    fun observeFormResponseVersionId(beneficiaryId: Long, formType: String): Flow<Int?>

    // Phase-scoped status observer for TPT_FOLLOW_UP PRE_SUBMIT, avoiding newer POST_SUBMIT rows masking the submitted PRE_SUBMIT status.
    @Query(
        """
        SELECT r.status FROM t_form_response r
        JOIN t_section_response sr ON sr.formResponseId = r.responseId
        JOIN t_form_section fs ON fs.sectionId = sr.sectionId
        JOIN t_form_version v ON r.formVersionId = v.versionId
        JOIN t_dynamic_form f ON v.formId = f.formId
        WHERE r.beneficiaryId = :beneficiaryId AND f.formType = :formType AND r.isHistorySnapshot = 0
          AND fs.sectionPhase = 'PRE_SUBMIT'
        ORDER BY r.responseId DESC LIMIT 1
        """
    )
    fun observePreSubmitResponseStatus(beneficiaryId: Long, formType: String): Flow<String?>

    @Transaction
    @Query(
        """
        SELECT * FROM t_form_response r
        WHERE r.beneficiaryId = :beneficiaryId AND r.formVersionId = :formVersionId
          AND (
            r.isHistorySnapshot = 1
            OR (
                r.isHistorySnapshot = 0 AND r.status = 'COMPLETE' AND r.syncStatus = 'UNSYNCED'
                AND EXISTS (
                    SELECT 1 FROM t_section_response sr
                    JOIN t_form_section fs ON fs.sectionId = sr.sectionId
                    WHERE sr.formResponseId = r.responseId AND fs.sectionPhase = 'POST_SUBMIT'
                )
            )
          )
        ORDER BY
            r.isHistorySnapshot ASC,
            (SELECT MAX(sr.backendSectionResponseId) FROM t_section_response sr WHERE sr.formResponseId = r.responseId) DESC,
            r.responseId DESC
        """
    )
    fun getTptHistory(beneficiaryId: Long, formVersionId: Int): Flow<List<CompleteFormResponse>>

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
    //
    // Takes MAX(live count, history-snapshot count) rather than either alone. After a reinstall
    // the local DAO has NO live (isHistorySnapshot = 0) rows at all — those visits only exist
    // locally as isHistorySnapshot = 1 history-snapshot rows once
    // ContactTracingRepositoryImpl.fetchAndRefreshTptHistory bootstraps them from the backend —
    // so counting isHistorySnapshot = 0 alone always reports 0 after reinstall regardless of how
    // many were actually completed, leaving the Fill button visible forever. The history count is
    // always an accurate reflection of the backend's confirmed total (deduped 1:1 against the
    // backend's own sectionResponseId — see fetchAndRefreshTptHistory), while the live count is
    // only ever ahead of it in the brief window right after a fresh local submission, before the
    // next background refresh catches up — so MAX (not SUM) avoids ever double-counting the same
    // real-world visit if it's briefly represented both ways.
    @Query("""
        SELECT MAX(
            (SELECT COUNT(DISTINCT r.responseId) FROM t_form_response r
             JOIN t_section_response sr ON sr.formResponseId = r.responseId
             JOIN t_form_section fs ON fs.sectionId = sr.sectionId
             WHERE r.beneficiaryId = :beneficiaryId AND r.formVersionId = :formVersionId
               AND r.isHistorySnapshot = 0 AND r.status = 'COMPLETE' AND fs.sectionPhase = 'POST_SUBMIT'),
            (SELECT COUNT(DISTINCT r.responseId) FROM t_form_response r
             JOIN t_section_response sr ON sr.formResponseId = r.responseId
             JOIN t_form_section fs ON fs.sectionId = sr.sectionId
             WHERE r.beneficiaryId = :beneficiaryId AND r.formVersionId = :formVersionId
               AND r.isHistorySnapshot = 1 AND fs.sectionPhase = 'POST_SUBMIT')
        )
    """)
    fun observeSubmittedFollowUpCount(beneficiaryId: Long, formVersionId: Int): Flow<Int>

    // One-shot variant of observeSubmittedFollowUpCount (same query, suspend instead of Flow) —
    // used by ContactTracingRepositoryImpl.isFollowUpTargetReached to enforce the regimen's
    // requiredFollowUpCount at form-open time itself, not just via the Examine row's Fill
    // button visibility (a hidden button alone can't stop a submission already in flight). Same
    // MAX(live, history) reasoning as observeSubmittedFollowUpCount above.
    @Query("""
        SELECT MAX(
            (SELECT COUNT(DISTINCT r.responseId) FROM t_form_response r
             JOIN t_section_response sr ON sr.formResponseId = r.responseId
             JOIN t_form_section fs ON fs.sectionId = sr.sectionId
             WHERE r.beneficiaryId = :beneficiaryId AND r.formVersionId = :formVersionId
               AND r.isHistorySnapshot = 0 AND r.status = 'COMPLETE' AND fs.sectionPhase = 'POST_SUBMIT'),
            (SELECT COUNT(DISTINCT r.responseId) FROM t_form_response r
             JOIN t_section_response sr ON sr.formResponseId = r.responseId
             JOIN t_form_section fs ON fs.sectionId = sr.sectionId
             WHERE r.beneficiaryId = :beneficiaryId AND r.formVersionId = :formVersionId
               AND r.isHistorySnapshot = 1 AND fs.sectionPhase = 'POST_SUBMIT')
        )
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

    // Phase-agnostic variant of getAnsweredOptionValue, for forms like CONTACT_FOLLOW_UP that
    // have no PRE_SUBMIT/POST_SUBMIT phase split of their own (unlike TPT_FOLLOW_UP), so there is
    // no single phase value to filter on.
    @Query("""
        SELECT qo.optionValue FROM t_form_response r
        JOIN t_section_response sr ON sr.formResponseId = r.responseId
        JOIN t_question_response qr ON qr.sectionResponseId = sr.sectionResponseId
        JOIN t_section_question sq ON sq.questionId = qr.questionId
        JOIN t_question_option qo ON qo.optionId = qr.optionId
        WHERE r.beneficiaryId = :beneficiaryId AND r.formVersionId = :formVersionId
          AND r.isHistorySnapshot = 0 AND sq.questionUuid = :questionUuid
        ORDER BY r.responseId DESC LIMIT 1
    """)
    suspend fun getAnsweredOptionValueAnyPhase(beneficiaryId: Long, formVersionId: Int, questionUuid: String): String?

    @Query(
        """
    SELECT fr.beneficiaryId AS beneficiaryId, COUNT(DISTINCT sr.sectionId) AS filledCount
    FROM t_form_response fr
    JOIN t_section_response sr
        ON sr.formResponseId = fr.responseId
    JOIN t_form_section fs
        ON fs.sectionId = sr.sectionId
        AND fs.versionId = fr.formVersionId
    JOIN t_form_version fv
        ON fv.versionId = fr.formVersionId
    JOIN t_dynamic_form df
        ON df.formID = fv.formId
    WHERE fs.sectionPhase = 'PRE_SUBMIT'
      AND df.formType = 'TB_COUNSELLING_V2'
      AND (
        fr.status IN ('SUBMITTED', 'COMPLETE', 'COMPLETED')
        OR sr.completedAt IS NOT NULL
        OR EXISTS (
            SELECT 1
            FROM t_question_response qr
            WHERE qr.sectionResponseId = sr.sectionResponseId
        )
      )
    GROUP BY fr.beneficiaryId
    """
    )
    suspend fun getLocalPreSubmitFilledCounts(): List<BeneficiaryPreSubmitFilledCount>

    @Query(
        """
        SELECT DISTINCT r.beneficiaryId FROM t_form_response r
        JOIN t_form_version v ON r.formVersionId = v.versionId
        JOIN t_dynamic_form f ON v.formId = f.formId
        JOIN t_section_response sr ON sr.formResponseId = r.responseId
        JOIN t_question_response qr ON qr.sectionResponseId = sr.sectionResponseId
        WHERE f.formType = :formType AND r.isHistorySnapshot = 0
          AND (r.status = 'SUBMITTED' OR r.status = 'COMPLETE' OR r.status = 'COMPLETED')
        """
    )
    fun getFormDoneBenIds(formType: String): Flow<List<Long>>

    // Aggregate, all-beneficiaries variant of observePreSubmitResponseStatus — used to gate the
    // Contact Follow Up "done" badge for TPT_ELIGIBLE beneficiaries on TPT_FOLLOW_UP PRE_SUBMIT
    // actually being submitted, matching ExamineViewModel.isContactFollowUpDone's stricter check.
    @Query(
        """
        SELECT DISTINCT r.beneficiaryId FROM t_form_response r
        JOIN t_section_response sr ON sr.formResponseId = r.responseId
        JOIN t_form_section fs ON fs.sectionId = sr.sectionId
        JOIN t_form_version v ON r.formVersionId = v.versionId
        JOIN t_dynamic_form f ON v.formId = f.formId
        WHERE f.formType = :formType AND r.isHistorySnapshot = 0
          AND fs.sectionPhase = 'PRE_SUBMIT'
          AND (r.status = 'SUBMITTED' OR r.status = 'COMPLETE' OR r.status = 'COMPLETED')
        """
    )
    fun getPreSubmitDoneBenIds(formType: String): Flow<List<Long>>

    // One-shot, single-beneficiary variant of getFormDoneBenIds — lets a caller check local
    // submission state directly (e.g. ContactTracingRepositoryImpl.getContactTracingStatus)
    // instead of relying solely on a network round-trip, so the status reflects a form
    // submitted while offline and survives being reopened offline afterwards.
    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM t_form_response r
            JOIN t_form_version v ON r.formVersionId = v.versionId
            JOIN t_dynamic_form f ON v.formId = f.formId
            WHERE r.beneficiaryId = :beneficiaryId AND f.formType = :formType AND r.isHistorySnapshot = 0
              AND (r.status = 'SUBMITTED' OR r.status = 'COMPLETE' OR r.status = 'COMPLETED')
        )
        """
    )
    suspend fun isFormSubmittedLocally(beneficiaryId: Long, formType: String): Boolean

    // Same MAX(live, history) reasoning as observeSubmittedFollowUpCount above, applied across all
    // beneficiaries — feeds the beneficiary list's Examine badge (see
    // IContactTracingRepository.observeTptFollowUpTargetReachedBenIds), which would otherwise
    // wrongly report a reinstalled beneficiary's TPT Followup as incomplete. Unions the live-only
    // and history-only per-beneficiary+version counts, then takes the max of whichever rows exist
    // for that key in the outer GROUP BY (a key present on only one side is unaffected — MAX of a
    // single value is that value).
    @Query(
        """
        SELECT beneficiaryId, formVersionId, MAX(submittedCount) AS submittedCount FROM (
            SELECT r.beneficiaryId AS beneficiaryId, r.formVersionId AS formVersionId, COUNT(DISTINCT r.responseId) AS submittedCount
            FROM t_form_response r
            JOIN t_section_response sr ON sr.formResponseId = r.responseId
            JOIN t_form_section fs ON fs.sectionId = sr.sectionId
            WHERE r.isHistorySnapshot = 0 AND r.status = 'COMPLETE' AND fs.sectionPhase = 'POST_SUBMIT'
            GROUP BY r.beneficiaryId, r.formVersionId

            UNION ALL

            SELECT r.beneficiaryId AS beneficiaryId, r.formVersionId AS formVersionId, COUNT(DISTINCT r.responseId) AS submittedCount
            FROM t_form_response r
            JOIN t_section_response sr ON sr.formResponseId = r.responseId
            JOIN t_form_section fs ON fs.sectionId = sr.sectionId
            WHERE r.isHistorySnapshot = 1 AND fs.sectionPhase = 'POST_SUBMIT'
            GROUP BY r.beneficiaryId, r.formVersionId
        )
        GROUP BY beneficiaryId, formVersionId
        """
    )
    fun getAllSubmittedFollowUpCounts(): Flow<List<BeneficiaryFollowUpCount>>

    @Query(
        """
        SELECT r.beneficiaryId AS beneficiaryId, r.formVersionId AS formVersionId, qo.optionValue AS optionValue
        FROM t_form_response r
        JOIN t_section_response sr ON sr.formResponseId = r.responseId
        JOIN t_form_section fs ON fs.sectionId = sr.sectionId
        JOIN t_question_response qr ON qr.sectionResponseId = sr.sectionResponseId
        JOIN t_section_question sq ON sq.questionId = qr.questionId
        JOIN t_question_option qo ON qo.optionId = qr.optionId
        WHERE r.isHistorySnapshot = 0 AND fs.sectionPhase = 'PRE_SUBMIT' AND sq.questionUuid = :questionUuid
        """
    )
    fun getAllRegimenAnswers(questionUuid: String): Flow<List<BeneficiaryRegimenAnswer>>

    // Bulk, phase-agnostic variant of getAnsweredOptionValueAnyPhase — feeds the beneficiary
    // list's Examine badge (see IContactTracingRepository.observeTptEligibleBenIds), which needs
    // to know per-beneficiary whether CONTACT_FOLLOW_UP's clinical screening question was
    // answered TPT_ELIGIBLE so the badge's denominator can switch between x/2 and x/3.
    // CONTACT_FOLLOW_UP has no PRE_SUBMIT/POST_SUBMIT phase split of its own, same reasoning as
    // getAnsweredOptionValueAnyPhase, so no t_form_section join/phase filter is applied here.
    @Query(
        """
        SELECT r.beneficiaryId AS beneficiaryId, r.formVersionId AS formVersionId, qo.optionValue AS optionValue
        FROM t_form_response r
        JOIN t_section_response sr ON sr.formResponseId = r.responseId
        JOIN t_question_response qr ON qr.sectionResponseId = sr.sectionResponseId
        JOIN t_section_question sq ON sq.questionId = qr.questionId
        JOIN t_question_option qo ON qo.optionId = qr.optionId
        WHERE r.isHistorySnapshot = 0 AND sq.questionUuid = :questionUuid
        """
    )
    fun getAllClinicalScreeningStatusAnswers(questionUuid: String): Flow<List<BeneficiaryRegimenAnswer>>
}

data class BeneficiaryPreSubmitFilledCount(
    val beneficiaryId: Long,
    val filledCount: Int
)

data class BeneficiaryFollowUpCount(
    val beneficiaryId: Long,
    val formVersionId: Int,
    val submittedCount: Int
)

data class BeneficiaryRegimenAnswer(
    val beneficiaryId: Long,
    val formVersionId: Int,
    val optionValue: String
)
