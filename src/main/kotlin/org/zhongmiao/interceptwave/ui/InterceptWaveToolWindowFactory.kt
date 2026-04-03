package org.zhongmiao.interceptwave.ui

import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.dsl.builder.panel
import org.zhongmiao.interceptwave.InterceptWaveBundle.message
import org.zhongmiao.interceptwave.events.*
import org.zhongmiao.interceptwave.services.ConfigService
import org.zhongmiao.interceptwave.services.ConsoleService
import org.zhongmiao.interceptwave.services.MockServerService
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

@Suppress("unused")
class InterceptWaveToolWindow(private val project: Project) {

    private val mockServerService = project.service<MockServerService>()
    private val consoleService = project.service<ConsoleService>()
    private val configService = project.service<ConfigService>()

    private val tabbedPane = JBTabbedPane()
    private val tabPanels = mutableMapOf<String, ProxyGroupTabPanel>()

    private lateinit var startAllButton: javax.swing.JButton
    private lateinit var stopAllButton: javax.swing.JButton

    private val enabledSummaryLabel = JBLabel()
    private val runningSummaryLabel = JBLabel()

    init {
        setupTabs()
        project.messageBus.connect().subscribe(MOCK_SERVER_TOPIC, MockServerEventListener { event ->
            SwingUtilities.invokeLater {
                when (event) {
                    is ServerStarting -> updateGlobalButtonStates()
                    is ServerStarted -> {
                        tabPanels[event.configId]?.updateStatus(true, mockServerService.getServerUrl(event.configId))
                        updateGlobalButtonStates()
                    }
                    is ServerStopped -> {
                        tabPanels[event.configId]?.updateStatus(false, null)
                        updateGlobalButtonStates()
                    }
                    is AllServersStarted, is AllServersStopped, is AllServersStarting, is ServerStartFailed -> refreshAllTabs()
                    is ErrorOccurred,
                    is RequestReceived,
                    is Forwarded,
                    is MockMatched,
                    is ForwardingTo,
                    is MatchedPath,
                    is WebSocketMessageIn,
                    is WebSocketMessageOut,
                    is WebSocketMockPushed -> Unit
                    is WebSocketClosed,
                    is WebSocketConnected,
                    is WebSocketConnecting,
                    is WebSocketError -> {
                        event.configId?.let { tabPanels[it]?.handleWsEvent(event) }
                    }
                }
            }
        })
    }

    @Suppress("unused")
    fun getContent(): JComponent {
        val rootPanel = JBPanel<JBPanel<*>>(BorderLayout(8, 8))
        rootPanel.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)

        val headerPanel = JPanel(BorderLayout(8, 8))
        headerPanel.add(createSummaryPanel(), BorderLayout.CENTER)
        headerPanel.add(createGlobalActions(), BorderLayout.EAST)

        rootPanel.add(headerPanel, BorderLayout.NORTH)
        rootPanel.add(tabbedPane, BorderLayout.CENTER)

