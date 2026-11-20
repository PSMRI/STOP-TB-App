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

    /** X-ray / TrueNat list order keys (same values the old per-row subqueries computed). */
    const val XRAY_TIER = "(SELECT xrayTier FROM TB_BEN_SUMMARY WHERE beneficiaryId = BEN_BASIC_CACHE.benId)"
    const val XRAY_KEY = "(SELECT xrayKey FROM TB_BEN_SUMMARY WHERE beneficiaryId = BEN_BASIC_CACHE.benId)"
    const val TN_TIER = "(SELECT tnTier FROM TB_BEN_SUMMARY WHERE beneficiaryId = BEN_BASIC_CACHE.benId)"
    const val TN_KEY = "(SELECT tnKey FROM TB_BEN_SUMMARY WHERE beneficiaryId = BEN_BASIC_CACHE.benId)"

    /** filterType chips and the search box of the beneficiary lists (BEN_BASIC_CACHE columns). */
    const val BEN_LIST_FILTERS = """
        AND (:filterType = 0
            OR (:filterType = 1 AND abhaId IS NOT NULL)
            OR (:filterType = 2 AND abhaId IS NULL)
            OR (:filterType = 3 AND CAST((strftime('%s','now') - dob/1000)/60/60/24/365 AS INTEGER) >= 30 AND isDeath = 0)
            OR (:filterType = 4 AND gender = 'Female' AND isDeath = 0
                AND CAST((strftime('%s','now') - dob/1000)/60/60/24/365 AS INTEGER) BETWEEN 20 AND 49
                AND (reproductiveStatusId = 1 OR reproductiveStatusId = 2))
        )
        AND (:query = '' OR
            benName LIKE '%' || :query || '%'
            OR benSurname LIKE '%' || :query || '%'
            OR (IFNULL(benName, '') || ' ' || IFNULL(benSurname, '')) LIKE '%' || :query || '%'
            OR CAST(mobileNo AS TEXT) LIKE '%' || REPLACE(:query, ' ', '') || '%'
            OR REPLACE(IFNULL(abhaId, ''), '-', '') LIKE '%' || REPLACE(:query, ' ', '') || '%'
            OR IFNULL(familyHeadName, '') LIKE '%' || :query || '%'
            OR IFNULL(spouseName, '') LIKE '%' || :query || '%'
            OR IFNULL(fatherName, '') LIKE '%' || :query || '%'
            OR CAST(benId AS TEXT) LIKE '%' || REPLACE(:query, ' ', '') || '%'
            OR CAST(hhId AS TEXT) LIKE '%' || :query || '%'
            OR IFNULL(rchId, '') LIKE '%' || REPLACE(:query, ' ', '') || '%'
        )
    """
}
