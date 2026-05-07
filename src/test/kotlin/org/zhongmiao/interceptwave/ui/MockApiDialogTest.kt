package org.zhongmiao.interceptwave.ui

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.zhongmiao.interceptwave.model.MockApiConfig
import javax.swing.JTextArea
import com.intellij.ui.components.JBTextField
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import com.intellij.openapi.ui.ComboBox

class MockApiDialogTest : BasePlatformTestCase() {

    private fun setPathField(instance: Any, value: String) {
        val field: java.lang.reflect.Field = instance.javaClass.getDeclaredField("pathField")
        field.isAccessible = true
        val textField: JBTextField = field.get(instance) as JBTextField
        textField.text = value
    }

    private fun setMockDataArea(instance: Any, value: String) {
        val field: java.lang.reflect.Field = instance.javaClass.getDeclaredField("mockDataArea")
        field.isAccessible = true
        val area: JTextArea = field.get(instance) as JTextArea
        area.text = value
    }

    private fun getMockDataArea(instance: Any): JTextArea {
        val field: java.lang.reflect.Field = instance.javaClass.getDeclaredField("mockDataArea")
        field.isAccessible = true
        return field.get(instance) as JTextArea
    }

    private fun selectTemplate(instance: Any, id: String) {
        val field: java.lang.reflect.Field = instance.javaClass.getDeclaredField("templateComboBox")
        field.isAccessible = true
        val combo = field.get(instance) as ComboBox<*>
        combo.selectedItem = MockResponseTemplates.byId(id)
    }

    fun testJsLikeInputNormalizedAndMinifiedOnSave() {
        val dialog = MockApiDialog(project, null)

        // Set minimal required path
        setPathField(dialog, "/rebate/tree")

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

        setMockDataArea(dialog, jsLike)

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

    fun testInvalidJsonIsRejectedInValidation() {
        val dialog = MockApiDialog(project, null)
        setPathField(dialog, "/x")

        // Broken input (unclosed quotes)
        val broken = "[{ key: '1, label: 'x' }]"
        setMockDataArea(dialog, broken)

        try {
            dialog.getMockApiConfig()
            fail("Expected invalid JSON to throw during getMockApiConfig()")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    fun testApplyingSuccessTemplatePopulatesEditableMockDataAndMinifiesOnSave() {
        val dialog = MockApiDialog(project, null)
        setPathField(dialog, "/success")
        selectTemplate(dialog, "success")

        dialog.applySelectedTemplate()
        val editableText = getMockDataArea(dialog).text
        assertTrue(editableText.contains("\"code\""))
        assertTrue(editableText.contains("\"data\""))

        val api = dialog.getMockApiConfig()
        assertEquals("""{"code":0,"message":"success","data":{}}""", api.mockData)
    }

    fun testBuiltInTemplatesAreValidJsonAndSavedAsMinifiedMockData() {
        listOf("pagination", "error", "auth-expired").forEach { id ->
            val dialog = MockApiDialog(project, null)
            setPathField(dialog, "/$id")
            selectTemplate(dialog, id)

            dialog.applySelectedTemplate()
            val api = dialog.getMockApiConfig()

            assertFalse(api.mockData.contains("\n"))
            Json.parseToJsonElement(api.mockData)
            assertEquals(200, api.statusCode)
        }
    }

    fun testEditingExistingMockApiDoesNotOverwriteMockDataUntilTemplateIsApplied() {
        val existing = MockApiConfig(
            path = "/existing",
            mockData = """{"custom":true}""",
            method = "GET"
        )
        val dialog = MockApiDialog(project, existing)

        assertEquals("""{"custom":true}""", getMockDataArea(dialog).text)
        assertEquals("""{"custom":true}""", dialog.getMockApiConfig().mockData)

        selectTemplate(dialog, "empty")
        dialog.applySelectedTemplate()

        assertEquals("""{"code":0,"message":"success","data":null}""", dialog.getMockApiConfig().mockData)
    }
}
