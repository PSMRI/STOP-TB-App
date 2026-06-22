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
    views = [BenBasicCache::class],
    version = 20, exportSchema = false
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
                    " FROM BENEFICIARY b " +
                    "LEFT JOIN HOUSEHOLD h ON b.householdId = h.householdId " +
                    "LEFT OUTER JOIN CBAC cbac ON b.beneficiaryId = cbac.benId " +
                    "LEFT OUTER JOIN TB_SCREENING tbsn ON b.beneficiaryId = tbsn.benId " +
                    "LEFT OUTER JOIN TB_SUSPECTED tbsp ON b.beneficiaryId = tbsp.benId " +
                    "WHERE b.isDraft = 0 GROUP BY b.beneficiaryId ORDER BY b.updatedDate DESC"
            )
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
                        .fallbackToDestructiveMigration()
                        .build()

                    INSTANCE = instance
                }
                return instance
            }
        }
    }
}
