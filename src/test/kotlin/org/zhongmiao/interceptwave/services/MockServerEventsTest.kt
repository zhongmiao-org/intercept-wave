package org.zhongmiao.interceptwave.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.zhongmiao.interceptwave.events.*
import org.zhongmiao.interceptwave.model.MockApiConfig
import org.zhongmiao.interceptwave.model.ProxyConfig
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URI
import java.util.UUID

class MockServerEventsTest : BasePlatformTestCase() {

    private lateinit var mockServerService: MockServerService
    private lateinit var configService: ConfigService
    private val received = mutableListOf<MockServerEvent>()
    private lateinit var connection: com.intellij.util.messages.MessageBusConnection
    private var targetServer: HttpServer? = null

    override fun setUp() {
        super.setUp()
        mockServerService = project.getService(MockServerService::class.java)
        configService = project.getService(ConfigService::class.java)

        // 清空配置，避免跨用例污染
        runCatching {
            val root = configService.getRootConfig()
            root.proxyGroups.clear()
            configService.saveRootConfig(root)
        }

        // 订阅事件，收集到内存
        received.clear()
        connection = project.messageBus.connect()
        connection.subscribe(MOCK_SERVER_TOPIC, MockServerEventListener { e -> received.add(e) })
    }

    override fun tearDown() {
        try {
            mockServerService.stopAllServers()
            connection.dispose()
            System.clearProperty("interceptwave.allowForwardInTests")
            targetServer?.stop(0)
        } finally {
            super.tearDown()
        }
    }

    // 简单等待工具：轮询条件直到超时
    private fun await(timeoutMs: Long = 3000L, predicate: () -> Boolean): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (predicate()) return true
            Thread.sleep(10)
        }
        return false
    }

    fun `test server start and stop publish events`() {
        val cfg = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "EvtStartStop",
            port = 19040,
            enabled = true
        )
        val root = configService.getRootConfig()
        root.proxyGroups.add(cfg)
        configService.saveRootConfig(root)

        val ok = mockServerService.startServer(cfg.id)
        assertTrue(ok)

        // 至少包含启动中的两个事件（等待异步投递）
        assertTrue(await { received.any { it is ServerStarting && it.configId == cfg.id } })
        assertTrue(await { received.any { it is ServerStarted && it.configId == cfg.id && (it as ServerStarted).port == 19040 } })

        mockServerService.stopServer(cfg.id)
        assertTrue(await { received.any { it is ServerStopped && it.configId == cfg.id } })
    }

    fun `test request emits RequestReceived and MockMatched`() {
        val cfg = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "EvtMock",
            port = 19041,
            interceptPrefix = "/api",
            stripPrefix = true,
            enabled = true,
            mockApis = mutableListOf(
                MockApiConfig(path = "/user", mockData = "{\"ok\":true}", method = "GET", enabled = true)
            )
        )
        val root = configService.getRootConfig()
        root.proxyGroups.add(cfg)
        configService.saveRootConfig(root)

        mockServerService.startServer(cfg.id)

        val url = URI("http://localhost:19041/api/user").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        assertEquals(200, conn.responseCode)

        assertTrue(await { received.any { it is RequestReceived && it.configId == cfg.id && (it as RequestReceived).path == "/api/user" } })
        assertTrue(await { received.any { it is MockMatched && it.configId == cfg.id && (it as MockMatched).statusCode == 200 } })
    }

    fun `test forwarding emits Forwarded`() {
        // 允许单测中真实转发
        System.setProperty("interceptwave.allowForwardInTests", "true")

        // 启动目标服务器
        val targetPort = 19042
        val path = "/api/echo"
        targetServer = HttpServer.create(InetSocketAddress(targetPort), 0).apply {
            createContext(path) { ex: HttpExchange ->
                val bytes = "{\"ok\":true}".toByteArray()
                ex.responseHeaders.add("Content-Type", "application/json")
                ex.sendResponseHeaders(200, bytes.size.toLong())
                ex.responseBody.use { it.write(bytes) }
            }
            executor = java.util.concurrent.Executors.newSingleThreadExecutor()
            start()
        }

        val cfg = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "EvtForward",
            port = 19043,
            interceptPrefix = "/api",
            stripPrefix = true,
            baseUrl = "http://localhost:$targetPort",
            enabled = true,
            mockApis = mutableListOf() // 无匹配，强制转发
        )
        val root = configService.getRootConfig()
        root.proxyGroups.add(cfg)
        configService.saveRootConfig(root)

        mockServerService.startServer(cfg.id)

        val url = URI("http://localhost:19043$path").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        assertEquals(200, conn.responseCode)

        assertTrue(await { received.any { it is RequestReceived && it.configId == cfg.id && (it as RequestReceived).path == path } })
        assertTrue(await { received.any { it is Forwarded && it.configId == cfg.id && (it as Forwarded).statusCode == 200 } })
    }

    fun `test forwarding failure emits ErrorOccurred`() {
        // 启用转发，但指向无效端口，触发失败
        System.setProperty("interceptwave.allowForwardInTests", "true")

        val cfg = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "EvtForwardFail",
            port = 19044,
            interceptPrefix = "/api",
            stripPrefix = true,
            baseUrl = "http://127.0.0.1:9", // 关闭端口，确保失败
            enabled = true,
            mockApis = mutableListOf()
        )
        val root = configService.getRootConfig()
        root.proxyGroups.add(cfg)
        configService.saveRootConfig(root)

        mockServerService.startServer(cfg.id)

        val url = URI("http://localhost:19044/api/any").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        // 502 由代理失败返回
        assertEquals(502, conn.responseCode)

        assertTrue(
            await {
                received.any { it is ErrorOccurred && it.configId == cfg.id && (it as ErrorOccurred).message.contains("代理错误") }
            }
        )
    }
}
