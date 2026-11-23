package org.piramalswasthya.stoptb.utils

import androidx.room.InvalidationTracker
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * Runs [query] now, then again after writes to [tables]: at most once per [periodMs], always
 * after the latest write. A Room Flow re-runs on every write, so during sync an expensive count
 * ran back to back and kept the query threads busy; this re-runs once per burst instead.
 *
 * The observer is added and removed on Dispatchers.IO: both write Room's tracking triggers and
 * wait for any open write, so on the main thread a bulk sync caused an ANR. [query] still runs
 * on the collector's dispatcher (callers set LiveData from it).
 */
fun <T> RoomDatabase.throttledQuery(tables: Array<String>, periodMs: Long, query: suspend () -> T): Flow<T> = flow {
    val changes = Channel<Unit>(Channel.CONFLATED)
    val observer = object : InvalidationTracker.Observer(tables) {
        override fun onInvalidated(tables: Set<String>) {
            changes.trySend(Unit)
        }
    }
    withContext(Dispatchers.IO) { invalidationTracker.addObserver(observer) }
    try {
        emit(query())
        while (true) {
            changes.receive()
            delay(periodMs)
            changes.tryReceive() // writes during the wait are covered by this run
            emit(query())
        }
    } finally {
        withContext(NonCancellable + Dispatchers.IO) { invalidationTracker.removeObserver(observer) }
    }
}
