package org.zhongmiao.interceptwave.services.http

import com.intellij.openapi.diagnostic.Logger
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.zhongmiao.interceptwave.InterceptWaveBundle.message
import org.zhongmiao.interceptwave.events.*
import org.zhongmiao.interceptwave.model.HttpRoute
import org.zhongmiao.interceptwave.model.MockApiConfig
import org.zhongmiao.interceptwave.model.ProxyConfig
import org.zhongmiao.interceptwave.util.Env
import java.net.InetSocketAddress
import org.zhongmiao.interceptwave.util.HttpWelcomeUtil
import org.zhongmiao.interceptwave.util.PathUtil
import org.zhongmiao.interceptwave.util.HttpForwardUtil
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.net.http.HttpResponse

/**
 * Lightweight HTTP server engine per group.
 * Encapsulates the JDK HttpServer and request handling for a single ProxyConfig.
 */
class HttpServerEngine(
    private val config: ProxyConfig,
    private val output: MockServerOutput,
) : org.zhongmiao.interceptwave.services.ServerEngine {
    private val log = Logger.getInstance(HttpServerEngine::class.java)

    @Volatile
    override var lastError: String? = null
        private set

    private var server: HttpServer? = null
    private var executor: ExecutorService? = null

    override fun isRunning(): Boolean = server != null

    override fun getUrl(): String = "http://localhost:${config.port}"

    override fun start(): Boolean {
        return try {
            val exec = Executors.newFixedThreadPool(10)
            val s = HttpServer.create(InetSocketAddress(config.port), 0).apply {
                createContext("/") { exchange ->
                    handleProxyRequest(exchange)
                }
                executor = exec
                start()
            }
            server = s
            executor = exec
            true
        } catch (t: Throwable) {
            lastError = t.message ?: t.toString()
            runCatching { stop() }
            false
        }
    }

    override fun stop() {
        runCatching { server?.stop(0) }
        runCatching { executor?.shutdown() }
        server = null
        executor = null
    }

    // ============ Request handling (copied from MockServerService, adapted) ============

    private fun handleProxyRequest(exchange: HttpExchange) {
        try {
            val requestPath = exchange.requestURI.path
            val method = exchange.requestMethod

            log.info("[${config.name}] Received: $method $requestPath")
            output.publish(RequestReceived(config.id, config.name, method, requestPath))

            if ((requestPath == "/" || requestPath.isEmpty()) && !hasRootHttpRoute()) {
                handleProxyWelcomePage(exchange)
                return
            }

            val route = PathUtil.selectHttpRoute(config, requestPath)
            if (route == null) {
                sendErrorResponse(exchange, 502, "Bad Gateway: No matching route configured")
                return
            }

            if (shouldServeWelcomePage(route, requestPath)) {
                handleProxyWelcomePage(exchange)
                return
            }

            val matchPath = PathUtil.computeHttpMatchPath(route, requestPath)

            val mockApi = findMatchingMockApiInRoute(route, matchPath, method)
            output.publish(MatchedPath(config.id, config.name, matchPath))

            if (route.enableMock && mockApi != null && mockApi.enabled) {
                handleProxyMockResponse(exchange, mockApi)
            } else {
                forwardToOriginalServerProxy(exchange, route)
            }
        } catch (e: Exception) {
            log.error("[${config.name}] Error handling request", e)
            output.publish(ErrorOccurred(config.id, config.name, message("error.request.processing"), e.message))
            sendErrorResponse(exchange, 500, "Internal Server Error: ${e.message}")
        }
    }

    private fun handleProxyWelcomePage(exchange: HttpExchange) {
        try {
            val welcomeJson = HttpWelcomeUtil.buildWelcomeJson(config)

            exchange.responseHeaders.set("Content-Type", "application/json; charset=UTF-8")
            org.zhongmiao.interceptwave.util.HttpServerUtil.applyCors(exchange.responseHeaders)

            val responseBytes = welcomeJson.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, responseBytes.size.toLong())
            exchange.responseBody.use { it.write(responseBytes) }
        } catch (e: Exception) {
            log.error("Error serving welcome page", e)
            sendErrorResponse(exchange, 500, "Error serving welcome page")
        }
    }

    private fun findMatchingMockApiInRoute(route: HttpRoute, requestPath: String, method: String): MockApiConfig? =
        org.zhongmiao.interceptwave.util.PathPatternUtil.findMatchingMockApiInRoute(requestPath, method, route)

    private fun handleProxyMockResponse(exchange: HttpExchange, mockApi: MockApiConfig) {
        try {
            if (mockApi.delay > 0) Thread.sleep(mockApi.delay)

            if (mockApi.useCookie && config.globalCookie.isNotEmpty()) {
                exchange.responseHeaders.add("Set-Cookie", config.globalCookie)
            }

            exchange.responseHeaders.set("Content-Type", "application/json; charset=UTF-8")
            org.zhongmiao.interceptwave.util.HttpServerUtil.applyCors(exchange.responseHeaders)

            if (org.zhongmiao.interceptwave.util.HttpServerUtil.isPreflight(exchange)) {
                exchange.sendResponseHeaders(200, -1)
                exchange.close()
                return
            }

            val responseBytes = mockApi.mockData.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(mockApi.statusCode, responseBytes.size.toLong())
            exchange.responseBody.use { it.write(responseBytes) }

            output.publish(MockMatched(config.id, config.name, mockApi.path, exchange.requestMethod, mockApi.statusCode))
        } catch (e: Exception) {
            log.error("Error sending mock response", e)
            sendErrorResponse(exchange, 500, "Error sending mock response")
        }
    }

    private fun forwardToOriginalServerProxy(exchange: HttpExchange, route: HttpRoute) {
        try {
            val requestPath = exchange.requestURI.path
            val forwardPath = PathUtil.computeHttpForwardPath(route, requestPath)
            val query = exchange.requestURI.rawQuery?.takeIf { it.isNotBlank() }?.let { "?$it" } ?: ""
            val targetUrl = buildForwardTargetUrl(route, forwardPath, query)

            output.publish(ForwardingTo(config.id, config.name, targetUrl))

            if (Env.isNoUi() && System.getProperty("interceptwave.allowForwardInTests") != "true") {
                sendErrorResponse(exchange, 502, "Forwarding disabled in tests/headless/CI: $targetUrl")
                return
            }

            val client = HttpForwardUtil.createClient()
            val request = HttpForwardUtil.buildRequest(targetUrl, exchange)
            val response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray())
            var responseTargetUrl = targetUrl
            val finalResponse = if (shouldRetrySpaFallback(exchange, route, requestPath, response)) {
                val fallbackPath = normalizedSpaFallbackPath(route.spaFallbackPath)
                val fallbackUrl = buildForwardTargetUrl(route, fallbackPath, "")
                output.publish(ForwardingTo(config.id, config.name, fallbackUrl))
                responseTargetUrl = fallbackUrl
                client.send(HttpForwardUtil.buildRequest(fallbackUrl, exchange), HttpResponse.BodyHandlers.ofByteArray())
            } else {
                response
            }

            org.zhongmiao.interceptwave.util.HttpServerUtil.copySafeResponseHeaders(
                finalResponse.headers().map(), exchange.responseHeaders
            )
            org.zhongmiao.interceptwave.util.HttpServerUtil.applyCors(exchange.responseHeaders)

            val respBytes = finalResponse.body()
            exchange.sendResponseHeaders(finalResponse.statusCode(), respBytes.size.toLong())
            exchange.responseBody.use { it.write(respBytes) }

            output.publish(Forwarded(config.id, config.name, responseTargetUrl, finalResponse.statusCode()))
        } catch (e: Exception) {
            logForwardError(e)
            output.publish(ErrorOccurred(config.id, config.name, message("error.proxy.error"), e.message))
            sendErrorResponse(exchange, 502, "Bad Gateway: Unable to reach original server")
        }
    }

    private fun sendErrorResponse(exchange: HttpExchange, statusCode: Int, message: String) {
        org.zhongmiao.interceptwave.util.HttpServerUtil.sendJsonError(exchange, statusCode, message)
    }

    private fun logForwardError(t: Throwable) {
        if (Env.isNoUi()) log.warn("Error forwarding request", t) else log.error("Error forwarding request", t)
    }

    private fun shouldServeWelcomePage(route: HttpRoute, requestPath: String): Boolean {
        if (!route.stripPrefix) return false
        val prefix = route.pathPrefix.trimEnd('/').ifEmpty { "/" }
        if (prefix == "/") return false
        return requestPath == prefix || requestPath == "$prefix/"
    }

    private fun hasRootHttpRoute(): Boolean =
        config.routes.any { it.pathPrefix.trim().trimEnd('/').ifEmpty { "/" } == "/" }

    private fun shouldRetrySpaFallback(
        exchange: HttpExchange,
        route: HttpRoute,
        requestPath: String,
        response: HttpResponse<ByteArray>
    ): Boolean {
        if (route.spaFallbackPath.isBlank()) return false
        if (response.statusCode() != 404) return false
        if (!isHtmlNavigation(exchange)) return false
        if (isStaticAssetPath(requestPath)) return false
        return exchange.requestMethod.equals("GET", true) || exchange.requestMethod.equals("HEAD", true)
    }

    private fun isHtmlNavigation(exchange: HttpExchange): Boolean {
        val accept = exchange.requestHeaders["Accept"].orEmpty().joinToString(",").lowercase()
        return accept.contains("text/html") || accept.contains("application/xhtml+xml")
    }

    private fun isStaticAssetPath(path: String): Boolean {
        val lastSegment = path.substringAfterLast('/')
        return lastSegment.contains('.') && !lastSegment.startsWith('.')
    }

    private fun normalizedSpaFallbackPath(path: String): String {
        val trimmed = path.trim().ifEmpty { "/" }
        return if (trimmed.startsWith("/")) trimmed else "/$trimmed"
    }

    private fun buildForwardTargetUrl(route: HttpRoute, path: String, query: String): String =
        route.targetBaseUrl.trimEnd('/') + path + query

}
