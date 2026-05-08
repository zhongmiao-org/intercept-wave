package org.zhongmiao.interceptwave.ui

import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import org.zhongmiao.interceptwave.InterceptWaveBundle.message
import org.zhongmiao.interceptwave.events.MockServerEvent
import org.zhongmiao.interceptwave.events.WebSocketClosed
import org.zhongmiao.interceptwave.events.WebSocketConnected
import org.zhongmiao.interceptwave.events.WebSocketConnecting
import org.zhongmiao.interceptwave.events.WebSocketError
import org.zhongmiao.interceptwave.model.HttpRoute
import org.zhongmiao.interceptwave.model.HttpRouteTargetType
import org.zhongmiao.interceptwave.model.WsPushRule
import org.zhongmiao.interceptwave.services.ConfigService
import org.zhongmiao.interceptwave.services.ConsoleService
import org.zhongmiao.interceptwave.services.MockServerService
import org.zhongmiao.interceptwave.util.PathUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

/**
 * 单个配置组的标签页面板
 */
class ProxyGroupTabPanel(
    private val project: Project,
    private val configId: String,
    private val configName: String,
    private val port: Int,
    private val enabled: Boolean,
    private val onStatusChanged: () -> Unit = {},
    private val onEditRouteRequested: (Int) -> Unit = {},
    private val onEditMockRequested: (Int, Int) -> Unit = { _, _ -> },
    private val onEditWsRuleRequested: (Int) -> Unit = {}
) {
    private val mockServerService = project.service<MockServerService>()
    private val configService = project.service<ConfigService>()
    private val consoleService = project.service<ConsoleService>()

    private val configStateLabel = JBLabel()
    private val groupMetaLabel = JBLabel()
    private val runtimeDotLabel = JBLabel()
    private val urlValueLabel = JBLabel().apply {
        toolTipText = message("toolwindow.status.url.tooltip")
    }
    private val copyUrlButton = JButton().apply {
        icon = AllIcons.Actions.Copy
        text = ""
        UiKit.applyToolbarButtonStyle(this)
        toolTipText = message("toolwindow.button.copyurl.tooltip")
        addActionListener {
            val text = urlValueLabel.text
            if (text.isNotBlank() && text != message("toolwindow.status.url.placeholder")) {
                CopyPasteManager.getInstance().setContents(StringSelection(text))
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("InterceptWave")
                    .createNotification(message("toolwindow.message.copyurl.success"), NotificationType.INFORMATION)
                    .notify(project)
            }
        }
    }
    private val startButton = JButton(message("toolwindow.button.start"), AllIcons.Actions.Execute).apply {
        isFocusPainted = false
        isFocusable = false
    }
    private val stopButton = JButton(message("toolwindow.button.stop"), AllIcons.Actions.Suspend).apply {
        isFocusPainted = false
        isFocusable = false
    }

    private val wsCustomMsgArea = JTextArea()
    private val wsTargetCombo = ComboBox(arrayOf(
        message("wspanel.target.match"),
        message("wspanel.target.all"),
        message("wspanel.target.latest")
    ))
    private val wsConnectionStateLabel = JBLabel()
    private val wsClientCountLabel = JBLabel()
    private val wsLastPathLabel = JBLabel()
    private val wsModeLabel = JBLabel()
    private val wsModeDetailLabel = JBLabel()
    private val wsUpstreamStateLabel = JBLabel()
    private val expandedRouteIds = linkedSetOf<String>()
    private var wsActiveConnectionCount = 0
    private var wsLatestPath: String? = null
    private var wsUpstreamTone = UiKit.StatusTone.GRAY
    private var wsUpstreamText: String = message("toolwindow.ws.connection.idle")

    init {
        startButton.addActionListener { startServer() }
        stopButton.addActionListener { stopServer() }
        updateStatus(mockServerService.getServerStatus(configId), mockServerService.getServerUrl(configId))
        refreshWsConnectionSummary()
    }

    fun getPanel(): JPanel {
        val panel = JPanel(BorderLayout(8, 8))
        panel.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)

        panel.add(createStatusPanel(), BorderLayout.NORTH)

        val center = JPanel(BorderLayout(8, 8))
        val cfg = configService.getProxyGroup(configId)
        if (cfg == null) {
            center.add(
                UiKit.createEmptyState(
                    message("toolwindow.config.notfound"),
                    message("toolwindow.add.hint"),
                    AllIcons.General.Warning
                ),
                BorderLayout.CENTER
            )
        } else {
            if (cfg.protocol == "WS" && cfg.wsManualPush) {
                center.add(createWsPushPanel(), BorderLayout.CENTER)
            } else {
                center.add(createInfoPanel(), BorderLayout.CENTER)
            }
        }
        val centerScroll = JBScrollPane(center).apply {
            border = null
            viewportBorder = null
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        }
        panel.add(centerScroll, BorderLayout.CENTER)
        return panel
    }

    private fun createStatusPanel(): JPanel {
        val cfg = configService.getProxyGroup(configId)
        val protocol = cfg?.protocol ?: "HTTP"
        groupMetaLabel.text = "$configName · $protocol · :$port"
        groupMetaLabel.font = groupMetaLabel.font.deriveFont(16f).deriveFont(java.awt.Font.BOLD)
        runtimeDotLabel.border = JBUI.Borders.emptyLeft(6)

        UiKit.applyInlineStatusText(
            configStateLabel,
            if (enabled) message("toolwindow.status.enabled") else message("toolwindow.status.disabled"),
            if (enabled) UiKit.StatusTone.GREEN else UiKit.StatusTone.YELLOW
        )

        return panel {
            group(message("toolwindow.status.title")) {
                row {
                    cell(groupMetaLabel)
                    cell(runtimeDotLabel)
                }
                row {
                    cell(configStateLabel)
                }
                row(message("toolwindow.label.url")) {
                    cell(urlValueLabel).align(AlignX.FILL)
                    cell(copyUrlButton)
                }
                row {
                    cell(startButton)
                    cell(stopButton)
                }
            }
        }
    }

    private fun createInfoPanel(): JPanel {
        val cfg = configService.getProxyGroup(configId)
            ?: return JBPanel<JBPanel<*>>(BorderLayout()).apply {
                add(UiKit.createEmptyState(message("toolwindow.config.notfound"), message("toolwindow.add.hint")), BorderLayout.CENTER)
            }

        return if (cfg.protocol == "WS") {
            createWsInfoPanel(cfg)
        } else {
            createHttpInfoPanel(cfg.routes)
        }
    }

    private fun createHttpInfoPanel(routes: List<HttpRoute>): JPanel {
        if (expandedRouteIds.isEmpty()) {
            routes.mapNotNull(HttpRoute::id).forEach(expandedRouteIds::add)
        }
        val routeContent = if (routes.isEmpty()) {
            UiKit.createEmptyState(
                message("toolwindow.config.routes.empty.title"),
                message("toolwindow.config.routes.empty.desc")
            )
        } else {
            createRouteTreeList(routes)
        }

        return panel {
            group(message("toolwindow.config.routes.title")) {
                row {
                    val hint = JBLabel(message("toolwindow.config.routes.help"))
                    UiKit.applySecondaryText(hint)
                    cell(hint).align(AlignX.FILL)
                }
                row { cell(routeContent).align(AlignX.FILL) }
            }
        }
    }

    private fun createWsInfoPanel(cfg: org.zhongmiao.interceptwave.model.ProxyConfig): JPanel {
        val rulesContent = if (cfg.wsPushRules.isEmpty()) {
            UiKit.createEmptyState(
                message("toolwindow.config.wsrules.empty.title"),
                message("toolwindow.config.wsrules.empty.desc")
            )
        } else {
            createWsRuleCards(cfg.wsPushRules)
        }

        return panel {
            group(message("toolwindow.config.wsrules.title")) {
                row {
                    val hint = JBLabel(message("toolwindow.config.wsrules.help"))
                    UiKit.applySecondaryText(hint)
                    cell(hint).align(AlignX.FILL)
                }
                row { cell(rulesContent).align(AlignX.FILL) }
            }
        }
    }

    private fun createWsRuleCards(rules: List<WsPushRule>): JComponent {
        val content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }
        rules.forEachIndexed { index, rule ->
            content.add(createWsRuleCard(rule, index))
            if (index < rules.lastIndex) {
                content.add(Box.createVerticalStrut(JBUI.scale(8)))
            }
        }
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            isOpaque = false
            add(content, BorderLayout.NORTH)
        }
    }

    private fun createWsRuleCard(rule: WsPushRule, index: Int): JComponent {
        val title = JBLabel(org.zhongmiao.interceptwave.util.formatWsRuleMatcher(rule)).apply {
            font = font.deriveFont(java.awt.Font.BOLD)
        }
        val subtitle = JBLabel(buildString {
            append(rule.mode.uppercase())
            append(" · ")
            append(rule.direction.uppercase())
            if (rule.mode.equals("periodic", true)) {
                append(" · ")
                append(message("toolwindow.ws.periodic.summary", rule.periodSec))
            } else if (rule.mode.equals("timeline", true)) {
                append(" · ")
                append(message("toolwindow.ws.timeline.summary", rule.timeline.size))
            }
        }).apply {
            UiKit.applyMutedText(this)
        }
        val meta = createMetaRow(
            createStatusChip(
                if (rule.enabled) message("wsrule.enabled") else message("toolwindow.status.disabled"),
                if (rule.enabled) JBColor(0x137333, 0x8FD18B) else JBColor(0xC5221F, 0xE06C75)
            ),
            createDirectionBadge(rule.direction),
            createForwardingStatusChip(rule.intercept)
        )
        val editButton = JButton(message("wsrule.edit.button"), AllIcons.Actions.Edit).apply {
            UiKit.applyToolbarButtonStyle(this)
            addActionListener { onEditWsRuleRequested(index) }
        }

        return UiKit.roundedPanel(JBColor(0xF7F9FC, 0x2B2F36)).apply {
            layout = BorderLayout(8, 0)
            border = BorderFactory.createCompoundBorder(
                UiKit.roundedLineBorder(JBColor(0xD8E0EB, 0x444B57), 1),
                JBUI.Borders.empty(12)
            )
            add(
                JBPanel<JBPanel<*>>().apply {
                    isOpaque = false
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    title.alignmentX = java.awt.Component.LEFT_ALIGNMENT
                    subtitle.alignmentX = java.awt.Component.LEFT_ALIGNMENT
                    meta.alignmentX = java.awt.Component.LEFT_ALIGNMENT
                    add(title)
                    add(Box.createVerticalStrut(JBUI.scale(4)))
                    add(subtitle)
                    add(Box.createVerticalStrut(JBUI.scale(6)))
                    add(meta)
                },
                BorderLayout.CENTER
            )
            add(editButton, BorderLayout.EAST)
        }
    }

    private fun createRouteTreeList(routes: List<HttpRoute>): JComponent {
        val content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }

        fun render() {
            content.removeAll()
            routes.forEachIndexed { index, route ->
                content.add(createRouteTreeCard(route, index, ::render))
                if (index < routes.lastIndex) {
                    content.add(Box.createVerticalStrut(JBUI.scale(8)))
                }
            }
            content.revalidate()
            content.repaint()
        }

        render()
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            isOpaque = false
            add(content, BorderLayout.NORTH)
        }
    }

    private fun createRouteTreeCard(route: HttpRoute, routeIndex: Int, rerender: () -> Unit): JComponent {
        val expanded = expandedRouteIds.contains(route.id)
        val toggleButton = JButton().apply {
            icon = if (expanded) AllIcons.General.ArrowDown else AllIcons.General.ArrowRight
            text = ""
            UiKit.applyToolbarButtonStyle(this)
            addActionListener {
                if (expandedRouteIds.contains(route.id)) {
                    expandedRouteIds.remove(route.id)
                } else {
                    expandedRouteIds.add(route.id)
                }
                rerender()
            }
        }
        val routeTitle = JBLabel(route.name).apply {
            font = font.deriveFont(java.awt.Font.BOLD)
        }
        val routePath = JBLabel("${route.pathPrefix} -> ${routeTargetSummary(route)}").apply {
            UiKit.applyMutedText(this)
        }
        val routeMetaItems = mutableListOf<JComponent>(
            createBadgeLabel(
                if (route.enableMock) message("config.http.route.badge.on") else message("config.http.route.badge.off"),
                if (route.enableMock) JBColor(0x137333, 0x8FD18B) else JBColor(0xC5221F, 0xE06C75),
                if (route.enableMock) JBColor(0xEAF6EC, 0x233428) else JBColor(0xFCEAEA, 0x3A2528)
            )
        )
        if (route.stripPrefix) {
            routeMetaItems += createBadgeLabel(
                message("config.group.stripprefix"),
                JBColor(0x8A5A00, 0xD9A441),
                JBColor(0xFFF4D6, 0x3A3222)
            )
        }
        if (route.targetType == HttpRouteTargetType.STATIC) {
            routeMetaItems += createBadgeLabel(
                message("config.http.route.targettype.static"),
                JBColor(0x0B8043, 0x81C995),
                JBColor(0xE6F4EA, 0x233428)
            )
        }
        if (route.rewriteTargetPath.isNotBlank()) {
            routeMetaItems += createBadgeLabel(
                message("config.http.route.rewrite.badge", route.rewriteTargetPath),
                JBColor(0x1A73E8, 0x8AB4F8),
                JBColor(0xE8F0FE, 0x26364D)
            )
        }
        if (route.spaFallbackPath.isNotBlank()) {
            routeMetaItems += createBadgeLabel(
                message("config.http.route.spa.fallback.badge", route.spaFallbackPath),
                JBColor(0x6F42C1, 0xC9A7FF),
                JBColor(0xF1E8FF, 0x332947)
            )
        }
        if (route.targetType == HttpRouteTargetType.STATIC && route.spaFallback) {
            routeMetaItems += createBadgeLabel(
                message("config.http.route.static.spa.fallback.badge"),
                JBColor(0x6F42C1, 0xC9A7FF),
                JBColor(0xF1E8FF, 0x332947)
            )
        }
        val routeMeta = createMetaRow(*routeMetaItems.toTypedArray())
        val editRouteButton = JButton("", AllIcons.Actions.Edit).apply {
            UiKit.applyToolbarButtonStyle(this)
            toolTipText = message("mockapi.edit.button")
            addActionListener { onEditRouteRequested(routeIndex) }
        }

        val header = JBPanel<JBPanel<*>>(BorderLayout(8, 0)).apply {
            isOpaque = false
            val left = JBPanel<JBPanel<*>>(BorderLayout(8, 0)).apply {
                isOpaque = false
                add(toggleButton, BorderLayout.WEST)
                add(
                    JBPanel<JBPanel<*>>().apply {
                        isOpaque = false
                        layout = BoxLayout(this, BoxLayout.Y_AXIS)
                        routeTitle.alignmentX = java.awt.Component.LEFT_ALIGNMENT
                        routePath.alignmentX = java.awt.Component.LEFT_ALIGNMENT
                        routeMeta.alignmentX = java.awt.Component.LEFT_ALIGNMENT
                        add(routeTitle)
                        add(Box.createVerticalStrut(JBUI.scale(4)))
                        add(routePath)
                        add(Box.createVerticalStrut(JBUI.scale(6)))
                        add(routeMeta)
                    },
                    BorderLayout.CENTER
                )
            }
            add(left, BorderLayout.CENTER)
            add(editRouteButton, BorderLayout.EAST)
        }

        val body = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }
        if (expanded) {
            if (route.mockApis.isEmpty()) {
                val emptyLabel = JBLabel(message("toolwindow.config.mocks.empty.desc")).apply {
                    UiKit.applyMutedText(this)
                    border = JBUI.Borders.empty(4, 28, 0, 0)
                }
                body.add(emptyLabel)
            } else {
                route.mockApis.forEachIndexed { mockIndex, api ->
                    body.add(createMockChildRow(route, routeIndex, api, mockIndex))
                    if (mockIndex < route.mockApis.lastIndex) {
                        body.add(Box.createVerticalStrut(JBUI.scale(6)))
                    }
                }
            }
        }

        return UiKit.roundedPanel(JBColor(0xF7F9FC, 0x2B2F36)).apply {
            layout = BorderLayout(0, 10)
            border = BorderFactory.createCompoundBorder(
                UiKit.roundedLineBorder(JBColor(0xD8E0EB, 0x444B57), 1),
                JBUI.Borders.empty(12)
            )
            add(header, BorderLayout.NORTH)
            if (expanded) add(body, BorderLayout.CENTER)
        }
    }

    private fun createMockChildRow(route: HttpRoute, routeIndex: Int, api: org.zhongmiao.interceptwave.model.MockApiConfig, mockIndex: Int): JComponent {
        val joinedPath = buildString {
            append(route.pathPrefix)
            if (route.pathPrefix.endsWith("/") && api.path.startsWith("/")) {
                deleteCharAt(length - 1)
            }
            append(api.path)
        }
        val localUrl = "http://localhost:$port$joinedPath"
        val forwardPath = PathUtil.computeHttpForwardPath(route, joinedPath)
        val targetUrl = if (route.targetType == HttpRouteTargetType.STATIC) {
            route.staticRoot.trimEnd('/') + forwardPath
        } else {
            route.targetBaseUrl.trimEnd('/') + forwardPath
        }
        val titleArea = JTextArea().apply {
            text = "Mock Path  $joinedPath"
            isEditable = false
            isFocusable = false
            isOpaque = false
            border = null
            lineWrap = false
            wrapStyleWord = false
            foreground = JBColor.foreground()
            font = JBLabel().font.deriveFont(java.awt.Font.BOLD)
            toolTipText = joinedPath
        }
        val forwardHint = JTextArea().apply {
            text = "$localUrl\n-> $targetUrl"
            isEditable = false
            isFocusable = false
            lineWrap = true
            wrapStyleWord = true
            isOpaque = false
            border = null
            foreground = JBColor(0x5F6B7A, 0xA7B0BA)
            font = JBLabel().font.deriveFont((JBLabel().font.size2D - 0.5f).coerceAtLeast(11f))
            toolTipText = "$localUrl -> $targetUrl"
        }
        val leftMeta = JBPanel<JBPanel<*>>().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(
                createStatusChip(
                    if (api.enabled) message("config.http.route.badge.on") else message("config.http.route.badge.off"),
                    if (api.enabled) JBColor(0x137333, 0x8FD18B) else JBColor(0xC5221F, 0xE06C75)
                ).also { it.alignmentX = java.awt.Component.LEFT_ALIGNMENT }
            )
            add(Box.createVerticalStrut(JBUI.scale(8)))
            add(createMethodChip(api.method).also { it.alignmentX = java.awt.Component.LEFT_ALIGNMENT })
            add(Box.createVerticalStrut(JBUI.scale(8)))
            add(
                createBadgeLabel(
                    api.statusCode.toString(),
                    statusCodeColor(api.statusCode),
                    statusCodeBackground(api.statusCode)
                ).also { it.alignmentX = java.awt.Component.LEFT_ALIGNMENT }
            )
        }
        val delayBadge = createBadgeLabel(
            "${api.delay}ms",
            JBColor(0x5F6B7A, 0xA7B0BA),
            JBColor(0xEEF2F7, 0x2F3640)
        )
        val editButton = JButton("", AllIcons.Actions.Edit).apply {
            UiKit.applyToolbarButtonStyle(this)
            toolTipText = message("mockapi.edit.button")
            addActionListener { onEditMockRequested(routeIndex, mockIndex) }
        }

        return UiKit.roundedPanel(JBColor(0xFFFFFF, 0x313741)).apply {
            layout = BorderLayout(8, 0)
            border = BorderFactory.createCompoundBorder(
                UiKit.roundedLineBorder(JBColor(0xE3E8EF, 0x4A515E), 1),
                JBUI.Borders.empty(12, 16, 12, 12)
            )
            val contentPanel = JBPanel<JBPanel<*>>().apply {
                isOpaque = false
                layout = BorderLayout(16, 0)
                add(leftMeta, BorderLayout.WEST)
                add(
                    JBPanel<JBPanel<*>>().apply {
                        isOpaque = false
                        layout = BoxLayout(this, BoxLayout.Y_AXIS)
                        titleArea.alignmentX = java.awt.Component.LEFT_ALIGNMENT
                        forwardHint.alignmentX = java.awt.Component.LEFT_ALIGNMENT
                        add(titleArea)
                        add(Box.createVerticalStrut(JBUI.scale(8)))
                        add(forwardHint)
                    },
                    BorderLayout.CENTER
                )
            }
            add(
                JBPanel<JBPanel<*>>().apply {
                    isOpaque = false
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    add(contentPanel)
                },
                BorderLayout.CENTER
            )
            add(
                JBPanel<JBPanel<*>>().apply {
                    isOpaque = false
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    delayBadge.alignmentX = java.awt.Component.CENTER_ALIGNMENT
                    editButton.alignmentX = java.awt.Component.CENTER_ALIGNMENT
                    add(delayBadge)
                    add(Box.createVerticalStrut(JBUI.scale(12)))
                    add(editButton)
                    add(Box.createVerticalGlue())
                },
                BorderLayout.EAST
            )
        }
    }

    private fun routeTargetSummary(route: HttpRoute): String =
        if (route.targetType == HttpRouteTargetType.STATIC) {
            route.staticRoot.ifBlank { message("toolwindow.notset") }
        } else {
            route.targetBaseUrl
        }

    private fun createMetaRow(vararg items: JComponent): JComponent =
        JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0)).apply {
            isOpaque = false
            items.forEach { add(it) }
        }

    private fun methodColor(method: String): Color = when (method.uppercase()) {
        "ALL" -> JBColor(0x2563EB, 0x6CB6FF)
        "GET" -> JBColor(0x16A34A, 0x7EE787)
        "POST" -> JBColor(0xF59E0B, 0xE3B341)
        "PUT" -> JBColor(0x0891B2, 0x56D4DD)
        "PATCH" -> JBColor(0xDB2777, 0xFF7AB8)
        "DELETE" -> JBColor(0xDC2626, 0xFF7B72)
        else -> JBColor(0x5F6B7A, 0x9DA7B3)
    }

    private fun createMethodChip(method: String): JComponent {
        val color = methodColor(method)
        return JBLabel(method).apply {
            icon = UiKit.statusDotIcon(color)
            foreground = color
            border = JBUI.Borders.empty(2, 0)
        }
    }

    private fun createDirectionBadge(direction: String): JComponent {
        val normalized = direction.uppercase()
        val foreground = when (normalized) {
            "IN" -> JBColor(0x2563EB, 0x6CB6FF)
            "OUT" -> JBColor(0xDB2777, 0xFF7AB8)
            else -> JBColor(0x8A5A00, 0xD9A441)
        }
        val background = when (normalized) {
            "IN" -> JBColor(0xEAF3FF, 0x2B3A52)
            "OUT" -> JBColor(0xFDE7F3, 0x3F2936)
            else -> JBColor(0xFFF4D6, 0x3A3222)
        }
        return createBadgeLabel(normalized, foreground, background)
    }

    private fun createStatusChip(text: String, color: Color): JComponent {
        return JBLabel(text).apply {
            icon = UiKit.statusDotIcon(color)
            foreground = color
            border = JBUI.Borders.empty(2, 0)
        }
    }

    private fun createForwardingStatusChip(intercept: Boolean): JComponent {
        return createStatusChip(
            if (intercept) message("wsrule.intercept") else message("toolwindow.ws.forward.summary"),
            if (intercept) JBColor(0x8A5A00, 0xD9A441) else JBColor(0x2563EB, 0x78A9FF)
        )
    }

    private fun createBadgeLabel(text: String, foreground: Color, background: Color): JComponent {
        return UiKit.roundedBadge(text, foreground, background)
    }

    private fun statusCodeColor(statusCode: Int): Color = when (statusCode) {
        in 200..299 -> JBColor(0x137333, 0x8FD18B)
        in 300..399 -> JBColor(0x8A5A00, 0xD9A441)
        in 400..499 -> JBColor(0xC5221F, 0xFF7B72)
        in 500..599 -> JBColor(0xA142F4, 0xC58AF9)
        else -> JBColor(0x5F6B7A, 0xA7B0BA)
    }

    private fun statusCodeBackground(statusCode: Int): Color = when (statusCode) {
        in 200..299 -> JBColor(0xEAF6EC, 0x233428)
        in 300..399 -> JBColor(0xFFF4D6, 0x3A3222)
        in 400..499 -> JBColor(0xFCEAEA, 0x3A2528)
        in 500..599 -> JBColor(0xF4EAFE, 0x352446)
        else -> JBColor(0xEEF2F7, 0x2F3640)
    }

    private fun createWsPushPanel(): JPanel {
        wsTargetCombo.selectedIndex = 0

        val cfg = configService.getProxyGroup(configId)
        var selectedRuleIndex: Int
        val ruleCards = mutableListOf<JComponent>()
        val ruleRows = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }
        val rules = cfg?.wsPushRules ?: emptyList()

        fun updateSelectedRule(newIndex: Int) {
            if (newIndex !in rules.indices) return
            selectedRuleIndex = newIndex
            wsCustomMsgArea.text = rules[newIndex].message
            ruleCards.forEachIndexed { index, card ->
                card.border = BorderFactory.createCompoundBorder(
                    UiKit.roundedLineBorder(
                        if (index == selectedRuleIndex) JBColor(0x8AB4FF, 0x78A9FF) else JBColor(0xD8E0EB, 0x444B57),
                        1
                    ),
                    JBUI.Borders.empty(12)
                )
                if (card is JPanel) {
                    card.background = if (index == selectedRuleIndex) JBColor(0xEAF3FF, 0x2C4369) else JBColor(0xF7F9FC, 0x2B2F36)
                }
            }
        }

        rules.forEachIndexed { index, rule ->
            val card = UiKit.roundedPanel(JBColor(0xF7F9FC, 0x2B2F36)).apply {
                layout = BorderLayout(12, 0)
                border = BorderFactory.createCompoundBorder(
                    UiKit.roundedLineBorder(JBColor(0xD8E0EB, 0x444B57), 1),
                    JBUI.Borders.empty(12)
                )
            }
            val matcherLabel = JBLabel(org.zhongmiao.interceptwave.util.formatWsRuleMatcher(rule)).apply {
                font = font.deriveFont(java.awt.Font.BOLD)
            }
            val modeLine = createMetaRow(
                createBadgeLabel(rule.mode.uppercase(), JBColor(0x5B8CFF, 0x78A9FF), JBColor(0xEAF3FF, 0x2B3A52)),
                createDirectionBadge(rule.direction),
                createBadgeLabel(
                    when (rule.mode.lowercase()) {
                        "periodic" -> message("toolwindow.ws.periodic.summary", rule.periodSec)
                        "timeline" -> message("toolwindow.ws.timeline.summary", rule.timeline.size)
                        else -> message("toolwindow.ws.off.summary")
                    },
                    JBColor(0x5F6B7A, 0xA7B0BA),
                    JBColor(0xEEF2F7, 0x2F3640)
                )
            )
            val behaviorLine = createMetaRow(
                createStatusChip(
                    if (rule.enabled) message("wsrule.enabled") else message("toolwindow.status.disabled"),
                    if (rule.enabled) JBColor(0x137333, 0x8FD18B) else JBColor(0xC5221F, 0xE06C75)
                ),
                createForwardingStatusChip(rule.intercept)
            )
            val detailLine = createMetaRow(
                createBadgeLabel(
                    listOfNotNull(
                        rule.eventKey?.takeIf { it.isNotBlank() },
                        rule.eventValue?.takeIf { it.isNotBlank() }
                    ).joinToString(" = ").ifBlank { message("toolwindow.notset") },
                    JBColor(0x5F6B7A, 0xA7B0BA),
                    JBColor(0xEEF2F7, 0x2F3640)
                ),
                createBadgeLabel(
                    if (rule.onOpenFire) message("wsrule.onopen") else message("toolwindow.ws.onopen.off"),
                    if (rule.onOpenFire) JBColor(0x137333, 0x8FD18B) else JBColor(0x5F6B7A, 0xA7B0BA),
                    if (rule.onOpenFire) JBColor(0xEAF6EC, 0x233428) else JBColor(0xEEF2F7, 0x2F3640)
                )
            )
            val playButton = JButton("", AllIcons.Actions.Execute).apply {
                UiKit.applyToolbarButtonStyle(this)
                toolTipText = message("wspanel.send.selected.tooltip")
                addActionListener {
                    updateSelectedRule(index)
                    sendWsRule(rule)
                }
            }
            val editButton = JButton("", AllIcons.Actions.Edit).apply {
                UiKit.applyToolbarButtonStyle(this)
                toolTipText = message("wsrule.edit.button")
                addActionListener {
                    updateSelectedRule(index)
                    onEditWsRuleRequested(index)
                }
            }
            val textArea = JBPanel<JBPanel<*>>(BorderLayout(0, 6)).apply {
                isOpaque = false
                add(matcherLabel, BorderLayout.NORTH)
                add(
                    JBPanel<JBPanel<*>>().apply {
                        isOpaque = false
                        layout = BoxLayout(this, BoxLayout.Y_AXIS)
                        add(modeLine)
                        add(Box.createVerticalStrut(JBUI.scale(6)))
                        add(behaviorLine)
                        add(Box.createVerticalStrut(JBUI.scale(6)))
                        add(detailLine)
                    },
                    BorderLayout.CENTER
                )
            }
            card.add(textArea, BorderLayout.CENTER)
            card.add(
                JBPanel<JBPanel<*>>().apply {
                    isOpaque = false
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    add(Box.createVerticalGlue())
                    add(editButton)
                    add(Box.createVerticalStrut(JBUI.scale(8)))
                    add(playButton)
                    add(Box.createVerticalGlue())
                },
                BorderLayout.EAST
            )
            val selectListener = object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent?) {
                    updateSelectedRule(index)
                }
            }
            card.addMouseListener(selectListener)
            textArea.addMouseListener(selectListener)
            matcherLabel.addMouseListener(selectListener)
            ruleCards.add(card)
            ruleRows.add(card)
            if (index < rules.lastIndex) {
                ruleRows.add(Box.createVerticalStrut(JBUI.scale(6)))
            }
        }
        if (rules.isNotEmpty()) {
            wsCustomMsgArea.text = rules.first().message
            updateSelectedRule(0)
        }
        val rulesListContent: JComponent = if (rules.isEmpty()) {
            UiKit.createEmptyState(
                message("toolwindow.config.wsrules.empty.title"),
                message("toolwindow.config.wsrules.empty.desc")
            )
        } else {
            JBPanel<JBPanel<*>>(BorderLayout()).apply {
                isOpaque = false
                add(ruleRows, BorderLayout.NORTH)
            }
        }

        wsCustomMsgArea.lineWrap = true
        wsCustomMsgArea.wrapStyleWord = true
        wsCustomMsgArea.rows = 4

        return panel {
            row {
                cell(createWsConnectionSummary()).align(AlignX.FILL)
            }
            group(message("wspanel.title")) {
                row(message("wspanel.target")) { cell(wsTargetCombo).align(AlignX.FILL) }
                row { cell(rulesListContent).align(AlignX.FILL) }
                row { cell(JBScrollPane(wsCustomMsgArea)).align(AlignX.FILL) }
                row {
                    button(message("wspanel.send.custom")) { sendWsCustomMessage() }
                        .applyToComponent {
                            icon = AllIcons.Actions.Execute
                            UiKit.applyToolbarButtonStyle(this)
                            toolTipText = message("wspanel.send.custom.tooltip")
                        }
                    button(message("wspanel.clear")) { wsCustomMsgArea.text = "" }
                        .applyToComponent {
                            icon = AllIcons.Actions.GC
                            UiKit.applyToolbarButtonStyle(this)
                        }
                }
            }
        }
    }

    private fun createWsConnectionSummary(): JComponent {
        refreshWsConnectionSummary()
        val titleLabel = JBLabel(message("toolwindow.ws.connection.title")).apply {
            font = font.deriveFont(java.awt.Font.BOLD)
            alignmentX = java.awt.Component.LEFT_ALIGNMENT
        }
        val modeRow = createMetaRow(wsModeLabel, wsConnectionStateLabel, wsClientCountLabel).apply {
            alignmentX = java.awt.Component.LEFT_ALIGNMENT
        }
        val upstreamRow = createMetaRow(wsUpstreamStateLabel, wsLastPathLabel).apply {
            alignmentX = java.awt.Component.LEFT_ALIGNMENT
        }
        wsModeDetailLabel.alignmentX = java.awt.Component.LEFT_ALIGNMENT
        return UiKit.roundedPanel(JBColor(0xF7F9FC, 0x2B2F36)).apply {
            layout = BorderLayout(12, 0)
            border = BorderFactory.createCompoundBorder(
                UiKit.roundedLineBorder(JBColor(0xD8E0EB, 0x444B57), 1),
                JBUI.Borders.empty(10, 12)
            )
            add(
                JBPanel<JBPanel<*>>().apply {
                    isOpaque = false
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    add(titleLabel)
                    add(Box.createVerticalStrut(JBUI.scale(6)))
                    add(modeRow)
                    add(Box.createVerticalStrut(JBUI.scale(4)))
                    add(upstreamRow)
                    add(Box.createVerticalStrut(JBUI.scale(4)))
                    add(wsModeDetailLabel)
                },
                BorderLayout.CENTER
            )
        }
    }

    private fun sendWsRule(rule: WsPushRule) {
        val msgTrimmed = rule.message.trim()
        if (msgTrimmed.isEmpty()) {
            JOptionPane.showMessageDialog(null, message("wspanel.send.rule.empty.warn"), message("config.message.info"), JOptionPane.WARNING_MESSAGE)
            return
        }
        if (wsActiveConnectionCount <= 0) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("InterceptWave")
                .createNotification(message("wspanel.send.no.connection"), NotificationType.WARNING)
                .notify(project)
            return
        }
        val target = when (wsTargetCombo.selectedIndex) {
            1 -> "ALL"
            2 -> "LATEST"
            else -> "MATCH"
        }
        mockServerService.sendWsMessage(configId, path = rule.path, message = msgTrimmed, target = target)
        NotificationGroupManager.getInstance()
            .getNotificationGroup("InterceptWave")
            .createNotification(message("wspanel.send.success"), NotificationType.INFORMATION)
            .notify(project)
    }

    private fun sendWsCustomMessage() {
        val cfg = configService.getProxyGroup(configId) ?: return
        if (cfg.protocol != "WS") return
        val msg = (wsCustomMsgArea.text ?: return).trim()
        if (msg.isEmpty()) {
            JOptionPane.showMessageDialog(null, message("ws.send.empty.warn"), message("config.message.info"), JOptionPane.WARNING_MESSAGE)
            return
        }
        if (wsActiveConnectionCount <= 0) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("InterceptWave")
                .createNotification(message("wspanel.send.no.connection"), NotificationType.WARNING)
                .notify(project)
            return
        }
        val target = when (wsTargetCombo.selectedIndex) {
            1 -> "ALL"
            2 -> "LATEST"
            else -> "MATCH"
        }
        try {
            mockServerService.sendWsMessage(configId, path = null, message = msg, target = target)
            NotificationGroupManager.getInstance()
                .getNotificationGroup("InterceptWave")
                .createNotification(message("wspanel.send.success"), NotificationType.INFORMATION)
                .notify(project)
        } catch (e: Exception) {
            thisLogger().warn("Send WS message failed", e)
        }
    }

    private fun startServer() {
        try {
            consoleService.showConsole()
            val success = mockServerService.startServer(configId)
            if (success) {
                updateStatus(true, mockServerService.getServerUrl(configId))
                onStatusChanged()
            }
        } catch (e: Exception) {
            thisLogger().error("Failed to start server for $configName", e)
        }
    }

    private fun stopServer() {
        try {
            mockServerService.stopServer(configId)
            updateStatus(false, null)
            onStatusChanged()
        } catch (e: Exception) {
            thisLogger().error("Failed to stop server for $configName", e)
        }
    }

    fun updateStatus(isRunning: Boolean, url: String?) {
        startButton.isEnabled = !isRunning && enabled
        stopButton.isEnabled = isRunning

        UiKit.applyStatusStyle(
            runtimeDotLabel,
            "",
            if (isRunning) UiKit.StatusTone.GREEN else UiKit.StatusTone.RED
        )
        runtimeDotLabel.toolTipText = if (isRunning) {
            message("toolwindow.status.running.indicator")
        } else {
            message("toolwindow.status.stopped.indicator")
        }

        if (isRunning && !url.isNullOrBlank()) {
            urlValueLabel.text = url
            urlValueLabel.foreground = JBColor(0x3D7EFF, 0x78A9FF)
            copyUrlButton.isEnabled = true
        } else {
            urlValueLabel.text = message("toolwindow.status.url.placeholder")
            urlValueLabel.foreground = UiKit.placeholderColor()
            copyUrlButton.isEnabled = false
            resetWsConnectionSummary()
        }
    }

    fun handleWsEvent(event: MockServerEvent) {
        when (event) {
            is WebSocketConnecting -> {
                wsActiveConnectionCount += 1
                wsLatestPath = event.path
                wsUpstreamTone = UiKit.StatusTone.YELLOW
                wsUpstreamText = message("toolwindow.ws.connection.connecting")
            }
            is WebSocketConnected -> {
                wsLatestPath = event.path
                wsUpstreamTone = UiKit.StatusTone.GREEN
                wsUpstreamText = message("toolwindow.ws.connection.upstream.connected")
            }
            is WebSocketClosed -> {
                wsActiveConnectionCount = (wsActiveConnectionCount - 1).coerceAtLeast(0)
                wsLatestPath = event.path.ifBlank { wsLatestPath ?: "" }
                if (wsActiveConnectionCount == 0) {
                    wsUpstreamTone = UiKit.StatusTone.GRAY
                    wsUpstreamText = message("toolwindow.ws.connection.idle")
                }
            }
            is WebSocketError -> {
                wsLatestPath = event.path.ifBlank { wsLatestPath ?: "" }
                wsUpstreamTone = UiKit.StatusTone.RED
                wsUpstreamText = message("toolwindow.ws.connection.upstream.error")
            }
            else -> return
        }
        refreshWsConnectionSummary()
    }

    private fun resetWsConnectionSummary() {
        wsActiveConnectionCount = 0
        wsLatestPath = null
        wsUpstreamTone = UiKit.StatusTone.GRAY
        wsUpstreamText = message("toolwindow.ws.connection.idle")
        refreshWsConnectionSummary()
    }

    private fun refreshWsConnectionSummary() {
        val cfg = configService.getProxyGroup(configId)
        val hasUpstreamBridge = !cfg?.wsBaseUrl.isNullOrBlank()
        UiKit.applyStatusStyle(
            wsModeLabel,
            if (hasUpstreamBridge) message("toolwindow.ws.mode.bridge") else message("toolwindow.ws.mode.local"),
            if (hasUpstreamBridge) UiKit.StatusTone.YELLOW else UiKit.StatusTone.BLUE
        )
        UiKit.applyStatusStyle(
            wsConnectionStateLabel,
            if (wsActiveConnectionCount > 0) message("toolwindow.ws.connection.active") else message("toolwindow.ws.connection.waiting"),
            if (wsActiveConnectionCount > 0) UiKit.StatusTone.GREEN else UiKit.StatusTone.GRAY
        )
        wsClientCountLabel.text = message("toolwindow.ws.connection.clients", wsActiveConnectionCount)
        wsClientCountLabel.foreground =
            if (wsActiveConnectionCount > 0) JBColor(0x425466, 0xC0CAD5) else JBColor(0x5F6B7A, 0xA7B0BA)
        wsClientCountLabel.border = JBUI.Borders.empty(2, 0)
        if (hasUpstreamBridge) {
            UiKit.applyStatusStyle(wsUpstreamStateLabel, wsUpstreamText, wsUpstreamTone)
            wsModeDetailLabel.text = message("toolwindow.ws.mode.bridge.detail", cfg.wsBaseUrl ?: message("toolwindow.notset"))
        } else {
            UiKit.applyStatusStyle(wsUpstreamStateLabel, message("toolwindow.ws.connection.local.only"), UiKit.StatusTone.BLUE)
            wsModeDetailLabel.text = message("toolwindow.ws.mode.local.detail")
        }
        wsLastPathLabel.text = message("toolwindow.ws.connection.path", wsLatestPath ?: message("toolwindow.notset"))
        UiKit.applyMutedText(wsLastPathLabel)
        wsLastPathLabel.border = JBUI.Borders.empty(2, 0)
        UiKit.applyMutedText(wsModeDetailLabel)
        wsModeDetailLabel.border = JBUI.Borders.empty(2, 0)
    }
}
