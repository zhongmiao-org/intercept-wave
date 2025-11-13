package org.zhongmiao.interceptwave.ui

import javax.swing.table.DefaultTableModel
import org.zhongmiao.interceptwave.model.WsPushRule
import org.zhongmiao.interceptwave.InterceptWaveBundle.message

/**
 * WS 规则表格的公共工具：列头与行数据装配，避免重复代码。
 */
fun createWsRuleTableModel(enabledEditable: Boolean = false): DefaultTableModel = object : DefaultTableModel(
    arrayOf(
        message("config.ws.table.enabled"),
        message("config.ws.table.matcher"),
        message("config.ws.table.mode"),
        message("config.ws.table.period")
    ),
    0
) {
    override fun getColumnClass(column: Int): Class<*> = if (column == 0) java.lang.Boolean::class.java else String::class.java
    override fun isCellEditable(row: Int, column: Int): Boolean = enabledEditable && column == 0
}

fun appendWsRuleRows(model: DefaultTableModel, rules: List<WsPushRule>) {
    rules.forEach { r ->
        val period = if (r.mode.equals("periodic", true)) r.periodSec.toString() else "-"
        val matcher = org.zhongmiao.interceptwave.util.formatWsRuleMatcher(r)
        model.addRow(arrayOf<Any>(r.enabled, matcher, r.mode.uppercase(), period))
    }
}
