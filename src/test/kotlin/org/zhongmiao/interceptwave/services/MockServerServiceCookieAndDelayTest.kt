package org.zhongmiao.interceptwave.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.zhongmiao.interceptwave.model.MockApiConfig
import org.zhongmiao.interceptwave.model.ProxyConfig
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID

class MockServerServiceCookieAndDelayTest : BasePlatformTestCase() {

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
        } finally {
            super.tearDown()
        }
    }

    fun `test mock response sets cookie and cors headers with delay`() {
        val config = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "CookieDelay",
            port = 19024,
            interceptPrefix = "/api",
            stripPrefix = true,
            globalCookie = "sessionId=abc123",
            enabled = true,
            mockApis = mutableListOf(
                MockApiConfig(
                    path = "/info",
                    mockData = "{\"ok\":true}",
                    method = "GET",
                    enabled = true,
                    useCookie = true,
                    delay = 5
                )
            )
        )
        val root = configService.getRootConfig()
        root.proxyGroups.add(config)
        configService.saveRootConfig(root)

        mockServerService.startServer(config.id)

        val url = URI("http://localhost:19024/api/info").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        val code = conn.responseCode

        // status
        assertEquals(200, code)
        // cookie
        val cookie = conn.getHeaderField("Set-Cookie")
        assertTrue(cookie?.contains("sessionId=abc123") == true)
        // cors
        assertEquals("*", conn.getHeaderField("Access-Control-Allow-Origin"))
        assertTrue(conn.getHeaderField("Access-Control-Allow-Methods").contains("OPTIONS"))
    }
}

