package org.zhongmiao.interceptwave.ui

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.application.ApplicationManager
import org.zhongmiao.interceptwave.events.*
import org.zhongmiao.interceptwave.InterceptWaveBundle.message
import org.zhongmiao.interceptwave.services.ConsoleService
import org.zhongmiao.interceptwave.services.ConfigService

/**
 * UI 订阅者：订阅 MockServer 领域事件并输出到 Console。
 * 放在 ui 包中，纯渲染层，不参与业务逻辑与覆盖率统计。
 */
@Service(Service.Level.PROJECT)
class MockServerConsoleSubscriber(private val project: Project) : com.intellij.openapi.Disposable {

    private val console: ConsoleService by lazy { project.service<ConsoleService>() }
    private val configService: ConfigService by lazy { project.service<ConfigService>() }
    private val mockServerService: org.zhongmiao.interceptwave.services.MockServerService by lazy { project.service<org.zhongmiao.interceptwave.services.MockServerService>() }

    init {
        // 订阅消息总线事件，连接与 Project 生命周期绑定，避免重复注册/泄漏
        project.messageBus.connect(this).subscribe(MOCK_SERVER_TOPIC, MockServerEventListener { event -> // UI 更新需在 EDT 上执行
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
                console.printInfo(message("console.starting", event.name))
                console.printInfo(message("console.port", event.port))
                // 附加详细配置
                runCatching {
                    val cfg = configService.getProxyGroup(event.configId)
                    if (cfg != null) {
                        console.printInfo(message("console.prefix", cfg.interceptPrefix))
                        console.printInfo(message("console.baseurl", cfg.baseUrl))
                        console.printInfo(message("console.stripprefix", cfg.stripPrefix))
                    }
                }
            }
            is ServerStarted -> {
                console.printSuccess(message("console.started", event.name))
                console.printSuccess(message("console.access.url", event.url))
                // 输出 Mock 接口启用情况
                runCatching {
                    val cfg = configService.getProxyGroup(event.configId)
                    if (cfg != null) {
                        val total = cfg.mockApis.size
                        val enabled = cfg.mockApis.count { it.enabled }
                        console.printInfo(message("console.mockapis.enabled", enabled, total))
                    }
                }
                console.printSeparator()
            }
            is ServerStartFailed -> {
                console.printError(message("console.start.failed.reason", event.reason ?: "Unknown"))
            }
            is ServerStopped -> {
                console.printSeparator()
                console.printWarning(message("console.server.stopped", event.name))
                console.printSeparator()
                // 如果这是最后一个服务，结束虚拟进程，禁用 Stop 按钮。
                // 若为 Stop 动作引发的停止事件，消费一次抑制标志并跳过销毁。
                runCatching {
                    if (!console.consumeSuppressAutoTerminateOnce() &&
                        mockServerService.getRunningServers().isEmpty()
                    ) {
                        console.terminateConsoleProcess()
                    }
                }
            }
            is AllServersStarting -> {
                console.showConsole()
                // 不再清空，确保多服务并发启动时日志按序追加
                console.printInfo(message("console.startall"))
            }
            is AllServersStarted -> {
                console.printSeparator()
                console.printInfo(message("console.startall.done", event.success, event.total))
                console.printSeparator()
            }
            is AllServersStopped -> {
                console.printInfo(message("console.allstopped"))
                // Stop 动作产生的事件：消费抑制标志并跳过；否则销毁虚拟进程
                if (!console.consumeSuppressAutoTerminateOnce()) {
                    console.terminateConsoleProcess()
                }
            }
            is RequestReceived -> {
                console.printInfo(message("console.request", event.configName, event.method, event.path))
            }
            is MatchedPath -> {
                console.printInfo(message("console.matched.path", event.configName, event.path))
            }
            is ForwardingTo -> {
                console.printInfo(message("console.forwarding.to", event.configName, event.targetUrl))
            }
            is MockMatched -> {
                console.printSuccess(message("console.mock.matched", event.configName, event.statusCode))
            }
            is Forwarded -> {
                console.printSuccess(message("console.forwarded", event.configName, event.statusCode))
            }
            is ErrorOccurred -> {
                console.printError("[${event.configName ?: ""}]   ✗ ${event.message}${event.details?.let { ": $it" } ?: ""}")
            }
        }
    }

    override fun dispose() {
        // No-op: messageBus connection is bound to this Disposable via connect(this)
        // and will be disposed automatically.
    }
}
