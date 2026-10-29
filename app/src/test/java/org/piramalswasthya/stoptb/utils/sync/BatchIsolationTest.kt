package org.piramalswasthya.stoptb.utils.sync

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

class BatchIsolationTest {

    private fun serverRejecting(vararg bad: Int): suspend (List<Int>) -> Boolean =
        { batch -> batch.none { it in bad } }

    @Test
    fun acceptedBatchIsSentInOneRequest() = runBlocking {
        var requests = 0
        val outcome = BatchIsolation.send((1..8).toList()) { requests++; true }
        assertThat(outcome.sent).containsExactlyElementsIn(1..8)
        assertThat(outcome.rejected).isEmpty()
        assertThat(requests).isEqualTo(1)
    }

    @Test
    fun oneBadItemDoesNotHoldBackTheOthers() = runBlocking {
        val outcome = BatchIsolation.send((1..8).toList(), serverRejecting(5))
        assertThat(outcome.sent).containsExactly(1, 2, 3, 4, 6, 7, 8)
        assertThat(outcome.rejected).containsExactly(5)
        assertThat(outcome.nothingAccepted).isFalse()
    }

    @Test
    fun severalBadItemsAreAllIsolated() = runBlocking {
        val outcome = BatchIsolation.send((1..9).toList(), serverRejecting(1, 9))
        assertThat(outcome.rejected).containsExactly(1, 9)
        assertThat(outcome.sent).hasSize(7)
    }

    @Test
    fun serverRejectingEverythingIsReportedAsNothingAccepted() = runBlocking {
        var requests = 0
        val outcome = BatchIsolation.send((1..9).toList()) { requests++; false }
        assertThat(outcome.nothingAccepted).isTrue()
        assertThat(outcome.rejected).hasSize(9)
        assertThat(requests).isEqualTo(17) // 2n - 1
    }

    @Test
    fun emptyBatchSendsNothing() = runBlocking {
        var requests = 0
        val outcome = BatchIsolation.send(emptyList<Int>()) { requests++; true }
        assertThat(requests).isEqualTo(0)
        assertThat(outcome.nothingAccepted).isFalse()
    }
}
