package org.zhongmiao.interceptwave.ui

import org.zhongmiao.interceptwave.InterceptWaveBundle.message
import org.zhongmiao.interceptwave.services.ConfigService
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.*
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*

/**
 * 配置对话框
 * 用于配置多个代理配置组，每个配置组有独立的设置和Mock接口
 */
class ConfigDialog(
    private val project: Project,
    val initialSelectedIndex: Int? = null,
    val autoAddOnOpen: Boolean = false,
) : DialogWrapper(project) {

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

        // 如果没有配置组，创建一个默认的（国际化）
        if (proxyGroups.isEmpty()) {
            proxyGroups.add(configService.createDefaultProxyConfig(0, message("config.group.default")))
        }

        setupTabbedPane()

        // 可选：打开时自动新增一个配置组并选中
        if (autoAddOnOpen) {
            addNewProxyGroup()
        } else if (initialSelectedIndex != null && initialSelectedIndex in proxyGroups.indices) {
            tabbedPane.selectedIndex = initialSelectedIndex
        }
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

        // 如果有标签，默认选中第一个（后续外层可能覆盖）
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
            message("config.group.default.indexed", proxyGroups.size + 1)
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
// ProxyConfigPanel 已迁移到独立文件：ProxyConfigPanel.kt
