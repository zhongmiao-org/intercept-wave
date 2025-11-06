package org.zhongmiao.interceptwave.services

import org.zhongmiao.interceptwave.model.MockApiConfig
import org.zhongmiao.interceptwave.model.ProxyConfig
import org.zhongmiao.interceptwave.events.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.zhongmiao.interceptwave.InterceptWaveBundle.message
import org.zhongmiao.interceptwave.util.Env
import java.net.BindException
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Mock服务器服务 - v2.0 支持多服务器实例
 * 负责启动/停止HTTP Mock服务器，处理请求拦截和转发
 */
@Service(Service.Level.PROJECT)
class MockServerService(private val project: Project) {

    // 多服务器实例管理
    private val serverInstances = ConcurrentHashMap<String, HttpServer>()
    private val serverStatus = ConcurrentHashMap<String, Boolean>()
    private val serverExecutors = ConcurrentHashMap<String, java.util.concurrent.ExecutorService>()
    private val wsEngines = ConcurrentHashMap<String, org.zhongmiao.interceptwave.services.ws.WsServerEngine>()

    private val configService: ConfigService by lazy { project.service<ConfigService>() }
    // 业务输出端口：面向事件发布，不直接依赖任何 UI
    private val output: MockServerOutput by lazy { project.service<MockServerEventPublisher>() }

    // ============ v2.0 新方法：多服务器管理 ============

    /**
     * 启动单个配置组的服务器
     */
    fun startServer(configId: String): Boolean {
        if (serverStatus[configId] == true) {
            thisLogger().warn("Server for config $configId is already running")
            return false
        }

        val proxyConfig = configService.getProxyGroup(configId)
        if (proxyConfig == null) {
            thisLogger().error("Config not found: $configId")
            output.publish(ErrorOccurred(configId, null, message("error.config.notfound"), configId))
            return false
        }

        if (!proxyConfig.enabled) {
            thisLogger().warn("Config $configId is disabled")
            output.publish(ErrorOccurred(configId, proxyConfig.name, message("error.config.disabled")))
            return false
        }

        // 分流：WS 组使用 WS 引擎
        if (proxyConfig.protocol.equals("WS", ignoreCase = true)) {
            if (isPortOccupied(proxyConfig.port)) {
                output.publish(ServerStartFailed(configId, proxyConfig.name, proxyConfig.port, message("error.port.in.use")))
                return false
            }
            output.publish(ServerStarting(configId, proxyConfig.name, proxyConfig.port))
            val engine = org.zhongmiao.interceptwave.services.ws.WsServerEngine(proxyConfig, output)
            val ok = engine.start()
            return if (ok) {
                wsEngines[configId] = engine
                serverStatus[configId] = true
                // WSS is currently disabled in UI; always report ws://
                output.publish(ServerStarted(configId, proxyConfig.name, proxyConfig.port, "ws://localhost:${proxyConfig.port}"))
                true
            } else {
                val reason = engine.lastError?.takeIf { it.isNotBlank() } ?: "WS engine start failed"
                output.publish(ServerStartFailed(configId, proxyConfig.name, proxyConfig.port, reason))
                false
            }
        }

        return try {
            // 检查端口是否已被占用
            if (isPortOccupied(proxyConfig.port)) {
                output.publish(ServerStartFailed(configId, proxyConfig.name, proxyConfig.port, message("error.port.in.use")))
                return false
            }

            // 发布启动事件
            output.publish(ServerStarting(configId, proxyConfig.name, proxyConfig.port))

            val executor = Executors.newFixedThreadPool(10)
            val server = HttpServer.create(InetSocketAddress(proxyConfig.port), 0).apply {
                createContext("/") { exchange ->
                    handleProxyRequest(exchange, proxyConfig)
                }
                setExecutor(executor)
                start()
            }

            serverInstances[configId] = server
            serverExecutors[configId] = executor
            serverStatus[configId] = true

            val serverUrl = "http://localhost:${proxyConfig.port}"
            thisLogger().info("Server started for config: ${proxyConfig.name} on port ${proxyConfig.port}")
            output.publish(ServerStarted(configId, proxyConfig.name, proxyConfig.port, serverUrl))

            true
        } catch (e: BindException) {
            thisLogger().error("Port ${proxyConfig.port} is already in use", e)
            output.publish(ServerStartFailed(configId, proxyConfig.name, proxyConfig.port, e.message))
            false
        } catch (e: Exception) {
            thisLogger().error("Failed to start server for config: ${proxyConfig.name}", e)
            output.publish(ServerStartFailed(configId, proxyConfig.name, proxyConfig.port, e.message))
            false
        }
    }

