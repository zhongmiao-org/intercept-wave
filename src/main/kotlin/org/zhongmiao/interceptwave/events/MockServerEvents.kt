package org.zhongmiao.interceptwave.events

import com.intellij.util.messages.Topic

/**
 * 领域事件定义：用于在业务层和 UI 渲染层之间解耦
 * 业务层只负责发布事件，UI 层自行订阅并渲染。
 */
sealed interface MockServerEvent {
    /** 配置组对应的 ID（如有） */
    val configId: String?
}

/** 服务启动相关事件 */
data class ServerStarting(
    override val configId: String,
    val name: String,
    val port: Int
) : MockServerEvent

data class ServerStarted(
    override val configId: String,
    val name: String,
    val port: Int,
    val url: String
) : MockServerEvent

data class ServerStartFailed(
    override val configId: String,
    val name: String,
    val port: Int,
    val reason: String?
) : MockServerEvent

data class ServerStopped(
    override val configId: String,
    val name: String,
    val port: Int
) : MockServerEvent

/** 批量启动/停止提示 */
data class AllServersStarting(
    override val configId: String? = null,
    val total: Int
) : MockServerEvent

data class AllServersStarted(
    override val configId: String? = null,
    val success: Int,
    val total: Int
) : MockServerEvent

data class AllServersStopped(
    override val configId: String? = null
) : MockServerEvent

/** 请求/匹配/转发/错误 等运行时事件 */
data class RequestReceived(
    override val configId: String,
    val configName: String,
    val method: String,
    val path: String
) : MockServerEvent

data class MockMatched(
    override val configId: String,
    val configName: String,
    val path: String,
    val method: String,
    val statusCode: Int
) : MockServerEvent

data class Forwarded(
    override val configId: String,
    val configName: String,
    val targetUrl: String,
    val statusCode: Int
) : MockServerEvent

/** 在匹配阶段记录最终用于匹配的路径（stripPrefix 后的结果等） */
data class MatchedPath(
    override val configId: String,
    val configName: String,
    val path: String
) : MockServerEvent

/** 在转发前记录目标 URL（包含 query） */
data class ForwardingTo(
    override val configId: String,
    val configName: String,
    val targetUrl: String
) : MockServerEvent

data class ErrorOccurred(
    override val configId: String? = null,
    val configName: String? = null,
    val message: String,
    val details: String? = null
) : MockServerEvent

/**
 * 事件监听接口：UI 层或其他适配器实现此接口以接收事件。
 */
fun interface MockServerEventListener {
    fun onEvent(event: MockServerEvent)
}

/**
 * IntelliJ 平台消息总线主题：用于广播 MockServerEvent。
 */
val MOCK_SERVER_TOPIC: Topic<MockServerEventListener> =
    Topic.create("InterceptWave.MockServerEvents", MockServerEventListener::class.java)

/**
 * 输出端口接口：业务层仅依赖该接口发布事件，不直接依赖任何 UI。
 */
interface MockServerOutput {
    /** 发布领域事件 */
    fun publish(event: MockServerEvent)
}
