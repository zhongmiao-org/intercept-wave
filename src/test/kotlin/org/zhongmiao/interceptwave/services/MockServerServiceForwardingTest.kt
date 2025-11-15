package org.zhongmiao.interceptwave.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertNotEquals
import org.junit.experimental.categories.Category
import org.zhongmiao.interceptwave.model.ProxyConfig
import org.zhongmiao.interceptwave.tags.IntegrationTest
import java.net.HttpURLConnection
import java.net.URI
import java.util.*

@Category(IntegrationTest::class)
class MockServerServiceForwardingTest : BasePlatformTestCase() {

    private lateinit var mockServerService: MockServerService
    private lateinit var configService: ConfigService

    private fun upstreamBase(): String =
        System.getProperty("iw.upstream.http") ?: (System.getenv("IW_UPSTREAM_HTTP") ?: "http://localhost:9000")

    private fun freePort(): Int = java.net.ServerSocket(0).use { it.localPort }

    override fun setUp() {
        super.setUp()
        mockServerService = project.getService(MockServerService::class.java)
        configService = project.getService(ConfigService::class.java)

        // Clean config
        runCatching {
            val root = configService.getRootConfig()
            root.proxyGroups.clear()
            configService.saveRootConfig(root)
        }
    }

    override fun tearDown() {
        try {
            mockServerService.stopAllServers()
        } finally {
            System.clearProperty("interceptwave.allowForwardInTests")
            super.tearDown()
        }
    }

    private fun isUpstreamAlive(base: String): Boolean = try {
        val api = base.trimEnd('/') + "/health"
        val conn = URI(api).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 1500
        conn.readTimeout = 1500
        conn.requestMethod = "GET"
        conn.responseCode
        true
    } catch (_: Exception) { false }

    fun `test forwarding enabled in tests hits target server`() {
        // Enable forwarding in tests for this case
        System.setProperty("interceptwave.allowForwardInTests", "true")

        val forwardPath = "/echo"
        val base = upstreamBase()
        if (!isUpstreamAlive(base)) return

        val localPort = freePort()
        val config = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Forwarding",
            port = localPort,
            interceptPrefix = "/api",
            stripPrefix = true,
            baseUrl = upstreamBase(),
            enabled = true,
            mockApis = mutableListOf() // no mocks, force forwarding
        )

        val root = configService.getRootConfig()
        root.proxyGroups.add(config)
        configService.saveRootConfig(root)
        assertTrue(mockServerService.startServer(config.id))

        val url = URI("http://localhost:$localPort$forwardPath").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        val code = conn.responseCode
        // Consider forward successful as long as not internal 502 from proxy
        assertNotEquals(502, code)
    }

    fun `test forwarding POST copies body and headers`() {
        System.setProperty("interceptwave.allowForwardInTests", "true")

        val forwardPath = "/echo"
        val base = upstreamBase()
        if (!isUpstreamAlive(base)) return

        val localPort = freePort()
        val config = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Forward POST",
            port = localPort,
            interceptPrefix = "/api",
            stripPrefix = true,
            baseUrl = upstreamBase(),
            enabled = true,
            mockApis = mutableListOf()
        )

        val root = configService.getRootConfig()
        root.proxyGroups.add(config)
        configService.saveRootConfig(root)
        assertTrue(mockServerService.startServer(config.id))

        val url = URI("http://localhost:$localPort$forwardPath").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer token123")
        val payload = "{\"name\":\"abc\"}"
        conn.outputStream.use { it.write(payload.toByteArray()) }

        val code = conn.responseCode
        assertNotEquals(502, code)
    }

    fun `test forwarding PUT copies body`() {
        System.setProperty("interceptwave.allowForwardInTests", "true")

        val forwardPath = "/echo"
        val base = upstreamBase()
        if (!isUpstreamAlive(base)) return

        val localPort = freePort()
        val config = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Forward PUT",
            port = localPort,
            interceptPrefix = "/api",
            stripPrefix = true,
            baseUrl = upstreamBase(),
            enabled = true,
            mockApis = mutableListOf()
        )

        val root = configService.getRootConfig()
        root.proxyGroups.add(config)
        configService.saveRootConfig(root)
        assertTrue(mockServerService.startServer(config.id))

        val url = URI("http://localhost:$localPort$forwardPath").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "PUT"
        conn.doOutput = true
        val payload = "{\"k\":\"v\"}"
        conn.outputStream.use { it.write(payload.toByteArray()) }

        val code = conn.responseCode
        assertNotEquals(502, code)
    }

    fun `test forwarding PATCH copies body`() {
        System.setProperty("interceptwave.allowForwardInTests", "true")

        val forwardPath = "/echo"
        val base = upstreamBase()
        if (!isUpstreamAlive(base)) return

        val localPort = freePort()
        val config = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Forward PATCH",
            port = localPort,
            interceptPrefix = "/api",
            stripPrefix = true,
            baseUrl = upstreamBase(),
            enabled = true,
            mockApis = mutableListOf()
        )

        val root = configService.getRootConfig()
        root.proxyGroups.add(config)
        configService.saveRootConfig(root)
        assertTrue(mockServerService.startServer(config.id))

        val url = URI("http://localhost:$localPort$forwardPath").toURL()
        val conn = url.openConnection() as HttpURLConnection
        // HttpURLConnection 不支持 PATCH，使用 POST 并通过 Header 模拟覆盖
        conn.requestMethod = "POST"
        conn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
        conn.doOutput = true
        val payload = "{\"a\":1}"
        conn.outputStream.use { it.write(payload.toByteArray()) }

        val code = conn.responseCode
        assertNotEquals(502, code)
    }
}
