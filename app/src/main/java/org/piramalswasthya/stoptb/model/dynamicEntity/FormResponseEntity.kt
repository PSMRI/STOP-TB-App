package org.piramalswasthya.stoptb.model.dynamicEntity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "t_form_response",
    foreignKeys = [
        ForeignKey(
            entity = FormVersionEntity::class,
            parentColumns = ["versionId"],
            childColumns = ["formVersionId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("formVersionId"),
        Index("beneficiaryId", unique = true) // 1 beneficiary = max 1 form response!
    ]
)
data class FormResponseEntity(
    @PrimaryKey(autoGenerate = true) val responseId: Long = 0,
    val beneficiaryId: Long,
    val formVersionId: Int,
    val status: String, // "DRAFT", "SUBMITTED", "COMPLETE"
    val lastVisitedSectionId: Int?, // For draft resumption
    val syncStatus: String = "UNSYNCED", // "UNSYNCED", "SYNCED", "ERROR"
    val syncedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
