package org.piramalswasthya.stoptb.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The camp-hub URL comes from a scanned QR code and re-points every authenticated request,
 * including the refresh-token call. These tests pin the rule that only local-network addresses
 * are accepted.
 */
class CampHubUrlValidatorTest {

    @Test
    fun `accepts the private hub addresses actually used in the field`() {
        listOf(
            "http://172.18.0.157:8080",      // the camp hub this was audited against
            "http://192.168.137.1:8080/",    // PreferenceDao.defaultCampHubUrl fallback
            "10.0.0.5:8080",                 // bare host:port, scheme inferred
            "https://192.168.1.50",
            "http://localhost:8080",
            "http://camp-hub.local:8080"
        ).forEach { input ->
            assertThat(CampHubUrlValidator.isAllowed(input)).isTrue()
        }
    }

    @Test
    fun `rejects public hosts - the QR redirect attack`() {
        listOf(
            "http://evil.example.com/",
            "https://evil.example.com/",     // TLS does not make it trustworthy
            "evil.example.com",              // bare, scheme inferred, still public
            "http://8.8.8.8:8080",
            "http://172.32.0.1:8080"         // just outside 172.16/12
        ).forEach { input ->
            assertThat(CampHubUrlValidator.isAllowed(input)).isFalse()
        }
    }

    @Test
    fun `rejects blank, malformed and non-http schemes`() {
        listOf(null, "", "   ", "not a url", "ftp://192.168.1.5/", "file:///etc/passwd")
            .forEach { input ->
                assertThat(CampHubUrlValidator.isAllowed(input)).isFalse()
            }
    }

    @Test
    fun `strips credentials, path, query and fragment from the stored value`() {
        val normalized = CampHubUrlValidator.normalizeOrNull(
            "http://user:pass@192.168.1.5:8080/some/path?token=abc#frag"
        )
        assertThat(normalized).isNotNull()
        assertThat(normalized).doesNotContain("user")
        assertThat(normalized).doesNotContain("pass")
        assertThat(normalized).doesNotContain("some/path")
        assertThat(normalized).doesNotContain("token=abc")
        assertThat(normalized).doesNotContain("frag")
        assertThat(normalized).isEqualTo("http://192.168.1.5:8080/")
    }

    @Test
    fun `rejects an absurdly long value rather than storing it`() {
        val longHost = "http://192.168.1.5:8080/" + "a".repeat(4096)
        assertThat(CampHubUrlValidator.isAllowed(longHost)).isFalse()
    }

    @Test
    fun `172 range boundaries follow RFC1918`() {
        assertThat(CampHubUrlValidator.isAllowed("http://172.15.255.255:80")).isFalse()
        assertThat(CampHubUrlValidator.isAllowed("http://172.16.0.0:80")).isTrue()
        assertThat(CampHubUrlValidator.isAllowed("http://172.31.255.255:80")).isTrue()
        assertThat(CampHubUrlValidator.isAllowed("http://172.32.0.0:80")).isFalse()
    }
}