    /**
     * 停止单个配置组的服务器
     */
    fun stopServer(configId: String) {
        val server = serverInstances[configId]
        val executor = serverExecutors[configId]
        val proxyConfig = configService.getProxyGroup(configId)
        val configName = proxyConfig?.name ?: configId

        val ws = wsEngines[configId]

        if (ws != null) {
            runCatching { ws.stop() }
            wsEngines.remove(configId)
            serverStatus.remove(configId)
            output.publish(ServerStopped(configId, configName, proxyConfig?.port ?: -1))
            return
        }

        if (server != null) {
            // Stop the HTTP server
            server.stop(0)

            // Shutdown the executor service to release threads
            executor?.shutdown()

            // Remove from maps
            serverInstances.remove(configId)
            serverExecutors.remove(configId)
            serverStatus.remove(configId)

            thisLogger().info("Server stopped for config: $configName")

            output.publish(ServerStopped(configId, configName, proxyConfig?.port ?: -1))
        }
    }

    /**
     * 启动所有已启用的配置组
     */
    fun startAllServers(): Map<String, Boolean> {
        val results = mutableMapOf<String, Boolean>()
        val enabledConfigs = configService.getEnabledProxyGroups()

        if (enabledConfigs.isEmpty()) {
            output.publish(ErrorOccurred(message = message("error.no.enabled.config")))
            return results
        }

        output.publish(AllServersStarting(total = enabledConfigs.size))

        enabledConfigs.forEach { config ->
            results[config.id] = startServer(config.id)
        }

        val successCount = results.values.count { it }
        output.publish(AllServersStarted(success = successCount, total = enabledConfigs.size))

        return results
    }

    /**
     * 停止所有服务器
     */
    fun stopAllServers() {
        val configIds = serverInstances.keys.toList()
        if (configIds.isEmpty()) {
            // 即使没有运行中的服务，也发布 AllServersStopped 事件，
            // 以便上层（Console 联动/抑制标志消费）能完成一次完整的停止周期。
            output.publish(AllServersStopped())
            return
        }

        // 发布批量停止事件

        configIds.forEach { stopServer(it) }

        output.publish(AllServersStopped())
    }

    /**
     * 获取服务器状态
     */
    fun getServerStatus(configId: String): Boolean {
        return serverStatus[configId] ?: false
    }

    /**
     * 获取服务器 URL
     */
    fun getServerUrl(configId: String): String? {
        val isRunning = serverStatus[configId] ?: false
        if (!isRunning) return null

        val config = configService.getProxyGroup(configId) ?: return null
        return if (config.protocol.equals("WS", ignoreCase = true)) {
            // Report ws:// while WSS is not supported in UI
            "ws://localhost:${config.port}"
        } else {
            "http://localhost:${config.port}"
        }
    }

    /**
     * 获取所有运行中的服务器
     */
    fun getRunningServers(): List<Pair<String, String>> {
        return serverStatus.filter { it.value }.mapNotNull { (configId, _) ->
            val config = configService.getProxyGroup(configId)
            if (config != null) {
                val url = if (config.protocol.equals("WS", ignoreCase = true)) {
                    // Report ws:// while WSS is not supported in UI
                    "ws://localhost:${config.port}"
                } else {
                    "http://localhost:${config.port}"
                }
                configId to url
            } else {
                null
            }
        }
    }

