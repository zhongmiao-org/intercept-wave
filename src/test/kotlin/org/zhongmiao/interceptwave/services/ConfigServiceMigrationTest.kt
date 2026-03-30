package org.zhongmiao.interceptwave.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

class ConfigServiceMigrationTest : BasePlatformTestCase() {

    private lateinit var configDir: File

    override fun setUp() {
        super.setUp()
        configDir = File(project.basePath, ".intercept-wave")
        if (configDir.exists()) configDir.deleteRecursively()
        configDir.mkdirs()
    }

    override fun tearDown() {
        try {
            if (configDir.exists()) configDir.deleteRecursively()
            System.clearProperty("intercept.wave.version")
        } finally {
            super.tearDown()
        }
    }

    fun `test migrate from v1 config file`() {
        // Prepare v1 config (no version/proxyGroups, only MockConfig fields)
        val v1 = """
            {
              "port": 18888,
              "interceptPrefix": "/api",
              "baseUrl": "http://localhost:18080",
              "stripPrefix": true,
              "globalCookie": "",
              "mockApis": [ { "path": "/user", "mockData": "{}" } ]
            }
        """.trimIndent()

        val configFile = File(configDir, "config.json")
        configFile.writeText(v1)

        // Instantiating service triggers load + migration
        val service = ConfigService(project)
        val root = service.getRootConfig()

        // Migration creates RootConfig with one proxy group and one HTTP route
        assertTrue(root.proxyGroups.isNotEmpty())
        assertEquals("4.0", root.version)
        val group = root.proxyGroups.first()
        assertEquals(18888, group.port)
        assertTrue(group.stripPrefix)
        assertEquals(1, group.routes.size)
        assertEquals("API", group.routes[0].name)
        assertEquals("/api", group.routes[0].pathPrefix)
        assertEquals("http://localhost:18080", group.routes[0].targetBaseUrl)
        assertTrue(group.routes[0].stripPrefix)
        assertTrue(group.routes[0].enableMock)
        assertEquals(1, group.routes[0].mockApis.size)

        // Backup should exist
        val backup = File(configDir, "config.json.backup")
        assertTrue(backup.exists())
    }

    fun `test load existing root_config_without_routes_adds_default_route`() {
        val v2WithoutRoutes = """
            {
              "version": "3.0",
              "proxyGroups": [
                {
                  "id": "http-1",
                  "name": "Legacy HTTP",
                  "protocol": "HTTP",
                  "port": 18889,
                  "interceptPrefix": "/api",
                  "baseUrl": "http://localhost:18081",
                  "stripPrefix": false,
                  "globalCookie": "",
                  "enabled": true,
                  "mockApis": [ { "path": "/api/user", "mockData": "{}" } ]
                }
              ]
            }
        """.trimIndent()

        File(configDir, "config.json").writeText(v2WithoutRoutes)

        val root = ConfigService(project).getRootConfig()
        assertEquals("4.0", root.version)
        val group = root.proxyGroups.single()
        assertEquals(1, group.routes.size)
        assertEquals("/api", group.routes[0].pathPrefix)
        assertEquals("http://localhost:18081", group.routes[0].targetBaseUrl)
        assertFalse(group.routes[0].stripPrefix)
        assertEquals("/api/user", group.routes[0].mockApis.single().path)
    }

    fun `test ensureVersionMajorMinor normalizes to major_minor`() {
        // Prepare minimal v2 config with a different version
        val service = ConfigService(project)
        val root = service.getRootConfig()
        root.version = "1.2"

        // Set system property for version fallback
        System.setProperty("intercept.wave.version", "3.4.5")
        service.saveRootConfig(root)

        // Reload through another instance to verify persisted version
        val reloaded = ConfigService(project).getRootConfig()
        // The version should be normalized to major.minor (either plugin's or system property)
        assertTrue(reloaded.version.matches(Regex("\\d+\\.\\d+")))
        // Ensure it has changed from the original value
        assertTrue(reloaded.version != "1.2")
    }

    fun `test load version 2 root_config_rolls through to 4_0`() {
        val v2Root = """
            {
              "version": "2.0",
              "proxyGroups": [
                {
                  "id": "http-2",
                  "name": "Legacy V2 HTTP",
                  "protocol": "HTTP",
                  "port": 18890,
                  "interceptPrefix": "/api",
                  "baseUrl": "http://localhost:18082",
                  "stripPrefix": true,
                  "globalCookie": "",
                  "enabled": true,
                  "mockApis": [ { "path": "/user", "mockData": "{}" } ]
                }
              ]
            }
        """.trimIndent()

        File(configDir, "config.json").writeText(v2Root)

        val root = ConfigService(project).getRootConfig()
        assertEquals("4.0", root.version)
        val group = root.proxyGroups.single()
        assertEquals(1, group.routes.size)
        assertEquals("/api", group.routes.single().pathPrefix)
        assertEquals("http://localhost:18082", group.routes.single().targetBaseUrl)
        assertEquals("/user", group.routes.single().mockApis.single().path)
    }

    fun `test load version 3 legacy group_with_blank_prefix_becomes_root_route`() {
        val v3Root = """
            {
              "version": "3.0",
              "proxyGroups": [
                {
                  "id": "http-root",
                  "name": "Legacy Root HTTP",
                  "protocol": "HTTP",
                  "port": 18891,
                  "interceptPrefix": "",
                  "baseUrl": "http://localhost:18083",
                  "stripPrefix": true,
                  "globalCookie": "",
                  "enabled": true,
                  "mockApis": []
                }
              ]
            }
        """.trimIndent()

        File(configDir, "config.json").writeText(v3Root)

        val root = ConfigService(project).getRootConfig()
        assertEquals("4.0", root.version)
        val route = root.proxyGroups.single().routes.single()
        assertEquals("/", route.pathPrefix)
        assertEquals("http://localhost:18083", route.targetBaseUrl)
        assertTrue(route.stripPrefix)
    }

    fun `test load version 3 malformed legacy group_falls_back_to_default_http_route`() {
        val malformedLegacy = """
            {
              "version": "3.0",
              "proxyGroups": [
                {
                  "id": "http-bad",
                  "name": "Broken Legacy HTTP",
                  "protocol": "HTTP",
                  "port": 18892,
                  "interceptPrefix": 123,
                  "baseUrl": false,
                  "stripPrefix": "oops",
                  "enabled": true,
                  "mockApis": []
                }
              ]
            }
        """.trimIndent()

        File(configDir, "config.json").writeText(malformedLegacy)

        val root = ConfigService(project).getRootConfig()
        assertEquals("4.0", root.version)
        val route = root.proxyGroups.single().routes.single()
        assertEquals("/api", route.pathPrefix)
        assertEquals("http://localhost:8080", route.targetBaseUrl)
        assertTrue(route.stripPrefix)
    }
}
