package org.zhongmiao.interceptwave.services

import org.junit.Assert.*
import org.junit.Test
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.zhongmiao.interceptwave.model.HttpRoute
import org.zhongmiao.interceptwave.model.MockApiConfig
import org.zhongmiao.interceptwave.model.ProxyConfig
import org.zhongmiao.interceptwave.model.RootConfig
import java.io.File
import java.lang.reflect.Proxy
import java.nio.file.Files

class ConfigServiceTest {

    private val testJson = Json { ignoreUnknownKeys = true }

    private fun fakeProject(base: File): Project {
        val cls = Project::class.java
        return Proxy.newProxyInstance(
            cls.classLoader,
            arrayOf(cls)
        ) { _, method, _ ->
            when (method.name) {
                "getBasePath" -> base.absolutePath
                "isDisposed" -> false
                "toString" -> "FakeProject(${base.absolutePath})"
                else -> null
            }
        } as Project
    }

    private fun invokePrivate(service: ConfigService, name: String, vararg args: Any?): Any? {
        val method = ConfigService::class.java.declaredMethods.first {
            it.name == name && it.parameterTypes.size == args.size
        }
        method.isAccessible = true
        return method.invoke(service, *args)
    }

    @Test
    fun defaultConfigCreatedAndLoaded() {
        val dir = Files.createTempDirectory("iw-conf").toFile()
        val svc = ConfigService(fakeProject(dir))
        val root = svc.getRootConfig()
        assertNotNull(root)
        assertTrue(File(dir, ".intercept-wave/config.json").exists())
        assertEquals(1, root.proxyGroups.size)
        assertEquals(1, svc.getEnabledProxyGroups().size)
        val pg = svc.getAllProxyGroups().first()
        assertNotNull(svc.getProxyGroup(pg.id))
    }

    @Test
    fun saveRootConfig_appliesMajorMinorVersion() {
        val dir = Files.createTempDirectory("iw-conf2").toFile()
        // Provide plugin version via system property fallback
        System.setProperty("intercept.wave.version", "3.7.9")
        val svc = ConfigService(fakeProject(dir))
        val custom = RootConfig(version = "1.0", proxyGroups = mutableListOf())
        svc.saveRootConfig(custom)
        val loaded = svc.getRootConfig()
        assertEquals("3.7", loaded.version)
    }

    @Test
    fun enabledGroupsFilter() {
        val dir = Files.createTempDirectory("iw-conf3").toFile()
        val svc = ConfigService(fakeProject(dir))
        val a = ProxyConfig(id = "A", name = "A", enabled = true)
        val b = ProxyConfig(id = "B", name = "B", enabled = false)
        svc.saveRootConfig(RootConfig(proxyGroups = mutableListOf(a, b)))
        val enabled = svc.getEnabledProxyGroups()
        assertEquals(1, enabled.size)
        assertEquals("A", enabled.first().id)
    }

    @Test
    fun ensureConfigFile_creates_default_config_when_missing() {
        val dir = Files.createTempDirectory("iw-conf-ensure").toFile()
        val svc = ConfigService(fakeProject(dir))
        val configFile = File(dir, ".intercept-wave/config.json")
        assertTrue(configFile.delete())

        val ensured = svc.ensureConfigFile()

        assertEquals(configFile.absolutePath, ensured.absolutePath)
        assertTrue(ensured.exists())
        assertTrue(svc.getRootConfig().proxyGroups.isNotEmpty())
    }

