package org.zhongmiao.interceptwave.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.experimental.categories.Category
import org.zhongmiao.interceptwave.model.HttpRoute
import org.zhongmiao.interceptwave.model.ProxyConfig
import org.zhongmiao.interceptwave.model.RootConfig
import org.zhongmiao.interceptwave.tags.IntegrationTest
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.util.*

@Category(IntegrationTest::class)
class UpstreamEndpointsIntegrationTest : BasePlatformTestCase() {

    private lateinit var mockServerService: MockServerService
    private lateinit var configService: ConfigService

    private fun upstreamBase(): String =
        System.getProperty("iw.upstream.http") ?: (System.getenv("IW_UPSTREAM_HTTP") ?: "http://localhost:9000")

    private fun upstreamBase(offset: Int): String {
        val base = URI(upstreamBase())
        val port = if (base.port >= 0) base.port + offset else 9000 + offset
        return URI(base.scheme ?: "http", null, base.host ?: "localhost", port, null, null, null).toString()
    }

    private fun isUpstreamAlive(): Boolean = try {
        val clean = upstreamBase().trimEnd('/')
        val url = "$clean/health"
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 1500
        conn.readTimeout = 1500
        conn.requestMethod = "GET"
        conn.responseCode
        true
    } catch (_: Exception) { false }

    private fun canReachUpstreamEndpoint(
        path: String,
        method: String = "GET",
        base: String = upstreamBase(),
        expectedCode: Int? = null,
        body: String? = null,
        headers: Map<String, String> = emptyMap()
    ): Boolean = try {
        val url = URI(base.trimEnd('/') + path).toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 1500
        conn.readTimeout = 3000
        conn.requestMethod = method
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        if (body != null) {
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = conn.responseCode
        expectedCode?.let { code == it } ?: true
    } catch (_: Exception) { false }

    private fun isUpstreamEndpointAvailable(
        path: String,
        method: String = "GET",
        base: String = upstreamBase(),
        expectedCode: Int? = null,
        body: String? = null,
        headers: Map<String, String> = emptyMap()
    ): Boolean = canReachUpstreamEndpoint(path, method, base, expectedCode, body, headers)

    override fun setUp() {
        super.setUp()
        mockServerService = project.getService(MockServerService::class.java)
        configService = project.getService(ConfigService::class.java)

        // 清理配置
        runCatching {
            val root = configService.getRootConfig()
            root.proxyGroups.clear()
            configService.saveRootConfig(root)
        }
        // 允许单测中进行真实代理
        System.setProperty("interceptwave.allowForwardInTests", "true")
    }

    override fun tearDown() {
        try {
            mockServerService.stopAllServers()
            System.clearProperty("interceptwave.allowForwardInTests")
        } finally {
            super.tearDown()
        }
    }

    private fun startProxy(port: Int) {
        val cfg = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Upstream IT",
            port = port,
            stripPrefix = true,
            enabled = true,
            routes = mutableListOf(HttpRoute(pathPrefix = "/api", targetBaseUrl = upstreamBase(), stripPrefix = true, enableMock = true))
        )
        val root = configService.getRootConfig()
        root.proxyGroups.add(cfg)
        configService.saveRootConfig(root)
        val ok = mockServerService.startServer(cfg.id)
        assertTrue(ok)
    }

