package org.piramalswasthya.stoptb.database.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * InAppDb no longer wipes the database when a migration is missing, so a missing
 * migration must be caught here rather than on field devices.
 */
class InAppDbMigrationsTest {

    @Test
    fun everyVersionHasAMigrationToTheNext() {
        val nextVersionByStart = InAppDb.MIGRATIONS.associate { it.startVersion to it.endVersion }
        for (version in InAppDb.OLDEST_MIGRATABLE_VERSION until InAppDb.DB_VERSION) {
            assertEquals(
                "No migration from version $version; add MIGRATION_${version}_${version + 1} to InAppDb.MIGRATIONS",
                version + 1,
                nextVersionByStart[version]
            )
        }
    }

    @Test
    fun noVersionHasTwoMigrations() {
        val starts = InAppDb.MIGRATIONS.map { it.startVersion }
        assertEquals("Duplicate start versions: $starts", starts.size, starts.toSet().size)
    }

    @Test
    fun lastMigrationReachesTheCurrentVersion() {
        assertTrue(InAppDb.MIGRATIONS.any { it.endVersion == InAppDb.DB_VERSION })
    }
}
