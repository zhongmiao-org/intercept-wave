package org.zhongmiao.interceptwave.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 根配置 - v2.0 多配置组支持
 */
@Serializable
data class RootConfig(
    // 配置版本号
    var version: String = "2.0",

    // 代理配置组列表
    var proxyGroups: MutableList<ProxyConfig> = mutableListOf()
)

/**
 * 单个代理配置组（原 MockConfig）
 */
@Serializable
data class ProxyConfig(
    // 唯一标识符
    var id: String = UUID.randomUUID().toString(),

    // 配置组名称（用户自定义）
    var name: String = "默认配置",

    // 组类型：本地监听协议。HTTP=仅处理HTTP；WS=仅处理WebSocket
    var protocol: String = "HTTP",

    // Mock服务的本地端口
    var port: Int = 8888,

    // 需要劫持的接口地址前缀
    var interceptPrefix: String = "/api",

    // 原始接口的基础URL
    var baseUrl: String = "http://localhost:8080",

    // 是否在匹配时去掉前缀（默认true，推荐）
    // mockApis中的path配置为相对路径（不含interceptPrefix）
    //
    // 当stripPrefix=true时（推荐）：
    //   - 请求 /api/user -> 去掉 /api -> /user -> 匹配 mockApis中的path="/user"
    //   - mockApis配置简洁，只需写 "/user" 即可
    //
    // 当stripPrefix=false时：
    //   - 请求 /api/user -> /api/user -> 需要 mockApis中的path="/api/user" 才能匹配
    //   - 需要在每个path配置中都写完整路径
    var stripPrefix: Boolean = true,

    // 全局Cookie，例如: sessionId=abc123; userId=456
    var globalCookie: String = "",

    // 是否启用该配置组
    var enabled: Boolean = true,

    // Mock接口配置列表（HTTP 组使用）
    var mockApis: MutableList<MockApiConfig> = mutableListOf(),

    // ================= WS 组相关（当 protocol=WS 时） =================
    // 上游 WebSocket 地址（支持 ws:// 或 wss://）。当为 WS 组时建议填写。
    var wsBaseUrl: String? = null,

    // WS 的拦截前缀（为空则复用 interceptPrefix）
    var wsInterceptPrefix: String? = null,

    // 是否在工具窗口显示手动推送面板
    var wsManualPush: Boolean = true,

    // WS 推送规则（用于自动推送与手动模板）
    var wsPushRules: MutableList<WsPushRule> = mutableListOf()
    ,
    // WSS(TLS) 本地监听支持
    var wssEnabled: Boolean = false,
    var wssKeystorePath: String? = null,
    var wssKeystorePassword: String? = null
)

/**
 * Mock服务的全局配置（保留用于向后兼容）
 * @deprecated 使用 RootConfig 和 ProxyConfig 替代 10 个迭代后废弃
 */
@Serializable
data class MockConfig(
    // Mock服务的本地端口
    var port: Int = 8888,

    // 需要劫持的接口地址前缀
    var interceptPrefix: String = "/api",

    // 原始接口的基础URL
    var baseUrl: String = "http://localhost:8080",

    // 是否在匹配时去掉前缀（默认true，推荐）
    var stripPrefix: Boolean = true,

    // 全局Cookie，例如: sessionId=abc123; userId=456
    var globalCookie: String = "",

    // Mock接口配置列表
    var mockApis: MutableList<MockApiConfig> = mutableListOf()
)

/**
 * 单个Mock接口的配置
 */
@Serializable
data class MockApiConfig(
    // 接口路径
    // 注意：如果stripPrefix=true（推荐），这里配置相对路径，例如: /user
    // 如果stripPrefix=false，这里需要配置完整路径，例如: /api/user
    // 支持通配符：
    //   *  匹配单个路径段（不含 /），如 /a/b/*
    //   ** 匹配多个路径段（可含 /），如 /a/b/**
    //   段内通配，如 /order/*/submit
    var path: String,

    // 是否启用Mock（如果不启用则转发到原始接口）
    var enabled: Boolean = true,

    // Mock返回的JSON数据
    var mockData: String = "{}",

    // HTTP方法，默认支持所有方法
    var method: String = "ALL",

    // 响应状态码
    var statusCode: Int = 200,

    // 是否使用全局Cookie
    var useCookie: Boolean = false,

    // 延迟时间（毫秒）
    var delay: Long = 0
)

/**
 * WebSocket 推送规则（适用于 WS 组）。
 * 可用于自动推送（periodic/timeline）与手动发送的模板。
 */
@Serializable
data class WsPushRule(
    var enabled: Boolean = true,
    // 路径模式，支持 * 与 **，与 HTTP Mock 的 path 规则一致
    var path: String,
    // 事件字段键（例如 action/type/event），为空表示不基于事件匹配；默认使用 action
    var eventKey: String? = "action",
    // 事件字段的值（可为通配符表达式），为空表示不基于事件值匹配
    var eventValue: String? = null,
    // 匹配方向：in（上游→客户端）、out（客户端→上游）、both（双向）
    var direction: String = "both",
    // 推送模式：off | periodic | timeline
    var mode: String = "off",
    // 周期（秒），仅当 mode=periodic 时有效，取值 >= 1
    var periodSec: Int = 5,
    // 周期模式下发送的消息内容。也作为该规则的手动发送模板
    var message: String = "{}",
    // 时间轴模式：按毫秒时间点发送多条消息
    var timeline: MutableList<WsTimelineItem> = mutableListOf(),
    // 时间轴是否循环
    var loop: Boolean = false,
    // 连接建立后是否立即发送一条 message（对 periodic 模式有意义）
    var onOpenFire: Boolean = false
)

@Serializable
data class WsTimelineItem(
    var atMs: Int,
    var message: String
)
