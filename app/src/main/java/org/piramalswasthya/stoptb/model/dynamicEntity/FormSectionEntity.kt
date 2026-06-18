package org.piramalswasthya.stoptb.model.dynamicEntity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "t_form_section",
    foreignKeys = [
        ForeignKey(
            entity = FormVersionEntity::class,
            parentColumns = ["versionId"],
            childColumns = ["versionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("versionId")]
)
data class FormSectionEntity(
    @PrimaryKey val sectionId: Int,
    val versionId: Int,
    val sectionName: String,
    val sectionOrder: Int,
    val sectionPhase: String, // "PRE_SUBMIT" (A-E) or "POST_SUBMIT" (F)
    val sectionUuid: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
