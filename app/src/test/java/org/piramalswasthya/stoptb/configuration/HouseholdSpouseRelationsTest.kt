package org.piramalswasthya.stoptb.configuration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HouseholdSpouseRelationsTest {

    private val relations = listOf("Mother", "Father", "Wife", "Husband", "Son")

    @Test
    fun wifeAndHusbandMatchStoredRelationPositions() {
        assertTrue(HouseholdSpouseRelations.isWife(5))
        assertFalse(HouseholdSpouseRelations.isWife(6))
        assertTrue(HouseholdSpouseRelations.isHusband(6))
        assertFalse(HouseholdSpouseRelations.isHusband(5))
    }

    @Test
    fun registeredWifeIsRemovedAndHusbandStays() {
        assertEquals(
            listOf("Mother", "Father", "Husband", "Son"),
            HouseholdSpouseRelations.withoutRegisteredSpouse(
                relations,
                wifeLabel = "Wife",
                husbandLabel = "Husband",
                wifeRegistered = true,
                husbandRegistered = false
            )
        )
    }

    @Test
    fun registeredHusbandIsRemovedAndWifeStays() {
        assertEquals(
            listOf("Mother", "Father", "Wife", "Son"),
            HouseholdSpouseRelations.withoutRegisteredSpouse(
                relations,
                wifeLabel = "Wife",
                husbandLabel = "Husband",
                wifeRegistered = false,
                husbandRegistered = true
            )
        )
    }

    @Test
    fun bothSpouseOptionsStayWhenNeitherIsRegistered() {
        assertEquals(
            relations,
            HouseholdSpouseRelations.withoutRegisteredSpouse(
                relations,
                wifeLabel = "Wife",
                husbandLabel = "Husband",
                wifeRegistered = false,
                husbandRegistered = false
            )
        )
    }
}
