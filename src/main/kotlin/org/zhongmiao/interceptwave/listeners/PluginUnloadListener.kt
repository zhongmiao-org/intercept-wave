package org.zhongmiao.interceptwave.listeners

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.components.service
import com.intellij.openapi.project.ProjectManager
import org.zhongmiao.interceptwave.services.ConsoleService
import org.zhongmiao.interceptwave.services.MockServerService

/**
 * Ensure dynamic unload is clean by stopping mock servers
 * and releasing console process before the plugin classloader
 * is unloaded.
 */
class PluginUnloadListener : DynamicPluginListener {
    override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        // Stop all running mock servers in all open projects
        ProjectManager.getInstance().openProjects.forEach { project ->
            runCatching { project.service<MockServerService>().stopAllServers() }
            runCatching { project.service<ConsoleService>().terminateConsoleProcess() }
        }
    }
}

