package org.piramalswasthya.stoptb.utils.sync

import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test

class CampHubReachabilityMergeTest {
    private fun withHub(status: Int, verify: (String) -> Unit) {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            val request = AtomicReference<String>()
            val failure = AtomicReference<Throwable>()
            val responder = thread(isDaemon = true) {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 3000
                        val reader = socket.getInputStream().bufferedReader()
                        request.set(reader.readLine())
                        while (!reader.readLine().isNullOrEmpty()) { }
                        socket.getOutputStream().write(
                            "HTTP/1.1 $status Test\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                    }
                } catch (error: Throwable) { failure.set(error) }
            }
            verify("http://127.0.0.1:${server.localPort}")
            responder.join(5000)
            assertFalse("Health request must complete", responder.isAlive)
            failure.get()?.let { throw AssertionError("Local test hub failed", it) }
            assertEquals("GET /common-api/health HTTP/1.1", request.get())
        }
    }

    @Test fun healthyHubReportsResponseAndRemainsReachable() = withHub(200) { url ->
        var code = 0
        assertTrue(CampHubReachability.check(url, onResponse = { code = it }))
        assertEquals(200, code)
    }

    @Test fun httpFailureIsNotMistakenForHealthyHub() = withHub(503) { url ->
        var code = 0
        assertFalse(CampHubReachability.check(url, onResponse = { code = it }))
        assertEquals(503, code)
    }

    @Test fun diagnosticsFailureCannotChangeSuccessfulHealthResult() = withHub(204) { url ->
        assertTrue(CampHubReachability.check(url, onResponse = { error("diagnostics unavailable") }))
    }

    @Test fun failedCheckRemainsSafeWhenFailureObserverAlsoThrows() {
        var observed = false
        assertFalse(CampHubReachability.check("invalid-url", onFailure = {
            observed = true
            error("diagnostics unavailable")
        }))
        assertTrue(observed)
    }
}
