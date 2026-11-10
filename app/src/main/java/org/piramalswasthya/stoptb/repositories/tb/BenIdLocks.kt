package org.piramalswasthya.stoptb.repositories.tb

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-beneficiary locks for TB_DIAGNOSTICS / TB_SUSPECTED read-modify-writes, moved out of TBRepo
 * unchanged. Must stay a process-wide singleton: every class that writes those rows has to use
 * the same lock instance for a benId, or the locks protect nothing.
 *
 * Only wrap a short leaf read-modify-write, never a whole function that might take the lock for
 * the same benId again - Mutex isn't reentrant.
 */
@Singleton
class BenIdLocks @Inject constructor() {

    private val locks = java.util.concurrent.ConcurrentHashMap<Long, Mutex>()
    suspend fun <T> withLock(benId: Long, block: suspend () -> T): T =
        locks.getOrPut(benId) { Mutex() }.withLock { block() }

    /**
     * Holds every lock in [benIds] for the duration of [block], acquiring them in ascending
     * benId order.
     *
     * Used by the pull loops, which need a whole chunk of records inside one write transaction.
     * The ordering matters twice over:
     *
     *  - Ascending order between callers means two overlapping chunks can never hold each
     *    other's next lock, so they cannot deadlock against one another.
     *  - Every lock is taken *before* the transaction opens. That keeps one global ordering -
     *    beneficiary mutex first, database write lock second - across every path in this file.
     *    Acquiring them the other way round (opening the transaction and then waiting on a lock
     *    held by, say, DiagnosticResultPollWorker, which is itself blocked on the write lock we
     *    are holding) would deadlock the two.
     */
    suspend fun <T> withLocks(benIds: Collection<Long>, block: suspend () -> T): T {
        val ordered = benIds.distinct().sorted()
        val acquired = ArrayList<Mutex>(ordered.size)
        try {
            ordered.forEach { benId ->
                // Acquired one at a time rather than by nesting withLock, so a chunk of a
                // few hundred does not build a correspondingly deep chain of suspend frames.
                // Cancellation while waiting here still unwinds through the finally below.
                locks.getOrPut(benId) { Mutex() }.also { mutex ->
                    mutex.lock()
                    acquired += mutex
                }
            }
            return block()
        } finally {
            for (index in acquired.indices.reversed()) acquired[index].unlock()
        }
    }
}
