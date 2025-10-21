package org.zhongmiao.interceptwave.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.Assert.*
import java.util.UUID

/**
 * Tests for RootConfig and ProxyConfig data models (v2.0)
 */
class ProxyConfigTest {

    private val json = Json { prettyPrint = true }

    @Test
    fun `test RootConfig default values`() {
        val rootConfig = RootConfig()

        assertEquals("2.0", rootConfig.version)
        assertTrue(rootConfig.proxyGroups.isEmpty())
    }

    @Test
    fun `test RootConfig with proxy groups`() {
        val rootConfig = RootConfig(
            version = "2.0",
            proxyGroups = mutableListOf(
                ProxyConfig(
                    name = "User Service",
                    port = 8888
                ),
                ProxyConfig(
                    name = "Order Service",
                    port = 8889
                )
            )
        )

        assertEquals("2.0", rootConfig.version)
        assertEquals(2, rootConfig.proxyGroups.size)
        assertEquals("User Service", rootConfig.proxyGroups[0].name)
        assertEquals("Order Service", rootConfig.proxyGroups[1].name)
    }

    @Test
    fun `test RootConfig serialization`() {
        val rootConfig = RootConfig(
            version = "2.0",
            proxyGroups = mutableListOf(
                ProxyConfig(
                    name = "Test Service",
                    port = 9000
                )
            )
        )

        val jsonString = json.encodeToString(rootConfig)
        val decoded = json.decodeFromString<RootConfig>(jsonString)

        assertEquals(rootConfig.version, decoded.version)
        assertEquals(rootConfig.proxyGroups.size, decoded.proxyGroups.size)
        assertEquals(rootConfig.proxyGroups[0].name, decoded.proxyGroups[0].name)
        assertEquals(rootConfig.proxyGroups[0].port, decoded.proxyGroups[0].port)
    }

    @Test
    fun `test ProxyConfig default values`() {
        val config = ProxyConfig()

        assertNotNull(config.id)
        assertEquals("默认配置", config.name)
        assertEquals(8888, config.port)
        assertEquals("/api", config.interceptPrefix)
        assertEquals("http://localhost:8080", config.baseUrl)
        assertTrue(config.stripPrefix)
        assertEquals("", config.globalCookie)
        assertTrue(config.enabled)
        assertTrue(config.mockApis.isEmpty())
    }

    @Test
    fun `test ProxyConfig with custom values`() {
        val id = UUID.randomUUID().toString()
        val config = ProxyConfig(
            id = id,
            name = "Custom Service",
            port = 9000,
            interceptPrefix = "/v1",
            baseUrl = "https://api.example.com",
            stripPrefix = false,
            globalCookie = "sessionId=abc123",
            enabled = false,
            mockApis = mutableListOf(
                MockApiConfig(
                    path = "/api/test",
                    mockData = "{\"status\": \"ok\"}"
                )
            )
        )

        assertEquals(id, config.id)
        assertEquals("Custom Service", config.name)
        assertEquals(9000, config.port)
        assertEquals("/v1", config.interceptPrefix)
        assertEquals("https://api.example.com", config.baseUrl)
        assertFalse(config.stripPrefix)
        assertEquals("sessionId=abc123", config.globalCookie)
        assertFalse(config.enabled)
        assertEquals(1, config.mockApis.size)
    }

    @Test
    fun `test ProxyConfig UUID generation`() {
        val config1 = ProxyConfig()
        val config2 = ProxyConfig()

        assertNotEquals(config1.id, config2.id)
        assertTrue(config1.id.isNotEmpty())
        assertTrue(config2.id.isNotEmpty())
    }

