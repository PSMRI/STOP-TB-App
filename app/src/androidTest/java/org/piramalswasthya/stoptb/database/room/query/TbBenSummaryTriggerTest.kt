package org.piramalswasthya.stoptb.database.room.query

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.piramalswasthya.stoptb.database.room.InAppDb
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.database.room.TbBenSummarySql
import org.piramalswasthya.stoptb.model.BenRegCache
import org.piramalswasthya.stoptb.model.Gender
import org.piramalswasthya.stoptb.model.LocationEntity
import org.piramalswasthya.stoptb.model.LocationRecord

/**
 * After every kind of write to the TB tables, TB_BEN_SUMMARY must hold exactly what a full
 * recompute ([TbBenSummarySql.FILL]) produces: the triggers may not miss a column or an event.
 */
@RunWith(AndroidJUnit4::class)
class TbBenSummaryTriggerTest {

    private lateinit var db: InAppDb

    @Before
    fun createDb() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, InAppDb::class.java)
            .addCallback(TbBenSummarySql.CALLBACK).allowMainThreadQueries().build()
        for (index in 0 until BENS) {
            db.benDao.upsert(
                BenRegCache(
                    beneficiaryId = FIRST + index, isDeath = false, reasonOfDeathId = 0, placeOfDeathId = 0,
                    ashaId = 1, isKid = false, isAdult = true,
                    locationRecord = LocationRecord(
                        country = LocationEntity(1, "India"), state = LocationEntity(2, "State"),
                        district = LocationEntity(3, "District"), block = LocationEntity(4, "Block"),
                        village = LocationEntity(101, "Village 101")
                    ),
                    syncState = SyncState.SYNCED, isDraft = false, gender = Gender.FEMALE,
                    dob = BASE - 30L * 365 * 24 * 3600 * 1000, updatedDate = BASE, isDeactivate = false
                ).apply { firstName = "Ben$index" }
            )
        }
    }

    @After
    fun closeDb() = db.close()

    private val sql get() = db.openHelper.writableDatabase

    /** Summary rows of beneficiaries that still have source rows, as strings. */
    private fun summary(): List<List<String?>> = QueryEquivalence.rows(
        db,
        "SELECT * FROM TB_BEN_SUMMARY WHERE beneficiaryId IN (" +
            "SELECT benId FROM TB_SCREENING UNION SELECT benId FROM TB_SUSPECTED UNION SELECT benId FROM TB_DIAGNOSTICS" +
            " UNION SELECT benId FROM BEN_VITALS UNION SELECT benId FROM TB_CONFIRMED_TREATMENT) ORDER BY beneficiaryId"
    )

    private fun assertMatchesRecompute(step: String) {
        val kept = summary()
        sql.beginTransaction()
        try {
            sql.execSQL("DELETE FROM TB_BEN_SUMMARY")
            sql.execSQL(TbBenSummarySql.FILL)
            assertEquals("after $step", summary(), kept)
        } finally {
            sql.endTransaction() // roll back the recompute
        }
    }

    @Test
    fun triggersKeepTheSummaryEqualToAFullRecompute() {
        val b = { i: Int -> FIRST + i }
        sql.execSQL("INSERT INTO TB_SCREENING (benId, visitDate, syncState, coughMoreThan2Weeks) VALUES (${b(0)}, $BASE, 2, 1)")
        sql.execSQL("INSERT INTO TB_SCREENING (benId, visitDate, syncState, historyOfTb, keyPopulationRiskFactors) VALUES (${b(1)}, ${BASE + 1}, 2, 1, 'PREGNANCY')")
        sql.execSQL("INSERT INTO TB_SCREENING (benId, visitDate, syncState, asymptomatic) VALUES (${b(2)}, ${BASE + 2}, 2, 'No')")
        assertMatchesRecompute("screening inserts")

        sql.execSQL("INSERT INTO TB_SUSPECTED (benId, visitDate, syncState, hasSymptoms, isConfirmed, isChestXRayDone, chestXRayResult, naatResult) VALUES (${b(0)}, ${BASE + 5}, 2, 0, 0, 1, 'TB Presumptive', 'MTB Not Detected')")
        sql.execSQL("INSERT INTO TB_SUSPECTED (benId, visitDate, syncState, hasSymptoms, isConfirmed, isTBConfirmed) VALUES (${b(3)}, ${BASE + 6}, 2, 1, 1, 1)")
        assertMatchesRecompute("suspected inserts")

        sql.execSQL("INSERT INTO TB_DIAGNOSTICS (benId, visitDate, syncState, isConfirmed, xrayOrderStatus, xrayOrderId, trueNatOrderStatus) VALUES (${b(0)}, ${BASE + 7}, 2, 0, 'FAILED', 11, 'PENDING')")
        sql.execSQL("INSERT INTO TB_DIAGNOSTICS (benId, visitDate, syncState, isConfirmed, xrayOrderId) VALUES (${b(2)}, ${BASE + 8}, 2, 0, 12)")
        sql.execSQL("INSERT INTO TB_DIAGNOSTICS (benId, visitDate, syncState, isConfirmed, naatResult, isLiquidCultureConducted) VALUES (${b(4)}, ${BASE + 9}, 2, 0, 'MTB Detected', 1)")
        assertMatchesRecompute("diagnostics inserts")

        sql.execSQL("INSERT INTO BEN_VITALS (benId, benRegId, capturedAt, syncState, keyPopulationRiskFactors, pulseRate) VALUES (${b(5)}, 1, $BASE, 2, 'PREGNANCY', 120)")
        sql.execSQL("INSERT INTO TB_CONFIRMED_TREATMENT (benId, treatmentStartDate, reasonForDeath, syncState, createdAt, updatedAt) VALUES (${b(0)}, $BASE, '', 2, $BASE, $BASE)")
        assertMatchesRecompute("vitals and treatment inserts")

        sql.execSQL("UPDATE TB_DIAGNOSTICS SET xrayOrderStatus = 'COMPLETED', chestXRayResult = 'Abnormal' WHERE benId = ${b(0)}")
        sql.execSQL("UPDATE TB_SCREENING SET coughMoreThan2Weeks = 0, nightSweats = 1, visitDate = ${BASE + 20} WHERE benId = ${b(0)}")
        sql.execSQL("UPDATE TB_SUSPECTED SET naatResult = 'MTB Detected' WHERE benId = ${b(0)}")
        sql.execSQL("UPDATE BEN_VITALS SET keyPopulationRiskFactors = NULL WHERE benId = ${b(5)}")
        assertMatchesRecompute("field updates")

        sql.execSQL("UPDATE TB_SCREENING SET benId = ${b(6)} WHERE benId = ${b(2)}")
        assertMatchesRecompute("row moved to another beneficiary")

        sql.execSQL("UPDATE TB_SCREENING SET syncState = 0")
        assertMatchesRecompute("sync-state-only update")

        sql.execSQL("DELETE FROM TB_CONFIRMED_TREATMENT WHERE benId = ${b(0)}")
        sql.execSQL("DELETE FROM TB_DIAGNOSTICS WHERE benId = ${b(4)}")
        sql.execSQL("DELETE FROM TB_SUSPECTED WHERE benId = ${b(3)}")
        assertMatchesRecompute("deletes")

        // BENEFICIARY: list sort key and the HWC temperature rule
        sql.execSQL("UPDATE BENEFICIARY SET createdDate = ${BASE + 99} WHERE beneficiaryId = ${b(0)}")
        sql.execSQL("UPDATE BENEFICIARY SET temperature = 101.5 WHERE beneficiaryId = ${b(7)}")
        sql.execSQL("UPDATE BENEFICIARY SET temperature = 98.0 WHERE beneficiaryId = ${b(7)}")
        sql.execSQL("UPDATE BENEFICIARY SET temperature = 102.0 WHERE beneficiaryId = ${b(6)}")
        assertMatchesRecompute("beneficiary date and temperature updates")

        assertTrue("summary is empty", summary().isNotEmpty())
    }

    /** Search index rows, as strings. */
    private fun searchIndex(): List<List<String?>> =
        QueryEquivalence.rows(db, "SELECT rowid, name, phone, phoneend, abha, head, spouse, father, benId, hhId, rch FROM BEN_SEARCH_FTS ORDER BY rowid")

    private fun assertSearchIndexMatchesRebuild(step: String) {
        val kept = searchIndex()
        sql.beginTransaction()
        try {
            sql.execSQL("DELETE FROM BEN_SEARCH_FTS")
            sql.execSQL(org.piramalswasthya.stoptb.database.room.BenSearchSql.FILL)
            assertEquals("after $step", searchIndex(), kept)
        } finally {
            sql.endTransaction()
        }
    }

    @Test
    fun searchIndexFollowsBeneficiaryAndHouseholdChanges() {
        assertSearchIndexMatchesRebuild("beneficiary inserts")
        sql.execSQL("UPDATE BENEFICIARY SET firstName = 'Ramesh', lastName = 'Kumar', contactNumber = 9876500012 WHERE beneficiaryId = ${FIRST + 1}")
        sql.execSQL("UPDATE BENEFICIARY SET gen_spouseName = 'Sunita', fatherName = 'Gopal', rchId = 'RCH77' WHERE beneficiaryId = ${FIRST + 2}")
        sql.execSQL("UPDATE BENEFICIARY SET syncState = 0")
        assertSearchIndexMatchesRebuild("beneficiary updates")
        sql.execSQL("DELETE FROM BENEFICIARY WHERE beneficiaryId = ${FIRST + 3}")
        assertSearchIndexMatchesRebuild("beneficiary delete")
        val found = QueryEquivalence.rows(db, "SELECT rowid FROM BEN_SEARCH_FTS WHERE BEN_SEARCH_FTS MATCH ?",
            arrayOf(org.piramalswasthya.stoptb.database.room.BenSearchSql.toFtsQuery("rame kum")))
        assertEquals(listOf(listOf((FIRST + 1).toString())), found)
        val byPhoneEnd = QueryEquivalence.rows(db, "SELECT rowid FROM BEN_SEARCH_FTS WHERE BEN_SEARCH_FTS MATCH ?",
            arrayOf(org.piramalswasthya.stoptb.database.room.BenSearchSql.toFtsQuery("00012")))
        assertEquals(listOf(listOf((FIRST + 1).toString())), byPhoneEnd)
    }

    private companion object {
        const val BENS = 8
        const val FIRST = 720_000L
        const val BASE = 1_700_000_000_000L
    }
}
