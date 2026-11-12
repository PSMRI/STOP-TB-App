package org.piramalswasthya.stoptb.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.paging.Pager
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
 * The case: the user types a new search, and while it is debouncing a sync write invalidates the
 * old search's source. The pager emits a new generation of the OLD search's pages, which the screen
 * submits and refreshes. That reload must not take the scroll; the new search's results must.
 * The debounce is longer than the screens' 300 ms so the write lands inside it every time.
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

    /**
     * Runs the scenario and returns, for each scroll to the top, the query of the data that was
     * on screen. [tagWithQuery] = false submits without the query (the behaviour before the fix).
     */
    private fun scrollsWhenOldSearchReloadsDuringDebounce(tagWithQuery: Boolean): Pair<List<String?>, List<String?>> = runBlocking(Dispatchers.Main) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val query = MutableStateFlow("r")
        var appliedQuery: String? = null
        var typedQuery = "r"
        val submitted = ArrayList<String?>()
        val scrolledWith = ArrayList<String?>()
        val adapter = IdAdapter()
        val transitions = ListTransitions { scrolledWith += submitted.lastOrNull() }
        val pages = query.debounceTyping(DEBOUNCE_MS).flatMapLatest { search ->
            appliedQuery = search
            Pager(PagingConfig(pageSize = 30, initialLoadSize = 60, enablePlaceholders = false)) {
                NoCountPagingSource(db, arrayOf("BENEFICIARY")) { limit, offset -> idsFor(search, limit, offset) }
            }.flow
        }
        try {
            transitions.onQueryChanged("r")
            scope.launch {
                pages.collectLatest { page ->
                    val forQuery = if (tagWithQuery) appliedQuery else typedQuery
                    submitted += appliedQuery
                    transitions.onDataSubmitted(forQuery)
                    adapter.submitData(page)
                }
            }
            scope.launch { adapter.renderListStates(render = { _, _ -> }, onSettled = { transitions.onSettled() }) }
            withTimeout(10_000) { while (scrolledWith.isEmpty()) delay(20) } // "r" shown and at the top

            // Type "ra"; while it debounces, a sync write reloads the "r" pages.
            typedQuery = "ra"
            transitions.onQueryChanged("ra")
            query.value = "ra"
            withContext(Dispatchers.IO) { db.benDao.upsert(bens[0].copy(updatedDate = 1_800_000_000_000L)) }
            withTimeout(DEBOUNCE_MS) { while (submitted.size < 2) delay(10) } // "r" generation 2, inside the debounce
            withTimeout(10_000) { while (submitted.lastOrNull() != "ra") delay(20) }
            withTimeout(10_000) { while (adapter.snapshot().items.firstOrNull() != FIRST_BEN_ID + 196) delay(20) } // "ra" shown
            delay(500) // let any late settle run
            submitted.toList() to scrolledWith.toList()
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun theOldSearchReloadedDuringTheDebounceDoesNotTakeTheNewSearchsScroll() {
        val (submitted, scrolledWith) = scrollsWhenOldSearchReloadsDuringDebounce(tagWithQuery = true)
        assertEquals("the sync write reloaded the old search during the debounce", listOf("r", "r", "ra"), submitted)
        assertEquals("one scroll for each search, the second with the new search's data", listOf("r", "ra"), scrolledWith)
    }

    /** Without the query tag (before the fix) the old search's reload takes the scroll: the test can see the bug. */
    @Test
    fun withoutTheQueryTagTheOldReloadTakesTheScroll() {
        val (submitted, scrolledWith) = scrollsWhenOldSearchReloadsDuringDebounce(tagWithQuery = false)
        assertEquals(listOf("r", "r", "ra"), submitted)
        assertEquals(listOf("r", "r"), scrolledWith)
    }

    private companion object {
        const val FIRST_BEN_ID = 700_000L
        const val DEBOUNCE_MS = 2_000L
    }
}
