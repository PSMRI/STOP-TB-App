package org.piramalswasthya.stoptb.network.interceptors

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
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

        val storedCampHubUrl = preferenceDao.getStoredCampHubUrl()
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        if (storedCampHubUrl == null) {
            throw IOException("Camp hub URL not configured")
        }

        val campHubUrl = storedCampHubUrl
            .trim()
            .trimEnd('/')
            .plus("/")
            .toHttpUrlOrNull()
            ?: throw IOException("Invalid camp hub URL: $storedCampHubUrl")

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
