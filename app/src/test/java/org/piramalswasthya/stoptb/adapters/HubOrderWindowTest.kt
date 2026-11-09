package org.piramalswasthya.stoptb.adapters

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao
import org.piramalswasthya.stoptb.model.TbDiagnosticsCardFields

class HubOrderWindowTest {

    private fun pref(camp: Boolean = true, hubCreates: Boolean = true): PreferenceDao {
        val pref = Mockito.mock(PreferenceDao::class.java)
        Mockito.`when`(pref.isCampModeEnabled()).thenReturn(camp)
        Mockito.`when`(pref.hubCreatesReferralOrders()).thenReturn(hubCreates)
        return pref
    }

    private fun diag(screenedAt: Long?) = TbDiagnosticsCardFields(benId = 1L, latestScreeningAt = screenedAt)

    @Test
    fun freshScreeningOnAHubThatCreatesOrdersWaits() {
        val remaining = hubOrderWindowRemaining(diag(System.currentTimeMillis() - 60_000L), pref())
        assertThat(remaining).isGreaterThan(0L)
        assertThat(remaining).isAtMost(HUB_ORDER_CREATION_WINDOW_MS - 60_000L)
    }

    @Test
    fun screeningOlderThanTheWindowOffersRetry() {
        val old = System.currentTimeMillis() - HUB_ORDER_CREATION_WINDOW_MS - 1_000L
        assertThat(hubOrderWindowRemaining(diag(old), pref())).isEqualTo(0L)
    }

    @Test
    fun hubThatDoesNotCreateOrdersOffersRetry() {
        val now = System.currentTimeMillis()
        assertThat(hubOrderWindowRemaining(diag(now), pref(hubCreates = false))).isEqualTo(0L)
        assertThat(hubOrderWindowRemaining(diag(now), pref(camp = false))).isEqualTo(0L)
        assertThat(hubOrderWindowRemaining(diag(now), null)).isEqualTo(0L)
    }

    @Test
    fun noScreeningTimeOffersRetry() {
        assertThat(hubOrderWindowRemaining(diag(null), pref())).isEqualTo(0L)
        assertThat(hubOrderWindowRemaining(null, pref())).isEqualTo(0L)
    }

    @Test
    fun screeningTimeAheadOfThisClockIsCappedToTheWindow() {
        val future = System.currentTimeMillis() + 60 * 60_000L
        assertThat(hubOrderWindowRemaining(diag(future), pref())).isEqualTo(HUB_ORDER_CREATION_WINDOW_MS)
    }
}
