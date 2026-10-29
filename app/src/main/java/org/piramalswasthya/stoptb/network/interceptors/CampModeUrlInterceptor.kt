package org.piramalswasthya.stoptb.network.interceptors

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.utils.CampHubUrlValidator
import java.net.HttpURLConnection
import java.net.URL
import java.io.IOException
import java.io.InterruptedIOException
import javax.inject.Inject

class CampModeUrlInterceptor @Inject constructor(
    private val preferenceDao: PreferenceDao
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Only redirect while camp mode is actually on. This used to key off the stored URL
        // alone, so a URL left behind by a failed connect kept re-pointing every request even
        // after the user turned camp mode off.
        if (!preferenceDao.isCampModeEnabled()) {
            return chain.proceed(originalRequest)
        }

        val storedCampHubUrl = preferenceDao.getStoredCampHubUrl()
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        if (storedCampHubUrl == null) {
            throw IOException("Camp hub URL not configured")
        }

        // Re-validate at request time, not just at storage time: this request already carries
        // the Jwttoken/userId headers, and on the auth client it is the refresh-token call.
        // A stored value that is not a local-network address must never receive either.
        val campHubUrl = CampHubUrlValidator.normalizeOrNull(storedCampHubUrl)
            ?.toHttpUrlOrNull()
            ?: throw IOException("Refusing to route request to non-local camp hub URL")

        val campUrl = originalRequest.url.newBuilder()
            .scheme(campHubUrl.scheme)
            .host(campHubUrl.host)
            .port(campHubUrl.port)
            .build()

        return try {
            chain.proceed(
                originalRequest.newBuilder()
                    .url(campUrl)
                    .build()
            )
        } catch (e: IOException) {
            if (e is InterruptedIOException || e.message.equals("Canceled", ignoreCase = true)) {
                throw e
            }
            if (!isCampHubReachable(storedCampHubUrl)) {
                preferenceDao.setCampHubConnected(false)
            }
            throw e
        }
    }

    private fun isCampHubReachable(rawUrl: String): Boolean {
        val baseUrl = rawUrl.trim().trimEnd('/')
        val healthUrl = "$baseUrl/common-api/health"
        var connection: HttpURLConnection? = null

        return runCatching {
            connection = URL(healthUrl).openConnection() as HttpURLConnection
            connection?.connectTimeout = 3000
            connection?.readTimeout = 3000
            connection?.requestMethod = "GET"
            connection?.useCaches = false
            val responseCode = connection?.responseCode ?: return@runCatching false
            responseCode in 200..299
        }.getOrDefault(false).also {
            connection?.disconnect()
        }
    }
}
