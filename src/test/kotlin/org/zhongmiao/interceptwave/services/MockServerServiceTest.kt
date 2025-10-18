package org.zhongmiao.interceptwave.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.zhongmiao.interceptwave.model.MockApiConfig
import org.zhongmiao.interceptwave.model.ProxyConfig
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID

class MockServerServiceTest : BasePlatformTestCase() {

    private lateinit var mockServerService: MockServerService
    private lateinit var configService: ConfigService

    override fun setUp() {
        super.setUp()
        mockServerService = project.getService(MockServerService::class.java)
        configService = project.getService(ConfigService::class.java)
    }

    override fun tearDown() {
        try {
            // Ensure all servers are stopped after each test
            mockServerService.stopAllServers()
        } finally {
            super.tearDown()
        }
    }

    // Helper method to add a proxy config
    private fun addProxyConfig(config: ProxyConfig) {
        val rootConfig = configService.getRootConfig()
        rootConfig.proxyGroups.add(config)
        configService.saveRootConfig(rootConfig)
    }

    fun `test server is not running initially`() {
        val runningServers = mockServerService.getRunningServers()
        assertTrue(runningServers.isEmpty())
    }

    fun `test start server successfully`() {
        val config = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Test Config",
            port = 18888,
            enabled = true
        )
        val rootConfig = configService.getRootConfig()
        rootConfig.proxyGroups.add(config)
        configService.saveRootConfig(rootConfig)

        val result = mockServerService.startServer(config.id)

