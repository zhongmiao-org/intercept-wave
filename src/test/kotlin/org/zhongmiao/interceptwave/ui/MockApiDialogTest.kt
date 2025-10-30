package org.zhongmiao.interceptwave.ui

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.zhongmiao.interceptwave.model.MockApiConfig
import javax.swing.JTextArea
import com.intellij.ui.components.JBTextField
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class MockApiDialogTest : BasePlatformTestCase() {

    private fun setPrivateTextField(instance: Any, fieldName: String, value: String) {
        val field: java.lang.reflect.Field = instance.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        val textField: JBTextField = field.get(instance) as JBTextField
        textField.text = value
    }

    private fun setPrivateTextArea(instance: Any, fieldName: String, value: String) {
        val field: java.lang.reflect.Field = instance.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        val area: JTextArea = field.get(instance) as JTextArea
        area.text = value
    }

    fun `test js-like input normalized and minified on save`() {
        val dialog = MockApiDialog(project, null)

        // Set minimal required path
        setPrivateTextField(dialog, "pathField", "/rebate/tree")

        // JS/JSON5-like input: single quotes, unquoted keys, trailing commas
        val jsLike = """
            [
              {
                key: '1',
                label: '定时返利',
                data: { amount: '260,000.00', rebateType: '返利类别汇总' },
                children: [ { key: '1-1', label: '年返' }, ],
              }
            ]
        """.trimIndent()

        setPrivateTextArea(dialog, "mockDataArea", jsLike)

        // Save and verify minified strict JSON (no exception indicates normalization+validation success)
        val api: MockApiConfig = dialog.getMockApiConfig()
        val saved = api.mockData
        // No newlines in minified output
        assertFalse(saved.contains("\n"))

        val element = Json.parseToJsonElement(saved)
        val arr = element.jsonArray
        assertTrue(arr.size > 0)
        val first = arr[0].jsonObject
        assertEquals("1", first["key"]!!.toString().trim('"'))
        assertEquals("定时返利", first["label"]!!.toString().trim('"'))
    }

    fun `test invalid json is rejected in validation`() {
        val dialog = MockApiDialog(project, null)
        setPrivateTextField(dialog, "pathField", "/x")

        // Broken input (unclosed quotes)
        val broken = "[{ key: '1, label: 'x' }]"
        setPrivateTextArea(dialog, "mockDataArea", broken)

        try {
            dialog.getMockApiConfig()
            fail("Expected invalid JSON to throw during getMockApiConfig()")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
