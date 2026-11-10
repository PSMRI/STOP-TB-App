package org.piramalswasthya.stoptb.database.room.query

import android.content.Context
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
import org.piramalswasthya.stoptb.model.BenRegCache
import org.piramalswasthya.stoptb.model.Gender
import org.piramalswasthya.stoptb.model.GeneralOpdCache
import org.piramalswasthya.stoptb.model.HouseHoldBasicDomain
import org.piramalswasthya.stoptb.model.HouseholdCache
import org.piramalswasthya.stoptb.model.HouseholdFamily
import org.piramalswasthya.stoptb.model.LocationEntity
import org.piramalswasthya.stoptb.model.LocationRecord
import org.piramalswasthya.stoptb.model.ScreeningStatus
import org.piramalswasthya.stoptb.model.VitalCache
import org.piramalswasthya.stoptb.repositories.RowBadgeRepo

/**
 * PR 6: Unscreened and All Household page from SQL with the search (and, for households, the
 * sort) the screens used to run in Kotlin; the row badges are read for the loaded rows only.
 * Each is checked against the old whole-list result.
 */
@RunWith(AndroidJUnit4::class)
class PagedScreensEquivalenceTest {

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
    fun unscreenedPagedMatchesOldListAndFilter() = runBlocking {
        val screened = db.tbDao.getAllTbScreeningBenIds().first().toHashSet()
        var nonEmpty = 0
        for (villageId in VILLAGES) {
            // Old screen: the whole unscreened list, minus TB-screened, then the Kotlin search.
            val old = db.benDao.getUnscreenedList(villageId).first()
                .sortedWith(compareByDescending<org.piramalswasthya.stoptb.model.BenBasicCache> { row -> row.updatedDate ?: 0L }
                    .thenByDescending { row -> row.benId })
                .map { row -> row.asBasicDomainModel() }
                .filter { ben -> ben.benId !in screened }
            assertEquals(old.size, db.benDao.getUnscreenedNotTbScreenedCount(villageId).first())
            for (query in listOf("", "ram", "singh", "98765000", "6200", "priya kumar")) {
                val expected = old.filter { ben ->
                    query.isBlank() || ben.benId.toString().contains(query) ||
                        "${ben.benName} ${ben.benSurname}".lowercase().contains(query) ||
                        ben.mobileNo.contains(query)
                }.map { ben -> ben.benId }
                val paged = db.benDao.getUnscreenedPaged(villageId, query).loadAll().map { row -> row.benId }
                assertEquals("village=$villageId query='$query'", expected, paged)
                if (paged.isNotEmpty()) nonEmpty++
            }
        }
        assertTrue("searches should find unscreened rows, got $nonEmpty", nonEmpty >= 6)
    }

    @Test
    fun householdsPagedMatchOldListSearchAndSort() = runBlocking {
        for (villageId in VILLAGES) {
            val all = db.householdDao.getAllHouseholdWithNumMembers(villageId).first().map { row -> row.asBasicDomainModel() }
            for (query in listOf("", "gopal", "khan", "9123", "700")) {
                val expected = oldHouseholdFilter(all, query).map { hh -> hh.hhId to hh.numMembers }
                val paged = db.householdDao.getHouseholdsWithNumMembersPaged(villageId, query).loadAll()
                    .map { row -> row.asBasicDomainModel() }.map { hh -> hh.hhId to hh.numMembers }
                assertEquals("village=$villageId query='$query'", expected, paged)
            }
        }
    }

    @Test
    fun rowBadgesForLoadedRowsEqualWholeTableBadgesRestrictedToThem() = runBlocking {
        val repo = RowBadgeRepo(db.benDao, db.tbDao, db.vitalDao)
        val loaded = (0 until 120 step 3).map { index -> FIRST_BEN_ID + index }.toSet()
        val badges = repo.badgesFor(loaded).first()

        fun List<Long>.within() = filter { benId -> benId in loaded }.toSet()
        val vitals = db.vitalDao.getVitalBenIdSyncStates().first().filter { row -> row.benId in loaded }
        assertEquals(vitals.map { row -> row.benId }.toSet(), badges.vitalBenIds.toSet())
        assertEquals(vitals.filter { row -> row.syncState == SyncState.UNSYNCED }.map { row -> row.benId }.toSet(), badges.unsyncedVitalBenIds.toSet())
        assertEquals(db.tbDao.getAllTbScreeningBenIds().first().within(), badges.tbScreeningBenIds.toSet())
        assertEquals(db.tbDao.getAllGeneralOpdBenIds().first().within(), badges.generalOpdBenIds.toSet())
        assertEquals(db.benDao.getAnthropometryFilledBenIds().first().within(), badges.anthropometryBenIds.toSet())
        assertEquals(
            (db.tbDao.getAllTbDiagnosticsBenIds().first() + db.tbDao.getAllTbSuspectedBenIds().first()).within(),
            badges.diagnosisBenIds.toSet()
        )
        assertEquals(
            db.tbDao.getAllTbDiagnosticsCardFields().first().filter { row -> row.benId in loaded }.toSet(),
            badges.tbDiagnostics.toSet()
        )
        assertEquals(
            db.tbDao.getAllTbScreeningSymptoms().first().filter { row -> row.benId in loaded }.toSet(),
            badges.tbScreeningSymptoms.toSet()
        )
        assertTrue("seed should give every badge some rows",
            listOf(badges.vitalBenIds, badges.tbScreeningBenIds, badges.generalOpdBenIds, badges.anthropometryBenIds,
                badges.diagnosisBenIds).all { ids -> ids.isNotEmpty() })
        assertEquals(RowBadgeRepo.RowBadges(), repo.badgesFor(emptySet()).first())
    }

