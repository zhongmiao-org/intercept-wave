package org.zhongmiao.interceptwave.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
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

    fun `test forwarding error path returns 502`() {
        System.setProperty("interceptwave.allowForwardInTests", "true")

        val config = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "ForwardError",
            port = 19025,
            interceptPrefix = "/api",
            stripPrefix = true,
            baseUrl = "http://127.0.0.1:9", // closed port, force ConnectException
            enabled = true,
            mockApis = mutableListOf()
        )
        val root = configService.getRootConfig()
        root.proxyGroups.add(config)
        configService.saveRootConfig(root)
        mockServerService.startServer(config.id)

        val url = URI("http://localhost:19025/api/anything").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        val code = conn.responseCode
        assertEquals(502, code)
    }
}

