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
            if (mockServerService.isRunning()) {
                thisLogger().info("Stopping mock server before project close")
                mockServerService.stop()
            }
        } catch (e: Exception) {
            thisLogger().error("Error stopping mock server on project close", e)
        }
    }
}