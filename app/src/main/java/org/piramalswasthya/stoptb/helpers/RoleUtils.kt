package org.piramalswasthya.stoptb.helpers

fun String?.normalizedRoleName(): String =
    this.orEmpty()
        .trim()
        .lowercase()
        .replace(" ", "")
        .replace("-", "")
        .replace("_", "")

fun String?.isRegistrationOfficerRole(): Boolean {
    val role = normalizedRoleName()
    return role == "registrar" || role == "registrationofficer"
}

fun String?.isNurseRole(): Boolean =
    normalizedRoleName() == "nurse"

fun String?.isCounsellingOfficerRole(): Boolean {
    val role = normalizedRoleName()
    return role == "counsellingofficer" || role == "counselingofficer" || role == "counsellor" || role == "counselor"
}
