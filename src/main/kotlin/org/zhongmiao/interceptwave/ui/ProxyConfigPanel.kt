package org.zhongmiao.interceptwave.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import org.zhongmiao.interceptwave.InterceptWaveBundle.message
import org.zhongmiao.interceptwave.model.ProxyConfig
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * ProxyConfigPanel: 每个配置组（Tab）对应的编辑面板（全局设置 + 中部内容）。
 * 中部内容拆分为 HttpConfigSection 与 WsConfigSection，避免单文件过大。
 */
class ProxyConfigPanel(
    project: Project,
    private val initialConfig: ProxyConfig,
    private val onChanged: () -> Unit = {}
) {
    // 顶部（全局）控件
    private val protocolCombo = ComboBox(arrayOf("HTTP", "WS"))
    private val nameField = JBTextField(initialConfig.name)
    private val portField = JBTextField(initialConfig.port.toString())
    // HTTP 专属字段移至 HttpConfigSection 顶部；此处仅保留组级配置
    private val stripPrefixCheckbox = JBCheckBox(message("config.group.stripprefix"), initialConfig.stripPrefix)
    private val enabledCheckbox = JBCheckBox(message("config.group.enabled"), initialConfig.enabled)

    // 标签：用于切换可见性（HTTP 专属已迁移，无需单独标签）

    // 中部内容分离
    private val httpSection = HttpConfigSection(project, initialConfig) { onChanged() }
    private val wsSection = WsConfigSection(project, initialConfig) { onChanged() }

    init {
        protocolCombo.selectedItem = initialConfig.protocol
        attachChangeListeners()
    }

    fun getPanel(): JBPanel<JBPanel<*>> {
        val mainPanel = JBPanel<JBPanel<*>>(BorderLayout(10, 10))

        // 上部：全局配置（UI DSL）
        val globalPanel = buildGlobalConfigPanelDsl()
        mainPanel.add(globalPanel, BorderLayout.NORTH)

        // 中部：HTTP/WS 内容
        val centerCard = JPanel(CardLayout())
        centerCard.add(httpSection.panel(), "HTTP")
        centerCard.add(wsSection.panel(), "WS")
        (centerCard.layout as CardLayout).show(centerCard, initialConfig.protocol)
        mainPanel.add(centerCard, BorderLayout.CENTER)

        // 协议切换时联动
        protocolCombo.addActionListener {
            val sel = protocolCombo.selectedItem as String
            (centerCard.layout as CardLayout).show(centerCard, sel)
            updateGlobalVisibility()
        }
        updateGlobalVisibility()

        return mainPanel
    }

    private fun buildGlobalConfigPanelDsl(): JComponent {
        // 工具提示
        nameField.toolTipText = message("config.group.name.tooltip")
        portField.toolTipText = message("config.group.port.tooltip")
        stripPrefixCheckbox.toolTipText = message("config.group.stripprefix.tooltip")
        enabledCheckbox.toolTipText = message("config.group.enabled.tooltip")

        // DSL 面板，复用现有组件，便于与现有逻辑/监听兼容
        return panel {
            group(message("config.group.settings")) {
                row(message("config.group.protocol") + ":") {
                    cell(protocolCombo).align(AlignX.FILL)
                }
                row(message("config.group.name") + ":") {
                    cell(nameField).align(AlignX.FILL)
                }
                row(message("config.group.port") + ":") {
                    cell(portField).align(AlignX.FILL)
                }
                // 剥离前缀（全宽）
                row {
                    cell(stripPrefixCheckbox)
                }
                // 启用开关（全宽）
                row {
                    cell(enabledCheckbox)
                }
            }
        }
    }

    private fun updateGlobalVisibility() {
        // 统一：全局区仅显示通用字段，无需按协议切换可见性
    }

    private fun attachChangeListeners() {
        nameField.document.onAnyChange(onChanged)
        portField.document.onAnyChange(onChanged)
        stripPrefixCheckbox.addActionListener { onChanged() }
        enabledCheckbox.addActionListener { onChanged() }
        protocolCombo.addActionListener { onChanged() }
    }

    fun validateInput(): Boolean {
        val port = portField.text.toIntOrNull()
        if (port == null || port < 1 || port > 65535) {
            javax.swing.JOptionPane.showMessageDialog(
                null,
                message("config.validation.port.invalid"),
                message("config.validation.input.error.title"),
                javax.swing.JOptionPane.WARNING_MESSAGE
            )
            return false
        }
        val protocol = protocolCombo.selectedItem as? String ?: "HTTP"
        if (protocol == "WS") {
            val wsUrl = wsSection.getWsBaseUrl().trim()
            if (wsUrl.isEmpty() || !(wsUrl.startsWith("ws://") || wsUrl.startsWith("wss://"))) {
                javax.swing.JOptionPane.showMessageDialog(
                    null,
                    message("config.ws.baseurl.tooltip"),
                    message("config.validation.input.error.title"),
                    javax.swing.JOptionPane.WARNING_MESSAGE
                )
                return false
            }
        }
        return true
    }

    fun applyChanges(config: ProxyConfig) {
        config.protocol = (protocolCombo.selectedItem as? String) ?: "HTTP"
        config.name = nameField.text.trim().ifEmpty { message("config.group.default") }
        config.port = portField.text.toIntOrNull() ?: 8888
        config.stripPrefix = stripPrefixCheckbox.isSelected
        config.enabled = enabledCheckbox.isSelected

        httpSection.applyTo(config)
        wsSection.applyTo(config)
    }
}