    // ================= WS 辅助 API（占位实现） =================
    /**
     * 发送 WS 消息（手动推送）。实际推送将在 WS 引擎实现后完成；当前仅发布事件供 Console 展示。
     * @param target 目标：MATCH/ALL/LATEST
     */
    fun sendWsMessage(configId: String, path: String?, message: String, target: String = "MATCH") {
        val cfg = configService.getProxyGroup(configId) ?: return
        if (!cfg.protocol.equals("WS", ignoreCase = true)) return
        val engine = wsEngines[configId]
        if (engine != null) {
            engine.send(target, path, message)
        } else {
            // 兜底：尚未启动，打印提示
            output.publish(ErrorOccurred(configId, cfg.name, message("error.ws.send.placeholder")))
        }
    }

    /**
     * 检查端口是否被占用
     * 使用 ServerSocket 来测试端口，更轻量且能立即释放
     */
    private fun isPortOccupied(port: Int): Boolean =
        org.zhongmiao.interceptwave.util.PathPatternUtil.isPortOccupied(port)

    // ============ 请求处理方法（ProxyConfig版本） ============

    /**
     * 处理HTTP请求（ProxyConfig版本）
     */
    private fun handleProxyRequest(exchange: HttpExchange, config: ProxyConfig) {
        try {
            val requestPath = exchange.requestURI.path
            val method = exchange.requestMethod

            thisLogger().info("[${config.name}] Received: $method $requestPath")
            output.publish(RequestReceived(config.id, config.name, method, requestPath))

            // 处理根路径访问
            if (requestPath == "/" || requestPath.isEmpty()) {
                handleProxyWelcomePage(exchange, config)
                return
            }

            // 当启用剥离前缀时，访问 /前缀 或 /前缀/ 也展示欢迎页
            if (config.stripPrefix && config.interceptPrefix.isNotEmpty()) {
                val normalizedPrefix = if (config.interceptPrefix.endsWith("/")) config.interceptPrefix.dropLast(1) else config.interceptPrefix
                if (requestPath == normalizedPrefix || requestPath == "$normalizedPrefix/") {
                    handleProxyWelcomePage(exchange, config)
                    return
                }
            }

            // 路径匹配逻辑
            val matchPath = if (config.stripPrefix && config.interceptPrefix.isNotEmpty()) {
                if (requestPath.startsWith(config.interceptPrefix)) {
                    requestPath.removePrefix(config.interceptPrefix).ifEmpty { "/" }
                } else {
                    requestPath
                }
            } else {
                requestPath
            }

            // 仅记录日志，渲染交给订阅方

            // 查找匹配的Mock配置
            val mockApi = findMatchingMockApiInProxy(matchPath, method, config)

            // 告知 UI 实际参与匹配的路径，便于诊断
            output.publish(MatchedPath(config.id, config.name, matchPath))

            if (mockApi != null && mockApi.enabled) {
                // 使用Mock数据响应
                handleProxyMockResponse(exchange, mockApi, config)
            } else {
                // 转发到原始服务器
                forwardToOriginalServerProxy(exchange, config)
            }
        } catch (e: Exception) {
            thisLogger().error("[${config.name}] Error handling request", e)
            output.publish(ErrorOccurred(config.id, config.name, message("error.request.processing"), e.message))
            sendErrorResponse(exchange, 500, "Internal Server Error: ${e.message}")
        }
    }

