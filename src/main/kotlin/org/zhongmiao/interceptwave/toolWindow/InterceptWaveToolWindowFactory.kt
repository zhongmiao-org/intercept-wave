package org.zhongmiao.interceptwave.toolWindow

import org.zhongmiao.interceptwave.InterceptWaveBundle.message
import org.zhongmiao.interceptwave.services.ConfigService
import org.zhongmiao.interceptwave.services.MockServerService
import org.zhongmiao.interceptwave.ui.ConfigDialog
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

        private val statusLabel = JBLabel(message("toolwindow.status.stopped"))
        private val serverUrlLabel = JBLabel("")
        private val startButton = JButton(message("toolwindow.button.start"))
        private val stopButton = JButton(message("toolwindow.button.stop"))
        private val configButton = JButton(message("toolwindow.button.config"))
        private val configInfoArea = JTextArea()

        init {
            updateUI()
        }

        fun getContent(): JComponent {
            val panel = JBPanel<JBPanel<*>>(BorderLayout(10, 10))
            panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

            // 标题
            val titleLabel = JBLabel(message("toolwindow.title"))
            titleLabel.font = titleLabel.font.deriveFont(16f)
            panel.add(titleLabel, BorderLayout.NORTH)

            // 中间面板
            val centerPanel = JPanel(GridBagLayout())
            val gbc = GridBagConstraints().apply {
                fill = GridBagConstraints.HORIZONTAL
                insets = JBUI.insets(5)
            }

            // 状态信息
            gbc.gridx = 0
            gbc.gridy = 0
            gbc.gridwidth = 2
            gbc.weightx = 1.0
            centerPanel.add(statusLabel, gbc)

            gbc.gridy = 1
            serverUrlLabel.foreground = JBColor(0x0066CC, 0x5394EC)
            centerPanel.add(serverUrlLabel, gbc)

            // 按钮面板
            val buttonPanel = JPanel()
            buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.X_AXIS)

            startButton.addActionListener {
                startMockServer()
            }

            stopButton.addActionListener {
                stopMockServer()
            }
            stopButton.isEnabled = false

            configButton.addActionListener {
                openConfigDialog()
            }

            buttonPanel.add(startButton)
            buttonPanel.add(Box.createHorizontalStrut(10))
            buttonPanel.add(stopButton)
            buttonPanel.add(Box.createHorizontalStrut(10))
            buttonPanel.add(configButton)

            gbc.gridy = 2
            gbc.insets = JBUI.insets(20, 5, 5, 5)
            centerPanel.add(buttonPanel, gbc)

            // 配置信息面板
            val configInfoPanel = createConfigInfoPanel()
            gbc.gridy = 3
            gbc.weighty = 1.0
            gbc.fill = GridBagConstraints.BOTH
            gbc.insets = JBUI.insets(20, 5, 5, 5)
            centerPanel.add(configInfoPanel, gbc)

            panel.add(centerPanel, BorderLayout.CENTER)

            return panel
        }

        /**
         * 创建配置信息面板
         */
        private fun createConfigInfoPanel(): JComponent {
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
            val config = configService.getConfig()
            val info = buildString {
                appendLine("${message("config.global.port")} ${config.port}")
                appendLine("${message("config.global.prefix")} ${config.interceptPrefix}")
                appendLine("${message("config.global.baseurl")} ${config.baseUrl}")
                appendLine("${message("config.global.stripprefix")}: ${if (config.stripPrefix) "✓" else "✗"}")
                appendLine("\n${message("config.mock.title")}:")
                if (config.mockApis.isEmpty()) {
                    appendLine("  (No configuration)")
                } else {
                    config.mockApis.forEach { api ->
                        val status = if (api.enabled) "✓" else "✗"
                        appendLine("  $status ${api.method} ${api.path}")
                    }
                }
            }
            configInfoArea.text = info
        }

        /**
         * 启动Mock服务
         */
        private fun startMockServer() {
            try {
                val success = mockServerService.start()
                if (success) {
                    thisLogger().info("Mock server started successfully")
                    updateUI()
                    // Log messages are now shown in Run tool window
                } else {
                    thisLogger().warn("Failed to start mock server")
                    // Error is already logged in Run tool window
                }
            } catch (e: Exception) {
                thisLogger().error("Failed to start mock server", e)
                // Error is already logged in Run tool window
            }
        }

        /**
         * 停止Mock服务
         */
        private fun stopMockServer() {
            try {
                mockServerService.stop()
                updateUI()
                // Log message is now shown in Run tool window
            } catch (e: Exception) {
                thisLogger().error("Failed to stop mock server", e)
                // Error is already logged in Run tool window
            }
        }

        /**
         * 打开配置对话框
         */
        private fun openConfigDialog() {
            val wasRunning = mockServerService.isRunning()
            if (wasRunning) {
                // 服务正在运行，直接停止服务（按用户要求：确认后直接停止服务）
                mockServerService.stop()
                updateUI()
            }

            val dialog = ConfigDialog(project)
            if (dialog.showAndGet()) {
                // 配置已保存，更新UI和配置信息
                updateConfigInfo()
                updateUI()
            } else if (wasRunning) {
                // 用户取消了配置，恢复服务
                mockServerService.start()
                updateUI()
            }
        }

        /**
         * 更新UI状态
         */
        private fun updateUI() {
            val isRunning = mockServerService.isRunning()
            startButton.isEnabled = !isRunning
            stopButton.isEnabled = isRunning

            if (isRunning) {
                statusLabel.text = message("toolwindow.status.running")
                statusLabel.foreground = JBColor(0x008000, 0x6A8759)
                serverUrlLabel.text = message("toolwindow.access.url", mockServerService.getServerUrl() ?: "")
            } else {
                statusLabel.text = message("toolwindow.status.stopped")
                statusLabel.foreground = JBColor.GRAY
                serverUrlLabel.text = ""
            }
        }
    }
}
