package org.zhongmiao.interceptwave.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.dsl.builder.panel
import org.zhongmiao.interceptwave.InterceptWaveBundle.message
import org.zhongmiao.interceptwave.events.*
import org.zhongmiao.interceptwave.services.ConfigService
import org.zhongmiao.interceptwave.services.ConsoleService
import org.zhongmiao.interceptwave.services.MockServerService
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities
import javax.swing.JComponent

@Suppress("unused")
class InterceptWaveToolWindow(private val project: Project) {

        private val mockServerService = project.service<MockServerService>()
        private val consoleService = project.service<ConsoleService>()
        private val configService = project.service<ConfigService>()

        // 使用标签页来切换不同的配置组
        private val tabbedPane = JBTabbedPane()
        private val tabPanels = mutableMapOf<String, ProxyGroupTabPanel>()

        // 全局按钮引用，用于状态管理
        private lateinit var startAllButton: javax.swing.JButton
        private lateinit var stopAllButton: javax.swing.JButton

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
            val rootPanel = JBPanel<JBPanel<*>>(BorderLayout(8, 8))
            rootPanel.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)

            // 顶部容器（仅放置右侧全局按钮，不再显示左侧标题）
            val titlePanel = JPanel(BorderLayout())

            // 全局操作按钮（使用 UI DSL 排列，并捕获引用）
            val globalRow = panel {
                row {
                    button(message("toolwindow.button.startall")) {
                        mockServerService.startAllServers()
                        refreshAllTabs()
                    }.applyToComponent {
                        icon = AllIcons.Actions.RunAll
                        isFocusPainted = false
                        isFocusable = false
                        startAllButton = this
                    }
                    button(message("toolwindow.button.stopall")) {
                        // 通过 ConsoleService 的虚拟进程终止来统一停止逻辑，确保 IDE Stop 按钮一致联动
                        consoleService.terminateConsoleProcess()
                        refreshAllTabs()
                    }.applyToComponent {
                        icon = AllIcons.Debugger.MuteBreakpoints
                        isFocusPainted = false
                        isFocusable = false
                        stopAllButton = this
                    }
                    button(message("toolwindow.button.config")) {
                        // Open the config dialog focusing on the currently selected group (if any)
                        val selected = tabbedPane.selectedIndex
                        // Exclude the trailing "+" tab when present
                        val maxGroupIndex = (tabbedPane.tabCount - 2).coerceAtLeast(0)
                        val initialIndex = if (selected in 0..maxGroupIndex) selected else 0
                        openConfigDialog(initialSelectedIndex = initialIndex, autoAddNew = false)
                    }.applyToComponent {
                        icon = AllIcons.General.Settings
                        isFocusPainted = false
                        isFocusable = false
                    }
                }
            }

            titlePanel.add(globalRow, BorderLayout.EAST)

            rootPanel.add(titlePanel, BorderLayout.NORTH)

            // 标签页
            rootPanel.add(tabbedPane, BorderLayout.CENTER)

            // 初始化按钮状态
            updateGlobalButtonStates()

            return rootPanel
        }

        // createButton: 已用 UI DSL button 替代

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
                // 没有配置组，显示提示（DSL 布局），不再额外包一层 JPanel
                val content = panel {
                    row {
                        val label = JBLabel(message("toolwindow.empty.hint"))
                        label.foreground = JBColor.GRAY
                        cell(label)
                    }
                }
                tabbedPane.addTab(message("toolwindow.empty.tab"), content)
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

                    // 添加标签页，标签名称显示配置组名称；为内容添加纵向滚动条
                    val scroll = JBScrollPane(tabPanel.getPanel()).apply {
                        verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
                        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
                        border = null
                    }
                    tabbedPane.addTab(config.name, scroll)
                }

                // 添加 "+" 标签用于快速新增配置
                val addContent = panel {
                    row {
                        val addLabel = JBLabel(message("toolwindow.add.hint"))
                        addLabel.foreground = JBColor.GRAY
                        cell(addLabel)
                    }
                }
                tabbedPane.addTab(null, AllIcons.General.Add, addContent, message("toolwindow.add.hint"))

                // 监听 "+" 标签被点击
                tabbedPane.addChangeListener {
                    if (tabbedPane.selectedIndex == tabbedPane.tabCount - 1) {
                        // 点击了 "+" 标签：打开配置对话框并直接新增一个配置组
                        openConfigDialog(initialSelectedIndex = null, autoAddNew = true)
                        // 新建后刷新完 tabs，选中新建的组（倒数第二个标签为新组）
                        if (tabbedPane.tabCount > 1) {
                            tabbedPane.selectedIndex = tabbedPane.tabCount - 2
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
        private fun openConfigDialog(initialSelectedIndex: Int? = null, autoAddNew: Boolean = false) {
            // 记录打开前正在运行的配置组，仅恢复这些，避免“全部启动”
            val previouslyRunning = mockServerService.getRunningServers().map { it.first }.toSet()
            if (previouslyRunning.isNotEmpty()) {
                mockServerService.stopAllServers()
            }

            val dialog = ConfigDialog(project, initialSelectedIndex = initialSelectedIndex, autoAddOnOpen = autoAddNew)
            val saved = dialog.showAndGet()

            if (saved) {
                // 配置已保存，刷新标签
                setupTabs()
                // 保持选中
                if (autoAddNew && tabbedPane.tabCount > 1) {
                    tabbedPane.selectedIndex = tabbedPane.tabCount - 2
                } else if (initialSelectedIndex != null) {
                    val maxIdx = (tabbedPane.tabCount - 2).coerceAtLeast(0)
                    tabbedPane.selectedIndex = initialSelectedIndex.coerceAtMost(maxIdx)
                }
            }

            // 无论保存或取消，按打开前的运行集合逐个恢复
            if (previouslyRunning.isNotEmpty()) {
                previouslyRunning.forEach { id ->
                    runCatching { mockServerService.startServer(id) }
                }
            }
            refreshAllTabs()
        }
}
