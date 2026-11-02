package org.piramalswasthya.stoptb.repositories.dynamicRepo

import org.piramalswasthya.stoptb.model.dynamicEntity.*
import org.piramalswasthya.stoptb.ui.counselling_activity.FormType
import org.piramalswasthya.stoptb.ui.counselling_activity.SectionPhase

interface ICounsellingRepository {
    suspend fun getFormDefinition(formType: FormType): CompleteFormDefinition?
    suspend fun getSectionsByPhase(formType: FormType, phase: SectionPhase): List<FormSectionWithQuestions>
//    suspend fun downloadLatestFormSchema(formType: String): Boolean
    /** @param forceUpdate true (login refresh) overwrites stored forms even when the version is unchanged. */
    suspend fun downloadAndStoreAllForms(forceUpdate: Boolean = false): Boolean
    /** Re-pulls all forms only when any of [formTypes] has a different versionNumber on the backend. */
    suspend fun refreshFormsIfOutdated(formTypes: List<FormType>): Boolean
    suspend fun getOrCreateDraft(beneficiaryId: Long, formVersionId: Int): CompleteFormResponse
    suspend fun saveDraftSection(
        responseId: Long,
        sectionId: Int,
        nextSectionId: Int?,
        answers: List<QuestionResponseEntity>
    )

    // Replaces answers for the given questions only. Leaves section completion untouched.
    suspend fun replaceQuestionAnswers(
        responseId: Long,
        sectionId: Int,
        questionIds: List<Int>,
        answers: List<QuestionResponseEntity>
    )
    suspend fun submitSectionE(responseId: Long, answers: List<QuestionResponseEntity>)
    suspend fun submitSectionF(responseId: Long, answers: List<QuestionResponseEntity>)
    suspend fun submitSectionGeneralInfo(responseId: Long, answers: List<QuestionResponseEntity>)
    suspend fun submitSectionBulk(responseId: Long, sectionId: Int): Boolean
    suspend fun syncUnsyncedRecords(): Boolean
    suspend fun fetchAndStoreCounsellingResponse(beneficiaryId: Long, formUuid: String): Boolean
    suspend fun fetchAndStoreCompletedBeneficiaries(): List<CompletedBeneficiaryStatus>?

    // Counselling status per beneficiary: from local responses when the changes feed keeps them
    // up to date, otherwise from the server (fetchAndStoreCompletedBeneficiaries).
    suspend fun getCompletedBeneficiaries(): List<CompletedBeneficiaryStatus>?

    // TB_COUNSELLING_V2 responses from the changes feed: the latest per beneficiary is saved with
    // its answers and derived status. Local UNSYNCED responses are kept.
    suspend fun applyFeedCounsellingResponses(responses: List<ServerCounsellingResponseDto>)
    suspend fun revertFormStatus(responseId: Long, status: String)
    suspend fun getLocalPreSubmitFilledCounts(): Map<Long, Int>
}
