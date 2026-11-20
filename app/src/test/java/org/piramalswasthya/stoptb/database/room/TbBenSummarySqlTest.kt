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
        for (table in listOf("TB_SCREENING", "TB_SUSPECTED", "TB_DIAGNOSTICS", "BEN_VITALS", "TB_CONFIRMED_TREATMENT")) {
            val existing = columnsOf(table).map { it.lowercase() }.toSet()
            val missing = TbBenSummarySql.columnsRead(table).filter { it.lowercase() !in existing }
            assertEquals("$table columns read by TbBenSummarySql but not in the schema", emptyList<String>(), missing)
        }
    }

    @Test
    fun summaryColumnsMatchTheEntity() {
        val entityColumns = TbBenSummary::class.java.declaredFields.map { it.name }.filter { it != "beneficiaryId" }.toSet()
        assertEquals(entityColumns, TbBenSummarySql.COLUMNS.keys)
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
    fun writesSqlForDeviceChecks() {
        File("build/tb-ben-summary.sql").writeText(
            (TbBenSummarySql.TRIGGERS + TbBenSummarySql.FILL).joinToString(";\n", postfix = ";\n")
        )
    }
}
