package org.zhongmiao.interceptwave.ui

import org.zhongmiao.interceptwave.InterceptWaveBundle.message
import org.zhongmiao.interceptwave.model.MockApiConfig
import kotlinx.serialization.json.JsonElement
import org.zhongmiao.interceptwave.util.JsonNormalizeUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*

/**
 * Mock接口编辑对话框
 */
class MockApiDialog(
    project: Project,
    existingApi: MockApiConfig?
) : DialogWrapper(project) {

    private val pathField = JBTextField(existingApi?.path ?: "")
    private val methodComboBox = ComboBox(arrayOf("ALL", "GET", "POST", "PUT", "DELETE", "PATCH"))
    private val statusCodeField = JBTextField(existingApi?.statusCode?.toString() ?: "200")
    private val delayField = JBTextField(existingApi?.delay?.toString() ?: "0")
    private val useCookieCheckBox = JCheckBox(message("mockapi.usecookie"), existingApi?.useCookie ?: false)
    private val mockDataArea = JTextArea(existingApi?.mockData ?: "{}")
    private val enabledCheckBox = JCheckBox(message("mockapi.enabled"), existingApi?.enabled ?: true)

    init {
        init()
        title = if (existingApi == null) message("mockapi.dialog.title.add") else message("mockapi.dialog.title.edit")

        existingApi?.let {
            methodComboBox.selectedItem = it.method
        }
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        panel.preferredSize = Dimension(600, 500)

        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = JBUI.insets(5)
        }

        // 启用状态
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.gridwidth = 2
        gbc.weightx = 0.0
        panel.add(enabledCheckBox, gbc)

        gbc.gridwidth = 1

        // 接口路径
        var row = 1
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        panel.add(JBLabel(message("mockapi.path")), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        pathField.toolTipText = message("mockapi.path.tooltip")
        panel.add(pathField, gbc)
        row++

        // HTTP方法
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        panel.add(JBLabel(message("mockapi.method")), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        panel.add(methodComboBox, gbc)
        row++

        // 状态码
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        panel.add(JBLabel(message("mockapi.statuscode")), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        statusCodeField.toolTipText = message("mockapi.statuscode.tooltip")
        panel.add(statusCodeField, gbc)
        row++

        // 延迟
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        panel.add(JBLabel(message("mockapi.delay")), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        delayField.toolTipText = message("mockapi.delay.tooltip")
        panel.add(delayField, gbc)
        row++

        // 使用全局Cookie
        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 2
        gbc.weightx = 0.0
        gbc.weighty = 0.0
        gbc.fill = GridBagConstraints.NONE
        gbc.anchor = GridBagConstraints.WEST
        useCookieCheckBox.toolTipText = message("mockapi.usecookie.tooltip")
        panel.add(useCookieCheckBox, gbc)
        row++

        gbc.gridwidth = 1
        gbc.fill = GridBagConstraints.BOTH

        // Mock数据
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        gbc.anchor = GridBagConstraints.NORTHWEST
        UiFormUtil.addLabeledScrollTextArea(panel, gbc, "mockapi.mockdata", mockDataArea)
        row++

        // 添加格式化按钮（用于辅助校验与可读性查看，不影响保存策略）
        gbc.gridx = 1
        gbc.gridy = row
        gbc.weightx = 0.0
        gbc.weighty = 0.0
        gbc.fill = GridBagConstraints.NONE
        gbc.anchor = GridBagConstraints.EAST
        val formatButton = JButton(message("mockapi.button.format"), AllIcons.Actions.ReformatCode)
        formatButton.isFocusPainted = false
        formatButton.addActionListener {
            try {
                val formatted = prettyJson(mockDataArea.text)
                mockDataArea.text = formatted
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(
                    panel,
                    message("mockapi.message.json.error", e.message ?: ""),
                    message("config.message.error"),
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }
        panel.add(formatButton, gbc)

        return panel
    }

    // 使用严格 JSON 解析进行格式化与压缩
    // 宽容解析：先严格解析，失败再尝试归一化后解析
    private fun parseJson(text: String): JsonElement = JsonNormalizeUtil.parseStrictOrNormalize(text)

    // 用于“格式化”按钮（辅助查看）：美化输出
    private fun prettyJson(text: String): String {
        return JsonNormalizeUtil.prettyJson(text)
    }

    // 保存时压缩为无空格无换行的 JSON 串
    private fun minifyJson(text: String): String {
        return JsonNormalizeUtil.minifyJson(text)
    }

    /**
     * 获取配置的Mock API
     */
    fun getMockApiConfig(): MockApiConfig {
        val raw = mockDataArea.text.trim()
        // 保存前进行严格校验并压缩
        val minified = try {
            minifyJson(raw)
        } catch (e: Exception) {
            // 理论上 doValidate 已校验，这里兜底再报错
            throw IllegalArgumentException("Invalid JSON: ${e.message}")
        }

        return MockApiConfig(
            path = pathField.text.trim(),
            enabled = enabledCheckBox.isSelected,
            mockData = minified,
            method = methodComboBox.selectedItem as String,
            statusCode = statusCodeField.text.toIntOrNull() ?: 200,
            useCookie = useCookieCheckBox.isSelected,
            delay = delayField.text.toLongOrNull() ?: 0
        )
    }

    override fun doValidate(): ValidationInfo? {
        if (pathField.text.isBlank()) {
            return ValidationInfo(message("mockapi.validation.path.empty"), pathField)
        }
        if (!pathField.text.startsWith("/")) {
            return ValidationInfo(message("mockapi.validation.path.slash"), pathField)
        }
        if (statusCodeField.text.toIntOrNull() == null) {
            return ValidationInfo(message("mockapi.validation.statuscode.invalid"), statusCodeField)
        }
        if (delayField.text.toLongOrNull() == null) {
            return ValidationInfo(message("mockapi.validation.delay.invalid"), delayField)
        }
        val text = mockDataArea.text.trim()
        if (text.isBlank()) {
            return ValidationInfo(message("mockapi.validation.mockdata.empty"), mockDataArea)
        }
        // 校验 JSON 合法性，要求双引号等严格 JSON 语法
        try {
            parseJson(text)
        } catch (e: Exception) {
            return ValidationInfo(message("mockapi.message.json.error", e.message ?: ""), mockDataArea)
        }
        return null
    }
}
