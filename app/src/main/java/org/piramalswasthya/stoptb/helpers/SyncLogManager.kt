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
        private const val MAX_BUFFER_SIZE = 500
        private const val EMIT_DELAY_MS = 250L
    }

    private val scope = CoroutineScope(Dispatchers.Default)
    private val buffer = ArrayDeque<SyncLogEntry>(MAX_BUFFER_SIZE)
    private val interactiveBuffer = ArrayDeque<SyncLogEntry>(MAX_BUFFER_SIZE)
    private val liveFilter = SyncLiveLogFilter()
    private var nextId = 0L
    private var emitJob: Job? = null
    private val _logs = MutableStateFlow<List<SyncLogEntry>>(emptyList())
    val logs: StateFlow<List<SyncLogEntry>> = _logs.asStateFlow()
    private val _interactiveLogs = MutableStateFlow<List<SyncLogEntry>>(emptyList())
    val interactiveLogs: StateFlow<List<SyncLogEntry>> = _interactiveLogs.asStateFlow()

    fun interactiveSnapshot(): List<SyncLogEntry> = synchronized(buffer) { interactiveBuffer.toList() }

    fun addLog(level: LogLevel, tag: String, message: String) {
        synchronized(buffer) {
            if (buffer.size >= MAX_BUFFER_SIZE) {
                buffer.removeFirst()
            }
            buffer.addLast(
                SyncLogEntry(
                    id = nextId++,
                    timestamp = System.currentTimeMillis(),
                    level = level,
                    tag = tag,
                    message = message
                )
            )
            if (liveFilter.accepts(tag, message)) {
                if (interactiveBuffer.size >= MAX_BUFFER_SIZE) interactiveBuffer.removeFirst()
                interactiveBuffer.addLast(buffer.last())
            }
            scheduleEmit()
        }
        fileWriter.writeLog(level, tag, message)
    }

    private fun scheduleEmit() {
        if (emitJob?.isActive == true) return
        emitJob = scope.launch {
            delay(EMIT_DELAY_MS)
            synchronized(buffer) {
                _logs.value = buffer.toList()
                _interactiveLogs.value = interactiveBuffer.toList()
                emitJob = null
            }
        }
    }

    fun clearLogs() {
        synchronized(buffer) {
            emitJob?.cancel()
            buffer.clear()
            interactiveBuffer.clear()
            _logs.value = emptyList()
            _interactiveLogs.value = emptyList()
        }
    }
}
