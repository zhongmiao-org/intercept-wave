package org.zhongmiao.interceptwave.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import org.zhongmiao.interceptwave.InterceptWaveBundle.message
import org.zhongmiao.interceptwave.model.ProxyConfig
import org.zhongmiao.interceptwave.model.WsPushRule
import org.zhongmiao.interceptwave.model.WsTimelineItem
import org.zhongmiao.interceptwave.util.JsonNormalizeUtil
import org.zhongmiao.interceptwave.util.formatWsRuleMatcher
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.FlowLayout
import java.util.UUID
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants
import javax.swing.table.DefaultTableModel

/** WS 内容区：左侧规则列表 + 右侧当前规则编辑/行为 */
class WsConfigSection(
    private val project: Project,
    private val config: ProxyConfig,
    private val initialWsRuleIndex: Int? = null,
    private val onChanged: () -> Unit = {}
) {
    private data class Labeled<T>(val value: T, val label: String) {
        override fun toString(): String = label
    }

    private val wsBaseUrlField = JBTextField(config.wsBaseUrl ?: "")
    private val wsPrefixField = JBTextField(config.wsInterceptPrefix ?: "")
    private val wsManualPushCheck = JBCheckBox(message("config.ws.manualpush"), config.wsManualPush)

    private val ruleListModel = DefaultListModel<WsPushRule>()
    private val ruleList = JBList(ruleListModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = RuleListRenderer()
        visibleRowCount = 8
    }

    private val enabledCheck = JBCheckBox(message("wsrule.enabled"))
    private val pathField = JBTextField()
    private val modeItems = arrayOf(
        Labeled("off", message("wsrule.mode.off")),
        Labeled("periodic", message("wsrule.mode.periodic")),
        Labeled("timeline", message("wsrule.mode.timeline"))
    )
    private val modeCombo: ComboBox<Labeled<String>> = ComboBox(modeItems)
    private val eventKeyField = JBTextField()
    private val eventValueField = JBTextField()
    private val directionItems = arrayOf(
        Labeled("both", message("wsrule.direction.both")),
        Labeled("in", message("wsrule.direction.in")),
        Labeled("out", message("wsrule.direction.out"))
    )
    private val directionCombo: ComboBox<Labeled<String>> = ComboBox(directionItems)
    private val onOpenCheck = JBCheckBox(message("wsrule.onopen"))
    private val interceptCheck = JBCheckBox(message("wsrule.intercept"))

    private val periodField = JBTextField("5")
    private val periodicMsgArea = javax.swing.JTextArea("{}").apply {
        lineWrap = true
        wrapStyleWord = true
        rows = 8
    }
    private val offMsgArea = javax.swing.JTextArea("{}").apply {
        lineWrap = true
        wrapStyleWord = true
        rows = 8
    }
    private val timelineModel = object : DefaultTableModel(arrayOf(message("wsrule.timeline.atms"), message("wsrule.timeline.message")), 0) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false
    }
    private val timelineTable = JBTable(timelineModel)
    private val loopCheck = JBCheckBox(message("wsrule.timeline.loop"))
    private val behaviorCard = JPanel(CardLayout())

    private var selectedRuleIndex = -1
    private var syncingDetails = false

    fun panel(): JBPanel<JBPanel<*>> {
        wsBaseUrlField.toolTipText = message("config.ws.baseurl.tooltip")
        wsPrefixField.toolTipText = message("config.ws.prefix.tooltip")
        wsManualPushCheck.toolTipText = message("config.ws.manualpush.tooltip")
        wsBaseUrlField.document.onAnyChange(onChanged)
        wsPrefixField.document.onAnyChange(onChanged)
        wsManualPushCheck.addActionListener { onChanged() }

        timelineTable.fillsViewportHeight = true
        UiKit.ensureVisibleRows(timelineTable, UiKit.DEFAULT_VISIBLE_ROWS)
        loadRules()
        attachListeners()
        ensureSelection()

        behaviorCard.add(createOffBehaviorPanel(), "off")
        behaviorCard.add(createPeriodicBehaviorPanel(), "periodic")
        behaviorCard.add(createTimelineBehaviorPanel(), "timeline")

        val rootPanel = JBPanel<JBPanel<*>>(BorderLayout(10, 10))
        val content = panel {
            group(message("config.ws.title")) {
                row(message("config.ws.baseurl") + ":") { cell(wsBaseUrlField).align(AlignX.FILL) }
                row(message("config.ws.prefix") + ":") { cell(wsPrefixField).align(AlignX.FILL) }
                row { cell(wsManualPushCheck) }
                row { cell(createRuleEditorContent()).align(AlignX.FILL) }
            }
        }
        rootPanel.add(content, BorderLayout.CENTER)
        updateRuleDetails()
        return rootPanel
    }

    private fun createRuleEditorContent(): JComponent {
        val container = JBPanel<JBPanel<*>>(BorderLayout(12, 0))
        val sidebarWrapper = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            preferredSize = JBUI.size(340, 0)
            add(createRuleSidebar(), BorderLayout.CENTER)
        }
        val detailWrapper = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(createRuleDetailPanel(), BorderLayout.CENTER)
        }
        container.add(sidebarWrapper, BorderLayout.WEST)
        container.add(detailWrapper, BorderLayout.CENTER)
        return container
    }

    private fun createRuleSidebar(): JComponent {
        val ruleScroll = JBScrollPane(ruleList).apply {
            border = BorderFactory.createEmptyBorder()
            viewportBorder = null
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS
            preferredSize = JBUI.size(0, 420)
            minimumSize = JBUI.size(280, 420)
        }
        val titlePanel = panel {
            row {
                val title = JBLabel(message("toolwindow.config.wsrules.title"))
                cell(title).align(AlignX.FILL)
            }
        }
        val actions = panel {
            row {
                button(message("wsrule.add.button")) { addRule() }
                    .applyToComponent {
                        icon = AllIcons.General.Add
                        UiKit.applyToolbarButtonStyle(this)
                    }
                button(message("wsrule.delete.button")) { deleteRule() }
                    .applyToComponent {
                        icon = AllIcons.General.Remove
                        UiKit.applyToolbarButtonStyle(this)
                    }
            }
        }
        return JBPanel<JBPanel<*>>(BorderLayout(0, 8)).apply {
            add(JBPanel<JBPanel<*>>(BorderLayout(0, 8)).apply {
                add(titlePanel, BorderLayout.NORTH)
                add(actions, BorderLayout.SOUTH)
            }, BorderLayout.NORTH)
            add(ruleScroll, BorderLayout.CENTER)
        }
    }

    private fun createRuleDetailPanel(): JComponent {
        val detailsForm = panel {
            row { cell(enabledCheck) }
            row(message("wsrule.path")) { cell(pathField).align(AlignX.FILL) }
            row(message("wsrule.mode")) { cell(modeCombo).align(AlignX.FILL) }
            row(message("wsrule.event.key")) { cell(eventKeyField).align(AlignX.FILL) }
            row(message("wsrule.event.value")) { cell(eventValueField).align(AlignX.FILL) }
            row(message("wsrule.direction")) { cell(directionCombo).align(AlignX.FILL) }
            row {
                cell(onOpenCheck)
                cell(interceptCheck)
            }
        }

        return JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(createSectionCard(message("config.ws.rule.details.section"), message("toolwindow.config.wsrules.help"), detailsForm))
            add(Box.createVerticalStrut(JBUI.scale(12)))
            add(createSectionCard(message("config.ws.rule.behavior.section"), null, behaviorCard))
        }
    }

    private fun createOffBehaviorPanel(): JComponent {
        val formatButton = JButton(message("mockapi.button.format"), AllIcons.Actions.PrettyPrint).apply {
            UiKit.applyToolbarButtonStyle(this)
            addActionListener { formatMessageArea(offMsgArea) }
        }
        return JBPanel<JBPanel<*>>(BorderLayout(0, 8)).apply {
            add(panel {
                row(message("wsrule.message")) { cell(JBScrollPane(offMsgArea)).align(AlignX.FILL) }
            }, BorderLayout.CENTER)
            add(JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                isOpaque = false
                add(formatButton)
            }, BorderLayout.SOUTH)
        }
    }

    private fun createPeriodicBehaviorPanel(): JComponent {
        val formatButton = JButton(message("mockapi.button.format"), AllIcons.Actions.PrettyPrint).apply {
            UiKit.applyToolbarButtonStyle(this)
            addActionListener { formatMessageArea(periodicMsgArea) }
        }
        return JBPanel<JBPanel<*>>(BorderLayout(0, 8)).apply {
            add(panel {
                row(message("wsrule.period.sec")) { cell(periodField).align(AlignX.FILL) }
                row(message("wsrule.message")) { cell(JBScrollPane(periodicMsgArea)).align(AlignX.FILL) }
            }, BorderLayout.CENTER)
            add(JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                isOpaque = false
                add(formatButton)
            }, BorderLayout.SOUTH)
        }
    }

    private fun createTimelineBehaviorPanel(): JComponent {
        val toolbar = panel {
            row {
                button(message("wsrule.timeline.add")) { addTimelineItem() }
                    .applyToComponent {
                        icon = AllIcons.General.Add
                        UiKit.applyToolbarButtonStyle(this)
                    }
                button(message("wsrule.timeline.edit")) { editTimelineItem() }
                    .applyToComponent {
                        icon = AllIcons.Actions.Edit
                        UiKit.applyToolbarButtonStyle(this)
                    }
                button(message("wsrule.timeline.delete")) { deleteTimelineItem() }
                    .applyToComponent {
                        icon = AllIcons.General.Remove
                        UiKit.applyToolbarButtonStyle(this)
                    }
            }
        }
        return JBPanel<JBPanel<*>>(BorderLayout(0, 8)).apply {
            add(loopCheck, BorderLayout.NORTH)
            add(JBScrollPane(timelineTable).apply {
                border = null
                viewportBorder = null
            }, BorderLayout.CENTER)
            add(toolbar, BorderLayout.SOUTH)
        }
    }

    private fun createSectionCard(title: String, description: String?, content: JComponent): JComponent {
        val titleLabel = JBLabel(title).apply {
            font = font.deriveFont(java.awt.Font.BOLD, font.size2D + 1f)
        }
        val header = JBPanel<JBPanel<*>>(BorderLayout(0, 4)).apply {
            isOpaque = false
            add(titleLabel, BorderLayout.NORTH)
            if (!description.isNullOrBlank()) {
                val descLabel = JBLabel(description).apply { UiKit.applySecondaryText(this) }
                add(descLabel, BorderLayout.CENTER)
            }
        }
        return JBPanel<JBPanel<*>>(BorderLayout(0, 10)).apply {
            border = BorderFactory.createCompoundBorder(
                UiKit.roundedLineBorder(JBColor.border(), 1),
                JBUI.Borders.empty(12)
            )
            add(header, BorderLayout.NORTH)
            add(content, BorderLayout.CENTER)
            alignmentX = Component.LEFT_ALIGNMENT
        }
    }

    private fun attachListeners() {
        ruleList.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) handleRuleSelectionChanged()
        }
        enabledCheck.addActionListener { onRuleDetailChanged() }
        pathField.document.onAnyChange { onRuleDetailChanged() }
        modeCombo.addActionListener {
            updateBehaviorCard()
            onRuleDetailChanged()
        }
        eventKeyField.document.onAnyChange { onRuleDetailChanged() }
        eventValueField.document.onAnyChange { onRuleDetailChanged() }
        directionCombo.addActionListener { onRuleDetailChanged() }
        onOpenCheck.addActionListener { onRuleDetailChanged() }
        interceptCheck.addActionListener { onRuleDetailChanged() }
        periodField.document.onAnyChange { onRuleDetailChanged() }
        offMsgArea.document.onAnyChange { onRuleDetailChanged() }
        periodicMsgArea.document.onAnyChange { onRuleDetailChanged() }
        loopCheck.addActionListener { onRuleDetailChanged() }
    }

    private fun loadRules() {
        ruleListModel.removeAllElements()
        config.wsPushRules.forEach { ruleListModel.addElement(it) }
        ruleList.revalidate()
        ruleList.repaint()
    }

    private fun ensureSelection() {
        if (config.wsPushRules.isNotEmpty()) {
            selectedRuleIndex = initialWsRuleIndex?.coerceIn(0, config.wsPushRules.lastIndex)
                ?: selectedRuleIndex.takeIf { it in config.wsPushRules.indices }
                ?: 0
            ruleList.selectedIndex = selectedRuleIndex
            ruleList.ensureIndexIsVisible(selectedRuleIndex)
        } else {
            selectedRuleIndex = -1
        }
    }

    private fun handleRuleSelectionChanged() {
        val newIndex = ruleList.selectedIndex
        if (newIndex !in config.wsPushRules.indices) return
        if (newIndex == selectedRuleIndex) return
        commitCurrentRule()
        selectedRuleIndex = newIndex
        updateRuleDetails()
    }

    private fun onRuleDetailChanged() {
        if (syncingDetails) return
        commitCurrentRule()
        loadRules()
        restoreSelection()
        onChanged()
    }

    private fun restoreSelection() {
        if (selectedRuleIndex in 0 until ruleListModel.size()) {
            ruleList.selectedIndex = selectedRuleIndex
            ruleList.ensureIndexIsVisible(selectedRuleIndex)
        }
    }

    private fun updateRuleDetails() {
        syncingDetails = true
        try {
            val rule = currentRule()
            if (rule == null) {
                enabledCheck.isSelected = false
                pathField.text = ""
                modeCombo.selectedItem = modeItems.first()
                eventKeyField.text = ""
                eventValueField.text = ""
                directionCombo.selectedItem = directionItems.first()
                onOpenCheck.isSelected = false
                interceptCheck.isSelected = false
                periodField.text = "5"
                offMsgArea.text = "{}"
                periodicMsgArea.text = "{}"
                timelineModel.rowCount = 0
                loopCheck.isSelected = false
            } else {
                enabledCheck.isSelected = rule.enabled
                pathField.text = rule.path
                modeCombo.selectedItem = modeItems.firstOrNull { it.value.equals(rule.mode, true) } ?: modeItems.first()
                eventKeyField.text = rule.eventKey ?: ""
                eventValueField.text = rule.eventValue ?: ""
                directionCombo.selectedItem = directionItems.firstOrNull { it.value.equals(rule.direction, true) } ?: directionItems.first()
                onOpenCheck.isSelected = rule.onOpenFire
                interceptCheck.isSelected = rule.intercept
                periodField.text = rule.periodSec.toString()
                offMsgArea.text = rule.message
                periodicMsgArea.text = rule.message
                timelineModel.rowCount = 0
                rule.timeline.forEach { item ->
                    timelineModel.addRow(arrayOf(item.atMs.toString(), item.message))
                }
                loopCheck.isSelected = rule.loop
            }
        } finally {
            syncingDetails = false
        }
        updateBehaviorCard()
    }

    private fun updateBehaviorCard() {
        val mode = selectedComboValue(modeCombo, "off")
        (behaviorCard.layout as CardLayout).show(behaviorCard, mode)
    }

    private fun commitCurrentRule() {
        val rule = currentRule() ?: return
        rule.enabled = enabledCheck.isSelected
        rule.path = pathField.text.trim()
        rule.mode = selectedComboValue(modeCombo, "off")
        rule.eventKey = eventKeyField.text.trim().ifEmpty { null }
        rule.eventValue = eventValueField.text.trim().ifEmpty { null }
        rule.direction = selectedComboValue(directionCombo, "both")
        rule.onOpenFire = onOpenCheck.isSelected
        rule.intercept = interceptCheck.isSelected
        rule.periodSec = periodField.text.toIntOrNull() ?: 5
        rule.message = when (rule.mode) {
            "periodic" -> periodicMsgArea.text.trim()
            else -> offMsgArea.text.trim()
        }
        rule.timeline = mutableListOf<WsTimelineItem>().apply {
            for (i in 0 until timelineModel.rowCount) {
                val at = (timelineModel.getValueAt(i, 0) as? String)?.toIntOrNull() ?: 0
                val msg = timelineModel.getValueAt(i, 1) as? String ?: "{}"
                add(WsTimelineItem(at, msg))
            }
        }
        rule.loop = loopCheck.isSelected
        ruleList.repaint()
    }

    private fun addRule() {
        val newRule = WsPushRule(
            enabled = true,
            path = "/ws/${UUID.randomUUID().toString().take(4)}",
            eventKey = "action",
            eventValue = null,
            direction = "both",
            intercept = false,
            mode = "off",
            periodSec = 5,
            message = "{}",
            timeline = mutableListOf(),
            loop = false,
            onOpenFire = false
        )
        config.wsPushRules.add(newRule)
        loadRules()
        selectedRuleIndex = config.wsPushRules.lastIndex
        restoreSelection()
        updateRuleDetails()
        onChanged()
    }

    private fun deleteRule() {
        val idx = selectedRuleIndex
        if (idx !in config.wsPushRules.indices) {
            Messages.showInfoMessage(project, message("wsrule.select.first.delete"), message("config.message.info"))
            return
        }
        val result = Messages.showYesNoDialog(project, message("wsrule.delete.confirm"), message("config.message.confirm.title"), Messages.getQuestionIcon())
        if (result == Messages.YES) {
            config.wsPushRules.removeAt(idx)
            loadRules()
            selectedRuleIndex = idx.coerceAtMost(config.wsPushRules.lastIndex)
            ensureSelection()
            updateRuleDetails()
            onChanged()
        }
    }

    private fun addTimelineItem() {
        val at = JOptionPane.showInputDialog(null, message("wsrule.timeline.at.prompt")) ?: return
        val atMs = at.toIntOrNull()
        if (atMs == null || atMs < 0) {
            JOptionPane.showMessageDialog(null, message("wsrule.timeline.at.invalid"), message("config.message.info"), JOptionPane.WARNING_MESSAGE)
            return
        }
        val msg = JOptionPane.showInputDialog(null, message("wsrule.timeline.message.prompt")) ?: return
        timelineModel.addRow(arrayOf(atMs.toString(), msg))
        sortTimelineByAt()
        onRuleDetailChanged()
    }

    private fun editTimelineItem() {
        val row = timelineTable.selectedRow
        if (row < 0) return
        val curAt = (timelineModel.getValueAt(row, 0) as? String)?.toIntOrNull() ?: 0
        val at = JOptionPane.showInputDialog(null, message("wsrule.timeline.at.prompt"), curAt) ?: return
        val atMs = at.toIntOrNull()
        if (atMs == null || atMs < 0) {
            JOptionPane.showMessageDialog(null, message("wsrule.timeline.at.invalid"), message("config.message.info"), JOptionPane.WARNING_MESSAGE)
            return
        }
        val curMsg = timelineModel.getValueAt(row, 1) as? String ?: "{}"
        val msg = JOptionPane.showInputDialog(null, message("wsrule.timeline.message.prompt"), curMsg) ?: return
        timelineModel.setValueAt(atMs.toString(), row, 0)
        timelineModel.setValueAt(msg, row, 1)
        sortTimelineByAt()
        onRuleDetailChanged()
    }

    private fun deleteTimelineItem() {
        val row = timelineTable.selectedRow
        if (row >= 0) {
            timelineModel.removeRow(row)
            onRuleDetailChanged()
        }
    }

    private fun sortTimelineByAt() {
        val rows = (0 until timelineModel.rowCount).map { i ->
            Pair((timelineModel.getValueAt(i, 0) as? String)?.toIntOrNull() ?: 0, timelineModel.getValueAt(i, 1) as? String ?: "{}")
        }.sortedBy { it.first }
        timelineModel.rowCount = 0
        rows.forEach { timelineModel.addRow(arrayOf(it.first.toString(), it.second)) }
    }

    private fun formatMessageArea(area: javax.swing.JTextArea) {
        runCatching {
            area.text = JsonNormalizeUtil.prettyJson(area.text)
            onRuleDetailChanged()
        }.onFailure { e ->
            JOptionPane.showMessageDialog(null, message("mockapi.message.json.error", e.message ?: ""), message("config.message.error"), JOptionPane.ERROR_MESSAGE)
        }
    }

    fun getWsBaseUrl(): String = wsBaseUrlField.text

    fun applyTo(target: ProxyConfig) {
        commitCurrentRule()
        target.wsBaseUrl = wsBaseUrlField.text.trim().ifEmpty { null }
        target.wsInterceptPrefix = wsPrefixField.text.trim().ifEmpty { null }
        target.wsManualPush = wsManualPushCheck.isSelected
    }

    private fun currentRule(): WsPushRule? = config.wsPushRules.getOrNull(selectedRuleIndex)

    private fun selectedComboValue(combo: ComboBox<Labeled<String>>, default: String): String =
        combo.selectedIndex.takeIf { it in 0 until combo.itemCount }?.let { combo.getItemAt(it).value } ?: default

    private inner class RuleListRenderer : JPanel(BorderLayout(0, 6)), ListCellRenderer<WsPushRule> {
        private val titleLabel = JBLabel()
        private val subtitleLabel = JBLabel()
        private val badgeLabel = JBLabel()
        private val contentPanel = JPanel(BorderLayout(0, 6))

        init {
            isOpaque = false
            val textPanel = JPanel().apply {
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                titleLabel.alignmentX = LEFT_ALIGNMENT
                subtitleLabel.alignmentX = LEFT_ALIGNMENT
                add(titleLabel)
                add(Box.createVerticalStrut(JBUI.scale(4)))
                add(subtitleLabel)
            }
            val footer = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                isOpaque = false
                add(badgeLabel)
            }
            contentPanel.isOpaque = true
            contentPanel.border = JBUI.Borders.empty(8, 10)
            contentPanel.add(textPanel, BorderLayout.NORTH)
            contentPanel.add(footer, BorderLayout.SOUTH)
            add(contentPanel, BorderLayout.CENTER)
        }

        override fun getListCellRendererComponent(
            list: JList<out WsPushRule>?,
            value: WsPushRule?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            val rule = value ?: return this
            titleLabel.text = formatWsRuleMatcher(rule)
            titleLabel.font = titleLabel.font.deriveFont(java.awt.Font.BOLD)
            subtitleLabel.text = "${rule.mode.uppercase()} · ${rule.direction.uppercase()}"
            subtitleLabel.foreground = if (isSelected) JBColor(0x1E4F91, 0xDCE8FF) else JBColor.GRAY

            val enabledTone = if (rule.enabled) UiKit.StatusTone.GREEN else UiKit.StatusTone.RED
            badgeLabel.text = if (rule.enabled) message("wsrule.enabled") else message("toolwindow.status.disabled")
            badgeLabel.icon = UiKit.statusDotIcon(enabledTone)
            badgeLabel.foreground = if (rule.enabled) JBColor(0x137333, 0x8FD18B) else JBColor(0xC5221F, 0xE06C75)

            contentPanel.background = if (isSelected) JBColor(0xEAF3FF, 0x2C4369) else JBColor.background()
            titleLabel.foreground = if (isSelected) JBColor(0x163E73, 0xEAF2FF) else JBColor.foreground()
            contentPanel.border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, JBUI.scale(3), 0, 0, if (isSelected) JBColor(0x8AB4FF, 0x78A9FF) else JBColor.background()),
                JBUI.Borders.empty(8, 10)
            )
            return this
        }
    }
}
