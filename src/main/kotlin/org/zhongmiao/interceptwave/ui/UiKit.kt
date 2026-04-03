package org.zhongmiao.interceptwave.ui

import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.RenderingHints
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.border.Border
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.Document

/**
 * UI 通用小工具与常量，统一表格列宽、可见行数与输入监听。
 */
object UiKit {
    enum class StatusTone {
        GREEN,
        YELLOW,
        RED,
        BLUE,
        GRAY
    }

    const val ENABLED_COL_WIDTH = 40
    const val DEFAULT_VISIBLE_ROWS = 5

    fun setEnabledColumnWidth(table: JTable, index: Int = 0) {
        runCatching {
            table.columnModel.getColumn(index).apply {
                val w = JBUI.scale(ENABLED_COL_WIDTH)
                minWidth = w; preferredWidth = w; maxWidth = w
            }
        }
    }

    fun ensureVisibleRows(table: JTable, rows: Int = DEFAULT_VISIBLE_ROWS) {
        val h = table.rowHeight * rows
        val size = table.preferredScrollableViewportSize
        if (size.height != h) {
            size.height = h
            table.preferredScrollableViewportSize = size
        }
    }

    fun installTableTooltips(table: JTable) {
        table.toolTipText = ""
        table.addMouseMotionListener(object : java.awt.event.MouseMotionAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                val row = table.rowAtPoint(e.point)
                val column = table.columnAtPoint(e.point)
                table.toolTipText = if (row >= 0 && column >= 0) {
                    table.getValueAt(row, column)?.toString()?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
            }
        })
    }

    fun applyCompactTableStyle(table: JTable) {
        table.rowHeight = JBUI.scale(24)
        table.intercellSpacing = java.awt.Dimension(0, 0)
        table.setShowGrid(false)
    }

    fun createEmptyState(title: String, description: String, icon: Icon = AllIcons.General.BalloonInformation): JComponent {
        val titleLabel = JBLabel(title, icon, SwingConstants.LEFT).apply {
            font = font.deriveFont(font.size2D + 0.5f)
        }
        val descLabel = JBLabel(description).apply {
            foreground = JBColor.GRAY
        }
        return panel {
            row { cell(titleLabel) }
            row { cell(descLabel) }
        }.apply {
            border = JBUI.Borders.empty(6, 0)
        }
    }

    fun applyStatusStyle(label: JBLabel, text: String, tone: StatusTone) {
        label.text = text
        label.icon = StatusDotIcon(tone)
        label.foreground = statusForeground(tone)
        label.border = JBUI.Borders.empty()
    }

    fun applyInlineStatusText(label: JBLabel, text: String, tone: StatusTone) {
        label.text = text
        label.icon = null
        label.foreground = statusForeground(tone)
        label.border = JBUI.Borders.empty()
    }

    fun statusDotIcon(tone: StatusTone): Icon = StatusDotIcon(tone)

    fun statusDotIcon(color: Color): Icon = CustomStatusDotIcon(color)

    fun applySecondaryText(label: JBLabel) {
        label.foreground = JBColor.GRAY
    }

    fun applyMutedText(label: JBLabel) {
        label.foreground = JBColor(0x5F6B7A, 0xA7B0BA)
    }

    fun placeholderColor(): Color = JBColor.GRAY

    fun applyToolbarButtonStyle(button: JButton) {
        button.isFocusPainted = false
        button.isFocusable = false
        button.isContentAreaFilled = false
        button.border = JBUI.Borders.empty(2, 4)
    }

    fun roundedLineBorder(color: Color, thickness: Int = 1, arc: Int = 16): Border =
        RoundedBorder(color, thickness, arc)

    fun roundedPanel(backgroundColor: Color, arc: Int = 16): JPanel {
        return object : JPanel() {
            init {
                isOpaque = false
            }

            override fun paintComponent(g: Graphics) {
                val g2 = g as? Graphics2D ?: return
                val oldHint = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
                val oldColor = g2.color
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = backgroundColor
                g2.fillRoundRect(0, 0, width, height, JBUI.scale(arc), JBUI.scale(arc))
                g2.color = oldColor
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldHint)
                super.paintComponent(g)
            }
        }
    }

    fun roundedBadge(text: String, foreground: Color, background: Color, arc: Int = 12): JBLabel {
        return object : JBLabel(text) {
            init {
                isOpaque = false
                this.foreground = foreground
                border = JBUI.Borders.empty(4, 8)
            }

            override fun paintComponent(g: Graphics) {
                val g2 = g as? Graphics2D ?: return
                val oldHint = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = background
                g2.fillRoundRect(0, 0, width, height, JBUI.scale(arc), JBUI.scale(arc))
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldHint)
                super.paintComponent(g)
            }
        }
    }

    private fun statusForeground(tone: StatusTone): Color = when (tone) {
        StatusTone.GREEN -> JBColor(0x137333, 0x6AAB73)
        StatusTone.YELLOW -> JBColor(0x9A6700, 0xCFA65A)
        StatusTone.RED -> JBColor(0xC5221F, 0xE06C75)
        StatusTone.BLUE -> JBColor(0x1A73E8, 0x78A9FF)
        StatusTone.GRAY -> JBColor.GRAY
    }

    private class StatusDotIcon(tone: StatusTone) : Icon {
        private val color: Color = when (tone) {
            StatusTone.GREEN -> JBColor(0x34A853, 0x6AAB73)
            StatusTone.YELLOW -> JBColor(0xF9AB00, 0xD9A441)
            StatusTone.RED -> JBColor(0xEA4335, 0xE06C75)
            StatusTone.BLUE -> JBColor(0x1A73E8, 0x78A9FF)
            StatusTone.GRAY -> JBColor(0x9AA0A6, 0x7A7F85)
        }

        override fun getIconWidth(): Int = JBUI.scale(10)

        override fun getIconHeight(): Int = JBUI.scale(10)

        override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
            paintDot(g, x, y, color)
        }
    }

    private class CustomStatusDotIcon(private val color: Color) : Icon {

        override fun getIconWidth(): Int = JBUI.scale(10)

        override fun getIconHeight(): Int = JBUI.scale(10)

        override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
            paintDot(g, x, y, color)
        }
    }

    private fun paintDot(g: Graphics?, x: Int, y: Int, color: Color) {
        val g2 = g as? Graphics2D ?: return
        val oldColor = g2.color
        val oldHint = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = color
        g2.fillOval(x, y, JBUI.scale(10), JBUI.scale(10))
        g2.color = oldColor
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldHint)
    }

    private class RoundedBorder(
        private val color: Color,
        private val thickness: Int,
        private val arc: Int
    ) : Border {
        override fun paintBorder(c: Component?, g: Graphics?, x: Int, y: Int, width: Int, height: Int) {
            val g2 = g as? Graphics2D ?: return
            val oldColor = g2.color
            val oldHint = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = color
            repeat(JBUI.scale(thickness)) { offset ->
                g2.drawRoundRect(
                    x + offset,
                    y + offset,
                    width - 1 - offset * 2,
                    height - 1 - offset * 2,
                    JBUI.scale(arc),
                    JBUI.scale(arc)
                )
            }
            g2.color = oldColor
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldHint)
        }

        override fun getBorderInsets(c: Component?): Insets {
            val scaled = JBUI.scale(thickness)
            return JBUI.insets(scaled)
        }

        override fun isBorderOpaque(): Boolean = false
    }
}

/**
 * 简易 Document 监听扩展：任意变化触发回调。
 */
fun Document.onAnyChange(cb: () -> Unit) {
    addDocumentListener(object : DocumentListener {
        override fun insertUpdate(e: DocumentEvent?) = cb()
        override fun removeUpdate(e: DocumentEvent?) = cb()
        override fun changedUpdate(e: DocumentEvent?) = cb()
    })
}
