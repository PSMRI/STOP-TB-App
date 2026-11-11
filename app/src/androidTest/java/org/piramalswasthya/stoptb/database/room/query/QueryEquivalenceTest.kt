package org.piramalswasthya.stoptb.database.room.query

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.piramalswasthya.stoptb.database.room.InAppDb
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.model.BenRegCache
import org.piramalswasthya.stoptb.model.Gender
import org.piramalswasthya.stoptb.model.LocationEntity
import org.piramalswasthya.stoptb.model.LocationRecord
import org.piramalswasthya.stoptb.model.TBDiagnosticsCache

/**
 * Side-by-side checks for rewritten queries (see [QueryEquivalence]).
 *
 * The seeded data spans several villages, deactivated and draft rows and mixed sync states,
 * so village filters, the view's isDraft filter and sync-state predicates are all exercised.
 * Each optimisation PR adds a test here comparing its new SQL with the SQL it replaces.
 */
@RunWith(AndroidJUnit4::class)
class QueryEquivalenceTest {

    private lateinit var db: InAppDb

    @Before
    fun createDb() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, InAppDb::class.java)
            .allowMainThreadQueries()
            .build()
        seedBeneficiaries()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun seededDataIsVisibleThroughTheView() {
        val nonDraft = SEED_COUNT - SEED_COUNT / DRAFT_EVERY
        assertEquals(
            listOf(listOf(nonDraft.toString())),
            QueryEquivalence.rows(db, "SELECT COUNT(*) FROM BEN_BASIC_CACHE")
        )
    }

    /** Baseline for PR 5: the view count per village equals the same count on the base table. */
    @Test
    fun villageCountOnViewEqualsCountOnBaseTable() {
        for (villageId in VILLAGES) {
            QueryEquivalence.assertSameRowsAnyOrder(
                db,
                originalSql = "SELECT COUNT(*) FROM BEN_BASIC_CACHE WHERE villageId = ? AND isDeactivate = 0",
                rewrittenSql = "SELECT COUNT(*) FROM BENEFICIARY WHERE isDraft = 0 AND loc_village_id = ? AND isDeactivate = 0",
                args = arrayOf(villageId)
            )
        }
    }

    /**
     * The poll's yes/no queries give the same answer as the Kotlin filters they replace: any
     * PENDING / manual-result row, and an order of an integrated device that is PENDING with a
     * real order id (not null, blank or "N/A").
     */
    @Test
    fun pendingYesNoQueriesMatchKotlinFilters() = runBlocking {
        assertEquals(false, db.tbDao.hasDiagnosticsWithPendingOrder())
        assertEquals(false, db.tbDao.hasDiagnosticsWithPendingManualResult())
        assertEquals(false, db.tbDao.hasPollableOrder(xray = true, truenat = true))

        fun hasRealOrderId(id: String?) = !id.isNullOrBlank() && !id.equals("N/A", ignoreCase = true)
        fun kotlinPollable(rows: List<TBDiagnosticsCache>, xray: Boolean, truenat: Boolean) = rows.any { row ->
            (xray && row.xrayOrderStatus.equals("PENDING", true) && hasRealOrderId(row.xrayOrderId)) ||
                (truenat && row.trueNatOrderStatus.equals("PENDING", true) && hasRealOrderId(row.trueNatOrderId)) ||
                (truenat && row.rifOrderStatus.equals("PENDING", true) && hasRealOrderId(row.rifOrderId))
        }
        val flags = listOf(false to false, true to false, false to true, true to true)
        val orderIds = listOf(null, "", "  ", "\t", "N/A", "n/a", "ORD-1")
        var benId = FIRST_BEN_ID
        // Only TB_DIAGNOSTICS is cleared: its rows reference the seeded beneficiaries.
        fun clearDiagnostics() = db.openHelper.writableDatabase.execSQL("DELETE FROM TB_DIAGNOSTICS")
        // One row at a time, so each order id is checked on its own for each column.
        for (column in 0..2) for (status in listOf("pending", "COMPLETED")) for (orderId in orderIds) {
            clearDiagnostics()
            db.tbDao.saveTbDiagnostics(
                when (column) {
                    0 -> TBDiagnosticsCache(benId = benId++, xrayOrderStatus = status, xrayOrderId = orderId)
                    1 -> TBDiagnosticsCache(benId = benId++, trueNatOrderStatus = status, trueNatOrderId = orderId)
                    else -> TBDiagnosticsCache(benId = benId++, rifOrderStatus = status, rifOrderId = orderId)
                }
            )
            val rows = db.tbDao.getDiagnosticsList()
            for ((xray, truenat) in flags) {
                assertEquals(
                    "column=$column status=$status orderId=$orderId xray=$xray truenat=$truenat",
                    kotlinPollable(rows, xray, truenat), db.tbDao.hasPollableOrder(xray, truenat)
                )
            }
            assertEquals(db.tbDao.getDiagnosticsWithPendingOrder().isNotEmpty(), db.tbDao.hasDiagnosticsWithPendingOrder())
        }

        clearDiagnostics()
        db.tbDao.saveTbDiagnostics(TBDiagnosticsCache(benId = benId++, trueNatManualResultPendingSync = true))
        assertEquals(true, db.tbDao.hasDiagnosticsWithPendingManualResult())
        assertEquals(false, db.tbDao.hasDiagnosticsWithPendingOrder())
    }

    /** PR 4: the poll's pending-order query returns exactly the rows the old Kotlin filter kept. */
    @Test
    fun pendingOrderAndManualResultQueriesMatchKotlinFilters() = runBlocking {
        val statuses = listOf(null, "PENDING", "pending", "Pending", "COMPLETED", "FAILED", "")
        var benId = FIRST_BEN_ID
        for (xray in statuses) for (trueNat in listOf(null, "PENDING", "COMPLETED")) for (rif in listOf(null, "pending")) {
            db.tbDao.saveTbDiagnostics(
                TBDiagnosticsCache(
                    benId = benId++,
                    xrayOrderStatus = xray, trueNatOrderStatus = trueNat, rifOrderStatus = rif,
                    xrayManualResultPendingSync = (benId % 5 == 0L).takeIf { benId % 3 != 0L },
                    rifManualResultPendingSync = benId % 7 == 0L
                )
            )
        }
        val all = db.tbDao.getDiagnosticsList()
        val expectedPending = all.filter { row ->
            listOf(row.xrayOrderStatus, row.trueNatOrderStatus, row.rifOrderStatus)
                .any { status -> status.equals("PENDING", ignoreCase = true) }
        }.map { row -> row.id }.sorted()
        assertEquals(expectedPending, db.tbDao.getDiagnosticsWithPendingOrder().map { row -> row.id }.sorted())

        val expectedManual = all.filter { row ->
            row.xrayManualResultPendingSync == true || row.trueNatManualResultPendingSync == true ||
                row.rifManualResultPendingSync == true
        }.map { row -> row.id }.sorted()
        assertEquals(expectedManual, db.tbDao.getDiagnosticsWithPendingManualResult().map { row -> row.id }.sorted())

        val someBenIds = all.map { row -> row.benId }.filterIndexed { index, _ -> index % 4 == 0 }
        assertEquals(
            all.filter { row -> row.benId in someBenIds }.associateBy { row -> row.benId },
            db.tbDao.getDiagnosticsForBenIds(someBenIds).associateBy { row -> row.benId }
        )
    }

    /**
     * PR 5: BEN_BASIC_CACHE lost its ORDER BY; list queries order explicitly. Rebuild the old
     * view (current view SQL + the old ORDER BY) as a temp view and check the DAO lists come
     * back in exactly the order the old view gave them.
     */
    @Test
    fun listOrderUnchangedAfterViewLostItsOrderBy() = runBlocking {
        val viewSql = QueryEquivalence.rows(db, "SELECT sql FROM sqlite_master WHERE type = 'view' AND name = 'BEN_BASIC_CACHE'")
            .single().single()!!
        db.openHelper.writableDatabase.execSQL(
            viewSql.replace("CREATE VIEW `BEN_BASIC_CACHE`", "CREATE TEMP VIEW OLD_BEN_BASIC_CACHE") +
                " ORDER BY b.updatedDate DESC"
        )
        for (villageId in VILLAGES) {
            val oldAllBen = QueryEquivalence.rows(db,
                "SELECT benId FROM OLD_BEN_BASIC_CACHE where villageId = ? AND isDeactivate = 0", arrayOf(villageId))
                .map { row -> row.single()!!.toLong() }
            assertEquals(oldAllBen, db.benDao.getAllBen(villageId).first().map { ben -> ben.benId })

            val oldUnscreened = QueryEquivalence.rows(db,
                "SELECT benId FROM OLD_BEN_BASIC_CACHE WHERE villageId = ? AND isDeactivate = 0 AND screeningStatus = 'UNSCREENED'",
                arrayOf(villageId)).map { row -> row.single()!!.toLong() }
            assertEquals(oldUnscreened, db.benDao.getUnscreenedList(villageId).first().map { ben -> ben.benId })
        }
    }

    private suspend fun seedBeneficiaries() {
        for (index in 0 until SEED_COUNT) {
            val villageId = VILLAGES[index % VILLAGES.size]
            val ageYears = 5 + index % 70
            db.benDao.upsert(
                BenRegCache(
                    beneficiaryId = FIRST_BEN_ID + index,
                    isDeath = false,
                    reasonOfDeathId = 0,
                    placeOfDeathId = 0,
                    ashaId = 1,
                    isKid = ageYears < 15,
                    isAdult = ageYears >= 15,
                    locationRecord = LocationRecord(
                        country = LocationEntity(1, "India"),
                        state = LocationEntity(2, "State"),
                        district = LocationEntity(3, "District"),
                        block = LocationEntity(4, "Block"),
                        village = LocationEntity(villageId, "Village $villageId")
                    ),
                    syncState = if (index % 4 == 0) SyncState.UNSYNCED else SyncState.SYNCED,
                    isDraft = index % DRAFT_EVERY == 0,
                    gender = if (index % 2 == 0) Gender.MALE else Gender.FEMALE,
                    dob = System.currentTimeMillis() - ageYears * MILLIS_PER_YEAR,
                    updatedDate = BASE_UPDATED_DATE + index * 1_000L,
                    isDeactivate = index % 17 == 0
                )
            )
        }
    }

    private companion object {
        const val SEED_COUNT = 300
        const val DRAFT_EVERY = 25
        const val FIRST_BEN_ID = 900_000L
        const val BASE_UPDATED_DATE = 1_700_000_000_000L
        const val MILLIS_PER_YEAR = 365L * 24 * 60 * 60 * 1000
        val VILLAGES = listOf(101, 102, 103)
    }
}
