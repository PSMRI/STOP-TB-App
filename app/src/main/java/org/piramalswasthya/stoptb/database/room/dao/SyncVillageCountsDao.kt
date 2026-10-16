package org.piramalswasthya.stoptb.database.room.dao

import androidx.room.Dao
import androidx.room.Query

data class SyncVillageCount(val module: String, val count: Long)

/** Current inventory, independent of the daily diagnostic event journal. */
@Dao
interface SyncVillageCountsDao {
    @Query("""
        WITH village_beneficiaries AS (
            SELECT beneficiaryId FROM BENEFICIARY
            WHERE loc_village_id = :villageId AND isDraft = 0 AND isDeactivate = 0
        )
        SELECT 'BENEFICIARY' AS module, COUNT(*) AS count FROM village_beneficiaries
        UNION ALL
        SELECT 'HOUSEHOLD', COUNT(*) FROM HOUSEHOLD
        WHERE loc_village_id = :villageId AND isDraft = 0 AND isDeactivate = 0
        UNION ALL
        SELECT 'TB_SCREENING', COUNT(*) FROM TB_SCREENING
        WHERE benId IN (SELECT beneficiaryId FROM village_beneficiaries)
        UNION ALL
        SELECT 'GENERAL_OPD', COUNT(*) FROM GENERAL_OPD
        WHERE benId IN (SELECT beneficiaryId FROM village_beneficiaries)
        UNION ALL
        SELECT 'TB_DIAGNOSTICS', COUNT(*) FROM TB_DIAGNOSTICS
        WHERE benId IN (SELECT beneficiaryId FROM village_beneficiaries)
        UNION ALL
        SELECT 'TB_SUSPECTED', COUNT(*) FROM TB_SUSPECTED
        WHERE benId IN (SELECT beneficiaryId FROM village_beneficiaries)
        UNION ALL
        SELECT 'TB_CONFIRMED', COUNT(*) FROM TB_CONFIRMED_TREATMENT
        WHERE benId IN (SELECT beneficiaryId FROM village_beneficiaries)
        UNION ALL
        SELECT 'GENERAL_EXAMINATION', COUNT(*) FROM BEN_VITALS
        WHERE benId IN (SELECT beneficiaryId FROM village_beneficiaries)
        UNION ALL
        SELECT 'COUNSELLING', COUNT(*) FROM t_form_response r
        JOIN t_form_version v ON r.formVersionId = v.versionId
        JOIN t_dynamic_form f ON v.formId = f.formId
        WHERE r.beneficiaryId IN (SELECT beneficiaryId FROM village_beneficiaries)
          AND r.status != 'DRAFT' AND r.isHistorySnapshot = 0
          AND f.formType IN ('TB_COUNSELLING', 'TB_COUNSELLING_V2')
    """)
    suspend fun counts(villageId: Int): List<SyncVillageCount>
}
