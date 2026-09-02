package org.piramalswasthya.stoptb.helpers

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.piramalswasthya.stoptb.configuration.RoleModuleConfig
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.model.AppModule
import org.piramalswasthya.stoptb.model.AppRole
import org.piramalswasthya.stoptb.model.ModulePrivilege
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sole runtime entry point for role/privilege logic app-wide.
 *
 * Two different questions, answered differently:
 * - [privilegesForActiveRole] — the bottom-nav-selected role only. Used ONLY for Home-card
 *   display: which tab you're on decides which cards you see.
 * - [privilegesUnion] — combined across EVERY assigned role, regardless of active tab. Used for
 *   everything else: a Registrar+Nurse user keeps Nurse's Referral access even on the
 *   Registration tab, because that's an account capability, not a display concern.
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
            screenNames = user?.assignedRoleScreenNames.orEmpty()
        )
        _activeRole.value = _assignedRoles.firstOrNull() ?: AppRole.VOLUNTEER
        Timber.d("RoleManager: assignedRoles=$_assignedRoles, activeRole=${_activeRole.value}")
    }

    fun setActiveRole(role: AppRole) {
        require(role in _assignedRoles) { "Role $role is not assigned to this user" }
        _activeRole.value = role
    }

    /** The login-gate check: does this user have at least one resolvable role? */
    fun hasAnyValidRole(): Boolean {
        val user = preferenceDao.getLoggedInUser()
        val resolved = AppRole.resolveAssignedRoles(
            screenNames = user?.assignedRoleScreenNames.orEmpty()
        )
        Timber.d("RoleManager: hasAnyValidRole screenNames=${user?.assignedRoleScreenNames}, resolved=$resolved")
        return resolved.isNotEmpty()
    }

    fun privilegesForActiveRole(): ModulePrivilege =
        RoleModuleConfig.privilegeFor(_activeRole.value)

    /**
     * Multi-role Home-card set for [role]'s tab, with duplicates removed across tabs.
     * Counselling's own set includes Referral/Tuberculosis (so a Registrar+Counsellor account
     * can still reach them). But if Nurse is also assigned, Nurse's tab already shows them, so
     * Counselling's tab drops them. Needs [assignedRoles], not just [role] — that's why this
     * cross-role adjustment lives here rather than in the per-role-static [RoleModuleConfig].
     */
    fun multiRoleHomeModulesFor(role: AppRole): Set<AppModule> {
        val base = RoleModuleConfig.privilegeFor(role).multiRoleHomeModules
        if (role == AppRole.COUNSELING && AppRole.NURSE in _assignedRoles) {
            return base - setOf(AppModule.REFERRAL, AppModule.TUBERCULOSIS)
        }
        return base
    }

    /** Union of every assigned role's privileges — see class doc. Falls back to VOLUNTEER's
     *  privileges only in the defensive case of assignedRoles being empty (shouldn't happen
     *  post-login-gate). */
    fun privilegesUnion(): ModulePrivilege {
        if (_assignedRoles.isEmpty()) return RoleModuleConfig.privilegeFor(AppRole.VOLUNTEER)
        return ModulePrivilege.union(_assignedRoles.map { RoleModuleConfig.privilegeFor(it) })
    }

    /**
     * Context-aware variant of [privilegesUnion] for the Examine sheet's two workflow-scoped
     * fields (which forms show, and the "x/y" denominator). These depend on which workflow the
     * sheet was opened from, not just role permissions. [showContactTracingForms] signals "this
     * is the Counselling/TPT workflow" — when true, forces Counsellor's own values regardless of
     * which other roles are assigned; otherwise falls back to the normal union.
     */
    fun examinePrivilegesFor(showContactTracingForms: Boolean): ModulePrivilege {
        val union = privilegesUnion()
        if (!showContactTracingForms || AppRole.COUNSELING !in _assignedRoles) return union
        val counselling = RoleModuleConfig.privilegeFor(AppRole.COUNSELING)
        return union.copy(
            examineRowSet = counselling.examineRowSet,
            examineDenominatorRule = counselling.examineDenominatorRule
        )
    }
}
