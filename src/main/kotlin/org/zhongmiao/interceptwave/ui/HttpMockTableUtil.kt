package org.zhongmiao.interceptwave.ui

import javax.swing.table.DefaultTableModel
import org.zhongmiao.interceptwave.InterceptWaveBundle.message
import org.zhongmiao.interceptwave.model.MockApiConfig

/**
 * HTTP Mock 简表（工具窗口信息区用）：列头与行数据装配，统一风格。
 */
fun createHttpShortTableModel(enabledEditable: Boolean = false): DefaultTableModel = object : DefaultTableModel(
    arrayOf(
        message("config.table.enabled"),
        message("config.table.path"),
    ), 0
) {
    override fun getColumnClass(column: Int): Class<*> = if (column == 0) java.lang.Boolean::class.java else String::class.java
    override fun isCellEditable(row: Int, column: Int): Boolean = enabledEditable && column == 0
}

fun appendHttpShortRows(model: DefaultTableModel, apis: List<MockApiConfig>) {
    apis.forEach { api ->
        model.addRow(arrayOf<Any>(api.enabled, api.path))
    }
}

