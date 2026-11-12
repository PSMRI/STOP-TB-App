package org.piramalswasthya.stoptb.ui

import android.view.View
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import com.google.android.material.snackbar.Snackbar
import org.piramalswasthya.stoptb.R
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import kotlinx.coroutines.delay
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
 * "No records found". [onRefreshStarted] runs when a refresh starts and [onSettled] when it
 * finishes (see [ListTransitions]); both are driven by every load-state change, not the
 * conflated flow.
 */
suspend fun PagingDataAdapter<*, *>.renderListStates(
    render: (awaitingFirstLoad: Boolean, isEmpty: Boolean) -> Unit,
    onSettled: () -> Unit = {},
    onRefreshStarted: () -> Unit = {},
    errorAnchor: View? = null
) {
    var failedLoads = 0
    var failureShown: Snackbar? = null
    // loadStateFlow is conflated and skips equal states: a quick refresh can reach the collector as
    // NotLoading only, or not at all (Loading then a NotLoading equal to the previous state), and
    // the search would never settle. A load-state listener is called for every change, so the
    // start and the end of each refresh are both seen there.
    val refreshTracker = RefreshTracker(onRefreshStarted, onSettled)
    val refreshListener: (CombinedLoadStates) -> Unit = { states -> refreshTracker.onRefreshState(states.refresh) }
    addLoadStateListener(refreshListener)
    try {
    loadStateFlow.collectLatest { loadStates ->
        val refresh = loadStates.refresh
        val firstLoadSettled = refreshTracker.firstLoadSettled
        // A failed load (a database error in the paging source) left the list empty, or stopped
        // at the last loaded row, with no way back. Retry a few times, backing off; then offer a
        // Retry action. Covers the first load (refresh), loading further rows (append), and
        // loading earlier rows again after they were dropped (prepend, when scrolling back up).
        val error = (refresh as? LoadState.Error) ?: (loadStates.append as? LoadState.Error)
            ?: (loadStates.prepend as? LoadState.Error)
        if (error == null) {
            // Reset only once nothing is loading: an append retry shows refresh NotLoading and
            // append Loading, and resetting there kept a failing append retrying forever.
            if (refresh is LoadState.NotLoading && loadStates.append is LoadState.NotLoading &&
                loadStates.prepend is LoadState.NotLoading
            ) failedLoads = 0
            failureShown?.dismiss()
            failureShown = null
        } else if (failedLoads < MAX_REFRESH_RETRIES) {
            failedLoads++
            timber.log.Timber.w(error.error, "Paged list load failed, retry $failedLoads of $MAX_REFRESH_RETRIES")
            render(!firstLoadSettled && refresh is LoadState.Error, false)
            delay(REFRESH_RETRY_DELAY_MS * failedLoads)
            retry()
            return@collectLatest
        } else if (failureShown == null && errorAnchor != null) {
            failureShown = Snackbar.make(errorAnchor, R.string.list_load_failed, Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.try_again) {
                    failedLoads = 0
                    failureShown = null
                    retry()
                }
                .also { snackbar -> snackbar.show() }
        }
        val isEmpty = refresh is LoadState.NotLoading && loadStates.append.endOfPaginationReached && itemCount == 0
        render(refresh is LoadState.Loading && !firstLoadSettled, isEmpty)
    }
    } finally {
        removeLoadStateListener(refreshListener)
    }
}

private const val MAX_REFRESH_RETRIES = 3
private const val REFRESH_RETRY_DELAY_MS = 1_000L

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
 * Follows a paged list's refresh state, change by change, and reports when a refresh starts and
 * when it finishes. Fed from a load-state listener (every change), not the conflated flow.
 * An append finishing never counts: only the refresh state is looked at.
 */
internal class RefreshTracker(
    private val onRefreshStarted: () -> Unit,
    private val onSettled: () -> Unit
) {
    var firstLoadSettled = false
        private set
    private var refreshLoading = false

    fun onRefreshState(refresh: LoadState) {
        when (refresh) {
            is LoadState.Loading -> if (!refreshLoading) {
                refreshLoading = true
                onRefreshStarted()
            }
            is LoadState.NotLoading -> {
                firstLoadSettled = true
                if (refreshLoading) {
                    refreshLoading = false
                    onSettled()
                }
            }
            is LoadState.Error -> refreshLoading = false
        }
    }
}

/**
 * Smoother list transitions for the paged screens:
 * - a new search shows the top of its results, not the old scroll offset. Call [onQueryChanged]
 *   when the query changes, [onDataSubmitted] right before each `submitData`, and pass
 *   [onSettled] to renderListStates;
 * - badge and sync-icon updates rebind rows without the cross-fade that made them flash.
 *
 * The scroll belongs to the data of the latest query: it runs at the first refresh that finishes
 * after that data was submitted. A refresh of the previous query finishing during the search
 * debounce (before the new data is submitted) doesn't take it, and neither does a background
 * reload when no query changed. Submitting new data while the previous refresh is still loading
 * reports no new "refresh started", which is why this keys on the submit instead.
 */
class ListTransitions internal constructor(private val scrollToTop: () -> Unit) {

    constructor(list: RecyclerView) : this({ list.scrollToPosition(0) }) {
        (list.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
    }

    private var scrollToTopPending = false
    private var pendingDataSubmitted = false
    private var lastQuery: String? = null

    /**
     * [query] is the query as the screen's filter will see it. A keystroke that leaves it
     * unchanged (e.g. a trailing space the screen trims) submits no new data, so it sets nothing.
     */
    fun onQueryChanged(query: String) {
        if (query == lastQuery) return
        lastQuery = query
        scrollToTopPending = true
        pendingDataSubmitted = false
    }

    /** Call right before `submitData`: the next refresh to finish belongs to this data. */
    fun onDataSubmitted() {
        if (scrollToTopPending) pendingDataSubmitted = true
    }

    fun onSettled() {
        if (!scrollToTopPending || !pendingDataSubmitted) return
        scrollToTopPending = false
        pendingDataSubmitted = false
        scrollToTop()
    }
}
