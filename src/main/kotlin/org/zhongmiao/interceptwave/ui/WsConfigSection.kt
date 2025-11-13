package org.zhongmiao.interceptwave.ui

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
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
        wsBaseUrlField.document.onAnyChange(onChanged)
        wsPrefixField.document.onAnyChange(onChanged)
        wsManualPushCheck.addActionListener { onChanged() }

        wsRuleTable.fillsViewportHeight = true
        // 统一可见行数与列宽
        UiKit.ensureVisibleRows(wsRuleTable, UiKit.DEFAULT_VISIBLE_ROWS)
        UiKit.setEnabledColumnWidth(wsRuleTable, 0)
        UiKit.setFixedColumnWidth(wsRuleTable, 2, UiKit.MODE_COL_WIDTH)
        UiKit.setFixedColumnWidth(wsRuleTable, 3, UiKit.PERIOD_COL_WIDTH)
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
