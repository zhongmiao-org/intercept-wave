package org.zhongmiao.interceptwave.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.zhongmiao.interceptwave.model.MockApiConfig
import org.zhongmiao.interceptwave.model.ProxyConfig
import java.net.ServerSocket

class MockServerPatternLogicTest : BasePlatformTestCase() {

    private lateinit var service: MockServerService

    override fun setUp() {
        super.setUp()
        service = project.getService(MockServerService::class.java)
    }

    // Helper to call a private method via reflection
    private fun callBoolean(name: String, vararg args: Any?): Boolean {
        val types = args.map { it!!::class.java }.toTypedArray()
        val m = service.javaClass.getDeclaredMethod(name, *types)
        m.isAccessible = true
        return m.invoke(service, *args) as Boolean
    }

    private fun <T> call(name: String, vararg args: Any?): T {
        val types = args.map { it!!::class.java }.toTypedArray()
        val m = service.javaClass.getDeclaredMethod(name, *types)
        m.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return m.invoke(service, *args) as T
    }

    fun `test pathPatternMatches no-wildcard equality`() {
        // When no '*' exists, it falls back to equality
        val same = callBoolean("pathPatternMatches", "/abc", "/abc")
        val diff = callBoolean("pathPatternMatches", "/abc", "/xyz")
        assertTrue(same)
        assertFalse(diff)
    }

    fun `test patternToRegex escapes regex meta chars`() {
        // Ensure '.' and '+' are treated literally, not as regex tokens
        val regexDot: Regex = call("patternToRegex", "/a.b/*")
        assertTrue(regexDot.matches("/a.b/123"))
        assertFalse(regexDot.matches("/axb/123"))

        val regexPlus: Regex = call("patternToRegex", "/a+b/*")
        assertTrue(regexPlus.matches("/a+b/1"))
        assertFalse(regexPlus.matches("/ab/1"))
    }

    fun `test pathPatternMatches single and double star`() {
        // single segment '*'
        assertTrue(callBoolean("pathPatternMatches", "/a/b/*", "/a/b/123"))
        assertFalse(callBoolean("pathPatternMatches", "/a/b/*", "/a/b/123/456"))

        // multi-segment '**'
        assertTrue(callBoolean("pathPatternMatches", "/a/b/**", "/a/b/123"))
        assertTrue(callBoolean("pathPatternMatches", "/a/b/**", "/a/b/123/456"))
        // by design does not match the base without further segment
        assertFalse(callBoolean("pathPatternMatches", "/a/b/**", "/a/b"))
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

        val method = service.javaClass.getDeclaredMethod(
            "findMatchingMockApiInProxy",
            String::class.java,
            String::class.java,
            ProxyConfig::class.java
        )
        method.isAccessible = true

        val matched = method.invoke(service, "/a/b/c", "GET", config) as MockApiConfig?
        assertNotNull(matched)
        assertEquals("/a/*/c", matched!!.path)
        assertEquals("{\"pick\": \"single\"}", matched.mockData)
    }

    fun `test isPortOccupied detects usage`() {
        // Bind an ephemeral port to simulate occupation
        val socket = ServerSocket(0)
        try {
            val port = socket.localPort
            val inUse = callBoolean("isPortOccupied", port)
            assertTrue(inUse)
        } finally {
            socket.close()
        }

        // After closing, port should be considered free (best effort)
        val freePort = ServerSocket(0).use { it.localPort }
        val free = callBoolean("isPortOccupied", freePort)
        assertFalse(free)
    }
}

