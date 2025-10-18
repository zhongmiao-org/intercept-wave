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
            println("Failed to delete config directory ${e.message}")
        }

        // Get the service - it will initialize with loadConfig()
        configService = project.getService(ConfigService::class.java)

        // Force reload to ensure we get a fresh config after cleanup
        // Since the config file was deleted, this should create default config
        val defaultConfig = MockConfig()
        configService.saveConfig(defaultConfig)
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

        // 获取第一个配置组的 ID
        val configId = configService.getAllProxyGroups().first().id

        val userApi = configService.getMockApi(configId = configId, path = "/api/user")
        assertNotNull(userApi)
        assertEquals("/api/user", userApi?.path)

        val postsApi = configService.getMockApi(configId = configId, path = "/api/posts")
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

        // 获取第一个配置组的 ID
        val configId = configService.getAllProxyGroups().first().id

        val api = configService.getMockApi(configId = configId, path = "/api/nonexistent")
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

        // 获取第一个配置组的 ID
        val configId = configService.getAllProxyGroups().first().id

        val api = configService.getMockApi(configId = configId, path = "/api/test")
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

    fun `test config auto-complete missing fields`() {
        // 手动创建一个缺少部分字段的配置文件
        val incompleteConfigJson = """
            {
                "baseUrl": "http://192.168.180.135:30332",
                "mockApis": [
                    {
                        "path": "/user",
                        "mockData": "{\n  name: \"a\",\n  age: 12\n}",
                        "method": "GET"
                    }
                ]
            }
        """.trimIndent()

        val configFile = File(configDir, "config.json")
        configDir.mkdirs()
        configFile.writeText(incompleteConfigJson)

        // 重新加载配置服务，触发自动补全
        val newService = ConfigService(project)
        val loadedConfig = newService.getConfig()

        // 验证缺失的字段已经被补全为默认值
        assertEquals(8888, loadedConfig.port)
        assertEquals("/api", loadedConfig.interceptPrefix)
        assertEquals("http://192.168.180.135:30332", loadedConfig.baseUrl)
        assertFalse(loadedConfig.stripPrefix)
        assertEquals("", loadedConfig.globalCookie)
        assertEquals(1, loadedConfig.mockApis.size)

        // 验证配置文件已经被更新
        val updatedContent = configFile.readText()
        println("Updated config content: $updatedContent")
        assertTrue("Config should contain 'port' field", updatedContent.contains("port"))
        assertTrue("Config should contain 'interceptPrefix' field", updatedContent.contains("interceptPrefix"))
        assertTrue("Config should contain 'stripPrefix' field", updatedContent.contains("stripPrefix"))
        assertTrue("Config should contain 'globalCookie' field", updatedContent.contains("globalCookie"))
    }

    fun `test config with all fields present is not rewritten`() {
        // 创建一个完整的配置
        val completeConfig = MockConfig(
            port = 9000,
            interceptPrefix = "/v1",
            baseUrl = "http://example.com",
            stripPrefix = true,
            globalCookie = "session=abc",
            mockApis = mutableListOf()
        )

        configService.saveConfig(completeConfig)

        val configFile = File(configDir, "config.json")
        val originalContent = configFile.readText()

        // 等待一小段时间确保时间戳会变化（如果文件被修改）
        Thread.sleep(10)

        // 重新加载配置
        val newService = ConfigService(project)
        newService.getConfig()

        // 验证文件内容没有变化（因为所有字段都存在）
        val newContent = configFile.readText()
        assertEquals(originalContent, newContent)
    }
}