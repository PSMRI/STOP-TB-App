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

    /** One beneficiary's lock and the number of callers holding it or waiting for it. */
    private class Entry {
        val mutex = Mutex()
        var users = 0 // guarded by [locks]
    }

    // An entry is removed once no caller holds or waits for it. Keeping every lock ever taken grew
    // without bound: a diagnostics pull touches every beneficiary with a record, so at 1M of them
    // the map alone held ~100 MB for the life of the process. A caller registers (users++) before
    // waiting on the mutex and unregisters after releasing it, all under the map's monitor, so two
    // callers for the same benId always get the same Entry and none is removed while in use.
    private val locks = HashMap<Long, Entry>()

    private fun acquire(benId: Long): Entry = synchronized(locks) {
        locks.getOrPut(benId) { Entry() }.also { entry -> entry.users++ }
    }

    private fun release(benId: Long, entry: Entry) = synchronized(locks) {
        entry.users--
        if (entry.users == 0) locks.remove(benId)
    }

    /** Number of beneficiaries with a lock in use (held or waited for); for tests. */
    internal fun activeCount(): Int = synchronized(locks) { locks.size }

    suspend fun <T> withLock(benId: Long, block: suspend () -> T): T {
        val entry = acquire(benId)
        try {
            return entry.mutex.withLock { block() }
        } finally {
            release(benId, entry)
        }
    }

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
        val registered = ArrayList<Pair<Long, Entry>>(ordered.size)
        val locked = ArrayList<Mutex>(ordered.size)
        try {
            ordered.forEach { benId ->
                // Acquired one at a time rather than by nesting withLock, so a chunk of a
                // few hundred does not build a correspondingly deep chain of suspend frames.
                // Cancellation while waiting here still unwinds through the finally below.
                val entry = acquire(benId)
                registered += benId to entry
                entry.mutex.lock()
                locked += entry.mutex
            }
            return block()
        } finally {
            for (index in locked.indices.reversed()) locked[index].unlock()
            for ((benId, entry) in registered) release(benId, entry)
        }
    }
}