    @Test
    fun reloadFromDisk_updates_in_memory_root_config() {
        val dir = Files.createTempDirectory("iw-conf-reload").toFile()
        val svc = ConfigService(fakeProject(dir))
        val configFile = svc.ensureConfigFile()
        configFile.writeText(
            """
            {
              "version": "4.0",
              "proxyGroups": [
                {
                  "id": "reloaded-group",
                  "name": "Reloaded Group",
                  "protocol": "HTTP",
                  "port": 18888,
                  "enabled": true,
                  "routes": [
                    {
                      "name": "API",
                      "pathPrefix": "/api",
                      "targetBaseUrl": "http://localhost:4002",
                      "stripPrefix": true,
                      "enableMock": true,
                      "mockApis": []
                    }
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        val reloaded = svc.reloadFromDisk()

        assertEquals("reloaded-group", reloaded.proxyGroups.single().id)
        assertEquals("Reloaded Group", svc.getRootConfig().proxyGroups.single().name)
    }

    @Test
    fun reloadFromDisk_keeps_previous_config_when_new_file_is_invalid() {
        val dir = Files.createTempDirectory("iw-conf-reload-invalid").toFile()
        val svc = ConfigService(fakeProject(dir))
        val previous = svc.getRootConfig()
        svc.ensureConfigFile().writeText("{bad json")

        runCatching { svc.reloadFromDisk() }

        assertEquals(previous.proxyGroups.first().id, svc.getRootConfig().proxyGroups.first().id)
    }

    @Test
    fun defaultProxyConfig_contains_default_route() {
        val dir = Files.createTempDirectory("iw-conf4").toFile()
        val svc = ConfigService(fakeProject(dir))
        val cfg = svc.createDefaultProxyConfig(2, "HTTP")
        assertEquals(1, cfg.routes.size)
        assertEquals("API", cfg.routes.first().name)
        assertEquals("/api", cfg.routes.first().pathPrefix)
        assertEquals("http://localhost:8080", cfg.routes.first().targetBaseUrl)
    }

    @Test
    fun malformedConfigFallsBackToDefaultConfig() {
        val dir = Files.createTempDirectory("iw-conf5").toFile()
        val configDir = File(dir, ".intercept-wave").apply { mkdirs() }
        File(configDir, "config.json").writeText("{bad json")

        val svc = ConfigService(fakeProject(dir))
        val root = svc.getRootConfig()
        assertEquals(1, root.proxyGroups.size)
        assertTrue(root.proxyGroups.first().routes.isNotEmpty())
    }

    @Test
    fun saveRootConfig_preserves_explicit_routes() {
        val dir = Files.createTempDirectory("iw-conf6").toFile()
        val svc = ConfigService(fakeProject(dir))
        val cfg = ProxyConfig(
            id = "routes",
            routes = mutableListOf(
                HttpRoute(name = "frontend", pathPrefix = "/", targetBaseUrl = "http://localhost:4001", stripPrefix = false, enableMock = false),
                HttpRoute(name = "api", pathPrefix = "/api", targetBaseUrl = "http://localhost:4002", stripPrefix = true, enableMock = true)
            )
        )

        svc.saveRootConfig(RootConfig(proxyGroups = mutableListOf(cfg)))
        val saved = svc.getRootConfig().proxyGroups.single()
        assertEquals(2, saved.routes.size)
        assertEquals("/", saved.routes.first().pathPrefix)
        assertEquals("/api", saved.routes.last().pathPrefix)
    }

    @Test
    fun loadRootConfig_normalizes_mock_and_ws_json_and_reloads_saved_schema() {
        val dir = Files.createTempDirectory("iw-conf7").toFile()
        val configDir = File(dir, ".intercept-wave").apply { mkdirs() }
        File(configDir, "config.json").writeText(
            """
            {
              "version": "4.0",
              "proxyGroups": [
                {
                  "id": "g1",
                  "name": "Normalize",
                  "protocol": "HTTP",
                  "port": 18888,
                  "routes": [
                    {
                      "name": "API",
                      "pathPrefix": "/api",
                      "targetBaseUrl": "http://localhost:4002",
                      "stripPrefix": true,
                      "enableMock": true,
                      "mockApis": [
                        { "path": "/user", "mockData": "{foo:'bar',}", "method": "GET" }
                      ]
                    }
                  ],
                  "wsPushRules": [
                    {
                      "path": "/ws/**",
                      "message": "{foo:'bar',}",
                      "timeline": [
                        { "atMs": 10, "message": "{bar:'baz',}" }
                      ]
                    }
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        val svc = ConfigService(fakeProject(dir))
        val root = svc.getRootConfig()
        val group = root.proxyGroups.single()
        assertEquals("""{"foo":"bar"}""", group.routes.single().mockApis.single().mockData)
        assertEquals("""{"foo":"bar"}""", group.wsPushRules.single().message)
        assertEquals("""{"bar":"baz"}""", group.wsPushRules.single().timeline.single().message)

        val saved = File(configDir, "config.json").readText()
        assertTrue(saved.contains("mockData"))
        assertTrue(saved.contains("\\\"foo\\\":\\\"bar\\\""))
        assertTrue(saved.contains("\\\"bar\\\":\\\"baz\\\""))
        assertTrue(saved.contains("wsPushRules"))
    }

    @Test
    fun loadRootConfig_with_unsupported_version_falls_back_to_default_config() {
        val dir = Files.createTempDirectory("iw-conf8").toFile()
        val configDir = File(dir, ".intercept-wave").apply { mkdirs() }
        File(configDir, "config.json").writeText(
            """
            {
              "version": "9.9",
              "proxyGroups": []
            }
            """.trimIndent()
        )

        val svc = ConfigService(fakeProject(dir))
        val root = svc.getRootConfig()
        assertEquals("4.0", root.version)
        assertNotNull(root.proxyGroups)
    }

    @Test
    fun loadRootConfig_with_unsupported_version_but_normalizable_keeps_existing_groups() {
        val dir = Files.createTempDirectory("iw-conf9").toFile()
        val configDir = File(dir, ".intercept-wave").apply { mkdirs() }
        System.setProperty("intercept.wave.version", "4.0.3")
        File(configDir, "config.json").writeText(
            """
            {
              "version": "9.9",
              "proxyGroups": [
                {
                  "id": "keep-me",
                  "name": "Keep Me",
                  "protocol": "HTTP",
                  "port": 19999,
                  "routes": [
                    {
                      "name": "API",
                      "pathPrefix": "/api",
                      "targetBaseUrl": "http://localhost:4002",
                      "stripPrefix": true,
                      "enableMock": true,
                      "mockApis": []
                    }
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        val svc = ConfigService(fakeProject(dir))
        val root = svc.getRootConfig()
        assertEquals("4.0", root.version)
        assertEquals(1, root.proxyGroups.size)
        assertEquals("keep-me", root.proxyGroups.single().id)
    }

    @Test
    fun currentMajorMinor_uses_system_property_major_minor() {
        val dir = Files.createTempDirectory("iw-conf-current-version").toFile()
        System.setProperty("intercept.wave.version", "8.9.7")
        val svc = ConfigService(fakeProject(dir))

        val actual = invokePrivate(svc, "currentMajorMinor", "1.0") as String
        assertEquals("8.9", actual)
    }

    @Test
    fun currentMajorMinor_falls_back_to_current_config_version_when_existing_is_not_major_minor() {
        val dir = Files.createTempDirectory("iw-conf-current-fallback").toFile()
        System.clearProperty("intercept.wave.version")
        val svc = ConfigService(fakeProject(dir))

        val actual = invokePrivate(svc, "currentMajorMinor", "9") as String
        assertEquals(ConfigService.CURRENT_CONFIG_VERSION, actual)
    }

    @Test
    fun loadRootConfig_keeps_existing_routes_when_legacy_fields_match() {
        val dir = Files.createTempDirectory("iw-conf10").toFile()
        val configDir = File(dir, ".intercept-wave").apply { mkdirs() }
        File(configDir, "config.json").writeText(
            """
            {
              "version": "3.0",
              "proxyGroups": [
                {
                  "id": "g1",
                  "name": "Legacy With Routes",
                  "protocol": "HTTP",
                  "port": 18888,
                  "interceptPrefix": "/api",
                  "baseUrl": "http://localhost:4002",
                  "stripPrefix": false,
                  "mockApis": [
                    { "path": "/user", "mockData": "{}" }
                  ],
                  "routes": [
                    {
                      "name": "Backend",
                      "pathPrefix": "/api",
                      "targetBaseUrl": "http://localhost:4002",
                      "stripPrefix": false,
                      "enableMock": true,
                      "mockApis": [
                        { "path": "/user", "mockData": "{}" }
                      ]
                    }
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        val svc = ConfigService(fakeProject(dir))
        val route = svc.getRootConfig().proxyGroups.single().routes.single()
        assertEquals("Backend", route.name)
        assertEquals("/api", route.pathPrefix)
        assertFalse(route.stripPrefix)
    }

    @Test
    fun loadRootConfig_replaces_placeholder_route_when_legacy_fields_differ() {
        val dir = Files.createTempDirectory("iw-conf12").toFile()
        val configDir = File(dir, ".intercept-wave").apply { mkdirs() }
        File(configDir, "config.json").writeText(
            """
            {
              "version": "3.0",
              "proxyGroups": [
                {
                  "id": "g2",
                  "name": "Legacy Placeholder",
                  "protocol": "HTTP",
                  "port": 18889,
                  "interceptPrefix": "/backend",
                  "baseUrl": "http://localhost:5000",
                  "stripPrefix": false,
                  "mockApis": [
                    { "path": "/alive", "mockData": "{}" }
                  ],
                  "routes": [
                    {
                      "name": "API",
                      "pathPrefix": "/api",
                      "targetBaseUrl": "http://localhost:8080",
                      "stripPrefix": true,
                      "enableMock": true,
                      "mockApis": []
                    }
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        val route = ConfigService(fakeProject(dir)).getRootConfig().proxyGroups.single().routes.single()
        assertEquals("/backend", route.pathPrefix)
        assertEquals("http://localhost:5000", route.targetBaseUrl)
        assertFalse(route.stripPrefix)
        assertEquals("/alive", route.mockApis.single().path)
    }

    @Test
    fun loadRootConfig_keeps_invalid_mock_and_ws_templates_as_is() {
        val dir = Files.createTempDirectory("iw-conf13").toFile()
        val configDir = File(dir, ".intercept-wave").apply { mkdirs() }
        File(configDir, "config.json").writeText(
            """
            {
              "version": "4.0",
              "proxyGroups": [
                {
                  "id": "g3",
                  "name": "Keep Text",
                  "protocol": "HTTP",
                  "port": 18890,
                  "routes": [
                    {
                      "name": "API",
                      "pathPrefix": "/api",
                      "targetBaseUrl": "http://localhost:4002",
                      "stripPrefix": true,
                      "enableMock": true,
                      "mockApis": [
                        { "path": "/user", "mockData": "not-json", "method": "GET" }
                      ]
                    }
                  ],
                  "wsPushRules": [
                    {
                      "path": "/ws/**",
                      "message": "plain-text",
                      "timeline": [
                        { "atMs": 10, "message": "still-text" }
                      ]
                    }
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        val group = ConfigService(fakeProject(dir)).getRootConfig().proxyGroups.single()
        assertEquals("not-json", group.routes.single().mockApis.single().mockData)
        assertEquals("plain-text", group.wsPushRules.single().message)
        assertEquals("still-text", group.wsPushRules.single().timeline.single().message)
    }

    @Test
    fun saveRootConfig_does_not_inject_http_routes_into_ws_groups() {
        val dir = Files.createTempDirectory("iw-conf11").toFile()
        val svc = ConfigService(fakeProject(dir))
        val wsGroup = ProxyConfig(
            id = "ws-only",
            protocol = "WS",
            routes = mutableListOf(),
            wsInterceptPrefix = "/ws",
            wsBaseUrl = "ws://localhost:9000"
        )

        svc.saveRootConfig(RootConfig(proxyGroups = mutableListOf(wsGroup)))
        val saved = svc.getRootConfig().proxyGroups.single()
        assertEquals("WS", saved.protocol)
        assertTrue(saved.routes.isEmpty())
    }

    @Test
    fun persistAndReloadIfNeeded_when_unchanged_returns_same_config_without_backup() {
        val dir = Files.createTempDirectory("iw-conf14").toFile()
        val svc = ConfigService(fakeProject(dir))
        val configDir = File(dir, ".intercept-wave")
        val configFile = File(configDir, "config.json")
        val backupFile = File(configDir, "config.json.backup")
        val before = configFile.readText()
        val root = svc.getRootConfig()

        val result = invokePrivate(svc, "persistAndReloadIfNeeded", false, root, false) as RootConfig

        assertSame(root, result)
        assertEquals(before, configFile.readText())
        assertFalse(backupFile.exists())
    }

    @Test
    fun defaultHttpRoute_uses_root_prefix_for_blank_legacy_prefix_and_copies_mock_list() {
        val dir = Files.createTempDirectory("iw-conf15").toFile()
        val svc = ConfigService(fakeProject(dir))
        val legacyClass = ConfigService::class.java.declaredClasses.first { it.simpleName == "LegacyHttpGroup" }
        val ctor = legacyClass.getDeclaredConstructor()
        ctor.isAccessible = true
        val legacy = ctor.newInstance()
        legacyClass.getDeclaredField("interceptPrefix").apply { isAccessible = true }.set(legacy, "")
        legacyClass.getDeclaredField("baseUrl").apply { isAccessible = true }.set(legacy, "http://localhost:5000")
        legacyClass.getDeclaredField("stripPrefix").apply { isAccessible = true }.set(legacy, false)
        val legacyApis = mutableListOf(MockApiConfig(path = "/alive", mockData = "{\"ok\":true}"))
        legacyClass.getDeclaredField("mockApis").apply { isAccessible = true }.set(legacy, legacyApis)

        val route = invokePrivate(svc, "defaultHttpRoute", legacy) as HttpRoute

        assertEquals("/", route.pathPrefix)
        assertEquals("http://localhost:5000", route.targetBaseUrl)
        assertFalse(route.stripPrefix)
        assertEquals(1, route.mockApis.size)
        assertNotSame(legacyApis, route.mockApis)
        legacyApis.first().path = "/changed"
        assertEquals("/alive", route.mockApis.first().path)
    }

    @Test
    fun shouldReplacePlaceholderRoute_handles_empty_routes_placeholder_and_custom_route() {
        val dir = Files.createTempDirectory("iw-conf16").toFile()
        val svc = ConfigService(fakeProject(dir))
        val legacyClass = ConfigService::class.java.declaredClasses.first { it.simpleName == "LegacyHttpGroup" }
        val ctor = legacyClass.getDeclaredConstructor()
        ctor.isAccessible = true
        val legacy = ctor.newInstance()
        legacyClass.getDeclaredField("baseUrl").apply { isAccessible = true }.set(legacy, "http://localhost:5000")

        val emptyGroup = ProxyConfig(routes = mutableListOf())
        assertEquals(true, invokePrivate(svc, "shouldReplacePlaceholderRoute", emptyGroup, legacy))

        val placeholder = ProxyConfig(
            routes = mutableListOf(
                HttpRoute(
                    name = "API",
                    pathPrefix = "/api",
                    targetBaseUrl = "http://localhost:8080",
                    stripPrefix = true,
                    enableMock = true,
                    mockApis = mutableListOf()
                )
            )
        )
        assertEquals(true, invokePrivate(svc, "shouldReplacePlaceholderRoute", placeholder, legacy))

        val custom = ProxyConfig(
            routes = mutableListOf(
                HttpRoute(
                    name = "Custom",
                    pathPrefix = "/custom",
                    targetBaseUrl = "http://localhost:5000",
                    stripPrefix = false,
                    enableMock = false
                )
            )
        )
        assertEquals(false, invokePrivate(svc, "shouldReplacePlaceholderRoute", custom, legacy))
    }

    @Test
    fun ensureHttpRoutes_adds_default_route_only_for_http_groups_without_routes() {
        val dir = Files.createTempDirectory("iw-conf17").toFile()
        val svc = ConfigService(fakeProject(dir))
        val root = RootConfig(
            proxyGroups = mutableListOf(
                ProxyConfig(id = "http-empty", protocol = "HTTP", routes = mutableListOf()),
                ProxyConfig(id = "ws-empty", protocol = "WS", routes = mutableListOf(), wsInterceptPrefix = "/ws", wsBaseUrl = "ws://localhost:9000")
            )
        )

        val result = invokePrivate(svc, "ensureHttpRoutes", root, null)
        val changed = result!!::class.java.getMethod("component1").invoke(result) as Boolean
        val normalized = result::class.java.getMethod("component2").invoke(result) as RootConfig

        assertTrue(changed)
        assertEquals(1, normalized.proxyGroups.first().routes.size)
        assertEquals("/api", normalized.proxyGroups.first().routes.single().pathPrefix)
        assertTrue(normalized.proxyGroups.last().routes.isEmpty())
    }

    @Test
    fun migrateToLatest_normalizes_unknown_version_when_major_minor_matches_current() {
        val dir = Files.createTempDirectory("iw-conf18").toFile()
        val svc = ConfigService(fakeProject(dir))
        System.setProperty("intercept.wave.version", "4.0.2")
        val root = RootConfig(
            version = "4.9.1",
            proxyGroups = mutableListOf(
                ProxyConfig(id = "keep", routes = mutableListOf(HttpRoute(pathPrefix = "/api", targetBaseUrl = "http://localhost:4002")))
            )
        )

        val result = invokePrivate(svc, "migrateToLatest", root, null)
        val changed = result!!::class.java.getMethod("component1").invoke(result) as Boolean
        val migrated = result::class.java.getMethod("component2").invoke(result) as RootConfig

        assertTrue(changed)
        assertEquals("4.0", migrated.version)
        assertEquals("keep", migrated.proxyGroups.single().id)
    }

    @Test
    fun ensureHttpRoutes_keeps_explicit_http_routes_without_legacy_data() {
        val dir = Files.createTempDirectory("iw-conf19").toFile()
        val svc = ConfigService(fakeProject(dir))
        val root = RootConfig(
            proxyGroups = mutableListOf(
                ProxyConfig(
                    id = "http-explicit",
                    protocol = "HTTP",
                    routes = mutableListOf(
                        HttpRoute(name = "Frontend", pathPrefix = "/", targetBaseUrl = "http://localhost:4001", stripPrefix = false, enableMock = false)
                    )
                )
            )
        )

        val result = invokePrivate(svc, "ensureHttpRoutes", root, null)
        val changed = result!!::class.java.getMethod("component1").invoke(result) as Boolean
        val normalized = result::class.java.getMethod("component2").invoke(result) as RootConfig

        assertFalse(changed)
        assertEquals("Frontend", normalized.proxyGroups.single().routes.single().name)
        assertEquals("/", normalized.proxyGroups.single().routes.single().pathPrefix)
    }

    @Test
    fun extractLegacyHttpGroup_handles_missing_out_of_range_and_invalid_payloads() {
        val dir = Files.createTempDirectory("iw-conf20").toFile()
        val svc = ConfigService(fakeProject(dir))

        assertNull(invokePrivate(svc, "extractLegacyHttpGroup", null, 0))

        val noLegacyRoot = testJson.parseToJsonElement(
            """
            {
              "proxyGroups": [
                {
                  "id": "g1",
                  "protocol": "HTTP",
                  "routes": []
                }
              ]
            }
            """.trimIndent()
        ).let { it as JsonObject }
        assertNull(invokePrivate(svc, "extractLegacyHttpGroup", noLegacyRoot, 0))
        assertNull(invokePrivate(svc, "extractLegacyHttpGroup", noLegacyRoot, 2))

        val invalidLegacyRoot = testJson.parseToJsonElement(
            """
            {
              "proxyGroups": [
                {
                  "interceptPrefix": 123,
                  "baseUrl": false
                }
              ]
            }
            """.trimIndent()
        ).let { it as JsonObject }
        assertNull(invokePrivate(svc, "extractLegacyHttpGroup", invalidLegacyRoot, 0))
    }

    @Test
    fun extractLegacyHttpGroup_decodes_legacy_fields_when_present() {
        val dir = Files.createTempDirectory("iw-conf21").toFile()
        val svc = ConfigService(fakeProject(dir))
        val rootJson = testJson.parseToJsonElement(
            """
            {
              "proxyGroups": [
                {
                  "interceptPrefix": "/legacy",
                  "baseUrl": "http://localhost:7777",
                  "stripPrefix": false,
                  "mockApis": [
                    { "path": "/ping", "mockData": "{}" }
                  ]
                }
              ]
            }
            """.trimIndent()
        ).let { it as JsonObject }

        val legacy = invokePrivate(svc, "extractLegacyHttpGroup", rootJson, 0)
        assertNotNull(legacy)
        val cls = legacy!!::class.java
        assertEquals("/legacy", cls.getDeclaredField("interceptPrefix").apply { isAccessible = true }.get(legacy))
        assertEquals("http://localhost:7777", cls.getDeclaredField("baseUrl").apply { isAccessible = true }.get(legacy))
    }

    @Test
    fun createDefaultProxyConfig_uses_generated_indexed_name_when_name_is_null() {
        val dir = Files.createTempDirectory("iw-conf22").toFile()
        val svc = ConfigService(fakeProject(dir))
        val cfg = svc.createDefaultProxyConfig(1, null)
        assertTrue(cfg.name.isNotBlank())
        assertEquals(8889, cfg.port)
    }

    @Test
    fun getProxyGroup_returns_null_for_unknown_id() {
        val dir = Files.createTempDirectory("iw-conf23").toFile()
        val svc = ConfigService(fakeProject(dir))
        assertNull(svc.getProxyGroup("missing"))
    }

}
