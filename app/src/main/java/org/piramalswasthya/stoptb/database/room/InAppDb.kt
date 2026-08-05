package org.piramalswasthya.stoptb.database.room

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.piramalswasthya.stoptb.database.converters.GenderConverter
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
import org.piramalswasthya.stoptb.database.room.dao.VitalDao
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
import org.piramalswasthya.stoptb.model.GeneralOpdCache
import org.piramalswasthya.stoptb.model.HouseholdCache
import org.piramalswasthya.stoptb.model.KalaAzarScreeningCache
import org.piramalswasthya.stoptb.model.LeprosyFollowUpCache
import org.piramalswasthya.stoptb.model.LeprosyScreeningCache
import org.piramalswasthya.stoptb.model.MalariaConfirmedCasesCache
import org.piramalswasthya.stoptb.model.MalariaScreeningCache
import org.piramalswasthya.stoptb.model.ReferalCache
import org.piramalswasthya.stoptb.model.TBDiagnosticsCache
import org.piramalswasthya.stoptb.model.TBScreeningCache
import org.piramalswasthya.stoptb.model.TBSuspectedCache
import org.piramalswasthya.stoptb.model.TBConfirmedTreatmentCache
import org.piramalswasthya.stoptb.model.VitalCache
import org.piramalswasthya.stoptb.model.dynamicEntity.FormResponseJsonEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.FormSchemaEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.NCDReferalFormResponseJsonEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.DynamicFormEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.FormVersionEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.FormSectionEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.SectionQuestionEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.QuestionOptionEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.QuestionValidationEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.OptionConditionEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.CounsellingFormResponseView
import org.piramalswasthya.stoptb.model.dynamicEntity.FormResponseEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.SectionResponseEntity
import org.piramalswasthya.stoptb.model.dynamicEntity.QuestionResponseEntity
import org.piramalswasthya.stoptb.database.room.dao.dynamicSchemaDao.DynamicFormMetadataDao
import org.piramalswasthya.stoptb.database.room.dao.dynamicSchemaDao.CounsellingFormResponseDao


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
        TBConfirmedTreatmentCache::class,
        VitalCache::class,
        GeneralOpdCache::class,
        TBDiagnosticsCache::class,
        DynamicFormEntity::class,
        FormVersionEntity::class,
        FormSectionEntity::class,
        SectionQuestionEntity::class,
        QuestionOptionEntity::class,
        QuestionValidationEntity::class,
        OptionConditionEntity::class,
        FormResponseEntity::class,
        SectionResponseEntity::class,
        QuestionResponseEntity::class
    ],
    views = [BenBasicCache::class, CounsellingFormResponseView::class],
    version = 42, exportSchema = false
)
@TypeConverters(
    LocationEntityListConverter::class,
    SyncStateConverter::class,
    StringListConverter::class,
    GenderConverter::class
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
    abstract fun dynamicFormMetadataDao(): DynamicFormMetadataDao
    abstract fun counsellingFormResponseDao(): CounsellingFormResponseDao
    abstract val syncDao: SyncDao
    abstract val vitalDao: VitalDao

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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `BEN_VITALS` (
                        `benId` INTEGER NOT NULL,
                        `benRegId` INTEGER NOT NULL DEFAULT 0,
                        `capturedAt` INTEGER NOT NULL,
                        `temperature` REAL,
                        `pulseRate` INTEGER,
                        `bpSystolic` INTEGER,
                        `bpDiastolic` INTEGER,
                        `respiratoryRate` INTEGER,
                        `spo2` INTEGER,
                        `height` REAL,
                        `weight` REAL,
                        `bmi` REAL,
                        `rbs` REAL,
                        `pallorId` INTEGER,
                        `pallor` TEXT,
                        `icterusId` INTEGER,
                        `icterus` TEXT,
                        `cyanosisId` INTEGER,
                        `cyanosis` TEXT,
                        `clubbingId` INTEGER,
                        `clubbing` TEXT,
                        `lymphadenopathyId` INTEGER,
                        `lymphadenopathy` TEXT,
                        `oedemaId` INTEGER,
                        `oedema` TEXT,
                        `keyPopulationRiskFactorIds` TEXT,
                        `keyPopulationRiskFactors` TEXT,
                        `hivStatusId` INTEGER,
                        `hivStatus` TEXT,
                        `referralToHwcNeeded` INTEGER,
                        `referralTriggers` TEXT,
                        `syncState` INTEGER NOT NULL,
                        PRIMARY KEY(`benId`),
                        FOREIGN KEY(`benId`) REFERENCES `BENEFICIARY`(`beneficiaryId`) ON UPDATE CASCADE ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `ind_vitals_ben` ON `BEN_VITALS` (`benId`)"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                if (!columnExists(database, "TB_SUSPECTED", "isLiquidCultureConducted")) {
                    database.execSQL(
                        "ALTER TABLE TB_SUSPECTED ADD COLUMN isLiquidCultureConducted INTEGER DEFAULT NULL"
                    )
                }
                if (!columnExists(database, "TB_SUSPECTED", "liquidCultureResult")) {
                    database.execSQL(
                        "ALTER TABLE TB_SUSPECTED ADD COLUMN liquidCultureResult TEXT DEFAULT NULL"
                    )
                }
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                addLocationRecordExtraColumns(database, "BENEFICIARY")
                addLocationRecordExtraColumns(database, "HOUSEHOLD")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                addVitalGeneralExaminationColumns(database)
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                addVitalGeneralExaminationIdColumns(database)
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                addTBScreeningReferralColumns(database)
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS `GENERAL_OPD`")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `GENERAL_OPD` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `benId` INTEGER NOT NULL,
                        `visitDate` INTEGER NOT NULL,
                        `chiefComplaints` TEXT,
                        `medications` TEXT,
                        `dosage` TEXT,
                        `frequency` TEXT,
                        `duration` TEXT,
                        `notes` TEXT,
                        `syncState` INTEGER NOT NULL,
                        FOREIGN KEY(`benId`) REFERENCES `BENEFICIARY`(`beneficiaryId`) ON UPDATE CASCADE ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `ind_general_opd_ben` ON `GENERAL_OPD` (`benId`)"
                )
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                if (!columnExists(database, "TB_SUSPECTED", "recommendedForLiquidCultureTest")) {
                    database.execSQL(
                        "ALTER TABLE TB_SUSPECTED ADD COLUMN recommendedForLiquidCultureTest INTEGER DEFAULT NULL"
                    )
                }
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `TB_DIAGNOSTICS` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `benId` INTEGER NOT NULL,
                        `visitDate` INTEGER NOT NULL,
                        `nikshayId` TEXT,
                        `isChestXRayDone` INTEGER,
                        `chestXRayResult` TEXT,
                        `isSputumCollected` INTEGER,
                        `sputumSubmittedAt` TEXT,
                        `isNaatConducted` INTEGER,
                        `naatResult` TEXT,
                        `recommendedForLiquidCultureTest` INTEGER,
                        `isLiquidCultureConducted` INTEGER,
                        `liquidCultureResult` TEXT,
                        `isTBConfirmed` INTEGER,
                        `isConfirmed` INTEGER NOT NULL,
                        `latitude` REAL,
                        `longitude` REAL,
                        `address` TEXT,
                        `syncState` INTEGER NOT NULL,
                        FOREIGN KEY(`benId`) REFERENCES `BENEFICIARY`(`beneficiaryId`) ON UPDATE CASCADE ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `ind_tb_diagnostics_ben` ON `TB_DIAGNOSTICS` (`benId`)"
                )
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val columns = listOf(
                    "personFrom TEXT DEFAULT NULL",
                    "personFromId INTEGER DEFAULT NULL",
                    "typeOfCaseFinding TEXT DEFAULT NULL",
                    "typeOfCaseFindingId INTEGER DEFAULT NULL",
                    "mobileNumberAvailable INTEGER DEFAULT NULL",
                    "address TEXT DEFAULT NULL",
                    "height REAL DEFAULT NULL",
                    "weight REAL DEFAULT NULL",
                    "bmi REAL DEFAULT NULL",
                    "temperature REAL DEFAULT NULL"
                )
                columns.forEach { columnDefinition ->
                    val columnName = columnDefinition.substringBefore(" ")
                    if (!columnExists(database, "BENEFICIARY", columnName)) {
                        database.execSQL("ALTER TABLE BENEFICIARY ADD COLUMN $columnDefinition")
                    }
                }
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                if (!columnExists(database, "BENEFICIARY", "nikshayId")) {
                    database.execSQL("ALTER TABLE BENEFICIARY ADD COLUMN nikshayId TEXT DEFAULT NULL")
                }
                recreateBenBasicCacheView(database)
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                if (!columnExists(database, "BENEFICIARY", "serverUpdatedDate")) {
                    database.execSQL("ALTER TABLE BENEFICIARY ADD COLUMN serverUpdatedDate INTEGER DEFAULT NULL")
                }
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val tables = listOf(
                    "TB_SCREENING",
                    "TB_SUSPECTED",
                    "TB_DIAGNOSTICS",
                    "GENERAL_OPD",
                    "BEN_VITALS",
                    "TB_CONFIRMED_TREATMENT",
                    "MALARIA_CONFIRMED",
                    "USER"
                )
                tables.forEach { tableName ->
                    if (tableExists(database, tableName) && !columnExists(database, tableName, "serverUpdatedDate")) {
                        database.execSQL("ALTER TABLE $tableName ADD COLUMN serverUpdatedDate INTEGER DEFAULT NULL")
                    }
                }
            }
        }

        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val benColumns = listOf(
                    "gpsLatitude REAL DEFAULT NULL",
                    "gpsLongitude REAL DEFAULT NULL",
                    "digipin TEXT DEFAULT NULL",
                    "gpsTimestamp TEXT DEFAULT NULL",
                    "isGpsUnavailable INTEGER NOT NULL DEFAULT 0",
                    "gpsUnavailableReason TEXT DEFAULT NULL"
                )
                benColumns.forEach { columnDefinition ->
                    val columnName = columnDefinition.substringBefore(" ")
                    if (!columnExists(database, "BENEFICIARY", columnName)) {
                        database.execSQL("ALTER TABLE BENEFICIARY ADD COLUMN $columnDefinition")
                    }
                }
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val householdColumns = listOf(
                    "gpsLatitude REAL DEFAULT NULL",
                    "gpsLongitude REAL DEFAULT NULL",
                    "digipin TEXT DEFAULT NULL",
                    "gpsTimestamp TEXT DEFAULT NULL",
                    "isGpsUnavailable INTEGER NOT NULL DEFAULT 0",
                    "gpsUnavailableReason TEXT DEFAULT NULL"
                )
                householdColumns.forEach { columnDefinition ->
                    val columnName = columnDefinition.substringBefore(" ")
                    if (!columnExists(database, "HOUSEHOLD", columnName)) {
                        database.execSQL("ALTER TABLE HOUSEHOLD ADD COLUMN $columnDefinition")
                    }
                }
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val newColumns = listOf(
                    "isReferredForDigitalChestXray INTEGER DEFAULT NULL",
                    "reasonForDenialChestXray TEXT DEFAULT NULL",
                    "reasonForDenialChestXrayOther TEXT DEFAULT NULL",
                    "reasonNotConductedChestXray TEXT DEFAULT NULL",
                    "reasonNotConductedChestXrayOther TEXT DEFAULT NULL",
                    "reasonForDenialSputum TEXT DEFAULT NULL",
                    "reasonForDenialSputumOther TEXT DEFAULT NULL",
                    "reasonNotConductedNaat TEXT DEFAULT NULL",
                    "reasonNotConductedNaatOther TEXT DEFAULT NULL"
                )
                newColumns.forEach { columnDefinition ->
                    val columnName = columnDefinition.substringBefore(" ")
                    if (!columnExists(database, "TB_DIAGNOSTICS", columnName)) {
                        database.execSQL(
                            "ALTER TABLE TB_DIAGNOSTICS ADD COLUMN $columnDefinition"
                        )
                    }
                }
            }
        }

        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(database: SupportSQLiteDatabase) {

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `t_dynamic_form` (
                        `formId` INTEGER NOT NULL, 
                        `formUuid` TEXT NOT NULL, 
                        `formName` TEXT NOT NULL, 
                        `formType` TEXT NOT NULL, 
                        `createdAt` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`formId`)
                    )
                """.trimIndent())
                
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `t_form_version` (
                        `versionId` INTEGER NOT NULL, 
                        `formId` INTEGER NOT NULL, 
                        `versionNumber` INTEGER NOT NULL, 
                        `isActive` INTEGER NOT NULL, 
                        `createdAt` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`versionId`), 
                        FOREIGN KEY(`formId`) REFERENCES `t_dynamic_form`(`formId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_t_form_version_formId` ON `t_form_version` (`formId`)")

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `t_form_section` (
                        `sectionId` INTEGER NOT NULL, 
                        `versionId` INTEGER NOT NULL, 
                        `sectionName` TEXT NOT NULL, 
                        `sectionOrder` INTEGER NOT NULL, 
                        `sectionPhase` TEXT NOT NULL, 
                        `sectionUuid` TEXT, 
                        `createdAt` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`sectionId`), 
                        FOREIGN KEY(`versionId`) REFERENCES `t_form_version`(`versionId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_t_form_section_versionId` ON `t_form_section` (`versionId`)")

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `t_section_question` (
                        `questionId` INTEGER NOT NULL, 
                        `sectionId` INTEGER NOT NULL, 
                        `questionText` TEXT NOT NULL, 
                        `questionType` TEXT NOT NULL, 
                        `questionOrder` INTEGER NOT NULL, 
                        `isRequired` INTEGER NOT NULL, 
                        `questionUuid` TEXT, 
                        `createdAt` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`questionId`), 
                        FOREIGN KEY(`sectionId`) REFERENCES `t_form_section`(`sectionId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_t_section_question_sectionId` ON `t_section_question` (`sectionId`)")

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `t_question_option` (
                        `optionId` INTEGER NOT NULL, 
                        `questionId` INTEGER NOT NULL, 
                        `optionText` TEXT NOT NULL, 
                        `optionValue` TEXT NOT NULL, 
                        `optionOrder` INTEGER NOT NULL, 
                        `createdAt` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`optionId`), 
                        FOREIGN KEY(`questionId`) REFERENCES `t_section_question`(`questionId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_t_question_option_questionId` ON `t_question_option` (`questionId`)")

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `t_question_validation` (
                        `validationId` INTEGER NOT NULL, 
                        `questionId` INTEGER NOT NULL, 
                        `validationType` TEXT NOT NULL, 
                        `validationValue` TEXT, 
                        `errorMessage` TEXT NOT NULL, 
                        `createdAt` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`validationId`), 
                        FOREIGN KEY(`questionId`) REFERENCES `t_section_question`(`questionId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_t_question_validation_questionId` ON `t_question_validation` (`questionId`)")

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `t_option_condition` (
                        `conditionId` INTEGER NOT NULL, 
                        `optionId` INTEGER NOT NULL, 
                        `targetQuestionId` INTEGER NOT NULL, 
                        `actionType` TEXT NOT NULL, 
                        `isFulfilledValue` INTEGER NOT NULL, 
                        `createdAt` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`conditionId`), 
                        FOREIGN KEY(`optionId`) REFERENCES `t_question_option`(`optionId`) ON UPDATE NO ACTION ON DELETE CASCADE, 
                        FOREIGN KEY(`targetQuestionId`) REFERENCES `t_section_question`(`questionId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_t_option_condition_optionId` ON `t_option_condition` (`optionId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_t_option_condition_targetQuestionId` ON `t_option_condition` (`targetQuestionId`)")

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `t_form_response` (
                        `responseId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `beneficiaryId` INTEGER NOT NULL, 
                        `formVersionId` INTEGER NOT NULL, 
                        `status` TEXT NOT NULL, 
                        `lastVisitedSectionId` INTEGER, 
                        `syncStatus` TEXT NOT NULL DEFAULT 'UNSYNCED', 
                        `syncedAt` INTEGER, 
                        `createdAt` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        FOREIGN KEY(`formVersionId`) REFERENCES `t_form_version`(`versionId`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_t_form_response_beneficiaryId` ON `t_form_response` (`beneficiaryId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_t_form_response_formVersionId` ON `t_form_response` (`formVersionId`)")

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `t_section_response` (
                        `sectionResponseId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `formResponseId` INTEGER NOT NULL, 
                        `sectionId` INTEGER NOT NULL, 
                        `completedAt` INTEGER, 
                        `createdAt` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        FOREIGN KEY(`formResponseId`) REFERENCES `t_form_response`(`responseId`) ON UPDATE NO ACTION ON DELETE CASCADE, 
                        FOREIGN KEY(`sectionId`) REFERENCES `t_form_section`(`sectionId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_t_section_response_formResponseId` ON `t_section_response` (`formResponseId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_t_section_response_sectionId` ON `t_section_response` (`sectionId`)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_t_section_response_formResponseId_sectionId` ON `t_section_response` (`formResponseId`, `sectionId`)")

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `t_question_response` (
                        `questionResponseId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `sectionResponseId` INTEGER NOT NULL, 
                        `questionId` INTEGER NOT NULL, 
                        `optionId` INTEGER, 
                        `answerText` TEXT, 
                        `createdAt` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        FOREIGN KEY(`sectionResponseId`) REFERENCES `t_section_response`(`sectionResponseId`) ON UPDATE NO ACTION ON DELETE CASCADE, 
                        FOREIGN KEY(`questionId`) REFERENCES `t_section_question`(`questionId`) ON UPDATE NO ACTION ON DELETE CASCADE, 
                        FOREIGN KEY(`optionId`) REFERENCES `t_question_option`(`optionId`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_t_question_response_sectionResponseId` ON `t_question_response` (`sectionResponseId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_t_question_response_questionId` ON `t_question_response` (`questionId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_t_question_response_optionId` ON `t_question_response` (`optionId`)")
                if (!columnExists(database, "t_form_section", "sectionUuid")) {
                    database.execSQL("ALTER TABLE t_form_section ADD COLUMN sectionUuid TEXT DEFAULT NULL")
                }
                if (!columnExists(database, "t_section_question", "questionUuid")) {
                    database.execSQL("ALTER TABLE t_section_question ADD COLUMN questionUuid TEXT DEFAULT NULL")
                }
                val householdColumns =  listOf(
                    "gpsLatitude REAL DEFAULT NULL",
                    "gpsLongitude REAL DEFAULT NULL",
                    "digipin TEXT DEFAULT NULL",
                    "gpsTimestamp TEXT DEFAULT NULL",
                    "isGpsUnavailable INTEGER NOT NULL DEFAULT 0",
                    "gpsUnavailableReason TEXT DEFAULT NULL"
                )
                householdColumns.forEach { columnDefinition ->
                    val columnName = columnDefinition.substringBefore(" ")
                    if (!columnExists(database, "HOUSEHOLD", columnName)) {
                        database.execSQL("ALTER TABLE HOUSEHOLD ADD COLUMN $columnDefinition")
                    }
                }


                val benColumns = listOf(
                    "gpsLatitude REAL DEFAULT NULL",
                    "gpsLongitude REAL DEFAULT NULL",
                    "digipin TEXT DEFAULT NULL",
                    "gpsTimestamp TEXT DEFAULT NULL",
                    "isGpsUnavailable INTEGER NOT NULL DEFAULT 0",
                    "gpsUnavailableReason TEXT DEFAULT NULL"
                )
                benColumns.forEach { columnDefinition ->
                    val columnName = columnDefinition.substringBefore(" ")
                    if (!columnExists(database, "BENEFICIARY", columnName)) {
                        database.execSQL("ALTER TABLE BENEFICIARY ADD COLUMN $columnDefinition")
                    }
                }
            }
        }

        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(database: SupportSQLiteDatabase) {
                if (!columnExists(database, "t_dynamic_form", "followUpDelayDays")) {
                    database.execSQL(
                        """
                        ALTER TABLE t_dynamic_form
                        ADD COLUMN followUpDelayDays INTEGER NOT NULL DEFAULT -1
                        """.trimIndent()
                    )
                }
                if (!columnExists(database, "t_section_question", "serverQuestionId")) {
                    database.execSQL("ALTER TABLE t_section_question ADD COLUMN serverQuestionId INTEGER DEFAULT NULL")
                }
                if (!columnExists(database, "t_question_option", "serverOptionId")) {
                    database.execSQL("ALTER TABLE t_question_option ADD COLUMN serverOptionId INTEGER DEFAULT NULL")
                }
                // Delete child tables first to satisfy FK constraints (t_form_response
                // references t_form_version with ON DELETE RESTRICT).
                database.execSQL("DELETE FROM t_question_response")
                database.execSQL("DELETE FROM t_section_response")
                database.execSQL("DELETE FROM t_form_response")
                database.execSQL("DELETE FROM t_question_option")
                database.execSQL("DELETE FROM t_section_question")
                database.execSQL("DELETE FROM t_form_section")
                database.execSQL("DELETE FROM t_form_version")
                database.execSQL("DELETE FROM t_dynamic_form")
            }
        }


        private val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(database: SupportSQLiteDatabase) {
                if (!columnExists(database, "t_form_section", "sectionNameHindi")) {
                    database.execSQL("ALTER TABLE t_form_section ADD COLUMN sectionNameHindi TEXT")
                }
                if (!columnExists(database, "t_section_question", "questionTextHindi")) {
                    database.execSQL("ALTER TABLE t_section_question ADD COLUMN questionTextHindi TEXT")
                }
                if (!columnExists(database, "t_question_option", "optionTextHindi")) {
                    database.execSQL("ALTER TABLE t_question_option ADD COLUMN optionTextHindi TEXT")
                }
            }
        }

        private val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(database: SupportSQLiteDatabase) {
                if (!columnExists(database, "t_form_response", "sectionsFilled")) {
                    database.execSQL("ALTER TABLE t_form_response ADD COLUMN sectionsFilled INTEGER DEFAULT NULL")
                }
                if (!columnExists(database, "t_form_response", "totalSections")) {
                    database.execSQL("ALTER TABLE t_form_response ADD COLUMN totalSections INTEGER DEFAULT NULL")
                }
                
               if (!columnExists(database, "BENEFICIARY", "pinCode")) {
                    database.execSQL("ALTER TABLE BENEFICIARY ADD COLUMN pinCode TEXT")
                }
                
                
                 if (!columnExists(database, "t_form_section", "isEditable")) {
                    database.execSQL("ALTER TABLE t_form_section ADD COLUMN isEditable INTEGER NOT NULL DEFAULT 0")
                }
            }
        }
     /*   private val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val householdFamilyColumns = listOf(
                    "fam_totalHhMembers INTEGER DEFAULT NULL",
                    "fam_isRegisteredAtCampSite TEXT DEFAULT NULL",
                    "fam_isRegisteredAtCampSiteId INTEGER NOT NULL DEFAULT 0"
                )
                householdFamilyColumns.forEach { columnDefinition ->
                    val columnName = columnDefinition.substringBefore(" ")
                    if (!columnExists(database, "HOUSEHOLD", columnName)) {
                        database.execSQL("ALTER TABLE HOUSEHOLD ADD COLUMN $columnDefinition")
                    }
                }
            }
        }
*/

        private val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(database: SupportSQLiteDatabase) {
                var originalSql = ""
                database.query("SELECT sql FROM sqlite_master WHERE type='table' AND name='BENEFICIARY'").use { cursor ->
                    if (cursor.moveToFirst()) {
                        originalSql = cursor.getString(0)
                    }
                }
                
                if (originalSql.isNotEmpty()) {
                    var newSql = originalSql.replace("CREATE TABLE `BENEFICIARY`", "CREATE TABLE `BENEFICIARY_new`")
                    newSql = newSql.replace("CREATE TABLE BENEFICIARY", "CREATE TABLE BENEFICIARY_new")
                    newSql = newSql.replace("`householdId` INTEGER NOT NULL", "`householdId` INTEGER")
                    newSql = newSql.replace("householdId INTEGER NOT NULL", "householdId INTEGER")
                    
                    database.execSQL(newSql)
                    
                    database.execSQL("ALTER TABLE `BENEFICIARY_new` ADD COLUMN `isNonHH` INTEGER NOT NULL DEFAULT 0")
                    database.execSQL("ALTER TABLE `BENEFICIARY_new` ADD COLUMN `placeOfCurrentLiving` INTEGER DEFAULT NULL")
                    database.execSQL("ALTER TABLE `BENEFICIARY_new` ADD COLUMN `otherPlaceOfCurrentLiving` TEXT DEFAULT NULL")
                    database.execSQL("ALTER TABLE `BENEFICIARY_new` ADD COLUMN `institutionName` TEXT DEFAULT NULL")
                    
                    val columns = ArrayList<String>()
                    database.query("PRAGMA table_info(`BENEFICIARY`)").use { cursor ->
                        val nameIndex = cursor.getColumnIndex("name")
                        while (cursor.moveToNext()) {
                            columns.add(cursor.getString(nameIndex))
                        }
                    }
                    val columnsCsv = columns.joinToString(", ") { "`$it`" }
                    
                    database.execSQL("INSERT INTO `BENEFICIARY_new` ($columnsCsv) SELECT $columnsCsv FROM `BENEFICIARY`")
                    
                    val indexSqls = ArrayList<String>()
                    database.query("SELECT sql FROM sqlite_master WHERE type='index' AND tbl_name='BENEFICIARY' AND sql IS NOT NULL").use { cursor ->
                        while (cursor.moveToNext()) {
                            indexSqls.add(cursor.getString(0))
                        }
                    }
                    
                    database.execSQL("DROP TABLE `BENEFICIARY`")
                    database.execSQL("ALTER TABLE `BENEFICIARY_new` RENAME TO `BENEFICIARY`")
                    
                    for (indexSql in indexSqls) {
                        try {
                            database.execSQL(indexSql)
                        } catch (e: Exception) {
                            // in case the index already got created, ignore
                        }
                    }
                }
                recreateBenBasicCacheView(database)
            }
        }


        /*private val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP INDEX IF EXISTS `index_t_form_response_beneficiaryId`")
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_t_form_response_beneficiaryId_formVersionId` " +
                        "ON `t_form_response` (`beneficiaryId`, `formVersionId`)"
                )

                database.execSQL("DROP TABLE IF EXISTS `t_ct_question_response`")
                database.execSQL("DROP TABLE IF EXISTS `t_ct_section_response`")
                database.execSQL("DROP TABLE IF EXISTS `t_ct_response`")
            }
        }*/

        // TPT Follow-up's PRE_SUBMIT/POST_SUBMIT split needs the backend's own responseId
        // captured per response row, and its History feature caches read-only snapshot rows
        // directly in t_form_response (isHistorySnapshot = true) rather than a separate table —
        // which needs the prior unique (beneficiaryId, formVersionId) index relaxed, since a
        // beneficiary can now have any number of historical rows for the same form/version
        // alongside their one live row. Purely additive: no data loss, existing rows default to
        // backendResponseId = NULL, isHistorySnapshot = 0 (i.e. classified as live, correct for
        // every row that exists today).
        private val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(database: SupportSQLiteDatabase) {
                addTBScreeningRiskFactorColumns(database)

                if (!columnExists(database, "t_form_response", "backendResponseId")) {
                    database.execSQL("ALTER TABLE t_form_response ADD COLUMN backendResponseId INTEGER DEFAULT NULL")
                }
                if (!columnExists(database, "t_form_response", "isHistorySnapshot")) {
                    database.execSQL("ALTER TABLE t_form_response ADD COLUMN isHistorySnapshot INTEGER NOT NULL DEFAULT 0")
                }

                database.execSQL("DROP INDEX IF EXISTS `index_t_form_response_beneficiaryId_formVersionId`")
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_t_form_response_beneficiaryId_formVersionId_isHistorySnapshot` " +
                        "ON `t_form_response` (`beneficiaryId`, `formVersionId`, `isHistorySnapshot`)"
                )
            }
        }

        // Tracks the backend's own sectionResponseId on each locally-stored section response, so
        // ContactTracingRepositoryImpl.fetchAndRefreshTptHistory can detect a section already
        // bootstrapped from a previous API fetch and skip re-inserting it as a duplicate row.
        private val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(database: SupportSQLiteDatabase) {
                if (!columnExists(database, "t_section_response", "backendSectionResponseId")) {
                    database.execSQL("ALTER TABLE t_section_response ADD COLUMN backendSectionResponseId INTEGER DEFAULT NULL")
                }
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_t_section_response_backendSectionResponseId` " +
                        "ON `t_section_response` (`backendSectionResponseId`)"
                )
            }
        }

        private val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(database: SupportSQLiteDatabase) {
                var originalSql = ""
                database.query("SELECT sql FROM sqlite_master WHERE type='table' AND name='HOUSEHOLD'").use { cursor ->
                    if (cursor.moveToFirst()) {
                        originalSql = cursor.getString(0)
                    }
                }

                if (originalSql.isNotEmpty()) {
                    var newSql = originalSql.replace("CREATE TABLE `HOUSEHOLD`", "CREATE TABLE `HOUSEHOLD_new`")
                    newSql = newSql.replace("CREATE TABLE HOUSEHOLD", "CREATE TABLE HOUSEHOLD_new")
                    newSql = newSql.replace("fam_totalHhMembers INTEGER DEFAULT NULL", "fam_totalHhMembers INTEGER")
                    newSql = newSql.replace("fam_isRegisteredAtCampSite TEXT DEFAULT NULL", "fam_isRegisteredAtCampSite TEXT")
                    newSql = newSql.replace("fam_isRegisteredAtCampSiteId INTEGER DEFAULT 0", "fam_isRegisteredAtCampSiteId INTEGER")

                    database.execSQL(newSql)

                    val columns = ArrayList<String>()
                    database.query("PRAGMA table_info(`HOUSEHOLD`)").use { cursor ->
                        val nameIndex = cursor.getColumnIndex("name")
                        while (cursor.moveToNext()) {
                            columns.add(cursor.getString(nameIndex))
                        }
                    }
                    val columnsCsv = columns.joinToString(", ") { "`$it`" }

                    database.execSQL("INSERT INTO `HOUSEHOLD_new` ($columnsCsv) SELECT $columnsCsv FROM `HOUSEHOLD`")

                    val indexSqls = ArrayList<String>()
                    database.query("SELECT sql FROM sqlite_master WHERE type='index' AND tbl_name='HOUSEHOLD' AND sql IS NOT NULL").use { cursor ->
                        while (cursor.moveToNext()) {
                            indexSqls.add(cursor.getString(0))
                        }
                    }

                    database.execSQL("DROP TABLE `HOUSEHOLD`")
                    database.execSQL("ALTER TABLE `HOUSEHOLD_new` RENAME TO `HOUSEHOLD`")

                    for (indexSql in indexSqls) {
                        try {
                            database.execSQL(indexSql)
                        } catch (e: Exception) {
                            // in case the index already got created, ignore
                        }
                    }
                }
            }
        }

        private val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val householdFamilyColumns = listOf(
                    "fam_address TEXT",
                    "fam_pinCode TEXT"
                )
                householdFamilyColumns.forEach { columnDefinition ->
                    val columnName = columnDefinition.substringBefore(" ")
                    if (!columnExists(database, "HOUSEHOLD", columnName)) {
                        database.execSQL("ALTER TABLE HOUSEHOLD ADD COLUMN $columnDefinition")
                    }
                }
            }
        }

        private val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val existingColumns = ArrayList<String>()
                database.query("PRAGMA table_info(`BENEFICIARY`)").use { cursor ->
                    val nameIndex = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        existingColumns.add(cursor.getString(nameIndex))
                    }
                }

                var householdIdNotNull = false
                database.query("PRAGMA table_info(`BENEFICIARY`)").use { cursor ->
                    val nameIndex = cursor.getColumnIndex("name")
                    val notNullIndex = cursor.getColumnIndex("notnull")
                    while (cursor.moveToNext()) {
                        if (cursor.getString(nameIndex) == "householdId") {
                            householdIdNotNull = cursor.getInt(notNullIndex) == 1
                        }
                    }
                }

                val missingNullableColumns = listOf(
                    "placeOfCurrentLiving" to "INTEGER",
                    "otherPlaceOfCurrentLiving" to "TEXT",
                    "institutionName" to "TEXT"
                ).filter { !existingColumns.contains(it.first) }

                missingNullableColumns.forEach { (name, type) ->
                    database.execSQL("ALTER TABLE BENEFICIARY ADD COLUMN $name $type")
                    existingColumns.add(name)
                }

                val needsIsNonHH = !existingColumns.contains("isNonHH")
                if (!needsIsNonHH && !householdIdNotNull) {
                    return
                }

                var originalSql = ""
                database.query("SELECT sql FROM sqlite_master WHERE type='table' AND name='BENEFICIARY'").use { cursor ->
                    if (cursor.moveToFirst()) {
                        originalSql = cursor.getString(0)
                    }
                }

                if (originalSql.isEmpty()) return

                var newSql = originalSql.replace("CREATE TABLE `BENEFICIARY`", "CREATE TABLE `BENEFICIARY_new`")
                newSql = newSql.replace("CREATE TABLE BENEFICIARY", "CREATE TABLE BENEFICIARY_new")
                newSql = newSql.replace("`householdId` INTEGER NOT NULL", "`householdId` INTEGER")
                newSql = newSql.replace("householdId INTEGER NOT NULL", "householdId INTEGER")

                if (needsIsNonHH) {
                    // A new column def must be inserted before the trailing `PRIMARY KEY(...)`
                    // table constraint — SQLite's grammar doesn't allow a column def after it.
                    val primaryKeyIndex = newSql.indexOf(", PRIMARY KEY(")
                    newSql = if (primaryKeyIndex >= 0) {
                        newSql.substring(0, primaryKeyIndex) + ", `isNonHH` INTEGER NOT NULL" + newSql.substring(primaryKeyIndex)
                    } else {
                        val lastParen = newSql.lastIndexOf(')')
                        newSql.substring(0, lastParen) + ", `isNonHH` INTEGER NOT NULL" + newSql.substring(lastParen)
                    }
                }

                database.execSQL(newSql)

                val columnsCsv = existingColumns.joinToString(", ") { "`$it`" }
                val selectCsv = if (needsIsNonHH) "$columnsCsv, 0" else columnsCsv
                val insertCsv = if (needsIsNonHH) "$columnsCsv, `isNonHH`" else columnsCsv

                database.execSQL("INSERT INTO `BENEFICIARY_new` ($insertCsv) SELECT $selectCsv FROM `BENEFICIARY`")

                val indexSqls = ArrayList<String>()
                database.query("SELECT sql FROM sqlite_master WHERE type='index' AND tbl_name='BENEFICIARY' AND sql IS NOT NULL").use { cursor ->
                    while (cursor.moveToNext()) {
                        indexSqls.add(cursor.getString(0))
                    }
                }

                database.execSQL("DROP TABLE `BENEFICIARY`")
                database.execSQL("ALTER TABLE `BENEFICIARY_new` RENAME TO `BENEFICIARY`")

                for (indexSql in indexSqls) {
                    try {
                        database.execSQL(indexSql)
                    } catch (e: Exception) {
                        // in case the index already got created, ignore
                    }
                }
            }
        }

        private val MIGRATION_30_31 = object : Migration(30, 31) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val dynamicFormColumns = listOf(
                    "definition TEXT",
                    "triggerRuleJson TEXT",
                    "globalRuleJson TEXT",
                    "enabledIfJson TEXT"
                )
                dynamicFormColumns.forEach { columnDefinition ->
                    val columnName = columnDefinition.substringBefore(" ")
                    if (!columnExists(database, "t_dynamic_form", columnName)) {
                        database.execSQL("ALTER TABLE t_dynamic_form ADD COLUMN $columnDefinition")
                    }
                }
            }
        }

        private val MIGRATION_31_32 = object : Migration(31, 32) {
            override fun migrate(database: SupportSQLiteDatabase) {
                if (columnExists(database, "t_form_section", "hasSubmitButton")) {
                    return
                }

                var originalSql = ""
                database.query("SELECT sql FROM sqlite_master WHERE type='table' AND name='t_form_section'").use { cursor ->
                    if (cursor.moveToFirst()) {
                        originalSql = cursor.getString(0)
                    }
                }
                if (originalSql.isEmpty()) return

                var newSql = originalSql.replace("CREATE TABLE `t_form_section`", "CREATE TABLE `t_form_section_new`")
                newSql = newSql.replace("CREATE TABLE t_form_section", "CREATE TABLE t_form_section_new")

                val primaryKeyIndex = newSql.indexOf(", PRIMARY KEY(")
                newSql = if (primaryKeyIndex >= 0) {
                    newSql.substring(0, primaryKeyIndex) + ", `hasSubmitButton` INTEGER NOT NULL" + newSql.substring(primaryKeyIndex)
                } else {
                    val lastParen = newSql.lastIndexOf(')')
                    newSql.substring(0, lastParen) + ", `hasSubmitButton` INTEGER NOT NULL" + newSql.substring(lastParen)
                }

                database.execSQL(newSql)

                val columns = ArrayList<String>()
                database.query("PRAGMA table_info(`t_form_section`)").use { cursor ->
                    val nameIndex = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        columns.add(cursor.getString(nameIndex))
                    }
                }
                val columnsCsv = columns.joinToString(", ") { "`$it`" }

                database.execSQL("INSERT INTO `t_form_section_new` ($columnsCsv, `hasSubmitButton`) SELECT $columnsCsv, 0 FROM `t_form_section`")

                val indexSqls = ArrayList<String>()
                database.query("SELECT sql FROM sqlite_master WHERE type='index' AND tbl_name='t_form_section' AND sql IS NOT NULL").use { cursor ->
                    while (cursor.moveToNext()) {
                        indexSqls.add(cursor.getString(0))
                    }
                }

                database.execSQL("DROP TABLE `t_form_section`")
                database.execSQL("ALTER TABLE `t_form_section_new` RENAME TO `t_form_section`")

                for (indexSql in indexSqls) {
                    try {
                        database.execSQL(indexSql)
                    } catch (e: Exception) {
                        // in case the index already got created, ignore
                    }
                }
            }
        }

        private val MIGRATION_32_33 = object : Migration(32, 33) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val tbScreeningColumns = listOf(
                    "referralRequired INTEGER",
                    "referralFor TEXT",
                    "latitude REAL",
                    "longitude REAL",
                    "familyContactScreeningRequired INTEGER"
                )
                tbScreeningColumns.forEach { columnDefinition ->
                    val columnName = columnDefinition.substringBefore(" ")
                    if (!columnExists(database, "TB_SCREENING", columnName)) {
                        database.execSQL("ALTER TABLE TB_SCREENING ADD COLUMN $columnDefinition")
                    }
                }

                val tbSuspectedColumns = listOf(
                    "isAICoughAssessmentDone INTEGER",
                    "aiCoughAssessmentResult TEXT",
                    "otherReasonForSuspicion TEXT",
                    "latitude REAL",
                    "longitude REAL",
                    "address TEXT"
                )
                tbSuspectedColumns.forEach { columnDefinition ->
                    val columnName = columnDefinition.substringBefore(" ")
                    if (!columnExists(database, "TB_SUSPECTED", columnName)) {
                        database.execSQL("ALTER TABLE TB_SUSPECTED ADD COLUMN $columnDefinition")
                    }
                }
            }
        }

        private val MIGRATION_33_34 = object : Migration(33, 34) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val nullableColumns = listOf(
                    "maxLength INTEGER",
                    "enabledIfJson TEXT",
                    "disabledIfJson TEXT",
                    "mandatoryIfJson TEXT",
                    "autoPopulateLogic TEXT",
                    "autoPopulateNote TEXT",
                    "unit TEXT",
                    "exampleValuesJson TEXT",
                    "note TEXT",
                    "displayFormat TEXT"
                )
                nullableColumns.forEach { columnDefinition ->
                    val columnName = columnDefinition.substringBefore(" ")
                    if (!columnExists(database, "t_section_question", columnName)) {
                        database.execSQL("ALTER TABLE t_section_question ADD COLUMN $columnDefinition")
                    }
                }

                val notNullDefaults = listOf(
                    Triple("allowMultiple", "INTEGER NOT NULL", "0"),
                    Triple("containsPii", "INTEGER NOT NULL", "0"),
                    Triple("visibleByDefault", "INTEGER NOT NULL", "1"),
                    Triple("autoPopulated", "INTEGER NOT NULL", "0")
                ).filter { (name, _, _) -> !columnExists(database, "t_section_question", name) }

                if (notNullDefaults.isEmpty()) return

                var originalSql = ""
                database.query("SELECT sql FROM sqlite_master WHERE type='table' AND name='t_section_question'").use { cursor ->
                    if (cursor.moveToFirst()) {
                        originalSql = cursor.getString(0)
                    }
                }
                if (originalSql.isEmpty()) return

                var newSql = originalSql.replace("CREATE TABLE `t_section_question`", "CREATE TABLE `t_section_question_new`")
                newSql = newSql.replace("CREATE TABLE t_section_question", "CREATE TABLE t_section_question_new")

                val newColumnDefs = notNullDefaults.joinToString(", ") { (name, type, _) -> "`$name` $type" }
                val primaryKeyIndex = newSql.indexOf(", PRIMARY KEY(")
                newSql = if (primaryKeyIndex >= 0) {
                    newSql.substring(0, primaryKeyIndex) + ", $newColumnDefs" + newSql.substring(primaryKeyIndex)
                } else {
                    val lastParen = newSql.lastIndexOf(')')
                    newSql.substring(0, lastParen) + ", $newColumnDefs" + newSql.substring(lastParen)
                }

                database.execSQL(newSql)

                val columns = ArrayList<String>()
                database.query("PRAGMA table_info(`t_section_question`)").use { cursor ->
                    val nameIndex = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        columns.add(cursor.getString(nameIndex))
                    }
                }
                val columnsCsv = columns.joinToString(", ") { "`$it`" }
                val newColumnsCsv = notNullDefaults.joinToString(", ") { "`${it.first}`" }
                val newValuesCsv = notNullDefaults.joinToString(", ") { it.third }

                database.execSQL(
                    "INSERT INTO `t_section_question_new` ($columnsCsv, $newColumnsCsv) SELECT $columnsCsv, $newValuesCsv FROM `t_section_question`"
                )

                val indexSqls = ArrayList<String>()
                database.query("SELECT sql FROM sqlite_master WHERE type='index' AND tbl_name='t_section_question' AND sql IS NOT NULL").use { cursor ->
                    while (cursor.moveToNext()) {
                        indexSqls.add(cursor.getString(0))
                    }
                }

                database.execSQL("DROP TABLE `t_section_question`")
                database.execSQL("ALTER TABLE `t_section_question_new` RENAME TO `t_section_question`")

                for (indexSql in indexSqls) {
                    try {
                        database.execSQL(indexSql)
                    } catch (e: Exception) {
                        // in case the index already got created, ignore
                    }
                }
            }
        }

        private val MIGRATION_34_35 = object : Migration(34, 35) {
            override fun migrate(database: SupportSQLiteDatabase) {
                if (columnExists(database, "t_question_option", "isExclusive")) {
                    return
                }

                var originalSql = ""
                database.query("SELECT sql FROM sqlite_master WHERE type='table' AND name='t_question_option'").use { cursor ->
                    if (cursor.moveToFirst()) {
                        originalSql = cursor.getString(0)
                    }
                }
                if (originalSql.isEmpty()) return

                var newSql = originalSql.replace("CREATE TABLE `t_question_option`", "CREATE TABLE `t_question_option_new`")
                newSql = newSql.replace("CREATE TABLE t_question_option", "CREATE TABLE t_question_option_new")

                val primaryKeyIndex = newSql.indexOf(", PRIMARY KEY(")
                newSql = if (primaryKeyIndex >= 0) {
                    newSql.substring(0, primaryKeyIndex) + ", `isExclusive` INTEGER NOT NULL" + newSql.substring(primaryKeyIndex)
                } else {
                    val lastParen = newSql.lastIndexOf(')')
                    newSql.substring(0, lastParen) + ", `isExclusive` INTEGER NOT NULL" + newSql.substring(lastParen)
                }

                database.execSQL(newSql)

                val columns = ArrayList<String>()
                database.query("PRAGMA table_info(`t_question_option`)").use { cursor ->
                    val nameIndex = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        columns.add(cursor.getString(nameIndex))
                    }
                }
                val columnsCsv = columns.joinToString(", ") { "`$it`" }

                database.execSQL("INSERT INTO `t_question_option_new` ($columnsCsv, `isExclusive`) SELECT $columnsCsv, 0 FROM `t_question_option`")

                val indexSqls = ArrayList<String>()
                database.query("SELECT sql FROM sqlite_master WHERE type='index' AND tbl_name='t_question_option' AND sql IS NOT NULL").use { cursor ->
                    while (cursor.moveToNext()) {
                        indexSqls.add(cursor.getString(0))
                    }
                }

                database.execSQL("DROP TABLE `t_question_option`")
                database.execSQL("ALTER TABLE `t_question_option_new` RENAME TO `t_question_option`")

                for (indexSql in indexSqls) {
                    try {
                        database.execSQL(indexSql)
                    } catch (e: Exception) {
                        // in case the index already got created, ignore
                    }
                }
            }
        }

        private val MIGRATION_35_36 = object : Migration(35, 36) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val columns = listOf(
                    "targetFormUuid TEXT",
                    "alertMessage TEXT",
                    "targetList TEXT",
                    "actionValue TEXT",
                    "note TEXT",
                    "reEnableCondition TEXT"
                )
                columns.forEach { columnDefinition ->
                    val columnName = columnDefinition.substringBefore(" ")
                    if (!columnExists(database, "t_option_condition", columnName)) {
                        database.execSQL("ALTER TABLE t_option_condition ADD COLUMN $columnDefinition")
                    }
                }
            }
        }

        private val MIGRATION_36_37 = object : Migration(36, 37) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP INDEX IF EXISTS `index_t_form_response_beneficiaryId`")

                var backendResponseIdHasDefault = false
                var isHistorySnapshotHasDefault = false
                database.query("PRAGMA table_info(`t_form_response`)").use { cursor ->
                    val nameIndex = cursor.getColumnIndex("name")
                    val dfltIndex = cursor.getColumnIndex("dflt_value")
                    while (cursor.moveToNext()) {
                        when (cursor.getString(nameIndex)) {
                            "backendResponseId" -> backendResponseIdHasDefault = cursor.getString(dfltIndex) != null
                            "isHistorySnapshot" -> isHistorySnapshotHasDefault = cursor.getString(dfltIndex) != null
                        }
                    }
                }

                if (!backendResponseIdHasDefault && !isHistorySnapshotHasDefault) {
                    return
                }

                var originalSql = ""
                database.query("SELECT sql FROM sqlite_master WHERE type='table' AND name='t_form_response'").use { cursor ->
                    if (cursor.moveToFirst()) {
                        originalSql = cursor.getString(0)
                    }
                }
                if (originalSql.isEmpty()) return

                var newSql = originalSql.replace("CREATE TABLE `t_form_response`", "CREATE TABLE `t_form_response_new`")
                newSql = newSql.replace("CREATE TABLE t_form_response", "CREATE TABLE t_form_response_new")
                newSql = newSql.replace("`backendResponseId` INTEGER DEFAULT NULL", "`backendResponseId` INTEGER")
                newSql = newSql.replace("backendResponseId INTEGER DEFAULT NULL", "backendResponseId INTEGER")
                newSql = newSql.replace("`isHistorySnapshot` INTEGER NOT NULL DEFAULT 0", "`isHistorySnapshot` INTEGER NOT NULL")
                newSql = newSql.replace("isHistorySnapshot INTEGER NOT NULL DEFAULT 0", "isHistorySnapshot INTEGER NOT NULL")

                database.execSQL(newSql)

                val columns = ArrayList<String>()
                database.query("PRAGMA table_info(`t_form_response`)").use { cursor ->
                    val nameIndex = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        columns.add(cursor.getString(nameIndex))
                    }
                }
                val columnsCsv = columns.joinToString(", ") { "`$it`" }

                database.execSQL("INSERT INTO `t_form_response_new` ($columnsCsv) SELECT $columnsCsv FROM `t_form_response`")

                val indexSqls = ArrayList<String>()
                database.query(
                    "SELECT sql FROM sqlite_master WHERE type='index' AND tbl_name='t_form_response' AND sql IS NOT NULL " +
                        "AND name != 'index_t_form_response_beneficiaryId'"
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        indexSqls.add(cursor.getString(0))
                    }
                }

                database.execSQL("DROP TABLE `t_form_response`")
                database.execSQL("ALTER TABLE `t_form_response_new` RENAME TO `t_form_response`")

                for (indexSql in indexSqls) {
                    try {
                        database.execSQL(indexSql)
                    } catch (e: Exception) {
                        // in case the index already got created, ignore
                    }
                }
            }
        }

        private val MIGRATION_37_38 = object : Migration(37, 38) {
            override fun migrate(database: SupportSQLiteDatabase) {
                recreateBenBasicCacheView(database)
            }
        }

        private val MIGRATION_38_39 = object : Migration(38, 39) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP VIEW IF EXISTS `COUNSELLING_FORM_RESPONSE`")
                database.execSQL(
                    "CREATE VIEW `COUNSELLING_FORM_RESPONSE` AS " + """
        SELECT r.beneficiaryId AS beneficiaryId, r.status AS status,
               r.sectionsFilled AS sectionsFilled, r.totalSections AS totalSections
        FROM t_form_response r
        JOIN t_form_version v ON r.formVersionId = v.versionId
        JOIN t_dynamic_form f ON v.formId = f.formId
        WHERE f.formType IN ('TB_COUNSELLING', 'TB_COUNSELLING_V2')
        GROUP BY r.beneficiaryId
    """.trim()
                )
            }
        }
        private val MIGRATION_39_40 = object : Migration(39, 40) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val columns = listOf(
                    "fam_totalHhMembers INTEGER",
                    "fam_isRegisteredAtCampSite TEXT",
                    "fam_isRegisteredAtCampSiteId INTEGER"
                )
                columns.forEach { columnDefinition ->
                    val columnName = columnDefinition.substringBefore(" ")
                    if (!columnExists(database, "HOUSEHOLD", columnName)) {
                        database.execSQL("ALTER TABLE HOUSEHOLD ADD COLUMN $columnDefinition")
                    }
                }
            }
        }

        private val MIGRATION_40_41 = object : Migration(40,41){
            override fun migrate(db: SupportSQLiteDatabase) {
                addLocationRecordExtraColumns(db, "BENEFICIARY")
                addLocationRecordExtraColumns(db, "HOUSEHOLD")
            }
        }

        private val MIGRATION_41_42 = object : Migration(41, 42) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val columns = listOf(
                    "xrayOrderId TEXT",
                    "xrayOrderStatus TEXT",
                    "trueNatOrderId TEXT",
                    "trueNatOrderStatus TEXT",
                    "trueNatRifResult TEXT"
                )
                columns.forEach { columnDefinition ->
                    val columnName = columnDefinition.substringBefore(" ")
                    if (!columnExists(database, "TB_DIAGNOSTICS", columnName)) {
                        database.execSQL("ALTER TABLE TB_DIAGNOSTICS ADD COLUMN $columnDefinition")
                    }
                }

                var rifOrderIdHasDefault = false
                var rifOrderStatusHasDefault = false
                database.query("PRAGMA table_info(`TB_DIAGNOSTICS`)").use { cursor ->
                    val nameIndex = cursor.getColumnIndex("name")
                    val dfltIndex = cursor.getColumnIndex("dflt_value")
                    while (cursor.moveToNext()) {
                        when (cursor.getString(nameIndex)) {
                            "rifOrderId" -> rifOrderIdHasDefault = cursor.getString(dfltIndex) != null
                            "rifOrderStatus" -> rifOrderStatusHasDefault = cursor.getString(dfltIndex) != null
                        }
                    }
                }

                if (!rifOrderIdHasDefault && !rifOrderStatusHasDefault) {
                    return
                }

                var originalSql = ""
                database.query("SELECT sql FROM sqlite_master WHERE type='table' AND name='TB_DIAGNOSTICS'").use { cursor ->
                    if (cursor.moveToFirst()) {
                        originalSql = cursor.getString(0)
                    }
                }
                if (originalSql.isEmpty()) return

                var newSql = originalSql.replace("CREATE TABLE `TB_DIAGNOSTICS`", "CREATE TABLE `TB_DIAGNOSTICS_new`")
                newSql = newSql.replace("CREATE TABLE TB_DIAGNOSTICS", "CREATE TABLE TB_DIAGNOSTICS_new")
                newSql = newSql.replace("`rifOrderId` TEXT DEFAULT NULL", "`rifOrderId` TEXT")
                newSql = newSql.replace("rifOrderId TEXT DEFAULT NULL", "rifOrderId TEXT")
                newSql = newSql.replace("`rifOrderStatus` TEXT DEFAULT NULL", "`rifOrderStatus` TEXT")
                newSql = newSql.replace("rifOrderStatus TEXT DEFAULT NULL", "rifOrderStatus TEXT")

                database.execSQL(newSql)

                val columnsList = ArrayList<String>()
                database.query("PRAGMA table_info(`TB_DIAGNOSTICS`)").use { cursor ->
                    val nameIndex = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        columnsList.add(cursor.getString(nameIndex))
                    }
                }
                val columnsCsv = columnsList.joinToString(", ") { "`$it`" }

                database.execSQL("INSERT INTO `TB_DIAGNOSTICS_new` ($columnsCsv) SELECT $columnsCsv FROM `TB_DIAGNOSTICS`")

                val indexSqls = ArrayList<String>()
                database.query(
                    "SELECT sql FROM sqlite_master WHERE type='index' AND tbl_name='TB_DIAGNOSTICS' AND sql IS NOT NULL"
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        indexSqls.add(cursor.getString(0))
                    }
                }

                database.execSQL("DROP TABLE `TB_DIAGNOSTICS`")
                database.execSQL("ALTER TABLE `TB_DIAGNOSTICS_new` RENAME TO `TB_DIAGNOSTICS`")

                for (indexSql in indexSqls) {
                    try {
                        database.execSQL(indexSql)
                    } catch (e: Exception) {
                        // in case the index already got created, ignore
                    }
                }
            }
        }

        private fun recreateBenBasicCacheView(database: SupportSQLiteDatabase) {
            database.execSQL("DROP VIEW IF EXISTS `BEN_BASIC_CACHE`")
            database.execSQL(
                "CREATE VIEW `BEN_BASIC_CACHE` AS SELECT b.beneficiaryId as benId,b.isMarried,b.noOfAliveChildren, b.noOfChildren, b.doYouHavechildren ,b.isConsent as isConsent, b.motherName as motherName, b.householdId as hhId, b.regDate, b.firstName as benName, b.lastName as benSurname, b.gender, b.dob as dob,b.isDeactivate, b.isDeath,b.isDeathValue,b.dateOfDeath,b.timeOfDeath,b.reasonOfDeath,b.reasonOfDeathId,b.placeOfDeath,b.placeOfDeathId,b.otherPlaceOfDeath,b.isSpouseAdded,b.isChildrenAdded, b.familyHeadRelationPosition as relToHeadId" +
                    ", b.contactNumber as mobileNo, b.fatherName,IFNULL(h.fam_familyHeadName,'') as familyHeadName, b.gen_spouseName as spouseName, b.rchId, b.nikshayId, b.gen_lastMenstrualPeriod as lastMenstrualPeriod" +
                    ", b.isHrpStatus as hrpStatus, b.syncState, b.gen_reproductiveStatusId as reproductiveStatusId, b.isKid, b.immunizationStatus" +
                    ", b.loc_village_id as villageId, b.abha_healthIdNumber as abhaId" +
                    ", b.isNewAbha" +
                    ", IFNULL(cbac.benId IS NOT NULL, 0) as cbacFilled, cbac.syncState as cbacSyncState" +
                    ", 0 as cdrFilled, NULL as cdrSyncState" +
                    ", 0 as mdsrFilled, NULL as mdsrSyncState" +
                    ", NULL as pmsmaSyncState, 0 as pmsmaFilled" +
                    ", 0 as hbncFilled" +
                    ", 0 as hbycFilled" +
                    ", 0 as pwrFilled, NULL as pwrSyncState" +
                    ", NULL as doSyncState, NULL as irSyncState, NULL as crSyncState" +
                    ", 0 as ecrFilled, 0 as ectFilled" +
                    ", 0 as isMdsr" +
                    ", IFNULL(tbsn.benId IS NOT NULL, 0) as tbsnFilled, tbsn.syncState as tbsnSyncState" +
                    ", IFNULL(tbsp.benId IS NOT NULL, 0) as tbspFilled, tbsp.syncState as tbspSyncState" +
                    ", 0 as hrppaFilled, 0 as hrpnpaFilled, 0 as hrpmbpFilled" +
                    ", 0 as hrptFilled, 0 as hrptrackingDone, 0 as hrnptrackingDone, 0 as hrnptFilled" +
                    ", NULL as hrppaSyncState, NULL as hrpnpaSyncState, NULL as hrpmbpSyncState, NULL as hrptSyncState, NULL as hrnptSyncState" +
                    ", 0 as isDelivered, 0 as pwHrp" +
                    ", 0 as irFilled, 0 as crFilled, 0 as doFilled" +
                    ", b.isNonHH" +
                    ", b.placeOfCurrentLiving" +
                    ", b.otherPlaceOfCurrentLiving" +
                    ", b.institutionName" +
                    " FROM BENEFICIARY b " +
                    "LEFT JOIN HOUSEHOLD h ON b.householdId = h.householdId " +
                    "LEFT OUTER JOIN CBAC cbac ON b.beneficiaryId = cbac.benId " +
                    "LEFT OUTER JOIN TB_SCREENING tbsn ON b.beneficiaryId = tbsn.benId " +
                    "LEFT OUTER JOIN TB_SUSPECTED tbsp ON b.beneficiaryId = tbsp.benId " +
                    "WHERE b.isDraft = 0 GROUP BY b.beneficiaryId ORDER BY b.updatedDate DESC"
            )
        }

        private val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(database: SupportSQLiteDatabase) {

                val householdFamilyColumns = listOf(
                    "fam_totalHhMembers INTEGER DEFAULT NULL",
                    "fam_isRegisteredAtCampSite TEXT DEFAULT NULL",
                    "fam_isRegisteredAtCampSiteId INTEGER DEFAULT 0"
                )
                householdFamilyColumns.forEach { columnDefinition ->
                    val columnName = columnDefinition.substringBefore(" ")
                    if (!columnExists(database, "HOUSEHOLD", columnName)) {
                        database.execSQL("ALTER TABLE HOUSEHOLD ADD COLUMN $columnDefinition")
                    }
                }

                if (!columnExists(database, "TB_DIAGNOSTICS", "xrayOrderId")) {
                    database.execSQL("ALTER TABLE TB_DIAGNOSTICS ADD COLUMN xrayOrderId TEXT DEFAULT NULL")
                }
                if (!columnExists(database, "TB_DIAGNOSTICS", "xrayOrderStatus")) {
                    database.execSQL("ALTER TABLE TB_DIAGNOSTICS ADD COLUMN xrayOrderStatus TEXT DEFAULT NULL")
                }
                if (!columnExists(database, "TB_DIAGNOSTICS", "trueNatOrderId")) {
                    database.execSQL("ALTER TABLE TB_DIAGNOSTICS ADD COLUMN trueNatOrderId TEXT DEFAULT NULL")
                }
                if (!columnExists(database, "TB_DIAGNOSTICS", "trueNatOrderStatus")) {
                    database.execSQL("ALTER TABLE TB_DIAGNOSTICS ADD COLUMN trueNatOrderStatus TEXT DEFAULT NULL")
                }
                if (!columnExists(database, "TB_DIAGNOSTICS", "trueNatRifResult")) {
                    database.execSQL("ALTER TABLE TB_DIAGNOSTICS ADD COLUMN trueNatRifResult TEXT DEFAULT NULL")
                }
            }
        }

        private val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(database: SupportSQLiteDatabase) {
                /*database.execSQL("DROP INDEX IF EXISTS `index_t_form_response_beneficiaryId`")
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_t_form_response_beneficiaryId_formVersionId` " +
                            "ON `t_form_response` (`beneficiaryId`, `formVersionId`)"
                )*/
                if (!columnExists(database, "TB_DIAGNOSTICS", "rifOrderId")) {
                    database.execSQL("ALTER TABLE TB_DIAGNOSTICS ADD COLUMN rifOrderId TEXT DEFAULT NULL")
                }
                if (!columnExists(database, "TB_DIAGNOSTICS", "rifOrderStatus")) {
                    database.execSQL("ALTER TABLE TB_DIAGNOSTICS ADD COLUMN rifOrderStatus TEXT DEFAULT NULL")
                }
            }
        }

        private fun addVitalGeneralExaminationColumns(database: SupportSQLiteDatabase) {
            val columns = listOf(
                "benRegId INTEGER NOT NULL DEFAULT 0",
                "pallorId INTEGER DEFAULT NULL",
                "pallor TEXT DEFAULT NULL",
                "icterusId INTEGER DEFAULT NULL",
                "icterus TEXT DEFAULT NULL",
                "cyanosisId INTEGER DEFAULT NULL",
                "cyanosis TEXT DEFAULT NULL",
                "clubbingId INTEGER DEFAULT NULL",
                "clubbing TEXT DEFAULT NULL",
                "lymphadenopathyId INTEGER DEFAULT NULL",
                "lymphadenopathy TEXT DEFAULT NULL",
                "oedemaId INTEGER DEFAULT NULL",
                "oedema TEXT DEFAULT NULL",
                "keyPopulationRiskFactorIds TEXT DEFAULT NULL",
                "keyPopulationRiskFactors TEXT DEFAULT NULL",
                "hivStatusId INTEGER DEFAULT NULL",
                "hivStatus TEXT DEFAULT NULL",
                "referralToHwcNeeded INTEGER DEFAULT NULL",
                "referralTriggers TEXT DEFAULT NULL"
            )
            columns.forEach { columnDefinition ->
                val columnName = columnDefinition.substringBefore(" ")
                if (!columnExists(database, "BEN_VITALS", columnName)) {
                    database.execSQL("ALTER TABLE BEN_VITALS ADD COLUMN $columnDefinition")
                }
            }
        }

        private fun addVitalGeneralExaminationIdColumns(database: SupportSQLiteDatabase) {
            val columns = listOf(
                "pallorId INTEGER DEFAULT NULL",
                "icterusId INTEGER DEFAULT NULL",
                "cyanosisId INTEGER DEFAULT NULL",
                "clubbingId INTEGER DEFAULT NULL",
                "lymphadenopathyId INTEGER DEFAULT NULL",
                "oedemaId INTEGER DEFAULT NULL",
                "keyPopulationRiskFactorIds TEXT DEFAULT NULL",
                "hivStatusId INTEGER DEFAULT NULL"
            )
            columns.forEach { columnDefinition ->
                val columnName = columnDefinition.substringBefore(" ")
                if (!columnExists(database, "BEN_VITALS", columnName)) {
                    database.execSQL("ALTER TABLE BEN_VITALS ADD COLUMN $columnDefinition")
                }
            }
        }

        private fun addTBScreeningReferralColumns(database: SupportSQLiteDatabase) {
            val columns = listOf(
                "referredForDigitalChestXray INTEGER DEFAULT NULL",
                "referredForSputumCollection INTEGER DEFAULT NULL",
                "sputumSampleSubmittedAt TEXT DEFAULT NULL",
                "recommendedForTruenatTest INTEGER DEFAULT NULL",
                "recommendedForLiquidCultureTest INTEGER DEFAULT NULL",
                "reasonForDenialForGettingTested TEXT DEFAULT NULL"
            )
            columns.forEach { columnDefinition ->
                val columnName = columnDefinition.substringBefore(" ")
                if (!columnExists(database, "TB_SCREENING", columnName)) {
                    database.execSQL("ALTER TABLE TB_SCREENING ADD COLUMN $columnDefinition")
                }
            }
        }

        /*private val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(database: SupportSQLiteDatabase) {
                addTBScreeningRiskFactorColumns(database)
            }
        }*/

        private fun addTBScreeningRiskFactorColumns(database: SupportSQLiteDatabase) {
            val columns = listOf(
                "keyPopulationRiskFactorIds TEXT DEFAULT NULL",
                "keyPopulationRiskFactors TEXT DEFAULT NULL",
                "hivStatusId INTEGER DEFAULT NULL",
                "hivStatus TEXT DEFAULT NULL"
            )
            columns.forEach { columnDefinition ->
                val columnName = columnDefinition.substringBefore(" ")
                if (!columnExists(database, "TB_SCREENING", columnName)) {
                    database.execSQL("ALTER TABLE TB_SCREENING ADD COLUMN $columnDefinition")
                }
            }
        }

        private fun addLocationRecordExtraColumns(
            database: SupportSQLiteDatabase,
            tableName: String
        ) {
            val columns = listOf(
                "loc_tu_id INTEGER DEFAULT NULL",
                "loc_tu_name TEXT DEFAULT NULL",
                "loc_tu_nameHindi TEXT DEFAULT NULL",
                "loc_tu_nameAssamese TEXT DEFAULT NULL",
                "loc_healthFacility_id INTEGER DEFAULT NULL",
                "loc_healthFacility_name TEXT DEFAULT NULL",
                "loc_healthFacility_nameHindi TEXT DEFAULT NULL",
                "loc_healthFacility_nameAssamese TEXT DEFAULT NULL"
            )
            columns.forEach { columnDefinition ->
                val columnName = columnDefinition.substringBefore(" ")
                if (!columnExists(database, tableName, columnName)) {
                    database.execSQL("ALTER TABLE $tableName ADD COLUMN $columnDefinition")
                }
            }
        }

        fun getInstance(appContext: Context): InAppDb {

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
                        .addMigrations(MIGRATION_2_3)
                        .addMigrations(MIGRATION_3_4)
                        .addMigrations(MIGRATION_4_5)
                        .addMigrations(MIGRATION_5_6)
                        .addMigrations(MIGRATION_6_7)
                        .addMigrations(MIGRATION_7_8)
                        .addMigrations(MIGRATION_8_9)
                        .addMigrations(MIGRATION_9_10)
                        .addMigrations(MIGRATION_10_11)
                        .addMigrations(MIGRATION_11_12)
                        .addMigrations(MIGRATION_12_13)
                        .addMigrations(MIGRATION_13_14)
                        .addMigrations(MIGRATION_14_15)
                        .addMigrations(MIGRATION_15_16)
                        .addMigrations(MIGRATION_16_17)
                        .addMigrations(MIGRATION_17_18)
                        .addMigrations(MIGRATION_18_19)
                        .addMigrations(MIGRATION_19_20)
                        .addMigrations(MIGRATION_20_21)
                        .addMigrations(MIGRATION_21_22)
                        .addMigrations(MIGRATION_22_23)
                        .addMigrations(MIGRATION_23_24)
                        .addMigrations(MIGRATION_24_25)
                        .addMigrations(MIGRATION_25_26)
                        .addMigrations(MIGRATION_26_27)
                        .addMigrations(MIGRATION_27_28)
                        .addMigrations(MIGRATION_28_29)
                        .addMigrations(MIGRATION_29_30)
                        .addMigrations(MIGRATION_30_31)
                        .addMigrations(MIGRATION_31_32)
                        .addMigrations(MIGRATION_32_33)
                        .addMigrations(MIGRATION_33_34)
                        .addMigrations(MIGRATION_34_35)
                        .addMigrations(MIGRATION_35_36)
                        .addMigrations(MIGRATION_36_37)
                        .addMigrations(MIGRATION_37_38)
                        .addMigrations(MIGRATION_38_39)
                        .addMigrations(MIGRATION_39_40)
                        .addMigrations(MIGRATION_40_41)
                        .addMigrations(MIGRATION_41_42)
                        .fallbackToDestructiveMigration()
                        .build()

                    INSTANCE = instance
                }
                return instance
            }
        }
    }
}
