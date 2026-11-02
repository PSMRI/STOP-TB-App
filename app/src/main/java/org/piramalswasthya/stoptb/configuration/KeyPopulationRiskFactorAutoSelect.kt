package org.piramalswasthya.stoptb.configuration

import org.piramalswasthya.stoptb.model.AgeUnit
import java.util.Calendar

/**
 * Pre-selects Key Population / Risk Factors from data already captured on the beneficiary.
 *
 * "Elderly" follows age >= 60. "Urban Slum" follows Type of Residential Area.
 * A user can clear either value. The choice stays cleared until that source
 * (age band, or whether the area is Urban Slum) actually changes.
 */
object KeyPopulationRiskFactorAutoSelect {

    const val ELDERLY_CODE = "ELDERLY"
    const val URBAN_SLUM_CODE = "URBAN_SLUM"
    const val NOT_APPLICABLE_CODE = "NOT_APPLICABLE"
    const val ELDERLY_MIN_AGE_YEARS = 60
    const val URBAN_SLUM_AREA_LABEL = "Urban Slum"

    class Session {
        var elderlySuppressedByUser: Boolean = false
        var urbanSlumSuppressedByUser: Boolean = false
        var elderlyAppliedByAuto: Boolean = false
        var urbanSlumAppliedByAuto: Boolean = false
        var lastElderly: Boolean? = null
        var lastUrbanSlum: Boolean? = null
    }

    data class ResidentialArea(val label: String?, val id: Int)

    fun ageInYears(dobMillis: Long, recordedAge: Int, ageUnit: AgeUnit?, ageUnitId: Int): Int {
        // Dates of birth before 1970 are negative epoch millis. 0 means "not captured".
        if (dobMillis != 0L) {
            val dob = Calendar.getInstance().apply { timeInMillis = dobMillis }
            val today = Calendar.getInstance()
            var years = today.get(Calendar.YEAR) - dob.get(Calendar.YEAR)
            if (today.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)) years--
            return years.coerceAtLeast(0)
        }
        return when {
            ageUnit == AgeUnit.MONTHS || ageUnit == AgeUnit.DAYS -> 0
            ageUnitId == 1 || ageUnitId == 2 -> 0
            else -> recordedAge.coerceAtLeast(0)
        }
    }

    fun isElderly(ageYears: Int): Boolean = ageYears >= ELDERLY_MIN_AGE_YEARS

    /**
     * Beneficiary value wins when it is present. Household value is the fallback
     * so members registered under a household still pick up the area captured there.
     */
    fun resolveResidentialArea(
        benLabel: String?,
        benId: Int?,
        householdLabel: String?,
        householdId: Int?
    ): ResidentialArea {
        fun usable(label: String?, id: Int?): ResidentialArea? {
            val clean = label?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
            val positiveId = id?.takeIf { it > 0 }
            if (clean == null && positiveId == null) return null
            return ResidentialArea(clean, positiveId ?: 0)
        }
        return usable(benLabel, benId)
            ?: usable(householdLabel, householdId)
            ?: ResidentialArea(null, 0)
    }

    fun isUrbanSlumResidentialArea(
        label: String?,
        areaId: Int?,
        urbanSlumAreaId: Int,
        acceptedLabels: Set<String>
    ): Boolean {
        if (urbanSlumAreaId > 0 && areaId == urbanSlumAreaId) return true
        val clean = label?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
            ?: return false
        return acceptedLabels.any { it.equals(clean, ignoreCase = true) }
    }

    /**
     * Adds or removes the auto-selected indexes inside [selected].
     * Returns true when the set changes.
     */
    fun apply(
        selected: MutableSet<Int>,
        elderlyIndex: Int,
        urbanSlumIndex: Int,
        notApplicableIndex: Int,
        isElderly: Boolean,
        isUrbanSlum: Boolean,
        session: Session
    ): Boolean {
        val before = selected.toSet()
        if (session.lastElderly != null && session.lastElderly != isElderly) {
            session.elderlySuppressedByUser = false
        }
        if (session.lastUrbanSlum != null && session.lastUrbanSlum != isUrbanSlum) {
            session.urbanSlumSuppressedByUser = false
        }

        applyOption(
            selected = selected,
            index = elderlyIndex,
            condition = isElderly,
            suppressed = session.elderlySuppressedByUser,
            appliedByAuto = session.elderlyAppliedByAuto,
            markApplied = { session.elderlyAppliedByAuto = it }
        )
        applyOption(
            selected = selected,
            index = urbanSlumIndex,
            condition = isUrbanSlum,
            suppressed = session.urbanSlumSuppressedByUser,
            appliedByAuto = session.urbanSlumAppliedByAuto,
            markApplied = { session.urbanSlumAppliedByAuto = it }
        )

        if (notApplicableIndex >= 0 && selected.size > 1 && selected.contains(notApplicableIndex)) {
            selected.remove(notApplicableIndex)
        }

        session.lastElderly = isElderly
        session.lastUrbanSlum = isUrbanSlum
        return selected != before
    }

    /**
     * Call after the user confirms the multi-select, with [previous] being the
     * selection before that edit and [current] the selection after it.
     */
    fun recordUserEdit(
        previous: Set<Int>,
        current: Set<Int>,
        elderlyIndex: Int,
        urbanSlumIndex: Int,
        isElderly: Boolean,
        isUrbanSlum: Boolean,
        session: Session
    ) {
        recordOptionEdit(
            wasSelected = elderlyIndex >= 0 && previous.contains(elderlyIndex),
            isSelected = elderlyIndex >= 0 && current.contains(elderlyIndex),
            condition = isElderly,
            suppress = { session.elderlySuppressedByUser = it },
            markApplied = { session.elderlyAppliedByAuto = it }
        )
        recordOptionEdit(
            wasSelected = urbanSlumIndex >= 0 && previous.contains(urbanSlumIndex),
            isSelected = urbanSlumIndex >= 0 && current.contains(urbanSlumIndex),
            condition = isUrbanSlum,
            suppress = { session.urbanSlumSuppressedByUser = it },
            markApplied = { session.urbanSlumAppliedByAuto = it }
        )
    }

    private fun applyOption(
        selected: MutableSet<Int>,
        index: Int,
        condition: Boolean,
        suppressed: Boolean,
        appliedByAuto: Boolean,
        markApplied: (Boolean) -> Unit
    ) {
        if (index < 0) return
        if (condition) {
            if (!suppressed && selected.add(index)) {
                markApplied(true)
            }
        } else if (appliedByAuto) {
            selected.remove(index)
            markApplied(false)
        }
    }

    private fun recordOptionEdit(
        wasSelected: Boolean,
        isSelected: Boolean,
        condition: Boolean,
        suppress: (Boolean) -> Unit,
        markApplied: (Boolean) -> Unit
    ) {
        if (wasSelected && !isSelected && condition) {
            suppress(true)
            markApplied(false)
        } else if (!wasSelected && isSelected) {
            suppress(false)
            markApplied(false)
        }
    }
}
