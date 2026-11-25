package org.piramalswasthya.stoptb.database.room.dao

/**
 * Beneficiary ids for the Clinical Assessment & Diagnosis referral card (release-2.4 rule):
 * positive X-ray with negative NAAT, or an indeterminate RIF result.
 *
 * TB_BEN_SUMMARY keeps its caFixed / caD0 / caSym / caElig columns so the schema matches
 * release-2.5, but this line does not read them.
 */
object ClinicalAssessmentSql {
    const val MATCHING_BEN_IDS = """
        SELECT ts.benId FROM TB_SUSPECTED ts
        WHERE UPPER(TRIM(IFNULL(ts.chestXRayResult,''))) IN ('TB PRESUMPTIVE','ABNORMAL BUT NOT TB PRESUMPTIVE')
          AND UPPER(TRIM(IFNULL(ts.naatResult,''))) IN ('TB NEGATIVE','MTB NOT DETECTED')
        UNION
        SELECT td.benId FROM TB_DIAGNOSTICS td
        WHERE (UPPER(TRIM(IFNULL(td.chestXRayResult,''))) IN ('TB PRESUMPTIVE','ABNORMAL BUT NOT TB PRESUMPTIVE')
               AND UPPER(TRIM(IFNULL(td.naatResult,''))) IN ('TB NEGATIVE','MTB NOT DETECTED'))
           OR UPPER(TRIM(IFNULL(td.trueNatRifResult,''))) = 'INDETERMINATE'
    """
}
