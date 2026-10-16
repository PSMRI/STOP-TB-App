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
     * Shares only the retained manual/direct live snapshot, never raw diagnostic files.
     * Must be called from a background thread (performs file I/O).
     * Returns `null` if there are no retained manual/direct entries.
     */
    fun createShareIntent(context: Context): Intent? {
        val logs = logManager.interactiveSnapshot()
        if (logs.isEmpty()) return null

        val zipFile = File(context.cacheDir, "manual-direct-sync-logs-${System.currentTimeMillis()}.zip")
        BufferedOutputStream(FileOutputStream(zipFile)).use { output ->
            val exportContext = "EXPORT CONTEXT (current installation, not historical record context)\n" +
                "exportedAtEpochMs=${System.currentTimeMillis()}\n" +
                "timezone=${java.util.TimeZone.getDefault().id}\n" + SyncDiagnostics.identity(context) + "\n"
            writeInteractiveLogArchive(output, logs, exportContext)
        }

        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".provider",
            zipFile
        )

        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Manual and Direct Save Sync Logs")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
