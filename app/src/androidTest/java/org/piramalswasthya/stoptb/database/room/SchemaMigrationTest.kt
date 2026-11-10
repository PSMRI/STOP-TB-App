package org.piramalswasthya.stoptb.database.room

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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

    private companion object {
        /** Versions whose exported schema JSON exists in app/schemas. */
        val BASE_VERSIONS = listOf(61)
    }
}
