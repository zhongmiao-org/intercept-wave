package org.zhongmiao.interceptwave.ui

import com.intellij.util.ui.JBUI
import javax.swing.JTable
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.Document

/**
 * UI 通用小工具与常量，统一表格列宽、可见行数与输入监听。
 */
object UiKit {
    const val ENABLED_COL_WIDTH = 40
    const val MODE_COL_WIDTH = 80
    const val PERIOD_COL_WIDTH = 80
    const val DEFAULT_VISIBLE_ROWS = 5

    fun setEnabledColumnWidth(table: JTable, index: Int = 0) {
        runCatching {
            table.columnModel.getColumn(index).apply {
                val w = JBUI.scale(ENABLED_COL_WIDTH)
                minWidth = w; preferredWidth = w; maxWidth = w
            }
        }
    }

    fun setFixedColumnWidth(table: JTable, index: Int, widthPx: Int) {
        runCatching {
            table.columnModel.getColumn(index).apply {
                val w = JBUI.scale(widthPx)
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
