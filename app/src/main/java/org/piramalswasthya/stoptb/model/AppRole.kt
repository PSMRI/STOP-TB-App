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

        /**
         * Verbatim relocation of the normalized-match logic from the old
         * `RoleConstants.isAllowedStopTbRole` — preserved exactly so the Volunteer
         * fallback below only fires for roles that were already allowed to log in today.
         */
        fun isRecognizedLegacyRoleString(roleName: String?): Boolean {
            return roleName?.trim()?.takeIf { it.isNotEmpty() }?.let { userRole ->
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
                        normalizedRole == "registrar" ||
                        normalizedRole == "volenteer"
            } ?: false
        }

        /**
         * previlegeObj-derived screenNames take priority. Only when none of them map to a
         * known [AppRole] do we fall back to VOLUNTEER, and only when the legacy role string
         * would have passed today's [isRecognizedLegacyRoleString] gate — otherwise this
         * returns empty, preserving today's login denial for roles like Asha/ProviderAdmin.
         */
        fun resolveAssignedRoles(
            screenNames: List<String>,
            legacyRoleName: String?
        ): List<AppRole> {
            val primary = screenNames.mapNotNull { fromScreenName(it) }.distinct()
            if (primary.isNotEmpty()) return primary

            return if (isRecognizedLegacyRoleString(legacyRoleName)) listOf(VOLUNTEER) else emptyList()
        }
    }
}
