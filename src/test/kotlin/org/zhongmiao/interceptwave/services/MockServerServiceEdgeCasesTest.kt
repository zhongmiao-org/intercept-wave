package org.zhongmiao.interceptwave.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.zhongmiao.interceptwave.model.MockApiConfig
import org.zhongmiao.interceptwave.model.ProxyConfig
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID

class MockServerServiceEdgeCasesTest : BasePlatformTestCase() {

    private lateinit var mockServerService: MockServerService
    private lateinit var configService: ConfigService

    override fun setUp() {
        super.setUp()
        mockServerService = project.getService(MockServerService::class.java)
        configService = project.getService(ConfigService::class.java)
        // Reset config to avoid cross-test accumulation
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
            super.tearDown()
        }
    }

    private fun addProxyConfig(config: ProxyConfig) {
        val root = configService.getRootConfig()
        root.proxyGroups.add(config)
        configService.saveRootConfig(root)
    }

    private fun freePort(): Int = java.net.ServerSocket(0).use { it.localPort }

    fun `test unmatched path returns 502 in tests`() {
        val p1 = freePort()
        val config = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Forward Disabled",
            port = p1,
            interceptPrefix = "/api",
            stripPrefix = true,
            enabled = true,
            mockApis = mutableListOf(
                MockApiConfig(path = "/exists", mockData = "{}", enabled = true)
            )
        )
        addProxyConfig(config)
        mockServerService.startServer(config.id)

        val url = URI("http://localhost:$p1/api/unknown").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        // Forwarding is disabled in unit tests by default, so expect 502
        val code = conn.responseCode
        assertEquals(502, code)
    }

    fun `test running servers list`() {
        val p2 = freePort()
        val config = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Running List",
            port = p2,
            enabled = true
        )
        addProxyConfig(config)
        mockServerService.startServer(config.id)

        val running = mockServerService.getRunningServers()
        assertEquals(1, running.size)
        val (id, url) = running[0]
        assertEquals(config.id, id)
        assertEquals("http://localhost:$p2", url)
    }

    fun `test stripPrefix false does not show welcome on prefix path`() {
        val p3 = freePort()
        val config = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "No Prefix Welcome",
            port = p3,
            interceptPrefix = "/api",
            stripPrefix = false,
            enabled = true,
            mockApis = mutableListOf(
                MockApiConfig(path = "/api/user", mockData = "{}", enabled = true)
            )
        )
        addProxyConfig(config)
        mockServerService.startServer(config.id)

        // Accessing /api (the prefix) should NOT return welcome JSON when stripPrefix=false
        val url = URI("http://localhost:$p3/api").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        val code = conn.responseCode
        assertEquals(502, code)
    }
}
