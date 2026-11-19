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

/**
 * Sync state for a diagnostics row after a server order result was merged into it: SYNCED,
 * unless the row still holds local edits that have not been uploaded (UNSYNCED, or SYNCING
 * while a push is in flight). Those must stay queued, or the edits would never reach the server.
 */
internal fun syncStateAfterServerMerge(existing: org.piramalswasthya.stoptb.model.TBDiagnosticsCache?) =
    when (existing?.syncState) {
        org.piramalswasthya.stoptb.database.room.SyncState.UNSYNCED,
        org.piramalswasthya.stoptb.database.room.SyncState.SYNCING -> existing.syncState
        else -> org.piramalswasthya.stoptb.database.room.SyncState.SYNCED
    }
