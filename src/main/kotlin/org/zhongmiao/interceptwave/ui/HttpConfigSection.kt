package org.zhongmiao.interceptwave.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import org.zhongmiao.interceptwave.InterceptWaveBundle.message
import org.zhongmiao.interceptwave.model.ProxyConfig
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.JButton

/** HTTP 内容区（API 列表等） */
class HttpConfigSection(
    private val project: Project,
    private val config: ProxyConfig,
    private val onChanged: () -> Unit = {}
) {
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

        val panel = JBPanel<JBPanel<*>>(BorderLayout(10, 10))
        panel.border = BorderFactory.createTitledBorder(message("config.group.mocklist"))

        mockTable.fillsViewportHeight = true
        panel.add(JBScrollPane(mockTable), BorderLayout.CENTER)

        val bar = JBPanel<JBPanel<*>>()
        bar.layout = javax.swing.BoxLayout(bar, javax.swing.BoxLayout.X_AXIS)

        val add = JButton(message("mockapi.add.button"), com.intellij.icons.AllIcons.General.Add)
        add.isFocusPainted = false
        add.addActionListener { addApi() }
        val edit = JButton(message("mockapi.edit.button"), com.intellij.icons.AllIcons.Actions.Edit)
        edit.isFocusPainted = false
        edit.addActionListener { editApi() }
        val del = JButton(message("mockapi.delete.button"), com.intellij.icons.AllIcons.General.Remove)
        del.isFocusPainted = false
        del.addActionListener { deleteApi() }

        bar.add(add); bar.add(Box.createHorizontalStrut(5))
        bar.add(edit); bar.add(Box.createHorizontalStrut(5))
        bar.add(del)
        panel.add(bar, BorderLayout.SOUTH)

        // 监听表格内容变化，标记变更
        tableModel.addTableModelListener { onChanged() }

        return panel
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
