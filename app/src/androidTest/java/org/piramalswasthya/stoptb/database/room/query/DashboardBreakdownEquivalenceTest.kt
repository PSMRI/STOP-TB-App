package org.piramalswasthya.stoptb.database.room.query

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.Flow
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
import org.piramalswasthya.stoptb.database.room.dao.TBDao
import org.piramalswasthya.stoptb.model.BenRegCache
import org.piramalswasthya.stoptb.model.DashboardBreakdownCounts
import org.piramalswasthya.stoptb.model.Gender
import org.piramalswasthya.stoptb.model.LocationEntity
import org.piramalswasthya.stoptb.model.LocationRecord

/**
 * PR 5: each dashboard section's single breakdown query must give exactly the six numbers the
 * six separate COUNT queries gave (total, male, female, children, others, senior), for several
 * filter combinations, on data that exercises every section's conditions.
 */
@RunWith(AndroidJUnit4::class)
class DashboardBreakdownEquivalenceTest {

    private lateinit var db: InAppDb
    private lateinit var tbDao: TBDao

    /** old(gender, isChild, isSenior) per section, and the new breakdown, for one filter set. */
    private class Section(
        val name: String,
        val hasSenior: Boolean,
        val old: (gender: String, isChild: Int, isSenior: Int) -> Flow<Int>,
        val new: () -> Flow<DashboardBreakdownCounts>
    )

    private fun sections(ids: List<Int>, village: String, start: Long, end: Long) = listOf(
        Section("TbScreening", true, { g, c, s -> tbDao.getDashboardTbScreeningCount(ids, village, start, end, g, c, s) },
            { tbDao.getDashboardTbScreeningBreakdown(ids, village, start, end) }),
        Section("PresumptiveTb", false, { g, c, _ -> tbDao.getDashboardPresumptiveTbCount(ids, village, start, end, g, c) },
            { tbDao.getDashboardPresumptiveTbBreakdown(ids, village, start, end) }),
        Section("PastHistoryTb", true, { g, c, s -> tbDao.getDashboardPastHistoryTbCount(ids, village, start, end, g, c, s) },
            { tbDao.getDashboardPastHistoryTbBreakdown(ids, village, start, end) }),
        Section("AntiTbDrugs", true, { g, c, s -> tbDao.getDashboardAntiTbDrugsCount(ids, village, start, end, g, c, s) },
            { tbDao.getDashboardAntiTbDrugsBreakdown(ids, village, start, end) }),
        Section("TbSuspected", true, { g, c, s -> tbDao.getDashboardTbSuspectedCount(ids, village, start, end, g, c, s) },
            { tbDao.getDashboardTbSuspectedBreakdown(ids, village, start, end) }),
        Section("Nikshay", true, { g, c, s -> tbDao.getDashboardNikshayCount(ids, village, start, end, g, c, s) },
            { tbDao.getDashboardNikshayBreakdown(ids, village, start, end) }),
        Section("DigitalChestXRay", true, { g, c, s -> tbDao.getDashboardDigitalChestXRayCount(ids, village, start, end, g, c, s) },
            { tbDao.getDashboardDigitalChestXRayBreakdown(ids, village, start, end) }),
        Section("SputumCollection", true, { g, c, s -> tbDao.getDashboardSputumCollectionCount(ids, village, start, end, g, c, s) },
            { tbDao.getDashboardSputumCollectionBreakdown(ids, village, start, end) }),
        Section("TrueNat", true, { g, c, s -> tbDao.getDashboardTrueNatCount(ids, village, start, end, g, c, s) },
            { tbDao.getDashboardTrueNatBreakdown(ids, village, start, end) }),
        Section("LiquidCulture", true, { g, c, s -> tbDao.getDashboardLiquidCultureCount(ids, village, start, end, g, c, s) },
            { tbDao.getDashboardLiquidCultureBreakdown(ids, village, start, end) }),
        Section("HwcReferral", true, { g, c, s -> tbDao.getDashboardHwcReferralCount(ids, village, start, end, g, c, s) },
            { tbDao.getDashboardHwcReferralBreakdown(ids, village, start, end) }),
        Section("Unscreened", false, { g, c, _ -> tbDao.getDashboardUnscreenedCount(ids, village, start, end, g, c) },
            { tbDao.getDashboardUnscreenedBreakdown(ids, village, start, end) }),
    )

