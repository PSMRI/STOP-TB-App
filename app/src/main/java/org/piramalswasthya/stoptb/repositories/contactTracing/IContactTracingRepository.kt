package org.piramalswasthya.stoptb.repositories.contactTracing

import kotlinx.coroutines.flow.Flow
import org.piramalswasthya.stoptb.model.contactTracing.ContactTracingCompleteResponse
import org.piramalswasthya.stoptb.model.contactTracing.ContactTracingQuestionResponseEntity
import org.piramalswasthya.stoptb.model.contactTracing.ContactTracingResponseEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.CompleteFormDefinition
import org.piramalswasthya.stoptb.ui.counselling_activity.FormType

interface IContactTracingRepository {

    /**
     * Parses the bundled static Contact Tracing JSON (a stand-in until the backend
     * endpoints are deployed) and seeds it into the reused generic dynamic-form schema tables.
     */
    suspend fun seedFormsFromStaticJson(): Boolean

    /** Reads a form's schema back via the unmodified, already-generic DynamicFormMetadataDao. */
    suspend fun getFormDefinition(formType: FormType): CompleteFormDefinition?

    suspend fun getCompleteResponse(responseId: Long): ContactTracingCompleteResponse?

    fun getResponsesForIndexCase(indexCaseBenId: Long, contactType: String): Flow<List<ContactTracingResponseEntity>>

    suspend fun getContactDisplayName(responseId: Long, nameQuestionUuid: String): String?

    suspend fun getOrCreateContactResponse(
        indexCaseBenId: Long,
        contactBenId: Long?,
        contactType: String,
        formVersionId: Int
    ): ContactTracingResponseEntity

    suspend fun saveSectionAnswers(
        responseId: Long,
        sectionId: Int,
        visitNumber: Int,
        answers: List<ContactTracingQuestionResponseEntity>
    )

    suspend fun submitResponse(responseId: Long, finalStatus: String)

    suspend fun syncUnsyncedResponses(): Boolean
}
