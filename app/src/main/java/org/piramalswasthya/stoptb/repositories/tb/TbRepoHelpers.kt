package org.piramalswasthya.stoptb.repositories.tb

import org.piramalswasthya.stoptb.database.room.dao.BenDao

/** The UI reads the Nikshay ID from BENEFICIARY, so copy it there from TB records. */
internal suspend fun BenDao.linkNikshayId(benId: Long, nikshayId: String?) {
    val value = nikshayId?.trim()?.takeIf { it.isNotEmpty() && !it.equals("N/A", ignoreCase = true) }
        ?: return
    updateNikshayId(benId, value)
}

/** Liquid culture is stored as "Positive" / "Negative" (R.array.tb_test_result). */
internal fun isLiquidCulturePositive(result: String?): Boolean =
    result?.trim()?.equals("Positive", ignoreCase = true) == true

/** A not-conducted reason as sent to the server: "Other: <text>" for Other, else the reason. */
internal fun formatNotConductedReason(reason: String?, other: String?): String? {
    if (reason.isNullOrBlank()) return null
    return if (reason.equals("Other", ignoreCase = true) && !other.isNullOrBlank()) "Other: $other" else reason
}
