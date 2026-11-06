package org.piramalswasthya.stoptb.helpers

import java.io.IOException
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.ArgumentCaptor
import org.mockito.stubbing.Stubber
import org.piramalswasthya.stoptb.model.LogLevel
import okio.BufferedSink
import okhttp3.RequestBody
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.asContextElement

class SyncApiPayloadInterceptorTest {
    // Local JVM-8 helpers avoid inlining the dependency's JVM-11 Kotlin helpers.
    private inline fun <reified T : Any> mock(): T = Mockito.mock(T::class.java)
    private fun <T> whenever(value: T) = Mockito.`when`(value)
    private fun <T> Stubber.whenever(value: T): T = `when`(value)
    private inline fun <reified T : Any> argumentCaptor(): ArgumentCaptor<T> = ArgumentCaptor.forClass(T::class.java)
    private inline fun <reified T : Any> any(): T {
        Mockito.any(T::class.java)
        return when (T::class.java) {
            String::class.java -> ""
            LogLevel::class.java -> LogLevel.INFO
            Request::class.java -> Request.Builder().url("https://example.test/matcher").build()
            else -> error("Unsupported matcher type")
        } as T
    }
    @Test fun oneShotBodiesAreNotReadAndOversizedResponsesAreExplicitlyOmitted() {
        val messages = mutableListOf<String>()
        val logs = mock<SyncLogManager>()
        doAnswer { messages.add(it.getArgument(2)); null }.whenever(logs).addLog(any(), any(), any())
        var writes = 0
        val body = object : RequestBody() {
            override fun contentType() = "application/json".toMediaType()
            override fun isOneShot() = true
            override fun writeTo(sink: BufferedSink) { writes++ }
        }
        val request = Request.Builder().url("https://example.test/save").post(body).tag(SyncHttpOrigin::class.java, origin).build()
        val chain = mock<Interceptor.Chain>()
        whenever(chain.request()).thenReturn(request)
        val large = "x".repeat(SYNC_PAYLOAD_LIMIT.toInt() + 10)
        whenever(chain.proceed(request)).thenReturn(response(request, large))
        val actual = SyncApiPayloadInterceptor(logs).intercept(chain)
        assertEquals(0, writes)
        assertEquals(large, actual.body!!.string())
        assertTrue(messages[0].contains("Streaming request omitted"))
        assertTrue(messages[1].contains("Response body omitted: over 32 KiB"))
    }
    @Test fun diagnosticFailureCannotBreakTheApiCall() {
        val logs = mock<SyncLogManager>()
        doThrow(IllegalStateException("logger unavailable")).whenever(logs).addLog(any(), any(), any())
        val request = Request.Builder().url("https://example.test/save").tag(SyncHttpOrigin::class.java, origin).build()
        val chain = mock<Interceptor.Chain>()
        whenever(chain.request()).thenReturn(request)
        val expected = response(request, "{\"statusCode\":200}")
        whenever(chain.proceed(request)).thenReturn(expected)
        assertSame(expected, SyncApiPayloadInterceptor(logs).intercept(chain))
    }
    @Test fun directSaveContextRestoresThreadStateAndNeverRelabelsAutomaticWork() = runBlocking {
        assertNull(SyncHttpOrigins.current.get())
        withDirectSaveHttpOrigin { assertEquals("DIRECT_SAVE", SyncHttpOrigins.current.get()?.trigger) }
        assertNull(SyncHttpOrigins.current.get())
        val auto = SyncHttpOrigin("auto", "PULL", "AUTO_PULL")
        withContext(SyncHttpOrigins.current.asContextElement(auto)) {
            withDirectSaveHttpOrigin { assertSame(auto, SyncHttpOrigins.current.get()) }
        }
        assertNull(SyncHttpOrigins.current.get())
    }
    private val origin = SyncHttpOrigin("manual-run", "PUSH", "MANUAL_REFRESH")
    private fun response(request: Request, body: String) = Response.Builder().request(request)
        .protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body.toResponseBody("application/json".toMediaType())).build()

    @Test fun capturesFailureBodyAndPreservesRequestAndReadableResponse() {
        val messages = mutableListOf<String>()
        val levels = mutableListOf<LogLevel>()
        val logs = mock<SyncLogManager>()
        doAnswer { levels.add(it.getArgument(0)); messages.add(it.getArgument(2)); null }.whenever(logs).addLog(any(), any(), any())
        val request = Request.Builder().url("https://example.test/generalOpd/save?token=HIDE_QUERY&villageId=12")
            .header("Authorization", "Bearer HIDE_HEADER")
            .post("""{"drugID":1341,"mobileNo":"HIDE_PHONE","duration":null}""".toRequestBody("application/json".toMediaType()))
            .tag(SyncHttpOrigin::class.java, origin).build()
        val body = """{"statusCode":5000,"errorMessage":"Beneficiary missing","token":"HIDE_RESPONSE"}"""
        val expected = response(request, body)
        val chain = mock<Interceptor.Chain>()
        whenever(chain.request()).thenReturn(request)
        whenever(chain.proceed(request)).thenReturn(expected)
        val actual = SyncApiPayloadInterceptor(logs).intercept(chain)
        assertSame(expected, actual)
        assertEquals(body, actual.body!!.string())
        verify(chain).proceed(request)
        assertEquals(2, messages.size)
        assertTrue(messages[0].contains("phase=REQUEST"))
        assertTrue(messages[1].contains("backendStatusCode=5000"))
        assertTrue(messages[1].contains("Beneficiary missing"))
        assertEquals(LogLevel.ERROR, levels[1])
        listOf("HIDE_QUERY", "HIDE_HEADER", "HIDE_PHONE", "HIDE_RESPONSE").forEach { secret -> assertFalse(messages.joinToString().contains(secret)) }
        assertEquals("Bearer HIDE_HEADER", request.header("Authorization"))
    }
    @Test fun automaticAndUnattributedRequestsAreNotCaptured() {
        for (source in listOf(null, SyncHttpOrigin("auto", "PULL", "AUTO_PULL"))) {
            val logs = mock<SyncLogManager>()
            val request = Request.Builder().url("https://example.test/getAll").tag(SyncHttpOrigin::class.java, source).build()
            val chain = mock<Interceptor.Chain>()
            whenever(chain.request()).thenReturn(request)
            val expected = response(request, "{}")
            whenever(chain.proceed(request)).thenReturn(expected)
            assertSame(expected, SyncApiPayloadInterceptor(logs).intercept(chain))
            verifyNoInteractions(logs)
        }
    }
    @Test fun networkFailureIsPairedAndOriginalExceptionRethrown() {
        val messages = mutableListOf<String>()
        val logs = mock<SyncLogManager>()
        doAnswer { messages.add(it.getArgument(2)); null }.whenever(logs).addLog(any(), any(), any())
        val request = Request.Builder().url("https://example.test/save").tag(SyncHttpOrigin::class.java, origin).build()
        val chain = mock<Interceptor.Chain>()
        whenever(chain.request()).thenReturn(request)
        val failure = IOException("HIDE_EXCEPTION_DETAIL")
        whenever(chain.proceed(request)).thenThrow(failure)
        try { SyncApiPayloadInterceptor(logs).intercept(chain); fail("Expected IO failure") }
        catch (actual: IOException) { assertSame(failure, actual) }
        assertTrue(messages.last().contains("phase=ERROR"))
        assertTrue(messages.last().contains("IOException"))
        assertFalse(messages.last().contains("HIDE_EXCEPTION_DETAIL"))
        assertEquals(summaryFields(messages.first().substringBefore('\n'))["callId"], summaryFields(messages.last().substringBefore('\n'))["callId"])
    }
    @Test fun retrofitCallFactoryCapturesOriginWithoutAddingWireHeaders() {
        val delegate = mock<Call.Factory>()
        whenever(delegate.newCall(any())).thenReturn(mock())
        val request = Request.Builder().url("https://example.test/save").build()
        SyncHttpOrigins.current.set(origin)
        try { SyncHttpCallFactory(delegate).newCall(request) } finally { SyncHttpOrigins.current.remove() }
        val captor = argumentCaptor<Request>()
        verify(delegate).newCall(captor.capture() ?: Request.Builder().url("https://example.test/matcher").build())
        assertEquals(origin, captor.value.tag(SyncHttpOrigin::class.java))
        assertEquals(request.headers, captor.value.headers)
        assertEquals(request.url, captor.value.url)
    }
}
