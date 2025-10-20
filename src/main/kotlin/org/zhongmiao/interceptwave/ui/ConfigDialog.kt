package org.zhongmiao.interceptwave.ui

import org.zhongmiao.interceptwave.InterceptWaveBundle.message
import org.zhongmiao.interceptwave.model.ProxyConfig
import org.zhongmiao.interceptwave.services.ConfigService
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.*
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*
import javax.swing.table.DefaultTableModel

/**
 * 配置对话框 - v2.0 多配置组支持
 * 用于配置多个代理配置组，每个配置组有独立的设置和Mock接口
 */
class ConfigDialog(private val project: Project) : DialogWrapper(project) {

    private val configService = project.service<ConfigService>()
    private val rootConfig = configService.getRootConfig()

    // 当前编辑的配置组列表（工作副本）
    private val proxyGroups = rootConfig.proxyGroups.toMutableList()

    // 标签页面板
    private val tabbedPane = JBTabbedPane()

    // 存储每个标签页的UI组件
    private val tabPanels = mutableMapOf<Int, ProxyConfigPanel>()

    init {
        init()
        title = message("config.dialog.title")

        // 如果没有配置组，创建一个默认的
        if (proxyGroups.isEmpty()) {
            proxyGroups.add(configService.createDefaultProxyConfig(0, "默认配置"))
        }

        setupTabbedPane()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JBPanel<JBPanel<*>>(BorderLayout(10, 10))
        panel.preferredSize = Dimension(900, 650)

        // 添加标签页
        panel.add(tabbedPane, BorderLayout.CENTER)

        // 底部按钮面板
        val bottomPanel = createBottomButtonPanel()
        panel.add(bottomPanel, BorderLayout.SOUTH)

        return panel
    }

    /**
     * 设置标签页
     */
    private fun setupTabbedPane() {
        tabbedPane.removeAll()
        tabPanels.clear()

        proxyGroups.forEachIndexed { index, config ->
            val configPanel = ProxyConfigPanel(project, config)
            tabPanels[index] = configPanel

            // 创建标签，显示配置组名称和端口
            val tabTitle = "${config.name} (:${config.port})"
            tabbedPane.addTab(tabTitle, configPanel.getPanel())
        }

        // 如果有标签，默认选中第一个
        if (tabbedPane.tabCount > 0) {
            tabbedPane.selectedIndex = 0
        }
    }

    /**
     * 创建底部按钮面板
     */
    private fun createBottomButtonPanel(): JBPanel<JBPanel<*>> {
        val panel = JBPanel<JBPanel<*>>()
        panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)

        val addButton = JButton(message("config.group.add"), AllIcons.General.Add)
        addButton.isFocusPainted = false
        addButton.addActionListener { addNewProxyGroup() }

        val deleteButton = JButton(message("config.group.delete"), AllIcons.General.Remove)
        deleteButton.isFocusPainted = false
        deleteButton.addActionListener { deleteCurrentProxyGroup() }

        val moveLeftButton = JButton(message("config.group.move.left"), AllIcons.Actions.Back)
        moveLeftButton.isFocusPainted = false
        moveLeftButton.addActionListener { moveCurrentTab(-1) }

        // 特殊处理：右移按钮图标在右边
        val moveRightButton = JButton(message("config.group.move.right"))
        moveRightButton.horizontalTextPosition = SwingConstants.LEFT
        moveRightButton.icon = AllIcons.Actions.Forward
        moveRightButton.isFocusPainted = false
        moveRightButton.addActionListener { moveCurrentTab(1) }

        panel.add(addButton)
        panel.add(Box.createHorizontalStrut(10))
        panel.add(deleteButton)
        panel.add(Box.createHorizontalGlue())
        panel.add(moveLeftButton)
        panel.add(Box.createHorizontalStrut(5))
        panel.add(moveRightButton)

