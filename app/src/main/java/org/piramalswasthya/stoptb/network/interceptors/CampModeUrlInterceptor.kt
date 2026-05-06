package org.piramalswasthya.stoptb.network.interceptors

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import java.io.IOException
import javax.inject.Inject

class CampModeUrlInterceptor @Inject constructor(
    private val preferenceDao: PreferenceDao
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        if (!preferenceDao.isCampModeEnabled() || !preferenceDao.isCampHubConnected()) {
            return chain.proceed(originalRequest)
        }

        val campHubUrl = preferenceDao.getCampHubUrl()
            .trim()
            .trimEnd('/')
            .plus("/")
            .toHttpUrlOrNull()
            ?: return chain.proceed(originalRequest)

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
            preferenceDao.setCampHubConnected(false)
            throw e
        }
    }
}
