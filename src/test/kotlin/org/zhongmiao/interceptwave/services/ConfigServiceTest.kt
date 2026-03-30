package org.zhongmiao.interceptwave.services

import org.junit.Assert.*
import org.junit.Test
import com.intellij.openapi.project.Project
import org.zhongmiao.interceptwave.model.HttpRoute
import org.zhongmiao.interceptwave.model.ProxyConfig
import org.zhongmiao.interceptwave.model.RootConfig
import java.io.File
import java.lang.reflect.Proxy
import java.nio.file.Files

class ConfigServiceTest {

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
        val types = args.map {
            when (it) {
                null -> Any::class.java
                is String -> String::class.java
                else -> it.javaClass
            }
        }.toTypedArray()
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
}
