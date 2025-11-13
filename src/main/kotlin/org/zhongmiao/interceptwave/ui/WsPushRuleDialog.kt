package org.zhongmiao.interceptwave.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import org.zhongmiao.interceptwave.InterceptWaveBundle.message
import org.zhongmiao.interceptwave.model.WsPushRule
import org.zhongmiao.interceptwave.model.WsTimelineItem
import org.zhongmiao.interceptwave.util.JsonNormalizeUtil
import java.awt.BorderLayout
import javax.swing.*
import com.intellij.ui.table.JBTable
import java.awt.CardLayout
import javax.swing.table.DefaultTableModel
import com.intellij.util.ui.JBUI

class WsPushRuleDialog(
    project: Project,
    existing: WsPushRule?
) : DialogWrapper(project) {

    private data class Labeled<T>(val value: T, val label: String) {
        override fun toString(): String = label
    }

    private val enabledCheck = JCheckBox(message("wsrule.enabled"), existing?.enabled ?: true)
    private val pathField = JBTextField(existing?.path ?: "")
    private val modeItems = arrayOf(
        Labeled("off", message("wsrule.mode.off")),
        Labeled("periodic", message("wsrule.mode.periodic")),
        Labeled("timeline", message("wsrule.mode.timeline"))
    )
    private val modeCombo: ComboBox<Labeled<String>> = ComboBox(modeItems)
    private val eventKeyField = JBTextField(existing?.eventKey ?: "action")
    private val eventValueField = JBTextField(existing?.eventValue ?: "")
    private val directionItems = arrayOf(
        Labeled("both", message("wsrule.direction.both")),
        Labeled("in", message("wsrule.direction.in")),
        Labeled("out", message("wsrule.direction.out"))
    )
    private val directionCombo: ComboBox<Labeled<String>> = ComboBox(directionItems)
    private val onOpenCheck = JCheckBox(message("wsrule.onopen"), existing?.onOpenFire ?: false)
    private val interceptCheck = JCheckBox(message("wsrule.intercept"), existing?.intercept ?: false)

    // periodic
    private val periodField = JBTextField((existing?.periodSec ?: 5).toString())
    private val periodicMsgArea = JTextArea(existing?.message ?: "{}").apply {
        lineWrap = true
        wrapStyleWord = true
        rows = 10
        toolTipText = message("wsrule.message.tooltip")
    }
    private val offMsgArea = JTextArea(existing?.message ?: "{}").apply {
        lineWrap = true
        wrapStyleWord = true
        rows = 10
        toolTipText = message("wsrule.message.tooltip")
    }

    // timeline
    private val tlModel = object : DefaultTableModel(arrayOf(message("wsrule.timeline.atms"), message("wsrule.timeline.message")), 0) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false
    }
    private val tlTable = JBTable(tlModel)
    private val loopCheck = JCheckBox(message("wsrule.timeline.loop"), existing?.loop ?: false)

    init {
        init()
        title = if (existing == null) message("wsrule.dialog.title.add") else message("wsrule.dialog.title.edit")
        existing?.let { ex ->
            modeCombo.selectedItem = modeItems.firstOrNull { item -> item.value.equals(ex.mode, ignoreCase = true) } ?: modeItems.first()
            directionCombo.selectedItem = directionItems.firstOrNull { item -> item.value.equals(ex.direction, ignoreCase = true) } ?: directionItems.first()
            ex.timeline.forEach { item -> tlModel.addRow(arrayOf(item.atMs.toString(), item.message)) }
        }
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(10, 10)).apply {
            // Dialog felt too narrow; increase default width
            preferredSize = JBUI.size(820, 560)
        }
        // 顶部“表单字段”改为 UI DSL（复用组件以保持行为/校验一致）
        pathField.toolTipText = message("wsrule.path.tooltip")
        eventKeyField.toolTipText = message("wsrule.event.key.tooltip")
        eventValueField.toolTipText = message("wsrule.event.value.tooltip")
        onOpenCheck.toolTipText = message("wsrule.onopen.tooltip")
        interceptCheck.toolTipText = message("wsrule.intercept.tooltip")

        val top = panel {
            group(message("wsrule.group.basic")) {
                row { cell(enabledCheck) }
                row(message("wsrule.path")) {
                    cell(pathField).align(AlignX.FILL)
                }
                row(message("wsrule.mode")) {
                    cell(modeCombo).align(AlignX.FILL)
                }
                row(message("wsrule.event.key")) {
                    cell(eventKeyField).align(AlignX.FILL)
                }
                row(message("wsrule.event.value")) {
                    cell(eventValueField).align(AlignX.FILL)
                }
                row(message("wsrule.direction")) {
                    cell(directionCombo).align(AlignX.FILL)
                }
                row { cell(onOpenCheck) }
                row { cell(interceptCheck) }
            }
        }

        // periodic subform (纯 DSL)
        val periodicPanel = panel {
            row(message("wsrule.period.sec")) { cell(periodField).align(AlignX.FILL) }
            row(message("wsrule.message")) { cell(JBScrollPane(periodicMsgArea)).align(AlignX.FILL) }
        }

        // timeline subform
        val tlPanel = JPanel(BorderLayout(5, 5))
        tlTable.fillsViewportHeight = true
        // 空数据时也给出适当高度，提升观感（约 5 行高度）
        UiKit.ensureVisibleRows(tlTable, UiKit.DEFAULT_VISIBLE_ROWS)
        tlPanel.add(JBScrollPane(tlTable), BorderLayout.CENTER)
        val tBtns = panel {
            row {
                button(message("wsrule.timeline.add")) { addTimelineItem() }
                    .applyToComponent { icon = AllIcons.General.Add }
                button(message("wsrule.timeline.edit")) { editTimelineItem() }
                    .applyToComponent { icon = AllIcons.Actions.Edit }
                button(message("wsrule.timeline.delete")) { deleteTimelineItem() }
                    .applyToComponent { icon = AllIcons.General.Remove }
            }
        }
        tlPanel.add(tBtns, BorderLayout.SOUTH)
        tlPanel.add(loopCheck, BorderLayout.NORTH)

        // off subform (manual template only, 纯 DSL)
        val offPanel = panel {
            row(message("wsrule.message")) { cell(JBScrollPane(offMsgArea)).align(AlignX.FILL) }
        }

        // center: cards by mode
        val cards = JPanel(CardLayout())
        cards.add(offPanel, "off")
        cards.add(periodicPanel, "periodic")
        cards.add(tlPanel, "timeline")
        (cards.layout as CardLayout).show(cards, modeItems[modeCombo.selectedIndex].value)
        modeCombo.addActionListener { (cards.layout as CardLayout).show(cards, modeItems[modeCombo.selectedIndex].value) }

        panel.add(top, BorderLayout.NORTH)
        panel.add(cards, BorderLayout.CENTER)
        return panel
    }

    override fun createSouthAdditionalPanel(): JPanel {
        // 左下角统一放置“格式化 JSON”，仅针对 off/periodic 模式有效
        return panel {
            row {
                button(message("mockapi.button.format")) {
                    val mode = modeItems[modeCombo.selectedIndex].value
                    when (mode) {
                        "periodic" -> runCatching { periodicMsgArea.text = JsonNormalizeUtil.prettyJson(periodicMsgArea.text) }
                            .onFailure { e ->
                                JOptionPane.showMessageDialog(contentPanel, message("mockapi.message.json.error", e.message ?: ""), message("config.message.error"), JOptionPane.ERROR_MESSAGE)
                            }
                        "off" -> runCatching { offMsgArea.text = JsonNormalizeUtil.prettyJson(offMsgArea.text) }
                            .onFailure { e ->
                                JOptionPane.showMessageDialog(contentPanel, message("mockapi.message.json.error", e.message ?: ""), message("config.message.error"), JOptionPane.ERROR_MESSAGE)
                            }
                        else -> JOptionPane.showMessageDialog(contentPanel, message("wsrule.validation.timeline.empty"), message("config.message.info"), JOptionPane.INFORMATION_MESSAGE)
                    }
                }.applyToComponent {
                    icon = AllIcons.Actions.ReformatCode
                    isFocusPainted = false
                }
            }
        }
    }

    // (message editors migrated to DSL; helper removed)

    private fun addTimelineItem() {
        val at = JOptionPane.showInputDialog(contentPanel, message("wsrule.timeline.at.prompt")) ?: return
        val atMs = at.toIntOrNull()
        if (atMs == null || atMs < 0) {
            JOptionPane.showMessageDialog(contentPanel, message("wsrule.timeline.at.invalid"), message("config.message.info"), JOptionPane.WARNING_MESSAGE)
            return
        }
        val msg = JOptionPane.showInputDialog(contentPanel, message("wsrule.timeline.message.prompt")) ?: return
        tlModel.addRow(arrayOf(atMs.toString(), msg))
        sortTimelineByAt()
    }

    private fun editTimelineItem() {
        val row = tlTable.selectedRow
        if (row < 0) return
        val curAt = (tlModel.getValueAt(row, 0) as String).toIntOrNull() ?: 0
        val at = JOptionPane.showInputDialog(contentPanel, message("wsrule.timeline.at.prompt"), curAt) ?: return
        val atMs = at.toIntOrNull()
        if (atMs == null || atMs < 0) {
            JOptionPane.showMessageDialog(contentPanel, message("wsrule.timeline.at.invalid"), message("config.message.info"), JOptionPane.WARNING_MESSAGE)
            return
        }
        val curMsg = tlModel.getValueAt(row, 1) as String
        val msg = JOptionPane.showInputDialog(contentPanel, message("wsrule.timeline.message.prompt"), curMsg) ?: return
        tlModel.setValueAt(atMs.toString(), row, 0)
        tlModel.setValueAt(msg, row, 1)
        sortTimelineByAt()
    }

    private fun deleteTimelineItem() {
        val row = tlTable.selectedRow
        if (row >= 0) tlModel.removeRow(row)
    }

    private fun sortTimelineByAt() {
        val rows = (0 until tlModel.rowCount).map { i ->
            Pair((tlModel.getValueAt(i, 0) as String).toIntOrNull() ?: 0, tlModel.getValueAt(i, 1) as String)
        }.sortedBy { it.first }
        tlModel.rowCount = 0
        rows.forEach { tlModel.addRow(arrayOf(it.first.toString(), it.second)) }
    }

    fun getRule(): WsPushRule {
        val mode = modeItems[modeCombo.selectedIndex].value
        val timeline = mutableListOf<WsTimelineItem>()
        for (i in 0 until tlModel.rowCount) {
            val at = (tlModel.getValueAt(i, 0) as String).toIntOrNull() ?: 0
            val msg = tlModel.getValueAt(i, 1) as String
            timeline.add(WsTimelineItem(at, msg))
        }
        val msgValue = when (mode) {
            "periodic" -> periodicMsgArea.text.trim()
            else -> offMsgArea.text.trim()
        }
        return WsPushRule(
            enabled = enabledCheck.isSelected,
            path = pathField.text.trim(),
            eventKey = eventKeyField.text.trim().ifEmpty { null },
            eventValue = eventValueField.text.trim().ifEmpty { null },
            direction = directionItems[directionCombo.selectedIndex].value,
            intercept = interceptCheck.isSelected,
            mode = mode,
            periodSec = periodField.text.toIntOrNull() ?: 5,
            message = msgValue,
            timeline = timeline,
            loop = loopCheck.isSelected,
            onOpenFire = onOpenCheck.isSelected
        )
    }

    override fun doValidate(): ValidationInfo? {
        val path = pathField.text.trim()
        val hasPath = path.isNotEmpty()
        val evKey = eventKeyField.text.trim()
        val evVal = eventValueField.text.trim()
        val hasEvent = evKey.isNotEmpty() && evVal.isNotEmpty()
        if (!hasPath && !hasEvent) return ValidationInfo(message("wsrule.validation.matcher.empty"), pathField)
        if (hasPath && !path.startsWith("/")) return ValidationInfo(message("wsrule.validation.path"), pathField)
        val mode = modeItems[modeCombo.selectedIndex].value
        if (mode == "periodic") {
            val sec = periodField.text.toIntOrNull()
            if (sec == null || sec < 1) return ValidationInfo(message("wsrule.validation.period"), periodField)
            if (periodicMsgArea.text.trim().isEmpty()) return ValidationInfo(message("wsrule.validation.message"), periodicMsgArea)
        } else if (mode == "timeline") {
            if (tlModel.rowCount == 0) return ValidationInfo(message("wsrule.validation.timeline.empty"), tlTable)
            // 校验升序、非负
            var last = -1
            for (i in 0 until tlModel.rowCount) {
                val at = (tlModel.getValueAt(i, 0) as String).toIntOrNull() ?: -1
                if (at < 0 || at < last) return ValidationInfo(message("wsrule.validation.timeline.order"), tlTable)
                last = at
            }
        }
        return null
    }
}
