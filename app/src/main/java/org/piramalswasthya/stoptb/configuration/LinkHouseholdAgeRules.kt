package org.piramalswasthya.stoptb.configuration

import org.piramalswasthya.stoptb.helpers.Konstants
import org.piramalswasthya.stoptb.model.BenRegCache
import java.util.Calendar

/**
 * Age rules for linking a Non-HH beneficiary to a household. Same rules as registering a new
 * household member (BenRegFormDataset.applyRelationDobRange). Relation ids are 0-indexed
 * positions in nbr_relationship_to_head_src; a stored familyHeadRelationPosition is id + 1.
 */
object LinkHouseholdAgeRules {

    /** [dob] is null when unknown; pre-1970 DOBs are negative and valid. */
    data class Member(val dob: Long?, val ageAtMarriage: Int? = null)

    data class LinkRelation(val label: String, val relationId: Int) {
        val relationPos: Int get() = relationId + 1
    }

    const val MOTHER = 0
    const val FATHER = 1
    const val WIFE = 4
    const val HUSBAND = 5
    const val SON = 8
    const val DAUGHTER = 9
    const val FATHER_IN_LAW = 12
    const val MOTHER_IN_LAW = 13
    const val SON_IN_LAW = 16
    const val DAUGHTER_IN_LAW = 17

    private val marriedRelations = setOf(
        WIFE, HUSBAND, FATHER_IN_LAW, MOTHER_IN_LAW, SON_IN_LAW, DAUGHTER_IN_LAW
    )

    /** Display order of the Link Household relationship dialog. "Other" is saved as Guest (20). */
    val femaleRelations = listOf(
        LinkRelation("Mother", MOTHER),
        LinkRelation("Sister", 3),
        LinkRelation("Wife", WIFE),
        LinkRelation("Niece", 7),
        LinkRelation("Daughter", DAUGHTER),
        LinkRelation("Grand Mother", 11),
        LinkRelation("Mother in Law", MOTHER_IN_LAW),
        LinkRelation("Grand Daughter", 15),
        LinkRelation("Daughter in Law", DAUGHTER_IN_LAW),
        LinkRelation("Sister in Law", 19),
        LinkRelation("Other", 20),
    )

    val maleRelations = listOf(
        LinkRelation("Father", FATHER),
        LinkRelation("Brother", 2),
        LinkRelation("Husband", HUSBAND),
        LinkRelation("Nephew", 6),
        LinkRelation("Son", SON),
        LinkRelation("Grand Father", 10),
        LinkRelation("Father in Law", FATHER_IN_LAW),
        LinkRelation("Grand Son", 14),
        LinkRelation("Son in Law", SON_IN_LAW),
        LinkRelation("Other", 20),
    )

    /** BenRegCache.dob defaults to 0 when not set; every other value, negative included, is a real DOB. */
    fun knownDob(storedDob: Long): Long? = storedDob.takeIf { it != 0L }

    fun canBeHeadOfFamily(benDob: Long?, now: Long = System.currentTimeMillis()): Boolean =
        benDob == null || benDob <= yearsBefore(now, Konstants.minAgeForHoF)

    fun isRelationAllowed(
        relationId: Int,
        benDob: Long?,
        hof: Member?,
        hofSpouse: Member?,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        // No DOB to check against: keep the old behaviour.
        if (benDob == null) return true
        val minAdultDob = yearsBefore(now, Konstants.minAgeForGenBen)
        val hofDob = hof?.dob
        return when (relationId) {
            MOTHER, FATHER ->
                if (hofDob != null) benDob <= yearsBefore(hofDob, Konstants.minAgeForGenBen)
                else benDob <= minAdultDob
            SON, DAUGHTER -> {
                if (hofDob == null) return true
                val spouseDob = hofSpouse?.dob
                val youngerParentDob = spouseDob?.let { maxOf(hofDob, it) } ?: hofDob
                val hofAge = hof.ageAtMarriage?.takeIf { it > 0 }
                val spouseAge = hofSpouse?.ageAtMarriage?.takeIf { it > 0 }
                val marriageAge = when {
                    hofAge != null && spouseAge != null -> minOf(hofAge, spouseAge)
                    else -> hofAge ?: spouseAge ?: Konstants.minAgeForGenBen
                }
                val earliestChildDob = Calendar.getInstance().apply {
                    timeInMillis = youngerParentDob
                    add(Calendar.YEAR, marriageAge)
                    add(Calendar.MONTH, 7)
                }.timeInMillis
                benDob >= earliestChildDob
            }
            in marriedRelations -> benDob <= minAdultDob
            else -> true
        }
    }

    fun allowedRelations(
        isFemale: Boolean,
        benDob: Long?,
        hof: Member?,
        hofSpouse: Member?,
        now: Long = System.currentTimeMillis()
    ): List<LinkRelation> =
        (if (isFemale) femaleRelations else maleRelations)
            .filter { isRelationAllowed(it.relationId, benDob, hof, hofSpouse, now) }

    /** HoF (stored position 19) and HoF's spouse (Wife 5 / Husband 6) of a household's members. */
    fun headAndSpouse(members: List<BenRegCache>): Pair<Member?, Member?> {
        val hof = members.firstOrNull { it.familyHeadRelationPosition == 19 }
        val spouse = members.firstOrNull {
            (it.familyHeadRelationPosition == 5 || it.familyHeadRelationPosition == 6) &&
                it.beneficiaryId != hof?.beneficiaryId
        }
        return hof?.toMember() to spouse?.toMember()
    }

    private fun BenRegCache.toMember() = Member(knownDob(dob), genDetails?.ageAtMarriage)

    private fun yearsBefore(millis: Long, years: Int): Long =
        Calendar.getInstance().apply {
            timeInMillis = millis
            add(Calendar.YEAR, -years)
        }.timeInMillis
}
