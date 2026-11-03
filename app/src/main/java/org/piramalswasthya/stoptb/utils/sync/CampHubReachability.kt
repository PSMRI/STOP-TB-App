package org.piramalswasthya.stoptb.utils.sync

import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks whether the camp hub answers its health endpoint. Blocking network
 * call: use it off the main thread. Shared by the camp-mode interceptor, the
 * connect and sign-in screens, and the sync gate.
 */
object CampHubReachability {

    private const val TIMEOUT_MS = 3000

    fun healthUrl(rawUrl: String): String = "${rawUrl.trim().trimEnd('/')}/common-api/health"

    fun isReachable(rawUrl: String): Boolean {
        val healthUrl = healthUrl(rawUrl)
        var connection: HttpURLConnection? = null
        return runCatching {
            connection = URL(healthUrl).openConnection() as HttpURLConnection
            connection?.connectTimeout = TIMEOUT_MS
            connection?.readTimeout = TIMEOUT_MS
            connection?.requestMethod = "GET"
            connection?.useCaches = false
            val responseCode = connection?.responseCode ?: return@runCatching false
            Timber.d("Camp hub health check: url=$healthUrl, code=$responseCode")
            responseCode in 200..299
        }.onFailure { error ->
            Timber.w("Camp hub health check failed: url=$healthUrl, error=${error.javaClass.simpleName}: ${error.message}")
        }.also {
            connection?.disconnect()
        }.getOrDefault(false)
    }
}
