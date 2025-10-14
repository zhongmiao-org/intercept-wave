package org.zhongmiao.interceptwave.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.Assert.*

class MockConfigTest {

    private val json = Json { prettyPrint = true }

    @Test
    fun `test MockApiConfig default values`() {
        val api = MockApiConfig(
            path = "/api/test",
            mockData = "{\"status\": \"ok\"}"
        )

        assertEquals("/api/test", api.path)
        assertEquals("{\"status\": \"ok\"}", api.mockData)
        assertEquals("ALL", api.method)
        assertEquals(200, api.statusCode)
        assertEquals(0L, api.delay)
        assertTrue(api.enabled)
    }

    @Test
    fun `test MockApiConfig with custom values`() {
        val api = MockApiConfig(
            path = "/api/user",
            mockData = "{\"id\": 1}",
            method = "POST",
            statusCode = 201,
            delay = 1000L,
            enabled = false
        )

        assertEquals("/api/user", api.path)
        assertEquals("{\"id\": 1}", api.mockData)
        assertEquals("POST", api.method)
        assertEquals(201, api.statusCode)
        assertEquals(1000L, api.delay)
        assertFalse(api.enabled)
    }

    @Test
    fun `test MockApiConfig serialization`() {
        val api = MockApiConfig(
            path = "/api/test",
            mockData = "{\"result\": true}",
            method = "GET",
            statusCode = 200,
            delay = 500L,
            enabled = true
        )

        val jsonString = json.encodeToString(api)
        val decoded = json.decodeFromString<MockApiConfig>(jsonString)

        assertEquals(api.path, decoded.path)
        assertEquals(api.mockData, decoded.mockData)
        assertEquals(api.method, decoded.method)
        assertEquals(api.statusCode, decoded.statusCode)
        assertEquals(api.delay, decoded.delay)
        assertEquals(api.enabled, decoded.enabled)
    }

    @Test
    fun `test MockConfig default values`() {
        val config = MockConfig()

        assertEquals(8888, config.port)
        assertEquals("/api", config.interceptPrefix)
        assertEquals("http://localhost:8080", config.baseUrl)
        assertFalse(config.stripPrefix)
        assertTrue(config.mockApis.isEmpty())
    }

    @Test
    fun `test MockConfig with custom values`() {
        val mockApis = mutableListOf(
            MockApiConfig(
                path = "/api/user",
                mockData = "{\"name\": \"test\"}"
            ),
            MockApiConfig(
                path = "/api/posts",
                mockData = "{\"posts\": []}"
            )
        )

        val config = MockConfig(
            port = 9000,
            interceptPrefix = "/v1",
            baseUrl = "https://api.example.com",
            stripPrefix = true,
            mockApis = mockApis
        )

        assertEquals(9000, config.port)
        assertEquals("/v1", config.interceptPrefix)
        assertEquals("https://api.example.com", config.baseUrl)
        assertTrue(config.stripPrefix)
        assertEquals(2, config.mockApis.size)
        assertEquals("/api/user", config.mockApis[0].path)
        assertEquals("/api/posts", config.mockApis[1].path)
    }

    @Test
    fun `test MockConfig serialization with empty apis`() {
        val config = MockConfig(
            port = 7777,
            interceptPrefix = "/test",
            baseUrl = "http://test.local",
            stripPrefix = false
        )

        val jsonString = json.encodeToString(config)
        val decoded = json.decodeFromString<MockConfig>(jsonString)

        assertEquals(config.port, decoded.port)
        assertEquals(config.interceptPrefix, decoded.interceptPrefix)
        assertEquals(config.baseUrl, decoded.baseUrl)
        assertEquals(config.stripPrefix, decoded.stripPrefix)
        assertEquals(0, decoded.mockApis.size)
    }

    @Test
    fun `test MockConfig serialization with multiple apis`() {
        val config = MockConfig(
            port = 8080,
            interceptPrefix = "/api",
            baseUrl = "http://localhost:3000",
            stripPrefix = true,
            mockApis = mutableListOf(
                MockApiConfig(
                    path = "/api/user/1",
                    mockData = "{\"id\": 1, \"name\": \"Alice\"}",
                    method = "GET",
                    statusCode = 200,
                    delay = 100L,
                    enabled = true
                ),
                MockApiConfig(
                    path = "/api/user",
                    mockData = "{\"id\": 2, \"name\": \"Bob\"}",
                    method = "POST",
                    statusCode = 201,
                    delay = 200L,
                    enabled = false
                )
            )
        )

        val jsonString = json.encodeToString(config)
        val decoded = json.decodeFromString<MockConfig>(jsonString)

        assertEquals(config.port, decoded.port)
        assertEquals(config.interceptPrefix, decoded.interceptPrefix)
        assertEquals(config.baseUrl, decoded.baseUrl)
        assertEquals(config.stripPrefix, decoded.stripPrefix)
        assertEquals(2, decoded.mockApis.size)

        // Verify first API
        assertEquals("/api/user/1", decoded.mockApis[0].path)
        assertEquals("GET", decoded.mockApis[0].method)
        assertEquals(200, decoded.mockApis[0].statusCode)
        assertEquals(100L, decoded.mockApis[0].delay)
        assertTrue(decoded.mockApis[0].enabled)

        // Verify second API
        assertEquals("/api/user", decoded.mockApis[1].path)
        assertEquals("POST", decoded.mockApis[1].method)
        assertEquals(201, decoded.mockApis[1].statusCode)
        assertEquals(200L, decoded.mockApis[1].delay)
        assertFalse(decoded.mockApis[1].enabled)
    }

    @Test
    fun `test MockApiConfig with special characters in mockData`() {
        val mockData = """{"message": "Hello \"World\"", "path": "/api/test", "value": null}"""
        val api = MockApiConfig(
            path = "/api/special",
            mockData = mockData
        )

        val jsonString = json.encodeToString(api)
        val decoded = json.decodeFromString<MockApiConfig>(jsonString)

        assertEquals(mockData, decoded.mockData)
    }

    @Test
    fun `test MockConfig with edge case port numbers`() {
        val config1 = MockConfig(port = 1)
        assertEquals(1, config1.port)

        val config2 = MockConfig(port = 65535)
        assertEquals(65535, config2.port)
    }

    @Test
    fun `test MockApiConfig with zero delay`() {
        val api = MockApiConfig(
            path = "/api/fast",
            mockData = "{}",
            delay = 0L
        )

        assertEquals(0L, api.delay)
    }

    @Test
    fun `test MockApiConfig with maximum delay`() {
        val api = MockApiConfig(
            path = "/api/slow",
            mockData = "{}",
            delay = Long.MAX_VALUE
        )

        assertEquals(Long.MAX_VALUE, api.delay)
    }
}