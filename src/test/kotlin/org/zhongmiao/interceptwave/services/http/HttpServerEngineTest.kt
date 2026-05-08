package org.zhongmiao.interceptwave.services.http

import org.junit.Assert.*
import org.junit.Test
import org.zhongmiao.interceptwave.events.*
import org.zhongmiao.interceptwave.model.HeaderOverrideOperation
import org.zhongmiao.interceptwave.model.HeaderOverrideRule
import org.zhongmiao.interceptwave.model.HttpRoute
import org.zhongmiao.interceptwave.model.HttpRouteTargetType
import org.zhongmiao.interceptwave.model.MockApiConfig
import org.zhongmiao.interceptwave.model.ProxyConfig
import org.zhongmiao.interceptwave.util.LocalCertificateUtil
import com.sun.net.httpserver.HttpServer
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URI
import java.nio.file.Files
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

class HttpServerEngineTest {

    private class TestOutput : MockServerOutput {
        val events = mutableListOf<MockServerEvent>()
        override fun publish(event: MockServerEvent) { events.add(event) }
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    private fun createStaticProject(): Pair<java.io.File, java.io.File> {
        val projectRoot = Files.createTempDirectory("iw-static-project").toFile()
        val dist = java.io.File(projectRoot, "frontend/dist").apply { mkdirs() }
        java.io.File(dist, "index.html").writeText("<html>static-spa</html>")
        java.io.File(dist, "assets").mkdirs()
        java.io.File(dist, "assets/index.js").writeText("console.log('static')")
        java.io.File(dist, "assets/style.css").writeText("body{color:#123}")
        return projectRoot to dist
    }

    private data class UpstreamRequest(
        val method: String,
        val path: String,
        val query: String?,
        val body: String,
        val headers: Map<String, List<String>>
    )

    private class TestUpstream(
        val port: Int,
        val requests: MutableList<UpstreamRequest>,
        private val server: HttpServer
    ) {
        val baseUrl: String = "http://localhost:$port"
        fun stop() = server.stop(0)
    }

