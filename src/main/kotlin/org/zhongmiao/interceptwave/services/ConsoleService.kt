package org.zhongmiao.interceptwave.services

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessEvent
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.JBColor
import org.zhongmiao.interceptwave.InterceptWaveBundle.message
import org.zhongmiao.interceptwave.util.Env
import java.text.SimpleDateFormat
import java.util.Date
import java.io.OutputStream

/**
 * Console服务
 * 负责管理Console视图和日志输出，使用IDEA原生Run工具窗口
 */
@Service(Service.Level.PROJECT)
class ConsoleService(private val project: Project) {

    private var consoleView: ConsoleView? = null
    private var contentDescriptor: RunContentDescriptor? = null
    private var processHandler: ProcessHandler? = null
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS")
    // 标记：是否由 IDE Stop 动作触发
    private var stopActionInProgress: Boolean = false
    // 抑制一次事件侧的自动销毁（Stop 动作引发的停止事件到达时不再重复销毁）
    private var suppressAutoTerminateOnce: Boolean = false

    // Colorized console content types
    private val infoType by lazy { createContentType("IW_INFO", JBColor(0x2E86C1, 0x5394EC)) }
    private val successType by lazy { createContentType("IW_SUCCESS", JBColor(0x2E7D32, 0x6A8759)) }
    private val warnType by lazy { createContentType("IW_WARN", JBColor(0xB26A00, 0xCC7832)) }
    private val debugType by lazy { createContentType("IW_DEBUG", JBColor(0x666666, 0x8A8A8A)) }
    private val tsType by lazy { createContentType("IW_TS", JBColor(0x888888, 0x787878)) }

    private fun createContentType(name: String, fg: JBColor): ConsoleViewContentType {
        val attrs = TextAttributes()
        attrs.foregroundColor = fg
        return ConsoleViewContentType(name, attrs)
    }

    /**
     * 终止绑定的虚拟进程，从而让 IDE 的 Stop 动作禁用。
     * 用于当所有服务停止时，主动结束“运行中”状态。
     */
    fun terminateConsoleProcess() {
        if (Env.isUnitTestMode()) return
        if (stopActionInProgress) return
        val ph = processHandler
        if (ph != null && !ph.isProcessTerminated) {
            ph.destroyProcess()

        }
    }

    fun consumeSuppressAutoTerminateOnce(): Boolean {
        val v = suppressAutoTerminateOnce
        suppressAutoTerminateOnce = false
        return v
    }

    /**
     * 获取或创建Console视图
     */
    private fun getOrCreateConsole(): ConsoleView {
        if (consoleView == null) {
            consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
        }
        return consoleView!!
    }

    /**
     * 确保 Console 绑定了一个 ProcessHandler，并处于“运行中”状态。
     * 未绑定或已结束时，创建一个轻量的虚拟进程并 startNotify()。
     */
    private fun ensureProcessAttached(console: ConsoleView) {
        val needNew = processHandler == null || processHandler!!.isProcessTerminated
        if (needNew) {
            processHandler = object : ProcessHandler() {
                override fun destroyProcessImpl() {
                    // 由 IDE Stop 按钮触发：先输出日志并停止所有服务
                    stopActionInProgress = true
                    suppressAutoTerminateOnce = true
                    runCatching {
                        this@ConsoleService.printInfo(message("console.stopping.all.from.stop"))
                        project.getService(MockServerService::class.java).stopAllServers()
                    }
                    notifyProcessTerminated(0)
                }

                override fun detachProcessImpl() {
                    notifyProcessDetached()
                }

                override fun detachIsDefault(): Boolean = false

                override fun getProcessInput(): OutputStream? = null
            }
            val ph = processHandler!!
            console.attachToProcess(ph)
            // 更新 RunContentDescriptor 的 ProcessHandler 以正确联动 Stop 按钮
            runCatching { contentDescriptor?.processHandler = ph }
            // 监听进程终止：重置状态（不清空 Console 视图，保留停止日志可见）
            ph.addProcessListener(object : ProcessListener {
                override fun processTerminated(event: ProcessEvent) {
                    processHandler = null
                    stopActionInProgress = false
                    runCatching { contentDescriptor?.processHandler = null }
                }
            })
            ph.startNotify()
        }
    }

