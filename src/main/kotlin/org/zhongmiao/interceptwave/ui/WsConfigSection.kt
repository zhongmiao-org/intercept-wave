package org.zhongmiao.interceptwave.ui

import com.intellij.ui.components.*
import com.intellij.util.ui.JBUI
import org.zhongmiao.interceptwave.InterceptWaveBundle.message
import org.zhongmiao.interceptwave.model.ProxyConfig
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.BorderFactory
import javax.swing.JButton

/** WS 内容区（上游、规则与手动推送设置） */
class WsConfigSection(private val project: com.intellij.openapi.project.Project, private val config: ProxyConfig) {
    private val wsBaseUrlField = JBTextField(config.wsBaseUrl ?: "")
    private val wsPrefixField = JBTextField(config.wsInterceptPrefix ?: "")
    private val wsManualPushCheck = JBCheckBox(message("config.ws.manualpush"), config.wsManualPush)

    private val wsRuleModel = createWsRuleTableModel()
    private val wsRuleTable = com.intellij.ui.table.JBTable(wsRuleModel)

    fun panel(): JBPanel<JBPanel<*>> {
        loadRules()

        val panel = JBPanel<JBPanel<*>>(BorderLayout(10, 10))
        panel.border = BorderFactory.createTitledBorder(message("config.ws.title"))

        val top = JBPanel<JBPanel<*>>(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = JBUI.insets(5)
        }

        // WS 上游
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0
        top.add(JBLabel(message("config.ws.baseurl") + ":"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        wsBaseUrlField.toolTipText = message("config.ws.baseurl.tooltip")
        top.add(wsBaseUrlField, gbc)

        // WS 前缀
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0
        top.add(JBLabel(message("config.ws.prefix") + ":"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        wsPrefixField.toolTipText = message("config.ws.prefix.tooltip")
        top.add(wsPrefixField, gbc)

        // 手动推送开关
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2
        wsManualPushCheck.toolTipText = message("config.ws.manualpush.tooltip")
        top.add(wsManualPushCheck, gbc)

        panel.add(top, BorderLayout.NORTH)

        wsRuleTable.fillsViewportHeight = true
        panel.add(JBScrollPane(wsRuleTable), BorderLayout.CENTER)

        val btnPanel = JBPanel<JBPanel<*>>()
        btnPanel.layout = javax.swing.BoxLayout(btnPanel, javax.swing.BoxLayout.X_AXIS)
        val addBtn = JButton(message("wsrule.add.button"), com.intellij.icons.AllIcons.General.Add)
        addBtn.isFocusPainted = false
        addBtn.addActionListener { addRule() }
        val editBtn = JButton(message("wsrule.edit.button"), com.intellij.icons.AllIcons.Actions.Edit)
        editBtn.isFocusPainted = false
        editBtn.addActionListener { editRule() }
        val delBtn = JButton(message("wsrule.delete.button"), com.intellij.icons.AllIcons.General.Remove)
        delBtn.isFocusPainted = false
        delBtn.addActionListener { deleteRule() }
        btnPanel.add(addBtn); btnPanel.add(javax.swing.Box.createHorizontalStrut(5))
        btnPanel.add(editBtn); btnPanel.add(javax.swing.Box.createHorizontalStrut(5))
        btnPanel.add(delBtn)
        panel.add(btnPanel, BorderLayout.SOUTH)

        return panel
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
