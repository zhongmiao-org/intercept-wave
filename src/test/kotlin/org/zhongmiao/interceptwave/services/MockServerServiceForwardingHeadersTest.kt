package org.zhongmiao.interceptwave.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.zhongmiao.interceptwave.model.ProxyConfig
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URI
import java.util.UUID

/**
 * 转发响应头过滤相关用例
 * 验证不会复制 Transfer-Encoding 等不安全头到客户端响应
 */
class MockServerServiceForwardingHeadersTest : BasePlatformTestCase() {

    private lateinit var mockServerService: MockServerService
    private lateinit var configService: ConfigService
    private var targetServer: HttpServer? = null

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
            targetServer?.stop(0)
            System.clearProperty("interceptwave.allowForwardInTests")
            super.tearDown()
        }
    }

    private fun startTargetServer(port: Int, path: String, body: String) {
        val server = HttpServer.create(InetSocketAddress(port), 0)
        server.createContext(path) { ex: HttpExchange ->
            val bytes = body.toByteArray(Charsets.UTF_8)
            // 故意设置会被过滤的响应头
            ex.responseHeaders.add("Transfer-Encoding", "chunked")
            ex.responseHeaders.add("Content-Length", (bytes.size).toString())
            ex.responseHeaders.add("X-Unit", "yes")
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        server.start()
        targetServer = server
    }

    fun `test forwarded response filters transfer-encoding header`() {
        // 允许单测中进行真实代理
        System.setProperty("interceptwave.allowForwardInTests", "true")

        val targetPort = 19060
        val forwardPath = "/api/h"
        val payload = "{\"h\":true}"
        startTargetServer(targetPort, forwardPath, payload)

        val config = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Forward Headers",
            port = 19061,
            interceptPrefix = "/api",
            stripPrefix = true,
            baseUrl = "http://localhost:$targetPort",
            enabled = true,
            mockApis = mutableListOf()
        )
        val root = configService.getRootConfig()
        root.proxyGroups.add(config)
        configService.saveRootConfig(root)
        mockServerService.startServer(config.id)

        val url = URI("http://localhost:19061$forwardPath").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        val code = conn.responseCode
        val body = conn.inputStream.bufferedReader().readText()

        assertEquals(200, code)
        assertEquals(payload, body)
        // Transfer-Encoding 头不应被复制
        assertNull(conn.getHeaderField("Transfer-Encoding"))
        // 其他自定义头应该存在
        assertEquals("yes", conn.getHeaderField("X-Unit"))
        // Content-Length 由 HttpServer.sendResponseHeaders 设置，可能存在，这里不做强校验
    }
}

