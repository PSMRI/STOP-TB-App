package org.piramalswasthya.stoptb.database.room.dao

/**
 * Pieces of the beneficiary list queries in [BenSearchQueries] (All Beneficiaries and the lists
 * opened from the home, Referral and TPT cards). Each list has its own query built from these,
 * so every list's membership and order can be read in one place.
 *
 * Unqualified columns are BEN_BASIC_CACHE columns.
 */
object BenListSql {

    /** Beneficiaries of the selected village(s), deactivated ones excluded. */
    const val FROM_VILLAGES = "SELECT * FROM BEN_BASIC_CACHE WHERE villageId IN (:villageIds) AND isDeactivate = 0"

    /**
     * Filter chips and the search box. The search uses the BEN_SEARCH_FTS index: every typed
     * word must match the start of a word in a name, phone, ABHA, ID, RCH, family head, spouse
     * or father field. :query is the raw text ('' = no search), :ftsQuery its FTS form
     * (BenSearchSql.toFtsQuery).
     */
    const val FILTERS = """
        AND (:filterType = 0
            OR (:filterType = 1 AND abhaId IS NOT NULL)
            OR (:filterType = 2 AND abhaId IS NULL)
            OR (:filterType = 3 AND CAST((strftime('%s','now') - dob/1000)/60/60/24/365 AS INTEGER) >= 30 AND isDeath = 0)
            OR (:filterType = 4 AND gender = 'Female' AND isDeath = 0
                AND CAST((strftime('%s','now') - dob/1000)/60/60/24/365 AS INTEGER) BETWEEN 20 AND 49
                AND (reproductiveStatusId = 1 OR reproductiveStatusId = 2))
        )
        AND (:query = '' OR benId IN (SELECT rowid FROM BEN_SEARCH_FTS WHERE BEN_SEARCH_FTS MATCH :ftsQuery))
    """

    /** Living before deceased, then newest registration first. */
    const val DEFAULT_ORDER = """
        ORDER BY CASE
            WHEN isDeath = 0 AND isDeactivate = 0 THEN 0
            WHEN isDeath = 1 AND isDeactivate = 0 THEN 1
            WHEN isDeactivate = 1 THEN 2
            ELSE 3
        END ASC,
        COALESCE(createdDate, regDate, 0) DESC,
        benId DESC
    """

    const val PAGE = " LIMIT :limit OFFSET :offset"

    // List memberships on top of FROM_VILLAGES.
    const val ABHA = " AND abhaId IS NOT NULL"
    const val RCH = " AND rchId IS NOT NULL AND rchId != ''"
    const val AGE_30_PLUS = " AND CAST((strftime('%s','now') - dob/1000)/60/60/24/365 AS INTEGER) >= 30 AND isDeath = 0"
    const val ELIGIBLE_COUPLE = " AND gender = 'Female' AND isDeath = 0" +
        " AND CAST((strftime('%s','now') - dob/1000)/60/60/24/365 AS INTEGER) BETWEEN 20 AND 49" +
        " AND (reproductiveStatusId = 1 OR reproductiveStatusId = 2)"
    const val TPT = " AND isDeath = 0 AND " + ReferralListSql.TPT

    /**
     * Referral lists read from TB_BEN_SUMMARY in its index order and stop after a page; every
     * member is alive and active, so the default order reduces to the stored key.
     */
    private const val FROM_SUMMARY_HEAD = "SELECT BEN_BASIC_CACHE.* FROM TB_BEN_SUMMARY s INDEXED BY "
    private const val FROM_SUMMARY_TAIL = " CROSS JOIN BEN_BASIC_CACHE ON BEN_BASIC_CACHE.benId = s.beneficiaryId" +
        " WHERE villageId IN (:villageIds) AND isDeactivate = 0 AND isDeath = 0"
    private const val BY_REGISTRATION = " ORDER BY s.genKey DESC, s.beneficiaryId DESC"

    const val HWC_PAGE = FROM_SUMMARY_HEAD + "ind_tbsum_hwc" + FROM_SUMMARY_TAIL + " AND s.hwc = 1" +
        FILTERS + BY_REGISTRATION + PAGE

    const val LIQUID_CULTURE_PAGE = FROM_SUMMARY_HEAD + "ind_tbsum_lc" + FROM_SUMMARY_TAIL + " AND s.lc = 1" +
        FILTERS + BY_REGISTRATION + PAGE

    /** Same rule as ClinicalAssessmentSql.MATCHING_BEN_IDS. */
    const val CLINICAL_ASSESSMENT_PAGE = FROM_VILLAGES + " AND isDeath = 0 AND benId IN (" +
        ClinicalAssessmentSql.MATCHING_BEN_IDS + ")" + FILTERS + DEFAULT_ORDER + PAGE

    /** Failed / manual entry first (oldest visit first), then in flight, no order, completed, closed. */
    const val XRAY_PAGE = FROM_SUMMARY_HEAD + "ind_tbsum_xray" + FROM_SUMMARY_TAIL +
        " AND s.xrayList = 1 AND s.pregVitals = 0 AND reproductiveStatusId != 1" +
        FILTERS + " ORDER BY s.xrayTier ASC, s.xrayKey DESC, s.beneficiaryId DESC" + PAGE

    const val TRUENAT_PAGE = FROM_SUMMARY_HEAD + "ind_tbsum_tn" + FROM_SUMMARY_TAIL +
        " AND (s.tnOther = 1 OR ((reproductiveStatusId = 1 OR s.pregVitals = 1) AND s.hasScreening = 1))" +
        FILTERS + " ORDER BY s.tnTier ASC, s.tnKey DESC, s.beneficiaryId DESC" + PAGE
}
