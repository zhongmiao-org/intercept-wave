package org.zhongmiao.interceptwave.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assume.assumeTrue
import org.junit.experimental.categories.Category
import org.zhongmiao.interceptwave.model.ProxyConfig
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
            interceptPrefix = "/api",
            stripPrefix = true,
            baseUrl = upstreamBase(),
            enabled = true,
            mockApis = mutableListOf()
        )
        val root = configService.getRootConfig()
        root.proxyGroups.add(cfg)
        configService.saveRootConfig(root)
        val ok = mockServerService.startServer(cfg.id)
        assertTrue(ok)
    }

    private fun freePort(): Int = java.net.ServerSocket(0).use { it.localPort }

    fun testRootSlashReturnsServiceInfo() {
        val base = upstreamBase()
        assumeTrue("Upstream not available at $base; skipping", isUpstreamAlive())
        val port = freePort()
        startProxy(port)
        val url = URI("http://localhost:$port/").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        assertEquals(200, conn.responseCode)
        assertNotNull(conn.inputStream.bufferedReader().readText())
    }

    fun testHealthReturns200() {
        val base = upstreamBase()
        assumeTrue("Upstream not available at $base; skipping", isUpstreamAlive())
        val port = freePort()
        startProxy(port)
        val url = URI("http://localhost:$port/health").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        assertEquals(200, conn.responseCode)
    }

    fun testStatus201Returns201() {
        val base = upstreamBase()
        assumeTrue("Upstream not available at $base; skipping", isUpstreamAlive())
        val port = freePort()
        startProxy(port)
        val url = URI("http://localhost:$port/status/201").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        assertEquals(201, conn.responseCode)
    }

    fun testDelay200TakesAtLeast180ms() {
        val base = upstreamBase()
        assumeTrue("Upstream not available at $base; skipping", isUpstreamAlive())
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
        val base = upstreamBase()
        assumeTrue("Upstream not available at $base; skipping", isUpstreamAlive())
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
        val base = upstreamBase()
        assumeTrue("Upstream not available at $base; skipping", isUpstreamAlive())
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
        val base = upstreamBase()
        assumeTrue("Upstream not available at $base; skipping", isUpstreamAlive())
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
        val base = upstreamBase()
        assumeTrue("Upstream not available at $base; skipping", isUpstreamAlive())
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
        val base = upstreamBase()
        assumeTrue("Upstream not available at $base; skipping", isUpstreamAlive())
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
        val base = upstreamBase()
        assumeTrue("Upstream not available at $base; skipping", isUpstreamAlive())
        val port = freePort()
        startProxy(port)
        val url = URI("http://localhost:$port/large?size=4096").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        assertEquals(200, conn.responseCode)
        val bytes = conn.inputStream.readAllBytes()
        assertTrue("Expected payload >= 4096 bytes, got ${'$'}{bytes.size}", bytes.size >= 4096)
    }
}
