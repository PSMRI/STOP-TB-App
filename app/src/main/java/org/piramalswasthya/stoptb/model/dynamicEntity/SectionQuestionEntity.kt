package org.piramalswasthya.stoptb.model.dynamicEntity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "t_section_question",
    foreignKeys = [
        ForeignKey(
            entity = FormSectionEntity::class,
            parentColumns = ["sectionId"],
            childColumns = ["sectionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sectionId")]
)
data class SectionQuestionEntity(
    @PrimaryKey val questionId: Int,
    val sectionId: Int,
    val questionText: String,
    val questionTextHindi: String? = null,
    val questionType: String, // "RADIO", "MCQ", "TEXT", "DATE", "AUTO_FILL", "DISPLAY"
    val questionOrder: Int,
    val isRequired: Boolean,
    val questionUuid: String? = null,
    val serverQuestionId: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
