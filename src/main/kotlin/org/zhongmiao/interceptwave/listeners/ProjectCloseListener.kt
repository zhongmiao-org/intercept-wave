package org.zhongmiao.interceptwave.listeners

import org.zhongmiao.interceptwave.services.MockServerService
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener

/**
 * 项目关闭监听器
 * 在项目关闭时自动停止Mock服务
 */
class ProjectCloseListener : ProjectManagerListener {

    override fun projectClosing(project: Project) {
        try {
            val mockServerService = project.service<MockServerService>()
            val runningServers = mockServerService.getRunningServers()
            if (runningServers.isNotEmpty()) {
                thisLogger().info("Stopping mock servers before project close")
                mockServerService.stopAllServers()
            }
        } catch (e: Exception) {
            thisLogger().error("Error stopping mock servers on project close", e)
        }
    }
}
