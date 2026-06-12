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
