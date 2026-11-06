package org.piramalswasthya.stoptb.configuration

/**
 * A household can have one Wife and one Husband.
 * Positions match the 1-indexed values stored in familyHeadRelationPosition
 * (index in nbr_relationship_to_head_src, plus one).
 */
object HouseholdSpouseRelations {
    const val WIFE_POSITION = 5
    const val HUSBAND_POSITION = 6

    fun isWife(relationPosition: Int): Boolean = relationPosition == WIFE_POSITION

    fun isHusband(relationPosition: Int): Boolean = relationPosition == HUSBAND_POSITION

    fun withoutRegisteredSpouse(
        relations: List<String>,
        wifeLabel: String?,
        husbandLabel: String?,
        wifeRegistered: Boolean,
        husbandRegistered: Boolean
    ): List<String> = relations.filter { relation ->
        val wifeBlocked = wifeRegistered && !wifeLabel.isNullOrBlank() && relation == wifeLabel
        val husbandBlocked = husbandRegistered && !husbandLabel.isNullOrBlank() && relation == husbandLabel
        !wifeBlocked && !husbandBlocked
    }
}
