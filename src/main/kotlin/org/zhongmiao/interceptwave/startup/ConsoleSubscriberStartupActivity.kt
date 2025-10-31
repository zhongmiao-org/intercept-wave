package org.zhongmiao.interceptwave.startup

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.startup.ProjectActivity
import org.zhongmiao.interceptwave.ui.MockServerConsoleSubscriber

/**
 * Ensure UI console subscriber is created after project open,
 * replacing deprecated ProjectManagerListener.projectOpened.
 */
class ConsoleSubscriberStartupActivity : ProjectActivity, DumbAware {
    override suspend fun execute(project: Project) {
        // Create and register console subscriber early so first events can show Run window
        runCatching { project.service<MockServerConsoleSubscriber>() }
    }
}
