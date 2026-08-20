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
    val updatedAt: Long = System.currentTimeMillis(),
    // Contact Tracing fields (nullable/defaulted, unused by existing forms)
    val allowMultiple: Boolean = false,
    val containsPii: Boolean = false,
    val visibleByDefault: Boolean = true,
    val maxLength: Int? = null,
    val enabledIfJson: String? = null,
    val disabledIfJson: String? = null,
    val mandatoryIfJson: String? = null,
    val autoPopulated: Boolean = false,
    val autoPopulateLogic: String? = null,
    val autoPopulateNote: String? = null,
    val unit: String? = null,
    val exampleValuesJson: String? = null,
    val note: String? = null,
    val displayFormat: String? = null
)
