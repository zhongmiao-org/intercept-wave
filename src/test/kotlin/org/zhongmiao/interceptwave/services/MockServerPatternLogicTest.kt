package org.zhongmiao.interceptwave.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.zhongmiao.interceptwave.model.MockApiConfig
import org.zhongmiao.interceptwave.model.ProxyConfig
import org.zhongmiao.interceptwave.util.PathPatternUtil
import java.net.ServerSocket

class MockServerPatternLogicTest : BasePlatformTestCase() {

    fun `test pathPatternMatches no-wildcard equality`() {
        // When no '*' exists, it falls back to equality
        val same = PathPatternUtil.pathPatternMatches("/abc", "/abc")
        val diff = PathPatternUtil.pathPatternMatches("/abc", "/xyz")
        assertTrue(same)
        assertFalse(diff)
    }

    fun `test patternToRegex escapes regex meta chars`() {
        // Ensure '.' and '+' are treated literally, not as regex tokens
        val regexDot: Regex = PathPatternUtil.patternToRegex("/a.b/*")
        assertTrue(regexDot.matches("/a.b/123"))
        assertFalse(regexDot.matches("/axb/123"))

        val regexPlus: Regex = PathPatternUtil.patternToRegex("/a+b/*")
        assertTrue(regexPlus.matches("/a+b/1"))
        assertFalse(regexPlus.matches("/ab/1"))
    }

    fun `test pathPatternMatches single and double star`() {
        // single segment '*'
        assertTrue(PathPatternUtil.pathPatternMatches("/a/b/*", "/a/b/123"))
        assertFalse(PathPatternUtil.pathPatternMatches("/a/b/*", "/a/b/123/456"))

        // multi-segment '**'
        assertTrue(PathPatternUtil.pathPatternMatches("/a/b/**", "/a/b/123"))
        assertTrue(PathPatternUtil.pathPatternMatches("/a/b/**", "/a/b/123/456"))
        // by design does not match the base without further segment
        assertFalse(PathPatternUtil.pathPatternMatches("/a/b/**", "/a/b"))
    }

    fun `test findMatchingMockApiInProxy prefers fewer wildcards`() {
        val config = ProxyConfig(
            interceptPrefix = "/api",
            stripPrefix = true,
            mockApis = mutableListOf(
                MockApiConfig(path = "/a/**", mockData = "{\"pick\": \"double\"}", method = "GET", enabled = true),
                MockApiConfig(path = "/a/*/c", mockData = "{\"pick\": \"single\"}", method = "GET", enabled = true),
            )
        )
        val matched = PathPatternUtil.findMatchingMockApiInProxy("/a/b/c", "GET", config)
        assertNotNull(matched)
        assertEquals("/a/*/c", matched!!.path)
        assertEquals("{\"pick\": \"single\"}", matched.mockData)
    }

    fun `test isPortOccupied detects usage`() {
        // Bind an ephemeral port to simulate occupation
        val socket = ServerSocket(0)
        try {
            val port = socket.localPort
            val inUse = PathPatternUtil.isPortOccupied(port)
            assertTrue(inUse)
        } finally {
            socket.close()
        }

        // After closing, port should be considered free (best effort)
        val freePort = ServerSocket(0).use { it.localPort }
        val free = PathPatternUtil.isPortOccupied(freePort)
        assertFalse(free)
    }
}
