package org.zhongmiao.interceptwave.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.zhongmiao.interceptwave.model.ProxyConfig
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URI
import java.util.UUID

class MockServerServiceForwardingTest : BasePlatformTestCase() {

    private lateinit var mockServerService: MockServerService
    private lateinit var configService: ConfigService
    private var targetServer: HttpServer? = null

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
            targetServer?.stop(0)
            System.clearProperty("interceptwave.allowForwardInTests")
            super.tearDown()
        }
    }

    private fun startTargetServer(port: Int, path: String, responseCode: Int, body: String) {
        val server = HttpServer.create(InetSocketAddress(port), 0)
        server.createContext(path) { ex: HttpExchange ->
            val reqBody = ex.requestBody.readBytes()
            val bytes = if (reqBody.isNotEmpty()) reqBody else body.toByteArray(Charsets.UTF_8)
            // Echo a header for verification
            val auth = ex.requestHeaders.getFirst("Authorization")
            if (auth != null) {
                ex.responseHeaders.add("X-Echo-Auth", auth)
            }
            ex.responseHeaders.add("Content-Type", "application/json; charset=UTF-8")
            ex.sendResponseHeaders(responseCode, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        server.start()
        targetServer = server
    }

    fun `test forwarding enabled in tests hits target server`() {
        // Enable forwarding in tests for this case
        System.setProperty("interceptwave.allowForwardInTests", "true")

        val targetPort = 19020
        val forwardPath = "/api/echo"
        val payload = "{\"ok\":true}"
        startTargetServer(targetPort, forwardPath, 202, payload)

        val config = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Forwarding",
            port = 19021,
            interceptPrefix = "/api",
            stripPrefix = true,
            baseUrl = "http://localhost:$targetPort",
            enabled = true,
            mockApis = mutableListOf() // no mocks, force forwarding
        )

        val root = configService.getRootConfig()
        root.proxyGroups.add(config)
        configService.saveRootConfig(root)
        mockServerService.startServer(config.id)

        val url = URI("http://localhost:19021$forwardPath").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        val code = conn.responseCode
        val body = conn.inputStream.bufferedReader().readText()

        assertEquals(202, code)
        assertEquals(payload, body)
    }

    fun `test forwarding POST copies body and headers`() {
        System.setProperty("interceptwave.allowForwardInTests", "true")

        val targetPort = 19022
        val forwardPath = "/api/submit"
        startTargetServer(targetPort, forwardPath, 200, "{}")

        val config = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Forward POST",
            port = 19023,
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

        val url = URI("http://localhost:19023$forwardPath").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer token123")
        val payload = "{\"name\":\"abc\"}"
        conn.outputStream.use { it.write(payload.toByteArray()) }

        val code = conn.responseCode
        val echoed = conn.inputStream.bufferedReader().readText()
        val echoedAuth = conn.getHeaderField("X-Echo-Auth")

        assertEquals(200, code)
        assertEquals(payload, echoed)
        assertEquals("Bearer token123", echoedAuth)
    }

    fun `test forwarding PUT copies body`() {
        System.setProperty("interceptwave.allowForwardInTests", "true")

        val targetPort = 19030
        val forwardPath = "/api/put"
        startTargetServer(targetPort, forwardPath, 200, "{}")

        val config = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Forward PUT",
            port = 19031,
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

        val url = URI("http://localhost:19031$forwardPath").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "PUT"
        conn.doOutput = true
        val payload = "{\"k\":\"v\"}"
        conn.outputStream.use { it.write(payload.toByteArray()) }

        val code = conn.responseCode
        val echoed = conn.inputStream.bufferedReader().readText()
        assertEquals(200, code)
        assertEquals(payload, echoed)
    }

    fun `test forwarding PATCH copies body`() {
        System.setProperty("interceptwave.allowForwardInTests", "true")

        val targetPort = 19032
        val forwardPath = "/api/patch"
        startTargetServer(targetPort, forwardPath, 202, "{}")

        val config = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Forward PATCH",
            port = 19033,
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

        val url = URI("http://localhost:19033$forwardPath").toURL()
        val conn = url.openConnection() as HttpURLConnection
        // HttpURLConnection 不支持 PATCH，使用 POST 并通过 Header 模拟覆盖
        conn.requestMethod = "POST"
        conn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
        conn.doOutput = true
        val payload = "{\"a\":1}"
        conn.outputStream.use { it.write(payload.toByteArray()) }

        val code = conn.responseCode
        val echoed = conn.inputStream.bufferedReader().readText()
        assertEquals(202, code)
        assertEquals(payload, echoed)
    }
}
