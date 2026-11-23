package org.piramalswasthya.stoptb.database.room.dao

/**
 * Beneficiary ids for the Clinical Assessment & Diagnosis referral card.
 * The predicate matches [org.piramalswasthya.stoptb.clinical.ClinicalAssessmentClassifier]:
 * the previous positive X-ray / negative NAAT and indeterminate RIF rows stay included,
 * and Category 1 (abnormal CXR) and Category 2 (no CXR) are added.
 *
 * The TB-row parts are TB_BEN_SUMMARY.caFixed / caD0 / caSym / caElig (TbBenSummarySql); the
 * parts that use beneficiary columns (pregnancy status, age under 14) are checked here.
 */
object ClinicalAssessmentSql {
    const val MATCHING_BEN_IDS = """
        SELECT b.benId
        FROM TB_BEN_SUMMARY s
        -- CROSS JOIN keeps the summary as the outer loop; a plain JOIN let SQLite start from
        -- every beneficiary (1M device DB: 5.8 s -> 0.8 s on the host).
        CROSS JOIN BEN_BASIC_CACHE b ON b.benId = s.beneficiaryId
        WHERE b.isDeath = 0
          AND (
            s.caFixed = 1
            OR (
              s.caD0 = 1
              AND (s.caSym = 1 OR b.reproductiveStatusId = 1)
              AND (
                b.reproductiveStatusId = 1
                OR CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 14
                OR s.caElig = 1
              )
            )
          )
    """
}