        return panel
    }

    /**
     * 添加新配置组
     */
    private fun addNewProxyGroup() {
        val newConfig = configService.createDefaultProxyConfig(
            proxyGroups.size,
            "配置组 ${proxyGroups.size + 1}"
        )

        proxyGroups.add(newConfig)
        setupTabbedPane()

        // 选中新添加的标签
        tabbedPane.selectedIndex = tabbedPane.tabCount - 1
    }

    /**
     * 删除当前配置组
     */
    private fun deleteCurrentProxyGroup() {
        val currentIndex = tabbedPane.selectedIndex

        if (currentIndex < 0) {
            JOptionPane.showMessageDialog(
                contentPane,
                message("config.group.select.first"),
                message("config.message.info"),
                JOptionPane.INFORMATION_MESSAGE
            )
            return
        }

        if (proxyGroups.size <= 1) {
            JOptionPane.showMessageDialog(
                contentPane,
                message("config.group.delete.atleastone"),
                message("config.message.info"),
                JOptionPane.WARNING_MESSAGE
            )
            return
        }

        val config = proxyGroups[currentIndex]
        val result = JOptionPane.showConfirmDialog(
            contentPane,
            message("config.group.delete.confirm", config.name),
            message("config.group.delete.confirm.title"),
            JOptionPane.YES_NO_OPTION
        )

        if (result == JOptionPane.YES_OPTION) {
            proxyGroups.removeAt(currentIndex)
            setupTabbedPane()

            // 选中前一个标签
            if (currentIndex > 0) {
                tabbedPane.selectedIndex = currentIndex - 1
            }
        }
    }

    /**
     * 移动当前标签
     * @param direction -1 左移, 1 右移
     */
    private fun moveCurrentTab(direction: Int) {
        val currentIndex = tabbedPane.selectedIndex

        if (currentIndex < 0) return

        val newIndex = currentIndex + direction

        if (newIndex < 0 || newIndex >= proxyGroups.size) {
            return
        }

        // 交换配置组位置
        val temp = proxyGroups[currentIndex]
        proxyGroups[currentIndex] = proxyGroups[newIndex]
        proxyGroups[newIndex] = temp

        setupTabbedPane()
        tabbedPane.selectedIndex = newIndex
    }

    override fun doOKAction() {
        try {
            // 验证端口号
            var hasError = false
            tabPanels.forEach { (_, panel) ->
                if (!panel.validateInput()) {
                    hasError = true
                }
            }

            if (hasError) {
                JOptionPane.showMessageDialog(
                    contentPane,
                    message("config.validation.input.error"),
                    message("config.validation.input.error.title"),
                    JOptionPane.WARNING_MESSAGE
                )
                return
            }

            // 从各个面板收集配置
            tabPanels.forEach { (index, panel) ->
                panel.applyChanges(proxyGroups[index])
            }

            // 保存到 RootConfig
            rootConfig.proxyGroups.clear()
            rootConfig.proxyGroups.addAll(proxyGroups)
            configService.saveRootConfig(rootConfig)

            // 调用父类方法关闭对话框
            super.doOKAction()
        } catch (e: Exception) {
            e.printStackTrace()
            JOptionPane.showMessageDialog(
                contentPane,
                message("config.save.error", e.message ?: "Unknown error"),
                message("config.save.error.title"),
                JOptionPane.ERROR_MESSAGE
            )
            // 不要调用 super.doOKAction()，让对话框保持打开状态
        }
    }
}

/**
 * 单个代理配置组的面板
 */