        updateGlobalButtonStates()
        updateSummaryLabels()
        return rootPanel
    }

    fun openConfigFile() {
        val configIoFile = configService.ensureConfigFile()
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(configIoFile)
        if (virtualFile == null) {
            notify(
                message("toolwindow.action.open.config.failed.title"),
                message("toolwindow.action.open.config.failed.message", configIoFile.absolutePath),
                NotificationType.ERROR
            )
            return
        }
        FileEditorManager.getInstance(project).openFile(virtualFile, true)
    }

    fun reloadConfigFromDisk() {
        // Pick up edits made in the IDE editor before reading config.json from disk.
        FileDocumentManager.getInstance().saveAllDocuments()

        val previouslyRunning = mockServerService.getRunningServers().map { it.first }.toSet()
        if (previouslyRunning.isNotEmpty()) {
            mockServerService.stopAllServers()
        }

        try {
            configService.reloadFromDisk()
            setupTabs()

            val restartableIds = configService.getEnabledProxyGroups().map { it.id }.toSet()
            val skippedIds = previouslyRunning.filterNot { it in restartableIds }
            previouslyRunning
                .filter { it in restartableIds }
                .forEach { id -> runCatching { mockServerService.startServer(id) } }

            refreshAllTabs()
            consoleService.printInfo(message("toolwindow.action.reload.config.success"))
            notify(
                message("toolwindow.action.reload.config.title"),
                if (skippedIds.isEmpty()) {
                    message("toolwindow.action.reload.config.success")
                } else {
                    message("toolwindow.action.reload.config.partial", skippedIds.size)
                },
                NotificationType.INFORMATION
            )
        } catch (e: Exception) {
            thisLogger().warn("Failed to reload config from disk", e)
            previouslyRunning.forEach { id -> runCatching { mockServerService.startServer(id) } }
            refreshAllTabs()
            notify(
                message("toolwindow.action.reload.config.failed.title"),
                message("toolwindow.action.reload.config.failed.message", e.message ?: e.javaClass.simpleName),
                NotificationType.ERROR
            )
        }
    }

    private fun createSummaryPanel(): JComponent {
        UiKit.applySecondaryText(enabledSummaryLabel)
        UiKit.applySecondaryText(runningSummaryLabel)
        return panel {
            row { cell(enabledSummaryLabel) }
            row { cell(runningSummaryLabel) }
        }
    }

    private fun createGlobalActions(): JComponent = panel {
        row {
            button(message("toolwindow.button.startall")) {
                mockServerService.startAllServers()
                refreshAllTabs()
            }.applyToComponent {
                icon = AllIcons.Actions.RunAll
                isFocusPainted = false
                isFocusable = false
                startAllButton = this
            }
            button(message("toolwindow.button.stopall")) {
                consoleService.terminateConsoleProcess()
                refreshAllTabs()
            }.applyToComponent {
                icon = AllIcons.Debugger.MuteBreakpoints
                isFocusPainted = false
                isFocusable = false
                stopAllButton = this
            }
            button(message("toolwindow.button.config")) {
                val selected = tabbedPane.selectedIndex
                val maxGroupIndex = (tabbedPane.tabCount - 2).coerceAtLeast(0)
                val initialIndex = if (selected in 0..maxGroupIndex) selected else 0
                openConfigDialog(initialSelectedIndex = initialIndex, autoAddNew = false)
            }.applyToComponent {
                icon = AllIcons.General.Settings
                isFocusPainted = false
                isFocusable = false
            }
        }
    }

    private fun updateGlobalButtonStates() {
        val proxyGroups = configService.getAllProxyGroups()
        val enabledGroups = proxyGroups.filter { it.enabled }

        if (enabledGroups.isEmpty()) {
            startAllButton.isEnabled = false
            stopAllButton.isEnabled = false
            updateSummaryLabels()
            return
        }

        val runningServerIds = mockServerService.getRunningServers().map { it.first }.toSet()
        val allEnabledRunning = enabledGroups.all { config -> config.id in runningServerIds }
        val allEnabledStopped = enabledGroups.none { config -> config.id in runningServerIds }

        startAllButton.isEnabled = !allEnabledRunning
        stopAllButton.isEnabled = !allEnabledStopped
        updateSummaryLabels()
    }

    private fun updateSummaryLabels() {
        val proxyGroups = configService.getAllProxyGroups()
        val enabledCount = proxyGroups.count { it.enabled }
        val runningCount = mockServerService.getRunningServers().size
        enabledSummaryLabel.text = message("toolwindow.summary.enabled", enabledCount)
        runningSummaryLabel.text = message("toolwindow.summary.running", runningCount)
    }

    private fun setupTabs() {
        tabbedPane.changeListeners.forEach { listener -> tabbedPane.removeChangeListener(listener) }
        tabbedPane.removeAll()
        tabPanels.clear()

        val proxyGroups = configService.getAllProxyGroups()
        if (proxyGroups.isEmpty()) {
            val content = panel {
                row {
                    val label = JBLabel(message("toolwindow.empty.hint"))
                    label.foreground = JBColor.GRAY
                    cell(label)
                }
            }
            tabbedPane.addTab(message("toolwindow.empty.tab"), content)
        } else {
            proxyGroups.forEach { config ->
                val tabPanel = ProxyGroupTabPanel(
                    project,
                    config.id,
                    config.name,
                    config.port,
                    config.enabled,
                    onStatusChanged = { updateGlobalButtonStates() },
                    onEditRouteRequested = { routeIndex ->
                        openConfigDialog(
                            initialSelectedIndex = indexOfConfig(config.id),
                            initialRouteIndex = routeIndex,
                            initialMockIndex = null,
                            autoAddNew = false
                        )
                    },
                    onEditMockRequested = { routeIndex, mockIndex ->
                        openConfigDialog(
                            initialSelectedIndex = indexOfConfig(config.id),
                            initialRouteIndex = routeIndex,
                            initialMockIndex = mockIndex,
                            autoAddNew = false
                        )
                    },
                    onEditWsRuleRequested = { wsRuleIndex ->
                        openConfigDialog(
                            initialSelectedIndex = indexOfConfig(config.id),
                            initialWsRuleIndex = wsRuleIndex,
                            autoAddNew = false
                        )
                    }
                )
                tabPanels[config.id] = tabPanel

                tabbedPane.addTab(config.name, tabPanel.getPanel())
            }

            val addContent = panel {
                row {
                    val addLabel = JBLabel(message("toolwindow.add.hint"))
                    addLabel.foreground = JBColor.GRAY
                    cell(addLabel)
                }
            }
            tabbedPane.addTab(null, AllIcons.General.Add, addContent, message("toolwindow.add.hint"))
            tabbedPane.addChangeListener {
                if (tabbedPane.selectedIndex == tabbedPane.tabCount - 1) {
                    openConfigDialog(initialSelectedIndex = null, autoAddNew = true)
                    if (tabbedPane.tabCount > 1) {
                        tabbedPane.selectedIndex = tabbedPane.tabCount - 2
                    }
                }
                updateSummaryLabels()
            }
        }
        updateSummaryLabels()
    }

    private fun refreshAllTabs() {
        tabPanels.forEach { (configId, panel) ->
            val isRunning = mockServerService.getServerStatus(configId)
            val url = mockServerService.getServerUrl(configId)
            panel.updateStatus(isRunning, url)
        }
        updateGlobalButtonStates()
        updateSummaryLabels()
    }

    private fun openConfigDialog(
        initialSelectedIndex: Int? = null,
        initialRouteIndex: Int? = null,
        initialMockIndex: Int? = null,
        initialWsRuleIndex: Int? = null,
        autoAddNew: Boolean = false
    ) {
        val previouslyRunning = mockServerService.getRunningServers().map { it.first }.toSet()
        if (previouslyRunning.isNotEmpty()) {
            mockServerService.stopAllServers()
        }

        val dialog = ConfigDialog(
            project,
            initialSelectedIndex = initialSelectedIndex,
            initialRouteIndex = initialRouteIndex,
            initialMockIndex = initialMockIndex,
            initialWsRuleIndex = initialWsRuleIndex,
            autoAddOnOpen = autoAddNew
        )
        val saved = dialog.showAndGet()

        if (saved) {
            setupTabs()
            if (autoAddNew && tabbedPane.tabCount > 1) {
                tabbedPane.selectedIndex = tabbedPane.tabCount - 2
            } else if (initialSelectedIndex != null) {
                val maxIdx = (tabbedPane.tabCount - 2).coerceAtLeast(0)
                tabbedPane.selectedIndex = initialSelectedIndex.coerceAtMost(maxIdx)
            }
        }

        if (previouslyRunning.isNotEmpty()) {
            previouslyRunning.forEach { id -> runCatching { mockServerService.startServer(id) } }
        }
        refreshAllTabs()
    }

    private fun notify(title: String, content: String, type: NotificationType) {
        runCatching {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("InterceptWave")
                .createNotification(title, content, type)
                .notify(project)
        }.onFailure {
            thisLogger().warn("Failed to show tool window notification: ${it.message}")
        }
    }

    private fun indexOfConfig(configId: String): Int? {
        val configs = configService.getAllProxyGroups()
        val index = configs.indexOfFirst { it.id == configId }
        return index.takeIf { it >= 0 }
    }
}

class ReloadConfigAction(private val panel: InterceptWaveToolWindow) : DumbAwareAction(
    message("toolwindow.action.reload.config.title"),
    message("toolwindow.action.reload.config.description"),
    AllIcons.Actions.Refresh
) {
    override fun actionPerformed(e: AnActionEvent) {
        panel.reloadConfigFromDisk()
    }
}

class OpenConfigFileAction(private val panel: InterceptWaveToolWindow) : DumbAwareAction(
    message("toolwindow.action.open.config.title"),
    message("toolwindow.action.open.config.description"),
    AllIcons.Actions.MenuOpen
) {
    override fun actionPerformed(e: AnActionEvent) {
        panel.openConfigFile()
    }
}
