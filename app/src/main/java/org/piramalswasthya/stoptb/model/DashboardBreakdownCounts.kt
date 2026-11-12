package org.piramalswasthya.stoptb.model

/** One dashboard section's numbers, returned by a single query (TbDashboardBreakdownQueries). */
data class DashboardBreakdownCounts(
    val total: Int = 0,
    val male: Int = 0,
    val female: Int = 0,
    val children: Int = 0,
    val others: Int = 0,
    val seniorCitizen: Int = 0
)
