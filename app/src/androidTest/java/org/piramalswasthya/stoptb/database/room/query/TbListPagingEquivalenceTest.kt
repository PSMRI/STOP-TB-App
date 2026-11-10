package org.piramalswasthya.stoptb.database.room.query

import android.content.Context
import androidx.core.text.isDigitsOnly
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.piramalswasthya.stoptb.database.room.InAppDb
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.model.BenBasicDomain
import org.piramalswasthya.stoptb.model.BenRegCache
import org.piramalswasthya.stoptb.model.BenWithTbSuspectedCache
import org.piramalswasthya.stoptb.model.Gender
import org.piramalswasthya.stoptb.model.LocationEntity
import org.piramalswasthya.stoptb.model.LocationRecord

/**
 * PR 6: the TB lists page from SQL and search in SQL. For each list and a set of searches, the
 * paged rows must be exactly the rows the old screen showed: the full list, filtered in Kotlin
 * by the old filter ([oldFilterMatches], copied from the removed helpers/CommonUtils
 * filterTb*List), in the same order.
 *
 * Searches avoid two known, accepted differences: age is matched on whole years in SQL (the old
 * filter matched the display text, e.g. "2 Months"), and the old filter matched the "Not
 * Available" placeholder shown for a missing spouse/father name.
 */
@RunWith(AndroidJUnit4::class)
class TbListPagingEquivalenceTest {

    private lateinit var db: InAppDb

