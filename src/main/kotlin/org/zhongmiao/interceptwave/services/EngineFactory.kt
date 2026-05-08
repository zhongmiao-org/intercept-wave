package org.zhongmiao.interceptwave.services

import org.zhongmiao.interceptwave.events.MockServerOutput
import org.zhongmiao.interceptwave.model.ProxyConfig

/**
 * Factory to create appropriate engine per config.
 */
object EngineFactory {
    fun create(config: ProxyConfig, output: MockServerOutput, projectBasePath: String? = null): ServerEngine =
        if (config.protocol.equals("WS", ignoreCase = true))
            org.zhongmiao.interceptwave.services.ws.WsServerEngine(config, output)
        else
            org.zhongmiao.interceptwave.services.http.HttpServerEngine(config, output, projectBasePath)
}
