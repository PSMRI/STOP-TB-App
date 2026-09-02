package org.piramalswasthya.stoptb.configuration

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.piramalswasthya.stoptb.model.AppModule
import org.piramalswasthya.stoptb.model.AppRole
import org.piramalswasthya.stoptb.model.ExamineDenominatorRule
import org.piramalswasthya.stoptb.model.ExamineRowSet
import org.piramalswasthya.stoptb.model.ModulePrivilege
import org.piramalswasthya.stoptb.model.Permission
import org.piramalswasthya.stoptb.model.SyncRowFilter

class RoleModuleConfigTest {

    @Test
    fun `every AppRole has a privilege entry`() {
        AppRole.entries.forEach { role ->
            assertThat(RoleModuleConfig.privilegesByRole).containsKey(role)
        }
    }

    @Test
    fun `registrar home modules and privileges match the product spec`() {
        val p = RoleModuleConfig.privilegeFor(AppRole.REGISTRAR)
        assertThat(p.homeModules).containsExactly(
            AppModule.HOUSEHOLD, AppModule.BENEFICIARIES, AppModule.NON_HOUSEHOLD
        )
        assertThat(p.multiRoleHomeModules).containsExactly(
            AppModule.HOUSEHOLD, AppModule.BENEFICIARIES, AppModule.NON_HOUSEHOLD
        )
        assertThat(p.canActOnReferral).isFalse()
        assertThat(p.examineDenominatorRule).isEqualTo(ExamineDenominatorRule.REGISTRAR_TWO)
        assertThat(p.syncBottomSheetRowFilter).isEqualTo(SyncRowFilter.REGISTRAR_ROWS_ONLY)
        assertThat(p.householdPermission).isEqualTo(Permission.FULL)
        assertThat(p.beneficiaryPermission).isEqualTo(Permission.FULL)
        assertThat(p.nonHouseholdPermission).isEqualTo(Permission.FULL)
        assertThat(p.anthropometryPermission).isEqualTo(Permission.FULL)
        assertThat(p.tbScreeningPermission).isEqualTo(Permission.FULL)
    }

    @Test
    fun `nurse gets TB and referral home cards plus full examine access`() {
        val p = RoleModuleConfig.privilegeFor(AppRole.NURSE)
        assertThat(p.homeModules).containsExactly(
            AppModule.HOUSEHOLD, AppModule.BENEFICIARIES, AppModule.NON_HOUSEHOLD,
            AppModule.TUBERCULOSIS, AppModule.REFERRAL
        )
        // Multi-role "Treatment" tab is deliberately narrower than the single-role set above:
        // Referral + Tuberculosis only, per the product spec.
        assertThat(p.multiRoleHomeModules).containsExactly(
            AppModule.REFERRAL, AppModule.TUBERCULOSIS
        )
        assertThat(p.examineRowSet).isEqualTo(ExamineRowSet.ALL_FOUR)
        assertThat(p.examineLockGeneralFormsBehindTbScreening).isTrue()
        assertThat(p.canActOnReferral).isTrue()
        assertThat(p.allowQuickRefresh).isTrue()
        // View-only on Household/Beneficiaries/Non-HH, full CRUD on Anthropometry/TB Screening.
        assertThat(p.householdPermission).isEqualTo(Permission.VIEW)
        assertThat(p.beneficiaryPermission).isEqualTo(Permission.VIEW)
        assertThat(p.nonHouseholdPermission).isEqualTo(Permission.VIEW)
        assertThat(p.anthropometryPermission).isEqualTo(Permission.FULL)
        assertThat(p.tbScreeningPermission).isEqualTo(Permission.FULL)
    }

