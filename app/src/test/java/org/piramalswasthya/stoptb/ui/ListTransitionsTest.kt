package org.piramalswasthya.stoptb.ui

import androidx.paging.LoadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The search scroll-to-top of the paged lists. [RefreshTracker] gets every refresh-state change,
 * as the adapter's load-state listener delivers them, and [ListTransitions] scrolls once per new
 * query, at the first refresh that finishes after that query's data was submitted.
 */
class ListTransitionsTest {

    private var scrolls = 0
    private val transitions = ListTransitions { scrolls++ }
    private val tracker = RefreshTracker(onRefreshStarted = {}, onSettled = { transitions.onSettled() })

    private val loading = LoadState.Loading
    private val done = LoadState.NotLoading(endOfPaginationReached = false)

    private fun refresh(vararg states: LoadState) = states.forEach { state -> tracker.onRefreshState(state) }

    /** The first load of the screen, before any search. */
    private fun openScreen() {
        transitions.onQueryChanged("")
        transitions.onDataSubmitted()
        refresh(loading, done)
        scrolls = 0
    }

    @Test
    fun aQuickRefreshEndingInAnEqualStateStillSettlesTheSearch() {
        // Loading then a NotLoading equal to the state before it: the conflated flow can drop both,
        // the listener sees both.
        openScreen()
        transitions.onQueryChanged("ram")
        transitions.onDataSubmitted()
        refresh(loading, done)
        assertEquals(1, scrolls)
        assertTrue(tracker.firstLoadSettled)
    }

    @Test
    fun theOldQueryFinishingDuringTheDebounceDoesNotSettleTheNewOne() {
        openScreen()
        transitions.onQueryChanged("r")
        transitions.onDataSubmitted()
        refresh(loading)
        transitions.onQueryChanged("ra") // typed on; "ra" is still debouncing
        refresh(done) // "r" finishes
        assertEquals(0, scrolls)
        transitions.onDataSubmitted() // "ra" submitted
        refresh(loading, done)
        assertEquals(1, scrolls)
    }

    @Test
    fun newDataSubmittedWhileTheOldRefreshIsLoadingSettlesOnItsCompletion() {
        // The old refresh is still Loading when the new data arrives: no new "refresh started" is
        // reported, only the final NotLoading.
        openScreen()
        transitions.onQueryChanged("r")
        transitions.onDataSubmitted()
        refresh(loading)
        transitions.onQueryChanged("ra")
        transitions.onDataSubmitted()
        refresh(done)
        assertEquals(1, scrolls)
    }

    @Test
    fun aBackgroundReloadWithoutANewQueryDoesNotScroll() {
        openScreen()
        refresh(loading, done) // a sync write invalidated the list
        transitions.onQueryChanged("") // same query again, e.g. a trimmed trailing space
        transitions.onDataSubmitted()
        refresh(loading, done)
        assertEquals(0, scrolls)
    }

    @Test
    fun eachSearchScrollsOnceOnly() {
        openScreen()
        transitions.onQueryChanged("ram")
        transitions.onDataSubmitted()
        refresh(loading, done)
        refresh(loading, done) // later reloads of the same results keep the user's position
        assertEquals(1, scrolls)
    }

    @Test
    fun aFailedRefreshThatIsRetriedSettlesWhenTheRetryFinishes() {
        openScreen()
        transitions.onQueryChanged("ram")
        transitions.onDataSubmitted()
        refresh(loading, LoadState.Error(IllegalStateException("db")))
        assertEquals(0, scrolls)
        refresh(loading, done)
        assertEquals(1, scrolls)
    }
}
