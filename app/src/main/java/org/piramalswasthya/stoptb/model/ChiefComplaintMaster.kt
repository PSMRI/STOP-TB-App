package org.piramalswasthya.stoptb.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "VISIT_CATEGORY_MASTER")
data class VisitCategoryMasterCache(
    @PrimaryKey val visitCategoryId: Int,
    val visitCategory: String
)

@Entity(tableName = "CHIEF_COMPLAINT_MASTER")
data class ChiefComplaintMasterCache(
    @PrimaryKey val chiefComplaintId: Int,
    val chiefComplaint: String
)
