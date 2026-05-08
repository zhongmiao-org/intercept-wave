package org.zhongmiao.interceptwave.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import org.zhongmiao.interceptwave.InterceptWaveBundle.message
import org.zhongmiao.interceptwave.model.HeaderOverrideOperation
import org.zhongmiao.interceptwave.model.HeaderOverrideRule
import org.zhongmiao.interceptwave.util.HeaderOverrideUtil
import javax.swing.JComponent

class HeaderOverrideRuleDialog(
    project: Project,
    existingRule: HeaderOverrideRule?,
    private val requestSide: Boolean
) : DialogWrapper(project) {
    private val enabledCheckBox = JBCheckBox(message("config.table.enabled"), existingRule?.enabled ?: true)
    private val operationComboBox = ComboBox(HeaderOverrideOperation.values()).apply {
        selectedItem = existingRule?.operation ?: HeaderOverrideOperation.SET
    }
    private val nameField = JBTextField(existingRule?.name ?: "")
    private val valueField = JBTextField(existingRule?.value ?: "")
    private val warningLabel = JBLabel(restrictedHint()).apply { UiKit.applySecondaryText(this) }

    init {
        title = if (existingRule == null) message("config.http.headers.dialog.add") else message("config.http.headers.dialog.edit")
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row { cell(enabledCheckBox) }
        row(message("config.http.headers.operation") + ":") { cell(operationComboBox) }
        row(message("config.http.headers.name") + ":") { cell(nameField).align(AlignX.FILL) }
        row(message("config.http.headers.value") + ":") { cell(valueField).align(AlignX.FILL) }
        row { cell(warningLabel).align(AlignX.FILL) }
    }

    override fun doValidate(): ValidationInfo? {
        if (nameField.text.isBlank()) return ValidationInfo(message("config.http.headers.validation.name"), nameField)
        val operation = operationComboBox.selectedItem as? HeaderOverrideOperation ?: HeaderOverrideOperation.SET
        if (operation != HeaderOverrideOperation.REMOVE && valueField.text.isBlank()) {
            return ValidationInfo(message("config.http.headers.validation.value"), valueField)
        }
        return null
    }

    fun rule(): HeaderOverrideRule =
        HeaderOverrideRule(
            name = nameField.text.trim(),
            value = valueField.text,
            operation = operationComboBox.selectedItem as? HeaderOverrideOperation ?: HeaderOverrideOperation.SET,
            enabled = enabledCheckBox.isSelected
        )

    private fun restrictedHint(): String {
        val names = if (requestSide) {
            HeaderOverrideUtil.restrictedRequestHeaders
        } else {
            HeaderOverrideUtil.restrictedResponseHeaders
        }.joinToString(", ")
        return message("config.http.headers.restricted.hint", names)
    }
}
