package org.zhongmiao.interceptwave.services.http

import com.intellij.openapi.diagnostic.Logger
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.zhongmiao.interceptwave.InterceptWaveBundle.message
import org.zhongmiao.interceptwave.events.*
import org.zhongmiao.interceptwave.model.MockApiConfig
import org.zhongmiao.interceptwave.model.ProxyConfig
import org.zhongmiao.interceptwave.util.Env
import java.net.InetSocketAddress
import org.zhongmiao.interceptwave.util.HttpWelcomeUtil
import org.zhongmiao.interceptwave.util.PathUtil
import org.zhongmiao.interceptwave.util.HttpForwardUtil
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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

            // Welcome page
            if (requestPath == "/" || requestPath.isEmpty()) {
                handleProxyWelcomePage(exchange)
                return
            }

            // When stripPrefix is enabled, visiting /prefix or /prefix/ shows welcome page as well
            if (config.stripPrefix && config.interceptPrefix.isNotEmpty()) {
                val normalizedPrefix = if (config.interceptPrefix.endsWith("/")) config.interceptPrefix.dropLast(1) else config.interceptPrefix
                if (requestPath == normalizedPrefix || requestPath == "$normalizedPrefix/") {
                    handleProxyWelcomePage(exchange)
                    return
                }
            }

            // Compute matching path
            val matchPath = PathUtil.computeHttpMatchPath(config, requestPath)

            // Find mock api
            val mockApi = findMatchingMockApiInProxy(matchPath, method)
            output.publish(MatchedPath(config.id, config.name, matchPath))

            if (mockApi != null && mockApi.enabled) {
                handleProxyMockResponse(exchange, mockApi)
            } else {
                forwardToOriginalServerProxy(exchange)
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

    private fun findMatchingMockApiInProxy(requestPath: String, method: String): MockApiConfig? =
        org.zhongmiao.interceptwave.util.PathPatternUtil.findMatchingMockApiInProxy(requestPath, method, config)

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

    private fun forwardToOriginalServerProxy(exchange: HttpExchange) {
        try {
            val requestPath = exchange.requestURI.toString()
            val targetUrl = config.baseUrl + requestPath

            output.publish(ForwardingTo(config.id, config.name, targetUrl))

            if (Env.isNoUi() && System.getProperty("interceptwave.allowForwardInTests") != "true") {
                sendErrorResponse(exchange, 502, "Forwarding disabled in tests/headless/CI: $targetUrl")
                return
            }

            val client = HttpForwardUtil.createClient()
            val request = HttpForwardUtil.buildRequest(targetUrl, exchange)
            val response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray())

            org.zhongmiao.interceptwave.util.HttpServerUtil.copySafeResponseHeaders(
                response.headers().map(), exchange.responseHeaders
            )
            org.zhongmiao.interceptwave.util.HttpServerUtil.applyCors(exchange.responseHeaders)

            val respBytes = response.body()
            exchange.sendResponseHeaders(response.statusCode(), respBytes.size.toLong())
            exchange.responseBody.use { it.write(respBytes) }

            output.publish(Forwarded(config.id, config.name, targetUrl, response.statusCode()))
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

}