    /** AllHouseholdViewModel's old filter and sort, as they were. */
    private fun oldHouseholdFilter(list: List<HouseHoldBasicDomain>, filter: String): List<HouseHoldBasicDomain> {
        val filtered = if (filter.isBlank()) list else {
            val filterText = filter.lowercase()
            list.filter { hh ->
                hh.hhId.toString().contains(filterText) ||
                    hh.headFullName.lowercase().contains(filterText) ||
                    hh.contactNumber.contains(filterText)
            }
        }
        return filtered.sortedWith(compareBy<HouseHoldBasicDomain> { hh -> hh.isDeactivate }.thenByDescending { hh -> hh.createdTimeStamp })
    }

    private suspend fun <T : Any> PagingSource<Int, T>.loadAll(): List<T> =
        (load(PagingSource.LoadParams.Refresh(key = null, loadSize = 100_000, placeholdersEnabled = false))
            as PagingSource.LoadResult.Page).data

    private fun location(villageId: Int) = LocationRecord(
        country = LocationEntity(1, "India"), state = LocationEntity(2, "State"),
        district = LocationEntity(3, "District"), block = LocationEntity(4, "Block"),
        village = LocationEntity(villageId, "Village $villageId")
    )

    private suspend fun seed() {
        val lastNames = listOf("Singh", "Kumar", "Devi", "Patel", "Khan")
        for (index in 0 until 30) {
            val villageId = VILLAGES[index % VILLAGES.size]
            db.householdDao.upsert(
                HouseholdCache(
                    householdId = FIRST_HH_ID + index, ashaId = 1, locationRecord = location(villageId),
                    family = HouseholdFamily(
                        familyHeadName = if (index % 7 == 0) null else "Gopal",
                        familyName = lastNames[index % lastNames.size],
                        familyHeadPhoneNo = if (index % 5 == 0) null else 9_123_400_000L + index
                    ),
                    createdTimeStamp = if (index % 6 == 0) null else BASE + (index % 4) * 1_000L, // ties + nulls
                    processed = "P", isDraft = index == 29, isDeactivate = index % 8 == 0
                )
            )
        }
        val firstNames = listOf("Ramesh", "Sunita", "Arjun", "Priya", "Mohan", "Kavita")
        for (index in 0 until 150) {
            val villageId = VILLAGES[index % VILLAGES.size]
            db.benDao.upsert(
                BenRegCache(
                    beneficiaryId = FIRST_BEN_ID + index, isDeath = false, reasonOfDeathId = 0,
                    placeOfDeathId = 0, ashaId = 1, isKid = false, isAdult = true,
                    locationRecord = location(villageId),
                    syncState = SyncState.SYNCED, isDraft = false,
                    gender = if (index % 2 == 0) Gender.MALE else Gender.FEMALE,
                    dob = BASE - 30 * MILLIS_PER_YEAR,
                    updatedDate = BASE + (index % 40) * 1_000L,
                    isDeactivate = index % 19 == 0
                ).apply {
                    firstName = firstNames[index % firstNames.size]
                    lastName = lastNames[index % lastNames.size]
                    contactNumber = 9_876_500_000L + index
                    householdId = FIRST_HH_ID + index % 30
                    screeningStatus = if (index % 4 == 3) ScreeningStatus.SYMPTOMS_SCREENED else ScreeningStatus.UNSCREENED
                    height = if (index % 5 == 0) 160.0 else null
                }
            )
            val benId = FIRST_BEN_ID + index
            if (index % 3 == 0) db.vitalDao.saveVitals(
                VitalCache(benId = benId, benRegId = benId).apply {
                    syncState = if (index % 2 == 0) SyncState.UNSYNCED else SyncState.SYNCED
                }
            )
            if (index % 4 == 0) db.tbDao.insertGeneralOpd(GeneralOpdCache(benId = benId))
        }
        val sql = db.openHelper.writableDatabase
        for (index in 0 until 150) {
            val benId = FIRST_BEN_ID + index
            if (index % 2 == 0) sql.execSQL(
                "INSERT INTO TB_SCREENING (benId, visitDate, syncState, coughMoreThan2Weeks) VALUES ($benId, $BASE, ${index % 3}, ${index % 4 == 0})"
                    .replace("true", "1").replace("false", "0")
            )
            if (index % 5 == 0) sql.execSQL(
                "INSERT INTO TB_SUSPECTED (benId, visitDate, hasSymptoms, isConfirmed, syncState) VALUES ($benId, $BASE, 1, 0, 2)"
            )
            if (index % 7 == 0) sql.execSQL(
                "INSERT INTO TB_DIAGNOSTICS (benId, visitDate, isConfirmed, syncState, xrayOrderStatus) VALUES ($benId, $BASE, 0, 2, 'PENDING')"
            )
        }
    }

    private companion object {
        const val FIRST_BEN_ID = 620_000L
        const val FIRST_HH_ID = 700L
        const val BASE = 1_700_000_000_000L
        const val MILLIS_PER_YEAR = 365L * 24 * 60 * 60 * 1000
        val VILLAGES = listOf(101, 102, 103)
    }
}