    /**
     * 处理欢迎页面（ProxyConfig版本）
     */
    private fun handleProxyWelcomePage(exchange: HttpExchange, config: ProxyConfig) {
        try {
            val mockApiCount = config.mockApis.size
            val enabledApiCount = config.mockApis.count { it.enabled }

            val enabledApis = config.mockApis.filter { it.enabled }

            // 构造示例访问链接（仅展示已启用的接口）
            val examples = enabledApis.joinToString(",\n    ") { api ->
                val method = api.method
                val exampleUrl = if (config.stripPrefix) {
                    // path 视为相对路径，例如 /user，拼接为 /<prefix><path>
                    val prefix = if (config.interceptPrefix.endsWith("/")) config.interceptPrefix.dropLast(1) else config.interceptPrefix
                    val path = if (api.path.startsWith("/")) api.path else "/${api.path}"
                    "http://localhost:${config.port}" + prefix + path
                } else {
                    // path 为完整路径，例如 /api/user，直接拼接到本地端口
                    val fullPath = if (api.path.startsWith("/")) api.path else "/${api.path}"
                    "http://localhost:${config.port}" + fullPath
                }
                """{"method": "$method", "url": "$exampleUrl"}"""
            }

            val apisJson = enabledApis.joinToString(",\n    ") { api ->
                """{"path": "${api.path}", "method": "${api.method}", "enabled": ${api.enabled}}"""
            }

            val welcomeJson = """
                {
                  "status": "running",
                  "message": "${message("welcome.running")}",
                  "configGroup": "${config.name}",
                  "server": {
                    "port": ${config.port},
                    "baseUrl": "${config.baseUrl}",
                    "interceptPrefix": "${config.interceptPrefix}",
                    "stripPrefix": ${config.stripPrefix}
                  },
                  "mockApis": {
                    "total": $mockApiCount,
                    "enabled": $enabledApiCount
                  },
                  "usage": {
                    "description": "${message("welcome.usage.description")}",
                    "example": "GET http://localhost:${config.port}${config.interceptPrefix}/your-api-path"
                  },
                  "apis": [
                    $apisJson
                  ],
                  "examples": [
                    $examples
                  ]
                }
            """.trimIndent()

            exchange.responseHeaders.add("Content-Type", "application/json; charset=UTF-8")
            exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")

            val responseBytes = welcomeJson.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, responseBytes.size.toLong())
            exchange.responseBody.use { it.write(responseBytes) }
        } catch (e: Exception) {
            thisLogger().error("Error serving welcome page", e)
            sendErrorResponse(exchange, 500, "Error serving welcome page")
        }
    }

    /**
     * 查找匹配的Mock API（ProxyConfig版本）
     */
    private fun findMatchingMockApiInProxy(requestPath: String, method: String, config: ProxyConfig): MockApiConfig? =
        org.zhongmiao.interceptwave.util.PathPatternUtil.findMatchingMockApiInProxy(requestPath, method, config)

    

    /**
     * 处理Mock响应（ProxyConfig版本）
     */
    private fun handleProxyMockResponse(exchange: HttpExchange, mockApi: MockApiConfig, config: ProxyConfig) {
        try {
            // 模拟延迟
            if (mockApi.delay > 0) {
                Thread.sleep(mockApi.delay)
            }

            // 添加Cookie
            if (mockApi.useCookie && config.globalCookie.isNotEmpty()) {
                exchange.responseHeaders.add("Set-Cookie", config.globalCookie)
            }

            // 设置响应头（使用 set 确保唯一值，避免浏览器报重复 CORS 值）
            exchange.responseHeaders.set("Content-Type", "application/json; charset=UTF-8")
            exchange.responseHeaders.set("Access-Control-Allow-Origin", "*")
            exchange.responseHeaders.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
            exchange.responseHeaders.set("Access-Control-Allow-Headers", "Content-Type, Authorization")

            // 处理OPTIONS请求
            if (exchange.requestMethod.equals("OPTIONS", ignoreCase = true)) {
                exchange.sendResponseHeaders(200, -1)
                exchange.close()
                return
            }

            // 发送响应
            val responseBytes = mockApi.mockData.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(mockApi.statusCode, responseBytes.size.toLong())
            exchange.responseBody.use { it.write(responseBytes) }

            output.publish(MockMatched(config.id, config.name, mockApi.path, exchange.requestMethod, mockApi.statusCode))
        } catch (e: Exception) {
            thisLogger().error("Error sending mock response", e)
            sendErrorResponse(exchange, 500, "Error sending mock response")
        }
    }

    /**
     * 转发请求到原始服务器（ProxyConfig版本）
     */
    private fun forwardToOriginalServerProxy(exchange: HttpExchange, config: ProxyConfig) {
        try {
            val requestPath = exchange.requestURI.toString()
            val targetUrl = config.baseUrl + requestPath
            val method = exchange.requestMethod

            // 仅记录日志，渲染交给订阅方
            // 在转发前发布目标 URL 事件，便于在控制台看到“→ 转发至: ...”
            output.publish(ForwardingTo(config.id, config.name, targetUrl))

            // 在单元测试模式下，默认不进行真实转发，避免连接被拒绝导致的错误日志
            // 如需在测试中允许真实转发，可设置 -Dinterceptwave.allowForwardInTests=true
            if (Env.isNoUi() && System.getProperty("interceptwave.allowForwardInTests") != "true") {
                sendErrorResponse(exchange, 502, "Forwarding disabled in tests/headless/CI: $targetUrl")
                return
            }

            // 使用 JDK HttpClient 以规避后端响应头（如 Transfer-Encoding 与实际编码不一致）导致的阻塞问题
            val client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build()

            // 构造请求
            val requestBuilder = java.net.http.HttpRequest.newBuilder(URI(targetUrl))
                .timeout(java.time.Duration.ofSeconds(30))

            // 复制请求头（排除受限与 Hop-by-hop 头）
            val restricted = setOf(
                "host", "connection", "content-length", "date", "expect", "upgrade", "trailer", "te"
            )
            exchange.requestHeaders.forEach { (key, values) ->
                if (restricted.contains(key.lowercase())) return@forEach
                values.forEach { value -> requestBuilder.header(key, value) }
            }

            // 复制请求体
            val hasBody = method in listOf("POST", "PUT", "PATCH")
            val bodyPublisher = if (hasBody) {
                val bytes = exchange.requestBody.readAllBytes()
                java.net.http.HttpRequest.BodyPublishers.ofByteArray(bytes)
            } else {
                java.net.http.HttpRequest.BodyPublishers.noBody()
            }
            requestBuilder.method(method, bodyPublisher)

            val response = client.send(requestBuilder.build(), java.net.http.HttpResponse.BodyHandlers.ofByteArray())

            // 复制响应头（过滤不安全与 CORS 相关头，CORS 在下方统一 set 保证唯一值）
            response.headers().map().forEach { (key, values) ->
                val k = key.lowercase()
                if (k != "transfer-encoding" && k != "content-length" &&
                    k != "access-control-allow-origin" && k != "access-control-allow-methods" &&
                    k != "access-control-allow-headers") {
                    values.forEach { value -> exchange.responseHeaders.add(key, value) }
                }
            }

            // 添加/覆盖 CORS 头，使用 set 确保不会出现重复值
            exchange.responseHeaders.set("Access-Control-Allow-Origin", "*")
            exchange.responseHeaders.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
            exchange.responseHeaders.set("Access-Control-Allow-Headers", "Content-Type, Authorization")

            val respBytes = response.body()
            exchange.sendResponseHeaders(response.statusCode(), respBytes.size.toLong())
            exchange.responseBody.use { it.write(respBytes) }

            output.publish(Forwarded(config.id, config.name, targetUrl, response.statusCode()))
        } catch (e: Exception) {
            // 在测试环境中降级为 warn，避免 TestLogger 对 error 级别抛出断言
            logForwardError(e)
            output.publish(ErrorOccurred(config.id, config.name, message("error.proxy.error"), e.message))
            sendErrorResponse(exchange, 502, "Bad Gateway: Unable to reach original server")
        }
    }

    /**
     * 发送错误响应
     */
    private fun sendErrorResponse(exchange: HttpExchange, statusCode: Int, message: String) {
        try {
            val errorJson = """{"error": "$message"}"""
            val responseBytes = errorJson.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json; charset=UTF-8")
            exchange.sendResponseHeaders(statusCode, responseBytes.size.toLong())
            exchange.responseBody.use { it.write(responseBytes) }
        } catch (e: Exception) {
            thisLogger().error("Error sending error response", e)
        }
    }

    private fun logForwardError(t: Throwable) {
        if (Env.isNoUi()) {
            thisLogger().warn("Error forwarding request", t)
        } else {
            thisLogger().error("Error forwarding request", t)
        }
    }
}
