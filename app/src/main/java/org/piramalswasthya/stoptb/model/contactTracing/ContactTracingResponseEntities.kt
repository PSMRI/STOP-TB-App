package org.piramalswasthya.stoptb.model.contactTracing

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import org.piramalswasthya.stoptb.model.dynamicEntity.QuestionOptionEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.SectionQuestionEntity

@Entity(
    tableName = "t_ct_response",
    indices = [Index("indexCaseBenId"), Index("contactBenId")]
)
data class ContactTracingResponseEntity(
    @PrimaryKey(autoGenerate = true) val responseId: Long = 0,
    val indexCaseBenId: Long,
    val contactBenId: Long? = null,
    val contactType: String, // "HOUSEHOLD", "COMMUNITY", "OCCUPATIONAL"
    val formVersionId: Int,
    val status: String = "DRAFT", // DRAFT, SUBMITTED, COMPLETE
    val syncStatus: String = "UNSYNCED", // UNSYNCED, SYNCED, ERROR
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncedAt: Long? = null
)

@Entity(
    tableName = "t_ct_section_response",
    foreignKeys = [
        ForeignKey(
            entity = ContactTracingResponseEntity::class,
            parentColumns = ["responseId"],
            childColumns = ["responseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["responseId", "sectionId", "visitNumber"], unique = true)]
)
data class ContactTracingSectionResponseEntity(
    @PrimaryKey(autoGenerate = true) val sectionResponseId: Long = 0,
    val responseId: Long,
    val sectionId: Int,
    val visitNumber: Int = 1, // lets the same section (e.g. Follow-up and Outcome) be answered again each month
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "t_ct_question_response",
    foreignKeys = [
        ForeignKey(
            entity = ContactTracingSectionResponseEntity::class,
            parentColumns = ["sectionResponseId"],
            childColumns = ["sectionResponseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sectionResponseId"), Index("questionId")]
)
data class ContactTracingQuestionResponseEntity(
    @PrimaryKey(autoGenerate = true) val questionResponseId: Long = 0,
    val sectionResponseId: Long,
    val questionId: Int,
    val optionId: Int? = null,
    val answerText: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class ContactTracingSectionResponseWithQuestions(
    @Embedded val sectionResponse: ContactTracingSectionResponseEntity,
    @Relation(
        parentColumn = "sectionResponseId",
        entityColumn = "sectionResponseId"
    )
    val questionResponses: List<ContactTracingQuestionResponseEntity>
)

data class ContactTracingCompleteResponse(
    @Embedded val response: ContactTracingResponseEntity,
    @Relation(
        entity = ContactTracingSectionResponseEntity::class,
        parentColumn = "responseId",
        entityColumn = "responseId"
    )
    val sectionResponses: List<ContactTracingSectionResponseWithQuestions>
)
