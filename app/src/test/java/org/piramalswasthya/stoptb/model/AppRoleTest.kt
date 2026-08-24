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
    }

    @Test
    fun `fromScreenName returns null for unrecognized strings`() {
        assertThat(AppRole.fromScreenName("Counsellor")).isNull()
        assertThat(AppRole.fromScreenName("Asha")).isNull()
        assertThat(AppRole.fromScreenName("")).isNull()
    }

    @Test
    fun `isRecognizedLegacyRoleString matches the old RoleConstants allow-list`() {
        assertThat(AppRole.isRecognizedLegacyRoleString("Registration Officer")).isTrue()
        assertThat(AppRole.isRecognizedLegacyRoleString("Nurse")).isTrue()
        assertThat(AppRole.isRecognizedLegacyRoleString("Counselling Officer")).isTrue()
        assertThat(AppRole.isRecognizedLegacyRoleString("Counseling Officer")).isTrue()
        assertThat(AppRole.isRecognizedLegacyRoleString("Volunteer")).isTrue()
        assertThat(AppRole.isRecognizedLegacyRoleString("volenteer")).isTrue()
        assertThat(AppRole.isRecognizedLegacyRoleString("Registrar")).isTrue()
    }

    @Test
    fun `isRecognizedLegacyRoleString rejects disallowed or blank roles`() {
        assertThat(AppRole.isRecognizedLegacyRoleString("Asha")).isFalse()
        assertThat(AppRole.isRecognizedLegacyRoleString("ASHA Supervisor")).isFalse()
        assertThat(AppRole.isRecognizedLegacyRoleString("ProviderAdmin")).isFalse()
        assertThat(AppRole.isRecognizedLegacyRoleString(null)).isFalse()
        assertThat(AppRole.isRecognizedLegacyRoleString("")).isFalse()
        assertThat(AppRole.isRecognizedLegacyRoleString("   ")).isFalse()
    }

    @Test
    fun `resolveAssignedRoles prioritizes screenNames, dedupes, preserves order`() {
        val roles = AppRole.resolveAssignedRoles(
            screenNames = listOf("Nurse", "Registrar", "Nurse"),
            legacyRoleName = null
        )
        assertThat(roles).containsExactly(AppRole.NURSE, AppRole.REGISTRAR).inOrder()
    }

    @Test
    fun `resolveAssignedRoles ignores unrecognized screenNames`() {
        val roles = AppRole.resolveAssignedRoles(
            screenNames = listOf("SomeFutureRole", "Nurse"),
            legacyRoleName = null
        )
        assertThat(roles).containsExactly(AppRole.NURSE)
    }

    @Test
    fun `resolveAssignedRoles falls back to VOLUNTEER when legacy role was allowed but no screenName resolves`() {
        val roles = AppRole.resolveAssignedRoles(
            screenNames = emptyList(),
            legacyRoleName = "Volunteer"
        )
        assertThat(roles).containsExactly(AppRole.VOLUNTEER)
    }

    @Test
    fun `resolveAssignedRoles returns empty for a disallowed legacy role - preserves login denial`() {
        val roles = AppRole.resolveAssignedRoles(
            screenNames = emptyList(),
            legacyRoleName = "Asha"
        )
        assertThat(roles).isEmpty()
    }

    @Test
    fun `resolveAssignedRoles returns empty for null blank role data`() {
        val roles = AppRole.resolveAssignedRoles(
            screenNames = emptyList(),
            legacyRoleName = null
        )
        assertThat(roles).isEmpty()
    }
}
