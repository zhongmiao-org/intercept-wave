package org.zhongmiao.interceptwave.util

import org.junit.Assert.*
import org.junit.Test
import org.zhongmiao.interceptwave.model.HttpRoute
import org.zhongmiao.interceptwave.model.MockApiConfig

class PathPatternUtilTest {

    @Test
    fun patternToRegex_singleAndDoubleStar() {
        val r1 = PathPatternUtil.patternToRegex("/api/*/detail").toPattern().toString()
        assertTrue(r1.startsWith("^") && r1.endsWith("$"))
        assertTrue(PathPatternUtil.pathPatternMatches("/api/*/detail", "/api/123/detail"))
        assertFalse(PathPatternUtil.pathPatternMatches("/api/*/detail", "/api/123/other"))

        val r2 = PathPatternUtil.patternToRegex("/files/**").toPattern().toString()
        assertTrue(r2.startsWith("^") && r2.endsWith("$"))
        assertTrue(PathPatternUtil.pathPatternMatches("/files/**", "/files/a/b/c.txt"))
        assertTrue(PathPatternUtil.pathPatternMatches("/files/**", "/files/one.txt"))
        assertFalse(PathPatternUtil.pathPatternMatches("/files/**", "/other/one.txt"))
    }

    @Test
    fun pathPattern_exactVsWildcard() {
        assertTrue(PathPatternUtil.pathPatternMatches("/a/b", "/a/b"))
        assertFalse(PathPatternUtil.pathPatternMatches("/a/b", "/a/b/c"))
        assertTrue(PathPatternUtil.pathPatternMatches("/a/*/c", "/a/b/c"))
        assertFalse(PathPatternUtil.pathPatternMatches("/a/*/c", "/a/b/c/d"))
    }

    @Test
    fun findMatchingMockApi_prioritizesSpecificity() {
        val route = HttpRoute(
            mockApis = mutableListOf(
                MockApiConfig(path = "/user/**", method = "ALL", mockData = "{}"),
                MockApiConfig(path = "/user/*/detail", method = "GET", mockData = "{}"),
                MockApiConfig(path = "/user/123/detail", method = "GET", mockData = "{}")
            )
        )

        val req = "/user/123/detail"
        val match = PathPatternUtil.findMatchingMockApiInRoute(req, "GET", route)
        assertNotNull(match)
        assertEquals("/user/123/detail", match!!.path)

        // Method specificity: if exact path with ALL and less specific method, prefer GET on wildcard
        val route2 = HttpRoute(
            mockApis = mutableListOf(
                MockApiConfig(path = "/hello/world", method = "ALL", mockData = "{}"),
                MockApiConfig(path = "/hello/*", method = "GET", mockData = "{}")
            )
        )
        val m2 = PathPatternUtil.findMatchingMockApiInRoute("/hello/world", "GET", route2)
        assertNotNull(m2)
        // exact match without wildcard wins over wildcard, even if method is ALL
        assertEquals("/hello/world", m2!!.path)
    }

    @Test
    fun findMatchingMockApiInRoute_returns_null_when_nothing_matches() {
        val route = HttpRoute(
            pathPrefix = "/api",
            targetBaseUrl = "http://localhost:4002",
            mockApis = mutableListOf(
                MockApiConfig(path = "/user", method = "POST", mockData = "{}")
            )
        )

        val match = PathPatternUtil.findMatchingMockApiInRoute("/user", "GET", route)
        assertNull(match)
    }
}
