package org.zhongmiao.interceptwave.ui

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.application.ApplicationManager
import org.zhongmiao.interceptwave.events.*
import org.zhongmiao.interceptwave.services.ConsoleService
import org.zhongmiao.interceptwave.services.ConfigService

/**
 * UI 订阅者：订阅 MockServer 领域事件并输出到 Console。
 * 放在 ui 包中，纯渲染层，不参与业务逻辑与覆盖率统计。
 */
@Service(Service.Level.PROJECT)
class MockServerConsoleSubscriber(private val project: Project) {

    private val console: ConsoleService by lazy { project.service<ConsoleService>() }
    private val configService: ConfigService by lazy { project.service<ConfigService>() }

    init {
        // 订阅消息总线事件，连接与 Project 生命周期绑定，避免重复注册/泄漏
        project.messageBus.connect(project).subscribe(MOCK_SERVER_TOPIC, MockServerEventListener { event -> // UI 更新需在 EDT 上执行
            runOnEdt {
                handle(event)
            }
        })
    }

    private fun runOnEdt(block: () -> Unit) {
        val app = ApplicationManager.getApplication()
        if (app.isDispatchThread) block() else app.invokeLater { block() }
    }

    /**
     * 将领域事件映射为 Console 输出（仅展示，不含任何业务判断）。
     */
    private fun handle(event: MockServerEvent) {
        when (event) {
            is ServerStarting -> {
                console.showConsole()
                console.printSeparator()
                console.printInfo("正在启动: 「${event.name}」")
                console.printInfo("端口: ${event.port}")
                // 附加详细配置
                runCatching {
                    val cfg = configService.getProxyGroup(event.configId)
                    if (cfg != null) {
                        console.printInfo("拦截前缀: ${cfg.interceptPrefix}")
                        console.printInfo("目标地址: ${cfg.baseUrl}")
                        console.printInfo("剥离前缀: ${cfg.stripPrefix}")
                    }
                }
            }
            is ServerStarted -> {
                console.printSuccess("「${event.name}」启动成功!")
                console.printSuccess("访问地址: ${event.url}")
                // 输出 Mock 接口启用情况
                runCatching {
                    val cfg = configService.getProxyGroup(event.configId)
                    if (cfg != null) {
                        val total = cfg.mockApis.size
                        val enabled = cfg.mockApis.count { it.enabled }
                        console.printInfo("Mock APIs: ${enabled}/${total} 已启用")
                    }
                }
                console.printSeparator()
            }
            is ServerStartFailed -> {
                console.printError("启动失败: ${event.reason ?: "未知错误"}")
            }
            is ServerStopped -> {
                console.printSeparator()
                console.printWarning("「${event.name}」已停止")
                console.printSeparator()
            }
            is AllServersStarting -> {
                console.showConsole()
                // 不再清空，确保多服务并发启动时日志按序追加
                console.printInfo("正在启动所有配置组...")
            }
            is AllServersStarted -> {
                console.printSeparator()
                console.printInfo("启动完成: ${event.success}/${event.total} 个配置组成功启动")
                console.printSeparator()
            }
            is AllServersStopped -> {
                console.printInfo("所有服务器已停止")
            }
            is RequestReceived -> {
                console.printInfo("[${event.configName}] ➤ ${event.method} ${event.path}")
            }
            is MatchedPath -> {
                console.printInfo("  [${event.configName}]   匹配路径: ${event.path}")
            }
            is ForwardingTo -> {
                console.printInfo("  [${event.configName}]   → 转发至: ${event.targetUrl}")
            }
            is MockMatched -> {
                console.printSuccess("[${event.configName}]   ← ${event.statusCode} Mock")
            }
            is Forwarded -> {
                console.printSuccess("[${event.configName}]   ← ${event.statusCode} Proxied")
            }
            is ErrorOccurred -> {
                console.printError("[${event.configName ?: ""}]   ✗ ${event.message}${event.details?.let { ": $it" } ?: ""}")
            }
        }
    }
}