class ProxyConfigPanel(
    private val project: Project,
    private val initialConfig: ProxyConfig
) {
    private val nameField = JBTextField(initialConfig.name)
    private val portField = JBTextField(initialConfig.port.toString())
    private val interceptPrefixField = JBTextField(initialConfig.interceptPrefix)
    private val baseUrlField = JBTextField(initialConfig.baseUrl)
    private val stripPrefixCheckbox = JBCheckBox(message("config.group.stripprefix"), initialConfig.stripPrefix)
    private val globalCookieField = JBTextField(initialConfig.globalCookie)
    private val enabledCheckbox = JBCheckBox(message("config.group.enabled"), initialConfig.enabled)

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
        loadMockApisToTable()
        setupTableEditors()
    }

    /**
     * 设置表格编辑器
     */
    private fun setupTableEditors() {
        // 为方法列（第2列）设置下拉选择编辑器
        val methodColumn = mockTable.columnModel.getColumn(2)
        val methodComboBox = ComboBox(arrayOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"))
        methodColumn.cellEditor = DefaultCellEditor(methodComboBox)
    }

    fun getPanel(): JBPanel<JBPanel<*>> {
        val mainPanel = JBPanel<JBPanel<*>>(BorderLayout(10, 10))

        // 上部：全局配置
        val globalPanel = createGlobalConfigPanel()
        mainPanel.add(globalPanel, BorderLayout.NORTH)

        // 中部：Mock API 列表
        val mockListPanel = createMockListPanel()
        mainPanel.add(mockListPanel, BorderLayout.CENTER)

        return mainPanel
    }

    private fun createGlobalConfigPanel(): JBPanel<JBPanel<*>> {
        val panel = JBPanel<JBPanel<*>>(GridBagLayout())
        panel.border = BorderFactory.createTitledBorder(message("config.group.settings"))

        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = JBUI.insets(5)
        }

        // 配置组名称
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0
        panel.add(JBLabel(message("config.group.name") + ":"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        nameField.toolTipText = message("config.group.name.tooltip")
        panel.add(nameField, gbc)

        // 端口
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0
        panel.add(JBLabel(message("config.group.port") + ":"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        portField.toolTipText = message("config.group.port.tooltip")
        panel.add(portField, gbc)

        // 拦截前缀
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0
        panel.add(JBLabel(message("config.group.prefix") + ":"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        interceptPrefixField.toolTipText = message("config.group.prefix.tooltip")
        panel.add(interceptPrefixField, gbc)

        // 目标地址
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0.0
        panel.add(JBLabel(message("config.group.baseurl") + ":"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        baseUrlField.toolTipText = message("config.group.baseurl.tooltip")
        panel.add(baseUrlField, gbc)

        // 剥离前缀
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2
        stripPrefixCheckbox.toolTipText = message("config.group.stripprefix.tooltip")
        panel.add(stripPrefixCheckbox, gbc)

        // 全局Cookie
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 1; gbc.weightx = 0.0
        panel.add(JBLabel(message("config.group.cookie") + ":"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        globalCookieField.toolTipText = message("config.group.cookie.tooltip")
        panel.add(globalCookieField, gbc)

        // 启用配置组
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2
        enabledCheckbox.toolTipText = message("config.group.enabled.tooltip")
        panel.add(enabledCheckbox, gbc)

        return panel
    }

    private fun createMockListPanel(): JBPanel<JBPanel<*>> {
        val panel = JBPanel<JBPanel<*>>(BorderLayout(10, 10))
        panel.border = BorderFactory.createTitledBorder(message("config.group.mocklist"))

        mockTable.fillsViewportHeight = true
        val scrollPane = JBScrollPane(mockTable)
        panel.add(scrollPane, BorderLayout.CENTER)

        // 按钮面板
        val buttonPanel = JBPanel<JBPanel<*>>()
        buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.X_AXIS)

        val addButton = JButton(message("mockapi.add.button"), AllIcons.General.Add)
        addButton.isFocusPainted = false
        addButton.addActionListener { addNewMockApi() }

        val editButton = JButton(message("mockapi.edit.button"), AllIcons.Actions.Edit)
        editButton.isFocusPainted = false
        editButton.addActionListener { editSelectedMockApi() }

        val deleteButton = JButton(message("mockapi.delete.button"), AllIcons.General.Remove)
        deleteButton.isFocusPainted = false
        deleteButton.addActionListener { deleteSelectedMockApi() }

        buttonPanel.add(addButton)
        buttonPanel.add(Box.createHorizontalStrut(5))
        buttonPanel.add(editButton)
        buttonPanel.add(Box.createHorizontalStrut(5))
        buttonPanel.add(deleteButton)

        panel.add(buttonPanel, BorderLayout.SOUTH)

        return panel
    }

    private fun loadMockApisToTable() {
        tableModel.rowCount = 0
        initialConfig.mockApis.forEach { api ->
            tableModel.addRow(
                arrayOf<Any>(
                    api.enabled,
                    api.path,
                    api.method,
                    api.statusCode.toString(),
                    api.delay.toString()
                )
            )
        }
    }

    private fun addNewMockApi() {
        val dialog = MockApiDialog(project, null)
        if (dialog.showAndGet()) {
            val newApi = dialog.getMockApiConfig()
            initialConfig.mockApis.add(newApi)
            loadMockApisToTable()
        }
    }

    private fun editSelectedMockApi() {
        val selectedRow = mockTable.selectedRow
        if (selectedRow >= 0) {
            val api = initialConfig.mockApis[selectedRow]
            val dialog = MockApiDialog(project, api)
            if (dialog.showAndGet()) {
                val updatedApi = dialog.getMockApiConfig()
                initialConfig.mockApis[selectedRow] = updatedApi
                loadMockApisToTable()
            }
        } else {
            JOptionPane.showMessageDialog(
                mockTable,
                message("mockapi.select.first.edit"),
                message("config.message.info"),
                JOptionPane.INFORMATION_MESSAGE
            )
        }
    }

    private fun deleteSelectedMockApi() {
        val selectedRow = mockTable.selectedRow
        if (selectedRow >= 0) {
            val result = JOptionPane.showConfirmDialog(
                mockTable,
                message("mockapi.delete.confirm"),
                message("config.message.confirm.title"),
                JOptionPane.YES_NO_OPTION
            )
            if (result == JOptionPane.YES_OPTION) {
                initialConfig.mockApis.removeAt(selectedRow)
                loadMockApisToTable()
            }
        } else {
            JOptionPane.showMessageDialog(
                mockTable,
                message("mockapi.select.first.delete"),
                message("config.message.info"),
                JOptionPane.INFORMATION_MESSAGE
            )
        }
    }

    /**
     * 验证输入
     */
    fun validateInput(): Boolean {
        val port = portField.text.toIntOrNull()
        if (port == null || port < 1 || port > 65535) {
            JOptionPane.showMessageDialog(
                null,
                message("config.validation.port.invalid"),
                message("config.validation.input.error.title"),
                JOptionPane.WARNING_MESSAGE
            )
            return false
        }
        return true
    }

    /**
     * 将UI的修改应用到配置对象
     */
    fun applyChanges(config: ProxyConfig) {
        config.name = nameField.text.trim().ifEmpty { "配置组" }
        config.port = portField.text.toIntOrNull() ?: 8888
        config.interceptPrefix = interceptPrefixField.text
        config.baseUrl = baseUrlField.text
        config.stripPrefix = stripPrefixCheckbox.isSelected
        config.globalCookie = globalCookieField.text.trim()
        config.enabled = enabledCheckbox.isSelected

        // 从表格同步所有Mock API的修改
        for (i in 0 until tableModel.rowCount) {
            if (i < config.mockApis.size) {
                val api = config.mockApis[i]
                api.enabled = tableModel.getValueAt(i, 0) as Boolean
                api.path = tableModel.getValueAt(i, 1) as String
                api.method = tableModel.getValueAt(i, 2) as String
                api.statusCode = (tableModel.getValueAt(i, 3) as String).toIntOrNull() ?: 200
                api.delay = (tableModel.getValueAt(i, 4) as String).toLongOrNull() ?: 0L
            }
        }
    }
}