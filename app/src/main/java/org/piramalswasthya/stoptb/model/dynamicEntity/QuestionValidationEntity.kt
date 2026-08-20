package org.piramalswasthya.stoptb.model.dynamicEntity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "t_question_validation",
    foreignKeys = [
        ForeignKey(
            entity = SectionQuestionEntity::class,
            parentColumns = ["questionId"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("questionId")]
)
data class QuestionValidationEntity(
    @PrimaryKey val validationId: Int,
    val questionId: Int,
    val validationType: String, // "MIN", "MAX", "REGEX", "MAX_LENGTH"
    val validationValue: String?,
    val errorMessage: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
