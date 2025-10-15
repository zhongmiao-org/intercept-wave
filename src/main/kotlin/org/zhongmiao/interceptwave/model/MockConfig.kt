package org.zhongmiao.interceptwave.model

import kotlinx.serialization.Serializable

/**
 * Mock服务的全局配置
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