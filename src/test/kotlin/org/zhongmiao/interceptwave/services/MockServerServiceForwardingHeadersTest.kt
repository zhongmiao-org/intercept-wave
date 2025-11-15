package org.zhongmiao.interceptwave.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.experimental.categories.Category
import org.junit.Assert.*
import org.zhongmiao.interceptwave.model.ProxyConfig
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID
import org.zhongmiao.interceptwave.tags.IntegrationTest

/**
 * 转发响应头过滤相关用例
 * 验证不会复制 Transfer-Encoding 等不安全头到客户端响应
 */
@Category(IntegrationTest::class)
class MockServerServiceForwardingHeadersTest : BasePlatformTestCase() {

    private lateinit var mockServerService: MockServerService
    private lateinit var configService: ConfigService

    private fun upstreamBase(): String =
        System.getProperty("iw.upstream.http") ?: (System.getenv("IW_UPSTREAM_HTTP") ?: "http://localhost:9000")

    private fun freePort(): Int = java.net.ServerSocket(0).use { it.localPort }

    override fun setUp() {
        super.setUp()
        mockServerService = project.getService(MockServerService::class.java)
        configService = project.getService(ConfigService::class.java)
        // 清理配置，避免跨用例影响
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

    fun `test forwarded response filters transfer-encoding header`() {
        // 允许单测中进行真实代理
        System.setProperty("interceptwave.allowForwardInTests", "true")

        val forwardPath = "/headers"
        val base = upstreamBase()
        run {
            val u = base.trimEnd('/') + "/health"
            val ok = try {
                val c = URI(u).toURL().openConnection() as HttpURLConnection
                c.connectTimeout = 1500
                c.readTimeout = 1500
                c.requestMethod = "GET"
                c.responseCode
                true
            } catch (_: Exception) { false }
            if (!ok) return
        }

        val localPort = freePort()
        val config = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Forward Headers",
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
        conn.requestMethod = "GET"
        val code = conn.responseCode
        assertNotEquals(502, code)
        // Transfer-Encoding 头不应被复制
        assertNull(conn.getHeaderField("Transfer-Encoding"))
    }
}
