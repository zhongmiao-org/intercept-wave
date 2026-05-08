package org.zhongmiao.interceptwave.ui

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import org.zhongmiao.interceptwave.InterceptWaveBundle.message
import org.zhongmiao.interceptwave.model.HttpRoute
import org.zhongmiao.interceptwave.model.HttpRouteTargetType
import org.zhongmiao.interceptwave.model.ProxyConfig
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.Font
import java.awt.FlowLayout
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComboBox
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants
import javax.swing.table.DefaultTableModel

/** HTTP 内容区：左侧路由侧栏 + 右侧路由详情/Mock 列表 */
class HttpConfigSection(
    private val project: Project,
    private val config: ProxyConfig,
    private val initialRouteIndex: Int? = null,
    initialMockIndex: Int? = null,
    private val onChanged: () -> Unit = {}
) {
    private val cookieField = JBTextField(config.globalCookie)
    private val routeNameField = JBTextField()
    private val routePrefixField = JBTextField()
    private val routeTargetTypeComboBox = JComboBox(HttpRouteTargetType.values())
    private val routeBaseUrlField = JBTextField()
    private val routeStaticRootField = TextFieldWithBrowseButton()
    private val routeStaticRootHintLabel = JBLabel()
    private val routeRewriteTargetPathField = JBTextField()
    private val routeSpaFallbackPathField = JBTextField()
    private val routeStaticSpaFallbackCheckBox = JBCheckBox(message("config.http.route.static.spa.fallback"))
    private val routeTargetCard = JPanel(CardLayout())
    private val routeStripPrefixCheckBox = JBCheckBox(message("config.group.stripprefix"))
    private val routeEnableMockCheckBox = JBCheckBox(message("config.http.route.enablemock"))
    private val routeMockHintLabel = JBLabel(message("config.http.mock.help"))

    private val routeListModel = DefaultListModel<HttpRoute>()
    private val routeList = JBList(routeListModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = RouteListRenderer()
        visibleRowCount = 8
    }

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
        override fun isCellEditable(row: Int, column: Int): Boolean = column == 0
    }
    private val mockTable = JBTable(mockTableModel)
    private val mockAddButton = JButton()
    private val mockEditButton = JButton()
    private val mockDeleteButton = JButton()
    private val mockListCard = JPanel(CardLayout())
    private val mockTableScroll = JBScrollPane(mockTable).apply {
        border = null
        viewportBorder = null
    }
    private val mockEmptyState = UiKit.createEmptyState(
        message("config.http.mock.empty.title"),
        message("config.http.mock.empty.desc")
    )
    private val mockDisabledState = UiKit.createEmptyState(
        message("config.http.mock.disabled.title"),
        message("config.http.mock.disabled.desc")
    )

    private val workingRoutes = initialRoutes()
    private var selectedRouteIndex = -1
    private var syncingRouteDetails = false
    private var syncingMockTable = false
    private var pendingInitialMockIndex: Int? = initialMockIndex

    fun panel(): JBPanel<JBPanel<*>> {
        cookieField.toolTipText = message("config.group.cookie.tooltip")
        routePrefixField.toolTipText = message("config.http.route.prefix.tooltip")
        routeTargetTypeComboBox.toolTipText = message("config.http.route.targettype.tooltip")
        routeBaseUrlField.toolTipText = message("config.group.baseurl.tooltip")
        routeStaticRootField.textField.toolTipText = message("config.http.route.static.root.tooltip")
        routeStaticRootHintLabel.toolTipText = message("config.http.route.static.root.hint")
        routeRewriteTargetPathField.toolTipText = message("config.http.route.rewrite.tooltip")
        routeSpaFallbackPathField.toolTipText = message("config.http.route.spa.fallback.tooltip")
        routeStaticSpaFallbackCheckBox.toolTipText = message("config.http.route.static.spa.fallback.tooltip")
        routeStripPrefixCheckBox.toolTipText = message("config.http.route.stripprefix.tooltip")
        routeEnableMockCheckBox.toolTipText = message("config.http.route.enablemock.tooltip")
        routeMockHintLabel.toolTipText = message("config.http.mock.help")

        mockTable.fillsViewportHeight = true
        UiKit.applyCompactTableStyle(mockTable)
        UiKit.ensureVisibleRows(mockTable, UiKit.DEFAULT_VISIBLE_ROWS)
        UiKit.setEnabledColumnWidth(mockTable, 0)
        UiKit.installTableTooltips(mockTable)

        attachListeners()
        reloadRouteList()
        ensureSelection()

        mockListCard.add(mockTableScroll, "table")
        mockListCard.add(mockEmptyState, "empty")
        mockListCard.add(mockDisabledState, "disabled")

        val rootPanel = JBPanel<JBPanel<*>>(BorderLayout(10, 10))
        val content = panel {
            row {
                val cookieLabel = JBLabel(message("config.group.cookie") + ":")
                UiKit.applySecondaryText(cookieLabel)
                cell(cookieLabel)
                cell(cookieField).align(AlignX.FILL)
            }
            row {
                val cookieHint = JBLabel(message("config.group.cookie.tooltip"))
                UiKit.applySecondaryText(cookieHint)
                cell(cookieHint).align(AlignX.FILL)
            }
            row { cell(createRouteEditorContent()).align(AlignX.FILL) }
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

        val duplicates = target.routes.groupBy { it.pathPrefix.trim() }.filter { it.key.isNotBlank() && it.value.size > 1 }.keys
        if (duplicates.isNotEmpty()) {
            JOptionPane.showMessageDialog(
                routeList,
                message("config.http.route.duplicate.warning", duplicates.joinToString(", ")),
                message("config.message.info"),
                JOptionPane.WARNING_MESSAGE
            )
        }
    }

    private fun createRouteSidebar(): JComponent {
        val routeScroll = JBScrollPane(routeList).apply {
            border = BorderFactory.createEmptyBorder()
            viewportBorder = null
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS
            preferredSize = JBUI.size(0, 420)
            minimumSize = JBUI.size(260, 420)
        }
        val titlePanel = panel {
            row {
                val title = JBLabel(message("config.http.routes.section"))
                cell(title).align(AlignX.FILL)
            }
        }
        val actions = panel {
            row {
                button(message("config.http.route.add")) { addRoute() }
                    .applyToComponent {
                        icon = com.intellij.icons.AllIcons.General.Add
                        UiKit.applyToolbarButtonStyle(this)
                    }
                button(message("config.http.route.delete")) { deleteRoute() }
                    .applyToComponent {
                        icon = com.intellij.icons.AllIcons.General.Remove
                        UiKit.applyToolbarButtonStyle(this)
                    }
            }
        }

        val topPanel = JBPanel<JBPanel<*>>(BorderLayout(0, 8)).apply {
            add(titlePanel, BorderLayout.NORTH)
            add(actions, BorderLayout.SOUTH)
        }

        return JBPanel<JBPanel<*>>(BorderLayout(0, 8)).apply {
            add(topPanel, BorderLayout.NORTH)
            add(routeScroll, BorderLayout.CENTER)
        }
    }

    private fun createRouteEditorContent(): JComponent {
        val container = JBPanel<JBPanel<*>>(BorderLayout(12, 0))
        val sidebarWrapper = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            preferredSize = JBUI.size(320, 0)
            add(createRouteSidebar(), BorderLayout.CENTER)
        }
        val detailWrapper = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(createRouteDetailPanel(), BorderLayout.CENTER)
        }
        container.add(sidebarWrapper, BorderLayout.WEST)
        container.add(detailWrapper, BorderLayout.CENTER)
        return container
    }

    private fun createRouteDetailPanel(): JComponent {
        routeTargetCard.removeAll()
        routeTargetCard.add(createProxyTargetPanel(), HttpRouteTargetType.PROXY.name)
        routeTargetCard.add(createStaticTargetPanel(), HttpRouteTargetType.STATIC.name)

        val detailsForm = panel {
            row(message("config.http.route.name") + ":") { cell(routeNameField).align(AlignX.FILL) }
            row(message("config.group.prefix") + ":") { cell(routePrefixField).align(AlignX.FILL) }
            row(message("config.http.route.targettype") + ":") { cell(routeTargetTypeComboBox) }
            row { cell(routeTargetCard).align(AlignX.FILL) }
            row(message("config.http.route.rewrite") + ":") { cell(routeRewriteTargetPathField).align(AlignX.FILL) }
            row {
                cell(routeStripPrefixCheckBox)
                cell(routeEnableMockCheckBox)
            }
        }
        val mockForm = panel {
            row { cell(routeMockHintLabel).align(AlignX.FILL) }
            row { cell(mockListCard).align(AlignX.FILL) }
            row {
                button(message("mockapi.add.button")) { addApi() }
                    .applyToComponent {
                        mockAddButton.text = text
                        mockAddButton.icon = com.intellij.icons.AllIcons.General.Add
                        icon = com.intellij.icons.AllIcons.General.Add
                        UiKit.applyToolbarButtonStyle(this)
                        UiKit.applyToolbarButtonStyle(mockAddButton)
                        mockAddButton.model = model
                    }
                button(message("mockapi.edit.button")) { editApi() }
                    .applyToComponent {
                        mockEditButton.text = text
                        mockEditButton.icon = com.intellij.icons.AllIcons.Actions.Edit
                        icon = com.intellij.icons.AllIcons.Actions.Edit
                        UiKit.applyToolbarButtonStyle(this)
                        UiKit.applyToolbarButtonStyle(mockEditButton)
                        mockEditButton.model = model
                    }
                button(message("mockapi.delete.button")) { deleteApi() }
                    .applyToComponent {
                        mockDeleteButton.text = text
                        mockDeleteButton.icon = com.intellij.icons.AllIcons.General.Remove
                        icon = com.intellij.icons.AllIcons.General.Remove
                        UiKit.applyToolbarButtonStyle(this)
                        UiKit.applyToolbarButtonStyle(mockDeleteButton)
                        mockDeleteButton.model = model
                    }
            }
        }

        return JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(createSectionCard(message("config.http.route.details.section"), detailsForm))
            add(Box.createVerticalStrut(JBUI.scale(12)))
            add(createSectionCard(message("config.http.mock.section"), mockForm))
        }
    }

    private fun createProxyTargetPanel(): JComponent = panel {
        row(message("config.group.baseurl") + ":") { cell(routeBaseUrlField).align(AlignX.FILL) }
        row(message("config.http.route.spa.fallback") + ":") { cell(routeSpaFallbackPathField).align(AlignX.FILL) }
    }

    private fun createStaticTargetPanel(): JComponent = panel {
        row(message("config.http.route.static.root") + ":") { cell(routeStaticRootField).align(AlignX.FILL) }
        row {
            UiKit.applySecondaryText(routeStaticRootHintLabel)
            cell(routeStaticRootHintLabel).align(AlignX.FILL)
        }
        row { cell(routeStaticSpaFallbackCheckBox) }
    }

    private fun createSectionCard(title: String, content: JComponent): JComponent {
        val titleLabel = JBLabel(title).apply {
            font = font.deriveFont(Font.BOLD, font.size2D + 1f)
        }
        val header = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            isOpaque = false
            add(titleLabel, BorderLayout.CENTER)
        }
        return JBPanel<JBPanel<*>>(BorderLayout(0, 10)).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor.border(), 1),
                JBUI.Borders.empty(12)
            )
            add(header, BorderLayout.NORTH)
            add(content, BorderLayout.CENTER)
            alignmentX = Component.LEFT_ALIGNMENT
        }
    }

    private fun attachListeners() {
        cookieField.document.onAnyChange(onChanged)
        routeNameField.document.onAnyChange { onRouteDetailChanged() }
        routePrefixField.document.onAnyChange { onRouteDetailChanged() }
        routeTargetTypeComboBox.addActionListener { onRouteDetailChanged() }
        routeBaseUrlField.document.onAnyChange { onRouteDetailChanged() }
        routeStaticRootField.textField.document.onAnyChange { onRouteDetailChanged() }
        routeStaticRootField.addActionListener { chooseStaticRoot() }
        routeRewriteTargetPathField.document.onAnyChange { onRouteDetailChanged() }
        routeSpaFallbackPathField.document.onAnyChange { onRouteDetailChanged() }
        routeStaticSpaFallbackCheckBox.addActionListener { onRouteDetailChanged() }
        routeStripPrefixCheckBox.addActionListener { onRouteDetailChanged() }
        routeEnableMockCheckBox.addActionListener {
            onRouteDetailChanged()
            updateMockAreaState()
        }
        routeList.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) handleRouteSelectionChanged()
        }
        mockTable.selectionModel.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) updateMockActionButtons()
        }
        mockTableModel.addTableModelListener { event ->
            if (syncingMockTable) return@addTableModelListener
            val row = event.firstRow
            val column = event.column
            if (row >= 0 && (column == 0 || column == javax.swing.event.TableModelEvent.ALL_COLUMNS)) {
                val route = currentRoute() ?: return@addTableModelListener
                val api = route.mockApis.getOrNull(row) ?: return@addTableModelListener
                api.enabled = (mockTableModel.getValueAt(row, 0) as? Boolean) ?: false
                routeList.repaint()
                onChanged()
            }
        }
    }

    private fun handleRouteSelectionChanged() {
        val newIndex = routeList.selectedIndex
        if (newIndex < 0 || newIndex >= workingRoutes.size) return
        if (newIndex == selectedRouteIndex) return
        commitCurrentRoute()
        selectedRouteIndex = newIndex
        updateRouteDetails()
        updateMockAreaState()
    }

    private fun onRouteDetailChanged() {
        if (syncingRouteDetails) return
        commitCurrentRoute()
        reloadRouteList()
        restoreSelection()
        updateTargetTypeUi()
        updateMockAreaState()
        onChanged()
    }

    private fun commitCurrentRoute() {
        if (selectedRouteIndex !in workingRoutes.indices) return
        val route = workingRoutes[selectedRouteIndex]
        route.name = routeNameField.text.trim().ifEmpty { "API" }
        route.pathPrefix = routePrefixField.text.trim().ifEmpty { "/" }
        route.targetType = selectedTargetType()
        route.targetBaseUrl = routeBaseUrlField.text.trim().ifEmpty { "http://localhost:8080" }
        route.staticRoot = routeStaticRootField.text.trim()
        route.rewriteTargetPath = routeRewriteTargetPathField.text.trim()
        route.spaFallbackPath = routeSpaFallbackPathField.text.trim()
        route.spaFallback = routeStaticSpaFallbackCheckBox.isSelected
        route.stripPrefix = routeStripPrefixCheckBox.isSelected
        route.enableMock = routeEnableMockCheckBox.isSelected
        routeList.repaint()
    }

    private fun updateRouteDetails() {
        syncingRouteDetails = true
        try {
            val route = currentRoute()
            if (route == null) {
                routeNameField.text = ""
                routePrefixField.text = ""
                routeTargetTypeComboBox.selectedItem = HttpRouteTargetType.PROXY
                routeBaseUrlField.text = ""
                routeStaticRootField.text = ""
                routeRewriteTargetPathField.text = ""
                routeSpaFallbackPathField.text = ""
                routeStaticSpaFallbackCheckBox.isSelected = false
                routeStripPrefixCheckBox.isSelected = false
                routeEnableMockCheckBox.isSelected = false
            } else {
                routeNameField.text = route.name
                routePrefixField.text = route.pathPrefix
                routeTargetTypeComboBox.selectedItem = route.targetType
                routeBaseUrlField.text = route.targetBaseUrl
                routeStaticRootField.text = route.staticRoot
                routeRewriteTargetPathField.text = route.rewriteTargetPath
                routeSpaFallbackPathField.text = route.spaFallbackPath
                routeStaticSpaFallbackCheckBox.isSelected = route.spaFallback
                routeStripPrefixCheckBox.isSelected = route.stripPrefix
                routeEnableMockCheckBox.isSelected = route.enableMock
            }
        } finally {
            syncingRouteDetails = false
        }
        updateTargetTypeUi()
        updateMockTable()
    }

    private fun updateTargetTypeUi() {
        val targetType = selectedTargetType()
        (routeTargetCard.layout as CardLayout).show(routeTargetCard, targetType.name)
        updateStaticRootHint()
        routeTargetCard.revalidate()
        routeTargetCard.repaint()
    }

    private fun selectedTargetType(): HttpRouteTargetType =
        routeTargetTypeComboBox.selectedItem as? HttpRouteTargetType ?: HttpRouteTargetType.PROXY

    private fun chooseStaticRoot() {
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
            .withTitle(message("config.http.route.static.root.choose.title"))
            .withDescription(message("config.http.route.static.root.choose.desc"))
        val current = resolveStaticRootForUi(routeStaticRootField.text.trim())
        val selected = FileChooser.chooseFile(descriptor, project, current) ?: return
        routeStaticRootField.text = toStoredStaticRoot(selected.path)
        onRouteDetailChanged()
    }

    private fun resolveStaticRootForUi(value: String): com.intellij.openapi.vfs.VirtualFile? {
        val path = runCatching {
            val candidate = if (value.isBlank()) {
                projectRootPath()
            } else {
                val configured = Paths.get(value)
                if (configured.isAbsolute) configured else projectRootPath()?.resolve(configured)
            }
            candidate?.toAbsolutePath()?.normalize()?.toFile()
        }.getOrNull() ?: projectRootPath()?.toFile()
        return path?.let { LocalFileSystem.getInstance().findFileByIoFile(it) }
    }

    private fun toStoredStaticRoot(selectedPath: String): String {
        val selected = runCatching { Paths.get(selectedPath).toAbsolutePath().normalize() }.getOrNull()
            ?: return selectedPath
        val projectRoot = projectRootPath() ?: return selected.toString()
        return if (selected.startsWith(projectRoot)) {
            val relative = projectRoot.relativize(selected).toString().replace(File.separatorChar, '/')
            relative.ifBlank { "." }
        } else {
            selected.toString()
        }
    }

    private fun updateStaticRootHint() {
        val value = routeStaticRootField.text.trim()
        routeStaticRootHintLabel.text = when {
            selectedTargetType() != HttpRouteTargetType.STATIC -> ""
            value.isBlank() -> message("config.http.route.static.root.empty")
            isProjectExternalPath(value) -> message("config.http.route.static.root.external.warning")
            else -> message("config.http.route.static.root.hint")
        }
    }

    private fun isProjectExternalPath(value: String): Boolean {
        val projectRoot = projectRootPath() ?: return false
        val selected = runCatching {
            val configured = Paths.get(value)
            val resolved = if (configured.isAbsolute) configured else projectRoot.resolve(configured)
            resolved.toAbsolutePath().normalize()
        }.getOrNull() ?: return false
        return !selected.startsWith(projectRoot)
    }

    private fun projectRootPath(): Path? =
        project.basePath?.takeIf { it.isNotBlank() }?.let { Paths.get(it).toAbsolutePath().normalize() }

    private fun reloadRouteList() {
        routeListModel.removeAllElements()
        workingRoutes.forEach { routeListModel.addElement(it) }
        routeList.revalidate()
        routeList.repaint()
    }

    private fun updateMockTable() {
        syncingMockTable = true
        try {
            mockTableModel.rowCount = 0
            currentRoute()?.mockApis?.forEach { api ->
                mockTableModel.addRow(arrayOf<Any>(api.enabled, api.path, api.method, api.statusCode.toString(), api.delay.toString()))
            }
        } finally {
            syncingMockTable = false
        }
        val initialMockRow = pendingInitialMockIndex?.takeIf { it in 0 until mockTableModel.rowCount }
        if (initialMockRow != null) {
            mockTable.setRowSelectionInterval(initialMockRow, initialMockRow)
            mockTable.scrollRectToVisible(mockTable.getCellRect(initialMockRow, 0, true))
            pendingInitialMockIndex = null
        } else if (mockTableModel.rowCount > 0) {
            mockTable.clearSelection()
        }
        updateMockActionButtons()
    }

    private fun updateMockAreaState() {
        val route = currentRoute()
        val enabled = route?.enableMock == true
        routeMockHintLabel.isEnabled = enabled
        setComponentEnabled(mockTable, enabled)
        mockTable.isEnabled = enabled
        mockAddButton.isEnabled = enabled
        val card = mockListCard.layout as CardLayout
        when {
            !enabled -> {
                routeMockHintLabel.text = message("config.http.mock.disabled.desc")
                card.show(mockListCard, "disabled")
            }
            route.mockApis.isEmpty() -> {
                routeMockHintLabel.text = message("config.http.mock.help")
                card.show(mockListCard, "empty")
            }
            else -> {
                routeMockHintLabel.text = message("config.http.mock.help")
                card.show(mockListCard, "table")
            }
        }
        updateMockActionButtons()
    }

    private fun updateMockActionButtons() {
        val route = currentRoute()
        val mockEnabled = route?.enableMock == true
        val hasSelection = mockTable.selectedRow >= 0
        mockEditButton.isEnabled = mockEnabled && hasSelection
        mockDeleteButton.isEnabled = mockEnabled && hasSelection
    }

    private fun addRoute() {
        commitCurrentRoute()
        workingRoutes.add(defaultRoute(index = workingRoutes.size))
        reloadRouteList()
        selectedRouteIndex = workingRoutes.lastIndex
        restoreSelection()
        updateRouteDetails()
        onChanged()
    }

    private fun deleteRoute() {
        if (workingRoutes.size <= 1) {
            JOptionPane.showMessageDialog(
                routeList,
                message("config.http.route.delete.atleastone"),
                message("config.message.info"),
                JOptionPane.WARNING_MESSAGE
            )
            return
        }
        val row = routeList.selectedIndex
        if (row < 0) {
            JOptionPane.showMessageDialog(
                routeList,
                message("config.http.route.select.first"),
                message("config.message.info"),
                JOptionPane.INFORMATION_MESSAGE
            )
            return
        }
        workingRoutes.removeAt(row)
        reloadRouteList()
        selectedRouteIndex = row.coerceAtMost(workingRoutes.lastIndex)
        restoreSelection()
        updateRouteDetails()
        updateMockAreaState()
        onChanged()
    }

    private fun addApi() {
        val route = currentRoute() ?: return
        if (!route.enableMock) return
        val dialog = MockApiDialog(project, null)
        if (dialog.showAndGet()) {
            route.mockApis.add(dialog.getMockApiConfig())
            updateMockTable()
            updateMockAreaState()
            routeList.repaint()
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
            updateMockAreaState()
            routeList.repaint()
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
        val result = Messages.showYesNoDialog(
            project,
            message("mockapi.delete.confirm"),
            message("config.message.confirm.title"),
            Messages.getQuestionIcon()
        )
        if (result == Messages.YES) {
            route.mockApis.removeAt(row)
            updateMockTable()
            updateMockAreaState()
            routeList.repaint()
            onChanged()
        }
    }

    private fun ensureSelection() {
        if (workingRoutes.isEmpty()) workingRoutes.add(defaultRoute())
        selectedRouteIndex = initialRouteIndex?.coerceIn(0, workingRoutes.lastIndex)
            ?: selectedRouteIndex.takeIf { it in workingRoutes.indices }
            ?: 0
        restoreSelection()
    }

    private fun restoreSelection() {
        if (selectedRouteIndex in 0 until routeListModel.size()) {
            routeList.selectedIndex = selectedRouteIndex
            routeList.ensureIndexIsVisible(selectedRouteIndex)
        }
    }

    private fun currentRoute(): HttpRoute? = workingRoutes.getOrNull(selectedRouteIndex)

    private fun initialRoutes(): MutableList<HttpRoute> {
        val source = if (config.routes.isNotEmpty()) config.routes else mutableListOf(defaultRoute())
        return source.map { copyRoute(it) }.toMutableList()
    }

    private fun defaultRoute(index: Int = 0): HttpRoute {
        val prefix = if (index == 0) "/api" else "/api$index"
        return HttpRoute(
            id = UUID.randomUUID().toString(),
            name = if (index == 0) "API" else "API ${index + 1}",
            pathPrefix = prefix,
            targetBaseUrl = "http://localhost:8080",
            stripPrefix = true,
            enableMock = true,
            mockApis = mutableListOf()
        )
    }

    private fun copyRoute(route: HttpRoute): HttpRoute =
        route.copy(mockApis = route.mockApis.map { it.copy() }.toMutableList())

    private fun setComponentEnabled(component: Component, enabled: Boolean) {
        component.isEnabled = enabled
        if (component is java.awt.Container) {
            component.components.forEach { child -> setComponentEnabled(child, enabled) }
        }
    }

    private inner class RouteListRenderer : JPanel(BorderLayout(0, 6)), ListCellRenderer<HttpRoute> {
        private val titleLabel = JBLabel()
        private val subtitleLabel = JBLabel()
        private val badgeLabel = JBLabel()

        init {
            isOpaque = true
            border = JBUI.Borders.empty(8, 10)
            val textPanel = JPanel().apply {
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                titleLabel.alignmentX = LEFT_ALIGNMENT
                subtitleLabel.alignmentX = LEFT_ALIGNMENT
                add(titleLabel)
                add(Box.createVerticalStrut(JBUI.scale(4)))
                add(subtitleLabel)
            }
            val footer = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                isOpaque = false
                add(badgeLabel)
            }
            add(textPanel, BorderLayout.NORTH)
            add(footer, BorderLayout.SOUTH)
        }

        override fun getListCellRendererComponent(
            list: JList<out HttpRoute>?,
            value: HttpRoute?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            val route = value ?: return this
            titleLabel.text = route.name
            titleLabel.font = titleLabel.font.deriveFont(Font.BOLD)
            subtitleLabel.text = "${route.pathPrefix} -> ${routeTargetSummary(route)}"
            subtitleLabel.foreground = if (isSelected) {
                JBColor(0x1E4F91, 0xDCE8FF)
            } else {
                JBColor.GRAY
            }

            val badgeTone = if (route.enableMock) UiKit.StatusTone.GREEN else UiKit.StatusTone.GRAY
            badgeLabel.text = if (route.enableMock) message("config.http.route.badge.on") else message("config.http.route.badge.off")
            badgeLabel.icon = UiKit.statusDotIcon(badgeTone)
            badgeLabel.foreground = if (route.enableMock) JBColor(0x137333, 0x8FD18B) else JBColor.GRAY

            background = if (isSelected) {
                JBColor(0xEAF3FF, 0x2C4369)
            } else {
                JBColor.background()
            }
            titleLabel.foreground = if (isSelected) JBColor(0x163E73, 0xEAF2FF) else JBColor.foreground()
            val sideColor = if (isSelected) JBColor(0x8AB4FF, 0x78A9FF) else JBColor.background()
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, JBUI.scale(3), 0, 0, sideColor),
                JBUI.Borders.empty(8, 10)
            )
            return this
        }
    }

    private fun routeTargetSummary(route: HttpRoute): String =
        if (route.targetType == HttpRouteTargetType.STATIC) {
            route.staticRoot.ifBlank { message("toolwindow.notset") }
        } else {
            route.targetBaseUrl
        }
}
