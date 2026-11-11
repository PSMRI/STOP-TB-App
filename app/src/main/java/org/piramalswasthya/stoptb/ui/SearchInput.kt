package org.piramalswasthya.stoptb.ui

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce

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
