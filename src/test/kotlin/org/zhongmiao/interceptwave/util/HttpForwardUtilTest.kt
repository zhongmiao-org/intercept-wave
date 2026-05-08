package org.zhongmiao.interceptwave.util

import com.sun.net.httpserver.Headers
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpPrincipal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.zhongmiao.interceptwave.model.HeaderOverrideOperation
import org.zhongmiao.interceptwave.model.HeaderOverrideRule
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient

class HttpForwardUtilTest {

    private class FakeExchange(
        private val method: String,
        private val uri: URI,
        body: ByteArray = ByteArray(0),
        headers: Headers = Headers()
    ) : HttpExchange() {
        private val reqHeaders = headers
        private val reqBody = ByteArrayInputStream(body)
        private val respHeaders = Headers()
        private val respBody = ByteArrayOutputStream()

        override fun getRequestHeaders(): Headers = reqHeaders
        override fun getResponseHeaders(): Headers = respHeaders
        override fun getRequestURI(): URI = uri
        override fun getRequestMethod(): String = method
        override fun getHttpContext() = throw UnsupportedOperationException()
        override fun close() {}
        override fun getRequestBody(): InputStream = reqBody
        override fun getResponseBody(): OutputStream = respBody
        override fun sendResponseHeaders(rCode: Int, responseLength: Long) {}
        override fun getRemoteAddress(): InetSocketAddress = InetSocketAddress("127.0.0.1", 12345)
        override fun getResponseCode(): Int = 200
        override fun getLocalAddress(): InetSocketAddress = InetSocketAddress("127.0.0.1", 8888)
        override fun getProtocol(): String = "HTTP/1.1"
        override fun getAttribute(name: String): Any? = null
        override fun setAttribute(name: String, value: Any?) {}
        override fun setStreams(i: InputStream?, o: OutputStream?) {}
        override fun getPrincipal(): HttpPrincipal? = null
    }

    @Test
    fun createClient_prefers_http_1_1() {
        val client = HttpForwardUtil.createClient()
        assertEquals(HttpClient.Version.HTTP_1_1, client.version())
    }

    @Test
    fun buildRequest_copies_safe_headers_and_body() {
        val headers = Headers().apply {
            add("Authorization", "Bearer demo")
            add("Content-Type", "application/json")
            add("Host", "should-not-copy")
        }
        val exchange = FakeExchange(
            method = "POST",
            uri = URI("http://localhost:8888/api/orders"),
            body = """{"id":1}""".toByteArray(),
            headers = headers
        )

        val request = HttpForwardUtil.buildRequest("http://localhost:9001/orders", exchange)
        assertEquals("POST", request.method())
        assertEquals(URI("http://localhost:9001/orders"), request.uri())
        val copiedHeaders = request.headers().map()
        assertEquals(listOf("Bearer demo"), copiedHeaders["Authorization"])
        assertEquals(listOf("application/json"), copiedHeaders["Content-Type"])
        assertFalse(copiedHeaders.containsKey("Host"))
        assertTrue(request.bodyPublisher().isPresent)
    }

    @Test
    fun buildRequest_applies_request_header_override_rules() {
        val headers = Headers().apply {
            add("X-Trace", "original")
            add("X-Remove", "delete-me")
            add("Host", "should-not-copy")
        }
        val exchange = FakeExchange(
            method = "GET",
            uri = URI("http://localhost:8888/api/users"),
            headers = headers
        )

        val request = HttpForwardUtil.buildRequest(
            "http://localhost:9001/users",
            exchange,
            listOf(
                HeaderOverrideRule("X-Trace", "override", HeaderOverrideOperation.SET, enabled = true),
                HeaderOverrideRule("X-Trace", "second", HeaderOverrideOperation.ADD, enabled = true),
                HeaderOverrideRule("X-Remove", "", HeaderOverrideOperation.REMOVE, enabled = true),
                HeaderOverrideRule("X-Disabled", "ignored", HeaderOverrideOperation.SET, enabled = false),
                HeaderOverrideRule("Host", "evil.example", HeaderOverrideOperation.SET, enabled = true)
            )
        )

        val copiedHeaders = request.headers().map()
        assertEquals(listOf("override", "second"), copiedHeaders["X-Trace"])
        assertFalse(copiedHeaders.containsKey("X-Remove"))
        assertFalse(copiedHeaders.containsKey("X-Disabled"))
        assertFalse(copiedHeaders.containsKey("Host"))
    }
}
