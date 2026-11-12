package org.piramalswasthya.stoptb.repositories.tb

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.piramalswasthya.stoptb.model.TBSuspectedCache

/**
 * A form holds a diagnostics snapshot with the X-ray order PENDING. Meanwhile the result for the
 * SAME order arrives (changes feed, poll). Saving the form must keep that result and status,
 * and still apply the form's own edits.
 */
@RunWith(AndroidJUnit4::class)
class StaleDiagnosticsSnapshotTest {

    private lateinit var db: InAppDb
    private lateinit var repo: TbLocalRepo

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, InAppDb::class.java).build()
        repo = TbLocalRepo(db.tbDao, db.benDao, BenIdLocks())
        db.benDao.upsert(
            BenRegCache(
                beneficiaryId = BEN_ID, isDeath = false, reasonOfDeathId = 0, placeOfDeathId = 0, ashaId = 1,
                isKid = false, isAdult = true,
                locationRecord = LocationRecord(
                    country = LocationEntity(1, "India"), state = LocationEntity(2, "State"),
                    district = LocationEntity(3, "District"), block = LocationEntity(4, "Block"),
                    village = LocationEntity(101, "Village")
                ),
                syncState = SyncState.SYNCED, isDraft = false, gender = Gender.MALE,
                dob = 0L, updatedDate = 1_700_000_000_000L, isDeactivate = false
            )
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun aResultThatArrivedWhileTheFormWasOpenIsKept() = runBlocking {
        db.tbDao.saveTbDiagnostics(TBDiagnosticsCache(benId = BEN_ID, xrayOrderId = "X1", xrayOrderStatus = "PENDING"))
        val snapshot = db.tbDao.getTbDiagnosticsByBenId(BEN_ID)!! // the form opens

        val fresh = db.tbDao.getTbDiagnosticsByBenId(BEN_ID)!!
        db.tbDao.saveTbDiagnostics(fresh.copy(xrayOrderStatus = "COMPLETED", chestXRayResult = "TB Presumptive"))

        repo.saveTBDiagnostics(snapshot.copy(isNaatConducted = true)) // the form saves an unrelated edit

        val saved = db.tbDao.getTbDiagnosticsByBenId(BEN_ID)!!
        assertEquals("COMPLETED", saved.xrayOrderStatus)
        assertEquals("TB Presumptive", saved.chestXRayResult)
        assertEquals(true, saved.isNaatConducted)
    }

    @Test
    fun theFormsOwnFinalStatusStillWins() = runBlocking {
        db.tbDao.saveTbDiagnostics(TBDiagnosticsCache(benId = BEN_ID, xrayOrderId = "X1", xrayOrderStatus = "PENDING"))
        val snapshot = db.tbDao.getTbDiagnosticsByBenId(BEN_ID)!!

        repo.saveTBDiagnostics(snapshot.copy(xrayOrderStatus = "CLOSED", chestXRayResult = null)) // Not Conducted

        assertEquals("CLOSED", db.tbDao.getTbDiagnosticsByBenId(BEN_ID)!!.xrayOrderStatus)
    }

    @Test
    fun anOfflineResultsPendingFlagSurvivesAStaleFormSave() = runBlocking {
        db.tbDao.saveTbDiagnostics(TBDiagnosticsCache(benId = BEN_ID, xrayOrderId = "X1", xrayOrderStatus = "PENDING"))
        val snapshot = db.tbDao.getTbDiagnosticsByBenId(BEN_ID)!! // the form opens

        // A result entered offline is queued meanwhile (ManualResultRepo writes it directly).
        val fresh = db.tbDao.getTbDiagnosticsByBenId(BEN_ID)!!
        db.tbDao.saveTbDiagnostics(fresh.copy(xrayOrderStatus = "COMPLETED", chestXRayResult = "Normal", xrayManualResultPendingSync = true))

        repo.saveTBDiagnostics(snapshot.copy(isNaatConducted = true))

        val saved = db.tbDao.getTbDiagnosticsByBenId(BEN_ID)!!
        assertEquals(true, saved.xrayManualResultPendingSync)
        assertEquals(listOf(BEN_ID), db.tbDao.getDiagnosticsWithPendingManualResult().map { row -> row.benId })
    }

    @Test
    fun confirmingKeepsWhatASyncWroteAfterTheScreenLoadedItsCopy() = runBlocking {
        db.tbDao.saveTbSuspected(TBSuspectedCache(benId = BEN_ID))
        db.tbDao.getTbSuspected(BEN_ID)!! // the confirmed-treatment screen loads its copy

        // A diagnostics sync updates the suspected record in between.
        val current = db.tbDao.getTbSuspected(BEN_ID)!!
        db.tbDao.saveTbSuspected(current.copy(chestXRayResult = "TB Presumptive"))

        assertEquals(true, repo.markTbSuspectedConfirmed(BEN_ID, createIfMissing = false))

        val saved = db.tbDao.getTbSuspected(BEN_ID)!!
        assertEquals("TB Presumptive", saved.chestXRayResult)
        assertEquals(true, saved.isConfirmed)
        assertEquals(true, saved.isTBConfirmed)
    }

    @Test
    fun confirmingWithNoRecordCreatesOneOnlyWhenAsked() = runBlocking {
        assertEquals(false, repo.markTbSuspectedConfirmed(BEN_ID, createIfMissing = false))
        assertEquals(null, db.tbDao.getTbSuspected(BEN_ID))
        assertEquals(true, repo.markTbSuspectedConfirmed(BEN_ID, createIfMissing = true))
        assertEquals(true, db.tbDao.getTbSuspected(BEN_ID)!!.isConfirmed)
    }

    private companion object {
        const val BEN_ID = 820_000L
    }
}
