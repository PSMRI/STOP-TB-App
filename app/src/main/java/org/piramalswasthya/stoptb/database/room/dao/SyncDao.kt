package org.piramalswasthya.stoptb.database.room.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.piramalswasthya.stoptb.model.SyncStatusCache

@Dao
interface SyncDao {

    @Query(
        "SELECT id, name, syncState, COUNT(*) as count " +
                "FROM ( " +
                "    SELECT 1 as id, 'Beneficiary' as name, b1.syncState as syncState " +
                "    FROM beneficiary b1 " +
                "    WHERE b1.isDeactivate = 0 AND b1.isDraft = 0 AND b1.loc_village_id = :selectedVillage " +
                "    UNION ALL " +
                "    SELECT 10 as id, 'CBAC' as name, c1.syncState as syncState " +
                "    FROM cbac c1 " +
                "    INNER JOIN beneficiary b ON b.beneficiaryId = c1.benId AND b.loc_village_id = :selectedVillage " +
                "    UNION ALL " +
                "    SELECT 11 as id, 'TB Screening' as name, tbsn.syncState as syncState " +
                "    FROM TB_SCREENING tbsn " +
                "    INNER JOIN beneficiary b ON b.beneficiaryId = tbsn.benId AND b.loc_village_id = :selectedVillage " +
                "    UNION ALL " +
                "    SELECT 12 as id, 'TB Suspected' as name, tbsp.syncState as syncState " +
                "    FROM TB_SUSPECTED tbsp " +
                "    INNER JOIN beneficiary b ON b.beneficiaryId = tbsp.benId AND b.loc_village_id = :selectedVillage " +
                "    UNION ALL " +
                "    SELECT 13 as id, 'NCD Refer' as name, ref.syncState as syncState " +
                "    FROM NCD_REFER ref " +
                "    INNER JOIN beneficiary b ON b.beneficiaryId = ref.benId AND b.loc_village_id = :selectedVillage " +
                "    UNION ALL " +
                "    SELECT 26 as id, 'NCD Follow Up' as name, CASE " +
                "   WHEN ncd.isSynced = 1 THEN 2 ELSE ncd.isSynced END AS syncState " +
                "    FROM ncd_referal_all_visit ncd " +
                "    INNER JOIN beneficiary b ON b.beneficiaryId = ncd.benId AND b.loc_village_id = :selectedVillage " +
                ") AS combined_data " +
                "GROUP BY id, name, syncState " +
                "ORDER BY id; "
    )
    fun getSyncStatus(selectedVillage: Int): Flow<List<SyncStatusCache>>
}