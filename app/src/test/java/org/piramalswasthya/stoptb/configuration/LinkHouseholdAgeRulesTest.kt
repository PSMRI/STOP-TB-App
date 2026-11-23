package org.piramalswasthya.stoptb.configuration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.piramalswasthya.stoptb.configuration.LinkHouseholdAgeRules.Member
import java.util.Calendar

class LinkHouseholdAgeRulesTest {

    private val now: Long = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 23, 12, 0, 0) }.timeInMillis

    private fun dobYearsAgo(years: Int, extraDays: Int = 0): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.YEAR, -years)
            add(Calendar.DAY_OF_YEAR, -extraDays)
        }.timeInMillis

    private fun labels(isFemale: Boolean, dob: Long, hof: Member?, spouse: Member? = null) =
        LinkHouseholdAgeRules.allowedRelations(isFemale, dob, hof, spouse, now).map { it.label }

    @Test
    fun `under 15 cannot become head of family`() {
        assertFalse(LinkHouseholdAgeRules.canBeHeadOfFamily(dobYearsAgo(9), now))
        assertFalse(LinkHouseholdAgeRules.canBeHeadOfFamily(dobYearsAgo(14, 360), now))
    }

    @Test
    fun `15 and over can become head of family`() {
        assertTrue(LinkHouseholdAgeRules.canBeHeadOfFamily(dobYearsAgo(15), now))
        assertTrue(LinkHouseholdAgeRules.canBeHeadOfFamily(dobYearsAgo(40), now))
    }

    @Test
    fun `child cannot be linked as wife mother or in-law`() {
        val hof = Member(dobYearsAgo(35), ageAtMarriage = 22)
        val spouse = Member(dobYearsAgo(30), ageAtMarriage = 20)
        val result = labels(isFemale = true, dob = dobYearsAgo(9), hof = hof, spouse = spouse)
        assertFalse("Wife" in result)
        assertFalse("Mother" in result)
        assertFalse("Mother in Law" in result)
        assertFalse("Daughter in Law" in result)
        assertTrue("Daughter" in result)
        assertTrue("Sister" in result)
        assertTrue("Other" in result)
    }

    @Test
    fun `parent must be at least 15 years older than head of family`() {
        val hof = Member(dobYearsAgo(30))
        assertTrue("Father" in labels(false, dobYearsAgo(46), hof))
        assertFalse("Father" in labels(false, dobYearsAgo(40), hof))
    }

    @Test
    fun `son must be born after parents marriage age plus 7 months`() {
        // Younger parent is 30 and married at 20: earliest child DOB is ~9 years 5 months ago.
        val hof = Member(dobYearsAgo(35), ageAtMarriage = 22)
        val spouse = Member(dobYearsAgo(30), ageAtMarriage = 20)
        assertTrue("Son" in labels(false, dobYearsAgo(5), hof, spouse))
        assertFalse("Son" in labels(false, dobYearsAgo(12), hof, spouse))
    }

    @Test
    fun `adult keeps every relation`() {
        val result = labels(isFemale = false, dob = dobYearsAgo(25), hof = null)
        assertEquals(LinkHouseholdAgeRules.maleRelations.map { it.label }, result)
    }

    @Test
    fun `other is always offered and saved as guest position`() {
        val other = LinkHouseholdAgeRules.allowedRelations(true, dobYearsAgo(1), Member(dobYearsAgo(20)), null, now)
            .single { it.label == "Other" }
        assertEquals(21, other.relationPos)
    }

    @Test
    fun `relation positions match the old hard-coded mapping`() {
        val expected = mapOf(
            "Mother" to 1, "Father" to 2, "Brother" to 3, "Sister" to 4, "Wife" to 5, "Husband" to 6,
            "Nephew" to 7, "Niece" to 8, "Son" to 9, "Daughter" to 10, "Grand Father" to 11,
            "Grand Mother" to 12, "Father in Law" to 13, "Mother in Law" to 14, "Grand Son" to 15,
            "Grand Daughter" to 16, "Son in Law" to 17, "Daughter in Law" to 18, "Sister in Law" to 20,
            "Other" to 21
        )
        (LinkHouseholdAgeRules.femaleRelations + LinkHouseholdAgeRules.maleRelations).forEach {
            assertEquals(it.label, expected[it.label], it.relationPos)
        }
    }
}
