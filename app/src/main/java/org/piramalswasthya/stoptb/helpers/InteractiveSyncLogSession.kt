package org.piramalswasthya.stoptb.helpers

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.piramalswasthya.stoptb.model.SyncLogEntry

internal val interactiveSyncLogSession = InteractiveSyncLogSession()

internal class InteractiveSyncLogSession(private val capacity: Int = 500) {
    private val buffer = ArrayDeque<SyncLogEntry>()
    private var filter = SyncLiveLogFilter()
    private val _session = MutableStateFlow<String?>(null)
    val session = _session.asStateFlow()
    private val _logs = MutableStateFlow<List<SyncLogEntry>>(emptyList())
    val logs = _logs.asStateFlow()

    @Synchronized
    fun reset(signedIn: Boolean) {
        _session.value = null
        clear()
        if (signedIn) _session.value = UUID.randomUUID().toString()
    }

    fun isCurrent(sessionId: String?): Boolean = sessionId != null && sessionId == session.value

    @Synchronized
    fun append(sessionId: String?, entry: SyncLogEntry) {
        if (!isCurrent(sessionId) || !filter.accepts(entry.tag, entry.message)) return
        if (buffer.size >= capacity) buffer.removeFirst()
        buffer.addLast(entry)
    }

    @Synchronized
    fun publish() {
        _logs.value = buffer.toList()
    }

    @Synchronized
    fun snapshot(sessionId: String): List<SyncLogEntry> =
        if (isCurrent(sessionId)) buffer.toList() else emptyList()

    @Synchronized
    fun clear() {
        buffer.clear()
        filter = SyncLiveLogFilter()
        _logs.value = emptyList()
    }
}
