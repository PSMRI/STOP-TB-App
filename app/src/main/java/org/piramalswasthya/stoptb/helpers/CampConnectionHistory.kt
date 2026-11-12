package org.piramalswasthya.stoptb.helpers

import java.net.URI
import java.net.ConnectException
import java.net.MalformedURLException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

internal data class CampConnectionObservation(val hub: String, val connected: Boolean,
    val at: Long, val reason: String, val source: String, val session: String)
internal data class CampHubChange(val at: Long, val fromHub: String, val toHub: String)
internal data class CampHubSummary(val hub: String, val connects: Long, val disconnects: Long,
    val unreachable: Long, val lastSeen: Long, val partial: Boolean)
internal fun campHubCount(value: Long, partial: Boolean): String =
    if (partial) "$value recorded (partial)" else value.toString()

internal data class CampHubDisplay(val address: String, val port: String) {
    fun text(label: String = "Hub") = "$label: $address\nPort: $port"
}

internal fun campHubDisplay(raw: String?): CampHubDisplay {
    val hub = campDiagnosticHub(raw)
    return if (hub.startsWith("http://") || hub.startsWith("https://")) {
        CampHubDisplay(hub.substringBeforeLast(':'), hub.substringAfterLast(':'))
    } else CampHubDisplay(hub, "Not reported")
}

/** Preferences can be cleared on logout; the diagnostic journal retains the last endpoint. */
internal fun campPreviousHub(preference: String?, retained: String?): String =
    campDiagnosticHub(preference).takeIf { it.startsWith("http://") || it.startsWith("https://") }
        ?: campDiagnosticHub(retained)

internal fun campHubAddressChanged(old: String?, new: String?): Boolean {
    val from = campDiagnosticHub(old)
    val to = campDiagnosticHub(new)
    return from.startsWith("http") && to.startsWith("http") && from != to
}

/** No credentials, paths or query parameters are retained in diagnostics. */
internal fun campDiagnosticHub(raw: String?): String = runCatching {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return "Not configured"
    val uri = URI(if (value.contains("://")) value else "http://$value")
    val scheme = uri.scheme?.lowercase()
    require(scheme in setOf("http", "https") && !uri.host.isNullOrBlank())
    val host = uri.host.lowercase().removeSurrounding("[", "]")
    val displayHost = if (host.contains(':')) "[$host]" else host
    "$scheme://$displayHost:${if (uri.port >= 0) uri.port else if (scheme == "https") 443 else 80}"
}.getOrDefault("Invalid hub address")

internal fun campConnectionTransition(previous: CampConnectionObservation?, next: CampConnectionObservation): Boolean =
    previous != null && previous.session == next.session && previous.hub == next.hub &&
        previous.connected != next.connected

internal fun campConnectionFailure(error: Throwable): String = when (error) {
    is SocketTimeoutException -> "TIMEOUT"
    is UnknownHostException -> "HOST_LOOKUP_FAILED"
    is NoRouteToHostException -> "NETWORK_UNREACHABLE"
    is ConnectException -> "CONNECTION_FAILED"
    is SSLException -> "TLS_ERROR"
    is MalformedURLException, is IllegalArgumentException -> "INVALID_HUB_ADDRESS"
    else -> "UNKNOWN"
}

internal fun campNetworkFailure(reason: String) = reason in setOf(
    "TIMEOUT", "HOST_LOOKUP_FAILED", "NETWORK_UNREACHABLE", "CONNECTION_FAILED", "WIFI_UNAVAILABLE")

internal fun campConnectionReason(reason: String): String = when {
    reason == "TIMEOUT" -> "Connection timed out"
    reason == "HOST_LOOKUP_FAILED" -> "Hub hostname could not be resolved"
    reason == "NETWORK_UNREACHABLE" -> "Network route unavailable"
    reason == "CONNECTION_FAILED" -> "Connection failed; server-down/refusal is not confirmed"
    reason == "WIFI_UNAVAILABLE" -> "Wi-Fi unavailable (detected by app)"
    reason == "TLS_ERROR" -> "Secure connection (TLS) failed"
    reason == "INVALID_HUB_ADDRESS" -> "Hub address is invalid"
    reason == "USER_DISABLED" -> "Camp mode disabled by user"
    reason == "REACHABLE" -> "Hub check succeeded"
    reason.startsWith("HTTP_") -> "Hub responded with HTTP ${reason.removePrefix("HTTP_")}; check was not accepted"
    else -> "Unknown; exact cause not confirmed"
}
