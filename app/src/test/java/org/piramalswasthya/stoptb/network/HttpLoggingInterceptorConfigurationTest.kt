package org.piramalswasthya.stoptb.network

import com.google.common.truth.Truth.assertThat
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Test
import org.piramalswasthya.stoptb.BuildConfig
import org.piramalswasthya.stoptb.di.AppModule

class HttpLoggingInterceptorConfigurationTest {
    @Test
    fun httpLoggingNeverLogsBodies() {
        val level = AppModule.provideHttpLoggingInterceptor().level

        assertThat(level).isNotEqualTo(HttpLoggingInterceptor.Level.BODY)
        assertThat(level).isEqualTo(
            if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        )
    }
}