    private fun startGatewayUpstream(name: String): TestUpstream {
        val port = freePort()
        val requests = mutableListOf<UpstreamRequest>()
        val server = HttpServer.create(InetSocketAddress(port), 0)
        server.createContext("/") { exchange ->
            val request = UpstreamRequest(
                method = exchange.requestMethod,
                path = exchange.requestURI.path,
                query = exchange.requestURI.rawQuery,
                body = String(exchange.requestBody.readAllBytes(), Charsets.UTF_8),
                headers = exchange.requestHeaders.mapValues { it.value.toList() }
            )
            requests.add(request)

            val (status, body, contentType) = when {
                name == "frontend" && request.path == "/" -> Triple(200, "frontend-root", "text/html")
                name == "frontend" && request.path == "/assets/app.js" -> Triple(200, "console.log('ok')", "application/javascript")
                name == "frontend" && request.path == "/index.html" -> Triple(200, "<html>spa</html>", "text/html")
                name == "frontend" -> Triple(404, "missing:${request.path}", "text/plain")
                else -> Triple(200, "$name:${request.method}:${request.path}:${request.query ?: ""}:${request.body}", "application/json")
            }
            exchange.responseHeaders.set("Content-Type", contentType)
            val bytes = body.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        return TestUpstream(port, requests, server)
    }

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

    private fun createLocalPkcs12(projectRoot: java.io.File, password: String = LocalCertificateUtil.DEFAULT_PASSWORD): java.io.File {
        org.junit.Assume.assumeTrue("keytool is required for HTTPS listener tests", LocalCertificateUtil.findKeytool() != null)
        val file = java.io.File(projectRoot, LocalCertificateUtil.DEFAULT_RELATIVE_PATH)
        LocalCertificateUtil.generateLocalPkcs12(file.toPath(), password, overwrite = true)
        return file
    }

    private fun openInsecureHttps(url: String): HttpsURLConnection {
        val trustAll = arrayOf<X509TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
        )
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, trustAll, SecureRandom())
        }
        return URI(url).toURL().openConnection().let { it as HttpsURLConnection }.apply {
            sslSocketFactory = sslContext.socketFactory
            hostnameVerifier = HostnameVerifier { _, _ -> true }
        }
    }

    private fun connectionBody(conn: HttpURLConnection): String =
        runCatching { conn.inputStream }.getOrElse { conn.errorStream }.bufferedReader().readText()

    private fun headerValue(headers: Map<String, List<String>>, name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, true) }?.value?.firstOrNull()

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
        assertTrue(
            out.events.any {
                it is MockMatched &&
                    (it.path == "/user" || it.path == "/api/user") &&
                    it.statusCode == 200
            }
        )

        engine.stop()
    }

    @Test
    fun https_listener_serves_mock_proxy_and_static_routes() {
        System.setProperty("interceptwave.allowForwardInTests", "true")
        val (projectRoot, _) = createStaticProject()
        val api = startGatewayUpstream("api")
        createLocalPkcs12(projectRoot)
        val port = freePort()
        val cfg = ProxyConfig(
            name = "HttpsGateway",
            port = port,
            httpsEnabled = true,
            httpsKeystorePath = LocalCertificateUtil.DEFAULT_RELATIVE_PATH,
            httpsKeystorePassword = LocalCertificateUtil.DEFAULT_PASSWORD,
            routes = mutableListOf(
                HttpRoute(
                    name = "Mock",
                    pathPrefix = "/mock",
                    targetBaseUrl = "http://localhost:8080",
                    stripPrefix = true,
                    enableMock = true,
                    mockApis = mutableListOf(
                        MockApiConfig(path = "/user", mockData = "{\"https\":true}", method = "GET", enabled = true)
                    )
                ),
                HttpRoute(
                    name = "API",
                    pathPrefix = "/api",
                    targetBaseUrl = api.baseUrl,
                    stripPrefix = true,
                    enableMock = false
                ),
                HttpRoute(
                    name = "Static",
                    pathPrefix = "/",
                    targetType = HttpRouteTargetType.STATIC,
                    staticRoot = "frontend/dist",
                    stripPrefix = false,
                    enableMock = false
                )
            )
        )
        val engine = HttpServerEngine(cfg, TestOutput(), projectRoot.absolutePath)
        assertTrue(engine.start())
        assertEquals("https://localhost:$port", engine.getUrl())

        try {
            val mockConn = openInsecureHttps("https://localhost:$port/mock/user")
            mockConn.requestMethod = "GET"
            assertEquals(200, mockConn.responseCode)
            assertEquals("{\"https\":true}", connectionBody(mockConn))

            val proxyConn = openInsecureHttps("https://localhost:$port/api/users?active=true")
            proxyConn.requestMethod = "GET"
            assertEquals(200, proxyConn.responseCode)
            assertTrue(connectionBody(proxyConn).contains("api:GET:/users:active=true"))

            val staticConn = openInsecureHttps("https://localhost:$port/assets/index.js")
            staticConn.requestMethod = "GET"
            assertEquals(200, staticConn.responseCode)
            assertEquals("console.log('static')", connectionBody(staticConn))
        } finally {
            engine.stop()
            api.stop()
            projectRoot.deleteRecursively()
            System.clearProperty("interceptwave.allowForwardInTests")
        }
    }

    @Test
    fun https_listener_fails_with_invalid_keystore_configuration() {
        val projectRoot = Files.createTempDirectory("iw-https-invalid").toFile()
        val port = freePort()
        val cfg = ProxyConfig(
            name = "BadHttps",
            port = port,
            httpsEnabled = true,
            httpsKeystorePath = "certs/missing.p12",
            httpsKeystorePassword = "changeit",
            routes = mutableListOf(HttpRoute())
        )
        val engine = HttpServerEngine(cfg, TestOutput(), projectRoot.absolutePath)

        try {
            assertFalse(engine.start())
            assertTrue(engine.lastError.orEmpty().contains("keystore", ignoreCase = true))
            assertEquals("https://localhost:$port", engine.getUrl())
        } finally {
            engine.stop()
            projectRoot.deleteRecursively()
        }
    }

    @Test
    fun response_header_overrides_apply_to_mock_response() {
        val port = freePort()
        val cfg = ProxyConfig(
            name = "MockHeaders",
            port = port,
            routes = mutableListOf(
                HttpRoute(
                    pathPrefix = "/api",
                    targetBaseUrl = "http://localhost:8080",
                    stripPrefix = true,
                    enableMock = true,
                    responseHeaders = mutableListOf(
                        HeaderOverrideRule("Access-Control-Allow-Origin", "http://local.dev", HeaderOverrideOperation.SET, true),
                        HeaderOverrideRule("X-Mock-Header", "mock", HeaderOverrideOperation.SET, true),
                        HeaderOverrideRule("Content-Type", "", HeaderOverrideOperation.REMOVE, true),
                        HeaderOverrideRule("X-Disabled", "ignored", HeaderOverrideOperation.SET, false),
                        HeaderOverrideRule("Content-Length", "1", HeaderOverrideOperation.SET, true)
                    ),
                    mockApis = mutableListOf(
                        MockApiConfig(path = "/user", mockData = "{\"ok\":true}", method = "GET", enabled = true)
                    )
                )
            )
        )
        val engine = HttpServerEngine(cfg, TestOutput())
        assertTrue(engine.start())

        try {
            val conn = URI("http://localhost:$port/api/user").toURL().openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            assertEquals(200, conn.responseCode)
            assertEquals("http://local.dev", conn.getHeaderField("Access-Control-Allow-Origin"))
            assertEquals("mock", conn.getHeaderField("X-Mock-Header"))
            assertNull(conn.getHeaderField("X-Disabled"))
            assertNull(conn.contentType)
            assertNotEquals("1", conn.getHeaderField("Content-Length"))
            assertEquals("{\"ok\":true}", connectionBody(conn))
        } finally {
            engine.stop()
        }
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
    fun frontend_root_route_forwards_root_assets_deep_links_and_respects_api_boundary() {
        System.setProperty("interceptwave.allowForwardInTests", "true")
        val api = startGatewayUpstream("api")
        val frontend = startGatewayUpstream("frontend")
        val port = freePort()
        val cfg = ProxyConfig(
            name = "FrontendProxy",
            port = port,
            routes = mutableListOf(
                HttpRoute(name = "API", pathPrefix = "/api", targetBaseUrl = api.baseUrl, stripPrefix = true, enableMock = false),
                HttpRoute(name = "Frontend", pathPrefix = "/", targetBaseUrl = frontend.baseUrl, stripPrefix = false, enableMock = false)
            )
        )
        val engine = HttpServerEngine(cfg, TestOutput())
        assertTrue(engine.start())

        try {
            val apiConn = URI("http://localhost:$port/api/users?active=true").toURL().openConnection() as HttpURLConnection
            apiConn.requestMethod = "POST"
            apiConn.doOutput = true
            apiConn.setRequestProperty("X-Trace-Id", "abc-123")
            apiConn.outputStream.use { it.write("payload".toByteArray(Charsets.UTF_8)) }
            assertEquals(200, apiConn.responseCode)
            assertTrue(apiConn.inputStream.bufferedReader().readText().contains("api:POST:/users:active=true:payload"))
            val traceHeader = api.requests.last().headers.entries.firstOrNull { it.key.equals("X-Trace-Id", true) }?.value?.single()
            assertEquals("abc-123", traceHeader)

            val rootConn = URI("http://localhost:$port/").toURL().openConnection() as HttpURLConnection
            rootConn.requestMethod = "GET"
            assertEquals(200, rootConn.responseCode)
            assertEquals("frontend-root", rootConn.inputStream.bufferedReader().readText())

            val assetConn = URI("http://localhost:$port/assets/app.js").toURL().openConnection() as HttpURLConnection
            assetConn.requestMethod = "GET"
            assertEquals(200, assetConn.responseCode)
            assertEquals("console.log('ok')", assetConn.inputStream.bufferedReader().readText())

            val apiaryConn = URI("http://localhost:$port/apiary").toURL().openConnection() as HttpURLConnection
            apiaryConn.requestMethod = "GET"
            assertEquals(404, apiaryConn.responseCode)
            assertTrue(frontend.requests.any { it.path == "/apiary" })
            assertFalse(api.requests.any { it.path == "ary" || it.path == "/ary" })
        } finally {
            engine.stop()
            api.stop()
            frontend.stop()
            System.clearProperty("interceptwave.allowForwardInTests")
        }
    }

    @Test
    fun header_overrides_apply_to_forwarded_request_and_proxy_response() {
        System.setProperty("interceptwave.allowForwardInTests", "true")
        val api = startGatewayUpstream("api")
        val port = freePort()
        val cfg = ProxyConfig(
            name = "ForwardHeaders",
            port = port,
            routes = mutableListOf(
                HttpRoute(
                    name = "API",
                    pathPrefix = "/api",
                    targetBaseUrl = api.baseUrl,
                    stripPrefix = true,
                    enableMock = false,
                    requestHeaders = mutableListOf(
                        HeaderOverrideRule("X-Trace", "override", HeaderOverrideOperation.SET, true),
                        HeaderOverrideRule("X-Trace", "second", HeaderOverrideOperation.ADD, true),
                        HeaderOverrideRule("X-Remove", "", HeaderOverrideOperation.REMOVE, true),
                        HeaderOverrideRule("X-Disabled", "ignored", HeaderOverrideOperation.SET, false),
                        HeaderOverrideRule("Host", "evil.example", HeaderOverrideOperation.SET, true)
                    ),
                    responseHeaders = mutableListOf(
                        HeaderOverrideRule("Access-Control-Allow-Origin", "http://local.dev", HeaderOverrideOperation.SET, true),
                        HeaderOverrideRule("X-Proxy-Header", "proxy", HeaderOverrideOperation.SET, true),
                        HeaderOverrideRule("X-Proxy-Header", "second", HeaderOverrideOperation.ADD, true)
                    )
                )
            )
        )
        val engine = HttpServerEngine(cfg, TestOutput())
        assertTrue(engine.start())

        try {
            val conn = URI("http://localhost:$port/api/users?active=true").toURL().openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("X-Trace", "original")
            conn.setRequestProperty("X-Remove", "delete-me")
            conn.outputStream.use { it.write("payload".toByteArray(Charsets.UTF_8)) }

            assertEquals(200, conn.responseCode)
            assertTrue(connectionBody(conn).contains("api:POST:/users:active=true:payload"))
            val upstreamHeaders = api.requests.last().headers
            assertEquals(listOf("override", "second"), upstreamHeaders.entries.first { it.key.equals("X-Trace", true) }.value)
            assertNull(headerValue(upstreamHeaders, "X-Remove"))
            assertNull(headerValue(upstreamHeaders, "X-Disabled"))
            assertNotEquals("evil.example", headerValue(upstreamHeaders, "Host"))
            assertEquals("http://local.dev", conn.getHeaderField("Access-Control-Allow-Origin"))
            val proxyHeaderValues = conn.headerFields.entries
                .first { it.key.equals("X-Proxy-Header", true) }
                .value
                .joinToString(",")
            assertTrue(proxyHeaderValues.contains("proxy"))
            assertTrue(proxyHeaderValues.contains("second"))
        } finally {
            engine.stop()
            api.stop()
            System.clearProperty("interceptwave.allowForwardInTests")
        }
    }

    @Test
    fun spa_fallback_retries_html_navigation_404_only() {
        System.setProperty("interceptwave.allowForwardInTests", "true")
        val frontend = startGatewayUpstream("frontend")
        val port = freePort()
        val cfg = ProxyConfig(
            name = "SpaFallback",
            port = port,
            routes = mutableListOf(
                HttpRoute(
                    name = "Frontend",
                    pathPrefix = "/",
                    targetBaseUrl = frontend.baseUrl,
                    stripPrefix = false,
                    spaFallbackPath = "/index.html",
                    enableMock = false
                )
            )
        )
        val out = TestOutput()
        val engine = HttpServerEngine(cfg, out)
        assertTrue(engine.start())

        try {
            val deepLinkConn = URI("http://localhost:$port/dashboard/settings").toURL().openConnection() as HttpURLConnection
            deepLinkConn.requestMethod = "GET"
            deepLinkConn.setRequestProperty("Accept", "text/html,application/xhtml+xml")
            assertEquals(200, deepLinkConn.responseCode)
            assertEquals("<html>spa</html>", deepLinkConn.inputStream.bufferedReader().readText())
            assertEquals(listOf("/dashboard/settings", "/index.html"), frontend.requests.takeLast(2).map { it.path })
            assertTrue(out.events.any { it is Forwarded && it.targetUrl == "${frontend.baseUrl}/index.html" && it.statusCode == 200 })

            val assetConn = URI("http://localhost:$port/missing/app.js").toURL().openConnection() as HttpURLConnection
            assetConn.requestMethod = "GET"
            assetConn.setRequestProperty("Accept", "text/html")
            assertEquals(404, assetConn.responseCode)
            assertEquals("/missing/app.js", frontend.requests.last().path)

            val postConn = URI("http://localhost:$port/dashboard/settings").toURL().openConnection() as HttpURLConnection
            postConn.requestMethod = "POST"
            postConn.doOutput = true
            postConn.setRequestProperty("Accept", "text/html")
            postConn.outputStream.use { it.write("body".toByteArray(Charsets.UTF_8)) }
            assertEquals(404, postConn.responseCode)
            assertEquals("POST", frontend.requests.last().method)
            assertEquals("/dashboard/settings", frontend.requests.last().path)
        } finally {
            engine.stop()
            frontend.stop()
            System.clearProperty("interceptwave.allowForwardInTests")
        }
    }

    @Test
    fun static_route_serves_frontend_assets_with_mime_types() {
        val (projectRoot, _) = createStaticProject()
        val port = freePort()
        val cfg = ProxyConfig(
            name = "StaticAssets",
            port = port,
            routes = mutableListOf(
                HttpRoute(
                    name = "Frontend Build",
                    pathPrefix = "/",
                    targetType = HttpRouteTargetType.STATIC,
                    staticRoot = "frontend/dist",
                    stripPrefix = false,
                    enableMock = false,
                    responseHeaders = mutableListOf(
                        HeaderOverrideRule("X-Static-Header", "static", HeaderOverrideOperation.SET, true),
                        HeaderOverrideRule("Access-Control-Allow-Origin", "http://static.local", HeaderOverrideOperation.SET, true)
                    )
                )
            )
        )
        val engine = HttpServerEngine(cfg, TestOutput(), projectRoot.absolutePath)
        assertTrue(engine.start())

        try {
            val jsConn = URI("http://localhost:$port/assets/index.js").toURL().openConnection() as HttpURLConnection
            assertEquals(200, jsConn.responseCode)
            assertTrue(jsConn.contentType.lowercase().startsWith("application/javascript"))
            assertEquals("static", jsConn.getHeaderField("X-Static-Header"))
            assertEquals("http://static.local", jsConn.getHeaderField("Access-Control-Allow-Origin"))
            assertEquals("console.log('static')", connectionBody(jsConn))

            val cssConn = URI("http://localhost:$port/assets/style.css").toURL().openConnection() as HttpURLConnection
            assertEquals(200, cssConn.responseCode)
            assertTrue(cssConn.contentType.lowercase().startsWith("text/css"))
            assertEquals("body{color:#123}", connectionBody(cssConn))

            val rootConn = URI("http://localhost:$port/").toURL().openConnection() as HttpURLConnection
            assertEquals(200, rootConn.responseCode)
            assertTrue(rootConn.contentType.lowercase().startsWith("text/html"))
            assertEquals("<html>static-spa</html>", connectionBody(rootConn))
        } finally {
            engine.stop()
            projectRoot.deleteRecursively()
        }
    }

    @Test
    fun static_route_spa_fallback_and_missing_file_behaviour() {
        val (projectRoot, _) = createStaticProject()
        val port = freePort()
        val cfg = ProxyConfig(
            name = "StaticSpa",
            port = port,
            routes = mutableListOf(
                HttpRoute(
                    pathPrefix = "/",
                    targetType = HttpRouteTargetType.STATIC,
                    staticRoot = "frontend/dist",
                    stripPrefix = false,
                    spaFallback = true,
                    enableMock = false
                )
            )
        )
        val engine = HttpServerEngine(cfg, TestOutput(), projectRoot.absolutePath)
        assertTrue(engine.start())

        try {
            val deepLink = URI("http://localhost:$port/dashboard").toURL().openConnection() as HttpURLConnection
            deepLink.requestMethod = "GET"
            deepLink.setRequestProperty("Accept", "text/html")
            assertEquals(200, deepLink.responseCode)
            assertEquals("<html>static-spa</html>", connectionBody(deepLink))

            val missingAsset = URI("http://localhost:$port/assets/missing.js").toURL().openConnection() as HttpURLConnection
            missingAsset.requestMethod = "GET"
            missingAsset.setRequestProperty("Accept", "text/html")
            assertEquals(404, missingAsset.responseCode)
        } finally {
            engine.stop()
            projectRoot.deleteRecursively()
        }

        val (projectRootNoFallback, _) = createStaticProject()
        val noFallbackPort = freePort()
        val noFallbackCfg = ProxyConfig(
            name = "StaticNoSpa",
            port = noFallbackPort,
            routes = mutableListOf(
                HttpRoute(
                    pathPrefix = "/",
                    targetType = HttpRouteTargetType.STATIC,
                    staticRoot = "frontend/dist",
                    stripPrefix = false,
                    spaFallback = false,
                    enableMock = false
                )
            )
        )
        val noFallbackEngine = HttpServerEngine(noFallbackCfg, TestOutput(), projectRootNoFallback.absolutePath)
        assertTrue(noFallbackEngine.start())

        try {
            val deepLink = URI("http://localhost:$noFallbackPort/dashboard").toURL().openConnection() as HttpURLConnection
            deepLink.requestMethod = "GET"
            deepLink.setRequestProperty("Accept", "text/html")
            assertEquals(404, deepLink.responseCode)
        } finally {
            noFallbackEngine.stop()
            projectRootNoFallback.deleteRecursively()
        }
    }

    @Test
    fun static_route_blocks_traversal_and_supports_absolute_root() {
        val (projectRoot, dist) = createStaticProject()
        java.io.File(projectRoot, "secret.txt").writeText("secret")
        val port = freePort()
        val cfg = ProxyConfig(
            name = "StaticTraversal",
            port = port,
            routes = mutableListOf(
                HttpRoute(
                    pathPrefix = "/",
                    targetType = HttpRouteTargetType.STATIC,
                    staticRoot = dist.absolutePath,
                    stripPrefix = false,
                    enableMock = false
                )
            )
        )
        val engine = HttpServerEngine(cfg, TestOutput(), projectRoot.absolutePath)
        assertTrue(engine.start())

        try {
            val direct = URI("http://localhost:$port/%2e%2e/secret.txt").toURL().openConnection() as HttpURLConnection
            direct.requestMethod = "GET"
            assertEquals(403, direct.responseCode)
            assertFalse(connectionBody(direct).contains("secret"))
        } finally {
            engine.stop()
            projectRoot.deleteRecursively()
        }
    }

    @Test
    fun static_route_still_prefers_enabled_mock_before_reading_files() {
        val (projectRoot, _) = createStaticProject()
        val port = freePort()
        val cfg = ProxyConfig(
            name = "StaticMock",
            port = port,
            routes = mutableListOf(
                HttpRoute(
                    pathPrefix = "/",
                    targetType = HttpRouteTargetType.STATIC,
                    staticRoot = "frontend/dist",
                    stripPrefix = false,
                    enableMock = true,
                    mockApis = mutableListOf(
                        MockApiConfig(path = "/assets/index.js", method = "GET", mockData = "{\"mock\":true}", enabled = true)
                    )
                )
            )
        )
        val engine = HttpServerEngine(cfg, TestOutput(), projectRoot.absolutePath)
        assertTrue(engine.start())

        try {
            val conn = URI("http://localhost:$port/assets/index.js").toURL().openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            assertEquals(200, conn.responseCode)
            assertTrue(conn.contentType.lowercase().startsWith("application/json"))
            assertEquals("{\"mock\":true}", connectionBody(conn))
        } finally {
            engine.stop()
            projectRoot.deleteRecursively()
        }
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
    fun route_rewrite_applies_to_mock_matching() {
        val port = freePort()
        val cfg = ProxyConfig(
            name = "RewriteMock",
            port = port,
            routes = mutableListOf(
                HttpRoute(
                    pathPrefix = "/backend",
                    targetBaseUrl = "http://localhost:4002",
                    stripPrefix = true,
                    rewriteTargetPath = "/v1",
                    enableMock = true,
                    mockApis = mutableListOf(
                        MockApiConfig(path = "/v1/users", mockData = "{\"rewrite\":true}", method = "GET", enabled = true)
                    )
                )
            )
        )
        val out = TestOutput()
        val engine = HttpServerEngine(cfg, out)
        assertTrue(engine.start())

        val conn = URI("http://localhost:$port/backend/users").toURL().openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        assertEquals(200, conn.responseCode)
        assertEquals("{\"rewrite\":true}", conn.inputStream.bufferedReader().readText())
        assertTrue(out.events.any { it is MatchedPath && it.path == "/v1/users" })
        assertTrue(out.events.any { it is MockMatched && it.path == "/v1/users" })

        engine.stop()
    }

    @Test
    fun route_rewrite_applies_to_forwarding_and_preserves_query_string() {
        System.clearProperty("interceptwave.allowForwardInTests")
        val port = freePort()
        val cfg = ProxyConfig(
            name = "RewriteForward",
            port = port,
            routes = mutableListOf(
                HttpRoute(
                    pathPrefix = "/backend",
                    targetBaseUrl = "http://localhost:4002",
                    stripPrefix = true,
                    rewriteTargetPath = "/v1",
                    enableMock = false
                )
            )
        )
        val out = TestOutput()
        val engine = HttpServerEngine(cfg, out)
        assertTrue(engine.start())

        val conn = URI("http://localhost:$port/backend/users?active=true&page=2").toURL().openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        assertEquals(502, conn.responseCode)
        assertTrue(
            out.events.any {
                it is ForwardingTo && it.targetUrl == "http://localhost:4002/v1/users?active=true&page=2"
            }
        )

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
