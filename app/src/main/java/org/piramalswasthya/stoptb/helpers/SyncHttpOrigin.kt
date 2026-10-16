package org.piramalswasthya.stoptb.helpers

import okhttp3.Call
import okhttp3.Request
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext
import java.util.UUID

internal data class SyncHttpOrigin(val runId: String, val direction: String, val trigger: String) {
    val interactive: Boolean get() = trigger == "MANUAL_REFRESH" || trigger == "DIRECT_SAVE"
}

internal object SyncHttpOrigins {
    val current = ThreadLocal<SyncHttpOrigin?>()
}

internal suspend fun <T> withDirectSaveHttpOrigin(block: suspend () -> T): T {
    if (SyncHttpOrigins.current.get() != null) return block()
    return withContext(SyncHttpOrigins.current.asContextElement(
        SyncHttpOrigin(UUID.randomUUID().toString(), "PUSH", "DIRECT_SAVE"))) { block() }
}

/** Capture origin while Retrofit creates the call, before OkHttp switches threads. */
internal class SyncHttpCallFactory(private val delegate: Call.Factory) : Call.Factory {
    override fun newCall(request: Request): Call {
        val origin = SyncHttpOrigins.current.get()
        return delegate.newCall(if (origin != null) request.newBuilder().tag(SyncHttpOrigin::class.java, origin).build() else request)
    }
}
