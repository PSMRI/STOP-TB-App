package org.piramalswasthya.stoptb.database.room

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Keeps BEN_SEARCH_FTS ([org.piramalswasthya.stoptb.model.BenSearchFts]) in step with
 * BENEFICIARY and HOUSEHOLD, and turns the text typed in the search box into an FTS query.
 */
object BenSearchSql {

    const val TABLE = "BEN_SEARCH_FTS"

    private const val COLUMNS = "rowid, name, phone, phoneend, abha, head, spouse, father, benId, hhId, rch"

    /** Index rows for the beneficiaries matched by [where] (alias b = BENEFICIARY). */
    private fun rows(where: String) =
        "INSERT INTO $TABLE ($COLUMNS) SELECT b.beneficiaryId," +
            " TRIM(IFNULL(b.firstName, '') || ' ' || IFNULL(b.lastName, ''))," +
            " IFNULL(CAST(b.contactNumber AS TEXT), '')," +
            " CASE WHEN LENGTH(CAST(b.contactNumber AS TEXT)) > 4 THEN" +
            " SUBSTR(CAST(b.contactNumber AS TEXT), -4) || ' ' || SUBSTR(CAST(b.contactNumber AS TEXT), -5) || ' ' ||" +
            " SUBSTR(CAST(b.contactNumber AS TEXT), -6) || ' ' || SUBSTR(CAST(b.contactNumber AS TEXT), -7) || ' ' ||" +
            " SUBSTR(CAST(b.contactNumber AS TEXT), -8) || ' ' || SUBSTR(CAST(b.contactNumber AS TEXT), -9) ELSE '' END," +
            " REPLACE(IFNULL(b.abha_healthIdNumber, ''), '-', '')," +
            " IFNULL(h.fam_familyHeadName, '')," +
            " IFNULL(b.gen_spouseName, '')," +
            " IFNULL(b.fatherName, '')," +
            " CAST(b.beneficiaryId AS TEXT)," +
            " IFNULL(CAST(b.householdId AS TEXT), '')," +
            " IFNULL(b.rchId, '')" +
            " FROM BENEFICIARY b LEFT JOIN HOUSEHOLD h ON h.householdId = b.householdId WHERE $where"

    /** Fills the index for every beneficiary. */
    val FILL: String = rows("1")

    private val SEARCHED_BEN_COLUMNS = listOf(
        "firstName", "lastName", "contactNumber", "abha_healthIdNumber", "gen_spouseName", "fatherName", "householdId", "rchId"
    )

    private val TRIGGERS: List<Pair<String, String>> = listOf(
        "trg_bensearch_ben_ins" to "AFTER INSERT ON `BENEFICIARY` BEGIN" +
            " ${rows("b.beneficiaryId = NEW.beneficiaryId")}; END",
        // An upsert rewrites every column: re-index only when a searched value changed.
        "trg_bensearch_ben_upd" to "AFTER UPDATE ON `BENEFICIARY` WHEN " +
            (SEARCHED_BEN_COLUMNS.map { "OLD.$it IS NOT NEW.$it" } + "OLD.beneficiaryId IS NOT NEW.beneficiaryId")
                .joinToString(" OR ") +
            " BEGIN DELETE FROM $TABLE WHERE rowid = OLD.beneficiaryId;" +
            " ${rows("b.beneficiaryId = NEW.beneficiaryId")}; END",
        "trg_bensearch_ben_del" to "AFTER DELETE ON `BENEFICIARY` BEGIN DELETE FROM $TABLE WHERE rowid = OLD.beneficiaryId; END",
        // The family head name lives on the household: re-index its members.
        "trg_bensearch_hh_ins" to "AFTER INSERT ON `HOUSEHOLD` BEGIN" +
            " DELETE FROM $TABLE WHERE rowid IN (SELECT beneficiaryId FROM BENEFICIARY WHERE householdId = NEW.householdId);" +
            " ${rows("b.householdId = NEW.householdId")}; END",
        "trg_bensearch_hh_upd" to "AFTER UPDATE ON `HOUSEHOLD` WHEN OLD.fam_familyHeadName IS NOT NEW.fam_familyHeadName" +
            " OR OLD.householdId IS NOT NEW.householdId BEGIN" +
            " DELETE FROM $TABLE WHERE rowid IN (SELECT beneficiaryId FROM BENEFICIARY WHERE householdId IN (OLD.householdId, NEW.householdId));" +
            " ${rows("b.householdId IN (OLD.householdId, NEW.householdId)")}; END",
        "trg_bensearch_hh_del" to "AFTER DELETE ON `HOUSEHOLD` BEGIN" +
            " DELETE FROM $TABLE WHERE rowid IN (SELECT beneficiaryId FROM BENEFICIARY WHERE householdId = OLD.householdId);" +
            " ${rows("b.householdId = OLD.householdId")}; END",
    )

    internal val CREATE_TRIGGERS: List<String> = TRIGGERS.map { (name, body) -> "CREATE TRIGGER IF NOT EXISTS $name $body" }

    fun createTriggers(db: SupportSQLiteDatabase) {
        CREATE_TRIGGERS.forEach { db.execSQL(it) }
    }

    fun dropTriggers(db: SupportSQLiteDatabase) {
        TRIGGERS.forEach { (name, _) -> db.execSQL("DROP TRIGGER IF EXISTS $name") }
    }

    /**
     * FTS query for [text]: every word must match the start of a word in some field
     * ("ram kum" finds "Ramesh Kumar"). Digits typed with spaces or dashes ("98765 000",
     * "91-2345") are one number, matched at the start of a number or at the end of a phone
     * number ("00012" finds 9876500012). Returns "" when there is nothing to search for.
     */
    fun toFtsQuery(text: String): String {
        val trimmed = text.trim().lowercase()
        if (trimmed.isEmpty()) return ""
        if (trimmed.all { it.isDigit() || it == ' ' || it == '-' }) {
            val digits = trimmed.filter { it.isDigit() }
            return if (digits.isEmpty()) "" else "$digits* OR phoneend:$digits*"
        }
        return trimmed.split(Regex("[^\\p{L}\\p{M}\\p{N}]+")).filter { it.isNotEmpty() }.joinToString(" ") { "$it*" }
    }
}
