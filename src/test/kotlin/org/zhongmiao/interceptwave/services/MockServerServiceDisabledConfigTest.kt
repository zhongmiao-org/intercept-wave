package org.zhongmiao.interceptwave.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.zhongmiao.interceptwave.model.ProxyConfig
import java.util.UUID

class MockServerServiceDisabledConfigTest : BasePlatformTestCase() {

    private lateinit var service: MockServerService
    private lateinit var configService: ConfigService

    override fun setUp() {
        super.setUp()
        service = project.getService(MockServerService::class.java)
        configService = project.getService(ConfigService::class.java)
        // 清空配置
        val root = configService.getRootConfig()
        root.proxyGroups.clear()
        configService.saveRootConfig(root)
    }

    override fun tearDown() {
        try { service.stopAllServers() } finally { super.tearDown() }
    }

    fun `test startServer returns false when config disabled`() {
        val cfg = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Disabled",
            port = 19050,
            enabled = false
        )
        val root = configService.getRootConfig()
        root.proxyGroups.add(cfg)
        configService.saveRootConfig(root)

        val ok = service.startServer(cfg.id)
        assertFalse(ok)
        assertFalse(service.getServerStatus(cfg.id))
    }

    fun `test startAllServers returns empty when no enabled groups`() {
        val cfg1 = ProxyConfig(id = UUID.randomUUID().toString(), name = "A", port = 19051, enabled = false)
        val cfg2 = ProxyConfig(id = UUID.randomUUID().toString(), name = "B", port = 19052, enabled = false)
        val root = configService.getRootConfig()
        root.proxyGroups.addAll(listOf(cfg1, cfg2))
        configService.saveRootConfig(root)

        val results = service.startAllServers()
        assertTrue(results.isEmpty())
        assertTrue(service.getRunningServers().isEmpty())
    }
}

