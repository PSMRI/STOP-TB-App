package org.piramalswasthya.stoptb.configuration

import org.piramalswasthya.stoptb.model.AppModule
import org.piramalswasthya.stoptb.model.AppRole
import org.piramalswasthya.stoptb.model.ExamineDenominatorRule
import org.piramalswasthya.stoptb.model.ExamineRowSet
import org.piramalswasthya.stoptb.model.ModulePrivilege
import org.piramalswasthya.stoptb.model.Permission
import org.piramalswasthya.stoptb.model.SyncRowFilter

/**
 * Single source of truth for role -> module/privilege mapping. Adding a role means adding
 * one enum entry to [AppRole] plus one entry in this map — nothing else.
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
            // Registrar: full CRUD everywhere it has access.
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
            // Nurse: View-only on Household/Beneficiaries/Non-Household, full CRUD on
            // Anthropometry/TB Screening (General Exam/OPD have no field — see ModulePrivilege).
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
            // Includes Referral/Tuberculosis alongside the 4 counselling modules — needed so a
            // Registrar+Counsellor account (no Nurse tab) can still reach them. RoleManager
            // removes them again if Nurse is also assigned — see multiRoleHomeModulesFor().
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
            // Examine sheet's own row/permission checks already restrict Counsellor correctly,
            // so the button itself doesn't need to be hidden by default too.
            showExamineButtonDefault = true,
            allowQuickRefresh = true,
            // Counsellor: View-only on Household/Beneficiaries/Non-Household/Anthropometry/TB
            // Screening. Full CRUD on the counselling modules is enforced elsewhere, not by
            // this Permission model — see the Counselling-module note in the docs.
            householdPermission = Permission.VIEW,
            beneficiaryPermission = Permission.VIEW,
            nonHouseholdPermission = Permission.VIEW,
            anthropometryPermission = Permission.VIEW,
            tbScreeningPermission = Permission.VIEW
        ),
        // VOLUNTEER can't be assigned to a real user (login is denied instead) — this entry is
        // a defensive placeholder only, for RoleManager's initial state and privilegeFor()'s
        // fallback if a role is ever missing from this map.
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
            // Unreachable via real login — kept maximally permissive, matching old behavior.
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
