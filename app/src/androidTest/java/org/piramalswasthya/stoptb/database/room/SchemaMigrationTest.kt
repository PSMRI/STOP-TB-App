package org.piramalswasthya.stoptb.database.room

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migrates a database created from an exported schema JSON (app/schemas/.../<version>.json)
 * up to [InAppDb.DB_VERSION] with the production [InAppDb.MIGRATIONS], and lets Room validate
 * the result against the current entities, views and indices.
 *
 * Add the version you migrate from to [BASE_VERSIONS] once its schema JSON is committed.
 * Each new MIGRATION_x_y (indices, views, FTS tables) is covered automatically, because the
 * test always migrates to the current version.
 */
@RunWith(AndroidJUnit4::class)
class SchemaMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        InAppDb::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migratesFromEachBaseVersionToCurrentAndValidatesSchema() {
        for (baseVersion in BASE_VERSIONS) {
            val dbName = "schema-migration-test-$baseVersion"
            helper.createDatabase(dbName, baseVersion).close()
            helper.runMigrationsAndValidate(dbName, InAppDb.DB_VERSION, true, *InAppDb.MIGRATIONS)
                .close()
        }
    }

    /**
     * A device on the field build (2.3.1, schema 61) upgrading straight to this build: every
     * table gets one row at v61, then all migrations run, and every table that still exists
     * must still hold its row. Values are 1 / '1' everywhere, so foreign keys line up.
     */
    @Test
    fun rowsWrittenOnTheFieldVersionSurviveTheUpgrade() {
        val dbName = "schema-migration-data-test"
        val tables = tablesOf(FIELD_VERSION)
        helper.createDatabase(dbName, FIELD_VERSION).use { db ->
            db.execSQL("PRAGMA foreign_keys=OFF")
            for ((table, columns) in tables) {
                val values = columns.joinToString(",") { (_, affinity) -> sampleValue(affinity) }
                db.execSQL("INSERT INTO `$table` (${columns.joinToString(",") { "`${it.first}`" }}) VALUES ($values)")
            }
        }
        val migrated = helper.runMigrationsAndValidate(dbName, InAppDb.DB_VERSION, true, *InAppDb.MIGRATIONS)
        migrated.use { db ->
            val existing = db.query("SELECT name FROM sqlite_master WHERE type='table'").use { c ->
                buildSet { while (c.moveToNext()) add(c.getString(0)) }
            }
            var checked = 0
            for ((table, _) in tables) {
                if (table !in existing) continue
                val count = db.query("SELECT COUNT(*) FROM `$table`").use { c -> c.moveToFirst(); c.getInt(0) }
                assertEquals("rows in $table after migrating $FIELD_VERSION -> ${InAppDb.DB_VERSION}", 1, count)
                checked++
            }
            assertTrue("checked $checked of ${tables.size} tables", checked > tables.size / 2)
        }
    }

    private fun tablesOf(version: Int): List<Pair<String, List<Pair<String, String>>>> {
        val json = InstrumentationRegistry.getInstrumentation().context.assets
            .open("${InAppDb::class.java.name}/$version.json").bufferedReader().use { it.readText() }
        val entities = JSONObject(json).getJSONObject("database").getJSONArray("entities")
        return (0 until entities.length()).map { entities.getJSONObject(it) }
            .filter { !it.has("ftsVersion") }
            .map { entity ->
                val fields = entity.getJSONArray("fields")
                entity.getString("tableName") to (0 until fields.length()).map {
                    val f = fields.getJSONObject(it)
                    f.getString("columnName") to f.optString("affinity", "TEXT")
                }
            }
    }

    private fun sampleValue(affinity: String) = when (affinity.uppercase()) {
        "INTEGER" -> "1"
        "REAL" -> "1.0"
        "BLOB" -> "x'01'"
        else -> "'1'"
    }

    private companion object {
        /** Schema of the build deployed in the field (release-2.3.1). */
        const val FIELD_VERSION = 61

        /** Versions whose exported schema JSON exists in app/schemas. */
        val BASE_VERSIONS = listOf(61, 67)
    }
}
