package org.piramalswasthya.stoptb.ui.home_activity.new_household_registration

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Door-lock households are partial registrations. The checkbox only relaxes
 * mandatory validation. A submit with every mandatory field filled is a
 * completed household even if the box is still ticked.
 */
object DoorLockSubmit {
    enum class Result {
        BLOCKED,
        DOOR_LOCK,
        COMPLETE
    }

    fun resolve(doorLockChecked: Boolean, mandatoryComplete: Boolean): Result {
        if (mandatoryComplete) return Result.COMPLETE
        return if (doorLockChecked) Result.DOOR_LOCK else Result.BLOCKED
    }

    /** A later visit that is still locked replaces the date shown on the badge. */
    fun recordsVisitDate(result: Result): Boolean = result == Result.DOOR_LOCK

    /**
     * Sync flag written on save. Null means leave the current flag unchanged.
     * A household that has never reached the server stays a pending new record,
     * including the save that clears the door lock. An already synced household
     * becomes an update. A locked edit of a synced household is not uploaded.
     */
    data class HouseholdSyncMarker(val serverUpdatedStatus: Int, val processed: String)

    fun syncMarker(alreadyOnServer: Boolean, asDoorLock: Boolean): HouseholdSyncMarker? {
        if (asDoorLock && alreadyOnServer) return null
        return if (alreadyOnServer) {
            HouseholdSyncMarker(serverUpdatedStatus = 2, processed = "U")
        } else {
            HouseholdSyncMarker(serverUpdatedStatus = 1, processed = "N")
        }
    }

    fun shouldLinkBeneficiary(asDoorLock: Boolean, benId: Long): Boolean =
        !asDoorLock && benId != 0L

    fun formatVisitDate(epochMillis: Long?): String {
        if (epochMillis == null || epochMillis <= 0L) return ""
        return SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(Date(epochMillis))
    }
}
