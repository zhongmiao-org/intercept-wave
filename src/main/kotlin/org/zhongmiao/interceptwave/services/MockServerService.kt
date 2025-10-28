package org.zhongmiao.interceptwave.services

import org.zhongmiao.interceptwave.model.MockApiConfig
import org.zhongmiao.interceptwave.model.ProxyConfig
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.BindException
import java.net.HttpURLConnection
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

    private val configService: ConfigService by lazy { project.service<ConfigService>() }
    private val consoleService: ConsoleService by lazy { project.service<ConsoleService>() }

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
            consoleService.showConsole()
            consoleService.printError("配置组不存在: $configId")
            return false
        }

        if (!proxyConfig.enabled) {
            thisLogger().warn("Config $configId is disabled")
            consoleService.showConsole()
            consoleService.printWarning("配置组「${proxyConfig.name}」已禁用")
            return false
        }

        return try {
            // 显示控制台窗口
            consoleService.showConsole()

            // 检查端口是否已被占用
            if (isPortOccupied(proxyConfig.port)) {
                consoleService.printError("端口 ${proxyConfig.port} 已被占用，无法启动「${proxyConfig.name}」")
                return false
            }

            // 打印启动信息
            consoleService.printSeparator()
            consoleService.printInfo("正在启动: 「${proxyConfig.name}」")
            consoleService.printInfo("端口: ${proxyConfig.port}")
            consoleService.printInfo("拦截前缀: ${proxyConfig.interceptPrefix}")
            consoleService.printInfo("目标地址: ${proxyConfig.baseUrl}")
            consoleService.printInfo("剥离前缀: ${proxyConfig.stripPrefix}")

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

            consoleService.printSuccess("✓ 「${proxyConfig.name}」启动成功!")
            consoleService.printSuccess("✓ 访问地址: $serverUrl")
            consoleService.printInfo("Mock APIs: ${proxyConfig.mockApis.count { it.enabled }}/${proxyConfig.mockApis.size} 已启用")
            consoleService.printSeparator()

            true
        } catch (e: BindException) {
            thisLogger().error("Port ${proxyConfig.port} is already in use", e)
            consoleService.printError("端口 ${proxyConfig.port} 已被占用")
            false
        } catch (e: Exception) {
            thisLogger().error("Failed to start server for config: ${proxyConfig.name}", e)
            consoleService.printError("启动失败: ${e.message}")
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

            try {
                consoleService.printSeparator()
                consoleService.printWarning("「$configName」已停止")
                consoleService.printSeparator()
            } catch (e: Exception) {
                // Ignore console errors if service is disposed
                thisLogger().debug("Console service unavailable during server stop", e)
            }
        }
    }

    /**
     * 启动所有已启用的配置组
     */
    fun startAllServers(): Map<String, Boolean> {
        val results = mutableMapOf<String, Boolean>()
        val enabledConfigs = configService.getEnabledProxyGroups()

        if (enabledConfigs.isEmpty()) {
            consoleService.printWarning("没有启用的配置组")
            return results
        }

        consoleService.showConsole()
        consoleService.clear()
        consoleService.printInfo("正在启动所有配置组...")

        enabledConfigs.forEach { config ->
            results[config.id] = startServer(config.id)
        }

        val successCount = results.values.count { it }
        consoleService.printSeparator()
        consoleService.printInfo("启动完成: $successCount/${enabledConfigs.size} 个配置组成功启动")
        consoleService.printSeparator()

        return results
    }

    /**
     * 停止所有服务器
     */
    fun stopAllServers() {
        val configIds = serverInstances.keys.toList()
        if (configIds.isEmpty()) {
            return
        }

        try {
            consoleService.printInfo("正在停止所有服务器...")
        } catch (e: Exception) {
            // Ignore console errors if service is disposed
            thisLogger().debug("Console service unavailable during stopAllServers", e)
        }

        configIds.forEach { stopServer(it) }

        try {
            consoleService.printInfo("所有服务器已停止")
        } catch (e: Exception) {
            // Ignore console errors if service is disposed
            thisLogger().debug("Console service unavailable during stopAllServers", e)
        }
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
        return "http://localhost:${config.port}"
    }

    /**
     * 获取所有运行中的服务器
     */
    fun getRunningServers(): List<Pair<String, String>> {
        return serverStatus.filter { it.value }.mapNotNull { (configId, _) ->
            val config = configService.getProxyGroup(configId)
            if (config != null) {
                configId to "http://localhost:${config.port}"
            } else {
                null
            }
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
            consoleService.printInfo("[${config.name}] ➤ $method $requestPath")

            // 处理根路径访问
            if (requestPath == "/" || requestPath.isEmpty()) {
                handleProxyWelcomePage(exchange, config)
                return
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

            consoleService.printDebug("[${config.name}]   匹配路径: $matchPath")

            // 查找匹配的Mock配置
            val mockApi = findMatchingMockApiInProxy(matchPath, method, config)

            if (mockApi != null && mockApi.enabled) {
                // 使用Mock数据响应
                handleProxyMockResponse(exchange, mockApi, config)
            } else {
                // 转发到原始服务器
                forwardToOriginalServerProxy(exchange, config)
            }
        } catch (e: Exception) {
            thisLogger().error("[${config.name}] Error handling request", e)
            consoleService.printError("[${config.name}] 请求处理错误: ${e.message}")
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

            val welcomeJson = """
                {
                  "status": "running",
                  "message": "Intercept Wave Mock 服务运行中",
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
                    "description": "访问配置的 Mock 接口路径即可获取 Mock 数据",
                    "example": "GET http://localhost:${config.port}${config.interceptPrefix}/your-api-path"
                  },
                  "apis": [
                    ${config.mockApis.joinToString(",\n    ") { api ->
                        """{"path": "${api.path}", "method": "${api.method}", "enabled": ${api.enabled}}"""
                    }}
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

            // 设置响应头
            exchange.responseHeaders.add("Content-Type", "application/json; charset=UTF-8")
            exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
            exchange.responseHeaders.add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
            exchange.responseHeaders.add("Access-Control-Allow-Headers", "Content-Type, Authorization")

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

            val delayInfo = if (mockApi.delay > 0) " (${mockApi.delay}ms delay)" else ""
            consoleService.printSuccess("[${config.name}]   ← ${mockApi.statusCode} Mock$delayInfo")
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

            consoleService.printDebug("[${config.name}]   → 转发至: $targetUrl")

            val connection = URI(targetUrl).toURL().openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.doInput = true
            connection.doOutput = method in listOf("POST", "PUT", "PATCH")
            connection.connectTimeout = 10000
            connection.readTimeout = 30000

            // 复制请求头
            exchange.requestHeaders.forEach { (key, values) ->
                if (key.equals("Host", ignoreCase = true)) return@forEach
                values.forEach { value ->
                    connection.setRequestProperty(key, value)
                }
            }

            // 复制请求体
            if (connection.doOutput) {
                exchange.requestBody.use { input ->
                    connection.outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
            }

            // 获取响应
            val responseCode = connection.responseCode
            val responseStream = if (responseCode < 400) connection.inputStream else connection.errorStream

            // 复制响应头
            connection.headerFields.forEach { (key, values) ->
                if (key != null && !key.equals("Transfer-Encoding", ignoreCase = true) &&
                    !key.equals("Content-Length", ignoreCase = true)) {
                    values.forEach { value ->
                        exchange.responseHeaders.add(key, value)
                    }
                }
            }

            // 添加CORS头
            exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
            exchange.responseHeaders.add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
            exchange.responseHeaders.add("Access-Control-Allow-Headers", "Content-Type, Authorization")

            // 发送响应
            val responseBytes = responseStream.readBytes()
            exchange.sendResponseHeaders(responseCode, responseBytes.size.toLong())
            exchange.responseBody.use { it.write(responseBytes) }

            consoleService.printSuccess("[${config.name}]   ← $responseCode Proxied")
        } catch (e: Exception) {
            thisLogger().error("Error forwarding request", e)
            consoleService.printError("[${config.name}]   ✗ 代理错误: ${e.message}")
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
}
