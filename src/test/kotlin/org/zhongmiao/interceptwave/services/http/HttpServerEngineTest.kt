package org.zhongmiao.interceptwave.services.http

import org.junit.Assert.*
import org.junit.Test
import org.zhongmiao.interceptwave.events.*
import org.zhongmiao.interceptwave.model.HttpRoute
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

    private fun invokeShouldServeWelcomePage(engine: HttpServerEngine, route: HttpRoute, requestPath: String): Boolean {
        val method = HttpServerEngine::class.java.declaredMethods.first {
            it.name == "shouldServeWelcomePage" && it.parameterTypes.size == 2
        }
        method.isAccessible = true
        return method.invoke(engine, route, requestPath) as Boolean
    }

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
            routes = mutableListOf(
                HttpRoute(pathPrefix = "/api", targetBaseUrl = "http://localhost:8080", stripPrefix = true)
            ),
            stripPrefix = true
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
            routes = mutableListOf(
                HttpRoute(
                    pathPrefix = "/api",
                    targetBaseUrl = "http://localhost:8080",
                    stripPrefix = true,
                    enableMock = true,
                    mockApis = mutableListOf(
                        MockApiConfig(path = "/user", mockData = "{\"ok\":true}", method = "GET", enabled = true, useCookie = true)
                    )
                )
            ),
            stripPrefix = true,
            globalCookie = "sid=abc123",
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
            routes = mutableListOf(
                HttpRoute(
                    pathPrefix = "/api",
                    targetBaseUrl = "http://localhost:8080",
                    stripPrefix = true,
                    enableMock = true,
                    mockApis = mutableListOf(
                        MockApiConfig(path = "/user", mockData = "{}", method = "ALL", enabled = true)
                    )
                )
            ),
            stripPrefix = true
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
    fun exact_route_prefix_serves_welcome_page_when_strip_prefix_is_enabled() {
        val port = freePort()
        val cfg = ProxyConfig(
            name = "PrefixWelcome",
            port = port,
            routes = mutableListOf(
                HttpRoute(pathPrefix = "/api", targetBaseUrl = "http://localhost:8080", stripPrefix = true)
            )
        )
        val engine = HttpServerEngine(cfg, TestOutput())
        assertTrue(engine.start())

        val conn = URI("http://localhost:$port/api").toURL().openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        assertEquals(200, conn.responseCode)
        val body = conn.inputStream.bufferedReader().readText()
        assertTrue(body.contains("\"routes\""))
        assertTrue(body.contains("\"pathPrefix\": \"/api\""))

        engine.stop()
    }

    @Test
    fun no_mock_forwarding_disabled_returns_502() {
        // Do not set interceptwave.allowForwardInTests -> forwarding is disabled in headless/tests
        val port = freePort()
        val cfg = ProxyConfig(
            name = "NoMock",
            port = port,
            routes = mutableListOf(
                HttpRoute(
                    pathPrefix = "/api",
                    targetBaseUrl = "http://localhost:9",
                    stripPrefix = true,
                    enableMock = true,
                    mockApis = mutableListOf()
                )
            ),
            stripPrefix = true
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

    @Test
    fun forwarding_disabled_response_contains_headless_hint() {
        System.clearProperty("interceptwave.allowForwardInTests")
        val port = freePort()
        val cfg = ProxyConfig(
            name = "NoForwardInTests",
            port = port,
            routes = mutableListOf(
                HttpRoute(
                    pathPrefix = "/api",
                    targetBaseUrl = "http://localhost:9000",
                    stripPrefix = true,
                    enableMock = false
                )
            )
        )
        val out = TestOutput()
        val engine = HttpServerEngine(cfg, out)
        assertTrue(engine.start())

        val conn = URI("http://localhost:$port/api/health").toURL().openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        assertEquals(502, conn.responseCode)
        val body = conn.errorStream.bufferedReader().readText()
        assertTrue(body.contains("Forwarding disabled in tests/headless/CI"))

        engine.stop()
    }

    @Test
    fun longest_prefix_route_and_disable_mock_behaviour() {
        val port = freePort()
        val cfg = ProxyConfig(
            name = "Routes",
            port = port,
            routes = mutableListOf(
                HttpRoute(
                    name = "Fallback",
                    pathPrefix = "/",
                    targetBaseUrl = "http://localhost:4001",
                    stripPrefix = false,
                    enableMock = false
                ),
                HttpRoute(
                    name = "API",
                    pathPrefix = "/api",
                    targetBaseUrl = "http://localhost:4002",
                    stripPrefix = true,
                    enableMock = true,
                    mockApis = mutableListOf(
                        MockApiConfig(path = "/user", mockData = "{\"route\":\"api\"}", method = "GET", enabled = true),
                        MockApiConfig(path = "/admin", mockData = "{\"route\":\"admin\"}", method = "GET", enabled = false)
                    )
                ),
                HttpRoute(
                    name = "Admin",
                    pathPrefix = "/api/admin",
                    targetBaseUrl = "http://localhost:4003",
                    stripPrefix = true,
                    enableMock = false
                )
            )
        )
        val out = TestOutput()
        val engine = HttpServerEngine(cfg, out)
        assertTrue(engine.start())

        val userConn = URI("http://localhost:$port/api/user").toURL().openConnection() as HttpURLConnection
        userConn.requestMethod = "GET"
        assertEquals(200, userConn.responseCode)
        assertEquals("{\"route\":\"api\"}", userConn.inputStream.bufferedReader().readText())

        val adminConn = URI("http://localhost:$port/api/admin/users").toURL().openConnection() as HttpURLConnection
        adminConn.requestMethod = "GET"
        assertEquals(502, adminConn.responseCode)

        val fallbackConn = URI("http://localhost:$port/home").toURL().openConnection() as HttpURLConnection
        fallbackConn.requestMethod = "GET"
        assertEquals(502, fallbackConn.responseCode)

        assertTrue(out.events.any { it is ForwardingTo && it.targetUrl == "http://localhost:4003/users" })
        assertTrue(out.events.any { it is ForwardingTo && it.targetUrl == "http://localhost:4001/home" })
        engine.stop()
    }

    @Test
    fun no_matching_route_with_multiple_routes_returns_502() {
        val port = freePort()
        val cfg = ProxyConfig(
            name = "NoRoute",
            port = port,
            routes = mutableListOf(
                HttpRoute(pathPrefix = "/api", targetBaseUrl = "http://localhost:4002", stripPrefix = true),
                HttpRoute(pathPrefix = "/admin", targetBaseUrl = "http://localhost:4003", stripPrefix = false)
            )
        )
        val out = TestOutput()
        val engine = HttpServerEngine(cfg, out)
        assertTrue(engine.start())

        val conn = URI("http://localhost:$port/health").toURL().openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        assertEquals(502, conn.responseCode)
        assertFalse(out.events.any { it is ForwardingTo })

        engine.stop()
    }

    @Test
    fun disabled_mock_entry_falls_back_to_forwarding() {
        System.clearProperty("interceptwave.allowForwardInTests")
        val port = freePort()
        val cfg = ProxyConfig(
            name = "DisabledMock",
            port = port,
            routes = mutableListOf(
                HttpRoute(
                    pathPrefix = "/api",
                    targetBaseUrl = "http://localhost:4002",
                    stripPrefix = true,
                    enableMock = true,
                    mockApis = mutableListOf(
                        MockApiConfig(path = "/user", mockData = "{\"ok\":true}", method = "GET", enabled = false)
                    )
                )
            )
        )
        val out = TestOutput()
        val engine = HttpServerEngine(cfg, out)
        assertTrue(engine.start())

        val conn = URI("http://localhost:$port/api/user").toURL().openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        assertEquals(502, conn.responseCode)
        assertFalse(out.events.any { it is MockMatched })
        assertTrue(out.events.any { it is ForwardingTo && it.targetUrl == "http://localhost:4002/user" })

        engine.stop()
    }

    @Test
    fun forwarding_allowed_but_unreachable_publishes_error_and_returns_502() {
        System.setProperty("interceptwave.allowForwardInTests", "true")
        val port = freePort()
        val cfg = ProxyConfig(
            name = "ForwardError",
            port = port,
            routes = mutableListOf(
                HttpRoute(
                    pathPrefix = "/api",
                    targetBaseUrl = "http://127.0.0.1:9",
                    stripPrefix = true,
                    enableMock = false
                )
            )
        )
        val out = TestOutput()
        val engine = HttpServerEngine(cfg, out)
        assertTrue(engine.start())

        val conn = URI("http://localhost:$port/api/users?id=1").toURL().openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        assertEquals(502, conn.responseCode)
        val body = conn.errorStream.bufferedReader().readText()
        assertTrue(body.contains("Bad Gateway"))
        assertTrue(out.events.any { it is ForwardingTo && it.targetUrl == "http://127.0.0.1:9/users?id=1" })
        assertTrue(out.events.any { it is ErrorOccurred })
        assertFalse(out.events.any { it is Forwarded })

        engine.stop()
        System.clearProperty("interceptwave.allowForwardInTests")
    }

    @Test
    fun start_returns_false_when_port_is_already_occupied() {
        val port = freePort()
        ServerSocket(port).use {
            val cfg = ProxyConfig(
                name = "BusyPort",
                port = port,
                routes = mutableListOf(
                    HttpRoute(pathPrefix = "/api", targetBaseUrl = "http://localhost:4002", stripPrefix = true)
                )
            )
            val engine = HttpServerEngine(cfg, TestOutput())
            assertFalse(engine.start())
            assertNotNull(engine.lastError)
        }
    }

    @Test
    fun shouldServeWelcomePage_returns_false_for_root_prefix_and_strip_disabled() {
        val engine = HttpServerEngine(
            ProxyConfig(
                name = "WelcomeFlags",
                port = freePort(),
                routes = mutableListOf(HttpRoute(pathPrefix = "/api", targetBaseUrl = "http://localhost:8080"))
            ),
            TestOutput()
        )
        val rootRoute = HttpRoute(pathPrefix = "/", targetBaseUrl = "http://localhost:4001", stripPrefix = true)
        val noStripRoute = HttpRoute(pathPrefix = "/api", targetBaseUrl = "http://localhost:4002", stripPrefix = false)

        assertEquals(false, invokeShouldServeWelcomePage(engine, rootRoute, "/"))
        assertEquals(false, invokeShouldServeWelcomePage(engine, noStripRoute, "/api"))
        assertEquals(true, invokeShouldServeWelcomePage(engine, HttpRoute(pathPrefix = "/api", targetBaseUrl = "http://localhost:4002", stripPrefix = true), "/api/"))
    }

    @Test
    fun getUrl_returns_localhost_with_configured_port() {
        val port = freePort()
        val engine = HttpServerEngine(
            ProxyConfig(
                name = "Url",
                port = port,
                routes = mutableListOf(HttpRoute(pathPrefix = "/api", targetBaseUrl = "http://localhost:8080"))
            ),
            TestOutput()
        )
        assertEquals("http://localhost:$port", engine.getUrl())
    }
}
