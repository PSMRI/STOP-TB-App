package org.piramalswasthya.stoptb.database.room.dao.dynamicSchemaDao

import androidx.room.*
import org.piramalswasthya.stoptb.model.dynamicEntity.FormSchemaEntity

@Dao
interface FormSchemaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(schema: FormSchemaEntity)

    @Query("SELECT * FROM form_schema WHERE formId = :formId LIMIT 1")
    suspend fun getSchema(formId: String): FormSchemaEntity?
}
