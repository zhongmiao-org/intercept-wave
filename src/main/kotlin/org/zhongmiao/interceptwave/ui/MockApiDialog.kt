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
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import java.awt.BorderLayout
import com.intellij.util.ui.JBUI
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
    private val mockDataArea = JTextArea(existingApi?.mockData ?: "{}").apply {
        lineWrap = true
        wrapStyleWord = true
        rows = 10
        toolTipText = message("mockapi.mockdata.tooltip")
    }
    private val enabledCheckBox = JCheckBox(message("mockapi.enabled"), existingApi?.enabled ?: true)

    init {
        init()
        title = if (existingApi == null) message("mockapi.dialog.title.add") else message("mockapi.dialog.title.edit")

        existingApi?.let {
            methodComboBox.selectedItem = it.method
        }
    }

    override fun createCenterPanel(): JComponent {
        // 容器：顶部 DSL 表单 + 中部 JSON 编辑区
        val root = JPanel(BorderLayout(10, 10))
        root.preferredSize = JBUI.size(600, 500)

        // 顶部基本信息（UI DSL，复用既有组件，保持监听/校验逻辑不变）
        pathField.toolTipText = message("mockapi.path.tooltip")
        statusCodeField.toolTipText = message("mockapi.statuscode.tooltip")
        delayField.toolTipText = message("mockapi.delay.tooltip")
        useCookieCheckBox.toolTipText = message("mockapi.usecookie.tooltip")

        val topForm = panel {
            group(message("mockapi.group.basic")) {
                row {
                    cell(enabledCheckBox)
                }
                row(message("mockapi.path")) {
                    cell(pathField).align(AlignX.FILL)
                }
                row(message("mockapi.method")) {
                    cell(methodComboBox).align(AlignX.FILL)
                }
                row(message("mockapi.statuscode")) {
                    cell(statusCodeField).align(AlignX.FILL)
                }
                row(message("mockapi.delay")) {
                    cell(delayField).align(AlignX.FILL)
                }
                row {
                    cell(useCookieCheckBox)
                }
            }
        }
        root.add(topForm, BorderLayout.NORTH)

        // 中部：Mock 数据编辑区（DSL）
        val editorForm = panel {
            row(message("mockapi.mockdata")) {
                cell(JBScrollPane(mockDataArea)).align(AlignX.FILL)
            }
        }

        root.add(editorForm, BorderLayout.CENTER)

        return root
    }

    override fun createSouthAdditionalPanel(): JPanel {
        // 左下角统一放置“格式化 JSON”
        return panel {
            row {
                button(message("mockapi.button.format")) {
                    try {
                        val formatted = prettyJson(mockDataArea.text)
                        mockDataArea.text = formatted
                    } catch (e: Exception) {
                        JOptionPane.showMessageDialog(
                            contentPanel,
                            message("mockapi.message.json.error", e.message ?: ""),
                            message("config.message.error"),
                            JOptionPane.ERROR_MESSAGE
                        )
                    }
                }.applyToComponent {
                    icon = AllIcons.Actions.ReformatCode
                    isFocusPainted = false
                }
            }
        }
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
