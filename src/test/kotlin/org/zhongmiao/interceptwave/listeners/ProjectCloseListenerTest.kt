package org.zhongmiao.interceptwave.listeners

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.zhongmiao.interceptwave.model.ProxyConfig
import org.zhongmiao.interceptwave.services.MockServerService
import java.util.UUID

/**
 * Tests for ProjectCloseListener
 */
class ProjectCloseListenerTest : BasePlatformTestCase() {

    private lateinit var listener: ProjectCloseListener
    private lateinit var mockServerService: MockServerService

    override fun setUp() {
        super.setUp()
        listener = ProjectCloseListener()
        mockServerService = project.getService(MockServerService::class.java)
    }

    override fun tearDown() {
        try {
            // Ensure all servers are stopped
            mockServerService.stopAllServers()
        } finally {
            super.tearDown()
        }
    }

    fun `test projectClosing with no running servers`() {
        // Should not throw exception
        listener.projectClosing(project)

        // Verify no servers are running
        assertTrue(mockServerService.getRunningServers().isEmpty())
    }

    fun `test projectClosing stops running servers`() {
        val configService = project.getService(org.zhongmiao.interceptwave.services.ConfigService::class.java)

        // Start a server
        val config = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Test Server",
            port = 19001,
            enabled = true
        )
        val rootConfig = configService.getRootConfig()
        rootConfig.proxyGroups.add(config)
        configService.saveRootConfig(rootConfig)

        mockServerService.startServer(config.id)
        assertTrue(mockServerService.getServerStatus(config.id))

        // Call project closing
        listener.projectClosing(project)

        // Verify server is stopped
        assertFalse(mockServerService.getServerStatus(config.id))
        assertTrue(mockServerService.getRunningServers().isEmpty())
    }

    fun `test projectClosing stops multiple running servers`() {
        val configService = project.getService(org.zhongmiao.interceptwave.services.ConfigService::class.java)

        // Start multiple servers
        val config1 = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Server 1",
            port = 19002,
            enabled = true
        )
        val config2 = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Server 2",
            port = 19003,
            enabled = true
        )
        val config3 = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Server 3",
            port = 19004,
            enabled = true
        )

        val rootConfig = configService.getRootConfig()
        rootConfig.proxyGroups.add(config1)
        rootConfig.proxyGroups.add(config2)
        rootConfig.proxyGroups.add(config3)
        configService.saveRootConfig(rootConfig)

        mockServerService.startServer(config1.id)
        mockServerService.startServer(config2.id)
        mockServerService.startServer(config3.id)

        assertEquals(3, mockServerService.getRunningServers().size)

        // Call project closing
        listener.projectClosing(project)

        // Verify all servers are stopped
        assertTrue(mockServerService.getRunningServers().isEmpty())
        assertFalse(mockServerService.getServerStatus(config1.id))
        assertFalse(mockServerService.getServerStatus(config2.id))
        assertFalse(mockServerService.getServerStatus(config3.id))
    }

    fun `test projectClosing is safe to call multiple times`() {
        val configService = project.getService(org.zhongmiao.interceptwave.services.ConfigService::class.java)

        val config = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Test Server",
            port = 19005,
            enabled = true
        )
        val rootConfig = configService.getRootConfig()
        rootConfig.proxyGroups.add(config)
        configService.saveRootConfig(rootConfig)

        mockServerService.startServer(config.id)

        // Call multiple times - should not throw
        listener.projectClosing(project)
        listener.projectClosing(project)
        listener.projectClosing(project)

        assertTrue(mockServerService.getRunningServers().isEmpty())
    }

    fun `test projectClosing handles stopped servers gracefully`() {
        val configService = project.getService(org.zhongmiao.interceptwave.services.ConfigService::class.java)

        val config = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Test Server",
            port = 19006,
            enabled = true
        )
        val rootConfig = configService.getRootConfig()
        rootConfig.proxyGroups.add(config)
        configService.saveRootConfig(rootConfig)

        mockServerService.startServer(config.id)
        mockServerService.stopServer(config.id)

        // Server already stopped - should not throw
        listener.projectClosing(project)

        assertTrue(mockServerService.getRunningServers().isEmpty())
    }

    fun `test listener can be instantiated`() {
        val newListener = ProjectCloseListener()
        assertNotNull(newListener)
    }

    fun `test projectClosing with mixed server states`() {
        val configService = project.getService(org.zhongmiao.interceptwave.services.ConfigService::class.java)

        val runningConfig = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Running Server",
            port = 19007,
            enabled = true
        )
        val stoppedConfig = ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = "Stopped Server",
            port = 19008,
            enabled = true
        )

        val rootConfig = configService.getRootConfig()
        rootConfig.proxyGroups.add(runningConfig)
        rootConfig.proxyGroups.add(stoppedConfig)
        configService.saveRootConfig(rootConfig)

        // Start one, leave one stopped
        mockServerService.startServer(runningConfig.id)

        listener.projectClosing(project)

        // All should be stopped
        assertTrue(mockServerService.getRunningServers().isEmpty())
        assertFalse(mockServerService.getServerStatus(runningConfig.id))
        assertFalse(mockServerService.getServerStatus(stoppedConfig.id))
    }
}