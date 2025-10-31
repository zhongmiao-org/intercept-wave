package org.zhongmiao.interceptwave.ui

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.zhongmiao.interceptwave.model.MockApiConfig
import org.zhongmiao.interceptwave.model.ProxyConfig
import org.zhongmiao.interceptwave.services.ConfigService
import org.zhongmiao.interceptwave.services.MockServerService
import java.util.UUID
import javax.swing.JButton

class ProxyGroupTabPanelStartStopTest : BasePlatformTestCase() {

    private lateinit var mockServerService: MockServerService
    private lateinit var configService: ConfigService

    override fun setUp() {
        super.setUp()
        mockServerService = project.getService(MockServerService::class.java)
        configService = project.getService(ConfigService::class.java)
        // Clean config
        runCatching {
            val root = configService.getRootConfig()
            root.proxyGroups.clear()
            configService.saveRootConfig(root)
        }
    }

    override fun tearDown() {
        try {
            mockServerService.stopAllServers()
        } finally {
            super.tearDown()
        }
    }

    fun `test start and stop buttons invoke service`() {
        val id = UUID.randomUUID().toString()
        val cfg = ProxyConfig(
            id = id,
            name = "PanelStartStop",
            port = 19026,
            enabled = true,
            mockApis = mutableListOf(MockApiConfig(path = "/ok", mockData = "{}", enabled = true))
        )
        val root = configService.getRootConfig()
        root.proxyGroups.add(cfg)
        configService.saveRootConfig(root)

        val panel = ProxyGroupTabPanel(project, id, cfg.name, cfg.port, cfg.enabled) {}
        panel.getPanel() // initialize buttons/actions

        val start = panel.javaClass.getDeclaredField("startButton").apply { isAccessible = true }.get(panel) as JButton
        val stop = panel.javaClass.getDeclaredField("stopButton").apply { isAccessible = true }.get(panel) as JButton

        // Start
        start.doClick()
        assertTrue(mockServerService.getServerStatus(id))

        // Stop
        stop.doClick()
        assertFalse(mockServerService.getServerStatus(id))
    }
}