    @Before
    fun createDb() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, InAppDb::class.java).allowMainThreadQueries().build()
        tbDao = db.tbDao
        seed()
    }

    @After
    fun closeDb() = db.close()

    @Test
    fun everySectionMatchesItsSixCountQueries() = runBlocking {
        val filters = listOf(
            listOf(101, 102, 103) to ("" to (0L to 0L)),
            listOf(101, 102, 103) to ("" to (BASE + 50_000L to BASE + 250_000L)),
            listOf(101, 102, 103) to ("Village 102" to (0L to 0L)),
            listOf(103, 103) to ("" to (0L to 0L)),
        )
        var nonZeroSections = 0
        for ((ids, rest) in filters) {
            val (village, range) = rest
            for (section in sections(ids, village, range.first, range.second)) {
                val expected = DashboardBreakdownCounts(
                    total = section.old("", 0, 0).first(),
                    male = section.old("MALE", 0, 0).first(),
                    female = section.old("FEMALE", 0, 0).first(),
                    children = section.old("", 1, 0).first(),
                    others = section.old("OTHERS", 0, 0).first(),
                    seniorCitizen = if (section.hasSenior) section.old("", 0, 1).first() else 0
                )
                assertEquals("${section.name} ids=$ids village='$village' range=$range", expected, section.new().first())
                if (expected.total > 0) nonZeroSections++
            }
        }
        assertTrue("seed data should give most sections non-zero counts, got $nonZeroSections", nonZeroSections >= 25)
    }

    /** PR 5: home-card counts in SQL equal the size of the lists they used to load. */
    @Test
    fun homeCardCountsEqualListSizes() = runBlocking {
        var nonZero = 0
        for (villageId in listOf(101, 102, 103)) {
            val presumptive = db.benDao.getTbScreeningList(villageId).first()
            assertEquals(presumptive.size, db.benDao.getTbPresumptiveListCount(villageId).first())
            val updated = presumptive.map { row -> row.ben.updatedDate ?: 0L }
            assertEquals("presumptive list is newest first", updated.sortedDescending(), updated)
            val confirmed = db.benDao.getTbConfirmedList(villageId).first()
            assertEquals(confirmed.size, db.benDao.getTbConfirmedListCount(villageId).first())
            if (presumptive.isNotEmpty() && confirmed.isNotEmpty()) nonZero++
        }
        assertTrue("seed should give non-empty lists", nonZero > 0)
    }

    /**
     * Presumptive and Confirmed use one definition (TbCaseSql): with no filters, each dashboard
     * tile equals the sum of its list's counts over the villages, and the gender split adds up.
     */
    @Test
    fun dashboardTilesEqualListCounts() = runBlocking {
        val villages = listOf(101, 102, 103)
        val presumptiveListTotal = villages.sumOf { villageId -> db.benDao.getTbPresumptiveListCount(villageId).first() }
        val confirmedListTotal = villages.sumOf { villageId -> db.benDao.getTbConfirmedListCount(villageId).first() }
        val presumptiveTile = tbDao.getDashboardPresumptiveTbBreakdown(villages, "", 0L, 0L).first()
        assertEquals(presumptiveListTotal, presumptiveTile.total)
        assertEquals(presumptiveListTotal, tbDao.getDashboardPresumptiveTbCount(villages, "", 0L, 0L, "", 0).first())
        assertEquals(presumptiveTile.total, presumptiveTile.male + presumptiveTile.female + presumptiveTile.others)
        assertEquals(confirmedListTotal, db.benDao.getDashboardFilteredTbConfirmedCount(villages, 0L, 0L, "", 0, 0).first())
        assertTrue("seed should give presumptive and confirmed rows", presumptiveListTotal > 0 && confirmedListTotal > 0)
    }

    private suspend fun seed() {
        for (index in 0 until 240) {
            val villageId = listOf(101, 102, 103)[index % 3]
            val ageYears = 3 + (index * 7) % 80
            db.benDao.upsert(
                BenRegCache(
                    beneficiaryId = FIRST_BEN_ID + index, isDeath = index % 13 == 0, reasonOfDeathId = 0,
                    placeOfDeathId = 0, ashaId = 1, isKid = ageYears < 15, isAdult = ageYears >= 15,
                    locationRecord = LocationRecord(
                        country = LocationEntity(1, "India"), state = LocationEntity(2, "State"),
                        district = LocationEntity(3, "District"), block = LocationEntity(4, "Block"),
                        village = LocationEntity(villageId, "Village $villageId")
                    ),
                    syncState = SyncState.SYNCED, isDraft = false,
                    gender = when (index % 5) { 0, 2 -> Gender.MALE; 1, 3 -> Gender.FEMALE; else -> Gender.TRANSGENDER },
                    dob = System.currentTimeMillis() - ageYears * MILLIS_PER_YEAR,
                    updatedDate = BASE + index * 1_000L,
                    isDeactivate = index % 29 == 0
                )
            )
        }
        val sql = db.openHelper.writableDatabase
        val results = listOf("POSITIVE", "TB Presumptive", "Normal", "MTB DETECTED", "TB Positive", "Negative",
            "Abnormal but not TB Presumptive", null)
        for (index in 0 until 240) {
            val benId = FIRST_BEN_ID + index
            val visit = BASE + index * 1_000L
            fun b(n: Int) = if (index % n == 0) 1 else 0
            fun r(shift: Int) = results[(index + shift) % results.size]?.let { "'$it'" } ?: "NULL"
            if (index % 2 == 0) sql.execSQL(
                """INSERT INTO TB_SCREENING (benId, visitDate, syncState, coughMoreThan2Weeks, bloodInSputum, feverMoreThan2Weeks,
                   lossOfWeight, nightSweats, riseOfFever, lossOfAppetite, familySufferingFromTB, contactWithTBPatient,
                   historyOfTBInLastFiveYrs, historyOfTb, takingAntiTBDrugs)
                   VALUES ($benId, $visit, 2, ${b(3)}, ${b(5)}, ${b(7)}, ${b(4)}, ${b(9)}, ${b(11)}, ${b(6)}, ${b(8)}, ${b(10)},
                   ${b(12)}, ${b(14)}, ${b(4)})"""
            )
            if (index % 3 == 0) sql.execSQL(
                """INSERT INTO TB_SUSPECTED (benId, visitDate, hasSymptoms, isConfirmed, syncState, isTBConfirmed, chestXRayResult,
                   nikshayId, isChestXRayDone, isSputumCollected, isNaatConducted, isLiquidCultureConducted)
                   VALUES ($benId, $visit, ${b(2)}, ${b(4)}, 2, ${b(5)}, ${r(0)}, ${if (index % 6 == 0) "'NK$index'" else "NULL"},
                   ${b(2)}, ${b(4)}, ${b(5)}, ${b(7)})"""
            )
            if (index % 4 == 0) sql.execSQL(
                """INSERT INTO TB_DIAGNOSTICS (benId, visitDate, isConfirmed, syncState, isTBConfirmed, chestXRayResult, isChestXRayDone,
                   isReferredForSputum, reasonForDenialSputum, isNaatConducted, naatResult, isLiquidCultureConducted,
                   liquidCultureResult, trueNatOrderStatus, xrayOrderStatus)
                   VALUES ($benId, $visit, ${b(8)}, 2, ${b(12)}, ${r(1)}, ${b(2)}, ${b(3)}, ${if (index % 5 == 0) "'Refused'" else "NULL"},
                   ${b(2)}, ${r(2)}, ${b(3)}, ${r(3)}, ${if (index % 3 == 0) "'COMPLETED'" else "'PENDING'"},
                   ${if (index % 2 == 0) "'COMPLETED'" else "NULL"})"""
            )
            if (index % 11 == 0) sql.execSQL(
                """INSERT INTO TB_CONFIRMED_TREATMENT (benId, treatmentStartDate, reasonForDeath, syncState, createdAt, updatedAt)
                   VALUES ($benId, $visit, '', 2, $visit, $visit)"""
            )
        }
    }

    private companion object {
        const val FIRST_BEN_ID = 700_000L
        const val BASE = 1_700_000_000_000L
        const val MILLIS_PER_YEAR = 365L * 24 * 60 * 60 * 1000
    }
}
