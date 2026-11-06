package org.piramalswasthya.stoptb.helpers

import java.util.UUID
import javax.inject.Inject
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import org.piramalswasthya.stoptb.model.LogLevel

class SyncApiPayloadInterceptor @Inject constructor(private val logs: SyncLogManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val origin = request.tag(SyncHttpOrigin::class.java)
        if (origin?.interactive != true) return chain.proceed(request)
        val id = UUID.randomUUID().toString()
        val prefix = "HTTP EXCHANGE | trigger=${origin.trigger} | runId=${origin.runId} | direction=${origin.direction} | callId=$id"
        fun emit(level: LogLevel, message: String) { runCatching { logs.addLog(level, "SyncApi", message) } }
        val safeQueryKeys = setOf("villageid", "benid", "beneficiaryid", "beneficiaryregid", "providerservicemapid",
            "pageno", "pagesize", "limit", "offset", "formuuid", "formid", "ordertype")
        val url = request.url.newBuilder().username("").password("").apply {
            request.url.queryParameterNames.forEach { key ->
                if (SyncPayloadRedaction.sensitiveKey(key) || key.lowercase() !in safeQueryKeys) setQueryParameter(key, "[REDACTED]")
            }
        }.build()
        val payload = runCatching {
            val body = request.body
            when {
                body == null -> "[No request body]"
                body.isOneShot() || body.isDuplex() -> "[Streaming request omitted]"
                body.contentLength() !in 0..SYNC_PAYLOAD_LIMIT -> "[Request body omitted: unknown size or over 32 KiB]"
                body.contentType()?.subtype?.contains("json") != true -> "[Non-JSON request omitted]"
                else -> Buffer().let { body.writeTo(it); SyncPayloadRedaction.body(it.readUtf8()) }
            }
        }.getOrDefault("[Request preview unavailable]")
        emit(LogLevel.INFO, "$prefix | phase=REQUEST\n${request.method} $url\nRequest JSON (sensitive fields masked):\n$payload")
        val started = System.nanoTime()
        val response = try { chain.proceed(request) } catch (failure: Exception) {
            emit(LogLevel.ERROR, "$prefix | phase=ERROR\nNo HTTP response received. Error: ${failure.javaClass.simpleName}")
            throw failure
        }
        val preview = runCatching {
            if (response.body == null) "[No response body]"
            else if (response.body?.contentType()?.subtype?.contains("json", ignoreCase = true) != true) "[Non-JSON response omitted]"
            else {
                val bytes = response.peekBody(SYNC_PAYLOAD_LIMIT + 1).bytes()
                if (bytes.size > SYNC_PAYLOAD_LIMIT) "[Response body omitted: over 32 KiB]"
                else SyncPayloadRedaction.body(bytes.toString(Charsets.UTF_8))
            }
        }.getOrDefault("[Response preview unavailable]")
        val backend = SyncPayloadRedaction.backendStatus(preview)
        emit(if (response.isSuccessful && (backend == null || backend == 200)) LogLevel.INFO else LogLevel.ERROR,
            "$prefix | phase=RESPONSE\nHTTP ${response.code} | backendStatusCode=${backend ?: "Not reported"} | durationMs=${(System.nanoTime() - started) / 1_000_000}\nResponse JSON (sensitive fields masked):\n$preview")
        return response
    }
}
