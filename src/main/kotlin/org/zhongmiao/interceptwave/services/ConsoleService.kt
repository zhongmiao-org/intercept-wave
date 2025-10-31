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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import org.zhongmiao.interceptwave.InterceptWaveBundle.message
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
            // 监听 Run 标签被关闭后用户选择“Terminate”的情况：联动停止所有服务并重置控制台状态
            ph.addProcessListener(object : ProcessListener {
                override fun processTerminated(event: ProcessEvent) {
                    // 停止所有服务器并重置内部状态，以便下次启动时重新唤起
                    runCatching {
                        project.getService(MockServerService::class.java)
                            .stopAllServers()
                    }
                    contentDescriptor = null
                    processHandler = null
                    consoleView = null
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
        if (isUnitTestMode()) return

        // 只在首次创建内容，后续仅激活，避免重复 Run 标签
        val console = getOrCreateConsole()
        // 关键：确保绑定 ProcessHandler，避免 Run 控制台被判定为已结束而整屏绿色
        ensureProcessAttached(console)
        var created = false
        if (contentDescriptor == null) {
            contentDescriptor = RunContentDescriptor(
                console,
                processHandler,
                console.component,
                message("console.descriptor.title"),
                AllIcons.Debugger.Console
            )
            created = true
        }

        // 首次添加，或者之前被用户手动关闭（组件不再可显示）时重新添加
        val needAdd = created || (contentDescriptor?.component?.isDisplayable != true)
        if (needAdd) {
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
        if (isUnitTestMode()) {
            thisLogger().info(message)
            return
        }
        val console = getOrCreateConsole()
        ensureProcessAttached(console)
        val timestamp = dateFormat.format(Date())
        // 使用 NORMAL_OUTPUT，避免因主题差异导致整屏染色
        console.print("[$timestamp] ", ConsoleViewContentType.NORMAL_OUTPUT)
        console.print("$message\n", ConsoleViewContentType.NORMAL_OUTPUT)
    }

    /**
     * 打印成功日志（绿色）
     */
    fun printSuccess(message: String) {
        if (isUnitTestMode()) {
            thisLogger().info("SUCCESS: $message")
            return
        }
        val console = getOrCreateConsole()
        ensureProcessAttached(console)
        val timestamp = dateFormat.format(Date())
        // 使用 NORMAL_OUTPUT 展示成功消息，保持中性配色
        console.print("[$timestamp] ", ConsoleViewContentType.NORMAL_OUTPUT)
        console.print("✓ $message\n", ConsoleViewContentType.NORMAL_OUTPUT)
    }

    /**
     * 打印警告日志（黄色）
     */
    fun printWarning(message: String) {
        if (isUnitTestMode()) {
            thisLogger().warn(message)
            return
        }
        val console = getOrCreateConsole()
        ensureProcessAttached(console)
        val timestamp = dateFormat.format(Date())
        // 警告采用 NORMAL 输出（依然带有 ⚠ 提示符）
        console.print("[$timestamp] ", ConsoleViewContentType.NORMAL_OUTPUT)
        console.print("⚠ $message\n", ConsoleViewContentType.NORMAL_OUTPUT)
    }

    /**
     * 打印错误日志（红色）
     */
    fun printError(message: String) {
        if (isUnitTestMode()) {
            // 避免 TestLogger 在单测中因 error 级别抛出断言
            thisLogger().warn("ERROR: $message")
            return
        }
        val console = getOrCreateConsole()
        ensureProcessAttached(console)
        val timestamp = dateFormat.format(Date())
        console.print("[$timestamp] ", ConsoleViewContentType.ERROR_OUTPUT)
        console.print("✗ $message\n", ConsoleViewContentType.ERROR_OUTPUT)
    }

    /**
     * 打印调试日志（灰色）
     */
    fun printDebug(message: String) {
        if (isUnitTestMode()) {
            thisLogger().debug(message)
            return
        }
        val console = getOrCreateConsole()
        val timestamp = dateFormat.format(Date())
        // 调试输出采用 NORMAL，避免主题统一上色
        console.print("[$timestamp] ", ConsoleViewContentType.NORMAL_OUTPUT)
        console.print("  $message\n", ConsoleViewContentType.NORMAL_OUTPUT)
    }

    /**
     * 清空Console
     */
    fun clear() {
        if (isUnitTestMode()) return
        consoleView?.clear()
    }

    /**
     * 打印分隔线
     */
    fun printSeparator() {
        if (isUnitTestMode()) return
        val console = getOrCreateConsole()
        ensureProcessAttached(console)
        // 分隔符采用 NORMAL 输出
        console.print("${"=".repeat(80)}\n", ConsoleViewContentType.NORMAL_OUTPUT)
    }

    private fun isUnitTestMode(): Boolean = try {
        ApplicationManager.getApplication()?.isUnitTestMode == true
    } catch (_: Throwable) {
        false
    }
}
