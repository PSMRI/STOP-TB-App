package org.piramalswasthya.stoptb.database.room.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.room.InvalidationTracker
import androidx.room.RoomDatabase
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A Room-backed PagingSource that loads pages with LIMIT/OFFSET and never counts the result.
 *
 * Room's own PagingSource (LimitOffsetPagingSource) runs `SELECT COUNT(*) FROM (<query>)` before
 * the first page and again after every invalidation, only to size placeholders. On a 1M-row
 * device that count took ~45 s for the TB Screening list while the page itself was instant
 * (index order), and a sync writing every few seconds made it start over each time
 * (device test 2026-11-10, report 10).
 *
 * Same SQL, same rows, same order as before - only the count is gone. Consequences:
 * - placeholders are off, so the list grows as it is scrolled (no jump-to-end);
 * - on a database change the source is invalidated exactly like Room's (same [tables]) and the
 *   list reloads around the user's position ([getRefreshKey]).
 *
 * [tables] must be the tables (or views) the query reads, as Room lists them for the original
 * @Query PagingSource, so writes to any of them refresh the list.
 */
class NoCountPagingSource<T : Any>(
    private val db: RoomDatabase,
    tables: Array<String>,
    private val loadRange: suspend (limit: Int, offset: Int) -> List<T>
) : PagingSource<Int, T>() {

    private val observer = object : InvalidationTracker.Observer(tables) {
        override fun onInvalidated(tables: Set<String>) {
            invalidate()
        }
    }
    private val registered = AtomicBoolean(false)

    // Adding or removing an observer can create or drop Room's triggers, which waits for the
    // database (behind a sync write, if one is running). Room's own PagingSource does both on its
    // query executor; load() can run on Main when the pager is collected in viewModelScope.
    private val queryDispatcher = db.queryExecutor.asCoroutineDispatcher()

    init {
        registerInvalidatedCallback {
            // invalidate() can be called on Main (a list refresh); remove on the query executor.
            if (registered.get()) db.queryExecutor.execute { db.invalidationTracker.removeObserver(observer) }
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
        return try {
            // Registered on the first load, on the query executor as Room does, before reading.
            // Inside the try: an unknown table name throws here, and that must become a
            // retryable LoadResult.Error rather than fail the list's collector.
            if (!invalid && registered.compareAndSet(false, true)) {
                withContext(queryDispatcher) {
                    try {
                        db.invalidationTracker.addObserver(observer)
                    } catch (error: Exception) {
                        registered.set(false) // a retry of this load registers again
                        throw error
                    }
                    // invalidate() may have run its callback before the observer was added, which then
                    // removed nothing; the tracker holds observers strongly, so drop it here instead of
                    // leaking this source and running it on every write. Removing twice is harmless.
                    if (invalid) db.invalidationTracker.removeObserver(observer)
                }
            }
            val (offset, limit) = when (params) {
                is LoadParams.Refresh -> (params.key ?: 0).coerceAtLeast(0) to params.loadSize
                is LoadParams.Append -> params.key to params.loadSize
                // Prepend key = offset where the already-loaded data starts; load the rows before it.
                is LoadParams.Prepend -> {
                    val end = params.key
                    val start = (end - params.loadSize).coerceAtLeast(0)
                    start to (end - start)
                }
            }
            if (limit <= 0) return LoadResult.Page(emptyList(), prevKey = null, nextKey = null)
            val rows = loadRange(limit, offset)
            if (invalid) return LoadResult.Invalid()
            LoadResult.Page(
                data = rows,
                prevKey = if (offset == 0) null else offset,
                nextKey = if (rows.size < limit) null else offset + rows.size
            )
        } catch (error: Exception) {
            LoadResult.Error(error)
        }
    }

    /** Reload around the item the user was looking at (offset of the anchor, minus half a load). */
    override fun getRefreshKey(state: PagingState<Int, T>): Int? {
        val anchor = state.anchorPosition ?: return null
        var itemsBefore = 0
        for (page in state.pages) {
            if (anchor < itemsBefore + page.data.size) {
                val pageOffset = page.prevKey ?: 0
                val anchorOffset = pageOffset + (anchor - itemsBefore)
                return (anchorOffset - state.config.initialLoadSize / 2).coerceAtLeast(0)
            }
            itemsBefore += page.data.size
        }
        return null
    }

    override val jumpingSupported: Boolean get() = false
}
