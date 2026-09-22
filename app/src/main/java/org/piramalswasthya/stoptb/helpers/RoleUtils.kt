package org.piramalswasthya.stoptb.helpers

import org.piramalswasthya.stoptb.model.AppRole

fun String?.normalizedRoleName(): String =
    this.orEmpty()
        .trim()
        .lowercase()
        .replace(" ", "")
        .replace("-", "")
        .replace("_", "")

fun String?.isRegistrationOfficerRole(): Boolean {
    val role = normalizedRoleName()
    return role == "registrar" ||
            role == "registrationofficer" ||
            role == "registration" ||
            role == "register" ||
            role == "registar" ||
            role == "registor" ||
            role == "registerbeneficiary" ||
            role == "registrationbeneficiary" ||
            role == "beneficiaryregistration" ||
            role.contains("registrar") ||
            role.contains("registration")
}

fun String?.isNurseRole(): Boolean =
    normalizedRoleName() == "nurse"

fun String?.isCounsellingOfficerRole(): Boolean {
    val role = normalizedRoleName()
    return role == "counsellingofficer" || role == "counselingofficer" || role == "counsellor" || role == "counselor"
}

/**
 * Upgrade shim for accounts that last logged in on release-2.1 or earlier.
 *
 * Those installs have a stored [org.piramalswasthya.stoptb.model.User] blob with only the flat
 * `role` string and no `assignedRoleScreenNames`, so screenName resolution finds nothing and the
 * user is denied login. Mapping the old string back to an [AppRole] lets them in with the right
 * privileges until their next online login rewrites the blob with real screenNames.
 *
 * Deliberately narrower than the old `RoleConstants.isAllowedStopTbRole`: "Volunteer" and every
 * other legacy value stay denied, so this does not reinstate the VOLUNTEER fallback that was
 * removed by product decision.
 *
 * [isRegistrationOfficerRole] ends with a greedy `contains` check, so it must be evaluated last.
 */
fun String?.toLegacyAppRole(): AppRole? = when {
    isNullOrBlank() -> null
    isNurseRole() -> AppRole.NURSE
    isCounsellingOfficerRole() -> AppRole.COUNSELING
    isRegistrationOfficerRole() -> AppRole.REGISTRAR
    else -> null
}
