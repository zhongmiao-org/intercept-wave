package org.zhongmiao.interceptwave.services.ws

import com.intellij.openapi.diagnostic.Logger
import org.java_websocket.server.WebSocketServer
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.framing.Framedata
import org.java_websocket.drafts.Draft_6455
import org.java_websocket.server.DefaultSSLWebSocketServerFactory
import org.zhongmiao.interceptwave.events.*
import org.zhongmiao.interceptwave.model.ProxyConfig
import org.zhongmiao.interceptwave.util.PathPatternUtil
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket.Builder
import java.net.http.WebSocket.Listener
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import java.util.concurrent.*

/**
 * Lightweight WS server engine per group using Java-WebSocket.
 * - Accepts local ws:// (or wss:// when configured)
 * - Bridges to upstream ws/wss via JDK Http WebSocket
 * - Supports periodic/timeline push per connection for matching rules
 */
class WsServerEngine(
    private val config: ProxyConfig,
    private val output: MockServerOutput
) {
    private val log = Logger.getInstance(WsServerEngine::class.java)

    private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(2)
    private val client: HttpClient = HttpClient.newBuilder().build()

    private lateinit var server: WebSocketServer

    private data class ConnCtx(
        val conn: WebSocket,
        val pathOnly: String, // without query, possibly stripPrefix applied
        val fullPath: String, // with query
        @Volatile var upstream: java.net.http.WebSocket? = null,
        val periodicTasks: MutableList<ScheduledFuture<*>> = mutableListOf(),
        val timelineTasks: MutableList<ScheduledFuture<*>> = mutableListOf(),
        @Volatile var lastManualSendAt: Long = 0L,
        val openedAt: Long = System.currentTimeMillis()
    )

    private val connections = ConcurrentHashMap<WebSocket, ConnCtx>()

    fun start(): Boolean {
        try {
            server = object : WebSocketServer(InetSocketAddress("127.0.0.1", config.port), 0, listOf(Draft_6455())) {
                override fun onStart() {
                    log.info("WS server started on :${config.port}")
                }

                override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
                    val resource = handshake.resourceDescriptor // includes path + query, starts with '/'
                    val pathOnly = resource.substringBefore('?')
                    val computed = computeMatchPath(pathOnly)
                    val fullForwardPath = computeForwardPath(resource)
                    val upstreamUrl = buildUpstreamUrl(fullForwardPath)
                    output.publish(WebSocketConnecting(config.id, config.name, computed, upstreamUrl))

                    val ctx = ConnCtx(conn, computed, fullForwardPath)
                    this@WsServerEngine.connections[conn] = ctx
                    attachUpstream(ctx, upstreamUrl)

                    // Auto push: schedule per matching rule
                    scheduleAutoPush(ctx)

                    output.publish(WebSocketConnected(config.id, config.name, computed))
                }

                override fun onClose(conn: WebSocket, code: Int, reason: String?, remote: Boolean) {
                    val ctx = this@WsServerEngine.connections.remove(conn)
                    ctx?.let { cleanupCtx(it) }
                    output.publish(WebSocketClosed(config.id, config.name, ctx?.pathOnly ?: "", reason))
                }

                override fun onMessage(conn: WebSocket, message: String) {
                    val ctx = this@WsServerEngine.connections[conn]
                    if (ctx != null) {
                        output.publish(WebSocketMessageIn(config.id, config.name, ctx.pathOnly, message.toByteArray().size, true, message.take(128)))
                        ctx.upstream?.sendText(message, true)
                    }
                }

                override fun onMessage(conn: WebSocket, message: ByteBuffer) {
                    val ctx = this@WsServerEngine.connections[conn]
                    if (ctx != null) {
                        output.publish(WebSocketMessageIn(config.id, config.name, ctx.pathOnly, message.remaining(), false, null))
                        val bytes = ByteArray(message.remaining())
                        message.get(bytes)
                        ctx.upstream?.sendBinary(ByteBuffer.wrap(bytes), true)
                    }
                }

                override fun onError(conn: WebSocket?, ex: Exception) {
                    val path = conn?.let { this@WsServerEngine.connections[it]?.pathOnly } ?: ""
                    output.publish(WebSocketError(config.id, config.name, path, ex.message ?: "error"))
                }

                override fun onWebsocketPing(conn: WebSocket?, f: Framedata?) {
                    super.onWebsocketPing(conn, f)
                }
            }

            if (config.wssEnabled) {
                val ssl = buildSslContext() ?: throw IllegalStateException("WSS enabled but SSLContext init failed")
                server.setWebSocketFactory(DefaultSSLWebSocketServerFactory(ssl))
            }

            server.start()
            return true
        } catch (t: Throwable) {
            log.warn("Failed to start WS server", t)
            return false
        }
    }

    fun stop() {
        try {
            server.stop(0)
        } catch (_: Throwable) { /* ignore */ }
        connections.values.forEach { cleanupCtx(it) }
        connections.clear()
        scheduler.shutdownNow()
    }

    fun send(target: String, path: String?, message: String) {
        val now = System.currentTimeMillis()
        val conns = selectConnections(target, path)
        conns.forEach { ctx ->
            if (message.length > 1_000_000) {
                output.publish(WebSocketError(config.id, config.name, ctx.pathOnly, "Message too large (>1MB)"))
                return@forEach
            }
            ctx.conn.send(message)
            ctx.lastManualSendAt = now
            output.publish(WebSocketMessageOut(config.id, config.name, ctx.pathOnly, message.toByteArray().size, true, message.take(128)))
            // reset periodic timers by re-scheduling
            resetPeriodic(ctx)
        }
    }

    private fun selectConnections(target: String, path: String?): List<ConnCtx> {
        if (connections.isEmpty()) return emptyList()
        return when (target.uppercase()) {
            "ALL" -> connections.values.toList()
            "LATEST" -> listOf(connections.values.maxByOrNull { it.openedAt } ?: return emptyList())
            else -> { // MATCH
                if (path.isNullOrBlank()) connections.values.toList()
                else connections.values.filter { matchRoute(it.pathOnly, path) }
            }
        }
    }

    private fun cleanupCtx(ctx: ConnCtx) {
        ctx.periodicTasks.forEach { runCatching { it.cancel(true) } }
        ctx.timelineTasks.forEach { runCatching { it.cancel(true) } }
        ctx.periodicTasks.clear(); ctx.timelineTasks.clear()
        runCatching { ctx.upstream?.sendClose(java.net.http.WebSocket.NORMAL_CLOSURE, "bye") }
    }

    private fun buildUpstreamUrl(forwardPathWithQuery: String): String {
        val base = config.wsBaseUrl?.trim() ?: ""
        return if (base.endsWith("/") && forwardPathWithQuery.startsWith("/")) base.dropLast(1) + forwardPathWithQuery else base + forwardPathWithQuery
    }

    private fun computeMatchPath(requestPath: String): String {
        val prefix = config.wsInterceptPrefix ?: config.interceptPrefix
        return if (config.stripPrefix && prefix.isNotEmpty() && requestPath.startsWith(prefix)) {
            requestPath.removePrefix(prefix).ifEmpty { "/" }
        } else requestPath
    }

    private fun computeForwardPath(resource: String): String {
        // resource includes query; only strip path portion
        val requestPath = resource.substringBefore('?')
        val query = resource.substringAfter('?', "")
        val path = computeMatchPath(requestPath)
        return if (query.isEmpty()) path else "$path?${query}"
    }

    private fun attachUpstream(ctx: ConnCtx, upstreamUrl: String) {
        try {
            val listener = object : Listener {
                override fun onOpen(webSocket: java.net.http.WebSocket) {
                    ctx.upstream = webSocket
                }
                override fun onText(webSocket: java.net.http.WebSocket, data: CharSequence, last: Boolean): CompletionStage<*> {
                    val text = data.toString()
                    output.publish(WebSocketMessageOut(config.id, config.name, ctx.pathOnly, text.toByteArray().size, true, text.take(128)))
                    ctx.conn.send(text)
                    return CompletableFuture.completedFuture(null)
                }
                override fun onBinary(webSocket: java.net.http.WebSocket, data: ByteBuffer, last: Boolean): CompletionStage<*> {
                    output.publish(WebSocketMessageOut(config.id, config.name, ctx.pathOnly, data.remaining(), false, null))
                    ctx.conn.send(data)
                    return CompletableFuture.completedFuture(null)
                }
                override fun onPing(webSocket: java.net.http.WebSocket, message: ByteBuffer): CompletionStage<*> {
                    // Java-WebSocket handles ping/pong internally; no explicit forward needed
                    return CompletableFuture.completedFuture(null)
                }
                override fun onPong(webSocket: java.net.http.WebSocket, message: ByteBuffer): CompletionStage<*> {
                    return CompletableFuture.completedFuture(null)
                }
                override fun onClose(webSocket: java.net.http.WebSocket, statusCode: Int, reason: String?): CompletionStage<*> {
                    ctx.conn.close(statusCode, reason)
                    return CompletableFuture.completedFuture(null)
                }
                override fun onError(webSocket: java.net.http.WebSocket, error: Throwable) {
                    output.publish(WebSocketError(config.id, config.name, ctx.pathOnly, error.message ?: "error"))
                }
            }
            val builder: Builder = client.newWebSocketBuilder()
            val uri = URI(upstreamUrl)
            builder.buildAsync(uri, listener)
        } catch (t: Throwable) {
            output.publish(WebSocketError(config.id, config.name, ctx.pathOnly, t.message ?: "error"))
        }
    }

    private fun matchRoute(actual: String, pattern: String): Boolean =
        if (pattern.contains('*')) PathPatternUtil.patternToRegex(pattern).matches(actual) else actual == pattern

    private fun scheduleAutoPush(ctx: ConnCtx) {
        // periodic
        config.wsPushRules.filter { it.enabled && it.mode.equals("periodic", true) }
            .filter { r -> r.path.isBlank() || matchRoute(ctx.pathOnly, r.path) }
            .forEach { r ->
                val sec = if (r.periodSec < 1) 1 else r.periodSec
                if (r.onOpenFire) {
                    sendAuto(ctx, r.message, "periodic")
                }
                val fut = scheduler.scheduleAtFixedRate({ sendAuto(ctx, r.message, "periodic") }, sec.toLong(), sec.toLong(), TimeUnit.SECONDS)
                ctx.periodicTasks.add(fut)
            }

        // timeline
        config.wsPushRules.filter { it.enabled && it.mode.equals("timeline", true) }
            .filter { r -> r.path.isBlank() || matchRoute(ctx.pathOnly, r.path) }
            .forEach { r ->
                scheduleTimeline(ctx, r)
            }
    }

    private fun sendAuto(ctx: ConnCtx, msg: String, mode: String) {
        if (msg.length > 1_000_000) return
        ctx.conn.send(msg)
        output.publish(WebSocketMockPushed(config.id, config.name, ctx.pathOnly, mode))
        output.publish(WebSocketMessageOut(config.id, config.name, ctx.pathOnly, msg.toByteArray().size, true, msg.take(128)))
    }

    private fun scheduleTimeline(ctx: ConnCtx, rule: org.zhongmiao.interceptwave.model.WsPushRule) {
        fun scheduleOnce(offsetMs: Long = 0L) {
            rule.timeline.sortedBy { it.atMs }.forEach { item ->
                val delay = (item.atMs.toLong() + offsetMs)
                val fut = scheduler.schedule({ sendAuto(ctx, item.message, "timeline") }, delay, TimeUnit.MILLISECONDS)
                ctx.timelineTasks.add(fut)
            }
            if (rule.loop && rule.timeline.isNotEmpty()) {
                val duration = rule.timeline.maxOf { it.atMs }.toLong()
                val fut = scheduler.schedule({
                    // reschedule next loop
                    scheduleOnce(0L)
                }, duration + 1, TimeUnit.MILLISECONDS)
                ctx.timelineTasks.add(fut)
            }
        }
        scheduleOnce(0L)
    }

    private fun resetPeriodic(ctx: ConnCtx) {
        // Cancel and recreate periodic tasks with fresh delay (period as initial delay)
        val periodicRules = config.wsPushRules.filter { it.enabled && it.mode.equals("periodic", true) }
            .filter { r -> r.path.isBlank() || matchRoute(ctx.pathOnly, r.path) }
        ctx.periodicTasks.forEach { runCatching { it.cancel(true) } }
        ctx.periodicTasks.clear()
        periodicRules.forEach { r ->
            val sec = if (r.periodSec < 1) 1 else r.periodSec
            val fut = scheduler.scheduleAtFixedRate({ sendAuto(ctx, r.message, "periodic") }, sec.toLong(), sec.toLong(), TimeUnit.SECONDS)
            ctx.periodicTasks.add(fut)
        }
    }

    private fun buildSslContext(): SSLContext? {
        try {
            val ksPath = config.wssKeystorePath ?: return null
            val pwd = (config.wssKeystorePassword ?: "").toCharArray()
            val ks = KeyStore.getInstance("PKCS12")
            ksPath.let { path ->
                val file = java.io.File(path)
                if (!file.exists()) return null
                ks.load(file.inputStream(), pwd)
            }
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            kmf.init(ks, pwd)
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(kmf.keyManagers, null, null)
            return ctx
        } catch (t: Throwable) {
            log.warn("Failed to init SSLContext for WSS", t)
            return null
        }
    }
}
