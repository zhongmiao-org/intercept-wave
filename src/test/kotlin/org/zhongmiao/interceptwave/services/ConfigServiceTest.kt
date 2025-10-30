package org.zhongmiao.interceptwave.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.zhongmiao.interceptwave.model.MockApiConfig
import org.zhongmiao.interceptwave.model.ProxyConfig
import org.zhongmiao.interceptwave.model.RootConfig
import java.io.File
import java.util.UUID

/**
 * Tests for ConfigService focusing on v2.0 API (RootConfig and ProxyConfig)
 */
class ConfigServiceTest : BasePlatformTestCase() {

    private lateinit var configService: ConfigService
    private lateinit var configDir: File

    override fun setUp() {
        super.setUp()
        configDir = File(project.basePath, ".intercept-wave")

        // Clean up before each test
        try {
            if (configDir.exists()) {
                configDir.deleteRecursively()
            }
        } catch (_: Exception) {
            // Ignore cleanup errors
        }

        configService = project.getService(ConfigService::class.java)
    }

    override fun tearDown() {
        try {
            if (configDir.exists()) {
                configDir.deleteRecursively()
            }
        } catch (_: Exception) {
            // Ignore cleanup errors
        }
        super.tearDown()
    }

    // ============== Root Config Tests ==============

    fun `test getRootConfig returns default config on first load`() {
        val rootConfig = configService.getRootConfig()

        assertNotNull(rootConfig)
        // version follows x.y (major.minor); value depends on running plugin version
        assertTrue(rootConfig.version.matches(Regex("\\d+\\.\\d+")))
        // Root config should have at least one default group or none
        assertNotNull(rootConfig.proxyGroups)
    }

    fun `test saveRootConfig persists configuration`() {
        val proxyConfig = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Test Config",
            port = 9000,
            interceptPrefix = "/v1",
            baseUrl = "https://api.example.com",
            enabled = true
        )

        val rootConfig = RootConfig(
            version = "2.0",
            proxyGroups = mutableListOf(proxyConfig)
        )

        configService.saveRootConfig(rootConfig)

