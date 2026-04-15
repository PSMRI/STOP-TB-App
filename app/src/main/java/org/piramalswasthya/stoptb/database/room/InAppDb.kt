package org.piramalswasthya.stoptb.database.room

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.piramalswasthya.stoptb.database.converters.LocationEntityListConverter
import org.piramalswasthya.stoptb.database.converters.StringListConverter
import org.piramalswasthya.stoptb.database.converters.SyncStateConverter
import org.piramalswasthya.stoptb.database.room.dao.ABHAGenratedDao
import org.piramalswasthya.stoptb.database.room.dao.AesDao
import org.piramalswasthya.stoptb.database.room.dao.BenDao
import org.piramalswasthya.stoptb.database.room.dao.BeneficiaryIdsAvailDao
import org.piramalswasthya.stoptb.database.room.dao.CbacDao
import org.piramalswasthya.stoptb.database.room.dao.FilariaDao
import org.piramalswasthya.stoptb.database.room.dao.HouseholdDao
import org.piramalswasthya.stoptb.database.room.dao.KalaAzarDao
import org.piramalswasthya.stoptb.database.room.dao.LeprosyDao
import org.piramalswasthya.stoptb.database.room.dao.MalariaDao
import org.piramalswasthya.stoptb.database.room.dao.SyncDao
import org.piramalswasthya.stoptb.database.room.dao.TBDao
import org.piramalswasthya.stoptb.database.room.dao.dynamicSchemaDao.FormResponseDao
import org.piramalswasthya.stoptb.database.room.dao.dynamicSchemaDao.FormResponseJsonDao
import org.piramalswasthya.stoptb.database.room.dao.dynamicSchemaDao.FormSchemaDao
import org.piramalswasthya.stoptb.model.ABHAModel
import org.piramalswasthya.stoptb.helpers.DatabaseKeyManager
import org.piramalswasthya.stoptb.helpers.RoomDbEncryptionHelper
import org.piramalswasthya.stoptb.database.room.dao.dynamicSchemaDao.NCDReferalFormResponseJsonDao
import org.piramalswasthya.stoptb.model.AESScreeningCache
import org.piramalswasthya.stoptb.model.BenBasicCache
import org.piramalswasthya.stoptb.model.BenRegCache
import org.piramalswasthya.stoptb.model.CbacCache
import org.piramalswasthya.stoptb.model.FilariaScreeningCache
import org.piramalswasthya.stoptb.model.HouseholdCache
import org.piramalswasthya.stoptb.model.KalaAzarScreeningCache
import org.piramalswasthya.stoptb.model.LeprosyFollowUpCache
import org.piramalswasthya.stoptb.model.LeprosyScreeningCache
import org.piramalswasthya.stoptb.model.MalariaConfirmedCasesCache
import org.piramalswasthya.stoptb.model.MalariaScreeningCache
import org.piramalswasthya.stoptb.model.ReferalCache
import org.piramalswasthya.stoptb.model.TBScreeningCache
import org.piramalswasthya.stoptb.model.TBSuspectedCache
import org.piramalswasthya.stoptb.model.TBConfirmedTreatmentCache
import org.piramalswasthya.stoptb.model.dynamicEntity.FormResponseJsonEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.FormSchemaEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.NCDReferalFormResponseJsonEntity

@Database(
    entities = [
        HouseholdCache::class,
        BenRegCache::class,
        BeneficiaryIdsAvail::class,
        CbacCache::class,
        TBScreeningCache::class,
        TBSuspectedCache::class,
        MalariaScreeningCache::class,
        AESScreeningCache::class,
        KalaAzarScreeningCache::class,
        FilariaScreeningCache::class,
        LeprosyScreeningCache::class,
        LeprosyFollowUpCache::class,
        MalariaConfirmedCasesCache::class,
        ABHAModel::class,
        FormSchemaEntity::class,
        FormResponseJsonEntity::class,
        NCDReferalFormResponseJsonEntity::class,
        ReferalCache::class,
        TBConfirmedTreatmentCache::class
    ],
    views = [BenBasicCache::class],
    version = 2, exportSchema = false
)
@TypeConverters(
    LocationEntityListConverter::class,
    SyncStateConverter::class,
    StringListConverter::class
)
abstract class InAppDb : RoomDatabase() {

    abstract val benIdGenDao: BeneficiaryIdsAvailDao
    abstract val householdDao: HouseholdDao
    abstract val benDao: BenDao
    abstract val cbacDao: CbacDao
    abstract val tbDao: TBDao
    abstract val malariaDao: MalariaDao
    abstract val aesDao: AesDao
    abstract val kalaAzarDao: KalaAzarDao
    abstract val leprosyDao: LeprosyDao
    abstract val filariaDao: FilariaDao
    abstract val abhaGenratedDao: ABHAGenratedDao
    abstract val referalDao: NcdReferalDao
    abstract fun formSchemaDao(): FormSchemaDao
    abstract fun formResponseDao(): FormResponseDao
    abstract fun NCDReferalFormResponseJsonDao(): NCDReferalFormResponseJsonDao
    abstract fun formResponseJsonDao(): FormResponseJsonDao
    abstract val syncDao: SyncDao

    companion object {
        @Volatile
        private var INSTANCE: InAppDb? = null

        fun tableExists(db: SupportSQLiteDatabase, tableName: String): Boolean {
            val cursor = db.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                arrayOf(tableName)
            )
            val exists = cursor.count > 0
            cursor.close()
            return exists
        }

        fun columnExists(
            db: SupportSQLiteDatabase,
            tableName: String,
            columnName: String
        ): Boolean {
            val cursor = db.query("PRAGMA table_info($tableName)")
            while (cursor.moveToNext()) {
                if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == columnName) {
                    cursor.close()
                    return true
                }
            }
            cursor.close()
            return false
        }

        fun getInstance(appContext: Context): InAppDb {

            // =====================================================================
            // HOW TO ADD MIGRATION IN FUTURE:
            // Step 1: Increment version in @Database annotation (e.g., version = 2)
            // Step 2: Add migration object below
            // Step 3: Add migration to addMigrations() and remove fallbackToDestructiveMigration()
            //
            // Example:
            // val MIGRATION_1_2 = object : Migration(1, 2) {
            //     override fun migrate(database: SupportSQLiteDatabase) {
            //         database.execSQL("ALTER TABLE BENEFICIARY ADD COLUMN newField TEXT DEFAULT NULL")
            //     }
            // }
            //
            // Then in builder:
            // instance = builder
            //     .addMigrations(MIGRATION_1_2)
            //     .build()
            // =====================================================================

            synchronized(this) {
                var instance = INSTANCE
                if (instance == null) {
                    val isDebug = appContext.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0

                    val builder = Room.databaseBuilder(
                        appContext,
                        InAppDb::class.java,
                        "Sakhi-2.0-In-app-database"
                    )

                    if (!isDebug) {
                        val passphrase = DatabaseKeyManager.getDatabasePassphrase(appContext)
                        RoomDbEncryptionHelper.encryptIfNeeded(
                            context = appContext,
                            dbName = "Sakhi-2.0-In-app-database",
                            passphrase = passphrase
                        )
                        val factory = SupportOpenHelperFactory(String(passphrase).toByteArray(Charsets.UTF_8))
                        builder.openHelperFactory(factory)
                    }

                    instance = builder
                        .fallbackToDestructiveMigration()
                        .build()

                    INSTANCE = instance
                }
                return instance
            }
        }
    }
}