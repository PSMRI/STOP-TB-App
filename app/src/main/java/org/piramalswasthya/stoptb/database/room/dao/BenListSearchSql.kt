package org.piramalswasthya.stoptb.database.room.dao

/**
 * The TB lists' search box as SQL, so a paged list filters in the database instead of loading
 * every row of the village to filter in Kotlin. Expects BEN_BASIC_CACHE aliased as `b` and a
 * lowercased `:query` bind ('' = no filter).
 *
 * Matches the fields the old Kotlin filters (helpers/CommonUtils filterTb*List) checked:
 * beneficiary ID, full name, family head, spouse, father, mobile, gender, RCH ID (digits only)
 * and age. Age is matched on whole years (dob 0 = unknown, as getAgeDisplayString treats it;
 * a negative dob is a birth before 1970 and is matched like any other); the old filter matched the display text ("2 Months",
 * "30 Years"), so typing a month/day count for an infant no longer finds it.
 */
object BenListSearchSql {

    private const val CONTAINS = " LIKE '%' || :query || '%'"

    const val MATCHES = "(:query = ''" +
        " OR CAST(b.benId AS TEXT)" + CONTAINS +
        " OR (IFNULL(b.benName, '') || ' ' || IFNULL(b.benSurname, ''))" + CONTAINS +
        " OR IFNULL(b.familyHeadName, '')" + CONTAINS +
        " OR IFNULL(b.spouseName, '')" + CONTAINS +
        " OR IFNULL(b.fatherName, '')" + CONTAINS +
        " OR CAST(b.mobileNo AS TEXT)" + CONTAINS +
        " OR IFNULL(b.gender, '')" + CONTAINS +
        " OR (IFNULL(b.rchId, '') <> '' AND b.rchId NOT GLOB '*[^0-9]*' AND b.rchId" + CONTAINS + ")" +
        " OR (IFNULL(b.dob, 0) <> 0 AND CAST(CAST((strftime('%s','now') - b.dob / 1000) / 31557600 AS INTEGER) AS TEXT)" + CONTAINS + "))"
}
