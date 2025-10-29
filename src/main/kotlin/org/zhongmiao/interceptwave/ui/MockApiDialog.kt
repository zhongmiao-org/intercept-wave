package org.zhongmiao.interceptwave.ui

import org.zhongmiao.interceptwave.InterceptWaveBundle.message
import org.zhongmiao.interceptwave.model.MockApiConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
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
        panel.add(JBLabel(message("mockapi.mockdata")), gbc)

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
    private val strictJson by lazy { Json { prettyPrint = false; ignoreUnknownKeys = false } }

    private fun parseJsonStrict(text: String): JsonElement = strictJson.parseToJsonElement(text)

    // 将 JS/JSON5 风格的文本（单引号、未加引号的键、尾逗号等）尽力归一化为严格 JSON
    private fun normalizeJsLikeToStrictJson(text: String): String {
        var s = text
        // 1) 去除行内与块注释（避免破坏字符串内部，这里先粗略处理单引号转换）
        // 注意：为简单起见，先转换单引号字符串，再做注释清理与键名修正
        s = convertSingleQuotedStrings(s)

        // 2) 去除 JS 风格注释（不在字符串内的情况）
        s = removeJsCommentsOutsideStrings(s)

        // 3) 为未加引号的对象键补齐引号（在 { 或 , 之后出现的 key: 形式）
        s = quoteUnquotedObjectKeys(s)

        // 4) 移除尾随逗号
        s = removeTrailingCommas(s)

        return s
    }

    // 将 '...' 转为 "..."，并对内容进行必要转义；跳过已在双引号内的内容
    private fun convertSingleQuotedStrings(input: String): String {
        val sb = StringBuilder()
        var i = 0
        var inDouble = false
        while (i < input.length) {
            val c = input[i]
            if (c == '"') {
                sb.append(c)
                inDouble = !inDouble
                i++
            } else if (!inDouble && c == '\'') {
                // 进入单引号字符串
                val start = i + 1
                var j = start
                var escape = false
                while (j < input.length) {
                    val ch = input[j]
                    if (escape) {
                        escape = false
                    } else if (ch == '\\') {
                        escape = true
                    } else if (ch == '\'') {
                        break
                    }
                    j++
                }
                val content = if (j <= input.length) input.substring(start, j) else input.substring(start)
                // 转为合法 JSON 字符串：转义反斜杠与双引号
                val escaped = content
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                sb.append('"').append(escaped).append('"')
                i = if (j < input.length && input[j] == '\'') j + 1 else j
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    // 去除 JS 注释（简化实现：不考虑字符串内的 // 或 /* */，因为前一步已将单引号转为双引号，但双引号内仍可能出现 //，此处尽量保守）
    private fun removeJsCommentsOutsideStrings(input: String): String {
        val sb = StringBuilder()
        var i = 0
        var inDouble = false
        while (i < input.length) {
            val c = input[i]
            val next = if (i + 1 < input.length) input[i + 1] else '\u0000'
            if (c == '"') {
                inDouble = !inDouble
                sb.append(c)
                i++
            } else if (!inDouble && c == '/' && next == '/') {
                // 行注释，跳到行尾
                i += 2
                while (i < input.length && input[i] != '\n') i++
            } else if (!inDouble && c == '/' && next == '*') {
                // 块注释，跳到 */
                i += 2
                while (i + 1 < input.length && !(input[i] == '*' && input[i + 1] == '/')) i++
                i = if (i + 1 < input.length) i + 2 else input.length
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    // 给未加引号的 key 加引号：仅在字符串外处理，模式：{ key: ... } 或 , key: ...
    private fun quoteUnquotedObjectKeys(input: String): String {
        val sb = StringBuilder()
        var i = 0
        var inDouble = false
        fun isKeyStart(ch: Char): Boolean = ch == '_' || ch == '$' || ch.isLetter()
        fun isKeyChar(ch: Char): Boolean = ch == '_' || ch == '-' || ch == '$' || ch.isLetterOrDigit()

        while (i < input.length) {
            val c = input[i]
            if (c == '"') {
                sb.append(c)
                inDouble = !inDouble
                i++
                continue
            }
            if (!inDouble && (c == '{' || c == ',')) {
                // 复制当前符号
                sb.append(c)
                i++
                // 保留后续空白
                while (i < input.length && input[i].isWhitespace()) sb.append(input[i++])
                if (i >= input.length) break
                // 已有引号则跳过
                if (input[i] == '"') {
                    // 交由后续流程处理
                    continue
                }
                // 尝试识别 key 标识符并观察冒号
                val keyStart = i
                if (isKeyStart(input[i])) {
                    var j = i + 1
                    while (j < input.length && isKeyChar(input[j])) j++
                    val key = input.substring(i, j)
                    var k = j
                    while (k < input.length && input[k].isWhitespace()) k++
                    if (k < input.length && input[k] == ':') {
                        // 命中 key: 结构，写入引号包裹的 key
                        sb.append('"').append(key).append('"')
                        // 写回 key 与冒号之间的空白
                        while (j < k) sb.append(input[j++])
                        sb.append(':')
                        i = k + 1
                        continue
                    }
                }
                // 否则回退为原样输出（从 keyStart 起）
                i = keyStart
                continue
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    // 去除尾随逗号：在字符串外，若在 } 或 ] 之前的最近非空白为逗号，则删除该逗号
    private fun removeTrailingCommas(input: String): String {
        val sb = StringBuilder()
        var inDouble = false
        for (ch in input) {
            if (ch == '"') {
                inDouble = !inDouble
                sb.append(ch)
                continue
            }
            if (!inDouble && (ch == '}' || ch == ']')) {
                // 回溯删除最近的非空白逗号
                var idx = sb.length - 1
                while (idx >= 0 && sb[idx].isWhitespace()) idx--
                if (idx >= 0 && sb[idx] == ',') {
                    sb.deleteCharAt(idx)
                }
                sb.append(ch)
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    // 宽容解析：先严格解析，失败再尝试归一化后解析
    private fun parseJson(text: String): JsonElement {
        return try {
            parseJsonStrict(text)
        } catch (_: Exception) {
            val normalized = normalizeJsLikeToStrictJson(text)
            parseJsonStrict(normalized)
        }
    }

    // 用于“格式化”按钮（辅助查看）：美化输出
    private val prettyJsonFmt by lazy { Json { prettyPrint = true } }
    private fun prettyJson(text: String): String {
        val element = parseJson(text)
        return prettyJsonFmt.encodeToString(JsonElement.serializer(), element)
    }

    // 保存时压缩为无空格无换行的 JSON 串
    private fun minifyJson(text: String): String {
        val element = parseJson(text)
        return element.toString() // 紧凑 JSON
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
