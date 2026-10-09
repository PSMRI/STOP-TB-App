package org.piramalswasthya.stoptb.database.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.piramalswasthya.stoptb.model.BenRegCache
import org.piramalswasthya.stoptb.model.GeneralOpdCache
import org.piramalswasthya.stoptb.model.GeneralOpdPrescription
import org.piramalswasthya.stoptb.model.LocationEntity
import org.piramalswasthya.stoptb.model.LocationRecord
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrescriptionMigrationTest {
    @Test
    fun migration55And56To57_preservesOldPrescriptionsAndAllocatesStableSubmissionIds() = runBlocking {
        for (oldVersion in listOf(55, 56)) verifyPayloadMigration(oldVersion)
    }

    private suspend fun verifyPayloadMigration(oldVersion: Int) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "opd-payload-migration-test-$oldVersion"
        context.deleteDatabase(name)
        try {
            val original = Room.databaseBuilder(context, InAppDb::class.java, name).build()
            original.benDao.upsert(BenRegCache(
                beneficiaryId = 12345L, isDeath = false, reasonOfDeathId = 0, placeOfDeathId = 0,
                ashaId = 1, isKid = false, isAdult = true, syncState = SyncState.UNSYNCED, isDraft = false,
                locationRecord = LocationRecord(country = LocationEntity(1, "India"), state = LocationEntity(2, "State"),
                    district = LocationEntity(3, "District"), block = LocationEntity(4, "Block"), village = LocationEntity(5, "Village"))
            ))
            original.tbDao.saveGeneralOpd(GeneralOpdCache(id = 7, benId = 12345, chiefComplaints = listOf("Fever"),
                medications = listOf("Legacy medicine"), notes = "Patient remarks"))
            val medicine = GeneralOpdPrescription(7, 0, "Legacy medicine", "Twice Daily(BD)", 3, "Day(s)", "Before food")
            original.tbDao.insertGeneralOpdPrescriptions(listOf(medicine))
            val sqlite = original.openHelper.writableDatabase
            restoreGeneralOpdV55Schema(sqlite)
            if (oldVersion == 56) InAppDb.MIGRATION_55_56.migrate(sqlite)
            sqlite.execSQL("PRAGMA user_version = $oldVersion")
            original.close()
            val migrated = Room.databaseBuilder(context, InAppDb::class.java, name)
                .addMigrations(InAppDb.MIGRATION_55_56, InAppDb.MIGRATION_56_57).build()
            try {
                val saved = migrated.tbDao.getGeneralOpdById(7)!!
                assertEquals("Patient remarks", saved.notes)
                assertEquals(listOf("Fever"), saved.chiefComplaints)
                assertEquals(listOf(medicine), migrated.tbDao.getGeneralOpdPrescriptions(7))
                val withForm = medicine.copy(itemFormId = 1, drugForm = "Tablet")
                migrated.tbDao.deleteGeneralOpdPrescriptions(7)
                migrated.tbDao.insertGeneralOpdPrescriptions(listOf(withForm))
                assertEquals(listOf(withForm), migrated.tbDao.getGeneralOpdPrescriptions(7))
                val uuid = migrated.tbDao.ensureGeneralOpdSubmissionId(7)
                java.util.UUID.fromString(uuid)
                assertEquals(uuid, migrated.tbDao.ensureGeneralOpdSubmissionId(7))
                assertEquals(57, migrated.openHelper.writableDatabase.version)
            } finally { migrated.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test
    fun migration53To57_validatesRoomSchemaAndPreservesLegacyOpd() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "opd-prescription-migration-test"
        context.deleteDatabase(name)
        try {
            // Recreate the prior schema by removing the subsequently added tables.
            val original = Room.databaseBuilder(context, InAppDb::class.java, name)
                .allowMainThreadQueries().build()
            val sqlite = original.openHelper.writableDatabase
            original.benDao.upsert(BenRegCache(
                beneficiaryId = 12345L, isDeath = false, reasonOfDeathId = 0,
                placeOfDeathId = 0, ashaId = 1, isKid = false, isAdult = true,
                syncState = SyncState.UNSYNCED, isDraft = false,
                locationRecord = LocationRecord(
                    country = LocationEntity(1, "India"), state = LocationEntity(2, "State"),
                    district = LocationEntity(3, "District"), block = LocationEntity(4, "Block"),
                    village = LocationEntity(5, "Village")
                )
            ))
            original.tbDao.saveGeneralOpd(GeneralOpdCache(
                id = 7, benId = 12345L, medications = listOf("Legacy medicine")
            ))
            restoreGeneralOpdV55Schema(sqlite)
            sqlite.execSQL("DROP TABLE GENERAL_OPD_PRESCRIPTION")
            listOf("DRUG_ITEM_MASTER", "DRUG_FORM_MASTER", "DRUG_FREQUENCY_MASTER", "DRUG_DURATION_UNIT_MASTER")
                .forEach { sqlite.execSQL("DROP TABLE $it") }
            sqlite.execSQL("PRAGMA user_version = 53")
            original.close()

            val migrated = Room.databaseBuilder(context, InAppDb::class.java, name)
                .allowMainThreadQueries().addMigrations(InAppDb.MIGRATION_53_54, InAppDb.MIGRATION_54_55,
                    InAppDb.MIGRATION_55_56, InAppDb.MIGRATION_56_57).build()
            try {
                val db = migrated.openHelper.writableDatabase
                db.query("SELECT medications FROM GENERAL_OPD WHERE id = 7").use {
                    check(it.moveToFirst())
                    check(it.getString(0).contains("Legacy medicine"))
                }
                db.query("SELECT COUNT(*) FROM GENERAL_OPD_PRESCRIPTION").use {
                    check(it.moveToFirst())
                    assertEquals(0, it.getInt(0))
                }
                assertEquals(57, db.version)
            } finally { migrated.close() }
        } finally { context.deleteDatabase(name) }
    }
}