    /**
     * 显示Console窗口（使用IDEA原生Run工具窗口）
     */
    fun showConsole() {
        // 单元测试模式下不创建 UI 组件，避免 Editor 资源泄漏
        if (Env.isUnitTestMode()) return

        // 若 Run 标签被关闭或首次打开，重建 Console 与 Descriptor
        val needRecreate = consoleView == null || (contentDescriptor?.component?.isDisplayable != true)
        if (needRecreate) {
            consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
            val console = consoleView!!
            contentDescriptor = RunContentDescriptor(
                console,
                processHandler,
                console.component,
                message("console.descriptor.title"),
                AllIcons.Debugger.Console
            )
        }

        // 关键：确保绑定 ProcessHandler，避免 Run 控制台被判定为已结束而整屏绿色
        ensureProcessAttached(consoleView!!)

        // 如果内容未显示（或被关闭），重新添加
        if (contentDescriptor?.component?.isDisplayable != true) {
            val executor = DefaultRunExecutor.getRunExecutorInstance()
            RunContentManager.getInstance(project).showRunContent(executor, contentDescriptor!!)
        }

        // 显示并激活 Run 工具窗口，确保用户可见
        ToolWindowManager.getInstance(project)
            .getToolWindow(ToolWindowId.RUN)
            ?.show(null)
    }

    /**
     * 打印信息日志
     */
    fun printInfo(message: String) {
        if (Env.isUnitTestMode()) {
            thisLogger().info(message)
            return
        }
        val console = getOrCreateConsole()
        val timestamp = dateFormat.format(Date())
        // 彩色输出：时间戳弱化，正文蓝色
        console.print("[$timestamp] ", tsType)
        console.print("$message\n", infoType)
    }

    /**
     * 打印成功日志（绿色）
     */
    fun printSuccess(message: String) {
        if (Env.isUnitTestMode()) {
            thisLogger().info("SUCCESS: $message")
            return
        }
        val console = getOrCreateConsole()
        val timestamp = dateFormat.format(Date())
        // 绿色成功
        console.print("[$timestamp] ", tsType)
        console.print("✓ $message\n", successType)
    }

    /**
     * 打印警告日志（黄色）
     */
    fun printWarning(message: String) {
        if (Env.isUnitTestMode()) {
            thisLogger().warn(message)
            return
        }
        val console = getOrCreateConsole()
        val timestamp = dateFormat.format(Date())
        // 橙色警告
        console.print("[$timestamp] ", tsType)
        console.print("⚠ $message\n", warnType)
    }

    /**
     * 打印错误日志（红色）
     */
    fun printError(message: String) {
        if (Env.isUnitTestMode()) {
            // 避免 TestLogger 在单测中因 error 级别抛出断言
            thisLogger().warn("ERROR: $message")
            return
        }
        val console = getOrCreateConsole()
        val timestamp = dateFormat.format(Date())
        console.print("[$timestamp] ", tsType)
        console.print("✗ $message\n", ConsoleViewContentType.ERROR_OUTPUT)
    }

    /**
     * 打印调试日志（灰色）
     */
    fun printDebug(message: String) {
        if (Env.isUnitTestMode()) {
            thisLogger().debug(message)
            return
        }
        val console = getOrCreateConsole()
        val timestamp = dateFormat.format(Date())
        // 灰色调试
        console.print("[$timestamp] ", tsType)
        console.print("  $message\n", debugType)
    }

    /**
     * 清空Console
     */
    fun clear() {
        if (Env.isUnitTestMode()) return
        consoleView?.clear()
    }

    /**
     * 打印分隔线
     */
    fun printSeparator() {
        if (Env.isUnitTestMode()) return
        val console = getOrCreateConsole()
        // 分隔符使用弱化颜色
        console.print("${"=".repeat(80)}\n", tsType)
    }

}
