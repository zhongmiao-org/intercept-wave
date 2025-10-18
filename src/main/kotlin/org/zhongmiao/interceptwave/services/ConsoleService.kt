package org.zhongmiao.interceptwave.services

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.Service
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
        val console = getOrCreateConsole()
        val timestamp = dateFormat.format(Date())
        console.print("[$timestamp] ", ConsoleViewContentType.LOG_INFO_OUTPUT)
        console.print("$message\n", ConsoleViewContentType.NORMAL_OUTPUT)
    }

    /**
     * 打印成功日志（绿色）
     */
    fun printSuccess(message: String) {
        val console = getOrCreateConsole()
        val timestamp = dateFormat.format(Date())
        console.print("[$timestamp] ", ConsoleViewContentType.LOG_INFO_OUTPUT)
        console.print("✓ $message\n", ConsoleViewContentType.LOG_INFO_OUTPUT)
    }

    /**
     * 打印警告日志（黄色）
     */
    fun printWarning(message: String) {
        val console = getOrCreateConsole()
        val timestamp = dateFormat.format(Date())
        console.print("[$timestamp] ", ConsoleViewContentType.LOG_WARNING_OUTPUT)
        console.print("⚠ $message\n", ConsoleViewContentType.LOG_WARNING_OUTPUT)
    }

    /**
     * 打印错误日志（红色）
     */
    fun printError(message: String) {
        val console = getOrCreateConsole()
        val timestamp = dateFormat.format(Date())
        console.print("[$timestamp] ", ConsoleViewContentType.ERROR_OUTPUT)
        console.print("✗ $message\n", ConsoleViewContentType.ERROR_OUTPUT)
    }

    /**
     * 打印调试日志（灰色）
     */
    fun printDebug(message: String) {
        val console = getOrCreateConsole()
        val timestamp = dateFormat.format(Date())
        console.print("[$timestamp] ", ConsoleViewContentType.LOG_DEBUG_OUTPUT)
        console.print("  $message\n", ConsoleViewContentType.LOG_DEBUG_OUTPUT)
    }

    /**
     * 清空Console
     */
    fun clear() {
        consoleView?.clear()
    }

    /**
     * 打印分隔线
     */
    fun printSeparator() {
        val console = getOrCreateConsole()
        console.print("${"=".repeat(80)}\n", ConsoleViewContentType.SYSTEM_OUTPUT)
    }
}