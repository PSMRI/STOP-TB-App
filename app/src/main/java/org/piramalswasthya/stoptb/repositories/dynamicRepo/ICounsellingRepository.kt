package org.piramalswasthya.stoptb.repositories.dynamicRepo

import kotlinx.coroutines.flow.Flow
import org.piramalswasthya.stoptb.model.dynamicEntity.*
import org.piramalswasthya.stoptb.ui.counselling_activity.FormType
import org.piramalswasthya.stoptb.ui.counselling_activity.SectionPhase

interface ICounsellingRepository {
    suspend fun getFormDefinition(formType: FormType): CompleteFormDefinition?
    suspend fun getSectionsByPhase(formType: FormType, phase: SectionPhase): List<FormSectionWithQuestions>
//    suspend fun downloadLatestFormSchema(formType: String): Boolean
    suspend fun downloadAndStoreAllForms(): Boolean
    suspend fun getOrCreateDraft(beneficiaryId: Long, formVersionId: Int): CompleteFormResponse
    suspend fun saveDraftSection(
        responseId: Long,
        sectionId: Int,
        nextSectionId: Int?,
        answers: List<QuestionResponseEntity>
    )
    suspend fun submitSectionE(responseId: Long, answers: List<QuestionResponseEntity>)
    suspend fun submitSectionF(responseId: Long, answers: List<QuestionResponseEntity>)
    suspend fun getCounsellingRecord(beneficiaryId: Long): Flow<CompleteFormResponse?>
    suspend fun syncUnsyncedRecords(): Boolean
    suspend fun fetchAndStoreCounsellingResponse(beneficiaryId: Long, formUuid: String): CounsellingResponseModel
    suspend fun fetchAndStoreCompletedBeneficiaries(): List<Long>?
    suspend fun revertFormStatus(responseId: Long, status: String)
}
