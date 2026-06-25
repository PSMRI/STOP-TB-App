package org.piramalswasthya.stoptb.model.dynamicEntity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "t_form_version",
    foreignKeys = [
        ForeignKey(
            entity = DynamicFormEntity::class,
            parentColumns = ["formId"],
            childColumns = ["formId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("formId")]
)
data class FormVersionEntity(
    @PrimaryKey val versionId: Int,
    val formId: Int,
    val versionNumber: Int,
    val isActive: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