        val loaded = configService.getRootConfig()
        assertEquals(1, loaded.proxyGroups.size)
        assertEquals("Test Config", loaded.proxyGroups[0].name)
        assertEquals(9000, loaded.proxyGroups[0].port)
    }

    // ============== Proxy Group Management Tests ==============

    fun `test getAllProxyGroups returns all groups`() {
        val config1 = ProxyConfig(id = UUID.randomUUID().toString(), name = "Config 1", port = 8001, enabled = true)
        val config2 = ProxyConfig(id = UUID.randomUUID().toString(), name = "Config 2", port = 8002, enabled = false)

        val rootConfig = RootConfig(proxyGroups = mutableListOf(config1, config2))
        configService.saveRootConfig(rootConfig)

        val groups = configService.getAllProxyGroups()
        assertEquals(2, groups.size)
        assertEquals("Config 1", groups[0].name)
        assertEquals("Config 2", groups[1].name)
    }

    fun `test getEnabledProxyGroups returns only enabled groups`() {
        val config1 = ProxyConfig(id = UUID.randomUUID().toString(), name = "Enabled", port = 8001, enabled = true)
        val config2 = ProxyConfig(id = UUID.randomUUID().toString(), name = "Disabled", port = 8002, enabled = false)
        val config3 = ProxyConfig(id = UUID.randomUUID().toString(), name = "Also Enabled", port = 8003, enabled = true)

        val rootConfig = RootConfig(proxyGroups = mutableListOf(config1, config2, config3))
        configService.saveRootConfig(rootConfig)

        val enabled = configService.getEnabledProxyGroups()
        assertEquals(2, enabled.size)
        assertTrue(enabled.all { it.enabled })
        assertEquals("Enabled", enabled[0].name)
        assertEquals("Also Enabled", enabled[1].name)
    }

    fun `test getProxyGroup returns specific group by ID`() {
        val id = UUID.randomUUID().toString()
        val config = ProxyConfig(id = id, name = "Test", port = 8001, enabled = true)

        val rootConfig = RootConfig(proxyGroups = mutableListOf(config))
        configService.saveRootConfig(rootConfig)

        val found = configService.getProxyGroup(id)
        assertNotNull(found)
        assertEquals("Test", found?.name)
        assertEquals(8001, found?.port)
    }

    fun `test getProxyGroup returns null for non-existent ID`() {
        val found = configService.getProxyGroup("non-existent-id")
        assertNull(found)
    }

    fun `test add proxy group manually via RootConfig`() {
        val rootConfig = configService.getRootConfig()

        val newConfig = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "New Group",
            port = 9999,
            enabled = true
        )

        rootConfig.proxyGroups.add(newConfig)
        configService.saveRootConfig(rootConfig)

        val all = configService.getAllProxyGroups()
        assertTrue(all.any { it.name == "New Group" })
    }

    fun `test update proxy group manually via RootConfig`() {
        val id = UUID.randomUUID().toString()
        val config = ProxyConfig(id = id, name = "Original", port = 8001, enabled = true)

        val rootConfig = RootConfig(proxyGroups = mutableListOf(config))
        configService.saveRootConfig(rootConfig)

        // Update manually
        val updated = configService.getRootConfig()
        val index = updated.proxyGroups.indexOfFirst { it.id == id }
        updated.proxyGroups[index] = ProxyConfig(id = id, name = "Updated", port = 8002, enabled = false)
        configService.saveRootConfig(updated)

        val found = configService.getProxyGroup(id)
        assertEquals("Updated", found?.name)
        assertEquals(8002, found?.port)
        assertFalse(found?.enabled ?: true)
    }

    fun `test delete proxy group manually via RootConfig`() {
        val id1 = UUID.randomUUID().toString()
        val id2 = UUID.randomUUID().toString()
        val config1 = ProxyConfig(id = id1, name = "Keep", port = 8001, enabled = true)
        val config2 = ProxyConfig(id = id2, name = "Delete", port = 8002, enabled = true)

        val rootConfig = RootConfig(proxyGroups = mutableListOf(config1, config2))
        configService.saveRootConfig(rootConfig)

        // Delete manually
        val updated = configService.getRootConfig()
        updated.proxyGroups.removeIf { it.id == id2 }
        configService.saveRootConfig(updated)

        val all = configService.getAllProxyGroups()
        assertEquals(1, all.size)
        assertEquals("Keep", all[0].name)
        assertNull(configService.getProxyGroup(id2))
    }

    fun `test toggle enabled status manually via RootConfig`() {
        val id = UUID.randomUUID().toString()
        val config = ProxyConfig(id = id, name = "Test", port = 8001, enabled = true)

        val rootConfig = RootConfig(proxyGroups = mutableListOf(config))
        configService.saveRootConfig(rootConfig)

        // Toggle to false
        val updated1 = configService.getRootConfig()
        val index1 = updated1.proxyGroups.indexOfFirst { it.id == id }
        updated1.proxyGroups[index1].enabled = false
        configService.saveRootConfig(updated1)
        assertFalse(configService.getProxyGroup(id)?.enabled ?: true)

        // Toggle to true
        val updated2 = configService.getRootConfig()
        val index2 = updated2.proxyGroups.indexOfFirst { it.id == id }
        updated2.proxyGroups[index2].enabled = true
        configService.saveRootConfig(updated2)
        assertTrue(configService.getProxyGroup(id)?.enabled ?: false)
    }

    fun `test createDefaultProxyConfig creates valid config`() {
        val config = configService.createDefaultProxyConfig(0)

        assertNotNull(config.id)
        assertTrue(config.id.isNotEmpty())
        assertNotNull(config.name)
        assertTrue(config.port > 0)
        assertNotNull(config.interceptPrefix)
        assertTrue(config.enabled)
    }

    fun `test createDefaultProxyConfig with custom name`() {
        val config = configService.createDefaultProxyConfig(1, "Custom Name")

        assertEquals("Custom Name", config.name)
        assertNotNull(config.id)
    }

    // ============== Mock API Persistence Tests ==============

    fun `test mockApis persist in ProxyConfig`() {
        val id = UUID.randomUUID().toString()
        val mockApi = MockApiConfig(
            path = "/api/user",
            mockData = "{\"name\": \"test\"}",
            method = "GET",
            statusCode = 200,
            enabled = true
        )
        val config = ProxyConfig(
            id = id,
            name = "Test",
            port = 8001,
            enabled = true,
            mockApis = mutableListOf(mockApi)
        )

        val rootConfig = RootConfig(proxyGroups = mutableListOf(config))
        configService.saveRootConfig(rootConfig)

        val loaded = configService.getProxyGroup(id)
        assertEquals(1, loaded?.mockApis?.size)
        assertEquals("/api/user", loaded?.mockApis?.get(0)?.path)
        assertEquals("{\"name\": \"test\"}", loaded?.mockApis?.get(0)?.mockData)
        assertTrue(loaded?.mockApis?.get(0)?.enabled ?: false)
    }

    fun `test multiple mockApis persist correctly`() {
        val id = UUID.randomUUID().toString()
        val api1 = MockApiConfig(path = "/api/user", mockData = "{\"type\": \"user\"}", enabled = true)
        val api2 = MockApiConfig(path = "/api/product", mockData = "{\"type\": \"product\"}", enabled = false)
        val api3 = MockApiConfig(path = "/api/order", mockData = "{\"type\": \"order\"}", enabled = true)

        val config = ProxyConfig(
            id = id,
            name = "Test",
            port = 8001,
            enabled = true,
            mockApis = mutableListOf(api1, api2, api3)
        )

        val rootConfig = RootConfig(proxyGroups = mutableListOf(config))
        configService.saveRootConfig(rootConfig)

        val loaded = configService.getProxyGroup(id)
        assertEquals(3, loaded?.mockApis?.size)
        assertEquals("/api/user", loaded?.mockApis?.get(0)?.path)
        assertEquals("/api/product", loaded?.mockApis?.get(1)?.path)
        assertEquals("/api/order", loaded?.mockApis?.get(2)?.path)
    }

    // ============== Configuration Persistence Tests ==============

    fun `test config persists after service reload`() {
        val id = UUID.randomUUID().toString()
        val config = ProxyConfig(
            id = id,
            name = "Persistent",
            port = 8765,
            interceptPrefix = "/custom",
            enabled = true
        )

        val rootConfig = RootConfig(proxyGroups = mutableListOf(config))
        configService.saveRootConfig(rootConfig)

        // Create new service instance to simulate reload
        val newService = ConfigService(project)
        val loaded = newService.getProxyGroup(id)

        assertNotNull(loaded)
        assertEquals("Persistent", loaded?.name)
        assertEquals(8765, loaded?.port)
        assertEquals("/custom", loaded?.interceptPrefix)
    }

    fun `test saveRootConfig creates config directory if not exists`() {
        if (configDir.exists()) {
            configDir.deleteRecursively()
        }

        val rootConfig = RootConfig()
        configService.saveRootConfig(rootConfig)

        assertTrue(configDir.exists())
        assertTrue(configDir.isDirectory)
    }

    fun `test saveRootConfig creates config file`() {
        val rootConfig = RootConfig()
        configService.saveRootConfig(rootConfig)

        val configFile = File(configDir, "config.json")
        assertTrue(configFile.exists())
        assertTrue(configFile.isFile)
    }

    fun `test normalize and minify existing mockData on load`() {
        // Prepare a group with pretty-printed and JS-like mockData
        val pretty = """
            {
              "a": 1,
              "b": { "c": 2 }
            }
        """.trimIndent()
        val jsLike = "{ key: 'v', arr: [1,2,], /*c*/ d: 'x' }"

        val cfg = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "NormTest",
            port = 19990,
            enabled = true,
            mockApis = mutableListOf(
                MockApiConfig(path = "/p1", mockData = pretty, enabled = true),
                MockApiConfig(path = "/p2", mockData = jsLike, enabled = true)
            )
        )
        val root = RootConfig(version = "2.0", proxyGroups = mutableListOf(cfg))
        configService.saveRootConfig(root)

        // Simulate a reload to trigger normalization during load
        val newService = ConfigService(project)
        val loadedGroup = newService.getAllProxyGroups().find { it.name == "NormTest" }!!
        val m1 = loadedGroup.mockApis[0].mockData
        val m2 = loadedGroup.mockApis[1].mockData

        // Should be minified (no newlines) and strict JSON (double quotes)
        assertFalse(m1.contains("\n"))
        assertFalse(m2.contains("\n"))
        assertTrue(m1.startsWith("{"))
        assertTrue(m2.startsWith("{"))
        assertTrue(m2.contains("\"key\""))
        assertTrue(m2.contains("\"v\""))
    }

    // ============== Edge Cases ==============

    fun `test config with special characters in fields`() {
        val id = UUID.randomUUID().toString()
        val config = ProxyConfig(
            id = id,
            name = "配置名称 - Special <>&",
            port = 8001,
            interceptPrefix = "/api/v1/中文",
            baseUrl = "http://example.com/特殊路径",
            enabled = true
        )

        val rootConfig = RootConfig(proxyGroups = mutableListOf(config))
        configService.saveRootConfig(rootConfig)

        val loaded = configService.getProxyGroup(id)
        assertEquals("配置名称 - Special <>&", loaded?.name)
        assertEquals("/api/v1/中文", loaded?.interceptPrefix)
        assertTrue(loaded?.baseUrl?.contains("特殊路径") ?: false)
    }

    fun `test empty proxyGroups list`() {
        val rootConfig = RootConfig(proxyGroups = mutableListOf())
        configService.saveRootConfig(rootConfig)

        val all = configService.getAllProxyGroups()
        assertTrue(all.isEmpty())
    }

    fun `test multiple saves overwrite correctly`() {
        val id = UUID.randomUUID().toString()

        val config1 = ProxyConfig(id = id, name = "Version 1", port = 8001, enabled = true)
        val root1 = RootConfig(proxyGroups = mutableListOf(config1))
        configService.saveRootConfig(root1)

        val config2 = ProxyConfig(id = id, name = "Version 2", port = 8002, enabled = false)
        val root2 = RootConfig(proxyGroups = mutableListOf(config2))
        configService.saveRootConfig(root2)

        val loaded = configService.getProxyGroup(id)
        assertEquals("Version 2", loaded?.name)
        assertEquals(8002, loaded?.port)
        assertFalse(loaded?.enabled ?: true)
    }
}