    @Test
    fun `counseling sees the counselling-specific UI and rows`() {
        val p = RoleModuleConfig.privilegeFor(AppRole.COUNSELING)
        assertThat(p.syncShowCounsellingStatusRow).isTrue()
        assertThat(p.showTbConfirmedCounsellingUi).isTrue()
        assertThat(p.syncBottomSheetRowFilter).isEqualTo(SyncRowFilter.COUNSELLING_ROWS_ONLY)
        assertThat(p.examineShowContactTracingRows).isTrue()
        assertThat(p.showCallButton).isFalse()
        // Multi-role tab also carries Referral/Tuberculosis — no other tab offers them here.
        assertThat(p.multiRoleHomeModules).containsExactly(
            AppModule.COUNSELLING, AppModule.CONTACT_TRACING,
            AppModule.TB_TREATMENT_FOLLOWUP, AppModule.TPT,
            AppModule.REFERRAL, AppModule.TUBERCULOSIS
        )
        // View-only everywhere outside the counselling modules themselves.
        assertThat(p.householdPermission).isEqualTo(Permission.VIEW)
        assertThat(p.beneficiaryPermission).isEqualTo(Permission.VIEW)
        assertThat(p.nonHouseholdPermission).isEqualTo(Permission.VIEW)
        assertThat(p.anthropometryPermission).isEqualTo(Permission.VIEW)
        assertThat(p.tbScreeningPermission).isEqualTo(Permission.VIEW)
        // Reachable via plain browsing, not only via TB Confirmed's fromContactTracing route.
        assertThat(p.showExamineButtonDefault).isTrue()
    }

    @Test
    fun `registrar plus nurse union grants full CRUD everywhere either role does`() {
        val union = ModulePrivilege.union(
            listOf(RoleModuleConfig.privilegeFor(AppRole.REGISTRAR), RoleModuleConfig.privilegeFor(AppRole.NURSE))
        )
        assertThat(union.householdPermission).isEqualTo(Permission.FULL)
        assertThat(union.beneficiaryPermission).isEqualTo(Permission.FULL)
        assertThat(union.nonHouseholdPermission).isEqualTo(Permission.FULL)
        assertThat(union.anthropometryPermission).isEqualTo(Permission.FULL)
        assertThat(union.tbScreeningPermission).isEqualTo(Permission.FULL)
    }

    @Test
    fun `registrar plus counsellor union keeps registrar's full CRUD, not counsellor's view-only`() {
        val union = ModulePrivilege.union(
            listOf(RoleModuleConfig.privilegeFor(AppRole.REGISTRAR), RoleModuleConfig.privilegeFor(AppRole.COUNSELING))
        )
        assertThat(union.householdPermission).isEqualTo(Permission.FULL)
        assertThat(union.beneficiaryPermission).isEqualTo(Permission.FULL)
        assertThat(union.nonHouseholdPermission).isEqualTo(Permission.FULL)
        assertThat(union.anthropometryPermission).isEqualTo(Permission.FULL)
        assertThat(union.tbScreeningPermission).isEqualTo(Permission.FULL)
    }

    @Test
    fun `three-role union denominates against all 4 general forms, not counsellor's dynamic rule`() {
        val threeRoleUnion = ModulePrivilege.union(
            listOf(
                RoleModuleConfig.privilegeFor(AppRole.REGISTRAR),
                RoleModuleConfig.privilegeFor(AppRole.NURSE),
                RoleModuleConfig.privilegeFor(AppRole.COUNSELING)
            )
        )
        assertThat(threeRoleUnion.examineDenominatorRule).isEqualTo(ExamineDenominatorRule.GENERIC_FOUR)
        assertThat(threeRoleUnion.examineRowSet).isEqualTo(ExamineRowSet.ALL_FOUR)

        val nurseCounsellorUnion = ModulePrivilege.union(
            listOf(RoleModuleConfig.privilegeFor(AppRole.NURSE), RoleModuleConfig.privilegeFor(AppRole.COUNSELING))
        )
        assertThat(nurseCounsellorUnion.examineDenominatorRule).isEqualTo(ExamineDenominatorRule.GENERIC_FOUR)
    }

    @Test
    fun `volunteer preserves today's legacy fallback behavior, including no quick refresh`() {
        val p = RoleModuleConfig.privilegeFor(AppRole.VOLUNTEER)
        assertThat(p.homeModules).containsExactly(AppModule.HOUSEHOLD, AppModule.BENEFICIARIES)
        assertThat(p.allowQuickRefresh).isFalse()
        assertThat(p.showRegisterSpouseButtons).isTrue()
        assertThat(p.canActOnReferral).isFalse()
        assertThat(p.showAbhaButton).isTrue()
        assertThat(p.showCallButton).isTrue()
    }
}
