package org.piramalswasthya.stoptb.helpers

import org.piramalswasthya.stoptb.model.SyncLogEntry

internal const val SYNC_LOG_DISPLAY_LIMIT = 200
internal const val SYNC_LOG_DISPLAY_INTERVAL_MS = 1000L

/** Bound UI work without truncating stored/exported logs. */
internal fun syncLogDisplaySnapshot(logs: List<SyncLogEntry>): List<SyncLogEntry> =
    logs.takeLast(SYNC_LOG_DISPLAY_LIMIT).map { entry ->
        if (entry.message.length > 500) entry.copy(message = entry.message.take(500) + "...") else entry
    }
