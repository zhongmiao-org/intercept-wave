package org.zhongmiao.interceptwave.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.zhongmiao.interceptwave.model.HttpRoute
import org.zhongmiao.interceptwave.model.ProxyConfig

class PathUtilRouteTest {

    @Test
    fun selectHttpRoute_prefers_longest_prefix_then_order() {
        val config = ProxyConfig(
            routes = mutableListOf(
                HttpRoute(name = "fallback", pathPrefix = "/", targetBaseUrl = "http://localhost:4001", stripPrefix = false),
                HttpRoute(name = "api", pathPrefix = "/api", targetBaseUrl = "http://localhost:4002", stripPrefix = true),
                HttpRoute(name = "admin", pathPrefix = "/api/admin", targetBaseUrl = "http://localhost:4003", stripPrefix = true)
            )
        )

        val route = PathUtil.selectHttpRoute(config, "/api/admin/users")
        assertNotNull(route)
        assertEquals("admin", route!!.name)
    }

    @Test
    fun computeHttpMatchPath_and_forwardPath_respect_route_strip_prefix() {
        val route = HttpRoute(
            pathPrefix = "/api",
            targetBaseUrl = "http://localhost:4002",
            stripPrefix = true
        )

        assertEquals("/user", PathUtil.computeHttpMatchPath(route, "/api/user"))
        assertEquals("/user", PathUtil.computeHttpForwardPath(route, "/api/user"))
        assertEquals("/", PathUtil.computeHttpMatchPath(route, "/api"))
    }

    @Test
    fun selectHttpRoute_falls_back_to_single_route_for_legacy_forwarding() {
        val config = ProxyConfig(
            routes = mutableListOf(
                HttpRoute(name = "legacy", pathPrefix = "/api", targetBaseUrl = "http://localhost:4002", stripPrefix = true)
            )
        )

        val route = PathUtil.selectHttpRoute(config, "/health")
        assertNotNull(route)
        assertEquals("legacy", route!!.name)
        assertEquals("/health", PathUtil.computeHttpForwardPath(route, "/health"))
    }
}
