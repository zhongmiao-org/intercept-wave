package org.zhongmiao.interceptwave.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import org.zhongmiao.interceptwave.InterceptWaveBundle.message
import org.zhongmiao.interceptwave.model.HttpRoute
import org.zhongmiao.interceptwave.model.ProxyConfig
import java.awt.BorderLayout
import java.awt.Component
import java.util.UUID
import javax.swing.JButton
import javax.swing.JOptionPane
import javax.swing.event.ListSelectionEvent
import javax.swing.table.DefaultTableModel

/** HTTP 内容区：路由列表 + 路由详情 + 当前路由 Mock 列表 */
class HttpConfigSection(
    private val project: Project,
    private val config: ProxyConfig,
    private val onChanged: () -> Unit = {}
) {
    private val cookieField = JBTextField(config.globalCookie)
    private val routeNameField = JBTextField()
    private val routePrefixField = JBTextField()
    private val routeBaseUrlField = JBTextField()
    private val routeStripPrefixCheckBox = JBCheckBox(message("config.group.stripprefix"))
    private val routeEnableMockCheckBox = JBCheckBox(message("config.http.route.enablemock"))
    private val routeMockHintLabel = JBLabel(message("config.http.route.mock.hint"))

    private val routeTableModel = object : DefaultTableModel(
        arrayOf(
            message("config.http.route.table.enablemock"),
            message("config.http.route.table.name"),
            message("config.http.route.table.prefix"),
            message("config.http.route.table.baseurl")
        ),
        0
    ) {
        override fun getColumnClass(column: Int): Class<*> = if (column == 0) Boolean::class.javaObjectType else String::class.java
        override fun isCellEditable(row: Int, column: Int): Boolean = false
    }
    private val routeTable = JBTable(routeTableModel)

    private val mockTableModel = object : DefaultTableModel(
        arrayOf(
            message("config.table.enabled"),
            message("config.table.path"),
            message("config.table.method"),
            message("config.table.statuscode"),
            message("config.table.delay")
        ),
        0
    ) {
        override fun getColumnClass(column: Int): Class<*> = if (column == 0) Boolean::class.javaObjectType else String::class.java
        override fun isCellEditable(row: Int, column: Int): Boolean = false
    }
    private val mockTable = JBTable(mockTableModel)
    private val mockAddButton = JButton()
    private val mockEditButton = JButton()
    private val mockDeleteButton = JButton()

    private val workingRoutes = initialRoutes()
    private var selectedRouteIndex = -1
    private var syncingRouteDetails = false

    fun panel(): JBPanel<JBPanel<*>> {
        cookieField.toolTipText = message("config.group.cookie.tooltip")
        routePrefixField.toolTipText = message("config.http.route.prefix.tooltip")
        routeBaseUrlField.toolTipText = message("config.group.baseurl.tooltip")
        routeStripPrefixCheckBox.toolTipText = message("config.http.route.stripprefix.tooltip")
        routeEnableMockCheckBox.toolTipText = message("config.http.route.enablemock.tooltip")
        routeMockHintLabel.toolTipText = message("config.http.route.mock.hint")

        routeTable.fillsViewportHeight = true
        mockTable.fillsViewportHeight = true
        UiKit.ensureVisibleRows(routeTable, 5)
        UiKit.ensureVisibleRows(mockTable, UiKit.DEFAULT_VISIBLE_ROWS)
        UiKit.setEnabledColumnWidth(routeTable, 0)
        UiKit.setEnabledColumnWidth(mockTable, 0)

        attachListeners()
        reloadRoutesTable()
        ensureSelection()

        val rootPanel = JBPanel<JBPanel<*>>(BorderLayout(10, 10))
        val routeScroll = JBScrollPane(routeTable).apply {
            border = null
            viewportBorder = null
        }
        val mockScroll = JBScrollPane(mockTable).apply {
            border = null
            viewportBorder = null
        }

        val content = panel {
            group(message("config.http.title")) {
                row(message("config.group.cookie") + ":") { cell(cookieField).align(AlignX.FILL) }

                row { cell(routeScroll).align(AlignX.FILL) }
                row {
                    button(message("config.http.route.add")) { addRoute() }
                        .applyToComponent {
                            icon = com.intellij.icons.AllIcons.General.Add
                            isFocusPainted = false
                        }
                    button(message("config.http.route.delete")) { deleteRoute() }
                        .applyToComponent {
                            icon = com.intellij.icons.AllIcons.General.Remove
                            isFocusPainted = false
                        }
                    button(message("config.http.route.move.up")) { moveRoute(-1) }
                        .applyToComponent {
                            icon = com.intellij.icons.AllIcons.Actions.MoveUp
                            isFocusPainted = false
                        }
                    button(message("config.http.route.move.down")) { moveRoute(1) }
                        .applyToComponent {
                            icon = com.intellij.icons.AllIcons.Actions.MoveDown
                            isFocusPainted = false
                        }
                }

                separator()

                row(message("config.http.route.name") + ":") { cell(routeNameField).align(AlignX.FILL) }
                row(message("config.group.prefix") + ":") { cell(routePrefixField).align(AlignX.FILL) }
                row(message("config.group.baseurl") + ":") { cell(routeBaseUrlField).align(AlignX.FILL) }
                row { cell(routeStripPrefixCheckBox) }
                row { cell(routeEnableMockCheckBox) }

                row { cell(routeMockHintLabel).align(AlignX.FILL) }
                row { cell(mockScroll).align(AlignX.FILL) }
                row {
                    button(message("mockapi.add.button")) { addApi() }
                        .applyToComponent {
                            mockAddButton.text = text
                            mockAddButton.icon = com.intellij.icons.AllIcons.General.Add
                            mockAddButton.isFocusPainted = false
                            icon = com.intellij.icons.AllIcons.General.Add
                            isFocusPainted = false
                            mockAddButton.model = model
                        }
                    button(message("mockapi.edit.button")) { editApi() }
                        .applyToComponent {
                            mockEditButton.text = text
                            mockEditButton.icon = com.intellij.icons.AllIcons.Actions.Edit
                            mockEditButton.isFocusPainted = false
                            icon = com.intellij.icons.AllIcons.Actions.Edit
                            isFocusPainted = false
                            mockEditButton.model = model
                        }
                    button(message("mockapi.delete.button")) { deleteApi() }
                        .applyToComponent {
                            mockDeleteButton.text = text
                            mockDeleteButton.icon = com.intellij.icons.AllIcons.General.Remove
                            mockDeleteButton.isFocusPainted = false
                            icon = com.intellij.icons.AllIcons.General.Remove
                            isFocusPainted = false
                            mockDeleteButton.model = model
                        }
                }
            }
        }
        rootPanel.add(content, BorderLayout.CENTER)
        updateRouteDetails()
        updateMockAreaState()
        return rootPanel
    }

    fun applyTo(target: ProxyConfig) {
        if (workingRoutes.isEmpty()) {
            workingRoutes.add(defaultRoute())
        }
        commitCurrentRoute()
        target.globalCookie = cookieField.text.trim()
        target.routes = workingRoutes.map { copyRoute(it) }.toMutableList()
        syncLegacyHttpFields(target)

        val duplicates = target.routes.groupBy { it.pathPrefix.trim() }.filter { it.key.isNotBlank() && it.value.size > 1 }.keys
        if (duplicates.isNotEmpty()) {
            JOptionPane.showMessageDialog(
                routeTable,
                message("config.http.route.duplicate.warning", duplicates.joinToString(", ")),
                message("config.message.info"),
                JOptionPane.WARNING_MESSAGE
            )
        }
    }

    private fun attachListeners() {
        cookieField.document.onAnyChange(onChanged)
        routeNameField.document.onAnyChange { onRouteDetailChanged() }
        routePrefixField.document.onAnyChange { onRouteDetailChanged() }
        routeBaseUrlField.document.onAnyChange { onRouteDetailChanged() }
        routeStripPrefixCheckBox.addActionListener { onRouteDetailChanged() }
        routeEnableMockCheckBox.addActionListener {
            onRouteDetailChanged()
            updateMockAreaState()
        }
        routeTable.selectionModel.addListSelectionListener { event: ListSelectionEvent ->
            if (!event.valueIsAdjusting) handleRouteSelectionChanged()
        }
    }

    private fun handleRouteSelectionChanged() {
        val newIndex = routeTable.selectedRow
        if (newIndex < 0 || newIndex >= workingRoutes.size) return
        if (newIndex == selectedRouteIndex) return
        commitCurrentRoute()
        selectedRouteIndex = newIndex
        updateRouteDetails()
        updateMockTable()
        updateMockAreaState()
    }

    private fun onRouteDetailChanged() {
        if (syncingRouteDetails) return
        commitCurrentRoute()
        reloadRoutesTable()
        if (selectedRouteIndex in 0 until routeTable.rowCount) {
            routeTable.setRowSelectionInterval(selectedRouteIndex, selectedRouteIndex)
        }
        updateMockAreaState()
        onChanged()
    }

    private fun commitCurrentRoute() {
        if (selectedRouteIndex !in workingRoutes.indices) return
        val route = workingRoutes[selectedRouteIndex]
        route.name = routeNameField.text.trim().ifEmpty { "API" }
        route.pathPrefix = routePrefixField.text.trim().ifEmpty { "/" }
        route.targetBaseUrl = routeBaseUrlField.text.trim().ifEmpty { "http://localhost:8080" }
        route.stripPrefix = routeStripPrefixCheckBox.isSelected
        route.enableMock = routeEnableMockCheckBox.isSelected
    }

    private fun updateRouteDetails() {
        syncingRouteDetails = true
        try {
            val route = currentRoute() ?: defaultRoute()
            routeNameField.text = route.name
            routePrefixField.text = route.pathPrefix
            routeBaseUrlField.text = route.targetBaseUrl
            routeStripPrefixCheckBox.isSelected = route.stripPrefix
            routeEnableMockCheckBox.isSelected = route.enableMock
        } finally {
            syncingRouteDetails = false
        }
        updateMockTable()
    }

    private fun reloadRoutesTable() {
        routeTableModel.rowCount = 0
        workingRoutes.forEach { route ->
            routeTableModel.addRow(arrayOf<Any>(route.enableMock, route.name, route.pathPrefix, route.targetBaseUrl))
        }
    }

    private fun updateMockTable() {
        mockTableModel.rowCount = 0
        currentRoute()?.mockApis?.forEach { api ->
            mockTableModel.addRow(arrayOf<Any>(api.enabled, api.path, api.method, api.statusCode.toString(), api.delay.toString()))
        }
    }

    private fun updateMockAreaState() {
        val enabled = currentRoute()?.enableMock == true
        routeMockHintLabel.isEnabled = enabled
        setComponentEnabled(mockTable, enabled)
        mockTable.isEnabled = enabled
        mockAddButton.isEnabled = enabled
        mockEditButton.isEnabled = enabled
        mockDeleteButton.isEnabled = enabled
    }

    private fun addRoute() {
        commitCurrentRoute()
        workingRoutes.add(defaultRoute(index = workingRoutes.size))
        reloadRoutesTable()
        selectedRouteIndex = workingRoutes.lastIndex
        routeTable.setRowSelectionInterval(selectedRouteIndex, selectedRouteIndex)
        updateRouteDetails()
        onChanged()
    }

    private fun deleteRoute() {
        if (workingRoutes.size <= 1) {
            JOptionPane.showMessageDialog(
                routeTable,
                message("config.http.route.delete.atleastone"),
                message("config.message.info"),
                JOptionPane.WARNING_MESSAGE
            )
            return
        }
        val row = routeTable.selectedRow
        if (row < 0) {
            JOptionPane.showMessageDialog(
                routeTable,
                message("config.http.route.select.first"),
                message("config.message.info"),
                JOptionPane.INFORMATION_MESSAGE
            )
            return
        }
        workingRoutes.removeAt(row)
        reloadRoutesTable()
        selectedRouteIndex = (row - 1).coerceAtLeast(0)
        routeTable.setRowSelectionInterval(selectedRouteIndex, selectedRouteIndex)
        updateRouteDetails()
        onChanged()
    }

    private fun moveRoute(direction: Int) {
        val row = routeTable.selectedRow
        val next = row + direction
        if (row < 0 || next !in workingRoutes.indices) return
        commitCurrentRoute()
        val route = workingRoutes.removeAt(row)
        workingRoutes.add(next, route)
        reloadRoutesTable()
        selectedRouteIndex = next
        routeTable.setRowSelectionInterval(next, next)
        updateRouteDetails()
        onChanged()
    }

    private fun addApi() {
        val route = currentRoute() ?: return
        if (!route.enableMock) return
        val dialog = MockApiDialog(project, null)
        if (dialog.showAndGet()) {
            route.mockApis.add(dialog.getMockApiConfig())
            updateMockTable()
            onChanged()
        }
    }

    private fun editApi() {
        val route = currentRoute() ?: return
        if (!route.enableMock) return
        val row = mockTable.selectedRow
        if (row < 0 || row >= route.mockApis.size) {
            JOptionPane.showMessageDialog(
                mockTable,
                message("mockapi.select.first.edit"),
                message("config.message.info"),
                JOptionPane.INFORMATION_MESSAGE
            )
            return
        }
        val dialog = MockApiDialog(project, route.mockApis[row])
        if (dialog.showAndGet()) {
            route.mockApis[row] = dialog.getMockApiConfig()
            updateMockTable()
            onChanged()
        }
    }

    private fun deleteApi() {
        val route = currentRoute() ?: return
        if (!route.enableMock) return
        val row = mockTable.selectedRow
        if (row < 0 || row >= route.mockApis.size) {
            JOptionPane.showMessageDialog(
                mockTable,
                message("mockapi.select.first.delete"),
                message("config.message.info"),
                JOptionPane.INFORMATION_MESSAGE
            )
            return
        }
        val result = JOptionPane.showConfirmDialog(
            mockTable,
            message("mockapi.delete.confirm"),
            message("config.message.confirm.title"),
            JOptionPane.YES_NO_OPTION
        )
        if (result == JOptionPane.YES_OPTION) {
            route.mockApis.removeAt(row)
            updateMockTable()
            onChanged()
        }
    }

    private fun ensureSelection() {
        if (workingRoutes.isEmpty()) workingRoutes.add(defaultRoute())
        if (selectedRouteIndex !in workingRoutes.indices) {
            selectedRouteIndex = 0
        }
        if (routeTable.rowCount > 0) {
            routeTable.setRowSelectionInterval(selectedRouteIndex, selectedRouteIndex)
        }
    }

    private fun currentRoute(): HttpRoute? = workingRoutes.getOrNull(selectedRouteIndex)

    private fun initialRoutes(): MutableList<HttpRoute> {
        val source = if (config.routes.isNotEmpty()) config.routes else mutableListOf(defaultRoute())
        return source.map { copyRoute(it) }.toMutableList()
    }

    private fun defaultRoute(index: Int = 0): HttpRoute {
        val prefix = if (index == 0) {
            legacyInterceptPrefix(config).ifBlank { "/api" }
        } else {
            "/api$index"
        }
        return HttpRoute(
            id = UUID.randomUUID().toString(),
            name = if (index == 0) "API" else "API ${index + 1}",
            pathPrefix = prefix,
            targetBaseUrl = legacyBaseUrl(config).ifBlank { "http://localhost:8080" },
            stripPrefix = legacyStripPrefix(config),
            enableMock = true,
            mockApis = mutableListOf()
        )
    }

    private fun copyRoute(route: HttpRoute): HttpRoute =
        route.copy(mockApis = route.mockApis.map { it.copy() }.toMutableList())

    @Suppress("DEPRECATION")
    private fun syncLegacyHttpFields(target: ProxyConfig) {
        val primary = target.routes.first()
        target.interceptPrefix = primary.pathPrefix
        target.baseUrl = primary.targetBaseUrl
        target.stripPrefix = primary.stripPrefix
        target.mockApis = primary.mockApis.map { it.copy() }.toMutableList()
    }

    @Suppress("DEPRECATION")
    private fun legacyInterceptPrefix(source: ProxyConfig): String = source.interceptPrefix

    @Suppress("DEPRECATION")
    private fun legacyBaseUrl(source: ProxyConfig): String = source.baseUrl

    @Suppress("DEPRECATION")
    private fun legacyStripPrefix(source: ProxyConfig): Boolean = source.stripPrefix
    private fun setComponentEnabled(component: Component, enabled: Boolean) {
        component.isEnabled = enabled
        if (component is java.awt.Container) {
            component.components.forEach { child -> setComponentEnabled(child, enabled) }
        }
    }
}
