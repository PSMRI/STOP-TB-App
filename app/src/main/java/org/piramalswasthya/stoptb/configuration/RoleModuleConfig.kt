package org.piramalswasthya.stoptb.configuration

import org.piramalswasthya.stoptb.model.AppModule
import org.piramalswasthya.stoptb.model.AppRole
import org.piramalswasthya.stoptb.model.ExamineDenominatorRule
import org.piramalswasthya.stoptb.model.ExamineRowSet
import org.piramalswasthya.stoptb.model.ModulePrivilege
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
            allowQuickRefresh = true
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
            allowQuickRefresh = true
        ),
        AppRole.COUNSELING to ModulePrivilege(
            homeModules = setOf(
                AppModule.HOUSEHOLD, AppModule.BENEFICIARIES, AppModule.NON_HOUSEHOLD,
                AppModule.TUBERCULOSIS, AppModule.REFERRAL
            ),
            multiRoleHomeModules = setOf(
                AppModule.COUNSELLING, AppModule.CONTACT_TRACING,
                AppModule.TB_TREATMENT_FOLLOWUP, AppModule.TPT
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
            showExamineButtonDefault = false,
            allowQuickRefresh = true
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
            allowQuickRefresh = false
        )
    )

    fun privilegeFor(role: AppRole): ModulePrivilege =
        privilegesByRole[role] ?: privilegesByRole.getValue(AppRole.VOLUNTEER)
}
