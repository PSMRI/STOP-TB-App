package org.piramalswasthya.stoptb.database.room.dao

/**
 * Beneficiary ids for the Clinical Assessment & Diagnosis referral card.
 * The predicate matches [org.piramalswasthya.stoptb.clinical.ClinicalAssessmentClassifier]:
 * the previous positive X-ray / negative NAAT and indeterminate RIF rows stay included,
 * and Category 1 (abnormal CXR) and Category 2 (no CXR) are added.
 */
object ClinicalAssessmentSql {
    const val MATCHING_BEN_IDS = """
        SELECT b.benId
        FROM BEN_BASIC_CACHE b
        WHERE b.isDeath = 0
          AND (
            EXISTS (
              SELECT 1 FROM TB_SUSPECTED su
              WHERE su.benId = b.benId
                AND UPPER(TRIM(IFNULL(su.chestXRayResult,''))) IN ('TB PRESUMPTIVE','ABNORMAL BUT NOT TB PRESUMPTIVE')
                AND UPPER(TRIM(IFNULL(su.naatResult,''))) IN ('TB NEGATIVE','MTB NOT DETECTED')
            )
            OR EXISTS (
              SELECT 1 FROM TB_DIAGNOSTICS d
              WHERE d.benId = b.benId
                AND (
                  (
                    UPPER(TRIM(IFNULL(d.chestXRayResult,''))) IN ('TB PRESUMPTIVE','ABNORMAL BUT NOT TB PRESUMPTIVE')
                    AND UPPER(TRIM(IFNULL(d.naatResult,''))) IN ('TB NEGATIVE','MTB NOT DETECTED')
                  )
                  OR UPPER(TRIM(IFNULL(d.trueNatRifResult,''))) = 'INDETERMINATE'
                )
            )
            OR (
              (
                EXISTS (
                  SELECT 1 FROM TB_DIAGNOSTICS d
                  WHERE d.benId = b.benId
                    AND UPPER(TRIM(IFNULL(d.chestXRayResult,''))) IN ('TB PRESUMPTIVE','ABNORMAL BUT NOT TB PRESUMPTIVE','ABNORMAL (NON-TB PRESUMPTIVE)','ABNORMAL','POSITIVE')
                )
                OR EXISTS (
                  SELECT 1 FROM TB_SUSPECTED su
                  WHERE su.benId = b.benId
                    AND UPPER(TRIM(IFNULL(su.chestXRayResult,''))) IN ('TB PRESUMPTIVE','ABNORMAL BUT NOT TB PRESUMPTIVE','ABNORMAL (NON-TB PRESUMPTIVE)','ABNORMAL','POSITIVE')
                )
              )
              AND NOT (
                EXISTS (
                  SELECT 1 FROM TB_DIAGNOSTICS d
                  WHERE d.benId = b.benId
                    AND UPPER(TRIM(IFNULL(d.naatResult,''))) IN ('TB POSITIVE','MTB DETECTED','MTB POSITIVE','POSITIVE','DETECTED')
                )
                OR EXISTS (
                  SELECT 1 FROM TB_SUSPECTED su
                  WHERE su.benId = b.benId
                    AND UPPER(TRIM(IFNULL(su.naatResult,''))) IN ('TB POSITIVE','MTB DETECTED','MTB POSITIVE','POSITIVE','DETECTED')
                )
              )
              AND (
                EXISTS (
                  SELECT 1 FROM TB_DIAGNOSTICS d
                  WHERE d.benId = b.benId
                    AND UPPER(TRIM(IFNULL(d.naatResult,''))) IN ('TB NEGATIVE','MTB NOT DETECTED','MTB NEGATIVE','NEGATIVE','NOT DETECTED')
                )
                OR EXISTS (
                  SELECT 1 FROM TB_SUSPECTED su
                  WHERE su.benId = b.benId
                    AND UPPER(TRIM(IFNULL(su.naatResult,''))) IN ('TB NEGATIVE','MTB NOT DETECTED','MTB NEGATIVE','NEGATIVE','NOT DETECTED')
                )
                OR EXISTS (
                  SELECT 1 FROM TB_DIAGNOSTICS d
                  WHERE d.benId = b.benId
                    AND (
                      TRIM(IFNULL(d.reasonNotConductedNaat,'')) != ''
                      OR (
                        d.isNaatConducted = 0
                        AND UPPER(TRIM(IFNULL(d.trueNatOrderStatus,''))) = 'CLOSED'
                      )
                    )
                )
                OR EXISTS (
                  SELECT 1 FROM TB_SUSPECTED su
                  WHERE su.benId = b.benId
                    AND TRIM(IFNULL(su.reasonForRefusalMTB,'')) != ''
                )
              )
            )
            OR (
              (
                EXISTS (SELECT 1 FROM TB_SCREENING s WHERE s.benId = b.benId)
                OR EXISTS (SELECT 1 FROM TB_SUSPECTED su WHERE su.benId = b.benId)
                OR EXISTS (SELECT 1 FROM TB_DIAGNOSTICS d WHERE d.benId = b.benId)
              )
              AND NOT EXISTS (
                SELECT 1 FROM TB_DIAGNOSTICS d
                WHERE d.benId = b.benId
                  AND (
                    TRIM(IFNULL(d.chestXRayResult,'')) != ''
                    OR d.isChestXRayDone = 1
                  )
              )
              AND NOT EXISTS (
                SELECT 1 FROM TB_SUSPECTED su
                WHERE su.benId = b.benId
                  AND (
                    TRIM(IFNULL(su.chestXRayResult,'')) != ''
                    OR su.isChestXRayDone = 1
                  )
              )
              AND (
                EXISTS (
                  SELECT 1 FROM TB_SCREENING s
                  WHERE s.benId = b.benId
                    AND (
                      s.coughMoreThan2Weeks = 1
                      OR s.bloodInSputum = 1
                      OR s.feverMoreThan2Weeks = 1
                      OR s.riseOfFever = 1
                      OR s.lossOfAppetite = 1
                      OR s.lossOfWeight = 1
                      OR s.nightSweats = 1
                      OR s.historyOfTb = 1
                      OR s.takingAntiTBDrugs = 1
                      OR s.familySufferingFromTB = 1
                      OR s.chestPain = 1
                      OR s.shortnessOfBreath = 1
                      OR s.fatigue = 1
                      OR s.failureToGainWeightInChildren = 1
                      OR s.decreasedActivityOrPlayfulnessInChildren = 1
                      OR s.otherSymptoms = 1
                      OR UPPER(TRIM(IFNULL(s.asymptomatic,''))) = 'NO'
                    )
                )
                OR EXISTS (
                  SELECT 1 FROM TB_SUSPECTED su
                  WHERE su.benId = b.benId AND su.hasSymptoms = 1
                )
                OR b.reproductiveStatusId = 1
                OR EXISTS (
                  SELECT 1 FROM TB_SCREENING s
                  WHERE s.benId = b.benId
                    AND TRIM(IFNULL(s.keyPopulationRiskFactors,'')) != ''
                    AND REPLACE(REPLACE(UPPER(s.keyPopulationRiskFactors), 'NOT_APPLICABLE', ''), '|', '') != ''
                )
                OR EXISTS (
                  SELECT 1 FROM BEN_VITALS v
                  WHERE v.benId = b.benId
                    AND TRIM(IFNULL(v.keyPopulationRiskFactors,'')) != ''
                    AND REPLACE(REPLACE(UPPER(v.keyPopulationRiskFactors), 'NOT_APPLICABLE', ''), '|', '') != ''
                )
              )
              AND (
                b.reproductiveStatusId = 1
                OR CAST((strftime('%s','now') - b.dob/1000)/60/60/24/365 AS INTEGER) < 14
                OR EXISTS (
                  SELECT 1 FROM TB_SCREENING s
                  WHERE s.benId = b.benId
                    AND UPPER(IFNULL(s.keyPopulationRiskFactors,'')) LIKE '%PREGNANCY%'
                )
                OR EXISTS (
                  SELECT 1 FROM BEN_VITALS v
                  WHERE v.benId = b.benId
                    AND UPPER(IFNULL(v.keyPopulationRiskFactors,'')) LIKE '%PREGNANCY%'
                )
                OR EXISTS (
                  SELECT 1 FROM TB_DIAGNOSTICS d
                  WHERE d.benId = b.benId
                    AND (
                      d.isChestXRayDone = 0
                      OR TRIM(IFNULL(d.reasonNotConductedChestXray,'')) != ''
                      OR TRIM(IFNULL(d.reasonNotConductedChestXrayOther,'')) != ''
                      OR TRIM(IFNULL(d.reasonForDenialChestXray,'')) != ''
                      OR TRIM(IFNULL(d.reasonForDenialChestXrayOther,'')) != ''
                    )
                )
                OR EXISTS (
                  SELECT 1 FROM TB_SUSPECTED su
                  WHERE su.benId = b.benId
                    AND (
                      su.isChestXRayDone = 0
                      OR TRIM(IFNULL(su.reasonForRefusalXray,'')) != ''
                    )
                )
              )
            )
          )
    """
}
