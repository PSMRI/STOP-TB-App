package org.piramalswasthya.stoptb.helpers

import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.piramalswasthya.stoptb.model.SyncLogEntry

/** Receives only the dedicated interactive snapshot, never the raw log directory. */
internal fun writeInteractiveLogArchive(output: OutputStream, logs: List<SyncLogEntry>, context: String) {
    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    ZipOutputStream(output).use { zip ->
        zip.putNextEntry(ZipEntry("sync-context.txt"))
        zip.write(("FILTERED EXPORT: manual-refresh and direct-save events only\n" +
            "source=current app session; maximum 500 retained entries; not full history\n" +
            "entries=${logs.size}\nAuto-sync and unmasked raw API logs excluded. Masked API previews may be included; capture limit 32 KiB/body, larger/non-JSON bodies omitted.\n$context\n").toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        zip.putNextEntry(ZipEntry("manual-direct-sync.log"))
        for (entry in logs) {
            val line = "${timestamp.format(Date(entry.timestamp))} [${entry.level}] [${entry.tag}] ${entry.message}\n"
            zip.write(line.toByteArray(Charsets.UTF_8))
        }
        zip.closeEntry()
    }
}
