package org.zhongmiao.interceptwave.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun selectHttpRoute_respects_path_segment_boundaries() {
        val config = ProxyConfig(
            routes = mutableListOf(
                HttpRoute(name = "fallback", pathPrefix = "/", targetBaseUrl = "http://localhost:4001", stripPrefix = false),
                HttpRoute(name = "api", pathPrefix = "/api", targetBaseUrl = "http://localhost:4002", stripPrefix = true)
            )
        )

        assertEquals("api", PathUtil.selectHttpRoute(config, "/api")!!.name)
        assertEquals("api", PathUtil.selectHttpRoute(config, "/api/users")!!.name)
        assertEquals("fallback", PathUtil.selectHttpRoute(config, "/apiary")!!.name)
    }

    @Test
    fun selectHttpRoute_prefers_earlier_route_when_prefix_length_is_equal() {
        val config = ProxyConfig(
            routes = mutableListOf(
                HttpRoute(name = "first", pathPrefix = "/api", targetBaseUrl = "http://localhost:4001", stripPrefix = true),
                HttpRoute(name = "second", pathPrefix = "/api/", targetBaseUrl = "http://localhost:4002", stripPrefix = true)
            )
        )

        val route = PathUtil.selectHttpRoute(config, "/api/users")
        assertNotNull(route)
        assertEquals("first", route!!.name)
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
    fun computeHttpMatchPath_and_forwardPath_apply_rewrite_after_strip_prefix() {
        val route = HttpRoute(
            pathPrefix = "/backend",
            targetBaseUrl = "http://localhost:4002",
            stripPrefix = true,
            rewriteTargetPath = "/v1"
        )

        assertEquals("/v1/users", PathUtil.computeHttpMatchPath(route, "/backend/users"))
        assertEquals("/v1/users", PathUtil.computeHttpForwardPath(route, "/backend/users"))
        assertEquals("/v1", PathUtil.computeHttpMatchPath(route, "/backend"))
        assertEquals("/v1", PathUtil.computeHttpForwardPath(route, "/backend/"))
    }

    @Test
    fun computeHttpPath_rewrite_handles_root_blank_and_relative_values() {
        val rootRewrite = HttpRoute(
            pathPrefix = "/api",
            targetBaseUrl = "http://localhost:4002",
            stripPrefix = true,
            rewriteTargetPath = "/"
        )
        assertEquals("/users", PathUtil.computeHttpForwardPath(rootRewrite, "/api/users"))
        assertEquals("/", PathUtil.computeHttpForwardPath(rootRewrite, "/api"))

        val blankRewrite = HttpRoute(
            pathPrefix = "/api",
            targetBaseUrl = "http://localhost:4002",
            stripPrefix = true,
            rewriteTargetPath = "   "
        )
        assertEquals("/users", PathUtil.computeHttpForwardPath(blankRewrite, "/api/users"))

        val relativeRewrite = HttpRoute(
            pathPrefix = "/backend",
            targetBaseUrl = "http://localhost:4002",
            stripPrefix = true,
            rewriteTargetPath = "v2/"
        )
        assertEquals("/v2/users", PathUtil.computeHttpForwardPath(relativeRewrite, "/backend/users"))
    }

    @Test
    fun computeHttpPath_rewrite_uses_unstripped_path_when_strip_prefix_is_disabled() {
        val route = HttpRoute(
            pathPrefix = "/backend",
            targetBaseUrl = "http://localhost:4002",
            stripPrefix = false,
            rewriteTargetPath = "/v1"
        )

        assertEquals("/v1/backend/users", PathUtil.computeHttpForwardPath(route, "/backend/users"))
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

    @Test
    fun selectHttpRoute_returns_null_when_multiple_routes_do_not_match() {
        val config = ProxyConfig(
            routes = mutableListOf(
                HttpRoute(name = "api", pathPrefix = "/api", targetBaseUrl = "http://localhost:4002", stripPrefix = true),
                HttpRoute(name = "admin", pathPrefix = "/admin", targetBaseUrl = "http://localhost:4003", stripPrefix = false)
            )
        )

        assertNull(PathUtil.selectHttpRoute(config, "/health"))
    }

    @Test
    fun computeWsMatchPath_handles_prefix_strip_and_passthrough() {
        val config = ProxyConfig(wsInterceptPrefix = "/ws", stripPrefix = true)
        assertEquals("/chat", PathUtil.computeWsMatchPath(config, "/ws/chat"))
        assertEquals("/health", PathUtil.computeWsMatchPath(config, "/health"))
    }

    @Test
    fun computePathHelpers_cover_blank_prefix_empty_path_and_ws_passthrough() {
        val blankRoute = HttpRoute(pathPrefix = "", targetBaseUrl = "http://localhost:4002", stripPrefix = true)
        assertEquals("/", PathUtil.computeHttpMatchPath(blankRoute, ""))
        assertEquals("/", PathUtil.computeHttpForwardPath(blankRoute, ""))

        val noStripWs = ProxyConfig(wsInterceptPrefix = "/ws", stripPrefix = false)
        assertEquals("/ws/chat", PathUtil.computeWsMatchPath(noStripWs, "/ws/chat"))

        val emptyWsPrefix = ProxyConfig(wsInterceptPrefix = "", stripPrefix = true)
        assertEquals("/health", PathUtil.computeWsMatchPath(emptyWsPrefix, "/health"))
    }

    @Test
    fun computeHttpForwardPath_keeps_original_path_when_strip_prefix_is_disabled() {
        val route = HttpRoute(pathPrefix = "/api/", targetBaseUrl = "http://localhost:4002", stripPrefix = false)
        assertEquals("/api/users", PathUtil.computeHttpForwardPath(route, "/api/users"))
        assertEquals("/", PathUtil.computeHttpForwardPath(route, ""))
    }

    @Test
    fun selectHttpRoute_returns_null_when_no_routes_are_configured() {
        val config = ProxyConfig(routes = mutableListOf())
        assertNull(PathUtil.selectHttpRoute(config, "/api/users"))
    }
}
