package org.zhongmiao.interceptwave.ui

import org.zhongmiao.interceptwave.InterceptWaveBundle.message
import org.zhongmiao.interceptwave.services.ConfigService
import org.zhongmiao.interceptwave.services.ConsoleService
import org.zhongmiao.interceptwave.services.MockServerService
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*

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
    private val consoleService = project.service<ConsoleService>()

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
        val nameLabel = JBLabel("$configName (:$port)")
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
            appendLine(message("toolwindow.config.port", "${config.port}"))
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
            // 先确保唤起 Run 窗口，避免首次订阅者尚未就绪导致不显示
            consoleService.showConsole()
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

