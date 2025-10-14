package org.zhongmiao.interceptwave.ui

import org.zhongmiao.interceptwave.InterceptWaveBundle.message
import org.zhongmiao.interceptwave.services.ConfigService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*
import javax.swing.table.DefaultTableModel

/**
 * 配置对话框
 * 用于配置Mock服务的全局设置和Mock接口
 */
class ConfigDialog(private val project: Project) : DialogWrapper(project) {

    private val configService = project.service<ConfigService>()
    private val config = configService.getConfig()

    private val portField = JBTextField(config.port.toString())
    private val interceptPrefixField = JBTextField(config.interceptPrefix)
    private val baseUrlField = JBTextField(config.baseUrl)
    private val stripPrefixCheckbox = JCheckBox(message("config.global.stripprefix"), config.stripPrefix)

    private val tableModel = object : DefaultTableModel(
        arrayOf(
            message("config.table.enabled"),
            message("config.table.path"),
            message("config.table.method"),
            message("config.table.statuscode"),
            message("config.table.delay")
        ),
        0
    ) {
        override fun getColumnClass(column: Int): Class<*> {
            return if (column == 0) java.lang.Boolean::class.java else String::class.java
        }

        override fun isCellEditable(row: Int, column: Int): Boolean = true
    }

    private val mockTable = JBTable(tableModel)

    init {
        init()
        title = message("config.dialog.title")
        loadMockApisToTable()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(10, 10))
        panel.preferredSize = Dimension(800, 600)

        // 全局配置面板
        val globalPanel = createGlobalConfigPanel()
        panel.add(globalPanel, BorderLayout.NORTH)

        // Mock接口列表面板
        val mockListPanel = createMockListPanel()
        panel.add(mockListPanel, BorderLayout.CENTER)

        return panel
    }

    /**
     * 创建全局配置面板
     */
    private fun createGlobalConfigPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.border = BorderFactory.createTitledBorder(message("config.global.title"))

        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(5, 5, 5, 5)
        }

        // 端口配置
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.weightx = 0.0
        panel.add(JBLabel(message("config.global.port")), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        portField.toolTipText = message("config.global.port.tooltip")
        panel.add(portField, gbc)

        // 拦截前缀配置
        gbc.gridx = 0
        gbc.gridy = 1
        gbc.weightx = 0.0
        panel.add(JBLabel(message("config.global.prefix")), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        interceptPrefixField.toolTipText = message("config.global.prefix.tooltip")
        panel.add(interceptPrefixField, gbc)

        // 原始接口地址配置
        gbc.gridx = 0
        gbc.gridy = 2
        gbc.weightx = 0.0
        panel.add(JBLabel(message("config.global.baseurl")), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        baseUrlField.toolTipText = message("config.global.baseurl.tooltip")
        panel.add(baseUrlField, gbc)

        // 过滤/取消前缀配置
        gbc.gridx = 0
        gbc.gridy = 3
        gbc.gridwidth = 2
        stripPrefixCheckbox.toolTipText = message("config.global.stripprefix.tooltip", config.port, config.interceptPrefix)
        panel.add(stripPrefixCheckbox, gbc)

        return panel
    }

    /**
     * 创建Mock接口列表面板
     */
    private fun createMockListPanel(): JPanel {
        val panel = JPanel(BorderLayout(10, 10))
        panel.border = BorderFactory.createTitledBorder(message("config.mock.title"))

        // 表格
        mockTable.fillsViewportHeight = true
        val scrollPane = JBScrollPane(mockTable)
        panel.add(scrollPane, BorderLayout.CENTER)

        // 按钮面板
        val buttonPanel = JPanel()
        buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.X_AXIS)

        val addButton = JButton(message("config.button.add"))
        addButton.addActionListener { addNewMockApi() }

        val editButton = JButton(message("config.button.edit"))
        editButton.addActionListener { editSelectedMockApi() }

        val deleteButton = JButton(message("config.button.delete"))
        deleteButton.addActionListener { deleteSelectedMockApi() }

        buttonPanel.add(addButton)
        buttonPanel.add(Box.createHorizontalStrut(5))
        buttonPanel.add(editButton)
        buttonPanel.add(Box.createHorizontalStrut(5))
        buttonPanel.add(deleteButton)

        panel.add(buttonPanel, BorderLayout.SOUTH)

        return panel
    }

    /**
     * 加载Mock接口配置到表格
     */
    private fun loadMockApisToTable() {
        tableModel.rowCount = 0
        config.mockApis.forEach { api ->
            tableModel.addRow(
                arrayOf(
                    api.enabled,
                    api.path,
                    api.method,
                    api.statusCode,
                    api.delay
                )
            )
        }
    }

    /**
     * 添加新的Mock接口
     */
    private fun addNewMockApi() {
        val dialog = MockApiDialog(project, null)
        if (dialog.showAndGet()) {
            val newApi = dialog.getMockApiConfig()
            config.mockApis.add(newApi)
            loadMockApisToTable()
        }
    }

    /**
     * 编辑选中的Mock接口
     */
    private fun editSelectedMockApi() {
        val selectedRow = mockTable.selectedRow
        if (selectedRow >= 0) {
            val api = config.mockApis[selectedRow]
            val dialog = MockApiDialog(project, api)
            if (dialog.showAndGet()) {
                val updatedApi = dialog.getMockApiConfig()
                config.mockApis[selectedRow] = updatedApi
                loadMockApisToTable()
            }
        } else {
            JOptionPane.showMessageDialog(
                mockTable,
                message("config.message.select"),
                message("config.message.info"),
                JOptionPane.INFORMATION_MESSAGE
            )
        }
    }

    /**
     * 删除选中的Mock接口
     */
    private fun deleteSelectedMockApi() {
        val selectedRow = mockTable.selectedRow
        if (selectedRow >= 0) {
            val result = JOptionPane.showConfirmDialog(
                mockTable,
                message("config.message.confirm.delete"),
                message("config.message.confirm.title"),
                JOptionPane.YES_NO_OPTION
            )
            if (result == JOptionPane.YES_OPTION) {
                config.mockApis.removeAt(selectedRow)
                loadMockApisToTable()
            }
        } else {
            JOptionPane.showMessageDialog(
                mockTable,
                message("config.message.select"),
                message("config.message.info"),
                JOptionPane.INFORMATION_MESSAGE
            )
        }
    }

    override fun doOKAction() {
        try {
            // 更新全局配置
            config.port = portField.text.toIntOrNull() ?: 8888
            config.interceptPrefix = interceptPrefixField.text
            config.baseUrl = baseUrlField.text
            config.stripPrefix = stripPrefixCheckbox.isSelected

            // 从表格更新启用状态
            for (i in 0 until tableModel.rowCount) {
                config.mockApis[i].enabled = tableModel.getValueAt(i, 0) as Boolean
            }

            // 保存配置
            configService.saveConfig(config)

            super.doOKAction()
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                contentPane,
                message("config.message.error.save", e.message ?: ""),
                message("config.message.error"),
                JOptionPane.ERROR_MESSAGE
            )
        }
    }
}