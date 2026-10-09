package org.piramalswasthya.stoptb.database.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import org.piramalswasthya.stoptb.model.DrugItemMasterCache
import org.piramalswasthya.stoptb.model.DrugFormMasterCache
import org.piramalswasthya.stoptb.model.DrugFrequencyMasterCache
import org.piramalswasthya.stoptb.model.DrugDurationUnitMasterCache
import org.piramalswasthya.stoptb.model.OpdDrugMasters

@Dao
interface DrugMasterDao {
    @Query("SELECT * FROM DRUG_ITEM_MASTER WHERE scope = :scope ORDER BY itemName COLLATE NOCASE, id")
    suspend fun getItems(scope: String): List<DrugItemMasterCache>
    @Query("SELECT * FROM DRUG_FORM_MASTER WHERE scope = :scope ORDER BY itemFormId")
    suspend fun getForms(scope: String): List<DrugFormMasterCache>
    @Query("SELECT * FROM DRUG_FREQUENCY_MASTER WHERE scope = :scope ORDER BY drugFrequencyId")
    suspend fun getFrequencies(scope: String): List<DrugFrequencyMasterCache>
    @Query("SELECT * FROM DRUG_DURATION_UNIT_MASTER WHERE scope = :scope ORDER BY drugDurationId")
    suspend fun getDurationUnits(scope: String): List<DrugDurationUnitMasterCache>

    @Query("DELETE FROM DRUG_ITEM_MASTER WHERE scope = :scope")
    suspend fun deleteItems(scope: String)
    @Query("DELETE FROM DRUG_FORM_MASTER WHERE scope = :scope")
    suspend fun deleteForms(scope: String)
    @Query("DELETE FROM DRUG_FREQUENCY_MASTER WHERE scope = :scope")
    suspend fun deleteFrequencies(scope: String)
    @Query("DELETE FROM DRUG_DURATION_UNIT_MASTER WHERE scope = :scope")
    suspend fun deleteDurationUnits(scope: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItems(items: List<DrugItemMasterCache>)
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertForms(items: List<DrugFormMasterCache>)
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFrequencies(items: List<DrugFrequencyMasterCache>)
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDurationUnits(items: List<DrugDurationUnitMasterCache>)

    @Transaction
    suspend fun getMasters(scope: String) = OpdDrugMasters(
        getItems(scope), getForms(scope), getFrequencies(scope), getDurationUnits(scope)
    )

    @Transaction
    suspend fun replaceMasters(scope: String, masters: OpdDrugMasters) {
        require(masters.items.all { it.scope == scope } && masters.forms.all { it.scope == scope } &&
            masters.frequencies.all { it.scope == scope } && masters.durationUnits.all { it.scope == scope })
        deleteItems(scope)
        deleteForms(scope)
        deleteFrequencies(scope)
        deleteDurationUnits(scope)
        insertItems(masters.items)
        insertForms(masters.forms)
        insertFrequencies(masters.frequencies)
        insertDurationUnits(masters.durationUnits)
    }
}
