package org.zhongmiao.interceptwave.ui

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import org.zhongmiao.interceptwave.util.LocalCertificateUtil
import org.zhongmiao.interceptwave.InterceptWaveBundle.message
import org.zhongmiao.interceptwave.model.ProxyConfig
import java.awt.BorderLayout
import java.awt.CardLayout
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JComponent
import javax.swing.JOptionPane
import javax.swing.JPasswordField
import javax.swing.JPanel

/**
 * ProxyConfigPanel: 每个配置组（Tab）对应的编辑面板（全局设置 + 中部内容）。
 * 中部内容拆分为 HttpConfigSection 与 WsConfigSection，避免单文件过大。
 */
class ProxyConfigPanel(
    private val project: Project,
    private val initialConfig: ProxyConfig,
    private val initialRouteIndex: Int? = null,
    private val initialMockIndex: Int? = null,
    private val initialWsRuleIndex: Int? = null,
    private val onChanged: () -> Unit = {}
) {
    // 顶部（全局）控件
    private val protocolCombo = ComboBox(arrayOf("HTTP", "WS"))
    private val nameField = JBTextField(initialConfig.name)
    private val portField = JBTextField(initialConfig.port.toString())
    private val enabledCheckbox = JBCheckBox(message("config.group.enabled"), initialConfig.enabled)
    private val httpsEnabledCheckbox = JBCheckBox(message("config.http.https.enabled"), initialConfig.httpsEnabled)
    private val httpsKeystorePathField = TextFieldWithBrowseButton().apply { text = initialConfig.httpsKeystorePath }
    private val httpsKeystorePasswordField = JPasswordField(initialConfig.httpsKeystorePassword)
    private val httpsHintLabel = JBLabel()

    // 标签：用于切换可见性（HTTP 专属已迁移，无需单独标签）

    // 中部内容分离
    private val httpSection = HttpConfigSection(
        project,
        initialConfig,
        initialRouteIndex = initialRouteIndex,
        initialMockIndex = initialMockIndex
    ) { onChanged() }
    private val wsSection = WsConfigSection(project, initialConfig, initialWsRuleIndex = initialWsRuleIndex) { onChanged() }

    init {
        protocolCombo.selectedItem = initialConfig.protocol
        httpsKeystorePathField.addActionListener { chooseHttpsKeystore() }
        attachChangeListeners()
        updateHttpsUi()
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
        }

        return mainPanel
    }

    private fun buildGlobalConfigPanelDsl(): JComponent {
        // 工具提示
        nameField.toolTipText = message("config.group.name.tooltip")
        portField.toolTipText = message("config.group.port.tooltip")
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
                // 启用开关（全宽）
                row {
                    cell(enabledCheckbox)
                }
                separator()
                row {
                    cell(httpsEnabledCheckbox)
                }
                row(message("config.http.https.keystore.path") + ":") {
                    cell(httpsKeystorePathField).align(AlignX.FILL)
                }
                row(message("config.http.https.keystore.password") + ":") {
                    cell(httpsKeystorePasswordField).align(AlignX.FILL)
                }
                row {
                    button(message("config.http.https.generate")) { generateLocalCertificate() }
                }
                row {
                    UiKit.applySecondaryText(httpsHintLabel)
                    cell(httpsHintLabel).align(AlignX.FILL)
                }
            }
        }
    }

    private fun attachChangeListeners() {
        nameField.document.onAnyChange(onChanged)
        portField.document.onAnyChange(onChanged)
        enabledCheckbox.addActionListener { onChanged() }
        httpsEnabledCheckbox.addActionListener {
            updateHttpsUi()
            onChanged()
        }
        httpsKeystorePathField.textField.document.onAnyChange {
            updateHttpsUi()
            onChanged()
        }
        httpsKeystorePasswordField.document.onAnyChange(onChanged)
        protocolCombo.addActionListener {
            updateHttpsUi()
            onChanged()
        }
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
            if (wsUrl.isNotEmpty() && !(wsUrl.startsWith("ws://") || wsUrl.startsWith("wss://"))) {
                javax.swing.JOptionPane.showMessageDialog(
                    null,
                    message("config.ws.baseurl.tooltip"),
                    message("config.validation.input.error.title"),
                    javax.swing.JOptionPane.WARNING_MESSAGE
                )
                return false
            }
        } else if (httpsEnabledCheckbox.isSelected) {
            if (httpsKeystorePathField.text.trim().isEmpty()) {
                JOptionPane.showMessageDialog(
                    null,
                    message("config.http.https.validation.path"),
                    message("config.validation.input.error.title"),
                    JOptionPane.WARNING_MESSAGE
                )
                return false
            }
            if (String(httpsKeystorePasswordField.password).isEmpty()) {
                JOptionPane.showMessageDialog(
                    null,
                    message("config.http.https.validation.password"),
                    message("config.validation.input.error.title"),
                    JOptionPane.WARNING_MESSAGE
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
        config.enabled = enabledCheckbox.isSelected
        config.httpsEnabled = httpsEnabledCheckbox.isSelected
        config.httpsKeystorePath = httpsKeystorePathField.text.trim()
        config.httpsKeystorePassword = String(httpsKeystorePasswordField.password)

        httpSection.applyTo(config)
        wsSection.applyTo(config)
    }

    private fun chooseHttpsKeystore() {
        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor()
            .withTitle(message("config.http.https.keystore.choose.title"))
            .withDescription(message("config.http.https.keystore.choose.desc"))
        val current = resolvePathForUi(httpsKeystorePathField.text.trim())
        val selected = FileChooser.chooseFile(descriptor, project, current) ?: return
        httpsKeystorePathField.text = toStoredPath(selected.path)
        updateHttpsUi()
        onChanged()
    }

    private fun generateLocalCertificate() {
        val target = projectRootPath()?.resolve(LocalCertificateUtil.DEFAULT_RELATIVE_PATH)
            ?: Paths.get(LocalCertificateUtil.DEFAULT_RELATIVE_PATH).toAbsolutePath().normalize()
        val passwordField = JPasswordField(String(httpsKeystorePasswordField.password).ifBlank { LocalCertificateUtil.DEFAULT_PASSWORD })
        val form = panel {
            row(message("config.http.https.generate.path") + ":") {
                cell(JBLabel(target.toString())).align(AlignX.FILL)
            }
            row(message("config.http.https.keystore.password") + ":") {
                cell(passwordField).align(AlignX.FILL)
            }
            row {
                val hint = JBLabel(message("config.http.https.generate.warning"))
                UiKit.applySecondaryText(hint)
                cell(hint).align(AlignX.FILL)
            }
        }
        val result = JOptionPane.showConfirmDialog(
            null,
            form,
            message("config.http.https.generate.title"),
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        )
        if (result != JOptionPane.OK_OPTION) return
        val password = String(passwordField.password)
        if (password.length < 6) {
            JOptionPane.showMessageDialog(null, message("config.http.https.generate.password.invalid"), message("config.message.error"), JOptionPane.ERROR_MESSAGE)
            return
        }
        val overwrite = if (java.nio.file.Files.exists(target)) {
            JOptionPane.showConfirmDialog(
                null,
                message("config.http.https.generate.overwrite", target.toString()),
                message("config.http.https.generate.title"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            ) == JOptionPane.YES_OPTION
        } else {
            true
        }
        if (!overwrite) return
        try {
            LocalCertificateUtil.generateLocalPkcs12(target, password, overwrite = true)
            httpsKeystorePathField.text = toStoredPath(target.toString())
            httpsKeystorePasswordField.text = password
            httpsEnabledCheckbox.isSelected = true
            updateHttpsUi()
            onChanged()
            JOptionPane.showMessageDialog(null, message("config.http.https.generate.success", target.toString()), message("config.http.https.generate.title"), JOptionPane.INFORMATION_MESSAGE)
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(null, message("config.http.https.generate.failed", e.message ?: e.toString()), message("config.message.error"), JOptionPane.ERROR_MESSAGE)
        }
    }

    private fun updateHttpsUi() {
        val httpSelected = (protocolCombo.selectedItem as? String ?: "HTTP") == "HTTP"
        httpsEnabledCheckbox.isEnabled = httpSelected
        httpsKeystorePathField.isEnabled = httpSelected && httpsEnabledCheckbox.isSelected
        httpsKeystorePasswordField.isEnabled = httpSelected && httpsEnabledCheckbox.isSelected
        httpsHintLabel.text = when {
            !httpSelected -> message("config.http.https.http.only")
            httpsKeystorePathField.text.trim().isEmpty() -> message("config.http.https.empty")
            isProjectExternalPath(httpsKeystorePathField.text.trim()) -> message("config.http.https.external.warning")
            else -> message("config.http.https.hint")
        }
    }

    private fun resolvePathForUi(value: String): com.intellij.openapi.vfs.VirtualFile? {
        val path = runCatching {
            val candidate = if (value.isBlank()) {
                projectRootPath()
            } else {
                val configured = Paths.get(value)
                if (configured.isAbsolute) configured else projectRootPath()?.resolve(configured)
            }
            candidate?.toAbsolutePath()?.normalize()?.toFile()
        }.getOrNull() ?: projectRootPath()?.toFile()
        return path?.let { LocalFileSystem.getInstance().findFileByIoFile(it) }
    }

    private fun toStoredPath(selectedPath: String): String {
        val selected = runCatching { Paths.get(selectedPath).toAbsolutePath().normalize() }.getOrNull()
            ?: return selectedPath
        val projectRoot = projectRootPath() ?: return selected.toString()
        return if (selected.startsWith(projectRoot)) {
            projectRoot.relativize(selected).toString().replace(File.separatorChar, '/')
        } else {
            selected.toString()
        }
    }

    private fun isProjectExternalPath(value: String): Boolean {
        val projectRoot = projectRootPath() ?: return false
        val selected = runCatching {
            val configured = Paths.get(value)
            val resolved = if (configured.isAbsolute) configured else projectRoot.resolve(configured)
            resolved.toAbsolutePath().normalize()
        }.getOrNull() ?: return false
        return !selected.startsWith(projectRoot)
    }

    private fun projectRootPath(): Path? =
        project.basePath?.takeIf { it.isNotBlank() }?.let { Paths.get(it).toAbsolutePath().normalize() }
}
