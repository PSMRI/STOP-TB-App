package org.piramalswasthya.stoptb.network.interceptors

import android.os.SystemClock
import okhttp3.Interceptor
import okhttp3.Response
import org.piramalswasthya.stoptb.utils.ScreenLoadTrace
import javax.inject.Inject

/**
 * TEMPORARY PERFORMANCE INSTRUMENTATION - remove with the rest of the trace scaffolding.
 *
 * Records one compact line per HTTP call (method, path, status, duration, body size) under the
 * same tag as the screen trace, so API timing lines up with screen lifecycle, Room flow
 * emissions and Paging loads on a single timeline.
 *
 * It is added LAST in the chain so it wraps the existing interceptors and measures what the
 * caller actually waits for, and so the order of the existing interceptors is unchanged.
 * It reads no body and modifies nothing.
 */
class PerfTimingInterceptor @Inject constructor() : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startedAt = SystemClock.elapsedRealtime()
        try {
            val response = chain.proceed(request)
            ScreenLoadTrace.apiCall(
                method = request.method,
                path = request.url.encodedPath,
                code = response.code,
                durationMs = SystemClock.elapsedRealtime() - startedAt,
                bytes = response.body?.contentLength() ?: -1L
            )
            return response
        } catch (e: Exception) {
            ScreenLoadTrace.apiCall(
                method = request.method,
                path = request.url.encodedPath,
                code = -1,
                durationMs = SystemClock.elapsedRealtime() - startedAt,
                bytes = -1L
            )
            throw e
        }
    }
}
