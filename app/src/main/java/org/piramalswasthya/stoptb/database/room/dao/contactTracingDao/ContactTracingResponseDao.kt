package org.piramalswasthya.stoptb.database.room.dao.contactTracingDao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.piramalswasthya.stoptb.model.contactTracing.ContactTracingCompleteResponse
import org.piramalswasthya.stoptb.model.contactTracing.ContactTracingQuestionResponseEntity
import org.piramalswasthya.stoptb.model.contactTracing.ContactTracingResponseEntity
import org.piramalswasthya.stoptb.model.contactTracing.ContactTracingSectionResponseEntity

@Dao
interface ContactTracingResponseDao {

    @Upsert
    suspend fun insertResponse(response: ContactTracingResponseEntity): Long

    @Upsert
    suspend fun insertSectionResponse(sectionResponse: ContactTracingSectionResponseEntity): Long

    @Upsert
    suspend fun insertQuestionResponses(responses: List<ContactTracingQuestionResponseEntity>)

    @Query("UPDATE t_ct_response SET status = :status, syncStatus = :syncStatus, updatedAt = :updatedAt WHERE responseId = :responseId")
    suspend fun updateResponseStatus(responseId: Long, status: String, syncStatus: String, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM t_ct_response WHERE indexCaseBenId = :indexCaseBenId AND contactType = :contactType ORDER BY updatedAt DESC")
    fun getResponsesForIndexCase(indexCaseBenId: Long, contactType: String): Flow<List<ContactTracingResponseEntity>>

    @Query("SELECT * FROM t_ct_response WHERE contactBenId = :contactBenId AND formVersionId = :formVersionId LIMIT 1")
    suspend fun getResponseForContact(contactBenId: Long, formVersionId: Int): ContactTracingResponseEntity?

    @Transaction
    @Query("SELECT * FROM t_ct_response WHERE responseId = :responseId LIMIT 1")
    suspend fun getCompleteResponse(responseId: Long): ContactTracingCompleteResponse?

    @Query("SELECT * FROM t_ct_response WHERE syncStatus = 'UNSYNCED'")
    suspend fun getUnsyncedResponses(): List<ContactTracingResponseEntity>

    @Query("SELECT sectionResponseId FROM t_ct_section_response WHERE responseId = :responseId AND sectionId = :sectionId AND visitNumber = :visitNumber LIMIT 1")
    suspend fun findSectionResponseId(responseId: Long, sectionId: Int, visitNumber: Int): Long?

    @Query("DELETE FROM t_ct_question_response WHERE sectionResponseId = :sectionResponseId")
    suspend fun deleteQuestionResponsesForSection(sectionResponseId: Long)

    // Resolves a contact's display name for the member list by joining the reused
    // schema table (t_section_question) with the new response tables, matched by
    // questionUuid ("COM_Q1"/"OCC_Q1" — the "Name of Contact" question in each form).
    @Query(
        "SELECT qr.answerText FROM t_ct_question_response qr " +
            "JOIN t_ct_section_response sr ON qr.sectionResponseId = sr.sectionResponseId " +
            "JOIN t_section_question sq ON qr.questionId = sq.questionId " +
            "WHERE sr.responseId = :responseId AND sq.questionUuid = :nameQuestionUuid LIMIT 1"
    )
    suspend fun getAnswerText(responseId: Long, nameQuestionUuid: String): String?
}
