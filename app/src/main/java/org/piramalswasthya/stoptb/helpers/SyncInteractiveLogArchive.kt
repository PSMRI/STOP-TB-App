package org.piramalswasthya.stoptb.helpers

import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.piramalswasthya.stoptb.model.SyncLogEntry

/** Receives only the dedicated interactive snapshot, never the raw log directory. */
internal fun writeInteractiveLogArchive(
    output: OutputStream, logs: List<SyncLogEntry>, context: String,
    includeLogs: Boolean = true, summary: String? = null
) {
    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    ZipOutputStream(output).use { zip ->
        zip.putNextEntry(ZipEntry("sync-context.txt"))
        zip.write(("SYNC DIAGNOSTICS EXPORT\n" +
            "logsIncluded=$includeLogs | summaryIncluded=${summary != null}\n" +
            "Log scope: manual-refresh/direct-save only, current app session, maximum 500 retained entries; not full history.\n" +
            "entries=${if (includeLogs) logs.size else 0}\nAuto-sync and unmasked raw API logs excluded from Logs. Masked API previews may be included; capture limit 32 KiB/body, larger/non-JSON bodies omitted. Summary scope is stated in sync-summary.txt.\n$context\n").toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        if (includeLogs) {
            zip.putNextEntry(ZipEntry("manual-direct-sync.log"))
            if (logs.isEmpty()) zip.write("No retained manual/direct log entries in this app session.\n".toByteArray(Charsets.UTF_8))
            for (entry in logs) {
                val line = "${timestamp.format(Date(entry.timestamp))} [${entry.level}] [${entry.tag}] ${entry.message}\n"
                zip.write(line.toByteArray(Charsets.UTF_8))
            }
            zip.closeEntry()
        }
        if (summary != null) {
            zip.putNextEntry(ZipEntry("sync-summary.txt"))
            zip.write(summary.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }
}
