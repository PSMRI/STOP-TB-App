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
 * Sole runtime entry point for role/privilege logic app-wide, replacing the old
 * RoleConstants/RoleUtils single-role-string checks.
 *
 * Two different things a screen might want, and they are NOT interchangeable:
 * - [privilegesForActiveRole] — exactly the bottom-nav-selected role's privileges. Used ONLY
 *   for Home-card display ([ModulePrivilege.homeModules]/[ModulePrivilege.multiRoleHomeModules]) —
 *   "tabs organize which Home cards you see," nothing more.
 * - [privilegesUnion] — the combined (most-permissive) privileges across EVERY role this user
 *   is assigned, regardless of which tab is active. Every other screen/permission decision in
 *   the app reads this — a Registrar+Nurse user keeps Nurse's Referral/General-Exam access
 *   even while sitting on the Registration tab.
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
        // TEMP verification log for the multi-role migration — safe to remove once confirmed working.
        Timber.d("RoleManager verify: initializeFromLoggedInUser -> legacyRole=${user?.role} (no longer consulted), assignedRoles=$_assignedRoles, activeRole=${_activeRole.value}")
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
        // TEMP verification log for the multi-role migration — safe to remove once confirmed working.
        Timber.d("RoleManager verify: hasAnyValidRole -> legacyRole=${user?.role} (no longer consulted), screenNames=${user?.assignedRoleScreenNames}, resolved=$resolved")
        return resolved.isNotEmpty()
    }

    fun privilegesForActiveRole(): ModulePrivilege =
        RoleModuleConfig.privilegeFor(_activeRole.value)

    /**
     * Multi-role Home-card set for [role]'s bottom-nav tab, deduplicated across tabs.
     * Counselling's own [ModulePrivilege.multiRoleHomeModules] includes Referral/Tuberculosis —
     * needed so a Registrar+Counsellor account (no Nurse tab to offer them) can still reach
     * those two modules. But if Nurse is ALSO assigned, Nurse's own tab already offers
     * Referral/Tuberculosis, so Counselling's tab drops them rather than showing the same two
     * cards on two different tabs. This is deliberately cross-role logic (needs [assignedRoles],
     * not just [role]), which is why it lives here rather than in the per-role-static
     * [RoleModuleConfig].
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
     * fields, [ModulePrivilege.examineRowSet] and [ModulePrivilege.examineDenominatorRule].
     * Those two aren't a role-permission question — they answer "what's relevant to the
     * workflow this Examine sheet was opened from." [showContactTracingForms] is the existing
     * signal (already threaded through AllBenFragment/HouseholdMembersFragment/NonHHFragment →
     * ExamineBottomSheetFragment → BenListAdapter/BenPagingAdapter) for "this is the
     * Counselling/TPT-module workflow, not the general beneficiary list" — when true, this
     * forces Counsellor's own module-specific values regardless of which OTHER roles are also
     * assigned, exactly matching what a solo Counsellor already gets since there's nothing to
     * union against. When false, falls back to the normal role union. Without this override, a
     * Registrar+Nurse+Counsellor user would leak Nurse's General Exam/OPD rows into the
     * Counselling-specific workflow (examineRowSet), and would lose Nurse's forms from the
     * general beneficiary list's denominator badge (examineDenominatorRule) — both were real
     * bugs before this function existed.
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
