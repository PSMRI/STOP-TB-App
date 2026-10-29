package org.piramalswasthya.stoptb.utils

import android.os.SystemClock
import androidx.paging.PagingSource
import androidx.paging.PagingState
import java.util.concurrent.atomic.AtomicInteger

/**
 * TEMPORARY PERFORMANCE INSTRUMENTATION - remove with the rest of the trace scaffolding.
 *
 * Wraps a Room [PagingSource] and times every [load], reporting whether the result came back
 * invalidated. Room's LimitOffsetPagingSource observes the query's tables and invalidates on any
 * write to them, and Paging then throws the source away and runs a full refresh (COUNT + page 0).
 * If a write lands during that refresh it happens again - so "how long does a load take" and
 * "how many times does it restart" are the two numbers that decide whether a list can ever
 * present. This records both.
 *
 * It delegates everything and changes no behaviour; invalidation is forwarded so Paging still
 * sees the wrapper go invalid exactly when the delegate does.
 */
class TimingPagingSource<Key : Any, Value : Any>(
    private val delegate: PagingSource<Key, Value>,
    private val label: String
) : PagingSource<Key, Value>() {

    companion object {
        private val generationCounter = AtomicInteger(0)
    }

    private val generation = generationCounter.incrementAndGet()

    init {
        delegate.registerInvalidatedCallback { invalidate() }
    }

    override fun getRefreshKey(state: PagingState<Key, Value>): Key? = delegate.getRefreshKey(state)

    override val jumpingSupported: Boolean get() = delegate.jumpingSupported

    override suspend fun load(params: LoadParams<Key>): LoadResult<Key, Value> {
        val kind = when (params) {
            is LoadParams.Refresh -> "REFRESH(count+page0)"
            is LoadParams.Append -> "APPEND"
            is LoadParams.Prepend -> "PREPEND"
        }
        // NOTE: do not issue another Room query from inside load(). Tried it to measure executor
        // queueing and it stopped the delegate's REFRESH from running at all - Paging delivered an
        // empty PagingData and the list never populated. Executor pressure is sampled from
        // DbContentionSampler instead, off the load path.
        val startedAt = SystemClock.elapsedRealtime()
        val result = delegate.load(params)
        val durationMs = SystemClock.elapsedRealtime() - startedAt
        val rows = (result as? LoadResult.Page)?.data?.size ?: -1
        ScreenLoadTrace.pagingLoad(
            kind = "$label#$generation $kind",
            durationMs = durationMs,
            rows = rows,
            invalidated = result is LoadResult.Invalid || delegate.invalid
        )
        return result
    }
}
