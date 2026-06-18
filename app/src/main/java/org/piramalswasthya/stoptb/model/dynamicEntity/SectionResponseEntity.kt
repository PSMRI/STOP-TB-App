package org.piramalswasthya.stoptb.model.dynamicEntity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "t_section_response",
    foreignKeys = [
        ForeignKey(
            entity = FormResponseEntity::class,
            parentColumns = ["responseId"],
            childColumns = ["formResponseId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = FormSectionEntity::class,
            parentColumns = ["sectionId"],
            childColumns = ["sectionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("formResponseId"),
        Index("sectionId"),
        Index(value = ["formResponseId", "sectionId"], unique = true)
    ]
)
data class SectionResponseEntity(
    @PrimaryKey(autoGenerate = true) val sectionResponseId: Long = 0,
    val formResponseId: Long,
    val sectionId: Int,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
