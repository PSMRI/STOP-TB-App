package org.piramalswasthya.stoptb.configuration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.piramalswasthya.stoptb.configuration.SpouseLinkSelector.Member

class SpouseLinkSelectorTest {

    @Test
    fun linksHouseholdHeadWhenStoredSpouseNameDoesNotMatch() {
        val head = Member(
            beneficiaryId = 10,
            genderId = 1,
            isSpouseAdded = false,
            isHouseholdHead = true,
            spouseName = "SOME OTHER NAME"
        )

        assertEquals(
            10L,
            SpouseLinkSelector.selectPartnerId(
                newBeneficiaryId = 0L,
                registeringWife = true,
                newSpouseFirstName = "LAKSHMI",
                newSpouseFullName = "LAKSHMI RAO",
                members = listOf(head)
            )
        )
    }

    @Test
    fun nameMatchWinsOverHouseholdHead() {
        val head = member(id = 10, genderId = 1, head = true, spouseName = null)
        val named = member(id = 11, genderId = 1, head = false, spouseName = "LAKSHMI")

        assertEquals(
            11L,
            SpouseLinkSelector.selectPartnerId(
                newBeneficiaryId = 0L,
                registeringWife = true,
                newSpouseFirstName = "Lakshmi",
                newSpouseFullName = "Lakshmi Rao",
                members = listOf(head, named)
            )
        )
    }

    @Test
    fun linksTheOnlyOppositeGenderMemberWhenThereIsNoHead() {
        val husband = member(id = 7, genderId = 1, head = false, spouseName = null)

        assertEquals(
            7L,
            SpouseLinkSelector.selectPartnerId(
                newBeneficiaryId = 0L,
                registeringWife = true,
                newSpouseFirstName = "SITA",
                newSpouseFullName = "SITA",
                members = listOf(husband)
            )
        )
    }

    @Test
    fun doesNotLinkAMemberWhoAlreadyHasASpouse() {
        val head = member(id = 10, genderId = 1, head = true, spouseName = "SITA", spouseAdded = true)

        assertNull(
            SpouseLinkSelector.selectPartnerId(
                newBeneficiaryId = 0L,
                registeringWife = true,
                newSpouseFirstName = "SITA",
                newSpouseFullName = "SITA",
                members = listOf(head)
            )
        )
    }

    @Test
    fun registeringHusbandLinksTheFemaleHead() {
        val head = member(id = 4, genderId = 2, head = true, spouseName = null)
        val brother = member(id = 5, genderId = 1, head = false, spouseName = null)

        assertEquals(
            4L,
            SpouseLinkSelector.selectPartnerId(
                newBeneficiaryId = 0L,
                registeringWife = false,
                newSpouseFirstName = "RAM",
                newSpouseFullName = "RAM",
                members = listOf(head, brother)
            )
        )
    }

    private fun member(
        id: Long,
        genderId: Int,
        head: Boolean,
        spouseName: String?,
        spouseAdded: Boolean = false
    ) = Member(
        beneficiaryId = id,
        genderId = genderId,
        isSpouseAdded = spouseAdded,
        isHouseholdHead = head,
        spouseName = spouseName
    )
}
