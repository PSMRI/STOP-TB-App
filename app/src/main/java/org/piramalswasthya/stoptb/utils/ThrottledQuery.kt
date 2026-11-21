package org.piramalswasthya.stoptb.utils

import androidx.room.InvalidationTracker
import androidx.room.RoomDatabase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Runs [query] now, then again after writes to [tables]: at most once per [periodMs], always
 * after the latest write. A Room Flow re-runs on every write, so during sync an expensive count
 * ran back to back and kept the query threads busy; this re-runs once per burst instead.
 */
fun <T> RoomDatabase.throttledQuery(tables: Array<String>, periodMs: Long, query: suspend () -> T): Flow<T> = flow {
    val changes = Channel<Unit>(Channel.CONFLATED)
    val observer = object : InvalidationTracker.Observer(tables) {
        override fun onInvalidated(tables: Set<String>) {
            changes.trySend(Unit)
        }
    }
    invalidationTracker.addObserver(observer)
    try {
        emit(query())
        while (true) {
            changes.receive()
            delay(periodMs)
            changes.tryReceive() // writes during the wait are covered by this run
            emit(query())
        }
    } finally {
        invalidationTracker.removeObserver(observer)
    }
}
