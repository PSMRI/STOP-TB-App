package org.piramalswasthya.stoptb.database.room.query

import android.content.Context
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.piramalswasthya.stoptb.database.room.InAppDb
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.database.room.paging.NoCountPagingSource
import org.piramalswasthya.stoptb.model.BenBasicCache
import org.piramalswasthya.stoptb.model.BenRegCache
import org.piramalswasthya.stoptb.model.Gender
import org.piramalswasthya.stoptb.model.LocationEntity
import org.piramalswasthya.stoptb.model.LocationRecord

/**
 * The count-free PagingSource: appending and prepending pages reproduces the full query result in
 * order, a refresh resumes around the user's position, and a write to an observed table
 * invalidates it (the list reloads) exactly as Room's own source did.
 */
@RunWith(AndroidJUnit4::class)
class NoCountPagingSourceTest {

    private lateinit var db: InAppDb

    @Before
    fun createDb() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, InAppDb::class.java).allowMainThreadQueries().build()
        for (index in 0 until 250) addBen(index)
    }

    @After
    fun closeDb() = db.close()

    private suspend fun addBen(index: Int) = db.benDao.upsert(
        BenRegCache(
            beneficiaryId = FIRST_BEN_ID + index, isDeath = false, reasonOfDeathId = 0, placeOfDeathId = 0,
            ashaId = 1, isKid = false, isAdult = true,
            locationRecord = LocationRecord(
                country = LocationEntity(1, "India"), state = LocationEntity(2, "State"),
                district = LocationEntity(3, "District"), block = LocationEntity(4, "Block"),
                village = LocationEntity(VILLAGE, "Village")
            ),
            syncState = SyncState.SYNCED, isDraft = false, gender = Gender.FEMALE,
            dob = 0L, updatedDate = 1_700_000_000_000L + (index % 90) * 1_000L
        )
    )

    private fun source() = NoCountPagingSource(db, arrayOf("BEN_BASIC_CACHE", "TB_SCREENING")) { limit, offset ->
        db.benDao.getUnscreenedPage(VILLAGE, "", limit, offset)
    }

    private val all: List<Long> by lazy { runBlocking { db.benDao.getUnscreenedPage(VILLAGE, "", 100_000, 0).map { ben -> ben.benId } } }

    private suspend fun PagingSource<Int, BenBasicCache>.page(params: PagingSource.LoadParams<Int>) =
        load(params) as PagingSource.LoadResult.Page

    @Test
    fun appendingPagesReproducesTheWholeResultInOrder() = runBlocking {
        val source = source()
        val first = source.page(PagingSource.LoadParams.Refresh(null, 60, false))
        assertNull(first.prevKey)
        assertEquals(60, first.nextKey)
        val loaded = first.data.map { ben -> ben.benId }.toMutableList()
        var key = first.nextKey
        while (key != null) {
            val next = source.page(PagingSource.LoadParams.Append(key, 30, false))
            loaded += next.data.map { ben -> ben.benId }
            key = next.nextKey
        }
        assertEquals(all, loaded)
        assertEquals(250, loaded.size)
    }

    @Test
    fun prependingFromAMidListRefreshFillsTheRowsBefore() = runBlocking {
        val source = source()
        val middle = source.page(PagingSource.LoadParams.Refresh(100, 60, false))
        assertEquals(all.subList(100, 160), middle.data.map { ben -> ben.benId })
        assertEquals(100, middle.prevKey)
        val before = source.page(PagingSource.LoadParams.Prepend(100, 30, false))
        assertEquals(all.subList(70, 100), before.data.map { ben -> ben.benId })
        assertEquals(70, before.prevKey)
        val top = source.page(PagingSource.LoadParams.Prepend(20, 30, false))
        assertEquals(all.subList(0, 20), top.data.map { ben -> ben.benId })
        assertNull(top.prevKey)
    }

    @Test
    fun refreshKeyResumesAroundTheAnchoredRow() = runBlocking {
        val source = source()
        val first = source.page(PagingSource.LoadParams.Refresh(90, 60, false)) // rows 90..149
        val second = source.page(PagingSource.LoadParams.Append(first.nextKey!!, 30, false)) // 150..179
        val config = PagingConfig(pageSize = 30, initialLoadSize = 60, enablePlaceholders = false)
        // Anchor on the 10th row of the second page = offset 159; reload starts half a load earlier.
        val state = PagingState(listOf(first, second), anchorPosition = 69, config = config, leadingPlaceholderCount = 0)
        assertEquals(159 - 30, source.getRefreshKey(state))
        assertNull(source.getRefreshKey(PagingState(listOf(first), anchorPosition = null, config = config, leadingPlaceholderCount = 0)))
    }

    @Test
    fun aWriteToAnObservedTableInvalidatesTheSource() = runBlocking {
        val source = source()
        source.page(PagingSource.LoadParams.Refresh(null, 60, false))
        assertFalse(source.invalid)
        addBen(999)
        repeat(50) { if (!source.invalid) delay(100) }
        assertTrue("a BENEFICIARY write must refresh the list", source.invalid)
    }

    private companion object {
        const val FIRST_BEN_ID = 640_000L
        const val VILLAGE = 101
    }
}
