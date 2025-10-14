package org.zhongmiao.interceptwave.services

import org.zhongmiao.interceptwave.model.MockApiConfig
import org.zhongmiao.interceptwave.model.MockConfig
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.Executors

/**
 * Mock服务器服务
 * 负责启动/停止HTTP Mock服务器，处理请求拦截和转发
 */
@Service(Service.Level.PROJECT)
class MockServerService(private val project: Project) {

    private var server: HttpServer? = null
    private var isRunning = false
    private val configService: ConfigService by lazy { project.service<ConfigService>() }

    /**
     * 启动Mock服务器
     */
    fun start(): Boolean {
        if (isRunning) {
            thisLogger().warn("Mock server is already running")
            return false
        }

        return try {
            val config = configService.getConfig()
            server = HttpServer.create(InetSocketAddress(config.port), 0).apply {
                createContext("/") { exchange ->
                    handleRequest(exchange, config)
                }
                executor = Executors.newFixedThreadPool(10)
                start()
            }
            isRunning = true
            thisLogger().info("Mock server started on port ${config.port}")
            true
        } catch (e: Exception) {
            thisLogger().error("Failed to start mock server", e)
            false
        }
    }

    /**
     * 停止Mock服务器
     */
    fun stop() {
        server?.stop(0)
        server = null
        isRunning = false
        thisLogger().info("Mock server stopped")
    }

    /**
     * 检查服务器是否运行中
     */
    fun isRunning(): Boolean = isRunning

    /**
     * 获取服务器地址
     */
    fun getServerUrl(): String? {
        val config = configService.getConfig()
        return if (isRunning) "http://localhost:${config.port}" else null
    }

    /**
     * 处理HTTP请求
     */
    private fun handleRequest(exchange: HttpExchange, config: MockConfig) {
        try {
            val requestPath = exchange.requestURI.path
            val method = exchange.requestMethod

            thisLogger().info("Received request: $method $requestPath")

            // 处理根路径访问，返回欢迎页面
            if (requestPath == "/" || requestPath.isEmpty()) {
                handleWelcomePage(exchange, config)
                return
            }

            // 如果启用了stripPrefix，则为请求路径添加前缀以匹配Mock配置
            // 例如：stripPrefix=true, interceptPrefix="/api"
            // 请求 /user/info 将被转换为 /api/user/info 来匹配
            val matchPath = if (config.stripPrefix && config.interceptPrefix.isNotEmpty()) {
                if (!requestPath.startsWith(config.interceptPrefix)) {
                    config.interceptPrefix + requestPath
                } else {
                    requestPath
                }
            } else {
                requestPath
            }

            thisLogger().info("Match path: $matchPath (stripPrefix=${config.stripPrefix})")

            // 检查是否有匹配的Mock配置
            val mockApi = findMatchingMockApi(matchPath, method, config)

            if (mockApi != null && mockApi.enabled) {
                // 使用Mock数据响应
                handleMockResponse(exchange, mockApi, config)
            } else {
                // 转发到原始接口
                forwardToOriginalServer(exchange, config)
            }
        } catch (e: Exception) {
            thisLogger().error("Error handling request", e)
            sendErrorResponse(exchange, 500, "Internal Server Error: ${e.message}")
        }
    }

    /**
     * 处理欢迎页面
     */
    private fun handleWelcomePage(exchange: HttpExchange, config: MockConfig) {
        try {
            val mockApiCount = config.mockApis.size
            val enabledApiCount = config.mockApis.count { it.enabled }

            val welcomeJson = """
                {
                  "status": "running",
                  "message": "Intercept Wave Mock 服务运行中",
                  "server": {
                    "port": ${config.port},
                    "baseUrl": "${config.baseUrl}",
                    "interceptPrefix": "${config.interceptPrefix}"
                  },
                  "mockApis": {
                    "total": $mockApiCount,
                    "enabled": $enabledApiCount
                  },
                  "usage": {
                    "description": "访问配置的 Mock 接口路径即可获取 Mock 数据",
                    "example": "GET http://localhost:${config.port}${config.interceptPrefix}/your-api-path",
                    "configPath": "请在 IntelliJ IDEA 的 Intercept Wave 工具窗口中配置 Mock 接口"
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

            thisLogger().info("Served welcome page")
        } catch (e: Exception) {
            thisLogger().error("Error serving welcome page", e)
            sendErrorResponse(exchange, 500, "Error serving welcome page")
        }
    }

    /**
     * 查找匹配的Mock API配置
     */
    private fun findMatchingMockApi(requestPath: String, method: String, config: MockConfig): MockApiConfig? {
        return config.mockApis.find { api ->
            api.enabled && api.path == requestPath && (api.method == "ALL" || api.method.equals(method, ignoreCase = true))
        }
    }

    /**
     * 处理Mock响应
     */
    private fun handleMockResponse(exchange: HttpExchange, mockApi: MockApiConfig, config: MockConfig) {
        try {
            // 模拟延迟
            if (mockApi.delay > 0) {
                Thread.sleep(mockApi.delay)
            }

            // 如果启用了全局Cookie且Cookie不为空，添加Set-Cookie响应头
            if (mockApi.useCookie && config.globalCookie.isNotEmpty()) {
                exchange.responseHeaders.add("Set-Cookie", config.globalCookie)
            }

            // 设置默认Content-Type为JSON
            if (!exchange.responseHeaders.containsKey("Content-Type")) {
                exchange.responseHeaders.add("Content-Type", "application/json; charset=UTF-8")
            }

            // 添加CORS头
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

            thisLogger().info("Responded with mock data for: ${mockApi.path}")
        } catch (e: Exception) {
            thisLogger().error("Error sending mock response", e)
            sendErrorResponse(exchange, 500, "Error sending mock response")
        }
    }

    /**
     * 转发请求到原始服务器
     */
    private fun forwardToOriginalServer(exchange: HttpExchange, config: MockConfig) {
        try {
            val requestPath = exchange.requestURI.toString()
            val targetUrl = config.baseUrl + requestPath
            val method = exchange.requestMethod

            thisLogger().info("Forwarding request to: $targetUrl")

            val connection = URI(targetUrl).toURL().openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.doInput = true
            connection.doOutput = method in listOf("POST", "PUT", "PATCH")

            // 复制请求头（保留原始User-Agent）
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
                if (key != null) {
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

            thisLogger().info("Forwarded response from original server: $responseCode")
        } catch (e: Exception) {
            thisLogger().error("Error forwarding request", e)
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