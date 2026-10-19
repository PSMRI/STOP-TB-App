package org.piramalswasthya.stoptb.helpers

import org.junit.Assert.*
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class CampConnectionHistoryTest {
    @Test fun hubDisplayLabelsDefaultAndCustomPortsWithoutChangingTheEndpoint() {
        assertEquals("Hub: https://hub.example\nPort: 443", campHubDisplay("https://hub.example").text())
        assertEquals("Hub: http://192.168.1.77\nPort: 8080", campHubDisplay("192.168.1.77:8080").text())
        assertEquals("http://[::1]", campHubDisplay("http://[::1]:8080").address)
        assertEquals("8080", campHubDisplay("http://[::1]:8080").port)
        assertEquals("Not reported", campHubDisplay(null).port)
        assertEquals("Invalid hub address", campHubDisplay("invalid address").address)
        assertEquals("https://hub.example", campHubDisplay("https://user:secret@hub.example/path?token=secret").address)
        assertEquals("https://hub.example:443", campDiagnosticHub("https://hub.example"))
    }
    @Test fun logoutDoesNotLosePreviousEndpointAndValidPreferencesTakePrecedence() {
        assertEquals("http://a.example:80", campPreviousHub(null, "http://a.example"))
        assertEquals("http://a.example:80", campPreviousHub(" ", "http://a.example"))
        assertTrue(campHubAddressChanged(campPreviousHub(null, "http://a.example"), "http://b.example"))
        assertFalse(campHubAddressChanged(campPreviousHub(null, "http://a.example"), "http://a.example"))
        assertEquals("http://b.example:80", campPreviousHub("http://b.example", "http://a.example"))
        assertFalse(campHubAddressChanged(campPreviousHub(null, null), "http://a.example"))
    }
    @Test fun addressChangesIgnoreSameEndpointFormattingPathsAndCredentials() {
        assertFalse(campHubAddressChanged("http://HUB.example/", "http://user:password@hub.example:80/path?token=secret"))
        assertFalse(campHubAddressChanged("192.168.1.77:8080", "http://192.168.1.77:8080/"))
        assertTrue(campHubAddressChanged("http://192.168.1.77:8080", "http://192.168.1.88:8080"))
        assertTrue(campHubAddressChanged("http://hub.example:8080", "http://hub.example:8081"))
    }

    @Test fun firstOrInvalidAddressDoesNotInventAChangeAndReturningToOldHubIsAnotherChange() {
        assertFalse(campHubAddressChanged(null, "http://hub.example"))
        assertFalse(campHubAddressChanged("http://hub.example", ""))
        assertFalse(campHubAddressChanged("invalid address", "http://hub.example"))
        val addresses = listOf("http://a.example", "http://b.example", "http://b.example/", "http://a.example")
        assertEquals(2, addresses.zipWithNext().count { campHubAddressChanged(it.first, it.second) })
        assertFalse(campConnectionTransition(observation(true), observation(false, hub = "http://new.example:80")))
    }

    @Test fun incompleteLegacyCountsAreNeverPresentedAsCompleteZeros() {
        assertEquals("0 recorded (partial)", campHubCount(0, true))
        assertEquals("2 recorded (partial)", campHubCount(2, true))
        assertEquals("0", campHubCount(0, false))
    }
    private fun observation(connected: Boolean, at: Long = 1, session: String = "session", hub: String = "http://192.168.1.77:8080") =
        CampConnectionObservation(hub, connected, at, if (connected) "REACHABLE" else "TIMEOUT", "TEST", session)

    @Test fun repeatedUnreachableAndConnectedChecksCountOnlyActualTransitions() {
        val observations = listOf(true, true, false, false, false, true, true, false, false)
        var previous: CampConnectionObservation? = null
        var connects = 0
        var disconnects = 0
        observations.forEachIndexed { index, value ->
            val next = observation(value, index.toLong())
            if (campConnectionTransition(previous, next)) {
                if (value) connects++ else disconnects++
            }
            previous = next
        }
        assertEquals(1, connects)
        assertEquals(2, disconnects)
    }

    @Test fun firstUnreachableObservationIsNotAnInventedDisconnect() {
        assertFalse(campConnectionTransition(null, observation(false)))
        assertFalse(campConnectionTransition(observation(false), observation(false).copy(reason = "CONNECTION_FAILED")))
        assertTrue(campConnectionTransition(observation(false), observation(true)))
    }

    @Test fun restartOrChangingHubCreatesABaselineNotAReconnect() {
        assertFalse(campConnectionTransition(observation(false), observation(true, session = "new-session")))
        assertFalse(campConnectionTransition(observation(true), observation(false, hub = "http://192.168.1.88:8080")))
    }

    @Test fun addressContainsPortButNeverCredentialsPathsOrQuerySecrets() {
        assertEquals("http://192.168.1.77:8080", campDiagnosticHub("192.168.1.77:8080/"))
        assertEquals("https://hub.example:443", campDiagnosticHub("https://secret:password@hub.example/path?token=secret"))
        assertEquals("http://[::1]:8080", campDiagnosticHub("http://[::1]:8080/"))
        assertEquals("Not configured", campDiagnosticHub(" "))
        assertEquals("Invalid hub address", campDiagnosticHub("ftp://hub.example"))
    }

    @Test fun knownFailureTypesAreReportedWithoutGuessingAServerOutage() {
        assertEquals("TIMEOUT", campConnectionFailure(SocketTimeoutException("secret")))
        assertEquals("HOST_LOOKUP_FAILED", campConnectionFailure(UnknownHostException("secret")))
        assertEquals("CONNECTION_FAILED", campConnectionFailure(ConnectException("secret")))
        assertEquals("UNKNOWN", campConnectionFailure(Exception("server is down")))
        assertTrue(campNetworkFailure("TIMEOUT"))
        assertFalse(campNetworkFailure("HTTP_503"))
        assertFalse(campNetworkFailure("USER_DISABLED"))
        assertTrue(campConnectionReason("HTTP_503").contains("503"))
        assertTrue(campConnectionReason("UNKNOWN").contains("not confirmed"))
    }
}
