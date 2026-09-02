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

    // isRecognizedLegacyRoleString tests removed: the function itself is commented out in
    // AppRole.kt (product decision — no more legacy-role-string fallback), so it's no longer
    // callable. Left out rather than commented, since a commented-out test asserting on
    // commented-out production code would just be dead weight either way.

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
        // Previously this would have fallen back to VOLUNTEER for an allowed legacy role
        // string like "Volunteer" or "Registrar" — that fallback was removed by product
        // decision. Now ANY account with no mapped screenName is denied, full stop.
        assertThat(AppRole.resolveAssignedRoles(screenNames = emptyList())).isEmpty()
        assertThat(AppRole.resolveAssignedRoles(screenNames = listOf("Volunteer"))).isEmpty()
        assertThat(AppRole.resolveAssignedRoles(screenNames = listOf("Asha"))).isEmpty()
    }
}
