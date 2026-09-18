package org.piramalswasthya.stoptb.model

enum class AppRole {
    REGISTRAR,
    NURSE,
    COUNSELING,
    LAB_TECHNICIAN,
    VOLUNTEER;

    companion object {

        fun fromScreenName(screenName: String): AppRole? =
            when (screenName.trim().lowercase().replace(" ", "")) {
                "registrar" -> REGISTRAR
                "nurse" -> NURSE
                "counseling" -> COUNSELING
                "labtechnician" -> LAB_TECHNICIAN
                else -> null
            }

//        /**
//         * Verbatim relocation of the normalized-match logic from the old
//         * `RoleConstants.isAllowedStopTbRole` — preserved exactly so the Volunteer
//         * fallback below only fires for roles that were already allowed to log in today.
//         */
//        fun isRecognizedLegacyRoleString(roleName: String?): Boolean {
//            return roleName?.trim()?.takeIf { it.isNotEmpty() }?.let { userRole ->
//                val normalizedRole = userRole
//                    .lowercase()
//                    .replace(" ", "")
//                    .replace("-", "")
//                    .replace("_", "")
//
//                normalizedRole == "registrationofficer" ||
//                        normalizedRole == "nurse" ||
//                        normalizedRole == "counsellingofficer" ||
//                        normalizedRole == "counselingofficer" ||
//                        normalizedRole == "counsellor" ||
//                        normalizedRole == "counselor" ||
//                        normalizedRole == "volunteer" ||
//                        normalizedRole == "registrar" ||
//                        normalizedRole == "volenteer"
//            } ?: false
//        }

        /**
         * Maps backend screenNames to roles. An account with no recognized screenName has no
         * usable role (no fallback). Result is ordered canonically (REGISTRAR, NURSE,
         * COUNSELING) regardless of backend order, so the first tab is always deterministic.
         */
        fun resolveAssignedRoles(screenNames: List<String>): List<AppRole> {
            val resolved = screenNames.mapNotNull { fromScreenName(it) }.toSet()
            val canonicalOrder = listOf(REGISTRAR, NURSE, COUNSELING, LAB_TECHNICIAN, VOLUNTEER)
            return canonicalOrder.filter { it in resolved }
        }
    }
}
