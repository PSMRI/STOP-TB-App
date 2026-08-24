package org.piramalswasthya.stoptb.database.room.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.piramalswasthya.stoptb.database.room.InAppDb
import org.piramalswasthya.stoptb.database.room.SyncState
import org.piramalswasthya.stoptb.model.*
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class TBDaoTest {

    private lateinit var db: InAppDb
    private lateinit var tbDao: TBDao
    private lateinit var benDao: BenDao

    private val villageId = 123
    private val assignedVillages = listOf(villageId)

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, InAppDb::class.java)
            .allowMainThreadQueries()
            .build()
        tbDao = db.tbDao
        benDao = db.benDao
        println("\n>>> [SETUP] In-memory SQLite database initialized successfully <<<")
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
        println(">>> [TEARDOWN] In-memory SQLite database closed <<<")
    }

    private suspend fun insertBeneficiary(benId: Long, age: Int = 30) {
        val dob = System.currentTimeMillis() - (age.toLong() * 365 * 24 * 60 * 60 * 1000)
        val ben = BenRegCache(
            beneficiaryId = benId,
            isDeath = false,
            reasonOfDeathId = 0,
            placeOfDeathId = 0,
            ashaId = 1,
            isKid = age < 15,
            isAdult = age >= 15,
            locationRecord = LocationRecord(
                country = LocationEntity(1, "India"),
                state = LocationEntity(2, "State"),
                district = LocationEntity(3, "District"),
                block = LocationEntity(4, "Block"),
                village = LocationEntity(villageId, "Village")
            ),
            syncState = SyncState.SYNCED,
            isDraft = false,
            gender = Gender.MALE,
            dob = dob
        )
        benDao.upsert(ben)
        println("  -> [DB INSERT] Beneficiary (ID: $benId, Village: $villageId) inserted.")
    }

    @Test
    fun presumptiveCount_asymptomaticNoXray_shouldNotCount() = runBlocking {
        println("\n=== Scenario 1: Asymptomatic Screening, No X-ray ===")
        val benId = 5001L
        insertBeneficiary(benId)

        // Asymptomatic screening (all symptoms set to 0/false)
        val screening = TBScreeningCache(
            benId = benId,
            visitDate = System.currentTimeMillis(),
            coughMoreThan2Weeks = false,
            bloodInSputum = false,
            feverMoreThan2Weeks = false,
            lossOfWeight = false,
            nightSweats = false,
            riseOfFever = false,
            lossOfAppetite = false,
            familySufferingFromTB = false,
            historyOfTb = false,
            takingAntiTBDrugs = false
        )
        tbDao.saveTbScreening(screening)
        println("  -> [DB INSERT] Saved asymptomatic verbal screening form (all symptoms = false).")

        val count = tbDao.getDashboardPresumptiveTbCount(villageId, assignedVillages, 0, 0, "", 0).first()
        println("  -> [DB QUERY] Dashboard presumptive count: $count")
        
        println("  -> [ASSERT] Verifying count is 0...")
        assertEquals(0, count)
        println("=== Scenario 1: PASSED ===")
    }

    @Test
    fun presumptiveCount_asymptomaticWithPendingXray_shouldNotCount() = runBlocking {
        println("\n=== Scenario 2: Asymptomatic Screening with Pending X-ray Referral ===")
        val benId = 5002L
        insertBeneficiary(benId)

        // Asymptomatic screening
        val screening = TBScreeningCache(
            benId = benId,
            visitDate = System.currentTimeMillis(),
            coughMoreThan2Weeks = false,
            bloodInSputum = false
        )
        tbDao.saveTbScreening(screening)
        println("  -> [DB INSERT] Saved asymptomatic verbal screening.")

        // Referral/Order created but result is still NULL
        val diag = TBDiagnosticsCache(
            benId = benId,
            isChestXRayDone = true,
            xrayOrderStatus = "PENDING"
        )
        tbDao.saveTbDiagnostics(diag)
        println("  -> [DB INSERT] X-ray order created (Status: PENDING, Result: NULL).")

        val count = tbDao.getDashboardPresumptiveTbCount(villageId, assignedVillages, 0, 0, "", 0).first()
        println("  -> [DB QUERY] Dashboard presumptive count: $count")
        
        println("  -> [ASSERT] Verifying count is 0...")
        assertEquals(0, count)
        println("=== Scenario 2: PASSED ===")
    }

    @Test
    fun presumptiveCount_asymptomaticWithCompletedXray_shouldCount() = runBlocking {
        println("\n=== Scenario 3: Asymptomatic Screening with Completed Positive X-ray ===")
        val benId = 5003L
        insertBeneficiary(benId)

        // Asymptomatic screening
        val screening = TBScreeningCache(
            benId = benId,
            visitDate = System.currentTimeMillis(),
            coughMoreThan2Weeks = false,
            bloodInSputum = false
        )
        tbDao.saveTbScreening(screening)
        println("  -> [DB INSERT] Saved asymptomatic verbal screening.")

        // Completed positive chest X-Ray result
        val diag = TBDiagnosticsCache(
            benId = benId,
            isChestXRayDone = true,
            xrayOrderStatus = "COMPLETED",
            chestXRayResult = "TB Presumptive"
        )
        tbDao.saveTbDiagnostics(diag)
        tbDao.saveTbSuspected(TBSuspectedCache(benId = benId, chestXRayResult = "TB Presumptive"))
        println("  -> [DB INSERT] Chest X-Ray completed. Result: 'TB Presumptive'.")

        val count = tbDao.getDashboardPresumptiveTbCount(villageId, assignedVillages, 0, 0, "", 0).first()
        println("  -> [DB QUERY] Dashboard presumptive count: $count")
        
        println("  -> [ASSERT] Verifying count is 1...")
        assertEquals(1, count)
        println("=== Scenario 3: PASSED ===")
    }

    @Test
    fun presumptiveCount_presumptiveWithPositiveNaat_shouldNotCount() = runBlocking {
        println("\n=== Scenario 4: Presumptive Case Progressing to Confirmed ===")
        val benId = 5004L
        insertBeneficiary(benId)

        // Symptomatic screening
        val screening = TBScreeningCache(
            benId = benId,
            visitDate = System.currentTimeMillis(),
            coughMoreThan2Weeks = true
        )
        tbDao.saveTbScreening(screening)
        println("  -> [DB INSERT] Saved symptomatic screening (Cough = true). Case is presumptive.")

        // Confirmed via positive NAAT test
        val diag = TBDiagnosticsCache(
            benId = benId,
            isChestXRayDone = true,
            xrayOrderStatus = "COMPLETED",
            naatResult = "Positive"
        )
        tbDao.saveTbDiagnostics(diag)
        println("  -> [DB INSERT] Lab results received. TrueNat/NAAT: 'Positive' (Confirmed).")

        val count = tbDao.getDashboardPresumptiveTbCount(villageId, assignedVillages, 0, 0, "", 0).first()
        println("  -> [DB QUERY] Dashboard presumptive count (expected to exclude confirmed): $count")
        
        println("  -> [ASSERT] Verifying count is 0...")
        assertEquals(0, count)
        println("=== Scenario 4: PASSED ===")
    }

    @Test
    fun presumptiveCount_presumptiveWithNegativeNaat_shouldCount() = runBlocking {
        println("\n=== Scenario 5: Presumptive Case with Negative NAAT ===")
        val benId = 5005L
        insertBeneficiary(benId)

        // Symptomatic screening
        val screening = TBScreeningCache(
            benId = benId,
            visitDate = System.currentTimeMillis(),
            coughMoreThan2Weeks = true
        )
        tbDao.saveTbScreening(screening)
        println("  -> [DB INSERT] Saved symptomatic screening (Cough = true).")

        // Negative NAAT test (still presumptive under follow-up)
        val diag = TBDiagnosticsCache(
            benId = benId,
            isChestXRayDone = true,
            xrayOrderStatus = "COMPLETED",
            naatResult = "Negative"
        )
        tbDao.saveTbDiagnostics(diag)
        println("  -> [DB INSERT] Lab results received. TrueNat/NAAT: 'Negative'.")

        val count = tbDao.getDashboardPresumptiveTbCount(villageId, assignedVillages, 0, 0, "", 0).first()
        println("  -> [DB QUERY] Dashboard presumptive count: $count")
        
        println("  -> [ASSERT] Verifying count is 1...")
        assertEquals(1, count)
        println("=== Scenario 5: PASSED ===")
    }

    @Test
    fun sputumCount_sputumCollectedTrueNatPending_shouldNotCount() = runBlocking {
        println("\n=== Scenario 6: Sputum Collected, TrueNat Test Pending ===")
        val benId = 5006L
        insertBeneficiary(benId)

        val diag = TBDiagnosticsCache(
            benId = benId,
            isSputumCollected = true,
            trueNatOrderStatus = "PENDING"
        )
        tbDao.saveTbDiagnostics(diag)
        println("  -> [DB INSERT] Sputum collected. TrueNat order status: 'PENDING'.")

        val count = tbDao.getDashboardSputumCollectionCount(villageId, assignedVillages, 0, 0, "", 0, 0).first()
        println("  -> [DB QUERY] Sputum collection conducted count: $count")
        
        println("  -> [ASSERT] Verifying count is 0...")
        assertEquals(0, count)
        println("=== Scenario 6: PASSED ===")
    }

    @Test
    fun sputumCount_sputumCollectedTrueNatCompleted_shouldCount() = runBlocking {
        println("\n=== Scenario 7: Sputum Collected, TrueNat Completed ===")
        val benId = 5007L
        insertBeneficiary(benId)

        val diag = TBDiagnosticsCache(
            benId = benId,
            isSputumCollected = true,
            trueNatOrderStatus = "COMPLETED"
        )
        tbDao.saveTbDiagnostics(diag)
        println("  -> [DB INSERT] Sputum collected. TrueNat order status: 'COMPLETED'.")

        val count = tbDao.getDashboardSputumCollectionCount(villageId, assignedVillages, 0, 0, "", 0, 0).first()
        println("  -> [DB QUERY] Sputum collection conducted count: $count")
        
        println("  -> [ASSERT] Verifying count is 1...")
        assertEquals(1, count)
        println("=== Scenario 7: PASSED ===")
    }

    @Test
    fun sputumCount_sputumCollectedTrueNatRefused_shouldCount() = runBlocking {
        println("\n=== Scenario 8: Sputum Collected, TrueNat Refused ===")
        val benId = 5008L
        insertBeneficiary(benId)

        val diag = TBDiagnosticsCache(
            benId = benId,
            isSputumCollected = true,
            trueNatOrderStatus = "REFUSED"
        )
        tbDao.saveTbDiagnostics(diag)
        println("  -> [DB INSERT] Sputum collected. TrueNat order status: 'REFUSED'.")

        val count = tbDao.getDashboardSputumCollectionCount(villageId, assignedVillages, 0, 0, "", 0, 0).first()
        println("  -> [DB QUERY] Sputum collection conducted count: $count")
        
        println("  -> [ASSERT] Verifying count is 1...")
        assertEquals(1, count)
        println("=== Scenario 8: PASSED ===")
    }

    @Test
    fun sputumCount_sputumRefusedTrueNatRefused_shouldNotCount() = runBlocking {
        println("\n=== Scenario 9: Sputum Collection Refused ===")
        val benId = 5009L
        insertBeneficiary(benId)

        val diag = TBDiagnosticsCache(
            benId = benId,
            isSputumCollected = false,
            trueNatOrderStatus = "REFUSED"
        )
        tbDao.saveTbDiagnostics(diag)
        println("  -> [DB INSERT] Sputum collection refused (isSputumCollected = false, TrueNat = REFUSED).")

        val count = tbDao.getDashboardSputumCollectionCount(villageId, assignedVillages, 0, 0, "", 0, 0).first()
        println("  -> [DB QUERY] Sputum collection conducted count: $count")
        
        println("  -> [ASSERT] Verifying count is 0...")
        assertEquals(0, count)
        println("=== Scenario 9: PASSED ===")
    }

    @Test
    fun sputumCount_sputumCollectedTrueNatFailed_shouldNotCount() = runBlocking {
        println("\n=== Scenario 10: Sputum Collected, TrueNat Failed ===")
        val benId = 5010L
        insertBeneficiary(benId)

        val diag = TBDiagnosticsCache(
            benId = benId,
            isSputumCollected = true,
            trueNatOrderStatus = "FAILED"
        )
        tbDao.saveTbDiagnostics(diag)
        println("  -> [DB INSERT] Sputum collected. TrueNat order status: 'FAILED'.")

        val count = tbDao.getDashboardSputumCollectionCount(villageId, assignedVillages, 0, 0, "", 0, 0).first()
        println("  -> [DB QUERY] Sputum collection conducted count: $count")
        
        println("  -> [ASSERT] Verifying count is 0...")
        assertEquals(0, count)
        println("=== Scenario 10: PASSED ===")
    }
}
