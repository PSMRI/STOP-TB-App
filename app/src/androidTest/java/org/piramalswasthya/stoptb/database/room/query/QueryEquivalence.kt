package org.piramalswasthya.stoptb.database.room.query

import android.database.Cursor
import org.junit.Assert.assertEquals
import org.piramalswasthya.stoptb.database.room.InAppDb

/**
 * Helpers to prove that a rewritten query returns exactly what the old one returned.
 *
 * Used by the scale optimisation work: every rewritten count, list, paging or search query
 * runs side by side with the original SQL on the same seeded database and must produce the
 * same rows before the original is removed.
 */
object QueryEquivalence {

    /** Runs [sql] and returns every row as a list of column values rendered as strings. */
    fun rows(db: InAppDb, sql: String, args: Array<Any?> = emptyArray()): List<List<String?>> {
        db.openHelper.readableDatabase.query(sql, args).use { cursor -> return readAll(cursor) }
    }

    /** Asserts both queries return the same rows in the same order (use for ordered lists). */
    fun assertSameOrderedRows(
        db: InAppDb,
        originalSql: String,
        rewrittenSql: String,
        args: Array<Any?> = emptyArray(),
        rewrittenArgs: Array<Any?> = args
    ) {
        assertEquals(
            "Rewritten query returned different rows or order",
            rows(db, originalSql, args),
            rows(db, rewrittenSql, rewrittenArgs)
        )
    }

    /** Asserts both queries return the same multiset of rows, ignoring order (counts, sets). */
    fun assertSameRowsAnyOrder(
        db: InAppDb,
        originalSql: String,
        rewrittenSql: String,
        args: Array<Any?> = emptyArray(),
        rewrittenArgs: Array<Any?> = args
    ) {
        val rowKey: (List<String?>) -> String = { row -> row.joinToString("\u0001") { value -> value ?: "\u0000" } }
        assertEquals(
            "Rewritten query returned different rows",
            rows(db, originalSql, args).map(rowKey).sorted(),
            rows(db, rewrittenSql, rewrittenArgs).map(rowKey).sorted()
        )
    }

    private fun readAll(cursor: Cursor): List<List<String?>> {
        val result = ArrayList<List<String?>>(cursor.count)
        while (cursor.moveToNext()) {
            result += (0 until cursor.columnCount).map { column ->
                if (cursor.isNull(column)) null else cursor.getString(column)
            }
        }
        return result
    }
}
