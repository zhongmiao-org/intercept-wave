package org.zhongmiao.interceptwave.ui

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.zhongmiao.interceptwave.model.HttpRoute
import org.zhongmiao.interceptwave.model.MockApiConfig
import org.zhongmiao.interceptwave.model.ProxyConfig
import org.zhongmiao.interceptwave.services.ConfigService
import org.zhongmiao.interceptwave.services.MockServerService
import java.util.UUID
import javax.swing.JButton
import javax.swing.JLabel

class ProxyGroupTabPanelTest : BasePlatformTestCase() {

    private lateinit var mockServerService: MockServerService
    private lateinit var configService: ConfigService

    override fun setUp() {
        super.setUp()
        mockServerService = project.getService(MockServerService::class.java)
        configService = project.getService(ConfigService::class.java)
        // Clean config to avoid cross-test accumulation
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

    fun `test updateStatus toggles buttons`() {
        val id = UUID.randomUUID().toString()
        val p = java.net.ServerSocket(0).use { it.localPort }
        val cfg = ProxyConfig(id = id, name = "PG", port = p, enabled = true,
            routes = mutableListOf(HttpRoute(mockApis = mutableListOf(MockApiConfig(path = "/u", mockData = "{}", enabled = true)))))
        val root = configService.getRootConfig()
        root.proxyGroups.add(cfg)
        configService.saveRootConfig(root)

        val panel = ProxyGroupTabPanel(project, id, cfg.name, cfg.port, cfg.enabled) {}
        // reflect buttons
        val startBtn = panel.javaClass.getDeclaredField("startButton").apply { isAccessible = true }.get(panel) as JButton
        val stopBtn = panel.javaClass.getDeclaredField("stopButton").apply { isAccessible = true }.get(panel) as JButton

        // Initially server not running
        assertTrue(startBtn.isEnabled)
        assertFalse(stopBtn.isEnabled)

        // Simulate running state
        panel.updateStatus(true, "http://localhost:$p")
        assertFalse(startBtn.isEnabled)
        assertTrue(stopBtn.isEnabled)

        // Back to stopped
        panel.updateStatus(false, null)
        assertTrue(startBtn.isEnabled)
        assertFalse(stopBtn.isEnabled)
    }

    fun `test missing config shows placeholder url`() {
        val missingId = "missing"
        val panel = ProxyGroupTabPanel(project, missingId, "Missing", 19099, true) {}
        panel.getPanel()
        val urlLabel = panel.javaClass.getDeclaredField("urlValueLabel").apply { isAccessible = true }.get(panel) as JLabel
        assertEquals("Not running yet", urlLabel.text)
    }
}
