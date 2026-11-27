package org.piramalswasthya.stoptb.database.room.dao.dynamicSchemaDao

import androidx.room.*
import org.piramalswasthya.stoptb.model.dynamicEntity.FormResponseJsonEntity

@Dao
interface FormResponseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(responses: List<FormResponseJsonEntity>)
}
