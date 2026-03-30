package org.zhongmiao.interceptwave.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.zhongmiao.interceptwave.model.HttpRoute
import org.zhongmiao.interceptwave.model.ProxyConfig
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID

class MockServerServiceForwardingErrorTest : BasePlatformTestCase() {

    private lateinit var mockServerService: MockServerService
    private lateinit var configService: ConfigService

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
            System.clearProperty("interceptwave.allowForwardInTests")
        } finally {
            super.tearDown()
        }
    }

    private fun freePort(): Int = java.net.ServerSocket(0).use { it.localPort }

    fun `test forwarding error path returns 502`() {
        System.setProperty("interceptwave.allowForwardInTests", "true")

        val p = freePort()
        val config = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "ForwardError",
            port = p,
            stripPrefix = true,
            enabled = true,
            routes = mutableListOf(HttpRoute(pathPrefix = "/api", targetBaseUrl = "http://127.0.0.1:9", stripPrefix = true, enableMock = true))
        )
        val root = configService.getRootConfig()
        root.proxyGroups.add(config)
        configService.saveRootConfig(root)
        mockServerService.startServer(config.id)

        val url = URI("http://localhost:$p/api/anything").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        val code = conn.responseCode
        assertEquals(502, code)
    }
}
