package org.zhongmiao.interceptwave.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import org.zhongmiao.interceptwave.InterceptWaveBundle.message
import org.zhongmiao.interceptwave.model.HeaderOverrideRule
import org.zhongmiao.interceptwave.util.HeaderImportUtil
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.JComponent
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.ScrollPaneConstants

class HeaderImportDialog(
    project: Project,
    private val requestSide: Boolean
) : DialogWrapper(project) {
    private val pasteArea = JTextArea().apply {
        rows = 10
        lineWrap = true
        wrapStyleWord = true
        font = Font(Font.MONOSPACED, Font.PLAIN, font.size)
    }
    private val jsonArea = JTextArea("[]").apply {
        rows = 8
        lineWrap = true
        wrapStyleWord = true
        font = Font(Font.MONOSPACED, Font.PLAIN, font.size)
        isEditable = true
    }

    init {
        title = if (requestSide) message("config.http.headers.import.request.title") else message("config.http.headers.import.response.title")
        setResizable(true)
        init()
    }

    override fun createCenterPanel(): JComponent {
        val root = JBPanel<JBPanel<*>>(BorderLayout(0, 10)).apply {
            preferredSize = JBUI.size(720, 520)
        }

        val pastePanel = panel {
            row {
                val hint = JBLabel(message("config.http.headers.import.help"))
                UiKit.applySecondaryText(hint)
                cell(hint).align(AlignX.FILL)
            }
            row {
                cell(scroll(pasteArea, 170)).align(AlignX.FILL)
            }
            row {
                button(message("config.http.headers.import.normalize")) { normalizePaste() }
                    .applyToComponent {
                        icon = AllIcons.Actions.PrettyPrint
                        isFocusPainted = false
                    }
            }
        }
        val jsonPanel = panel {
            row {
                val label = JBLabel(message("config.http.headers.import.normalized"))
                UiKit.applySecondaryText(label)
                cell(label).align(AlignX.FILL)
            }
            row { cell(scroll(jsonArea, 180)).align(AlignX.FILL) }
        }

        root.add(pastePanel, BorderLayout.NORTH)
        root.add(jsonPanel, BorderLayout.CENTER)
        return root
    }

    override fun doValidate(): ValidationInfo? {
        return try {
            if (jsonArea.text.trim() == "[]" && pasteArea.text.isNotBlank()) {
                normalizePaste()
            }
            val parsed = HeaderImportUtil.parsePrettyJson(jsonArea.text)
            if (parsed.isEmpty()) ValidationInfo(message("config.http.headers.import.empty"), jsonArea) else null
        } catch (e: Exception) {
            ValidationInfo(message("config.http.headers.import.invalid", e.message ?: ""), jsonArea)
        }
    }

    fun rules(): List<HeaderOverrideRule> {
        if (jsonArea.text.trim() == "[]" && pasteArea.text.isNotBlank()) {
            normalizePaste()
        }
        return HeaderImportUtil.parsePrettyJson(jsonArea.text)
    }

    private fun normalizePaste() {
        try {
            val rules = HeaderImportUtil.parse(pasteArea.text)
            jsonArea.text = HeaderImportUtil.toPrettyJson(rules)
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                contentPanel,
                message("config.http.headers.import.invalid", e.message ?: ""),
                message("config.message.error"),
                JOptionPane.ERROR_MESSAGE
            )
        }
    }

    private fun scroll(area: JTextArea, height: Int): JComponent =
        JBScrollPane(area).apply {
            preferredSize = JBUI.size(0, height)
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        }

    override fun createSouthAdditionalPanel(): JPanel = panel {
        row {
            button(message("config.http.headers.import.normalize")) { normalizePaste() }
                .applyToComponent {
                    icon = AllIcons.Actions.PrettyPrint
                    isFocusPainted = false
                }
        }
    }
}
