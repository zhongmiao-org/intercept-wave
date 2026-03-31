package org.zhongmiao.interceptwave.util

import org.junit.Assert.assertTrue
import org.junit.Test
import org.zhongmiao.interceptwave.model.HttpRoute
import org.zhongmiao.interceptwave.model.MockApiConfig
import org.zhongmiao.interceptwave.model.ProxyConfig

class HttpWelcomeUtilTest {

    @Test
    fun buildWelcomeJson_includes_routes_examples_and_strip_prefix_urls() {
        val config = ProxyConfig(
            name = "Welcome",
            port = 18888,
            routes = mutableListOf(
                HttpRoute(
                    name = "API",
                    pathPrefix = "/api/",
                    targetBaseUrl = "http://localhost:4002",
                    stripPrefix = true,
                    enableMock = true,
                    mockApis = mutableListOf(
                        MockApiConfig(path = "/user", method = "GET", enabled = true, mockData = "{}")
                    )
                ),
                HttpRoute(
                    name = "Fallback",
                    pathPrefix = "/",
                    targetBaseUrl = "http://localhost:4001",
                    stripPrefix = false,
                    enableMock = true,
                    mockApis = mutableListOf(
                        MockApiConfig(path = "home", method = "POST", enabled = true, mockData = "{}")
                    )
                )
            )
        )

        val json = HttpWelcomeUtil.buildWelcomeJson(config)
        assertTrue(json.contains("\"routes\": 2"))
        assertTrue(json.contains("\"pathPrefix\": \"/api/\""))
        assertTrue(json.contains("\"pathPrefix\": \"/\""))
        assertTrue(json.contains("\"url\": \"http://localhost:18888/api/user\""))
        assertTrue(json.contains("\"url\": \"http://localhost:18888/home\""))
        assertTrue(json.contains("\"route\": \"Fallback\""))
    }

    @Test
    fun buildWelcomeJson_counts_only_enabled_examples_but_all_mock_apis() {
        val config = ProxyConfig(
            name = "Welcome Counts",
            port = 19999,
            routes = mutableListOf(
                HttpRoute(
                    name = "API",
                    pathPrefix = "/api",
                    targetBaseUrl = "http://localhost:4002",
                    stripPrefix = true,
                    enableMock = true,
                    mockApis = mutableListOf(
                        MockApiConfig(path = "/enabled", method = "GET", enabled = true, mockData = "{}"),
                        MockApiConfig(path = "/disabled", method = "GET", enabled = false, mockData = "{}")
                    )
                )
            )
        )

        val json = HttpWelcomeUtil.buildWelcomeJson(config)
        assertTrue(json.contains("\"total\": 2"))
        assertTrue(json.contains("\"enabled\": 1"))
        assertTrue(json.contains("/enabled"))
        assertTrue(!json.contains("/disabled\", \"method\""))
    }

    @Test
    fun buildWelcomeJson_handles_empty_routes_and_root_prefix_examples() {
        val emptyConfig = ProxyConfig(name = "Empty", port = 17777, routes = mutableListOf())
        val emptyJson = HttpWelcomeUtil.buildWelcomeJson(emptyConfig)
        assertTrue(emptyJson.contains("\"routes\": 0"))
        assertTrue(emptyJson.contains("\"total\": 0"))
        assertTrue(emptyJson.contains("\"enabled\": 0"))

        val rootRouteConfig = ProxyConfig(
            name = "Root Route",
            port = 18889,
            routes = mutableListOf(
                HttpRoute(
                    name = "Root",
                    pathPrefix = "/",
                    targetBaseUrl = "http://localhost:4001",
                    stripPrefix = true,
                    enableMock = true,
                    mockApis = mutableListOf(
                        MockApiConfig(path = "dashboard", method = "GET", enabled = true, mockData = "{}")
                    )
                ),
                HttpRoute(
                    name = "Passthrough",
                    pathPrefix = "/full",
                    targetBaseUrl = "http://localhost:4002",
                    stripPrefix = false,
                    enableMock = true,
                    mockApis = mutableListOf(
                        MockApiConfig(path = "/full/path", method = "GET", enabled = true, mockData = "{}")
                    )
                )
            )
        )

        val json = HttpWelcomeUtil.buildWelcomeJson(rootRouteConfig)
        assertTrue(json.contains("\"url\": \"http://localhost:18889/dashboard\""))
        assertTrue(json.contains("\"url\": \"http://localhost:18889/full/path\""))
    }
}
