package org.piramalswasthya.stoptb.database.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.piramalswasthya.stoptb.model.TbBenSummary
import java.io.File

/**
 * Checks the TB_BEN_SUMMARY SQL against the exported schema: a typo in a column name would
 * leave a list silently wrong, and a column missing from a trigger's UPDATE OF list would leave
 * the summary stale after an edit. Also writes the SQL to build/ for running it on a device DB.
 */
class TbBenSummarySqlTest {

    private val schema = File("schemas/org.piramalswasthya.stoptb.database.room.InAppDb/${InAppDb.DB_VERSION}.json").readText()

    private fun columnsOf(table: String): Set<String> {
        val start = schema.indexOf("\"tableName\": \"$table\"")
        assertTrue("$table not in schema", start >= 0)
        val end = schema.indexOf("\"tableName\":", start + 1).let { if (it < 0) schema.length else it }
        return Regex("\"columnName\": \"([^\"]+)\"").findAll(schema.substring(start, end)).map { it.groupValues[1] }.toSet()
    }

    @Test
    fun everyColumnTheRulesReadExists() {
        for (table in listOf("TB_SCREENING", "TB_SUSPECTED", "TB_DIAGNOSTICS", "BEN_VITALS", "TB_CONFIRMED_TREATMENT", "BENEFICIARY")) {
            val existing = columnsOf(table).map { it.lowercase() }.toSet()
            val missing = TbBenSummarySql.columnsRead(table).filter { it.lowercase() !in existing }
            assertEquals("$table columns read by TbBenSummarySql but not in the schema", emptyList<String>(), missing)
        }
    }

    @Test
    fun summaryColumnsMatchTheEntity() {
        val entityColumns = TbBenSummary::class.java.declaredFields.map { it.name }.filter { it != "beneficiaryId" }.toSet()
        assertEquals(entityColumns, TbBenSummarySql.COLUMNS.keys + TbBenSummarySql.DERIVED.keys)
        assertEquals(columnsOf(TbBenSummarySql.TABLE), entityColumns + "beneficiaryId")
    }

    @Test
    fun updateTriggersWatchEveryColumnTheRulesRead() {
        for (table in listOf("TB_SCREENING", "TB_SUSPECTED", "TB_DIAGNOSTICS", "BEN_VITALS", "TB_CONFIRMED_TREATMENT")) {
            val trigger = TbBenSummarySql.TRIGGERS.single { it.contains("AFTER UPDATE OF") && it.contains("ON `$table`") && !it.contains("WHEN OLD") }
            TbBenSummarySql.columnsRead(table).forEach { col ->
                assertTrue("$table.$col not watched", trigger.contains("`$col`"))
            }
        }
    }

    @Test
    fun beneficiaryTriggerWatchesTheColumnsTheRulesRead() {
        val trigger = TbBenSummarySql.TRIGGERS.single { it.contains("ON `BENEFICIARY`") && it.contains("AFTER UPDATE OF") }
        TbBenSummarySql.columnsRead("BENEFICIARY").filter { it != "beneficiaryId" && it != "benId" }.forEach { col ->
            assertTrue("BENEFICIARY.$col not watched", trigger.contains("`$col`"))
        }
    }

    @Test
    fun ftsQueryMatchesWordStarts() {
        assertEquals("", BenSearchSql.toFtsQuery("  "))
        assertEquals("ram*", BenSearchSql.toFtsQuery("Ram"))
        assertEquals("sunita* devi*", BenSearchSql.toFtsQuery("sunita  devi"))
        assertEquals("98765000* OR phoneend:98765000*", BenSearchSql.toFtsQuery("98765 000"))
        assertEquals("912345* OR phoneend:912345*", BenSearchSql.toFtsQuery("91-2345"))
        assertEquals("o* brien*", BenSearchSql.toFtsQuery("O'Brien"))
        assertEquals("", BenSearchSql.toFtsQuery("*\"-"))
    }

    // The firing statement's conflict policy overrides the trigger's (Room's UPDATE OR ABORT made
    // INSERT OR REPLACE fail), so each refresh must delete the row and then insert it plainly.
    @Test
    fun triggersRefreshWithDeleteThenInsert() {
        TbBenSummarySql.TRIGGERS.forEach { trigger ->
            assertTrue(trigger, !trigger.contains("OR REPLACE INTO TB_BEN_SUMMARY"))
            val delete = trigger.indexOf("DELETE FROM TB_BEN_SUMMARY WHERE beneficiaryId = ")
            val insert = trigger.indexOf("INSERT INTO TB_BEN_SUMMARY")
            assertTrue(trigger, delete >= 0 && insert > delete)
        }
    }

    @Test
    fun writesSqlForDeviceChecks() {
        File("build/tb-ben-summary.sql").writeText(
            (TbBenSummarySql.TRIGGERS + TbBenSummarySql.FILL).joinToString(";\n", postfix = ";\n")
        )
        File("build/ben-search-fts.sql").writeText(
            (BenSearchSql.CREATE_TRIGGERS + BenSearchSql.FILL).joinToString(";\n", postfix = ";\n")
        )
    }
}
