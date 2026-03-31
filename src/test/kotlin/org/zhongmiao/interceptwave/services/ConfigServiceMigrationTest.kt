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

    fun `test load version 3_1 multi_group_config_preserves_prefix_and_base_url`() {
        val v31Root = """
            {
              "version": "3.1",
              "proxyGroups": [
                {
                  "id": "user-service",
                  "name": "User Service",
                  "protocol": "HTTP",
                  "port": 8888,
                  "interceptPrefix": "/api",
                  "baseUrl": "http://localhost:9000",
                  "stripPrefix": true,
                  "globalCookie": "",
                  "enabled": true,
                  "mockApis": []
                },
                {
                  "id": "order-service",
                  "name": "Order Service",
                  "protocol": "HTTP",
                  "port": 8889,
                  "interceptPrefix": "/order-api",
                  "baseUrl": "http://localhost:9001",
                  "stripPrefix": true,
                  "globalCookie": "",
                  "enabled": true,
                  "mockApis": []
                },
                {
                  "id": "payment-service",
                  "name": "Payment Service",
                  "protocol": "HTTP",
                  "port": 8890,
                  "interceptPrefix": "/pay-api",
                  "baseUrl": "http://localhost:9002",
                  "stripPrefix": true,
                  "globalCookie": "",
                  "enabled": true,
                  "mockApis": []
                }
              ]
            }
        """.trimIndent()

        File(configDir, "config.json").writeText(v31Root)

        val root = ConfigService(project).getRootConfig()
        assertEquals("4.0", root.version)
        assertEquals(3, root.proxyGroups.size)

        val user = root.proxyGroups[0].routes.single()
        assertEquals("/api", user.pathPrefix)
        assertEquals("http://localhost:9000", user.targetBaseUrl)

        val order = root.proxyGroups[1].routes.single()
        assertEquals("/order-api", order.pathPrefix)
        assertEquals("http://localhost:9001", order.targetBaseUrl)

        val payment = root.proxyGroups[2].routes.single()
        assertEquals("/pay-api", payment.pathPrefix)
        assertEquals("http://localhost:9002", payment.targetBaseUrl)
    }

    fun `test load version 2 config_preserves_mock_data_while_migrating_to_routes`() {
        val v2Root = """
            {
              "version": "2.0",
              "proxyGroups": [
                {
                  "id": "business-api",
                  "name": "业务接口",
                  "protocol": "HTTP",
                  "port": 8888,
                  "interceptPrefix": "/api",
                  "baseUrl": "http://192.168.180.135:30332",
                  "stripPrefix": true,
                  "globalCookie": "",
                  "enabled": true,
                  "mockApis": [
                    {
                      "path": "/product/getClassList",
                      "enabled": true,
                      "mockData": "{\n  \"list\": [{\"id\": 6, \"classCode\": \"60\"}],\n  \"count\": 1,\n  \"pageNum\": 1,\n  \"pageSize\": 25\n}",
                      "method": "GET",
                      "statusCode": 200,
                      "useCookie": false,
                      "delay": 0
                    }
                  ]
                },
                {
                  "id": "auth-api",
                  "name": "认证服务",
                  "protocol": "HTTP",
                  "port": 8889,
                  "interceptPrefix": "/api",
                  "baseUrl": "http://192.168.180.135:30334",
                  "stripPrefix": true,
                  "globalCookie": "",
                  "enabled": true,
                  "mockApis": []
                }
              ]
            }
        """.trimIndent()

        File(configDir, "config.json").writeText(v2Root)

        val root = ConfigService(project).getRootConfig()
        assertEquals("4.0", root.version)
        assertEquals(2, root.proxyGroups.size)

        val businessGroup = root.proxyGroups[0]
        assertEquals("http://192.168.180.135:30332", businessGroup.routes.single().targetBaseUrl)
        assertEquals("/api", businessGroup.routes.single().pathPrefix)
        assertEquals(1, businessGroup.routes.single().mockApis.size)
        assertEquals(
            """{"list":[{"id":6,"classCode":"60"}],"count":1,"pageNum":1,"pageSize":25}""",
            businessGroup.routes.single().mockApis.single().mockData
        )

        val authGroup = root.proxyGroups[1]
        assertEquals("http://192.168.180.135:30334", authGroup.routes.single().targetBaseUrl)
        assertEquals("/api", authGroup.routes.single().pathPrefix)
        assertTrue(authGroup.routes.single().mockApis.isEmpty())
    }

    fun `test migrated 3_1 config_is_rewritten_as_routes_only_schema`() {
        val v31Root = """
            {
              "version": "3.1",
              "proxyGroups": [
                {
                  "id": "user-service",
                  "name": "User Service",
                  "protocol": "HTTP",
                  "port": 8888,
                  "interceptPrefix": "/api",
                  "baseUrl": "http://localhost:9000",
                  "stripPrefix": true,
                  "globalCookie": "",
                  "enabled": true,
                  "mockApis": []
                }
              ]
            }
        """.trimIndent()

        val configFile = File(configDir, "config.json")
        configFile.writeText(v31Root)

        ConfigService(project).getRootConfig()

        val saved = configFile.readText()
        assertTrue(saved.contains(""""version": "4.0""""))
        assertTrue(saved.contains(""""routes": ["""))
        assertTrue(saved.contains(""""pathPrefix": "/api""""))
        assertTrue(saved.contains(""""targetBaseUrl": "http://localhost:9000""""))
        assertFalse(saved.contains(""""interceptPrefix""""))
        assertFalse(saved.contains(""""baseUrl""""))
        assertFalse(saved.contains(""""mockApis": [],\n            "wsBaseUrl""""))
    }

    fun `test load version 2 config_preserves_multiple_mock_apis_fields_and_order`() {
        val v2Root = """
            {
              "version": "2.0",
              "proxyGroups": [
                {
                  "id": "business-api",
                  "name": "业务接口",
                  "protocol": "HTTP",
                  "port": 8888,
                  "interceptPrefix": "/api",
                  "baseUrl": "http://192.168.180.135:30332",
                  "stripPrefix": true,
                  "globalCookie": "",
                  "enabled": true,
                  "mockApis": [
                    {
                      "path": "/product/getClassList",
                      "enabled": true,
                      "mockData": "{\n  \"list\": [{\"id\": 6}],\n  \"count\": 1\n}",
                      "method": "GET",
                      "statusCode": 200,
                      "useCookie": false,
                      "delay": 0
                    },
                    {
                      "path": "/product/getListProductsByDealer",
                      "enabled": true,
                      "mockData": "{\n  \"pageNum\": 1,\n  \"pageSize\": 30,\n  \"dealerId\": 1690873831842000\n}",
                      "method": "POST",
                      "statusCode": 200,
                      "useCookie": false,
                      "delay": 0
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        File(configDir, "config.json").writeText(v2Root)

        val route = ConfigService(project).getRootConfig().proxyGroups.single().routes.single()
        assertEquals(2, route.mockApis.size)

        val first = route.mockApis[0]
        assertEquals("/product/getClassList", first.path)
        assertEquals("GET", first.method)
        assertEquals(200, first.statusCode)
        assertEquals("""{"list":[{"id":6}],"count":1}""", first.mockData)

        val second = route.mockApis[1]
        assertEquals("/product/getListProductsByDealer", second.path)
        assertEquals("POST", second.method)
        assertEquals(200, second.statusCode)
        assertEquals("""{"pageNum":1,"pageSize":30,"dealerId":1690873831842000}""", second.mockData)
    }
}