    @Test
    fun `test ProxyConfig serialization`() {
        val config = ProxyConfig(
            id = "test-id-123",
            name = "Test Config",
            port = 8765,
            interceptPrefix = "/test",
            baseUrl = "http://test.local",
            stripPrefix = true,
            globalCookie = "cookie=value",
            enabled = true,
            mockApis = mutableListOf(
                MockApiConfig(
                    path = "/api/user",
                    mockData = "{\"id\": 1}"
                )
            )
        )

        val jsonString = json.encodeToString(config)
        val decoded = json.decodeFromString<ProxyConfig>(jsonString)

        assertEquals(config.id, decoded.id)
        assertEquals(config.name, decoded.name)
        assertEquals(config.port, decoded.port)
        assertEquals(config.interceptPrefix, decoded.interceptPrefix)
        assertEquals(config.baseUrl, decoded.baseUrl)
        assertEquals(config.stripPrefix, decoded.stripPrefix)
        assertEquals(config.globalCookie, decoded.globalCookie)
        assertEquals(config.enabled, decoded.enabled)
        assertEquals(config.mockApis.size, decoded.mockApis.size)
    }

    @Test
    fun `test ProxyConfig with multiple mock APIs`() {
        val config = ProxyConfig(
            mockApis = mutableListOf(
                MockApiConfig(path = "/api/user", mockData = "{\"user\": 1}"),
                MockApiConfig(path = "/api/order", mockData = "{\"order\": 1}"),
                MockApiConfig(path = "/api/product", mockData = "{\"product\": 1}")
            )
        )

        assertEquals(3, config.mockApis.size)
        assertEquals("/api/user", config.mockApis[0].path)
        assertEquals("/api/order", config.mockApis[1].path)
        assertEquals("/api/product", config.mockApis[2].path)
    }

    @Test
    fun `test ProxyConfig with special characters in name`() {
        val config = ProxyConfig(
            name = "用户服务 / User Service (测试)"
        )

        val jsonString = json.encodeToString(config)
        val decoded = json.decodeFromString<ProxyConfig>(jsonString)

        assertEquals("用户服务 / User Service (测试)", decoded.name)
    }

    @Test
    fun `test ProxyConfig with edge case port numbers`() {
        val config1 = ProxyConfig(port = 1)
        assertEquals(1, config1.port)

        val config2 = ProxyConfig(port = 65535)
        assertEquals(65535, config2.port)

        val config3 = ProxyConfig(port = 8888)
        assertEquals(8888, config3.port)
    }

    @Test
    fun `test ProxyConfig with empty interceptPrefix`() {
        val config = ProxyConfig(interceptPrefix = "")

        assertEquals("", config.interceptPrefix)
    }

    @Test
    fun `test ProxyConfig with long globalCookie`() {
        val longCookie = "session=abc123; userId=456; token=xyz789; preferences=dark_mode; language=zh_CN"
        val config = ProxyConfig(globalCookie = longCookie)

        assertEquals(longCookie, config.globalCookie)

        val jsonString = json.encodeToString(config)
        val decoded = json.decodeFromString<ProxyConfig>(jsonString)

        assertEquals(longCookie, decoded.globalCookie)
    }

    @Test
    fun `test RootConfig with multiple proxy groups serialization`() {
        val rootConfig = RootConfig(
            proxyGroups = mutableListOf(
                ProxyConfig(
                    name = "Service 1",
                    port = 8001,
                    enabled = true
                ),
                ProxyConfig(
                    name = "Service 2",
                    port = 8002,
                    enabled = false
                ),
                ProxyConfig(
                    name = "Service 3",
                    port = 8003,
                    enabled = true
                )
            )
        )

        val jsonString = json.encodeToString(rootConfig)
        val decoded = json.decodeFromString<RootConfig>(jsonString)

        assertEquals(3, decoded.proxyGroups.size)
        assertEquals("Service 1", decoded.proxyGroups[0].name)
        assertEquals(8001, decoded.proxyGroups[0].port)
        assertTrue(decoded.proxyGroups[0].enabled)

        assertEquals("Service 2", decoded.proxyGroups[1].name)
        assertEquals(8002, decoded.proxyGroups[1].port)
        assertFalse(decoded.proxyGroups[1].enabled)

        assertEquals("Service 3", decoded.proxyGroups[2].name)
        assertEquals(8003, decoded.proxyGroups[2].port)
        assertTrue(decoded.proxyGroups[2].enabled)
    }

