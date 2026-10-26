package org.piramalswasthya.stoptb.ui.home_activity.new_household_registration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoorLockSubmitTest {

    @Test
    fun doorLockCheckedAndMandatoryEmptySavesTheHousehold() {
        val result = DoorLockSubmit.resolve(doorLockChecked = true, mandatoryComplete = false)
        assertEquals(DoorLockSubmit.Result.DOOR_LOCK, result)
        assertTrue(DoorLockSubmit.recordsVisitDate(result))
    }

    @Test
    fun checkboxUntickedAndMandatoryEmptyBlocksSubmit() {
        val result = DoorLockSubmit.resolve(doorLockChecked = false, mandatoryComplete = false)
        assertEquals(DoorLockSubmit.Result.BLOCKED, result)
        assertFalse(DoorLockSubmit.recordsVisitDate(result))
    }

    @Test
    fun mandatoryFieldsFilledCompletesEvenIfCheckboxStaysTicked() {
        val result = DoorLockSubmit.resolve(doorLockChecked = true, mandatoryComplete = true)
        assertEquals(DoorLockSubmit.Result.COMPLETE, result)
        assertFalse(DoorLockSubmit.recordsVisitDate(result))
    }

    @Test
    fun mandatoryFieldsFilledWithoutCheckboxCompletesAsUsual() {
        assertEquals(
            DoorLockSubmit.Result.COMPLETE,
            DoorLockSubmit.resolve(doorLockChecked = false, mandatoryComplete = true)
        )
    }

    @Test
    fun anotherLockedVisitRecordsANewVisitDate() {
        val stillLocked = DoorLockSubmit.resolve(doorLockChecked = true, mandatoryComplete = false)
        assertTrue(DoorLockSubmit.recordsVisitDate(stillLocked))
    }
}
