package org.zhongmiao.interceptwave.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
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
            // Wait for servers to fully stop
            Thread.sleep(200)
        } catch (_: Exception) {
            // Ignore errors during cleanup
        }
        super.tearDown()
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

    fun `test wildcard single segment star`() {
        val mockData = "{\"star\": 1}"
        val config = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Wildcard Single",
            port = 18905,
            interceptPrefix = "/api",
            stripPrefix = true,
            enabled = true,
            mockApis = mutableListOf(
                MockApiConfig(
                    path = "/a/b/*",
                    mockData = mockData,
                    method = "GET",
                    enabled = true
                )
            )
        )
        addProxyConfig(config)

        mockServerService.startServer(config.id)

        try {
            // Matches exactly one segment
            val url1 = URI("http://localhost:18905/api/a/b/123").toURL()
            val conn1 = url1.openConnection() as HttpURLConnection
            conn1.requestMethod = "GET"
            assertEquals(200, conn1.responseCode)
            assertEquals(mockData, conn1.inputStream.bufferedReader().readText())

            // Does not match multiple segments
            val url2 = URI("http://localhost:18905/api/a/b/123/456").toURL()
            val conn2 = url2.openConnection() as HttpURLConnection
            conn2.requestMethod = "GET"
            val notMatched2 = if (conn2.responseCode < 400) conn2.inputStream else conn2.errorStream
            val text2 = notMatched2.bufferedReader().readText()
            assertFalse(text2 == mockData)
        } finally {
            mockServerService.stopServer(config.id)
        }
    }

    fun `test wildcard multi segment double star`() {
        val mockData = "{\"dstar\": 1}"
        val config = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Wildcard Double",
            port = 18906,
            interceptPrefix = "/api",
            stripPrefix = true,
            enabled = true,
            mockApis = mutableListOf(
                MockApiConfig(
                    path = "/a/b/**",
                    mockData = mockData,
                    method = "GET",
                    enabled = true
                )
            )
        )
        addProxyConfig(config)

        mockServerService.startServer(config.id)

        try {
            // Matches nested paths
            val url1 = URI("http://localhost:18906/api/a/b/123").toURL()
            val conn1 = url1.openConnection() as HttpURLConnection
            conn1.requestMethod = "GET"
            assertEquals(mockData, conn1.inputStream.bufferedReader().readText())

            val url2 = URI("http://localhost:18906/api/a/b/123/456").toURL()
            val conn2 = url2.openConnection() as HttpURLConnection
            conn2.requestMethod = "GET"
            assertEquals(mockData, conn2.inputStream.bufferedReader().readText())

            // Does not match base without further segment (by design)
            val url3 = URI("http://localhost:18906/api/a/b").toURL()
            val conn3 = url3.openConnection() as HttpURLConnection
            conn3.requestMethod = "GET"
            val notMatched3 = if (conn3.responseCode < 400) conn3.inputStream else conn3.errorStream
            val text3 = notMatched3.bufferedReader().readText()
            assertFalse(text3 == mockData)
        } finally {
            mockServerService.stopServer(config.id)
        }
    }

    fun `test wildcard middle segment`() {
        val mockData = "{\"middle\": 1}"
        val config = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Wildcard Middle",
            port = 18907,
            interceptPrefix = "/api",
            stripPrefix = true,
            enabled = true,
            mockApis = mutableListOf(
                MockApiConfig(
                    path = "/order/*/submit",
                    mockData = mockData,
                    method = "GET",
                    enabled = true
                )
            )
        )
        addProxyConfig(config)

        mockServerService.startServer(config.id)

        try {
            // Matches exactly one middle segment
            val url1 = URI("http://localhost:18907/api/order/123/submit").toURL()
            val conn1 = url1.openConnection() as HttpURLConnection
            conn1.requestMethod = "GET"
            assertEquals(mockData, conn1.inputStream.bufferedReader().readText())

            // Does not match if there are extra segments
            val url2 = URI("http://localhost:18907/api/order/123/extra/submit").toURL()
            val conn2 = url2.openConnection() as HttpURLConnection
            conn2.requestMethod = "GET"
            val notMatched4 = if (conn2.responseCode < 400) conn2.inputStream else conn2.errorStream
            val text4 = notMatched4.bufferedReader().readText()
            assertFalse(text4 == mockData)
        } finally {
            mockServerService.stopServer(config.id)
        }
    }

    fun `test prefix route returns welcome when stripPrefix true`() {
        val config = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Prefix Welcome",
            port = 18908,
            interceptPrefix = "/api",
            stripPrefix = true,
            enabled = true,
            mockApis = mutableListOf(
                MockApiConfig(path = "/user", mockData = "{\"u\":1}", enabled = true),
                MockApiConfig(path = "/product", mockData = "{\"p\":1}", enabled = false),
                MockApiConfig(path = "/order", mockData = "{\"o\":1}", enabled = true)
            )
        )
        addProxyConfig(config)

        mockServerService.startServer(config.id)

        try {
            fun checkWelcome(path: String) {
                val url = URI("http://localhost:18908$path").toURL()
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                assertEquals(200, conn.responseCode)
                assertEquals("application/json; charset=UTF-8", conn.contentType)
                val body = conn.inputStream.bufferedReader().readText()

                // Basic fields
                assertTrue(body.contains("\"status\""))
                assertTrue(body.contains("\"running\""))

                // Validate counts and arrays are enabled-only (2 enabled from 3)
                val json = kotlinx.serialization.json.Json.parseToJsonElement(body).jsonObject
                val mockApisObj = json["mockApis"]!!.jsonObject
                assertEquals(3, mockApisObj["total"]!!.toString().toInt())
                assertEquals(2, mockApisObj["enabled"]!!.toString().toInt())

                val apis = json["apis"]!!.jsonArray
                assertEquals(2, apis.size)

                val examples = json["examples"]!!.jsonArray
                assertEquals(2, examples.size)
                // Example URL should start with http://localhost:18908/api
                val example0 = examples[0].jsonObject["url"]!!.toString().trim('"')
                assertTrue(example0.startsWith("http://localhost:18908/api"))
            }

            // /api without trailing slash
            checkWelcome("/api")
            // /api/ with trailing slash
            checkWelcome("/api/")
        } finally {
            mockServerService.stopServer(config.id)
        }
    }
}
