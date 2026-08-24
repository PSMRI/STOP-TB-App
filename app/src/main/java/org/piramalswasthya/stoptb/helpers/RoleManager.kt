package org.piramalswasthya.stoptb.helpers

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.piramalswasthya.stoptb.configuration.RoleModuleConfig
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.model.AppRole
import org.piramalswasthya.stoptb.model.ModulePrivilege
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sole runtime entry point for role/privilege logic app-wide, replacing the old
 * RoleConstants/RoleUtils single-role-string checks. [activeRole] is exactly the
 * bottom-nav-selected role — every screen reads [privilegesForActiveRole] rather than
 * consulting the logged-in user's role string directly.
 */
@Singleton
class RoleManager @Inject constructor(
    private val preferenceDao: PreferenceDao
) {

    private var _assignedRoles: List<AppRole> = emptyList()
    val assignedRoles: List<AppRole> get() = _assignedRoles

    private val _activeRole = MutableStateFlow(AppRole.VOLUNTEER)
    val activeRole: StateFlow<AppRole> = _activeRole.asStateFlow()

    /** Call once per cold start (VolunteerActivity/HomeActivity onCreate). Always resets
     *  active role to the first assigned role — no persistence across app restarts. */
    fun initializeFromLoggedInUser() {
        val user = preferenceDao.getLoggedInUser()
        _assignedRoles = AppRole.resolveAssignedRoles(
            screenNames = user?.assignedRoleScreenNames.orEmpty(),
            legacyRoleName = user?.role
        )
        _activeRole.value = _assignedRoles.firstOrNull() ?: AppRole.VOLUNTEER
        // TEMP verification log for the multi-role migration — safe to remove once confirmed working.
        Timber.d("RoleManager verify: initializeFromLoggedInUser -> assignedRoles=$_assignedRoles, activeRole=${_activeRole.value}")
    }

    fun setActiveRole(role: AppRole) {
        require(role in _assignedRoles) { "Role $role is not assigned to this user" }
        _activeRole.value = role
    }

    /** The login-gate check: does this user have at least one resolvable role? */
    fun hasAnyValidRole(): Boolean {
        val user = preferenceDao.getLoggedInUser()
        val resolved = AppRole.resolveAssignedRoles(
            screenNames = user?.assignedRoleScreenNames.orEmpty(),
            legacyRoleName = user?.role
        )
        // TEMP verification log for the multi-role migration — safe to remove once confirmed working.
        Timber.d("RoleManager verify: hasAnyValidRole -> legacyRole=${user?.role}, screenNames=${user?.assignedRoleScreenNames}, resolved=$resolved")
        return resolved.isNotEmpty()
    }

    fun privilegesForActiveRole(): ModulePrivilege =
        RoleModuleConfig.privilegeFor(_activeRole.value)
}
