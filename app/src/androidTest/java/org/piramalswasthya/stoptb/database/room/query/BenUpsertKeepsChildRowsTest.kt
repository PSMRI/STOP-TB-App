package org.piramalswasthya.stoptb.database.room.query

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.piramalswasthya.stoptb.database.room.InAppDb
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.model.BenRegCache
import org.piramalswasthya.stoptb.model.Gender
import org.piramalswasthya.stoptb.model.LocationEntity
import org.piramalswasthya.stoptb.model.LocationRecord
import org.piramalswasthya.stoptb.model.VitalCache

/**
 * PR 7.1: saving an existing beneficiary again (BenDao.upsert, e.g. editing a child) updates the
 * row in place. With @Insert(REPLACE) it was DELETE + INSERT and every ON DELETE CASCADE child
 * row (TB screening, vitals, ...) of that beneficiary disappeared.
 */
@RunWith(AndroidJUnit4::class)
class BenUpsertKeepsChildRowsTest {

    private lateinit var db: InAppDb

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, InAppDb::class.java).allowMainThreadQueries().build()
    }

    @After
    fun closeDb() = db.close()

    private fun ben(benId: Long, firstName: String) = BenRegCache(
        beneficiaryId = benId, isDeath = false, reasonOfDeathId = 0, placeOfDeathId = 0, ashaId = 1,
        isKid = true, isAdult = false,
        locationRecord = LocationRecord(
            country = LocationEntity(1, "India"), state = LocationEntity(2, "State"),
            district = LocationEntity(3, "District"), block = LocationEntity(4, "Block"),
            village = LocationEntity(101, "Village")
        ),
        syncState = SyncState.UNSYNCED, isDraft = false, gender = Gender.MALE
    ).apply { this.firstName = firstName }

    @Test
    fun upsertOfAnExistingBeneficiaryKeepsItsChildRowsAndUpdatesTheRow() = runBlocking {
        db.benDao.upsert(ben(BEN_ID, "Before"))
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO TB_SCREENING (benId, visitDate, syncState) VALUES ($BEN_ID, 1700000000000, 0)"
        )
        db.vitalDao.saveVitals(VitalCache(benId = BEN_ID, benRegId = 0L))

        db.benDao.upsert(ben(BEN_ID, "After"))

        assertEquals("After", db.benDao.getBen(BEN_ID)?.firstName)
        assertEquals(listOf(listOf("1")), QueryEquivalence.rows(db, "SELECT COUNT(*) FROM TB_SCREENING WHERE benId = ?", arrayOf(BEN_ID)))
        assertNotNull("vitals must survive the re-save", db.vitalDao.getVitals(BEN_ID))
    }

    @Test
    fun upsertOfANewBeneficiaryInsertsIt() = runBlocking {
        db.benDao.upsert(ben(BEN_ID + 1, "New"), ben(BEN_ID + 2, "Other"))
        assertEquals("New", db.benDao.getBen(BEN_ID + 1)?.firstName)
        assertEquals("Other", db.benDao.getBen(BEN_ID + 2)?.firstName)
    }

    private companion object {
        const val BEN_ID = 660_000L
    }
}
