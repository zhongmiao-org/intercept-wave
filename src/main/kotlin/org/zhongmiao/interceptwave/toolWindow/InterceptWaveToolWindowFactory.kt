package org.zhongmiao.interceptwave.toolWindow

import org.zhongmiao.interceptwave.services.ConfigService
import org.zhongmiao.interceptwave.services.MockServerService
import org.zhongmiao.interceptwave.ui.ConfigDialog
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*

class InterceptWaveToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val toolWindowPanel = InterceptWaveToolWindow(project)
        val content = ContentFactory.getInstance().createContent(toolWindowPanel.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    class InterceptWaveToolWindow(private val project: Project) {

        private val mockServerService = project.service<MockServerService>()
        private val configService = project.service<ConfigService>()

        private val statusLabel = JBLabel("状态: 未启动")
        private val serverUrlLabel = JBLabel("")
        private val startButton = JButton("启动服务")
        private val stopButton = JButton("停止服务")
        private val configButton = JButton("配置")
        private val configInfoArea = JTextArea()

        init {
            updateUI()
        }

        fun getContent(): JComponent {
            val panel = JBPanel<JBPanel<*>>(BorderLayout(10, 10))
            panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

            // 标题
            val titleLabel = JBLabel("Intercept Wave - Mock 服务")
            titleLabel.font = titleLabel.font.deriveFont(16f)
            panel.add(titleLabel, BorderLayout.NORTH)

            // 中间面板
            val centerPanel = JPanel(GridBagLayout())
            val gbc = GridBagConstraints().apply {
                fill = GridBagConstraints.HORIZONTAL
                insets = Insets(5, 5, 5, 5)
            }

            // 状态信息
            gbc.gridx = 0
            gbc.gridy = 0
            gbc.gridwidth = 2
            gbc.weightx = 1.0
            centerPanel.add(statusLabel, gbc)

            gbc.gridy = 1
            serverUrlLabel.foreground = java.awt.Color(0, 102, 204)
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
            gbc.insets = Insets(20, 5, 5, 5)
            centerPanel.add(buttonPanel, gbc)

            // 配置信息面板
            val configInfoPanel = createConfigInfoPanel()
            gbc.gridy = 3
            gbc.weighty = 1.0
            gbc.fill = GridBagConstraints.BOTH
            gbc.insets = Insets(20, 5, 5, 5)
            centerPanel.add(configInfoPanel, gbc)

            panel.add(centerPanel, BorderLayout.CENTER)

            return panel
        }

        /**
         * 创建配置信息面板
         */
        private fun createConfigInfoPanel(): JComponent {
            val panel = JPanel(BorderLayout())
            panel.border = BorderFactory.createTitledBorder("当前配置")

            configInfoArea.isEditable = false
            configInfoArea.lineWrap = true
            configInfoArea.wrapStyleWord = true

            updateConfigInfo()

            val scrollPane = JScrollPane(configInfoArea)
            panel.add(scrollPane, BorderLayout.CENTER)

            return panel
        }

        /**
         * 更新配置信息显示
         */
        private fun updateConfigInfo() {
            val config = configService.getConfig()
            val info = buildString {
                appendLine("Mock端口: ${config.port}")
                appendLine("拦截前缀: ${config.interceptPrefix}")
                appendLine("原始接口: ${config.baseUrl}")
                appendLine("过滤前缀: ${if (config.stripPrefix) "是" else "否"}")
                appendLine("\nMock接口列表:")
                if (config.mockApis.isEmpty()) {
                    appendLine("  (暂无配置)")
                } else {
                    config.mockApis.forEachIndexed { index, api ->
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
                    JOptionPane.showMessageDialog(
                        null,
                        "Mock服务启动成功！\n访问地址: ${mockServerService.getServerUrl()}",
                        "成功",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                } else {
                    JOptionPane.showMessageDialog(
                        null,
                        "Mock服务启动失败，请检查端口是否被占用",
                        "错误",
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            } catch (e: Exception) {
                thisLogger().error("Failed to start mock server", e)
                JOptionPane.showMessageDialog(
                    null,
                    "Mock服务启动失败: ${e.message}",
                    "错误",
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }

        /**
         * 停止Mock服务
         */
        private fun stopMockServer() {
            try {
                mockServerService.stop()
                updateUI()
                JOptionPane.showMessageDialog(
                    null,
                    "Mock服务已停止",
                    "提示",
                    JOptionPane.INFORMATION_MESSAGE
                )
            } catch (e: Exception) {
                thisLogger().error("Failed to stop mock server", e)
                JOptionPane.showMessageDialog(
                    null,
                    "停止Mock服务失败: ${e.message}",
                    "错误",
                    JOptionPane.ERROR_MESSAGE
                )
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
                statusLabel.text = "状态: 运行中"
                statusLabel.foreground = java.awt.Color(0, 128, 0)
                serverUrlLabel.text = "访问地址: ${mockServerService.getServerUrl()}"
            } else {
                statusLabel.text = "状态: 未启动"
                statusLabel.foreground = java.awt.Color(128, 128, 128)
                serverUrlLabel.text = ""
            }
        }
    }
}
