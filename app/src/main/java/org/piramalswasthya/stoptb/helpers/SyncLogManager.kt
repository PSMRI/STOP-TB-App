package org.piramalswasthya.stoptb.helpers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.piramalswasthya.stoptb.model.LogLevel
import org.piramalswasthya.stoptb.model.SyncLogEntry
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncLogManager @Inject constructor(
    private val fileWriter: SyncLogFileWriter
) {

    companion object {
        private const val EMIT_DELAY_MS = 250L
    }

    private val scope = CoroutineScope(Dispatchers.Default)
    private val buffer = ArrayDeque<SyncLogEntry>(SyncLogLimits.MAX_BUFFER_ENTRIES)
    // Count UTF-16 storage for message and tag content; entry count also bounds object overhead.
    private var bufferedBytes = 0
    private var nextId = 0L
    private var emitJob: Job? = null
    private val _logs = MutableStateFlow<List<SyncLogEntry>>(emptyList())
    val logs: StateFlow<List<SyncLogEntry>> = _logs.asStateFlow()
    val interactiveLogs: StateFlow<List<SyncLogEntry>> = interactiveSyncLogSession.logs

    internal fun interactiveSnapshot(sessionId: String): List<SyncLogEntry> =
        interactiveSyncLogSession.snapshot(sessionId)

    fun addLog(level: LogLevel, tag: String, message: String) {
        val boundedMessage = message.take(SyncLogLimits.MAX_MESSAGE_CHARS).let {
            if (message.length > SyncLogLimits.MAX_MESSAGE_CHARS) it + SyncLogLimits.TRUNCATION_SUFFIX else it
        }
        val boundedTag = tag.take(SyncLogLimits.MAX_TAG_CHARS)
        val entryBytes = (boundedMessage.length + boundedTag.length) * 2
        synchronized(buffer) {
            while (buffer.isNotEmpty() && (buffer.size >= SyncLogLimits.MAX_BUFFER_ENTRIES ||
                    bufferedBytes + entryBytes > SyncLogLimits.MAX_BUFFER_BYTES)) {
                val removed = buffer.removeFirst()
                bufferedBytes -= (removed.message.length + removed.tag.length) * 2
            }
            val entry = SyncLogEntry(
                    id = nextId++,
                    timestamp = System.currentTimeMillis(),
                    level = level,
                    tag = boundedTag,
                    message = boundedMessage
                )
            buffer.addLast(entry)
            bufferedBytes += entryBytes
            interactiveSyncLogSession.append(SyncHttpOrigins.current.get()?.sessionId, entry)
            scheduleEmit()
        }
        fileWriter.writeLog(level, boundedTag, boundedMessage)
    }

    private fun scheduleEmit() {
        if (emitJob?.isActive == true) return
        emitJob = scope.launch {
            delay(EMIT_DELAY_MS)
            synchronized(buffer) {
                _logs.value = buffer.toList()
                interactiveSyncLogSession.publish()
                emitJob = null
            }
        }
    }

    fun clearLogs() {
        synchronized(buffer) {
            emitJob?.cancel()
            buffer.clear()
            bufferedBytes = 0
            interactiveSyncLogSession.clear()
            _logs.value = emptyList()
        }
    }
}
