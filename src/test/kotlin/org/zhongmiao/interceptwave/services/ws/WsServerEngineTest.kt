package org.zhongmiao.interceptwave.services.ws

import org.junit.Assert.*
import org.junit.Test
import org.zhongmiao.interceptwave.events.MockServerEvent
import org.zhongmiao.interceptwave.events.MockServerOutput
import org.zhongmiao.interceptwave.model.ProxyConfig
import java.lang.reflect.Method
import org.zhongmiao.interceptwave.util.PathUtil

class WsServerEngineTest {

    private class TestOutput : MockServerOutput {
        val events = mutableListOf<MockServerEvent>()
        override fun publish(event: MockServerEvent) { events.add(event) }
    }

    private fun invokePrivate(obj: Any, name: String, vararg args: Any?): Any? {
        var m: Method? = null
        val methods = obj::class.java.declaredMethods
        for (candidate in methods) {
            if (candidate.name == name && candidate.parameterTypes.size == args.size) {
                m = candidate
                break
            }
        }
        requireNotNull(m) { "Method $name not found" }
        m.isAccessible = true
        return m.invoke(obj, *args)
    }

    @Test
    fun computePathsAndUpstream() {
        val cfg = ProxyConfig(
            protocol = "WS",
            port = 9999,
            interceptPrefix = "/api",
            wsInterceptPrefix = "/ws",
            stripPrefix = true,
            wsBaseUrl = "ws://upstream/service/"
        )
        val out = TestOutput()
        val engine = WsServerEngine(cfg, out)

        // computeMatchPath moved to PathUtil
        val match = PathUtil.computeWsMatchPath(cfg, "/ws/chat")
        assertEquals("/chat", match)

        val fwd = invokePrivate(engine, "computeForwardPath", "/ws/chat?token=1") as String
        assertEquals("/ws/chat?token=1", fwd)

        val upstream = invokePrivate(engine, "buildUpstreamUrl", "/ws/chat?token=1") as String
        // base ends with '/', forward starts with '/' => single slash join (no double slash)
        assertEquals("ws://upstream/service/ws/chat?token=1", upstream)

        // base without trailing slash
        val cfg2 = cfg.copy(wsBaseUrl = "ws://upstream/service")
        val engine2 = WsServerEngine(cfg2, out)
        val upstream2 = invokePrivate(engine2, "buildUpstreamUrl", "/ws/chat?x=1") as String
        assertEquals("ws://upstream/service/ws/chat?x=1", upstream2)
    }

    @Test
    fun routeMatching_glob() {
        val cfg = ProxyConfig(protocol = "WS", port = 9998, interceptPrefix = "/api", wsInterceptPrefix = null, stripPrefix = false, wsBaseUrl = "ws://up")
        val out = TestOutput()
        val engine = WsServerEngine(cfg, out)
        val ok1 = invokePrivate(engine, "matchRoute", "/room/123/detail", "/room/*/detail") as Boolean
        assertTrue(ok1)
        val ok2 = invokePrivate(engine, "matchRoute", "/files/a/b/c", "/files/**") as Boolean
        assertTrue(ok2)
        val bad = invokePrivate(engine, "matchRoute", "/room/1/2", "/room/*/detail") as Boolean
        assertFalse(bad)
    }
}
