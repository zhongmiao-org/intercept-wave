package org.zhongmiao.interceptwave.ui

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.zhongmiao.interceptwave.model.HttpRoute
import org.zhongmiao.interceptwave.model.MockApiConfig
import org.zhongmiao.interceptwave.model.ProxyConfig
import org.zhongmiao.interceptwave.model.RootConfig
import org.zhongmiao.interceptwave.services.ConfigService
import org.zhongmiao.interceptwave.services.MockServerService
import java.io.File

class InterceptWaveToolWindowActionsTest : BasePlatformTestCase() {

    private lateinit var configService: ConfigService
    private lateinit var mockServerService: MockServerService

    override fun setUp() {
        super.setUp()
        configService = project.getService(ConfigService::class.java)
        mockServerService = project.getService(MockServerService::class.java)
    }

    override fun tearDown() {
        try {
            mockServerService.stopAllServers()
        } finally {
            super.tearDown()
        }
    }

    fun `test openConfigFile opens project config json in editor`() {
        val toolWindow = InterceptWaveToolWindow(project)
        toolWindow.getContent()

        toolWindow.openConfigFile()

        val selected = FileEditorManager.getInstance(project).selectedFiles
        assertEquals("config.json", selected.single().name)
        assertTrue(File(project.basePath, ".intercept-wave/config.json").exists())
    }

    fun `test reloadConfigFromDisk refreshes config and restarts previously running group`() {
        val port = java.net.ServerSocket(0).use { it.localPort }
        val cfg = ProxyConfig(
            id = "reload-running",
            name = "Reload Running",
            port = port,
            enabled = true,
            routes = mutableListOf(
                HttpRoute(
                    mockApis = mutableListOf(MockApiConfig(path = "/ok", mockData = "{}", enabled = true))
                )
            )
        )
        configService.saveRootConfig(RootConfig(proxyGroups = mutableListOf(cfg)))

        assertTrue(mockServerService.startServer(cfg.id))
        val toolWindow = InterceptWaveToolWindow(project)
        toolWindow.getContent()

        configService.ensureConfigFile().writeText(
            """
            {
              "version": "4.0",
              "proxyGroups": [
                {
                  "id": "reload-running",
                  "name": "Reloaded Name",
                  "protocol": "HTTP",
                  "port": $port,
                  "enabled": true,
                  "routes": [
                    {
                      "name": "API",
                      "pathPrefix": "/api",
                      "targetBaseUrl": "http://localhost:4002",
                      "stripPrefix": true,
                      "enableMock": true,
                      "mockApis": [
                        {
                          "path": "/ok",
                          "method": "GET",
                          "mockData": "{}",
                          "enabled": true
                        }
                      ]
                    }
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        toolWindow.reloadConfigFromDisk()

        assertEquals("Reloaded Name", configService.getProxyGroup(cfg.id)?.name)
        assertTrue(mockServerService.getServerStatus(cfg.id))
    }

    fun `test reloadConfigFromDisk saves unsaved editor changes before reloading`() {
        val toolWindow = InterceptWaveToolWindow(project)
        toolWindow.getContent()
        val configFile = configService.ensureConfigFile()
        val virtualFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByIoFile(configFile)!!
        FileEditorManager.getInstance(project).openFile(virtualFile, true)
        val document = FileDocumentManager.getInstance().getDocument(virtualFile)!!

        document.setText(
            """
            {
              "version": "4.0",
              "proxyGroups": [
                {
                  "id": "edited-in-editor",
                  "name": "Edited In Editor",
                  "protocol": "HTTP",
                  "port": 18888,
                  "enabled": true,
                  "routes": [
                    {
                      "name": "API1",
                      "pathPrefix": "/api",
                      "targetBaseUrl": "http://localhost:9000",
                      "stripPrefix": true,
                      "enableMock": true,
                      "mockApis": []
                    }
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        toolWindow.reloadConfigFromDisk()

        assertEquals("API1", configService.getRootConfig().proxyGroups.single().routes.single().name)
    }
}
