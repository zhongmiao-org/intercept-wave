package org.zhongmiao.interceptwave.ui

import org.zhongmiao.interceptwave.InterceptWaveBundle.message
import org.zhongmiao.interceptwave.services.ConfigService
import org.zhongmiao.interceptwave.services.MockServerService
import org.zhongmiao.interceptwave.services.ConsoleService
import org.zhongmiao.interceptwave.events.*
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTabbedPane
import java.awt.BorderLayout
import javax.swing.SwingUtilities
import javax.swing.*

@Suppress("unused")
class InterceptWaveToolWindow(private val project: Project) {

        private val mockServerService = project.service<MockServerService>()
        private val consoleService = project.service<ConsoleService>()
        private val configService = project.service<ConfigService>()

        // 使用标签页来切换不同的配置组
        private val tabbedPane = JBTabbedPane()
        private val tabPanels = mutableMapOf<String, ProxyGroupTabPanel>()

        // 全局按钮引用，用于状态管理
        private lateinit var startAllButton: JButton
        private lateinit var stopAllButton: JButton

        init {
            setupTabs()
            // 订阅领域事件，实时刷新 ToolWindow（仅 UI 更新，无业务逻辑）
            project.messageBus.connect().subscribe(MOCK_SERVER_TOPIC, MockServerEventListener { event ->
                SwingUtilities.invokeLater {
                    when (event) {
                        is ServerStarted -> {
                            tabPanels[event.configId]?.updateStatus(true, mockServerService.getServerUrl(event.configId))
                            updateGlobalButtonStates()
                        }
                        is ServerStopped -> {
                            tabPanels[event.configId]?.updateStatus(false, null)
                            updateGlobalButtonStates()
                        }
                        is AllServersStarted, is AllServersStopped, is AllServersStarting, is ServerStartFailed -> {
                            refreshAllTabs()
                        }
                        is ServerStarting -> updateGlobalButtonStates()
                        is ErrorOccurred,
                        is RequestReceived,
                        is Forwarded,
                        is MockMatched,
                        is ForwardingTo,
                        is MatchedPath,
                        // WebSocket runtime events do not affect ToolWindow buttons/tabs
                        is WebSocketClosed,
                        is WebSocketConnected,
                        is WebSocketConnecting,
                        is WebSocketError,
                        is WebSocketMessageIn,
                        is WebSocketMessageOut,
                        is WebSocketMockPushed -> { /* 无需状态变更 */ }
                    }
                }
            })
        }

        @Suppress("unused")
        fun getContent(): JComponent {
            val panel = JBPanel<JBPanel<*>>(BorderLayout(10, 10))
            panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

            // 顶部容器（仅放置右侧全局按钮，不再显示左侧标题）
            val titlePanel = JPanel(BorderLayout())

            // 全局操作按钮
            val globalButtonPanel = JPanel()
            globalButtonPanel.layout = BoxLayout(globalButtonPanel, BoxLayout.X_AXIS)

            startAllButton = createButton(message("toolwindow.button.startall"), AllIcons.Actions.RunAll) {
                mockServerService.startAllServers()
                refreshAllTabs()
            }

            stopAllButton = createButton(message("toolwindow.button.stopall"), AllIcons.Debugger.MuteBreakpoints) {
                // 通过 ConsoleService 的虚拟进程终止来统一停止逻辑，确保 IDE Stop 按钮一致联动
                consoleService.terminateConsoleProcess()
                refreshAllTabs()
            }

            val configButton = createButton(message("toolwindow.button.config"), AllIcons.General.Settings) {
                openConfigDialog()
            }

            globalButtonPanel.add(startAllButton)
            globalButtonPanel.add(Box.createHorizontalStrut(10))
            globalButtonPanel.add(stopAllButton)
            globalButtonPanel.add(Box.createHorizontalStrut(10))
            globalButtonPanel.add(configButton)

            titlePanel.add(globalButtonPanel, BorderLayout.EAST)

            panel.add(titlePanel, BorderLayout.NORTH)

            // 标签页
            panel.add(tabbedPane, BorderLayout.CENTER)

            // 初始化按钮状态
            updateGlobalButtonStates()

            return panel
        }

