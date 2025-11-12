package org.zhongmiao.interceptwave.ui

import org.zhongmiao.interceptwave.InterceptWaveBundle.message
import org.zhongmiao.interceptwave.model.ProxyConfig
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.*
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.BorderFactory
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

        // 上部：全局配置
        val globalPanel = createGlobalConfigPanel()
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

    private fun createGlobalConfigPanel(): JBPanel<JBPanel<*>> {
        val panel = JBPanel<JBPanel<*>>(GridBagLayout())
        panel.border = BorderFactory.createTitledBorder(message("config.group.settings"))

        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = JBUI.insets(5)
        }

        // 组类型
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0
        panel.add(JBLabel(message("config.group.protocol") + ":"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        panel.add(protocolCombo, gbc)

        // 组名称
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0
        panel.add(JBLabel(message("config.group.name") + ":"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        nameField.toolTipText = message("config.group.name.tooltip")
        panel.add(nameField, gbc)

        // 端口
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0
        panel.add(JBLabel(message("config.group.port") + ":"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        portField.toolTipText = message("config.group.port.tooltip")
        panel.add(portField, gbc)

        // 拦截前缀
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0.0
        panel.add(prefixLabel, gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        interceptPrefixField.toolTipText = message("config.group.prefix.tooltip")
        panel.add(interceptPrefixField, gbc)

        // 目标地址
        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0.0
        panel.add(baseUrlLabel, gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        baseUrlField.toolTipText = message("config.group.baseurl.tooltip")
        panel.add(baseUrlField, gbc)

        // 剥离前缀
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2
        stripPrefixCheckbox.toolTipText = message("config.group.stripprefix.tooltip")
        panel.add(stripPrefixCheckbox, gbc)

        // 全局 Cookie
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 1; gbc.weightx = 0.0
        panel.add(cookieLabel, gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        globalCookieField.toolTipText = message("config.group.cookie.tooltip")
        panel.add(globalCookieField, gbc)

        // 启用
        gbc.gridx = 0; gbc.gridy = 7; gbc.gridwidth = 2
        enabledCheckbox.toolTipText = message("config.group.enabled.tooltip")
        panel.add(enabledCheckbox, gbc)

        return panel
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
