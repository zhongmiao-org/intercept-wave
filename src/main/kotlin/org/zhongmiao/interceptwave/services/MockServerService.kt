package org.zhongmiao.interceptwave.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import org.zhongmiao.interceptwave.InterceptWaveBundle.message
import org.zhongmiao.interceptwave.events.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Mock服务器服务 - v2.0 支持多服务器实例
 * 负责启动/停止HTTP Mock服务器，处理请求拦截和转发
 */
@Service(Service.Level.PROJECT)
class MockServerService(private val project: Project) {

    // 多服务器实例管理（统一接口）
    private val engines = ConcurrentHashMap<String, ServerEngine>()

    private val configService: ConfigService by lazy { project.service<ConfigService>() }
    // 业务输出端口：面向事件发布，不直接依赖任何 UI
    private val output: MockServerOutput by lazy { project.service<MockServerEventPublisher>() }

    // ============ v2.0 新方法：多服务器管理 ============

    /**
     * 启动单个配置组的服务器
     */
    fun startServer(configId: String): Boolean {
        if (engines[configId]?.isRunning() == true) {
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

        return try {
            if (isPortOccupied(proxyConfig.port)) {
                output.publish(ServerStartFailed(configId, proxyConfig.name, proxyConfig.port, message("error.port.in.use")))
                return false
            }
            output.publish(ServerStarting(configId, proxyConfig.name, proxyConfig.port))
            val engine = EngineFactory.create(proxyConfig, output)
            val ok = engine.start()
            if (ok) {
                engines[configId] = engine
                val url = engine.getUrl()
                thisLogger().info("Server started for config: ${proxyConfig.name} on port ${proxyConfig.port}")
                output.publish(ServerStarted(configId, proxyConfig.name, proxyConfig.port, url))
                true
            } else {
                val reason = engine.lastError?.takeIf { it.isNotBlank() } ?: (if (proxyConfig.protocol.equals("WS", true)) "WS engine start failed" else "HTTP engine start failed")
                output.publish(ServerStartFailed(configId, proxyConfig.name, proxyConfig.port, reason))
                false
            }
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
        val proxyConfig = configService.getProxyGroup(configId)
        val configName = proxyConfig?.name ?: configId
        val engine = engines[configId]
        if (engine != null) {
            runCatching { engine.stop() }
            engines.remove(configId)
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
        // Collect both HTTP and WS running IDs
        val configIds = engines.keys.toList()
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
    fun getServerStatus(configId: String): Boolean = engines[configId]?.isRunning() == true

    /**
     * 获取服务器 URL
     */
    fun getServerUrl(configId: String): String? {
        val engine = engines[configId] ?: return null
        return if (engine.isRunning()) engine.getUrl() else null
    }

    /**
     * 获取所有运行中的服务器
     */
    fun getRunningServers(): List<Pair<String, String>> {
        return engines.entries.mapNotNull { (configId, engine) ->
            if (engine.isRunning()) configId to engine.getUrl() else null
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
        val engine = engines[configId] as? org.zhongmiao.interceptwave.services.ws.WsServerEngine
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

    // （HTTP 请求处理已迁移至 HttpServerEngine）
}