        /**
         * 创建按钮并设置样式
         */
        private fun createButton(text: String, icon: Icon, action: () -> Unit): JButton {
            return JButton(text, icon).apply {
                isFocusPainted = false
                isFocusable = false
                addActionListener { action() }
            }
        }

        /**
         * 更新全局按钮状态
         */
        private fun updateGlobalButtonStates() {
            val proxyGroups = configService.getAllProxyGroups()
            val enabledGroups = proxyGroups.filter { it.enabled }

            if (enabledGroups.isEmpty()) {
                startAllButton.isEnabled = false
                stopAllButton.isEnabled = false
                return
            }

            val runningServers = mockServerService.getRunningServers()
            val runningServerIds = runningServers.map { it.first }.toSet()
            val allEnabledRunning = enabledGroups.all { config ->
                config.id in runningServerIds
            }
            val allEnabledStopped = enabledGroups.none { config ->
                config.id in runningServerIds
            }

            startAllButton.isEnabled = !allEnabledRunning
            stopAllButton.isEnabled = !allEnabledStopped
        }

        /**
         * 设置标签页
         */
        private fun setupTabs() {
            // 移除所有监听器，避免重复添加
            tabbedPane.changeListeners.forEach { listener ->
                tabbedPane.removeChangeListener(listener)
            }

            tabbedPane.removeAll()
            tabPanels.clear()

            val proxyGroups = configService.getAllProxyGroups()

            if (proxyGroups.isEmpty()) {
                // 没有配置组，显示提示
                val emptyPanel = JPanel(BorderLayout())
                val label = JBLabel(message("toolwindow.empty.hint"))
                label.foreground = JBColor.GRAY
                label.horizontalAlignment = SwingConstants.CENTER
                emptyPanel.add(label, BorderLayout.CENTER)
                tabbedPane.addTab(message("toolwindow.empty.tab"), emptyPanel)
            } else {
                proxyGroups.forEach { config ->
                    val tabPanel = ProxyGroupTabPanel(
                        project,
                        config.id,
                        config.name,
                        config.port,
                        config.enabled,
                        onStatusChanged = { updateGlobalButtonStates() }
                    )
                    tabPanels[config.id] = tabPanel

                    // 添加标签页，标签名称显示配置组名称
                    tabbedPane.addTab(config.name, tabPanel.getPanel())
                }

                // 添加 "+" 标签用于快速新增配置
                val addPanel = JPanel(BorderLayout())
                val addLabel = JBLabel(message("toolwindow.add.hint"))
                addLabel.foreground = JBColor.GRAY
                addLabel.horizontalAlignment = SwingConstants.CENTER
                addPanel.add(addLabel, BorderLayout.CENTER)
                tabbedPane.addTab(null, AllIcons.General.Add, addPanel, message("toolwindow.add.hint"))

                // 监听 "+" 标签被点击
                tabbedPane.addChangeListener {
                    if (tabbedPane.selectedIndex == tabbedPane.tabCount - 1) {
                        // 点击了 "+" 标签
                        openConfigDialog()
                        // 切回到第一个标签
                        if (tabbedPane.tabCount > 1) {
                            tabbedPane.selectedIndex = 0
                        }
                    }
                }
            }
        }

        /**
         * 刷新所有标签页的状态
         */
        private fun refreshAllTabs() {
            tabPanels.forEach { (configId, panel) ->
                val isRunning = mockServerService.getServerStatus(configId)
                val url = mockServerService.getServerUrl(configId)
                panel.updateStatus(isRunning, url)
            }
            // 同时更新全局按钮状态
            updateGlobalButtonStates()
        }

        /**
         * 打开配置对话框
         */
        private fun openConfigDialog() {
            val wasRunning = mockServerService.getRunningServers().isNotEmpty()
            if (wasRunning) {
                mockServerService.stopAllServers()
            }

            val dialog = ConfigDialog(project)
            if (dialog.showAndGet()) {
                // 配置已保存，重新设置标签页
                setupTabs()
                refreshAllTabs()
            } else if (wasRunning) {
                // 用户取消了配置，恢复服务
                mockServerService.startAllServers()
                refreshAllTabs()
            }
        }
}
