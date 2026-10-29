package org.piramalswasthya.stoptb.utils

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.Inet4Address
import java.net.InetAddress

/**
 * Validates a camp-hub base URL before it is stored or used to re-point a request.
 *
 * A camp hub is a laptop or small server on the same local network as the tablet. The URL is
 * obtained by scanning a QR code, which is attacker-supplied input: anyone who can put a printed
 * code in front of a field worker controls it.
 *
 * That matters because [org.piramalswasthya.stoptb.network.interceptors.CampModeUrlInterceptor]
 * rewrites the scheme, host and port of every request on the client while preserving the path,
 * the query and **all headers** - including the `Jwttoken` and `userId` added upstream, and the
 * refresh-token call on the auth client. Without this check, one scanned code redirects the
 * session token, the refresh token and every beneficiary record to an arbitrary host, in the
 * clear (`network_security_config.xml` permits cleartext).
 *
 * So the rule is: a camp hub must live on the local network. Public hosts are refused.
 */
object CampHubUrlValidator {

    /** Hostnames that resolve to the device itself or to an mDNS name on the local segment. */
    private val allowedLocalSuffixes = listOf(".local", ".lan", ".home")

    /**
     * Normalises [raw] into a canonical base URL, or returns null when it is not a usable
     * local camp-hub address. A bare host or `host:port` is assumed to be `http`, which is how
     * the LAN hubs are addressed.
     */
    fun normalizeOrNull(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isBlank() || trimmed.length > MAX_URL_LENGTH) return null

        val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
        val parsed = withScheme.toHttpUrlOrNull() ?: return null

        // toHttpUrlOrNull only ever yields http/https, but be explicit: anything else would be
        // a scheme OkHttp cannot carry and must not be stored.
        if (parsed.scheme != "http" && parsed.scheme != "https") return null
        if (!isLocalHost(parsed.host)) return null

        return parsed.newBuilder()
            .encodedPath("/")
            .query(null)
            .fragment(null)
            .username("")
            .password("")
            .build()
            .toString()
    }

    /** True when [candidate] is already an acceptable camp-hub URL. */
    fun isAllowed(candidate: String?): Boolean = normalizeOrNull(candidate) != null

    /** True when [url]'s host is on the local network, for re-checking at request time. */
    fun isLocalHost(url: HttpUrl): Boolean = isLocalHost(url.host)

    private fun isLocalHost(host: String): Boolean {
        if (host.isBlank()) return false
        val lower = host.lowercase()
        if (lower == "localhost") return true
        if (allowedLocalSuffixes.any { lower.endsWith(it) }) return true

        // Only literal IPs are resolved here - calling InetAddress on a DNS name would hit the
        // network from whatever thread we are on, and a name that resolves privately today can
        // resolve publicly tomorrow (DNS rebinding).
        val literal = parseIpLiteral(lower) ?: return false
        return literal.isSiteLocalAddress ||      // 10/8, 172.16/12, 192.168/16
                literal.isLoopbackAddress ||      // 127/8, ::1
                literal.isLinkLocalAddress ||     // 169.254/16, fe80::/10
                literal.isAnyLocalAddress
    }

    /** Parses [host] as an IP literal without ever performing a DNS lookup. */
    private fun parseIpLiteral(host: String): InetAddress? {
        val bare = host.removeSurrounding("[", "]")
        val looksNumericV4 = bare.count { it == '.' } == 3 && bare.all { it.isDigit() || it == '.' }
        val looksV6 = bare.contains(':')
        if (!looksNumericV4 && !looksV6) return null
        return runCatching {
            if (looksNumericV4) Inet4Address.getByName(bare) else InetAddress.getByName(bare)
        }.getOrNull()
    }

    private const val MAX_URL_LENGTH = 2048
}
