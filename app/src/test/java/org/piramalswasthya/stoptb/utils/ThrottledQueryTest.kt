package org.piramalswasthya.stoptb.utils

import androidx.room.InvalidationTracker
import androidx.room.RoomDatabase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class ThrottledQueryTest {

    // addObserver writes Room's tracking triggers and can wait for an open write; on the
    // collector's (main) thread that caused an ANR during a bulk sync.
    @Test
    fun observerIsAddedAndRemovedOffTheCollectorThread() = runBlocking {
        var addThread: Thread? = null
        var removeThread: Thread? = null
        var queryThread: Thread? = null
        val tracker = mock(InvalidationTracker::class.java) { call ->
            when (call.method.name) {
                "addObserver" -> addThread = Thread.currentThread()
                "removeObserver" -> removeThread = Thread.currentThread()
            }
            null
        }
        val db = mock(RoomDatabase::class.java) { call ->
            if (call.method.name == "getInvalidationTracker") tracker else null
        }

        val collector = Thread.currentThread()
        val value = db.throttledQuery(arrayOf("T"), periodMs = 10) {
            queryThread = Thread.currentThread()
            42
        }.first()

        assertEquals(42, value)
        assertNotSame(collector, addThread)
        assertSame("query stays on the collector (callers set LiveData)", collector, queryThread)
        assertNotNull("observer must be removed when collection stops", removeThread)
        assertNotSame(collector, removeThread)
    }

    // Cancelled right after the observer is added: withContext throws on return, and the
    // observer must still be removed.
    @Test
    fun observerIsRemovedWhenCancelledDuringRegistration() = runBlocking {
        var collector: Job? = null
        var added = 0
        var removed = 0
        val tracker = mock(InvalidationTracker::class.java) { call ->
            when (call.method.name) {
                "addObserver" -> { added++; collector?.cancel() }
                "removeObserver" -> removed++
            }
            null
        }
        val db = mock(RoomDatabase::class.java) { call ->
            if (call.method.name == "getInvalidationTracker") tracker else null
        }

        collector = launch { db.throttledQuery(arrayOf("T"), periodMs = 10) { 1 }.collect {} }
        collector.join()

        assertTrue("observer was added", added == 1)
        assertEquals(1, removed)
    }
}
