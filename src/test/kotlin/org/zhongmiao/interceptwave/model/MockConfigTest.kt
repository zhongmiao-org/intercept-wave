package org.zhongmiao.interceptwave.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class MockConfigTest {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true; prettyPrint = false }

    @Test
    fun proxyConfig_defaults() {
        val p = ProxyConfig()
        assertTrue(p.id.isNotBlank())
        assertEquals("HTTP", p.protocol)
        assertEquals(8888, p.port)
        assertEquals("/api", p.interceptPrefix)
        assertTrue(p.stripPrefix)
        assertTrue(p.enabled)
        assertTrue(p.mockApis.isEmpty())
        assertNull(p.wsBaseUrl)
        assertNull(p.wsInterceptPrefix)
        assertTrue(p.wsManualPush)
        assertTrue(p.wsPushRules.isEmpty())
        assertFalse(p.wssEnabled)
    }

    @Test
    fun mockApiConfig_defaults() {
        val api = MockApiConfig(path = "/x")
        assertTrue(api.enabled)
        assertEquals("{}", api.mockData)
        assertEquals("ALL", api.method)
        assertEquals(200, api.statusCode)
        assertFalse(api.useCookie)
        assertEquals(0L, api.delay)
    }

    @Test
    fun wsPushRule_defaultsAndSerialization() {
        val r = WsPushRule(path = "/room/**")
        assertTrue(r.enabled)
        assertEquals("action", r.eventKey)
        assertNull(r.eventValue)
        assertEquals("both", r.direction)
        assertEquals("off", r.mode)
        assertEquals(5, r.periodSec)
        assertEquals("{}", r.message)
        assertTrue(r.timeline.isEmpty())
        assertFalse(r.loop)
        assertFalse(r.onOpenFire)

        val s = json.encodeToString(r)
        val back = Json.decodeFromString(WsPushRule.serializer(), s)
        assertEquals(r, back)
    }

    @Test
    fun rootConfig_defaults() {
        val root = RootConfig()
        assertEquals("2.0", root.version)
        assertTrue(root.proxyGroups.isEmpty())
        val s = json.encodeToString(root)
        assertTrue(s.contains("\"version\":\"2.0\""))
    }
}

