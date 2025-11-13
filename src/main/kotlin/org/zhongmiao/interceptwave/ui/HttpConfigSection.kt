package org.zhongmiao.interceptwave.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import org.zhongmiao.interceptwave.InterceptWaveBundle.message
import org.zhongmiao.interceptwave.model.ProxyConfig
import java.awt.BorderLayout

/** HTTP 内容区（API 列表等） */
class HttpConfigSection(
    private val project: Project,
    private val config: ProxyConfig,
    private val onChanged: () -> Unit = {}
) {
    // 顶部 HTTP 基本设置（与 WS 保持一致的布局：上游、前缀、全局 Cookie）
    private val baseUrlField = JBTextField(config.baseUrl)
    private val prefixField = JBTextField(config.interceptPrefix)
    private val cookieField = JBTextField(config.globalCookie)

    private val tableModel = object : javax.swing.table.DefaultTableModel(
        arrayOf(
            message("config.table.enabled"),
            message("config.table.path"),
            message("config.table.method"),
            message("config.table.statuscode"),
            message("config.table.delay")
        ),
        0
    ) {
        override fun getColumnClass(column: Int): Class<*> = if (column == 0) java.lang.Boolean::class.java else String::class.java
        override fun isCellEditable(row: Int, column: Int): Boolean = true
    }
    private val mockTable = JBTable(tableModel)

    fun panel(): JBPanel<JBPanel<*>> {
        loadMockApis()
        setupEditors()

        val rootPanel = JBPanel<JBPanel<*>>(BorderLayout(10, 10))

        // 提示信息：与 WS 配置保持一致地提供 tooltip，说明填写规则
        baseUrlField.toolTipText = message("config.group.baseurl.tooltip")
        prefixField.toolTipText = message("config.group.prefix.tooltip")
        cookieField.toolTipText = message("config.group.cookie.tooltip")

        mockTable.fillsViewportHeight = true
        // 统一可见行数与启用列宽
        UiKit.ensureVisibleRows(mockTable, UiKit.DEFAULT_VISIBLE_ROWS)
        UiKit.setEnabledColumnWidth(mockTable, 0)
        val tableScroll = JBScrollPane(mockTable).apply {
            // 列表无标题与边框
            border = null
            viewportBorder = null
        }

        // 使用 DSL group 作为外层边框与内边距
        val content = panel {
            group(message("config.http.title")) {
                row(message("config.group.baseurl") + ":") { cell(baseUrlField).align(AlignX.FILL) }
                row(message("config.group.prefix") + ":") { cell(prefixField).align(AlignX.FILL) }
                row(message("config.group.cookie") + ":") { cell(cookieField).align(AlignX.FILL) }
                row { cell(tableScroll).align(AlignX.FILL) }
                row {
                    button(message("mockapi.add.button")) { addApi() }
                        .applyToComponent {
                            icon = com.intellij.icons.AllIcons.General.Add
                            isFocusPainted = false
                        }
                    button(message("mockapi.edit.button")) { editApi() }
                        .applyToComponent {
                            icon = com.intellij.icons.AllIcons.Actions.Edit
                            isFocusPainted = false
                        }
                    button(message("mockapi.delete.button")) { deleteApi() }
                        .applyToComponent {
                            icon = com.intellij.icons.AllIcons.General.Remove
                            isFocusPainted = false
                        }
                }
            }
        }
        rootPanel.add(content, BorderLayout.CENTER)

        // 顶部字段联动变更（使用通用扩展）
        baseUrlField.document.onAnyChange(onChanged)
        prefixField.document.onAnyChange(onChanged)
        cookieField.document.onAnyChange(onChanged)

        // 监听表格内容变化，标记变更
        tableModel.addTableModelListener { onChanged() }

        return rootPanel
    }

    private fun setupEditors() {
        val methodColumn = mockTable.columnModel.getColumn(2)
        val methodCombo = ComboBox(arrayOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"))
        methodColumn.cellEditor = javax.swing.DefaultCellEditor(methodCombo)
    }

    private fun loadMockApis() {
        tableModel.rowCount = 0
        config.mockApis.forEach { api ->
            tableModel.addRow(arrayOf<Any>(api.enabled, api.path, api.method, api.statusCode.toString(), api.delay.toString()))
        }
    }

    private fun addApi() {
        val dialog = MockApiDialog(project, null)
        if (dialog.showAndGet()) {
            val newApi = dialog.getMockApiConfig()
            config.mockApis.add(newApi)
            loadMockApis()
            onChanged()
        }
    }

    private fun editApi() {
        val row = mockTable.selectedRow
        if (row >= 0) {
            val api = config.mockApis[row]
            val dialog = MockApiDialog(project, api)
            if (dialog.showAndGet()) {
                config.mockApis[row] = dialog.getMockApiConfig()
                loadMockApis()
                onChanged()
            }
        } else {
            javax.swing.JOptionPane.showMessageDialog(
                mockTable,
                message("mockapi.select.first.edit"),
                message("config.message.info"),
                javax.swing.JOptionPane.INFORMATION_MESSAGE
            )
        }
    }

    private fun deleteApi() {
        val row = mockTable.selectedRow
        if (row >= 0) {
            val result = javax.swing.JOptionPane.showConfirmDialog(
                mockTable,
                message("mockapi.delete.confirm"),
                message("config.message.confirm.title"),
                javax.swing.JOptionPane.YES_NO_OPTION
            )
            if (result == javax.swing.JOptionPane.YES_OPTION) {
                config.mockApis.removeAt(row)
                loadMockApis()
                onChanged()
            }
        } else {
            javax.swing.JOptionPane.showMessageDialog(
                mockTable,
                message("mockapi.select.first.delete"),
                message("config.message.info"),
                javax.swing.JOptionPane.INFORMATION_MESSAGE
            )
        }
    }

    fun applyTo(target: ProxyConfig) {
        // 顶部 HTTP 基本设置
        target.baseUrl = baseUrlField.text
        target.interceptPrefix = prefixField.text
        target.globalCookie = cookieField.text.trim()
        // 覆盖目标 mockApis 中已存在的行（保留长度一致的策略）
        for (i in 0 until tableModel.rowCount) {
            if (i < target.mockApis.size) {
                val api = target.mockApis[i]
                api.enabled = tableModel.getValueAt(i, 0) as Boolean
                api.path = tableModel.getValueAt(i, 1) as String
                api.method = tableModel.getValueAt(i, 2) as String
                api.statusCode = (tableModel.getValueAt(i, 3) as String).toIntOrNull() ?: 200
                api.delay = (tableModel.getValueAt(i, 4) as String).toLongOrNull() ?: 0L
            }
        }
    }
}
