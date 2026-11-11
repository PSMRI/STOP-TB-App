package org.piramalswasthya.stoptb.ui

import androidx.paging.LoadState
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * Drives a paged list screen's loading / empty / list views from the adapter's load states.
 *
 * The loader belongs to the first load only: Room reloads the PagingSource on every write to
 * the tables it reads, and showing the loader for those reloads made the list vanish under the
 * user during a sync (same rule as AllBenFragment and NonHHFragment). "Empty" needs the load to
 * have finished and reached the end, so the idle state before the first load never flashes
 * "No records found". [onSettled] runs each time a load finishes (to clear a search loader).
 */
suspend fun PagingDataAdapter<*, *>.renderListStates(
    render: (awaitingFirstLoad: Boolean, isEmpty: Boolean) -> Unit,
    onSettled: () -> Unit = {}
) {
    var firstLoadSettled = false
    loadStateFlow.collectLatest { loadStates ->
        val refresh = loadStates.refresh
        if (refresh is LoadState.NotLoading) {
            firstLoadSettled = true
            onSettled()
        }
        val isEmpty = refresh is LoadState.NotLoading && loadStates.append.endOfPaginationReached && itemCount == 0
        render(refresh is LoadState.Loading && !firstLoadSettled, isEmpty)
    }
}

/**
 * Ids of the rows the adapter currently holds, re-emitted when pages load or drop. Lets a screen
 * read per-row decorations (badges, sync icons) for just those rows instead of whole tables.
 */
fun <T : Any> PagingDataAdapter<T, *>.loadedIds(idOf: (T) -> Long): Flow<Set<Long>> =
    onPagesUpdatedFlow
        .onStart { emit(Unit) }
        .map { snapshot().items.mapTo(HashSet()) { item -> idOf(item) } }
        .distinctUntilChanged()

/**
 * Smoother list transitions for the paged screens:
 * - a new search shows the top of its results, not the old scroll offset (call [onQueryChanged]
 *   when the query changes and [onSettled] when a load finishes - renderListStates' onSettled);
 * - badge and sync-icon updates rebind rows without the cross-fade that made them flash.
 */
class ListTransitions(private val list: RecyclerView) {
    private var scrollToTopPending = false
    private var lastQuery: String? = null

    init {
        (list.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
    }

    /**
     * [query] is the query as the screen's filter will see it. A keystroke that leaves it
     * unchanged (e.g. a trailing space the screen trims) starts no reload, so nothing would clear
     * the pending scroll and the next background reload would jump the list to the top.
     */
    fun onQueryChanged(query: String) {
        if (query == lastQuery) return
        lastQuery = query
        scrollToTopPending = true
    }

    fun onSettled() {
        if (!scrollToTopPending) return
        scrollToTopPending = false
        list.scrollToPosition(0)
    }
}
