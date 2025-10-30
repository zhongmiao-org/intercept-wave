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

        // Migration creates v2 RootConfig with one proxy group
        assertTrue(root.proxyGroups.isNotEmpty())
        val group = root.proxyGroups.first()
        assertEquals(18888, group.port)
        assertEquals("/api", group.interceptPrefix)
        assertEquals("http://localhost:18080", group.baseUrl)
        assertTrue(group.stripPrefix)
        assertEquals(1, group.mockApis.size)

        // Backup should exist
        val backup = File(configDir, "config.json.backup")
        assertTrue(backup.exists())
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
}
