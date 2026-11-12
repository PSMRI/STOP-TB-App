package org.piramalswasthya.stoptb.network.interceptors

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import org.json.JSONObject
import org.piramalswasthya.stoptb.crypt.CryptoUtil
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.helpers.TokenExpiryManager
import org.piramalswasthya.stoptb.network.AmritApiService
import org.piramalswasthya.stoptb.network.TmcRefreshTokenRequest
import org.piramalswasthya.stoptb.network.TmcAuthUserRequest
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named

class TokenAuthenticator @Inject constructor(
    private val pref: PreferenceDao,
    @Named("authApi") private val authApi: AmritApiService,
    private val tokenExpiryManager: TokenExpiryManager
) : Authenticator {

    private val refreshLock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {

        val attempt = responseCount(response)
        if (attempt >= 3) return null
        if (response.request.header("No-Auth") == "true") return null
        if (tokenExpiryManager.loginRequired.value) return null

        val oldJwt = response.request.header("Jwttoken")
        val newJwt = synchronized(refreshLock) {
            val currentJwt = pref.getJWTAmritToken()
            if (!currentJwt.isNullOrBlank() && currentJwt != oldJwt) {
                return@synchronized currentJwt
            }
            runBlocking {
                try {
                    val refreshed = if (attempt == 1) try {
                        pref.getRefreshToken()?.let { refreshToken ->
                            val resp = authApi.getRefreshToken(TmcRefreshTokenRequest(refreshToken))
                            if (resp.isSuccessful) {
                                val json = JSONObject(resp.body()?.string().orEmpty())
                                val data = json.optJSONObject("data") ?: json
                                data.optString("jwtToken").takeIf { it.isNotBlank() }?.also { jwt ->
                                    pref.registerJWTAmritToken(jwt)
                                    pref.registerRefreshToken(data.optString("refreshToken", refreshToken))
                                }
                            } else {
                                Timber.w("Token refresh failed: HTTP ${resp.code()}")
                                resp.errorBody()?.close()
                                null
                            }
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Token refresh failed; trying login")
                        null
                    } else null
                    if (refreshed != null) {
                        tokenExpiryManager.onRefreshSuccess()
                        refreshed
                    } else {
                        // A revoked or expired refresh token can still be recovered with the
                        // current user's saved credentials. Keep all local rows and cursors.
                        val user = pref.getLoggedInUser() ?: run {
                            tokenExpiryManager.onRefreshFailed()
                            return@runBlocking null
                        }
                        val login = authApi.getJwtToken(
                            TmcAuthUserRequest(user.userName, CryptoUtil().encrypt(user.password))
                        )
                        if (!login.isSuccessful) {
                            login.errorBody()?.close()
                            if (login.code() == 401 || login.code() == 403)
                                tokenExpiryManager.onRefreshFailed()
                            return@runBlocking null
                        }
                        val data = JSONObject(login.body()?.string().orEmpty())
                            .optJSONObject("data") ?: run {
                            tokenExpiryManager.onRefreshFailed()
                            return@runBlocking null
                        }
                        // Never replace this tablet's identity with another user's session.
                        if (data.optInt("userID", -1) != user.userId) {
                            tokenExpiryManager.onRefreshFailed()
                            return@runBlocking null
                        }
                        val jwt = data.optString("jwtToken")
                        val newRefresh = data.optString("refreshToken")
                        if (jwt.isBlank() || newRefresh.isBlank()) {
                            tokenExpiryManager.onRefreshFailed()
                            return@runBlocking null
                        }
                        pref.registerJWTAmritToken(jwt)
                        pref.registerRefreshToken(newRefresh)
                        data.optString("key").takeIf { it.isNotBlank() }
                            ?.let { pref.registerAmritToken(it) }
                        tokenExpiryManager.onRefreshSuccess()
                        jwt
                    }

                } catch (e: IOException) {
                    // Network failure is temporary; the next worker run can retry.
                    Timber.w(e, "Authentication network error")
                    null
                } catch (e: Exception) {
                    Timber.e(e, "Token refresh failed")
                    tokenExpiryManager.onRefreshFailed()
                    null
                }
            }
        } ?: return null

        return response.request.newBuilder()
            .removeHeader("Jwttoken")
            .header("Jwttoken", newJwt)
            .build()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
