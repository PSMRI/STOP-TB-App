package org.piramalswasthya.stoptb.helpers

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.model.AppRole
import org.piramalswasthya.stoptb.model.ExamineDenominatorRule
import org.piramalswasthya.stoptb.model.ExamineRowSet
import org.piramalswasthya.stoptb.model.User

class RoleManagerTest {

    private lateinit var preferenceDao: PreferenceDao
    private lateinit var roleManager: RoleManager

    @Before
    fun setUp() {
        preferenceDao = mock(PreferenceDao::class.java)
        roleManager = RoleManager(preferenceDao)
    }

    private fun mockUserWithScreenNames(screenNames: List<String>) {
        val user = mock(User::class.java)
        `when`(user.assignedRoleScreenNames).thenReturn(screenNames)
        `when`(preferenceDao.getLoggedInUser()).thenReturn(user)
    }

    @Test
    fun `multi-role account defaults activeRole to 0th index tab on launch`() {
        // Registrar + Nurse + Counselling in any backend payload order
        mockUserWithScreenNames(listOf("Counseling", "Nurse", "Registrar"))
        roleManager.initializeFromLoggedInUser()
        assertThat(roleManager.assignedRoles).containsExactly(
            AppRole.REGISTRAR, AppRole.NURSE, AppRole.COUNSELING
        ).inOrder()
        assertThat(roleManager.activeRole.value).isEqualTo(AppRole.REGISTRAR)

        // Registrar + Nurse
        mockUserWithScreenNames(listOf("Nurse", "Registrar"))
        roleManager.initializeFromLoggedInUser()
        assertThat(roleManager.assignedRoles).containsExactly(
            AppRole.REGISTRAR, AppRole.NURSE
        ).inOrder()
        assertThat(roleManager.activeRole.value).isEqualTo(AppRole.REGISTRAR)

        // Registrar + Counselling
        mockUserWithScreenNames(listOf("Counseling", "Registrar"))
        roleManager.initializeFromLoggedInUser()
        assertThat(roleManager.assignedRoles).containsExactly(
            AppRole.REGISTRAR, AppRole.COUNSELING
        ).inOrder()
        assertThat(roleManager.activeRole.value).isEqualTo(AppRole.REGISTRAR)

        // Nurse + Counselling
        mockUserWithScreenNames(listOf("Counseling", "Nurse"))
        roleManager.initializeFromLoggedInUser()
        assertThat(roleManager.assignedRoles).containsExactly(
            AppRole.NURSE, AppRole.COUNSELING
        ).inOrder()
        assertThat(roleManager.activeRole.value).isEqualTo(AppRole.NURSE)
    }

    @Test
    fun `nurse role gets all four forms and generic four denominator even if showContactTracingForms is passed`() {
        mockUserWithScreenNames(listOf("Nurse"))
        roleManager.initializeFromLoggedInUser()

        val privWithoutContact = roleManager.examinePrivilegesFor(showContactTracingForms = false)
        assertThat(privWithoutContact.examineRowSet).isEqualTo(ExamineRowSet.ALL_FOUR)
        assertThat(privWithoutContact.examineDenominatorRule).isEqualTo(ExamineDenominatorRule.GENERIC_FOUR)

        val privWithContact = roleManager.examinePrivilegesFor(showContactTracingForms = true)
        assertThat(privWithContact.examineRowSet).isEqualTo(ExamineRowSet.ALL_FOUR)
        assertThat(privWithContact.examineDenominatorRule).isEqualTo(ExamineDenominatorRule.GENERIC_FOUR)
    }

    @Test
    fun `counsellor role gets counselling examine rules when showContactTracingForms is true`() {
        mockUserWithScreenNames(listOf("Counseling", "Nurse", "Registrar"))
        roleManager.initializeFromLoggedInUser()

        // General list -> all 4 forms
        val privGeneral = roleManager.examinePrivilegesFor(showContactTracingForms = false)
        assertThat(privGeneral.examineRowSet).isEqualTo(ExamineRowSet.ALL_FOUR)
        assertThat(privGeneral.examineDenominatorRule).isEqualTo(ExamineDenominatorRule.GENERIC_FOUR)

        // TPT / Counselling workflow -> 2 forms and dynamic denominator
        val privCounselling = roleManager.examinePrivilegesFor(showContactTracingForms = true)
        assertThat(privCounselling.examineRowSet).isEqualTo(ExamineRowSet.ANTHROPOMETRY_AND_TB_SCREENING_ONLY)
        assertThat(privCounselling.examineDenominatorRule).isEqualTo(ExamineDenominatorRule.COUNSELLING_DYNAMIC)
    }

    @Test
    fun `showRegisterSpouseButtons is only true for activeRole REGISTRAR`() {
        mockUserWithScreenNames(listOf("Registrar", "Nurse", "Counseling"))
        roleManager.initializeFromLoggedInUser()

        // Default tab is REGISTRAR
        assertThat(roleManager.privilegesForActiveRole().showRegisterSpouseButtons).isTrue()

        // Switch to NURSE tab
        roleManager.setActiveRole(AppRole.NURSE)
        assertThat(roleManager.privilegesForActiveRole().showRegisterSpouseButtons).isFalse()

        // Switch to COUNSELING tab
        roleManager.setActiveRole(AppRole.COUNSELING)
        assertThat(roleManager.privilegesForActiveRole().showRegisterSpouseButtons).isFalse()
    }
}
