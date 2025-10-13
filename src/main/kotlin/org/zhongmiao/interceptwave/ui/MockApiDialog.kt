package org.zhongmiao.interceptwave.ui

import org.zhongmiao.interceptwave.model.MockApiConfig
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*

/**
 * Mock接口编辑对话框
 */
class MockApiDialog(
    private val project: Project,
    private val existingApi: MockApiConfig?
) : DialogWrapper(project) {

    private val pathField = JBTextField(existingApi?.path ?: "")
    private val methodComboBox = JComboBox(arrayOf("ALL", "GET", "POST", "PUT", "DELETE", "PATCH"))
    private val statusCodeField = JBTextField(existingApi?.statusCode?.toString() ?: "200")
    private val delayField = JBTextField(existingApi?.delay?.toString() ?: "0")
    private val mockDataArea = JTextArea(existingApi?.mockData ?: "{}")
    private val enabledCheckBox = JCheckBox("启用", existingApi?.enabled ?: true)

    init {
        init()
        title = if (existingApi == null) "添加Mock接口" else "编辑Mock接口"

        existingApi?.let {
            methodComboBox.selectedItem = it.method
        }
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        panel.preferredSize = Dimension(600, 500)

        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(5, 5, 5, 5)
        }

        var row = 0

        // 启用状态
        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 2
        gbc.weightx = 0.0
        panel.add(enabledCheckBox, gbc)
        row++

        gbc.gridwidth = 1

        // 接口路径
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        panel.add(JBLabel("接口路径:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        pathField.toolTipText = "例如: /api/user/info"
        panel.add(pathField, gbc)
        row++

        // HTTP方法
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        panel.add(JBLabel("HTTP方法:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        panel.add(methodComboBox, gbc)
        row++

        // 状态码
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        panel.add(JBLabel("状态码:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        statusCodeField.toolTipText = "HTTP响应状态码，例如: 200, 404, 500"
        panel.add(statusCodeField, gbc)
        row++

        // 延迟
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        panel.add(JBLabel("延迟(毫秒):"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        delayField.toolTipText = "模拟网络延迟，单位：毫秒"
        panel.add(delayField, gbc)
        row++

        // Mock数据
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        gbc.anchor = GridBagConstraints.NORTHWEST
        panel.add(JBLabel("Mock数据 (JSON):"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        gbc.weighty = 1.0
        gbc.fill = GridBagConstraints.BOTH
        mockDataArea.lineWrap = true
        mockDataArea.wrapStyleWord = true
        mockDataArea.font = UIManager.getFont("TextArea.font")
        val scrollPane = JBScrollPane(mockDataArea)
        panel.add(scrollPane, gbc)
        row++

        // 添加格式化按钮
        gbc.gridx = 1
        gbc.gridy = row
        gbc.weightx = 0.0
        gbc.weighty = 0.0
        gbc.fill = GridBagConstraints.NONE
        gbc.anchor = GridBagConstraints.EAST
        val formatButton = JButton("格式化JSON")
        formatButton.addActionListener {
            try {
                val formatted = formatJson(mockDataArea.text)
                mockDataArea.text = formatted
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(
                    panel,
                    "JSON格式错误: ${e.message}",
                    "错误",
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }
        panel.add(formatButton, gbc)

        return panel
    }

    /**
     * 简单的JSON格式化
     */
    private fun formatJson(json: String): String {
        var indent = 0
        val result = StringBuilder()
        var inString = false
        var escapeNext = false

        for (char in json) {
            when {
                escapeNext -> {
                    result.append(char)
                    escapeNext = false
                }
                char == '\\' -> {
                    result.append(char)
                    escapeNext = true
                }
                char == '"' -> {
                    inString = !inString
                    result.append(char)
                }
                !inString && char == '{' || char == '[' -> {
                    result.append(char)
                    result.append('\n')
                    indent++
                    result.append("  ".repeat(indent))
                }
                !inString && char == '}' || char == ']' -> {
                    result.append('\n')
                    indent--
                    result.append("  ".repeat(indent))
                    result.append(char)
                }
                !inString && char == ',' -> {
                    result.append(char)
                    result.append('\n')
                    result.append("  ".repeat(indent))
                }
                !inString && char == ':' -> {
                    result.append(char)
                    result.append(' ')
                }
                !inString && char.isWhitespace() -> {
                    // Skip whitespace outside strings
                }
                else -> {
                    result.append(char)
                }
            }
        }

        return result.toString()
    }

    /**
     * 获取配置的Mock API
     */
    fun getMockApiConfig(): MockApiConfig {
        return MockApiConfig(
            path = pathField.text.trim(),
            enabled = enabledCheckBox.isSelected,
            mockData = mockDataArea.text.trim(),
            method = methodComboBox.selectedItem as String,
            statusCode = statusCodeField.text.toIntOrNull() ?: 200,
            delay = delayField.text.toLongOrNull() ?: 0
        )
    }

    override fun doValidate(): ValidationInfo? {
        if (pathField.text.isBlank()) {
            return ValidationInfo("接口路径不能为空", pathField)
        }
        if (!pathField.text.startsWith("/")) {
            return ValidationInfo("接口路径必须以/开头", pathField)
        }
        if (statusCodeField.text.toIntOrNull() == null) {
            return ValidationInfo("状态码必须是有效的数字", statusCodeField)
        }
        if (delayField.text.toLongOrNull() == null) {
            return ValidationInfo("延迟必须是有效的数字", delayField)
        }
        if (mockDataArea.text.isBlank()) {
            return ValidationInfo("Mock数据不能为空", mockDataArea)
        }
        return null
    }
}