    @Test
    fun `test ProxyConfig stripPrefix behavior documentation`() {
        // stripPrefix = true (推荐)
        val config1 = ProxyConfig(
            interceptPrefix = "/api",
            stripPrefix = true,
            mockApis = mutableListOf(
                MockApiConfig(path = "/user", mockData = "{}")  // 相对路径
            )
        )
        assertTrue(config1.stripPrefix)
        assertEquals("/user", config1.mockApis[0].path)

        // stripPrefix = false
        val config2 = ProxyConfig(
            interceptPrefix = "/api",
            stripPrefix = false,
            mockApis = mutableListOf(
                MockApiConfig(path = "/api/user", mockData = "{}")  // 完整路径
            )
        )
        assertFalse(config2.stripPrefix)
        assertEquals("/api/user", config2.mockApis[0].path)
    }

    @Test
    fun `test RootConfig version validation`() {
        val rootConfig = RootConfig(version = "2.0")
        assertEquals("2.0", rootConfig.version)

        rootConfig.version = "3.0"
        assertEquals("3.0", rootConfig.version)
    }

    @Test
    fun `test ProxyConfig mutability`() {
        val config = ProxyConfig()

        // Test that all fields are mutable
        config.name = "Updated Name"
        assertEquals("Updated Name", config.name)

        config.port = 9999
        assertEquals(9999, config.port)

        config.enabled = false
        assertFalse(config.enabled)

        config.mockApis.add(MockApiConfig(path = "/test", mockData = "{}"))
        assertEquals(1, config.mockApis.size)
    }

    @Test
    fun `test MockApiConfig with useCookie flag`() {
        val api = MockApiConfig(
            path = "/api/test",
            mockData = "{}",
            useCookie = true
        )

        assertTrue(api.useCookie)

        val jsonString = json.encodeToString(api)
        val decoded = json.decodeFromString<MockApiConfig>(jsonString)

        assertTrue(decoded.useCookie)
    }

    @Test
    fun `test ProxyConfig with all fields populated`() {
        val config = ProxyConfig(
            id = "unique-id-123",
            name = "Full Config",
            port = 7777,
            interceptPrefix = "/v2/api",
            baseUrl = "https://prod.example.com",
            stripPrefix = false,
            globalCookie = "auth=token123; session=xyz",
            enabled = false,
            mockApis = mutableListOf(
                MockApiConfig(
                    path = "/v2/api/user/1",
                    mockData = "{\"id\": 1, \"name\": \"Test User\"}",
                    method = "GET",
                    statusCode = 200,
                    delay = 500L,
                    enabled = true,
                    useCookie = true
                ),
                MockApiConfig(
                    path = "/v2/api/order",
                    mockData = "{\"orders\": []}",
                    method = "POST",
                    statusCode = 201,
                    delay = 1000L,
                    enabled = false,
                    useCookie = false
                )
            )
        )

        val jsonString = json.encodeToString(config)
        val decoded = json.decodeFromString<ProxyConfig>(jsonString)

        // Verify all fields are preserved
        assertEquals("unique-id-123", decoded.id)
        assertEquals("Full Config", decoded.name)
        assertEquals(7777, decoded.port)
        assertEquals("/v2/api", decoded.interceptPrefix)
        assertEquals("https://prod.example.com", decoded.baseUrl)
        assertFalse(decoded.stripPrefix)
        assertEquals("auth=token123; session=xyz", decoded.globalCookie)
        assertFalse(decoded.enabled)

        assertEquals(2, decoded.mockApis.size)

        // Verify first mock API
        assertEquals("/v2/api/user/1", decoded.mockApis[0].path)
        assertEquals("GET", decoded.mockApis[0].method)
        assertEquals(200, decoded.mockApis[0].statusCode)
        assertEquals(500L, decoded.mockApis[0].delay)
        assertTrue(decoded.mockApis[0].enabled)
        assertTrue(decoded.mockApis[0].useCookie)

        // Verify second mock API
        assertEquals("/v2/api/order", decoded.mockApis[1].path)
        assertEquals("POST", decoded.mockApis[1].method)
        assertEquals(201, decoded.mockApis[1].statusCode)
        assertEquals(1000L, decoded.mockApis[1].delay)
        assertFalse(decoded.mockApis[1].enabled)
        assertFalse(decoded.mockApis[1].useCookie)
    }
}