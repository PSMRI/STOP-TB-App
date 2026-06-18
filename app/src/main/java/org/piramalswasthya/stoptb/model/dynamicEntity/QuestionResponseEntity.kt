package org.piramalswasthya.stoptb.model.dynamicEntity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "t_question_response",
    foreignKeys = [
        ForeignKey(
            entity = SectionResponseEntity::class,
            parentColumns = ["sectionResponseId"],
            childColumns = ["sectionResponseId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SectionQuestionEntity::class,
            parentColumns = ["questionId"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = QuestionOptionEntity::class,
            parentColumns = ["optionId"],
            childColumns = ["optionId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("sectionResponseId"),
        Index("questionId"),
        Index("optionId")
    ]
)
data class QuestionResponseEntity(
    @PrimaryKey(autoGenerate = true) val questionResponseId: Long = 0,
    val sectionResponseId: Long,
    val questionId: Int,
    val optionId: Int? = null,
    val answerText: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
