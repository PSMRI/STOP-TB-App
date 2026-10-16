package org.piramalswasthya.stoptb.database.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.piramalswasthya.stoptb.model.*
import org.piramalswasthya.stoptb.network.*
import org.piramalswasthya.stoptb.repositories.DrugMasterRepo.Companion.toCache

@RunWith(AndroidJUnit4::class)
class DrugMasterTest {
    private fun masters(scope: String): OpdDrugMasters = DoctorDrugMasterData(
        itemMaster = listOf(DrugItemNetwork(5, 712, "Aluminium Hydroxide", "250", "mg", 100.0, 1, 1, 127),
            DrugItemNetwork(9, 792, "Amlodipine", "2.5", "mg", 0.0, 1, 1, 127)),
        drugFormMaster = listOf(DrugFormNetwork(1, "Tablet")),
        drugFrequencyMaster = listOf(DrugFrequencyNetwork(1, "Once daily")),
        drugDurationUnitMaster = listOf(DrugDurationNetwork(1, "Day(s)"))
    ).toCache(scope)!!

    @Test
    fun cachesAllMasterFieldsAndReplacesOnlyCurrentScope() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, InAppDb::class.java).build()
        try {
            val dao = db.drugMasterDao
            val first = masters("6/1734/Male/0/134")
            val other = masters("6/9999/Male/0/134")
            dao.replaceMasters(first.items.first().scope, first)
            dao.replaceMasters(other.items.first().scope, other)
            assertEquals(first, dao.getMasters(first.items.first().scope))
            assertEquals("Tablet Aluminium Hydroxide (250 mg)", first.medicineLabels().first())
            assertEquals(0.0, dao.getItems(first.items.first().scope).last().quantityInHand!!, 0.0)
            dao.replaceMasters(first.items.first().scope, first.copy(items = first.items.take(1)))
            assertEquals(1, dao.getItems(first.items.first().scope).size)
            assertEquals(other, dao.getMasters(other.items.first().scope))
            // A duplicate row must roll back the whole refresh, not wipe the last good cache.
            try {
                dao.replaceMasters(first.items.first().scope, first.copy(items = listOf(first.items.first(), first.items.first())))
                fail("Expected duplicate primary key rejection")
            } catch (_: android.database.sqlite.SQLiteConstraintException) { }
            assertEquals(1, dao.getItems(first.items.first().scope).size)
        } finally { db.close() }
    }

    @Test
    fun incompleteResponseDoesNotBecomeAnEmptyCache() {
        assertNull(DoctorDrugMasterData().toCache("scope"))
        assertNotNull(DoctorDrugMasterData(emptyList(), emptyList(), emptyList(), emptyList()).toCache("scope"))
    }

    @Test
    fun migration54To57_preservesExistingTablesAndValidatesNewSchema() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "drug-master-migration-test"
        context.deleteDatabase(name)
        try {
            val original = Room.databaseBuilder(context, InAppDb::class.java, name).build()
            original.chiefComplaintMasterDao.insertChiefComplaints(listOf(ChiefComplaintMasterCache(1, "Legacy complaint")))
            val sqlite = original.openHelper.writableDatabase
            restoreGeneralOpdV55Schema(sqlite)
            listOf("DRUG_ITEM_MASTER", "DRUG_FORM_MASTER", "DRUG_FREQUENCY_MASTER", "DRUG_DURATION_UNIT_MASTER")
                .forEach { sqlite.execSQL("DROP TABLE $it") }
            sqlite.execSQL("PRAGMA user_version = 54")
            original.close()
            val migrated = Room.databaseBuilder(context, InAppDb::class.java, name)
                .addMigrations(InAppDb.MIGRATION_54_55, InAppDb.MIGRATION_55_56, InAppDb.MIGRATION_56_57).build()
            try {
                assertEquals(listOf("Legacy complaint"), migrated.chiefComplaintMasterDao.getChiefComplaints().map { it.chiefComplaint })
                assertTrue(migrated.drugMasterDao.getItems("scope").isEmpty())
                assertEquals(57, migrated.openHelper.writableDatabase.version)
            } finally { migrated.close() }
        } finally { context.deleteDatabase(name) }
    }
}
