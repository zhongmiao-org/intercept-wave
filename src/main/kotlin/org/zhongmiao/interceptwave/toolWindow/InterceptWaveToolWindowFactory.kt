package org.zhongmiao.interceptwave.toolWindow

import org.zhongmiao.interceptwave.InterceptWaveBundle.message
import org.zhongmiao.interceptwave.services.ConfigService
import org.zhongmiao.interceptwave.services.MockServerService
import org.zhongmiao.interceptwave.ui.ConfigDialog
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*

class InterceptWaveToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val toolWindowPanel = InterceptWaveToolWindow(project)
        val content = ContentFactory.getInstance().createContent(toolWindowPanel.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    class InterceptWaveToolWindow(private val project: Project) {

        private val mockServerService = project.service<MockServerService>()
        private val configService = project.service<ConfigService>()

        // 使用标签页来切换不同的配置组
        private val tabbedPane = JBTabbedPane()
        private val tabPanels = mutableMapOf<String, ProxyGroupTabPanel>()

        // 全局按钮引用，用于状态管理
        private lateinit var startAllButton: JButton
        private lateinit var stopAllButton: JButton

        init {
            setupTabs()
        }

        fun getContent(): JComponent {
            val panel = JBPanel<JBPanel<*>>(BorderLayout(10, 10))
            panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

            // 标题
            val titlePanel = JPanel(BorderLayout())
            val titleLabel = JBLabel(message("plugin.name"))
            titleLabel.font = titleLabel.font.deriveFont(16f)
            titlePanel.add(titleLabel, BorderLayout.WEST)

            // 全局操作按钮
            val globalButtonPanel = JPanel()
            globalButtonPanel.layout = BoxLayout(globalButtonPanel, BoxLayout.X_AXIS)

            startAllButton = createButton(message("toolwindow.button.startall"), AllIcons.Actions.RunAll) {
                mockServerService.startAllServers()
                refreshAllTabs()
            }

            stopAllButton = createButton(message("toolwindow.button.stopall"), AllIcons.Debugger.MuteBreakpoints) {
                mockServerService.stopAllServers()
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
}

/**
 * 单个配置组的标签页面板
 */
class ProxyGroupTabPanel(
    project: Project,
    private val configId: String,
    private val configName: String,
    private val port: Int,
    private val enabled: Boolean,
    private val onStatusChanged: () -> Unit = {}
) {
    private val mockServerService = project.service<MockServerService>()
    private val configService = project.service<ConfigService>()

    private val statusLabel = JBLabel(message("toolwindow.status.stopped"))
    private val urlLabel = JBLabel("")
    private val startButton = JButton(message("toolwindow.button.start"), AllIcons.Actions.Execute).apply {
        isFocusPainted = false
        isFocusable = false
    }
    private val stopButton = JButton(message("toolwindow.button.stop"), AllIcons.Actions.Suspend).apply {
        isFocusPainted = false
        isFocusable = false
    }
    private val configInfoArea = JTextArea()

    init {
        updateStatus(mockServerService.getServerStatus(configId), mockServerService.getServerUrl(configId))
    }

    fun getPanel(): JPanel {
        val panel = JPanel(BorderLayout(15, 15))
        panel.border = BorderFactory.createEmptyBorder(15, 15, 15, 15)

        // 顶部：状态信息
        val statusPanel = createStatusPanel()
        panel.add(statusPanel, BorderLayout.NORTH)

        // 中部：配置信息
        val infoPanel = createInfoPanel()
        panel.add(infoPanel, BorderLayout.CENTER)

        return panel
    }

    /**
     * 创建状态面板
     */
    private fun createStatusPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.border = BorderFactory.createTitledBorder(message("toolwindow.status.title"))

        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = JBUI.insets(5)
        }

        // 配置组名称
        val nameLabel = JBLabel("$configName (:${port.toString()})")
        nameLabel.font = nameLabel.font.deriveFont(16f).deriveFont(java.awt.Font.BOLD)
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.gridwidth = 2
        gbc.weightx = 1.0
        panel.add(nameLabel, gbc)

        // 配置状态
        gbc.gridy = 1
        gbc.gridwidth = 1
        panel.add(JBLabel(message("toolwindow.label.config.status")), gbc)
        gbc.gridx = 1
        val enabledLabel = JBLabel(
            if (enabled) message("toolwindow.status.enabled") else message("toolwindow.status.disabled"),
            if (enabled) AllIcons.General.InspectionsOK else AllIcons.General.InspectionsError,
            SwingConstants.LEFT
        )
        enabledLabel.foreground = if (enabled) JBColor(0x008000, 0x6A8759) else JBColor.GRAY
        panel.add(enabledLabel, gbc)

        // 运行状态
        gbc.gridx = 0
        gbc.gridy = 2
        panel.add(JBLabel(message("toolwindow.label.running.status")), gbc)
        gbc.gridx = 1
        panel.add(statusLabel, gbc)

        // 访问 URL
        gbc.gridx = 0
        gbc.gridy = 3
        panel.add(JBLabel(message("toolwindow.label.url")), gbc)
        gbc.gridx = 1
        urlLabel.foreground = JBColor(0x0066CC, 0x5394EC)
        panel.add(urlLabel, gbc)

        // 按钮
        val buttonPanel = JPanel()
        buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.X_AXIS)

        startButton.addActionListener {
            startServer()
        }

        stopButton.addActionListener {
            stopServer()
        }

        buttonPanel.add(startButton)
        buttonPanel.add(Box.createHorizontalStrut(10))
        buttonPanel.add(stopButton)

        gbc.gridx = 0
        gbc.gridy = 4
        gbc.gridwidth = 2
        gbc.insets = JBUI.insets(15, 5, 5, 5)
        panel.add(buttonPanel, gbc)

        return panel
    }

    /**
     * 创建配置信息面板
     */
    private fun createInfoPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createTitledBorder(message("toolwindow.config.title"))

        configInfoArea.isEditable = false
        configInfoArea.lineWrap = true
        configInfoArea.wrapStyleWord = true

        updateConfigInfo()

        val scrollPane = JBScrollPane(configInfoArea)
        panel.add(scrollPane, BorderLayout.CENTER)

        return panel
    }

    /**
     * 更新配置信息显示
     */
    private fun updateConfigInfo() {
        val config = configService.getProxyGroup(configId)
        if (config == null) {
            configInfoArea.text = message("toolwindow.config.notfound")
            return
        }

        val info = buildString {
            appendLine(message("toolwindow.config.name", config.name))
            appendLine(message("toolwindow.config.port", config.port.toString()))
            appendLine(message("toolwindow.config.prefix", config.interceptPrefix))
            appendLine(message("toolwindow.config.baseurl", config.baseUrl))
            appendLine(message("toolwindow.config.stripprefix", if (config.stripPrefix) message("toolwindow.yes") else message("toolwindow.no")))
            appendLine(message("toolwindow.config.cookie", config.globalCookie.ifEmpty { message("toolwindow.notset") }))
            appendLine()
            appendLine(message("toolwindow.config.mocklist"))
            if (config.mockApis.isEmpty()) {
                appendLine(message("toolwindow.config.nomock"))
            } else {
                config.mockApis.forEach { api ->
                    val status = if (api.enabled) "✓" else "✗"
                    appendLine("  $status ${api.method.padEnd(6)} ${api.path}")
                }
            }
        }
        configInfoArea.text = info
    }

    /**
     * 启动服务器
     */
    private fun startServer() {
        try {
            val success = mockServerService.startServer(configId)
            if (success) {
                val url = mockServerService.getServerUrl(configId)
                updateStatus(true, url)
                // 通知父组件更新全局按钮状态
                onStatusChanged()
            }
        } catch (e: Exception) {
            thisLogger().error("Failed to start server for $configName", e)
        }
    }

    /**
     * 停止服务器
     */
    private fun stopServer() {
        try {
            mockServerService.stopServer(configId)
            updateStatus(false, null)
            // 通知父组件更新全局按钮状态
            onStatusChanged()
        } catch (e: Exception) {
            thisLogger().error("Failed to stop server for $configName", e)
        }
    }

    /**
     * 更新状态显示
     */
    fun updateStatus(isRunning: Boolean, url: String?) {
        startButton.isEnabled = !isRunning && enabled
        stopButton.isEnabled = isRunning

        if (isRunning) {
            statusLabel.text = message("toolwindow.status.running.indicator")
            statusLabel.icon = AllIcons.RunConfigurations.TestPassed
            statusLabel.foreground = JBColor(0x008000, 0x6A8759)
            urlLabel.text = url ?: ""
        } else {
            statusLabel.text = message("toolwindow.status.stopped.indicator")
            statusLabel.icon = AllIcons.RunConfigurations.TestTerminated
            statusLabel.foreground = JBColor.GRAY
            urlLabel.text = ""
        }
    }
}