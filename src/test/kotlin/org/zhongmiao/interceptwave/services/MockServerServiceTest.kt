package org.zhongmiao.interceptwave.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.zhongmiao.interceptwave.model.MockApiConfig
import org.zhongmiao.interceptwave.model.MockConfig
import java.net.HttpURLConnection
import java.net.URI

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
            // Ensure server is stopped after each test
            if (mockServerService.isRunning()) {
                mockServerService.stop()
            }
        } finally {
            super.tearDown()
        }
    }

    fun `test server is not running initially`() {
        assertFalse(mockServerService.isRunning())
        assertNull(mockServerService.getServerUrl())
    }

    fun `test start server successfully`() {
        val config = MockConfig(port = 18888) // Use different port for testing
        configService.saveConfig(config)

        val result = mockServerService.start()

        assertTrue(result)
        assertTrue(mockServerService.isRunning())
        assertNotNull(mockServerService.getServerUrl())
        assertEquals("http://localhost:18888", mockServerService.getServerUrl())
    }

    fun `test stop server successfully`() {
        val config = MockConfig(port = 18889)
        configService.saveConfig(config)

        mockServerService.start()
        assertTrue(mockServerService.isRunning())

        mockServerService.stop()
        assertFalse(mockServerService.isRunning())
        assertNull(mockServerService.getServerUrl())
    }

    fun `test start server when already running returns false`() {
        val config = MockConfig(port = 18890)
        configService.saveConfig(config)

        mockServerService.start()
        val result = mockServerService.start()

        assertFalse(result)
        assertTrue(mockServerService.isRunning())
    }

    fun `test server responds to root path with welcome page`() {
        val config = MockConfig(port = 18891)
        configService.saveConfig(config)

        mockServerService.start()
        assertTrue(mockServerService.isRunning())

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
            mockServerService.stop()
        }
    }

    fun `test server responds with mock data for configured api`() {
        val mockData = "{\"id\": 123, \"name\": \"test\"}"
        val config = MockConfig(
            port = 18892,
            interceptPrefix = "/api",
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
        configService.saveConfig(config)

        mockServerService.start()
        assertTrue(mockServerService.isRunning())

        try {
            val url = URI("http://localhost:18892/api/user").toURL()
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"

            assertEquals(200, connection.responseCode)

            val response = connection.inputStream.bufferedReader().readText()
            assertEquals(mockData, response)
        } finally {
            mockServerService.stop()
        }
    }

    fun `test server respects method matching`() {
        val config = MockConfig(
            port = 18893,
            mockApis = mutableListOf(
                MockApiConfig(
                    path = "/api/data",
                    mockData = "{\"method\": \"POST\"}",
                    method = "POST",
                    enabled = true
                )
            )
        )
        configService.saveConfig(config)

        mockServerService.start()

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
            mockServerService.stop()
        }
    }

    fun `test server respects ALL method matching`() {
        val mockData = "{\"method\": \"ALL\"}"
        val config = MockConfig(
            port = 18894,
            mockApis = mutableListOf(
                MockApiConfig(
                    path = "/api/all",
                    mockData = mockData,
                    method = "ALL",
                    enabled = true
                )
            )
        )
        configService.saveConfig(config)

        mockServerService.start()

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
            mockServerService.stop()
        }
    }

    fun `test server respects enabled flag`() {
        val config = MockConfig(
            port = 18895,
            mockApis = mutableListOf(
                MockApiConfig(
                    path = "/api/disabled",
                    mockData = "{\"disabled\": true}",
                    enabled = false
                )
            )
        )
        configService.saveConfig(config)

        mockServerService.start()

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
            mockServerService.stop()
        }
    }

    fun `test server returns correct status code`() {
        val config = MockConfig(
            port = 18896,
            mockApis = mutableListOf(
                MockApiConfig(
                    path = "/api/created",
                    mockData = "{\"created\": true}",
                    statusCode = 201,
                    enabled = true
                )
            )
        )
        configService.saveConfig(config)

        mockServerService.start()

        try {
            val url = URI("http://localhost:18896/api/created").toURL()
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"

            assertEquals(201, connection.responseCode)
        } finally {
            mockServerService.stop()
        }
    }

    fun `test server handles OPTIONS request`() {
        val config = MockConfig(
            port = 18897,
            mockApis = mutableListOf(
                MockApiConfig(
                    path = "/api/cors",
                    mockData = "{}",
                    enabled = true
                )
            )
        )
        configService.saveConfig(config)

        mockServerService.start()

        try {
            val url = URI("http://localhost:18897/api/cors").toURL()
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "OPTIONS"

            assertEquals(200, connection.responseCode)
            assertNotNull(connection.getHeaderField("Access-Control-Allow-Origin"))
        } finally {
            mockServerService.stop()
        }
    }

    fun `test getServerUrl returns null when server is stopped`() {
        assertNull(mockServerService.getServerUrl())

        val config = MockConfig(port = 18898)
        configService.saveConfig(config)

        mockServerService.start()
        assertNotNull(mockServerService.getServerUrl())

        mockServerService.stop()
        assertNull(mockServerService.getServerUrl())
    }
}