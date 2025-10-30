package org.zhongmiao.interceptwave.services

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Console服务
 * 负责管理Console视图和日志输出，使用IDEA原生Run工具窗口
 */
@Service(Service.Level.PROJECT)
class ConsoleService(private val project: Project) {

    private var consoleView: ConsoleView? = null
    private var contentDescriptor: RunContentDescriptor? = null
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
     * 显示Console窗口（使用IDEA原生Run工具窗口）
     */
    fun showConsole() {
        // 单元测试模式下不创建 UI 组件，避免 Editor 资源泄漏
        if (isUnitTestMode()) return

        // 重新创建 Console 和 RunContentDescriptor（解决窗口关闭后重启显示空白的问题）
        consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).console

        contentDescriptor = RunContentDescriptor(
            consoleView,
            null,  // processHandler
            consoleView!!.component,
            "Intercept Wave Mock Server",
            AllIcons.Debugger.Console
        )

        // 显示在 Run 工具窗口
        val executor = DefaultRunExecutor.getRunExecutorInstance()
        RunContentManager.getInstance(project).showRunContent(executor, contentDescriptor!!)
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
        val timestamp = dateFormat.format(Date())
        console.print("[$timestamp] ", ConsoleViewContentType.LOG_INFO_OUTPUT)
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
        val timestamp = dateFormat.format(Date())
        console.print("[$timestamp] ", ConsoleViewContentType.LOG_INFO_OUTPUT)
        console.print("✓ $message\n", ConsoleViewContentType.LOG_INFO_OUTPUT)
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
        val timestamp = dateFormat.format(Date())
        console.print("[$timestamp] ", ConsoleViewContentType.LOG_WARNING_OUTPUT)
        console.print("⚠ $message\n", ConsoleViewContentType.LOG_WARNING_OUTPUT)
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
        console.print("[$timestamp] ", ConsoleViewContentType.LOG_DEBUG_OUTPUT)
        console.print("  $message\n", ConsoleViewContentType.LOG_DEBUG_OUTPUT)
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
        console.print("${"=".repeat(80)}\n", ConsoleViewContentType.SYSTEM_OUTPUT)
    }

    private fun isUnitTestMode(): Boolean = try {
        ApplicationManager.getApplication()?.isUnitTestMode == true
    } catch (_: Throwable) {
        false
    }
}
