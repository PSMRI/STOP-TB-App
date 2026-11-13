package org.piramalswasthya.stoptb.network.interceptors

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.helpers.TokenExpiryManager
import org.piramalswasthya.stoptb.network.AmritApiService

class TokenAuthenticatorTest {

    @Test
    fun `third rejected authenticated request requires login`() {
        val manager = mock(TokenExpiryManager::class.java)
        `when`(manager.loginRequired).thenReturn(MutableStateFlow(false))
        val authenticator = TokenAuthenticator(
            mock(PreferenceDao::class.java),
            mock(AmritApiService::class.java),
            manager
        )
        val first = unauthorized()
        val second = unauthorized(first)

        assertThat(authenticator.authenticate(null, unauthorized(second))).isNull()
        verify(manager).onRefreshFailed()
    }

    private fun unauthorized(prior: Response? = null): Response = Response.Builder()
        .request(Request.Builder().url("https://hub.example/flw-api/stoptb/sync/changes")
            .header("Jwttoken", "rejected-token").build())
        .protocol(Protocol.HTTP_1_1)
        .code(401)
        .message("Unauthorized")
        .apply { prior?.let { priorResponse(it) } }
        .build()
}
