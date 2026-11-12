package org.piramalswasthya.stoptb.helpers

internal object SyncLogLimits {
    const val MAX_MESSAGE_CHARS = 2_048
    const val MAX_TAG_CHARS = 64
    const val MAX_BUFFER_ENTRIES = 500
    const val MAX_BUFFER_BYTES = 1024 * 1024
    const val MAX_QUEUED_WRITES = 256
    const val MAX_FILE_LINE_CHARS = 2_304
    const val TRUNCATION_SUFFIX = "… [truncated]"
}
