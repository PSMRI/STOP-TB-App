package org.piramalswasthya.stoptb.utils.sync

import android.content.Context
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.piramalswasthya.stoptb.database.shared_preferences.PreferenceDao

/** Tablets report their app version only at a real camp, where the hub URL is an IP address. */
class TabletVersionReporterTest {

    @Test
    fun `an IP hub becomes the laptop's tablet port`() {
        assertThat(TabletVersionReporter.reportUrl("http://192.168.137.1:8080/"))
            .isEqualTo("http://192.168.137.1:9091/tablet/report")
        assertThat(TabletVersionReporter.reportUrl("http://172.18.0.157:8080"))
            .isEqualTo("http://172.18.0.157:9091/tablet/report")
        assertThat(TabletVersionReporter.reportUrl("10.0.0.5:8080"))
            .isEqualTo("http://10.0.0.5:9091/tablet/report")
        assertThat(TabletVersionReporter.reportUrl(" https://192.168.1.50 "))
            .isEqualTo("http://192.168.1.50:9091/tablet/report")
    }

    @Test
    fun `hostnames, public IPs and junk are not a camp`() {
        listOf(
            "https://uatstoptb.piramalswasthya.org/",   // UAT flavor
            "http://localhost:8080",
            "http://camp-hub.local:8080",
            "http://999.1.1.1:8080",
            "http://8.8.8.8:8080",                       // public IP: never gets the tablet id
            "http://172.32.0.1:8080",
            "http://127.0.0.1:8080",
            "",
            null,
            "not a url"
        ).forEach { assertThat(TabletVersionReporter.reportUrl(it)).isNull() }
    }

    @Test
    fun `a skipped report says why`() {
        val context = mock(Context::class.java)
        val off = mock(PreferenceDao::class.java)
        `when`(off.isCampModeEnabled()).thenReturn(false)
        assertThat(TabletVersionReporter.report(context, off))
            .isEqualTo(TabletVersionReporter.Outcome(TabletVersionReporter.Outcome.Kind.SKIPPED, "camp mode is off"))

        val uat = mock(PreferenceDao::class.java)
        `when`(uat.isCampModeEnabled()).thenReturn(true)
        `when`(uat.getCampHubUrl()).thenReturn("https://uatstoptb.piramalswasthya.org/")
        val outcome = TabletVersionReporter.report(context, uat)
        assertThat(outcome.kind).isEqualTo(TabletVersionReporter.Outcome.Kind.SKIPPED)
        assertThat(outcome.message).contains("uatstoptb.piramalswasthya.org")
    }
}
