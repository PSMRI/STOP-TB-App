package org.piramalswasthya.stoptb.database.room

import androidx.sqlite.db.SupportSQLiteDatabase

/** Reconstruct the actual v55 tables without v56 columns, preserving fixture data. */
internal fun restoreGeneralOpdV55Schema(db: SupportSQLiteDatabase) {
    val parentColumns = "id, benId, visitDate, chiefComplaints, medications, dosage, frequency, duration, notes, serverUpdatedDate, syncState"
    val childColumns = "opdId, position, medicine, frequency, durationCount, durationUnit, instruction"
    db.execSQL("CREATE TEMP TABLE opd_backup AS SELECT $parentColumns FROM GENERAL_OPD")
    db.execSQL("CREATE TEMP TABLE prescription_backup AS SELECT $childColumns FROM GENERAL_OPD_PRESCRIPTION")
    db.execSQL("DROP TABLE GENERAL_OPD_PRESCRIPTION")
    db.execSQL("DROP TABLE GENERAL_OPD")
    db.execSQL("""
        CREATE TABLE GENERAL_OPD (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, benId INTEGER NOT NULL,
            visitDate INTEGER NOT NULL, chiefComplaints TEXT, medications TEXT, dosage TEXT,
            frequency TEXT, duration TEXT, notes TEXT, serverUpdatedDate INTEGER, syncState INTEGER NOT NULL,
            FOREIGN KEY(benId) REFERENCES BENEFICIARY(beneficiaryId) ON UPDATE CASCADE ON DELETE CASCADE
        )
    """.trimIndent())
    db.execSQL("CREATE INDEX ind_general_opd_ben ON GENERAL_OPD (benId)")
    db.execSQL("INSERT INTO GENERAL_OPD ($parentColumns) SELECT $parentColumns FROM opd_backup")
    InAppDb.MIGRATION_53_54.migrate(db)
    db.execSQL("INSERT INTO GENERAL_OPD_PRESCRIPTION ($childColumns) SELECT $childColumns FROM prescription_backup")
    db.execSQL("DROP TABLE opd_backup")
    db.execSQL("DROP TABLE prescription_backup")
}
