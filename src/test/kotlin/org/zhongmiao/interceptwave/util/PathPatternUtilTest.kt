package org.zhongmiao.interceptwave.util

import org.junit.Assert.*
import org.junit.Test
import org.zhongmiao.interceptwave.model.MockApiConfig
import org.zhongmiao.interceptwave.model.ProxyConfig

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
        val cfg = ProxyConfig()
        cfg.mockApis.addAll(
            listOf(
                MockApiConfig(path = "/user/**", method = "ALL", mockData = "{}"),
                MockApiConfig(path = "/user/*/detail", method = "GET", mockData = "{}"),
                MockApiConfig(path = "/user/123/detail", method = "GET", mockData = "{}")
            )
        )

        val req = "/user/123/detail"
        val match = PathPatternUtil.findMatchingMockApiInProxy(req, "GET", cfg)
        assertNotNull(match)
        assertEquals("/user/123/detail", match!!.path)

        // Method specificity: if exact path with ALL and less specific method, prefer GET on wildcard
        val cfg2 = ProxyConfig()
        cfg2.mockApis.addAll(
            listOf(
                MockApiConfig(path = "/hello/world", method = "ALL", mockData = "{}"),
                MockApiConfig(path = "/hello/*", method = "GET", mockData = "{}")
            )
        )
        val m2 = PathPatternUtil.findMatchingMockApiInProxy("/hello/world", "GET", cfg2)
        assertNotNull(m2)
        // exact match without wildcard wins over wildcard, even if method is ALL
        assertEquals("/hello/world", m2!!.path)
    }
}

