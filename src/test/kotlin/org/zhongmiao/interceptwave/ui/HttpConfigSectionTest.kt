package org.zhongmiao.interceptwave.ui

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.zhongmiao.interceptwave.model.HttpRoute
import org.zhongmiao.interceptwave.model.ProxyConfig
import javax.swing.JList

class HttpConfigSectionTest : BasePlatformTestCase() {

    fun `test first route is selected by default`() {
        val section = HttpConfigSection(
            project,
            ProxyConfig(
                protocol = "HTTP",
                routes = mutableListOf(
                    HttpRoute(
                        name = "API",
                        pathPrefix = "/api",
                        targetBaseUrl = "http://localhost:4002",
                        stripPrefix = true,
                        enableMock = true
                    )
                )
            )
        )

        section.panel()

        val listField = HttpConfigSection::class.java.getDeclaredField("routeList").apply { isAccessible = true }
        val routeList = listField.get(section) as JList<*>
        assertEquals(0, routeList.selectedIndex)
    }

    fun `test mock area switches to disabled state when route mock is turned off`() {
        val section = HttpConfigSection(
            project,
            ProxyConfig(
                protocol = "HTTP",
                routes = mutableListOf(
                    HttpRoute(
                        name = "API",
                        pathPrefix = "/api",
                        targetBaseUrl = "http://localhost:4002",
                        stripPrefix = true,
                        enableMock = true
                    )
                )
            )
        )

        section.panel()

        val checkboxField = HttpConfigSection::class.java.getDeclaredField("routeEnableMockCheckBox").apply { isAccessible = true }
        val buttonField = HttpConfigSection::class.java.getDeclaredField("mockAddButton").apply { isAccessible = true }
        val checkbox = checkboxField.get(section) as com.intellij.ui.components.JBCheckBox
        val addButton = buttonField.get(section) as javax.swing.JButton

        checkbox.doClick()

        assertFalse(addButton.isEnabled)
    }
}
