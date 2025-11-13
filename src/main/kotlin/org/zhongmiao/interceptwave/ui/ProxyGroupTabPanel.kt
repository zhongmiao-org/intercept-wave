package org.zhongmiao.interceptwave.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.table.JBTable
import org.zhongmiao.interceptwave.InterceptWaveBundle.message
import org.zhongmiao.interceptwave.services.ConfigService
import org.zhongmiao.interceptwave.services.ConsoleService
import org.zhongmiao.interceptwave.services.MockServerService
import java.awt.BorderLayout
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
    // WS 手动推送控件
    private val wsCustomMsgArea = JTextArea()
    private val wsTargetCombo = ComboBox(arrayOf(
        message("wspanel.target.match"),
        message("wspanel.target.all"),
        message("wspanel.target.latest")
    ))

    init {
        updateStatus(mockServerService.getServerStatus(configId), mockServerService.getServerUrl(configId))
    }

    fun getPanel(): JPanel {
        val panel = JPanel(BorderLayout(8, 8))
        panel.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)

        // 顶部：状态信息
        val statusPanel = createStatusPanel()
        panel.add(statusPanel, BorderLayout.NORTH)

        // 中部：配置信息 +（可选）WS推送面板
        val center = JPanel(BorderLayout(6, 6))
        val cfg = configService.getProxyGroup(configId)
        if (cfg != null && cfg.protocol == "WS" && cfg.wsManualPush) {
            // WS 组：配置信息放在顶部（不限制高度），下方是推送面板
            val infoPanel = createInfoPanel()
            center.add(infoPanel, BorderLayout.NORTH)
            center.add(createWsPushPanel(), BorderLayout.CENTER)
        } else {
            val infoPanel = createInfoPanel()
            center.add(infoPanel, BorderLayout.CENTER)
        }
        panel.add(center, BorderLayout.CENTER)

        return panel
    }

    /**
     * 创建状态面板
     */
    private fun createStatusPanel(): JPanel {
        val nameLabel = JBLabel("$configName (:$port)")
        nameLabel.font = nameLabel.font.deriveFont(16f).deriveFont(java.awt.Font.BOLD)

        val enabledLabel = JBLabel(
            if (enabled) message("toolwindow.status.enabled") else message("toolwindow.status.disabled"),
            if (enabled) AllIcons.General.InspectionsOK else AllIcons.General.InspectionsError,
            SwingConstants.LEFT
        ).apply { foreground = if (enabled) JBColor(0x008000, 0x6A8759) else JBColor.GRAY }

        urlLabel.foreground = JBColor(0x0066CC, 0x5394EC)

        val content = com.intellij.ui.dsl.builder.panel {
            group(message("toolwindow.status.title")) {
                row { cell(nameLabel) }
                row(message("toolwindow.label.config.status")) { cell(enabledLabel).align(AlignX.FILL) }
                row(message("toolwindow.label.running.status")) { cell(statusLabel).align(AlignX.FILL) }
                row(message("toolwindow.label.url")) { cell(urlLabel).align(AlignX.FILL) }
                row {
                    startButton.addActionListener { startServer() }
                    stopButton.addActionListener { stopServer() }
                    cell(startButton)
                    cell(stopButton)
                }
            }
        }
        return content
    }

    /**
     * 创建配置信息面板
     */
    private fun createInfoPanel(): JPanel {
        val cfg = configService.getProxyGroup(configId)
        val isWs = cfg?.protocol == "WS"
        val yes = message("toolwindow.yes")
        val no = message("toolwindow.no")

        val content = com.intellij.ui.dsl.builder.panel {
            group(message("toolwindow.config.title")) {
                // 顶部基本信息
                row(message("toolwindow.config.name", configName)) { }
                row(message("toolwindow.config.port", "$port")) { }
                row(message("toolwindow.config.stripprefix", if (cfg?.stripPrefix == true) yes else no)) { }

                if (!isWs) {
                    // HTTP 详细
                    row(message("toolwindow.config.prefix", cfg?.interceptPrefix ?: "")) { }
                    row(message("toolwindow.config.baseurl", cfg?.baseUrl ?: "")) { }
                    val cookie = (cfg?.globalCookie ?: "").ifEmpty { message("toolwindow.notset") }
                    row(message("toolwindow.config.cookie", cookie)) { }

                    // Mock 接口列表（两列：启用复选框 + 路径）
                    val apis = cfg?.mockApis ?: emptyList()
                    val model = createHttpShortTableModel(enabledEditable = true)
                    appendHttpShortRows(model, apis)
                    val table = JBTable(model).apply {
                        fillsViewportHeight = true
                        UiKit.setEnabledColumnWidth(this, 0)
                        UiKit.ensureVisibleRows(this, UiKit.DEFAULT_VISIBLE_ROWS)
                    }
                    // 勾选启用后，直接修改内存中的配置以便下一次请求生效
                    (table.model as javax.swing.table.DefaultTableModel).addTableModelListener { e ->
                        if (e is javax.swing.event.TableModelEvent) {
                            val row = e.firstRow
                            val col = e.column
                            if (row >= 0 && (col == 0 || col == javax.swing.event.TableModelEvent.ALL_COLUMNS)) {
                                val latest = configService.getProxyGroup(configId)
                                val target = latest?.mockApis?.getOrNull(row)
                                if (target != null) {
                                    val newVal = (table.model.getValueAt(row, 0) as? Boolean) ?: false
                                    target.enabled = newVal
                                }
                            }
                        }
                    }
                    row { cell(JBScrollPane(table)).align(AlignX.FILL) }
                } else {
                    // WS 详细（进入该分支意味着 cfg 非空）
                    row(message("toolwindow.ws.title")) { }
                    val wsBase = cfg.wsBaseUrl ?: message("toolwindow.notset")
                    row(message("toolwindow.ws.baseurl", wsBase)) { }
                    val wsPrefix = cfg.wsInterceptPrefix?.takeIf { it.isNotEmpty() } ?: message("toolwindow.notset")
                    row(message("toolwindow.ws.prefix", wsPrefix)) { }
                    row(message("toolwindow.ws.manualpush", if (cfg.wsManualPush) yes else no)) { }
                }
            }
        }
        return content
    }

    // HTTP 简表构造已收敛到 HttpMockTableUtil

    /**
     * 更新配置信息显示
     */
    // 更新文本方式已移除，改为 DSL 构建

    /**
     * WS 手动推送面板
     */
    private fun createWsPushPanel(): JPanel {
        wsTargetCombo.selectedIndex = 0

        val cfg = configService.getProxyGroup(configId)
        // 侧边栏允许直接编辑“启用”列（仅第 0 列可编辑）
        val ruleModel = createWsRuleTableModel(enabledEditable = true)
        val ruleTable = JBTable(ruleModel).apply {
            fillsViewportHeight = true
            UiKit.ensureVisibleRows(this, UiKit.DEFAULT_VISIBLE_ROWS)
            UiKit.setEnabledColumnWidth(this, 0)
            UiKit.setFixedColumnWidth(this, 2, UiKit.MODE_COL_WIDTH)
            UiKit.setFixedColumnWidth(this, 3, UiKit.PERIOD_COL_WIDTH)
        }
        appendWsRuleRows(ruleModel, cfg?.wsPushRules ?: emptyList())
        // 勾选启用后，直接修改内存中的配置（周期/时间轴既有任务不自动重建）
        ruleModel.addTableModelListener { e ->
            if (e is javax.swing.event.TableModelEvent) {
                val row = e.firstRow
                val col = e.column
                if (row >= 0 && (col == 0 || col == javax.swing.event.TableModelEvent.ALL_COLUMNS)) {
                    val latest = configService.getProxyGroup(configId)
                    val rule = latest?.wsPushRules?.getOrNull(row)
                    if (rule != null) {
                        val newVal = (ruleModel.getValueAt(row, 0) as? Boolean) ?: false
                        rule.enabled = newVal
                    }
                }
            }
        }
        ruleTable.selectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                val row = ruleTable.selectedRow
                val latest = configService.getProxyGroup(configId)
                val rule = latest?.wsPushRules?.getOrNull(row)
                if (row >= 0 && rule != null) {
                    wsCustomMsgArea.text = rule.message
                }
            }
        }
        // 选中规则发送按钮改为 DSL 创建（在下方 group 中定义）

        wsCustomMsgArea.lineWrap = true
        wsCustomMsgArea.wrapStyleWord = true
        wsCustomMsgArea.rows = 4

        val content = com.intellij.ui.dsl.builder.panel {
            group(message("wspanel.title")) {
                row(message("wspanel.target")) { cell(wsTargetCombo).align(AlignX.FILL) }
                row { cell(JBScrollPane(ruleTable)).align(AlignX.FILL) }
                row {
                    button(message("wspanel.send.selected")) {
                        val row = ruleTable.selectedRow
                        if (row < 0) return@button
                        val latest = configService.getProxyGroup(configId)
                        val rule = latest?.wsPushRules?.getOrNull(row) ?: return@button
                        val msgTrimmed = rule.message.trim()
                        if (msgTrimmed.isEmpty()) {
                            JOptionPane.showMessageDialog(null, message("wspanel.send.rule.empty.warn"), message("config.message.info"), JOptionPane.WARNING_MESSAGE)
                            return@button
                        }
                        val target = when (wsTargetCombo.selectedIndex) { 1 -> "ALL"; 2 -> "LATEST"; else -> "MATCH" }
                        mockServerService.sendWsMessage(configId, path = rule.path, message = msgTrimmed, target = target)
                    }.applyToComponent {
                        icon = AllIcons.Actions.Execute
                        isFocusPainted = false
                        toolTipText = message("wspanel.send.selected.tooltip")
                    }
                }
                row { cell(JBScrollPane(wsCustomMsgArea)).align(AlignX.FILL) }
                row {
                    button(message("wspanel.send.custom")) { sendWsCustomMessage() }
                        .applyToComponent {
                            icon = AllIcons.Actions.Execute
                            isFocusPainted = false
                            toolTipText = message("wspanel.send.custom.tooltip")
                        }
                    button(message("wspanel.clear")) { wsCustomMsgArea.text = "" }
                        .applyToComponent {
                            icon = AllIcons.Actions.GC
                            isFocusPainted = false
                        }
                }
            }
        }
        return content
    }

    private fun sendWsCustomMessage() {
        val cfg = configService.getProxyGroup(configId) ?: return
        if (cfg.protocol != "WS") return
        val raw = wsCustomMsgArea.text ?: return
        val msg = raw.trim()
        if (msg.isEmpty()) {
            JOptionPane.showMessageDialog(
                null,
                message("ws.send.empty.warn"),
                message("config.message.info"),
                JOptionPane.WARNING_MESSAGE
            )
            return
        }
        val target = when (wsTargetCombo.selectedIndex) {
            1 -> "ALL"
            2 -> "LATEST"
            else -> "MATCH"
        }
        try {
            mockServerService.sendWsMessage(configId, path = null, message = msg, target = target)
        } catch (e: Exception) {
            thisLogger().warn("Send WS message failed", e)
        }
    }

    // formatting logic extracted to util: formatWsRuleMatcher

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
