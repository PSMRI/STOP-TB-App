package org.piramalswasthya.stoptb.database.room.dao

/**
 * Membership of the Referral lists (BenSearchQueries sources 5, 6, 7, 9) and their home-card
 * counts, read from TB_BEN_SUMMARY. The full rules live in TbBenSummarySql; only the parts that
 * need beneficiary columns (reproductiveStatusId, temperature, household) are checked here.
 * Fragments expect BEN_BASIC_CACHE columns unqualified (benId, hhId, reproductiveStatusId).
 */
object ReferralListSql {

    /** Source 5: HWC referral. */
    const val HWC_BEN_IDS = "SELECT beneficiaryId FROM TB_BEN_SUMMARY WHERE hwc = 1" +
        " UNION SELECT b2.beneficiaryId FROM BENEFICIARY b2 WHERE b2.temperature IS NOT NULL AND b2.temperature >= 100.0"

    /** Source 6: Digital Chest X-ray. Pregnant beneficiaries are not referred for X-ray. */
    const val XRAY = "(reproductiveStatusId != 1" +
        " AND benId IN (SELECT beneficiaryId FROM TB_BEN_SUMMARY WHERE xrayList = 1 AND pregVitals = 0))"

    /** Source 7: TrueNat. */
    const val TRUENAT = "(benId IN (SELECT beneficiaryId FROM TB_BEN_SUMMARY WHERE tnOther = 1)" +
        " OR ((reproductiveStatusId = 1 OR benId IN (SELECT beneficiaryId FROM TB_BEN_SUMMARY WHERE pregVitals = 1))" +
        " AND benId IN (SELECT beneficiaryId FROM TB_BEN_SUMMARY WHERE hasScreening = 1)))"

    /**
     * Source 9: TPT. Household contacts of a confirmed case (any village), not the case itself.
     * Starts from the confirmed cases instead of probing every beneficiary's household.
     */
    const val TPT = "(hhId IS NOT NULL" +
        " AND hhId IN (SELECT b2.hhId FROM TB_BEN_SUMMARY s2 CROSS JOIN BEN_BASIC_CACHE b2 ON b2.benId = s2.beneficiaryId" +
        " WHERE s2.tptIndex = 1 AND b2.isDeactivate = 0 AND b2.isDeath = 0)" +
        " AND benId NOT IN (SELECT beneficiaryId FROM TB_BEN_SUMMARY WHERE tptIndex = 1))"
}
