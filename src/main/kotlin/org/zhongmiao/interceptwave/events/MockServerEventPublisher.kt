package org.zhongmiao.interceptwave.events

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * 事件发布适配器（输出端口实现）：将领域事件发布到 IntelliJ MessageBus。
 * 业务层注入并调用该服务，UI 层通过订阅 [MOCK_SERVER_TOPIC] 获取事件。
 */
@Service(Service.Level.PROJECT)
class MockServerEventPublisher(private val project: Project) : MockServerOutput {

    override fun publish(event: MockServerEvent) {
        // 同步发布事件（无状态，轻量）
        val publisher = project.messageBus.syncPublisher(MOCK_SERVER_TOPIC)
        publisher.onEvent(event)
    }
}

