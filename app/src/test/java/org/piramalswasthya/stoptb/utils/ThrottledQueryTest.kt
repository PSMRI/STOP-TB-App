package org.piramalswasthya.stoptb.utils

import androidx.room.InvalidationTracker
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
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
}
