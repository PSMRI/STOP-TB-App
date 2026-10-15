package org.piramalswasthya.stoptb.helpers

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.piramalswasthya.stoptb.model.AppRole

class RoleUtilsTest {

    @Test
    fun `toLegacyAppRole maps registration officer variants`() {
        assertThat("Registration Officer".toLegacyAppRole()).isEqualTo(AppRole.REGISTRAR)
        assertThat("registrationofficer".toLegacyAppRole()).isEqualTo(AppRole.REGISTRAR)
        assertThat("Registrar".toLegacyAppRole()).isEqualTo(AppRole.REGISTRAR)
        assertThat(" REGISTRATION-OFFICER ".toLegacyAppRole()).isEqualTo(AppRole.REGISTRAR)
    }

    @Test
    fun `toLegacyAppRole maps nurse`() {
        assertThat("Nurse".toLegacyAppRole()).isEqualTo(AppRole.NURSE)
        assertThat(" nurse ".toLegacyAppRole()).isEqualTo(AppRole.NURSE)
    }

    @Test
    fun `toLegacyAppRole maps counselling officer variants`() {
        assertThat("Counselling Officer".toLegacyAppRole()).isEqualTo(AppRole.COUNSELING)
        assertThat("Counseling Officer".toLegacyAppRole()).isEqualTo(AppRole.COUNSELING)
        assertThat("Counsellor".toLegacyAppRole()).isEqualTo(AppRole.COUNSELING)
        assertThat("counselor".toLegacyAppRole()).isEqualTo(AppRole.COUNSELING)
    }

    @Test
    fun `toLegacyAppRole denies volunteer, asha and unknown roles`() {
        assertThat("Volunteer".toLegacyAppRole()).isNull()
        assertThat("Volenteer".toLegacyAppRole()).isNull()
        assertThat("Asha".toLegacyAppRole()).isNull()
        assertThat("ASHA Supervisor".toLegacyAppRole()).isNull()
        assertThat("ProviderAdmin".toLegacyAppRole()).isNull()
    }

    @Test
    fun `toLegacyAppRole denies null and blank`() {
        assertThat(null.toLegacyAppRole()).isNull()
        assertThat("".toLegacyAppRole()).isNull()
        assertThat("   ".toLegacyAppRole()).isNull()
    }

    @Test
    fun `counselling is matched before the greedy registration check`() {
        // isRegistrationOfficerRole ends with contains("registration"), so ordering in
        // toLegacyAppRole matters - an exact counselling match must not be swallowed by it.
        assertThat("Counselling Officer".toLegacyAppRole()).isEqualTo(AppRole.COUNSELING)
        assertThat("Nurse".toLegacyAppRole()).isEqualTo(AppRole.NURSE)
    }
}
