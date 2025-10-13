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

    // 是否过滤/取消前缀（默认false，不过滤）
    // 当为true时，访问 localhost:8888/*** 会匹配 /api/***
    // 当为false时，需要访问 localhost:8888/api/*** 才能匹配
    var stripPrefix: Boolean = false,

    // Mock接口配置列表
    var mockApis: MutableList<MockApiConfig> = mutableListOf()
)

/**
 * 单个Mock接口的配置
 */
@Serializable
data class MockApiConfig(
    // 接口路径，例如: /api/b
    var path: String,

    // 是否启用Mock（如果不启用则转发到原始接口）
    var enabled: Boolean = true,

    // Mock返回的JSON数据
    var mockData: String = "{}",

    // HTTP方法，默认支持所有方法
    var method: String = "ALL",

    // 响应状态码
    var statusCode: Int = 200,

    // 自定义响应头
    var headers: Map<String, String> = emptyMap(),

    // 延迟时间（毫秒）
    var delay: Long = 0
)