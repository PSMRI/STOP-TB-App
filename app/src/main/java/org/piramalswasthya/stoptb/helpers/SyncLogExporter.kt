package org.piramalswasthya.stoptb.helpers

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncLogExporter @Inject constructor(
    private val logManager: SyncLogManager
) {

    /**
     * Shares selected sections: retained manual/direct logs and/or the rendered summary.
     * Never reads raw diagnostic files.
     * Must be called from a background thread (performs file I/O).
     * Returns `null` if neither selected section has content.
     */
    internal fun createShareIntent(context: Context, sessionId: String, includeLogs: Boolean,
                                   summary: String?): Intent? {
        if (!interactiveSyncLogSession.isCurrent(sessionId)) return null
        val logs = if (includeLogs) logManager.interactiveSnapshot(sessionId) else emptyList()
        if (logs.isEmpty() && summary == null) return null
        val identity = SyncDiagnostics.identity(context)
        if (!interactiveSyncLogSession.isCurrent(sessionId)) return null

        val zipFile = File.createTempFile("sync-diagnostics-", ".zip", context.cacheDir)
        BufferedOutputStream(FileOutputStream(zipFile)).use { output ->
            val exportContext = "EXPORT CONTEXT (current installation, not historical record context)\n" +
                "exportedAtEpochMs=${System.currentTimeMillis()}\n" +
                "timezone=${java.util.TimeZone.getDefault().id}\n" + identity + "\n"
            writeInteractiveLogArchive(output, logs, exportContext, includeLogs, summary)
        }
        if (!interactiveSyncLogSession.isCurrent(sessionId)) {
            zipFile.delete()
            return null
        }

        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".provider",
            zipFile
        )

        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Sync Diagnostics")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
