package org.piramalswasthya.stoptb.ui

import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.shareIn

/** Pause after the last keystroke before a list is re-filtered. */
const val SEARCH_DEBOUNCE_MS = 300L

/**
 * Debounces typed search text so a list is re-filtered once per pause in typing instead of once
 * per keystroke. An empty query passes straight through, so the list still appears immediately
 * when the screen opens and when the search box is cleared.
 */
@OptIn(FlowPreview::class)
fun Flow<String>.debounceTyping(delayMs: Long = SEARCH_DEBOUNCE_MS): Flow<String> =
    debounce { query -> if (query.isEmpty()) 0L else delayMs }

/** One emission of a searchable paged list: the pages and the query they were built for. */
class QueryPages<T : Any>(val query: String, val pages: PagingData<T>)

/**
 * The query -> paged data step of a searchable list, with every emission tagged with the query it
 * was built for, all the way through the cache. Replaces `flatMapLatest { pages(it) }.cachedIn(scope)`.
 *
 * Each query's pages are cached (cachedIn) in a scope that ends when the query is replaced, and the
 * latest tagged emission is shared in [scope] with replay 1, so a screen that collects again gets
 * the cached pages together with their own query. The screen passes [QueryPages.query] to
 * ListTransitions.onDataSubmitted: pages of an older query - however late they are submitted -
 * can't take a newer search's scroll to the top.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T : Any> Flow<String>.pagedByQuery(
    scope: CoroutineScope,
    pages: (query: String) -> Flow<PagingData<T>>
): Flow<QueryPages<T>> =
    flatMapLatest { query ->
        channelFlow { pages(query).cachedIn(this).collect { data -> send(QueryPages(query, data)) } }
    }.shareIn(scope, SharingStarted.Lazily, replay = 1)
