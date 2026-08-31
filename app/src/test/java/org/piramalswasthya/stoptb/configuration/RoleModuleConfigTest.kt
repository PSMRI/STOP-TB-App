package org.piramalswasthya.stoptb.configuration

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.piramalswasthya.stoptb.model.AppModule
import org.piramalswasthya.stoptb.model.AppRole
import org.piramalswasthya.stoptb.model.ExamineDenominatorRule
import org.piramalswasthya.stoptb.model.ExamineRowSet
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
    }

    @Test
    fun `counseling sees the counselling-specific UI and rows`() {
        val p = RoleModuleConfig.privilegeFor(AppRole.COUNSELING)
        assertThat(p.syncShowCounsellingStatusRow).isTrue()
        assertThat(p.showTbConfirmedCounsellingUi).isTrue()
        assertThat(p.syncBottomSheetRowFilter).isEqualTo(SyncRowFilter.COUNSELLING_ROWS_ONLY)
        assertThat(p.examineShowContactTracingRows).isTrue()
        assertThat(p.showCallButton).isFalse()
        // Multi-role "Counselling" tab is its own distinct set — none of the single-role
        // homeModules carry over.
        assertThat(p.multiRoleHomeModules).containsExactly(
            AppModule.COUNSELLING, AppModule.CONTACT_TRACING,
            AppModule.TB_TREATMENT_FOLLOWUP, AppModule.TPT
        )
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
