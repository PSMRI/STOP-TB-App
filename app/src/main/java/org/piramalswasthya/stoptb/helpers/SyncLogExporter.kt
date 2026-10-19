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
    fun createShareIntent(context: Context, includeLogs: Boolean = true, summary: String? = null): Intent? {
        val logs = if (includeLogs) logManager.interactiveSnapshot() else emptyList()
        if (logs.isEmpty() && summary == null) return null

        val zipFile = File(context.cacheDir, "sync-diagnostics-${System.currentTimeMillis()}.zip")
        BufferedOutputStream(FileOutputStream(zipFile)).use { output ->
            val exportContext = "EXPORT CONTEXT (current installation, not historical record context)\n" +
                "exportedAtEpochMs=${System.currentTimeMillis()}\n" +
                "timezone=${java.util.TimeZone.getDefault().id}\n" + SyncDiagnostics.identity(context) + "\n"
            writeInteractiveLogArchive(output, logs, exportContext, includeLogs, summary)
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