    @Before
    fun createDb() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, InAppDb::class.java).allowMainThreadQueries().build()
        seed()
    }

    @After
    fun closeDb() = db.close()

    @Test
    fun pagedListsMatchTheOldListsAndFilter() = runBlocking {
        var nonEmpty = 0
        for (villageId in VILLAGES) {
            val allScreening = db.benDao.getTbScreeningBenPaged(villageId, "").loadAll()
                .map { row -> row.asTbScreeningDomainModel().ben }
            // The Flow lists order by updatedDate only; ties came back in no defined order. The
            // paged lists add benId DESC as a tie-break, so the reference is sorted the same way.
            val newestFirst = compareByDescending<BenWithTbSuspectedCache> { row -> row.ben.updatedDate ?: 0L }
                .thenByDescending { row -> row.ben.benId }
            val allPresumptive = db.benDao.getTbScreeningList(villageId).first().sortedWith(newestFirst)
                .map { row -> row.asTbSuspectedDomainModel().ben }
            val allConfirmed = db.benDao.getTbConfirmedList(villageId).first().sortedWith(newestFirst)
                .map { row -> row.asTbSuspectedDomainModel().ben }

            assertEquals("presumptive unfiltered", allPresumptive.map { ben -> ben.benId },
                db.benDao.getTbPresumptivePaged(villageId, "").loadAll().map { row -> row.ben.benId })
            assertEquals("confirmed unfiltered", allConfirmed.map { ben -> ben.benId },
                db.benDao.getTbConfirmedPaged(villageId, "").loadAll().map { row -> row.ben.benId })

            for (query in QUERIES) {
                val label = "village=$villageId query='$query'"
                val screening = db.benDao.getTbScreeningBenPaged(villageId, query).loadAll().map { row -> row.ben.benId }
                assertEquals("screening $label", allScreening.filter { ben -> oldFilterMatches(ben, query) }.map { ben -> ben.benId }, screening)
                val presumptive = db.benDao.getTbPresumptivePaged(villageId, query).loadAll().map { row -> row.ben.benId }
                assertEquals("presumptive $label", allPresumptive.filter { ben -> oldFilterMatches(ben, query) }.map { ben -> ben.benId }, presumptive)
                val confirmed = db.benDao.getTbConfirmedPaged(villageId, query).loadAll().map { row -> row.ben.benId }
                assertEquals("confirmed $label", allConfirmed.filter { ben -> oldFilterMatches(ben, query) }.map { ben -> ben.benId }, confirmed)
                if (screening.isNotEmpty() && presumptive.isNotEmpty() && confirmed.isNotEmpty()) nonEmpty++
            }
        }
        assertTrue("searches should find rows in all three lists, got $nonEmpty", nonEmpty >= 6)
    }

    /** The screening list is every live beneficiary of the village, newest update first. */
    @Test
    fun screeningListIsTheWholeVillageNewestFirst() = runBlocking {
        for (villageId in VILLAGES) {
            val expected = QueryEquivalence.rows(db,
                "SELECT benId FROM BEN_BASIC_CACHE WHERE villageId = ? AND isDeath = 0 AND isDeactivate = 0 ORDER BY updatedDate DESC, benId DESC",
                arrayOf(villageId)).map { row -> row.single()!!.toLong() }
            assertEquals(expected, db.benDao.getTbScreeningBenPaged(villageId, "").loadAll().map { row -> row.ben.benId })
        }
    }

    /** The old Kotlin filter, as it was (query already lowercased). */
    private fun oldFilterMatches(ben: BenBasicDomain, filterText: String) =
        ben.benId.toString().lowercase().contains(filterText) ||
            ben.age.lowercase().contains(filterText) ||
            ben.familyHeadName.lowercase().contains(filterText) ||
            ben.benFullName.lowercase().contains(filterText) ||
            ben.spouseName?.lowercase()?.contains(filterText) ?: false ||
            ben.fatherName?.lowercase()?.contains(filterText) ?: false ||
            ben.mobileNo.lowercase().contains(filterText) ||
            ben.gender.lowercase().contains(filterText) ||
            ben.rchId.takeIf { rch -> rch?.isDigitsOnly() == true }?.contains(filterText) ?: false

    private suspend fun <T : Any> PagingSource<Int, T>.loadAll(): List<T> =
        (load(PagingSource.LoadParams.Refresh(key = null, loadSize = 100_000, placeholdersEnabled = false))
            as PagingSource.LoadResult.Page).data

    private suspend fun seed() {
        val firstNames = listOf("Ramesh", "Sunita", "Arjun", "Priya", "Mohan", "Kavita", "Imran", "Lakshmi")
        val lastNames = listOf("Singh", "Kumar", "Devi", "Patel", "Khan")
        for (index in 0 until 180) {
            val villageId = VILLAGES[index % VILLAGES.size]
            val ageYears = 16 + (index * 7) % 60
            db.benDao.upsert(
                BenRegCache(
                    beneficiaryId = FIRST_BEN_ID + index, isDeath = index % 17 == 0, reasonOfDeathId = 0,
                    placeOfDeathId = 0, ashaId = 1, isKid = false, isAdult = true,
                    locationRecord = LocationRecord(
                        country = LocationEntity(1, "India"), state = LocationEntity(2, "State"),
                        district = LocationEntity(3, "District"), block = LocationEntity(4, "Block"),
                        village = LocationEntity(villageId, "Village $villageId")
                    ),
                    syncState = SyncState.SYNCED, isDraft = false,
                    gender = if (index % 2 == 0) Gender.MALE else Gender.FEMALE,
                    dob = System.currentTimeMillis() - ageYears * MILLIS_PER_YEAR - 40L * MILLIS_PER_DAY,
                    updatedDate = BASE + (index % 50) * 1_000L, // ties, so the benId tie-break is exercised
                    isDeactivate = index % 23 == 0
                ).apply {
                    firstName = firstNames[index % firstNames.size]
                    lastName = lastNames[index % lastNames.size]
                    fatherName = if (index % 3 == 0) "Gopal ${lastNames[(index + 1) % lastNames.size]}" else null
                    contactNumber = 9_876_500_000L + index
                    rchId = when (index % 4) { 0 -> "4401${index}"; 1 -> "RCH$index"; else -> null }
                }
            )
        }
        val sql = db.openHelper.writableDatabase
        for (index in 0 until 180) {
            val benId = FIRST_BEN_ID + index
            if (index % 2 == 0) sql.execSQL(
                "INSERT INTO TB_SCREENING (benId, visitDate, syncState, coughMoreThan2Weeks, nightSweats) " +
                    "VALUES ($benId, $BASE, 2, ${if (index % 4 == 0) 1 else 0}, ${if (index % 6 == 0) 1 else 0})"
            )
            if (index % 5 == 0) sql.execSQL(
                "INSERT INTO TB_SUSPECTED (benId, visitDate, hasSymptoms, isConfirmed, syncState, isTBConfirmed) " +
                    "VALUES ($benId, $BASE, 1, ${if (index % 10 == 0) 1 else 0}, 2, ${if (index % 10 == 0) 1 else 0})"
            )
        }
    }

    private companion object {
        const val FIRST_BEN_ID = 610_000L
        const val BASE = 1_700_000_000_000L
        const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
        const val MILLIS_PER_YEAR = 365L * MILLIS_PER_DAY
        val VILLAGES = listOf(101, 102, 103)
        // Lowercased, as the screens pass them. None can match an age display string.
        val QUERIES = listOf("", "ram", "singh", "sunita devi", "gopal", "98765000", "6100", "female", "male", "4401", "rch")
    }
}