    private fun startProxy(port: Int, routes: MutableList<HttpRoute>) {
        val cfg = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Upstream IT",
            port = port,
            stripPrefix = true,
            enabled = true,
            routes = routes
        )
        configService.saveRootConfig(RootConfig(version = "4.0", proxyGroups = mutableListOf(cfg)))
        val ok = mockServerService.startServer(cfg.id)
        assertTrue(ok)
    }

    private fun freePort(): Int = java.net.ServerSocket(0).use { it.localPort }

    fun testRootSlashReturnsServiceInfo() {
        if (!isUpstreamAlive()) return
        val port = freePort()
        startProxy(port)
        val url = URI("http://localhost:$port/").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        assertEquals(200, conn.responseCode)
        assertNotNull(conn.inputStream.bufferedReader().readText())
    }

    fun testHealthReturns200() {
        if (!isUpstreamAlive()) return
        if (!isUpstreamEndpointAvailable("/health", expectedCode = 200)) return
        val port = freePort()
        startProxy(port)
        val url = URI("http://localhost:$port/health").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        assertEquals(200, conn.responseCode)
    }

    fun testStatus201Returns201() {
        if (!isUpstreamAlive()) return
        if (!isUpstreamEndpointAvailable("/status/201", expectedCode = 201)) return
        val port = freePort()
        startProxy(port)
        val url = URI("http://localhost:$port/status/201").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        assertEquals(201, conn.responseCode)
    }

    fun testDelay200TakesAtLeast180ms() {
        if (!isUpstreamAlive()) return
        if (!isUpstreamEndpointAvailable("/delay/10", expectedCode = 200)) return
        val port = freePort()
        startProxy(port)
        val url = URI("http://localhost:$port/delay/200").toURL()
        val t0 = System.currentTimeMillis()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        assertEquals(200, conn.responseCode)
        val dt = System.currentTimeMillis() - t0
        assertTrue("Expected >=180ms delay but was ${'$'}dt ms", dt >= 180)
    }

    fun testPostEchoReturnsMethodPathQueryBody() {
        if (!isUpstreamAlive()) return
        if (!isUpstreamEndpointAvailable("/echo", method = "POST", expectedCode = 200, body = "{\"probe\":true}", headers = mapOf("Content-Type" to "application/json"))) return
        val port = freePort()
        startProxy(port)
        val q = "k=${URLEncoder.encode("v 1", "UTF-8")}"
        val url = URI("http://localhost:$port/echo?$q").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        val payload = "{\"p\":1}"
        conn.outputStream.use { it.write(payload.toByteArray()) }
        val code = conn.responseCode
        val body = conn.inputStream.bufferedReader().readText()
        assertEquals(200, code)
        // 仅做弱断言以兼容不同实现
        assertTrue(body.contains("/echo"))
        assertTrue(body.uppercase().contains("POST"))
        assertTrue(body.contains("p"))
        assertTrue(body.contains("1"))
    }

    fun testPutEchoReturnsSuccess() {
        if (!isUpstreamAlive()) return
        if (!isUpstreamEndpointAvailable("/echo", method = "PUT", expectedCode = 200, body = "{\"probe\":true}")) return
        val port = freePort()
        startProxy(port)
        val url = URI("http://localhost:$port/echo").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "PUT"
        conn.doOutput = true
        val payload = "{\"a\":2}"
        conn.outputStream.use { it.write(payload.toByteArray()) }
        val code = conn.responseCode
        val body = conn.inputStream.bufferedReader().readText()
        assertEquals(200, code)
        assertTrue(body.uppercase().contains("PUT") || body.contains("a"))
    }

    fun testPatchEchoViaOverrideReturnsSuccess() {
        if (!isUpstreamAlive()) return
        if (!isUpstreamEndpointAvailable("/echo", method = "POST", expectedCode = 200, body = "{\"probe\":true}", headers = mapOf("X-HTTP-Method-Override" to "PATCH"))) return
        val port = freePort()
        startProxy(port)
        val url = URI("http://localhost:$port/echo").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST" // 覆盖为 PATCH
        conn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
        conn.doOutput = true
        val payload = "{\"b\":3}"
        conn.outputStream.use { it.write(payload.toByteArray()) }
        val code = conn.responseCode
        val body = conn.inputStream.bufferedReader().readText()
        assertEquals(200, code)
        assertTrue(body.contains("/echo"))
    }

    fun testHeadersEchoesSelectedHeader() {
        if (!isUpstreamAlive()) return
        if (!isUpstreamEndpointAvailable("/headers", expectedCode = 200)) return
        val port = freePort()
        startProxy(port)
        val url = URI("http://localhost:$port/headers").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("Authorization", "Bearer it-123")
        conn.requestMethod = "GET"
        val code = conn.responseCode
        val body = conn.inputStream.bufferedReader().readText()
        assertEquals(200, code)
        assertTrue(body.contains("Authorization") || body.contains("Bearer it-123"))
    }

    fun testCookiesEchoesCookie() {
        if (!isUpstreamAlive()) return
        if (!isUpstreamEndpointAvailable("/cookies", expectedCode = 200, headers = mapOf("Cookie" to "u=1; t=xyz"))) return
        val port = freePort()
        startProxy(port)
        val url = URI("http://localhost:$port/cookies").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("Cookie", "u=1; t=xyz")
        conn.requestMethod = "GET"
        val code = conn.responseCode
        val body = conn.inputStream.bufferedReader().readText()
        assertEquals(200, code)
        assertTrue(body.contains("u") || body.contains("xyz"))
    }

    fun testLargeReturnsBigPayload() {
        if (!isUpstreamAlive()) return
        if (!isUpstreamEndpointAvailable("/large?size=64", expectedCode = 200)) return
        val port = freePort()
        startProxy(port)
        val url = URI("http://localhost:$port/large?size=4096").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        assertEquals(200, conn.responseCode)
        val bytes = conn.inputStream.readAllBytes()
        assertTrue("Expected payload >= 4096 bytes, got ${'$'}{bytes.size}", bytes.size >= 4096)
    }

    fun testMultiRouteAliasesForwardToThreeHttpServices() {
        if (!isUpstreamAlive()) return
        if (!isUpstreamEndpointAvailable("/users", base = upstreamBase(0), expectedCode = 200)) return
        if (!isUpstreamEndpointAvailable("/orders/3009", base = upstreamBase(1), expectedCode = 200)) return
        if (!isUpstreamEndpointAvailable("/checkout/preview", base = upstreamBase(2), expectedCode = 200)) return
        val port = freePort()
        startProxy(
            port,
            mutableListOf(
                HttpRoute(name = "user", pathPrefix = "/api", targetBaseUrl = upstreamBase(0), stripPrefix = true, enableMock = false),
                HttpRoute(name = "order", pathPrefix = "/order-api", targetBaseUrl = upstreamBase(1), stripPrefix = true, enableMock = false),
                HttpRoute(name = "payment", pathPrefix = "/pay-api", targetBaseUrl = upstreamBase(2), stripPrefix = true, enableMock = false)
            )
        )

        val userConn = URI("http://localhost:$port/api/users").toURL().openConnection() as HttpURLConnection
        userConn.requestMethod = "GET"
        assertEquals(200, userConn.responseCode)
        val userBody = userConn.inputStream.bufferedReader().readText()
        assertTrue(userBody.contains(""""code":0"""))
        assertTrue(userBody.contains(""""data""""))
        assertTrue(userBody.contains(""""meta"""") || userBody.trim().startsWith("{"))

        val orderConn = URI("http://localhost:$port/order-api/orders/3009").toURL().openConnection() as HttpURLConnection
        orderConn.requestMethod = "GET"
        assertEquals(200, orderConn.responseCode)
        val orderBody = orderConn.inputStream.bufferedReader().readText()
        assertTrue(orderBody.contains(""""code":0"""))
        assertTrue(orderBody.contains("3009"))
        assertTrue(orderBody.contains(""""data""""))

        val payConn = URI("http://localhost:$port/pay-api/checkout/preview").toURL().openConnection() as HttpURLConnection
        payConn.requestMethod = "GET"
        assertEquals(200, payConn.responseCode)
        val payBody = payConn.inputStream.bufferedReader().readText()
        assertTrue(payBody.contains(""""code":0"""))
        assertTrue(payBody.contains(""""data""""))
        assertTrue(payBody.contains("preview") || payBody.contains("amount"))
    }

    fun testLongestPrefixRouteCanPointToDifferentUpstream() {
        if (!isUpstreamAlive()) return
        if (!isUpstreamEndpointAvailable("/admin/orders/summary", base = upstreamBase(1), expectedCode = 200)) return
        val port = freePort()
        startProxy(
            port,
            mutableListOf(
                HttpRoute(name = "fallback", pathPrefix = "/", targetBaseUrl = upstreamBase(0), stripPrefix = false, enableMock = false),
                HttpRoute(name = "admin-order", pathPrefix = "/admin", targetBaseUrl = upstreamBase(1), stripPrefix = false, enableMock = false)
            )
        )

        val conn = URI("http://localhost:$port/admin/orders/summary").toURL().openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        assertEquals(200, conn.responseCode)
        val body = conn.inputStream.bufferedReader().readText()
        assertTrue(body.contains(""""code":0"""))
        assertTrue(body.contains(""""data""""))
        assertTrue(body.contains("created") || body.contains("paid") || body.contains("avgAmount"))
    }
}
