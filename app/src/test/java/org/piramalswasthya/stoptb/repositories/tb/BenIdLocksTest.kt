package org.piramalswasthya.stoptb.repositories.tb

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class BenIdLocksTest {

    @Test
    fun locksAreRemovedOnceNoCallerUsesThem() = runBlocking {
        val locks = BenIdLocks()
        locks.withLock(1L) { assertEquals(1, locks.activeCount()) }
        locks.withLocks((1L..500L).toList()) { assertEquals(500, locks.activeCount()) }
        assertEquals(0, locks.activeCount())
    }

    @Test
    fun callersForTheSameBenIdNeverOverlap() = runBlocking(Dispatchers.Default) {
        val locks = BenIdLocks()
        val inside = AtomicInteger(0)
        val maxInside = AtomicInteger(0)
        var counter = 0 // unsynchronised on purpose: only the lock keeps it correct
        (1..200).map { round ->
            async {
                val block: suspend () -> Unit = {
                    maxInside.accumulateAndGet(inside.incrementAndGet()) { a, b -> maxOf(a, b) }
                    val read = counter
                    yield()
                    counter = read + 1
                    inside.decrementAndGet()
                }
                // Mix single and chunked callers; every chunk includes benId 7.
                if (round % 2 == 0) locks.withLock(7L, block) else locks.withLocks(listOf(3L, 7L, 9L), block)
            }
        }.awaitAll()
        assertEquals(1, maxInside.get())
        assertEquals(200, counter)
        assertEquals(0, locks.activeCount())
    }

    @Test
    fun aCancelledWaiterReleasesItsEntry() = runBlocking {
        val locks = BenIdLocks()
        val holding = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val holder = launch { locks.withLock(5L) { holding.complete(Unit); finish.await() } }
        holding.await()
        val waiter = launch { locks.withLocks(listOf(4L, 5L)) { } }
        withTimeout(5_000) { while (locks.activeCount() < 2) yield() }
        waiter.cancelAndJoin()
        assertTrue("held lock stays registered", locks.activeCount() == 1)
        finish.complete(Unit)
        holder.join()
        assertEquals(0, locks.activeCount())
    }
}
