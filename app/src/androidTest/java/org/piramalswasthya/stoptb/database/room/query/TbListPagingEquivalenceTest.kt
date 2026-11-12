package org.piramalswasthya.stoptb.database.room.query

import android.content.Context
import androidx.core.text.isDigitsOnly
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
            val allScreening = db.benDao.getTbScreeningBenPage(villageId, "", ALL_ROWS, 0)
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
                db.benDao.getTbPresumptivePage(villageId, "", ALL_ROWS, 0).map { row -> row.ben.benId })
            assertEquals("confirmed unfiltered", allConfirmed.map { ben -> ben.benId },
                db.benDao.getTbConfirmedPage(villageId, "", ALL_ROWS, 0).map { row -> row.ben.benId })

            for (query in QUERIES) {
                val label = "village=$villageId query='$query'"
                val screening = db.benDao.getTbScreeningBenPage(villageId, query, ALL_ROWS, 0).map { row -> row.ben.benId }
                assertEquals("screening $label", allScreening.filter { ben -> oldFilterMatches(ben, query) }.map { ben -> ben.benId }, screening)
                val presumptive = db.benDao.getTbPresumptivePage(villageId, query, ALL_ROWS, 0).map { row -> row.ben.benId }
                assertEquals("presumptive $label", allPresumptive.filter { ben -> oldFilterMatches(ben, query) }.map { ben -> ben.benId }, presumptive)
                val confirmed = db.benDao.getTbConfirmedPage(villageId, query, ALL_ROWS, 0).map { row -> row.ben.benId }
                assertEquals("confirmed $label", allConfirmed.filter { ben -> oldFilterMatches(ben, query) }.map { ben -> ben.benId }, confirmed)
                if (screening.isNotEmpty() && presumptive.isNotEmpty() && confirmed.isNotEmpty()) nonEmpty++
            }
        }
        assertTrue("searches should find rows in all three lists, got $nonEmpty", nonEmpty >= 6)
    }

    /**
     * A suspected record with a positive NAAT or liquid culture result but no confirmed flag (a
     * record synced from elsewhere) makes the beneficiary Confirmed, not Presumptive, in the lists
     * and their counts - the same as a positive result on the diagnostics record.
     */
    @Test
    fun positiveSuspectedLabResultWithoutFlagsIsConfirmed() = runBlocking {
        // Index 4 and 8: symptomatic screening (cough), no suspected row in the seed, alive, active.
        val cases = listOf(4 to "naatResult = 'MTB Detected'", 8 to "liquidCultureResult = 'Positive'")
        val sql = db.openHelper.writableDatabase
        for ((index, _) in cases) {
            val benId = FIRST_BEN_ID + index
            val villageId = VILLAGES[index % VILLAGES.size]
            assertTrue("seed: $benId presumptive before", db.benDao.getTbPresumptivePage(villageId, "", ALL_ROWS, 0).any { row -> row.ben.benId == benId })
        }
        for ((index, result) in cases) {
            val benId = FIRST_BEN_ID + index
            sql.execSQL(
                "INSERT INTO TB_SUSPECTED (benId, visitDate, hasSymptoms, isConfirmed, syncState, isTBConfirmed) " +
                    "VALUES ($benId, $BASE, 1, 0, 2, 0)"
            )
            sql.execSQL("UPDATE TB_SUSPECTED SET $result WHERE benId = $benId")
        }
        for ((index, result) in cases) {
            val benId = FIRST_BEN_ID + index
            val villageId = VILLAGES[index % VILLAGES.size]
            val confirmed = db.benDao.getTbConfirmedPage(villageId, "", ALL_ROWS, 0).map { row -> row.ben.benId }
            val presumptive = db.benDao.getTbPresumptivePage(villageId, "", ALL_ROWS, 0).map { row -> row.ben.benId }
            assertTrue("$result: confirmed", benId in confirmed)
            assertTrue("$result: not presumptive", benId !in presumptive)
            assertEquals("$result: confirmed count", confirmed.size, db.benDao.getTbConfirmedListCount(villageId).first())
            assertEquals("$result: presumptive count", presumptive.size, db.benDao.getTbPresumptiveListCount(villageId).first())
        }
    }

    /** A birth before 1970 is a negative dob; an age search must still find it (dob 0 = unknown). */
    @Test
    fun ageSearchFindsABirthBefore1970() = runBlocking {
        val dob = java.util.Calendar.getInstance().apply { clear(); set(1960, java.util.Calendar.JANUARY, 1) }.timeInMillis
        val benId = FIRST_BEN_ID + 900
        db.benDao.upsert(
            BenRegCache(
                beneficiaryId = benId, isDeath = false, reasonOfDeathId = 0, placeOfDeathId = 0, ashaId = 1,
                isKid = false, isAdult = true,
                locationRecord = LocationRecord(
                    country = LocationEntity(1, "India"), state = LocationEntity(2, "State"),
                    district = LocationEntity(3, "District"), block = LocationEntity(4, "Block"),
                    village = LocationEntity(VILLAGES[0], "Village")
                ),
                syncState = SyncState.SYNCED, isDraft = false, gender = Gender.MALE, dob = dob,
                updatedDate = BASE, isDeactivate = false
            ).apply { firstName = "Elder"; lastName = "Person" }
        )
        val years = ((System.currentTimeMillis() - dob) / 1000 / 31_557_600L).toString()
        val found = db.benDao.getTbScreeningBenPage(VILLAGES[0], years, ALL_ROWS, 0).map { row -> row.ben.benId }
        assertTrue("age $years should find the 1960 birth", benId in found)
    }

    /** The screening list is every live beneficiary of the village, newest update first. */
    @Test
    fun screeningListIsTheWholeVillageNewestFirst() = runBlocking {
        for (villageId in VILLAGES) {
            val expected = QueryEquivalence.rows(db,
                "SELECT benId FROM BEN_BASIC_CACHE WHERE villageId = ? AND isDeath = 0 AND isDeactivate = 0 ORDER BY updatedDate DESC, benId DESC",
                arrayOf(villageId)).map { row -> row.single()!!.toLong() }
            assertEquals(expected, db.benDao.getTbScreeningBenPage(villageId, "", ALL_ROWS, 0).map { row -> row.ben.benId })
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
        const val ALL_ROWS = 100_000
        const val FIRST_BEN_ID = 610_000L
        const val BASE = 1_700_000_000_000L
        const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
        const val MILLIS_PER_YEAR = 365L * MILLIS_PER_DAY
        val VILLAGES = listOf(101, 102, 103)
        // Lowercased, as the screens pass them. None can match an age display string.
        val QUERIES = listOf("", "ram", "singh", "sunita devi", "gopal", "98765000", "6100", "female", "male", "4401", "rch")
    }
}
