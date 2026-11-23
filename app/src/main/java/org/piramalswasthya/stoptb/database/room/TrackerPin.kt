package org.piramalswasthya.stoptb.database.room

import android.util.Log
import androidx.room.InvalidationTracker
import androidx.room.RoomDatabase

/**
 * Keeps Room's change-tracking triggers installed for every table. Room drops a table's triggers
 * when its last observer leaves; that DDL makes Android close all idle read connections, and each
 * reopened one truncates the WAL behind any open write. During sync, reads queued behind it.
 */
object TrackerPin {
    private const val TAG = "TrackerPin"

    /** Call once, off the main thread (adding an observer writes to the database). */
    fun pinAllTables(db: RoomDatabase) {
        val tables = db.query("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { cursor ->
            List(cursor.count) { index -> cursor.moveToPosition(index); cursor.getString(0) }
        }
        var pinned = 0
        tables.forEach { table ->
            val observer = object : InvalidationTracker.Observer(table) {
                override fun onInvalidated(tables: Set<String>) = Unit
            }
            try {
                db.invalidationTracker.addObserver(observer)
                pinned++
            } catch (notTracked: IllegalArgumentException) {
                // Not a Room table (FTS shadow tables, android_metadata, ...).
            }
        }
        Log.i(TAG, "pinned $pinned of ${tables.size} tables")
    }
}
