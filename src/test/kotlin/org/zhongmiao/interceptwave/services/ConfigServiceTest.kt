package org.zhongmiao.interceptwave.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.zhongmiao.interceptwave.model.MockApiConfig
import org.zhongmiao.interceptwave.model.MockConfig
import java.io.File

class ConfigServiceTest : BasePlatformTestCase() {

    private lateinit var configService: ConfigService
    private lateinit var configDir: File

    override fun setUp() {
        super.setUp()
        // Initialize configDir first
        configDir = File(project.basePath, ".intercept-wave")

        // Force clean up any existing config before each test
        // This must happen before getting the service
        try {
            if (configDir.exists()) {
                configDir.deleteRecursively()
            }
        } catch (e: Exception) {
            // Ignore deletion errors in setup
        }

        configService = project.getService(ConfigService::class.java)
    }

    override fun tearDown() {
        try {
            // Clean up config directory after each test
            if (configDir.exists()) {
                configDir.deleteRecursively()
            }
        } finally {
            super.tearDown()
        }
    }

    fun `test getConfig returns default config on first load`() {
        val config = configService.getConfig()

        assertNotNull(config)
        assertEquals(8888, config.port)
        assertEquals("/api", config.interceptPrefix)
        assertEquals("http://localhost:8080", config.baseUrl)
        assertFalse(config.stripPrefix)
        assertEquals("", config.globalCookie)
        assertTrue(config.mockApis.isEmpty())
    }

    fun `test saveConfig persists configuration`() {
        val newConfig = MockConfig(
            port = 9000,
            interceptPrefix = "/v1",
            baseUrl = "https://api.example.com",
            stripPrefix = true,
            mockApis = mutableListOf(
                MockApiConfig(
                    path = "/api/test",
                    mockData = "{\"status\": \"ok\"}"
                )
            )
        )

        configService.saveConfig(newConfig)

        val loadedConfig = configService.getConfig()
        assertEquals(9000, loadedConfig.port)
        assertEquals("/v1", loadedConfig.interceptPrefix)
        assertEquals("https://api.example.com", loadedConfig.baseUrl)
        assertTrue(loadedConfig.stripPrefix)
        assertEquals(1, loadedConfig.mockApis.size)
        assertEquals("/api/test", loadedConfig.mockApis[0].path)
    }

    fun `test saveConfig creates config directory if not exists`() {
        // Ensure directory doesn't exist
        if (configDir.exists()) {
            configDir.deleteRecursively()
        }

        val config = MockConfig(port = 7777)
        configService.saveConfig(config)

        assertTrue(configDir.exists())
        assertTrue(configDir.isDirectory)
    }

    fun `test saveConfig creates config file`() {
        val config = MockConfig(port = 8080)
        configService.saveConfig(config)

        val configFile = File(configDir, "config.json")
        assertTrue(configFile.exists())
        assertTrue(configFile.isFile)
    }

    fun `test getMockApi returns correct api when enabled`() {
        val config = MockConfig(
            mockApis = mutableListOf(
                MockApiConfig(
                    path = "/api/user",
                    mockData = "{\"name\": \"test\"}",
                    enabled = true
                ),
                MockApiConfig(
                    path = "/api/posts",
                    mockData = "{\"posts\": []}",
                    enabled = false
                )
            )
        )

        configService.saveConfig(config)

        val userApi = configService.getMockApi("/api/user")
        assertNotNull(userApi)
        assertEquals("/api/user", userApi?.path)

        val postsApi = configService.getMockApi("/api/posts")
        assertNull(postsApi) // Should be null because it's disabled
    }

    fun `test getMockApi returns null for non-existent path`() {
        val config = MockConfig(
            mockApis = mutableListOf(
                MockApiConfig(
                    path = "/api/user",
                    mockData = "{}",
                    enabled = true
                )
            )
        )

        configService.saveConfig(config)

        val api = configService.getMockApi("/api/nonexistent")
        assertNull(api)
    }

    fun `test saveConfig with empty mockApis list`() {
        val config = MockConfig(
            port = 8080,
            mockApis = mutableListOf()
        )

        configService.saveConfig(config)

        val loadedConfig = configService.getConfig()
        assertTrue(loadedConfig.mockApis.isEmpty())
    }

    fun `test saveConfig with multiple mock apis`() {
        val config = MockConfig(
            mockApis = mutableListOf(
                MockApiConfig(path = "/api/1", mockData = "{\"id\": 1}"),
                MockApiConfig(path = "/api/2", mockData = "{\"id\": 2}"),
                MockApiConfig(path = "/api/3", mockData = "{\"id\": 3}")
            )
        )

        configService.saveConfig(config)

        val loadedConfig = configService.getConfig()
        assertEquals(3, loadedConfig.mockApis.size)
        assertEquals("/api/1", loadedConfig.mockApis[0].path)
        assertEquals("/api/2", loadedConfig.mockApis[1].path)
        assertEquals("/api/3", loadedConfig.mockApis[2].path)
    }

    fun `test saveConfig overwrites existing configuration`() {
        val config1 = MockConfig(port = 8888, interceptPrefix = "/api")
        configService.saveConfig(config1)

        val config2 = MockConfig(port = 9999, interceptPrefix = "/v2")
        configService.saveConfig(config2)

        val loadedConfig = configService.getConfig()
        assertEquals(9999, loadedConfig.port)
        assertEquals("/v2", loadedConfig.interceptPrefix)
    }

    fun `test config persists after service reload`() {
        val originalConfig = MockConfig(
            port = 8765,
            interceptPrefix = "/custom",
            mockApis = mutableListOf(
                MockApiConfig(path = "/test", mockData = "{}")
            )
        )

        configService.saveConfig(originalConfig)

        // Create a new service instance to simulate reload
        val newService = ConfigService(project)
        val loadedConfig = newService.getConfig()

        assertEquals(8765, loadedConfig.port)
        assertEquals("/custom", loadedConfig.interceptPrefix)
        assertEquals(1, loadedConfig.mockApis.size)
    }

    fun `test getMockApi with multiple apis having same path returns first enabled`() {
        val config = MockConfig(
            mockApis = mutableListOf(
                MockApiConfig(
                    path = "/api/test",
                    mockData = "{\"version\": 1}",
                    enabled = false
                ),
                MockApiConfig(
                    path = "/api/test",
                    mockData = "{\"version\": 2}",
                    enabled = true
                ),
                MockApiConfig(
                    path = "/api/test",
                    mockData = "{\"version\": 3}",
                    enabled = true
                )
            )
        )

        configService.saveConfig(config)

        val api = configService.getMockApi("/api/test")
        assertNotNull(api)
        assertEquals("{\"version\": 2}", api?.mockData)
    }

    fun `test config with special characters in paths and data`() {
        val config = MockConfig(
            interceptPrefix = "/api/v1/中文",
            mockApis = mutableListOf(
                MockApiConfig(
                    path = "/api/special/!@#$%",
                    mockData = "{\"message\": \"特殊字符 Special chars: <>&\\\"'\"}"
                )
            )
        )

        configService.saveConfig(config)

        val loadedConfig = configService.getConfig()
        assertEquals("/api/v1/中文", loadedConfig.interceptPrefix)
        assertEquals("/api/special/!@#$%", loadedConfig.mockApis[0].path)
        assertTrue(loadedConfig.mockApis[0].mockData.contains("特殊字符"))
    }
}