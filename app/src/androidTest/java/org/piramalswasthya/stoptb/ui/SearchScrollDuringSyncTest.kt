package org.piramalswasthya.stoptb.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.paging.Pager
import androidx.paging.PagingData
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CompletableDeferred
import androidx.paging.cachedIn
import androidx.paging.PagingConfig
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.piramalswasthya.stoptb.database.room.InAppDb
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.database.room.paging.NoCountPagingSource
import org.piramalswasthya.stoptb.model.BenRegCache
import org.piramalswasthya.stoptb.model.Gender
import org.piramalswasthya.stoptb.model.LocationEntity
import org.piramalswasthya.stoptb.model.LocationRecord

/**
 * The search scroll-to-top on the real stack: Room, NoCountPagingSource, Pager, a
 * PagingDataAdapter, renderListStates and ListTransitions, wired the way the list screens are
 * (debounced query -> flatMapLatest -> submitData).
 *
 * The case: the user types a new search and a sync write invalidates the OLD search's source, so
 * the pager emits a new generation of the old search's pages. Whether those pages are submitted
 * during the debounce or only after the query has switched (main thread busy, or replayed from the
 * cache), they must not take the scroll; the new search's results must. The debounce is longer
 * than the screens' 300 ms and the new search's pages are gated, so each ordering is exact.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SearchScrollDuringSyncTest {

    private lateinit var db: InAppDb
    private val bens = ArrayList<BenRegCache>()

    @Before
    fun createDb() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, InAppDb::class.java).build()
        for (index in 0 until 200) {
            val ben = BenRegCache(
                beneficiaryId = FIRST_BEN_ID + index, isDeath = false, reasonOfDeathId = 0, placeOfDeathId = 0,
                ashaId = 1, isKid = false, isAdult = true,
                locationRecord = LocationRecord(
                    country = LocationEntity(1, "India"), state = LocationEntity(2, "State"),
                    district = LocationEntity(3, "District"), block = LocationEntity(4, "Block"),
                    village = LocationEntity(101, "Village")
                ),
                syncState = SyncState.SYNCED, isDraft = false, gender = Gender.MALE,
                dob = 0L, updatedDate = 1_700_000_000_000L, isDeactivate = false
            )
            bens += ben
            db.benDao.upsert(ben)
        }
    }

    @After
    fun closeDb() = db.close()

    /** "r" = even ids, "ra" = multiples of four: two searches with different first rows. */
    private suspend fun idsFor(query: String, limit: Int, offset: Int): List<Long> = withContext(Dispatchers.IO) {
        val modulo = if (query == "ra") 4 else 2
        db.query(
            SimpleSQLiteQuery(
                "SELECT beneficiaryId FROM BENEFICIARY WHERE beneficiaryId % $modulo = 0 ORDER BY beneficiaryId DESC LIMIT ? OFFSET ?",
                arrayOf<Any>(limit, offset)
            )
        ).use { cursor -> List(cursor.count) { cursor.moveToNext(); cursor.getLong(0) } }
    }

    private class IdAdapter : PagingDataAdapter<Long, RecyclerView.ViewHolder>(object : DiffUtil.ItemCallback<Long>() {
        override fun areItemsTheSame(oldItem: Long, newItem: Long) = oldItem == newItem
        override fun areContentsTheSame(oldItem: Long, newItem: Long) = oldItem == newItem
    }) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = object : RecyclerView.ViewHolder(View(parent.context)) {}
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
    }

    private enum class Pipeline {
        /** The screens' flow: pagedByQuery, every emission tagged with its query through the cache. */
        TAGGED,
        /**
         * The previous flow: flatMapLatest + cachedIn, the query read from a separate field. Kept
         * for comparison runs: on a device it passed both orderings too, because the old search's
         * pages, once the query has switched, only come from the cache and replay as NotLoading -
         * no refresh finishes for them, so they can't take the scroll.
         */
        APPLIED_QUERY_FIELD
    }

    /** Which search's results a page shows, from its first row ("r" = even ids, "ra" = multiples of 4). */
    private fun shownSearch(firstId: Long?): String? = when (firstId) {
        FIRST_BEN_ID + 198 -> "r"
        FIRST_BEN_ID + 196 -> "ra"
        else -> null
    }

    /**
     * Wires a list screen (debounced query -> paged flow -> collectLatest submitData,
     * renderListStates, ListTransitions) and runs [steps]. Returns the search whose results were on
     * screen at each scroll to the top.
     */
    private fun runScreen(
        pipeline: Pipeline,
        steps: suspend Screen.() -> Unit
    ): List<String?> = runBlocking(Dispatchers.Main) {
        val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val screen = Screen()
        val adapter = IdAdapter()
        val transitions = ListTransitions { screen.scrolledWith += shownSearch(adapter.snapshot().items.firstOrNull()) }
        val pager: (String) -> Flow<PagingData<Long>> = { search ->
            val pages = Pager(PagingConfig(pageSize = 30, initialLoadSize = 60, enablePlaceholders = false)) {
                NoCountPagingSource(db, arrayOf("BENEFICIARY")) { limit, offset -> idsFor(search, limit, offset) }
            }.flow.onEach { screen.produced += search }
            if (search == "ra") flow { screen.newSearchGate.await(); emitAll(pages) } else pages
        }
        var appliedQuery: String? = null
        val tagged: Flow<Pair<String?, PagingData<Long>>> = when (pipeline) {
            Pipeline.TAGGED -> screen.query.debounceTyping(DEBOUNCE_MS)
                .onEach { search -> screen.switchedTo = search }
                .pagedByQuery(viewModelScope, pager)
                .map { page -> page.query to page.pages }
            Pipeline.APPLIED_QUERY_FIELD -> screen.query.debounceTyping(DEBOUNCE_MS)
                .flatMapLatest { search -> appliedQuery = search; screen.switchedTo = search; pager(search) }
                .cachedIn(viewModelScope)
                .map { pages -> appliedQuery to pages }
        }
        try {
            transitions.onQueryChanged("r")
            screenScope.launch {
                tagged.collectLatest { (forQuery, pages) ->
                    while (screen.busy) delay(10) // the main thread is busy; the latest emission waits
                    transitions.onDataSubmitted(forQuery)
                    adapter.submitData(pages)
                }
            }
            screenScope.launch { adapter.renderListStates(render = { _, _ -> }, onSettled = { transitions.onSettled() }) }
            withTimeout(10_000) { while (screen.scrolledWith.isEmpty()) delay(20) } // "r" shown, at the top
            screen.typeNewSearch = { transitions.onQueryChanged("ra"); screen.query.value = "ra" }
            screen.steps()
            withTimeout(10_000) { while (shownSearch(adapter.snapshot().items.firstOrNull()) != "ra") delay(20) }
            delay(500) // let any late settle run
            screen.scrolledWith.toList()
        } finally {
            screenScope.cancel()
            viewModelScope.cancel()
        }
    }

    private inner class Screen {
        val query = MutableStateFlow("r")
        val produced = ArrayList<String>()
        val scrolledWith = ArrayList<String?>()
        val newSearchGate = CompletableDeferred<Unit>()
        @Volatile var busy = false
        @Volatile var switchedTo: String? = null
        lateinit var typeNewSearch: () -> Unit

        /** A sync write to the table the old search reads, then wait for its new generation. */
        suspend fun syncWriteReloadsOldSearch() {
            val before = produced.count { search -> search == "r" }
            withContext(Dispatchers.IO) { db.benDao.upsert(bens[0].copy(updatedDate = System.currentTimeMillis())) }
            withTimeout(DEBOUNCE_MS) { while (produced.count { search -> search == "r" } == before) delay(10) }
        }
    }

    /** The old search's source reloads while the new search is still debouncing. */
    private suspend fun Screen.reloadDuringDebounce() {
        typeNewSearch()
        syncWriteReloadsOldSearch()
        delay(300) // the reload is submitted and settles inside the debounce
        newSearchGate.complete(Unit)
    }

    /**
     * The old search's reloaded pages are held back (main thread busy) until after the query has
     * switched to "ra", then submitted; only then do "ra"'s pages arrive.
     */
    private suspend fun Screen.oldPagesDeliveredAfterTheSwitch() {
        busy = true
        syncWriteReloadsOldSearch()
        typeNewSearch()
        withTimeout(10_000) { while (switchedTo != "ra") delay(10) } // switched; "ra" pages gated
        busy = false
        delay(1_000) // the old pages are submitted and settle
        newSearchGate.complete(Unit)
    }

    @Test
    fun oldSearchReloadedDuringTheDebounceDoesNotTakeTheScroll() {
        assertEquals(listOf("r", "ra"), runScreen(Pipeline.TAGGED) { reloadDuringDebounce() })
    }

    @Test
    fun oldSearchPagesDeliveredAfterTheSwitchDoNotTakeTheScroll() {
        assertEquals(listOf("r", "ra"), runScreen(Pipeline.TAGGED) { oldPagesDeliveredAfterTheSwitch() })
    }

    private companion object {
        const val FIRST_BEN_ID = 700_000L
        const val DEBOUNCE_MS = 2_000L
    }
}
