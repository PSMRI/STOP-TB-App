package org.piramalswasthya.stoptb.database.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import org.piramalswasthya.stoptb.model.ChiefComplaintMasterCache
import org.piramalswasthya.stoptb.model.VisitCategoryMasterCache

@Dao
interface ChiefComplaintMasterDao {
    @Query("SELECT * FROM CHIEF_COMPLAINT_MASTER ORDER BY chiefComplaint COLLATE NOCASE")
    suspend fun getChiefComplaints(): List<ChiefComplaintMasterCache>

    @Query("SELECT visitCategoryId FROM VISIT_CATEGORY_MASTER WHERE visitCategory = 'General OPD' LIMIT 1")
    suspend fun getGeneralOpdCategoryId(): Int?

    @Query("DELETE FROM VISIT_CATEGORY_MASTER")
    suspend fun deleteVisitCategories()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVisitCategories(items: List<VisitCategoryMasterCache>)

    @Query("DELETE FROM CHIEF_COMPLAINT_MASTER")
    suspend fun deleteChiefComplaints()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChiefComplaints(items: List<ChiefComplaintMasterCache>)

    @Transaction
    suspend fun replaceMasters(
        categories: List<VisitCategoryMasterCache>,
        complaints: List<ChiefComplaintMasterCache>
    ) {
        deleteVisitCategories()
        insertVisitCategories(categories)
        deleteChiefComplaints()
        insertChiefComplaints(complaints)
    }
}
