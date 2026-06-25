package org.piramalswasthya.stoptb.model.dynamicEntity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "t_option_condition",
    foreignKeys = [
        ForeignKey(
            entity = QuestionOptionEntity::class,
            parentColumns = ["optionId"],
            childColumns = ["optionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SectionQuestionEntity::class,
            parentColumns = ["questionId"],
            childColumns = ["targetQuestionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("optionId"), Index("targetQuestionId")]
)
data class OptionConditionEntity(
    @PrimaryKey val conditionId: Int,
    val optionId: Int,
    val targetQuestionId: Int,
    val actionType: String, // "VISIBLE", "MANDATORY"
    val isFulfilledValue: Boolean, // e.g. true for show, false for hide
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
