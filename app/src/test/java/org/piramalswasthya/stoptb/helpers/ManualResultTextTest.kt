package org.piramalswasthya.stoptb.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * An Invalid/Error result entered while the camp hub is down used to be stored with its text
 * dropped and its pending-sync flag set. TBRepo.retryPendingManualResultSyncs derives what to
 * submit from that text, so the row was skipped on every sweep: the result was never sent, and
 * the repeat order that an Invalid/Error exists to trigger was never raised either.
 */
class ManualResultTextTest {

    @Test
    fun `an invalid or error result is kept while the entry is queued for replay`() {
        assertEquals(
            "Invalid/Error",
            manualResultTextToStore("Invalid/Error", isInvalidOrError = true, pendingSync = true)
        )
    }

    @Test
    fun `an invalid or error result is dropped once the backend has accepted it`() {
        // At that point the repeat order exists, and the old text must not outlive it on the card.
        assertNull(
            manualResultTextToStore("Invalid/Error", isInvalidOrError = true, pendingSync = false)
        )
    }

    @Test
    fun `every other result is stored whether queued or accepted`() {
        assertEquals(
            "MTB detected",
            manualResultTextToStore("MTB detected", isInvalidOrError = false, pendingSync = true)
        )
        assertEquals(
            "MTB detected",
            manualResultTextToStore("MTB detected", isInvalidOrError = false, pendingSync = false)
        )
    }

    @Test
    fun `a null result stays null`() {
        assertNull(manualResultTextToStore(null, isInvalidOrError = false, pendingSync = true))
        assertNull(manualResultTextToStore(null, isInvalidOrError = true, pendingSync = true))
    }
}
