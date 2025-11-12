package org.zhongmiao.interceptwave.ui

import com.intellij.ui.components.*
import com.intellij.util.ui.JBUI
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import org.zhongmiao.interceptwave.InterceptWaveBundle.message
import org.zhongmiao.interceptwave.model.ProxyConfig
import java.awt.BorderLayout
 

/** WS 内容区（上游、规则与手动推送设置） */
class WsConfigSection(
    private val project: com.intellij.openapi.project.Project,
    private val config: ProxyConfig,
    private val onChanged: () -> Unit = {}
) {
    private val wsBaseUrlField = JBTextField(config.wsBaseUrl ?: "")
    private val wsPrefixField = JBTextField(config.wsInterceptPrefix ?: "")
    private val wsManualPushCheck = JBCheckBox(message("config.ws.manualpush"), config.wsManualPush)

    private val wsRuleModel = createWsRuleTableModel()
    private val wsRuleTable = com.intellij.ui.table.JBTable(wsRuleModel)

    fun panel(): JBPanel<JBPanel<*>> {
        loadRules()

        val rootPanel = JBPanel<JBPanel<*>>(BorderLayout(10, 10))

        // 顶部“上半区字段行”切换为 UI DSL（复用既有组件与监听，保持行为不变）
        wsBaseUrlField.toolTipText = message("config.ws.baseurl.tooltip")
        wsPrefixField.toolTipText = message("config.ws.prefix.tooltip")
        wsManualPushCheck.toolTipText = message("config.ws.manualpush.tooltip")
        wsBaseUrlField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = onChanged()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = onChanged()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = onChanged()
        })
        wsPrefixField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = onChanged()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = onChanged()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = onChanged()
        })
        wsManualPushCheck.addActionListener { onChanged() }

        wsRuleTable.fillsViewportHeight = true
        // 空数据时也给出适当高度（约 5 行），避免过于矮小
        run {
            val visibleRows = 5
            wsRuleTable.preferredScrollableViewportSize = java.awt.Dimension(
                wsRuleTable.preferredScrollableViewportSize.width,
                wsRuleTable.rowHeight * visibleRows
            )
        }
        // 缩短“启用/模式/间隔”列宽，提升可读性
        runCatching {
            // 启用（checkbox + 文案）列，固定为 40
            wsRuleTable.columnModel.getColumn(0).apply {
                minWidth = JBUI.scale(40)
                preferredWidth = JBUI.scale(40)
                maxWidth = JBUI.scale(40)
            }
            wsRuleTable.columnModel.getColumn(2).apply {
                minWidth = JBUI.scale(60)
                preferredWidth = JBUI.scale(80)
                maxWidth = JBUI.scale(120)
            }
            wsRuleTable.columnModel.getColumn(3).apply {
                minWidth = JBUI.scale(50)
                preferredWidth = JBUI.scale(70)
                maxWidth = JBUI.scale(90)
            }
        }
        val tableScroll = JBScrollPane(wsRuleTable)

        // 使用 DSL group 作为外层边框与内边距（替换 titled border）
        val content = panel {
            group(message("config.ws.title")) {
                row(message("config.ws.baseurl") + ":") { cell(wsBaseUrlField).align(AlignX.FILL) }
                row(message("config.ws.prefix") + ":") { cell(wsPrefixField).align(AlignX.FILL) }
                row { cell(wsManualPushCheck) }
                row { cell(tableScroll).align(AlignX.FILL) }
                row {
                    button(message("wsrule.add.button")) { addRule() }
                        .applyToComponent {
                            icon = com.intellij.icons.AllIcons.General.Add
                            isFocusPainted = false
                        }
                    button(message("wsrule.edit.button")) { editRule() }
                        .applyToComponent {
                            icon = com.intellij.icons.AllIcons.Actions.Edit
                            isFocusPainted = false
                        }
                    button(message("wsrule.delete.button")) { deleteRule() }
                        .applyToComponent {
                            icon = com.intellij.icons.AllIcons.General.Remove
                            isFocusPainted = false
                        }
                }
            }
        }
        rootPanel.add(content, BorderLayout.CENTER)

        return rootPanel
    }

    private fun loadRules() {
        wsRuleModel.rowCount = 0
        appendWsRuleRows(wsRuleModel, config.wsPushRules)
    }

    private fun addRule() {
        val dialog = WsPushRuleDialog(project, null)
        if (dialog.showAndGet()) {
            config.wsPushRules.add(dialog.getRule())
            loadRules()
            onChanged()
        }
    }

    private fun editRule() {
        val idx = wsRuleTable.selectedRow
        if (idx < 0) {
            javax.swing.JOptionPane.showMessageDialog(
                wsRuleTable,
                message("wsrule.select.first.edit"),
                message("config.message.info"),
                javax.swing.JOptionPane.INFORMATION_MESSAGE
            )
            return
        }
        val current = config.wsPushRules[idx]
        val dialog = WsPushRuleDialog(project, current)
        if (dialog.showAndGet()) {
            config.wsPushRules[idx] = dialog.getRule()
            loadRules()
            onChanged()
        }
    }

    private fun deleteRule() {
        val idx = wsRuleTable.selectedRow
        if (idx < 0) {
            javax.swing.JOptionPane.showMessageDialog(
                wsRuleTable,
                message("wsrule.select.first.delete"),
                message("config.message.info"),
                javax.swing.JOptionPane.INFORMATION_MESSAGE
            )
            return
        }
        val result = javax.swing.JOptionPane.showConfirmDialog(
            wsRuleTable,
            message("wsrule.delete.confirm"),
            message("config.message.confirm.title"),
            javax.swing.JOptionPane.YES_NO_OPTION
        )
        if (result == javax.swing.JOptionPane.YES_OPTION) {
            config.wsPushRules.removeAt(idx)
            loadRules()
            onChanged()
        }
    }

    // formatting logic extracted to util: formatWsRuleMatcher

    fun getWsBaseUrl(): String = wsBaseUrlField.text

    fun applyTo(target: ProxyConfig) {
        target.wsBaseUrl = wsBaseUrlField.text.trim().ifEmpty { null }
        target.wsInterceptPrefix = wsPrefixField.text.trim().ifEmpty { null }
        target.wsManualPush = wsManualPushCheck.isSelected
        // WSS 字段不在 UI 中编辑，保持原值
    }
}
