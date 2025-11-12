package org.zhongmiao.interceptwave.ui

import org.zhongmiao.interceptwave.InterceptWaveBundle.message
import org.zhongmiao.interceptwave.model.ProxyConfig
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.*
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
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
    private val interceptPrefixField = JBTextField(initialConfig.interceptPrefix)
    private val baseUrlField = JBTextField(initialConfig.baseUrl)
    private val stripPrefixCheckbox = JBCheckBox(message("config.group.stripprefix"), initialConfig.stripPrefix)
    private val globalCookieField = JBTextField(initialConfig.globalCookie)
    private val enabledCheckbox = JBCheckBox(message("config.group.enabled"), initialConfig.enabled)

    // 标签：用于切换可见性
    private val prefixLabel = JBLabel(message("config.group.prefix") + ":")
    private val baseUrlLabel = JBLabel(message("config.group.baseurl") + ":")
    private val cookieLabel = JBLabel(message("config.group.cookie") + ":")

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
        interceptPrefixField.toolTipText = message("config.group.prefix.tooltip")
        baseUrlField.toolTipText = message("config.group.baseurl.tooltip")
        stripPrefixCheckbox.toolTipText = message("config.group.stripprefix.tooltip")
        globalCookieField.toolTipText = message("config.group.cookie.tooltip")
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
                // 拦截前缀（HTTP 可见）
                row {
                    cell(prefixLabel)
                    cell(interceptPrefixField).align(AlignX.FILL)
                }
                // 目标地址（HTTP 可见）
                row {
                    cell(baseUrlLabel)
                    cell(baseUrlField).align(AlignX.FILL)
                }
                // 剥离前缀（全宽）
                row {
                    cell(stripPrefixCheckbox)
                }
                // 全局 Cookie（HTTP 可见）
                row {
                    cell(cookieLabel)
                    cell(globalCookieField).align(AlignX.FILL)
                }
                // 启用开关（全宽）
                row {
                    cell(enabledCheckbox)
                }
            }
        }
    }

    private fun updateGlobalVisibility() {
        val isWs = (protocolCombo.selectedItem as? String)?.equals("WS", ignoreCase = true) == true
        prefixLabel.isVisible = !isWs
        interceptPrefixField.isVisible = !isWs
        baseUrlLabel.isVisible = !isWs
        baseUrlField.isVisible = !isWs
//        // 剥离前缀在 WS 组同样生效（针对 WS 前缀），因此在 WS 组也显示该选项
//        stripPrefixCheckbox.isVisible = true
        cookieLabel.isVisible = !isWs
        globalCookieField.isVisible = !isWs
    }

    private fun attachChangeListeners() {
        fun javax.swing.text.Document.onAnyChange(cb: () -> Unit) {
            addDocumentListener(object : javax.swing.event.DocumentListener {
                override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = cb()
                override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = cb()
                override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = cb()
            })
        }

        nameField.document.onAnyChange(onChanged)
        portField.document.onAnyChange(onChanged)
        interceptPrefixField.document.onAnyChange(onChanged)
        baseUrlField.document.onAnyChange(onChanged)
        globalCookieField.document.onAnyChange(onChanged)

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
        config.interceptPrefix = interceptPrefixField.text
        config.baseUrl = baseUrlField.text
        config.stripPrefix = stripPrefixCheckbox.isSelected
        config.globalCookie = globalCookieField.text.trim()
        config.enabled = enabledCheckbox.isSelected

        httpSection.applyTo(config)
        wsSection.applyTo(config)
    }
}
