package org.piramalswasthya.stoptb.model

enum class AppRole {
    REGISTRAR,
    NURSE,
    COUNSELING,
    VOLUNTEER;

    companion object {

        fun fromScreenName(screenName: String): AppRole? =
            when (screenName.trim().lowercase()) {
                "registrar" -> REGISTRAR
                "nurse" -> NURSE
                "counseling" -> COUNSELING
                else -> null
            }

        // Legacy login-allow-list fallback — removed by product decision. previlegeObj-derived
        // screenNames are now the ONLY source of truth for role resolution; an account with no
        // screenName mapping to a real AppRole is denied login outright, regardless of its old
        // flat `role` string. Left commented in place for reference (not deleted, per project
        // convention), not because it's still used.
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
         * previlegeObj-derived screenNames are the ONLY source of truth for role resolution.
         * An account whose screenNames don't map to any known AppRole has no usable role —
         * there is no legacy-role-string fallback to VOLUNTEER anymore (product decision).
         * Roles are ordered canonically (REGISTRAR, NURSE, COUNSELING, VOLUNTEER) so that
         * multi-role accounts always default to the 0th index tab on launch.
         */
        fun resolveAssignedRoles(screenNames: List<String>): List<AppRole> {
            val resolved = screenNames.mapNotNull { fromScreenName(it) }.toSet()
            val canonicalOrder = listOf(REGISTRAR, NURSE, COUNSELING, VOLUNTEER)
            return canonicalOrder.filter { it in resolved }
        }
    }
}
