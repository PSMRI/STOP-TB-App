package org.piramalswasthya.stoptb.repositories.contactTracing

import kotlinx.coroutines.flow.Flow
import org.piramalswasthya.stoptb.helpers.NetworkResponse
import org.piramalswasthya.stoptb.model.contactTracing.ContactTracingStatus
import org.piramalswasthya.stoptb.model.dynamicEntity.CompleteFormDefinition
import org.piramalswasthya.stoptb.model.dynamicEntity.CompleteFormResponse
import org.piramalswasthya.stoptb.model.dynamicEntity.FormResponseEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.QuestionResponseEntity
import org.piramalswasthya.stoptb.ui.contact_tracing.ClinicalScreeningStatus
import org.piramalswasthya.stoptb.ui.contact_tracing.RegimenAdvised
import org.piramalswasthya.stoptb.ui.counselling_activity.FormType
import org.piramalswasthya.stoptb.ui.counselling_activity.SectionPhase

interface IContactTracingRepository {

    // Reads a form's cached schema from local DB for the given form type.
    suspend fun getFormDefinition(formType: FormType): CompleteFormDefinition?

    // Returns the form schema for formType, fetching and caching it from the server on a cache miss.
    suspend fun getFormSchema(formType: FormType): NetworkResponse<CompleteFormDefinition>

    suspend fun getCompleteResponse(responseId: Long): CompleteFormResponse?

    // Checks if a response already exists for this beneficiary+form, without creating one (used to decide View vs Fill mode).
    suspend fun getExistingContactResponse(
        indexCaseBenId: Long,
        formVersionId: Int
    ): FormResponseEntity?

    suspend fun getOrCreateContactResponse(
        indexCaseBenId: Long,
        formVersionId: Int
    ): FormResponseEntity

    // Fetches the beneficiary's previously submitted answers for this form type from the server and stores them locally for prefill.
    suspend fun fetchAndStoreContactResponse(
        beneficiaryId: Long,
        formType: FormType,
        formVersionId: Int
    ): Boolean

    // Fetches previously submitted answers for all beneficiaries in the village for this form type and stores them locally.
    suspend fun fetchAndStoreVillageContactResponses(
        villageId: Int,
        formType: FormType,
        formVersionId: Int
    ): Boolean

    suspend fun saveSectionAnswers(
        responseId: Long,
        sectionId: Int,
        answers: List<QuestionResponseEntity>,
        status: String = "DRAFT"
    )

    suspend fun submitResponse(responseId: Long, finalStatus: String)
    suspend fun submitResponseBulk(responseId: Long, phaseFilter: String? = null): Boolean

    suspend fun syncUnsyncedResponses(): Boolean

    // Streams the beneficiary's current form status, used to decide Fill vs View for the Examine screen row.
    fun observeResponseStatus(beneficiaryId: Long, formType: FormType): Flow<String?>

    // Streams the formVersionId the beneficiary's latest response is actually stored under — may
    // differ from the form schema's current "active" version, so callers must use this (not the
    // active version) when looking up version-scoped answers like getClinicalScreeningStatus.
    fun observeResponseFormVersionId(beneficiaryId: Long, formType: FormType): Flow<Int?>

    // Streams PRE_SUBMIT status for the formType, keeping TPT Followup visibility unaffected by newer POST_SUBMIT drafts.
    fun observePreSubmitResponseStatus(beneficiaryId: Long, formType: FormType): Flow<String?>

    // Streams the beneficiary's saved TPT Follow-up history entries, latest first.
    fun getTptHistory(beneficiaryId: Long, formVersionId: Int): Flow<List<CompleteFormResponse>>

    // Fetches TPT Follow-up history from the server and refreshes local storage to match, avoiding duplicates.
    suspend fun fetchAndRefreshTptHistory(beneficiaryId: Long, formVersionId: Int): Boolean

    // Finds the beneficiary's current response for a given form phase, or null if a new one should be started.
    suspend fun getExistingContactResponseForPhase(
        beneficiaryId: Long,
        formVersionId: Int,
        phase: SectionPhase
    ): FormResponseEntity?

    // Creates a new response scoped to one phase's sections only, used for repeatable forms like TPT_FOLLOW_UP.
    suspend fun createPhaseScopedResponse(
        beneficiaryId: Long,
        formVersionId: Int,
        sectionIds: List<Int>
    ): FormResponseEntity

    // Reads back the beneficiary's already-selected TPT regimen answer and resolves it to a RegimenAdvised value.
    suspend fun getRegimenAdvised(beneficiaryId: Long, formVersionId: Int): RegimenAdvised?

    // Reads back the beneficiary's CONTACT_FOLLOW_UP clinical screening status answer (Tpt Eligible / Full Treatment / No Treatment).
    suspend fun getClinicalScreeningStatus(beneficiaryId: Long, formVersionId: Int): ClinicalScreeningStatus?

    // Streams the beneficiary's completed TPT follow-up form count, computed live from submitted rows.
    fun observeSubmittedFollowUpCount(beneficiaryId: Long, formVersionId: Int): Flow<Int>

    // Checks whether the beneficiary has completed enough follow-up forms to meet their regimen's required count.
    suspend fun isFollowUpTargetReached(beneficiaryId: Long, formVersionId: Int): Boolean

    // Fetches Community and Occupational contact tracing completion status to drive the type bottom sheet's tick/cross indicators.
    suspend fun getContactTracingStatus(beneficiaryId: Long): ContactTracingStatus

    // "Done" requires the follow-on TPT_FOLLOW_UP PRE_SUBMIT to also be submitted for
    // TPT_ELIGIBLE beneficiaries — matches ExamineViewModel.isContactFollowUpDone so the list
    // badge and the Examine screen's per-row Fill/View state never disagree.
    fun observeContactFollowUpDoneBenIds(): Flow<List<Long>>

    fun observeTptFollowUpTargetReachedBenIds(): Flow<List<Long>>

    // Beneficiaries whose CONTACT_FOLLOW_UP clinical screening answer is TPT_ELIGIBLE — drives
    // whether the Counselling Officer's Examine badge total is x/2 (TB Screening + Contact
    // Follow Up only) or x/3 (TPT Follow Up also required).
    fun observeTptEligibleBenIds(): Flow<List<Long>>
}
