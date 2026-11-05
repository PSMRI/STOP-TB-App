package org.piramalswasthya.stoptb.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.piramalswasthya.stoptb.database.room.SyncState

class TBSuspectedCacheTest {

    // What comes back from the changes feed: the laptop has no columns for these three fields.
    private val fromServer = TBSuspectedCache(
        benId = 42L,
        visitDate = 1_000L,
        hasSymptoms = false,
        isNaatConducted = null,
        naatResult = null,
        isSputumCollected = true,
        sputumTestResult = "TB Negative",
        syncState = SyncState.SYNCED,
        serverUpdatedDate = 5_000L,
    )

    @Test
    fun `re-deriving tablet-only fields is not a change the server cares about`() {
        val derived = fromServer.copy(
            hasSymptoms = true,
            isNaatConducted = true,
            naatResult = "TB Negative",
            syncState = SyncState.UNSYNCED,
            serverUpdatedDate = null,
        )
        assertThat(derived).isNotEqualTo(fromServer)
        assertThat(derived.withoutTabletOnlyFields()).isEqualTo(fromServer.withoutTabletOnlyFields())
    }

    @Test
    fun `a change to a field the server stores still counts`() {
        val derived = fromServer.copy(hasSymptoms = true, sputumTestResult = "TB Positive")
        assertThat(derived.withoutTabletOnlyFields()).isNotEqualTo(fromServer.withoutTabletOnlyFields())
    }
}
