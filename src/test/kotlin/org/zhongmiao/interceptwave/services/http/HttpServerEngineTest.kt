package org.zhongmiao.interceptwave.services.http

import org.junit.Assert.*
import org.junit.Test
import org.zhongmiao.interceptwave.events.*
import org.zhongmiao.interceptwave.model.MockApiConfig
import org.zhongmiao.interceptwave.model.ProxyConfig
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URI

class HttpServerEngineTest {

    private class TestOutput : MockServerOutput {
        val events = mutableListOf<MockServerEvent>()
        override fun publish(event: MockServerEvent) { events.add(event) }
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    private fun httpGet(url: String): Pair<Int, Map<String, List<String>>> {
        var last: Pair<Int, Map<String, List<String>>>? = null
        repeat(10) {
            try {
                val conn = URI(url).toURL().openConnection() as HttpURLConnection
                conn.connectTimeout = 1000
                conn.readTimeout = 2000
                conn.requestMethod = "GET"
                val code = conn.responseCode
                last = code to conn.headerFields.filterKeys { it != null }.mapKeys { it.key!! }
                return last
            } catch (_: Exception) {
                Thread.sleep(30)
            }
        }
        return last ?: (throw AssertionError("GET $url failed after retries"))
    }

    @Test
    fun start_stop_and_welcome() {
        val port = freePort()
        val cfg = ProxyConfig(
            name = "Basic",
            port = port,
            interceptPrefix = "/api",
            stripPrefix = true,
        )
        val out = TestOutput()
        val engine = HttpServerEngine(cfg, out)
        assertTrue(engine.start())

        // root welcome
        val (codeRoot, headersRoot) = httpGet("http://localhost:$port/")
        assertEquals(200, codeRoot)
        // CORS headers present (case-insensitive)
        val acao = headersRoot.entries.firstOrNull { it.key.equals("Access-Control-Allow-Origin", true) }?.value?.first()
        assertEquals("*", acao)

        // prefix welcome
        val (codePrefix, _) = httpGet("http://localhost:$port/api/")
        assertEquals(200, codePrefix)

        engine.stop()
        assertFalse(engine.isRunning())
    }

    @Test
    fun mock_response_cors_and_cookie() {
        val port = freePort()
        val cfg = ProxyConfig(
            name = "Mock",
            port = port,
            interceptPrefix = "/api",
            stripPrefix = true,
            globalCookie = "sid=abc123",
            mockApis = mutableListOf(
                MockApiConfig(path = "/user", mockData = "{\"ok\":true}", method = "GET", enabled = true, useCookie = true)
            )
        )
        val out = TestOutput()
        val engine = HttpServerEngine(cfg, out)
        assertTrue(engine.start())

        val url = URI("http://localhost:$port/api/user").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        val code = conn.responseCode
        val body = conn.inputStream.bufferedReader().readText()
        val headers = conn.headerFields

        assertEquals(200, code)
        val ct = headers.entries.firstOrNull { it.key.equals("Content-Type", true) }?.value?.first() ?: ""
        assertTrue(ct.lowercase().startsWith("application/json"))
        val acao2 = headers.entries.firstOrNull { it.key.equals("Access-Control-Allow-Origin", true) }?.value?.first()
        assertEquals("*", acao2)
        val sc = headers.entries.firstOrNull { it.key.equals("Set-Cookie", true) }?.value?.first() ?: ""
        assertTrue(sc.contains("sid=abc123"))
        assertEquals("{\"ok\":true}", body)

        // Events include RequestReceived, MatchedPath, MockMatched
        assertTrue(out.events.any { it is RequestReceived && it.path == "/api/user" })
        assertTrue(out.events.any { it is MockMatched && it.path == "/user" && it.statusCode == 200 })

        engine.stop()
    }

    @Test
    fun preflight_options_returns_200() {
        val port = freePort()
        val cfg = ProxyConfig(
            name = "Preflight",
            port = port,
            interceptPrefix = "/api",
            stripPrefix = true,
            mockApis = mutableListOf(
                MockApiConfig(path = "/user", mockData = "{}", method = "ALL", enabled = true)
            )
        )
        val out = TestOutput()
        val engine = HttpServerEngine(cfg, out)
        assertTrue(engine.start())

        val url = URI("http://localhost:$port/api/user").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "OPTIONS"
        val code = conn.responseCode
        assertEquals(200, code)
        engine.stop()
    }

    @Test
    fun no_mock_forwarding_disabled_returns_502() {
        // Do not set interceptwave.allowForwardInTests -> forwarding is disabled in headless/tests
        val port = freePort()
        val cfg = ProxyConfig(
            name = "NoMock",
            port = port,
            interceptPrefix = "/api",
            stripPrefix = true,
            baseUrl = "http://localhost:9", // irrelevant since forwarding is disabled in tests
            mockApis = mutableListOf()
        )
        val out = TestOutput()
        val engine = HttpServerEngine(cfg, out)
        assertTrue(engine.start())

        val url = URI("http://localhost:$port/api/unknown").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        val code = conn.responseCode
        assertEquals(502, code)

        // Should publish ForwardingTo event but not Forwarded
        assertTrue(out.events.any { it is ForwardingTo })
        assertFalse(out.events.any { it is Forwarded })

        engine.stop()
    }
}
