package org.zhongmiao.interceptwave.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.zhongmiao.interceptwave.model.ProxyConfig
import java.net.ServerSocket
import java.util.UUID

class MockServerServicePortOccupiedTest : BasePlatformTestCase() {

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

    fun `test startServer returns false when port occupied`() {
        // occupy a system-chosen free port and reuse it for config
        val port = ServerSocket(0).use { it.localPort }
        // Occupy it again for the duration of start attempt
        ServerSocket(port).use { _ ->
            val cfg = ProxyConfig(
                id = UUID.randomUUID().toString(),
                name = "Occupied",
                port = port,
                enabled = true
            )
            val root = configService.getRootConfig()
            root.proxyGroups.add(cfg)
            configService.saveRootConfig(root)

            val result = mockServerService.startServer(cfg.id)
            assertFalse(result)
            assertFalse(mockServerService.getServerStatus(cfg.id))
        }
    }
}

