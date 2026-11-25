package org.piramalswasthya.stoptb.utils

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * Emits the first value immediately, then at most one value per [periodMs], always the latest.
 * Nothing is dropped at the end: the last value is always delivered.
 *
 * For badge/decoration Flows over whole tables: during sync every TB write re-runs the query and
 * the screen re-processes a list of every beneficiary on the main thread. Users can't see more
 * than a couple of badge updates per second anyway.
 */
fun <T> Flow<T>.throttleLatest(periodMs: Long): Flow<T> = flow {
    coroutineScope {
        val values = Channel<T>(Channel.CONFLATED)
        val producer = launch {
            collect { value -> values.send(value) }
            values.close()
        }
        try {
            while (true) {
                // isClosed, not a null check: a Flow<T?> may emit null, which must be delivered.
                val received = values.receiveCatching()
                if (received.isClosed) break
                @Suppress("UNCHECKED_CAST")
                emit(received.getOrNull() as T)
                delay(periodMs)
            }
        } finally {
            producer.cancel()
        }
    }
}
