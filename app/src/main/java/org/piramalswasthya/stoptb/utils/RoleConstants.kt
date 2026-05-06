package org.piramalswasthya.stoptb.utils

object RoleConstants {
    const val ROLE_ASHA = "Asha"
    const val ROLE_ASHA_SUPERVISOR = "ASHA Supervisor"
    const val ROLE_PROVIDER_ADMIN = "ProviderAdmin"
    const val ROLE_VOLUNTEER  = "Volunteer"
    const val ROLE_REGISTRATION_OFFICER = "Registration Officer"
    const val ROLE_NURSE = "Nurse"
    const val ROLE_COUNSELLING_OFFICER = "Counselling Officer"
    const val ROLE_COUNSELING_OFFICER = "Counseling Officer"

    fun isAllowedStopTbRole(role: String?): Boolean {
        return role?.trim()?.takeIf { it.isNotEmpty() }?.let { userRole ->
            val normalizedRole = userRole
                .lowercase()
                .replace(" ", "")
                .replace("-", "")
                .replace("_", "")

            normalizedRole == "registrationofficer" ||
                    normalizedRole == "nurse" ||
                    normalizedRole == "counsellingofficer" ||
                    normalizedRole == "counselingofficer" ||
                    normalizedRole == "counsellor" ||
                    normalizedRole == "counselor" ||
                    normalizedRole == "volunteer" ||
                    normalizedRole == "volenteer"
        } ?: false
    }
}
