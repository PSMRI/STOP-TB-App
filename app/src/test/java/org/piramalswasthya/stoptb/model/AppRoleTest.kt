package org.piramalswasthya.stoptb.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppRoleTest {

    @Test
    fun `fromScreenName maps known backend strings, case-insensitively`() {
        assertThat(AppRole.fromScreenName("Registrar")).isEqualTo(AppRole.REGISTRAR)
        assertThat(AppRole.fromScreenName("nurse")).isEqualTo(AppRole.NURSE)
        assertThat(AppRole.fromScreenName("COUNSELING")).isEqualTo(AppRole.COUNSELING)
        assertThat(AppRole.fromScreenName(" Registrar ")).isEqualTo(AppRole.REGISTRAR)
        assertThat(AppRole.fromScreenName("LabTechnician")).isEqualTo(AppRole.LAB_TECHNICIAN)
        assertThat(AppRole.fromScreenName("labtechnician")).isEqualTo(AppRole.LAB_TECHNICIAN)
        // Confirmed via a live login failure that the backend actually sends "Lab Technician"
        // with a space — must resolve too.
        assertThat(AppRole.fromScreenName("Lab Technician")).isEqualTo(AppRole.LAB_TECHNICIAN)
    }

    @Test
    fun `fromScreenName returns null for unrecognized strings`() {
        assertThat(AppRole.fromScreenName("Counsellor")).isNull()
        assertThat(AppRole.fromScreenName("Asha")).isNull()
        assertThat(AppRole.fromScreenName("")).isNull()
    }

    @Test
    fun `resolveAssignedRoles prioritizes screenNames, dedupes, sorts in canonical order`() {
        val roles = AppRole.resolveAssignedRoles(
            screenNames = listOf("Nurse", "Registrar", "Nurse")
        )
        assertThat(roles).containsExactly(AppRole.REGISTRAR, AppRole.NURSE).inOrder()

        val allRoles = AppRole.resolveAssignedRoles(
            screenNames = listOf("Counseling", "Nurse", "Registrar")
        )
        assertThat(allRoles).containsExactly(AppRole.REGISTRAR, AppRole.NURSE, AppRole.COUNSELING).inOrder()

        val nurseCounseling = AppRole.resolveAssignedRoles(
            screenNames = listOf("Counseling", "Nurse")
        )
        assertThat(nurseCounseling).containsExactly(AppRole.NURSE, AppRole.COUNSELING).inOrder()

        // Lab Technician sorts last regardless of backend order.
        val withLabTechnician = AppRole.resolveAssignedRoles(
            screenNames = listOf("LabTechnician", "Registrar")
        )
        assertThat(withLabTechnician).containsExactly(AppRole.REGISTRAR, AppRole.LAB_TECHNICIAN).inOrder()
    }

    @Test
    fun `resolveAssignedRoles ignores unrecognized screenNames`() {
        val roles = AppRole.resolveAssignedRoles(
            screenNames = listOf("SomeFutureRole", "Nurse")
        )
        assertThat(roles).containsExactly(AppRole.NURSE)
    }

    @Test
    fun `resolveAssignedRoles returns empty when no screenNames resolve - no legacy fallback anymore`() {
        assertThat(AppRole.resolveAssignedRoles(screenNames = emptyList())).isEmpty()
        assertThat(AppRole.resolveAssignedRoles(screenNames = listOf("Volunteer"))).isEmpty()
        assertThat(AppRole.resolveAssignedRoles(screenNames = listOf("Asha"))).isEmpty()
    }
}
