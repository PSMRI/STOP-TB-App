package org.piramalswasthya.stoptb.configuration

/**
 * Chooses the existing household member who should be linked when Add Member
 * registers a Wife or Husband. The removed Register Wife / Register Husband
 * buttons passed that member explicitly. Add Member has to find the same person.
 */
object SpouseLinkSelector {

    data class Member(
        val beneficiaryId: Long,
        val genderId: Int,
        val isSpouseAdded: Boolean,
        val isHouseholdHead: Boolean,
        val spouseName: String?
    )

    fun selectPartnerId(
        newBeneficiaryId: Long,
        registeringWife: Boolean,
        newSpouseFirstName: String?,
        newSpouseFullName: String?,
        members: List<Member>
    ): Long? {
        val expectedGenderId = if (registeringWife) 1 else 2
        val candidates = members.filter { member ->
            member.beneficiaryId != newBeneficiaryId &&
                member.genderId == expectedGenderId &&
                !member.isSpouseAdded
        }
        if (candidates.isEmpty()) return null

        val newFirstName = newSpouseFirstName.normalizeName()
        val newFullName = newSpouseFullName.normalizeName()
        if (newFirstName.isNotBlank() || newFullName.isNotBlank()) {
            val nameMatches = candidates.filter { member ->
                val spouseName = member.spouseName.normalizeName()
                spouseName.isNotBlank() && (spouseName == newFirstName || spouseName == newFullName)
            }
            nameMatches.singleOrNull()?.let { return it.beneficiaryId }
        }

        candidates.singleOrNull { it.isHouseholdHead }?.let { return it.beneficiaryId }
        return candidates.singleOrNull()?.beneficiaryId
    }

    private fun String?.normalizeName(): String =
        this?.trim()
            ?.replace(Regex("\\s+"), " ")
            ?.uppercase()
            .orEmpty()
}
