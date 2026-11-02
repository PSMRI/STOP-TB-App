package org.piramalswasthya.stoptb.configuration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.piramalswasthya.stoptb.model.AgeUnit
import java.util.Calendar

class KeyPopulationRiskFactorAutoSelectTest {

    private val elderlyIndex = 2
    private val urbanSlumIndex = 5
    private val notApplicableIndex = 9

    @Test
    fun age60OrAboveIsElderly() {
        val turning60Today = Calendar.getInstance().apply { add(Calendar.YEAR, -60) }
        assertTrue(
            KeyPopulationRiskFactorAutoSelect.isElderly(
                KeyPopulationRiskFactorAutoSelect.ageInYears(turning60Today.timeInMillis, 0, null, 0)
            )
        )

        val still59 = Calendar.getInstance().apply {
            add(Calendar.YEAR, -60)
            add(Calendar.DAY_OF_YEAR, 1)
        }
        assertFalse(
            KeyPopulationRiskFactorAutoSelect.isElderly(
                KeyPopulationRiskFactorAutoSelect.ageInYears(still59.timeInMillis, 0, null, 0)
            )
        )
    }

    @Test
    fun recordedAgeUsesYearsOnly() {
        assertEquals(70, KeyPopulationRiskFactorAutoSelect.ageInYears(0L, 70, AgeUnit.YEARS, 3))
        assertEquals(0, KeyPopulationRiskFactorAutoSelect.ageInYears(0L, 70, AgeUnit.MONTHS, 2))
        assertEquals(0, KeyPopulationRiskFactorAutoSelect.ageInYears(0L, 70, AgeUnit.DAYS, 1))
        assertEquals(65, KeyPopulationRiskFactorAutoSelect.ageInYears(0L, 65, null, 0))
    }

    @Test
    fun residentialAreaFallsBackToHousehold() {
        val fromBen = KeyPopulationRiskFactorAutoSelect.resolveResidentialArea(
            benLabel = "Rural",
            benId = 3,
            householdLabel = "Urban Slum",
            householdId = 5
        )
        assertEquals("Rural", fromBen.label)
        assertEquals(3, fromBen.id)

        val fromHousehold = KeyPopulationRiskFactorAutoSelect.resolveResidentialArea(
            benLabel = "null",
            benId = 0,
            householdLabel = "Urban Slum",
            householdId = 5
        )
        assertEquals("Urban Slum", fromHousehold.label)
        assertEquals(5, fromHousehold.id)
    }

    @Test
    fun urbanSlumMatchesIdOrLabel() {
        val labels = setOf("Urban Slum", "शहरी स्लम")
        assertTrue(
            KeyPopulationRiskFactorAutoSelect.isUrbanSlumResidentialArea("Rural", 5, 5, labels)
        )
        assertTrue(
            KeyPopulationRiskFactorAutoSelect.isUrbanSlumResidentialArea("शहरी स्लम", 0, 5, labels)
        )
        assertFalse(
            KeyPopulationRiskFactorAutoSelect.isUrbanSlumResidentialArea("Urban", 4, 5, labels)
        )
        assertFalse(
            KeyPopulationRiskFactorAutoSelect.isUrbanSlumResidentialArea("null", 0, 5, labels)
        )
    }

    @Test
    fun preselectsElderlyAndUrbanSlumAndDropsNotApplicable() {
        val selected = mutableSetOf(notApplicableIndex)
        val session = KeyPopulationRiskFactorAutoSelect.Session()

        val changed = KeyPopulationRiskFactorAutoSelect.apply(
            selected = selected,
            elderlyIndex = elderlyIndex,
            urbanSlumIndex = urbanSlumIndex,
            notApplicableIndex = notApplicableIndex,
            isElderly = true,
            isUrbanSlum = true,
            session = session
        )

        assertTrue(changed)
        assertEquals(setOf(elderlyIndex, urbanSlumIndex), selected)
    }

    @Test
    fun userCanClearAutoSelectionUntilSourceChanges() {
        val selected = mutableSetOf<Int>()
        val session = KeyPopulationRiskFactorAutoSelect.Session()
        KeyPopulationRiskFactorAutoSelect.apply(
            selected, elderlyIndex, urbanSlumIndex, notApplicableIndex,
            isElderly = true, isUrbanSlum = true, session = session
        )

        val beforeEdit = selected.toSet()
        selected.remove(elderlyIndex)
        KeyPopulationRiskFactorAutoSelect.recordUserEdit(
            previous = beforeEdit,
            current = selected,
            elderlyIndex = elderlyIndex,
            urbanSlumIndex = urbanSlumIndex,
            isElderly = true,
            isUrbanSlum = true,
            session = session
        )

        val changed = KeyPopulationRiskFactorAutoSelect.apply(
            selected, elderlyIndex, urbanSlumIndex, notApplicableIndex,
            isElderly = true, isUrbanSlum = true, session = session
        )

        assertFalse(changed)
        assertFalse(selected.contains(elderlyIndex))
        assertTrue(selected.contains(urbanSlumIndex))
    }

    @Test
    fun editingAgeOrResidentialAreaReappliesSelection() {
        val selected = mutableSetOf<Int>()
        val session = KeyPopulationRiskFactorAutoSelect.Session()
        KeyPopulationRiskFactorAutoSelect.apply(
            selected, elderlyIndex, urbanSlumIndex, notApplicableIndex,
            isElderly = true, isUrbanSlum = false, session = session
        )
        val beforeEdit = selected.toSet()
        selected.remove(elderlyIndex)
        KeyPopulationRiskFactorAutoSelect.recordUserEdit(
            beforeEdit, selected, elderlyIndex, urbanSlumIndex,
            isElderly = true, isUrbanSlum = false, session = session
        )

        KeyPopulationRiskFactorAutoSelect.apply(
            selected, elderlyIndex, urbanSlumIndex, notApplicableIndex,
            isElderly = false, isUrbanSlum = true, session = session
        )
        assertEquals(setOf(urbanSlumIndex), selected)

        KeyPopulationRiskFactorAutoSelect.apply(
            selected, elderlyIndex, urbanSlumIndex, notApplicableIndex,
            isElderly = true, isUrbanSlum = true, session = session
        )
        assertEquals(setOf(elderlyIndex, urbanSlumIndex), selected)
    }

    @Test
    fun manualElderlySelectionIsKeptWhenAgeIsUnder60() {
        val selected = mutableSetOf(elderlyIndex)
        val session = KeyPopulationRiskFactorAutoSelect.Session()

        KeyPopulationRiskFactorAutoSelect.apply(
            selected, elderlyIndex, urbanSlumIndex, notApplicableIndex,
            isElderly = false, isUrbanSlum = false, session = session
        )

        assertTrue(selected.contains(elderlyIndex))
    }
}