        assertTrue(result)
        assertTrue(mockServerService.getServerStatus(config.id))
        assertNotNull(mockServerService.getServerUrl(config.id))
        assertEquals("http://localhost:18888", mockServerService.getServerUrl(config.id))
    }

    fun `test stop server successfully`() {
        val config = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Test Config",
            port = 18889,
            enabled = true
        )
        addProxyConfig(config)

        mockServerService.startServer(config.id)
        assertTrue(mockServerService.getServerStatus(config.id))

        mockServerService.stopServer(config.id)
        assertFalse(mockServerService.getServerStatus(config.id))
        assertNull(mockServerService.getServerUrl(config.id))
    }

    fun `test start server when already running returns false`() {
        val config = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Test Config",
            port = 18890,
            enabled = true
        )
        addProxyConfig(config)

        mockServerService.startServer(config.id)
        val result = mockServerService.startServer(config.id)

        assertFalse(result)
        assertTrue(mockServerService.getServerStatus(config.id))
    }

    fun `test server responds to root path with welcome page`() {
        val config = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Test Config",
            port = 18891,
            enabled = true
        )
        addProxyConfig(config)

        mockServerService.startServer(config.id)
        assertTrue(mockServerService.getServerStatus(config.id))

        try {
            val url = URI("http://localhost:18891/").toURL()
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"

            assertEquals(200, connection.responseCode)
            assertEquals("application/json; charset=UTF-8", connection.contentType)

            val response = connection.inputStream.bufferedReader().readText()
            assertTrue(response.contains("status"))
            assertTrue(response.contains("running"))
        } finally {
            mockServerService.stopServer(config.id)
        }
    }

    fun `test server responds with mock data for configured api`() {
        val mockData = "{\"id\": 123, \"name\": \"test\"}"
        val config = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Test Config",
            port = 18892,
            interceptPrefix = "/api",
            enabled = true,
            mockApis = mutableListOf(
                MockApiConfig(
                    path = "/api/user",
                    mockData = mockData,
                    method = "GET",
                    statusCode = 200,
                    enabled = true
                )
            )
        )
        addProxyConfig(config)

        mockServerService.startServer(config.id)
        assertTrue(mockServerService.getServerStatus(config.id))

        try {
            val url = URI("http://localhost:18892/api/user").toURL()
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"

            assertEquals(200, connection.responseCode)

            val response = connection.inputStream.bufferedReader().readText()
            assertEquals(mockData, response)
        } finally {
            mockServerService.stopServer(config.id)
        }
    }

    fun `test server respects method matching`() {
        val config = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Test Config",
            port = 18893,
            enabled = true,
            mockApis = mutableListOf(
                MockApiConfig(
                    path = "/api/data",
                    mockData = "{\"method\": \"POST\"}",
                    method = "POST",
                    enabled = true
                )
            )
        )
        addProxyConfig(config)

        mockServerService.startServer(config.id)

        try {
            // GET request should not match POST-only api
            val getUrl = URI("http://localhost:18893/api/data").toURL()
            val getConnection = getUrl.openConnection() as HttpURLConnection
            getConnection.requestMethod = "GET"

            // Should forward or return error (not 200 with mock data)
            val getResponse = if (getConnection.responseCode < 400) {
                getConnection.inputStream.bufferedReader().readText()
            } else {
                getConnection.errorStream.bufferedReader().readText()
            }
            assertFalse(getResponse.contains("{\"method\": \"POST\"}"))
        } finally {
            mockServerService.stopServer(config.id)
        }
    }

    fun `test server respects ALL method matching`() {
        val mockData = "{\"method\": \"ALL\"}"
        val config = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Test Config",
            port = 18894,
            enabled = true,
            mockApis = mutableListOf(
                MockApiConfig(
                    path = "/api/all",
                    mockData = mockData,
                    method = "ALL",
                    enabled = true
                )
            )
        )
        addProxyConfig(config)

        mockServerService.startServer(config.id)

        try {
            // GET request should match
            val getUrl = URI("http://localhost:18894/api/all").toURL()
            val getConnection = getUrl.openConnection() as HttpURLConnection
            getConnection.requestMethod = "GET"
            val getResponse = getConnection.inputStream.bufferedReader().readText()
            assertEquals(mockData, getResponse)

            // POST request should also match
            val postUrl = URI("http://localhost:18894/api/all").toURL()
            val postConnection = postUrl.openConnection() as HttpURLConnection
            postConnection.requestMethod = "POST"
            postConnection.doOutput = true
            val postResponse = postConnection.inputStream.bufferedReader().readText()
            assertEquals(mockData, postResponse)
        } finally {
            mockServerService.stopServer(config.id)
        }
    }

    fun `test server respects enabled flag`() {
        val config = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Test Config",
            port = 18895,
            enabled = true,
            mockApis = mutableListOf(
                MockApiConfig(
                    path = "/api/disabled",
                    mockData = "{\"disabled\": true}",
                    enabled = false
                )
            )
        )
        addProxyConfig(config)

        mockServerService.startServer(config.id)

        try {
            val url = URI("http://localhost:18895/api/disabled").toURL()
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"

            // Should not return mock data since API is disabled
            val response = if (connection.responseCode < 400) {
                connection.inputStream.bufferedReader().readText()
            } else {
                connection.errorStream.bufferedReader().readText()
            }
            assertFalse(response.contains("{\"disabled\": true}"))
        } finally {
            mockServerService.stopServer(config.id)
        }
    }

    fun `test server returns correct status code`() {
        val config = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Test Config",
            port = 18896,
            enabled = true,
            mockApis = mutableListOf(
                MockApiConfig(
                    path = "/api/created",
                    mockData = "{\"created\": true}",
                    statusCode = 201,
                    enabled = true
                )
            )
        )
        addProxyConfig(config)

        mockServerService.startServer(config.id)

        try {
            val url = URI("http://localhost:18896/api/created").toURL()
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"

            assertEquals(201, connection.responseCode)
        } finally {
            mockServerService.stopServer(config.id)
        }
    }

    fun `test server handles OPTIONS request`() {
        val config = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Test Config",
            port = 18897,
            enabled = true,
            mockApis = mutableListOf(
                MockApiConfig(
                    path = "/api/cors",
                    mockData = "{}",
                    enabled = true
                )
            )
        )
        addProxyConfig(config)

        mockServerService.startServer(config.id)

        try {
            val url = URI("http://localhost:18897/api/cors").toURL()
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "OPTIONS"

            assertEquals(200, connection.responseCode)
            assertNotNull(connection.getHeaderField("Access-Control-Allow-Origin"))
        } finally {
            mockServerService.stopServer(config.id)
        }
    }

    fun `test getServerUrl returns null when server is stopped`() {
        val config = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Test Config",
            port = 18898,
            enabled = true
        )
        addProxyConfig(config)

        assertNull(mockServerService.getServerUrl(config.id))

        mockServerService.startServer(config.id)
        assertNotNull(mockServerService.getServerUrl(config.id))

        mockServerService.stopServer(config.id)
        assertNull(mockServerService.getServerUrl(config.id))
    }

    fun `test stripPrefix true matches relative paths`() {
        val mockData = "{\"user\": \"test\"}"
        val config = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Test Config",
            port = 18899,
            interceptPrefix = "/api",
            stripPrefix = true,
            enabled = true,
            mockApis = mutableListOf(
                MockApiConfig(
                    path = "/user",  // 相对路径，不包含 /api
                    mockData = mockData,
                    method = "GET",
                    enabled = true
                )
            )
        )
        addProxyConfig(config)

        mockServerService.startServer(config.id)

        try {
            // 请求 /api/user 应该匹配到 path="/user"
            val url = URI("http://localhost:18899/api/user").toURL()
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"

            assertEquals(200, connection.responseCode)
            val response = connection.inputStream.bufferedReader().readText()
            assertEquals(mockData, response)
        } finally {
            mockServerService.stopServer(config.id)
        }
    }

    fun `test stripPrefix false matches full paths`() {
        val mockData = "{\"product\": \"test\"}"
        val config = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Test Config",
            port = 18900,
            interceptPrefix = "/api",
            stripPrefix = false,
            enabled = true,
            mockApis = mutableListOf(
                MockApiConfig(
                    path = "/api/product",  // 完整路径，包含 /api
                    mockData = mockData,
                    method = "GET",
                    enabled = true
                )
            )
        )
        addProxyConfig(config)

        mockServerService.startServer(config.id)

        try {
            // 请求 /api/product 应该匹配到 path="/api/product"
            val url = URI("http://localhost:18900/api/product").toURL()
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"

            assertEquals(200, connection.responseCode)
            val response = connection.inputStream.bufferedReader().readText()
            assertEquals(mockData, response)
        } finally {
            mockServerService.stopServer(config.id)
        }
    }

    fun `test stripPrefix true does not match without prefix`() {
        val config = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Test Config",
            port = 18901,
            interceptPrefix = "/api",
            stripPrefix = true,
            enabled = true,
            mockApis = mutableListOf(
                MockApiConfig(
                    path = "/api/order",  // 错误配置：stripPrefix=true 但 path 包含了完整路径
                    mockData = "{\"order\": 1}",
                    enabled = true
                )
            )
        )
        addProxyConfig(config)

        mockServerService.startServer(config.id)

        try {
            // 请求 /api/order，去掉前缀后是 /order，不会匹配到 path="/api/order"
            val url = URI("http://localhost:18901/api/order").toURL()
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"

            // 应该不返回 mock 数据（因为不匹配）
            val response = if (connection.responseCode < 400) {
                connection.inputStream.bufferedReader().readText()
            } else {
                connection.errorStream.bufferedReader().readText()
            }
            assertFalse(response.contains("{\"order\": 1}"))
        } finally {
            mockServerService.stopServer(config.id)
        }
    }

    fun `test stripPrefix true with multiple paths`() {
        val config = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Test Config",
            port = 18902,
            interceptPrefix = "/api",
            stripPrefix = true,
            enabled = true,
            mockApis = mutableListOf(
                MockApiConfig(
                    path = "/user",
                    mockData = "{\"type\": \"user\"}",
                    enabled = true
                ),
                MockApiConfig(
                    path = "/product",
                    mockData = "{\"type\": \"product\"}",
                    enabled = true
                ),
                MockApiConfig(
                    path = "/order",
                    mockData = "{\"type\": \"order\"}",
                    enabled = true
                )
            )
        )
        addProxyConfig(config)

        mockServerService.startServer(config.id)

        try {
            // 测试第一个路径
            val url1 = URI("http://localhost:18902/api/user").toURL()
            val conn1 = url1.openConnection() as HttpURLConnection
            assertEquals("{\"type\": \"user\"}", conn1.inputStream.bufferedReader().readText())

            // 测试第二个路径
            val url2 = URI("http://localhost:18902/api/product").toURL()
            val conn2 = url2.openConnection() as HttpURLConnection
            assertEquals("{\"type\": \"product\"}", conn2.inputStream.bufferedReader().readText())

            // 测试第三个路径
            val url3 = URI("http://localhost:18902/api/order").toURL()
            val conn3 = url3.openConnection() as HttpURLConnection
            assertEquals("{\"type\": \"order\"}", conn3.inputStream.bufferedReader().readText())
        } finally {
            mockServerService.stopServer(config.id)
        }
    }

    fun `test startAllServers and stopAllServers`() {
        val config1 = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Config 1",
            port = 18903,
            enabled = true
        )
        val config2 = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Config 2",
            port = 18904,
            enabled = true
        )
        addProxyConfig(config1)
        addProxyConfig(config2)

        val results = mockServerService.startAllServers()

        assertTrue(results[config1.id] == true)
        assertTrue(results[config2.id] == true)
        assertEquals(2, mockServerService.getRunningServers().size)

        mockServerService.stopAllServers()
        assertTrue(mockServerService.getRunningServers().isEmpty())
    }
}