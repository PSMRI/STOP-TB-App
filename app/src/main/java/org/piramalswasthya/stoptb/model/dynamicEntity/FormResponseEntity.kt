package org.piramalswasthya.stoptb.model.dynamicEntity

import androidx.room.DatabaseView
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
        Index(value = ["beneficiaryId", "formVersionId", "isHistorySnapshot"])
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
    val updatedAt: Long = System.currentTimeMillis(),
    val sectionsFilled: Int? = null,
    val totalSections: Int? = null,
    // responseId from backend side
    val backendResponseId: Long? = null,
    val isHistorySnapshot: Boolean = false
)

@DatabaseView(
    viewName = "COUNSELLING_FORM_RESPONSE",
    value = """
        SELECT r.beneficiaryId AS beneficiaryId, r.status AS status,
               r.sectionsFilled AS sectionsFilled, r.totalSections AS totalSections
        FROM t_form_response r
        JOIN t_form_version v ON r.formVersionId = v.versionId
        JOIN t_dynamic_form f ON v.formId = f.formId
        WHERE f.formType IN ('TB_COUNSELLING', 'TB_COUNSELLING_V2')
        GROUP BY r.beneficiaryId
    """
)
data class CounsellingFormResponseView(
    val beneficiaryId: Long,
    val status: String,
    val sectionsFilled: Int?,
    val totalSections: Int?
)
