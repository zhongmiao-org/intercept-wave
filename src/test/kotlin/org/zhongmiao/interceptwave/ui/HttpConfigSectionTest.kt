package org.zhongmiao.interceptwave.ui

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.zhongmiao.interceptwave.model.HttpRoute
import org.zhongmiao.interceptwave.model.ProxyConfig
import javax.swing.table.DefaultTableModel

class HttpConfigSectionTest : BasePlatformTestCase() {

    fun `test route table mock enabled checkbox updates working route`() {
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

        val modelField = HttpConfigSection::class.java.getDeclaredField("routeTableModel").apply { isAccessible = true }
        val routesField = HttpConfigSection::class.java.getDeclaredField("workingRoutes").apply { isAccessible = true }
        val checkboxField = HttpConfigSection::class.java.getDeclaredField("routeEnableMockCheckBox").apply { isAccessible = true }

        val model = modelField.get(section) as DefaultTableModel
        @Suppress("UNCHECKED_CAST")
        val workingRoutes = routesField.get(section) as MutableList<HttpRoute>
        val checkbox = checkboxField.get(section) as com.intellij.ui.components.JBCheckBox

        assertEquals(true, model.isCellEditable(0, 0))
        model.setValueAt(false, 0, 0)

        assertFalse(workingRoutes[0].enableMock)
        assertFalse(checkbox.isSelected)
        assertEquals(false, model.getValueAt(0, 0))
    }
}
