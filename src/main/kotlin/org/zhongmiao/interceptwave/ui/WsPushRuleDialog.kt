package org.zhongmiao.interceptwave.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import org.zhongmiao.interceptwave.InterceptWaveBundle.message
import org.zhongmiao.interceptwave.model.WsPushRule
import org.zhongmiao.interceptwave.model.WsTimelineItem
import org.zhongmiao.interceptwave.util.JsonNormalizeUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*
import com.intellij.ui.table.JBTable
import java.awt.CardLayout
import javax.swing.table.DefaultTableModel

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

    // periodic
    private val periodField = JBTextField((existing?.periodSec ?: 5).toString())
    private val periodicMsgArea = JTextArea(existing?.message ?: "{}")

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
            preferredSize = Dimension(820, 560)
        }
        val top = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = JBUI.insets(5)
        }

        // enabled
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2
        top.add(enabledCheck, gbc)
        gbc.gridwidth = 1

        // path
        gbc.gridx = 0; gbc.gridy = 1
        top.add(JBLabel(message("wsrule.path")), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        pathField.toolTipText = message("wsrule.path.tooltip")
        top.add(pathField, gbc)
        gbc.weightx = 0.0

        // mode
        gbc.gridx = 0; gbc.gridy = 2
        top.add(JBLabel(message("wsrule.mode")), gbc)
        gbc.gridx = 1
        top.add(modeCombo, gbc)

        // event key/value
        gbc.gridx = 0; gbc.gridy = 3
        top.add(JBLabel(message("wsrule.event.key")), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        eventKeyField.toolTipText = message("wsrule.event.key.tooltip")
        top.add(eventKeyField, gbc)
        gbc.weightx = 0.0

        gbc.gridx = 0; gbc.gridy = 4
        top.add(JBLabel(message("wsrule.event.value")), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        eventValueField.toolTipText = message("wsrule.event.value.tooltip")
        top.add(eventValueField, gbc)
        gbc.weightx = 0.0

        // direction
        gbc.gridx = 0; gbc.gridy = 5
        top.add(JBLabel(message("wsrule.direction")), gbc)
        gbc.gridx = 1
        top.add(directionCombo, gbc)

        // onOpen
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2
        onOpenCheck.toolTipText = message("wsrule.onopen.tooltip")
        top.add(onOpenCheck, gbc)
        gbc.gridwidth = 1

        // periodic subform
        val periodicPanel = JPanel(GridBagLayout())
        val pgbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = JBUI.insets(5)
        }
        pgbc.gridx = 0; pgbc.gridy = 0
        periodicPanel.add(JBLabel(message("wsrule.period.sec")), pgbc)
        pgbc.gridx = 1
        periodicPanel.add(periodField, pgbc)
        pgbc.gridx = 0; pgbc.gridy = 1
        periodicPanel.add(JBLabel(message("wsrule.message")), pgbc)
        pgbc.gridx = 1; pgbc.weightx = 1.0; pgbc.weighty = 1.0; pgbc.fill = GridBagConstraints.BOTH
        periodicMsgArea.lineWrap = true
        periodicMsgArea.wrapStyleWord = true
        periodicMsgArea.font = UIManager.getFont("TextArea.font")
        val pscroll = JBScrollPane(periodicMsgArea)
        periodicPanel.add(pscroll, pgbc)
        pgbc.gridy = 2; pgbc.fill = GridBagConstraints.NONE; pgbc.weighty = 0.0; pgbc.anchor = GridBagConstraints.EAST
        val fmtBtn = JButton(message("mockapi.button.format"), AllIcons.Actions.ReformatCode)
        fmtBtn.addActionListener {
            runCatching { periodicMsgArea.text = JsonNormalizeUtil.prettyJson(periodicMsgArea.text) }
                .onFailure {
                    JOptionPane.showMessageDialog(
                        periodicPanel,
                        message("mockapi.message.json.error", it.message ?: ""),
                        message("config.message.error"),
                        JOptionPane.ERROR_MESSAGE
                    )
                }
        }
        periodicPanel.add(fmtBtn, pgbc)

        // timeline subform
        val tlPanel = JPanel(BorderLayout(5, 5))
        tlTable.fillsViewportHeight = true
        tlPanel.add(JBScrollPane(tlTable), BorderLayout.CENTER)
        val tBtns = JPanel()
        tBtns.layout = BoxLayout(tBtns, BoxLayout.X_AXIS)
        val addBtn = JButton(message("wsrule.timeline.add"), AllIcons.General.Add)
        addBtn.addActionListener { addTimelineItem() }
        val editBtn = JButton(message("wsrule.timeline.edit"), AllIcons.Actions.Edit)
        editBtn.addActionListener { editTimelineItem() }
        val delBtn = JButton(message("wsrule.timeline.delete"), AllIcons.General.Remove)
        delBtn.addActionListener { deleteTimelineItem() }
        tBtns.add(addBtn); tBtns.add(Box.createHorizontalStrut(5)); tBtns.add(editBtn); tBtns.add(Box.createHorizontalStrut(5)); tBtns.add(delBtn)
        tlPanel.add(tBtns, BorderLayout.SOUTH)
        tlPanel.add(loopCheck, BorderLayout.NORTH)

        // center: cards by mode
        val cards = JPanel(CardLayout())
        cards.add(JPanel(), "off")
        cards.add(periodicPanel, "periodic")
        cards.add(tlPanel, "timeline")
        (cards.layout as CardLayout).show(cards, modeItems[modeCombo.selectedIndex].value)
        modeCombo.addActionListener { (cards.layout as CardLayout).show(cards, modeItems[modeCombo.selectedIndex].value) }

        panel.add(top, BorderLayout.NORTH)
        panel.add(cards, BorderLayout.CENTER)
        return panel
    }

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
        return WsPushRule(
            enabled = enabledCheck.isSelected,
            path = pathField.text.trim(),
            eventKey = eventKeyField.text.trim().ifEmpty { null },
            eventValue = eventValueField.text.trim().ifEmpty { null },
            direction = directionItems[directionCombo.selectedIndex].value,
            mode = mode,
            periodSec = periodField.text.toIntOrNull() ?: 5,
            message = periodicMsgArea.text.trim(),
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
