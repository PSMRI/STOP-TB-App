package org.piramalswasthya.stoptb.configuration

import org.piramalswasthya.stoptb.model.AppModule
import org.piramalswasthya.stoptb.model.AppRole
import org.piramalswasthya.stoptb.model.ExamineDenominatorRule
import org.piramalswasthya.stoptb.model.ExamineRowSet
import org.piramalswasthya.stoptb.model.ModulePrivilege
import org.piramalswasthya.stoptb.model.Permission
import org.piramalswasthya.stoptb.model.SyncRowFilter

/**
 * Single source of truth for role -> module/privilege mapping. Values are a direct
 * codification of the legacy per-screen role-string behavior (RoleConstants/RoleUtils
 * call sites) — see the truth table in the multi-role-user-access plan doc. Adding a
 * future role means adding one enum entry to [AppRole] plus one entry in this map.
 */
object RoleModuleConfig {

    val privilegesByRole: Map<AppRole, ModulePrivilege> = mapOf(
        AppRole.REGISTRAR to ModulePrivilege(
            homeModules = setOf(AppModule.HOUSEHOLD, AppModule.BENEFICIARIES, AppModule.NON_HOUSEHOLD),
            multiRoleHomeModules = setOf(AppModule.HOUSEHOLD, AppModule.BENEFICIARIES, AppModule.NON_HOUSEHOLD),
            syncShowCounsellingStatusRow = false,
            syncBottomSheetRowFilter = SyncRowFilter.REGISTRAR_ROWS_ONLY,
            examineRowSet = ExamineRowSet.ANTHROPOMETRY_AND_TB_SCREENING_ONLY,
            examineReorderTbScreeningBeforeAnthropometry = true,
            examineLockGeneralFormsBehindTbScreening = false,
            examineShowContactTracingRows = false,
            examineDenominatorRule = ExamineDenominatorRule.REGISTRAR_TWO,
            canActOnReferral = false,
            showRegisterSpouseButtons = true,
            showTbConfirmedCounsellingUi = false,
            showAbhaButton = true,
            showCallButton = true,
            showExamineButtonDefault = true,
            allowQuickRefresh = true,
            // Registrar (acceptance criteria item 1): full CRUD on Household/Beneficiaries/
            // Non-Household/Anthropometry/TB Screening.
            householdPermission = Permission.FULL,
            beneficiaryPermission = Permission.FULL,
            nonHouseholdPermission = Permission.FULL,
            anthropometryPermission = Permission.FULL,
            tbScreeningPermission = Permission.FULL
        ),
        AppRole.NURSE to ModulePrivilege(
            homeModules = setOf(
                AppModule.HOUSEHOLD, AppModule.BENEFICIARIES, AppModule.NON_HOUSEHOLD,
                AppModule.TUBERCULOSIS, AppModule.REFERRAL
            ),
            multiRoleHomeModules = setOf(AppModule.REFERRAL, AppModule.TUBERCULOSIS),
            syncShowCounsellingStatusRow = false,
            syncBottomSheetRowFilter = SyncRowFilter.ALL_EXCEPT_COUNSELLING,
            examineRowSet = ExamineRowSet.ALL_FOUR,
            examineReorderTbScreeningBeforeAnthropometry = true,
            examineLockGeneralFormsBehindTbScreening = true,
            examineShowContactTracingRows = false,
            examineDenominatorRule = ExamineDenominatorRule.GENERIC_FOUR,
            canActOnReferral = true,
            showRegisterSpouseButtons = false,
            showTbConfirmedCounsellingUi = false,
            showAbhaButton = true,
            showCallButton = true,
            showExamineButtonDefault = true,
            allowQuickRefresh = true,
            // Nurse (acceptance criteria item 2): View-only on Household/Beneficiaries/
            // Non-Household; full CRUD on Anthropometry/TB Screening (General Exam/OPD have no
            // dedicated field — see the ModulePrivilege doc, they're always FULL when Nurse's
            // presence makes them reachable at all).
            householdPermission = Permission.VIEW,
            beneficiaryPermission = Permission.VIEW,
            nonHouseholdPermission = Permission.VIEW,
            anthropometryPermission = Permission.FULL,
            tbScreeningPermission = Permission.FULL
        ),
        AppRole.COUNSELING to ModulePrivilege(
            homeModules = setOf(
                AppModule.HOUSEHOLD, AppModule.BENEFICIARIES, AppModule.NON_HOUSEHOLD,
                AppModule.TUBERCULOSIS, AppModule.REFERRAL
            ),
            // Includes REFERRAL/TUBERCULOSIS alongside the 4 counselling-family modules: the
            // acceptance criteria's solo-Counsellor list (item 3) grants Referral CRUD + TB
            // section View in addition to the counselling modules, and the Registrar+Counsellor
            // union example (item 2) expects both reachable — they can only come from this tab,
            // since Registrar's own set has neither.
            multiRoleHomeModules = setOf(
                AppModule.COUNSELLING, AppModule.CONTACT_TRACING,
                AppModule.TB_TREATMENT_FOLLOWUP, AppModule.TPT,
                AppModule.REFERRAL, AppModule.TUBERCULOSIS
            ),
            syncShowCounsellingStatusRow = true,
            syncBottomSheetRowFilter = SyncRowFilter.COUNSELLING_ROWS_ONLY,
            examineRowSet = ExamineRowSet.ANTHROPOMETRY_AND_TB_SCREENING_ONLY,
            examineReorderTbScreeningBeforeAnthropometry = false,
            examineLockGeneralFormsBehindTbScreening = false,
            examineShowContactTracingRows = true,
            examineDenominatorRule = ExamineDenominatorRule.COUNSELLING_DYNAMIC,
            canActOnReferral = true,
            showRegisterSpouseButtons = false,
            showTbConfirmedCounsellingUi = true,
            showAbhaButton = true,
            showCallButton = false,
            // Was false (legacy-preserved from the pre-Gap-2 app, where the only safe way to
            // give Counsellor any visibility into Anthropometry/TB Screening was the special
            // fromContactTracing=true path from TB Confirmed). Now that Gap 2 properly enforces
            // View-only access inside the Examine sheet itself (examineRowSet restricts rows to
            // Anthropometry/TB Screening; formPermissionFor() locks the submit buttons), hiding
            // the button entirely on the plain Household Members / All Beneficiaries browsing
            // path denied Counsellor their legitimate View access from anywhere except that one
            // TB-Confirmed-linked route. HouseholdMembersFragment.kt is the only consumer of
            // this field — verified before flipping it.
            showExamineButtonDefault = true,
            allowQuickRefresh = true,
            // Counsellor (acceptance criteria item 3): View-only on Household/Beneficiaries/
            // Non-Household/Anthropometry/TB Screening. Full CRUD on the 4 counselling-family
            // modules is enforced separately (those screens aren't gated by this Permission
            // model — see the Counselling-module recon note).
            householdPermission = Permission.VIEW,
            beneficiaryPermission = Permission.VIEW,
            nonHouseholdPermission = Permission.VIEW,
            anthropometryPermission = Permission.VIEW,
            tbScreeningPermission = Permission.VIEW
        ),
        // AppRole.VOLUNTEER can no longer be assigned to a real logged-in user —
        // AppRole.resolveAssignedRoles() has no legacy-role fallback anymore, so a user whose
        // previlegeObj doesn't map to REGISTRAR/NURSE/COUNSELING is denied login outright
        // rather than resolving to VOLUNTEER. This entry only exists as a defensive
        // placeholder: RoleManager's activeRole StateFlow needs a non-null initial value
        // before initializeFromLoggedInUser() runs, and RoleModuleConfig.privilegeFor()
        // falls back to it if a role were ever missing from this map.
        AppRole.VOLUNTEER to ModulePrivilege(
            homeModules = setOf(AppModule.HOUSEHOLD, AppModule.BENEFICIARIES),
            multiRoleHomeModules = setOf(AppModule.HOUSEHOLD, AppModule.BENEFICIARIES),
            syncShowCounsellingStatusRow = false,
            syncBottomSheetRowFilter = SyncRowFilter.ALL_EXCEPT_COUNSELLING,
            examineRowSet = ExamineRowSet.ALL_FOUR,
            examineReorderTbScreeningBeforeAnthropometry = false,
            examineLockGeneralFormsBehindTbScreening = false,
            examineShowContactTracingRows = false,
            examineDenominatorRule = ExamineDenominatorRule.GENERIC_FOUR,
            canActOnReferral = false,
            showRegisterSpouseButtons = true,
            showTbConfirmedCounsellingUi = false,
            showAbhaButton = true,
            showCallButton = true,
            showExamineButtonDefault = true,
            allowQuickRefresh = false,
            // Unreachable via real login (see comment above) — kept maximally permissive to
            // match the old fallback's observed behavior (showExamineButtonDefault=true,
            // examineRowSet=ALL_FOUR) rather than introducing a new restriction nobody asked for.
            householdPermission = Permission.FULL,
            beneficiaryPermission = Permission.FULL,
            nonHouseholdPermission = Permission.FULL,
            anthropometryPermission = Permission.FULL,
            tbScreeningPermission = Permission.FULL
        )
    )

    fun privilegeFor(role: AppRole): ModulePrivilege =
        privilegesByRole[role] ?: privilegesByRole.getValue(AppRole.VOLUNTEER)
}
